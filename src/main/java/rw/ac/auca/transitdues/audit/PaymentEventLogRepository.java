package rw.ac.auca.transitdues.audit;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentEventLogRepository extends MongoRepository<PaymentEventLog, String> {

    /**
     * The operator portal's "Recent Payments" list: only payment-attempt
     * outcomes (not every due-issuance/edit event), newest first.
     */
    List<PaymentEventLog> findTop5ByOperatorIdAndStatusInOrderByOccurredAtDesc(String operatorId,
                                                                                 List<String> statuses);
}
