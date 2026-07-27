package space.ajcool.paintbrush.family;

import com.conquestrefabricated.core.item.family.Family;
import com.conquestrefabricated.core.item.family.FamilyRegistry;
import com.google.gson.Gson;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry for family groups that handle Conquest Reforged's multifamily block variants.
 * Conquest splits some materials across multiple families (e.g., log, branch, beam).
 * This registry redirects paint operations to the correct sibling family based on the target block.
 * Loads configuration from family-groups.json during resource reloads.
 */
@Environment(EnvType.CLIENT)
public final class FamilyGroupRegistry {
    /** Logger for family group operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger("FamilyGroupRegistry");

    /** Placeholder text in anchor templates for the material name. */
    private static final String MATERIAL_PLACEHOLDER = "{material}";

    /** Compiled family groups loaded from the resource file. */
    private static List<CompiledGroup> groups = List.of();

    /**
     * Private constructor to prevent instantiation.
     */
    private FamilyGroupRegistry() {
    }

    /**
     * Loads family groups from the family-groups.json resource file.
     * Compiles templates and initializes the registry.
     */
    public static void load() {
        var id = new Identifier("paintbrush", "family-groups.json");
        Optional<Resource> resource = MinecraftClient.getInstance()
                .getResourceManager()
                .getResource(id);

        if (resource.isEmpty()) {
            groups = List.of();
            LOGGER.warn("Paintbrush - Could not find family-groups.json!");
            return;
        }

        try (InputStream stream = resource.get().getInputStream()) {
            var data = new Gson().fromJson(new InputStreamReader(stream), GroupData.class);
            if (data == null) {
                groups = List.of();
                LOGGER.warn("Paintbrush - family-groups.json contains no groups");
                return;
            }

            var compiledGroups = new ArrayList<CompiledGroup>();

            for (var group : data.groups) {
                if (group == null || group.families.isEmpty()) continue;

                var familyDefinitions = new ArrayList<CompiledFamilyDefinition>();

                for (int i = 0; i < group.families.size(); i++) {
                    var family = group.families.get(i);
                    if (family == null || family.anchors.isEmpty()) continue;

                    var familyIndex = familyDefinitions.size();
                    var anchors = new ArrayList<String>();
                    var templates = new ArrayList<CompiledTemplate>();

                    for (var anchor : family.anchors) {
                        if (anchor == null || anchor.isBlank()) continue;

                        anchors.add(anchor);
                        templates.add(compileTemplate(anchor, familyIndex));
                    }

                    if (!templates.isEmpty()) {
                        templates.sort(Comparator.comparingInt(CompiledTemplate::literalLength).reversed());
                        familyDefinitions.add(new CompiledFamilyDefinition(family.id, List.copyOf(anchors), List.copyOf(templates)));
                    }
                }

                if (!familyDefinitions.isEmpty()) {
                    var templates = familyDefinitions.stream()
                            .flatMap(family -> family.templates.stream())
                            .sorted(Comparator.comparingInt(CompiledTemplate::literalLength).reversed())
                            .toList();
                    compiledGroups.add(new CompiledGroup(group.name, List.copyOf(familyDefinitions), templates));
                }
            }

            groups = List.copyOf(compiledGroups);
            LOGGER.info("Paintbrush - Initialized {} family groups", groups.size());

        } catch (Exception e) {
            groups = List.of();
            LOGGER.warn("Paintbrush - Could not load family-groups.json!");
            LOGGER.error("Error during family group initialization", e);
        }
    }

    /**
     * Compiles a family group anchor template into a regex pattern.
     * Replaces {material} placeholders with regex capture groups.
     *
     * @param template the template string with {material} placeholders
     * @param index    the family index this template belongs to
     * @return a compiled template with the regex pattern
     */
    private static CompiledTemplate compileTemplate(String template, int index) {
        var builder = new StringBuilder("^");
        var literalLength = 0;
        var remaining = template;

        while (true) {
            var placeholderIndex = remaining.indexOf(MATERIAL_PLACEHOLDER);
            if (placeholderIndex < 0) break;

            var literal = remaining.substring(0, placeholderIndex);
            builder.append(Pattern.quote(literal));
            builder.append("([a-z0-9_]+)");
            literalLength += literal.length();
            remaining = remaining.substring(placeholderIndex + MATERIAL_PLACEHOLDER.length());
        }

        builder.append(Pattern.quote(remaining));
        builder.append("$");
        literalLength += remaining.length();

        return new CompiledTemplate(template, index, Pattern.compile(builder.toString()), literalLength);
    }

