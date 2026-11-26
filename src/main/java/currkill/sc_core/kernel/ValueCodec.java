package currkill.sc_core.kernel;

import currkill.sc_csc.api.CscType;
import currkill.sc_csc.api.CscValue;

import java.util.ArrayList;
import java.util.List;

/**
 * 值的字符串编码，用于把 {@link CscValue} 存进世界存档。
 *
 * <p>自描述、可嵌套、不需要转义：类型标记 + 内容 + {@code ;}，
 * 字符串写成「长度:内容」，解析时按长度截取。
 */
public final class ValueCodec {

    private ValueCodec() {
    }

    public static String encode(CscValue value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(CscValue value, StringBuilder sb) {
        switch (value.type()) {
            case INT -> sb.append('i').append(value.asInt()).append(';');
            case FLOAT -> sb.append('f').append(Double.toString(value.asFloat())).append(';');
            case BOOL -> sb.append('b').append(value.asBool() ? '1' : '0').append(';');
            case STRING -> {
                String text = value.asString();
                sb.append('s').append(text.length()).append(':').append(text).append(';');
            }
            case LIST -> {
                List<CscValue> items = value.asList();
                sb.append('l').append(items.size()).append(':');
                for (CscValue item : items) {
                    write(item, sb);
                }
                sb.append(';');
            }
            default -> sb.append('v').append(';');
        }
    }

    /** 解析失败返回 {@code null}（数据损坏时跳过，不让存档整体失败）。 */
    public static CscValue decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            Cursor cursor = new Cursor(encoded);
            return cursor.read();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Cursor {
        private final String text;
        private int pos;

        Cursor(String text) {
            this.text = text;
        }

        CscValue read() {
            char tag = text.charAt(pos++);
            return switch (tag) {
                case 'i' -> CscValue.ofInt(Long.parseLong(readUntil(';')));
                case 'f' -> CscValue.ofFloat(Double.parseDouble(readUntil(';')));
                case 'b' -> CscValue.ofBool('1' == text.charAt(pos++));
                case 's' -> {
                    int colon = text.indexOf(':', pos);
                    int length = Integer.parseInt(text.substring(pos, colon));
                    pos = colon + 1;
                    String value = text.substring(pos, pos + length);
                    pos = pos + length;
                    expect(';');
                    yield CscValue.ofString(value);
                }
                case 'l' -> {
                    int colon = text.indexOf(':', pos);
                    int count = Integer.parseInt(text.substring(pos, colon));
                    pos = colon + 1;
                    List<CscValue> items = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        items.add(read());
                    }
                    expect(';');
                    yield CscValue.ofList(items);
                }
                case 'v' -> {
                    expect(';');
                    yield CscValue.ofVoid();
                }
                default -> throw new IllegalArgumentException("未知的值标记：" + tag);
            };
        }

        private String readUntil(char terminator) {
            int end = text.indexOf(terminator, pos);
            String value = text.substring(pos, end);
            pos = end + 1;
            return value;
        }

        private void expect(char c) {
            if (text.charAt(pos) != c) {
                throw new IllegalArgumentException("期望 " + c + "，实际是 " + text.charAt(pos));
            }
            pos++;
        }
    }

    /** 便捷方法：按类型取默认值。 */
    public static CscValue defaultValue(CscType type) {
        return switch (type) {
            case INT -> CscValue.ofInt(0);
            case FLOAT -> CscValue.ofFloat(0.0D);
            case BOOL -> CscValue.ofBool(false);
            case STRING -> CscValue.ofString("");
            case LIST -> CscValue.ofList(List.of());
            default -> CscValue.ofVoid();
        };
    }
}
