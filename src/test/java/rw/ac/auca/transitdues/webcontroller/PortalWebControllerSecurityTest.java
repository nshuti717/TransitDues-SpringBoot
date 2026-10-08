package rw.ac.auca.transitdues.webcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /portal/** is OPERATOR-only per SecurityConfig, and every payment action
 * additionally checks that the authenticated user has a linked Operator and
 * owns the due being acted on - these checks are independent of role, so
 * they are tested here rather than relying on the role rule alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PortalWebControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotReachThePortal() throws Exception {
        mockMvc.perform(get("/portal"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "FINANCE_OFFICER")
    void financeOfficerCannotReachThePortal() throws Exception {
        mockMvc.perform(get("/portal"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "no-account@example.com", roles = "OPERATOR")
    void operatorWithNoLinkedProfileCannotPayAnyDue() throws Exception {
        mockMvc.perform(post("/portal/duepayments/" + UUID.randomUUID() + "/pay").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "no-account@example.com", roles = "OPERATOR")
    void operatorWithNoLinkedProfileCannotRequestCashPayment() throws Exception {
        mockMvc.perform(post("/portal/duepayments/" + UUID.randomUUID() + "/request-cash").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/portal"))
                .andExpect(status().is3xxRedirection());
    }
}
