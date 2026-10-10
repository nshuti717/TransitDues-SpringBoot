package rw.ac.auca.transitdues.stage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StageServiceImpl implements StageService {

    private final StageRepository stageRepository;
    private final AuditLogService auditLogService;

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Stage createStage(Stage stage) {
        Stage savedStage = stageRepository.save(stage);
        auditLogService.record("Stage", savedStage.getId().toString(), "CREATE", savedStage.getName());
        return savedStage;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Stage updateStage(UUID id, Stage stage) {
        Stage existingStage = findStageById(id);
        existingStage.setName(stage.getName());
        existingStage.setLocation(stage.getLocation());
        existingStage.setCapacity(stage.getCapacity());
        Stage savedStage = stageRepository.save(existingStage);
        auditLogService.record("Stage", savedStage.getId().toString(), "UPDATE", savedStage.getName());
        return savedStage;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public void deleteStage(UUID id) {
        Stage stage = findStageById(id);
        stageRepository.delete(stage);
        auditLogService.record("Stage", stage.getId().toString(), "DELETE", stage.getName());
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
