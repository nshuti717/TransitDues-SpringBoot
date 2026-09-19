package rw.ac.auca.transitdues.operator.service;

import rw.ac.auca.transitdues.operator.domain.Operator;

import java.util.List;
import java.util.UUID;

public interface OperatorService {

    Operator createOperator(Operator operator);

    Operator updateOperator(UUID id, Operator operator);

    void deleteOperator(UUID id);

    Operator findOperatorById(UUID id);

    List<Operator> findAllOperators();
}
