package keystrokesmod.module.impl.other;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.HoverEvent.Action;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;

public class LatencyAlerts extends Module {
    private SliderSetting interval;
    private SliderSetting highLatency;
    private ButtonSetting ignoreLimbo;
    private long lastPacketTime = 0L;
    private long lastAlert = 0L;
    private Packet<?> lastPacket = null;

    public LatencyAlerts() {
        super("Latency Alerts", Module.category.other);
        this.registerSetting(new DescriptionSetting("Detects packet loss."));
        this.registerSetting(this.interval = new SliderSetting("Alert interval", " second", 3.0, 0.0, 5.0, 0.1));
        this.registerSetting(this.highLatency = new SliderSetting("High latency", " second", 0.5, 0.1, 5.0, 0.1));
        this.registerSetting(this.ignoreLimbo = new ButtonSetting("Ignore limbo", true));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        this.lastPacketTime = 0L;
        this.lastAlert = 0L;
        this.lastPacket = null;
    }

    @ForgeEvents.SubscribeEvent
    public void onPacketReceive(ReceivePacketEvent e) {
        this.lastPacketTime = System.currentTimeMillis();
        this.lastPacket = e.getPacket();
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            this.lastPacketTime = System.currentTimeMillis();
            this.lastAlert = System.currentTimeMillis();
            this.lastPacket = null;
        } else if (!mc.isSingleplayer() && (!this.ignoreLimbo.isToggled() || !this.inLimbo())) {
            long currentMs = System.currentTimeMillis();
            if (currentMs - this.lastPacketTime >= this.highLatency.getInput() * 1000.0
                    && currentMs - this.lastAlert >= this.interval.getInput() * 1000.0) {
                String msSinceLastPacket = String.valueOf(Math.abs(System.currentTimeMillis() - this.lastPacketTime));
                LiteralText component = new LiteralText(Utils.formatColor("&7[&dR&7]&r &7Packet loss detected: "));
                Style style = new Style();
                String packetName = this.lastPacket == null
                        ? "Unknown"
                        : this.lastPacket.getClass().getSimpleName();
                String contents = "&7Last packet: &b" + packetName;
                String timeString = new SimpleDateFormat("h:mm:ss a").format(new Date(this.lastPacketTime));
                contents = contents + "\n&7Received at: &b" + timeString;
                style.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new LiteralText(Utils.formatColor(contents))));
                component.append(new LiteralText(Utils.formatColor("&c" + msSinceLastPacket)).setStyle(style));
                component.append(new LiteralText(Utils.formatColor("&7ms")));
                mc.player.sendMessage(component);
                this.lastAlert = System.currentTimeMillis();
            }
        } else {
            this.lastPacketTime = System.currentTimeMillis();
            this.lastAlert = System.currentTimeMillis();
        }
    }

    @Override
    public void onEnable() {
        this.lastPacketTime = System.currentTimeMillis();
    }

    public boolean inLimbo() {
        if (!Utils.nullCheck()) {
            return false;
        }

        List<String> scoreboard = Utils.getSidebarLines();
        return scoreboard.isEmpty() ? mc.world.dimension.getName().equals("The End") : false;
    }
}
