package keystrokesmod.utility.font;

import java.awt.Desktop;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import keystrokesmod.module.setting.impl.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;

public final class FontManager {
    private static final String MINECRAFT = "Minecraft";
    private static final String RESOURCE_ROOT = "/assets/keystrokesmod/fonts/";
    private static final long MAX_CUSTOM_FONT_BYTES = 33554432L;
    private static final int MAX_CACHED_RENDERERS = 512;
    // Crafty Girls font (Meower bundles it as "MeowClient-Regular") + size/thickness, same as MeowerClient
    private static final String DEFAULT_FONT = "Crafty Girls";
    private static final float MAX_THICKNESS = 1.0F;
    private static final float DEFAULT_HUD_FONT_SIZE = 10.0F;
    private static final float DEFAULT_CLICK_GUI_HEADER_HEIGHT = 9.0F;
    private static final float DEFAULT_CLICK_GUI_SETTING_HEIGHT = 9.0F;
    private static final float DEFAULT_NAMETAG_FONT_SIZE = 9.0F;
    private static final FontManager.FontDefinition[] BUNDLED_FONTS = new FontManager.FontDefinition[] {
        FontManager.FontDefinition.bundled("Crafty Girls", "CraftyGirls-Regular.ttf"),
        FontManager.FontDefinition.bundled("Sf-Bold", "Sf-Bold.ttf"),
        FontManager.FontDefinition.bundled("Sf-Regular", "Sf-Regular.ttf"),
        FontManager.FontDefinition.bundled("Sf-Ui", "Sf-Ui.ttf")
    };
    private static final File FONT_DIRECTORY =
            new File(new File(Minecraft.getInstance().gameDir, "keystrokes"), "fonts");
    private static final List<SliderSetting> FONT_SETTINGS = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Font> BASE_FONT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, FeralFontRenderer> FONT_CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static volatile Map<String, FontManager.FontDefinition> fontDefinitions = Collections.emptyMap();
    private static volatile String[] hudFontOptions = new String[] {"Minecraft"};

    private FontManager() {}

    public static String getDefaultFontName() {
        return DEFAULT_FONT;
    }

    public static File getFontDirectory() {
        ensureFontDirectory();
        return FONT_DIRECTORY;
    }

    public static boolean openFontDirectory() {
        ensureFontDirectory();

        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }

            Desktop.getDesktop().open(FONT_DIRECTORY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static SliderSetting createFontSetting(String settingName) {
        String[] options = getHudFontOptions();
        int defaultIndex = Math.max(0, java.util.Arrays.asList(options).indexOf(DEFAULT_FONT));
        SliderSetting setting = new SliderSetting(settingName, defaultIndex, options);
        FONT_SETTINGS.add(setting);
        return setting;
    }

    public static String[] getHudFontOptions() {
        return (String[]) hudFontOptions.clone();
    }

    public static synchronized FontManager.ReloadResult reloadCustomFonts() {
        ensureFontDirectory();
        LinkedHashMap<String, FontManager.FontDefinition> rebuiltDefinitions = new LinkedHashMap<>();
        LinkedHashMap<String, Font> validatedCustomFonts = new LinkedHashMap<>();

        for (FontManager.FontDefinition bundledFont : BUNDLED_FONTS) {
            rebuiltDefinitions.put(bundledFont.displayName, bundledFont);
        }

        int loaded = 0;
        int failed = 0;
        File[] files = FONT_DIRECTORY.listFiles(file -> file.isFile() && isSupportedFontFile(file.getName()));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

            for (File file : files) {
                if (file.length() > 0L && file.length() <= 33554432L) {
                    Font baseFont = loadCustomFont(file);
                    if (baseFont == null) {
                        failed++;
                    } else {
                        String displayName = makeCustomDisplayName(file, rebuiltDefinitions);
                        FontManager.FontDefinition definition = FontManager.FontDefinition.custom(displayName, file);
                        rebuiltDefinitions.put(displayName, definition);
                        validatedCustomFonts.put(definition.cacheKey, baseFont);
                        loaded++;
                    }
                } else {
                    failed++;
                }
            }
        }

        clearRendererCache();
        BASE_FONT_CACHE.clear();
        BASE_FONT_CACHE.putAll(validatedCustomFonts);
        fontDefinitions = Collections.unmodifiableMap(rebuiltDefinitions);
        String[] options = new String[rebuiltDefinitions.size() + 1];
        options[0] = "Minecraft";
        int optionIndex = 1;

        for (String displayName : rebuiltDefinitions.keySet()) {
            options[optionIndex++] = displayName;
        }

        hudFontOptions = options;
        synchronized (FONT_SETTINGS) {
            for (SliderSetting setting : FONT_SETTINGS) {
                setting.setOptions(options);
            }
        }

        return new FontManager.ReloadResult(loaded, failed);
    }

