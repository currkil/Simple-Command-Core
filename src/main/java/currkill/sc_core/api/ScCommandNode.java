package currkill.sc_core.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 加载器无关的指令树节点。子模组用 {@link #literal} / {@link #argument} 声明自己的指令树，
 * Core 负责把它翻译成当前平台的指令树（现在只有 Forge/Brigadier）。
 *
 * <pre>{@code
 * ScCommandNode give = ScCommandNode.literal("give")
 *         .then(ScCommandNode.argument("item", ScArgumentType.WORD, ctx -> {...}));
 * }</pre>
 */
public final class ScCommandNode {

    private final String name;
    private final ScArgumentType type;
    private final List<ScCommandNode> children = new ArrayList<>();
    private CommandEffect effect;
    private int permissionLevel;

    private ScCommandNode(String name, ScArgumentType type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("指令节点名不能为空");
        }
        this.name = name;
        this.type = type;
    }

    public static ScCommandNode literal(String name) {
        return new ScCommandNode(name, null);
    }

    public static ScCommandNode literal(String name, CommandEffect effect) {
        return literal(name).executes(effect);
    }

    public static ScCommandNode argument(String name, ScArgumentType type) {
        if (type == null) {
            throw new IllegalArgumentException("指令参数类型不能为空：" + name);
        }
        return new ScCommandNode(name, type);
    }

    public static ScCommandNode argument(String name, ScArgumentType type, CommandEffect effect) {
        return argument(name, type).executes(effect);
    }

    /** 追加子节点，返回自身便于链式声明。 */
    public ScCommandNode then(ScCommandNode... nodes) {
        for (ScCommandNode node : nodes) {
            if (node != null) {
                children.add(node);
            }
        }
        return this;
    }

    public ScCommandNode executes(CommandEffect effect) {
        this.effect = effect;
        return this;
    }

    /** 需要的权限等级（0 = 所有人，2 = OP）。默认 0。 */
    public ScCommandNode requires(int level) {
        this.permissionLevel = Math.max(0, level);
        return this;
    }

    /** 复制成另一个名字，用于短名别名，不影响原树。 */
    public ScCommandNode copyAs(String newName) {
        ScCommandNode copy = new ScCommandNode(newName, type);
        copy.effect = effect;
        copy.permissionLevel = permissionLevel;
        for (ScCommandNode child : children) {
            copy.children.add(child.copyAs(child.name));
        }
        return copy;
    }

    public String name() {
        return name;
    }

    public ScArgumentType type() {
        return type;
    }

    public boolean isLiteral() {
        return type == null;
    }

    public List<ScCommandNode> children() {
        return Collections.unmodifiableList(children);
    }

    public CommandEffect effect() {
        return effect;
    }

    public int permissionLevel() {
        return permissionLevel;
    }

    @Override
    public String toString() {
        return isLiteral() ? name : "<" + name + ":" + type + ">";
    }
}
