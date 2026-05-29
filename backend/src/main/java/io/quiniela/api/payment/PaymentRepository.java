package io.quiniela.api.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, PaymentId> {

  Optional<Payment> findByPoolIdAndUserId(Long poolId, Long userId);
}
