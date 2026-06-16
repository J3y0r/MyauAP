package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.script.ScriptModule;
import myau.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ScriptCommand extends Command {
    public ScriptCommand() {
        super(new ArrayList<>(Arrays.asList("script", "scripts", "lua")));
    }

    public String getUsage() {
        return ".script <list|load|reload|unload|reloadall> [name]";
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.isEmpty()) {
            ChatUtil.sendFormatted("&7[Script] &rUsage: " + getUsage());
            return;
        }

        String action = args.remove(0).toLowerCase();

        switch (action) {
            case "list":
                if (Myau.scriptManager.getScriptCount() == 0) {
                    ChatUtil.sendFormatted("&7[Script] No scripts loaded.");
                } else {
                    ChatUtil.sendFormatted(String.format("&7[Script] Loaded scripts (&a%d&7):", Myau.scriptManager.getScriptCount()));
                    for (ScriptModule mod : Myau.scriptManager.getScriptModules()) {
                        String err = mod.getScript().getLastError() != null ? " &c(error)" : "";
                        ChatUtil.sendFormatted(String.format("&7 - &r%s &7[%s]%s", mod.getName(), mod.isEnabled() ? "&aON" : "&cOFF", err));
                    }
                }
                break;

            case "load":
                if (args.isEmpty()) {
                    ChatUtil.sendFormatted("&c[Script] Usage: .script load <name>");
                    return;
                }
                Myau.scriptManager.load(args.get(0));
                break;

            case "reload":
                if (args.isEmpty()) {
                    ChatUtil.sendFormatted("&c[Script] Usage: .script reload <name>");
                    return;
                }
                Myau.scriptManager.reload(args.get(0));
                break;

            case "unload":
                if (args.isEmpty()) {
                    ChatUtil.sendFormatted("&c[Script] Usage: .script unload <name>");
                    return;
                }
                Myau.scriptManager.unload(args.get(0));
                break;

            case "reloadall":
                Myau.scriptManager.reloadAll();
                break;

            default:
                ChatUtil.sendFormatted("&c[Script] Unknown action: " + action);
                ChatUtil.sendFormatted("&7[Script] &rUsage: " + getUsage());
                break;
        }
    }
}
