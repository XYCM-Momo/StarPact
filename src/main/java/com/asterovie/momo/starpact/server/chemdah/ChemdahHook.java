package com.asterovie.momo.starpact.server.chemdah;

import com.asterovie.momo.starpact.server.model.QuestInfo;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Chemdah 存在性探测与读取入口。
 * <b>本类不得出现任何 Chemdah 类型</b>（方法签名、字段、泛型都不行），
 * 否则 Chemdah 未安装时会在类解析阶段抛 NoClassDefFoundError，catch 不住。
 */
public final class ChemdahHook {
    private final boolean available;
    private Method readMethod;
    private Method readQuestsMethod;

    public ChemdahHook() {
        boolean bl = false;
        try {
            Class.forName("ink.ptms.chemdah.api.ChemdahAPI", false, ChemdahHook.class.getClassLoader());
            bl = Bukkit.getPluginManager().getPlugin("Chemdah") != null;
        }
        catch (Throwable throwable) {
            bl = false;
        }
        this.available = bl;
        if (this.available) {
            try {
                Method method = Class.forName("com.asterovie.momo.starpact.server.chemdah.ChemdahReader", true, ChemdahHook.class.getClassLoader()).getDeclaredMethod("read", Player.class, Integer.TYPE);
                method.setAccessible(true);
                this.readMethod = method;
            }
            catch (Throwable throwable) {
                Bukkit.getLogger().warning("[StarPact] Chemdah 读取器初始化失败: " + String.valueOf(throwable));
            }
        }
    }

    public boolean available() {
        return this.available;
    }

    public List<QuestInfo> readQuests(Player player) {
        if (!this.available || player == null) {
            return List.of();
        }
        try {
            Object object;
            if (this.readQuestsMethod == null) {
                this.readQuestsMethod = Class.forName("com.asterovie.momo.starpact.server.chemdah.ChemdahReader", true, ChemdahHook.class.getClassLoader()).getDeclaredMethod("readQuests", Player.class);
                this.readQuestsMethod.setAccessible(true);
            }
            if ((object = this.readQuestsMethod.invoke(null, player)) instanceof List) {
                List list = (List)object;
                return list;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return List.of();
    }

    public List<TrackEntry> readTracks(Player player, int n) {
        if (!this.available || this.readMethod == null || player == null || n <= 0) {
            return List.of();
        }
        try {
            Object object = this.readMethod.invoke(null, player, n);
            if (object instanceof List) {
                List list = (List)object;
                return list;
            }
            return List.of();
        }
        catch (Throwable throwable) {
            return new ArrayList<TrackEntry>();
        }
    }
}

