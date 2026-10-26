package com.asterovie.momo.starpact.server.command;

import com.asterovie.momo.starpact.server.StarPactPlugin;
import com.asterovie.momo.starpact.server.model.TrackEntry;
import com.asterovie.momo.starpact.server.storage.PlayerPrefs;
import com.asterovie.momo.starpact.server.util.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /sp 命令实现。
 */
public final class StarPactCommand
implements CommandExecutor,
TabCompleter {
    private final StarPactPlugin plugin;

    public StarPactCommand(StarPactPlugin starPactPlugin) {
        this.plugin = starPactPlugin;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        String string2;
        if (stringArray.length == 0) {
            this.help(commandSender);
            return true;
        }
        switch (string2 = stringArray[0].toLowerCase(Locale.ROOT)) {
            case "help": {
                this.help(commandSender);
                break;
            }
            case "panel": 
            case "gui": 
            case "menu": {
                this.panel(commandSender);
                break;
            }
            case "toggle": {
                this.toggle(commandSender);
                break;
            }
            case "move": {
                this.move(commandSender, stringArray);
                break;
            }
            case "reset": {
                this.reset(commandSender);
                break;
            }
            case "status": {
                this.status(commandSender);
                break;
            }
            case "reload": {
                this.reload(commandSender);
                break;
            }
            default: {
                this.msg(commandSender, "command.unknown", new String[0]);
            }
        }
        return true;
    }

    private void panel(CommandSender commandSender) {
        Player player = this.asPlayer(commandSender);
        if (player == null) {
            return;
        }
        this.plugin.panel().openPanel(player);
    }

    private void help(CommandSender commandSender) {
        for (String string : this.plugin.lang().getStringList("command.help")) {
            commandSender.sendMessage(Texts.colorize(string));
        }
    }

    private void toggle(CommandSender commandSender) {
        Player player = this.asPlayer(commandSender);
        if (player == null) {
            return;
        }
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        boolean bl = !pref.visible;
        this.plugin.hud().setVisible(player, bl);
        this.msg(commandSender, bl ? "command.toggled-on" : "command.toggled-off", new String[0]);
    }

    private void move(CommandSender commandSender, String[] stringArray) {
        int n;
        int n2;
        Player player = this.asPlayer(commandSender);
        if (player == null) {
            return;
        }
        if (!this.plugin.spConfig().allowPlayerMove()) {
            this.msg(commandSender, "command.move-disabled", new String[0]);
            return;
        }
        if (stringArray.length < 3) {
            this.msg(commandSender, "command.usage-move", new String[0]);
            return;
        }
        try {
            n2 = Integer.parseInt(stringArray[1]);
            n = Integer.parseInt(stringArray[2]);
        }
        catch (NumberFormatException numberFormatException) {
            this.msg(commandSender, "command.move-invalid", new String[0]);
            return;
        }
        n2 = Math.max(-4096, Math.min(4096, n2));
        n = Math.max(-4096, Math.min(4096, n));
        this.plugin.prefs().setPosition(player.getUniqueId(), n2, n);
        this.plugin.hud().applyPosition(player);
        this.msg(commandSender, "command.moved", "x", String.valueOf(n2), "y", String.valueOf(n));
    }

    private void reset(CommandSender commandSender) {
        Player player = this.asPlayer(commandSender);
        if (player == null) {
            return;
        }
        this.plugin.prefs().resetPosition(player.getUniqueId());
        this.plugin.hud().applyPosition(player);
        this.msg(commandSender, "command.reset", new String[0]);
    }

    private void status(CommandSender commandSender) {
        Player player = this.asPlayer(commandSender);
        if (player == null) {
            return;
        }
        PlayerPrefs.Pref pref = this.plugin.prefs().of(player.getUniqueId());
        List<TrackEntry> list = this.plugin.chemdah().readTracks(player, this.plugin.spConfig().maxTracks());
        List<String> list2 = this.plugin.lang().getStringList("command.status");
        for (String string : list2) {
            commandSender.sendMessage(Texts.colorize(string.replace("{visible}", pref.visible ? "§a开" : "§7关").replace("{x}", String.valueOf(pref.x)).replace("{y}", String.valueOf(pref.y)).replace("{tracks}", String.valueOf(list.size())).replace("{ax}", this.plugin.ax().available() ? "§a已接入" : "§c未接入").replace("{ch}", this.plugin.chemdah().available() ? "§a已接入" : "§c未接入")));
        }
    }

    private void reload(CommandSender commandSender) {
        if (!commandSender.hasPermission("starpact.command.reload")) {
            this.msg(commandSender, "command.no-permission", new String[0]);
            return;
        }
        this.plugin.reloadAll();
        this.msg(commandSender, "command.reloaded", new String[0]);
    }

    private Player asPlayer(CommandSender commandSender) {
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            return player;
        }
        this.msg(commandSender, "command.not-player", new String[0]);
        return null;
    }

    private void msg(CommandSender commandSender, String string, String ... stringArray) {
        String string2 = this.plugin.lang().getString(string, string);
        int n = 0;
        while (n + 1 < stringArray.length) {
            string2 = string2.replace("{" + stringArray[n] + "}", stringArray[n + 1]);
            n += 2;
        }
        commandSender.sendMessage(Texts.colorize(string2));
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (stringArray.length == 1) {
            ArrayList<String> arrayList = new ArrayList<String>();
            for (String string2 : List.of("toggle", "move", "reset", "status", "help", "reload")) {
                if (!string2.startsWith(stringArray[0].toLowerCase(Locale.ROOT)) || "reload".equals(string2) && !commandSender.hasPermission("starpact.command.reload")) continue;
                arrayList.add(string2);
            }
            return arrayList;
        }
        if (stringArray.length == 2 && "move".equals(stringArray[0].toLowerCase(Locale.ROOT))) {
            return List.of("-5", "-150", "0", "8");
        }
        if (stringArray.length == 3 && "move".equals(stringArray[0].toLowerCase(Locale.ROOT))) {
            return List.of("120", "168", "200");
        }
        return List.of();
    }
}

