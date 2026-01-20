package space.controlnet.chatmc.core.util;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing item tags in chat messages.
 * Item tags follow the format: {@code <item id="namespace:item_id" display_name="Item Name">}
 */
public final class ItemTagParser {
    /**
     * Pattern to match item tags in text.
     * Group 1: item ID (e.g., "minecraft:diamond")
     * Group 2: display name (optional)
     */
    public static final Pattern ITEM_TAG_PATTERN =
            Pattern.compile("<item\\s+id=\"([^\"]+)\"(?:\\s+display_name=\"([^\"]+)\")?\\s*>");

    private ItemTagParser() {
    }

    /**
     * Finds the first invalid item tag in the text.
     *
     * @param text            the text to search
     * @param itemIdValidator predicate to validate item IDs (returns true if valid)
     * @return the first invalid item ID found, or empty if all are valid
     */
    public static Optional<String> findInvalidItemTag(String text, Predicate<String> itemIdValidator) {
        if (text == null || text.isBlank() || !text.contains("<item")) {
            return Optional.empty();
        }

        Matcher matcher = ITEM_TAG_PATTERN.matcher(text);
        while (matcher.find()) {
            String itemId = matcher.group(1);
            if (!itemIdValidator.test(itemId)) {
                return Optional.of(itemId);
            }
        }
        return Optional.empty();
    }

    /**
     * Extracts an attribute value from an item tag.
     *
     * @param tag  the full tag string (e.g., {@code <item id="minecraft:diamond">})
     * @param attr the attribute name (e.g., "id" or "display_name")
     * @return the attribute value, or null if not found
     */
    public static String extractAttribute(String tag, String attr) {
        if (tag == null || attr == null) {
            return null;
        }
        String needle = attr + "=\"";
        int idx = tag.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int start = idx + needle.length();
        int end = tag.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return tag.substring(start, end);
    }

    /**
     * Checks if the text contains any item tags.
     *
     * @param text the text to check
     * @return true if the text contains at least one item tag pattern
     */
    public static boolean containsItemTags(String text) {
        return text != null && text.contains("<item");
    }
}
