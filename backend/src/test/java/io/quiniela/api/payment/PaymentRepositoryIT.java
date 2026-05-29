package io.quiniela.api.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentRepositoryIT extends AbstractIntegrationTest {

  @Autowired PaymentRepository payments;
  @Autowired UserRepository users;

  @Test
  void upsertsAndReadsBack() {
    var u = new User("g-pay1", "pay1@example.com", "Pay One", null, UserRole.PLAYER);
    u.setInvitePath("pay1-abc");
    u = users.save(u);

    var admin = new User("g-admin", "admin@example.com", "Admin", null, UserRole.ADMIN);
    admin = users.save(admin);

    var p = new Payment(1L, u.getId());
    p.markPaid(admin.getId(), 2000, "Zelle 8821");
    payments.save(p);

    var fetched = payments.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    assertThat(fetched.isPaid()).isTrue();
    assertThat(fetched.getAmountCents()).isEqualTo(2000);
    assertThat(fetched.getNote()).isEqualTo("Zelle 8821");
    assertThat(fetched.isSettled()).isFalse();
  }
}
