package currkill.sc_core.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个命名空间的配置，对应 {@code config/sc/<namespace>.toml}。
 *
 * <p>Core 自己管动态数量的配置文件，不用 ForgeConfigSpec（它不适合动态文件数量）。
 * 键用扁平形式，带点表示分节，例如 {@code backup.path} 在文件里是 {@code [backup]} 节下的 {@code path}。
 *
 * <p>子模组注册时把自己的默认值交给 Core（{@link ModRegistration.Builder#configDefaults}），
 * Core 负责读、写、重载。
 */
public final class NamespaceConfig {

    private final String namespace;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Object> defaults = new LinkedHashMap<>();

    public NamespaceConfig(String namespace) {
        this.namespace = namespace;
    }

    public String namespace() {
        return namespace;
    }

    /** 注册默认值：文件里没有这个键时用它。 */
    public NamespaceConfig defaultValue(String key, Object value) {
        defaults.put(key, value);
        return this;
    }

    public Map<String, Object> defaults() {
        return Collections.unmodifiableMap(defaults);
    }

    public Map<String, Object> values() {
        return Collections.unmodifiableMap(values);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public void put(String key, Object value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    /** 清空所有已加载的值（默认值保留），用于 reloadconfig。 */
    public void clear() {
        values.clear();
    }

    /** 清空并从另一份配置整体覆盖（用于 reloadconfig）。 */
    public void replaceWith(NamespaceConfig other) {
        values.clear();
        values.putAll(other.values);
    }

    /** 补齐默认值：只补缺失的键，不覆盖已有值。 */
    public void fillDefaults() {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            values.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return fallback;
    }

    public String getString(String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public int getInt(String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public double getDouble(String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
