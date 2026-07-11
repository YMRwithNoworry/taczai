# Prone Aim and Enemy Outline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make prone players valid auto-aim targets and locally outline every non-allied remote player while auto-aim is enabled.

**Architecture:** Extract pose-aware points from the entity's current bounding box and reuse them for visibility and rotation. Add a client-only outline handler that owns only glow flags it introduced, qualifies players through a pure predicate, and restores state when targets stop qualifying or the client leaves the level.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1, JUnit Jupiter 5, Gradle/ForgeGradle.

---

## File Structure

- Modify `src/main/java/alku/taczai/aimbot/TargetSelector.java`: expose current-pose interior sample points and accept visibility through any sample.
- Modify `src/main/java/alku/taczai/aimbot/RotationHelper.java`: aim at the upper or center point of the current bounding box.
- Create `src/main/java/alku/taczai/overlay/EnemyOutlineHandler.java`: qualify remote enemies, apply client-local glow, preserve pre-existing glow, and clean up owned state.
- Modify `src/main/java/alku/taczai/Taczai.java`: register the outline handler on the client event bus.
- Modify `src/test/java/alku/taczai/aimbot/TargetSelectorTest.java`: cover low prone-box sample generation.
- Create `src/test/java/alku/taczai/aimbot/RotationHelperTest.java`: cover pose-aware body/head aim points.
- Create `src/test/java/alku/taczai/overlay/EnemyOutlineHandlerTest.java`: cover enabled, self, allied, and pre-existing glow decisions through pure helpers.

### Task 1: Pose-Aware Target Points

**Files:**
- Modify: `src/main/java/alku/taczai/aimbot/TargetSelector.java`
- Test: `src/test/java/alku/taczai/aimbot/TargetSelectorTest.java`

- [ ] **Step 1: Write the failing prone-box sample test**

Add a test that creates `new AABB(-0.3, 0.0, 2.0, 0.3, 0.6, 2.6)`, calls `TargetSelector.visibilityPoints(box)`, asserts three points are returned, and asserts every point satisfies `box.contains(point)`.

- [ ] **Step 2: Run the focused test and verify red**

Run:

```powershell
gradle test --tests alku.taczai.aimbot.TargetSelectorTest
```

Expected: test compilation fails because `visibilityPoints(AABB)` does not exist.

- [ ] **Step 3: Implement current-pose interior samples**

Add package-private `static List<Vec3> visibilityPoints(AABB box)` returning center, a point at 80 percent of box height, and a point at 20 percent of box height. Use a small epsilon to ensure upper/lower points remain strictly inside the box.

Update `hasLineOfSight` to trace from the local eye to each point and return true when any trace misses or reaches within the existing one-block tolerance. Return false only when all points are blocked.

- [ ] **Step 4: Run the focused tests and verify green**

Run the Task 1 focused test command. Expected: all `TargetSelectorTest` tests pass.

- [ ] **Step 5: Commit Task 1**

Commit message body:

```text
-（功能）使用当前姿态包围盒采样趴姿目标可见点
-（功能）任一身体采样点可见时允许自动锁定
```

### Task 2: Pose-Aware Rotation

**Files:**
- Modify: `src/main/java/alku/taczai/aimbot/RotationHelper.java`
- Create: `src/test/java/alku/taczai/aimbot/RotationHelperTest.java`

- [ ] **Step 1: Write failing aim-point tests**

Create tests for `RotationHelper.targetPoint(AABB, boolean)`. With the prone box from Task 1, assert body mode returns `box.getCenter()`, head mode lies inside the box, and head mode has a greater Y coordinate than body mode.

- [ ] **Step 2: Run the focused test and verify red**

Run:

```powershell
gradle test --tests alku.taczai.aimbot.RotationHelperTest
```

Expected: compilation fails because `targetPoint(AABB, boolean)` does not exist.

- [ ] **Step 3: Implement pose-aware rotation point**

