# Conquest Reforged Paintbrush

Conquest Reforged Paintbrush adds a creative paintbrush that allows the user to "copy" and "paste" the material of a block onto another block while maintaining its variant and properties. It also adds a paint knife which allows the user to change the layer properties of a targeted block.

## Usage

`pb` or `paintbrush` to spawn a paintbrush tool in the inventory.

### Primary click:

- With a **paintbrush** to copy the targeted block's material to the **paintbrush**.
- With a **paint knife** will decrease the layer of a targeted block if it has layer properties.
- With a **paint knife** on a layer-1 block will remove it when paint knife deletion is enabled.

### Secondary click:

- With a **paintbrush** to paint the selected material onto the targeted block. This attempts to find the same block variant in a material's Conquest family, including configured linked family groups such as logs, branches, and beams (see [Configuration](#configuration)).
- With a **paint knife** will increase the layer of a targeted block if it has layer properties.
- With a **paint knife** on a near-full layer/slab block will promote it to the full block in the same Conquest family.
- With a **paint knife** on a full block will append a layer-1 slab in the clicked adjacent space when paint knife append is enabled and the target space is replaceable.

### Hold "Left Ctrl":

- With a **paintbrush** while selecting a material to select in strict mode. All painted blocks will be an exact copy.
- With a **paint knife** will change layers for a block adjacent to the blockface targeted.

### Paint knife commands

- `paintknife` or `pk` shows the current paint knife settings.
- `paintknife allow delete` or `pk allow delete` toggles layer-1 block deletion.
- `paintknife allow append` or `pk allow append` toggles appending layer-1 slabs from full blocks.

Both paint knife settings default to disabled and are stored in `config/paintbrush.json`:

```json
{
  "paintknifeAllowDelete": false,
  "paintknifeAllowAppend": false
}
```

**Crouching** will show the material that is currently selected on a paintbrush.

## Configuration

The algorithm used while painting with the secondary click uses a token matching mechanic. A given paint block and its target are tokenized using their translation key : `Brown Oven Tiles Corner Slab` becomes `Corner` and `Slab`.


Given Conquest naming structure, relevant tokens are identified by declaring them in the [tokens.json](src/main/resources/assets/paintbrush/tokens.json) file. This allows to remove the discriminant during paint and target matching : `Brown Oven Tiles Corner Slab` (`Cobble`, `Slab`) = `Deepslate Tiles Corner Slab` (`Cobble`, `Slab`)  

Some blocks may contain a matching token in their names : `Red Brown Vertical Wood Plank Vertical Slab` will resolve as `Vertical`, `Vertical` and `Slab`. The **reserved_names** section of the json file exists to manage this cases. Adding `Red Brown Vertical Wood Plank` to this section will resolve as `Vertical` and `Slab`.

The tokens.json file supports expansion using `( )`, any characters in parenthesis will resolve into two distinct tokens or reserved name : `board(s)` expands into `board` and `boards`  

### Linked family groups

Some Conquest materials are split across multiple block families even though they are the same material in different shapes. For example, logs, branches, and beams can each have their own family. Paintbrush can link these families through [family-groups.json](src/main/resources/assets/paintbrush/family-groups.json), so selecting one shape can paint the corresponding shape in a sibling family.

```json
{
  "groups": [
    {
      "name": "wood_logs_branches_beams",
      "families": [
        {
          "id": "logs",
          "anchors": [
            "conquest:{material}_log_vertical_slab",
            "conquest:{material}_log_slab",
            "conquest:{material}_log_pillar"
          ]
        },
        {
          "id": "branches",
          "anchors": [
            "conquest:{material}_branch_tip",
            "conquest:thick_diagonal_{material}_branch_22"
          ]
        },
        {
          "id": "beams",
          "anchors": [
            "conquest:{material}_wood_beam_wall",
            "conquest:{material}_wood_beam"
          ]
        }
      ]
    }
  ]
}
```

Each family entry has a display `id` and one or more concrete block-id `anchors`. Anchors are matched against any member of a family, not the family root, because Conquest family roots are registration-order artifacts. `{material}` captures the material name and is substituted into the target family's anchors. For example, when painting with a birch log material over an oak branch, Paintbrush can redirect from the birch log family to the birch branch family before the normal token matching runs.

