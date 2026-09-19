package rw.ac.auca.transitdues.operator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    @Override
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
        return operatorRepository.save(operator);
    }

    @Override
    public Operator updateOperator(UUID id, Operator operator) {
        Operator existingOperator = findOperatorById(id);
        existingOperator.setFullName(operator.getFullName());
        existingOperator.setPhoneNumber(operator.getPhoneNumber());
        existingOperator.setPlateNumber(operator.getPlateNumber());
        existingOperator.setStage(operator.getStage());
        return operatorRepository.save(existingOperator);
    }

    @Override
    public void deleteOperator(UUID id) {
        Operator operator = findOperatorById(id);
        operatorRepository.delete(operator);
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
