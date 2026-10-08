package rw.ac.auca.transitdues.webcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This project's test environment has no GOOGLE_CLIENT_ID configured (see
 * .env.example - it's optional), so the login page's Google button must be
 * absent here, not just present-but-broken. If this ever starts failing
 * because a real client ID got configured for local testing, that's a sign
 * this assertion needs an explicit @TestPropertySource instead of relying on
 * the ambient environment.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageHidesTheGoogleButtonWhenNoClientIsConfigured() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Continue with Google"))));
    }
}
