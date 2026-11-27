package keystrokesmod.clickgui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import keystrokesmod.Feral;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.clickgui.components.impl.ModuleComponent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.script.ScriptDefaults;
import keystrokesmod.utility.BlockSearchIndex;
import keystrokesmod.utility.CatRenderer;
import keystrokesmod.utility.ItemSearchIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Window;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The "Big cat" Gui style: Meower's cat window GUI (design concept 2), ported from the catGUI script. A black cat
 * holds the window: its head looks over the top, front paws grip the top edge, back paws stand under it and its
 * fluffy tail wraps the right side. While the style is selected it also draws its own notifications and arraylist.
 *
 * <p>Written against the script API ({@link ScriptDefaults}), so it stays close to the script it came from. Its
 * settings live in the {@link Gui} module; its colours come from the selected category's
 * {@link CatColors}. {@link BigCatEvents}
 * drives it.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class BigCatGui extends ScriptDefaults {
    public static final BigCatGui INSTANCE = new BigCatGui();
    /** The window, notification and arraylist settings live in the Gui module (read by name). */
    private static final String scriptName = "Gui";

    private static final ScriptDefaults.modules modules = new ScriptDefaults.modules(scriptName);
    private static final String[] LOGO_LETTERS = {"F", "e", "r", "a", "l"};

    private BigCatGui() {}

    /** Opens the window on the next frame (called by the Gui module in the Big cat style). */
    public void requestOpen() {
        this.pendingOpen = true;
    }

    public boolean isOpen() {
        return this.open;
    }

    /** True while the Gui module's bind (a key, or a mouse button stored as 1000 + button) is held. */
    private static boolean openKeyDown() {
        Module gui = ModuleManager.getModule(Gui.class);
        int code = gui == null ? Gui.DEFAULT_KEY : gui.getKeycode();
        if (code == 1069 || code == 1070) {
            return false; // A wheel bind is an event, not a held button.
        }
        if (code >= 1000) {
            return Mouse.isButtonDown(code - 1000);
        }

        return code > 0 && Keyboard.isKeyDown(code);
    }

    private static final String S_ALPHA = "Opacity";

    private static final int NOTIF_MAX = 6;
    private static final long NOTIF_LIFE = 1200L;
    private static final int C_NOTIF_BG = 0xFF0C0C0E;
    private static final int C_MODERN_BG = 0xFF090A0D;
    private static final int C_ON = 0xFF4CD778;
    private static final int C_OFF = 0xFFEF5350;
    private static final int C_DARK = 0xFF0E0E0E;
    private static final int C_TOGGLE_OFF = 0xFF070707;
    private static final int C_KNOB_OFF = 0xFF555555;
    private static final int C_LINE = 0x15FFFFFF;
    private static final int C_HOVER = 0x30FFFFFF;
    private static final int C_WHITE = 0xFFFFFFFF;

    /* Layout, in scaled units (multiply by s). */
    private static final float SIDEBAR_W = 116f;
    private static final float CARD_HEAD_H = 31f;
    private static final float RESIZE_HANDLE = 22f;
    private static final float SCROLLBAR_W = 3f;

    /* Expanded colour picker: sat/bri square plus hue and alpha bars. */
    private static final float PICKER_H = 50f;
    private static final float PICKER_BAR_W = 10f;
    private static final float PICKER_GAP = 4f;
    private static final float PICKER_PAD_TOP = 3f;
    private static final float PICKER_PAD_BOTTOM = 5f;
    private static final float PICKER_R = 3f;
    private static final float PICKER_CHECK = 4f;

    /* List settings. */
    private static final float LIST_ROW_H = 16f;
    private static final float LIST_PAD = 3f;
    private static final int LIST_MAX_ROWS = 5;

    /* --------------------------------------------------------------- menu state */
    private boolean open = false;
    private boolean closing = false;
    private float menuAnim = 0f;
    private float fadeMul = 1f;
    private boolean inMenu = false;

    /* ---------------------------------------------------------- selection state */
    private Module.category tab = Module.category.combat;
    private Module.category lastSelTab = tab;
    private float selAnim = 1f;
    private float sideScroll = 0f;
    private final EnumMap<Module.category, Float> categoryScroll = new EnumMap<>(Module.category.class);
    private float searchScroll;
    private String searchText = "";
    private boolean searchFocused = false;

    private final HashSet expandedCards = new HashSet();
    private final HashSet expandedModes = new HashSet();
    private final Map<ColorSetting, ColorPickerState> pickers = new WeakHashMap<>();
    private final Map<Object, Integer> uiIds = new WeakHashMap<>();
    private final Map<Module, List<Setting>> frameSettings = new IdentityHashMap<>();
    private int nextUiId;

    /* Keyed animations, each advanced at most once per frame. */
    private final KeyedAnim cardAnim = new KeyedAnim();
    private final KeyedAnim toggleAnim = new KeyedAnim();
    private final KeyedAnim groupAnim = new KeyedAnim();
    private final KeyedAnim modeAnim = new KeyedAnim();
    private final KeyedAnim listAnim = new KeyedAnim();
    private final HashMap arrayAnim = new HashMap();
    private float arrayListAnim = 0f;
    private float arrayInfoAnim = 0f;
    private int lastArrayCorner = Integer.MIN_VALUE;
    private int frameId = 0;

    /* ---------------------------------------------------------- list settings */
    private String listSearchKey = null;
    private keystrokesmod.module.Module listSearchMod = null;
    private keystrokesmod.module.setting.Setting listSearchSetting = null;
    private String listQuery = "";
    private String listRowsKey = null;
    private ArrayList listDropRows = new ArrayList();
    private String listExpandedGroup = null;
    private String listExpandedLabel = null;
    private String listExpandedAll = null;
    private ArrayList listExpandedVariants = new ArrayList();
    private float listDropScroll = 0f;
    private boolean listOpen = false;
    private final HashMap listSelScroll = new HashMap();
    private boolean sawListFocus;

    /* --------------------------------------------------------- notifications */
    private final String[] notifText = new String[NOTIF_MAX];
    private final boolean[] notifOn = new boolean[NOTIF_MAX];
    private final long[] notifStart = new long[NOTIF_MAX];
    private final float[] notifY = new float[NOTIF_MAX];
    private int notifCount = 0;
    private final java.util.IdentityHashMap prevEnabled = new java.util.IdentityHashMap();
    private boolean enabledInit = false;

    /* ------------------------------------------------------------------ timing */
    private long lastFrameTime = 0L;
    private float frameDt = 0f;
    private long lastToggleTime = 0L;

    /* --------------------------------------------------------- window geometry */
    private float winX = 0f;
    private float winY = 0f;
    private float winW = 540f;
    private float winH = 410f;
    private float winOffX = 0f;
    private float winOffY = 0f;
    private float dragLastX = 0f;
    private float dragLastY = 0f;
    private float sideW = SIDEBAR_W;
    private boolean winDragging = false;
    private float resizeGrabX = 0f;
    private float resizeGrabY = 0f;
    private float resizeAnchorX;
    private float resizeAnchorY;
    private float resizeLastX;
    private float resizeLastY;
    private boolean resizing = false;

    /* Scrollable content area. */
    private float listX = 0f;
    private float listY = 0f;
    private float listW = 0f;
    private float listH = 0f;
    private boolean clipping = false;
    private final ScrollDrag sideDrag = new ScrollDrag();
    private final ScrollDrag contentDrag = new ScrollDrag();

    /* The active scissor rect, so nested clips can be intersected and restored. */
    private float clipRectX = 0f;
    private float clipRectY = 0f;
    private float clipRectW = 0f;
    private float clipRectH = 0f;

    /* Active keybind / text / slider / colour interaction. */
    private keystrokesmod.module.Module bindModule = null;
    private keystrokesmod.module.setting.impl.KeySetting bindKey = null;
    private keystrokesmod.module.setting.impl.TextSetting editing = null;
    private Module editingModule;
    private String editBuffer = "";
    private boolean sawEdit;
    private keystrokesmod.module.setting.impl.SliderSetting dragSlider = null;
    private keystrokesmod.module.setting.impl.ColorSetting dragColorSetting = null;
    private int dragColorPart = -1;
    private boolean sawColorDrag;

    /* ----------------------------------------------------- input for this frame */
    private boolean leftDown = false;
    private boolean pressLeft = false;
    private boolean pressRight = false;
    private boolean releaseLeft = false;
    private float releaseX;
    private float releaseY;
    private boolean prevOpenKey = false;
    private int frameWheel = 0;
    private final PointerInput pointer = new PointerInput();
    private final ArrayList<KeyInput> queuedKeys = new ArrayList<>();
    private float mx = 0f;
    private float my = 0f;
    private float scaledW = 1f;
    private float scaledH = 1f;
    private int scale = 1;
    private int framebufferHeight;

    /* ---------------------------------------------------- ClickGui juggling */
    private boolean pendingOpen = false;
    private long logoOpenedAt;
    private final Screen screen = new Screen() {
        @Override
        public void handleMouse() {
            if (closing) {
                return;
            }
            int button = Mouse.getEventButton();
            boolean down = Mouse.getEventButtonState();
            int wheel = Mouse.getEventDWheel();
            if (down && button >= 2 && button <= 4 && (bindModule != null || bindKey != null)) {
                applyBind(1000 + button);
            } else if (wheel != 0 && (bindModule != null || bindKey != null)) {
                applyBind(wheel > 0 ? 1069 : 1070);
                return;
            } else if (wheel != 0 && !searchFocused && editing == null && !listOpen) {
                Module gui = ModuleManager.getModule(Gui.class);
                if (gui != null
                        && gui.getKeycode() == (wheel > 0 ? 1069 : 1070)
                        && client.time() - lastToggleTime > 150L) {
                    lastToggleTime = client.time();
                    startClose();
                    return;
                }
            }
            pointer.accept(button, down, Mouse.getEventX(), Mouse.getEventY(), wheel);
        }

        @Override
        public void keyPressed(char typedChar, int keyCode) {
            if (!closing && !Keyboard.isRepeatEvent()) {
                queuedKeys.add(new KeyInput(typedChar, keyCode));
            }
        }
    };

    /* ------------------------------------------ theme, resolved once per frame */
    private float s = 1f;
    private int accent = 0xFF1AA879;
    private int guiAlpha = 255;
    private int cardAlpha = 255;
    private int settingsAlpha = 255;
    private int switchAlpha = 255;
    private float onTintFrac = 1f;
    private int cPanel = 0xFF111111;
    private int cField = 0xFF111111;
    private int cCard = 0xFF1A1A1A;
    private int cCardHover = 0xFF202020;
    private int cRaised = 0xFF2A2A2A;
    private int cText = 0xFFF0F0F0;
    private int cTextDim = 0xFF9A9A9A;
    private int cTextFaint = 0xFF6A6A6A;
    private String fontFamily = "Crafty Girls";
    private float fontSizeMul = 1f;
    private float fontThickness = 0f;
    private float menuFontScale = 1f;
    private float settledControlScale;
    private keystrokesmod.utility.font.FeralFontRenderer fBig = null;
    private keystrokesmod.utility.font.FeralFontRenderer fMid = null;
    private keystrokesmod.utility.font.FeralFontRenderer fSmall = null;

    /* Cached module list, rebuilt once per frame. */
    private int modulesFrame = -1;
    private final ArrayList modulesCache = new ArrayList();

    /* ===================================================================== lifecycle */

    public void onDisable() {
        if (open) {
            closeMenu();
        }
    }

    private void onKeyPress(char typedChar, int keyCode) {
        if (!open) {
            return;
        }
        if (editing != null) {
            handleEditKey(typedChar, keyCode);
        } else if (bindModule != null || bindKey != null) {
            applyBind(keyCode == 1 ? 0 : keyCode);
        } else if (listOpen) {
            handleListKey(typedChar, keyCode);
        } else if (searchFocused) {
            handleSearchKey(typedChar, keyCode);
        } else if (keyCode == 1) {
            startClose();
        }
    }

    private void handleEditKey(char typedChar, int keyCode) {
        if (keyCode == 1) {
            editing = null;
            editingModule = null;
            editBuffer = "";
        } else if (keyCode == 28 || keyCode == 156) {
            final keystrokesmod.module.setting.impl.TextSetting target = editing;
            commitEdit();
            runSafely(new Runnable() {
                public void run() {
                    target.submit();
                }
            });
        } else if (keyCode == 14) {
            if (editBuffer.length() > 0) {
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            }
        } else if (typedChar >= ' ' && typedChar != (char) 127 && editBuffer.length() < editing.getMaxLength()) {
            editBuffer = editBuffer + typedChar;
        }
    }

    private void handleListKey(char typedChar, int keyCode) {
        if (keyCode == 1) {
            if (listExpandedGroup != null) {
                listCollapseGroup();
            } else {
                listBlur();
            }
        } else if (keyCode == 14) {
            if (listQuery.length() > 0) {
                listQuery = listQuery.substring(0, listQuery.length() - 1);
                listDropScroll = 0f;
            }
        } else if (keyCode == 28 || keyCode == 156) {
            listSubmit(listSearchMod, listSearchSetting);
        } else if (typedChar >= ' ' && typedChar != (char) 127 && listQuery.length() < 48) {
            listQuery = listQuery + typedChar;
            listDropScroll = 0f;
        }
    }

    private void handleSearchKey(char typedChar, int keyCode) {
        if (keyCode == 1) {
            searchFocused = false;
        } else if (keyCode == 14) {
            if (searchText.length() > 0) {
                searchText = searchText.substring(0, searchText.length() - 1);
            }
            searchScroll = 0f;
        } else if (typedChar >= ' ' && typedChar != (char) 127 && searchText.length() < 32) {
            searchText = searchText + typedChar;
            searchScroll = 0f;
        }
    }

    private void applyBind(int code) {
        if (bindKey != null) {
            bindKey.setKey(code);
        } else if (bindModule != null) {
            bindModule.setBind(code);
        }
        markUnsaved(bindModule);
        bindKey = null;
        bindModule = null;
        // Adopt the just-bound key's current state so the press that bound it cannot also fire the menu.
        prevOpenKey = openKeyDown();
    }

    public void onRenderTick(boolean notifications) {
        Minecraft mc = Minecraft.getInstance();
        Window viewport = new Window(mc);
        scaledW = (float) viewport.getScaledWidth();
        scaledH = (float) viewport.getScaledHeight();
        scale = viewport.getScale();
        framebufferHeight = mc.height;

        boolean keyNow = openKeyDown();
        // The Gui module opens the menu (see requestOpen); its key closes it again while it is open.
        // While a row is capturing a keybind, the open key must not toggle the menu, or the very
        // key you press to rebind it would immediately close the menu you are editing in.
        boolean binding = bindModule != null || bindKey != null || editing != null || listOpen || searchFocused;
        if (open
                && keyNow
                && !prevOpenKey
                && mc.screen == screen
                && !binding
                && client.time() - lastToggleTime > 150L) {
            lastToggleTime = client.time();
            if (closing) {
                closing = false;
            } else {
                startClose();
            }
        }
        prevOpenKey = keyNow;

        if (pendingOpen) {
            pendingOpen = false;
            if (!open && (mc.screen == null || mc.screen instanceof ClickGui)) {
                lastToggleTime = client.time();
                openMenu();
                prevOpenKey = openKeyDown();
            }
        }

        long now = client.time();
        if (lastFrameTime == 0L) {
            lastFrameTime = now;
        }
        frameDt = (float) (now - lastFrameTime) / 1000f;
        if (frameDt < 0f) {
            frameDt = 0f;
        } else if (frameDt > 0.1f) {
            frameDt = 0.1f;
        }
        lastFrameTime = now;
        frameId++;
        beginFrame();

        // Theme colours must be read every frame, not just while the menu is open, or the arraylist and
        // notifications use the hardcoded defaults until the menu is opened once after launch.
        applySettings();
        if (notifications) {
            detectToggles();
            if (!open && !(mc.screen instanceof ClickGui)) {
                drawNotifications();
            }
        }
        // The arraylist belongs to the HUD module (Arraylist: Big cat), whatever the Gui style is.
        arrayListAnim = HUD.isArraylistEnabled()
                ? approach(arrayListAnim, HUD.isCatArraylistSelected() ? 1f : 0f, 3f, frameDt)
                : 0f;
        if (arrayListAnim > 0.005f) {
            drawArraylist();
        }

        if (!open) {
            return;
        }
        if (mc.screen != screen) {
            open = false;
            resetInteraction();
            return;
        }

        pollMouse();
        if (closing) {
            pressLeft = false;
            pressRight = false;
            releaseLeft = false;
        }
        gl.push();
        gl.blend(true);
        gl.texture2d(true);
        gl.depth(false);
        gl.scissor(false);
        inMenu = true;
        try {
            drawMenu();
        } finally {
            inMenu = false;
            gl.scissor(false);
            gl.color(1f, 1f, 1f, 1f);
            gl.blend(true);
            gl.texture2d(true);
            gl.depth(true);
            gl.pop();
        }
        // Resolve a queued click (including its focus change) before delivering typed keys.
        for (int i = 0; i < queuedKeys.size() && !closing; i++) {
            KeyInput key = queuedKeys.get(i);
            onKeyPress(key.character, key.code);
        }
        queuedKeys.clear();
        if (closing && menuAnim < 0.02f) {
            closeMenu();
        }
    }

    private void beginFrame() {
        cardAnim.begin(frameId, frameDt);
        toggleAnim.begin(frameId, frameDt);
        groupAnim.begin(frameId, frameDt);
        modeAnim.begin(frameId, frameDt);
        listAnim.begin(frameId, frameDt);
    }

    /* ================================================================ notifications */

    void resetNotifications() {
        if (enabledInit) {
            enabledInit = false;
            notifCount = 0;
            prevEnabled.clear();
        }
    }

    private void detectToggles() {
        ArrayList all = allModules();
        for (int i = 0; i < all.size(); i++) {
            keystrokesmod.module.Module m = (keystrokesmod.module.Module) all.get(i);
            if (!m.canBeEnabled()) {
                continue;
            }
            String name = m.getName();
            boolean now = m.isEnabled();
            // Track by module identity, not name: a script can share its file-derived name with a
            // built-in module (e.g. a "BedESP" script and Raven's own BedESP), and keying by name made
            // the two fight over one entry, firing a notification every frame.
            Object prev = prevEnabled.get(m);
            boolean was = prev != null && ((Boolean) prev).booleanValue();
            if (enabledInit && prev != null && now != was && !HUD.isNotificationBlacklisted(name)) {
                pushNotif(name, now);
            }
            prevEnabled.put(m, Boolean.valueOf(now));
        }
        enabledInit = true;
    }

    private void pushNotif(String name, boolean on) {
        if (notifCount >= NOTIF_MAX) {
            dropFirstNotif();
        }
        notifText[notifCount] = name;
        notifOn[notifCount] = on;
        notifStart[notifCount] = client.time();
        notifY[notifCount] = 0f;
        notifCount++;
    }

    private void dropFirstNotif() {
        for (int i = 1; i < notifCount; i++) {
            notifText[i - 1] = notifText[i];
            notifOn[i - 1] = notifOn[i];
            notifStart[i - 1] = notifStart[i];
            notifY[i - 1] = notifY[i];
        }
        notifCount--;
    }

    /* ==================================================================== arraylist */

    private float arrayProgress(String name, boolean on) {
        Object o = arrayAnim.get(name);
        float cur = o == null ? 0f : ((Float) o).floatValue();
        cur = approach(cur, on ? 1f : 0f, 10f, frameDt);
        if (!on && cur <= 0.01f) {
            arrayAnim.remove(name);
            return 0f;
        }
        arrayAnim.put(name, Float.valueOf(cur));
        return cur;
    }

    private void drawArraylist() {
        boolean info = HUD.showInfo.isToggled();
        int corner = (int) Math.round(HUD.catCorner.getInput());
        // Changing the corner replays the same slide-in the toggle uses, from the new edge.
        if (corner != lastArrayCorner) {
            lastArrayCorner = corner;
            arrayAnim.clear();
        }
        arrayInfoAnim = approach(arrayInfoAnim, info ? 1f : 0f, 10f, frameDt);

        ArrayList shown = enabledModulesForArray();
        HashSet onNames = new HashSet();
        for (int i = 0; i < shown.size(); i++) {
            onNames.add(((keystrokesmod.module.Module) shown.get(i)).getName());
        }
        List<String> closing = new ArrayList<>(arrayAnim.keySet());
        closing.removeIf(onNames::contains);
        appendClosingModules(shown, closing);
        int n = shown.size();
        if (n == 0) {
            return;
        }

        float as = (float) HUD.catScale.getInput() * 0.8f;
        float aop = clampF((float) HUD.catOpacity.getInput() / 100f, 0f, 1f);
        float rowH = 15f * as;
        float padX = 6f * as;
        float margin = 6f * as;
        float bar = 2.5f * as;
        boolean right = corner == 1 || corner == 3;
        boolean bottom = corner == 2 || corner == 3;

        keystrokesmod.utility.font.FeralFontRenderer fr = font(0.95f * as);
        float infoF = easeInOut(arrayInfoAnim);
        sortByWidth(shown, fr, infoF);

        float y = bottom ? (scaledH - margin - rowH) : margin;
        float step = rowH + 1.5f * as;
        float rowIndex = 0f;
        // Category: follow the Big Cat GUI's selected tab (accent is refreshed every frame in applySettings).
        boolean categoryColor = HUD.isCategoryColorMode();
        for (int i = 0; i < n; i++) {
            keystrokesmod.module.Module m = (keystrokesmod.module.Module) shown.get(i);
            String name = m.getName();
            float p = arrayProgress(name, onNames.contains(name)) * arrayListAnim;
            if (p <= 0.01f) {
                continue;
            }
            String suffix = arrayInfoAnim > 0.001f ? statusSuffix(m) : "";
            float nameW = tw(fr, name);
            float sufW = suffix.length() > 0 ? tw(fr, " " + suffix) * infoF : 0f;
            float w = (nameW + sufW + 2f * padX + bar) * easeInOut(p);
            float slide = (1f - easeInOut(p)) * 14f * as;
            float x = right ? (scaledW - margin - w + slide) : (margin - slide);
            int a = Math.round(255f * p);

            if (aop > 0.001f) {
                qRound(x, y, x + w, y + rowH, 3.5f * as, argb(Math.round(115f * p * aop), 0, 0, 0));
            }
            // Bar and name use the HUD module's color settings (Color mode, wave, theme).
            float barX = right ? (x + w - bar) : x;
            qRect(
                    barX,
                    y,
                    barX + bar,
                    y + rowH,
                    withAlpha(categoryColor ? accent : HUD.getArraylistColor(rowIndex, barX + bar / 2f), a));

            float tx = right ? (x + padX) : (x + padX + bar);
            if (HUD.isHorizontalWaveColor() && fr != null) {
                final float row = rowIndex;
                final float textX = tx;
                fr.drawGlyphString(
                        name,
                        tx,
                        y + rowH / 2f - (float) fr.getFontHeight() / 2f,
                        (ch, xOff, gw, fmt) ->
                                menuTint(withAlpha(HUD.getArraylistColor(row, textX + xOff + gw * 0.5f), a)),
                        false);
            } else {
                txt(
                        fr,
                        name,
                        tx,
                        y + rowH / 2f,
                        withAlpha(categoryColor ? accent : HUD.getArraylistColor(rowIndex, tx), a));
            }
            if (suffix.length() > 0 && infoF > 0.001f) {
                txt(fr, suffix, tx + nameW + tw(fr, " "), y + rowH / 2f, argb(Math.round(a * infoF), 176, 176, 176));
            }

            y += bottom ? -step * p : step * p;
            rowIndex += p;
            if (y < 0f || y > (float) scaledH) {
                break;
            }
        }
    }

    private void appendClosingModules(List<Module> shown, List<String> closing) {
        if (closing.isEmpty()) {
            return;
        }
        if (closing.size() == 1) {
            Module found = findModule(closing.get(0));
            if (found != null) {
                shown.add(found);
            }
            return;
        }

        Map<String, Module> closingModules = new HashMap<>();
        HashSet<String> remaining = new HashSet<>(closing);
        for (Object item : allModules()) {
            Module module = (Module) item;
            String name = module.getName();
            if (remaining.remove(name)) {
                closingModules.put(name, module);
                if (remaining.isEmpty()) {
                    break;
                }
            }
        }
        for (String name : closing) {
            Module found = closingModules.get(name);
            if (found != null) {
                shown.add(found);
            }
        }
    }

    private keystrokesmod.module.Module findModule(String name) {
        ArrayList all = allModules();
        for (int i = 0; i < all.size(); i++) {
            keystrokesmod.module.Module m = (keystrokesmod.module.Module) all.get(i);
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    /** True for modules that belong on the arraylist (and get a "show in arraylist" toggle). */
    private boolean arraylistEligible(keystrokesmod.module.Module m) {
        return m.canBeEnabled()
                && !(m instanceof keystrokesmod.utility.profile.ProfileModule)
                && !m.getName().equals("HUD");
    }

    private ArrayList enabledModulesForArray() {
        ArrayList all = allModules();
        ArrayList out = new ArrayList();
        for (int i = 0; i < all.size(); i++) {
            keystrokesmod.module.Module m = (keystrokesmod.module.Module) all.get(i);
            if (m.isEnabled() && arraylistEligible(m) && !m.isHidden()) {
                out.add(m);
            }
        }
        return out;
    }

    private void sortByWidth(ArrayList list, keystrokesmod.utility.font.FeralFontRenderer fr, float infoF) {
        int n = list.size();
        // Info may call a script, so retain the original evaluation order while suffixes are visible.
        float[] widths = null;
        if (n > 2 && !(infoF > 0.001f)) {
            widths = new float[n];
            for (int i = 0; i < n; i++) {
                widths[i] = tw(fr, ((keystrokesmod.module.Module) list.get(i)).getName());
            }
        }
        for (int i = 0; i < n - 1; i++) {
            int best = i;
            float bestW = widths == null ? rowWidth((keystrokesmod.module.Module) list.get(i), fr, infoF) : widths[i];
            for (int j = i + 1; j < n; j++) {
                float w = widths == null ? rowWidth((keystrokesmod.module.Module) list.get(j), fr, infoF) : widths[j];
                if (w > bestW) {
                    bestW = w;
                    best = j;
                }
            }
            if (best != i) {
                Object tmp = list.get(i);
                list.set(i, list.get(best));
                list.set(best, tmp);
                if (widths != null) {
                    widths[best] = widths[i];
                    widths[i] = bestW;
                }
            }
        }
    }

    private float rowWidth(
            keystrokesmod.module.Module m, keystrokesmod.utility.font.FeralFontRenderer fr, float infoF) {
        float w = tw(fr, m.getName());
        if (infoF > 0.001f) {
            String suffix = statusSuffix(m);
            if (suffix.length() > 0) {
                w += tw(fr, " " + suffix) * infoF;
            }
        }
        return w;
    }

    private String statusSuffix(keystrokesmod.module.Module mod) {
        // Prefer the module's own HUD info (same text the built-in HUD shows), minus color codes.
        String info = mod.getInfo();
        if (info != null) {
            info = info.replaceAll("§.", "").trim();
            if (info.length() > 0) {
                return info;
            }
        }
        // No info: fall back to the first visible slider.
        ArrayList list = mod.getSettings();
        for (int i = 0; i < list.size(); i++) {
            keystrokesmod.module.setting.Setting st = (keystrokesmod.module.setting.Setting) list.get(i);
            if (!st.visible) {
                continue;
            }
            if (st instanceof keystrokesmod.module.setting.impl.SliderSetting) {
                keystrokesmod.module.setting.impl.SliderSetting ss =
                        (keystrokesmod.module.setting.impl.SliderSetting) st;
                if (ss.isString) {
                    String[] opts = ss.getOptions();
                    if (opts != null && opts.length > 0) {
                        int idx = clampI((int) Math.round(ss.getInput()), 0, opts.length - 1);
                        return opts[idx];
                    }
                } else {
                    double v = ss.getInput();
                    if (!(ss.canBeDisabled && v == -1.0D)) {
                        return fmt(v) + ss.getSuffix();
                    }
                }
            }
        }
        return "";
    }

    private void drawNotifications() {
        long now = client.time();
        while (notifCount > 0 && now - notifStart[0] >= NOTIF_LIFE) {
            dropFirstNotif();
        }
        if (notifCount == 0) {
            return;
        }

        boolean modern = HUD.notificationStyle.getInput() >= 0.5;
        int corner = (int) HUD.notificationCorner.getInput();
        boolean right = corner == 1 || corner == 3;
        boolean bottom = corner == 2 || corner == 3;

        float ns = (float) HUD.notificationScale.getInput() * 0.8f;
        float nop = clampF((float) HUD.notificationOpacity.getInput() / 100f * 0.9f, 0f, 0.9f);
        float cardH = (modern ? 34f : 20f) * ns;
        float cardW = 170f * ns;
        float pad = 9f * ns;
        float gap = 5f * ns;
        float margin = 10f * ns;
        keystrokesmod.utility.font.FeralFontRenderer fr = font(ns);

        float slotY = bottom ? (scaledH - margin - cardH) : margin;
        for (int i = notifCount - 1; i >= 0; i--) {
            long age = now - notifStart[i];
            float appear = clampF(age / 260f, 0f, 1f);
            float fade = clampF((NOTIF_LIFE - age) / 260f, 0f, 1f);
            float t = easeInOut(appear) * easeInOut(fade);

            notifY[i] = notifY[i] + (slotY - notifY[i]) * 0.35f;
            float yy = notifY[i];

            float w = cardW;
            float x = right ? (scaledW - margin - w * t) : (margin - w * (1f - t));
            drawNotifCard(modern, fr, ns, nop, i, x, yy, w, cardH, pad);

            slotY += bottom ? -(cardH + gap) : (cardH + gap);
        }
    }

    private void drawNotifCard(
            boolean modern,
            keystrokesmod.utility.font.FeralFontRenderer fr,
            float ns,
            float nop,
            int i,
            float x,
            float y,
            float w,
            float h,
            float pad) {
        int status = notifOn[i] ? C_ON : C_OFF;
        String label = (notifOn[i] ? "Enabled " : "Disabled ") + notifText[i];
        int bg = modern ? C_MODERN_BG : C_NOTIF_BG;
        int bgA = Math.round((modern ? 205 : 185) * nop);
        qRound(x, y, x + w, y + h, 6f * ns, withAlpha(bg, bgA));

        float remain = 1f - clampF((client.time() - notifStart[i]) / (float) NOTIF_LIFE, 0f, 1f);
        float fillW = w * remain;
        if (fillW > 0.5f) {
            float fillOp = clampF(nop + 0.02f, 0f, 1f);
            qRound(x, y, x + fillW, y + h, 6f * ns, withAlpha(status, Math.round(255f * fillOp)));
        }

        int st = lighten(status, 0.3f);
        if (modern) {
            float iconD = 12f * ns;
            float ix = x + pad;
            float iy = y + h / 2f - iconD / 2f;
            qRound(ix, iy, ix + iconD, iy + iconD, iconD / 2f, withAlpha(status, 60));
            qRound(ix + iconD * 0.35f, iy + iconD * 0.35f, ix + iconD * 0.65f, iy + iconD * 0.65f, iconD * 0.15f, st);
            float textX = ix + iconD + 6f * ns;
            float textW = x + w - pad - textX;
            txt(fr, trim(fr, notifText[i], textW), textX, y + h * 0.3f, C_WHITE);
            txt(fr, trim(fr, notifOn[i] ? "Enabled" : "Disabled", textW), textX, y + h * 0.73f, st);
        } else {
            txt(fr, trim(fr, label, w - 2f * pad), x + pad, y + h / 2f, C_WHITE);
        }
    }

    private int lighten(int c, float amt) {
        int r = Math.round(((c >> 16) & 255) + (255 - ((c >> 16) & 255)) * amt);
        int g = Math.round(((c >> 8) & 255) + (255 - ((c >> 8) & 255)) * amt);
        int b = Math.round((c & 255) + (255 - (c & 255)) * amt);
        return argb(255, r, g, b);
    }

    private int logoHoverArgb(int color) {
        int rgb = color & 0x00FFFFFF;
        int lum = (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
        if (lum > 127) {
            return 0xFF000000 | scaleRgb(rgb, 0.72f);
        }
        return 0xFF000000 | (lighten(rgb, 0.28f) & 0x00FFFFFF);
    }

    private int fadeColor(int c, float op) {
        return argb(Math.round(((c >>> 24) & 255) * op), (c >> 16) & 255, (c >> 8) & 255, c & 255);
    }

    /* ============================================================== menu open/close */

    private void openMenu() {
        Feral.clickGui.refreshModulesIfLiteModeChanged();
        open = true;
        closing = false;
        menuAnim = 0f;
        logoOpenedAt = System.nanoTime();
        resetInteraction();
        Minecraft.getInstance().openScreen(screen);
    }

    private void startClose() {
        if (!open || closing) {
            return;
        }
        closing = true;
        resetInteraction();
    }

    private void closeMenu() {
        open = false;
        closing = false;
        resetInteraction();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == screen) {
            mc.openScreen(null);
        }
    }

    private void resetInteraction() {
        bindModule = null;
        bindKey = null;
        editing = null;
        editingModule = null;
        editBuffer = "";
        dragSlider = null;
        dragColorSetting = null;
        dragColorPart = -1;
        sideDrag.dragging = false;
        contentDrag.dragging = false;
        winDragging = false;
        resizing = false;
        searchFocused = false;
        listFinalizeClose();
        listAnim.clear();
        pointer.clear();
        queuedKeys.clear();
        clipping = false;
        scissorOff();
    }

    /* ================================================================= frame input */

    private void pollMouse() {
        int x = pointer.hasPosition ? pointer.x : Mouse.getX();
        int y = pointer.hasPosition ? pointer.y : Mouse.getY();
        mx = (x + 0.5f) / scale;
        my = (framebufferHeight - y - 0.5f) / scale;
        leftDown = Mouse.isButtonDown(0);
        pressLeft = pointer.leftPress;
        releaseLeft = pointer.leftRelease;
        if (releaseLeft) {
            releaseX = (pointer.releaseX + 0.5f) / scale;
            releaseY = (framebufferHeight - pointer.releaseY - 0.5f) / scale;
        }
        pressRight = pointer.rightPress;
        frameWheel = pointer.wheel;
        pointer.clear();
    }

    private float dragX() {
        return releaseLeft ? releaseX : mx;
    }

    private float dragY() {
        return releaseLeft ? releaseY : my;
    }

    /* ==================================================================== frame draw */

    private void drawMenu() {
        sawColorDrag = false;
        sawEdit = false;
        sawListFocus = false;
        syncSettingVisibility();
        frameSettings.clear();
        animateOpen();

        resolveWindowPosition();
        handleWindowDrag();
        resolveWindowPosition();
        handleResize();

        float topCat = BigCatLayout.topCatScale(s, winY);
        float tailY = CatRenderer.windowTailAnchorY(winY, winH);
        float tail = BigCatLayout.tailScale(s, scaledW - winX - winW, tailY, scaledH - tailY);
        CatRenderer.setAlpha(fadeMul);
        try {
            CatRenderer.drawWindowCatBehind(
                    winX,
                    winY,
                    winW,
                    winH,
                    topCat,
                    tail,
                    accent,
                    topCat > 0f && CatRenderer.isOverWindowCatHead(mx, my, winX, winY, winW, topCat));
        } finally {
            CatRenderer.setAlpha(1f);
        }
        drawWindowFrame();
        drawSidebar();
        drawContent();
        drawResizeGrip();
        CatRenderer.setAlpha(fadeMul);
        try {
            CatRenderer.drawWindowCatFront(
                    winX, winY, winW, winH, topCat, BigCatLayout.backPawScale(s, scaledH - winY - winH), accent);
        } finally {
            CatRenderer.setAlpha(1f);
        }

        if (!sawListFocus || pressLeft) {
            listBlur();
        }
        if (!sawEdit || pressLeft) {
            commitEdit();
        }

        endFrameInput();
    }

    /** The selected tab's category: its colour is the window's accent. */
    private void applyCategoryAccent() {
        accent = CatColors.of(tab);
    }

    /** Reads the selected category's colours and the Gui's font settings into the per-frame theme fields. */
    private void applySettings() {
        // Meower: per-category cat managers. Feral: category accent from CatColors, background/text from Gui.
        applyCategoryAccent();
        int bgRgb = keystrokesmod.module.impl.client.Gui.bigCatBackgroundColor.getRGB() & 0x00FFFFFF;
        int txtRgb = keystrokesmod.module.impl.client.Gui.bigCatTextColor.getRGB() & 0x00FFFFFF;

        float opacity = clampF((float) (modules.getSlider(scriptName, S_ALPHA) / 100.0), 0f, 1f);
        float cardCurve = opacityCurve(opacity, 0.70f);
        guiAlpha = clampI(Math.round(opacity * 255f), 0, 255);
        cardAlpha = clampI(Math.round(cardCurve * 255f), 0, 255);
        settingsAlpha = clampI(Math.round(opacityCurve(opacity, 0.78f) * 255f), 0, 255);
        switchAlpha = clampI(Math.round(opacityCurve(opacity, 0.86f) * 255f), 0, 255);
        onTintFrac = cardCurve;
        cPanel = (guiAlpha << 24) | bgRgb;
        cField = (settingsAlpha << 24) | bgRgb;
        cCard = (cardAlpha << 24) | boostRgb(bgRgb, 9);
        cCardHover = (cardAlpha << 24) | boostRgb(bgRgb, 15);
        cRaised = (settingsAlpha << 24) | boostRgb(bgRgb, 24);
        cText = 0xFF000000 | txtRgb;
        cTextDim = 0xFF000000 | scaleRgb(txtRgb, 0.64f);
        cTextFaint = 0xFF000000 | scaleRgb(txtRgb, 0.42f);

        fontFamily = Gui.getSelectedFontName();
        fontSizeMul = Gui.getSelectedFontSize();
        fontThickness = Gui.getSelectedFontThickness();
    }

    /** Picks up the configured size, advances the open/close animation, and applies its pop scale. */
    private void animateOpen() {
        menuAnim = approach(menuAnim, closing ? 0f : 1f, closing ? 4f : 2f, frameDt);
        fadeMul = 0.1f + 0.9f * clampF(menuAnim, 0f, 1f);
        settledControlScale = BigCatLayout.controlsScale(
                preferredControlScale(),
                BigCatLayout.fit((float) Gui.bigCatWidth.getInput(), scaledW, 1f),
                BigCatLayout.fit((float) Gui.bigCatHeight.getInput(), scaledH, 1f));
        fBig = font(1.25f * settledControlScale);
        fMid = font(settledControlScale);
        fSmall = font(0.9f * settledControlScale);
        updateWindowSize(resizing || menuAnim > 0.98f ? 1f : 0.7f + 0.3f * easeOutBack(menuAnim));
    }

    private void updateWindowSize(float pop) {
        winW = BigCatLayout.fit((float) Gui.bigCatWidth.getInput(), scaledW, pop);
        winH = BigCatLayout.fit((float) Gui.bigCatHeight.getInput(), scaledH, pop);
        s = BigCatLayout.controlsScale(preferredControlScale() * pop, winW, winH);
        sideW = SIDEBAR_W * s;
        // Scale the glyphs and their layout, not the rasterized font size, during the window pop.
        menuFontScale = settledControlScale > 0f && s > 0f ? s / settledControlScale : 1f;
    }

    private static float preferredControlScale() {
        return 0.7f * Gui.getClickGuiScale();
    }

    /** Drags the window by its sidebar header. */
    private void handleWindowDrag() {
        if (!resizing && pressLeft && inside(winX, winY, sideW, 44f * s)) {
            pressLeft = false;
            winDragging = true;
            dragLastX = mx;
            dragLastY = my;
        }
        if (!winDragging) {
            return;
        }
        if (leftDown || releaseLeft) {
            winOffX = winX - BigCatLayout.centered(scaledW, winW) + dragX() - dragLastX;
            winOffY = winY - BigCatLayout.centeredY(scaledH, winH, s) + dragY() - dragLastY;
            dragLastX = dragX();
            dragLastY = dragY();
        } else {
            winDragging = false;
        }
    }

    /** Resolves the window's top-left from its centring offset and current size, clamped on screen. */
    private void resolveWindowPosition() {
        float baseX = BigCatLayout.centered(scaledW, winW);
        float baseY = BigCatLayout.centeredY(scaledH, winH, s);
        winX = BigCatLayout.position(baseX + winOffX, scaledW, winW);
        winY = BigCatLayout.position(baseY + winOffY, scaledH, winH);
    }

    /** Bottom-right drag handle; writes back to the Width/Height sliders and anchors the top-left. */
    private void handleResize() {
        float handleS = resizeGripSize();
        float handleX = winX + winW - handleS;
        float handleY = winY + winH - handleS;
        if (pressLeft && menuAnim > 0.98f && inside(handleX, handleY, handleS, handleS)) {
            pressLeft = false;
            resizing = true;
            resizeAnchorX = winX;
            resizeAnchorY = winY;
            resizeGrabX = mx - (winX + winW);
            resizeGrabY = my - (winY + winH);
            resizeLastX = mx;
            resizeLastY = my;
            if (!releaseLeft) {
                return;
            }
        }
        if (!resizing) {
            return;
        }
        if (!leftDown && !releaseLeft) {
            resizing = false;
            return;
        }
        float x = dragX();
        float y = dragY();
        if (resizeLastX != x) {
            resizeAxis(Gui.bigCatWidth, x - resizeGrabX, resizeAnchorX, scaledW);
            resizeLastX = x;
        }
        if (resizeLastY != y) {
            resizeAxis(Gui.bigCatHeight, y - resizeGrabY, resizeAnchorY, scaledH);
            resizeLastY = y;
        }
        updateWindowSize(1f);
        winOffX = resizeAnchorX - BigCatLayout.centered(scaledW, winW);
        winOffY = resizeAnchorY - BigCatLayout.centeredY(scaledH, winH, s);
        resolveWindowPosition();
    }

    private void resizeAxis(SliderSetting setting, float edge, float anchor, float viewport) {
        float available = viewport - anchor - BigCatLayout.MARGIN;
        if (available < setting.getMin() || edge - anchor >= available && setting.getInput() >= available) {
            return;
        }
        double value = BigCatLayout.resizedValue(
                edge - anchor, available, setting.getMin(), setting.getMax(), setting.getInterval());
        if (value != setting.getInput()) {
            setting.setValueWithEvent(value);
            markUnsaved(ModuleManager.getModule(Gui.class));
        }
    }

    private float resizeGripSize() {
        return Math.min(Math.min(winW, winH), Math.max(12f, RESIZE_HANDLE * s));
    }

    /** Press latches are consumed during drawing; clear them and any drags for the next frame. */
    private void endFrameInput() {
        pressLeft = false;
        pressRight = false;
        if (!sawColorDrag) {
            dragColorSetting = null;
            dragColorPart = -1;
        }
        if (releaseLeft) {
            sideDrag.dragging = false;
            dragSlider = null;
            dragColorSetting = null;
            dragColorPart = -1;
            contentDrag.dragging = false;
            resizing = false;
        }
    }

    /* --------------------------------------------------------------- window chrome */

    private void drawWindowFrame() {
        qRound(winX, winY, winX + winW, winY + winH, 10f * s, cPanel);
    }

    private void drawResizeGrip() {
        float hs = resizeGripSize();
        float hx = winX + winW - hs;
        float hy = winY + winH - hs;
        int col = resizing || inside(hx, hy, hs, hs) ? accent : 0x99FFFFFF;
        for (int i = 1; i <= 3; i++) {
            float d = i * 3.4f * s;
            float dot = 1.3f * s;
            float cxp = winX + winW - 5f * s - d;
            float cyp = winY + winH - 5f * s - d;
            qRound(cxp - dot, cyp - dot, cxp + dot, cyp + dot, dot, col);
        }
    }

    /* -------------------------------------------------------------------- sidebar */

    private void drawSidebar() {
        float x = winX;
        float y = winY;
        scissorOn(x, y, sideW, winH);
        drawLogo(x, y);
        drawSearchBox(x, y);
        txt(fSmall, "Modules", x + 14f * s, y + 86f * s, cTextFaint);

        List<CategoryComponent> categories = ClickGui.categories;
        float listTop = y + 98f * s;
        float listBot = y + winH - resizeGripSize();
        float listH = listBot - listTop;
        float step = 27f * s;
        float total = categories.size() * step;
        float maxScroll = total - listH;
        if (maxScroll < 0f) {
            maxScroll = 0f;
        }
        if (frameWheel != 0 && inside(x, listTop, sideW, listH)) {
            sideScroll -= (float) frameWheel / 6f;
            frameWheel = 0;
        }
        sideScroll = clampF(sideScroll, 0f, maxScroll);

        float barX = x + sideW - 4.5f * s;
        float barW = SCROLLBAR_W * s;
        sideScroll = dragScrollbar(sideDrag, barX, barW, listTop, listH, total, maxScroll, sideScroll);

        if (tab != lastSelTab) {
            lastSelTab = tab;
            selAnim = 0f;
        }
        selAnim = approach(selAnim, 1f, 6f, frameDt);

        drawCategoryList(x, categories, listTop, listBot, step);
        drawScrollbar(barX, barW, listTop, listH, total, sideScroll, maxScroll, sideDrag.dragging);
    }

    private void drawLogo(float x, float y) {
        float cx = x + 25f * s;
        float cy = y + 23f * s;
        int pawColor = inside(cx - 11f * s, cy - 11f * s, 22f * s, 22f * s) ? logoHoverArgb(accent) : accent;
        float age = (System.nanoTime() - logoOpenedAt) * 1e-6f;
        float drop = clampF(age / 220f, 0f, 1f);
        float settle = clampF((age - 220f) / 340f, 0f, 1f);
        float impact = (float) Math.sin(settle * 10.5f) * (1f - settle) * (1f - settle);
        float idle = clampF((age - 650f) / 600f, 0f, 1f);
        float stampY = cy
                + (-14f * (1f - drop * drop)
                                + 3f * Math.min(0f, impact)
                                + (float) Math.sin(age * 0.002f) * 0.55f * idle)
                        * s;
        CatRenderer.setAlpha(fadeMul);
        CatRenderer.begin();
        try {
            for (int i = 0; i < 4; i++) {
                float reveal = clampF((age - 220f - i * 72f) / 230f, 0f, 1f);
                if (reveal <= 0f) {
                    continue;
                }
                double angle = i * Math.PI / 2.0 + age * (i % 2 == 0 ? 0.00032 : -0.00024);
                float spread = (13f + 5f * settle) * s;
                float glow = 0.76f + 0.24f * (float) Math.sin(age * 0.003f + i);
                CatRenderer.pawIcon(
                        cx + (float) Math.cos(angle) * spread,
                        cy + (float) Math.sin(angle) * spread * 0.8f,
                        (4f + (i % 2) * 1.25f) * s,
                        fadeColor(accent, 0.54f * reveal * glow));
            }
            gl.push();
            try {
                gl.translate(cx, stampY, 0f);
                gl.rotate(-15f * (1f - drop) + 6f * impact, 0f, 0f, 1f);
                gl.scale(0.83f + 0.17f * drop + 0.16f * impact, 0.83f + 0.17f * drop - 0.18f * impact, 1f);
                CatRenderer.pawIcon(0f, 0f, 22f * s, fadeColor(pawColor, 0.55f + 0.45f * drop));
            } finally {
                gl.pop();
            }
        } finally {
            CatRenderer.end();
            CatRenderer.setAlpha(1f);
        }

        float width = 3f * s;
        for (String letter : LOGO_LETTERS) {
            width += tw(fBig, letter);
        }
        float fit =
                Math.min(1f, Math.min(64f * s / width, 18f * s / Math.max(1f, fBig.getFontHeight() * menuFontScale)));
        gl.push();
        try {
            gl.translate(x + 48f * s, cy, 0f);
            gl.scale(fit, fit, 1f);
            float advance = 0f;
            for (int i = 0; i < LOGO_LETTERS.length; i++) {
                float step = clampF((age - 145f - i * 65f) / 360f, 0f, 1f);
                if (step > 0f) {
                    float hop = 5f * (1f - step) - 7f * (float) Math.sin(step * Math.PI);
                    float sway = 0.65f * (float) Math.sin(age * 0.0024f - i * 0.7f) * idle;
                    txt(fBig, LOGO_LETTERS[i], advance, (hop + sway) * s / fit, fadeColor(accent, easeInOut(step)));
                }
                advance += tw(fBig, LOGO_LETTERS[i]) + 0.75f * s;
            }
        } finally {
            gl.pop();
        }
    }

    private void drawSearchBox(float x, float y) {
        float sx = x + 10f * s;
        float sy = y + 46f * s;
        float sw = sideW - 20f * s;
        float sh = 24f * s;
        boolean onSearch = mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sh;
        if (pressLeft && !onSearch && mx >= x && mx < x + sideW && my >= y && my <= y + winH) {
            searchText = "";
            searchFocused = false;
        }
        if (clicked(sx, sy, sw, sh)) {
            commitEdit();
            bindModule = null;
            bindKey = null;
            searchFocused = true;
            listBlur();
        } else if (pressLeft) {
            searchFocused = false;
        }
        boolean boxHov = inside(sx, sy, sw, sh);
        qRound(
                sx,
                sy,
                sx + sw,
                sy + sh,
                7f * s,
                argb(Math.round(guiAlpha * (searchFocused || boxHov ? 0.3f : 0.2f)), 0, 0, 0));
        float scy = sy + sh / 2f;
        if (searchText.length() == 0 && !searchFocused) {
            txt(fSmall, trim(fSmall, "Search", sw - 20f * s), sx + 9f * s, scy, cTextFaint);
        } else {
            String shown = trimLeft(fSmall, searchText, sw - 20f * s);
            txt(fSmall, shown, sx + 9f * s, scy, cText);
            if (searchFocused && (client.time() / 500L) % 2L == 0L) {
                float caretX = sx + 9f * s + tw(fSmall, shown) + 1f * s;
                qRect(caretX, sy + 6f * s, caretX + 1f * s, sy + sh - 6f * s, cText);
            }
        }
    }

    private void drawCategoryList(
            float x, List<CategoryComponent> categories, float listTop, float listBot, float step) {
        float rx = x + 6f * s;
        float rw = sideW - 12f * s;
        float rh = 25f * s;
        beginClip(x, listTop, sideW, listBot - listTop);
        float ry = listTop - sideScroll;
        for (CategoryComponent component : categories) {
            Module.category category = component.category;
            if (intersectsClip(rx, ry, rw, rh)) {
                boolean sel = category == tab && !searchFocused && searchText.length() == 0;
                boolean hov = inside(rx, ry, rw, rh);
                float rowTop = ry < listTop ? listTop : ry;
                float rowBot = ry + rh > listBot ? listBot : ry + rh;
                int selectionAlpha = 0;
                if (sel) {
                    float sp = easeInOut(selAnim);
                    float sc = 0.8f + 0.2f * sp;
                    float opT = clampF((selAnim - 0.2f) / 0.8f, 0f, 1f);
                    float op = opT * opT * opT;
                    selectionAlpha = Math.round(138f * op * cardAlpha / 255f);
                    float ccx = rx + rw / 2f;
                    float ccy = (rowTop + rowBot) / 2f;
                    qRound(
                            ccx - rw / 2f * sc,
                            ccy - (rowBot - rowTop) / 2f * sc,
                            ccx + rw / 2f * sc,
                            ccy + (rowBot - rowTop) / 2f * sc,
                            7f * s * sc,
                            withAlpha(accent, selectionAlpha));
                } else if (hov) {
                    qRound(rx, rowTop, rx + rw, rowBot, 7f * s, cRaised);
                }
                int col = sel ? contrastText(selectionAlpha) : (hov ? cText : cTextDim);
                txt(fMid, trim(fMid, pretty(category.name()), rw - 22f * s), rx + 12f * s, ry + rh / 2f, col);
                if (clicked(rx, ry, rw, rh) && category != tab) {
                    tab = category;
                    applyCategoryAccent();
                    commitEdit();
                    bindModule = null;
                    bindKey = null;
                    searchFocused = false;
                    listBlur();
                }
            }
            ry += step;
        }
        endClip();
    }

    /** Shared press/drag handling for both vertical scrollbars. Returns the clamped scroll. */
    private float dragScrollbar(
            ScrollDrag drag, float barX, float barW, float top, float viewH, float total, float maxScroll, float sc) {
        if (maxScroll <= 0f || viewH <= 0f) {
            drag.dragging = false;
            return sc;
        }
        float thumbH = thumbHeight(viewH, total);
        float thumbY = top + (viewH - thumbH) * (sc / maxScroll);
        if (pressLeft && inside(barX - 4f * s, top, barW + 8f * s, viewH)) {
            pressLeft = false;
            drag.dragging = true;
            drag.grab = (my >= thumbY && my <= thumbY + thumbH) ? (my - thumbY) : (thumbH / 2f);
        }
        if (drag.dragging && (leftDown || releaseLeft)) {
            float span = viewH - thumbH;
            sc = span <= 0f ? 0f : clampF(((dragY() - drag.grab - top) / span) * maxScroll, 0f, maxScroll);
        }
        return sc;
    }

    /** Scrollbar thumb length, never shorter than 16px (in scaled units). */
    private float thumbHeight(float viewH, float total) {
        float t = total <= 0f ? viewH : viewH * (viewH / total);
        if (t < 16f * s) {
            t = 16f * s;
        }
        return Math.min(Math.max(0f, viewH - s), t);
    }

    /** Draws a vertical scrollbar track + thumb at its current scroll position. */
    private void drawScrollbar(
            float barX,
            float barW,
            float top,
            float viewH,
            float total,
            float scroll,
            float maxScroll,
            boolean dragging) {
        if (maxScroll <= 0f || viewH <= 0f) {
            return;
        }
        float thumbH = thumbHeight(viewH, total);
        float thumbY = top + (viewH - thumbH) * (scroll / maxScroll);
        qRound(barX, top, barX + barW, top + viewH, barW / 2f, 0x22FFFFFF);
        qRound(barX, thumbY, barX + barW, thumbY + thumbH, barW / 2f, dragging ? accent : 0x77FFFFFF);
    }

    /* ------------------------------------------------------------------ module list */

    private void drawContent() {
        float cx = winX + sideW + 8f * s;
        float cw = winW - sideW - 22f * s;
        // Start below the cat's front paws, which reach about 21 px into the window.
        float cTop = winY + 26f * s;
        float cBot = winY + winH - resizeGripSize();
        if (cw <= 0f || cBot <= cTop) {
            return;
        }

        boolean searching = !searchText.isEmpty();
        ArrayList mods = visibleModules();
        int count = mods.size();
        if (count == 0) {
            txt(fMid, trim(fMid, "No modules found.", cw - 8f * s), cx + 4f * s, cTop + 12f * s, cTextFaint);
            return;
        }

        float gap = 6f * s;
        float[] hs = new float[count];
        float total = 0f;
        for (int i = 0; i < count; i++) {
            hs[i] = cardHeight((keystrokesmod.module.Module) mods.get(i));
            total += hs[i] + gap;
        }
        float viewH = cBot - cTop;
        total -= gap;
        float maxScroll = total - viewH;
        if (maxScroll < 0f) {
            maxScroll = 0f;
        }

        float sc = searching ? searchScroll : categoryScroll.getOrDefault(tab, 0f);
        sc = clampF(sc, 0f, maxScroll);

        float barX = winX + winW - 6f * s;
        float barW = SCROLLBAR_W * s;
        sc = dragScrollbar(contentDrag, barX, barW, cTop, viewH, total, maxScroll, sc);
        drawScrollbar(barX, barW, cTop, viewH, total, sc, maxScroll, contentDrag.dragging);

        listX = cx;
        listY = cTop;
        listW = cw;
        listH = viewH;
        beginClip(cx, cTop, cw, viewH);
        float yy = cTop - sc;
        for (int i = 0; i < count; i++) {
            float h = hs[i];
            if (yy + h >= cTop && yy <= cBot) {
                drawCard((keystrokesmod.module.Module) mods.get(i), cx, yy, cw, h);
            }
            yy += h + gap;
        }
        endClip();
        // The nested panel under this frame's pointer has first refusal. Unclaimed wheel moves
        // the outer viewport on its next draw, never both viewports on the same wheel event.
        if (frameWheel != 0 && inside(cx, cTop, winX + winW - cx, viewH)) {
            sc = clampF(sc - frameWheel / 6f, 0f, maxScroll);
            frameWheel = 0;
        }
        if (searching) {
            searchScroll = sc;
        } else {
            categoryScroll.put(tab, sc);
        }
    }

    /* =================================================================== animations */

    private float animFor(String key, boolean isOpen) {
        return cardAnim.towards(key, isOpen, 3.2f, false);
    }

    private float curAnim(String key) {
        return cardAnim.value(key);
    }

    private float toggleProgress(String key, boolean on) {
        return toggleAnim.towards(key, on, 3f, true);
    }

    private float groupProgress(String key, boolean open) {
        return groupAnim.towards(key, open, 3.2f, true);
    }

    private float modeProgress(String key, boolean open) {
        return modeAnim.towards(key, open, 3.2f, true);
    }

    /** Colour picker reveal, driven exactly like the module settings expand. */
    private float colorProgress(ColorPickerState state) {
        if (state.frame != frameId) {
            state.frame = frameId;
            state.reveal = approach(state.reveal, state.open ? 1f : 0f, 3.2f, frameDt);
        }
        return state.reveal;
    }

    private float groupReveal(keystrokesmod.module.Module mod, keystrokesmod.module.setting.impl.GroupSetting g) {
        if (g == null) {
            return 1f;
        }
        return groupProgress("g|" + uiKey(g), g.isOpened());
    }

    private String modeKey(keystrokesmod.module.Module mod, keystrokesmod.module.setting.impl.SliderSetting ss) {
        return uiKey(ss);
    }

    private float cardHeight(keystrokesmod.module.Module mod) {
        String key = uiKey(mod);
        float p = easeInOut(animFor(key, expandedCards.contains(key)));
        float h = CARD_HEAD_H * s;
        if (p > 0.0005f) {
            h += (settingsHeight(mod) + 16f * s) * p;
        }
        return h;
    }

    private void drawCard(keystrokesmod.module.Module mod, float x, float y, float w, float h) {
        String key = uiKey(mod);
        boolean isOpen = expandedCards.contains(key);
        float p = easeInOut(curAnim(key));
        float headH = CARD_HEAD_H * s;
        boolean on = mod.isEnabled();
        boolean headHov = inside(x, y, w, headH);

        float bgTop = y < listY ? listY : y;
        float bgBot = y + h > listY + listH ? listY + listH : y + h;
        qRound(x, bgTop, x + w, bgBot, 5f * s, (headHov && !isOpen) ? cCardHover : cCard);

        float tp = toggleProgress(key, on);
        if (tp > 0.001f && y + headH > listY) {
            float tTop = y < listY ? listY : y;
            int tint = withAlpha(accent, Math.round(40f * onTintFrac));
            float r = 5f * s;
            float wipe = w * tp;
            if (wipe < 2f * r) {
                // Tail of the wipe: clip the leading edge so the accent cannot show outside the
                // card's rounded corner once the tint is narrower than the corner diameter.
                float[] prev = clipTo(x, tTop - s, wipe, (y + headH - tTop) + 2f * s);
                qRound(x, tTop, x + 2f * r, y + headH, r, tint);
                clipRestore(prev);
            } else {
                qRound(x, tTop, x + wipe, y + headH, r, tint);
            }
        }

        float cyH = y + 15.5f * s;
        float togW = 22f * s;
        float togH = 12f * s;
        float togX = x + w - 38f * s;
        float togY = cyH - togH / 2f;

        boolean binding = bindModule == mod;
        float nameRight = x + w - 8f * s;
        if (mod.canBeBound()) {
            String bindText = trim(
                    fSmall,
                    binding ? "..." : keyName(mod.getKeycode()),
                    Math.min(64f * s, Math.max(10f * s, w - 82f * s)));
            float chipW = Math.max(22f * s, tw(fSmall, bindText) + 12f * s);
            float chipH = 18f * s;
            float chipX = nameRight - chipW - (mod.canBeEnabled() ? 42f * s : 0f);
            float chipY = cyH - chipH / 2f;
            nameRight = chipX - 8f * s;
            qRound(chipX, chipY, chipX + chipW, chipY + chipH, 4f * s, cField);
            txt(fSmall, bindText, chipX + chipW / 2f - tw(fSmall, bindText) / 2f, cyH, binding ? cText : cTextDim);
            if (clicked(chipX, chipY, chipW, chipH)) {
                focusControl();
                bindModule = binding ? null : mod;
            } else if (pressRight && inside(chipX, chipY, chipW, chipH)) {
                pressRight = false;
                mod.setBind(0);
                markUnsaved(mod);
                if (binding) {
                    bindModule = null;
                }
            }
        }
        txt(fMid, trim(fMid, mod.getName(), nameRight - (x + 13f * s)), x + 13f * s, cyH, on ? cText : cTextDim);

        if (mod.canBeEnabled()) {
            drawSwitch(togX, togY, togW, togH, key, on);
            if (clicked(togX - 4f * s, togY - 4f * s, togW + 8f * s, togH + 8f * s) || clicked(x, y, w, headH)) {
                toggleModule(mod);
            }
        }

        if (pressRight && inside(x, y, w, headH)) {
            pressRight = false;
            if (isOpen) {
                expandedCards.remove(key);
            } else {
                expandedCards.add(key);
            }
        }

        if (p > 0.001f) {
            float sy = y + headH + 8f * s;
            float shown = settingsHeight(mod) * p;
            qRect(x + 11f * s, y + headH + 4f * s, x + w - 11f * s, y + headH + 5f * s, C_LINE);
            float clipX1 = Math.max(x, listX);
            float clipY1 = Math.max(sy - 2f * s, listY);
            float clipX2 = Math.min(x + w, listX + listW);
            float clipY2 = Math.min(sy - 2f * s + shown + 4f * s, listY + listH);
            scissorOn(clipX1, clipY1, clipX2 - clipX1, clipY2 - clipY1);
            drawSettings(mod, x + 10f * s, sy, w - 20f * s);
            beginClip(listX, listY, listW, listH);
        }
    }

    private void drawSwitch(float tx, float ty, float tw, float th, String key, boolean on) {
        float p = toggleProgress(key, on);
        // The switch is a "button", so it reads 15% above the cards/settings behind it.
        qRound(
                tx,
                ty,
                tx + tw,
                ty + th,
                th / 2f,
                mixColor(withAlpha(C_TOGGLE_OFF, switchAlpha), withAlpha(accent, switchAlpha), p));
        float kn = th - 3f * s;
        float kx = (tx + 1.5f * s) + (tw - kn - 3f * s) * p;
        qRound(
                kx,
                ty + 1.5f * s,
                kx + kn,
                ty + th - 1.5f * s,
                kn / 2f,
                mixColor(withAlpha(C_KNOB_OFF, switchAlpha), withAlpha(cText, switchAlpha), p));
    }

    /* -------------------------------------------------------- reload-safe actions */

    /** Runs a setting's action without the reload it triggers tripping over this render pass.
     *  Raven dispatches callbacks by iterating its live script map, so an action that reloads
     *  scripts would edit that map mid-iteration and crash. Handing the registry a copy first
     *  keeps the running loop on its original map while the reload edits the copy. */
    private void runSafely(final Runnable action) {
        try {
            keystrokesmod.script.ScriptManager mgr = keystrokesmod.Feral.scriptManager;
            mgr.scripts = new LinkedHashMap(mgr.scripts);
        } catch (Throwable ignored) {
        }
        action.run();
    }

    /* ---------------------------------------------------------- settings rendering */

    private float settingsHeight(keystrokesmod.module.Module mod) {
        float h = 0f;
        List<Setting> list = orderedSettings(mod);
        for (int i = 0; i < list.size(); i++) {
            keystrokesmod.module.setting.Setting st = (keystrokesmod.module.setting.Setting) list.get(i);
            if (!visibleSetting(mod, st, i)) {
                continue;
            }
            float rh = rowHeight(mod, st);
            keystrokesmod.module.setting.impl.GroupSetting g = groupOf(st);
            if (g != null) {
                rh *= groupReveal(mod, g);
            }
            h += rh;
        }
        if (showsArrayToggle(mod)) {
            h += 22f * s;
        }
        if (h <= 0f) {
            h = 20f * s;
        }
        return h;
    }

    private float rowHeight(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        if (isListSetting(st)) {
            return listSettingHeight(mod, st);
        }
        if (st instanceof keystrokesmod.module.setting.impl.SliderSetting) {
            keystrokesmod.module.setting.impl.SliderSetting ss = (keystrokesmod.module.setting.impl.SliderSetting) st;
            if (ss.isString) {
                return 17f * s + modeBoxHeight(mod, ss) + 3f * s;
            }
            return 32f * s;
        }
        if (st instanceof keystrokesmod.module.setting.impl.ColorSetting) {
            keystrokesmod.module.setting.impl.ColorSetting cs = (keystrokesmod.module.setting.impl.ColorSetting) st;
            float base = 26f * s;
            float reveal = easeInOut(colorProgress(pickers.computeIfAbsent(cs, ignored -> new ColorPickerState())));
            if (reveal > 0.0005f) {
                base += colorPickerHeight() * reveal;
            }
            return base;
        }
        if (st instanceof keystrokesmod.module.setting.impl.KeySetting) {
            return 26f * s;
        }
        if (st instanceof keystrokesmod.module.setting.impl.TextSetting) {
            return 50f * s;
        }
        if (st instanceof keystrokesmod.module.setting.impl.DescriptionSetting) {
            return 16f * s;
        }
        return 22f * s;
    }

    private float modeBoxHeight(keystrokesmod.module.Module mod, keystrokesmod.module.setting.impl.SliderSetting ss) {
        String[] options = ss.getOptions();
        int n = options == null ? 0 : options.length;
        float h = 22f * s;
        if (n > 1) {
            h += modeExtraHeight(ss)
                    * easeInOut(modeProgress(modeKey(mod, ss), expandedModes.contains(modeKey(mod, ss))));
        }
        return h;
    }

    /** Height of the option list that appears below a multi-option box when expanded. */
    private float modeExtraHeight(keystrokesmod.module.setting.impl.SliderSetting ss) {
        String[] options = ss.getOptions();
        int n = options == null ? 0 : options.length;
        return n > 1 ? (n - 1) * 17f * s + 4f * s : 0f;
    }

    private void drawSettings(keystrokesmod.module.Module mod, float x, float y, float w) {
        List<Setting> list = orderedSettings(mod);
        float cy = y;
        int drawn = 0;
        for (int i = 0; i < list.size(); i++) {
            keystrokesmod.module.setting.Setting st = (keystrokesmod.module.setting.Setting) list.get(i);
            if (!visibleSetting(mod, st, i)) {
                continue;
            }
            float rh = rowHeight(mod, st);
            keystrokesmod.module.setting.impl.GroupSetting g = groupOf(st);
            if (g != null) {
                float gp = groupReveal(mod, g);
                if (gp <= 0.0005f) {
                    continue;
                }
                float indent = 7f * s;
                float slot = rh * gp;
                if (gp < 0.999f) {
                    float[] outer = saveClip();
                    float ix1 = Math.max(outer[0], x);
                    float iy1 = Math.max(outer[1], cy);
                    float ix2 = Math.min(outer[0] + outer[2], x + w);
                    float iy2 = Math.min(outer[1] + outer[3], cy + slot);
                    scissorOn(ix1, iy1, ix2 - ix1, iy2 - iy1);
                    drawSettingRow(mod, st, x + indent, cy, w - indent);
                    clipRestore(outer);
                } else {
                    drawSettingRow(mod, st, x + indent, cy, w - indent);
                }
                cy += slot;
                drawn++;
            } else {
                drawSettingRow(mod, st, x, cy, w);
                cy += rh;
                drawn++;
            }
        }
        if (showsArrayToggle(mod)) {
            drawArrayRow(mod, x, cy, w);
        } else if (drawn == 0) {
            txt(fSmall, "No settings", x, cy + 8f * s, cTextFaint);
        }
    }

    private void drawSettingRow(
            keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st, float x, float y, float w) {
        if (st instanceof keystrokesmod.module.setting.impl.GroupSetting) {
            drawGroupRow((keystrokesmod.module.setting.impl.GroupSetting) st, x, y, w);
        } else if (st instanceof keystrokesmod.module.setting.impl.DescriptionSetting) {
            txt(
                    fSmall,
                    trim(fSmall, ((keystrokesmod.module.setting.impl.DescriptionSetting) st).getDesc(), w),
                    x,
                    y + 8f * s,
                    cTextFaint);
        } else if (st instanceof keystrokesmod.module.setting.impl.ButtonSetting) {
            drawBoolRow(mod, (keystrokesmod.module.setting.impl.ButtonSetting) st, x, y, w);
        } else if (st instanceof keystrokesmod.module.setting.impl.SliderSetting) {
            keystrokesmod.module.setting.impl.SliderSetting ss = (keystrokesmod.module.setting.impl.SliderSetting) st;
            if (ss.isString) {
                drawModeRow(mod, ss, x, y, w);
            } else {
                drawSliderRow(mod, ss, x, y, w);
            }
        } else if (st instanceof keystrokesmod.module.setting.impl.ColorSetting) {
            drawColorRow(mod, (keystrokesmod.module.setting.impl.ColorSetting) st, x, y, w);
        } else if (st instanceof keystrokesmod.module.setting.impl.KeySetting) {
            drawKeyRow((keystrokesmod.module.setting.impl.KeySetting) st, x, y, w);
        } else if (st instanceof keystrokesmod.module.setting.impl.TextSetting) {
            drawTextRow(mod, (keystrokesmod.module.setting.impl.TextSetting) st, x, y, w);
        } else if (isListSetting(st)) {
            drawListRow(mod, st, x, y, w);
        } else {
            txt(fSmall, trim(fSmall, st.getName(), w), x, y + 11f * s, cTextDim);
        }
    }

    private void drawGroupRow(keystrokesmod.module.setting.impl.GroupSetting gs, float x, float y, float w) {
        float cy = y + 8f * s;
        txt(fSmall, gs.isOpened() ? "-" : "+", x, cy, accent);
        txt(fSmall, trim(fSmall, gs.getName().toUpperCase(), w - 12f * s), x + 10f * s, cy, cTextDim);
        qRect(x, y + 19f * s, x + w, y + 20f * s, C_LINE);
        if (activated(x, y, w, 22f * s)) {
            gs.setOpened(!gs.isOpened());
        }
    }

    /** Applies a pending text edit the way pressing Enter does, minus the setting's own submit
     *  action - run buttons read the setting's text, so a pending edit has to land first. */
    private void commitEdit() {
        if (editing != null) {
            if (!editBuffer.equals(editing.getText())) {
                markUnsaved(editingModule);
            }
            editing.setText(editBuffer);
            editing = null;
            editingModule = null;
            editBuffer = "";
        }
    }

    private void focusControl() {
        commitEdit();
        listBlur();
        searchFocused = false;
        bindKey = null;
        bindModule = null;
    }

    private void drawBoolRow(
            keystrokesmod.module.Module mod,
            keystrokesmod.module.setting.impl.ButtonSetting bs,
            float x,
            float y,
            float w) {
        drawRowHover(x, y, w);

        if (bs.isMethodButton) {
            float cy = y + 11f * s;
            String label = "RUN";
            float pw = tw(fSmall, label) + 12f * s;
            float ph = 14f * s;
            float px = x + w - pw;
            float py = cy - ph / 2f;
            qRound(px, py, px + pw, py + ph, ph / 2f, inside(px, py, pw, ph) ? accent : cRaised);
            txt(fSmall, label, px + pw / 2f - tw(fSmall, label) / 2f, cy, cText);
            txt(fSmall, trim(fSmall, bs.getName(), w - pw - 8f * s), x, cy, cText);
            if (clicked(px, py - 2f * s, pw, ph + 4f * s)) {
                commitEdit();
                final keystrokesmod.module.setting.impl.ButtonSetting button = bs;
                runSafely(new Runnable() {
                    public void run() {
                        button.runMethod();
                    }
                });
            }
            return;
        }

        boolean on = bs.isToggled();
        if (drawToggleRow("b|" + uiKey(bs), bs.getName(), on, on ? accent : cTextDim, x, y, w)) {
            bs.toggle();
            mod.guiButtonToggled(bs);
            markUnsaved(mod);
        }
    }

    private void drawArrayRow(keystrokesmod.module.Module mod, float x, float y, float w) {
        drawRowHover(x, y, w);
        boolean shown = !mod.isHidden();
        if (drawToggleRow("a|" + mod.getName(), "Show in arraylist", shown, shown ? cText : cTextDim, x, y, w)) {
            mod.setHidden(shown);
            markUnsaved(mod);
        }
    }

    /** Hover highlight shared by the full-width setting rows. */
    private void drawRowHover(float x, float y, float w) {
        if (inside(x - 4f * s, y, w + 8f * s, 22f * s)) {
            qRound(x - 4f * s, y, x + w + 4f * s, y + 22f * s, 4f * s, C_HOVER);
        }
    }

    /** Draws the label + animated switch of a full-width toggle row, returning true when clicked. */
    private boolean drawToggleRow(String key, String label, boolean on, int labelColor, float x, float y, float w) {
        float h = 22f * s;
        float cy = y + h / 2f;
        float togW = 22f * s;
        float togH = 11f * s;
        drawSwitch(x + w - togW, cy - togH / 2f, togW, togH, key, on);
        txt(fSmall, trim(fSmall, label, w - togW - 8f * s), x, cy, labelColor);
        return activated(x - 4f * s, y, w + 8f * s, h);
    }

    private void drawSliderRow(
            keystrokesmod.module.Module mod,
            keystrokesmod.module.setting.impl.SliderSetting ss,
            float x,
            float y,
            float w) {
        double value = ss.getInput();
        boolean disabled = ss.canBeDisabled && value == -1.0D;

        String valueText;
        if (disabled) {
            String ms = ss.getMinString();
            valueText = ms == null ? "OFF" : ms;
        } else {
            valueText = fmt(value) + ss.getSuffix();
        }
        float labelCy = y + 8f * s;
        valueText = trim(fSmall, valueText, w * 0.55f);
        float vw = tw(fSmall, valueText);
        txt(fSmall, trim(fSmall, ss.getName(), w - vw - 8f * s), x, labelCy, cText);
        txt(fSmall, valueText, x + w - vw, labelCy, disabled ? cTextFaint : accent);

        float barY = y + 25.5f * s;
        float barH = 3f * s;
        qRound(x, barY - barH / 2f, x + w, barY + barH / 2f, barH / 2f, C_TOGGLE_OFF);

        double min = ss.getMin();
        double max = ss.getMax();
        double span = max - min;
        if (span <= 0.0D) {
            span = 1.0D;
        }
        float frac = disabled ? 0f : clampF((float) ((value - min) / span), 0f, 1f);
        if (frac > 0f) {
            qRound(x, barY - barH / 2f, x + w * frac, barY + barH / 2f, barH / 2f, accent);
        }
        float knobD = 9f * s;
        float kx = x + w * frac;
        qRound(kx - knobD / 2f, barY - knobD / 2f, kx + knobD / 2f, barY + knobD / 2f, knobD / 2f, cText);

        if (pressLeft && inside(x - 2f * s, y + 16f * s, w + 4f * s, 18f * s)) {
            pressLeft = false;
            dragSlider = ss;
        }
        if (dragSlider == ss && (leftDown || releaseLeft)) {
            float d = clampF(dragX() - x, 0f, w);
            if (d <= 0.5f && ss.canBeDisabled) {
                ss.setValueRaw(-1.0D);
            } else {
                ss.setValue(min + (double) (d / w) * span);
            }
            markUnsaved(mod);
        }
    }

    private void drawModeRow(
            keystrokesmod.module.Module mod,
            keystrokesmod.module.setting.impl.SliderSetting ss,
            float x,
            float y,
            float w) {
        String[] options = ss.getOptions();
        if (options == null || options.length == 0) {
            return;
        }
        int idx = clampI((int) Math.round(ss.getInput()), 0, options.length - 1);
        String key = modeKey(mod, ss);
        boolean ex = expandedModes.contains(key);
        float reveal = easeInOut(modeProgress(key, ex));

        txt(fSmall, trim(fSmall, ss.getName(), w - 8f * s), x, y + 8f * s, cText);

        float boxY = y + 17f * s;
        float boxH = 22f * s;
        float boxTotal = boxH + modeExtraHeight(ss) * reveal;
        qRound(x, boxY, x + w, boxY + boxTotal, 5f * s, cField);
        txt(fSmall, trim(fSmall, options[idx], w - 18f * s), x + 9f * s, boxY + boxH / 2f, cText);

        if (activated(x, boxY, w, boxH)) {
            if (ex) {
                expandedModes.remove(key);
            } else {
                expandedModes.add(key);
            }
        }
        if (reveal > 0.0005f) {
            float[] outer = saveClip();
            float cx1 = Math.max(outer[0], x);
            float cy1 = Math.max(outer[1], boxY);
            float cx2 = Math.min(outer[0] + outer[2], x + w);
            float cy2 = Math.min(outer[1] + outer[3], boxY + boxTotal);
            scissorOn(cx1, cy1, cx2 - cx1, cy2 - cy1);
            int row = 0;
            for (int k = 0; k < options.length; k++) {
                if (k == idx) {
                    continue;
                }
                float ry = boxY + boxH + row * 17f * s;
                boolean visible = ry + 17f * s <= boxY + boxTotal;
                boolean ohov = visible && inside(x + 3f * s, ry, w - 6f * s, 17f * s);
                if (ohov) {
                    qRound(x + 3f * s, ry, x + w - 3f * s, ry + 17f * s, 4f * s, C_HOVER);
                }
                txt(fSmall, trim(fSmall, options[k], w - 18f * s), x + 9f * s, ry + 8.5f * s, ohov ? cText : cTextDim);
                if (visible && activated(x + 3f * s, ry, w - 6f * s, 17f * s)) {
                    ss.setValue((double) k);
                    expandedModes.remove(key);
                    markUnsaved(mod);
                    clipRestore(outer);
                    return;
                }
                row++;
            }
            clipRestore(outer);
        }
    }

    private void drawColorRow(
            keystrokesmod.module.Module mod,
            keystrokesmod.module.setting.impl.ColorSetting cs,
            float x,
            float y,
            float w) {
        ColorPickerState state = pickers.computeIfAbsent(cs, ignored -> new ColorPickerState());
        float rowH = 26f * s;
        float cy = y + rowH / 2f;
        float sw = 18f * s;
        float swX = x + w - sw;
        float swY = cy - sw / 2f;
        String hex = formatHex(cs);
        float hexW = tw(fSmall, hex);

        txt(fSmall, trim(fSmall, cs.getName(), w - sw - hexW - 16f * s), x, cy, cText);
        txt(fSmall, hex, swX - 8f * s - hexW, cy, cTextDim);
        qRound(swX - 1.5f * s, swY - 1.5f * s, swX + sw + 1.5f * s, swY + sw + 1.5f * s, 5f * s, cRaised);
        if (!cs.hasAlpha()
                || !PickerGradient.draw(
                        PickerGradient.SWATCH,
                        swX,
                        swY,
                        sw,
                        sw,
                        4f * s,
                        scale,
                        PICKER_CHECK * s,
                        cs.getColor(),
                        fadeMul)) {
            qRound(swX, swY, swX + sw, swY + sw, 4f * s, cs.getColor());
        }

        if (activated(x, y, w, rowH)) {
            state.open = !state.open;
        }
        float reveal = easeInOut(colorProgress(state));
        if (reveal > 0.0005f) {
            float[] outer = clipTo(x, y + rowH, w, colorPickerHeight() * reveal);
            if (outer != null) {
                drawColorPicker(mod, cs, state, x + 7f * s, y + rowH, w - 7f * s);
                clipRestore(outer);
            }
        }
    }

    /* ------------------------------------------------------------------ colour picker */

    /** Height of the expanded colour picker drawn below the colour row. */
    private float colorPickerHeight() {
        return (PICKER_PAD_TOP + PICKER_H + PICKER_PAD_BOTTOM) * s;
    }

    /** Saturation/brightness square plus hue and alpha bars, Raven's colour picker look. */
    private void drawColorPicker(
            keystrokesmod.module.Module mod, ColorSetting cs, ColorPickerState state, float x, float y, float w) {
        boolean hasAlpha = cs.hasAlpha();
        float barW = PICKER_BAR_W * s;
        float gap = PICKER_GAP * s;
        float sqX = x;
        float sqY = y + PICKER_PAD_TOP * s;
        float sqH = PICKER_H * s;
        float sqW = w - barW - gap - (hasAlpha ? barW + gap : 0f);
        if (sqW <= 0f || sqH <= 0f) {
            return;
        }
        float sqR = sqX + sqW;
        float sqB = sqY + sqH;
        float hueX = sqR + gap;
        float hueR = hueX + barW;
        float alphaX = hueR + gap;
        float alphaR = alphaX + barW;
        float r = PICKER_R * s;
        if (r > sqH / 2f) {
            r = sqH / 2f;
        }

        state.sync(cs);
        if (state.open && pressLeft) {
            int part = inside(sqX, sqY, sqW, sqH)
                    ? 0
                    : inside(hueX, sqY, barW, sqH) ? 1 : hasAlpha && inside(alphaX, sqY, barW, sqH) ? 2 : -1;
            if (part >= 0) {
                pressLeft = false;
                dragColorSetting = cs;
                dragColorPart = part;
            }
        }
        if (state.open && dragColorSetting == cs) {
            sawColorDrag = true;
            if (leftDown || releaseLeft) {
                int before = cs.getColor();
                if (dragColorPart == 0) {
                    state.saturation = clampF((dragX() - sqX) / sqW, 0f, 1f);
                    state.brightness = clampF(1f - (dragY() - sqY) / sqH, 0f, 1f);
                    state.commit(cs);
                } else if (dragColorPart == 1) {
                    state.hue = clampF((dragY() - sqY) / sqH, 0f, 1f) * 360f;
                    state.commit(cs);
                } else {
                    cs.setAlpha(Math.round(clampF((dragY() - sqY) / sqH, 0f, 1f) * 255f));
                }
                if (before != cs.getColor()) {
                    markUnsaved(mod);
                }
            }
        }

        /* Frames: rounded a unit wider than the content, so the two keep concentric corners. */
        float fr = r + 1f * s;
        qRound(sqX - 1f * s, sqY - 1f * s, sqR + 1f * s, sqB + 1f * s, fr, cRaised);
        qRound(hueX - 1f * s, sqY - 1f * s, hueR + 1f * s, sqB + 1f * s, fr, cRaised);
        if (hasAlpha) {
            qRound(alphaX - 1f * s, sqY - 1f * s, alphaR + 1f * s, sqB + 1f * s, fr, cRaised);
        }

        int hueRgb = Color.HSBtoRGB(state.hue / 360f, 1f, 1f);
        if (!PickerGradient.draw(PickerGradient.SATURATION_VALUE, sqX, sqY, sqW, sqH, r, scale, 1f, hueRgb, fadeMul)) {
            gradH(sqX, sqY, sqR, sqB, C_WHITE, hueRgb);
            gradV(sqX, sqY, sqR, sqB, 0, 0xFF000000);
        }
        if (!PickerGradient.draw(PickerGradient.HUE, hueX, sqY, barW, sqH, r, scale, 1f, 0, fadeMul)) {
            for (int i = 0; i < 6; i++) {
                gradV(
                        hueX,
                        sqY + sqH * i / 6f,
                        hueR,
                        sqY + sqH * (i + 1) / 6f,
                        Color.HSBtoRGB(i / 6f, 1f, 1f),
                        Color.HSBtoRGB((i + 1) / 6f, 1f, 1f));
            }
        }
        if (hasAlpha) {
            if (!PickerGradient.draw(
                    PickerGradient.ALPHA, alphaX, sqY, barW, sqH, r, scale, PICKER_CHECK * s, cs.getColor(), fadeMul)) {
                gradV(alphaX, sqY, alphaR, sqB, 0xFF666666, 0xFF000000 | cs.getRGB());
            }
        }

        float marker = 2.5f * s;
        float indX = clampF(sqX + state.saturation * sqW, sqX + marker, sqR - marker);
        float indY = clampF(sqY + (1f - state.brightness) * sqH, sqY + marker, sqB - marker);
        qRound(indX - marker, indY - marker, indX + marker, indY + marker, marker, C_WHITE);
        qRound(indX - marker / 2f, indY - marker / 2f, indX + marker / 2f, indY + marker / 2f, marker / 2f, C_DARK);
        drawPickerMarker(hueX, hueR, sqY, sqH, state.hue / 360f);
        if (hasAlpha) {
            drawPickerMarker(alphaX, alphaR, sqY, sqH, cs.getAlpha() / 255f);
        }
    }

    private void drawPickerMarker(float left, float right, float top, float height, float progress) {
        float cy = top + clampF(progress * height, s, height - s);
        qRect(left, cy - s, right, cy + s, C_DARK);
        qRect(left + s, cy - 0.5f * s, right - s, cy + 0.5f * s, C_WHITE);
    }

    /* ------------------------------------------------------------ key/text rows */

    private void drawKeyRow(keystrokesmod.module.setting.impl.KeySetting ks, float x, float y, float w) {
        float rowH = 26f * s;
        float cy = y + rowH / 2f;
        boolean binding = bindKey == ks;
        String text = binding ? "..." : keyName(ks.getKey());
        float pw = Math.min(w * 0.55f, Math.max(48f * s, tw(fSmall, text) + 16f * s));
        text = trim(fSmall, text, pw - 10f * s);
        float ph = 20f * s;
        float px = x + w - pw;
        float py = cy - ph / 2f;

        txt(fSmall, trim(fSmall, ks.getName(), w - pw - 8f * s), x, cy, cText);
        qRound(px, py, px + pw, py + ph, 5f * s, cField);
        txt(
                fSmall,
                text,
                px + pw / 2f - tw(fSmall, text) / 2f,
                cy,
                binding ? cText : (ks.getKey() != 0 ? accent : cTextDim));

        if (clicked(px, py, pw, ph)) {
            if (binding) {
                bindKey = null;
            } else {
                focusControl();
                bindKey = ks;
            }
        } else if (pressRight && inside(px, py, pw, ph)) {
            pressRight = false;
            ks.setKey(0);
            if (binding) {
                bindKey = null;
            }
        }
    }

    private void drawTextRow(Module mod, keystrokesmod.module.setting.impl.TextSetting ts, float x, float y, float w) {
        boolean active = editing == ts;
        txt(fSmall, trim(fSmall, ts.getName(), w - 8f * s), x, y + 7f * s, cText);

        float by = y + 20f * s;
        float bh = 24f * s;
        if (active && intersectsClip(x, by, w, bh)) {
            sawEdit = true;
        }
        qRound(x, by, x + w, by + bh, 5f * s, active ? cRaised : cField);

        String shown;
        int color;
        if (active) {
            shown = editBuffer + ((client.time() / 500L) % 2L == 0L ? "_" : "");
            color = cText;
        } else if (ts.getText() != null && ts.getText().length() > 0) {
            shown = ts.getText();
            color = cText;
        } else {
            shown = ts.getPlaceholder();
            color = cTextFaint;
        }
        txt(fSmall, trimLeft(fSmall, shown, w - 16f * s), x + 8f * s, by + bh / 2f, color);

        if (clicked(x, by, w, bh)) {
            if (active) {
                final keystrokesmod.module.setting.impl.TextSetting target = editing;
                commitEdit();
                runSafely(new Runnable() {
                    public void run() {
                        target.submit();
                    }
                });
            } else {
                focusControl();
                editing = ts;
                editingModule = mod;
                editBuffer = ts.getText() == null ? "" : ts.getText();
                sawEdit = true;
            }
        }
    }

    /* ---------------------------------------------------------------- list settings */

    private boolean isBlockList(keystrokesmod.module.setting.Setting st) {
        return st instanceof keystrokesmod.module.setting.impl.BlockListSetting;
    }

    private boolean isPotionList(keystrokesmod.module.setting.Setting st) {
        return st instanceof keystrokesmod.module.setting.impl.PotionListSetting;
    }

    private boolean isItemList(keystrokesmod.module.setting.Setting st) {
        return st instanceof keystrokesmod.module.setting.impl.ItemListSetting;
    }

    private boolean isStringList(keystrokesmod.module.setting.Setting st) {
        return st instanceof keystrokesmod.module.setting.impl.StringListSetting;
    }

    private boolean isPlayerList(keystrokesmod.module.setting.Setting st) {
        return st instanceof keystrokesmod.module.setting.impl.PlayerListSetting;
    }

    private boolean isSearchableList(keystrokesmod.module.setting.Setting st) {
        return st == HUD.notificationBlacklist || isPotionList(st) || isItemList(st) || isBlockList(st);
    }

    private boolean isListSetting(keystrokesmod.module.setting.Setting st) {
        return isSearchableList(st) || isStringList(st) || isPlayerList(st);
    }

    private String listKey(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        return uiKey(st);
    }

    private boolean isFocusedList(keystrokesmod.module.setting.Setting st) {
        return listSearchSetting == st;
    }

    private String listPlaceholder(keystrokesmod.module.setting.Setting st) {
        if (isPotionList(st)) {
            return "Search potions...";
        }
        if (isItemList(st)) {
            return "Search items...";
        }
        if (isBlockList(st)) {
            return "Search blocks...";
        }
        if (isStringList(st)) {
            return ((keystrokesmod.module.setting.impl.StringListSetting) st).getPlaceholder();
        }
        if (isPlayerList(st)) {
            return ((keystrokesmod.module.setting.impl.PlayerListSetting) st).getPlaceholder();
        }
        return "Type...";
    }

    private int listSelectedCount(keystrokesmod.module.setting.Setting st) {
        if (isStringList(st)) {
            return ((keystrokesmod.module.setting.impl.StringListSetting) st)
                    .getEntries()
                    .size();
        }
        if (isPlayerList(st)) {
            return ((keystrokesmod.module.setting.impl.PlayerListSetting) st)
                    .getEntries()
                    .size();
        }
        if (isBlockList(st)) {
            return ((keystrokesmod.module.setting.impl.BlockListSetting) st)
                    .getBlocks()
                    .size();
        }
        return 0;
    }

    /** displayName, storageId, iconId (registry id or null) for one selected entry. */
    private Object[] listSelectedRow(keystrokesmod.module.setting.Setting st, int i) {
        if (isStringList(st)) {
            String e = (String) ((keystrokesmod.module.setting.impl.StringListSetting) st)
                    .getEntries()
                    .get(i);
            return new Object[] {e, e, null};
        }
        if (isPlayerList(st)) {
            keystrokesmod.utility.PlayerRelationsManager.PlayerEntry e =
                    ((keystrokesmod.module.setting.impl.PlayerListSetting) st)
                            .getEntries()
                            .get(i);
            return new Object[] {e.getDisplayName(), e.getKey(), null};
        }
        if (isPotionList(st)) {
            String id = (String) ((keystrokesmod.module.setting.impl.PotionListSetting) st)
                    .getPotions()
                    .get(i);
            return new Object[] {keystrokesmod.utility.PotionSearchIndex.getDisplayName(id), id, null};
        }
        if (isItemList(st)) {
            String id = (String) ((keystrokesmod.module.setting.impl.ItemListSetting) st)
                    .getItems()
                    .get(i);
            return new Object[] {keystrokesmod.utility.ItemSearchIndex.getDisplayName(id), id, id};
        }
        String id = (String) ((keystrokesmod.module.setting.impl.BlockListSetting) st)
                .getBlocks()
                .get(i);
        return new Object[] {keystrokesmod.utility.BlockSearchIndex.getDisplayName(id), id, id};
    }

    private void listAdd(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st, String id) {
        if (id == null) {
            return;
        }
        if (st == HUD.notificationBlacklist) {
            if (!HUD.notificationBlacklist.addEntry(id)) {
                return;
            }
        } else {
            ((keystrokesmod.module.setting.impl.BlockListSetting) st).addBlock(id);
        }
        markUnsaved(mod);
        listQuery = "";
        listCollapseGroup();
        listBlur();
    }

    private void listRemove(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st, String id) {
        if (id == null) {
            return;
        }
        if (isStringList(st)) {
            ((keystrokesmod.module.setting.impl.StringListSetting) st).removeEntry(id);
        } else if (isPlayerList(st)) {
            ((keystrokesmod.module.setting.impl.PlayerListSetting) st).removePlayer(id);
        } else {
            ((keystrokesmod.module.setting.impl.BlockListSetting) st).removeBlock(id);
        }
        markUnsaved(mod);
        listRowsKey = null;
    }

    private void listFocus(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        commitEdit();
        bindKey = null;
        bindModule = null;
        String key = listKey(mod, st);
        if (!key.equals(listSearchKey)) {
            listQuery = "";
            listCollapseGroup();
        }
        listSearchMod = mod;
        listSearchSetting = st;
        listSearchKey = key;
        listRowsKey = null;
        listOpen = true;
        sawListFocus = true;
        searchFocused = false;
    }

    private void listCollapseGroup() {
        listExpandedGroup = null;
        listExpandedLabel = null;
        listExpandedAll = null;
        listExpandedVariants = new ArrayList();
        listRowsKey = null;
        listDropScroll = 0f;
    }

    /** Starts the closing animation: the panel keeps its rows until it has shrunk away. */
    private void listBlur() {
        listOpen = false;
        listSearchKey = null; // next focus starts from a fresh query
    }

    /** Drops all list state (used when the menu closes or the animation has finished). */
    private void listFinalizeClose() {
        listOpen = false;
        listSearchKey = null;
        listSearchMod = null;
        listSearchSetting = null;
        listExpandedGroup = null;
        listExpandedLabel = null;
        listExpandedAll = null;
        listExpandedVariants = new ArrayList();
        listDropRows = new ArrayList();
        listRowsKey = null;
        listDropScroll = 0f;
    }

    private void listSubmit(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        if (listExpandedGroup != null) {
            listCollapseGroup();
            return;
        }
        if (st == HUD.notificationBlacklist) {
            // The query may have changed in the same batch of key events as Enter.
            listEnsureRows(mod, st);
            if (!listDropRows.isEmpty()) {
                listRowClick(mod, st, (Object[]) listDropRows.get(0));
            }
            return;
        }
        if (isStringList(st)) {
            if (((keystrokesmod.module.setting.impl.StringListSetting) st).addEntry(listQuery)) {
                markUnsaved(mod);
                listQuery = "";
                listBlur();
            }
            return;
        }
        if (isPlayerList(st)) {
            if (((keystrokesmod.module.setting.impl.PlayerListSetting) st).addPlayer(listQuery)) {
                markUnsaved(mod);
                listQuery = "";
                listBlur();
            }
            return;
        }
        listBlur();
    }

    private void listExpandGroup(String registryId, String label, String allId) {
        listExpandedGroup = registryId;
        listExpandedLabel = label;
        listExpandedAll = allId;
        listExpandedVariants = new ArrayList();
        List variants;
        if (isItemList(listSearchSetting)) {
            variants = keystrokesmod.utility.ItemSearchIndex.getVariants(registryId);
            for (int i = 0; i < variants.size(); i++) {
                keystrokesmod.utility.ItemSearchIndex.ItemEntry e =
                        (keystrokesmod.utility.ItemSearchIndex.ItemEntry) variants.get(i);
                if (((keystrokesmod.module.setting.impl.ItemListSetting) listSearchSetting).containsItem(e.storageId)) {
                    continue;
                }
                listExpandedVariants.add(new Object[] {e.displayName, e.storageId});
            }
        } else {
            variants = keystrokesmod.utility.BlockSearchIndex.getVariants(registryId);
            for (int i = 0; i < variants.size(); i++) {
                keystrokesmod.utility.BlockSearchIndex.BlockEntry e =
                        (keystrokesmod.utility.BlockSearchIndex.BlockEntry) variants.get(i);
                if (((keystrokesmod.module.setting.impl.BlockListSetting) listSearchSetting).contains(e.storageId)) {
                    continue;
                }
                listExpandedVariants.add(new Object[] {e.displayName, e.storageId});
            }
        }
        listRowsKey = null;
        listDropScroll = 0f;
    }

    /** Rebuilds the dropdown rows when the focused setting, query or selection changed. */
    private void listEnsureRows(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        if (!isFocusedList(st) || !listOpen) {
            return;
        }
        String key = listKey(mod, st) + "|" + listQuery + "|" + (listExpandedGroup == null ? "" : listExpandedGroup)
                + "|" + listSelectedCount(st);
        if (key.equals(listRowsKey)) {
            return;
        }
        listRowsKey = key;
        listDropRows = new ArrayList();

        if (!isSearchableList(st)) {
            return;
        }

        if (st == HUD.notificationBlacklist) {
            String query = listQuery.trim().toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>(LIST_MAX_ROWS);
            for (Object entry : allModules()) {
                String name = ((Module) entry).getName();
                if (!name.toLowerCase(Locale.ROOT).contains(query)
                        || HUD.notificationBlacklist.containsEntry(name)
                        || matches.stream().anyMatch(name::equalsIgnoreCase)) {
                    continue;
                }
                matches.add(name);
                listDropRows.add(new Object[] {name, null, Integer.valueOf(0), name, null, null, null});
                if (matches.size() == LIST_MAX_ROWS) {
                    break;
                }
            }
            return;
        }

        if (listExpandedGroup != null) {
            listDropRows.add(new Object[] {"<< Back", null, Integer.valueOf(2), null, null, null, null});
            listDropRows.add(new Object[] {
                listExpandedLabel + " (All)", listExpandedAll, Integer.valueOf(3), listExpandedAll, null, null, null
            });
            for (int i = 0; i < listExpandedVariants.size(); i++) {
                Object[] v = (Object[]) listExpandedVariants.get(i);
                listDropRows.add(new Object[] {v[0], v[1], Integer.valueOf(0), v[1], null, null, null});
            }
            return;
        }

        if (isPotionList(st)) {
            List res = keystrokesmod.utility.PotionSearchIndex.search(
                    listQuery, (keystrokesmod.module.setting.impl.PotionListSetting) st);
            for (int i = 0; i < res.size(); i++) {
                keystrokesmod.utility.PotionSearchIndex.PotionEntry e =
                        (keystrokesmod.utility.PotionSearchIndex.PotionEntry) res.get(i);
                listDropRows.add(new Object[] {e.displayName, null, Integer.valueOf(0), e.key, null, null, null});
            }
            return;
        }

        if (isItemList(st)) {
            List res = keystrokesmod.utility.ItemSearchIndex.searchGrouped(
                    listQuery, (keystrokesmod.module.setting.impl.ItemListSetting) st);
            for (int i = 0; i < res.size(); i++) {
                keystrokesmod.utility.ItemSearchIndex.GroupedItemResult r =
                        (keystrokesmod.utility.ItemSearchIndex.GroupedItemResult) res.get(i);
                if (r.isSingleVariant()) {
                    keystrokesmod.utility.ItemSearchIndex.ItemEntry e =
                            (keystrokesmod.utility.ItemSearchIndex.ItemEntry) r.variants.get(0);
                    listDropRows.add(
                            new Object[] {e.displayName, e.storageId, Integer.valueOf(0), e.storageId, null, null, null
                            });
                } else {
                    listDropRows.add(new Object[] {
                        r.getGroupLabel(),
                        r.registryId,
                        Integer.valueOf(1),
                        null,
                        r.registryId,
                        r.getGroupDisplayName(),
                        r.getAllSelectionStorageId()
                    });
                }
            }
            return;
        }

        List res = keystrokesmod.utility.BlockSearchIndex.searchGrouped(
                listQuery, (keystrokesmod.module.setting.impl.BlockListSetting) st);
        for (int i = 0; i < res.size(); i++) {
            keystrokesmod.utility.BlockSearchIndex.GroupedBlockResult r =
                    (keystrokesmod.utility.BlockSearchIndex.GroupedBlockResult) res.get(i);
            if (r.isSingleVariant()) {
                keystrokesmod.utility.BlockSearchIndex.BlockEntry e =
                        (keystrokesmod.utility.BlockSearchIndex.BlockEntry) r.variants.get(0);
                listDropRows.add(
                        new Object[] {e.displayName, e.storageId, Integer.valueOf(0), e.storageId, null, null, null});
            } else {
                keystrokesmod.utility.BlockSearchIndex.BlockEntry first =
                        (keystrokesmod.utility.BlockSearchIndex.BlockEntry) r.variants.get(0);
                listDropRows.add(new Object[] {
                    r.getGroupLabel(),
                    r.registryId,
                    Integer.valueOf(1),
                    null,
                    r.registryId,
                    first.displayName,
                    r.registryId + ":*"
                });
            }
        }
    }

    private void listRowClick(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st, Object[] row) {
        int kind = ((Integer) row[2]).intValue();
        if (kind == 2) {
            listCollapseGroup();
        } else if (kind == 1) {
            listExpandGroup((String) row[4], (String) row[5], (String) row[6]);
        } else {
            listAdd(mod, st, (String) row[3]);
        }
    }

    private float listSettingHeight(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        listEnsureRows(mod, st);
        // Field row matches a TextSetting: 20px label + 24px box + 4px trailing gap.
        float h = 48f * s;
        if (isSearchableList(st)) {
            h += listDropAnimH(mod, st);
        }
        h += listSelAnimH(mod, st);
        return h;
    }

    private String listDropKey(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        return "d|" + listKey(mod, st);
    }

    private String listSelKey(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        return "s|" + listKey(mod, st);
    }

    /** Full height of a panel showing {@code rows} rows. */
    private float listPanelFullH(int rows) {
        return (float) Math.min(LIST_MAX_ROWS, rows) * LIST_ROW_H * s;
    }

    /** Animated height of the results panel, including its trailing gap while open. */
    private float listDropAnimH(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        int rows = listDropRows.size();
        boolean open = listOpen && isFocusedList(st) && rows > 0;
        return listAnim.height(listDropKey(mod, st), open ? listPanelFullH(rows) + 4f * s : 0f);
    }

    /** Animated height of the selected-entries panel. */
    private float listSelAnimH(keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st) {
        int sel = listSelectedCount(st);
        return listAnim.height(listSelKey(mod, st), sel > 0 ? listPanelFullH(sel) : 0f);
    }

    private void drawListRow(
            keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st, float x, float y, float w) {
        int sel = listSelectedCount(st);
        String label = st.getName() + (sel > 0 ? " (" + sel + ")" : "");
        txt(fSmall, trim(fSmall, label, w - 8f * s), x, y + 7f * s, cText);

        // Search field reuses the TextSetting box so list rows read like ordinary settings.
        float boxY = y + 20f * s;
        float boxH = 24f * s;
        boolean owns = isFocusedList(st);
        boolean active = owns && listOpen;
        if (active && intersectsClip(x, boxY, w, boxH)) {
            sawListFocus = true;
        }
        qRound(x, boxY, x + w, boxY + boxH, 5f * s, active ? cRaised : cField);
        drawListField(st, x, boxY, w, boxH, active);
        if (clicked(x, boxY, w, boxH)) {
            listFocus(mod, st);
        }

        float cursor = boxY + boxH + 4f * s;
        if (isSearchableList(st)) {
            // The panel and its trailing gap animate open/closed like the mode dropdown.
            float anim = listDropAnimH(mod, st);
            float panelH = Math.min(anim, listPanelFullH(listDropRows.size()));
            if (panelH > 0.5f) {
                drawListDropdown(mod, st, x, cursor, w, panelH, active);
            }
            cursor += anim;
        }
        drawListSelected(mod, st, x, cursor, w);

        if (owns && !listOpen && listDropAnimH(mod, st) <= 0.5f) {
            listFinalizeClose();
        }
    }

    /** Search text + caret, drawn inside an already-painted field box. */
    private void drawListField(
            keystrokesmod.module.setting.Setting st, float x, float boxY, float w, float boxH, boolean focused) {
        String shown = trimLeft(fSmall, listQuery, w - 19f * s);
        float tx = x + 8f * s;
        float cy = boxY + boxH / 2f;
        if (listQuery.length() == 0) {
            txt(fSmall, trim(fSmall, listPlaceholder(st), w - 16f * s), tx, cy, cTextFaint);
        } else {
            txt(fSmall, shown, tx, cy, cText);
        }
        if (focused && (client.time() / 500L) % 2L == 0L) {
            float caretX = tx + tw(fSmall, shown) + 1f * s;
            qRect(caretX, boxY + 7f * s, caretX + 1f * s, boxY + boxH - 7f * s, cText);
        }
    }

    private void drawListDropdown(
            keystrokesmod.module.Module mod,
            keystrokesmod.module.setting.Setting st,
            float x,
            float top,
            float w,
            float panelH,
            boolean interactive) {
        ArrayList rows = listDropRows;
        int n = rows.size();
        float rowStep = LIST_ROW_H * s;
        float maxScroll = Math.max(0f, (float) (n - LIST_MAX_ROWS) * rowStep);
        listDropScroll = listScrollPanel(x, top, w, panelH, maxScroll, listDropScroll, interactive);

        float[] prev = listPushPanel(x, top, w, panelH);
        if (prev == null) {
            return;
        }
        int first = (int) (listDropScroll / rowStep);
        boolean items = isItemList(st);
        for (int i = first; i < n; i++) {
            float ry = top - listDropScroll + (float) i * rowStep;
            if (ry > top + panelH) {
                break;
            }
            if (ry + rowStep < top) {
                continue;
            }
            Object[] row = (Object[]) rows.get(i);
            boolean hov = interactive && my >= top && my <= top + panelH && inside(x, ry, w, rowStep);
            drawListRowPill(x, ry, w, rowStep, hov);
            float tx = listIcon((String) row[1], items, x, ry, rowStep);
            txt(
                    fSmall,
                    trim(fSmall, (String) row[0], w - (tx - x) - 8f * s),
                    tx,
                    ry + rowStep / 2f,
                    hov ? cText : cTextDim);
            if (hov && clicked(x, ry, w, rowStep)) {
                listRowClick(mod, st, row);
                break;
            }
        }
        if (interactive) {
            drawListScrollbar(x, top, w, panelH, maxScroll, listDropScroll);
        }
        listPopPanel(prev);
    }

    private void drawListSelected(
            keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st, float x, float top, float w) {
        float rowStep = LIST_ROW_H * s;
        float panelH = listSelAnimH(mod, st);
        if (panelH <= 0.5f) {
            return;
        }
        int count = listSelectedCount(st);
        float maxScroll = Math.max(0f, (float) (count - LIST_MAX_ROWS) * rowStep);
        String key = listKey(mod, st);
        Object o = listSelScroll.get(key);
        float sc = listScrollPanel(x, top, w, panelH, maxScroll, o == null ? 0f : ((Float) o).floatValue(), true);
        listSelScroll.put(key, Float.valueOf(sc));

        float[] prev = listPushPanel(x, top, w, panelH);
        if (prev == null) {
            return;
        }
        int first = (int) (sc / rowStep);
        boolean items = isItemList(st);
        for (int i = first; i < count; i++) {
            float ry = top - sc + (float) i * rowStep;
            if (ry > top + panelH) {
                break;
            }
            if (ry + rowStep < top) {
                continue;
            }
            Object[] row = listSelectedRow(st, i);
            if (row == null) {
                continue;
            }
            boolean over = my >= top && my <= top + panelH;
            float cbW = 14f * s;
            float cbX = x + w - cbW - 4f * s;
            boolean rhov = over && inside(x, ry, w, rowStep);
            boolean chov = over && inside(cbX, ry, cbW, rowStep);
            drawListRowPill(x, ry, w, rowStep, rhov && !chov);
            float tx = listIcon((String) row[2], items, x, ry, rowStep);
            txt(fSmall, trim(fSmall, (String) row[0], cbX - tx - 4f * s), tx, ry + rowStep / 2f, cText);
            if (chov) {
                qRound(cbX + 1f * s, ry + 1.5f * s, cbX + cbW - 1f * s, ry + rowStep - 1.5f * s, 4f * s, C_OFF);
            }
            txt(fSmall, "x", cbX + cbW / 2f - tw(fSmall, "x") / 2f, ry + rowStep / 2f, chov ? 0xFFF0F0F0 : cTextFaint);
            if (chov && clicked(cbX, ry, cbW, rowStep)) {
                listRemove(mod, st, (String) row[1]);
                listPopPanel(prev);
                return;
            }
        }
        drawListScrollbar(x, top, w, panelH, maxScroll, sc);
        listPopPanel(prev);
    }

    /* ---- list panel helpers ---- */

    /** Applies wheel input (when hovered and interactive) to a scrollable panel, returns the clamped scroll. */
    private float listScrollPanel(
            float x, float top, float w, float h, float maxScroll, float scroll, boolean interactive) {
        if (interactive && maxScroll > 0f && frameWheel != 0 && inside(x, top, w, h)) {
            scroll -= (float) frameWheel / 6f * (LIST_ROW_H * s);
            frameWheel = 0;
        }
        return clampF(scroll, 0f, maxScroll);
    }

    /** Clips to the panel rect (intersected with the active clip) and paints its rounded background.
     *  Returns the previous clip for {@link #listPopPanel}, or null when the panel is fully off-screen. */
    private float[] listPushPanel(float x, float top, float w, float h) {
        float[] prev = clipTo(x, top, w, h);
        if (prev == null) {
            return null;
        }
        qRound(x, top, x + w, top + h, 5f * s, cField);
        return prev;
    }

    private void listPopPanel(float[] prev) {
        clipRestore(prev);
    }

    /** Hover pill for one row, inset so it never covers the panel's rounded corners. */
    private void drawListRowPill(float x, float ry, float w, float rowStep, boolean hover) {
        if (!hover) {
            return;
        }
        qRound(x + LIST_PAD * s, ry + 1f * s, x + w - LIST_PAD * s, ry + rowStep - 1f * s, 4f * s, C_HOVER);
    }

    /** Thin rounded scrollbar, drawn only while the panel overflows. */
    private void drawListScrollbar(float x, float top, float w, float viewH, float maxScroll, float scroll) {
        if (maxScroll <= 0f) {
            return;
        }
        float barW = 2f * s;
        float barX = x + w - barW - 2f * s;
        float thumbH = Math.max(10f * s, viewH * (viewH / (viewH + maxScroll)));
        float thumbY = top + (viewH - thumbH) * (scroll / maxScroll);
        qRound(barX, top, barX + barW, top + viewH, barW / 2f, 0x22FFFFFF);
        qRound(barX, thumbY, barX + barW, thumbY + thumbH, barW / 2f, withAlpha(accent, 190));
    }

    /** Draws a row's leading item icon (when one maps) and returns the text inset past it. */
    private float listIcon(String storageId, boolean items, float x, float ry, float rowStep) {
        if (storageId == null) {
            return x + 8f * s;
        }
        ItemStack stack = items ? ItemSearchIndex.getItemStack(storageId) : BlockSearchIndex.getItemStack(storageId);
        if (stack == null || stack.getItem() == null) {
            return x + 8f * s;
        }
        float iconScale = 0.7f * s;
        ClassicIcons.drawItem(stack, x + 5f * s, ry + (rowStep - 16f * iconScale) / 2f, iconScale);
        return x + 19f * s;
    }

    /** Runs the modules' own guiUpdate hooks so conditional settings show/hide like they do in Raven. */
    private void syncSettingVisibility() {
        if (keystrokesmod.clickgui.ClickGui.categories == null) {
            return;
        }
        ArrayList all = allModules();
        for (int i = 0; i < all.size(); i++) {
            ((keystrokesmod.module.Module) all.get(i)).guiUpdate();
        }
    }

    /* ------------------------------------------------------------------ data access */

    private ArrayList visibleModules() {
        ArrayList<Module> out = new ArrayList<>();
        if (searchText.length() == 0) {
            for (CategoryComponent cc : ClickGui.categories) {
                if (cc.category == tab) {
                    for (ModuleComponent component : cc.modules) {
                        out.add(component.mod);
                    }
                    break;
                }
            }
            return out;
        }
        String q = searchText.toLowerCase(Locale.ROOT);
        ArrayList all = allModules();
        for (int i = 0; i < all.size(); i++) {
            keystrokesmod.module.Module m = (keystrokesmod.module.Module) all.get(i);
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(m);
            }
        }
        return out;
    }

    /** Every registered module, cached for the current frame. */
    private ArrayList allModules() {
        if (modulesFrame == frameId) {
            return modulesCache;
        }
        modulesCache.clear();
        if (keystrokesmod.clickgui.ClickGui.categories != null) {
            for (keystrokesmod.clickgui.components.impl.CategoryComponent cc :
                    keystrokesmod.clickgui.ClickGui.categories) {
                if (cc == null) {
                    continue;
                }
                for (keystrokesmod.clickgui.components.impl.ModuleComponent mc : cc.modules) {
                    if (mc != null && mc.mod != null) {
                        modulesCache.add(mc.mod);
                    }
                }
            }
        }
        modulesFrame = frameId;
        return modulesCache;
    }

    private String uiKey(Object object) {
        return Integer.toString(uiIds.computeIfAbsent(object, ignored -> ++nextUiId));
    }

    private List<Setting> orderedSettings(Module module) {
        return frameSettings.computeIfAbsent(module, mod -> {
            List<Setting> raw = mod.getSettings();
            List<Setting> ordered = new ArrayList<>(raw.size());
            Map<keystrokesmod.module.setting.impl.GroupSetting, List<Setting>> children = new IdentityHashMap<>();
            for (Setting st : raw) {
                if (st instanceof keystrokesmod.module.setting.impl.GroupSetting) {
                    children.put((keystrokesmod.module.setting.impl.GroupSetting) st, new ArrayList<>());
                }
            }
            for (Setting st : raw) {
                List<Setting> group = children.get(groupOf(st));
                if (group != null) {
                    group.add(st);
                }
            }
            for (Setting st : raw) {
                if (children.containsKey(groupOf(st))) {
                    continue;
                }
                ordered.add(st);
                List<Setting> group = children.get(st);
                if (group != null) {
                    ordered.addAll(group);
                }
            }
            return ordered;
        });
    }

    private keystrokesmod.module.setting.impl.GroupSetting groupOf(keystrokesmod.module.setting.Setting st) {
        if (st instanceof keystrokesmod.module.setting.impl.SliderSetting) {
            return ((keystrokesmod.module.setting.impl.SliderSetting) st).groupSetting;
        }
        if (st instanceof keystrokesmod.module.setting.impl.ButtonSetting) {
            return ((keystrokesmod.module.setting.impl.ButtonSetting) st).group;
        }
        if (st instanceof keystrokesmod.module.setting.impl.ColorSetting) {
            return ((keystrokesmod.module.setting.impl.ColorSetting) st).groupSetting;
        }
        if (st instanceof keystrokesmod.module.setting.impl.KeySetting) {
            return ((keystrokesmod.module.setting.impl.KeySetting) st).group;
        }
        if (st instanceof keystrokesmod.module.setting.impl.TextSetting) {
            return ((keystrokesmod.module.setting.impl.TextSetting) st).group;
        }
        if (st instanceof keystrokesmod.module.setting.impl.BlockListSetting) {
            return ((keystrokesmod.module.setting.impl.BlockListSetting) st).group;
        }
        if (st instanceof keystrokesmod.module.setting.impl.StringListSetting) {
            return ((keystrokesmod.module.setting.impl.StringListSetting) st).group;
        }
        if (st instanceof keystrokesmod.module.setting.impl.PlayerListSetting) {
            return ((keystrokesmod.module.setting.impl.PlayerListSetting) st).group;
        }
        if (st instanceof keystrokesmod.module.setting.impl.TextSetting) {
            return ((keystrokesmod.module.setting.impl.TextSetting) st).group;
        }
        if (st instanceof keystrokesmod.module.setting.impl.KeySetting) {
            return ((keystrokesmod.module.setting.impl.KeySetting) st).group;
        }
        return null;
    }

    private boolean visibleSetting(
            keystrokesmod.module.Module mod, keystrokesmod.module.setting.Setting st, int index) {
        if (!st.visible) {
            return false;
        }
        if (st instanceof keystrokesmod.module.setting.impl.DescriptionSetting && index == 0) {
            return false;
        }
        keystrokesmod.module.setting.impl.GroupSetting g = groupOf(st);
        if (g != null && !g.isOpened() && groupReveal(mod, g) <= 0.0005f) {
            return false;
        }
        return true;
    }

    private boolean showsArrayToggle(keystrokesmod.module.Module mod) {
        return arraylistEligible(mod);
    }

    private void toggleModule(keystrokesmod.module.Module mod) {
        if (mod instanceof keystrokesmod.utility.profile.ProfileModule) {
            if (keystrokesmod.Feral.profileManager == null) {
                return;
            }
            String name = mod.getName();
            keystrokesmod.utility.profile.Profile profile = keystrokesmod.Feral.profileManager.getProfile(name);
            keystrokesmod.Feral.profileManager.loadProfile(name);
            if (profile != null) {
                keystrokesmod.Feral.currentProfile = profile;
            }
            ((keystrokesmod.utility.profile.ProfileModule) mod).saved = true;
            client.print(util.color("&7Enabled profile: &b" + name));
            return;
        }
        mod.toggle();
        markUnsaved(mod);
    }

    private void markUnsaved(keystrokesmod.module.Module mod) {
        if (keystrokesmod.Feral.currentProfile == null || mod == null || mod.ignoreOnSave) {
            return;
        }
        if (keystrokesmod.Feral.currentProfile.getModule() != null) {
            keystrokesmod.Feral.currentProfile.getModule().saved = false;
        }
    }

    private String formatHex(keystrokesmod.module.setting.impl.ColorSetting cs) {
        return "#" + hex2(cs.getRed()) + hex2(cs.getGreen()) + hex2(cs.getBlue());
    }

    private String hex2(int v) {
        String t = Integer.toHexString(v & 255).toUpperCase();
        return t.length() < 2 ? "0" + t : t;
    }

    private static final String[] KEY_NAMES = new String[] {
        "", "ESC", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "BACK", "TAB", "Q", "W", "E", "R", "T",
        "Y", "U", "I", "O", "P", "[", "]", "ENTER", "LCTRL", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'", "`",
        "LSHIFT", "\\", "Z", "X", "C", "V", "B", "N", "M", ",", ".", "/", "RSHIFT", "NUM*", "LALT", "SPACE", "CAPS"
    };

    private String keyName(int code) {
        if (code == 0) {
            return "NONE";
        }
        if (code == 1069) {
            return "SCROLL UP";
        }
        if (code == 1070) {
            return "SCROLL DOWN";
        }
        if (code >= 1000) {
            return "MOUSE" + (code - 999);
        }
        if (code < KEY_NAMES.length) {
            return KEY_NAMES[code];
        }
        return "KEY " + code;
    }

    private keystrokesmod.utility.font.FeralFontRenderer font(float size) {
        return keystrokesmod.utility.font.FontManager.getHudRenderer(
                fontFamily, clampF(size * fontSizeMul, 0.5f, 2.0f), fontThickness);
    }

    /* ------------------------------------------------------------------ draw helpers */

    private int menuTint(int color) {
        if (!inMenu || fadeMul >= 0.999f) {
            return color;
        }
        return fadeColor(color, fadeMul);
    }

    private void qRect(float startX, float startY, float endX, float endY, int color) {
        render.rect(startX, startY, endX, endY, menuTint(color));
    }

    private void qRound(float startX, float startY, float endX, float endY, float radius, int color) {
        float maxR =
                (Math.abs(endX - startX) < Math.abs(endY - startY) ? Math.abs(endX - startX) : Math.abs(endY - startY))
                        / 2f;
        if (radius > maxR) {
            radius = maxR;
        }
        render.roundedRect(startX, startY, endX, endY, radius, menuTint(color));
    }

    /** Left-to-right two-stop gradient, faded with the menu. */
    private void gradH(float startX, float startY, float endX, float endY, int left, int right) {
        keystrokesmod.utility.RenderUtils.drawHorizontalGradientRect(
                startX, startY, endX, endY, menuTint(left), menuTint(right));
    }

    /** Top-to-bottom two-stop gradient, faded with the menu. */
    private void gradV(float startX, float startY, float endX, float endY, int top, int bottom) {
        keystrokesmod.utility.RenderUtils.drawVerticalGradientRect(
                startX, startY, endX, endY, menuTint(top), menuTint(bottom));
    }

    private void txt(keystrokesmod.utility.font.FeralFontRenderer fr, String text, float x, float centerY, int color) {
        if (fr == null || text == null || text.length() == 0) {
            return;
        }
        float textScale = inMenu ? menuFontScale : 1f;
        int tint = menuTint(color);
        if (textScale == 1f) {
            fr.drawString(text, x, centerY - fr.getFontHeight() / 2f, tint, false);
            return;
        }
        gl.push();
        try {
            gl.translate(x, centerY, 0f);
            gl.scale(textScale, textScale, 1f);
            fr.drawString(text, 0f, -fr.getFontHeight() / 2f, tint, false);
        } finally {
            gl.pop();
        }
    }

    private float tw(keystrokesmod.utility.font.FeralFontRenderer fr, String text) {
        if (fr == null || text == null) {
            return 0f;
        }
        return fr.getStringWidth(text) * (inMenu ? menuFontScale : 1f);
    }

    private String trim(keystrokesmod.utility.font.FeralFontRenderer fr, String text, float maxWidth) {
        return fr == null ? "" : GuiText.fit(fr, text, maxWidth / (inMenu ? menuFontScale : 1f));
    }

    private String trimLeft(keystrokesmod.utility.font.FeralFontRenderer fr, String text, float maxWidth) {
        if (fr == null || text == null || maxWidth <= 0f) {
            return "";
        }
        int start = 0;
        while (start < text.length() && tw(fr, text.substring(start)) > maxWidth) {
            start = text.offsetByCodePoints(start, 1);
        }
        return text.substring(start);
    }

    private String pretty(String raw) {
        if (raw == null || raw.length() == 0) {
            return "";
        }
        return raw.substring(0, 1).toUpperCase() + raw.substring(1);
    }

    private String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1.0E9D) {
            return Integer.toString((int) v);
        }
        return Double.toString(util.round(v, 2));
    }

    /* ------------------------------------------------------------------ colour math */

    private static int clamp255(int v) {
        if (v < 0) {
            return 0;
        }
        return v > 255 ? 255 : v;
    }

    private int scaleRgb(int rgb, float mult) {
        return (clamp255((int) (((rgb >> 16) & 255) * mult)) << 16)
                | (clamp255((int) (((rgb >> 8) & 255) * mult)) << 8)
                | clamp255((int) ((rgb & 255) * mult));
    }

    private int boostRgb(int rgb, int add) {
        return (clamp255(((rgb >> 16) & 255) + add) << 16)
                | (clamp255(((rgb >> 8) & 255) + add) << 8)
                | clamp255((rgb & 255) + add);
    }

    private int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    /** Re-alphas a packed colour, keeping its RGB. */
    private int withAlpha(int rgb, int a) {
        return ((a & 255) << 24) | (rgb & 0x00FFFFFF);
    }

    private int mixColor(int from, int to, float t) {
        if (t <= 0f) {
            return from;
        }
        if (t >= 1f) {
            return to;
        }
        int fa = (from >> 24) & 255;
        int fr = (from >> 16) & 255;
        int fg = (from >> 8) & 255;
        int fb = from & 255;
        int ta = (to >> 24) & 255;
        int tr = (to >> 16) & 255;
        int tg = (to >> 8) & 255;
        int tb = to & 255;
        return (Math.round(fa + (ta - fa) * t) << 24)
                | (Math.round(fr + (tr - fr) * t) << 16)
                | (Math.round(fg + (tg - fg) * t) << 8)
                | Math.round(fb + (tb - fb) * t);
    }

    private int contrastText(int selectionAlpha) {
        int fill = mixColor(cPanel, accent, selectionAlpha * fadeMul / 255f);
        double lum = 0.2126 * linear(fill >> 16 & 255) + 0.7152 * linear(fill >> 8 & 255) + 0.0722 * linear(fill & 255);
        return lum > 0.179 ? 0xFF000000 : C_WHITE;
    }

    private static double linear(int value) {
        double v = value / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /* -------------------------------------------------------------------- math utils */

    /** Maps the Opacity slider (40%..100%) onto {@code floor}..1, so a layer stays visible at low
     *  slider values but still reaches fully solid at 100%. */
    private float opacityCurve(float opacity, float floor) {
        float u = clampF((opacity - 0.4f) / 0.6f, 0f, 1f);
        return floor + (1f - floor) * u;
    }

    private float clampF(float v, float lo, float hi) {
        if (hi < lo) {
            return lo;
        }
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    private int clampI(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    /** Frame-rate independent ease toward {@code target}; snaps once within a pixel. */
    private static float approach(float current, float target, float speed, float dt) {
        float t = 1f - (float) Math.pow(0.0025, dt * speed);
        if (t < 0f) {
            t = 0f;
        }
        if (t > 1f) {
            t = 1f;
        }
        float next = current + (target - current) * t;
        if (Math.abs(target - next) < 0.001f) {
            next = target;
        }
        return next;
    }

    private float easeInOut(float x) {
        if (x < 0f) {
            x = 0f;
        }
        if (x > 1f) {
            x = 1f;
        }
        return x * x * (3f - 2f * x);
    }

    private float easeOutBack(float x) {
        if (x < 0f) {
            x = 0f;
        }
        if (x > 1f) {
            x = 1f;
        }
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = x - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /* ---------------------------------------------------------------------- input */

    private boolean inside(float x, float y, float w, float h) {
        return pointerIn(x, y, w, h) && (!clipping || pointerIn(clipRectX, clipRectY, clipRectW, clipRectH));
    }

    private boolean pointerIn(float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean intersectsClip(float x, float y, float w, float h) {
        return !clipping
                || x + w > clipRectX && x < clipRectX + clipRectW && y + h > clipRectY && y < clipRectY + clipRectH;
    }

    private boolean clicked(float x, float y, float w, float h) {
        if (!pressLeft || !inside(x, y, w, h)) {
            return false;
        }
        pressLeft = false;
        return true;
    }

    /** True when the row is activated with either mouse button; consumes that press. */
    private boolean activated(float x, float y, float w, float h) {
        if (pressLeft && inside(x, y, w, h)) {
            pressLeft = false;
            return true;
        }
        if (pressRight && inside(x, y, w, h)) {
            pressRight = false;
            return true;
        }
        return false;
    }

    /* ------------------------------------------------------------------ scissor/clip */

    private void scissorOn(float x, float y, float w, float h) {
        if (w < 0f) {
            w = 0f;
        }
        if (h < 0f) {
            h = 0f;
        }
        clipRectX = x;
        clipRectY = y;
        clipRectW = w;
        clipRectH = h;
        gl.scissor(true);
        int left = (int) Math.floor(x * scale);
        int top = (int) Math.floor(y * scale);
        int right = (int) Math.ceil((x + w) * scale);
        int bottom = (int) Math.ceil((y + h) * scale);
        gl.scissor(left, framebufferHeight - bottom, w <= 0f ? 0 : right - left, h <= 0f ? 0 : bottom - top);
    }

    private void scissorOff() {
        gl.scissor(false);
    }

    private float[] saveClip() {
        return new float[] {clipRectX, clipRectY, clipRectW, clipRectH};
    }

    /** Intersects the given rect with the active clip and applies it; returns the previous clip
     *  (for {@link #clipRestore}), or null when the intersection is empty. */
    private float[] clipTo(float x, float y, float w, float h) {
        float x1 = Math.max(x, clipRectX);
        float y1 = Math.max(y, clipRectY);
        float x2 = Math.min(x + w, clipRectX + clipRectW);
        float y2 = Math.min(y + h, clipRectY + clipRectH);
        if (x2 <= x1 || y2 <= y1) {
            return null;
        }
        float[] prev = saveClip();
        scissorOn(x1, y1, x2 - x1, y2 - y1);
        return prev;
    }

    /** Restores a clip rect previously returned by {@link #saveClip} or {@link #clipTo}. */
    private void clipRestore(float[] prev) {
        if (prev != null) {
            scissorOn(prev[0], prev[1], prev[2], prev[3]);
        }
    }

    private void beginClip(float x, float y, float w, float h) {
        scissorOn(x, y, w, h);
        clipping = true;
    }

    private void endClip() {
        clipping = false;
        scissorOff();
    }

    /* ===================================================================== helpers */

    private static final class KeyInput {
        final char character;
        final int code;

        KeyInput(char character, int code) {
            this.character = character;
            this.code = code;
        }
    }

    private static final class PointerInput {
        boolean hasPosition, leftPress, leftRelease, rightPress;
        int x, y, wheel, releaseX, releaseY;

        void accept(int button, boolean down, int x, int y, int wheel) {
            if (!hasPosition && (button >= 0 || wheel != 0)) {
                hasPosition = true;
                this.x = x;
                this.y = y;
            }
            if (button == 0 && down) {
                leftPress = true;
            } else if (button == 0) {
                leftRelease = true;
                releaseX = x;
                releaseY = y;
            } else if (button == 1 && down) {
                rightPress = true;
            }
            this.wheel += wheel;
        }

        void clear() {
            hasPosition = leftPress = leftRelease = rightPress = false;
            wheel = 0;
        }
    }

    /** Keyed animation values, each advanced at most once per frame (tracked by frame stamp). */
    private static final class KeyedAnim {
        private final HashMap values = new HashMap();
        private final HashMap stamps = new HashMap();
        private int frame = -1;
        private float dt = 0f;

        void begin(int frameId, float frameDt) {
            frame = frameId;
            dt = frameDt;
        }

        /** Eases toward a 0..1 target; {@code snap} pulls near-0/1 values fully home so they settle. */
        float towards(String key, boolean target, float speed, boolean snap) {
            float cur = value(key, target ? 1f : 0f);
            if (fresh(key)) {
                cur = approach(cur, target ? 1f : 0f, speed, dt);
                if (snap) {
                    if (cur < 0.001f) {
                        cur = 0f;
                    }
                    if (cur > 0.999f) {
                        cur = 1f;
                    }
                }
                put(key, cur);
            }
            return cur;
        }

        /** Eases toward an absolute pixel height, snapping once close enough. */
        float height(String key, float target) {
            float cur = value(key, target);
            if (fresh(key)) {
                cur = approach(cur, target, 3.2f, dt);
                if (Math.abs(cur - target) < 0.5f) {
                    cur = target;
                }
                put(key, cur);
            }
            return cur;
        }

        float value(String key) {
            return value(key, 0f);
        }

        private float value(String key, float fallback) {
            Object o = values.get(key);
            return o == null ? fallback : ((Float) o).floatValue();
        }

        private boolean fresh(String key) {
            Object stamp = stamps.get(key);
            return stamp == null || ((Integer) stamp).intValue() != frame;
        }

        private void put(String key, float v) {
            values.put(key, Float.valueOf(v));
            stamps.put(key, Integer.valueOf(frame));
        }

        void clear() {
            values.clear();
            stamps.clear();
        }
    }

    /** Per-scrollbar drag state. */
    private static final class ScrollDrag {
        boolean dragging = false;
        float grab = 0f;
    }
}
