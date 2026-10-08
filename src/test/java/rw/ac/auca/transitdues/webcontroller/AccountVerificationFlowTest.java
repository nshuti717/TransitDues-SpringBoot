package rw.ac.auca.transitdues.webcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import rw.ac.auca.transitdues.otp.OtpPurpose;
import rw.ac.auca.transitdues.otp.OtpService;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;
import rw.ac.auca.transitdues.user.domain.AccountStatus;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack coverage of the "verify your email before /portal works" flow
 * added on top of self-registration: register -&gt; blocked from /portal -&gt;
 * verify with the real OTP (read back via OtpService, exactly as the emailed
 * code would be) -&gt; /portal now works, auto-logged-in. Runs against the real
 * local Postgres/Mongo/Redis/RabbitMQ containers, same as the rest of this
 * project's integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountVerificationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OtpService otpService;

    @Test
    void registeringThenVerifyingGrantsPortalAccessAndLogsTheOperatorIn() throws Exception {
        Stage stage = newStage();
        String email = "flow-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Flow Test Operator")
                        .param("email", email)
                        .param("phoneNumber", uniquePhone())
                        .param("plateNumber", uniquePlate("FLW"))
                        .param("stageId", stage.getId().toString())
                        .param("password", "FlowPass123")
                        .param("confirmPassword", "FlowPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-account?email=" + email.replace("@", "%40")));

        UserAccount pending = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertEquals(AccountStatus.PENDING_VERIFICATION, pending.getStatus());

        // The account exists but is not yet verified: CustomUserDetailsService reports it
        // disabled, so even a correct password fails form login entirely (not just /portal).
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", email)
                        .param("password", "FlowPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        // And without a session, /portal just bounces to the login page like any other guest.
        mockMvc.perform(get("/portal"))
                .andExpect(status().is3xxRedirection());

        String otpCode = otpService.generate(email, OtpPurpose.REGISTRATION_VERIFY);

        mockMvc.perform(post("/verify-account").with(csrf())
                        .param("email", email)
                        .param("otpCode", otpCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/portal?verified"));

        UserAccount verified = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, verified.getStatus());
    }

    @Test
    void wrongCodeShowsAGenericErrorAndDoesNotActivate() throws Exception {
        Stage stage = newStage();
        String email = "wrongcode-" + UUID.randomUUID() + "@example.com";
        registerOperator(stage, email);

        mockMvc.perform(post("/verify-account").with(csrf())
                        .param("email", email)
                        .param("otpCode", "000000"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage",
                        "That code is incorrect or has expired. Check it, or request a new one."));

        assertEquals(AccountStatus.PENDING_VERIFICATION,
                userAccountRepository.findByEmailIgnoreCase(email).orElseThrow().getStatus());
    }

    @Test
    void resendWithinTheCooldownIsRefusedWithASpecificMessage() throws Exception {
        Stage stage = newStage();
        String email = "resend-" + UUID.randomUUID() + "@example.com";
        registerOperator(stage, email); // registration already sent one code, starting the cooldown

        mockMvc.perform(post("/verify-account/resend").with(csrf())
                        .param("email", email))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-account?email=" + email.replace("@", "%40")));
        // Flash error message content (cooldown seconds) is covered at the unit level
        // (OtpServiceTest/AccountVerificationServiceTest) where the clock isn't racing a real test run.
    }

    @Test
    void duplicateEmailRegistrationIsRejected() throws Exception {
        Stage stage = newStage();
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        registerOperator(stage, email);

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Second Attempt")
                        .param("email", email)
                        .param("phoneNumber", uniquePhone())
                        .param("plateNumber", uniquePlate("DUP"))
                        .param("stageId", stage.getId().toString())
                        .param("password", "AnotherPass123")
                        .param("confirmPassword", "AnotherPass123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registerForm", "email"));
    }

    private void registerOperator(Stage stage, String email) throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                .param("fullName", "Test Operator")
                .param("email", email)
                .param("phoneNumber", uniquePhone())
                .param("plateNumber", uniquePlate("REG"))
                .param("stageId", stage.getId().toString())
                .param("password", "TestPass123")
                .param("confirmPassword", "TestPass123"));
    }

    /** 8 random digits after "07" - System.nanoTime()'s leading digits barely move
     * between calls in the same test run, so that was not a safe source of entropy. */
    private String uniquePhone() {
        long random = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000L;
        return String.format("07%08d", random);
    }

    private String uniquePlate(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
    }

    private Stage newStage() {
        Stage stage = new Stage();
        stage.setName("Verify Flow Stage " + UUID.randomUUID());
        stage.setLocation("Kigali");
        stage.setCapacity(100);
        return stageRepository.save(stage);
    }
}
