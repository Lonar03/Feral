package keystrokesmod.module.impl.render;

import java.util.ArrayList;
import keystrokesmod.Feral;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public class Tracers extends Module {
    public ButtonSetting showInvis;
    public ColorSetting color;
    public ButtonSetting rainbow;
    public SliderSetting lineWidth;
    private boolean viewBobbingEnabled;
    private final ArrayList<Entity> trackedEntities = new ArrayList<>();
    private int trackedEntityCount = 0;

    public Tracers() {
        super("Tracers", Module.category.render);
        this.registerSetting(this.showInvis = new ButtonSetting("Show invis", true));
        this.registerSetting(this.lineWidth = new SliderSetting("Line Width", 1.0, 1.0, 5.0, 1.0));
        this.registerSetting(this.color = new ColorSetting("Color", 0, 255, 0));
        this.registerSetting(this.rainbow = new ButtonSetting("Rainbow", false));
    }

    @Override
    public void onEnable() {
        this.viewBobbingEnabled = mc.options.viewBobbing;
        if (this.viewBobbingEnabled) {
            mc.options.viewBobbing = false;
        }
    }

    @Override
    public void onDisable() {
        mc.options.viewBobbing = this.viewBobbingEnabled;
    }

    @Override
    public void onUpdate() {
        if (mc.options.viewBobbing) {
            mc.options.viewBobbing = false;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            this.updateTrackedEntities();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && this.trackedEntityCount != 0) {
            int rgb = this.rainbow.isToggled() ? Utils.getChroma(2L, 0L) : this.color.getColor();

            for (int i = 0; i < this.trackedEntityCount; i++) {
                Entity entity = this.trackedEntities.get(i);
                if (entity != null) {
                    RenderUtils.drawTracerLine(entity, rgb, (float) this.lineWidth.getInput(), e.partialTicks);
                }
            }
        }
    }

    private void updateTrackedEntities() {
        this.trackedEntityCount = 0;
        if (Utils.nullCheck() && mc.world != null) {
            if (Feral.DEBUG) {
                for (Entity entity : mc.world.entities) {
                    if (entity instanceof LivingEntity && entity != mc.player) {
                        this.addTrackedEntity(entity);
                    }
                }
            } else {
                for (PlayerEntity player : mc.world.players) {
                    if (player != mc.player
                            && player.deathTicks == 0
                            && (this.showInvis.isToggled() || !player.isInvisible())
                            && !AntiBot.isBot(player)) {
                        this.addTrackedEntity(player);
                    }
                }
            }
        }
    }

    private void addTrackedEntity(Entity entity) {
        if (this.trackedEntityCount >= this.trackedEntities.size()) {
            this.trackedEntities.add(entity);
        } else {
            this.trackedEntities.set(this.trackedEntityCount, entity);
        }

        this.trackedEntityCount++;
    }
}
