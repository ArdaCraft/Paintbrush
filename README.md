# Conquest Reforged Paintbrush

Conquest Reforged Paintbrush adds a creative paintbrush that allows the user to "copy" and "paste" the material of a block onto another block while maintaining it's variant and properties. It also adds a paintknife which allows the user to change the layer properties of a targeted block.
Usage

## Usage

`pb` or `paintbrush` to spawn a paintbrush tool in the inventory.

### Primary click:

- With a **paintbrush** to copy the targeted block's material to the **paintbrush**.
- With a **paintknife** will decrease the layer of a targeted block if it has layer properties.

### Secondary click:

- With a **paintbrush** to paint the selected material onto the targeted block. This attempts to find the same block variant in a material's conquest family (see [Configuration](#Configuration)).
- With a **paintknife** will increase the layer of a targeted block if it has layer properties.

### Hold "Left Ctrl":

- With a **paintbrush** while selecting a material to select in strict mode. All painted blocks will be an exact copy.
- With a **paintknife** will change layers for a block adjacent to the blockface targeted.

**Crouching** will show the material that is currently selected on a paintbrush.

## Configuration

The algorithm used while painting with the secondary click uses a token matching mechanic. A given paint block and its target are tokenized using their translation key : `Brown Oven Tiles Corner Slab` becomes `Corner` and `Slab`.


Given Conquest naming structure, relevant tokens are identified by declaring them in the [tokens.json](src/main/resources/assets/paintbrush/tokens.json) file. This allows to remove the discriminant during paint and target matching : `Brown Oven Tiles Corner Slab` (`Cobble`, `Slab`) = `Deepslate Tiles Corner Slab` (`Cobble`, `Slab`)  

Some blocks may contain a matching token in their names : `Red Brown Vertical Wood Plank Vertical Slab` will resolve as `Vertical`, `Vertical` and `Slab`. The **reserved_names** section of the json file exists to manage this cases. Adding `Red Brown Vertical Wood Plank` to this section will resolve as `Vertical` and `Slab`.

The tokens.json file supports expansion using `( )`, any characters in parenthesis will resolve into two distinct tokens or reserved name : `board(s)` expands into `board` and `boards`  

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
