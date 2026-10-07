package rw.ac.auca.transitdues.operator.service;

import rw.ac.auca.transitdues.operator.domain.Operator;

import java.util.List;
import java.util.UUID;

public interface OperatorService {

    default Operator createOperator(Operator operator) {
        return createOperator(operator, null);
    }

    /**
     * Same checks as {@link #createOperator(Operator)} (stage exists, stage has
     * capacity), but lets callers with no authenticated principal of their own
     * (e.g. self-registration) supply who the audit log should credit.
     */
    Operator createOperator(Operator operator, String performedByOverride);

    Operator updateOperator(UUID id, Operator operator);

    void deleteOperator(UUID id);

    Operator findOperatorById(UUID id);

    List<Operator> findAllOperators();
}
