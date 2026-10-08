package rw.ac.auca.transitdues.duepayment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.domain.PaymentMethod;
import rw.ac.auca.transitdues.duepayment.domain.PaymentType;
import rw.ac.auca.transitdues.duepayment.repository.DuePaymentRepository;
import rw.ac.auca.transitdues.exception.DuplicateDuePaymentException;
import rw.ac.auca.transitdues.exception.InvalidPaymentStateException;
import rw.ac.auca.transitdues.messaging.DuePaymentEventPublisher;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.service.StageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuePaymentServiceImplTest {

    @Mock
    private DuePaymentRepository duePaymentRepository;

    @Mock
    private OperatorRepository operatorRepository;

    @Mock
    private StageService stageService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private DuePaymentEventPublisher duePaymentEventPublisher;

    @InjectMocks
    private DuePaymentServiceImpl duePaymentService;

    @Test
    void duplicateDueIsRejected() {
        UUID operatorId = UUID.randomUUID();
        Operator operator = operator(operatorId, "Jean Claude Ishimwe");
        LocalDate dueDate = LocalDate.of(2026, 3, 1);

        DuePayment newDue = new DuePayment();
        newDue.setOperator(operator);
        newDue.setType(PaymentType.DAILY);
        newDue.setAmount(new BigDecimal("1000"));
        newDue.setDueDate(dueDate);

        when(operatorRepository.findById(operatorId)).thenReturn(Optional.of(operator));
        when(duePaymentRepository.existsByOperatorIdAndTypeAndDueDate(operatorId, PaymentType.DAILY, dueDate))
                .thenReturn(true);

        DuplicateDuePaymentException ex = assertThrows(DuplicateDuePaymentException.class,
                () -> duePaymentService.createDuePayment(newDue));

        assertEquals("This due is already issued for that operator and date.", ex.getMessage());
        verify(duePaymentRepository, never()).save(any(DuePayment.class));
    }

    @Test
    void bulkIssueSkipsExistingDuesAndCountsCorrectly() {
        UUID stageId = UUID.randomUUID();
        Stage stage = new Stage();
        stage.setId(stageId);
        stage.setName("Nyabugogo");
        stage.setLocation("Gasabo, Kigali");
        stage.setCapacity(10);

        Operator operatorOne = operator(UUID.randomUUID(), "Operator One");
        Operator operatorTwo = operator(UUID.randomUUID(), "Operator Two");
        Operator operatorThree = operator(UUID.randomUUID(), "Operator Three");
        LocalDate dueDate = LocalDate.of(2026, 3, 1);
        BigDecimal amount = new BigDecimal("1000");

        when(stageService.findStageById(stageId)).thenReturn(stage);
        when(operatorRepository.findByStageId(stageId))
                .thenReturn(List.of(operatorOne, operatorTwo, operatorThree));

        when(duePaymentRepository.existsByOperatorIdAndTypeAndDueDate(operatorOne.getId(), PaymentType.DAILY, dueDate))
                .thenReturn(false);
        when(duePaymentRepository.existsByOperatorIdAndTypeAndDueDate(operatorTwo.getId(), PaymentType.DAILY, dueDate))
                .thenReturn(true);
        when(duePaymentRepository.existsByOperatorIdAndTypeAndDueDate(operatorThree.getId(), PaymentType.DAILY, dueDate))
                .thenReturn(false);

        when(duePaymentRepository.save(any(DuePayment.class))).thenAnswer(invocation -> {
            DuePayment duePayment = invocation.getArgument(0);
            duePayment.setId(UUID.randomUUID());
            return duePayment;
        });

        BulkIssueResult result = duePaymentService.bulkIssueDuePayments(PaymentType.DAILY, amount, dueDate, stageId);

        assertEquals(2, result.issuedCount());
        assertEquals(1, result.skippedCount());
        verify(duePaymentRepository, times(2)).save(any(DuePayment.class));
        verify(auditLogService, times(2)).record(eq("DuePayment"), anyString(), eq("CREATE"), anyString());
        verify(duePaymentEventPublisher, times(2)).publish(any());
    }

    @Test
    void initiateOnlinePaymentMovesPendingDueToSubmitted() {
        Operator operator = operator(UUID.randomUUID(), "Jean Claude Ishimwe");
        DuePayment due = payableDue(operator, DuePaymentStatus.PENDING);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));
        when(duePaymentRepository.save(any(DuePayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DuePayment result = duePaymentService.initiateOnlinePayment(due.getId(), operator);

        assertEquals(DuePaymentStatus.SUBMITTED, result.getStatus());
        assertEquals(PaymentMethod.ONLINE, result.getPaymentMethod());
        verify(duePaymentEventPublisher).publish(any());
    }

    @Test
    void initiateOnlinePaymentRejectsAnotherOperatorsDue() {
        Operator owner = operator(UUID.randomUUID(), "Owner Operator");
        Operator intruder = operator(UUID.randomUUID(), "Intruder Operator");
        DuePayment due = payableDue(owner, DuePaymentStatus.PENDING);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));

        assertThrows(AccessDeniedException.class,
                () -> duePaymentService.initiateOnlinePayment(due.getId(), intruder));
        verify(duePaymentRepository, never()).save(any(DuePayment.class));
    }

    @Test
    void initiateOnlinePaymentRejectsAnAlreadyPaidDue() {
        Operator operator = operator(UUID.randomUUID(), "Jean Claude Ishimwe");
        DuePayment due = payableDue(operator, DuePaymentStatus.PAID);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));

        assertThrows(InvalidPaymentStateException.class,
                () -> duePaymentService.initiateOnlinePayment(due.getId(), operator));
        verify(duePaymentRepository, never()).save(any(DuePayment.class));
    }

    @Test
    void confirmOnlinePaymentSucceedsWhenGatewayDoesNotFail() {
        Operator operator = operator(UUID.randomUUID(), "Jean Claude Ishimwe");
        DuePayment due = payableDue(operator, DuePaymentStatus.SUBMITTED);
        ReflectionTestUtils.setField(duePaymentService, "simulatedFailureRate", 0.0);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));
        when(duePaymentRepository.save(any(DuePayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DuePayment result = duePaymentService.confirmOnlinePayment(due.getId(), operator);

        assertEquals(DuePaymentStatus.PAID, result.getStatus());
        assertNotNull(result.getReference());
        assertNotNull(result.getPaidAt());
    }

    @Test
    void confirmOnlinePaymentFailsWhenGatewayIsForcedToFail() {
        Operator operator = operator(UUID.randomUUID(), "Jean Claude Ishimwe");
        DuePayment due = payableDue(operator, DuePaymentStatus.SUBMITTED);
        ReflectionTestUtils.setField(duePaymentService, "simulatedFailureRate", 1.0);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));
        when(duePaymentRepository.save(any(DuePayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DuePayment result = duePaymentService.confirmOnlinePayment(due.getId(), operator);

        assertEquals(DuePaymentStatus.FAILED, result.getStatus());
        assertNull(result.getReference());
    }

    @Test
    void confirmOnlinePaymentRejectsADueThatWasNeverSubmitted() {
        Operator operator = operator(UUID.randomUUID(), "Jean Claude Ishimwe");
        DuePayment due = payableDue(operator, DuePaymentStatus.PENDING);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));

        assertThrows(InvalidPaymentStateException.class,
                () -> duePaymentService.confirmOnlinePayment(due.getId(), operator));
    }

    @Test
    void cancelOnlinePaymentRevertsSubmittedDueToPending() {
        Operator operator = operator(UUID.randomUUID(), "Jean Claude Ishimwe");
        DuePayment due = payableDue(operator, DuePaymentStatus.SUBMITTED);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));
        when(duePaymentRepository.save(any(DuePayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DuePayment result = duePaymentService.cancelOnlinePayment(due.getId(), operator);

        assertEquals(DuePaymentStatus.PENDING, result.getStatus());
        assertNull(result.getPaymentMethod());
    }

    @Test
    void requestCashPaymentMovesPendingDueToCashPending() {
        Operator operator = operator(UUID.randomUUID(), "Jean Claude Ishimwe");
        DuePayment due = payableDue(operator, DuePaymentStatus.PENDING);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));
        when(duePaymentRepository.save(any(DuePayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DuePayment result = duePaymentService.requestCashPayment(due.getId(), operator);

        assertEquals(DuePaymentStatus.CASH_PENDING, result.getStatus());
        assertEquals(PaymentMethod.CASH, result.getPaymentMethod());
    }

    @Test
    void requestCashPaymentRejectsAnotherOperatorsDue() {
        Operator owner = operator(UUID.randomUUID(), "Owner Operator");
        Operator intruder = operator(UUID.randomUUID(), "Intruder Operator");
        DuePayment due = payableDue(owner, DuePaymentStatus.PENDING);
        when(duePaymentRepository.findById(due.getId())).thenReturn(Optional.of(due));

        assertThrows(AccessDeniedException.class,
                () -> duePaymentService.requestCashPayment(due.getId(), intruder));
    }

    private DuePayment payableDue(Operator operator, DuePaymentStatus status) {
        DuePayment due = new DuePayment();
        due.setId(UUID.randomUUID());
        due.setOperator(operator);
        due.setType(PaymentType.DAILY);
        due.setAmount(new BigDecimal("1000"));
        due.setDueDate(LocalDate.now().plusDays(1));
        due.setStatus(status);
        return due;
    }

    private Operator operator(UUID id, String fullName) {
        Operator operator = new Operator();
        operator.setId(id);
        operator.setFullName(fullName);
        operator.setPhoneNumber("0788000000");
        operator.setPlateNumber("RAB" + id.toString().substring(0, 4).toUpperCase());
        return operator;
    }
}
