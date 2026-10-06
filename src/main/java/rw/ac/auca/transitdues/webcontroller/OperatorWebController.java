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
import rw.ac.auca.transitdues.exception.StageCapacityExceededException;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.service.OperatorService;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.service.StageService;

import java.beans.PropertyEditorSupport;
import java.util.UUID;

@Controller
@RequestMapping("/web/operators")
@RequiredArgsConstructor
public class OperatorWebController {

    private final OperatorService operatorService;
    private final StageService stageService;

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
        model.addAttribute("operators", operatorService.findAllOperators());
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
    public String createOperator(@Valid @ModelAttribute("operator") Operator operator, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("stages", stageService.findAllStages());
            return "operators/form";
        }
        try {
            operatorService.createOperator(operator);
        } catch (StageNotFoundException | StageCapacityExceededException ex) {
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
        } catch (StageNotFoundException | StageCapacityExceededException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("stages", stageService.findAllStages());
            return "operators/form";
        }
        return "redirect:/web/operators";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteOperator(@PathVariable UUID id) {
        operatorService.deleteOperator(id);
        return "redirect:/web/operators";
    }
}
