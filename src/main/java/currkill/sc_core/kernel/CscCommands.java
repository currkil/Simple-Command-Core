package currkill.sc_core.kernel;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscEvaluator;
import currkill.sc_core.SimpleCommandCore;
import currkill.sc_core.api.ExecutionContext;
import currkill.sc_core.api.ScArgumentType;
import currkill.sc_core.api.ScCommandNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code csc} 命名空间的指令树。
 *
 * <p>指令语法与脚本语法对齐：{@code /sc csc <语句>} 把一段语句直接交给脚本解释器，
 * 因此 {@code var} / {@code list} / {@code io} / {@code log} / {@code file} / {@code sys}
 * 都能直接在聊天栏用，不需要单独为每条语句写一遍解析。
 *
 * <p>另外两条入口：{@code run} 读脚本文件，{@code repl} 进入逐行模式。
 */
public final class CscCommands {

    private CscCommands() {
    }

    /**
     * 直接贡献到 {@code csc} 命名空间下的节点（不再套一层同名 literal）。
     *
     * <p>结果是：{@code /sc csc run <脚本>}、{@code /sc csc repl}、以及
     * {@code /sc csc <语句...>}——最后一条用贪心字符串，所以 {@code var} / {@code list} /
     * {@code io} 等关键字不需要各写一个 Brigadier 节点。
     */
    public static List<ScCommandNode> build() {
        return List.of(
                ScCommandNode.literal("run").requires(2)
                        .then(ScCommandNode.argument("script", ScArgumentType.WORD,
                                ctx -> runScript(ctx, ctx.arg("script")))),
                ScCommandNode.literal("repl", ctx -> toggleRepl(ctx)).requires(2),
                ScCommandNode.argument("statement", ScArgumentType.GREEDY_STRING, CscCommands::statement)
                        .requires(2)
        );
    }

    /** 执行一条或多条语句（分号分隔）。 */
    private static int statement(ExecutionContext ctx) {
        String text = ctx.arg("statement");
        if (text.isBlank()) {
            ctx.error("缺少语句");
            return 0;
        }
        return execute(ctx, normalize(text));
    }

    /**
     * 把顶层的 {@code ;} 换成换行：指令里可以用分号把多条语句写在一行，
     * 而 {@code ctrl for(a; b; c)} 这种括号内的分号原样保留。
     */
    static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int paren = 0;
        int bracket = 0;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                sb.append(c);
                if (c == '\\' && i + 1 < text.length()) {
                    sb.append(text.charAt(++i));
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    sb.append(c);
                }
                case '(' -> {
                    paren++;
                    sb.append(c);
                }
                case ')' -> {
                    paren--;
                    sb.append(c);
                }
                case '[' -> {
                    bracket++;
                    sb.append(c);
                }
                case ']' -> {
                    bracket--;
                    sb.append(c);
                }
                case ';' -> sb.append(paren == 0 && bracket == 0 ? '\n' : c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 读取并执行脚本文件。 */
    private static int runScript(ExecutionContext ctx, String name) {
        if (name.isBlank()) {
            ctx.error("缺少脚本名");
            return 0;
        }
        Path file;
        try {
            file = ctx.paths().scriptFile(name);
        } catch (RuntimeException e) {
            ctx.error("无法定位脚本：" + e.getMessage());
            return 0;
        }
        if (!Files.isRegularFile(file)) {
            ctx.error("脚本不存在：" + file);
            return 0;
        }
        String script;
        try {
            script = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ctx.error("读取脚本失败：" + e.getMessage());
            return 0;
        }
        return execute(ctx, script);
    }

    private static int toggleRepl(ExecutionContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.error("REPL 只能在游戏内使用");
            return 0;
        }
        boolean on = SimpleCommandCore.services().repl().toggle(ctx.senderId());
        if (on) {
            ctx.reply("已进入 REPL：接下来每条聊天消息都会当作 CSC 语句执行，输入 sys exit 退出");
        } else {
            // 退出时把会话里打开的日志流/文件流一起关掉
            SimpleCommandCore.services().sessions().close(ctx.senderId());
            ctx.reply("已退出 REPL");
        }
        return 1;
    }

    private static int execute(ExecutionContext ctx, String script) {
        CscContext csc = ctx.csc();
        try {
            CscEvaluator.evaluateScript(script, csc);
        } finally {
            if (csc instanceof HostCscContext host) {
                host.closeAll();
            }
        }
        return 1;
    }
}
