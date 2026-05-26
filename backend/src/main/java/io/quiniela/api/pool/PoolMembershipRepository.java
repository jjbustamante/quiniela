package io.quiniela.api.pool;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PoolMembershipRepository extends JpaRepository<PoolMembership, PoolMembershipId> {

  boolean existsByPoolIdAndUserId(Long poolId, Long userId);

  long countByPoolId(Long poolId);
}
