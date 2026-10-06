package rw.ac.auca.transitdues.webcontroller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AuthWebController {

    @GetMapping("/login")
    public String login() {
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
