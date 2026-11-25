package keystrokesmod.module.impl.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.DifferentThreadException;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket.Angles;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket.Position;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket.PositionAndAngles;
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerHealthS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.opengl.GL11;

public class Backtrack extends Module {
    private final SliderSetting delay;
    private final SliderSetting distanceMin;
    private final SliderSetting distanceMax;
    private final SliderSetting cooldown;
    private final SliderSetting fov;
    private final ButtonSetting disableAdventure;
    private final ButtonSetting disableWhileChasing;
    private final ButtonSetting flushOnDamage;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting showServerPosition;
    private final ButtonSetting onlyHittable;
    private final ButtonSetting onlyMovement;
    private final ButtonSetting weaponOnly;
    private final ColorSetting positionColor;
    private final TreeMap<Integer, List<Packet<?>>> held = new TreeMap<>();
    private final Object packetLock = new Object();
    private int tick;
    private volatile int delayTicks;
    private volatile boolean holding;
    private volatile boolean ready;
    private volatile LivingEntity target;
    private volatile Vec3d serverPos;
    private volatile RemoteClientPlayerEntity ghost;
    private volatile Packet<?> processing;
    private volatile long lastFlush;
    private double minRange;
    private double maxRange;
    private double maxRangeSq;
    private double scanRangeSq;

