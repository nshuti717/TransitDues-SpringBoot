package rw.ac.auca.transitdues.duepayment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.domain.PaymentType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DuePaymentRepository extends JpaRepository<DuePayment, UUID> {

    boolean existsByOperatorIdAndTypeAndDueDate(UUID operatorId, PaymentType type, LocalDate dueDate);

    boolean existsByOperatorIdAndTypeAndDueDateAndIdNot(UUID operatorId, PaymentType type, LocalDate dueDate, UUID id);

    List<DuePayment> findByStatusAndDueDateBefore(DuePaymentStatus status, LocalDate date);
}
