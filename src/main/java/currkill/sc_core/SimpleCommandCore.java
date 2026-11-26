package currkill.sc_core;

import currkill.sc_csc.api.CscContext;
import currkill.sc_csc.api.CscEvaluator;
import currkill.sc_csc.api.CscValue;
import currkill.sc_core.api.CommandLogger;
import currkill.sc_core.api.ExecutionContext;
import currkill.sc_core.api.ModRegistration;
import currkill.sc_core.api.ScPaths;
import currkill.sc_core.kernel.CscCommands;
import currkill.sc_core.kernel.CoreServices;
import currkill.sc_core.kernel.NamespaceRegistry;

/**
 * SC Core 的对外门面，也是子模组唯一该碰的入口。
 *
 * <p>子模组通过它做三件事：注册自己的命名空间；把指令参数交给 CSC 处理；
 * 取共享设施（路径、指令日志、CSC 会话表）。子模组不应直接依赖 CSC 的内部实现——
 * CSC 的语义变更由这里吸收。
 *
 * <p>所有方法都是静态的，因为 Core 是单例模组；{@link #init} 由平台适配层在
 * 模组构造时调用一次，其余方法在初始化完成后才可用。
 */
public final class SimpleCommandCore {

    /** 与 {@code mods.toml} 的 modId 一致。 */
    public static final String MOD_ID = "sc_core";

    /** 内置命名空间：CSC 的全部指令都挂在这里。 */
    public static final String CSC_NAMESPACE = NamespaceRegistry.CSC_NAMESPACE;

    private static volatile CoreServices services;

    private SimpleCommandCore() {
    }

    /** 由平台适配层在模组构造时调用一次。 */
    public static void init(CoreServices coreServices) {
        services = coreServices;
    }

    /**
     * 取运行时服务集合。
     *
     * @throws IllegalStateException 尚未初始化
     */
    public static CoreServices services() {
        CoreServices current = services;
        if (current == null) {
            throw new IllegalStateException("SC Core 尚未初始化");
        }
        return current;
    }

    /** 是否已经完成初始化。 */
    public static boolean initialized() {
        return services != null;
    }

    // ---------------------------------------------------------------- 注册

    /** 子模组注册自己的命名空间、指令树、短名、配置默认值。 */
    public static void register(ModRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration 不能为空");
        }
        services().registry().register(registration);
    }

    /** Core 自己注册内置的 {@code csc} 命名空间。 */
    static void registerBuiltins() {
        register(ModRegistration.builder(MOD_ID)
                .namespace(CSC_NAMESPACE)
                .commands(CscCommands::build)
                .build());
    }

    // ---------------------------------------------------------------- CSC 封装

    /**
     * 用执行上下文跑一遍 CSC 文本格式化：处理转义、注释、变量、表达式，并自动按
     * Core 的分级投递诊断。这是子模组处理指令参数时最常用的入口。
     */
    public static String formatText(ExecutionContext ctx, String raw) {
        return CscEvaluator.formatText(raw, ctx.csc());
    }

    /** 用给定的 CSC 上下文做文本格式化，诊断同样交回上下文。 */
    public static String formatText(String raw, CscContext context) {
        return CscEvaluator.formatText(raw, context);
    }

    /**
     * 求值一条表达式，返回 {@link CscValue}（类型信息保留）。
     *
     * @throws currkill.sc_csc.api.CscRuntimeException 语法错误、除零、未知变量、转换失败
     */
    public static CscValue evaluate(String expression, CscContext context) {
        return CscEvaluator.evaluate(expression, context);
    }

    /** 执行脚本；语法与运行时错误通过上下文记成严重错误，不向调用方抛。 */
    public static CscValue runScript(String script, CscContext context) {
        return CscEvaluator.evaluateScript(script, context);
    }

    // ---------------------------------------------------------------- 共享设施

    /** 全家桶统一的路径解析（配置、备份、日志、脚本、缓存）。 */
    public static ScPaths paths() {
        return services().paths();
    }

    /** 指令日志（{@code sc_logs/command.log}，按大小滚动）。 */
    public static CommandLogger commandLog() {
        return services().commandLog();
    }

    /** 命名空间注册表，只读地看已注册的命名空间。 */
    public static NamespaceRegistry namespaces() {
        return services().registry();
    }
}
