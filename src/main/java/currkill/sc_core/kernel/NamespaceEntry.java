package currkill.sc_core.kernel;

import currkill.sc_core.api.ModRegistration;
import currkill.sc_core.api.NamespaceConfig;
import currkill.sc_core.api.ScCommandNode;
import currkill.sc_core.api.ShortCommand;

import java.util.List;
import java.util.function.Supplier;

/**
 * 一个已注册的命名空间：配置开关、指令树贡献、短名映射都在这里。
 */
public final class NamespaceEntry {

    private final String modId;
    private final String namespace;
    private final Supplier<List<ScCommandNode>> commands;
    private final List<ShortCommand> shortCommands;
    private final NamespaceConfig config;

    NamespaceEntry(ModRegistration registration, NamespaceConfig config) {
        this.modId = registration.modId();
        this.namespace = registration.namespace();
        this.commands = registration.commands();
        this.shortCommands = registration.shortCommands();
        this.config = config;
    }

    public String modId() {
        return modId;
    }

    public String namespace() {
        return namespace;
    }

    public Supplier<List<ScCommandNode>> commands() {
        return commands;
    }

    public List<ShortCommand> shortCommands() {
        return shortCommands;
    }

    public NamespaceConfig config() {
        return config;
    }

    /** 这个命名空间的短名是否启用（改内存即可，saveconfig 才持久化）。 */
    public boolean shortCommandEnabled() {
        return config.getBoolean("shortcommand", true);
    }
}
