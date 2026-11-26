package currkill.sc_core.kernel;

import currkill.sc_core.api.NamespaceConfig;
import currkill.sc_core.api.ModRegistration;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 命名空间注册表：{@code Map<String, NamespaceEntry>}。
 *
 * <p>{@code csc} 是内置命名空间，由 Core 自己注册，不走子模组流程。CSC 的全部指令
 * （{@code var} / {@code list} / {@code io} / {@code log} / {@code file} / {@code run} /
 * {@code repl} / {@code sys}）都挂在这里。
 */
public final class NamespaceRegistry {

    public static final String CSC_NAMESPACE = "csc";

    private final Map<String, NamespaceEntry> entries = new LinkedHashMap<>();
    private final ConfigManager configs;
    private final Consumer<String> warner;

    public NamespaceRegistry(ConfigManager configs, Consumer<String> warner) {
        this.configs = configs;
        this.warner = warner == null ? message -> {
        } : warner;
    }

    public ConfigManager configs() {
        return configs;
    }

    /**
     * 注册一个命名空间。重复注册/非法名字只记 WARN 并跳过，不抛异常——一个子模组写错不该拖垮全家桶。
     */
    public boolean register(ModRegistration registration) {
        String namespace = registration.namespace();
        if (entries.containsKey(namespace)) {
            warner.accept("命名空间 " + namespace + " 已被 " + entries.get(namespace).modId()
                    + " 注册，忽略 " + registration.modId() + " 的重复注册");
            return false;
        }
        NamespaceConfig config = new NamespaceConfig(namespace);
        config.defaultValue("shortcommand", true);
        try {
            registration.configDefaults().accept(config);
        } catch (RuntimeException e) {
            warner.accept("命名空间 " + namespace + " 的配置默认值注册失败：" + e);
        }
        try {
            configs.load(namespace, config);
        } catch (RuntimeException e) {
            warner.accept("命名空间 " + namespace + " 的配置加载失败，使用默认值：" + e);
        }
        entries.put(namespace, new NamespaceEntry(registration, config));
        return true;
    }

    public Optional<NamespaceEntry> get(String namespace) {
        return Optional.ofNullable(entries.get(namespace));
    }

    public boolean contains(String namespace) {
        return entries.containsKey(namespace);
    }

    public Collection<NamespaceEntry> all() {
        return Collections.unmodifiableCollection(entries.values());
    }
}
