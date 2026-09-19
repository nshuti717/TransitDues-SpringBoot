package rw.ac.auca.transitdues.operator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.ac.auca.transitdues.operator.domain.Operator;

import java.util.List;
import java.util.UUID;

public interface OperatorRepository extends JpaRepository<Operator, UUID> {

    List<Operator> findByStageId(UUID stageId);
}
