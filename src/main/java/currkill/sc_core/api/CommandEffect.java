package currkill.sc_core.api;

/**
 * 指令效果。只依赖 {@link ExecutionContext}，不 import Brigadier/Forge/Paper。
 *
 * <p>这是让内核跨平台复用的关键抽象：Forge 适配层负责把 Brigadier 的
 * {@code CommandContext} 包成 {@link ExecutionContext}，Paper 适配层将来做同样的事。
 */
@FunctionalInterface
public interface CommandEffect {

    /**
     * @param ctx 执行上下文
     * @return 影响的结果码，0 表示失败/无操作
     */
    int execute(ExecutionContext ctx);
}
