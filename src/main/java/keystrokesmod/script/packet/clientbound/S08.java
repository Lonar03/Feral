package keystrokesmod.script.packet.clientbound;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket.Argument;

public class S08 extends SPacket {
    public Vec3 position;
    public float yaw;
    public float pitch;
    public Set<String> enumFlags;

    public S08(PlayerMoveS2CPacket packet) {
        super(packet);
        this.position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        this.yaw = packet.getYaw();
        this.pitch = packet.getPitch();
        Set<Argument> flags = packet.getRelativeArgs();
        Set<String> flagsStr = new HashSet<>(flags.size());

        for (Argument flag : flags) {
            flagsStr.add(flag.name());
        }

        this.enumFlags = flagsStr;
    }

    public S08(Vec3 position, float yaw, float pitch, Set<String> enumFlags) {
        super(create(position, yaw, pitch, enumFlags));
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.enumFlags = enumFlags;
    }

    private static PlayerMoveS2CPacket create(Vec3 position, float yaw, float pitch, Set<String> enumFlags) {
        Set<Argument> enumSet = EnumSet.noneOf(Argument.class);

        for (String flag : enumFlags) {
            Argument enumFlag = Utils.getEnum(Argument.class, flag);
            if (enumFlag != null) {
                enumSet.add(enumFlag);
            }
        }

        return new PlayerMoveS2CPacket(position.x, position.y, position.z, yaw, pitch, enumSet);
    }
}
