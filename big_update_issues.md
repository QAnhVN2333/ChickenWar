# Big Update Betting Logic - Gap Report

## Checklist doi chieu
- [x] Pari-Mutuel co ban: da co chia pool theo ti le va tax tren profit.
- [x] Dummy UUID (`SERVER_POT_UUID`) co tham gia tinh payout va refund.
- [x] Liquidity check + cancel/refund flow da co.
- [x] Scaling `initial-max-bet` theo success/cancel da co.
- [x] Offline payout + pending claim + crash recovery state da co.
- [x] Tax tinh tren `cappedProfit` (khong con tax tren `rawProfit` khi bi cap).
- [x] Config `split-mode` da duoc doc va ap dung trong seeding.
- [x] `jackpot-system.enabled` da gate nhat quan cac luong jackpot.
- [x] Config `liquidity.refund-on-fail` da duoc ton trong.

## 1) Tax sau cap multiplier
**Trang thai:** Da fix  
**Vi tri:** `src/main/java/com/example/chickenwar/managers/betting/PariMutuelCalculator.java`

### Da thay doi
- Doi thu tu tinh toan:
  1. Tinh `rawReturn`.
  2. Ap cap -> `cappedReturn`.
  3. Suy ra `cappedProfit = max(0, cappedReturn - wager)`.
  4. Tax = `ceil(cappedProfit * taxPercent)`.
  5. Payout = `wager + floor(max(0, cappedProfit - tax))`.
- `totalExcessByCap` duoc track theo phan return bi cat boi cap.

### Test lien quan
- Cap nhat/bo sung trong `src/test/java/com/example/chickenwar/managers/betting/PariMutuelCalculatorTest.java`:
  - `shouldApplyTaxOnCappedProfitOnly`.

## 2) Config `split-mode`
**Trang thai:** Da fix  
**Vi tri:** `src/main/java/com/example/chickenwar/managers/BetManager.java`

### Da thay doi
- `seedFromJackpot()` da doc `ConfigKeys.BettingLogic.SPLIT_MODE`.
- Them parse mode qua `resolveSplitMode()`.
- Support:
  - `RANDOM_SKEWED` (mac dinh)
  - `EVEN`
- Mode sai: fallback `RANDOM_SKEWED` + log warning.

## 3) `jackpot-system.enabled` gate chua dong nhat
**Trang thai:** Da fix  
**Vi tri:** `src/main/java/com/example/chickenwar/managers/BetManager.java`

### Da thay doi
- `processPayout()`:
  - Neu khong co winner bet, chi cong `loserPool` vao jackpot khi jackpot enabled.
  - Payout/refund cua `SERVER_POT_UUID` chi cong jackpot khi enabled.
  - `cappedExcessToJackpot` chi nap jackpot khi enabled.
- `refundEntry()`:
  - Dummy refund chi cong jackpot khi enabled.
- `openBetting()` da giu gate seed nhu cu (chi seed khi enabled).

## 4) `liquidity.refund-on-fail`
**Trang thai:** Da fix  
**Vi tri:** `src/main/java/com/example/chickenwar/managers/BetManager.java`

### Da thay doi
- `lockBettingAndValidateLiquidity()` da doc `ConfigKeys.BettingLogic.REFUND_ON_FAIL`.
- Hanh vi:
  - `true` -> `refundAll(true)` (giu nhu cu).
  - `false` -> huy round, xoa bet khong refund, broadcast fail message.

## Ghi chu policy
- Da bo sung vao `README.md`:
  - Policy khi `jackpot-system.enabled=false`.
  - Policy khi `liquidity.refund-on-fail=false`.
