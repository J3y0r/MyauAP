package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.*;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty latencyMin = new IntProperty("latency-min", 50, 10, 500);
    public final IntProperty latencyMax = new IntProperty("latency-max", 100, 50, 1000);
    public final FloatProperty distanceMin = new FloatProperty("distance-min", 0.0F, 0.0F, 6.0F);
    public final FloatProperty distanceMax = new FloatProperty("distance-max", 4.0F, 0.5F, 6.0F);
    public final ModeProperty espMode = new ModeProperty("esp-mode", 1, new String[]{"NONE", "HITBOX", "HUD"});
    public final ModeProperty releaseStyle = new ModeProperty("style", 0, new String[]{"PULSE", "SMOOTH"});
    public final BooleanProperty smart = new BooleanProperty("smart", true);

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final List<Packet<?>> skipPackets = new ArrayList<>();
    private final TimerUtil cycleTimer = new TimerUtil();
    private final Random random = new Random();

    private Vec3 realPosition;
    private EntityPlayer target;
    private int currentLatency;

    public BackTrack() {
        super("BackTrack", false);
    }

    @Override
    public void onEnabled() {
        resetState(false);
    }

    @Override
    public void onDisabled() {
        releaseAll();
        resetState(false);
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        releaseAll();
        resetState(false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }

        if (target == null || realPosition == null) {
            return;
        }

        if (target.isDead || mc.theWorld == null || mc.theWorld.getEntityByID(target.getEntityId()) == null) {
            releaseAll();
            resetState(false);
            return;
        }

        if (smart.getValue() && target.hurtTime <= 2) {
            double currentDistance = mc.thePlayer.getDistanceToEntity(target);
            double backtrackDistance = mc.thePlayer.getDistance(realPosition.xCoord, realPosition.yCoord, realPosition.zCoord);
            if (currentDistance + 0.5D < backtrackDistance) {
                releaseAll();
                resetState(false);
                return;
            }
        }

        double distance = mc.thePlayer.getDistanceToEntity(target);
        if (distance < distanceMin.getValue() || distance > distanceMax.getValue()) {
            releaseAll();
            resetState(false);
            return;
        }

        if (releaseStyle.getValue() == 0) {
            releasePulse();
        } else {
            releaseSmooth();
        }

        if (packetQueue.isEmpty() && target != null) {
            realPosition = target.getPositionVector();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE) {
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.ticksExisted < 20) {
            packetQueue.clear();
            return;
        }

        Packet<?> packet = event.getPacket();
        if (skipPackets.remove(packet)) {
            return;
        }

        if (target == null || realPosition == null) {
            releaseAll();
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect) {
            releaseAll();
            resetState(false);
            return;
        }

        if (packet instanceof S13PacketDestroyEntities) {
            S13PacketDestroyEntities destroy = (S13PacketDestroyEntities) packet;
            for (int id : destroy.getEntityIDs()) {
                if (id == target.getEntityId()) {
                    releaseAll();
                    resetState(false);
                    return;
                }
            }
        }

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity entityPacket = (S14PacketEntity) packet;
            Entity entity = entityPacket.getEntity(mc.theWorld);
            if (entity == null || entity.getEntityId() != target.getEntityId() || packetQueue.size() >= 50) {
                return;
            }

            realPosition = realPosition.addVector(
                    entityPacket.func_149062_c() / 32.0D,
                    entityPacket.func_149061_d() / 32.0D,
                    entityPacket.func_149064_e() / 32.0D
            );
            queuePacket(packet, event);
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            if (teleport.getEntityId() != target.getEntityId() || packetQueue.size() >= 50) {
                return;
            }

            realPosition = new Vec3(teleport.getX() / 32.0D, teleport.getY() / 32.0D, teleport.getZ() / 32.0D);
            queuePacket(packet, event);
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) {
            return;
        }

        Entity entity = event.getTarget();
        if (!(entity instanceof EntityPlayer) || mc.thePlayer == null) {
            return;
        }

        EntityPlayer attacked = (EntityPlayer) entity;
        if (target == null || attacked != target) {
            realPosition = attacked.getPositionVector();
        }
        target = attacked;

        double distance = mc.thePlayer.getDistanceToEntity(target);
        if (distance < distanceMin.getValue() || distance > distanceMax.getValue()) {
            return;
        }

        int min = latencyMin.getValue();
        int max = Math.max(min, latencyMax.getValue());
        currentLatency = min + random.nextInt(Math.max(1, max - min + 1));
        cycleTimer.reset();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isEnabled() || espMode.getValue() == 0 || target == null || realPosition == null || target.isDead || currentLatency == 0) {
            return;
        }

        double x = realPosition.xCoord - mc.getRenderManager().viewerPosX;
        double y = realPosition.yCoord - mc.getRenderManager().viewerPosY;
        double z = realPosition.zCoord - mc.getRenderManager().viewerPosZ;

        AxisAlignedBB box = new AxisAlignedBB(
                x - target.width / 2.0D,
                y,
                z - target.width / 2.0D,
                x + target.width / 2.0D,
                y + target.height,
                z + target.width / 2.0D
        );

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);

        Color color = getEspColor();
        RenderGlobal.drawOutlinedBoundingBox(box, color.getRed(), color.getGreen(), color.getBlue(), 153);

        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_BLEND);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private void queuePacket(Packet<?> packet, PacketEvent event) {
        packetQueue.add(new TimedPacket(packet, currentLatency));
        event.setCancelled(true);
    }

    private void releasePulse() {
        if (!cycleTimer.hasTimeElapsed(currentLatency)) {
            return;
        }

        releaseAll();
        cycleTimer.reset();
    }

    private void releaseSmooth() {
        while (!packetQueue.isEmpty()) {
            TimedPacket timedPacket = packetQueue.peek();
            if (timedPacket == null || !timedPacket.timer.hasTimeElapsed(timedPacket.latency)) {
                break;
            }

            packetQueue.poll();
            receivePacket(timedPacket.packet);
        }
    }

    private void releaseAll() {
        while (!packetQueue.isEmpty()) {
            TimedPacket timedPacket = packetQueue.poll();
            if (timedPacket != null) {
                receivePacket(timedPacket.packet);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void receivePacket(Packet<?> packet) {
        if (packet == null || mc.getNetHandler() == null) {
            return;
        }

        try {
            skipPackets.add(packet);
            ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }

    private void resetState(boolean keepSkippedPackets) {
        packetQueue.clear();
        if (!keepSkippedPackets) {
            skipPackets.clear();
        }
        realPosition = null;
        target = null;
        currentLatency = 0;
        cycleTimer.reset();
    }

    private Color getEspColor() {
        if (espMode.getValue() == 2) {
            return ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
        }

        return Color.RED;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%d-%dms", latencyMin.getValue(), latencyMax.getValue())};
    }

    private static class TimedPacket {
        private final Packet<?> packet;
        private final TimerUtil timer;
        private final int latency;

        TimedPacket(Packet<?> packet, int latency) {
            this.packet = packet;
            this.timer = new TimerUtil();
            this.latency = Math.max(latency, 1);
        }
    }
}
