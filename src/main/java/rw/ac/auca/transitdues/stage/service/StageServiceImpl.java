package rw.ac.auca.transitdues.stage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StageServiceImpl implements StageService {

    private final StageRepository stageRepository;

    @Override
    public Stage createStage(Stage stage) {
        return stageRepository.save(stage);
    }

    @Override
    public Stage updateStage(UUID id, Stage stage) {
        Stage existingStage = findStageById(id);
        existingStage.setName(stage.getName());
        existingStage.setLocation(stage.getLocation());
        existingStage.setCapacity(stage.getCapacity());
        return stageRepository.save(existingStage);
    }

    @Override
    public void deleteStage(UUID id) {
        Stage stage = findStageById(id);
        stageRepository.delete(stage);
    }

    @Override
    public Stage findStageById(UUID id) {
        return stageRepository.findById(id)
                .orElseThrow(() -> new StageNotFoundException("Stage not found with id: " + id));
    }

    @Override
    public List<Stage> findAllStages() {
        return stageRepository.findAll();
    }
}
