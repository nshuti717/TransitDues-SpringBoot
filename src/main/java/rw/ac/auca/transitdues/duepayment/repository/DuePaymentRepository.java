package rw.ac.auca.transitdues.duepayment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;

import java.util.UUID;

public interface DuePaymentRepository extends JpaRepository<DuePayment, UUID> {
}
