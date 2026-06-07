package io.quiniela.api.admin;

import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.user.AdminGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only per-round score multipliers. Reads/writes {@code round.points_multiplier} (the column
 * the scoring engine already honours). Only knockout rounds are editable; the group stage is fixed
 * at 1. Role check uses the shared {@link AdminGuard}.
 *
 * <p>Changing a multiplier does NOT retroactively rescore: the DB trigger recomputes points only on
 * match-result updates. Admins must set values before the first knockout match.
 */
@Service
public class AdminRoundMultiplierService {

  private static final Long ACTIVE_TOURNAMENT_ID = 1L;
  private static final String GROUP_CODE = "GROUP";
  private static final int MAX_MULTIPLIER = 10;

  private final RoundRepository rounds;
  private final AdminGuard adminGuard;

  public AdminRoundMultiplierService(RoundRepository rounds, AdminGuard adminGuard) {
    this.rounds = rounds;
    this.adminGuard = adminGuard;
  }

  public record RoundMultiplierRow(String code, String name, int multiplier) {}

  public record MultipliersView(List<RoundMultiplierRow> rounds) {}

  public record UpdateRow(String code, int multiplier) {}

  public record UpdateRequest(List<UpdateRow> rounds) {}

  @Transactional(readOnly = true)
  public MultipliersView getMultipliers(Long callerId) {
    adminGuard.requireAdmin(callerId);
    return view();
  }

  @Transactional
  public MultipliersView updateMultipliers(Long callerId, UpdateRequest req) {
    adminGuard.requireAdmin(callerId);
    if (req == null || req.rounds() == null || req.rounds().isEmpty()) {
      throw new IllegalArgumentException("rounds must not be empty");
    }

    // Validate every row first (range + known round + not GROUP) so a bad row never
    // leaves a partial write — guard-first, write-second.
    for (UpdateRow row : req.rounds()) {
      if (row.multiplier() < 1 || row.multiplier() > MAX_MULTIPLIER) {
        throw new IllegalArgumentException(
            "multiplier for " + row.code() + " must be between 1 and " + MAX_MULTIPLIER);
      }
      Round round =
          rounds
              .findByTournamentIdAndCode(ACTIVE_TOURNAMENT_ID, row.code())
              .orElseThrow(() -> new IllegalArgumentException("unknown round " + row.code()));
      if (GROUP_CODE.equals(round.getCode())) {
        throw new IllegalArgumentException("the group stage multiplier is fixed at 1");
      }
    }

    for (UpdateRow row : req.rounds()) {
      Round round =
          rounds.findByTournamentIdAndCode(ACTIVE_TOURNAMENT_ID, row.code()).orElseThrow();
      round.setPointsMultiplier(row.multiplier());
      rounds.save(round);
    }
    return view();
  }

  private MultipliersView view() {
    List<RoundMultiplierRow> rows =
        rounds.findByTournamentIdOrderBySequenceAsc(ACTIVE_TOURNAMENT_ID).stream()
            .filter(r -> !GROUP_CODE.equals(r.getCode()))
            .map(r -> new RoundMultiplierRow(r.getCode(), r.getName(), r.getPointsMultiplier()))
            .toList();
    return new MultipliersView(rows);
  }
}
