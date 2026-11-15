package currkill.sc_core;

import currkill.sc_csc.Csc;
import currkill.sc_csc.api.FormatResult;
import currkill.sc_csc.api.VariableProvider;
import currkill.sc_core.api.CommandLogger;
import currkill.sc_core.api.ExecutionContext;
import currkill.sc_core.api.ModRegistration;
import currkill.sc_core.api.ScPaths;
import currkill.sc_core.kernel.CoreCommands;
import currkill.sc_core.kernel.CoreServices;
import currkill.sc_core.kernel.Diagnostics;
import currkill.sc_core.kernel.NamespaceRegistry;

/**
 * SC Core 的对外门面，也是子模组唯一该碰的入口。
 *
 * <p>子模组通过它做三件事：注册自己的命名空间；把指令参数交给 CSC 格式化；
 * 取共享设施（路径、指令日志、命名空间注册表）。子模组不应直接 import
 * {@code currkill.sc_csc} 的内部类——CSC 的语义变更由这里吸收。
 *
 * <p>所有方法都是静态的，因为 Core 是单例模组；{@link #init} 由平台适配层在
 * 模组构造时调用一次，其余方法在初始化完成后才可用。
 */
public final class SimpleCommandCore {

    /** 与 {@code mods.toml} 的 modId 一致。 */
    public static final String MOD_ID = "sc_core";

    /** 内置命名空间。 */
    public static final String CORE_NAMESPACE = NamespaceRegistry.CORE_NAMESPACE;

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

    /** Core 自己注册内置的 {@code core} 命名空间。 */
    static void registerBuiltins() {
        register(ModRegistration.builder(MOD_ID)
                .namespace(CORE_NAMESPACE)
                .commands(CoreCommands::build)
                .build());
    }

    // ---------------------------------------------------------------- CSC 封装

    /**
     * 格式化一段文本，返回结果文本与全部诊断。
     *
     * <p>适合调用方想自己决定提示方式的场景；诊断的级别含义见 CSC 的
     * {@code Severity}，投递策略由调用方实现。
     *
     * @param raw  原始文本，可为 {@code null}
     * @param vars 变量来源，可为 {@code null}（等价于空来源）
     */
    public static FormatResult format(String raw, VariableProvider vars) {
        return Csc.format(raw, vars);
    }

    /**
     * 只要格式化后的文本，丢掉诊断。需要提示玩家时请改用
     * {@link #format(String, VariableProvider)} 或 {@link #formatText(ExecutionContext, String)}。
     */
    public static String formatText(String raw, VariableProvider vars) {
        return Csc.formatText(raw, vars);
    }

    /**
     * 用执行上下文的变量表格式化，并按 Core 的分级自动投递诊断，返回结果文本。
     *
     * <p>严重错误进聊天栏、游戏日志、指令日志；隐式转换只进指令日志。这是子模组
     * 处理指令参数时最常用的重载。
     */
    public static String formatText(ExecutionContext ctx, String raw) {
        FormatResult result = Csc.format(raw, ctx.variables());
        Diagnostics.report(ctx, result);
        return result.text();
    }

    /**
     * 直接求值一条裸表达式（不带表达式包装），给脚本层、终端一类调用方复用。
     *
     * @throws currkill.sc_csc.api.EvaluationException 语法错误、除零、未知变量等
     */
    public static String evaluate(String expression, VariableProvider vars) {
        return Csc.evaluate(expression, vars);
    }

    // ---------------------------------------------------------------- 共享设施

    /** 全家桶统一的路径解析（配置、备份、日志、缓存）。 */
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
