package rw.ac.auca.transitdues.operator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.ac.auca.transitdues.operator.domain.ApprovalStatus;
import rw.ac.auca.transitdues.operator.domain.Operator;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OperatorRepository extends JpaRepository<Operator, UUID> {

    List<Operator> findByStageId(UUID stageId);

    boolean existsByPlateNumberIgnoreCase(String plateNumber);

    boolean existsByPlateNumberIgnoreCaseAndIdNot(String plateNumber, UUID id);

    List<Operator> findByApprovalStatus(ApprovalStatus approvalStatus);

    List<Operator> findByApprovalStatusNotIn(Collection<ApprovalStatus> approvalStatuses);

    List<Operator> findByStageIdAndApprovalStatus(UUID stageId, ApprovalStatus approvalStatus);

    int countByStageIdAndApprovalStatusNotIn(UUID stageId, Collection<ApprovalStatus> approvalStatuses);
}
