package rw.ac.auca.transitdues.webcontroller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.ac.auca.transitdues.config.AuthenticatedUserResolver;
import rw.ac.auca.transitdues.exception.DuplicateEmailException;
import rw.ac.auca.transitdues.exception.DuplicatePhoneException;
import rw.ac.auca.transitdues.exception.DuplicatePlateException;
import rw.ac.auca.transitdues.exception.InvalidRegistrationException;
import rw.ac.auca.transitdues.exception.OperatorNotEligibleException;
import rw.ac.auca.transitdues.exception.SelfActionNotAllowedException;
import rw.ac.auca.transitdues.exception.StageCapacityExceededException;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.service.OperatorService;
import rw.ac.auca.transitdues.registration.RegistrationService;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.service.StageService;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.beans.PropertyEditorSupport;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/web/operators")
@RequiredArgsConstructor
public class OperatorWebController {

    private final OperatorService operatorService;
    private final StageService stageService;
    private final RegistrationService registrationService;
    private final UserAccountRepository userAccountRepository;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Stage.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.isBlank()) {
                    setValue(null);
                    return;
                }
                setValue(stageService.findStageById(UUID.fromString(text)));
            }

            @Override
            public String getAsText() {
                Stage stage = (Stage) getValue();
                return stage == null ? "" : stage.getId().toString();
            }
        });
    }

    @GetMapping("")
    public String listOperators(Model model) {
        model.addAttribute("operators", operatorService.findActiveListOperators());
        model.addAttribute("pendingCount", operatorService.findPendingApprovalOperators().size());
        return "operators/list";
    }

    @GetMapping("/new")
    public String newOperator(Model model) {
        model.addAttribute("operator", new Operator());
        model.addAttribute("stages", stageService.findAllStages());
        return "operators/form";
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public String createOperator(@Valid @ModelAttribute("operator") Operator operator, BindingResult bindingResult,
                                  @RequestParam(required = false) String operatorEmail,
                                  @RequestParam(required = false) String operatorInitialPassword,
                                  Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("stages", stageService.findAllStages());
            return "operators/form";
        }
        try {
            registrationService.createOperatorWithOptionalLogin(operator, operatorEmail, operatorInitialPassword);
        } catch (StageNotFoundException | StageCapacityExceededException | DuplicateEmailException
                 | DuplicatePhoneException | DuplicatePlateException | InvalidRegistrationException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("stages", stageService.findAllStages());
            return "operators/form";
        }
        return "redirect:/web/operators";
    }

    @GetMapping("/{id}/edit")
    public String editOperator(@PathVariable UUID id, Model model) {
        model.addAttribute("operator", operatorService.findOperatorById(id));
        model.addAttribute("stages", stageService.findAllStages());
        return "operators/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateOperator(@PathVariable UUID id, @Valid @ModelAttribute("operator") Operator operator,
                                  BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("stages", stageService.findAllStages());
            return "operators/form";
        }
        try {
            operatorService.updateOperator(id, operator);
        } catch (StageNotFoundException | StageCapacityExceededException | DuplicatePlateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("stages", stageService.findAllStages());
            return "operators/form";
        }
        return "redirect:/web/operators";
    }

    /** Pending-approval queue: Finance Officers and Admins review self-registered operators here. */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER')")
    public String pendingApprovals(Model model) {
        List<OperatorApprovalView> views = operatorService.findPendingApprovalOperators().stream()
                .map(this::toView)
                .toList();
        model.addAttribute("views", views);
        return "operators/pending";
    }

    /** Full review page for one operator: profile details plus approve/reject/suspend actions. */
    @GetMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER')")
    public String reviewOperator(@PathVariable UUID id, Model model) {
        model.addAttribute("view", toView(operatorService.findOperatorById(id)));
        model.addAttribute("stages", stageService.findAllStages());
        return "operators/review";
    }

    private OperatorApprovalView toView(Operator operator) {
        String email = userAccountRepository.findByOperatorId(operator.getId())
                .map(account -> account.getEmail())
                .orElse(null);
        return new OperatorApprovalView(operator, email);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER')")
    public String approveOperator(@PathVariable UUID id, @RequestParam UUID stageId,
                                   @RequestParam(required = false) String reason,
                                   RedirectAttributes redirectAttributes) {
        try {
            Operator approved = operatorService.approveOperator(id, stageId, reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    approved.getFullName() + " is now ACTIVE at " + approved.getStage().getName() + ".");
        } catch (StageNotFoundException | StageCapacityExceededException | OperatorNotEligibleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/web/operators/" + id + "/review";
        }
        return "redirect:/web/operators/pending";
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER')")
    public String rejectOperator(@PathVariable UUID id, @RequestParam(required = false) String reason,
                                  RedirectAttributes redirectAttributes) {
        try {
            Operator rejected = operatorService.rejectOperator(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", rejected.getFullName() + " has been rejected.");
        } catch (OperatorNotEligibleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/web/operators/pending";
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER')")
    public String suspendOperator(@PathVariable UUID id, @RequestParam(required = false) String reason,
                                   RedirectAttributes redirectAttributes) {
        try {
            Operator suspended = operatorService.suspendOperator(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", suspended.getFullName() + " has been suspended.");
        } catch (OperatorNotEligibleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/web/operators";
    }

    /** Confirmation page shown before an Admin deactivates an operator - see delete-confirm.html. */
    @GetMapping("/{id}/delete-confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDeleteOperator(@PathVariable UUID id, Model model) {
        model.addAttribute("operator", operatorService.findOperatorById(id));
        return "operators/delete-confirm";
    }

    /**
     * Admin-only safe removal. This is a soft delete: the operator flips to
     * DEACTIVATED and its linked login (if any) is disabled, but its row and
     * all due/payment/audit history stay exactly as they were - see
     * OperatorServiceImpl#deactivateOperator. Self-deletion (an Admin
     * deactivating the operator profile linked to their own login) is refused.
     */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteOperator(@PathVariable UUID id, @RequestParam(required = false) String reason,
                                  RedirectAttributes redirectAttributes) {
        String requesterIdentifier = AuthenticatedUserResolver.resolveAuditIdentity(
                SecurityContextHolder.getContext().getAuthentication());
        try {
            Operator deactivated = operatorService.deactivateOperator(id, reason, requesterIdentifier);
            redirectAttributes.addFlashAttribute("successMessage",
                    deactivated.getFullName() + " has been deactivated.");
        } catch (OperatorNotEligibleException | SelfActionNotAllowedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/web/operators";
    }
}
