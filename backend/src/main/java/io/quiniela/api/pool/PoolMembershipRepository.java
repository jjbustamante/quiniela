package io.quiniela.api.pool;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoolMembershipRepository extends JpaRepository<PoolMembership, PoolMembershipId> {

  boolean existsByPoolIdAndUserId(Long poolId, Long userId);

  long countByPoolId(Long poolId);

  @Query(
      value =
          "SELECT COUNT(*) FROM pool_membership pm JOIN users u ON u.id = pm.user_id "
              + "WHERE pm.pool_id = :poolId AND u.role <> 'admin' AND u.is_bot = false",
      nativeQuery = true)
  long countPayingPanas(@Param("poolId") Long poolId);
}
