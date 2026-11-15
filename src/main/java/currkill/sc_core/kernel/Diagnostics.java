package currkill.sc_core.kernel;

import currkill.sc_csc.api.Diagnostic;
import currkill.sc_csc.api.FormatResult;
import currkill.sc_core.api.ExecutionContext;

/**
 * 把 CSC 的诊断按 Core 的日志分级投递出去。
 *
 * <p>严重错误 → 聊天栏 + 游戏日志 + 指令日志；隐式转换 → 只进指令日志。
 */
public final class Diagnostics {

    private Diagnostics() {
    }

    public static void report(ExecutionContext ctx, FormatResult result) {
        for (Diagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.isSevere()) {
                String message = "[CSC] " + diagnostic.message();
                ctx.error(message);
                ctx.gameLog(message);
                ctx.commandLog().severe(diagnostic.toString());
            } else {
                ctx.commandLog().conversion(diagnostic.toString());
            }
        }
    }
}
