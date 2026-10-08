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
 * /web/finance is readable by ADMIN and FINANCE_OFFICER, same as the other
 * finance-adjacent pages, but the cash confirm/reject actions themselves
 * (on DuePaymentWebController) are FINANCE_OFFICER-only, same rule as
 * issuing/editing a due - so an ADMIN or an OPERATOR must both be refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FinanceWebControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanViewCollections() throws Exception {
        mockMvc.perform(get("/web/finance"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "FINANCE_OFFICER")
    void financeOfficerCanViewCollections() throws Exception {
        mockMvc.perform(get("/web/finance"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "operator@example.com", roles = "OPERATOR")
    void operatorCannotViewCollections() throws Exception {
        mockMvc.perform(get("/web/finance"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotConfirmACashPayment() throws Exception {
        mockMvc.perform(post("/web/duepayments/" + UUID.randomUUID() + "/confirm-cash").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operator@example.com", roles = "OPERATOR")
    void operatorCannotConfirmACashPayment() throws Exception {
        mockMvc.perform(post("/web/duepayments/" + UUID.randomUUID() + "/confirm-cash").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
