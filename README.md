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

## Credits

***Credit to Monsterfish_ for the paintbrush and paint knife textures.***
