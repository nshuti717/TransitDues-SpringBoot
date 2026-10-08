package rw.ac.auca.transitdues.webcontroller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.service.DuePaymentService;
import rw.ac.auca.transitdues.finance.CollectionsService;
import rw.ac.auca.transitdues.stage.service.StageService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Finance collections dashboard: expected/collected/outstanding totals,
 * breakdown by status, and a filterable due list with cash-confirmation
 * actions. Read access matches the rest of the finance-adjacent pages
 * (ADMIN + FINANCE_OFFICER); the cash confirm/reject actions themselves are
 * FINANCE_OFFICER-only, enforced on DuePaymentWebController.
 */
@Controller
@RequestMapping("/web/finance")
@RequiredArgsConstructor
public class FinanceWebController {

    private final DuePaymentService duePaymentService;
    private final StageService stageService;
    private final CollectionsService collectionsService;

    @GetMapping
    public String collections(@RequestParam(required = false) String status,
                               @RequestParam(required = false) UUID stageId,
                               @RequestParam(required = false) String operatorQuery,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                               Model model) {
        List<DuePayment> allDuePayments = duePaymentService.findAllDuePayments();

        model.addAttribute("summary", collectionsService.summarize(allDuePayments));
        model.addAttribute("duepayments",
                collectionsService.filter(allDuePayments, status, stageId, operatorQuery, fromDate, toDate));
        model.addAttribute("stages", stageService.findAllStages());
        model.addAttribute("statusFilter", status);
        model.addAttribute("stageFilter", stageId);
        model.addAttribute("operatorQuery", operatorQuery);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        return "finance";
    }
}
