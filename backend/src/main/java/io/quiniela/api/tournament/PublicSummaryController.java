package io.quiniela.api.tournament;

import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.pool.Pool;
import io.quiniela.api.pool.PoolMembershipRepository;
import io.quiniela.api.pool.PoolRepository;
import io.quiniela.api.team.TeamRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated tournament + pool summary used by the landing and home pages.
 *
 * <p>Returns the active tournament's display metadata (name, dates, host countries, opening venue,
 * group-stage match count, group count) and the active pool's live stats (entry fee, pot in cents,
 * pana count). Year glyph, days-to-kickoff and formatted ranges are derived on the client.
 */
@RestController
@RequestMapping("/api/public")
public class PublicSummaryController {

  /** Single active pool/tournament for v1 — see schema seed in V003 / V006. */
  private static final Long ACTIVE_POOL_ID = 1L;

  private static final String GROUP_STAGE_CODE = "GROUP";

  private final TournamentRepository tournaments;
  private final PoolRepository pools;
  private final PoolMembershipRepository memberships;
  private final MatchRepository matches;
  private final RoundRepository rounds;
  private final TeamRepository teams;

  public PublicSummaryController(
      TournamentRepository tournaments,
      PoolRepository pools,
      PoolMembershipRepository memberships,
      MatchRepository matches,
      RoundRepository rounds,
      TeamRepository teams) {
    this.tournaments = tournaments;
    this.pools = pools;
    this.memberships = memberships;
    this.matches = matches;
    this.rounds = rounds;
    this.teams = teams;
  }

  public record TournamentSummary(
      String slug,
      String name,
      LocalDate startDate,
      LocalDate endDate,
      List<String> hostCountryCodes,
      String openingVenue,
      int totalGroupStageMatches,
      int totalGroups) {}

  public record PoolSummary(String currency, int entryFeeCents, long potCents, long panaCount) {}

  public record SummaryResponse(TournamentSummary tournament, PoolSummary pool) {}

  @GetMapping("/summary")
  public ResponseEntity<SummaryResponse> get() {
    Pool pool = pools.findById(ACTIVE_POOL_ID).orElse(null);
    if (pool == null) return ResponseEntity.notFound().build();

    Tournament tournament = tournaments.findById(pool.getTournamentId()).orElse(null);
    if (tournament == null) return ResponseEntity.notFound().build();

    long panaCount = memberships.countByPoolId(ACTIVE_POOL_ID);
    long potCents = panaCount * pool.getEntryFeeCents();

    int groupStageMatches =
        rounds
            .findByTournamentIdAndCode(tournament.getId(), GROUP_STAGE_CODE)
            .map(r -> matches.countByTournamentIdAndRoundId(tournament.getId(), r.getId()))
            .orElse(0);
    int totalGroups = teams.countDistinctGroupCodeByTournamentId(tournament.getId());

    List<String> hosts =
        tournament.getHostCountryCodes() == null
            ? List.of()
            : Arrays.stream(tournament.getHostCountryCodes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

    return ResponseEntity.ok(
        new SummaryResponse(
            new TournamentSummary(
                tournament.getSlug(),
                tournament.getName(),
                tournament.getStartDate(),
                tournament.getEndDate(),
                hosts,
                tournament.getOpeningVenue(),
                groupStageMatches,
                totalGroups),
            new PoolSummary(pool.getCurrency(), pool.getEntryFeeCents(), potCents, panaCount)));
  }
}
