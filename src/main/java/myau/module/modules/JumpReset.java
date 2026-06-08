package myau.module.modules;

import myau.event.EventTarget;
import myau.events.KnockbackEvent;
import myau.events.LivingUpdateEvent;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.property.properties.PercentProperty;
import myau.util.MoveUtil;
import myau.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean jumpFlag = false;
    public final PercentProperty chance = new PercentProperty("Chance", 100, 20, 100, null);

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
        if (this.isEnabled()) {
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
}