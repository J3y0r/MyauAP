package myau.script;

import myau.script.api.MinecraftAPI;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class LuaScript {
    private final String name;
    private final File file;
    private Globals globals;
    private boolean loaded;
    private final Map<ScriptEvent, List<LuaValue>> eventHandlers;
    private String lastError;

    public LuaScript(File file) {
        this.name = file.getName().replace(".lua", "");
        this.file = file;
        this.eventHandlers = new HashMap<>();
        this.loaded = false;
        for (ScriptEvent event : ScriptEvent.values()) {
            this.eventHandlers.put(event, new ArrayList<>());
        }
    }

    public boolean load() {
        try {
            String source = new String(Files.readAllBytes(this.file.toPath()));
            this.globals = JsePlatform.standardGlobals();

            // Set up the event registration table
            LuaTable eventTable = new LuaTable();
            for (ScriptEvent event : ScriptEvent.values()) {
                final String eventName = event.name().toLowerCase();
                eventTable.set(eventName, new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue handler) {
                        if (handler.isfunction()) {
                            eventHandlers.get(ScriptEvent.valueOf(eventName.toUpperCase())).add(handler);
                        }
                        return LuaValue.NIL;
                    }
                });
            }
            globals.set("event", eventTable);

            // Inject API
            MinecraftAPI.inject(globals);

            // Execute script
            LuaValue chunk = globals.load(source, this.name);
            chunk.call();
            this.loaded = true;
            this.lastError = null;

            // Fire WORLD_LOAD immediately if already in a world
            if (Minecraft.getMinecraft().theWorld != null) {
                fireEvent(ScriptEvent.WORLD_LOAD);
            }

            return true;
        } catch (Exception e) {
            this.lastError = e.getMessage();
            ChatUtil.sendFormatted(String.format("&c[Script] Failed to load '%s': %s", this.name, e.getMessage()));
            return false;
        }
    }

    public void unload() {
        this.loaded = false;
        this.eventHandlers.values().forEach(List::clear);
        this.globals = null;
    }

    public boolean reload() {
        unload();
        return load();
    }

    public void fireEvent(ScriptEvent event, LuaValue... args) {
        if (!this.loaded) return;
        List<LuaValue> handlers = this.eventHandlers.get(event);
        if (handlers == null || handlers.isEmpty()) return;

        LuaValue luaArgs = args.length == 0 ? LuaValue.NIL :
                args.length == 1 ? args[0] :
                        LuaValue.listOf(args);

        for (LuaValue handler : handlers) {
            try {
                handler.call(luaArgs);
            } catch (Exception e) {
                ChatUtil.sendFormatted(String.format("&c[Script:%s] Error in %s handler: %s", this.name, event.name(), e.getMessage()));
            }
        }
    }

    public void onTick() {
        if (this.loaded) {
            fireEvent(ScriptEvent.TICK);
        }
    }

    public String getName() { return name; }
    public boolean isLoaded() { return loaded; }
    public String getLastError() { return lastError; }
}
