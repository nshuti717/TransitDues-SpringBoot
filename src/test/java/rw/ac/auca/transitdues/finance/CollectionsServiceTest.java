package rw.ac.auca.transitdues.finance;

import org.junit.jupiter.api.Test;
import rw.ac.auca.transitdues.duepayment.domain.DuePayment;
import rw.ac.auca.transitdues.duepayment.domain.DuePaymentStatus;
import rw.ac.auca.transitdues.duepayment.domain.PaymentType;
import rw.ac.auca.transitdues.operator.domain.Operator;
import rw.ac.auca.transitdues.stage.domain.Stage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionsServiceTest {

    private final CollectionsService collectionsService = new CollectionsService();

    @Test
    void summarizeComputesExpectedCollectedAndOutstanding() {
        Stage stage = stage("Nyabugogo");
        Operator operator = operator(stage, "Jean Claude Ishimwe", "RAB123A");

        DuePayment paid = due(operator, new BigDecimal("1000"), DuePaymentStatus.PAID, LocalDate.now());
        DuePayment pending = due(operator, new BigDecimal("500"), DuePaymentStatus.PENDING, LocalDate.now().plusDays(5));

        CollectionsSummary summary = collectionsService.summarize(List.of(paid, pending));

        assertEquals(new BigDecimal("1500"), summary.totalExpected());
        assertEquals(new BigDecimal("1000"), summary.totalCollected());
        assertEquals(new BigDecimal("500"), summary.totalOutstanding());
        assertEquals(1L, summary.countsByStatus().get("PAID"));
        assertEquals(1L, summary.countsByStatus().get("PENDING"));
    }

    @Test
    void filterMatchesOperatorNameOrPlateCaseInsensitively() {
        Stage stage = stage("Nyabugogo");
        Operator jeanClaude = operator(stage, "Jean Claude Ishimwe", "RAB123A");
        Operator alia = operator(stage, "Alia", "RAB999Z");

        DuePayment jeanClaudeDue = due(jeanClaude, new BigDecimal("1000"), DuePaymentStatus.PENDING, LocalDate.now());
        DuePayment aliaDue = due(alia, new BigDecimal("1000"), DuePaymentStatus.PENDING, LocalDate.now());

        List<DuePayment> result = collectionsService.filter(List.of(jeanClaudeDue, aliaDue), null, null, "rab999z",
                null, null);

        assertEquals(1, result.size());
        assertTrue(result.contains(aliaDue));
    }

    @Test
    void filterMatchesStatusStageAndDateRangeTogether() {
        Stage nyabugogo = stage("Nyabugogo");
        Stage remera = stage("Remera");
        Operator atNyabugogo = operator(nyabugogo, "Operator A", "RAB111A");
        Operator atRemera = operator(remera, "Operator B", "RAB222B");

        // Dates are in the future relative to "now" so effectiveStatus stays PENDING
        // (a past dueDate still PENDING computes as OVERDUE - see DuePayment.getEffectiveStatus).
        LocalDate farFuture = LocalDate.now().plusYears(1);
        DuePayment inRange = due(atNyabugogo, new BigDecimal("1000"), DuePaymentStatus.PENDING,
                farFuture.withDayOfMonth(10));
        DuePayment wrongStage = due(atRemera, new BigDecimal("1000"), DuePaymentStatus.PENDING,
                farFuture.withDayOfMonth(10));
        DuePayment outOfRange = due(atNyabugogo, new BigDecimal("1000"), DuePaymentStatus.PENDING,
                farFuture.plusMonths(2).withDayOfMonth(1));
        DuePayment wrongStatus = due(atNyabugogo, new BigDecimal("1000"), DuePaymentStatus.PAID,
                farFuture.withDayOfMonth(10));

        List<DuePayment> result = collectionsService.filter(
                List.of(inRange, wrongStage, outOfRange, wrongStatus), "PENDING", nyabugogo.getId(), null,
                farFuture.withDayOfMonth(1), farFuture.withDayOfMonth(farFuture.lengthOfMonth()));

        assertEquals(1, result.size());
        assertTrue(result.contains(inRange));
    }

    private Stage stage(String name) {
        Stage stage = new Stage();
        stage.setId(UUID.randomUUID());
        stage.setName(name);
        stage.setLocation("Kigali");
        stage.setCapacity(10);
        return stage;
    }

    private Operator operator(Stage stage, String fullName, String plateNumber) {
        Operator operator = new Operator();
        operator.setId(UUID.randomUUID());
        operator.setFullName(fullName);
        operator.setPhoneNumber("0788000000");
        operator.setPlateNumber(plateNumber);
        operator.setStage(stage);
        return operator;
    }

    private DuePayment due(Operator operator, BigDecimal amount, DuePaymentStatus status, LocalDate dueDate) {
        DuePayment due = new DuePayment();
        due.setId(UUID.randomUUID());
        due.setOperator(operator);
        due.setType(PaymentType.DAILY);
        due.setAmount(amount);
        due.setStatus(status);
        due.setDueDate(dueDate);
        return due;
    }
}
