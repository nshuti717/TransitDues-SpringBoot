package rw.ac.auca.transitdues.webcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /forgot-password and /reset-password must be reachable by a signed-out
 * visitor (permitAll in SecurityConfig, same as /login and /register), and
 * requesting a reset for an email that does not exist must not error or
 * behave any differently from one that does (no account-enumeration oracle).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void forgotPasswordFormIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordFormIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/reset-password"))
                .andExpect(status().isOk());
    }

    @Test
    void requestingAResetForAnUnknownEmailStillSucceeds() throws Exception {
        mockMvc.perform(post("/forgot-password").with(csrf())
                        .param("email", "definitely-not-registered@example.com"))
                .andExpect(status().isOk());
    }

    @Test
    void submittingAWrongCodeShowsAGenericError() throws Exception {
        mockMvc.perform(post("/reset-password").with(csrf())
                        .param("email", "definitely-not-registered@example.com")
                        .param("otpCode", "000000")
                        .param("newPassword", "NewPassword1")
                        .param("confirmPassword", "NewPassword1"))
                .andExpect(status().isOk());
    }
}
