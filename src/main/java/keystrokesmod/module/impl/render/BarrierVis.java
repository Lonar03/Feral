package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;

/** Renders barrier blocks as visible blocks and hides their particles. */
public class BarrierVis extends Module {
    public BarrierVis() {
        super("Barrier Vis", Module.category.render);
    }

    @Override
    public void onEnable() {
        reloadRenderers();
    }

    @Override
    public void onDisable() {
        reloadRenderers();
    }

    private static void reloadRenderers() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }
}
