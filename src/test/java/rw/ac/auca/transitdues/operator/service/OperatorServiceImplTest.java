package rw.ac.auca.transitdues.operator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rw.ac.auca.transitdues.audit.AuditLogService;
import rw.ac.auca.transitdues.exception.DuplicatePlateException;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.operator.repository.OperatorRepository;
import rw.ac.auca.transitdues.stage.domain.Stage;
import rw.ac.auca.transitdues.stage.repository.StageRepository;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatorServiceImplTest {

    @Mock
    private OperatorRepository operatorRepository;

    @Mock
    private StageRepository stageRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private OperatorServiceImpl operatorService;

    @Test
    void secondOperatorWithDifferentlyFormattedPlateIsRejectedAsDuplicate() {
        UUID stageId = UUID.randomUUID();
        Stage stage = new Stage();
        stage.setId(stageId);
        stage.setName("Remera");
        stage.setLocation("Gasabo, Kigali");
        stage.setCapacity(5);

        Operator secondOperator = new Operator();
        secondOperator.setFullName("Second Operator");
        secondOperator.setPhoneNumber("0788222333");
        secondOperator.setPlateNumber(" rab-123a ");
        secondOperator.setStage(stage);

        when(stageRepository.findById(stageId)).thenReturn(Optional.of(stage));
        when(operatorRepository.findByStageId(stageId)).thenReturn(Collections.emptyList());
        // "RAB123A" is already registered; the incoming " rab-123a " must
        // normalize to the same value before the duplicate check runs.
        when(operatorRepository.existsByPlateNumberIgnoreCase("RAB123A")).thenReturn(true);

        DuplicatePlateException ex = assertThrows(DuplicatePlateException.class,
                () -> operatorService.createOperator(secondOperator));

        assertEquals("This plate number is already registered.", ex.getMessage());
        verify(operatorRepository, never()).save(any(Operator.class));
        verifyNoInteractions(auditLogService);
    }
}
