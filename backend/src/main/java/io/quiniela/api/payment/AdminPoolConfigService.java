package io.quiniela.api.payment;

import io.quiniela.api.pool.Pool;
import io.quiniela.api.pool.PoolRepository;
import io.quiniela.api.pool.PrizeSplit;
import io.quiniela.api.pool.PrizeSplitRepository;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only pool money configuration: entry fee, organizer (house) cut, and the prize-split
 * percentages.
 *
 * <p>All three values already live in the schema ({@code pool.entry_fee_cents}, {@code
 * pool.house_cut_percentage}, {@code prize_split}) and are read live by {@code
 * PublicSummaryController} and the payments ledger — this service just lets the admin change them
 * after the initial seed. Role check mirrors {@link AdminPaymentService}: an explicit local guard,
 * not method security.
 */
@Service
public class AdminPoolConfigService {

  private static final Long ACTIVE_POOL_ID = 1L;

  private final PoolRepository pools;
  private final PrizeSplitRepository prizeSplits;
  private final UserRepository users;

  public AdminPoolConfigService(
      PoolRepository pools, PrizeSplitRepository prizeSplits, UserRepository users) {
    this.pools = pools;
    this.prizeSplits = prizeSplits;
    this.users = users;
  }

  public record PrizeRow(int rank, int percentage) {}

  public record PoolConfigView(
      String currency, int entryFeeCents, int houseCutPercentage, List<PrizeRow> prizeSplit) {}

  public record UpdateRequest(
      Integer entryFeeCents, Integer houseCutPercentage, List<PrizeRow> prizeSplit) {}

  @Transactional(readOnly = true)
  public PoolConfigView getConfig(Long callerId) {
    requireAdmin(callerId);
    return view(pool());
  }

  @Transactional
  public PoolConfigView updateConfig(Long callerId, UpdateRequest req) {
    requireAdmin(callerId);
    validate(req);

    Pool pool = pool();
    pool.setEntryFeeCents(req.entryFeeCents());
    pool.setHouseCutPercentage(req.houseCutPercentage());
    pools.save(pool);

    // Replace the whole prize-split set so removed ranks disappear.
    prizeSplits.deleteAll(prizeSplits.findByPoolIdOrderByRankAsc(ACTIVE_POOL_ID));
    prizeSplits.flush();
    for (PrizeRow row : req.prizeSplit()) {
      prizeSplits.save(new PrizeSplit(ACTIVE_POOL_ID, row.rank(), row.percentage()));
    }

    return view(pool);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private Pool pool() {
    return pools
        .findById(ACTIVE_POOL_ID)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pool not found"));
  }

  private PoolConfigView view(Pool pool) {
    List<PrizeRow> rows =
        prizeSplits.findByPoolIdOrderByRankAsc(ACTIVE_POOL_ID).stream()
            .map(p -> new PrizeRow(p.getRank(), p.getPercentage()))
            .toList();
    return new PoolConfigView(
        pool.getCurrency(), pool.getEntryFeeCents(), pool.getHouseCutPercentage(), rows);
  }

  private void validate(UpdateRequest req) {
    if (req.entryFeeCents() == null || req.entryFeeCents() < 0) {
      throw new IllegalArgumentException("entryFeeCents must be >= 0");
    }
    if (req.houseCutPercentage() == null
        || req.houseCutPercentage() < 0
        || req.houseCutPercentage() > 100) {
      throw new IllegalArgumentException("houseCutPercentage must be between 0 and 100");
    }
    List<PrizeRow> split = req.prizeSplit();
    if (split == null || split.isEmpty()) {
      throw new IllegalArgumentException("prizeSplit must have at least one rank");
    }
    int sum = 0;
    int expectedRank = 1;
    for (PrizeRow row : split) {
      if (row.rank() != expectedRank) {
        throw new IllegalArgumentException("prizeSplit ranks must be contiguous starting at 1");
      }
      if (row.percentage() <= 0 || row.percentage() > 100) {
        throw new IllegalArgumentException("each prize percentage must be between 1 and 100");
      }
      sum += row.percentage();
      expectedRank++;
    }
    if (sum != 100) {
      throw new IllegalArgumentException("prize percentages must sum to 100 (got " + sum + ")");
    }
  }

  private void requireAdmin(Long callerUserId) {
    User caller =
        users
            .findById(callerUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (caller.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }
}
