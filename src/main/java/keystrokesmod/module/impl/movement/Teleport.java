package keystrokesmod.module.impl.movement;

import java.awt.Color;
import java.util.ArrayList;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Position;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class Teleport extends Module {
    private ButtonSetting rightClick;
    private ButtonSetting highlightTarget;
    private ButtonSetting highlightPath;
    private ButtonSetting instant;
    private BlockPos targetPos;
    private ArrayList<Vec3d> path = new ArrayList<>();

    public Teleport() {
        super("Teleport", Module.category.movement);
        this.registerSetting(this.rightClick = new ButtonSetting("Right click teleport", true));
        this.registerSetting(this.highlightTarget = new ButtonSetting("Highlight target", true));
        this.registerSetting(this.highlightPath = new ButtonSetting("Highlight path", false));
        this.registerSetting(this.instant = new ButtonSetting("Instant", false));
    }

    public void teleport(BlockPos targetBlock, boolean sendMessage) {
        targetBlock = targetBlock.up(1);
        int packetsSent = 0;
        if (!this.instant.isToggled()) {
            for (Vec3d pathPos : this.path = this.getPath(targetBlock)) {
                mc.getNetworkHandler().sendPacket(new Position(pathPos.x, pathPos.y, pathPos.z, true));
                if (++packetsSent >= 175) {
                    if (sendMessage) {
                        Utils.sendMessage("&eToo many packets, ending loop.");
                    }
                    break;
                }
            }
        }

        mc.player.setPosition(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        if (sendMessage) {
            Utils.sendMessage("&eTeleported to &d("
                    + targetBlock.getX()
                    + ", "
                    + targetBlock.getY()
                    + ", "
                    + targetBlock.getZ()
                    + ")"
                    + (!this.instant.isToggled() ? " &ewith &b" + packetsSent + " &epackets." : "&e."));
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (this.rightClick.isToggled()
                && this.highlightTarget.isToggled()
                && this.targetPos != null
                && Utils.nullCheck()) {
            RenderUtils.renderBlock(this.targetPos, Color.orange.getRGB(), true, true);
            if (this.highlightPath.isToggled() && !this.instant.isToggled()) {
                int positions = 0;

                for (Vec3d pos : this.path) {
                    if (positions >= 175) {
                        break;
                    }

                    RenderUtils.renderBlock(new BlockPos(pos.x, pos.y, pos.z), Color.yellow.getRGB(), false, true);
                    positions++;
                }
            }
        }
    }

    private ArrayList getPath(BlockPos target) {
        ArrayList<Vec3d> path = new ArrayList<>();
        double newX = target.getX() + 0.5;
        double newY = target.getY() + 1;
        double newZ = target.getZ() + 0.5;
        double distance = mc.player.distanceTo(newX, newY, newZ);

        for (double d = 0.0; d < distance; d += 2.0) {
            path.add(new Vec3d(
                    mc.player.x + (newX - mc.player.getHorizontalFacing().getOffsetX() - mc.player.x) * d / distance,
                    mc.player.y + (newY - mc.player.y) * d / distance,
                    mc.player.z + (newZ - mc.player.getHorizontalFacing().getOffsetZ() - mc.player.z) * d / distance));
        }

        return path;
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent mouseEvent) {
        if (mouseEvent.button == 1 && mouseEvent.buttonstate && this.rightClick.isToggled() && Utils.nullCheck()) {
            HitResult rayCast = RotationUtils.rayCast(150.0, mc.player.yaw, mc.player.pitch, true);
            if (rayCast != null && rayCast.type == Type.BLOCK) {
                BlockPos getBlockPos = rayCast.getPos();
                this.targetPos = getBlockPos;
                this.teleport(getBlockPos, true);
            }
        }
    }

    @Override
    public void onEnable() {
        this.targetPos = null;
        this.path.clear();
        if (!this.rightClick.isToggled()) {
            HitResult rayCast = RotationUtils.rayCast(150.0, mc.player.yaw, mc.player.pitch, true);
            if (rayCast != null && rayCast.type == Type.BLOCK) {
                this.teleport(rayCast.getPos(), true);
                this.disable();
            }
        }
    }
}
