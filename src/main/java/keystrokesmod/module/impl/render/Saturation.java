package keystrokesmod.module.impl.render;

import java.io.IOException;
import java.util.List;
import keystrokesmod.mixin.impl.accessor.IAccessorShaderGroup;
import keystrokesmod.mixin.interfaces.ISaturationRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;
import net.minecraft.client.render.PostChain;
import net.minecraft.client.render.PostPass;
import net.minecraft.client.render.shaders.Uniform;
import net.minecraft.resource.Identifier;

public class Saturation extends Module {
    private static final Identifier SHADER_LOCATION = new Identifier("minecraft:shaders/post/color_convolve.json");
    private SliderSetting saturationSlider;
    private float lastSaturation = 1.0F;

    public Saturation() {
        super("Saturation", Module.category.render);
        this.registerSetting(this.saturationSlider = new SliderSetting("Saturation", 1.0, -1.0, 5.0, 0.05));
    }

    @Override
    public void onEnable() {
        this.loadShader();
    }

    @Override
    public void onDisable() {
        this.removeShader();
    }

    @Override
    public void onUpdate() {
        float current = (float) this.saturationSlider.getInput();
        if (!this.isShaderActive()) {
            this.loadShader();
        }

        if (current != this.lastSaturation) {
            this.lastSaturation = current;
            this.applySaturation();
        }
    }

    private void loadShader() {
        if (mc.world != null) {
            if (!this.isShaderActive()) {
                try {
                    PostChain shader = new PostChain(
                            mc.getTextureManager(), mc.getResourceManager(), mc.getRenderTarget(), SHADER_LOCATION);
                    shader.resize(mc.width, mc.height);
                    ISaturationRenderer ren = this.getSaturationRenderer();
                    if (ren != null) {
                        ren.feral$setSaturationShader(shader);
                    }

                    this.lastSaturation = (float) this.saturationSlider.getInput();
                    this.applySaturation();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void removeShader() {
        ISaturationRenderer renderer = this.getSaturationRenderer();
        if (renderer != null) {
            PostChain shader = renderer.feral$getSaturationShader();
            if (shader != null) {
                shader.close();
            }

            renderer.feral$setSaturationShader(null);
        }
    }

    private void applySaturation() {
        ISaturationRenderer renderer = this.getSaturationRenderer();
        if (renderer != null) {
            PostChain shader = renderer.feral$getSaturationShader();
            if (shader != null) {
                List<PostPass> shaders = ((IAccessorShaderGroup) shader).getListShaders();
                if (shaders != null) {
                    for (PostPass s : shaders) {
                        Uniform su = s.getEffect().getUniform("Saturation");
                        if (su != null) {
                            su.set(this.lastSaturation);
                        }
                    }
                }
            }
        }
    }

    private boolean isShaderActive() {
        ISaturationRenderer renderer = this.getSaturationRenderer();
        return renderer != null && renderer.feral$getSaturationShader() != null;
    }

    private ISaturationRenderer getSaturationRenderer() {
        return mc.gameRenderer == null ? null : (ISaturationRenderer) mc.gameRenderer;
    }
}
