package rw.ac.auca.transitdues.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.service.OperatorService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/operators")
@RequiredArgsConstructor
public class OperatorController {

    private final OperatorService operatorService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Operator createOperator(@Valid @RequestBody Operator operator) {
        return operatorService.createOperator(operator);
    }

    @GetMapping
    public List<Operator> getAllOperators() {
        return operatorService.findAllOperators();
    }

    @GetMapping("/{id}")
    public Operator getOperatorById(@PathVariable UUID id) {
        return operatorService.findOperatorById(id);
    }

    @PutMapping("/{id}")
    public Operator updateOperator(@PathVariable UUID id, @Valid @RequestBody Operator operator) {
        return operatorService.updateOperator(id, operator);
    }

    /**
     * Legacy hard-delete REST endpoint, kept for API completeness; restricted to
     * ADMIN only (deny-by-default, same rule as the safe deactivation path on
     * the web UI - see OperatorWebController). This still performs a real,
     * permanent row delete and is refused by the database if the operator has
     * any due_payment/user_account history (foreign-key constraints with no
     * cascade). Prefer the web UI's "Deactivate" action, which always
     * soft-deletes and preserves history.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOperator(@PathVariable UUID id) {
        operatorService.deleteOperator(id);
    }
}
