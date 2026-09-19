package rw.ac.auca.transitdues.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.service.StageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stages")
@RequiredArgsConstructor
public class StageController {

    private final StageService stageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Stage createStage(@Valid @RequestBody Stage stage) {
        return stageService.createStage(stage);
    }

    @GetMapping
    public List<Stage> getAllStages() {
        return stageService.findAllStages();
    }

    @GetMapping("/{id}")
    public Stage getStageById(@PathVariable UUID id) {
        return stageService.findStageById(id);
    }

    @PutMapping("/{id}")
    public Stage updateStage(@PathVariable UUID id, @Valid @RequestBody Stage stage) {
        return stageService.updateStage(id, stage);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStage(@PathVariable UUID id) {
        stageService.deleteStage(id);
    }
}
