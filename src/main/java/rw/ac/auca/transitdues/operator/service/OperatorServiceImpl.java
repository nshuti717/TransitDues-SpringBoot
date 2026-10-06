package rw.ac.auca.transitdues.operator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.exception.OperatorNotFoundException;
import rw.ac.auca.transitdues.exception.StageCapacityExceededException;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperatorServiceImpl implements OperatorService {

    private final OperatorRepository operatorRepository;
    private final StageRepository stageRepository;
    private final AuditLogService auditLogService;

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator createOperator(Operator operator) {
        UUID stageId = operator.getStage().getId();
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found with id: " + stageId));

        int currentOperatorCount = operatorRepository.findByStageId(stageId).size();
        if (currentOperatorCount >= stage.getCapacity()) {
            throw new StageCapacityExceededException(
                    "Stage '" + stage.getName() + "' has reached its capacity of " + stage.getCapacity());
        }

        operator.setStage(stage);
        Operator savedOperator = operatorRepository.save(operator);
        auditLogService.record("Operator", savedOperator.getId().toString(), "CREATE", savedOperator.getFullName());
        return savedOperator;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator updateOperator(UUID id, Operator operator) {
        Operator existingOperator = findOperatorById(id);
        existingOperator.setFullName(operator.getFullName());
        existingOperator.setPhoneNumber(operator.getPhoneNumber());
        existingOperator.setPlateNumber(operator.getPlateNumber());
        existingOperator.setStage(operator.getStage());
        Operator savedOperator = operatorRepository.save(existingOperator);
        auditLogService.record("Operator", savedOperator.getId().toString(), "UPDATE", savedOperator.getFullName());
        return savedOperator;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public void deleteOperator(UUID id) {
        Operator operator = findOperatorById(id);
        operatorRepository.delete(operator);
        auditLogService.record("Operator", operator.getId().toString(), "DELETE", operator.getFullName());
    }

    @Override
    public Operator findOperatorById(UUID id) {
        return operatorRepository.findById(id)
                .orElseThrow(() -> new OperatorNotFoundException("Operator not found with id: " + id));
    }

    @Override
    public List<Operator> findAllOperators() {
        return operatorRepository.findAll();
    }
}
