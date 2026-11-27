package keystrokesmod.module.impl.client;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import keystrokesmod.Feral;
import keystrokesmod.clickgui.BigCatGui;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FeralFontRenderer;
import keystrokesmod.utility.font.FontManager;

public class Gui extends Module {
    public static SliderSetting guiScale;
    public static SliderSetting style;
    public static SliderSetting font;
    public static SliderSetting fontSize;
    public static SliderSetting fontThickness;
    public static SliderSetting backgroundBlur;
    public static SliderSetting scrollSpeed;
    public static ButtonSetting hidePlayerModel;
    public static ButtonSetting darkBackground;
    public static ButtonSetting hideWatermark;
    public static ButtonSetting rainBowOutlines;
    public static ButtonSetting liteMode;
    public static ButtonSetting loadGuiPositions;
    public static ColorSetting enabledColor;
    public static ColorSetting disabledColor;
    /** Big cat window colours (Meower has these per category in its category cats, Feral has one set). */
    public static ColorSetting bigCatBackgroundColor;

    public static ColorSetting bigCatTextColor;
    public static SliderSetting bigCatWidth;
    public static SliderSetting bigCatHeight;
    /** Only used by the Big cat style. BigCatGui reads them by name, so keep the names in sync. */
    private final List<Setting> bigCatSettings = new ArrayList<>();
    /** Only used by the Classic style (the ClickGui). */
    private final List<Setting> clickGuiSettings = new ArrayList<>();

    private static final String[] STYLES = new String[] {"Classic", "Big cat"};
    public static final int DEFAULT_KEY = 54;
    /** Style the open GUI was opened with; a change while it's open closes it (-1 = unknown). */
    private static double openedStyle = -1.0;

    public static final int UNSAVED_COLOR = new Color(116, 202, 255).getRGB();
    public static final int INVALID_COLOR = new Color(255, 80, 80).getRGB();
    public static final int INFO_COLOR = new Color(57, 146, 229, 255).getRGB();

    public Gui() {
        super("Gui", Module.category.client, DEFAULT_KEY);
        this.liteModule = true;
        this.canBeEnabled = false;
        this.registerSetting(guiScale = new SliderSetting("Scale", "x", 1.0, 0.5, 2.0, 0.01, "Gui scale"));
        this.registerSetting(style = new SliderSetting("Style", 0, STYLES));
        this.registerSetting(font = FontManager.createFontSetting("Font"));
        this.registerSetting(fontSize = new SliderSetting("Font size", "x", 1.0, 0.75, 1.5, 0.05));
        this.registerSetting(fontThickness = new SliderSetting("Font thickness", 0.0, 0.0, 1.0, 0.05));
        this.registerSetting(
                backgroundBlur = new SliderSetting("Background blur", "%", 0.0, 0.0, 100.0, 1.0), clickGuiSettings);
        this.registerSetting(scrollSpeed = new SliderSetting("Scroll speed", 20.0, 2.0, 90.0, 1.0), clickGuiSettings);
        this.registerSetting(darkBackground = new ButtonSetting("Dark background", true), clickGuiSettings);
        this.registerSetting(hidePlayerModel = new ButtonSetting("Hide player model", false), clickGuiSettings);
        this.registerSetting(hideWatermark = new ButtonSetting("Hide watermark", false), clickGuiSettings);
        this.registerSetting(rainBowOutlines = new ButtonSetting("Rainbow outlines", true), clickGuiSettings);
        this.registerSetting(liteMode = new ButtonSetting("Lite mode", false));
        this.registerSetting(loadGuiPositions = new ButtonSetting("Save category positions", false), clickGuiSettings);
        this.registerSetting(
                new ButtonSetting("Reset positions", () -> Feral.clickGui.resetPositions()), clickGuiSettings);
        this.registerSetting(new DescriptionSetting("Colors"), clickGuiSettings);
        this.registerSetting(enabledColor = new ColorSetting("Enabled color", 24, 154, 255), clickGuiSettings);
        this.registerSetting(disabledColor = new ColorSetting("Disabled color", 192, 192, 192), clickGuiSettings);

        // Big cat window (ported from MeowerClient). Its accent colour is the selected category's colour.
        GroupSetting window = new GroupSetting("Big cat window");
        this.registerSetting(window, bigCatSettings);
        this.registerSetting(
                bigCatWidth = new SliderSetting(window, "Width", "px", 540.0, 260.0, 900.0, 5.0), bigCatSettings);
        this.registerSetting(
                bigCatHeight = new SliderSetting(window, "Height", "px", 410.0, 180.0, 700.0, 5.0), bigCatSettings);
        this.registerSetting(new SliderSetting(window, "Opacity", "%", 100.0, 40.0, 100.0, 1.0), bigCatSettings);
        this.registerSetting(
                bigCatBackgroundColor = new ColorSetting(window, "Background color", 20, 22, 26), bigCatSettings);
        this.registerSetting(bigCatTextColor = new ColorSetting(window, "Text color", 240, 240, 240), bigCatSettings);
    }

