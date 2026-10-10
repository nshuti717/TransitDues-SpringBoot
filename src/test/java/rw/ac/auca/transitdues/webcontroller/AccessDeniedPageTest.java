package rw.ac.auca.transitdues.webcontroller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccessDeniedPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "noroleuser", authorities = {}, roles = {})
    void noRoleUserSeesNoRoleMessageWithoutDashboardLinkOrSidebar() throws Exception {
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("no role")))
                .andExpect(content().string(containsString("Sign out")))
                .andExpect(content().string(not(containsString("Back to dashboard"))))
                .andExpect(content().string(not(containsString("aria-label=\"Main Navigation\""))));
    }
}