    public static FeralFontRenderer getHudRenderer(String family, float scale) {
        return getHudRenderer(family, scale, 0.0F);
    }

    /** @param thickness extra letter weight in GUI pixels (0 = the font as designed). */
    public static FeralFontRenderer getHudRenderer(String family, float scale, float thickness) {
        float safeScale = Math.max(0.5F, Math.min(2.0F, scale));
        return getRenderer(family, 10.0F * safeScale, thickness);
    }

    public static FeralFontRenderer getClickGuiHeaderRenderer(String family) {
        return getClickGuiHeaderRenderer(family, 1.0F, 0.0F);
    }

    public static FeralFontRenderer getClickGuiHeaderRenderer(String family, float sizeMultiplier, float thickness) {
        return getRendererForPixelHeight(family, 9.0F * clampSizeMultiplier(sizeMultiplier), thickness);
    }

    public static FeralFontRenderer getClickGuiSettingRenderer(String family) {
        return getClickGuiSettingRenderer(family, 1.0F, 0.0F);
    }

    public static FeralFontRenderer getClickGuiSettingRenderer(String family, float sizeMultiplier, float thickness) {
        return getRendererForPixelHeight(family, 9.0F * clampSizeMultiplier(sizeMultiplier), thickness);
    }

    private static float clampSizeMultiplier(float sizeMultiplier) {
        return Math.max(0.5F, Math.min(2.0F, sizeMultiplier));
    }

    private static float clampThickness(float thickness) {
        return Math.max(0.0F, Math.min(MAX_THICKNESS, thickness));
    }

    public static FeralFontRenderer getNametagRenderer(String family) {
        return getRenderer(family, 9.0F);
    }

    public static FeralFontRenderer getRenderer(String family, float fontSize) {
        return getRenderer(family, fontSize, 0.0F);
    }

    public static FeralFontRenderer getRenderer(String family, float fontSize, float thickness) {
        final float safeFontSize = Math.max(1.0F, fontSize);
        final float safeThickness = clampThickness(thickness);
        if (family != null && !isMinecraftFont(family)) {
            final FontManager.FontDefinition fontDefinition = fontDefinitions.get(family);
            if (fontDefinition == null) {
                return getMinecraftRenderer(safeFontSize);
            }

            String key = family + "#" + quantizeForCacheKey(safeFontSize) + "#t" + quantizeForCacheKey(safeThickness)
                    + "#" + getUiScale();
            return getCachedRenderer(key, new Supplier<FeralFontRenderer>() {
                public FeralFontRenderer get() {
                    Font baseFont = FontManager.getBaseFont(fontDefinition);
                    return baseFont == null
                            ? FontManager.getMinecraftRenderer(safeFontSize)
                            : new GlyphFontRenderer(baseFont.deriveFont(safeFontSize), true, safeThickness);
                }
            });
        } else {
            return getMinecraftRenderer(safeFontSize);
        }
    }

