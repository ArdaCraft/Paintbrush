package space.ajcool.paintbrush.item;

import com.conquestrefabricated.content.blocks.block.Slab;
import com.conquestrefabricated.core.item.family.Family;
import com.conquestrefabricated.core.item.family.FamilyRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import space.ajcool.paintbrush.Paintbrush;

import java.util.*;

public class PaintbrushItem extends Item
{
    public PaintbrushItem(Settings settings)
    {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand)
    {
        ItemStack itemStack = user.getStackInHand(hand);
        return TypedActionResult.consume(itemStack);
    }

    /**
     * Given a target block and a paint family, filter down the family and extract the best candidates matching the
     * target block. This method will try to return a single result
     * @param targetBlockState the reference block from which to find a counterpart in the family
     * @param paintFamily the paint family from which to find a counterpart
     * @return a list of matching blocks for the given target.
     */
    private List<Block> filterBlockCandidatesFromPaintFamily(BlockState targetBlockState, Family<Block> paintFamily)
    {

        var targetBlockId = ConquestDisambiguation(Registries.BLOCK.getId(targetBlockState.getBlock()).toString());

        var targetBlockIdSplit = targetBlockId.split(":");
        var idParts = targetBlockIdSplit[1].split("_");

        var layerMismatch = false;

        Set<Block> matches = new HashSet<>();
        List<Block> filteredFamilyMembers = paintFamily.getMembers();

        // Operational Steps
        // 1. Split the material string by underscores
        // 2. Starting from the end, check for matches between different block families
        // 3. If a match is found, add it to the return list
        // 4. Iterate on the filtered down list
        for (int i = idParts.length - 1; i >= 0; i--) {

            boolean newAddition = false;

            StringBuilder stringBuilder = new StringBuilder();
            for (int j = i; j < idParts.length; j++) {
                stringBuilder.append("_").append(idParts[j]);
            }
            var endMatch = stringBuilder.toString();

            // Check for separate matches for both conquest and minecraft blocks.
            // We want to prioritize conquest blocks if two blocks have the same ending type.
            // ie. minecraft:...cobble_slab & conquest:...cobble_slab
            // The conquest version supports layers, the minecraft version does not.
            for (Block paintFamilyMember : filteredFamilyMembers) {
                var rawMemberId = Registries.BLOCK.getId(paintFamilyMember).toString();
                if (ConquestDisambiguation(rawMemberId).endsWith(endMatch))
                {
                    matches.add(paintFamilyMember);
                    newAddition = true;
                }
            }

            if (newAddition)
            {
                filteredFamilyMembers = new ArrayList<>(matches);
                matches.clear();
            }
            else break;
        }

        // If there are more than one result, filter run the filter forward
        // 1. Starting from the beginning, check for matches between different block families
        // 2. If a match is found, add it to the return list
        // 3. Iterate on the filtered down list
        if (filteredFamilyMembers.size() > 1)
        {
            for (int i = 0; i < idParts.length - 1; i++)
            {
                boolean newAddition = false;

                StringBuilder stringBuilder = new StringBuilder();
                for (int j = 0; j < i+1; j++) {
                    stringBuilder.append(idParts[j]).append(("_"));
                }

                var startMatch = stringBuilder.toString();

                for (Block paintFamilyMember : filteredFamilyMembers) {
                    var rawMemberId = Registries.BLOCK.getId(paintFamilyMember).toString();

                    var rawMemberIdSplit = ConquestDisambiguation(rawMemberId).split(":");

                    if (rawMemberIdSplit[1].startsWith(startMatch))
                    {
                        matches.add(paintFamilyMember);
                        newAddition = true;
                    }
                }

                if (newAddition)
                {
                    filteredFamilyMembers = new ArrayList<>(matches);
                    matches.clear();
                }
                else break;
            }
        }

        return filteredFamilyMembers;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext itemUsageContext)
    {
        var world = itemUsageContext.getWorld();
        if (!world.isClient()) return ActionResult.CONSUME;

        var player = itemUsageContext.getPlayer();
        if (player == null) return ActionResult.FAIL;

        var blockPos = itemUsageContext.getBlockPos();
        if (!world.canSetBlock(blockPos)) return ActionResult.FAIL;

        var itemStack = itemUsageContext.getStack();
        var paintNbt = itemStack.getOrCreateSubNbt("paintbrush");

        var brushSize = paintNbt.contains("size") ? paintNbt.getInt("size") : 1;
        var positions = new ArrayList<BlockPos>();

        if (brushSize == 1) positions.add(blockPos);

        if (brushSize > 1)
        {
            brushSize -= 1;

            for(int x = -brushSize; x <= brushSize; x++) {
                for(int y = -brushSize; y <= brushSize; y++) {
                    for(int z = -brushSize; z <= brushSize; z++) {
                        if(x * x + y * y + z * z <= brushSize * brushSize) {
                            positions.add(blockPos.add(x, y, z));
                        }
                    }
                }
            }
        }

        var blockStates = new HashMap<BlockPos, BlockState>();;

        for(BlockPos pos : positions) {

            var targetBlockState = world.getBlockState(pos);

            if (!world.canSetBlock(pos) || targetBlockState.isAir() || targetBlockState.isLiquid()) continue;

            BlockState paintBlockState = null;

            if (paintNbt.contains("state"))
            {
                // Paintbrush has a full block state, no further processing required.
                var state = paintNbt.getCompound("state");
                RegistryWrapper<Block> registryEntryLookup = player.getWorld() != null ? player.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK) : Registries.BLOCK.getReadOnlyWrapper();
                paintBlockState = NbtHelper.toBlockState(registryEntryLookup, state);
            }
            else
            {
                var material = paintNbt.getString("material");
                var paintIdentifier = new Identifier(material);

                // Get the conquest family of the paint material
                var paintFamily = FamilyRegistry.BLOCKS.getFamily(paintIdentifier);

                // Get the conquest family of the target material
                var targetFamily = FamilyRegistry.BLOCKS.getFamily(targetBlockState.getBlock());

                // If the blocks are from the same family, lets not do anything.
                if (targetFamily.getRoot().equals(paintFamily.getRoot())) continue;

                // Use paint default state if the target block has no members or if it is the root of the family
                if (targetFamily.getMembers().isEmpty() || paintFamily.getMembers().isEmpty())
                {
                    RegistryWrapper<Block> registryEntryLookup = player.getWorld() != null ? player.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK) : Registries.BLOCK.getReadOnlyWrapper();

                    paintBlockState = Registries.BLOCK.get(paintIdentifier).getDefaultState();
                }
                else if (targetBlockState.getBlock().equals(targetFamily.getRoot()))
                {
                    paintBlockState = paintFamily.getRoot().getDefaultState();
                }
                /*
                 Find a match between the target block and the paint family
                 copy its properties, account for edge-cases, and set the new block state
                 */
                else
                {
                    var layerMismatch = false;

                    /*
                     Identify a list of block candidates for painting in a block family, then isolate a prime candidate
                     with priority conquest>other RP>minecraft
                     */
                    List<Block> matchingBlocks = filterBlockCandidatesFromPaintFamily(targetBlockState, paintFamily);
                    Block matchingBlock = identifyBlockCandidate(matchingBlocks);

                    // If at least a conquest block or a minecraft block has been found proceed with setup
                    if (matchingBlock != null) {

                        paintBlockState = matchingBlock.getStateWithProperties(targetBlockState);

                        var matchingBlockId = Registries.BLOCK.getId(matchingBlock).toString();
                        Property<?> typeKey = targetBlockState.getBlock().getStateManager().getProperty("type");

                        if (typeKey != null) {

                            Comparable<?> typeValue = targetBlockState.get(typeKey);

                            if (typeValue.toString().equals("double")) {
                                paintBlockState = paintFamily.getRoot().getDefaultState();
                            } else if (matchingBlockId.endsWith("layer")) {
                                if (typeValue.toString().equals("bottom")
                                        && targetBlockState.getBlock().getStateManager().getProperty("layers") == null
                                        && targetBlockState.getBlock().getStateManager().getProperty("layer") == null) {
                                    paintBlockState = setLayerBlockState(paintBlockState);
                                } else if (typeValue.toString().equals("top")) {
                                    // If the target block has type=top, we can't use a layer-type with it.
                                    paintBlockState = null;
                                    layerMismatch = true;
                                }
                            } else if (matchingBlockId.endsWith("slab")
                                    && targetBlockState.getBlock().getStateManager().getProperty("layers") == null
                                    && targetBlockState.getBlock().getStateManager().getProperty("layer") == null) {
                                var tempState = paintBlockState.with(Slab.TYPE_UPDOWN, typeValue.toString().equals("bottom") ? BlockHalf.BOTTOM : BlockHalf.TOP);
                                paintBlockState = setLayerBlockState(tempState);
                            }
                        }

                        if (paintBlockState != null) {

                            // Conquest uses two naming schemes for layered blocks, lets check if we're mismatching and convert between the two.
                            var forwardLayerMismatch = targetBlockState.getBlock().getStateManager().getProperty("layers") != null && paintBlockState.getBlock().getStateManager().getProperty("layer") != null;
                            var backwardLayerMismatch = targetBlockState.getBlock().getStateManager().getProperty("layer") != null && paintBlockState.getBlock().getStateManager().getProperty("layers") != null;

                            if (forwardLayerMismatch || backwardLayerMismatch) {

                                var targetKey = targetBlockState.getBlock().getStateManager().getProperty(forwardLayerMismatch ? "layers" : "layer");
                                Integer targetValue = (Integer) targetBlockState.get(targetKey);

                                if (forwardLayerMismatch && targetValue == 8)
                                    paintBlockState = paintFamily.getRoot().getDefaultState();
                                else if (forwardLayerMismatch && (targetValue == 3 || targetValue == 5 || targetValue > 6)) {
                                    paintBlockState = null;
                                    layerMismatch = true;
                                } else {
                                    if (forwardLayerMismatch && targetValue == 4) targetValue = 3;
                                    if (forwardLayerMismatch && targetValue == 6) targetValue = 4;

                                    if (backwardLayerMismatch && targetValue == 4) targetValue = 6;
                                    if (backwardLayerMismatch && targetValue == 3) targetValue = 4;

                                    var paintKey = (IntProperty) paintBlockState.getBlock().getStateManager().getProperty(forwardLayerMismatch ? "layer" : "layers");
                                    paintBlockState = paintBlockState.with(paintKey, targetValue);
                                }
                            }
                        }
                    }

                    // Handle error messages if block is not found.
                    if (paintBlockState == null && world.isClient && brushSize == 1)
                    {
                        net.minecraft.text.MutableText errorMessage;

                        if (!layerMismatch)
                        {
                            errorMessage = Text.empty()
                                    .append(Text.literal("Paintbrush:").formatted(Formatting.DARK_AQUA))
                                    .append(Text.literal(" Target model ").formatted(Formatting.DARK_GRAY))
                                    .append(Text.literal(Registries.BLOCK.getId(targetBlockState.getBlock()).toString()).formatted(Formatting.GRAY))
                                    .append(Text.literal(" can not be found in the family of ").formatted(Formatting.DARK_GRAY))
                                    .append(Text.literal(material).formatted(Formatting.GRAY))
                                    .append(Text.literal(". ").formatted(Formatting.DARK_GRAY));

                        }
                        else
                        {
                            errorMessage = Text.empty()
                                    .append(Text.literal("Paintbrush:").formatted(Formatting.DARK_AQUA))
                                    .append(Text.literal(" Target block layer is not supported by the selected paint material").formatted(Formatting.DARK_GRAY))
                                    .append(Text.literal(". ").formatted(Formatting.DARK_GRAY));

                        }

                        player.sendMessage(errorMessage);
                    }
                }
            }

