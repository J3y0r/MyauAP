package myau.script.api;

import myau.Myau;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public class MinecraftAPI {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void inject(Globals globals) {
        LuaTable myau = new LuaTable();

        // myau.chat("message")
        myau.set("chat", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue msg) {
                if (mc.thePlayer != null) {
                    mc.thePlayer.sendChatMessage(msg.tojstring());
                }
                return LuaValue.NIL;
            }
        });

        // myau.log("message")
        myau.set("log", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue msg) {
                ChatUtil.sendFormatted("&7[Script] &r" + msg.tojstring());
                return LuaValue.NIL;
            }
        });

        // myau.toggle("ModuleName") or myau.toggle("ModuleName", true|false)
        myau.set("toggle", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String moduleName = args.arg1().tojstring();
                Module module = findModule(moduleName);
                if (module != null) {
                    if (args.narg() >= 2) {
                        module.setEnabled(args.arg(2).toboolean());
                    } else {
                        module.toggle();
                    }
                    ChatUtil.sendFormatted(String.format("&7[Script] &r%s &7%s", module.getName(), module.isEnabled() ? "&aON" : "&cOFF"));
                } else {
                    ChatUtil.sendFormatted("&c[Script] Module not found: " + moduleName);
                }
                return LuaValue.NIL;
            }
        });

        // myau.isEnabled("ModuleName") -> boolean
        myau.set("isEnabled", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue name) {
                Module module = findModule(name.tojstring());
                return module != null ? LuaValue.valueOf(module.isEnabled()) : LuaValue.FALSE;
            }
        });

        // myau.player() -> table {x, y, z, health, name, ...}
        myau.set("player", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                EntityPlayerSP p = mc.thePlayer;
                if (p == null) return LuaValue.NIL;
                LuaTable t = new LuaTable();
                t.set("name", LuaValue.valueOf(p.getName()));
                t.set("x", LuaValue.valueOf(p.posX));
                t.set("y", LuaValue.valueOf(p.posY));
                t.set("z", LuaValue.valueOf(p.posZ));
                t.set("health", LuaValue.valueOf(p.getHealth()));
                t.set("onGround", LuaValue.valueOf(p.onGround));
                t.set("isInWater", LuaValue.valueOf(p.isInWater()));
                t.set("isInLava", LuaValue.valueOf(p.isInLava()));
                t.set("food", LuaValue.valueOf(p.getFoodStats().getFoodLevel()));
                t.set("dimension", LuaValue.valueOf(p.dimension));
                return t;
            }
        });

        // myau.server() -> table {ip}
        myau.set("server", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable t = new LuaTable();
                if (mc.getCurrentServerData() != null) {
                    t.set("ip", LuaValue.valueOf(mc.getCurrentServerData().serverIP));
                } else {
                    t.set("ip", LuaValue.valueOf("singleplayer"));
                }
                return t;
            }
        });

        // myau.getSetting("ModuleName", "settingName") -> value
        myau.set("getSetting", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue moduleName, LuaValue settingName) {
                Module module = findModule(moduleName.tojstring());
                if (module == null) return LuaValue.NIL;
                try {
                    java.lang.reflect.Field field = module.getClass().getField(settingName.tojstring());
                    Object value = field.get(module);
                    if (value instanceof Number) {
                        return LuaValue.valueOf(((Number) value).doubleValue());
                    } else if (value instanceof Boolean) {
                        return LuaValue.valueOf((Boolean) value);
                    } else if (value instanceof String) {
                        return LuaValue.valueOf((String) value);
                    } else {
                        return LuaValue.valueOf(String.valueOf(value));
                    }
                } catch (Exception e) {
                    return LuaValue.NIL;
                }
            }
        });

        // myau.setSetting("ModuleName", "settingName", value)
        myau.set("setSetting", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() < 3) return LuaValue.FALSE;
                String moduleName = args.arg1().tojstring();
                String settingName = args.arg(2).tojstring();
                LuaValue val = args.arg(3);
                Module module = findModule(moduleName);
                if (module == null) return LuaValue.FALSE;
                try {
                    java.lang.reflect.Field field = module.getClass().getField(settingName);
                    Object current = field.get(module);
                    if (current instanceof FloatProperty) {
                        ((FloatProperty) current).setValue((float) val.todouble());
                    } else if (current instanceof IntProperty) {
                        ((IntProperty) current).setValue(val.toint());
                    } else if (current instanceof BooleanProperty) {
                        ((BooleanProperty) current).setValue(val.toboolean());
                    } else if (current instanceof ModeProperty) {
                        ((ModeProperty) current).setValue(val.isstring() ? val.tojstring() : val.toint());
                    }
                    return LuaValue.TRUE;
                } catch (Exception e) {
                    return LuaValue.FALSE;
                }
            }
        });

        globals.set("myau", myau);
    }

    private static Module findModule(String name) {
        Module module = Myau.moduleManager.modules.get(name);
        if (module == null) {
            for (Module m : Myau.moduleManager.modules.values()) {
                if (m.getName().equalsIgnoreCase(name)) {
                    return m;
                }
            }
        }
        return module;
    }
}
