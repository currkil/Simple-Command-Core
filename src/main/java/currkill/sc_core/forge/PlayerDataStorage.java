package currkill.sc_core.forge;

import currkill.sc_core.api.Home;
import currkill.sc_core.api.PlayerDataStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家数据：世界级 SavedData 按 UUID 索引，跨维度可用，不随死亡重置。
 *
 * <p>home 点用前缀 {@code home:} 存在通用 key-value 上，所以将来加别的玩家数据不用改存储结构。
 */
public final class PlayerDataStorage extends SavedData implements PlayerDataStore {

    private static final String DATA_NAME = "sc_players";
    private static final String HOME_PREFIX = "home:";

    private final Map<UUID, Map<String, String>> data = new HashMap<>();

    private PlayerDataStorage() {
    }

    private PlayerDataStorage(CompoundTag tag) {
        for (String uuidKey : tag.getAllKeys()) {
            try {
                UUID player = UUID.fromString(uuidKey);
                CompoundTag stored = tag.getCompound(uuidKey);
                Map<String, String> values = new LinkedHashMap<>();
                for (String key : stored.getAllKeys()) {
                    values.put(key, stored.getString(key));
                }
                data.put(player, values);
            } catch (IllegalArgumentException ignored) {
                // 数据损坏的条目跳过，不影响其他玩家
            }
        }
    }

    public static PlayerDataStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(PlayerDataStorage::new, PlayerDataStorage::new, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        data.forEach((uuid, values) -> {
            CompoundTag stored = new CompoundTag();
            values.forEach(stored::putString);
            tag.put(uuid.toString(), stored);
        });
        return tag;
    }

    @Override
    public Optional<String> get(UUID player, String key) {
        Map<String, String> values = data.get(player);
        return values == null ? Optional.empty() : Optional.ofNullable(values.get(key));
    }

    @Override
    public void set(UUID player, String key, String value) {
        data.computeIfAbsent(player, k -> new LinkedHashMap<>()).put(key, value);
        setDirty();
    }

    @Override
    public void remove(UUID player, String key) {
        Map<String, String> values = data.get(player);
        if (values != null && values.remove(key) != null) {
            setDirty();
        }
    }

    @Override
    public Set<String> keys(UUID player) {
        Map<String, String> values = data.get(player);
        return values == null ? Set.of() : Set.copyOf(values.keySet());
    }

    @Override
    public List<Home> homes(UUID player) {
        List<Home> homes = new ArrayList<>();
        Map<String, String> values = data.get(player);
        if (values == null) {
            return homes;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!entry.getKey().startsWith(HOME_PREFIX)) {
                continue;
            }
            Home home = Home.decode(entry.getKey().substring(HOME_PREFIX.length()), entry.getValue());
            if (home != null) {
                homes.add(home);
            }
        }
        return homes;
    }

    @Override
    public void setHome(UUID player, Home home) {
        set(player, HOME_PREFIX + home.name(), home.encode());
    }

    @Override
    public boolean removeHome(UUID player, String name) {
        Map<String, String> values = data.get(player);
        if (values == null || values.remove(HOME_PREFIX + name) == null) {
            return false;
        }
        setDirty();
        return true;
    }
}