Anchors without `{material}` are allowed and match literal block ids. This lets resource packs link one-off families without adding code.

The included `family-groups.json` links wood logs, branches, and beams. Verify custom resource-pack changes in-game with `/pb debug showFamily`, then reload resources with `F3+T`.

### Debug commands

- `pb debug` or `paintbrush debug` toggles the logging of information during pattern matching : 
```
[21:41:12] [Render thread/INFO] (Minecraft) [System] [CHAT] Debug output enabled
[21:41:17] [Render thread/INFO] (paintbrush) Looking for match of "Smooth Inscribed Black Painted Block Vertical Slab" [[vertical, slab]] in paint family : 
[21:41:17] [Render thread/INFO] (paintbrush) - "Light Limestone Brick" [[]]
- "Light Limestone Brick Small Arch" [[small, arch]]
- "Light Limestone Brick Small Arch Half" [[small, arch, half]]
- "Light Limestone Brick Two Meter Arch" [[two, meter, arch]]
- "Light Limestone Brick Arrowslit" [[arrowslit]]
- "Light Limestone Brick Vertical Slab" [[vertical, slab]]
- "Light Limestone Brick Vertical Corner" [[vertical, corner]]
- "Light Limestone Brick Vertical Quarter" [[vertical, quarter]]
...
```

- `pb debug showTokens` or `paintbrush debug showTokens` logs the current loaded tokens and reserved names in the console.

- `pb debug showFamily` or `paintbrush debug showFamily` logs and chats the targeted block id, its family root id, member count, linked family group match, and the anchor member that matched. Use this when authoring `family-groups.json`.

### Developers Notes


Server and clients requires the following mods to be installed to work properly in a dev environment :

```gradle
[animatica-0.6.1+1.20.4.jar](runServer/mods/animatica-0.6.1%2B1.20.4.jar)
[ArdaGrass-1.2-1.20.1.jar](runServer/mods/ArdaGrass-1.2-1.20.1.jar)
[cloth-config-11.1.136-fabric.jar](runServer/mods/cloth-config-11.1.136-fabric.jar)
[ConquestArchitects-1.0.2-1.20.1.jar](runServer/mods/ConquestArchitects-1.0.2-1.20.1.jar)
[ConquestHearthfire-1.0.3-1.20.1.jar](runServer/mods/ConquestHearthfire-1.0.3-1.20.1.jar)
[ConquestReforged-fabric-1.20.1-1.4.1.4.jar](runServer/mods/ConquestReforged-fabric-1.20.1-1.5.0)
[continuity-3.0.0+1.20.1.jar](runServer/mods/continuity-3.0.0%2B1.20.1.jar)
[entity_model_features_1.20.1-fabric-3.0.1.jar](runServer/mods/entity_model_features_1.20.1-fabric-3.0.1.jar)
[entity_texture_features_1.20.1-fabric-7.0.2.jar](runServer/mods/entity_texture_features_1.20.1-fabric-7.0.2.jar)
[fabricskyboxes-0.7.3+mc1.20.1-custom.jar](runServer/mods/fabricskyboxes-0.7.3%2Bmc1.20.1-custom.jar)
[polytone-1.20-3.5.9-fabric.jar](runServer/mods/polytone-1.20-3.5.9-fabric.jar)
[worldedit-mod-7.2.15.jar](runServer/mods/worldedit-mod-7.2.15.jar)
``` 

<details>

#### If using Conquest Reforged 1-1.4.1.4

Additionally *on a local environment* the following files from **ConquestReforged-fabric-1.20.1-1.4.1.4.jar** should be modified as following to force the usage of 
proper mixins (handled by the clients modloader in a production environment) :

- File : **./Refabricated-fabric.mixins.json**, `"refmap": "conquest.refmap.json"` should point to `"refmap": "ConquestReforged-fabric-1.20.1-fabric-refmap.json"`
- File : **./Refabricated.mixins.json**, `"refmap": "ConquestReforged-common-1.20.1-common-refmap.json"` should point to `"refmap": "ConquestReforged-fabric-1.20.1-fabric-refmap.json"`

</details>


## Credits

***Credit to Monsterfish_ for the paintbrush and paint knife textures.***
***Credit to Ajcool & Paul for developing the mod.***
