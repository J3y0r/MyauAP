package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;

import java.lang.reflect.Field;

/**
 * Watchdog Disabler — ported from GuardAlpha-Refactor.
 * Only the Inventory Move bypass for Hypixel.
 */
public class Disabler extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty inventoryMove = new BooleanProperty("inventory-move", true);
    private boolean shouldBlink;

    private static final Field C0D_WINDOWID;
    static {
        Field f = null;
        try {
            f = C0DPacketCloseWindow.class.getDeclaredField("windowId");
            f.setAccessible(true);
        } catch (NoSuchFieldException ignored) {}
        C0D_WINDOWID = f;
    }

    public Disabler() {
        super("Disabler", false);
    }

    @Override
    public void onEnabled() {
        shouldBlink = false;
    }

    @EventTarget
    public void onWorld(LoadWorldEvent event) {
        shouldBlink = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!inventoryMove.getValue()) return;
        if (mc.currentScreen == null) {
            shouldBlink = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!inventoryMove.getValue()) return;
        if (event.getType() != EventType.SEND) return;

        // -- C0EPacketClickWindow: Detect quick-move/swap/throw --
        if (event.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow click = (C0EPacketClickWindow) event.getPacket();
            int mode = click.getMode();
            boolean allowed = mode == 1 || mode == 2 || mode == 4; // QUICK_MOVE, SWAP, THROW
            int playerSync = mc.thePlayer.openContainer.windowId;

            if (click.getWindowId() == playerSync && allowed) {
                // Player inventory: send fake close to trick Watchdog
                PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(click.getWindowId()));
            } else {
                // Chest/other container: blink outgoing packets until real close
                shouldBlink = true;
            }
        }

        // -- C0DPacketCloseWindow: Player closed inventory → stop blink --
        if (event.getPacket() instanceof C0DPacketCloseWindow) {
            try {
                if (C0D_WINDOWID.getInt(event.getPacket()) == mc.thePlayer.openContainer.windowId) {
                    shouldBlink = false;
                }
            } catch (IllegalAccessException ignored) {}
        }

        // -- Blink: block everything except click / close / keepalive --
        if (shouldBlink) {
            if (!(event.getPacket() instanceof C0EPacketClickWindow
                    || event.getPacket() instanceof C0DPacketCloseWindow
                    || event.getPacket() instanceof C00PacketKeepAlive)) {
                event.setCancelled(true);
            }
        }
    }
}
