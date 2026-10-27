package bg.roulette.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

public class ColorUtil {

    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();

    /**
     * Converts a string with color codes ('&' or '§') to an Adventure Component.
     */
    public static Component component(String message) {
        if (message == null) {
            return Component.empty();
        }
        return SECTION_SERIALIZER.deserialize(color(message));
    }

    /**
     * Translates '&' color codes to '§' for legacy String methods.
     */
    public static String color(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
