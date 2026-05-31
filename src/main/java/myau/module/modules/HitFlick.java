package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.AttackEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class HitFlick extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private FlickState state = FlickState.IDLE;
    private long sinceLastFlick = 0L;
    private float originalYaw = 0.0F;
    private float flickYaw = 0.0F;
    public final ModeProperty direction = new ModeProperty("direction", 1, new String[]{"LEFT", "RIGHT", "BACK", "CUSTOM"});
    public final FloatProperty customAngle = new FloatProperty("custom-angle", 90.0F, 1.0F, 180.0F, () -> this.direction.getValue() == 3);
    public final IntProperty cooldown = new IntProperty("cooldown", 1, 1, 40);
    public final BooleanProperty blink = new BooleanProperty("blink", false);

    public HitFlick() {
        super("HitFlick", false);
    }

    @Override
    public void onEnabled() {
        this.sinceLastFlick = 0L;
        this.state = FlickState.IDLE;
    }

    @Override
    public void onDisabled() {
        this.state = FlickState.IDLE;
        this.sinceLastFlick = 0L;
        if (this.blink.getValue()) {
            Myau.blinkManager.setBlinkState(false, BlinkModules.HITFLICK);
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()
                || mc.thePlayer == null
                || event.getTarget() == null
                || event.getTarget() == mc.thePlayer
                || this.state != FlickState.IDLE
                || this.sinceLastFlick < (long) this.cooldown.getValue()
                || !(event.getTarget() instanceof EntityLivingBase)) {
            return;
        }

        this.originalYaw = mc.thePlayer.rotationYaw;
        this.flickYaw = this.originalYaw + this.getFlickAngle();
        this.state = FlickState.FLICKING_AWAY;

        if (this.blink.getValue()) {
            Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
            Myau.blinkManager.setBlinkState(true, BlinkModules.HITFLICK);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }

        if (this.state == FlickState.IDLE) {
            this.sinceLastFlick++;
        } else if (this.state == FlickState.FLICKING_AWAY) {
            event.setRotation(this.flickYaw, mc.thePlayer.rotationPitch, 1);
            this.state = FlickState.RESTORING;
        } else if (this.state == FlickState.RESTORING) {
            event.setRotation(this.originalYaw, mc.thePlayer.rotationPitch, 1);
            this.state = FlickState.IDLE;
            this.sinceLastFlick = 0L;
            if (this.blink.getValue()) {
                Myau.blinkManager.setBlinkState(false, BlinkModules.HITFLICK);
            }
        }
    }

    private float getFlickAngle() {
        switch (this.direction.getValue()) {
            case 0:
                return -90.0F;
            case 1:
                return 90.0F;
            case 2:
                return 180.0F;
            case 3:
                return this.customAngle.getValue();
            default:
                return 90.0F;
        }
    }

    private enum FlickState {
        IDLE,
        FLICKING_AWAY,
        RESTORING
    }
}
