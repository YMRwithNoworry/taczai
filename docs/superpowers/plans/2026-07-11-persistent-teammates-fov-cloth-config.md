# Persistent Teammates, FOV, and Cloth Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add permanent middle-click teammates, restore a configurable 20-degree auto-aim FOV, render teammates with green through-wall frames, and expose settings through Cloth Config.

**Architecture:** A UUID-based `TeammateManager` owns local membership and combines it with vanilla alliance state. Target selection, enemy outlines, teammate rendering, and input all consume that single authority; Forge config persists values while Cloth Config provides the UI.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1, Cloth Config Forge 11.1.136, JUnit Jupiter 5, ForgeGradle.

---

## File Structure

- Modify `build.gradle` and `mods.toml`: add Cloth Config Maven dependency and client mod dependency.
- Modify `Config.java`: add `aimbotFov`, UUID/name lists, setters, and immediate save support.
- Create `teammate/TeammateManager.java`: parse, serialize, toggle, and query persistent UUID teammates.
- Modify `TargetSelector.java`: expose player ray-pick, exclude teammates, and enforce FOV in auto mode.
- Modify `KeyMappings.java` and `MouseInputHandler.java`: bind manual target to V and middle-click teammate toggling.
- Modify `EnemyOutlineHandler.java`: exclude local persistent teammates.
- Create `overlay/TeammateFrameRenderer.java`: draw green pose-aware through-wall boxes while enabled.
- Create `config/TaczaiConfigScreen.java`: Cloth Config categories and save actions.
- Modify `Taczai.java`: register renderer and config-screen extension point.
- Modify language JSON files: key, config, teammate, and action-bar labels.
- Add focused JUnit tests for teammate persistence, FOV, filtering, and frame qualification.

### Task 1: Cloth Config Dependency Contract

**Files:** `build.gradle`, `src/main/resources/META-INF/mods.toml`

- [ ] Add `https://maven.shedaniel.me/` and `modImplementation fg.deobf("me.shedaniel.cloth:cloth-config-forge:11.1.136")`.
- [ ] Add a mandatory client dependency on `cloth_config` with version range `[11.1.136,12)`.
- [ ] Run `gradle compileJava`; expect dependency resolution and current sources to compile.
- [ ] Commit with body `-（功能）添加Minecraft 1.20.1 Cloth Config客户端依赖`.

### Task 2: Persistent UUID Teammate Domain

**Files:** `Config.java`, create `teammate/TeammateManager.java`, create `teammate/TeammateManagerTest.java`

- [ ] Write red tests for invalid UUID skipping, duplicate removal, stable UUID serialization, add/remove toggle, and last-known name lookup.
- [ ] Run the focused test; expect missing `TeammateManager` compilation errors.
- [ ] Add Forge config lists `teammateUuids` and `teammateNames`; implement pure parse/serialize helpers and runtime `Set<UUID>`/`Map<UUID,String>` state.
- [ ] Implement `toggle(Player)`, `isLocalTeammate(UUID)`, and `isEffectiveTeammate(Player local, Player candidate)` where effective means local UUID membership or `local.isAlliedTo(candidate)`.
- [ ] Save both lists immediately using the active client `ModConfig` after toggles and config-screen edits; ignore malformed UUIDs with a warning.
- [ ] Run focused and full unit tests; expect green.
- [ ] Commit with bodies for UUID persistence, rename-safe membership, and invalid entry handling.

### Task 3: Restore 20-Degree Auto-Aim FOV

**Files:** `Config.java`, `TargetSelector.java`, modify `TargetSelectorTest.java`

- [ ] Write red tests for `isWithinFov(look, direction, 20)`: zero and exactly 20 degrees true, over 20 false, opposite false.
- [ ] Run focused test; expect missing method failure.
- [ ] Add Forge config `aimbotFov` default 20, range 1 through 180, and runtime value.
- [ ] Implement clamped-dot angular comparison and filter auto candidates before ranking.
- [ ] Filter automatic and manual player targets through `TeammateManager`; non-player living targets remain eligible.
- [ ] Run focused tests; expect green.
- [ ] Commit with bodies for 20-degree FOV and teammate exclusion.

### Task 4: Middle-Click Teammates and V Manual Target

**Files:** `KeyMappings.java`, `MouseInputHandler.java`, `TargetSelector.java`, language JSON files

- [ ] Register `SELECT_TARGET_KEY` on V and consume it on client tick to set the existing manual selection signal.
- [ ] Remove middle-click signaling from `KeyMappings`.
- [ ] Make `TargetSelector.raytraceEntity` available for teammate player picking, or add a focused `raytracePlayer` wrapper that accepts players only and respects blocks/range.
- [ ] On middle-button press, ray-pick a player, toggle through `TeammateManager`, reset that player if currently confirmed, and show translated add/remove action-bar text.
- [ ] Add English and Chinese translations for V selection and teammate actions.
- [ ] Run `gradle test compileJava`; expect green.
- [ ] Commit with bodies for middle-click toggling and rebindable V manual selection.

### Task 5: Enemy Filtering and Green Through-Wall Frames

**Files:** `EnemyOutlineHandler.java`, create `TeammateFrameRenderer.java`, `Taczai.java`, tests

- [ ] Extend enemy outline predicate tests so local teammate and vanilla ally inputs both reject outlining.
- [ ] Add red pure tests for teammate-frame qualification: enabled, other, alive, effective teammate required.
- [ ] Update `EnemyOutlineHandler` to use `TeammateManager.isEffectiveTeammate`.
- [ ] Implement `TeammateFrameRenderer` on `RenderLevelStageEvent.Stage.AFTER_ENTITIES`; translate boxes by camera position and render `LevelRenderer.renderLineBox` in bright green with depth test disabled inside a try/finally restoration block.
- [ ] Register renderer on the Forge client event bus.
- [ ] Run focused tests and `compileJava`; expect green.
- [ ] Commit with bodies for green through-wall frames and enemy-outline exclusion.

### Task 6: Cloth Config Screen

**Files:** create `config/TaczaiConfigScreen.java`, modify `Taczai.java`, `Config.java`, language JSON files

- [ ] Build categories for aiming and teammates using `ConfigBuilder`/`ConfigEntryBuilder`.
- [ ] Add range, smoothing, FOV, head aim, and auto-fire entries with current values, Forge limits, defaults, and save consumers.
- [ ] List persisted teammates as UUID plus last-known name and provide per-entry removal booleans or buttons supported by Cloth Config 11; saving removes marked UUIDs.
- [ ] Register `ConfigScreenHandler.ConfigScreenFactory` through `ModLoadingContext.registerExtensionPoint` so the Mods screen opens it.
- [ ] Add all English and Chinese translation keys.
- [ ] Run `gradle test compileJava`; expect green and no missing Cloth Config symbols.
- [ ] Commit with bodies for the Mods-page screen and immediate persisted updates.

### Task 7: Full Verification and Delivery

**Files:** all modified source, resources, tests, docs, and generated JAR

- [ ] Run on JDK 17: `gradle clean test build`; expect `BUILD SUCCESSFUL`, zero test failures, and reobfuscated JAR output.
- [ ] Inspect `build/resources/main/META-INF/mods.toml` and JAR contents for the Cloth Config dependency and new classes/translations.
- [ ] Restore only tracked `.gradle/` and `build/` generated changes, then run `git diff --check` and verify a clean source worktree.
- [ ] Push every pending commit to `origin/main` as required by repository instructions.
- [ ] Confirm `HEAD`, `origin/main`, and GitHub `main` SHA match.
