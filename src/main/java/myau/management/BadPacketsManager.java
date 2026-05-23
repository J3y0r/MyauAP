package myau.management;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

public final class BadPacketsManager {
    private boolean slot;
    private boolean attack;
    private boolean swing;
    private boolean block;
    private boolean unblock;
    private boolean inventory;

    public boolean bad() {
        return this.bad(true, true, true, true, true, true);
    }

    public boolean bad(boolean slot, boolean attack, boolean swing, boolean block, boolean unblock, boolean inventory) {
        return this.slot && slot
                || this.attack && attack
                || this.swing && swing
                || this.block && block
                || this.unblock && unblock
                || this.inventory && inventory;
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof C09PacketHeldItemChange) {
            this.slot = true;
        } else if (packet instanceof C0APacketAnimation) {
            this.swing = true;
        } else if (packet instanceof C02PacketUseEntity) {
            this.attack = true;
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            this.block = true;
        } else if (packet instanceof C07PacketPlayerDigging) {
            this.unblock = true;
        } else if (packet instanceof C0EPacketClickWindow
                || packet instanceof C0DPacketCloseWindow
                || packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
            this.inventory = true;
        } else if (packet instanceof C03PacketPlayer) {
            this.reset();
        }
    }

    public void reset() {
        this.slot = false;
        this.attack = false;
        this.swing = false;
        this.block = false;
        this.unblock = false;
        this.inventory = false;
    }
}
