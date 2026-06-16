package myau.script;

import myau.module.Module;

public class ScriptModule extends Module {
    private final LuaScript script;

    public ScriptModule(LuaScript script) {
        super(script.getName(), true);
        this.script = script;
    }

    public LuaScript getScript() {
        return script;
    }

    @Override
    public void onEnabled() {
        // Script is already loaded; toggle just controls event dispatch
    }

    @Override
    public void onDisabled() {
        // Script stays loaded; events are suppressed by fireEvent check
    }

    @Override
    public String[] getSuffix() {
        return script.getLastError() != null ? new String[]{"\u00a7cerror"} : new String[0];
    }
}
