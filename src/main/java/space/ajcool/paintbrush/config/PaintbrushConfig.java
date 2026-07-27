package space.ajcool.paintbrush.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import space.ajcool.paintbrush.Paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class PaintbrushConfig
{
    public static boolean PAINTKNIFE_ALLOW_DELETE = false;
    public static boolean PAINTKNIFE_ALLOW_APPEND = false;
    public static boolean FILTER_FOLIAGE = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("paintbrush.json");

    public static void load()
    {
        if (!Files.exists(CONFIG_PATH)) return;

        try (var reader = Files.newBufferedReader(CONFIG_PATH))
        {
            var data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) return;

            PAINTKNIFE_ALLOW_DELETE = data.paintknifeAllowDelete;
            PAINTKNIFE_ALLOW_APPEND = data.paintknifeAllowAppend;
            FILTER_FOLIAGE = data.filterFoliage;
        }
        catch (Exception e)
        {
            PAINTKNIFE_ALLOW_DELETE = false;
            PAINTKNIFE_ALLOW_APPEND = false;
            FILTER_FOLIAGE = false;
            Paintbrush.LOGGER.warn("Paintbrush - Could not load config from {}", CONFIG_PATH, e);
        }
    }

    public static void save()
    {
        var data = new ConfigData();
        data.paintknifeAllowDelete = PAINTKNIFE_ALLOW_DELETE;
        data.paintknifeAllowAppend = PAINTKNIFE_ALLOW_APPEND;
        data.filterFoliage = FILTER_FOLIAGE;

        try
        {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        }
        catch (IOException e)
        {
            Paintbrush.LOGGER.warn("Paintbrush - Could not save config to {}", CONFIG_PATH, e);
        }
    }

    private static class ConfigData
    {
        boolean paintknifeAllowDelete = false;
        boolean paintknifeAllowAppend = false;
        boolean filterFoliage = false;
    }
}
