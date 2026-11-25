package keystrokesmod.module.impl.combat;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.movement.LongJump;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.lwjgl.input.Mouse;

public class AntiKnockback extends Module {
    private SliderSetting horizontal;
    private SliderSetting vertical;
    private ButtonSetting disableInLobby;
    private ButtonSetting cancelBurning;
    private ButtonSetting cancelExplosion;
    private ButtonSetting cancelWhileFalling;
    private ButtonSetting cancelOffGround;
    private SliderSetting boostMultiplier;
    private ButtonSetting boostWithLMB;
    public boolean disable;

    public AntiKnockback() {
        super("AntiKnockback", Module.category.combat);
        this.registerSetting(new DescriptionSetting("Overrides Velocity."));
        this.registerSetting(this.horizontal = new SliderSetting("Horizontal", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.vertical = new SliderSetting("Vertical", 0.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.disableInLobby = new ButtonSetting("Disable in lobby", false));
        this.registerSetting(this.cancelBurning = new ButtonSetting("Cancel burning", true));
        this.registerSetting(this.cancelExplosion = new ButtonSetting("Cancel explosion", true));
        this.registerSetting(this.cancelWhileFalling = new ButtonSetting("Cancel while falling", true));
        this.registerSetting(this.cancelOffGround = new ButtonSetting("Cancel off ground", true));
        this.registerSetting(this.boostMultiplier = new SliderSetting("Damage boost", "x", 1.0, 0.5, 2.5, 0.01));
        this.registerSetting(this.boostWithLMB = new ButtonSetting("Boost with LMB", false));
        this.liteModule = true;
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (Utils.nullCheck() && !LongJump.stopVelocity && !e.isCanceled()) {
            if (e.getPacket() instanceof EntityVelocityS2CPacket) {
                if (((EntityVelocityS2CPacket) e.getPacket()).getId() == mc.player.getNetworkId() && !this.disable) {
                    if (!this.cancelBurning.isToggled() && mc.player.isOnFire()) {
                        return;
                    }

                    if (this.disableInLobby.isToggled() && Utils.isLobby()) {
                        return;
                    }

                    e.setCanceled(true);
                    if (this.cancel()) {
                        return;
                    }

                    if (this.cancelConditions()) {
                        return;
                    }

                    EntityVelocityS2CPacket s12PacketEntityVelocity = (EntityVelocityS2CPacket) e.getPacket();
                    if (this.horizontal.getInput() == 0.0 && this.vertical.getInput() > 0.0) {
                        mc.player.velocityY =
                                s12PacketEntityVelocity.getVelocityY() / 8000.0 * this.vertical.getInput() / 100.0;
                    } else if (this.horizontal.getInput() > 0.0 && this.vertical.getInput() == 0.0) {
                        mc.player.velocityX =
                                s12PacketEntityVelocity.getVelocityX() / 8000.0 * this.horizontal.getInput() / 100.0;
                        mc.player.velocityZ =
                                s12PacketEntityVelocity.getVelocityZ() / 8000.0 * this.horizontal.getInput() / 100.0;
                    } else {
                        mc.player.velocityX =
                                s12PacketEntityVelocity.getVelocityX() / 8000.0 * this.horizontal.getInput() / 100.0;
                        mc.player.velocityY =
                                s12PacketEntityVelocity.getVelocityY() / 8000.0 * this.vertical.getInput() / 100.0;
                        mc.player.velocityZ =
                                s12PacketEntityVelocity.getVelocityZ() / 8000.0 * this.horizontal.getInput() / 100.0;
                    }

                    if (this.boostMultiplier.getInput() != 1.0) {
                        if (this.boostWithLMB.isToggled() && !Mouse.isButtonDown(0)) {
                            return;
                        }

                        Utils.setSpeed(Utils.getHorizontalSpeed() * this.boostMultiplier.getInput());
                    }
                }
            } else if (e.getPacket() instanceof ExplosionS2CPacket && !this.disable) {
                if (this.disableInLobby.isToggled() && Utils.isLobby()) {
                    return;
                }

                e.setCanceled(true);
                if (this.cancelExplosion.isToggled() || this.cancel()) {
                    return;
                }

                if (this.cancelConditions()) {
                    return;
                }

                ExplosionS2CPacket s27PacketExplosion = (ExplosionS2CPacket) e.getPacket();
                if (this.horizontal.getInput() == 0.0 && this.vertical.getInput() > 0.0) {
                    mc.player.velocityY = mc.player.velocityY
                            + s27PacketExplosion.getPlayerVelocityY() * this.vertical.getInput() / 100.0;
                } else if (this.horizontal.getInput() > 0.0 && this.vertical.getInput() == 0.0) {
                    mc.player.velocityX = mc.player.velocityX
                            + s27PacketExplosion.getPlayerVelocityX() * this.horizontal.getInput() / 100.0;
                    mc.player.velocityZ = mc.player.velocityZ
                            + s27PacketExplosion.getPlayerVelocityZ() * this.horizontal.getInput() / 100.0;
                } else {
                    mc.player.velocityX = mc.player.velocityX
                            + s27PacketExplosion.getPlayerVelocityX() * this.horizontal.getInput() / 100.0;
                    mc.player.velocityY = mc.player.velocityY
                            + s27PacketExplosion.getPlayerVelocityY() * this.vertical.getInput() / 100.0;
                    mc.player.velocityZ = mc.player.velocityZ
                            + s27PacketExplosion.getPlayerVelocityZ() * this.horizontal.getInput() / 100.0;
                }
            }
        }
    }

    private boolean cancel() {
        return this.vertical.getInput() == 0.0 && this.horizontal.getInput() == 0.0;
    }

    @Override
    public String getInfo() {
        return (int) this.horizontal.getInput() + "% " + (int) this.vertical.getInput() + "%";
    }

    private boolean cancelConditions() {
        if (mc.player != null) {
            if (this.cancelWhileFalling.isToggled() && mc.player.fallDistance > 0.0F) {
                return true;
            }

            if (this.cancelOffGround.isToggled() && !mc.player.onGround) {
                return true;
            }
        }

        return false;
    }
}
