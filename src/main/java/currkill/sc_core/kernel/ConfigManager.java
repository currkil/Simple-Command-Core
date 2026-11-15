package currkill.sc_core.kernel;

import currkill.sc_core.api.NamespaceConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/sc/<namespace>.toml} 的读写。
 *
 * <p>只用 TOML 的一个子集：单行 {@code key = value}、{@code [section]}、{@code #} 注释、
 * 字符串/布尔/整数/小数。够用且不引入第三方依赖。
 */
public final class ConfigManager {

    private static final String HEADER = "Simple Command 配置";

    private final Path configRoot;

    public ConfigManager(Path configRoot) {
        this.configRoot = configRoot;
    }

    public Path configRoot() {
        return configRoot;
    }

    public Path fileOf(String namespace) {
        return configRoot.resolve(namespace + ".toml");
    }

    /**
     * 从磁盘读取并覆盖到 {@code target}，然后补齐默认值。
     *
     * <p>文件不存在不是错误：直接用默认值，并在下次 save 时创建文件。
     */
    public void load(String namespace, NamespaceConfig target) {
        Path file = fileOf(namespace);
        Map<String, Object> parsed = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                parsed.putAll(parse(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException ignored) {
                // 配置坏掉时退回默认值，不让整个模组加载失败
            }
        }
        target.clear();
        for (Map.Entry<String, Object> entry : parsed.entrySet()) {
            target.put(entry.getKey(), entry.getValue());
        }
        target.fillDefaults();
    }

    public void save(NamespaceConfig config) {
        Path file = fileOf(config.namespace());
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, render(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("无法写入配置 " + file, e);
        }
    }

    // ---------------------------------------------------------------- 解析

    private static Map<String, Object> parse(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        String section = "";
        for (String rawLine : text.split("\\R")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty()) {
                continue;
            }
            String fullKey = section.isEmpty() ? key : section + "." + key;
            result.put(fullKey, parseValue(value));
        }
        return result;
    }

    /** 去掉不在引号内的注释。 */
    private static String stripComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inString) {
                i++;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static Object parseValue(String value) {
        if (value.isEmpty()) {
            return "";
        }
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return unescape(value.substring(1, value.length() - 1));
        }
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        if (value.matches("[-+]?\\d+")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        if (value.matches("[-+]?(\\d+\\.\\d*|\\.\\d+|\\d+)([eE][-+]?\\d+)?")) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    private static String unescape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                sb.append(c);
                continue;
            }
            char n = text.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                default -> sb.append('\\').append(n);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 渲染

    private static String render(NamespaceConfig config) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("# ").append(HEADER).append("：").append(config.namespace()).append('\n');
        sb.append("# 由 SC Core 托管。指令里改的是内存，/sc ").append(config.namespace())
                .append(" saveconfig 才会写回这里。\n\n");

        List<Map.Entry<String, Object>> rootKeys = new ArrayList<>();
        Map<String, List<Map.Entry<String, Object>>> sections = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.values().entrySet()) {
            int dot = entry.getKey().lastIndexOf('.');
            if (dot < 0) {
                rootKeys.add(entry);
            } else {
                String section = entry.getKey().substring(0, dot);
                String key = entry.getKey().substring(dot + 1);
                sections.computeIfAbsent(section, k -> new ArrayList<>())
                        .add(Map.entry(key, entry.getValue()));
            }
        }

        for (Map.Entry<String, Object> entry : rootKeys) {
            sb.append(entry.getKey()).append(" = ").append(renderValue(entry.getValue())).append('\n');
        }
        if (!rootKeys.isEmpty() && !sections.isEmpty()) {
            sb.append('\n');
        }
        boolean firstSection = true;
        for (Map.Entry<String, List<Map.Entry<String, Object>>> section : sections.entrySet()) {
            if (!firstSection) {
                sb.append('\n');
            }
            firstSection = false;
            sb.append('[').append(section.getKey()).append("]\n");
            for (Map.Entry<String, Object> entry : section.getValue()) {
                sb.append(entry.getKey()).append(" = ").append(renderValue(entry.getValue())).append('\n');
            }
        }
        return sb.toString();
    }

    private static String renderValue(Object value) {
        if (value instanceof Boolean b) {
            return b.toString();
        }
        if (value instanceof Number n) {
            return n instanceof Double || n instanceof Float ? n.toString() : Long.toString(n.longValue());
        }
        String text = String.valueOf(value);
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + '"';
    }
}
