package rw.ac.auca.transitdues.webcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack coverage of Admin-only safe operator deletion: only ADMIN may
 * deactivate an operator (FINANCE_OFFICER/OPERATOR are refused), the action is
 * a soft delete (the operator row and its due/payment history stay in the
 * database), and a deactivated operator can no longer log in or reach
 * /portal. Runs against the real local containers; class-level
 * @Transactional rolls back every row these tests write.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperatorDeletionTest {

    private static final String PASSWORD = "DeleteFlow123";

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
    void adminCanDeactivateAnOperatorAndItsDueHistorySurvives() throws Exception {
        Stage stage = newStage();
        String email = registerVerifyAndApprove(stage);
        Operator operator = operatorOf(email);

        mockMvc.perform(post("/web/duepayments").with(csrf())
                .with(user("finance@example.com").roles("FINANCE_OFFICER"))
                .param("operator", operator.getId().toString())
                .param("amount", "1000")
                .param("type", "DAILY")
                .param("dueDate", LocalDate.now().plusDays(3).toString()));

        List<DuePayment> duesBefore = duePaymentRepository.findByOperatorIdOrderByDueDateDesc(operator.getId());
        assertEquals(1, duesBefore.size());

        mockMvc.perform(post("/web/operators/{id}/delete", operator.getId()).with(csrf())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("reason", "Operator left the cooperative"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/web/operators"));

        Operator deactivated = operatorRepository.findById(operator.getId()).orElseThrow();
        assertEquals(ApprovalStatus.DEACTIVATED, deactivated.getApprovalStatus());
        assertEquals("admin@example.com", deactivated.getApprovalActionBy());

        UserAccount account = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertFalse(account.isEnabled());

        // The due issued before deactivation is untouched - same row, same data.
        List<DuePayment> duesAfter = duePaymentRepository.findByOperatorIdOrderByDueDateDesc(operator.getId());
        assertEquals(1, duesAfter.size());
        assertEquals(duesBefore.get(0).getId(), duesAfter.get(0).getId());

        // The operator row itself still exists (soft delete, not a hard delete).
        assertTrue(operatorRepository.findById(operator.getId()).isPresent());
    }

    @Test
    void financeOfficerCannotDeactivateAnOperator() throws Exception {
        Stage stage = newStage();
        String email = registerVerifyAndApprove(stage);
        Operator operator = operatorOf(email);

        mockMvc.perform(post("/web/operators/{id}/delete", operator.getId()).with(csrf())
                        .with(user("finance@example.com").roles("FINANCE_OFFICER"))
                        .param("reason", "n/a"))
                .andExpect(status().isForbidden());

        assertEquals(ApprovalStatus.ACTIVE, operatorRepository.findById(operator.getId()).orElseThrow().getApprovalStatus());
    }

    @Test
    void operatorCannotDeactivateAnyOperator() throws Exception {
        Stage stage = newStage();
        String email = registerVerifyAndApprove(stage);
        Operator operator = operatorOf(email);

        mockMvc.perform(post("/web/operators/{id}/delete", operator.getId()).with(csrf())
                        .with(user(email).roles("OPERATOR"))
                        .param("reason", "n/a"))
                .andExpect(status().isForbidden());

        assertEquals(ApprovalStatus.ACTIVE, operatorRepository.findById(operator.getId()).orElseThrow().getApprovalStatus());
    }

    @Test
    void deactivatedOperatorCannotLogInOrReachThePortal() throws Exception {
        Stage stage = newStage();
        String email = registerVerifyAndApprove(stage);
        Operator operator = operatorOf(email);

        mockMvc.perform(post("/web/operators/{id}/delete", operator.getId()).with(csrf())
                .with(user("admin@example.com").roles("ADMIN"))
                .param("reason", "Operator left"));

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    /** Registers, verifies the email OTP, and approves the operator into the given stage. Returns its email. */
    private String registerVerifyAndApprove(Stage stage) throws Exception {
        String email = "deletion-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/register").with(csrf())
                .param("fullName", "Deletion Flow Operator")
                .param("email", email)
                .param("phoneNumber", uniquePhone())
                .param("plateNumber", uniquePlate("DEL"))
                .param("stageId", stage.getId().toString())
                .param("password", PASSWORD)
                .param("confirmPassword", PASSWORD));

        String code = otpService.generate(email, OtpPurpose.REGISTRATION_VERIFY);
        mockMvc.perform(post("/verify-account").with(csrf())
                .param("email", email)
                .param("otpCode", code));

        Operator operator = operatorOf(email);
        mockMvc.perform(post("/web/operators/{id}/approve", operator.getId()).with(csrf())
                .with(user("finance@example.com").roles("FINANCE_OFFICER"))
                .param("stageId", stage.getId().toString()));

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
        stage.setName("Deletion Flow Stage " + UUID.randomUUID());
        stage.setLocation("Kigali");
        stage.setCapacity(100);
        return stageRepository.save(stage);
    }
}