    public static FeralFontRenderer getRendererForPixelHeight(String family, float targetHeight) {
        return getRendererForPixelHeight(family, targetHeight, 0.0F);
    }

    public static FeralFontRenderer getRendererForPixelHeight(String family, float targetHeight, float thickness) {
        final float safeTargetHeight = Math.max(1.0F, targetHeight);
        final float safeThickness = clampThickness(thickness);
        if (family != null && !isMinecraftFont(family)) {
            final FontManager.FontDefinition fontDefinition = fontDefinitions.get(family);
            if (fontDefinition == null) {
                return getMinecraftRenderer(safeTargetHeight);
            }

            String key = family + "#height#" + quantizeForCacheKey(safeTargetHeight) + "#t"
                    + quantizeForCacheKey(safeThickness) + "#" + getUiScale();
            return getCachedRenderer(key, new Supplier<FeralFontRenderer>() {
                public FeralFontRenderer get() {
                    Font baseFont = FontManager.getBaseFont(fontDefinition);
                    return baseFont == null
                            ? FontManager.getMinecraftRenderer(safeTargetHeight)
                            : FontManager.createHeightMatchedRenderer(baseFont, safeTargetHeight, safeThickness);
                }
            });
        } else {
            return getMinecraftRenderer(safeTargetHeight);
        }
    }

    private static FeralFontRenderer createHeightMatchedRenderer(Font baseFont, float targetHeight, float thickness) {
        float derivedSize = targetHeight;
        GlyphFontRenderer renderer = new GlyphFontRenderer(baseFont.deriveFont(derivedSize), true, thickness);

        for (int i = 0; i < 2; i++) {
            float measuredHeight = Math.max(1.0F, renderer.getFontHeight());
            float difference = Math.abs(measuredHeight - targetHeight);
            if (difference <= 0.5F) {
                break;
            }

            GlyphFontRenderer previousRenderer = renderer;
            derivedSize = Math.max(1.0F, derivedSize * (targetHeight / measuredHeight));
            renderer = new GlyphFontRenderer(baseFont.deriveFont(derivedSize), true, thickness);
            previousRenderer.destroy();
        }

        return renderer;
    }

    private static FeralFontRenderer getMinecraftRenderer(float fontSize) {
        float vanillaHeight = Math.max(1.0F, Minecraft.getInstance().textRenderer.fontHeight);
        final float scale = Math.max(0.5F, Math.min(2.0F, fontSize / vanillaHeight));
        String key = "Minecraft#" + quantizeForCacheKey(scale);
        return getCachedRenderer(key, new Supplier<FeralFontRenderer>() {
            public FeralFontRenderer get() {
                return new MinecraftFontAdapter(Minecraft.getInstance().textRenderer, scale);
            }
        });
    }

    public static boolean isMinecraftFont(String family) {
        return family == null || "Minecraft".equalsIgnoreCase(family);
    }

    private static Font getBaseFont(FontManager.FontDefinition definition) {
        Font cached = BASE_FONT_CACHE.get(definition.cacheKey);
        if (cached != null) {
            return cached;
        } else {
            Font loaded = definition.customFile == null
                    ? loadBundledFont(definition.bundledFileName)
                    : loadCustomFont(definition.customFile);
            if (loaded != null) {
                Font existing = BASE_FONT_CACHE.putIfAbsent(definition.cacheKey, loaded);
                return existing == null ? loaded : existing;
            } else {
                return null;
            }
        }
    }

    private static Font loadBundledFont(String fileName) {
        try {
            InputStream inputStream = FontManager.class.getResourceAsStream("/assets/keystrokesmod/fonts/" + fileName);

            Font var2;
            try {
                var2 = createFont(readFontData(inputStream));
            } catch (Throwable var5) {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable var4) {
                        var5.addSuppressed(var4);
                    }
                }

                throw var5;
            }

            if (inputStream != null) {
                inputStream.close();
            }

