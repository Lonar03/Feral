package keystrokesmod.utility;

import java.util.List;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.DamageTags;
import keystrokesmod.module.impl.render.HoloVis;
import keystrokesmod.module.impl.render.ItemESP;
import keystrokesmod.module.impl.render.MobESP;
import keystrokesmod.module.impl.render.Nametags;
import keystrokesmod.module.impl.render.TNTTimer;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderWorldLastEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.utility.WorldEntityScan.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Plans shared discovery only. Each subscriber still reads entity properties in its own callback. */
public final class OverlayEntityScans {
    private OverlayEntityScans() {}

    public static List<Entity> render(RenderWorldLastEvent event, Minecraft mc, Kind requested) {
        World world = mc.world;
        if (world == null || mc.player == null) {
            // Bypass the shared path; the subscriber owns its normal null handling.
            return world.entities;
        }
        // Use current settings only to decide whether sharing is worth it. The caller always requests
        // its own kind, even if it was disabled after the bus took its listener snapshot.
        int kinds = requested.bit();
        Module items = ModuleManager.getModule(ItemESP.class);
        if (items instanceof ItemESP && items.isEnabled() && ((ItemESP) items).hasResourceOverlay()) {
            kinds |= Kind.ITEMS.bit();
        }
        if (enabled(HoloVis.class)) {
            kinds |= Kind.ARMOR_STANDS.bit();
        }
        boolean mobEsp = enabled(MobESP.class);
        boolean damageTags = enabled(DamageTags.class);
        if (mobEsp || damageTags) {
            kinds |= Kind.LIVING.bit();
        }
        return Integer.bitCount(kinds) > 1 || (mobEsp && damageTags)
                ? event.entityCandidates(world, kinds, requested)
                : event.existingEntityCandidates(world, requested);
    }

    public static List<Entity> tick(ClientTickEvent event, Minecraft mc, Kind requested) {
        World world = mc.world;
        int kinds = requested.bit();
        Module tags = ModuleManager.getModule(Nametags.class);
        if (tags instanceof Nametags && tags.isEnabled() && ((Nametags) tags).showsMobs() && mc.getCamera() != null) {
            kinds |= Kind.LIVING.bit();
        }
        if (enabled(TNTTimer.class)) {
            kinds |= Kind.PRIMED_TNT.bit();
        }
        return Integer.bitCount(kinds) > 1 ? event.entityCandidates(world, kinds, requested) : world.entities;
    }

    private static boolean enabled(Class<? extends Module> type) {
        Module module = ModuleManager.getModule(type);
        return module != null && module.isEnabled();
    }
}
