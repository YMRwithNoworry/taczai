# Prone Aim and Enemy Outline Design

## Goal

Fix auto-aim against prone players and show a through-wall outline around every non-allied remote player while auto-aim is enabled.

## Scope

- Auto-aim must acquire and track players whose current pose has a low prone bounding box.
- When auto-aim is enabled, remote players who are not allied with the local player receive a client-side glowing outline.
- The local player and allied players never receive an outline from this mod.
- When auto-aim is disabled, every outline added by this mod is removed immediately.
- The feature must not change server-side entity state or another client's view.

## Prone Targeting

The current line-of-sight test only traces from the local player's eye position to the target's eye position. A prone target's eye position can sit close enough to the ground that the block trace hits terrain before reaching the target and rejects an otherwise visible player.

Target visibility will use points derived from the target's current bounding box. It will test the center plus upper and lower interior points. The target is visible when any sampled point has an unobstructed block trace. The points remain inside the current pose dimensions, so the same logic handles standing, crouching, swimming, and prone poses.

Rotation will aim at a point inside the current bounding box rather than assuming a standing eye height. Head-aim mode will use an upper interior point; body-aim mode will use the center. This keeps the crosshair on the rendered prone body.

## Enemy Outline

A client tick handler will maintain the set of players whose glowing flag was added by this mod. A player qualifies when:

- auto-aim is enabled;
- the entity is another player;
- the entity is alive;
- `localPlayer.isAlliedTo(remotePlayer)` is false.

Qualifying players receive the local entity glowing flag. Players that stop qualifying have only the flag introduced by this handler removed. The handler records whether a player was already glowing before modification and does not clear glow owned by the server, another mod, or a gameplay effect.

Client logout and level changes clear the tracked state. No packets or server mutations are introduced.

## Components

- `TargetSelector`: bounding-box visibility sampling and target filtering.
- `RotationHelper`: pose-aware aim point selection.
- `EnemyOutlineHandler`: local outline qualification, ownership tracking, cleanup, and client tick integration.
- `Taczai`: client-only handler registration.

## Tests

Unit tests will cover:

- a low prone bounding box exposes valid interior visibility samples;
- the pose-aware head and body aim points remain inside a prone bounding box;
- another non-allied player qualifies only while auto-aim is enabled;
- the local player never qualifies;
- an allied player never qualifies;
- pre-existing glow is preserved during cleanup.

The final verification gate is a clean Gradle test and build run on JDK 17, followed by a clean Git worktree and matching local/remote `main` commit IDs.