            return var2;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Font loadCustomFont(File file) {
        try {
            InputStream inputStream = new FileInputStream(file);

            Font var2;
            try {
                var2 = createFont(readFontData(inputStream));
            } catch (Throwable var5) {
                try {
                    inputStream.close();
                } catch (Throwable var4) {
                    var5.addSuppressed(var4);
                }

                throw var5;
            }

            inputStream.close();
            return var2;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Font createFont(byte[] fontData) {
        if (fontData == null) {
            return null;
        }

        try {
            return Font.createFont(0, new ByteArrayInputStream(fontData));
        } catch (Exception ignored) {
            try {
                return Font.createFont(1, new ByteArrayInputStream(fontData));
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static byte[] readFontData(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];

        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }

        return outputStream.toByteArray();
    }

    private static String makeCustomDisplayName(File file, Map<String, FontManager.FontDefinition> definitions) {
        String fileName = file.getName();
        int extensionIndex = fileName.lastIndexOf(46);
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String displayName = baseName;
        if (!definitions.containsKey(displayName)) {
            return displayName;
        }

        displayName = fileName;
        int suffix = 2;

        while (definitions.containsKey(displayName)) {
            displayName = fileName + " (" + suffix++ + ")";
        }

        return displayName;
    }

    private static boolean isSupportedFontFile(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".ttf") || lowerName.endsWith(".otf");
    }

    private static void ensureFontDirectory() {
        if (!FONT_DIRECTORY.exists()) {
            FONT_DIRECTORY.mkdirs();
        }
    }

    private static int getUiScale() {
        try {
            return Math.max(1, new Window(Minecraft.getInstance()).getScale());
        } catch (Exception ignored) {
            return 1;
        }
    }

    private static float quantizeForCacheKey(float value) {
        return Math.round(value * 100.0F) / 100.0F;
    }

    private static synchronized FeralFontRenderer getCachedRenderer(
            String key, Supplier<FeralFontRenderer> rendererSupplier) {
        FeralFontRenderer renderer = FONT_CACHE.get(key);
        if (renderer != null) {
            return renderer;
        }

        renderer = rendererSupplier.get();
        FONT_CACHE.put(key, renderer);
        trimFontCache();
        return renderer;
    }

    private static synchronized void clearRendererCache() {
        for (FeralFontRenderer renderer : FONT_CACHE.values()) {
            if (renderer != null) {
                renderer.destroy();
            }
        }

        FONT_CACHE.clear();
    }

    private static void trimFontCache() {
        while (FONT_CACHE.size() > 512) {
            Iterator<Entry<String, FeralFontRenderer>> iterator =
                    FONT_CACHE.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }

            Entry<String, FeralFontRenderer> eldestEntry = iterator.next();
            iterator.remove();
            if (eldestEntry.getValue() != null) {
                eldestEntry.getValue().destroy();
            }
        }
    }

    static {
        reloadCustomFonts();
    }

    private static final class FontDefinition {
        private final String displayName;
        private final String bundledFileName;
        private final File customFile;
        private final String cacheKey;

        private FontDefinition(String displayName, String bundledFileName, File customFile, String cacheKey) {
            this.displayName = displayName;
            this.bundledFileName = bundledFileName;
            this.customFile = customFile;
            this.cacheKey = cacheKey;
        }

        private static FontManager.FontDefinition bundled(String displayName, String fileName) {
            return new FontManager.FontDefinition(displayName, fileName, null, "bundled:" + fileName);
        }

        private static FontManager.FontDefinition custom(String displayName, File file) {
            return new FontManager.FontDefinition(displayName, null, file, "custom:" + file.getAbsolutePath());
        }
    }

    public static final class ReloadResult {
        private final int loaded;
        private final int failed;

        private ReloadResult(int loaded, int failed) {
            this.loaded = loaded;
            this.failed = failed;
        }

        public int getLoaded() {
            return this.loaded;
        }

        public int getFailed() {
            return this.failed;
        }
    }
}
