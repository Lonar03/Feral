package keystrokesmod.module.impl.minigames;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.text.Text;

public class AutoRequeue extends Module {
    private SliderSetting delay;
    private String receivedMessage = "";
    private long receiveTime = 0L;

    public AutoRequeue() {
        super("Auto Requeue", Module.category.minigames);
        this.registerSetting(new DescriptionSetting("Automatically requeues games."));
        this.registerSetting(this.delay = new SliderSetting("Delay", " second", 0.5, 0.0, 5.0, 0.1));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        this.receivedMessage = "";
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!this.receivedMessage.isEmpty()
                && System.currentTimeMillis() - this.receiveTime >= this.delay.getInput() * 1000.0) {
            mc.player.sendChat(this.receivedMessage);
            this.receivedMessage = "";
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onChat(ForgeEvents.ClientChatReceivedEvent e) {
        if (e.type != 2 && Utils.nullCheck()) {
            String stripped = Utils.stripColor(e.message.getString());
            if (!stripped.isEmpty() && stripped.contains("play again")) {
                if (e.message != null) {
                    for (Text component : e.message.getSiblings()) {
                        if (component != null
                                && component.getFormattedString().contains("Click here")
                                && component.getStyle() != null
                                && component.getStyle().getClickEvent() != null
                                && component
                                        .getStyle()
                                        .getClickEvent()
                                        .getValue()
                                        .startsWith("/")) {
                            this.receivedMessage =
                                    component.getStyle().getClickEvent().getValue();
                            this.receiveTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.receivedMessage = "";
        }
    }
}
