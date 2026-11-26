package currkill.sc_core.api;

import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;

import java.util.Map;
import java.util.Set;

/**
 * 类型化变量表。值是 {@link CscValue}，不是裸字符串——类型信息一路保留到存档。
 *
 * <p>变量必须先声明再使用（C++ 风格）；赋值时按声明类型做隐式转换，转换记录由
 * CSC 通过诊断交回。
 */
public interface VariableStore {

    boolean has(String name);

    /** 不存在时返回 {@code null}。 */
    CscValue get(String name);

    /** 声明类型；不存在时返回 {@code null}。 */
    CscType typeOf(String name);

    void declare(String name, CscType type, CscValue initial);

    void set(String name, CscValue value);

    void delete(String name);

    Set<String> names();

    /** 只读快照，给展示用。 */
    Map<String, CscValue> snapshot();
}