    private void registerSetting(Setting setting, List<Setting> styleList) {
        this.registerSetting(setting);
        styleList.add(setting);
    }

    @Override
    public void guiUpdate() {
        // Switching the style closes the ClickGui: some style settings only apply when the GUI opens.
        // (Big cat closes itself in BigCatEvents.)
        if (mc.screen == Feral.clickGui && openedStyle >= 0.0 && style.getInput() != openedStyle) {
            mc.openScreen(null);
        }
        openedStyle = style.getInput();

        boolean bigCat = isBigCatStyle();
        for (Setting setting : this.bigCatSettings) {
            setting.setVisible(bigCat, this);
        }
        for (Setting setting : this.clickGuiSettings) {
            setting.setVisible(!bigCat, this);
        }
    }

    @Override
    public boolean canBeBound() {
        return true;
    }

    @Override
    protected void onBindPressed() {
        openedStyle = style.getInput();
        if (isBigCatStyle()) {
            if (Utils.nullCheck() && mc.screen == null) {
                // Profile/script reloads and Lite mode already keep the shared module list current.
                BigCatGui.INSTANCE.requestOpen();
            }
        } else if (Utils.nullCheck() && mc.screen != Feral.clickGui) {
            mc.openScreen(Feral.clickGui);
            Feral.clickGui.initMain();
        }
    }

    public static String getSelectedFontName() {
        if (font == null) {
            return FontManager.getDefaultFontName();
        }

        int index = (int) Math.max(0.0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    public static float getSelectedFontSize() {
        return fontSize == null ? 1.0F : (float) fontSize.getInput();
    }

    public static float getSelectedFontThickness() {
        return fontThickness == null ? 0.0F : (float) fontThickness.getInput();
    }

    public static FeralFontRenderer getClickGuiHeaderFontRenderer() {
        return FontManager.getClickGuiHeaderRenderer(
                getSelectedFontName(), getSelectedFontSize(), getSelectedFontThickness());
    }

    public static FeralFontRenderer getClickGuiSettingFontRenderer() {
        return FontManager.getClickGuiSettingRenderer(
                getSelectedFontName(), getSelectedFontSize(), getSelectedFontThickness());
    }

    public static float getClickGuiScale() {
        double multiplier =
                guiScale == null ? 1.0 : Math.max(guiScale.getMin(), Math.min(guiScale.getMax(), guiScale.getInput()));
        return (float) multiplier;
    }

    /** True when the Gui opens the big cat window (Meower's catGUI design) instead of the ClickGUI. */
    public static boolean isBigCatStyle() {
        return style != null && style.getInput() == 1.0;
    }

    public static boolean isLiteModeEnabled() {
        return liteMode != null && liteMode.isToggled();
    }

    public static boolean shouldShowModule(Module module) {
        return module.moduleCategory() == Module.category.profiles
                || module.moduleCategory() == Module.category.scripts
                || !isLiteModeEnabled()
                || module.liteModule;
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == liteMode && Feral.clickGui != null) {
            Feral.clickGui.reloadModulesForCurrentMode();
        }
    }
}