            if (paintBlockState == null) continue;
            blockStates.put(pos, paintBlockState);
        }

        if (blockStates.isEmpty())
        {
            Paintbrush.LOGGER.info("Block state is null.");
            return ActionResult.FAIL;
        }

        /*
        SET_BLOCK_PACKET_ID

        1. **Size**: The packet begins with the total number of block entries.

        2. **Block Entries**: The rest of the packet contains pairs of block entries. Each pair consists of:

            - **Block Position**: Representing the location of the block.
            - **Block State**: The corresponding state of the block in NBT format.
         */
        var packetBuffer = PacketByteBufs.create();

        packetBuffer.writeInt(blockStates.size());

        for (Map.Entry<BlockPos, BlockState> kvpEntry : blockStates.entrySet())
        {
            packetBuffer.writeBlockPos(kvpEntry.getKey());
            packetBuffer.writeNbt(NbtHelper.fromBlockState(kvpEntry.getValue()));
        }

        ClientPlayNetworking.send(Paintbrush.SET_BLOCK_PACKET_ID, packetBuffer);

        player.playSound(SoundEvents.BLOCK_SLIME_BLOCK_PLACE, SoundCategory.BLOCKS, .2F, 1.0F);

        return ActionResult.CONSUME;
    }

    /**
     * Identifies a block candidate for painting given a list of blocks. Will prioritize conquest blocks over other
     * resource packs mods, otherwise defaults to minecraft block
     * @param matchingBlocks a list of potential candidate
     * @return the extracted block candidate
     */
    private Block identifyBlockCandidate(List<Block> matchingBlocks) {

        // Extract matching minecraft blocks
        List<Block> matchingMinecraftBlocks = matchingBlocks.stream()
                .filter(block -> Registries.BLOCK.getId(block).toString().startsWith("minecraft"))
                .toList();

        // Extract matching conquest blocks
        List<Block> matchingConquestBlocks = matchingBlocks.stream()
                .filter(block -> Registries.BLOCK.getId(block).toString().startsWith("conquest"))
                .toList();

        // Extract matching blocks from other sources
        List<Block> matchingOtherBlocks = matchingBlocks.stream()
                .filter(block -> !Registries.BLOCK.getId(block).toString().startsWith("conquest") &&
                        !Registries.BLOCK.getId(block).toString().startsWith("minecraft"))
                .toList();


        Block matchingBlock;

        /*
            Prioritize conquest,
            else check for another resource pack block
            otherwise check for a minecraft block.
         */
        if (matchingConquestBlocks.size() == 1)
            matchingBlock = matchingConquestBlocks.get(0);
        else if (matchingOtherBlocks.size() == 1)
            matchingBlock = matchingOtherBlocks.get(0);
        else
            matchingBlock = matchingMinecraftBlocks.get(0);

        return  matchingBlock;
    }

    private BlockState setLayerBlockState(BlockState paintBlockState)
    {
        var paintKey = (IntProperty) paintBlockState.getBlock().getStateManager().getProperty("layer");

        if (paintKey != null) paintBlockState = paintBlockState.with(paintKey, 3);
        else
        {
            paintKey = (IntProperty) paintBlockState.getBlock().getStateManager().getProperty("layers");
            if (paintKey != null) paintBlockState = paintBlockState.with(paintKey, 4);
        }

        return paintBlockState;
    }

    private String ConquestDisambiguation(String input)
    {
        return input
                .replaceAll("layer", "slab")
                .replaceAll("small_arch_half", "smallarchhalf")
                .replaceAll("small_arch", "smallarch")
                .replaceAll("segmental_arch", "segmentalarch")
                .replaceAll("two_meter_arch", "twometerarch")
                .replaceAll("two_meter_arch_half", "twometerarchhalf")
                .replaceAll("gothic_arch", "gothicarch")
                .replaceAll("round_arch", "roundarch")
                .replaceAll("two_meter_arch", "twometerarch")
                .replaceAll("small_window", "smallwindow")
                .replaceAll("small_window_half", "smallwindowhalf")
                .replaceAll("quarter_slab", "quarterslab")
                .replaceAll("corner_slab", "cornerslab")
                .replaceAll("eighth_slab", "eighthslab")
                .replaceAll("two_meter_arch_half", "twometerarchhalf")
                .replaceAll("vertical_corner_slab", "verticalcornerslab")
                .replaceAll("vertical_slab", "verticalslab")
                .replaceAll("vertical_corner", "verticalcorner")
                .replaceAll("vertical_quarter", "verticalquarter");
    }
}
