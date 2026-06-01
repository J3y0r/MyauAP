package myau.module.modules;

import com.google.common.base.CaseFormat;
import io.netty.buffer.Unpooled;
import myau.Myau;
import myau.enums.BlinkModules;
import myau.enums.FloatModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.RightClickMouseEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.PacketUtil;
import myau.util.PlayerUtil;
import myau.util.TeamUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.BlockPos;

import java.util.Random;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int lastSlot = -1;
    private int count;
    private int delay;
    private boolean post;
    private final Random random = new Random();
    public final ModeProperty swordMode = new ModeProperty("sword-mode", 1, new String[]{"NONE", "VANILLA", "PREDICTION", "PREDICTION-SEMI", "GRIM"});
    public final IntProperty cancelTick = new IntProperty("cancel-tick", 1, 0, 2, () -> swordMode.getValue() == 3);
    public final IntProperty cancelTick2 = new IntProperty("cancel-tick2", 1, 0, 2, () -> swordMode.getValue() == 3);
    public final IntProperty swapDelay = new IntProperty("SwapDelay", 0, 0, 3, () -> swordMode.getValue() == 2);
    public final BooleanProperty blink = new BooleanProperty("Blink", false, () -> swordMode.getValue() == 2);
    public final BooleanProperty c17 = new BooleanProperty("C17Packet", false, () -> swordMode.getValue() == 2);
    public final PercentProperty swordMotion = new PercentProperty("sword-motion", 100, () -> this.swordMode.getValue() == 1);
    public final BooleanProperty swordSprint = new BooleanProperty("sword-sprint", true, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty killauraonly = new BooleanProperty("killaura-only", false, () -> this.swordMode.getValue() != 0);
    public final ModeProperty foodMode = new ModeProperty("food-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "GRIM"});
    public final PercentProperty foodMotion = new PercentProperty("food-motion", 100, () -> this.foodMode.getValue() == 1);
    public final BooleanProperty foodSprint = new BooleanProperty("food-sprint", true, () -> this.foodMode.getValue() != 0);
    public final ModeProperty bowMode = new ModeProperty("bow-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT", "GRIM"});
    public final PercentProperty bowMotion = new PercentProperty("bow-motion", 100, () -> this.bowMode.getValue() == 1);
    public final BooleanProperty bowSprint = new BooleanProperty("bow-sprint", true, () -> this.bowMode.getValue() != 0);

    public NoSlow() {
        super("NoSlow", false);
    }

    public boolean isSwordActive() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killauraonly.getValue()) {
            if (!killAura.isEnabled()) return false;
            if (killAura.getTarget() == null) return false;
        }
        return this.swordMode.getValue() != 0 && ItemUtil.isHoldingSword();
    }

    public boolean isFoodActive() {
        return this.foodMode.getValue() != 0 && ItemUtil.isEating();
    }

    public boolean isBowActive() {
        return this.bowMode.getValue() != 0 && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.foodMode.getValue() == 2 && ItemUtil.isEating()
                || this.bowMode.getValue() == 2 && ItemUtil.isUsingBow();
    }

    public boolean isGrimMode() {
        return this.swordMode.getValue() == 4 && ItemUtil.isHoldingSword()
                || this.foodMode.getValue() == 3 && ItemUtil.isEating()
                || this.bowMode.getValue() == 3 && ItemUtil.isUsingBow();
    }

    public boolean isAnyActive() {
        if (swordMode.getValue() == 3 && isSwordActive()) {
            KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
            return killAura.isEnabled() && killAura.shouldAutoBlock()
                    && (killAura.blockTick == cancelTick.getValue() || killAura.blockTick == cancelTick2.getValue());
        }
        if (swordMode.getValue() == 2 && isSwordActive()) {
            return delay == 0;
        }
        return mc.thePlayer.isUsingItem() && (this.isSwordActive() || this.isFoodActive() || this.isBowActive());
    }

    public boolean canSprint() {
        return this.isSwordActive() && this.swordSprint.getValue()
                || this.isFoodActive() && this.foodSprint.getValue()
                || this.isBowActive() && this.bowSprint.getValue();
    }

    public int getMotionMultiplier() {
        count++;
        if (ItemUtil.isHoldingSword()) {
            if (swordMode.getValue() == 4) {
                return count % 2 == 0 ? 100 : 20;
            }
            return this.swordMotion.getValue();
        } else if (ItemUtil.isEating()) {
            if (foodMode.getValue() == 3) {
                return count % 2 == 0 ? 100 : 20;
            }
            return this.foodMotion.getValue();
        } else if (ItemUtil.isUsingBow()) {
            if (bowMode.getValue() == 3) {
                return count % 2 == 0 ? 100 : 20;
            }
            return this.bowMotion.getValue();
        }
        return 100;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.PRE) return;

        if (ItemUtil.isHoldingSword() && mc.thePlayer.isUsingItem() && isSwordActive()) {
            if (swordMode.getValue() == 2) {
                delay--;
                if (delay < 0) {
                    int randomSlot = random.nextInt(9);
                    while (randomSlot == mc.thePlayer.inventory.currentItem) {
                        randomSlot = random.nextInt(9);
                    }
                    if (blink.getValue()) {
                        Myau.blinkManager.setBlinkState(true, BlinkModules.NO_SLOW);
                    }
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                    if (c17.getValue()) {
                        PacketUtil.sendPacket(new C17PacketCustomPayload("woshijiejue", new PacketBuffer(Unpooled.buffer())));
                    }
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                    post = true;
                    delay = swapDelay.getValue();
                }
            }
        } else {
            if (post) {
                if (blink.getValue()) {
                    int randomSlot = random.nextInt(9);
                    while (randomSlot == mc.thePlayer.inventory.currentItem) {
                        randomSlot = random.nextInt(9);
                    }
                    Myau.blinkManager.setBlinkState(false, BlinkModules.NO_SLOW);
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                    if (c17.getValue()) {
                        PacketUtil.sendPacket(new C17PacketCustomPayload("woshijiejue", new PacketBuffer(Unpooled.buffer())));
                    }
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                }
                post = false;
            }
        }
    }

    @EventTarget
    public void onPostUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.POST) return;

        if (!ItemUtil.isHoldingSword() || !mc.thePlayer.isUsingItem()) return;
        if (!isSwordActive()) return;
        if (swordMode.getValue() != 2) return;

        if (post) {
            post = false;
            if (blink.getValue()) {
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                Myau.blinkManager.setBlinkState(false, BlinkModules.NO_SLOW);
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.isAnyActive()) {
            float multiplier = (float) this.getMotionMultiplier() / 100.0F;
            mc.thePlayer.movementInput.moveForward *= multiplier;
            mc.thePlayer.movementInput.moveStrafe *= multiplier;
            if (!this.canSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled() && this.isFloatMode()) {
            int item = mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != item && PlayerUtil.isUsingItem()) {
                this.lastSlot = item;
                Myau.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            Myau.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            if (mc.objectMouseOver != null) {
                switch (mc.objectMouseOver.typeOfHit) {
                    case BLOCK:
                        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                        if (BlockUtil.isInteractable(blockPos) && !PlayerUtil.isSneaking()) {
                            return;
                        }
                        break;
                    case ENTITY:
                        Entity entityHit = mc.objectMouseOver.entityHit;
                        if (entityHit instanceof EntityVillager) {
                            return;
                        }
                        if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                            return;
                        }
                }
            }
            if (this.isFloatMode() && !Myau.floatManager.isPredicted() && mc.thePlayer.onGround) {
                event.setCancelled(true);
                mc.thePlayer.motionY = 0.42F;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.swordMode.getModeString())};
    }
}
