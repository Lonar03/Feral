package keystrokesmod.clickgui;

import java.awt.Color;
import keystrokesmod.module.setting.impl.ColorSetting;

/** Keeps otherwise unrepresentable hue/saturation for one picker at black or gray. */
public final class ColorPickerState {
    boolean open;
    float reveal;
    int frame = -1;
    float hue;
    float saturation;
    float brightness;
    private int rgb = -1;

    public void sync(ColorSetting setting) {
        if (rgb != setting.getRGB()) {
            rgb = setting.getRGB();
            float[] hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), null);
            hue = hsb[0] * 360f;
            saturation = hsb[1];
            brightness = hsb[2];
        }
    }

    public float hue() {
        return hue;
    }

    public float saturation() {
        return saturation;
    }

    public float brightness() {
        return brightness;
    }

    public void setSaturationValue(ColorSetting setting, float saturation, float brightness) {
        this.saturation = saturation;
        this.brightness = brightness;
        commit(setting);
    }

    public void setHue(ColorSetting setting, float hue) {
        this.hue = hue;
        commit(setting);
    }

    void commit(ColorSetting setting) {
        setting.setFromHSB(hue, saturation, brightness);
        rgb = setting.getRGB();
    }
}
