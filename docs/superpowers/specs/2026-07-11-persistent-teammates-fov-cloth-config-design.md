# Persistent Teammates, FOV, and Cloth Config Design

## Goal

Restore local teammate selection and a 20-degree auto-aim field-of-view limit, then expose all user settings through a Cloth Config screen.

## Input

- Middle-click while pointing at another player toggles that player's teammate status.
- The selection ray uses the configured aimbot range and respects intervening blocks.
- The existing manual target action moves to a registered `V` key binding and remains rebindable in Minecraft Controls.
- Middle-click outside a player does nothing and does not change the teammate list.
- A short action-bar message reports whether the player was added or removed.

## Persistent Teammates

Teammates are stored by player UUID in the Forge client configuration, not by display name. The UUID list is loaded at client startup and saved immediately after every middle-click toggle or config-screen edit. It therefore survives game restarts and player renames.

Effective teammate status is the union of:

- locally selected UUIDs; and
- Minecraft's server-provided alliance relation from `localPlayer.isAlliedTo(candidate)`.

The local player is always excluded. Local teammates and server allies are excluded from automatic target selection, manual target confirmation, and enemy outlines.

## Green Teammate Frame

While auto-aim is enabled, every online local teammate or server ally receives a bright green, through-wall bounding-box frame. The frame follows the player's current pose bounding box, so standing, crouching, swimming, and prone players are framed correctly.

The frame is drawn during the client level render stage with depth testing disabled and restored afterward. It does not modify scoreboard teams, entity glow flags, server state, or packets. This is necessary because vanilla glow color comes from scoreboard team color; assigning a synthetic green team would overwrite or conflict with the server's real team data.

Enemy players continue to use the existing glowing outline. The enemy qualification predicate is updated to treat locally selected UUIDs as allies.

## 20-Degree FOV Limit

Automatic target selection accepts only candidates whose pose-aware aim direction lies within 20 degrees of the current look direction. The configured value represents the maximum angular offset from the crosshair, not the full cone width. Therefore the default `20` means a 40-degree total cone.

The calculation clamps the dot product before `acos` to avoid floating-point errors. Visibility, range, alive-state, self, and teammate filters run before final ranking. Manual `V` selection is not FOV-limited because it already requires a direct ray hit.

## Cloth Config Screen

Cloth Config API for Minecraft 1.20.1 is a required client dependency. A Forge extension point exposes the screen from the Minecraft Mods menu.

The screen contains:

- maximum auto-aim range;
- aim smoothing;
- FOV angle, default 20 degrees;
- aim at head toggle;
- auto-fire toggle;
- a teammate category listing saved UUIDs with last-known player names when available;
- remove controls for saved teammates.

Saving validates values against the existing Forge config ranges, updates runtime values immediately, persists the Forge config, and refreshes outline/target behavior without restarting.

## Components

- `Config`: adds FOV and persistent teammate UUID/name entries plus explicit save/update helpers.
- `TeammateManager`: owns UUID membership, effective alliance checks, toggling, and lookup metadata.
- `MouseInputHandler`: ray-picks a player and delegates middle-click toggling.
- `KeyMappings`: registers `V` for manual target selection and removes middle-click target signaling.
- `TargetSelector`: excludes effective teammates and applies the configurable FOV predicate.
- `EnemyOutlineHandler`: excludes persistent teammates from enemy glow.
- `TeammateFrameRenderer`: renders green through-wall pose-aware frames while auto-aim is enabled.
- `TaczaiConfigScreen`: builds and saves the Cloth Config UI.
- `Taczai`: registers client handlers and the Forge config-screen factory.

## Error and Lifecycle Handling

- Invalid persisted UUID strings are ignored and logged rather than crashing config load.
- Teammates remain saved when offline; their UUID entries can be removed from the config screen.
- Changing worlds clears only transient entity references, not the saved UUID set.
- Disabling auto-aim immediately removes enemy glow and stops teammate frame rendering.
- Server-provided glow and scoreboard teams remain untouched.

## Tests

Unit tests cover:

- the default and boundary behavior of a 20-degree angular predicate;
- local and server teammates being excluded from automatic targeting and enemy outlines;
- UUID parsing, deduplication, toggle add/remove, and persistence serialization;
- manual selection remaining independent of the FOV predicate;
- teammate frame qualification only while auto-aim is enabled;
- invalid UUID config entries being skipped.

Final verification runs the complete JUnit suite and ForgeGradle `clean build`, inspects the generated mod metadata for Cloth Config, and confirms the distributable reobfuscated JAR.
