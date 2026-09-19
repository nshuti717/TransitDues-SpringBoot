package rw.ac.auca.transitdues.stage.service;

import rw.ac.auca.transitdues.stage.domain.Stage;

import java.util.List;
import java.util.UUID;

public interface StageService {

    Stage createStage(Stage stage);

    Stage updateStage(UUID id, Stage stage);

    void deleteStage(UUID id);

    Stage findStageById(UUID id);

    List<Stage> findAllStages();
}
