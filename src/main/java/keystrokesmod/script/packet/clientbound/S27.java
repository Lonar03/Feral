package keystrokesmod.script.packet.clientbound;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.script.model.Vec3;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.BlockPos;

public class S27 extends SPacket {
    public float strength;
    public Vec3 position;
    public Vec3 motion;
    public List<Vec3> affectedBlockPositions;

    public S27(ExplosionS2CPacket packet) {
        super(packet);
        this.strength = packet.getPower();
        this.position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        this.motion = new Vec3(packet.getPlayerVelocityX(), packet.getPlayerVelocityY(), packet.getPlayerVelocityZ());
        List<Vec3> affectedBlockPositions = new ArrayList<>();

        for (BlockPos blockPos : packet.getDamagedBlocks()) {
            affectedBlockPositions.add(Vec3.convert(blockPos));
        }

        this.affectedBlockPositions = affectedBlockPositions;
    }

    public S27(float strength, Vec3 position, Vec3 motion, List<Vec3> affectedBlockPositions) {
        super(create(strength, position, motion, affectedBlockPositions));
        this.strength = strength;
        this.position = position;
        this.motion = motion;
        this.affectedBlockPositions = affectedBlockPositions;
    }

    private static ExplosionS2CPacket create(
            float strength, Vec3 position, Vec3 motion, List<Vec3> affectedBlockPositions) {
        List<BlockPos> list = new ArrayList<>();

        for (Vec3 vec3 : affectedBlockPositions) {
            list.add(Vec3.getBlockPos(vec3));
        }

        return new ExplosionS2CPacket(position.x, position.y, position.z, strength, list, Vec3.getVec3(motion));
    }
}
