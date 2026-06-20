package dev.kaepsis.kommons.chat;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A comprehensive, high-performance chat rendering and delivery record utility designed
 * to unify legacy ampersand color coding systems with modern, advanced Kyori Adventure {@link Component} ecosystems.
 * <p>
 * Key features integrated inside this orchestration framework include:
 * <ul>
 * <li><b>Hybrid Text Processing:</b> Automatically parses both standard legacy section colors (e.g., {@code &a}),
 * hex structures (e.g., {@code &#ff3333}), and highly customized tag formats via {@link MiniMessage} tags.</li>
 * <li><b>LRU Caching Core:</b> Leverages fixed-boundary, thread-safe Least-Recently-Used (LRU) maps
 * to cache expensive deserialization and conversion processes, safeguarding heap performance during rapid chat ticks.</li>
 * <li><b>Cross-Platform Branching:</b> Dynamically detects native Paper API availability. If native execution routes are absent,
 * it automatically delegates rendering routines transparently down into a lazy-initialized fallback {@link BukkitAudiences} platform wrapper instance.</li>
 * <li><b>Thread Context Execution Safety:</b> Guarantees thread context validation, intercepting async chat events
 * to automatically bounce message delivery onto the main thread where platform mechanics dictate safety boundaries.</li>
 * </ul>
 * </p>
 *
 * @param plugin the hosting {@link JavaPlugin} platform runtime execution context instance
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public record Chat(JavaPlugin plugin) {

    private static volatile BukkitAudiences audiences;

    private static final boolean IS_PAPER;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER;
    private static final MiniMessage MINI_MESSAGE;
    private static final Map<String, Component> COMPONENT_CACHE;
    private static final Map<String, String> CONVERSION_CACHE;
    private static final Component EMPTY_COMPONENT;
    private static final Pattern HEX_PATTERN;
    private static final String[] LEGACY_CODES;
    private static final String[] MINI_TAGS;

    static {
        boolean paper;
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            paper = true;
        } catch (ClassNotFoundException e) {
            paper = false;
        }
        IS_PAPER = paper;

        System.setProperty("adventure.minimessage.strict", "false");

        LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
                .character('&')
                .hexColors()
                .build();

        MINI_MESSAGE = MiniMessage.miniMessage();

        COMPONENT_CACHE = Collections.synchronizedMap(
                new LinkedHashMap<>(128, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
                        return size() > 512;
                    }
                }
        );

        CONVERSION_CACHE = Collections.synchronizedMap(
                new LinkedHashMap<>(128, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                        return size() > 512;
                    }
                }
        );

        EMPTY_COMPONENT = Component.empty();
        HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

        LEGACY_CODES = new String[]{"&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9", "&a", "&b", "&c", "&d", "&e", "&f", "&k", "&l", "&m", "&n", "&o", "&r"};
        MINI_TAGS = new String[]{"<black>", "<dark_blue>", "<dark_green>", "<dark_aqua>", "<dark_red>", "<dark_purple>", "<gold>", "<gray>", "<dark_gray>", "<blue>", "<green>", "<aqua>", "<red>", "<light_purple>", "<yellow>", "<white>", "<obfuscated>", "<bold>", "<strikethrough>", "<underlined>", "<italic>", "<reset>"};
    }

    /**
     * Closes the active underlying {@link BukkitAudiences} platform wrapper, if it was initialized.
     * <p>
     * This utility cleanup routine should typically be called exactly once inside the teardown or shutdown lifecycle
     * phase of a plugin (e.g., {@code onDisable}) to eliminate lingering thread context risks or memory footprint locks.
     * </p>
     */
    public static void close() {
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
    }

    /**
     * Processes, translates, and deserializes a raw text string into a completely compiled Kyori {@link Component}.
     * <p>
     * This method applies a multi-tier fallback parsing pipeline:
     * <ol>
     * <li>Checks the synchronized LRU cache for a matching pre-built Component.</li>
     * <li>Attempts translation via {@link MiniMessage} formatting parameters after internally converting legacy tags.</li>
     * <li>Falls back to the standard {@link LegacyComponentSerializer} if complex MiniMessage parsing fails.</li>
     * <li>Returns a plain text representation block as a final structural safety mechanism if text errors interrupt previous stages.</li>
     * </ol>
     * </p>
     *
     * @param message the raw target text string to convert
     * @return a fully styled {@link Component} object, or {@link Component#empty()} if input string is null/empty
     */
    public Component parse(String message) {
        if (message == null || message.isEmpty()) return EMPTY_COMPONENT;
        Component cached = COMPONENT_CACHE.get(message);
        if (cached != null) return cached;
        Component component;
        try {
            component = MINI_MESSAGE.deserialize(convertLegacyToMiniMessage(message));
        } catch (Exception e) {
            try {
                component = LEGACY_SERIALIZER.deserialize(message);
            } catch (Exception ex) {
                component = Component.text(message);
            }
        }
        COMPONENT_CACHE.put(message, component);
        return component;
    }

    /**
     * Strips all standard legacy ampersand formatting codes and hex sequences out of a text string.
     *
     * @param message the raw message data to inspect
     * @return a plain, clean string stripped of styling markers, or an empty string if input is null/empty
     */
    public String stripColors(String message) {
        if (message == null || message.isEmpty()) return "";
        return HEX_PATTERN.matcher(message).replaceAll("").replace("&", "");
    }

    /**
     * Dispatches a formatted and colored text message out to a specified {@link CommandSender}.
     * <p>
     * This method analyzes current runtime thread origins. If called on an active asynchronous thread execution loop,
     * it systematically bounces message rendering and delivery onto the primary server thread framework via the scheduler
     * to eliminate concurrent connection pipeline access exceptions.
     * </p>
     *
     * @param sender       the target destination entity or terminal console to receive the message
     * @param message      the target raw text string to send
     * @param placeholders an optional array of alternating key-value text pairs to replace within the text layout parameters
     * @see #format(String, Object...)
     */
    public void send(CommandSender sender, String message, Object... placeholders) {
        if (sender == null || message == null || message.isEmpty()) return;
        final String processedMessage = format(message, placeholders);
        if (Bukkit.isPrimaryThread()) {
            deliverMessage(sender, processedMessage);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> deliverMessage(sender, processedMessage));
        }
    }

    /**
     * Executes a linear structural search-and-replace algorithm over a target message string, substituting
     * sequentially matching pairs of placeholder elements.
     * <p>
     * Pair evaluation ignores unpaired dangling entries at the trailing boundaries of the object array
     * by applying bitwise masking validation boundaries ({@code placeholders.length & ~1}).
     * </p>
     *
     * @param message      the target base text template string to modify
     * @param placeholders an array of alternating key-value pairings (e.g., {@code ["%player%", "Kaepsis", "%count%", 5]})
     * @return a processed string with substituting tokens populated, or an empty string if base template is null
     */
    public String format(String message, Object... placeholders) {
        if (message == null) return "";
        if (placeholders == null || placeholders.length == 0) return message;
        String result = message;
        int len = placeholders.length & ~1;
        for (int i = 0; i < len; i += 2) {
            result = result.replace(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
        }
        return result;
    }

    /**
     * Broadcasts a styled text message out to all active online player entities matching an optional authorization node parameter.
     *
     * @param message      the raw target message text template to broadcast
     * @param permission   the explicit permission node required to receive the transmission; pass {@code null} to target everyone
     * @param placeholders an optional array of alternating key-value text pairs to replace within the text layout parameters
     */
    public void broadcast(String message, String permission, Object... placeholders) {
        if (message == null || message.isEmpty()) return;
        String processed = placeholders != null && placeholders.length > 0 ? format(message, placeholders) : message;
        final Component colored = parse(processed);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> permission == null || p.hasPermission(permission))
                .forEach(p -> p.sendMessage(colored));
    }

    /**
     * Transforms an entire list of strings by iterating over each line and applying key-value token formatting rules.
     *
     * @param list         the base textual string collections list template source
     * @param placeholders an alternating key-value parameters array to inject across every list entry line
     * @return a new mutable compiled {@link List} containing updated data lines, or an empty list if the input structure was null
     */
    public List<String> formatList(List<String> list, Object... placeholders) {
        if (list == null) return List.of();
        return list.stream().map(line -> format(line, placeholders)).collect(Collectors.toList());
    }

    /**
     * Iterates over a collection of raw strings, passing each through the primary color parsing framework
     * to resolve a list of fully formatted Adventure Components.
     *
     * @param list the raw string lines collection list to parse
     * @return an ordered compiled {@link List} tracking individual formatted {@link Component} assets
     */
    public List<Component> colorList(List<String> list) {
        if (list == null) return List.of();
        return list.stream().map(this::parse).collect(Collectors.toList());
    }

    /**
     * Displays a stylized text overlay title and subtitle framework onto a specific player's client viewport interface.
     *
     * @param player   the target destination player entity
     * @param title    the primary raw main title text string template
     * @param subtitle the structural raw secondary subtitle text string template
     * @param fadeIn   the animation duration window in server ticks for the title to fade in
     * @param stay     the static display duration window in server ticks for the title to remain fully visible
     * @param fadeOut  the animation duration window in server ticks for the title to fade away
     */
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );
        player.showTitle(Title.title(parse(title), parse(subtitle), times));
    }

    /**
     * Compiles a single processed and formatted text line into a definitive styled Kyori {@link Component} structure.
     *
     * @param message      the target raw text string template to manipulate
     * @param placeholders an explicit array of alternating key-value text replacements
     * @return a fully populated styled text {@link Component} element
     * @throws IllegalArgumentException if the provided placeholder parameter items array has an odd number of arguments
     */
    public Component createComponent(String message, Object... placeholders) {
        if (message == null || message.isEmpty()) return EMPTY_COMPONENT;
        if (placeholders.length > 0) {
            if (placeholders.length % 2 != 0)
                throw new IllegalArgumentException("Placeholders must be key-value pairs");
            message = format(message, placeholders);
        }
        return parse(message);
    }

    /**
     * Asynchronously compiles, formats, and initializes a styled Kyori {@link Component} on a background worker thread.
     *
     * @param message      the target raw text string template to manipulate
     * @param placeholders an explicit array of alternating key-value text replacements
     * @return a {@link CompletableFuture} supplying the fully populated styled text {@link Component} asset once ready
     * @see #createComponent(String, Object...)
     */
    public CompletableFuture<Component> createComponentAsync(String message, Object... placeholders) {
        return CompletableFuture.supplyAsync(() -> createComponent(message, placeholders));
    }

    /**
     * Standardizes a nullable string reference into a safe string literal.
     */
    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Internally routes a compiled text component out to a targeted sender using the most optimal platform route.
     * <p>
     * Uses native Paper API methods directly if available; otherwise fallback routines resolve via modern,
     * multi-version compatible {@link BukkitAudiences} rendering engine blocks.
     * </p>
     */
    private void deliverMessage(CommandSender sender, String message) {
        Component parsed = parse(message);
        if (IS_PAPER) {
            sender.sendMessage(parsed);
        } else {
            getAudiences().sender(sender).sendMessage(parsed);
        }
    }

    /**
     * Thread-safely initializes or retrieves the global platform fallback {@link BukkitAudiences} singleton
     * via a classic double-checked locking design pattern.
     */
    private BukkitAudiences getAudiences() {
        if (audiences == null) {
            synchronized (Chat.class) {
                if (audiences == null) {
                    audiences = BukkitAudiences.create(plugin);
                }
            }
        }
        return audiences;
    }

    /**
     * Iteratively scans a raw string input to detect, convert, and rewrite legacy ampersand attributes
     * and hex codes down into unified, modern {@link MiniMessage} tags.
     * <p>
     * To protect application cycles against heavy repetitive string reconstruction parsing patterns,
     * successful structural conversions are cached internally using an LRU map.
     * </p>
     */
    private static String convertLegacyToMiniMessage(String message) {
        String cached = CONVERSION_CACHE.get(message);
        if (cached != null) return cached;
        StringBuilder result = new StringBuilder(message.length() + 32);
        Matcher matcher = HEX_PATTERN.matcher(message);
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(message, lastEnd, matcher.start());
            result.append("<color:#").append(matcher.group(1)).append('>');
            lastEnd = matcher.end();
        }
        result.append(message, lastEnd, message.length());
        String withHex = result.toString();
        if (withHex.indexOf('&') == -1) {
            CONVERSION_CACHE.put(message, withHex);
            return withHex;
        }
        String converted = withHex;
        for (int i = 0; i < LEGACY_CODES.length; i++) {
            if (converted.contains(LEGACY_CODES[i])) {
                converted = converted.replace(LEGACY_CODES[i], MINI_TAGS[i]);
            }
        }
        CONVERSION_CACHE.put(message, converted);
        return converted;
    }

}