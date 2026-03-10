# ChickenWar

This plugin provides a chicken-fight betting game for Paper servers with a pari-mutuel betting engine.

## What was updated

- Admin-only management commands: `build`, `start`, `end`, `restart`, `setwarp`, `reload`
- Player flow: `bet`, `bossbar`, `claim`
- New betting logic:
  - pari-mutuel payout
  - tax on capped winner profit only
  - jackpot seeding with dummy server pot
  - payout multiplier cap with excess tracking
  - liquidity validation and configurable cancel/refund
  - scaling for `initial-max-bet`
- Crash-safe persistence in `plugins/ChickenWar/data.yml`
- Unit tests for payout math in `PariMutuelCalculatorTest`

## Betting policy notes

- If `betting-logic.jackpot-system.enabled` is `false`, all jackpot-related transfers are skipped.
  - No jackpot seeding.
  - No transfer of cap excess to jackpot.
  - Dummy server-pot payout/refund does not change jackpot.
- If `betting-logic.liquidity.refund-on-fail` is `false`, failed liquidity rounds are cancelled without player refund (treated as sink by policy).

## Run tests

```bash
mvn test
```

## Build plugin

```bash
mvn clean package
```
