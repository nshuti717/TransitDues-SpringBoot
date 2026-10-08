package rw.ac.auca.transitdues.webcontroller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;
import rw.ac.auca.transitdues.user.service.CustomUserDetailsService;
import rw.ac.auca.transitdues.verification.AccountVerificationOutcome;
import rw.ac.auca.transitdues.verification.AccountVerificationService;
import rw.ac.auca.transitdues.verification.VerifyAccountForm;

import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class AccountVerificationWebController {

    private final AccountVerificationService accountVerificationService;
    private final CustomUserDetailsService customUserDetailsService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping("/verify-account")
    public String verifyAccountForm(@RequestParam(required = false) String email, Model model) {
        if (!model.containsAttribute("verifyAccountForm")) {
            VerifyAccountForm form = new VerifyAccountForm();
            form.setEmail(email);
            model.addAttribute("verifyAccountForm", form);
        }
        return "verify-account";
    }

    @PostMapping("/verify-account")
    public String verifyAccount(@Valid @ModelAttribute("verifyAccountForm") VerifyAccountForm form,
                                 BindingResult bindingResult, Model model,
                                 HttpServletRequest request, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return "verify-account";
        }

        AccountVerificationOutcome outcome = accountVerificationService.verify(form.getEmail(), form.getOtpCode());
        if (outcome != AccountVerificationOutcome.SUCCESS) {
            model.addAttribute("errorMessage", outcome.userMessage());
            return "verify-account";
        }

        signIn(form.getEmail(), request, response);
        return "redirect:/portal?verified";
    }

    @PostMapping("/verify-account/resend")
    public String resend(@RequestParam String email, RedirectAttributes redirectAttributes) {
        long secondsRemaining = accountVerificationService.resend(email);
        if (secondsRemaining > 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please wait " + secondsRemaining + " seconds before requesting another code.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "A new code has been sent to your email.");
        }
        String encodedEmail = UriUtils.encode(email, StandardCharsets.UTF_8);
        return "redirect:/verify-account?email=" + encodedEmail;
    }

    /**
     * Logs the operator in right after verification, the same way a normal
     * form login would: load their UserDetails, put them on the
     * SecurityContext, and persist it to the session - the standard
     * Spring-Security-recommended way to authenticate a user programmatically
     * outside the login form itself.
     */
    private void signIn(String email, HttpServletRequest request, HttpServletResponse response) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
