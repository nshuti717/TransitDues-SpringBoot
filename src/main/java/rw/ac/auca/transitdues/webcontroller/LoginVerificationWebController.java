package rw.ac.auca.transitdues.webcontroller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;
import rw.ac.auca.transitdues.config.ProgrammaticAuthenticator;
import rw.ac.auca.transitdues.loginverification.LoginVerificationOutcome;
import rw.ac.auca.transitdues.loginverification.LoginVerificationService;
import rw.ac.auca.transitdues.verification.VerifyAccountForm;

import java.nio.charset.StandardCharsets;

/**
 * The second factor of password login - reached only via
 * LoginOtpRequiredFailureHandler after a correct password, never linked to
 * directly. Reuses VerifyAccountForm (identical email+6-digit-code shape, no
 * need for a near-duplicate class) and the same RoleBasedAuthenticationSuccessHandler
 * every other login path already redirects through, so "where does each role
 * land" is decided in exactly one place.
 */
@Controller
@RequiredArgsConstructor
public class LoginVerificationWebController {

    private final LoginVerificationService loginVerificationService;
    private final ProgrammaticAuthenticator programmaticAuthenticator;
    private final AuthenticationSuccessHandler roleBasedAuthenticationSuccessHandler;

    @GetMapping("/verify-login")
    public String verifyLoginForm(@RequestParam(required = false) String email, Model model) {
        if (!model.containsAttribute("verifyAccountForm")) {
            VerifyAccountForm form = new VerifyAccountForm();
            form.setEmail(email);
            model.addAttribute("verifyAccountForm", form);
        }
        return "verify-login";
    }

    /**
     * Returns String, not void: the error branches render the Thymeleaf view
     * normally (same as every other verify controller in this codebase). Only
     * the success branch hands the response to roleBasedAuthenticationSuccessHandler
     * (which calls response.sendRedirect(...) itself) and returns null -
     * Spring MVC's documented convention for "this method, which also takes an
     * HttpServletResponse parameter, handled the response directly; skip view
     * resolution."
     */
    @PostMapping("/verify-login")
    public String verifyLogin(@Valid @ModelAttribute("verifyAccountForm") VerifyAccountForm form,
                               BindingResult bindingResult, Model model,
                               HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (bindingResult.hasErrors()) {
            return "verify-login";
        }

        LoginVerificationOutcome outcome = loginVerificationService.verify(form.getEmail(), form.getOtpCode());
        if (outcome != LoginVerificationOutcome.SUCCESS) {
            model.addAttribute("errorMessage", outcome.userMessage());
            return "verify-login";
        }

        programmaticAuthenticator.signIn(form.getEmail(), request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        roleBasedAuthenticationSuccessHandler.onAuthenticationSuccess(request, response, authentication);
        return null;
    }

    @PostMapping("/verify-login/resend")
    public String resend(@RequestParam String email, RedirectAttributes redirectAttributes) {
        long secondsRemaining = loginVerificationService.resend(email);
        if (secondsRemaining > 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please wait " + secondsRemaining + " seconds before requesting another code.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "A new code has been sent to your email.");
        }
        String encodedEmail = UriUtils.encode(email, StandardCharsets.UTF_8);
        return "redirect:/verify-login?email=" + encodedEmail;
    }
}
