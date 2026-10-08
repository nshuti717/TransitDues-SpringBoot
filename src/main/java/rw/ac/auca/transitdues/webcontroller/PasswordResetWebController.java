package rw.ac.auca.transitdues.webcontroller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.ac.auca.transitdues.passwordreset.ForgotPasswordForm;
import rw.ac.auca.transitdues.passwordreset.PasswordResetOutcome;
import rw.ac.auca.transitdues.passwordreset.PasswordResetService;
import rw.ac.auca.transitdues.passwordreset.ResetPasswordForm;

@Controller
@RequiredArgsConstructor
public class PasswordResetWebController {

    private final PasswordResetService passwordResetService;

    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        }
        model.addAttribute("submitted", false);
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                                BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "forgot-password";
        }
        passwordResetService.requestReset(form.getEmail());
        model.addAttribute("submitted", true);
        model.addAttribute("submittedEmail", form.getEmail());
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String email, Model model) {
        if (!model.containsAttribute("resetPasswordForm")) {
            ResetPasswordForm form = new ResetPasswordForm();
            form.setEmail(email);
            model.addAttribute("resetPasswordForm", form);
        }
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                                 BindingResult bindingResult, Model model) {
        if (!bindingResult.hasFieldErrors("confirmPassword")
                && form.getNewPassword() != null && !form.getNewPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        }
        if (bindingResult.hasErrors()) {
            return "reset-password";
        }

        PasswordResetOutcome outcome = passwordResetService.resetPassword(form.getEmail(), form.getOtpCode(),
                form.getNewPassword());
        if (outcome == PasswordResetOutcome.SUCCESS) {
            return "redirect:/login?reset";
        }

        model.addAttribute("errorMessage", outcome.userMessage());
        return "reset-password";
    }
}
