package rw.ac.auca.transitdues.webcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADMIN may view the due payments list but may not issue dues: the web
 * controller's issue/bulk-issue actions are @PreAuthorize("hasRole('FINANCE_OFFICER')"),
 * so an ADMIN submitting them must be refused regardless of what is in the
 * request body.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DuePaymentWebControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanViewTheDuePaymentsList() throws Exception {
        mockMvc.perform(get("/web/duepayments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotIssueADue() throws Exception {
        mockMvc.perform(post("/web/duepayments").with(csrf())
                        .param("type", "DAILY")
                        .param("amount", "1000")
                        .param("dueDate", "2026-03-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotBulkIssueDues() throws Exception {
        mockMvc.perform(post("/web/duepayments/bulk-issue").with(csrf())
                        .param("type", "DAILY")
                        .param("amount", "1000")
                        .param("dueDate", "2026-03-01")
                        .param("scope", "ALL"))
                .andExpect(status().isForbidden());
    }
}
