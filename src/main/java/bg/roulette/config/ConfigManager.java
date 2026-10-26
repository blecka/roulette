package bg.roulette.config;

import bg.roulette.Roulette;
import bg.roulette.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ConfigManager {

    private final Roulette plugin;

    private File configFile;
    private FileConfiguration config;

    private File messagesFile;
    private FileConfiguration messages;

    private File guiFile;
    private FileConfiguration gui;

    private File tablesFile;
    private FileConfiguration tables;

    public ConfigManager(Roulette plugin) {
        this.plugin = plugin;
    }

    public void loadConfigurations() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        guiFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        gui = YamlConfiguration.loadConfiguration(guiFile);

        tablesFile = new File(plugin.getDataFolder(), "tables.yml");
        if (!tablesFile.exists()) {
            plugin.saveResource("tables.yml", false);
        }
        tables = YamlConfiguration.loadConfiguration(tablesFile);
    }

    public void reloadConfigurations() {
        loadConfigurations();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public FileConfiguration getGui() {
        return gui;
    }

    public FileConfiguration getTables() {
        return tables;
    }

    public void saveTables() {
        try {
            tables.save(tablesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save tables.yml!", e);
        }
    }

    public String getPrefix() {
        return ColorUtil.color(config.getString("prefix", "&8[&cРулетка&8] "));
    }

    public String getMessageString(String path) {
        String msg = messages.getString(path, "");
        return ColorUtil.color(getPrefix() + msg);
    }

    public String getRawMessageString(String path) {
        return ColorUtil.color(messages.getString(path, ""));
    }

    public Component getMessageComponent(String path) {
        return ColorUtil.component(getPrefix() + messages.getString(path, ""));
    }
}
