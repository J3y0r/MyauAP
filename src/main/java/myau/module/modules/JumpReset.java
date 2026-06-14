package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KnockbackEvent;
import myau.events.LivingUpdateEvent;
import myau.events.PacketEvent;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.util.MoveUtil;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.potion.Potion;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final PercentProperty chance = new PercentProperty("chance", 100, 20, 100, null);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    private boolean jumpFlag = false;
    private boolean allowNext = true;

    public JumpReset() {
        super("JumpReset", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{chance.getValue() + "%"};
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.allowNext = true;
            return;
        }

        if (!this.allowNext || !this.fakeCheck.getValue()) {
            this.allowNext = true;
            if (mc.thePlayer.hurtTime >= 7) {
                int random = RandomUtil.nextInt(1, 101);
                int threshold = chance.getValue();
                if (random <= threshold) {
                    this.jumpFlag = true;
                }
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb() && mc.thePlayer.isSprinting()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (isEnabled() && event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S19PacketEntityStatus) {
                S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                Entity entity = packet.getEntity(mc.theWorld);
                if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                    this.allowNext = false;
                }
            }
        }
    }

    @Override
    public void onDisabled() {
        this.allowNext = true;
    }
}