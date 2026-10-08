package rw.ac.auca.transitdues.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.service.DuePaymentService;

import java.util.List;
import java.util.UUID;

/**
 * Finance/admin CRUD over due payments, same role rules as the Thymeleaf
 * /web/duepayments equivalent: ADMIN can read, only FINANCE_OFFICER can
 * mutate. Operator-initiated payment actions live under /portal instead, not
 * here.
 */
@RestController
@RequestMapping("/api/duepayments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_OFFICER')")
public class DuePaymentController {

    private final DuePaymentService duePaymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
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
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public DuePayment updateDuePayment(@PathVariable UUID id, @Valid @RequestBody DuePayment duePayment) {
        return duePaymentService.updateDuePayment(id, duePayment);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public void deleteDuePayment(@PathVariable UUID id) {
        duePaymentService.deleteDuePayment(id);
    }
}
