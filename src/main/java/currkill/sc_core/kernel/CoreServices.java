package currkill.sc_core.kernel;

import currkill.sc_core.api.CommandLogger;
import currkill.sc_core.api.ScPaths;

import java.util.function.Consumer;

/**
 * Core 的运行时服务集合：路径、指令日志、配置托管、命名空间注册表。
 *
 * <p>由平台适配层（{@code Sc_core}）在模组构造时创建并交给 {@code SimpleCommandCore.init}。
 * 这里不含任何 Minecraft 类型。
 */
public final class CoreServices {

    private final ScPaths paths;
    private final CommandLogger commandLog;
    private final ConfigManager configs;
    private final NamespaceRegistry registry;

    public CoreServices(ScPaths paths, CommandLogger commandLog, Consumer<String> warner) {
        this.paths = paths;
        this.commandLog = commandLog;
        this.configs = new ConfigManager(paths.configRoot());
        this.registry = new NamespaceRegistry(configs, warner);
    }

    public ScPaths paths() {
        return paths;
    }

    public CommandLogger commandLog() {
        return commandLog;
    }

    public ConfigManager configs() {
        return configs;
    }

    public NamespaceRegistry registry() {
        return registry;
    }
}
