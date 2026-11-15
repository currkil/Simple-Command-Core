package currkill.sc_core.kernel;

import currkill.sc_core.SimpleCommandCore;
import currkill.sc_core.api.CommandEffect;
import currkill.sc_core.api.ExecutionContext;
import currkill.sc_core.api.ScArgumentType;
import currkill.sc_core.api.ScCommandNode;
import currkill.sc_csc.api.FormatResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * {@code core} 命名空间自带的指令：{@code /sc core var create|set|delete|list}。
 *
 * <p>变量是 Core 的内置能力（{@code VariableProvider} 的唯一内置实现），所以它归 core 命名空间。
 */
public final class CoreCommands {

    private static final String[] TYPE_HINTS = {"string", "number", "boolean"};

    private CoreCommands() {
    }

    public static List<ScCommandNode> build() {
        return List.of(varCommand());
    }

    private static ScCommandNode varCommand() {
        ScCommandNode var = ScCommandNode.literal("var").requires(2);
        var.then(createCommand(), setCommand(), deleteCommand(), listCommand());
        return var;
    }

    private static ScCommandNode createCommand() {
        ScCommandNode value = ScCommandNode.argument("value", ScArgumentType.STRING,
                ctx -> store(ctx, ctx.arg("name"), ctx.arg("value"), null, true));
        attachTypeHints(value, type ->
                ctx -> store(ctx, ctx.arg("name"), ctx.arg("value"), type, true));
        return ScCommandNode.literal("create")
                .then(ScCommandNode.argument("name", ScArgumentType.STRING).then(value));
    }

    private static ScCommandNode setCommand() {
        ScCommandNode value = ScCommandNode.argument("value", ScArgumentType.STRING,
                ctx -> store(ctx, ctx.arg("name"), ctx.arg("value"), null, false));
        attachTypeHints(value, type ->
                ctx -> store(ctx, ctx.arg("name"), ctx.arg("value"), type, false));
        return ScCommandNode.literal("set")
                .then(ScCommandNode.argument("name", ScArgumentType.STRING).then(value));
    }

    private static ScCommandNode deleteCommand() {
        return ScCommandNode.literal("delete").then(ScCommandNode.argument("name", ScArgumentType.STRING,
                ctx -> {
                    String name = ctx.arg("name");
                    if (ctx.variables().get(name).isEmpty()) {
                        ctx.error("变量不存在：" + name);
                        return 0;
                    }
                    ctx.variables().delete(name);
                    ctx.reply("已删除变量 %" + name + "%");
                    return 1;
                }));
    }

    private static ScCommandNode listCommand() {
        return ScCommandNode.literal("list", ctx -> {
            List<String> names = new ArrayList<>(ctx.variables().names());
            if (names.isEmpty()) {
                ctx.reply("当前没有任何变量");
                return 1;
            }
            names.sort(String::compareTo);
            StringBuilder sb = new StringBuilder("变量（").append(names.size()).append("）：");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('%').append(names.get(i)).append('%');
            }
            ctx.reply(sb.toString());
            return 1;
        });
    }

    /** 可选类型提示：{@code ... string|number|boolean}，用来校验值的形状。 */
    private static void attachTypeHints(ScCommandNode valueNode,
                                        Function<String, CommandEffect> factory) {
        for (String type : TYPE_HINTS) {
            valueNode.then(ScCommandNode.literal(type, factory.apply(type)));
        }
    }

    private static int store(ExecutionContext ctx, String name, String raw, String typeHint, boolean createNew) {
        if (name == null || name.isBlank()) {
            ctx.error("变量名不能为空");
            return 0;
        }
        boolean exists = ctx.variables().get(name).isPresent();
        if (createNew && exists) {
            ctx.error("变量已存在：" + name + "（用 /sc core var set 修改）");
            return 0;
        }
        if (!createNew && !exists) {
            ctx.error("变量不存在：" + name + "（用 /sc core var create 新建）");
            return 0;
        }

        FormatResult formatted = SimpleCommandCore.format(raw, ctx.variables());
        Diagnostics.report(ctx, formatted);
        if (formatted.hasSevere()) {
            return 0;
        }
        String value = formatted.text();
        if (typeHint != null && !matchesType(typeHint, value)) {
            ctx.error("值 \"" + value + "\" 不是合法的 " + typeHint);
            return 0;
        }
        ctx.variables().set(name, value);
        ctx.reply("变量 %" + name + "% = " + value);
        return 1;
    }

    private static boolean matchesType(String typeHint, String value) {
        return switch (typeHint) {
            case "number" -> {
                try {
                    Double.parseDouble(value.trim());
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "boolean" -> "true".equalsIgnoreCase(value.trim()) || "false".equalsIgnoreCase(value.trim());
            default -> true;
        };
    }
}
