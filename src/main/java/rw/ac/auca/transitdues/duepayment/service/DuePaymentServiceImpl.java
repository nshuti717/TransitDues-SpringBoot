package rw.ac.auca.transitdues.duepayment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.repository.DuePaymentRepository;
import rw.ac.auca.transitdues.exception.DuePaymentNotFoundException;
import rw.ac.auca.transitdues.exception.OperatorNotFoundException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DuePaymentServiceImpl implements DuePaymentService {

    private final DuePaymentRepository duePaymentRepository;
    private final OperatorRepository operatorRepository;

    @Override
    public DuePayment createDuePayment(DuePayment duePayment) {
        UUID operatorId = duePayment.getOperator().getId();
        Operator operator = operatorRepository.findById(operatorId)
                .orElseThrow(() -> new OperatorNotFoundException("Operator not found with id: " + operatorId));

        duePayment.setOperator(operator);
        return duePaymentRepository.save(duePayment);
    }

    @Override
    public DuePayment updateDuePayment(UUID id, DuePayment duePayment) {
        DuePayment existingDuePayment = findDuePaymentById(id);
        existingDuePayment.setAmount(duePayment.getAmount());
        existingDuePayment.setType(duePayment.getType());
        existingDuePayment.setDatePaid(duePayment.getDatePaid());
        existingDuePayment.setStatus(duePayment.getStatus());
        existingDuePayment.setOperator(duePayment.getOperator());
        return duePaymentRepository.save(existingDuePayment);
    }

    @Override
    public void deleteDuePayment(UUID id) {
        DuePayment duePayment = findDuePaymentById(id);
        duePaymentRepository.delete(duePayment);
    }

    @Override
    public DuePayment findDuePaymentById(UUID id) {
        return duePaymentRepository.findById(id)
                .orElseThrow(() -> new DuePaymentNotFoundException("DuePayment not found with id: " + id));
    }

    @Override
    public List<DuePayment> findAllDuePayments() {
        return duePaymentRepository.findAll();
    }
}
