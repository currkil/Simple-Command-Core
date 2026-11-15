package currkill.sc_core.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 按玩家 UUID 存跨世界持久化数据。
 *
 * <p>底层是挂 overworld 的世界级 SavedData，不用 Capability（跨维度、死亡时生命周期太复杂）。
 * 提供通用的 key-value，再在它之上给 home 家族一个便利接口。
 */
public interface PlayerDataStore {

    Optional<String> get(UUID player, String key);

    void set(UUID player, String key, String value);

    void remove(UUID player, String key);

    /** 某个玩家已有的全部 key。 */
    Set<String> keys(UUID player);

    /** 某个玩家的全部 home（顺序不定）。 */
    List<Home> homes(UUID player);

    /** 写入/覆盖一个 home。 */
    void setHome(UUID player, Home home);

    /**
     * @return 是否真的删掉了（不存在时返回 false）
     */
    boolean removeHome(UUID player, String name);
}
