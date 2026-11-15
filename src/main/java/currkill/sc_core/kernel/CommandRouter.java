package currkill.sc_core.kernel;

import currkill.sc_core.api.NamespaceConfig;
import currkill.sc_core.api.ScArgumentType;
import currkill.sc_core.api.ScCommandNode;
import currkill.sc_core.api.ShortCommand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 把命名空间注册表翻译成加载器无关的指令树。
 *
 * <p>结构：
 * <pre>
 * /sc &lt;namespace&gt; &lt;子指令...&gt;
 * /sc &lt;namespace&gt; shortcommand|saveconfig|reloadconfig   （每个命名空间都有）
 * /&lt;短名&gt; ...                                          （按 config 开关注册）
 * </pre>
 */
public final class CommandRouter {

    private final NamespaceRegistry registry;
    private final Consumer<String> warner;

    public CommandRouter(NamespaceRegistry registry, Consumer<String> warner) {
        this.registry = registry;
        this.warner = warner == null ? message -> {
        } : warner;
    }

    /** {@code /sc} 的直接子节点：每个命名空间一个字面量节点。 */
    public List<ScCommandNode> scChildren() {
        List<ScCommandNode> children = new ArrayList<>();
        for (NamespaceEntry entry : registry.all()) {
            children.add(namespaceNode(entry));
        }
        return children;
    }

    /** 根级别的短名节点。冲突静默跳过 + WARN，回落到 {@code /sc <ns> <sub>}。 */
    public List<ScCommandNode> aliasRoots() {
        List<ScCommandNode> aliases = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (NamespaceEntry entry : registry.all()) {
            used.add(entry.namespace());
        }
        for (NamespaceEntry entry : registry.all()) {
            if (!entry.shortCommandEnabled()) {
                continue;
            }
            List<ScCommandNode> roots = entry.commands().get();
            for (ShortCommand shortCommand : entry.shortCommands()) {
                String alias = shortCommand.alias();
                if (used.contains(alias)) {
                    warner.accept("短名 /" + alias + " 冲突，已跳过（仍可用 /sc " + entry.namespace() + " "
                            + String.join(" ", shortCommand.path()) + "）");
                    continue;
                }
                ScCommandNode target = resolve(roots, shortCommand.path());
                if (target == null) {
                    warner.accept("短名 /" + alias + " 指向的子指令 "
                            + String.join(" ", shortCommand.path()) + " 在命名空间 "
                            + entry.namespace() + " 中不存在，已跳过");
                    continue;
                }
                used.add(alias);
                aliases.add(target.copyAs(alias));
            }
        }
        return aliases;
    }

    private ScCommandNode namespaceNode(NamespaceEntry entry) {
        ScCommandNode node = ScCommandNode.literal(entry.namespace());
        node.then(shortCommandNode(entry), saveConfigNode(entry), reloadConfigNode(entry));
        for (ScCommandNode root : entry.commands().get()) {
            node.then(root);
        }
        return node;
    }

    private ScCommandNode shortCommandNode(NamespaceEntry entry) {
        NamespaceConfig config = entry.config();
        ScCommandNode node = ScCommandNode.literal("shortcommand").requires(2);
        node.executes(ctx -> {
            ctx.reply("命名空间 " + entry.namespace() + " 的短名开关："
                    + (config.getBoolean("shortcommand", true) ? "开" : "关")
                    + "（改完记得 /sc " + entry.namespace() + " saveconfig）");
            return 1;
        });
        node.then(ScCommandNode.argument("enabled", ScArgumentType.BOOLEAN, ctx -> {
            boolean enabled = ctx.boolArg("enabled");
            config.put("shortcommand", enabled);
            ctx.reply("命名空间 " + entry.namespace() + " 的短名开关已设为 " + (enabled ? "开" : "关")
                    + "，本条指令结束后生效；/sc " + entry.namespace() + " saveconfig 可持久化");
            return 1;
        }).requires(2));
        return node;
    }

    private ScCommandNode saveConfigNode(NamespaceEntry entry) {
        return ScCommandNode.literal("saveconfig", ctx -> {
            try {
                registry.configs().save(entry.config());
                ctx.reply("已保存 config/sc/" + entry.namespace() + ".toml");
                return 1;
            } catch (RuntimeException e) {
                ctx.error("保存配置失败：" + e.getMessage());
                ctx.gameLog("保存配置失败：" + e);
                return 0;
            }
        }).requires(2);
    }

    private ScCommandNode reloadConfigNode(NamespaceEntry entry) {
        return ScCommandNode.literal("reloadconfig", ctx -> {
            try {
                registry.configs().load(entry.namespace(), entry.config());
                ctx.reply("已重新读取 config/sc/" + entry.namespace() + ".toml"
                        + "（短名改动在重载后生效）");
                return 1;
            } catch (RuntimeException e) {
                ctx.error("重载配置失败：" + e.getMessage());
                ctx.gameLog("重载配置失败：" + e);
                return 0;
            }
        }).requires(2);
    }

    private static ScCommandNode resolve(List<ScCommandNode> roots, List<String> path) {
        List<ScCommandNode> level = roots;
        ScCommandNode found = null;
        for (String segment : path) {
            found = null;
            if (level == null) {
                return null;
            }
            for (ScCommandNode candidate : level) {
                if (candidate.name().equals(segment)) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) {
                return null;
            }
            level = found.children();
        }
        return found;
    }
}
