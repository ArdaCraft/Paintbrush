# Paintbrush 1.3.0 - ALPHA

**Paintknife layering**:
- Layer stacking: a block turns into a full cube at *max layers + 1* (and the reverse when going down).
- **Deletion**: a primary click on a layer-1 block removes it (off by default).
- **Append**: a secondary click on a full block places a new layer-1 slab in the adjacent space you clicked, if that space is replaceable (off by default).
- **Full-block promotion**: a near-full layer/slab is promoted to the real full block of the same Conquest family. This is now a three-way setting instead of on/off - `ALL`, `PARTIAL` (default), `NONE`. `PARTIAL` only promotes shapes that don't already visually fill a cube (*vertical_slab* for example).

**Linked family groups.** Some Conquest materials are split across several families that are the same material in different shapes - logs, branches, beams, and capital/column/cornice sets. Paintbrush can now link them, so painting a birch log material onto an oak branch finds the birch *branch*, not nothing. The included `family-groups.json` links wood logs/branches/beams and capitals/columns/cornices. Overridable via RP.

**Foliage filtering (press `N`).** With the paintbrush or paint knife in hand, filtered blocks become invisible to targeting: the crosshair, copy, paint, and knife actions pass straight through grass, leaves, bushes, branches, saplings, and flowers to the block behind. Large brush strokes also skip filtered blocks inside the volume, so you can paint a wall through undergrowth without wrecking it. A HUD indicator shows when it is active. The filter list supports both name substrings and block tags (`#namespace:id`).

**Wireframe preview when painting hidden blocks.** The brush volume and the target block are outlined so you can see what you're about to paint when it's occluded.

**No more accidental door/lever clicks.** While holding the paintbrush or paint knife, block activation is suppressed. Enabled by default; toggle with `/pb blocktoggles toggle`.

## Fixes

- **Plot and permission issues.** Race condition fix.
- **FAWE / block desync.** The WorldEdit session is now resolved by player rather than by name, which fixes edits being dropped or not syncing back under FAWE. If no session exists, you now get an explicit "No WorldEdit session - edit dropped" message instead of nothing happening. `//undo` continues to work on paintbrush edits.
- **Paint knife cooldown** is only applied when an edit was actually sent, so failed clicks no longer eat a cooldown.
- **Cornice bug**: painting a cornice with a different material now carries over the block's properties.
- Oversized/malformed paint packets are rejected server-side.

## New and changed commands

Paint knife (`/paintknife` or `/pk`):

| Command | Effect                                              |
|---|-----------------------------------------------------|
| `/pk` | Gives a paint knife and prints the current settings |
| `/pk toggle` | Toggles delete and append together                  |
| `/pk delete` | Toggles layer-1 deletion (default: off)             |
| `/pk append` | Toggles appending layer-1 slabs (default: off)      |
| `/pk fullblocks` | Cycles `ALL` -> `PARTIAL` -> `NONE`                  |
| `/pk fullblocks <all\|partial\|none>` | Sets the mode directly (default: `PARTIAL`)         |
| `/pk debug` | Toggles paint knife chat diagnostics                |

Paintbrush (`/paintbrush` or `/pb`):

| Command | Effect |
|---|---|
| `/pb filter` / `/pb filter toggle` | Shows / toggles foliage filtering (also `N`) |
| `/pb blocktoggles` / `/pb blocktoggles toggle` | Shows / toggles block-activation suppression |
| `/pb debug showFamily` | Reports the targeted block's family, member count, and which linked group and anchor matched - use this when authoring `family-groups.json` |

`/pb`, `/pb hand`, `/pb size <1-5>`, and `/pb debug showTokens` are unchanged.

## Configuration

Settings are per-client and live in **`config/paintbrush/paintbrush.json`** 

```json
{
  "paintknifeAllowDelete": false,
  "paintknifeAllowAppend": false,
  "paintknifeFullBlocks": "PARTIAL",
  "paintknifeDebug": false,
  "filterFoliage": false,
  "disableBlockToggles": true
}
```