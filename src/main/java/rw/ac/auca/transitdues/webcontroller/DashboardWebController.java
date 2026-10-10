package rw.ac.auca.transitdues.webcontroller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import rw.ac.auca.transitdues.dashboard.DashboardStats;
import rw.ac.auca.transitdues.dashboard.DashboardStatsService;

@Controller
@RequiredArgsConstructor
public class DashboardWebController {

    private final DashboardStatsService dashboardStatsService;

    @GetMapping({"/", "/web/dashboard"})
    public String dashboard(Model model) {
        DashboardStats stats = dashboardStatsService.getStats();

        model.addAttribute("totalStages", stats.totalStages());
        model.addAttribute("totalOperators", stats.totalOperators());
        model.addAttribute("totalDuePayments", stats.totalDuePayments());
        model.addAttribute("totalAmountCollected", stats.totalAmountCollected());
        model.addAttribute("paymentCountsByStatus", stats.paymentCountsByStatus());
        return "dashboard";
    }
}