    public Backtrack() {
        super("Backtrack", Module.category.network);
        this.liteModule = true;
        this.registerSetting(new DescriptionSetting("Attack previous positions."));
        this.registerSetting(this.delay = new SliderSetting("Delay", "ms", 150.0, 50.0, 1500.0, 50.0));
        this.registerSetting(this.distanceMin = new SliderSetting("Distance (min)", 2.7, 0.0, 8.0, 0.1));
        this.registerSetting(this.distanceMax = new SliderSetting("Distance (max)", 5.0, 2.0, 8.0, 0.1));
        this.registerSetting(this.cooldown = new SliderSetting("Cooldown", "s", "&cDisabled", 0.0, 0.0, 8.0, 0.1));
        this.registerSetting(this.fov = new SliderSetting("FOV", 360.0, 20.0, 360.0, 5.0));
        this.registerSetting(this.disableAdventure = new ButtonSetting("Disable adventure", true));
        this.registerSetting(this.disableWhileChasing = new ButtonSetting("Disable while chasing", false));
        this.registerSetting(this.flushOnDamage = new ButtonSetting("Flush on damage", true));
        this.registerSetting(this.ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(this.showServerPosition = new ButtonSetting("Show server position", false));
        this.registerSetting(this.onlyHittable = new ButtonSetting("Only hittable", false));
        this.registerSetting(this.onlyMovement = new ButtonSetting("Only movement", false));
        this.registerSetting(this.weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(this.positionColor = new ColorSetting("Position color", 0, 0, 250, 100));
        this.guiUpdate();
    }

    private boolean canStart() {
        return (ModuleManager.freeze == null || !ModuleManager.freeze.isFrozen())
                        && (ModuleManager.knockbackDelay == null || !ModuleManager.knockbackDelay.isDelayingPackets())
                ? this.cooldown.getInput() == 0.0
                        || Math.abs(System.currentTimeMillis() - this.lastFlush)
                                > this.cooldown.getInput() * 1000.0 - 20.0
                : false;
    }

    private void start() {
        this.delayTicks = Math.max(1, (int) this.delay.getInput() / 50);
        this.updateServerPos();
        this.holding = true;
        if (this.showServerPosition.isToggled()) {
            this.makeGhost(this.target);
        }
    }

    @Override
    public void onEnable() {
        this.guiUpdate();
    }

    @Override
    public void onDisable() {
        this.flush();
    }

    @Override
    public String getInfo() {
        return (int) this.delay.getInput() + "ms";
    }

    private boolean passesThrough(Packet<?> packet) {
        return packet instanceof ChatMessageS2CPacket
                || packet instanceof PlayerHealthS2CPacket
                || packet instanceof Angles
                || packet instanceof EntityEventS2CPacket
                || packet instanceof BlocksUpdateS2CPacket
                || packet instanceof BlockUpdateS2CPacket
                || packet instanceof SoundEventS2CPacket
                || packet instanceof ParticleS2CPacket
                || packet instanceof ScoreboardObjectiveS2CPacket
                || packet instanceof ScoreboardScoreS2CPacket
                || packet instanceof ScoreboardDisplayS2CPacket
                || packet instanceof TeamS2CPacket;
    }

    public void flush() {
        List<Packet<?>> packets = new ArrayList<>();
        synchronized (this.packetLock) {
            for (List<Packet<?>> list : this.held.values()) {
                packets.addAll(list);
            }

            this.held.clear();
            this.reset();
        }

        this.process(packets);
    }

    private boolean valid(LivingEntity entity) {
        if (this.onlyHittable.isToggled() && entity.damagedTimer > Settings.hittableHurtTime.getInput()) {
            return false;
        } else if (this.disableWhileChasing.isToggled() && !(mc.player.squaredDistanceTo(entity) <= 1.0)) {
            float yaw =
                    (float) (-Math.atan2(mc.player.x - entity.x, mc.player.z - entity.z) * (float) (180.0 / Math.PI));
            return Math.abs(MathHelper.wrapDegrees(yaw - entity.yaw)) <= 90.0;
        } else {
            return true;
        }
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(this.distanceMin, this.distanceMax);
        this.minRange = this.distanceMin.getInput();
        this.maxRange = this.distanceMax.getInput();
        double range = this.maxRange + 1.2;
        double scanRange = this.maxRange + 2.5;
        this.maxRangeSq = range * range;
        this.scanRangeSq = scanRange * scanRange;
    }

    private void reset() {
        this.ready = false;
        this.holding = false;
        this.ghost = null;
        this.target = null;
        this.tick = 0;
        this.lastFlush = System.currentTimeMillis();
    }

    private boolean targetValid() {
        if (Utils.nullCheck() && !mc.isSingleplayer()) {
            if (this.target == null
                    || mc.world.getEntity(this.target.getNetworkId()) == null
                    || this.target.deathTicks != 0) {
                return false;
            } else if (mc.screen != null && !(mc.screen instanceof ClickGui)) {
                return false;
            } else {
                return !mc.player.abilities.canFly
                                && (!this.disableAdventure.isToggled()
                                        || !mc.interactionManager.getGameMode().restrictsWorldModification())
                        ? !this.weaponOnly.isToggled() || Utils.holdingWeapon()
                        : false;
            }
        } else {
            return false;
        }
    }

    private void updateServerPos() {
        this.serverPos =
                new Vec3d(this.target.lastKnownX / 32.0, this.target.lastKnownY / 32.0, this.target.lastKnownZ / 32.0);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onReceivePacket(ReceivePacketEvent event) {
        try {
            this.handlePacket(event);
        } catch (Throwable exception) {
            Utils.sendMessage("&cBacktrack could not handle packet: "
                    + exception.getClass().getSimpleName());
        }
    }

    private void handlePacket(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (!event.isCanceled() && packet != this.processing) {
            if (Utils.nullCheck() && mc.getNetworkHandler() != null) {
                if (this.holding) {
                    if (packet instanceof PlayerMoveS2CPacket
                            || packet instanceof DisconnectS2CPacket
                            || this.flushOnDamage.isToggled()
                                    && packet instanceof EntityVelocityS2CPacket
                                    && ((EntityVelocityS2CPacket) packet).getId() == mc.player.getNetworkId()) {
                        this.flush();
                        return;
                    }
                } else if (!this.ready) {
                    return;
                }

                Vec3d position = this.serverPos;
                if (position != null) {
                    double x = position.x;
                    double y = position.y;
                    double z = position.z;
                    boolean moves = false;
                    boolean teleport = false;
                    if (packet instanceof Position || packet instanceof PositionAndAngles) {
                        EntityMoveS2CPacket movement = (EntityMoveS2CPacket) packet;
                        Entity entity = movement.getEntity(mc.world);
                        if (entity != null
                                && this.target != null
                                && entity.getNetworkId() == this.target.getNetworkId()) {
                            moves = true;
                            x += movement.getDx() / 32.0;
                            y += movement.getDy() / 32.0;
                            z += movement.getDz() / 32.0;
                        }
                    } else if (packet instanceof EntityTeleportS2CPacket) {
                        EntityTeleportS2CPacket movement = (EntityTeleportS2CPacket) packet;
                        if (this.target != null && movement.getId() == this.target.getNetworkId()) {
                            teleport = true;
                            moves = true;
                            x = movement.getX() / 32.0;
                            y = movement.getY() / 32.0;
                            z = movement.getZ() / 32.0;
                        }
                    }

                    if (moves) {
                        boolean flush = true;
                        double distanceSq = mc.player.squaredDistanceTo(x, y, z);
                        Box box = this.target.getShape();
                        Box newBox = box.moved(x - this.target.x, y - this.target.y, z - this.target.z);
                        float[] rotations = this.toBox(this.target, newBox);
                        if (distanceSq <= this.maxRangeSq
                                && !this.rayHits(this.target, newBox, rotations, this.minRange)
                                && this.rayHits(this.target, newBox, rotations, this.maxRange)) {
                            Box oldBox = box.moved(
                                    position.x - this.target.x, position.y - this.target.y, position.z - this.target.z);
                            float[] oldRotations = this.toBox(this.target, oldBox);
                            double newDistance = this.rayDistanceSq(this.target, newBox, rotations, this.maxRange);
                            double oldDistance =
                                    this.rayDistanceSq(this.target, oldBox, oldRotations, this.maxRange + 2.0);
                            if (oldDistance >= 0.0
                                    && newDistance > oldDistance
                                    && (!teleport || newDistance - oldDistance < 64.0)) {
                                if (!this.holding && this.canStart()) {
                                    this.start();
                                }

                                this.serverPos = new Vec3d(x, y, z);
                                RemoteClientPlayerEntity copy = this.ghost;
                                if (copy != null) {
                                    copy.lerpPositionAndAngles(x, y, z, 0.0F, 0.0F, 3, teleport);
                                }

                                flush = false;
                            }
                        }

                        if (this.holding && flush) {
                            this.flush();
                            return;
                        }
                    }

                    if (this.holding && (this.onlyMovement.isToggled() ? moves : !this.passesThrough(packet))) {
                        this.hold(packet);
                        event.setCanceled(true);
                    }
                }
            } else {
                if (this.holding) {
                    this.discardPackets();
                }
            }
        }
    }

    private float[] toBox(Entity entity, Box box) {
        float border = entity.getPickRadius();
        box = box.grown(border, border, border);
        Vec3d eyes = mc.player.getEyePosition(1.0F);
        if (box.contains(eyes)) {
            return new float[] {mc.player.yaw, mc.player.pitch};
        }

        double amount = Settings.aimClosestPoint.getInput() / 100.0;
        double centerX = (box.minX + box.maxX) / 2.0;
        double centerY = box.minY + (box.maxY - box.minY) * 0.825;
        double centerZ = (box.minZ + box.maxZ) / 2.0;
        double closestX = MathHelper.clamp(eyes.x, box.minX + 0.03, box.maxX - 0.03);
        double closestY = MathHelper.clamp(eyes.y, box.minY + 0.03, box.maxY - 0.03);
        double closestZ = MathHelper.clamp(eyes.z, box.minZ + 0.03, box.maxZ - 0.03);
        double dx = centerX + (closestX - centerX) * amount - eyes.x;
        double dy = centerY + (closestY - centerY) * amount - eyes.y;
        double dz = centerZ + (closestZ - centerZ) * amount - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * (float) (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-Math.atan2(dy, horizontal) * (float) (180.0 / Math.PI));
        RotationHelper helper = RotationHelper.get();
        float currentYaw = helper.isActive() && helper.getServerYaw() != null ? helper.getServerYaw() : mc.player.yaw;
        float currentPitch =
                helper.isActive() && helper.getServerPitch() != null ? helper.getServerPitch() : mc.player.pitch;
        return new float[] {
            currentYaw + MathHelper.wrapDegrees(yaw - currentYaw),
            RotationUtils.clampPitch(currentPitch + MathHelper.wrapDegrees(pitch - currentPitch))
        };
    }

    private HitResult rayHit(Entity entity, Box box, float[] rotations, double range) {
        if (!entity.hasCollision()) {
            return null;
        }

        Vec3d from = mc.player.getEyePosition(1.0F);
        Vec3d direction = RotationUtils.getVectorForRotation(rotations[1], rotations[0]);
        Vec3d to = from.add(direction.x * range, direction.y * range, direction.z * range);
        float border = entity.getPickRadius();
        return box.grown(border, border, border).clip(from, to);
    }

    private boolean rayHits(Entity entity, Box box, float[] rotations, double range) {
        return this.rayHit(entity, box, rotations, range) != null;
    }

    private double rayDistanceSq(Entity entity, Box box, float[] rotations, double range) {
        HitResult hit = this.rayHit(entity, box, rotations, range);
        return hit == null ? -1.0 : mc.player.getEyePosition(1.0F).squaredDistanceTo(hit.facePos);
    }

    private void hold(Packet<?> packet) {
        synchronized (this.packetLock) {
            this.held
                    .computeIfAbsent(this.tick + this.delayTicks, key -> new ArrayList<>())
                    .add(packet);
        }
    }

    private void process(List<Packet<?>> packets) {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (!packets.isEmpty() && Utils.nullCheck() && handler != null) {
            for (Packet<?> packet : packets) {
                try {
                    this.processing = packet;
                    ReceivePacketEvent replay = new ReceivePacketEvent(packet);
                    ForgeEvents.MinecraftForge.EVENT_BUS.post(replay);
                    if (!replay.isCanceled()) {
                        ((Packet) packet).handle(handler);
                    }
                } catch (DifferentThreadException var10) {
                } catch (Throwable exception) {
                    Utils.sendMessage(
                            "&cBacktrack could not process " + packet.getClass().getSimpleName() + ": "
                                    + exception.getClass().getSimpleName());
                } finally {
                    this.processing = null;
                }
            }
        }
    }

    private void releaseDue() {
        List<Packet<?>> packets = new ArrayList<>();
        synchronized (this.packetLock) {
            Iterator<Entry<Integer, List<Packet<?>>>> iterator =
                    this.held.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<Integer, List<Packet<?>>> entry = iterator.next();
                if (this.tick < entry.getKey()) {
                    break;
                }

                packets.addAll(entry.getValue());
                iterator.remove();
            }

            this.tick++;
        }

        this.process(packets);
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == this.onlyMovement) {
            this.flush();
        } else if (setting == this.showServerPosition) {
            if (!setting.isToggled()) {
                this.ghost = null;
            } else if (this.holding) {
                this.makeGhost(this.target);
            }
        }
    }

    private LivingEntity findTarget() {
        if (ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null
                && this.valid(KillAura.target)) {
            return KillAura.target;
        }

        if (ModuleManager.aimAssist != null && ModuleManager.aimAssist.isEnabled()) {
            Entity aimed = ModuleManager.aimAssist.getAimAssistTarget();
            if (aimed instanceof LivingEntity && this.valid((LivingEntity) aimed)) {
                return (LivingEntity) aimed;
            }
        }

        double halfFov = this.fov.getInput() / 2.0;
        double best = 999.0;
        PlayerEntity found = null;

        for (PlayerEntity player : mc.world.players) {
            if (player != mc.player
                    && player.deathTicks == 0
                    && this.valid(player)
                    && !AntiBot.isBot(player)
                    && !(mc.player.squaredDistanceTo(player) > this.scanRangeSq)) {
                double yaw = RotationUtils.distanceFromYaw(player, false);
                if (!(yaw > halfFov)
                        && !Utils.isFriended(player)
                        && (!this.ignoreTeammates.isToggled() || !Utils.isTeammate(player))
                        && yaw < best) {
                    best = yaw;
                    found = player;
                }
            }
        }

        return found;
    }

    private void makeGhost(LivingEntity entity) {
        RemoteClientPlayerEntity copy = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
        copy.copyPositionAndRotationFrom(entity);
        Vec3d position = this.serverPos;
        copy.setPosition(position.x, position.y, position.z);
        copy.lerpPositionAndAngles(position.x, position.y, position.z, entity.yaw, entity.pitch, 0, false);
        this.ghost = copy;
    }

    @Override
    public void onUpdate() {
        if (Utils.nullCheck() && !mc.isSingleplayer()) {
            this.guiUpdate();
            LivingEntity entity = this.findTarget();
            if (this.holding) {
                if (entity != this.target || !this.targetValid()) {
                    this.flush();
                    return;
                }

                RemoteClientPlayerEntity copy = this.ghost;
                if (copy != null) {
                    copy.lastX = copy.prevX = copy.x;
                    copy.lastY = copy.prevY = copy.y;
                    copy.lastZ = copy.prevZ = copy.z;
                    copy.mobTick();
                }

                this.releaseDue();
            } else {
                if (entity != null) {
                    this.target = entity;
                    this.updateServerPos();
                }

                this.ready = this.target != null && this.targetValid();
            }
        } else {
            this.discardPackets();
        }
    }

    private void discardPackets() {
        synchronized (this.packetLock) {
            this.held.clear();
            this.reset();
            this.serverPos = null;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldUnload(ForgeEvents.WorldEvent.Unload event) {
        if (event.world == mc.world) {
            this.discardPackets();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent event) {
        if (Utils.nullCheck()) {
            RemoteClientPlayerEntity copy = this.ghost;
            if (copy != null) {
                EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
                double x = copy.prevX + (copy.x - copy.prevX) * event.partialTicks - renderManager.cameraX;
                double y = copy.prevY + (copy.y - copy.prevY) * event.partialTicks - renderManager.cameraY;
                double z = copy.prevZ + (copy.z - copy.prevZ) * event.partialTicks - renderManager.cameraZ;
                Box bounds = copy.getShape().grown(0.1, 0.1, 0.1);
                Box box = new Box(
                        bounds.minX - copy.x + x,
                        bounds.minY - copy.y + y,
                        bounds.minZ - copy.z + z,
                        bounds.maxX - copy.x + x,
                        bounds.maxY - copy.y + y,
                        bounds.maxZ - copy.z + z);
                GL11.glBlendFunc(770, 771);
                GL11.glEnable(3042);
                GL11.glDisable(3553);
                this.filledBox(
                        box,
                        this.positionColor.getRed() / 255.0F,
                        this.positionColor.getGreen() / 255.0F,
                        this.positionColor.getBlue() / 255.0F,
                        this.positionColor.getAlpha() / 255.0F);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glEnable(3553);
                GL11.glDisable(3042);
            }
        }
    }

    private void filledBox(Box box, float red, float green, float blue, float alpha) {
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder renderer = tessellator.getBuffer();
        renderer.begin(7, DefaultVertexFormat.POSITION_COLOR);
        double[][] vertices = new double[][] {
            {box.minX, box.minY, box.minZ},
            {box.minX, box.maxY, box.minZ},
            {box.maxX, box.maxY, box.minZ},
            {box.maxX, box.minY, box.minZ},
            {box.minX, box.minY, box.maxZ},
            {box.maxX, box.minY, box.maxZ},
            {box.maxX, box.maxY, box.maxZ},
            {box.minX, box.maxY, box.maxZ},
            {box.minX, box.minY, box.minZ},
            {box.minX, box.minY, box.maxZ},
            {box.minX, box.maxY, box.maxZ},
            {box.minX, box.maxY, box.minZ},
            {box.maxX, box.minY, box.minZ},
            {box.maxX, box.maxY, box.minZ},
            {box.maxX, box.maxY, box.maxZ},
            {box.maxX, box.minY, box.maxZ},
            {box.minX, box.minY, box.minZ},
            {box.maxX, box.minY, box.minZ},
            {box.maxX, box.minY, box.maxZ},
            {box.minX, box.minY, box.maxZ},
            {box.minX, box.maxY, box.minZ},
            {box.minX, box.maxY, box.maxZ},
            {box.maxX, box.maxY, box.maxZ},
            {box.maxX, box.maxY, box.minZ}
        };

        for (double[] vertex : vertices) {
            renderer.vertex(vertex[0], vertex[1], vertex[2])
                    .color(red, green, blue, alpha)
                    .nextVertex();
        }

        tessellator.end();
    }

    public boolean isRenderingServerPositionFor(LivingEntity entity) {
        return this.isEnabled()
                && this.holding
                && this.showServerPosition.isToggled()
                && this.target == entity
                && this.ghost != null;
    }

    public Vec3d getBacktrackPosition() {
        return this.holding && this.target != null && !this.target.removed ? this.serverPos : null;
    }

    public LivingEntity getBacktrackTarget() {
        return this.holding && this.target != null && !this.target.removed ? this.target : null;
    }
}
