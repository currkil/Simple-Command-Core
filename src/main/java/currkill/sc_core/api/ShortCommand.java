package currkill.sc_core.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 短名：把命名空间里的某个子指令挂成根指令别名（如 {@code /backup} 等价于 {@code /sc server backup}）。
 *
 * @param alias 根级别名
 * @param path  命名空间内的子指令路径，例如 {@code ["backup"]} 或 {@code ["tpa", "here"]}
 */
public record ShortCommand(String alias, List<String> path) {

    public ShortCommand {
        path = List.copyOf(path);
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("短名不能为空");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("短名 " + alias + " 缺少子指令路径");
        }
    }

    /** 解析 {@code "tpa here"} / {@code "tpa.here"} / {@code "tpa/here"} 这几种写法。 */
    public static List<String> splitPath(String raw) {
        String[] parts = raw.trim().split("[\\s./]+");
        List<String> path = new ArrayList<>();
        Arrays.stream(parts).filter(s -> !s.isBlank()).forEach(path::add);
        return path;
    }
}
