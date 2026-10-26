package com.asterovie.momo.starpact.server.command;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /starpact — toggle / move / reset / status / reload / help */
public final class StarPactCommand implements CommandExecutor, TabCompleter {

    private final StarPactPlugin plugin;

    public StarPactCommand(StarPactPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "panel", "gui", "menu" -> panel(sender);
            case "toggle" -> toggle(sender);
            case "move" -> move(sender, args);
            case "reset" -> reset(sender);
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            default -> msg(sender, "command.unknown");
        }
        return true;
    }

    private void panel(CommandSender sender) {
        Player p = asPlayer(sender);
        if (p == null) {
            return;
        }
        plugin.panel().openPanel(p);
    }

    private void help(CommandSender sender) {
        for (String line : plugin.lang().getStringList("command.help")) {
            sender.sendMessage(com.asterovie.momo.starpact.server.util.Texts.colorize(line));
        }
    }

    private void toggle(CommandSender sender) {
        Player p = asPlayer(sender);
        if (p == null) {
            return;
        }
        PlayerPrefs.Pref pref = plugin.prefs().of(p.getUniqueId());
        boolean next = !pref.visible;
        plugin.hud().setVisible(p, next);
        msg(sender, next ? "command.toggled-on" : "command.toggled-off");
    }

    private void move(CommandSender sender, String[] args) {
        Player p = asPlayer(sender);
        if (p == null) {
            return;
        }
        if (!plugin.spConfig().allowPlayerMove()) {
            msg(sender, "command.move-disabled");
            return;
        }
        if (args.length < 3) {
            msg(sender, "command.usage-move");
            return;
        }
        int x;
        int y;
        try {
            x = Integer.parseInt(args[1]);
            y = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            msg(sender, "command.move-invalid");
            return;
        }
        // 防止被塞进离谱坐标把 UI 顶到屏幕外
        x = Math.max(-4096, Math.min(4096, x));
        y = Math.max(-4096, Math.min(4096, y));

        plugin.prefs().setPosition(p.getUniqueId(), x, y);
        plugin.hud().applyPosition(p);
        msg(sender, "command.moved", "x", String.valueOf(x), "y", String.valueOf(y));
    }

    private void reset(CommandSender sender) {
        Player p = asPlayer(sender);
        if (p == null) {
            return;
        }
        plugin.prefs().resetPosition(p.getUniqueId());
        plugin.hud().applyPosition(p);
        msg(sender, "command.reset");
    }

    private void status(CommandSender sender) {
        Player p = asPlayer(sender);
        if (p == null) {
            return;
        }
        PlayerPrefs.Pref pref = plugin.prefs().of(p.getUniqueId());
        List<TrackEntry> tracks = plugin.chemdah().readTracks(p, plugin.spConfig().maxTracks());
        List<String> lines = plugin.lang().getStringList("command.status");
        for (String line : lines) {
            sender.sendMessage(com.asterovie.momo.starpact.server.util.Texts.colorize(line
                    .replace("{visible}", pref.visible ? "\u00a7a\u5f00" : "\u00a77\u5173")
                    .replace("{x}", String.valueOf(pref.x))
                    .replace("{y}", String.valueOf(pref.y))
                    .replace("{tracks}", String.valueOf(tracks.size()))
                    .replace("{ax}", plugin.ax().available()
                            ? "\u00a7a\u5df2\u63a5\u5165" : "\u00a7c\u672a\u63a5\u5165")
                    .replace("{ch}", plugin.chemdah().available()
                            ? "\u00a7a\u5df2\u63a5\u5165" : "\u00a7c\u672a\u63a5\u5165")));
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("starpact.command.reload")) {
            msg(sender, "command.no-permission");
            return;
        }
        plugin.reloadAll();
        msg(sender, "command.reloaded");
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        msg(sender, "command.not-player");
        return null;
    }

    private void msg(CommandSender sender, String key, String... kv) {
        String raw = plugin.lang().getString(key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            raw = raw.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        sender.sendMessage(com.asterovie.momo.starpact.server.util.Texts.colorize(raw));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("toggle", "move", "reset", "status", "help", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    if ("reload".equals(s) && !sender.hasPermission("starpact.command.reload")) {
                        continue;
                    }
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && "move".equals(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("-5", "-150", "0", "8");
        }
        if (args.length == 3 && "move".equals(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("120", "168", "200");
        }
        return List.of();
    }
}
