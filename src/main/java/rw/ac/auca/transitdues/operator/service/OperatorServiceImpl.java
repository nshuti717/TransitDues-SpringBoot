package rw.ac.auca.transitdues.operator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.exception.DuplicatePlateException;
import rw.ac.auca.transitdues.exception.OperatorNotFoundException;
import rw.ac.auca.transitdues.exception.StageCapacityExceededException;
import rw.ac.auca.transitdues.exception.StageNotFoundException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperatorServiceImpl implements OperatorService {

    private static final String DUPLICATE_PLATE_MESSAGE = "This plate number is already registered.";

    private final OperatorRepository operatorRepository;
    private final StageRepository stageRepository;
    private final AuditLogService auditLogService;

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator createOperator(Operator operator, String performedByOverride) {
        UUID stageId = operator.getStage().getId();
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new StageNotFoundException("Stage not found with id: " + stageId));

        int currentOperatorCount = operatorRepository.findByStageId(stageId).size();
        if (currentOperatorCount >= stage.getCapacity()) {
            throw new StageCapacityExceededException(
                    "Stage '" + stage.getName() + "' has reached its capacity of " + stage.getCapacity());
        }

        String normalizedPlate = normalizePlateNumber(operator.getPlateNumber());
        if (operatorRepository.existsByPlateNumberIgnoreCase(normalizedPlate)) {
            throw new DuplicatePlateException(DUPLICATE_PLATE_MESSAGE);
        }

        operator.setPlateNumber(normalizedPlate);
        operator.setStage(stage);
        Operator savedOperator = saveOperator(operator);
        auditLogService.record("Operator", savedOperator.getId().toString(), "CREATE", savedOperator.getFullName(),
                performedByOverride);
        return savedOperator;
    }

    @Override
    @CacheEvict(cacheNames = "dashboardStats", allEntries = true)
    public Operator updateOperator(UUID id, Operator operator) {
        Operator existingOperator = findOperatorById(id);

        String normalizedPlate = normalizePlateNumber(operator.getPlateNumber());
        if (operatorRepository.existsByPlateNumberIgnoreCaseAndIdNot(normalizedPlate, id)) {
            throw new DuplicatePlateException(DUPLICATE_PLATE_MESSAGE);
        }

        existingOperator.setFullName(operator.getFullName());
        existingOperator.setPhoneNumber(operator.getPhoneNumber());
        existingOperator.setPlateNumber(normalizedPlate);
        existingOperator.setStage(operator.getStage());
        Operator savedOperator = saveOperator(existingOperator);
        auditLogService.record("Operator", savedOperator.getId().toString(), "UPDATE", savedOperator.getFullName());
        return savedOperator;
    }

    /**
     * The explicit existsBy... checks above catch almost every duplicate, but a
     * concurrent request can still slip past both checks before either one
     * commits. The database's unique constraint on plate_number is the backstop
     * for that race, and its violation is translated to the same friendly
     * message rather than surfacing a raw SQL error.
     */
    private Operator saveOperator(Operator operator) {
        try {
            return operatorRepository.save(operator);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicatePlateException(DUPLICATE_PLATE_MESSAGE);
        }
    }

    private String normalizePlateNumber(String plateNumber) {
        if (plateNumber == null) {
            return null;
        }
        return plateNumber.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
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
