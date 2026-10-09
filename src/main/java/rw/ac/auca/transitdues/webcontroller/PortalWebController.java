package rw.ac.auca.transitdues.webcontroller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.ac.auca.transitdues.audit.PaymentEventLogRepository;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.service.DuePaymentService;
import rw.ac.auca.transitdues.exception.InvalidPaymentStateException;
import rw.ac.auca.transitdues.exception.OperatorNotEligibleException;
import rw.ac.auca.transitdues.operator.domain.ApprovalStatus;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PortalWebController {

    private static final List<String> PAYMENT_EVENT_STATUSES = List.of(
            DuePaymentStatus.PAID.name(), DuePaymentStatus.FAILED.name(), DuePaymentStatus.CASH_PENDING.name());

    private final UserAccountRepository userAccountRepository;
    private final DuePaymentService duePaymentService;
    private final PaymentEventLogRepository paymentEventLogRepository;

    @GetMapping("/portal")
    public String portal(Model model) {
        Operator operator = currentOperator();
        model.addAttribute("operator", operator);
        if (operator != null && operator.getApprovalStatus() == ApprovalStatus.ACTIVE) {
            model.addAttribute("dues", duePaymentService.findDuePaymentsByOperator(operator.getId()));
            model.addAttribute("recentPayments", paymentEventLogRepository
                    .findTop5ByOperatorIdAndStatusInOrderByOccurredAtDesc(operator.getId().toString(),
                            PAYMENT_EVENT_STATUSES));
        }
        return "portal";
    }

    @PostMapping("/portal/duepayments/{id}/pay")
    public String pay(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        Operator operator = requireCurrentOperator();
        try {
            duePaymentService.initiateOnlinePayment(id, operator);
            return "redirect:/portal/duepayments/" + id + "/pay/confirm";
        } catch (InvalidPaymentStateException | OperatorNotEligibleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/portal";
        }
    }

    @GetMapping("/portal/duepayments/{id}/pay/confirm")
    public String confirmPaymentForm(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        Operator operator = requireCurrentOperator();
        DuePayment duePayment = duePaymentService.findDuePaymentById(id);
        if (!duePayment.getOperator().getId().equals(operator.getId())) {
            throw new AccessDeniedException("You cannot act on another operator's due payment.");
        }
        if (duePayment.getStatus() != DuePaymentStatus.SUBMITTED) {
            redirectAttributes.addFlashAttribute("errorMessage", "This due is not awaiting confirmation.");
            return "redirect:/portal";
        }
        model.addAttribute("duepayment", duePayment);
        return "portal-pay-confirm";
    }

    @PostMapping("/portal/duepayments/{id}/pay/confirm")
    public String confirmPayment(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        Operator operator = requireCurrentOperator();
        try {
            DuePayment duePayment = duePaymentService.confirmOnlinePayment(id, operator);
            if (duePayment.getStatus() == DuePaymentStatus.PAID) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Payment confirmed. Reference: " + duePayment.getReference());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "The payment could not be completed. Please try again.");
            }
        } catch (InvalidPaymentStateException | OperatorNotEligibleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/portal";
    }

    @PostMapping("/portal/duepayments/{id}/pay/cancel")
    public String cancelPayment(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        Operator operator = requireCurrentOperator();
        try {
            duePaymentService.cancelOnlinePayment(id, operator);
            redirectAttributes.addFlashAttribute("successMessage", "Payment attempt cancelled.");
        } catch (InvalidPaymentStateException | OperatorNotEligibleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/portal";
    }

    @PostMapping("/portal/duepayments/{id}/request-cash")
    public String requestCash(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        Operator operator = requireCurrentOperator();
        try {
            duePaymentService.requestCashPayment(id, operator);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cash payment requested. A finance officer will confirm it once received.");
        } catch (InvalidPaymentStateException | OperatorNotEligibleException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/portal";
    }

    private Operator currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserAccount account = userAccountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        return account != null ? account.getOperator() : null;
    }

    private Operator requireCurrentOperator() {
        Operator operator = currentOperator();
        if (operator == null) {
            throw new AccessDeniedException("No operator profile is linked to your account.");
        }
        return operator;
    }
}
