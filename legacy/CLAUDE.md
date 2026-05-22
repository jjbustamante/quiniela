# CLAUDE.md (legacy/)

This file documents the **legacy Rails 3.2 app** kept under `/legacy/` as a reference spec for the rewrite. It is no longer deployed, no longer maintained, and CI was deleted. **Do not add features here.** Read it to understand the business rules (especially the scoring trigger and time-gating) before re-implementing them on the new Spring Boot + Next.js stack at the repo root.

All paths and commands below are **relative to this `/legacy/` directory**. To run anything, `cd legacy/` first.

## Project overview

A Spanish-language sports betting pool ("quiniela") web app, originally built for the 2014 FIFA World Cup. Rails 3.2.13 on Ruby, backed by PostgreSQL. User-facing strings are in Spanish — keep that locale when porting copy to the new stack.

The Rails application module is `Demoapp` (see `config/application.rb`), not `Quiniela` — referenced as `Demoapp::Application` in `config.ru`, `Rakefile`, and initializers.

## Commands

```bash
# Setup
bundle install
bin/rails db:schema:load        # create schema from db/schema.rb (preferred over migrating from scratch)
psql -d dev_quiniela -f db/scripts/update_players_score_trigger.sql   # install scoring trigger (required, see below)

# Run
bin/rails server                # http://localhost:3000
bin/rails console

# Tests (Test::Unit, the Rails 3 default)
bin/rake                        # full suite + linters as CI runs it
bin/rake test                   # all tests
bin/rake test:units             # models only
bin/rake test:functionals       # controllers only
ruby -Itest test/unit/quiniela_test.rb              # single file
ruby -Itest test/unit/quiniela_test.rb -n test_xyz  # single test

# Lint / security (run in CI via .github/workflows/rubyonrails.yml)
bin/bundler-audit --update
bin/brakeman -q -w2
bin/rubocop --parallel
```

Default DB credentials in `config/database.yml` are `postgres`/`postgres` against databases `dev_quiniela`, `test_quiniela`, `quiniela`. CI uses a `DATABASE_URL` env override against a `rails_test` database.

## Architecture

### Domain model

Tournament data → user predictions → automatic scoring:

- **Team** — competitor (name, code, group).
- **Round** — tournament phase. IDs are load-bearing: `1`=group stage, `2`=round of 16, `3`=quarterfinals, `4`=semifinals, `5`=third place, `6`=final. Controllers filter by `round_id` directly (e.g., `Match.where(round_id: [2,3,4,5,6])` in `quinielas_controller#edit`).
- **Match** — fixture between two Teams in a Round, with a result (`score_t1`, `score_t2`, `winner_id`, `played`). May reference parent matches (`match_parent1_id`/`match_parent2_id`) for knockout brackets.
- **User** — uses [authlogic](https://github.com/binarylogic/authlogic) (`acts_as_authentic`, login field is `email`) and [paperclip](https://github.com/thoughtbot/paperclip) for `photo`. Has many quinielas. `is_admin` is an integer flag.
- **Quiniela** — a user's bracket; `has_many :bets, dependent: :destroy`, with `accepts_nested_attributes_for :bets`. Stores a cached `points` integer (see scoring trigger below).
- **Bet** — a user's prediction for a single Match within a Quiniela.

### Scoring is in the database, not Ruby

Points are computed by a PL/pgSQL trigger `update_players_score` defined in `db/scripts/update_players_score_trigger.sql`. It fires `BEFORE UPDATE OF score_t1, score_t2 ON matches`, iterates all bets for that match, and increments/decrements `quinielas.points` accordingly. Group stage (round 1) and knockouts (rounds 2–6) use different point rules; the trigger handles both branches and also undoes prior points when a result is corrected.

Implications:
- The trigger is not in `db/schema.rb` or in any migration — installing the schema is not enough; run the SQL script.
- Don't try to recompute points in Ruby on top of what the trigger does.
- If the scoring rules change, edit the SQL file and re-run it (it drops and recreates the trigger).

The `db/scripts/update_final_match.sql` script documents an ad-hoc swap of matches 63/64/65 done mid-tournament; treat it as historical context, not a thing to re-run.

### Time-gated controller logic

`config/config.yml` holds four deadlines (group-stage cutoff, second-phase open/close, tournament end). `config/environment.rb` loads it into a top-level `APP_CONFIG` constant (`HashWithIndifferentAccess`). `QuinielasController` gates `new`/`create`/`edit`/`update`/`delete` against `DateTime.now < APP_CONFIG['...'].to_datetime`. When extending this logic, follow the existing pattern rather than introducing a new config mechanism — and remember the dates in `config.yml` are from 2014.

### Auth wiring

`ApplicationController` exposes `current_user` / `current_user_session` as helper methods via authlogic. Most controllers do an inline `if !@session ... redirect_to "/" end` rather than using `before_filter :require_user`. New controllers should follow whichever style fits the surrounding code; don't refactor the inline checks without a reason.

### Routes

`config/routes.rb` mixes RESTful `resources` with many bespoke `match '...'` named routes (`signup`, `matches`, `showro`, `compare_by_bet`, `results`, `ranking`, `update`, `edit_matches`, `brackets`, `showcount`, `sudocool`). When adding a screen, check both styles before assuming a path doesn't exist. `routes.rb.orig` is leftover from a merge — ignore it.
