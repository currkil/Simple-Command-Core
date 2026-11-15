package currkill.sc_core.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 子模组向 Core 注册自己的命名空间。
 *
 * <pre>{@code
 * SimpleCommandCore.register(ModRegistration.builder("sc_server")
 *         .namespace("server")
 *         .commands(ServerCommands::build)
 *         .shortcommand("backup")
 *         .shortcommand("tpa", "tpa")
 *         .build());
 * }</pre>
 */
public final class ModRegistration {

    private final String modId;
    private final String namespace;
    private final Supplier<List<ScCommandNode>> commands;
    private final List<ShortCommand> shortCommands;
    private final Consumer<NamespaceConfig> configDefaults;

    private ModRegistration(Builder builder) {
        this.modId = builder.modId;
        this.namespace = builder.namespace;
        this.commands = builder.commands;
        this.shortCommands = List.copyOf(builder.shortCommands);
        this.configDefaults = builder.configDefaults;
    }

    public static Builder builder(String modId) {
        return new Builder(modId);
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

    public Consumer<NamespaceConfig> configDefaults() {
        return configDefaults;
    }

    public static final class Builder {

        private final String modId;
        private String namespace;
        private Supplier<List<ScCommandNode>> commands = List::of;
        private final List<ShortCommand> shortCommands = new ArrayList<>();
        private Consumer<NamespaceConfig> configDefaults = cfg -> {
        };

        private Builder(String modId) {
            if (modId == null || modId.isBlank()) {
                throw new IllegalArgumentException("modId 不能为空");
            }
            this.modId = modId;
        }

        /** 命名空间名，也是 {@code /sc <namespace>} 和 {@code config/sc/<namespace>.toml} 里的那段。 */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /** 指令树贡献。每次 {@code /sc} 树重建时调用，必须是纯构建、不产生副作用。 */
        public Builder commands(Supplier<List<ScCommandNode>> commands) {
            this.commands = commands == null ? List::of : commands;
            return this;
        }

        /** 注册短名：{@code shortcommand("backup")} 的短名就是 {@code backup}。 */
        public Builder shortcommand(String path) {
            List<String> segments = ShortCommand.splitPath(path);
            return shortcommand(path, segments.isEmpty() ? path : segments.get(segments.size() - 1));
        }

        /** 注册短名并指定别名：{@code shortcommand("clean", "cl")}。 */
        public Builder shortcommand(String path, String alias) {
            shortCommands.add(new ShortCommand(alias, ShortCommand.splitPath(path)));
            return this;
        }

        /** 子模组的业务配置默认值，由 Core 托管读写。 */
        public Builder configDefaults(Consumer<NamespaceConfig> defaults) {
            this.configDefaults = defaults == null ? cfg -> {
            } : defaults;
            return this;
        }

        public ModRegistration build() {
            if (namespace == null || namespace.isBlank()) {
                throw new IllegalStateException("注册 " + modId + " 时必须指定 namespace");
            }
            return new ModRegistration(this);
        }
    }
}
