package myau.script;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.modules.HUD;
import myau.util.ChatUtil;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import org.luaj.vm2.LuaValue;

import java.io.File;
import java.util.*;

public class ScriptManager {
    private final File scriptsDir;
    private final Map<String, LuaScript> scripts;
    private final LinkedHashMap<String, ScriptModule> scriptModules;
    private boolean initialized;

    public ScriptManager() {
        this.scriptsDir = new File("./config/Myau/", "scripts");
        this.scripts = new LinkedHashMap<>();
        this.scriptModules = new LinkedHashMap<>();
        this.initialized = false;
    }

    public void init() {
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs();
        }
        loadAll();
        this.initialized = true;
        ChatUtil.sendFormatted(String.format("&7[Script] Loaded &a%d &7script(s)", this.scriptModules.size()));
    }

    public Collection<ScriptModule> getScriptModules() {
        return Collections.unmodifiableCollection(this.scriptModules.values());
    }

    public int getScriptCount() {
        return this.scriptModules.size();
    }

    public File getScriptsDir() {
        return scriptsDir;
    }

    // ---- Lifecycle ----

    public void loadAll() {
        if (!scriptsDir.exists() || !scriptsDir.isDirectory()) return;
        File[] files = scriptsDir.listFiles((dir, name) -> name.endsWith(".lua"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".lua", "");
            if (!scripts.containsKey(name)) {
                createScript(file);
            }
        }
    }

    public boolean load(String name) {
        if (scripts.containsKey(name)) {
            ChatUtil.sendFormatted("&c[Script] Already loaded: " + name);
            return false;
        }
        File file = new File(scriptsDir, name + ".lua");
        if (!file.exists()) {
            ChatUtil.sendFormatted("&c[Script] File not found: " + name + ".lua");
            return false;
        }
        if (createScript(file)) {
            ChatUtil.sendFormatted(String.format("&7[Script] Loaded &a%s", name));
            return true;
        }
        return false;
    }

    public boolean reload(String name) {
        LuaScript script = scripts.get(name);
        if (script == null) {
            ChatUtil.sendFormatted("&c[Script] Not loaded: " + name);
            return false;
        }
        boolean wasEnabled = scriptModules.get(name).isEnabled();
        script.reload();
        ChatUtil.sendFormatted(String.format("&7[Script] Reloaded &a%s", name));
        return true;
    }

    public boolean unload(String name) {
        LuaScript script = scripts.remove(name);
        ScriptModule mod = scriptModules.remove(name);
        if (script == null) {
            ChatUtil.sendFormatted("&c[Script] Not loaded: " + name);
            return false;
        }
        script.unload();
        ChatUtil.sendFormatted(String.format("&7[Script] Unloaded &c%s", name));
        return true;
    }

    public void reloadAll() {
        for (LuaScript script : scripts.values()) {
            script.reload();
        }
        ChatUtil.sendFormatted(String.format("&7[Script] Reloaded &a%d &7script(s)", this.scriptModules.size()));
    }

    private boolean createScript(File file) {
        String name = file.getName().replace(".lua", "");
        LuaScript script = new LuaScript(file);
        if (!script.load()) return false;

        scripts.put(name, script);
        ScriptModule module = new ScriptModule(script);
        scriptModules.put(name, module);
        return true;
    }

    // ---- Event helpers ----

    private boolean isScriptEnabled(LuaScript script) {
        for (ScriptModule mod : scriptModules.values()) {
            if (mod.getScript() == script) {
                return mod.isEnabled();
            }
        }
        return true; // fallback: if no module found, allow
    }

    private void onTick() {
        if (!initialized) return;
        for (LuaScript script : scripts.values()) {
            if (isScriptEnabled(script)) {
                script.onTick();
            }
        }
    }

    private void onChat(String message) {
        if (!initialized) return;
        LuaValue luaMsg = LuaValue.valueOf(message);
        for (LuaScript script : scripts.values()) {
            if (isScriptEnabled(script)) {
                script.fireEvent(ScriptEvent.CHAT, luaMsg);
            }
        }
    }

    private void onWorldLoad() {
        if (!initialized) return;
        for (LuaScript script : scripts.values()) {
            if (isScriptEnabled(script)) {
                script.fireEvent(ScriptEvent.WORLD_LOAD);
            }
        }
    }

    // ---- EventTargets ----

    @EventTarget
    public void onTickEvent(TickEvent event) {
        onTick();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onWorldLoad();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!initialized || event.getType() != EventType.RECEIVE) return;
        if (event.getPacket() instanceof S02PacketChat) {
            S02PacketChat chat = (S02PacketChat) event.getPacket();
            IChatComponent component = chat.getChatComponent();
            String text = component.getUnformattedText();
            if (text != null && !text.isEmpty()) {
                onChat(text);
            }
        }
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!initialized) return;
        for (ScriptModule mod : scriptModules.values()) {
            if (mod.getKey() != 0 && mod.getKey() == event.getKey()) {
                mod.toggle();
                HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
                if (hud != null && hud.toggleAlerts.getValue()) {
                    String status = mod.isEnabled() ? "&a&lON" : "&c&lOFF";
                    ChatUtil.sendFormatted(String.format("%s%s: %s&r", Myau.clientName, mod.getName(), status));
                }
            }
        }
    }
}
