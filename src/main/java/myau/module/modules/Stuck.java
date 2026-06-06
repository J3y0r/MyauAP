package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.events.LivingUpdateEvent;
import myau.events.MoveInputEvent;
import myau.events.StrafeEvent;
import myau.events.UpdateEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;

    public final FloatProperty timer = new FloatProperty("Timer", 1.0F, 0.0F, 1.0F);

    public Stuck() {
        super("Stuck", false, false);
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            savedMotionX = mc.thePlayer.motionX;
            savedMotionY = mc.thePlayer.motionY;
            savedMotionZ = mc.thePlayer.motionZ;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled()) {
            Myau.blinkManager.setBlinkState(true, BlinkModules.STUCK);
            KeyBinding.unPressAllKeys();
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;
            ((IAccessorMinecraft)mc).getTimer().timerSpeed = timer.getValue();
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            mc.thePlayer.movementInput.moveForward = 0.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
        }
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.STUCK);
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionZ = savedMotionZ;
            mc.thePlayer.motionY = savedMotionY;
            ((IAccessorMinecraft)mc).getTimer().timerSpeed = 1.0F;
        }
    }
}
