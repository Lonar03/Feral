// Layout, key types, custom keys and config format based on Sk1er LLC's Keystrokes mod (club.sk1er.mods.keystrokes,
// by Fyu, boomboompower and Sk1er), rewritten for Feral. Module input tracking is Feral's own.
package keystrokesmod.module.impl.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import keystrokesmod.helper.KeyInputTracker;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.RenderTickEvent;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class Keystrokes extends Module {
    public static final int TYPE_BAR = 0;
    public static final int TYPE_WIDE = 1;
    public static final int TYPE_SQUARE = 2;
    private static final String[] TYPE_NAMES = new String[] {"Bar", "Wide", "Square"};
    private static final int WIDTH = 74;
    private static final int KEY = 22; // square key size
    private static final int ROW = 16; // wide row height
    private static final long PRESS_FLASH_MS = 50L; // module clicks are often shorter than a frame
    private static final float DEFAULT_REL_X = 0.005F;
    private static final float DEFAULT_REL_Y = 0.01F;

    private final ButtonSetting physicalOnly;
    private final ButtonSetting countFastPlace;
    private final ButtonSetting showWasd;
    private final ButtonSetting showMouse;
    private final ButtonSetting cpsOnButtons;
    private final ButtonSetting showSneak;
    private final ButtonSetting showSpace;
    private final ButtonSetting showCps;
    private final SliderSetting cpsButton;
    private final ButtonSetting showFps;
    private final ButtonSetting showPing;
    private final ButtonSetting chroma;
    private final ColorSetting color;
    private final ColorSetting pressedTextColor;
    private final ButtonSetting background;
    private final ColorSetting backgroundColor;
    private final ColorSetting pressedBackgroundColor;
    private final SliderSetting fadeTime;
    private final SliderSetting scale;
    private final ButtonSetting literalKeys;
    private final ButtonSetting arrowKeys;

    private final List<CustomKey> customKeys = new ArrayList<>();
    private final KeyState[] wasd = new KeyState[] {new KeyState(), new KeyState(), new KeyState(), new KeyState()};
    private final KeyState[] mouse = new KeyState[] {new KeyState(), new KeyState()};
    private final KeyState sneaking = new KeyState();
    private final KeyState space = new KeyState();
    private float relX = DEFAULT_REL_X;
    private float relY = DEFAULT_REL_Y;

    public Keystrokes() {
        super("Keystrokes", Module.category.client);
        this.registerSetting(new DescriptionSetting("Customize keys with Edit layout"));
        this.registerSetting(new ButtonSetting("Edit layout", () -> mc.openScreen(new EditScreen())));
        this.registerSetting(this.physicalOnly = new ButtonSetting("Show physical only", false));
        this.registerSetting(this.countFastPlace = new ButtonSetting("Count Fast Place clicks", true));
        this.registerSetting(this.showWasd = new ButtonSetting("Show WASD", true));
        this.registerSetting(this.showMouse = new ButtonSetting("Show mouse buttons", true));
        this.registerSetting(this.cpsOnButtons = new ButtonSetting("CPS on buttons", true));
        this.registerSetting(this.showSneak = new ButtonSetting("Show sneak", true));
        this.registerSetting(this.showSpace = new ButtonSetting("Show spacebar", true));
        this.registerSetting(this.showCps = new ButtonSetting("Show CPS counter", false));
        this.registerSetting(this.cpsButton = new SliderSetting("Click counter", 0, new String[] {"Left", "Right"}));
        this.registerSetting(this.showFps = new ButtonSetting("Show FPS", true));
        this.registerSetting(this.showPing = new ButtonSetting("Show ping", false));
        this.registerSetting(this.chroma = new ButtonSetting("Chroma", false));
        this.registerSetting(this.color = new ColorSetting("Text color", 255, 255, 255));
        this.registerSetting(this.pressedTextColor = new ColorSetting("Pressed text color", 0, 0, 0));
        this.registerSetting(this.background = new ButtonSetting("Key background", true));
        this.registerSetting(this.backgroundColor = new ColorSetting("Background color", 0, 0, 0, 120));
        this.registerSetting(
                this.pressedBackgroundColor = new ColorSetting("Pressed background color", 255, 255, 255, 120));
        this.registerSetting(this.fadeTime = new SliderSetting("Fade time", "ms", 50.0, 0.0, 250.0, 5.0));
        this.registerSetting(this.scale = new SliderSetting("Scale", 1.0, 0.5, 1.5, 0.05));
        this.registerSetting(this.literalKeys = new ButtonSetting("Literal keys", false));
        this.registerSetting(this.arrowKeys = new ButtonSetting("Arrow keys", false));
        this.registerSetting(new ButtonSetting("Import Sk1er Keystrokes", this::importSk1erConfig));
    }

    @Override
    public void guiUpdate() {
        this.countFastPlace.setVisible(!this.physicalOnly.isToggled(), this);
        this.cpsButton.setVisible(this.showCps.isToggled() && !this.cpsOnButtons.isToggled(), this);
        this.cpsOnButtons.setVisible(this.showMouse.isToggled(), this);
        this.color.setVisible(!this.chroma.isToggled(), this);
        this.backgroundColor.setVisible(this.background.isToggled(), this);
        this.pressedBackgroundColor.setVisible(this.background.isToggled(), this);
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase == Phase.END
                && Utils.nullCheck()
                && mc.screen == null
                && mc.focused
                && !mc.options.debugEnabled) {
            KeyInputTracker.countFastRepeats = this.countFastPlace.isToggled();
            this.render(null);
        }
    }

    // ---------------------------------------------------------------- input

    private boolean isDown(int keyCode) {
        if (keyCode == 0) {
            return false;
        }

        if (this.physicalOnly.isToggled()) {
            return KeyInputTracker.isPhysicallyDown(keyCode);
        }

        // Bound keys follow the binding state, so a module holding/releasing the key (AutoClicker releases attack
        // while you hold LMB) shows what the game actually gets.
        return KeyInputTracker.isBindingOrPhysicalDown(keyCode)
                || System.currentTimeMillis() - KeyInputTracker.getLastPress(keyCode) < PRESS_FLASH_MS
                || this.isModuleHeld(keyCode);
    }

    /**
     * Keys a module holds without touching the key binding: movement it adds to your input (WASD, jump, sneak)
     * and items it keeps in use (AutoAim drawing a bow, autoblock) for the use key.
     */
    private boolean isModuleHeld(int keyCode) {
        if (mc.player == null) {
            return false;
        }

        Input input = mc.player.input;
        GameOptions settings = mc.options;
        if (keyCode == settings.useKey.getKeyCode()) {
            return mc.player.hasItemInUse();
        }

        if (input == null) {
            return false;
        }

        return keyCode == settings.forwardKey.getKeyCode() && input.movementForward > 0.0F
                || keyCode == settings.backKey.getKeyCode() && input.movementForward < 0.0F
                || keyCode == settings.leftKey.getKeyCode() && input.movementSideways > 0.0F
                || keyCode == settings.rightKey.getKeyCode() && input.movementSideways < 0.0F
                || keyCode == settings.jumpKey.getKeyCode() && input.jumping
                || keyCode == settings.sneakKey.getKeyCode() && input.sneaking;
    }

    private int getCps(boolean left) {
        KeyBinding binding = left ? mc.options.attackKey : mc.options.useKey;
        return KeyInputTracker.getCps(binding.getKeyCode(), this.physicalOnly.isToggled());
    }

    // ---------------------------------------------------------------- layout & render

    private float getScale() {
        return (float) this.scale.getInput();
    }

    /** Bottom of the built-in rows in overlay space; custom key offsets are relative to (0, this). */
    private int getRowsEnd() {
        int y = 2;
        if (this.showWasd.isToggled()) y += 48;
        boolean mouse = this.showMouse.isToggled();
        if (mouse) y += 24;
        if (this.showSneak.isToggled()) y += ROW + 2;
        if (this.showSpace.isToggled()) y += ROW + 2;
        if (this.showCps.isToggled() && !(mouse && this.cpsOnButtons.isToggled())) y += ROW + 2;
        if (this.showFps.isToggled()) y += ROW + 2;
        if (this.showPing.isToggled()) y += ROW + 2;
        return y;
    }

    /** Overlay-space bounds of everything drawn: {minX, minY, maxX, maxY}. */
    private float[] getLocalBounds() {
        int base = this.getRowsEnd();
        float minX = 0, minY = 0, maxX = WIDTH, maxY = Math.max(base, 4);
        for (CustomKey key : this.customKeys) {
            minX = Math.min(minX, (float) key.x);
            minY = Math.min(minY, (float) (base + key.y));
            maxX = Math.max(maxX, (float) key.x + key.getWidth());
            maxY = Math.max(maxY, (float) (base + key.y) + key.getHeight());
        }

        return new float[] {minX, minY, maxX, maxY};
    }

    /** Screen position of the overlay origin, kept on screen. */
    private float[] getScreenOrigin(Window res) {
        float s = this.getScale();
        return this.getScreenOrigin(res, s, this.getLocalBounds());
    }

    private float[] getScreenOrigin(Window res, float s, float[] b) {
        float x = this.relX * res.getWidth();
        float y = this.relY * res.getHeight();
        x = Math.max(-b[0] * s, Math.min(x, res.getWidth() - b[2] * s));
        y = Math.max(-b[1] * s, Math.min(y, res.getHeight() - b[3] * s));
        return new float[] {x, y};
    }

    private void render(CustomKey selected) {
        Window res = new Window(mc);
        float[] origin = this.getScreenOrigin(res);
        float s = this.getScale();
        GlStateManager.pushMatrix();
        GlStateManager.translatef(origin[0], origin[1], 0.0F);
        GlStateManager.scalef(s, s, 1.0F);
        GlStateManager.enableBlend();

        int y = 2;
        if (this.showWasd.isToggled()) {
            KeyBinding[] keys =
                    new KeyBinding[] {mc.options.forwardKey, mc.options.leftKey, mc.options.backKey, mc.options.rightKey
                    };
            int[][] vertex = new int[][] {{26, 2}, {2, 26}, {26, 26}, {50, 26}};
            int[] arrowAngles = new int[] {0, -90, 180, 90};
            for (int i = 0; i < 4; i++) {
                int code = keys[i].getKeyCode();
                float t = this.wasd[i].update(this.isDown(code), this.getFadeMs());
                this.drawBackgroundTexture(vertex[i][0], vertex[i][1], KEY, KEY, t);
                if (this.arrowKeys.isToggled()) {
                    this.drawArrow(vertex[i][0], vertex[i][1], arrowAngles[i], t);
                } else {
                    this.drawLabel(this.getKeyName(code), vertex[i][0], vertex[i][1], KEY, KEY, t);
                }
            }

            y += 48;
        }

        if (this.showMouse.isToggled()) {
            for (int i = 0; i < 2; i++) {
                KeyBinding binding = i == 0 ? mc.options.attackKey : mc.options.useKey;
                float t = this.mouse[i].update(this.isDown(binding.getKeyCode()), this.getFadeMs());
                int x = i == 0 ? 2 : 38;
                this.drawBackgroundTexture(x, y, 34, KEY, t);
                String name = i == 0 ? "LMB" : "RMB";
                if (this.cpsOnButtons.isToggled() && this.showCps.isToggled()) {
                    this.drawLabel(name, x, y - 4, 34, KEY, t);
                    GlStateManager.pushMatrix();
                    GlStateManager.translatef(x + 17, y + 14, 0.0F);
                    GlStateManager.scalef(0.5F, 0.5F, 1.0F);
                    String cps = this.getCps(i == 0) + " CPS";
                    this.drawText(cps, -mc.textRenderer.getWidth(cps) / 2, 0, t);
                    GlStateManager.popMatrix();
                } else {
                    this.drawLabel(name, x, y, 34, KEY, t);
                }
            }

            y += 24;
        }

        if (this.showSneak.isToggled()) {
            float t = this.sneaking.update(this.isDown(mc.options.sneakKey.getKeyCode()), this.getFadeMs());
            this.drawBackgroundTexture(2, y, 70, ROW, t);
            this.drawLabel("Sneak", 2, y, 70, ROW, t);
            y += ROW + 2;
        }

        if (this.showSpace.isToggled()) {
            float t = this.space.update(this.isDown(mc.options.jumpKey.getKeyCode()), this.getFadeMs());
            this.drawBackgroundTexture(2, y, 70, ROW, t);
            this.drawBar(2, y, 70, ROW, t);
            y += ROW + 2;
        }

        if (this.showCps.isToggled() && !(this.showMouse.isToggled() && this.cpsOnButtons.isToggled())) {
            this.drawBackgroundTexture(2, y, 70, ROW, 0.0F);
            this.drawLabel(this.getCps((int) this.cpsButton.getInput() == 0) + " CPS", 2, y, 70, ROW, 0.0F);
            y += ROW + 2;
        }

        if (this.showFps.isToggled()) {
            this.drawBackgroundTexture(2, y, 70, ROW, 0.0F);
            this.drawLabel(Minecraft.getCurrentFps() + " FPS", 2, y, 70, ROW, 0.0F);
            y += ROW + 2;
        }

        if (this.showPing.isToggled()) {
            this.drawBackgroundTexture(2, y, 70, ROW, 0.0F);
            this.drawLabel(getPingText(), 2, y, 70, ROW, 0.0F);
            y += ROW + 2;
        }

        for (CustomKey key : this.customKeys) {
            int kx = (int) Math.round(key.x);
            int ky = y + (int) Math.round(key.y);
            int w = key.getWidth();
            int h = key.getHeight();
            if (key == selected) {
                GuiElement.fill(kx - 1, ky - 1, kx + w + 1, ky + h + 1, -1);
            }

            float t = key.state.update(this.isDown(key.key), this.getFadeMs());
            this.drawBackgroundTexture(kx, ky, w, h, t);
            if (key.type == TYPE_BAR) {
                this.drawBar(kx, ky, w, h, t);
            } else {
                this.drawLabel(this.getKeyName(key.key), kx, ky, w, h, t);
            }
        }

        GlStateManager.popMatrix();
    }

    private static String getPingText() {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler != null && mc.player != null) {
            PlayerInfo info = handler.getOnlinePlayer(mc.player.getUuid());
            if (info != null) {
                return info.getPing() + "ms";
            }
        }

        return "Unknown";
    }

    private double getFadeMs() {
        return this.fadeTime.getInput();
    }

    private void drawBackgroundTexture(int x, int y, int w, int h, float t) {
        if (this.background.isToggled()) {
            int color = lerpColor(this.backgroundColor.getColor(), this.pressedBackgroundColor.getColor(), t);
            GuiElement.fill(x, y, x + w, y + h, color);
        }
    }

    private int getTextColor(float t, int x, int y) {
        int base = this.chroma.isToggled() ? getChroma(x, y) : this.color.getRGB() | 0xFF000000;
        return lerpColor(base, this.pressedTextColor.getRGB() | 0xFF000000, t);
    }

    private void drawText(String text, int x, int y, float t) {
        TextRenderer font = mc.textRenderer;
        if (this.chroma.isToggled() && t < 1.0F) {
            int cx = x;
            for (char c : text.toCharArray()) {
                String ch = String.valueOf(c);
                font.draw(ch, cx, y, this.getTextColor(t, cx, y), false);
                cx += font.getWidth(ch);
            }
        } else {
            font.draw(text, x, y, this.getTextColor(t, x, y), false);
        }
    }

    private void drawLabel(String text, int x, int y, int w, int h, float t) {
        TextRenderer font = mc.textRenderer;
        int textWidth = font.getWidth(text);
        float fit = textWidth > w - 2 ? (float) (w - 2) / textWidth : 1.0F;
        int textY = y + (h - 7) / 2 + 1;
        if (fit < 1.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translatef(x + w / 2.0F, textY + 4.0F, 0.0F);
            GlStateManager.scalef(fit, fit, 1.0F);
            this.drawText(text, -textWidth / 2, -4, t);
            GlStateManager.popMatrix();
        } else {
            this.drawText(text, x + (w - textWidth) / 2, textY, t);
        }
    }

    /** The struck-through line of the spacebar. */
    private void drawBar(int x, int y, int w, int h, float t) {
        int lineWidth = Math.min(36, w - 6);
        int left = x + (w - lineWidth) / 2;
        int top = y + h / 2;
        GuiElement.fill(left, top, left + lineWidth, top + 1, this.getTextColor(t, left, top));
    }

    private void drawArrow(int x, int y, int angle, float t) {
        float cx = x + KEY / 2.0F;
        float cy = y + KEY / 2.0F;
        int color = this.getTextColor(t, (int) cx, (int) cy);
        GlStateManager.pushMatrix();
        GlStateManager.translatef(cx, cy, 0.0F);
        GlStateManager.rotatef(angle, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, 1.0F);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormat.POSITION);
        wr.vertex(0.0, -6.0, 0.0).nextVertex();
        wr.vertex(-6.0, 6.0, 0.0).nextVertex();
        wr.vertex(6.0, 6.0, 0.0).nextVertex();
        tessellator.end();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture();
        GlStateManager.popMatrix();
    }

    private static int getChroma(int x, int y) {
        float hue = (float) ((System.currentTimeMillis() - x * 10L - y * 10L) % 2000L) / 2000.0F;
        return Color.HSBtoRGB(hue, 0.8F, 0.8F);
    }

    private static int lerpColor(int from, int to, float t) {
        if (t <= 0.0F) return from;
        if (t >= 1.0F) return to;
        int a = lerp(from >>> 24, to >>> 24, t);
        int r = lerp(from >> 16 & 255, to >> 16 & 255, t);
        int g = lerp(from >> 8 & 255, to >> 8 & 255, t);
        int b = lerp(from & 255, to & 255, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    String getKeyName(int keyCode) {
        if (keyCode < 0) {
            int button = keyCode + 100;
            if (button == 0) return "LMB";
            if (button == 1) return "RMB";
            if (button == 2) return "MMB";
            return "M" + (button + 1);
        }

        if (keyCode == 0) {
            return "-";
        }

        if (this.literalKeys.isToggled()) {
            switch (keyCode) {
                case Keyboard.KEY_MINUS:
                case Keyboard.KEY_SUBTRACT:
                    return "-";
                case Keyboard.KEY_EQUALS:
                    return "=";
                case Keyboard.KEY_LBRACKET:
                    return "[";
                case Keyboard.KEY_RBRACKET:
                    return "]";
                case Keyboard.KEY_SEMICOLON:
                    return ";";
                case Keyboard.KEY_APOSTROPHE:
                    return "'";
                case Keyboard.KEY_GRAVE:
                    return "~";
                case Keyboard.KEY_BACKSLASH:
                    return "\\";
                case Keyboard.KEY_COMMA:
                    return ",";
                case Keyboard.KEY_PERIOD:
                    return ".";
                case Keyboard.KEY_SLASH:
                case Keyboard.KEY_DIVIDE:
                    return "/";
                case Keyboard.KEY_MULTIPLY:
                    return "*";
                case Keyboard.KEY_ADD:
                    return "+";
                case Keyboard.KEY_UP:
                    return "▲";
                case Keyboard.KEY_LEFT:
                    return "◀";
                case Keyboard.KEY_RIGHT:
                    return "▶";
                case Keyboard.KEY_DOWN:
                    return "▼";
            }
        }

        String name = Keyboard.getKeyName(keyCode);
        return name == null ? "#" + keyCode : name;
    }

    // ---------------------------------------------------------------- profile data

    public void saveProfileData(JsonObject json) {
        json.addProperty("relPosX", this.relX);
        json.addProperty("relPosY", this.relY);
        JsonArray keys = new JsonArray();
        for (CustomKey key : this.customKeys) {
            JsonObject obj = new JsonObject();
            obj.addProperty("key", key.key);
            obj.addProperty("type", key.type);
            obj.addProperty("x", key.x);
            obj.addProperty("y", key.y);
            keys.add(obj);
        }

        json.add("customKeys", keys);
    }

    public void loadProfileData(JsonObject json) {
        if (json.has("relPosX") && json.has("relPosY")) {
            this.relX = json.get("relPosX").getAsFloat();
            this.relY = json.get("relPosY").getAsFloat();
        }

        if (json.has("customKeys") && json.get("customKeys").isJsonArray()) {
            this.customKeys.clear();
            for (JsonElement element : json.getAsJsonArray("customKeys")) {
                JsonObject obj = element.getAsJsonObject();
                this.customKeys.add(new CustomKey(
                        obj.get("key").getAsInt(),
                        obj.get("type").getAsInt(),
                        obj.get("x").getAsDouble(),
                        obj.get("y").getAsDouble()));
            }
        }
    }

    // ---------------------------------------------------------------- Sk1er import

    /** Imports config/keystrokes.json from Sk1er's Keystrokes mod (look, position and custom keys). */
    private void importSk1erConfig() {
        File file = new File(new File(mc.gameDir, "config"), "keystrokes.json");
        if (!file.isFile()) {
            Utils.sendMessage("&cNo Keystrokes config found at &7config/keystrokes.json");
            return;
        }

        JsonObject json;
        try (Reader reader = new FileReader(file)) {
            json = new JsonParser().parse(reader).getAsJsonObject();
        } catch (Exception e) {
            Utils.sendMessage("&cCouldn't read config/keystrokes.json: " + e.getMessage());
            return;
        }

        boolean wasd = optBoolean(json, "showWASD", true);
        boolean mouseButtons = optBoolean(json, "mouseButtons", false);
        boolean cps = optBoolean(json, "showCPS", false);
        boolean onButtons = optBoolean(json, "showCPSOnButtons", false);
        boolean spaceBar = optBoolean(json, "showSpacebar", false);
        boolean sneakRow = optBoolean(json, "showSneak", false);
        boolean fps = optBoolean(json, "fps", false);
        boolean ping = optBoolean(json, "ping", false);
        this.showWasd.setEnabled(wasd);
        this.showMouse.setEnabled(mouseButtons);
        this.showCps.setEnabled(cps);
        this.cpsOnButtons.setEnabled(onButtons);
        this.showSpace.setEnabled(spaceBar);
        this.showSneak.setEnabled(sneakRow);
        this.showFps.setEnabled(fps);
        this.showPing.setEnabled(ping);
        this.cpsButton.setValue(optBoolean(json, "leftClick", true) ? 0 : 1);
        this.chroma.setEnabled(optBoolean(json, "chroma", false));
        this.background.setEnabled(optBoolean(json, "keyBackground", true));
        this.literalKeys.setEnabled(optBoolean(json, "literalKeys", false));
        this.arrowKeys.setEnabled(optBoolean(json, "arrowKeys", false));
        this.color.setColor(optInt(json, "red", 255), optInt(json, "green", 255), optInt(json, "blue", 255));
        this.pressedTextColor.setColor(
                optInt(json, "pressedRed", 0), optInt(json, "pressedGreen", 0), optInt(json, "pressedBlue", 0));
        int bgR = optInt(json, "keyBackgroundRed", 0);
        int bgG = optInt(json, "keyBackgroundGreen", 0);
        int bgB = optInt(json, "keyBackgroundBlue", 0);
        int bgA = optInt(json, "keyBackgroundOpacity", 120);
        this.backgroundColor.setColor(bgR, bgG, bgB, bgA);
        // Sk1er only lights the background up (towards white) when it is pure black.
        if (bgR == 0 && bgG == 0 && bgB == 0) {
            this.pressedBackgroundColor.setColor(255, 255, 255, bgA);
        } else {
            this.pressedBackgroundColor.setColor(bgR, bgG, bgB, bgA);
        }

        double sk1erScale = Math.max(0.5, Math.min(1.5, optDouble(json, "scale", 1.0)));
        this.scale.setValue(sk1erScale);
        double sk1erFade = Math.max(0.1, Math.min(3.0, optDouble(json, "fadeTime", 1.0)));
        this.fadeTime.setValue(Math.round(51.0 / sk1erFade / 5.0) * 5.0); // Sk1er fades fully in 51ms / fadeTime

        Window res = new Window(mc);
        this.relX = (float) (Math.max(0, optInt(json, "x", 0)) * sk1erScale / res.getWidth());
        this.relY = (float) (Math.max(0, optInt(json, "y", 0)) * sk1erScale / res.getHeight());

        // Sk1er places custom keys from a base that sits 16px above the end of the rows (30px lower without WASD).
        double yShift = -16 + (wasd ? 0 : 30);
        this.customKeys.clear();
        if (json.has("custom") && json.get("custom").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("custom")) {
                JsonObject obj = element.getAsJsonObject();
                this.customKeys.add(new CustomKey(
                        optInt(obj, "key", 0),
                        optInt(obj, "type", TYPE_SQUARE),
                        optDouble(obj, "xOffset", 0),
                        optDouble(obj, "yOffset", 0) + yShift));
            }
        }

        Utils.sendMessage("&aImported Keystrokes config &7(" + this.customKeys.size()
                + " custom keys). Save your profile to keep it.");
    }

    private static boolean optBoolean(JsonObject json, String key, boolean def) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : def;
    }

    private static int optInt(JsonObject json, String key, int def) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : def;
    }

    private static double optDouble(JsonObject json, String key, double def) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : def;
    }

    // ---------------------------------------------------------------- types

    /** Fade state of one key: returns 0 (released look) .. 1 (pressed look). */
    private static final class KeyState {
        private boolean pressed;
        private long changedAt;

        float update(boolean down, double fadeMs) {
            long now = System.currentTimeMillis();
            if (down != this.pressed) {
                this.pressed = down;
                this.changedAt = now;
            }

            float progress = fadeMs <= 0.0 ? 1.0F : (float) Math.min(1.0, (now - this.changedAt) / fadeMs);
            return this.pressed ? progress : 1.0F - progress;
        }
    }

    private static final class CustomKey {
        private int key;
        private int type;
        private double x;
        private double y;
        private final KeyState state = new KeyState();

        private CustomKey(int key, int type, double x, double y) {
            this.key = key;
            this.type = type < TYPE_BAR || type > TYPE_SQUARE ? TYPE_SQUARE : type;
            this.x = x;
            this.y = y;
        }

        int getWidth() {
            return this.type == TYPE_SQUARE ? KEY : 70;
        }

        int getHeight() {
            return this.type == TYPE_SQUARE ? KEY : ROW;
        }
    }

    // ---------------------------------------------------------------- edit screen

    private class EditScreen extends Screen {
        private ButtonWidget changeKey;
        private ButtonWidget changeType;
        private ButtonWidget delete;
        private CustomKey selected;
        private CustomKey draggingKey;
        private boolean draggingOverlay;
        private boolean listening;
        private int lastMouseX;
        private int lastMouseY;

        @Override
        public void init() {
            this.buttons.clear();
            int cx = this.width / 2 - 100;
            int cy = this.height / 2 - 44;
            this.buttons.add(new ButtonWidget(1, cx, cy, 200, 20, "Add key"));
            this.buttons.add(this.changeKey = new ButtonWidget(2, cx, cy + 22, 200, 20, "Change key"));
            this.buttons.add(this.changeType = new ButtonWidget(3, cx, cy + 44, 200, 20, "Change type"));
            this.buttons.add(this.delete = new ButtonWidget(4, cx, cy + 66, 200, 20, "Delete key"));
            this.buttons.add(new ButtonWidget(5, this.width - 90, this.height - 25, 85, 20, "Reset position"));
        }

        @Override
        public void render(int mouseX, int mouseY, float partialTicks) {
            this.renderBackground();
            boolean hasSelection = this.selected != null;
            this.changeKey.active = hasSelection;
            this.changeType.active = hasSelection;
            this.delete.active = hasSelection;
            this.changeKey.message = this.listening ? "Press a key... (Esc cancels)" : "Change key";
            if (hasSelection) {
                this.changeType.message = "Type: " + TYPE_NAMES[this.selected.type];
            } else {
                this.changeType.message = "Change type";
            }

            Keystrokes.this.render(this.selected);
            float[] box = this.getScreenBounds();
            int outline = 0x80FFFFFF;
            GuiElement.fill((int) box[0] - 1, (int) box[1] - 1, (int) box[2] + 1, (int) box[1], outline);
            GuiElement.fill((int) box[0] - 1, (int) box[3], (int) box[2] + 1, (int) box[3] + 1, outline);
            GuiElement.fill((int) box[0] - 1, (int) box[1], (int) box[0], (int) box[3], outline);
            GuiElement.fill((int) box[2], (int) box[1], (int) box[2] + 1, (int) box[3], outline);
            this.drawCenteredString(
                    this.textRenderer,
                    "Drag the overlay to move it, click a custom key to select and drag it",
                    this.width / 2,
                    this.height / 2 - 60,
                    0xFFAAAAAA);
            super.render(mouseX, mouseY, partialTicks);
        }

        private float[] getScreenBounds() {
            Window res = new Window(this.minecraft);
            float s = Keystrokes.this.getScale();
            float[] b = Keystrokes.this.getLocalBounds();
            float[] origin = Keystrokes.this.getScreenOrigin(res, s, b);
            return new float[] {origin[0] + b[0] * s, origin[1] + b[1] * s, origin[0] + b[2] * s, origin[1] + b[3] * s};
        }

        private CustomKey keyAt(int mouseX, int mouseY) {
            Window res = new Window(this.minecraft);
            float[] origin = Keystrokes.this.getScreenOrigin(res);
            float s = Keystrokes.this.getScale();
            int base = Keystrokes.this.getRowsEnd();
            for (int i = Keystrokes.this.customKeys.size() - 1; i >= 0; i--) {
                CustomKey key = Keystrokes.this.customKeys.get(i);
                float left = origin[0] + (float) key.x * s;
                float top = origin[1] + (float) (base + key.y) * s;
                if (mouseX >= left
                        && mouseX <= left + key.getWidth() * s
                        && mouseY >= top
                        && mouseY <= top + key.getHeight() * s) {
                    return key;
                }
            }

            return null;
        }

        @Override
        public void handleMouse() {
            if (this.listening && Mouse.getEventButtonState() && Mouse.getEventButton() >= 0) {
                this.selected.key = Mouse.getEventButton() - 100;
                this.listening = false;
                return;
            }

            super.handleMouse();
        }

        @Override
        protected void keyPressed(char typedChar, int keyCode) {
            if (this.listening) {
                if (keyCode != Keyboard.KEY_ESCAPE && keyCode != 0) {
                    this.selected.key = keyCode;
                }

                this.listening = false;
                return;
            }

            super.keyPressed(typedChar, keyCode);
        }

        @Override
        protected void buttonClicked(ButtonWidget button) {
            switch (button.id) {
                case 1: {
                    CustomKey key = new CustomKey(Keyboard.KEY_E, TYPE_SQUARE, 2, 2);
                    Keystrokes.this.customKeys.add(key);
                    this.selected = key;
                    break;
                }
                case 2:
                    this.listening = this.selected != null;
                    break;
                case 3:
                    if (this.selected != null) {
                        this.selected.type = (this.selected.type + 1) % TYPE_NAMES.length;
                    }
                    break;
                case 4:
                    Keystrokes.this.customKeys.remove(this.selected);
                    this.selected = null;
                    break;
                case 5:
                    Keystrokes.this.relX = DEFAULT_REL_X;
                    Keystrokes.this.relY = DEFAULT_REL_Y;
                    break;
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            for (ButtonWidget button : this.buttons) {
                if (button.mouseClicked(this.minecraft, mouseX, mouseY)) {
                    super.mouseClicked(mouseX, mouseY, mouseButton);
                    return;
                }
            }

            if (mouseButton != 0) {
                return;
            }

            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            CustomKey key = this.keyAt(mouseX, mouseY);
            if (key != null) {
                this.selected = key;
                this.draggingKey = key;
                return;
            }

            float[] box = this.getScreenBounds();
            this.selected = null;
            if (mouseX >= box[0] && mouseX <= box[2] && mouseY >= box[1] && mouseY <= box[3]) {
                this.draggingOverlay = true;
            }
        }

        @Override
        protected void mouseDragged(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
            int dx = mouseX - this.lastMouseX;
            int dy = mouseY - this.lastMouseY;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            if (this.draggingKey != null) {
                float s = Keystrokes.this.getScale();
                this.draggingKey.x += dx / s;
                this.draggingKey.y += dy / s;
            } else if (this.draggingOverlay) {
                Window res = new Window(this.minecraft);
                float[] origin = Keystrokes.this.getScreenOrigin(res);
                Keystrokes.this.relX = (origin[0] + dx) / res.getWidth();
                Keystrokes.this.relY = (origin[1] + dy) / res.getHeight();
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (this.draggingKey != null) {
                this.draggingKey.x = Math.round(this.draggingKey.x);
                this.draggingKey.y = Math.round(this.draggingKey.y);
            }

            this.draggingKey = null;
            this.draggingOverlay = false;
        }

        @Override
        public boolean shouldPauseGame() {
            return false;
        }
    }
}
