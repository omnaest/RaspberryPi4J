package org.omnaest.pi.adapter.mcp;

import java.util.List;
import java.util.Map;

/**
 * Static coercion helpers for reading MCP tool arguments from a {@code Map<String, Object>}.
 *
 * <p>Ported from {@code ClaudeMemoryServer}'s {@code McpArgs}, trimmed to the argument shapes actually needed by
 * the pi-server hardware tools, plus {@link #requiredDouble(Map, String)}/{@link #optDouble(Map, String)},
 * {@link #requiredBoolean(Map, String)}, {@link #requiredEnum(Map, String, Class)}, and {@link #intArray(Map, String)}.
 *
 * <p>The opt* methods are null-safe on the args map and return {@code null} when the key is absent/of the wrong
 * type. The required* methods follow the original pattern of failing fast with NPE/CCE if the key is absent or of
 * the wrong type — callers must ensure those keys are present for required fields.
 */
public final class McpArgs
{

    private McpArgs()
    {
    }

    /**
     * Returns the value for {@code key} as a {@code String}, or {@code null} if {@code args} is {@code null} or the
     * key is absent/null.
     */
    public static String string(Map<String, Object> args, String key)
    {
        return args != null ? (String) args.get(key) : null;
    }

    /**
     * Returns the value for {@code key} as an {@code Integer} using a safe {@code instanceof Number} check, or
     * {@code null} if {@code args} is {@code null}, the key is absent, or the value is not a {@code Number}.
     */
    public static Integer optInt(Map<String, Object> args, String key)
    {
        if (args == null)
            return null;
        Object val = args.get(key);
        return val instanceof Number n ? n.intValue() : null;
    }

    /**
     * Returns the value for {@code key} cast to {@code Number} and converted to {@code int}. Throws
     * {@code NullPointerException} or {@code ClassCastException} if the key is absent or not a {@code Number} —
     * matches the original required-field pattern.
     */
    public static int requiredInt(Map<String, Object> args, String key)
    {
        return ((Number) args.get(key)).intValue();
    }

    /**
     * Returns the value for {@code key} as a {@code Double} using a safe {@code instanceof Number} check, or
     * {@code null} if {@code args} is {@code null}, the key is absent, or the value is not a {@code Number}.
     */
    public static Double optDouble(Map<String, Object> args, String key)
    {
        if (args == null)
            return null;
        Object val = args.get(key);
        return val instanceof Number n ? n.doubleValue() : null;
    }

    /**
     * Returns the value for {@code key} cast to {@code Number} and converted to {@code double}. Throws
     * {@code NullPointerException} or {@code ClassCastException} if the key is absent or not a {@code Number} —
     * matches the original required-field pattern.
     */
    public static double requiredDouble(Map<String, Object> args, String key)
    {
        return ((Number) args.get(key)).doubleValue();
    }

    /**
     * Returns the value for {@code key} cast to {@code Boolean}. Throws {@code NullPointerException} or
     * {@code ClassCastException} if the key is absent or not a {@code Boolean} — matches the original
     * required-field pattern.
     */
    public static boolean requiredBoolean(Map<String, Object> args, String key)
    {
        return (Boolean) args.get(key);
    }

    /**
     * Returns the value for {@code key} as a {@code String} converted to the given enum type via
     * {@link Enum#valueOf(Class, String)} (case-sensitive — matches Spring's existing
     * {@code @PathVariable}/{@code @RequestParam} enum binding). Throws {@code NullPointerException} or
     * {@code IllegalArgumentException} if the key is absent or not a valid enum constant name.
     */
    public static <E extends Enum<E>> E requiredEnum(Map<String, Object> args, String key, Class<E> enumType)
    {
        return Enum.valueOf(enumType, (String) args.get(key));
    }

    /**
     * Returns the value for {@code key} as an {@code int[]}, coercing each element via a safe
     * {@code instanceof Number} check, or {@code null} if {@code args} is {@code null} or the key is absent/null.
     */
    public static int[] intArray(Map<String, Object> args, String key)
    {
        if (args == null)
            return null;
        Object val = args.get(key);
        if (!(val instanceof List<?> list))
            return null;
        return list.stream()
                   .mapToInt(element -> element instanceof Number n ? n.intValue() : 0)
                   .toArray();
    }
}
