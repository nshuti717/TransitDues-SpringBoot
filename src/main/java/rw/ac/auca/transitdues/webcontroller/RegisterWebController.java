package rw.ac.auca.transitdues.webcontroller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import rw.ac.auca.transitdues.exception.DuplicateEmailException;
import rw.ac.auca.transitdues.exception.DuplicatePhoneException;
import rw.ac.auca.transitdues.exception.DuplicatePlateException;
import rw.ac.auca.transitdues.exception.InvalidRegistrationException;
import rw.ac.auca.transitdues.exception.StageCapacityExceededException;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.registration.RegisterForm;
import rw.ac.auca.transitdues.registration.RegistrationService;
import rw.ac.auca.transitdues.registration.StageOption;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class RegisterWebController {

    private static final String KIGALI_FRAGMENT = "Kigali";

    private final RegistrationService registrationService;
    private final StageRepository stageRepository;
    private final OperatorRepository operatorRepository;

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        model.addAttribute("stageOptions", buildStageOptions());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form, BindingResult bindingResult,
                            Model model) {
        if (!bindingResult.hasFieldErrors("confirmPassword") && !bindingResult.hasFieldErrors("password")
                && form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        }

        if (!bindingResult.hasErrors()) {
            try {
                registrationService.registerOperator(form);
            } catch (DuplicateEmailException ex) {
                bindingResult.rejectValue("email", "duplicate", ex.getMessage());
            } catch (DuplicatePhoneException ex) {
                bindingResult.rejectValue("phoneNumber", "duplicate", ex.getMessage());
            } catch (DuplicatePlateException ex) {
                bindingResult.rejectValue("plateNumber", "duplicate", ex.getMessage());
            } catch (StageCapacityExceededException ex) {
                bindingResult.rejectValue("stageId", "full", ex.getMessage());
            } catch (StageNotFoundException ex) {
                bindingResult.rejectValue("stageId", "notfound", ex.getMessage());
            } catch (InvalidRegistrationException ex) {
                bindingResult.rejectValue("confirmPassword", "invalid", ex.getMessage());
            }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("stageOptions", buildStageOptions());
            return "register";
        }

        String encodedEmail = UriUtils.encode(form.getEmail(), StandardCharsets.UTF_8);
        return "redirect:/verify-account?email=" + encodedEmail;
    }

    private List<StageOption> buildStageOptions() {
        List<Stage> kigaliStages = stageRepository.findByLocationContainingIgnoreCase(KIGALI_FRAGMENT);
        return kigaliStages.stream()
                .map(stage -> {
                    int used = operatorRepository.findByStageId(stage.getId()).size();
                    boolean full = used >= stage.getCapacity();
                    String label = stage.getName() + " (" + used + " of " + stage.getCapacity() + " places used)"
                            + (full ? " (full)" : "");
                    return new StageOption(stage.getId(), label, full);
                })
                .toList();
    }
}
