package rw.ac.auca.transitdues.duepayment.service;

import rw.ac.auca.transitdues.duepayment.domain.DuePayment;

import java.util.List;
import java.util.UUID;

public interface DuePaymentService {

    DuePayment createDuePayment(DuePayment duePayment);

    DuePayment updateDuePayment(UUID id, DuePayment duePayment);

    void deleteDuePayment(UUID id);

    DuePayment findDuePaymentById(UUID id);

    List<DuePayment> findAllDuePayments();
}
