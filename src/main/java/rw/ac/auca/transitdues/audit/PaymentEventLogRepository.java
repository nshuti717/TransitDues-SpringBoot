package rw.ac.auca.transitdues.audit;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentEventLogRepository extends MongoRepository<PaymentEventLog, String> {
}
