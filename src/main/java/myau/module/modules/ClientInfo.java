package myau.module.modules;

import myau.module.Module;
import myau.ui.clickgui.ClickGui;
import myau.ui.clientinfo.Clientinfo;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class ClientInfo extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private Clientinfo clientinfo;

    public ClientInfo() {
        super("ClientInfo", false);
        setKey(Keyboard.KEY_NONE);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (clientinfo == null) {
            clientinfo = new Clientinfo();
        }
        mc.displayGuiScreen(clientinfo);
    }
}
