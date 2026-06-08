package myau.module.modules;

import myau.module.Module;
import net.minecraft.client.Minecraft;

public class ClientInfo extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private myau.ui.clientinfo.ClientInfo clientInfo;

    public ClientInfo() {
        super("ClientInfo", false, false);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (clientInfo == null) {
            clientInfo = new myau.ui.clientinfo.ClientInfo();
        }
        mc.displayGuiScreen(clientInfo);
    }
}