    /**
     * Redirects the paint family to a sibling family if both paint and target are in the same group.
     * For example, if painting oak from the log family onto a target in the beam family,
     * redirects to oak from the beam family.
     *
     * @param paintFamily  the family containing the paint material
     * @param targetFamily the family of the target block
     * @return the redirected paint family, or the original if no redirection is needed
     */
    public static Family<Block> redirect(Family<Block> paintFamily, Family<Block> targetFamily) {
        if (paintFamily == null || targetFamily == null) return paintFamily;

        for (var group : groups) {
            var paintMatch = group.match(paintFamily);
            if (paintMatch.isEmpty()) continue;

            var targetMatch = group.match(targetFamily);
            if (targetMatch.isEmpty()) continue;

            if (paintMatch.get().index == targetMatch.get().index) return paintFamily;

            var targetDefinition = group.families.get(targetMatch.get().index);

            for (var anchor : targetDefinition.anchors) {
                var redirectedFamilyId = anchor.replace(MATERIAL_PLACEHOLDER, paintMatch.get().material);
                Family<Block> redirectedFamily;

                try {
                    redirectedFamily = FamilyRegistry.BLOCKS.getFamily(new Identifier(redirectedFamilyId));
                } catch (Exception e) {
                    LOGGER.debug("Paintbrush - Invalid redirected family id {}", redirectedFamilyId);
                    continue;
                }

                if (redirectedFamily == null || redirectedFamily.getMembers().isEmpty()) continue;

                return redirectedFamily;
            }

            return paintFamily;

        }

        return paintFamily;
    }

    /**
     * Describes a block's family group membership.
     * Used for debugging to see what group and family a block belongs to.
     *
     * @param block the block to describe
     * @return an optional containing a description string, or empty if not in any group
     */
    public static Optional<String> describe(Block block) {
        var family = FamilyRegistry.BLOCKS.getFamily(block);
        if (family == null) return Optional.empty();

        for (var group : groups) {
            var match = group.match(family);
            if (match.isEmpty()) continue;

            var familyId = group.families.get(match.get().index).id;
            var description = String.format("%s %s material=%s anchor=%s", group.name, familyId, match.get().material, match.get().memberId);
            return Optional.of(description);
        }

        return Optional.empty();
    }

    /**
     * Deserialized data structure for family groups from JSON.
     */
    private static class GroupData {
        /** List of group definitions. */
        public final List<GroupDefinition> groups = List.of();
    }

    /**
     * Deserialized data structure for a single family group definition.
     */
    @SuppressWarnings("unused")
    private static class GroupDefinition {
        /** Name of the group (e.g., "wood", "stone"). */
        public final String name = "";

        /** Families within this group. */
        public final List<FamilyDefinition> families = List.of();
    }

    /**
     * Deserialized data structure for a family definition within a group.
     */
    private static class FamilyDefinition {
        /** Unique identifier for this family type. */
        public final String id = "";

        /** Anchor templates for identifying family members (with {material} placeholders). */
        public final List<String> anchors = List.of();
    }

    /**
     * Compiled family group with regex patterns and caching.
     */
    private static class CompiledGroup {
        /** Name of the group. */
        private final String name;

        /** Compiled family definitions in this group. */
        private final List<CompiledFamilyDefinition> families;

        /** All compiled templates sorted by literal length for efficient matching. */
        private final List<CompiledTemplate> templates;

        /** Cache for matching results to avoid recomputation. */
        private final Map<Family<Block>, Optional<FamilyMatch>> matchCache = new IdentityHashMap<>();

        /**
         * Creates a compiled group.
         *
         * @param name      the group name
         * @param families  the compiled family definitions
         * @param templates the compiled templates
         */
        private CompiledGroup(String name, List<CompiledFamilyDefinition> families, List<CompiledTemplate> templates) {
            this.name = name;
            this.families = families;
            this.templates = templates;
        }

        /**
         * Finds a family match within this group.
         * Uses caching to avoid repeated regex matching.
         *
         * @param family the family to match
         * @return an optional containing the match details, or empty if no match
         */
        public Optional<FamilyMatch> match(Family<Block> family) {
            if (matchCache.containsKey(family)) return matchCache.get(family);

            Optional<FamilyMatch> result = Optional.empty();

            for (var member : family.getMembers()) {
                var id = Registries.BLOCK.getId(member).toString();

                for (var template : templates) {
                    var match = template.match(id);
                    if (match.isEmpty()) continue;

                    if (result.isPresent()) {
                        LOGGER.debug("Paintbrush - Family member id {} matched multiple templates in group {}", id, name);
                        matchCache.put(family, result);
                        return result;
                    }

                    result = match;
                }
            }

            matchCache.put(family, result);
            return result;
        }
    }

    /**
     * Compiled family definition with anchor templates.
     *
     * @param id        the family identifier
     * @param anchors   the raw anchor template strings
     * @param templates the compiled regex templates
     */
    private record CompiledFamilyDefinition(String id, List<String> anchors, List<CompiledTemplate> templates) {
    }

    /**
     * Compiled regex template for matching family member block IDs.
     *
     * @param template      the original template string
     * @param index         the family index this template belongs to
     * @param pattern       the compiled regex pattern
     * @param literalLength the number of non-placeholder characters in the template
     */
    private record CompiledTemplate(String template, int index, Pattern pattern, int literalLength) {
        /**
         * Attempts to match a block ID against this template.
         *
         * @param id the block ID to match
         * @return an optional containing the match details, or empty if no match
         */
        public Optional<FamilyMatch> match(String id) {
            Matcher matcher = pattern.matcher(id);
            if (!matcher.matches()) return Optional.empty();

            var material = matcher.groupCount() > 0 ? matcher.group(1) : "";
            return Optional.of(new FamilyMatch(index, material, id));
        }
    }

    /**
     * Result of matching a block ID against a family group.
     *
     * @param index    the family index within the group
     * @param material the extracted material name from the block ID
     * @param memberId the full block ID that was matched
     */
    private record FamilyMatch(int index, String material, String memberId) {
    }
}
