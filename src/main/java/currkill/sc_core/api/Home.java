package currkill.sc_core.api;

/**
 * 一个 home 点。
 *
 * @param name      玩家自己起的名字
 * @param dimension 维度 id，例如 {@code minecraft:overworld}
 * @param x         X 坐标
 * @param y         Y 坐标
 * @param z         Z 坐标
 * @param yaw       水平朝向
 * @param pitch     垂直朝向
 */
public record Home(String name, String dimension, double x, double y, double z, float yaw, float pitch) {

    /** 存储用的紧凑文本格式：{@code dim|x|y|z|yaw|pitch}。 */
    public String encode() {
        return dimension + "|" + x + "|" + y + "|" + z + "|" + yaw + "|" + pitch;
    }

    /**
     * 解析 {@link #encode()} 的产物。
     *
     * @return 解析失败返回 {@code null}（数据损坏时跳过而不是崩）
     */
    public static Home decode(String name, String encoded) {
        if (encoded == null) {
            return null;
        }
        String[] parts = encoded.split("\\|");
        if (parts.length != 6) {
            return null;
        }
        try {
            return new Home(name, parts[0],
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
