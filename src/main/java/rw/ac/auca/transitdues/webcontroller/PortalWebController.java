package rw.ac.auca.transitdues.webcontroller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.user.domain.UserAccount;
import rw.ac.auca.transitdues.user.repository.UserAccountRepository;

@Controller
@RequiredArgsConstructor
public class PortalWebController {

    private final UserAccountRepository userAccountRepository;

    @GetMapping("/portal")
    public String portal(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserAccount account = userAccountRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        Operator operator = account != null ? account.getOperator() : null;
        model.addAttribute("operator", operator);
        return "portal";
    }
}
