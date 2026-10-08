package rw.ac.auca.transitdues.webcontroller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.*;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.service.BulkIssueForm;
import rw.ac.auca.transitdues.duepayment.service.BulkIssueResult;
import rw.ac.auca.transitdues.duepayment.service.BulkIssueScope;
import rw.ac.auca.transitdues.duepayment.service.DuePaymentService;
import rw.ac.auca.transitdues.exception.DuplicateDuePaymentException;
import rw.ac.auca.transitdues.exception.OperatorNotFoundException;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.service.OperatorService;
import rw.ac.auca.transitdues.stage.service.StageService;

import java.beans.PropertyEditorSupport;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/web/duepayments")
@RequiredArgsConstructor
public class DuePaymentWebController {

    private final DuePaymentService duePaymentService;
    private final OperatorService operatorService;
    private final StageService stageService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Operator.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.isBlank()) {
                    setValue(null);
                    return;
                }
                setValue(operatorService.findOperatorById(UUID.fromString(text)));
            }

            @Override
            public String getAsText() {
                Operator operator = (Operator) getValue();
                return operator == null ? "" : operator.getId().toString();
            }
        });
    }

    @GetMapping("")
    public String listDuePayments(@RequestParam(required = false) String status, Model model) {
        List<DuePayment> duepayments = duePaymentService.findAllDuePayments();
        if (status != null && !status.isBlank()) {
            duepayments = duepayments.stream()
                    .filter(duepayment -> duepayment.getEffectiveStatus().name().equalsIgnoreCase(status))
                    .toList();
        }
        model.addAttribute("duepayments", duepayments);
        model.addAttribute("statusFilter", status);
        return "duepayments/list";
    }

    @GetMapping("/new")
    public String newDuePayment(Model model) {
        model.addAttribute("duepayment", new DuePayment());
        model.addAttribute("operators", operatorService.findAllOperators());
        return "duepayments/form";
    }

    @PostMapping("")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public String createDuePayment(@Valid @ModelAttribute("duepayment") DuePayment duepayment, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("operators", operatorService.findAllOperators());
            return "duepayments/form";
        }
        try {
            duePaymentService.createDuePayment(duepayment);
        } catch (OperatorNotFoundException | DuplicateDuePaymentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("operators", operatorService.findAllOperators());
            return "duepayments/form";
        }
        return "redirect:/web/duepayments";
    }

    @GetMapping("/bulk-issue")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public String newBulkIssue(Model model) {
        model.addAttribute("bulkIssueForm", new BulkIssueForm());
        model.addAttribute("stages", stageService.findAllStages());
        return "duepayments/bulk-issue";
    }

    @PostMapping("/bulk-issue")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public String bulkIssue(@Valid @ModelAttribute("bulkIssueForm") BulkIssueForm form, BindingResult bindingResult,
                             Model model) {
        if (form.getScope() == BulkIssueScope.STAGE && form.getStageId() == null) {
            bindingResult.rejectValue("stageId", "required", "Choose a stage.");
        }

        if (!bindingResult.hasErrors()) {
            try {
                UUID stageId = form.getScope() == BulkIssueScope.STAGE ? form.getStageId() : null;
                BulkIssueResult result = duePaymentService.bulkIssueDuePayments(form.getType(), form.getAmount(),
                        form.getDueDate(), stageId);
                model.addAttribute("resultMessage", result.summary());
                model.addAttribute("bulkIssueForm", new BulkIssueForm());
                model.addAttribute("stages", stageService.findAllStages());
                return "duepayments/bulk-issue";
            } catch (StageNotFoundException ex) {
                bindingResult.rejectValue("stageId", "notfound", ex.getMessage());
            }
        }

        model.addAttribute("stages", stageService.findAllStages());
        return "duepayments/bulk-issue";
    }

    @GetMapping("/{id}/edit")
    public String editDuePayment(@PathVariable UUID id, Model model) {
        model.addAttribute("duepayment", duePaymentService.findDuePaymentById(id));
        model.addAttribute("operators", operatorService.findAllOperators());
        return "duepayments/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public String updateDuePayment(@PathVariable UUID id, @Valid @ModelAttribute("duepayment") DuePayment duepayment,
                                    BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("operators", operatorService.findAllOperators());
            return "duepayments/form";
        }
        try {
            duePaymentService.updateDuePayment(id, duepayment);
        } catch (OperatorNotFoundException | DuplicateDuePaymentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("operators", operatorService.findAllOperators());
            return "duepayments/form";
        }
        return "redirect:/web/duepayments";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public String deleteDuePayment(@PathVariable UUID id) {
        duePaymentService.deleteDuePayment(id);
        return "redirect:/web/duepayments";
    }
}