Add package-private `static Vec3 targetPoint(AABB box, boolean aimAtHead)`. Return the center for body mode and an interior point at 80 percent of current box height for head mode. Change `getTargetRotation` to call this helper with `target.getBoundingBox()` and `Config.aimAtHead`.

- [ ] **Step 4: Run Task 1 and Task 2 tests**

Run both aimbot test classes. Expected: all pass.

- [ ] **Step 5: Commit Task 2**

Commit message body:

```text
-（功能）根据玩家当前姿态包围盒计算瞄准点
-（功能）保持趴姿头部与身体瞄准点位于实体内部
```

### Task 3: Enemy Outline Qualification and Ownership

**Files:**
- Create: `src/main/java/alku/taczai/overlay/EnemyOutlineHandler.java`
- Create: `src/test/java/alku/taczai/overlay/EnemyOutlineHandlerTest.java`
- Modify: `src/main/java/alku/taczai/Taczai.java`

- [ ] **Step 1: Write failing pure decision tests**

Test a package-private helper `shouldOutline(boolean enabled, boolean samePlayer, boolean allied, boolean alive)` for these cases: enabled enemy is true; disabled enemy is false; self is false; allied is false; dead enemy is false. Test `shouldClearOwnedGlow(boolean owned, boolean currentlyGlowing, boolean wasGlowingBefore)` so only owned, currently glowing flags that were not pre-existing are cleared.

- [ ] **Step 2: Run the focused outline test and verify red**

Run:

```powershell
gradle test --tests alku.taczai.overlay.EnemyOutlineHandlerTest
```

Expected: compilation fails because `EnemyOutlineHandler` does not exist.

- [ ] **Step 3: Implement outline state management**

Create an event handler with identity-based maps/sets for tracked players and their pre-existing glow state. On client tick END, obtain `Minecraft.getInstance().player` and `.level`. For every level player, compute eligibility using `KeyMappings.aimbotEnabled`, identity comparison, `localPlayer.isAlliedTo(remotePlayer)`, and `remotePlayer.isAlive()`.

For newly eligible players, record `remotePlayer.hasGlowingTag()` and call `setGlowingTag(true)` only when it was false. For players no longer eligible or no longer in the level, clear the flag only if this handler set it and the player did not previously glow. Remove all bookkeeping after cleanup. When player or level is null, clean every tracked entry.

- [ ] **Step 4: Register the client handler**

In `Taczai.clientSetup`, register `new EnemyOutlineHandler()` on `MinecraftForge.EVENT_BUS` next to `AimbotHandler` and `AimbotOverlay`.

- [ ] **Step 5: Run outline and complete unit tests**

Run `gradle test`. Expected: all unit tests pass.

- [ ] **Step 6: Commit Task 3**

Commit message body:

```text
-（功能）自瞄开启时为所有非队友玩家添加本地透视轮廓
-（功能）关闭自瞄或离开世界时恢复本模组管理的发光状态
-（功能）保留服务器和其他模组已有的发光效果
```

### Task 4: Full Verification and Delivery

**Files:**
- Verify all modified source, tests, design, and plan documents.

- [ ] **Step 1: Run fresh full verification**

Run on JDK 17:

```powershell
gradle clean test build
```

Expected: `BUILD SUCCESSFUL`, all JUnit tests pass, and the reobfuscated JAR is produced.

- [ ] **Step 2: Inspect repository state**

Run `git diff --check`, `git status --short`, and inspect the final commits. Restore only generated tracked `.gradle/` and `build/` changes; retain all source, tests, and docs.

- [ ] **Step 3: Push all pending commits**

Run `git push origin main`. If HTTPS connectivity fails, retry after checking `gh auth status`; use the repository's configured transport without creating unrelated credentials.

- [ ] **Step 4: Confirm remote equality**

Verify `git rev-parse HEAD`, `git rev-parse origin/main`, and `gh api repos/YMRwithNoworry/taczai/commits/main --jq '.sha'` return the same commit ID.
