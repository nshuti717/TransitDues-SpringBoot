package rw.ac.auca.transitdues.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.service.DuePaymentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/duepayments")
@RequiredArgsConstructor
public class DuePaymentController {

    private final DuePaymentService duePaymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DuePayment createDuePayment(@Valid @RequestBody DuePayment duePayment) {
        return duePaymentService.createDuePayment(duePayment);
    }

    @GetMapping
    public List<DuePayment> getAllDuePayments() {
        return duePaymentService.findAllDuePayments();
    }

    @GetMapping("/{id}")
    public DuePayment getDuePaymentById(@PathVariable UUID id) {
        return duePaymentService.findDuePaymentById(id);
    }

    @PutMapping("/{id}")
    public DuePayment updateDuePayment(@PathVariable UUID id, @Valid @RequestBody DuePayment duePayment) {
        return duePaymentService.updateDuePayment(id, duePayment);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDuePayment(@PathVariable UUID id) {
        duePaymentService.deleteDuePayment(id);
    }
}
