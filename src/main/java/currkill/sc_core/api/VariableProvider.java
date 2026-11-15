package currkill.sc_core.api;

import java.util.Optional;
import java.util.Set;

/**
 * Core 的变量接口。语义上等同 CSC 的 {@code VariableProvider}，只是把读写都显式化，
 * 并多给一个 {@link #names()} 方便子模组列表。
 *
 * <p>继承 CSC 的接口意味着 CSC 不需要认识 Core，方向是 Core → CSC，不会形成循环依赖。
 */
public interface VariableProvider extends currkill.sc_csc.api.VariableProvider {

    @Override
    Optional<String> get(String name);

    @Override
    void set(String name, String value);

    @Override
    void delete(String name);

    /** 当前全部变量名，用于 {@code /sc core var list} 一类展示。 */
    default Set<String> names() {
        return Set.of();
    }
}
