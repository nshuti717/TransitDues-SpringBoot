package rw.ac.auca.transitdues.operator.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.email.EmailEventPublisher;
import rw.ac.auca.transitdues.exception.DuplicatePlateException;
import rw.ac.auca.transitdues.exception.OperatorNotEligibleException;
import rw.ac.auca.transitdues.exception.SelfActionNotAllowedException;
import rw.ac.auca.transitdues.exception.StageCapacityExceededException;
import rw.ac.auca.transitdues.operator.domain.ApprovalStatus;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatorServiceImplTest {

    @Mock
    private OperatorRepository operatorRepository;

    @Mock
    private StageRepository stageRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmailEventPublisher emailEventPublisher;

    @InjectMocks
    private OperatorServiceImpl operatorService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void secondOperatorWithDifferentlyFormattedPlateIsRejectedAsDuplicate() {
        UUID stageId = UUID.randomUUID();
        Stage stage = new Stage();
        stage.setId(stageId);
        stage.setName("Remera");
        stage.setLocation("Gasabo, Kigali");
        stage.setCapacity(5);

        Operator secondOperator = new Operator();
        secondOperator.setFullName("Second Operator");
        secondOperator.setPhoneNumber("0788222333");
        secondOperator.setPlateNumber(" rab-123a ");
        secondOperator.setStage(stage);

        when(stageRepository.findById(stageId)).thenReturn(Optional.of(stage));
        when(operatorRepository.countByStageIdAndApprovalStatusNotIn(eq(stageId), any())).thenReturn(0);
        // "RAB123A" is already registered; the incoming " rab-123a " must
        // normalize to the same value before the duplicate check runs.
        when(operatorRepository.existsByPlateNumberIgnoreCase("RAB123A")).thenReturn(true);

        DuplicatePlateException ex = assertThrows(DuplicatePlateException.class,
                () -> operatorService.createOperator(secondOperator));

        assertEquals("This plate number is already registered.", ex.getMessage());
        verify(operatorRepository, never()).save(any(Operator.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void approvingAPendingOperatorConfirmsTheStageAndActivatesThem() {
        asStaffUser("finance@example.com");
        UUID stageId = UUID.randomUUID();
        Stage stage = stage(stageId, "Kimironko", 10);

        Operator operator = pendingOperator(stage);
        when(operatorRepository.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(stageRepository.findById(stageId)).thenReturn(Optional.of(stage));
        when(operatorRepository.save(any(Operator.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Operator approved = operatorService.approveOperator(operator.getId(), stageId, "Looks good");

        assertEquals(ApprovalStatus.ACTIVE, approved.getApprovalStatus());
        assertEquals("finance@example.com", approved.getApprovalActionBy());
        assertEquals("Looks good", approved.getApprovalReason());
        verify(auditLogService).record(eq("Operator"), any(), eq("APPROVE"), any());
    }

    @Test
    void approvingIntoAStageAtCapacityIsRejected() {
        asStaffUser("finance@example.com");
        UUID currentStageId = UUID.randomUUID();
        UUID fullStageId = UUID.randomUUID();
        Stage currentStage = stage(currentStageId, "Nyabugogo", 10);
        Stage fullStage = stage(fullStageId, "Remera", 1);

        Operator operator = pendingOperator(currentStage);
        when(operatorRepository.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(stageRepository.findById(fullStageId)).thenReturn(Optional.of(fullStage));
        when(operatorRepository.countByStageIdAndApprovalStatusNotIn(eq(fullStageId), any())).thenReturn(1);

        assertThrows(StageCapacityExceededException.class,
                () -> operatorService.approveOperator(operator.getId(), fullStageId, null));
        verify(operatorRepository, never()).save(any(Operator.class));
    }

    @Test
    void rejectingAPendingOperatorBlocksThemFromDues() {
        asStaffUser("finance@example.com");
        Stage stage = stage(UUID.randomUUID(), "Kacyiru", 10);
        Operator operator = pendingOperator(stage);
        when(operatorRepository.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(operatorRepository.save(any(Operator.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Operator rejected = operatorService.rejectOperator(operator.getId(), "Invalid plate");

        assertEquals(ApprovalStatus.REJECTED, rejected.getApprovalStatus());
        verify(auditLogService).record(eq("Operator"), any(), eq("REJECT"), any());
    }

    @Test
    void suspendingAnActiveOperatorBlocksThemFromDues() {
        asStaffUser("admin@example.com");
        Stage stage = stage(UUID.randomUUID(), "Kacyiru", 10);
        Operator operator = activeOperator(stage);
        when(operatorRepository.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(operatorRepository.save(any(Operator.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Operator suspended = operatorService.suspendOperator(operator.getId(), "Complaint under review");

        assertEquals(ApprovalStatus.SUSPENDED, suspended.getApprovalStatus());
    }

    @Test
    void deactivatingAnOperatorDisablesItsLinkedLoginAndKeepsTheRow() {
        asStaffUser("admin@example.com");
        Stage stage = stage(UUID.randomUUID(), "Kacyiru", 10);
        Operator operator = activeOperator(stage);
        UserAccount linkedAccount = new UserAccount();
        linkedAccount.setEmail("operator@example.com");
        linkedAccount.setEnabled(true);

        when(operatorRepository.findById(operator.getId())).thenReturn(Optional.of(operator));
        lenient().when(userAccountRepository.findByOperatorId(operator.getId())).thenReturn(Optional.of(linkedAccount));
        when(operatorRepository.save(any(Operator.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Operator deactivated = operatorService.deactivateOperator(operator.getId(), "Left the cooperative", "admin@example.com");

        assertEquals(ApprovalStatus.DEACTIVATED, deactivated.getApprovalStatus());
        assertFalse(linkedAccount.isEnabled());
        verify(operatorRepository, never()).delete(any(Operator.class));
        verify(userAccountRepository).save(linkedAccount);
    }

    @Test
    void adminCannotDeactivateTheOperatorLinkedToTheirOwnAccount() {
        Stage stage = stage(UUID.randomUUID(), "Kacyiru", 10);
        Operator operator = activeOperator(stage);
        UserAccount selfAccount = new UserAccount();
        selfAccount.setEmail("self-admin@example.com");

        when(operatorRepository.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(userAccountRepository.findByOperatorId(operator.getId())).thenReturn(Optional.of(selfAccount));

        assertThrows(SelfActionNotAllowedException.class,
                () -> operatorService.deactivateOperator(operator.getId(), "reason", "self-admin@example.com"));
        verify(operatorRepository, never()).save(any(Operator.class));
    }

    @Test
    void aDeactivatedOperatorCannotBeApprovedAgain() {
        Stage stage = stage(UUID.randomUUID(), "Kacyiru", 10);
        Operator operator = activeOperator(stage);
        operator.setApprovalStatus(ApprovalStatus.DEACTIVATED);
        when(operatorRepository.findById(operator.getId())).thenReturn(Optional.of(operator));

        assertThrows(OperatorNotEligibleException.class,
                () -> operatorService.approveOperator(operator.getId(), stage.getId(), null));
    }

    private void asStaffUser(String email) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                email, "n/a", AuthorityUtils.createAuthorityList("ROLE_FINANCE_OFFICER"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Stage stage(UUID id, String name, int capacity) {
        Stage stage = new Stage();
        stage.setId(id);
        stage.setName(name);
        stage.setLocation("Kigali");
        stage.setCapacity(capacity);
        return stage;
    }

    private Operator pendingOperator(Stage stage) {
        Operator operator = new Operator();
        operator.setId(UUID.randomUUID());
        operator.setFullName("Jean Claude Ishimwe");
        operator.setPhoneNumber("0788000000");
        operator.setPlateNumber("RAB111A");
        operator.setStage(stage);
        operator.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        return operator;
    }

    private Operator activeOperator(Stage stage) {
        Operator operator = pendingOperator(stage);
        operator.setApprovalStatus(ApprovalStatus.ACTIVE);
        return operator;
    }
}
