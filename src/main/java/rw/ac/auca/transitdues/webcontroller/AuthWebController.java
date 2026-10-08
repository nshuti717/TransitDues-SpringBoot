package rw.ac.auca.transitdues.webcontroller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AuthWebController {

    /**
     * Blank (the default when GOOGLE_CLIENT_ID is unset) means Spring Boot's
     * OAuth2 autoconfiguration never registered a "google" client at all -
     * /oauth2/authorization/google would 404. Rather than let that happen,
     * the login page hides the button entirely when this is blank, so the app
     * starts and runs normally with no Google credentials configured.
     */
    @Value("${app.oauth2.google-client-id:}")
    private String googleClientId;

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("googleLoginEnabled", !googleClientId.isBlank());
        return "login";
    }

    /**
     * No HTTP method restriction: AccessDeniedHandler forwards here via
     * RequestDispatcher.forward(), which preserves the original request's method
     * (e.g. a denied POST), so this must accept any method, not just GET.
     */
    @RequestMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}
