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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack coverage of the login-OTP second factor: a correct password
 * no longer completes authentication by itself - it redirects to
 * /verify-login, and only a correct OTP there actually creates a session.
 * Runs against the real local containers, same as the rest of this
 * project's integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginVerificationFlowTest {

    private static final String PASSWORD = "LoginFlow123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private OtpService otpService;

    @Test
    void correctPasswordRedirectsToVerifyLoginInsteadOfAuthenticatingDirectly() throws Exception {
        String email = activatedOperatorEmail();

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-login?email=" + email.replace("@", "%40")));

        // No session was created by the password check alone: /portal still bounces to login.
        mockMvc.perform(get("/portal"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void correctLoginOtpCompletesAuthenticationAndRedirectsToPortal() throws Exception {
        String email = activatedOperatorEmail();

        mockMvc.perform(post("/login").with(csrf())
                .param("username", email)
                .param("password", PASSWORD));

        String otpCode = otpService.generate(email, OtpPurpose.LOGIN_VERIFY);

        mockMvc.perform(post("/verify-login").with(csrf())
                        .param("email", email)
                        .param("otpCode", otpCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/portal"));
    }

    @Test
    void wrongLoginOtpShowsAGenericErrorAndDoesNotAuthenticate() throws Exception {
        String email = activatedOperatorEmail();

        mockMvc.perform(post("/login").with(csrf())
                .param("username", email)
                .param("password", PASSWORD));

        mockMvc.perform(post("/verify-login").with(csrf())
                        .param("email", email)
                        .param("otpCode", "000000"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage",
                        "That code is incorrect or has expired. Check it, or request a new one."));

        mockMvc.perform(get("/portal"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void wrongPasswordStillFailsNormallyWithoutSendingAnOtp() throws Exception {
        String email = activatedOperatorEmail();

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", email)
                        .param("password", "NotTheRealPassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void verifyLoginPageIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/verify-login"))
                .andExpect(status().isOk());
    }

    @Test
    void loginOtpCannotBeSatisfiedByARegistrationVerifyCode() throws Exception {
        String email = activatedOperatorEmail();
        mockMvc.perform(post("/login").with(csrf())
                .param("username", email)
                .param("password", PASSWORD));

        // A code generated for a different purpose must not verify a login attempt,
        // even though it's for the same email.
        String wrongPurposeCode = otpService.generate(email, OtpPurpose.REGISTRATION_VERIFY);

        mockMvc.perform(post("/verify-login").with(csrf())
                        .param("email", email)
                        .param("otpCode", wrongPurposeCode))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage",
                        "That code is incorrect or has expired. Check it, or request a new one."));
    }

    /** Registers and fully verifies a fresh operator, returning its email. */
    private String activatedOperatorEmail() throws Exception {
        Stage stage = newStage();
        String email = "loginflow-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/register").with(csrf())
                .param("fullName", "Login Flow Operator")
                .param("email", email)
                .param("phoneNumber", uniquePhone())
                .param("plateNumber", uniquePlate("LGN"))
                .param("stageId", stage.getId().toString())
                .param("password", PASSWORD)
                .param("confirmPassword", PASSWORD));

        String registrationCode = otpService.generate(email, OtpPurpose.REGISTRATION_VERIFY);
        mockMvc.perform(post("/verify-account").with(csrf())
                .param("email", email)
                .param("otpCode", registrationCode));

        return email;
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
        stage.setName("Login Flow Stage " + UUID.randomUUID());
        stage.setLocation("Kigali");
        stage.setCapacity(100);
        return stageRepository.save(stage);
    }
}
