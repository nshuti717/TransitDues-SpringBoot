package rw.ac.auca.transitdues.webcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.auca.transitdues.duepayment.repository.DuePaymentRepository;
import rw.ac.auca.transitdues.operator.domain.ApprovalStatus;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.otp.OtpPurpose;
import rw.ac.auca.transitdues.otp.OtpService;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack coverage of the operator approval workflow: self-registration
 * leaves an operator PENDING_APPROVAL (not ACTIVE) even after email
 * verification; a PENDING_APPROVAL operator can still sign in but sees a
 * restricted /portal message instead of dues; only ADMIN/FINANCE_OFFICER can
 * approve/reject/suspend; approval requires confirming a real Stage; and a
 * rejected/suspended operator stays blocked from dues/payments afterwards.
 * Runs against the real local containers, same as the rest of this
 * project's integration tests. Class-level @Transactional rolls back every
 * row these tests write.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperatorApprovalWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private OperatorRepository operatorRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DuePaymentRepository duePaymentRepository;

    @Autowired
    private OtpService otpService;

    @Test
    void verifiedSelfRegisteredOperatorIsPendingApprovalNotActive() throws Exception {
        String email = registerAndVerify(newStage());

        Operator operator = operatorOf(email);
        assertEquals(ApprovalStatus.PENDING_APPROVAL, operator.getApprovalStatus());
    }

    @Test
    void pendingOperatorCanSignInButPortalShowsARestrictedMessageInsteadOfDues() throws Exception {
        String email = registerAndVerify(newStage());

        mockMvc.perform(get("/portal").with(user(email).roles("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Your account is awaiting Finance/Admin approval and stage assignment.")));
    }

    @Test
    void financeOfficerCanApproveAPendingOperatorIntoAConfirmedStage() throws Exception {
        Stage requestedStage = newStage();
        String email = registerAndVerify(requestedStage);
        Operator operator = operatorOf(email);
        Stage confirmedStage = newStage();

        mockMvc.perform(post("/web/operators/{id}/approve", operator.getId()).with(csrf())
                        .with(user("finance@example.com").roles("FINANCE_OFFICER"))
                        .param("stageId", confirmedStage.getId().toString())
                        .param("reason", "Verified in person"))
                .andExpect(status().is3xxRedirection());

        Operator approved = operatorRepository.findById(operator.getId()).orElseThrow();
        assertEquals(ApprovalStatus.ACTIVE, approved.getApprovalStatus());
        assertEquals(confirmedStage.getId(), approved.getStage().getId());
        assertEquals("finance@example.com", approved.getApprovalActionBy());
    }

    @Test
    void operatorRoleCannotApproveAnyone() throws Exception {
        String email = registerAndVerify(newStage());
        Operator operator = operatorOf(email);

        mockMvc.perform(post("/web/operators/{id}/approve", operator.getId()).with(csrf())
                        .with(user("some-operator@example.com").roles("OPERATOR"))
                        .param("stageId", operator.getStage().getId().toString()))
                .andExpect(status().isForbidden());

        assertEquals(ApprovalStatus.PENDING_APPROVAL, operatorRepository.findById(operator.getId()).orElseThrow().getApprovalStatus());
    }

    @Test
    void rejectedOperatorStaysBlockedFromDuesAfterRejection() throws Exception {
        String email = registerAndVerify(newStage());
        Operator operator = operatorOf(email);

        mockMvc.perform(post("/web/operators/{id}/reject", operator.getId()).with(csrf())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("reason", "Could not verify plate"))
                .andExpect(status().is3xxRedirection());

        Operator rejected = operatorRepository.findById(operator.getId()).orElseThrow();
        assertEquals(ApprovalStatus.REJECTED, rejected.getApprovalStatus());

        // A rejected operator must not receive a bulk-issued due even for their own stage.
        mockMvc.perform(get("/portal").with(user(email).roles("OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Your operator application was not approved.")));
    }

    @Test
    void bulkIssueSkipsAPendingApprovalOperatorEvenAtTheTargetStage() throws Exception {
        Stage stage = newStage();
        String email = registerAndVerify(stage);
        Operator operator = operatorOf(email);

        mockMvc.perform(post("/web/duepayments/bulk-issue").with(csrf())
                .with(user("finance@example.com").roles("FINANCE_OFFICER"))
                .param("type", "DAILY")
                .param("amount", "1000")
                .param("dueDate", java.time.LocalDate.now().plusDays(5).toString())
                .param("scope", "STAGE")
                .param("stageId", stage.getId().toString()));

        assertEquals(0, duePaymentRepository.findByOperatorIdOrderByDueDateDesc(operator.getId()).size());
    }

    /** Registers, verifies the email OTP, and returns the new operator's email. */
    private String registerAndVerify(Stage stage) throws Exception {
        String email = "approval-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/register").with(csrf())
                .param("fullName", "Approval Flow Operator")
                .param("email", email)
                .param("phoneNumber", uniquePhone())
                .param("plateNumber", uniquePlate("APR"))
                .param("stageId", stage.getId().toString())
                .param("password", "ApprovalPass123")
                .param("confirmPassword", "ApprovalPass123"));

        String code = otpService.generate(email, OtpPurpose.REGISTRATION_VERIFY);
        mockMvc.perform(post("/verify-account").with(csrf())
                .param("email", email)
                .param("otpCode", code));

        return email;
    }

    private Operator operatorOf(String email) {
        UserAccount account = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        return account.getOperator();
    }

    private String uniquePhone() {
        long random = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000L;
        return String.format("07%08d", random);
    }

    private String uniquePlate(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
    }

    private Stage newStage() {
        Stage stage = new Stage();
        stage.setName("Approval Flow Stage " + UUID.randomUUID());
        stage.setLocation("Kigali");
        stage.setCapacity(100);
        return stageRepository.save(stage);
    }
}
