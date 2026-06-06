package myau.module.modules;

import com.google.common.base.CaseFormat;
import myau.Myau;
import myau.enums.DelayModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.management.RotationState;
import myau.mixin.IAccessorEntity;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean hasReceivedVelocity = false;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "PREDICTION", "REDUCE", "JUMP", "DELAY", "REVERSE", "LEGIT_TEST"});
    public final IntProperty delayTicks = new IntProperty("delay-ticks", 3, 1, 20, () -> this.mode.getValue() == 4);
    public final PercentProperty delayChance = new PercentProperty("delay-chance", 100, () -> this.mode.getValue() == 4);
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debugLog = new BooleanProperty("debug-log", false);
    public final BooleanProperty reduceWhenCanAttack = new BooleanProperty("reduce-when-can-attack", true, () -> this.mode.getValue() == 1 || this.mode.getValue() == 2);
    public final BooleanProperty reduce = new BooleanProperty("reduce", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty smartAttack = new BooleanProperty("smart-attack", false, () -> (this.mode.getValue() == 1 && this.reduce.getValue()) || this.mode.getValue() == 2);
    public final IntProperty attackTimes = new IntProperty("attack-times", 1, 1, 5, () -> (this.mode.getValue() == 1 && this.reduce.getValue() || this.mode.getValue() == 2) && !this.smartAttack.getValue());
    public final BooleanProperty jump = new BooleanProperty("jump", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty delay = new BooleanProperty("delay", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty airBuffer = new BooleanProperty("air-buffer", true, () -> this.mode.getValue() == 1 && this.delay.getValue());
    public final IntProperty predictionDelayTicks = new IntProperty("prediction-delay-ticks", 1, 1, 5, () -> this.mode.getValue() == 1 && this.delay.getValue() && !this.airBuffer.getValue());
    public final BooleanProperty groundDelay = new BooleanProperty("ground-delay", false, () -> this.mode.getValue() == 1 && this.delay.getValue() && !this.airBuffer.getValue());
    public final BooleanProperty rotate = new BooleanProperty("rotate", false, () -> this.mode.getValue() == 1);
    public final IntProperty rotateTick = new IntProperty("rotate-tick", 3, 1, 12, () -> this.mode.getValue() == 1 && this.rotate.getValue());
    public final BooleanProperty autoMove = new BooleanProperty("auto-move", false, () -> this.mode.getValue() == 1 && this.rotate.getValue());
    private int chanceCounter = 0;
    private int delayChanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean jumpFlag = false;
    private boolean reverseFlag = false;
    private boolean delayActive = false;
    private boolean delayFlag = false;
    private boolean handleReset = false;
    private boolean shouldJump = false;
    private int jumpCooldown = 0;
    private int rotateTickCounter = 0;
    private int ticksSinceVelocity = -1;
    private int reduceTick = -1;
    private double rawKnockbackMagnitude = 0.0;
    private double knockbackX = 0.0;
    private double knockbackZ = 0.0;
    private float[] targetRotation = null;

    public Velocity() {
        super("Velocity", false);
    }

    private int calculateSmartAttackTimes() {
        int result = (int) Math.round(6.43153527E-4 * this.rawKnockbackMagnitude + 2.9419087136);
        return Math.max(1, Math.min(result, 10));
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private boolean canDelay() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        return mc.thePlayer.onGround && (!killAura.isEnabled() || !killAura.shouldAutoBlock());
    }

    private void handleJumpReset() {
        if (!this.isEnabled() || !this.jump.getValue() || this.mode.getValue() != 1 || mc.thePlayer == null) {
            return;
        }
        if (this.ticksSinceVelocity >= 0) {
            this.handleReset = true;
            if (this.ticksSinceVelocity <= 2 && mc.thePlayer.onGround) {
                mc.thePlayer.movementInput.jump = true;
            }
            if (this.ticksSinceVelocity >= 4 && this.ticksSinceVelocity <= 9) {
                mc.thePlayer.movementInput.jump = false;
                this.handleReset = false;
            }
        }
    }

    private void dbg(String message) {
        if (this.debugLog.getValue()) {
            ChatUtil.sendFormatted(String.format("%s%s&r", Myau.clientName, message));
        }
    }

    private boolean isPredictionTarget(Entity entity) {
        if (!(entity instanceof EntityPlayer) || entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) entity;
        return !TeamUtil.isFriend(player) && !TeamUtil.isSameTeam(player) && !TeamUtil.isBot(player) && player.deathTime <= 0;
    }

    private void resetPrediction() {
        this.delayFlag = false;
        this.handleReset = false;
        this.rotateTickCounter = 0;
        this.ticksSinceVelocity = -1;
        this.reduceTick = -1;
        this.knockbackX = 0.0;
        this.knockbackZ = 0.0;
        this.rawKnockbackMagnitude = 0.0;
        this.targetRotation = null;
        hasReceivedVelocity = false;
        if (Myau.delayManager.getDelayModule() == DelayModules.VELOCITY) {
            Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
        }
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
            return;
        }
        if (!this.allowNext || !this.fakeCheck.getValue()) {
            this.allowNext = true;
            if (this.pendingExplosion) {
                this.pendingExplosion = false;
                if (this.explosionHorizontal.getValue() > 0) {
                    event.setX(event.getX() * (double) this.explosionHorizontal.getValue() / 100.0);
                    event.setZ(event.getZ() * (double) this.explosionHorizontal.getValue() / 100.0);
                } else {
                    event.setX(mc.thePlayer.motionX);
                    event.setZ(mc.thePlayer.motionZ);
                }
                if (this.explosionVertical.getValue() > 0) {
                    event.setY(event.getY() * (double) this.explosionVertical.getValue() / 100.0);
                } else {
                    event.setY(mc.thePlayer.motionY);
                }
                return;
            }
            if (this.mode.getValue() == 1 && this.rotate.getValue() && event.getY() > 0.0) {
                this.knockbackX = event.getX();
                this.knockbackZ = event.getZ();
                if (Math.abs(this.knockbackX) > 0.01 || Math.abs(this.knockbackZ) > 0.01) {
                    this.rotateTickCounter = 1;
                }
            }
            if (this.mode.getValue() == 1 && !this.delay.getValue()) {
                this.ticksSinceVelocity = 0;
            }
            this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
            if (this.chanceCounter >= 100) {
                this.jumpFlag = (this.mode.getValue() == 3 || this.mode.getValue() == 4) && event.getY() > 0.0;
                this.delayActive = this.mode.getValue() == 5;
                if (this.mode.getValue() == 0 || this.mode.getValue() >= 3) {
                    if (this.horizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) this.horizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) this.horizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (this.vertical.getValue() > 0) {
                        event.setY(event.getY() * (double) this.vertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.ticksSinceVelocity >= 0) {
            this.ticksSinceVelocity++;
            if (this.ticksSinceVelocity >= 10) {
                this.ticksSinceVelocity = -1;
            }
        }
        if (this.jump.getValue() && this.mode.getValue() == 1) {
            this.handleJumpReset();
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.isEnabled() && this.mode.getValue() == 1 && this.rotateTickCounter > 0 && this.rotateTickCounter <= this.rotateTick.getValue()) {
                if (this.rotateTickCounter == 1) {
                    this.targetRotation = RotationUtil.getRotationsTo(-this.knockbackX, 0.0, -this.knockbackZ, event.getYaw(), event.getPitch());
                }
                if (this.targetRotation != null) {
                    event.setRotation(this.targetRotation[0], this.targetRotation[1], 2);
                    event.setPervRotation(this.targetRotation[0], 2);
                }
            }
            if ((this.mode.getValue() == 1 && this.reduce.getValue() || this.mode.getValue() == 2) && hasReceivedVelocity) {
                int effectiveAttackTimes = this.smartAttack.getValue() ? this.calculateSmartAttackTimes() : this.attackTimes.getValue();
                if (this.reduceTick >= effectiveAttackTimes) {
                    this.reduceTick = 0;
                    hasReceivedVelocity = false;
                }
                MovingObjectPosition targetA = mc.objectMouseOver;
                if (targetA != null
                        && targetA.entityHit instanceof EntityPlayer
                        && targetA.entityHit != mc.thePlayer
                        && mc.thePlayer.isSprinting()) {
                    KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
                    if (killAura.getTarget() != null) {
                        if (!this.reduceWhenCanAttack.getValue()
                                || killAura.autoBlock.getValue() == 3 && killAura.blockTick == 0
                                || killAura.autoBlock.getValue() == 10 && killAura.blockTick == killAura.attackTick.getValue()
                                || killAura.autoBlock.getValue() != 10 && killAura.autoBlock.getValue() != 3
                                || killAura.autoBlock.getValue() == 9 && killAura.blockTick == 0) {
                            EventManager.call(new AttackEvent(killAura.getTarget()));
                            PacketUtil.sendPacket(new C0APacketAnimation());
                            PacketUtil.sendPacket(new C02PacketUseEntity(killAura.getTarget(), C02PacketUseEntity.Action.ATTACK));
                            mc.thePlayer.motionX *= 0.6;
                            mc.thePlayer.motionZ *= 0.6;
                            mc.thePlayer.setSprinting(false);
                        }
                    } else if (this.isPredictionTarget(targetA.entityHit)) {
                        EventManager.call(new AttackEvent(targetA.entityHit));
                        PacketUtil.sendPacket(new C0APacketAnimation());
                        PacketUtil.sendPacket(new C02PacketUseEntity(targetA.entityHit, C02PacketUseEntity.Action.ATTACK));
                        mc.thePlayer.motionX *= 0.6;
                        mc.thePlayer.motionZ *= 0.6;
                        mc.thePlayer.setSprinting(false);
                    }
                }
                this.reduceTick++;
            }
            return;
        }

        if (this.reverseFlag && (this.canDelay() || this.isInLiquidOrWeb() || Myau.delayManager.getDelay() >= (long) this.delayTicks.getValue())) {
            Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
            this.reverseFlag = false;
        }
        if (this.delayActive) {
            MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
            this.delayActive = false;
        }
        if (this.mode.getValue() == 1 && this.rotateTickCounter > 0 && this.rotateTickCounter <= this.rotateTick.getValue()) {
            this.rotateTickCounter++;
            if (this.rotateTickCounter > this.rotateTick.getValue()) {
                this.rotateTickCounter = 0;
                this.targetRotation = null;
                this.knockbackX = 0.0;
                this.knockbackZ = 0.0;
            }
        }
        if (this.mode.getValue() == 1 && this.delayFlag) {
            boolean shouldRelease = (this.delay.getValue() && (this.isInLiquidOrWeb() || Myau.delayManager.getDelay() >= (long) this.predictionDelayTicks.getValue() && !this.airBuffer.getValue()))
                    || (mc.thePlayer.onGround && !this.groundDelay.getValue() && !this.airBuffer.getValue())
                    || (this.airBuffer.getValue() && mc.thePlayer.onGround && this.delayFlag);
            if (shouldRelease) {
                this.ticksSinceVelocity = 0;
                hasReceivedVelocity = true;
                this.dbg("Prediction delay released");
                Myau.delayManager.setDelayState(false, DelayModules.VELOCITY);
                this.delayFlag = false;
            }
        }
        if (this.mode.getValue() == 6) {
            int hurtTime = mc.thePlayer.hurtTime;
            if (hurtTime >= 8) {
                if (this.jumpCooldown <= 0) {
                    this.shouldJump = true;
                    this.jumpCooldown = 2;
                }
            } else if (hurtTime <= 1) {
                this.shouldJump = false;
                this.jumpCooldown = 0;
            }
            if (this.shouldJump && mc.thePlayer.onGround && this.jumpCooldown <= 0) {
                mc.thePlayer.jump();
                this.shouldJump = false;
            }
            if (this.jumpCooldown > 0) {
                this.jumpCooldown--;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.handleReset) {
            mc.thePlayer.movementInput.moveForward = 1.0F;
        }
        if (this.mode.getValue() == 1 && this.rotateTickCounter > 0 && this.rotateTickCounter <= this.rotateTick.getValue()) {
            if (this.autoMove.getValue()) {
                mc.thePlayer.movementInput.moveForward = 1.0F;
            }
            if (this.targetRotation != null && RotationState.isActived() && RotationState.getPriority() == 2.0F && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }
        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                double motionX = (double) packet.getMotionX() / 8000.0;
                double motionZ = (double) packet.getMotionZ() / 8000.0;
                this.rawKnockbackMagnitude = Math.hypot(packet.getMotionX(), packet.getMotionZ());
                if (this.mode.getValue() == 1) {
                    if (!this.delay.getValue()) {
                        hasReceivedVelocity = true;
                    }
                    LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);
                    boolean canPredictDelay = !this.delayFlag
                            && !this.isInLiquidOrWeb()
                            && !this.pendingExplosion
                            && (!this.allowNext || !this.fakeCheck.getValue())
                            && (!longJump.isEnabled() || !longJump.canStartJump());
                    boolean shouldBuffer = this.airBuffer.getValue() && !mc.thePlayer.onGround
                            || this.delay.getValue() && !mc.thePlayer.onGround
                            || this.delay.getValue() && this.groundDelay.getValue() && !this.airBuffer.getValue();
                    if (canPredictDelay && shouldBuffer) {
                        Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
                        this.dbg("Prediction delay buffered velocity");
                        Myau.delayManager.delayedPacket.offer(packet);
                        event.setCancelled(true);
                        this.delayFlag = true;
                        return;
                    }
                }
                LongJump longJump = (LongJump) Myau.moduleManager.modules.get(LongJump.class);
                if (this.mode.getValue() == 4
                        && !this.reverseFlag
                        && !this.canDelay()
                        && !this.isInLiquidOrWeb()
                        && !this.pendingExplosion
                        && (!this.allowNext || !this.fakeCheck.getValue())
                        && (!longJump.isEnabled() || !longJump.canStartJump())) {
                    this.delayChanceCounter = this.delayChanceCounter % 100 + this.delayChance.getValue();
                    if (this.delayChanceCounter >= 100) {
                        Myau.delayManager.setDelayState(true, DelayModules.VELOCITY);
                        Myau.delayManager.delayedPacket.offer(packet);
                        event.setCancelled(true);
                        this.reverseFlag = true;
                        return;
                    }
                }
                if (this.debugLog.getValue()) {
                    StringBuilder sb = new StringBuilder(
                            String.format(
                                    "%sVelocity (&otick: %d, x: %.2f, y: %.2f, z: %.2f&r)",
                                    Myau.clientName,
                                    mc.thePlayer.ticksExisted,
                                    motionX,
                                    (double) packet.getMotionY() / 8000.0,
                                    motionZ
                            )
                    );
                    if (this.smartAttack.getValue() && ((this.mode.getValue() == 1 && this.reduce.getValue()) || this.mode.getValue() == 2)) {
                        sb.append(String.format(" &bsmart-attack: %d&r", this.calculateSmartAttackTimes()));
                    }
                    sb.append("&r");
                    ChatUtil.sendFormatted(sb.toString());
                }
            }
            return;
        }
        if (event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
            Entity entity = packet.getEntity(mc.theWorld);
            if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                this.allowNext = false;
            }
            return;
        }
        if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                this.pendingExplosion = true;
                if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                    event.setCancelled(true);
                }
                if (this.debugLog.getValue()) {
                    ChatUtil.sendFormatted(
                            String.format(
                                    "%sExplosion (&otick: %d, x: %.2f, y: %.2f, z: %.2f&r)&r",
                                    Myau.clientName,
                                    mc.thePlayer.ticksExisted,
                                    mc.thePlayer.motionX + (double) packet.func_149149_c(),
                                    mc.thePlayer.motionY + (double) packet.func_149144_d(),
                                    mc.thePlayer.motionZ + (double) packet.func_149147_e()
                            )
                    );
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.onDisabled();
    }

    @Override
    public void onDisabled() {
        this.pendingExplosion = false;
        this.allowNext = true;
        this.shouldJump = false;
        this.jumpCooldown = 0;
        this.jumpFlag = false;
        this.reverseFlag = false;
        this.delayActive = false;
        this.resetPrediction();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
