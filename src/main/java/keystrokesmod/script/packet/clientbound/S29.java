package keystrokesmod.script.packet.clientbound;

import keystrokesmod.script.model.Vec3;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;

public class S29 extends SPacket {
    public String sound;
    public Vec3 position;
    public float volume;
    public float pitch;

    public S29(SoundEventS2CPacket packet) {
        super(packet);
        this.sound = packet.getSound();
        this.position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        this.volume = packet.getVolume();
        this.pitch = packet.getPitch();
    }

    public S29(String sound, Vec3 position, float volume, float pitch) {
        super(new SoundEventS2CPacket(sound, position.x, position.y, position.z, volume, pitch));
        this.sound = sound;
        this.position = position;
        this.volume = volume;
        this.pitch = pitch;
    }
}
