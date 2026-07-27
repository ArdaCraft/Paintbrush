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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public final class FamilyGroupRegistry
{
    private static final Logger LOGGER = LoggerFactory.getLogger("FamilyGroupRegistry");
    private static final String MATERIAL_PLACEHOLDER = "{material}";
    private static List<CompiledGroup> groups = List.of();

    private FamilyGroupRegistry()
    {
    }

    public static void load()
    {
        var id = new Identifier("paintbrush", "family-groups.json");
        Optional<Resource> resource = MinecraftClient.getInstance()
                .getResourceManager()
                .getResource(id);

        if (resource.isEmpty())
        {
            groups = List.of();
            LOGGER.warn("Paintbrush - Could not find family-groups.json!");
            return;
        }

        try (InputStream stream = resource.get().getInputStream())
        {
            var data = new Gson().fromJson(new InputStreamReader(stream), GroupData.class);
            if (data == null || data.groups == null)
            {
                groups = List.of();
                LOGGER.warn("Paintbrush - family-groups.json contains no groups");
                return;
            }

            var compiledGroups = new ArrayList<CompiledGroup>();

            for (var group : data.groups)
            {
                if (group == null || group.families == null || group.families.isEmpty()) continue;

                var familyDefinitions = new ArrayList<CompiledFamilyDefinition>();

                for (int i = 0; i < group.families.size(); i++)
                {
                    var family = group.families.get(i);
                    if (family == null || family.anchors == null || family.anchors.isEmpty()) continue;

                    var familyIndex = familyDefinitions.size();
                    var anchors = new ArrayList<String>();
                    var templates = new ArrayList<CompiledTemplate>();

                    for (var anchor : family.anchors)
                    {
                        if (anchor == null || anchor.isBlank()) continue;

                        anchors.add(anchor);
                        templates.add(compileTemplate(anchor, familyIndex));
                    }

                    if (!templates.isEmpty())
                    {
                        templates.sort(Comparator.comparingInt(CompiledTemplate::literalLength).reversed());
                        familyDefinitions.add(new CompiledFamilyDefinition(family.id == null ? "" : family.id, List.copyOf(anchors), List.copyOf(templates)));
                    }
                }

                if (!familyDefinitions.isEmpty())
                {
                    var templates = familyDefinitions.stream()
                            .flatMap(family -> family.templates.stream())
                            .sorted(Comparator.comparingInt(CompiledTemplate::literalLength).reversed())
                            .toList();
                    compiledGroups.add(new CompiledGroup(group.name == null ? "" : group.name, List.copyOf(familyDefinitions), templates));
                }
            }

            groups = List.copyOf(compiledGroups);
            LOGGER.info("Paintbrush - Initialized {} family groups", groups.size());
        }
        catch (Exception e)
        {
            groups = List.of();
            LOGGER.warn("Paintbrush - Could not load family-groups.json!");
            LOGGER.error("Error during family group initialization", e);
        }
    }

    public static Family<Block> redirect(Family<Block> paintFamily, Family<Block> targetFamily)
    {
        if (paintFamily == null || targetFamily == null) return paintFamily;

        for (var group : groups)
        {
            var paintMatch = group.match(paintFamily);
            if (paintMatch.isEmpty()) continue;

            var targetMatch = group.match(targetFamily);
            if (targetMatch.isEmpty()) continue;

            if (paintMatch.get().index == targetMatch.get().index) return paintFamily;

            var targetDefinition = group.families.get(targetMatch.get().index);

            for (var anchor : targetDefinition.anchors)
            {
                var redirectedFamilyId = anchor.replace(MATERIAL_PLACEHOLDER, paintMatch.get().material);
                Family<Block> redirectedFamily;

                try
                {
                    redirectedFamily = FamilyRegistry.BLOCKS.getFamily(new Identifier(redirectedFamilyId));
                }
                catch (Exception e)
                {
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

    public static Optional<String> describe(Block block)
    {
        var family = FamilyRegistry.BLOCKS.getFamily(block);
        if (family == null) return Optional.empty();

        for (var group : groups)
        {
            var match = group.match(family);
            if (match.isEmpty()) continue;

            var familyId = group.families.get(match.get().index).id;
            var description = String.format("%s %s material=%s anchor=%s", group.name, familyId, match.get().material, match.get().memberId);
            return Optional.of(description);
        }

        return Optional.empty();
    }

    private static CompiledTemplate compileTemplate(String template, int index)
    {
        var builder = new StringBuilder("^");
        var literalLength = 0;
        var remaining = template;

        while (true)
        {
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

    private static class GroupData
    {
        public List<GroupDefinition> groups = List.of();
    }

    private static class GroupDefinition
    {
        public String name = "";
        public List<FamilyDefinition> families = List.of();
    }

    private static class FamilyDefinition
    {
        public String id = "";
        public List<String> anchors = List.of();
    }

    private static class CompiledGroup
    {
        private final String name;
        private final List<CompiledFamilyDefinition> families;
        private final List<CompiledTemplate> templates;
        private final Map<Family<Block>, Optional<FamilyMatch>> matchCache = new IdentityHashMap<>();

        private CompiledGroup(String name, List<CompiledFamilyDefinition> families, List<CompiledTemplate> templates)
        {
            this.name = name;
            this.families = families;
            this.templates = templates;
        }

        public Optional<FamilyMatch> match(Family<Block> family)
        {
            if (matchCache.containsKey(family)) return matchCache.get(family);

            Optional<FamilyMatch> result = Optional.empty();

            for (var member : family.getMembers())
            {
                var id = Registries.BLOCK.getId(member).toString();

                for (var template : templates)
                {
                    var match = template.match(id);
                    if (match.isEmpty()) continue;

                    if (result.isPresent())
                    {
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

    private record CompiledFamilyDefinition(String id, List<String> anchors, List<CompiledTemplate> templates)
    {
    }

    private record CompiledTemplate(String template, int index, Pattern pattern, int literalLength)
    {
        public Optional<FamilyMatch> match(String id)
        {
            Matcher matcher = pattern.matcher(id);
            if (!matcher.matches()) return Optional.empty();

            var material = matcher.groupCount() > 0 ? matcher.group(1) : "";
            return Optional.of(new FamilyMatch(index, material, id));
        }
    }

    private record FamilyMatch(int index, String material, String memberId)
    {
    }
}
