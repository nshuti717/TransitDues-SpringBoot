package rw.ac.auca.transitdues.webcontroller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.service.StageService;

import java.util.UUID;

@Controller
@RequestMapping("/web/stages")
@RequiredArgsConstructor
public class StageWebController {

    private final StageService stageService;

    @GetMapping("")
    public String listStages(Model model) {
        model.addAttribute("stages", stageService.findAllStages());
        return "stages/list";
    }

    @GetMapping("/new")
    public String newStage(Model model) {
        model.addAttribute("stage", new Stage());
        return "stages/form";
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public String createStage(@Valid @ModelAttribute("stage") Stage stage, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "stages/form";
        }
        stageService.createStage(stage);
        return "redirect:/web/stages";
    }

    @GetMapping("/{id}/edit")
    public String editStage(@PathVariable UUID id, Model model) {
        model.addAttribute("stage", stageService.findStageById(id));
        return "stages/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateStage(@PathVariable UUID id, @Valid @ModelAttribute("stage") Stage stage,
                               BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "stages/form";
        }
        stageService.updateStage(id, stage);
        return "redirect:/web/stages";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteStage(@PathVariable UUID id) {
        stageService.deleteStage(id);
        return "redirect:/web/stages";
    }
}
