package keystrokesmod.module.impl.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import keystrokesmod.clickgui.HudPositionEditor;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.PotionListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FeralFontRenderer;
import keystrokesmod.utility.font.FontManager;
import net.minecraft.client.render.Window;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.locale.I18n;

public class PotionHUD extends Module {
    private static final String[] TIME_FORMAT_OPTIONS = new String[] {"1m20s", "01:20"};
    private static final String[] SORT_OPTIONS = new String[] {"Duration", "Length"};
    private static final String[] SORT_DIRECTION_OPTIONS = new String[] {"Descending", "Ascending"};
    private static final String[] ALIGNMENT_OPTIONS = new String[] {"Left", "Center", "Right"};
    private static final String[] VERTICAL_ALIGNMENT_OPTIONS = new String[] {"Top", "Center", "Bottom"};
    private static final float DEFAULT_RELATIVE_X = 0.5F;
    private static final float DEFAULT_RELATIVE_Y = 0.5F;
    private static final int TIMER_COLOR = -5592406;
    private static final int EDIT_OUTLINE_COLOR = -1;
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 110).getRGB();
    private static final String PLACEHOLDER_TEXT = "No Potions Active";
    private final SliderSetting timeFormat;
    private final SliderSetting sortMode;
    private final SliderSetting sortDirection;
    private final SliderSetting horizontalAlignment;
    private final SliderSetting verticalAlignment;
    private final SliderSetting font;
    private final SliderSetting scale;
    private final ButtonSetting excludePermanent;
    private final ButtonSetting drawBackground;
    private final ButtonSetting textShadow;
    private final PotionListSetting potionBlacklist;
    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;

    public PotionHUD() {
        super("Potion HUD", Module.category.render);
        this.registerSetting(this.timeFormat = new SliderSetting("Time Format", 0, TIME_FORMAT_OPTIONS));
        this.registerSetting(this.sortMode = new SliderSetting("Sort By", 0, SORT_OPTIONS));
        this.registerSetting(this.sortDirection = new SliderSetting("Sort Direction", 0, SORT_DIRECTION_OPTIONS));
        this.registerSetting(
                this.horizontalAlignment =
                        new SliderSetting("Horizontal Alignment", 0, ALIGNMENT_OPTIONS, "Alignment"));
        this.registerSetting(
                this.verticalAlignment =
                        new SliderSetting("Vertical Alignment", 0, VERTICAL_ALIGNMENT_OPTIONS, "Direction"));
        this.registerSetting(this.font = FontManager.createFontSetting("Font"));
        this.registerSetting(this.scale = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.openScreen(new PotionHUD.EditScreen())));
        this.registerSetting(this.excludePermanent = new ButtonSetting("Exclude permanent", false));
        this.registerSetting(this.drawBackground = new ButtonSetting("Draw background", false));
        this.registerSetting(this.textShadow = new ButtonSetting("Text shadow", true));
        this.registerSetting(
                this.potionBlacklist = new PotionListSetting("Blacklisted potions", "Potions", "Potion blacklist"));
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck()) {
            if (mc.screen == null && !mc.options.debugEnabled) {
                this.render(false);
            }
        }
    }

    public float getPosX() {
        this.syncPositionToResolution();
        return this.posX;
    }

    public float getPosY() {
        this.syncPositionToResolution();
        return this.posY;
    }

    public float getRelativePosX() {
        this.syncPositionToResolution();
        return this.relativePosX;
    }

    public float getRelativePosY() {
        this.syncPositionToResolution();
        return this.relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        this.relativePosX = normalizedX;
        this.relativePosY = normalizedY;
        this.syncPositionToResolution();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        this.setAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    public void resetPosition() {
        this.setRelativePosition(0.5F, 0.5F);
    }

    private void render(boolean editing) {
        Window resolution = new Window(mc);
        this.syncPositionToResolution(resolution);
        PotionHUD.RenderState state = this.buildRenderState(editing);
        if (!state.entries.isEmpty()) {
            this.adjustAnchorForLayoutChanges(resolution, state);
            PotionHUD.Bounds bounds = this.renderState(state);
            if (editing) {
                this.drawBounds(bounds);
            }
        }
    }

    private PotionHUD.RenderState buildRenderState(boolean includePlaceholder) {
        FeralFontRenderer renderer = this.getFontRenderer();
        PotionHUD.LayoutMetrics metrics = PotionHUD.LayoutMetrics.from(renderer, this.getSelectedScale());
        ArrayList<PotionHUD.PotionEntry> entries = new ArrayList<>();
        Collection<StatusEffectInstance> activeEffects = mc.player.getStatusEffects();
        if (activeEffects != null) {
            for (StatusEffectInstance effect : activeEffects) {
                PotionHUD.PotionEntry entry = this.buildEntry(effect, renderer);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        this.sortEntries(entries);
        if (entries.isEmpty() && includePlaceholder) {
            entries.add(PotionHUD.PotionEntry.placeholder(renderer));
        }

        int maxWidth = 0;

        for (PotionHUD.PotionEntry entry : entries) {
            maxWidth = Math.max(maxWidth, entry.totalWidth);
        }

        return new PotionHUD.RenderState(renderer, metrics, entries, maxWidth);
    }

    private PotionHUD.PotionEntry buildEntry(StatusEffectInstance effect, FeralFontRenderer renderer) {
        if (effect == null) {
            return null;
        }

        int duration = effect.getDuration();
        if (this.excludePermanent.isToggled() && duration > 32000) {
            return null;
        }

        StatusEffect potion = StatusEffect.BY_ID[effect.getId()];
        if (potion == null) {
            return null;
        }

        if (this.potionBlacklist.containsPotion(potion.getTranslationKey())) {
            return null;
        }

        String label = this.getPotionLabel(effect, potion);
        String durationText = this.formatDuration(duration);
        int labelWidth = renderer.getStringWidth(label);
        int durationWidth = durationText.isEmpty() ? 0 : renderer.getStringWidth(durationText);
        int gapWidth = durationWidth > 0 ? renderer.getStringWidth(" ") : 0;
        return new PotionHUD.PotionEntry(
                label, durationText, labelWidth, durationWidth, gapWidth, duration, potion.getPotionColor());
    }

    private void sortEntries(List<PotionHUD.PotionEntry> entries) {
        final int directionMultiplier = (int) this.sortDirection.getInput() == 1 ? 1 : -1;
        if ((int) this.sortMode.getInput() == 0) {
            entries.sort(new Comparator<PotionHUD.PotionEntry>() {
                public int compare(PotionHUD.PotionEntry first, PotionHUD.PotionEntry second) {
                    return Integer.compare(first.durationTicks, second.durationTicks) * directionMultiplier;
                }
            });
        } else {
            entries.sort(new Comparator<PotionHUD.PotionEntry>() {
                public int compare(PotionHUD.PotionEntry first, PotionHUD.PotionEntry second) {
                    return Integer.compare(first.totalWidth, second.totalWidth) * directionMultiplier;
                }
            });
        }
    }

    private PotionHUD.Bounds renderState(PotionHUD.RenderState state) {
        int horizontalAlignMode = (int) this.horizontalAlignment.getInput();
        int verticalAlignMode = (int) this.verticalAlignment.getInput();
        float stackHeight = state.entries.size() * state.metrics.rowHeight;
        float firstRowTop = verticalAlignMode == 0
                ? this.posY
                : (verticalAlignMode == 1 ? this.posY - stackHeight / 2.0F : this.posY - stackHeight);
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int i = 0; i < state.entries.size(); i++) {
            PotionHUD.PotionEntry entry = state.entries.get(i);
            float rowTop = firstRowTop + i * state.metrics.rowHeight;
            float rowLeft = horizontalAlignMode == 0
                    ? this.posX
                    : (horizontalAlignMode == 2 ? this.posX - entry.totalWidth : this.posX - entry.totalWidth / 2.0F);
            float backgroundLeft = rowLeft - state.metrics.horizontalTextPadding;
            float backgroundRight = rowLeft + entry.totalWidth + state.metrics.horizontalTextPadding;
            float backgroundBottom = rowTop + state.metrics.rowHeight;
            float textY = rowTop + state.metrics.textTopPadding - state.metrics.textTopOffset;
            float timerX = rowLeft + entry.labelWidth + entry.gapWidth;
            if (this.drawBackground.isToggled()) {
                RenderUtils.drawRect(backgroundLeft, rowTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
            }

            state.renderer.drawString(entry.label, rowLeft, textY, entry.color, this.textShadow.isToggled());
            if (!entry.durationText.isEmpty()) {
                state.renderer.drawString(entry.durationText, timerX, textY, -5592406, this.textShadow.isToggled());
            }

            minX = Math.min(minX, backgroundLeft);
            minY = Math.min(minY, rowTop);
            maxX = Math.max(maxX, backgroundRight);
            maxY = Math.max(maxY, backgroundBottom);
        }

        return new PotionHUD.Bounds(minX, minY, maxX, maxY);
    }

    private void drawBounds(PotionHUD.Bounds bounds) {
        float left = bounds.left - 1.0F;
        float top = bounds.top - 1.0F;
        float right = bounds.right + 1.0F;
        float bottom = bounds.bottom + 1.0F;
        RenderUtils.drawRect(left, top, right, top + 1.0F, -1);
        RenderUtils.drawRect(left, bottom - 1.0F, right, bottom, -1);
        RenderUtils.drawRect(left, top, left + 1.0F, bottom, -1);
        RenderUtils.drawRect(right - 1.0F, top, right, bottom, -1);
    }

    private void adjustAnchorForLayoutChanges(Window resolution, PotionHUD.RenderState state) {
        this.syncPositionToResolution(resolution);
    }

    private void syncPositionToResolution() {
        this.syncPositionToResolution(new Window(mc));
    }

    private void syncPositionToResolution(Window resolution) {
        int scaledWidth = Math.max(1, resolution.getWidth());
        int scaledHeight = Math.max(1, resolution.getHeight());
        if (Float.isNaN(this.relativePosX) || Float.isNaN(this.relativePosY)) {
            if (!Float.isNaN(this.posX) && !Float.isNaN(this.posY)) {
                this.relativePosX = this.posX / scaledWidth;
                this.relativePosY = this.posY / scaledHeight;
            } else {
                this.relativePosX = 0.5F;
                this.relativePosY = 0.5F;
            }
        }

        this.posX = this.relativePosX * scaledWidth;
        this.posY = this.relativePosY * scaledHeight;
    }

    private void setAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.posX = absoluteX;
        this.posY = absoluteY;
        int scaledWidth = Math.max(1, resolution.getWidth());
        int scaledHeight = Math.max(1, resolution.getHeight());
        this.relativePosX = absoluteX / scaledWidth;
        this.relativePosY = absoluteY / scaledHeight;
    }

    private FeralFontRenderer getFontRenderer() {
        return FontManager.getHudRenderer(this.getSelectedFontName(), this.getSelectedScale());
    }

    private String getSelectedFontName() {
        int index = (int) Math.max(0.0, Math.min(this.font.getOptions().length - 1, this.font.getInput()));
        return this.font.getOptions()[index];
    }

    private float getSelectedScale() {
        return (float) this.scale.getInput();
    }

    private String getPotionLabel(StatusEffectInstance effect, StatusEffect potion) {
        String label = I18n.translate(effect.getName());
        if (effect.getAmplifier() >= 1) {
            label = label + " " + toRomanNumeral(effect.getAmplifier() + 1);
        }

        return label.isEmpty() ? I18n.translate(potion.getTranslationKey()) : label;
    }

    private String formatDuration(int durationTicks) {
        int totalSeconds = Math.max(0, durationTicks / 20);
        if ((int) this.timeFormat.getInput() == 1) {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        } else {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return minutes > 0 && seconds == 0 ? minutes + "m" : (minutes > 0 ? minutes + "m" : "") + seconds + "s";
        }
    }

    private static int getScaledHudPixels(float basePixels, float fontScale) {
        return Math.max(1, Math.round(basePixels * fontScale));
    }

    private static String toRomanNumeral(int value) {
        if (value <= 0) {
            return Integer.toString(value);
        }

        int[] values = new int[] {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = new String[] {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        int remaining = value;

        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                builder.append(numerals[i]);
                remaining -= values[i];
            }
        }

        return builder.toString();
    }

    private static final class Bounds {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private class EditScreen extends HudPositionEditor {
        private float minX;
        private float minY;
        private float maxX;
        private float maxY;

        private EditScreen() {
            super("Edit the HUD position by dragging.", 20);
        }

        @Override
        protected void syncAnchor(Window window) {
            PotionHUD.this.syncPositionToResolution(window);
        }

        @Override
        protected float anchorX() {
            return PotionHUD.this.posX;
        }

        @Override
        protected float anchorY() {
            return PotionHUD.this.posY;
        }

        @Override
        protected void drawPreview(float x, float y, Window window) {
            PotionHUD.this.setAbsolutePosition(x, y, window);
            PotionHUD.RenderState state = PotionHUD.this.buildRenderState(true);
            PotionHUD.this.adjustAnchorForLayoutChanges(window, state);
            PotionHUD.Bounds bounds = PotionHUD.this.renderState(state);
            PotionHUD.this.drawBounds(bounds);
            this.minX = bounds.left;
            this.minY = bounds.top;
            this.maxX = bounds.right;
            this.maxY = bounds.bottom;
        }

        @Override
        protected boolean hitsPreview(int mouseX, int mouseY) {
            return mouseX >= this.minX && mouseX <= this.maxX && mouseY >= this.minY && mouseY <= this.maxY;
        }

        @Override
        protected void resetAnchor() {
            PotionHUD.this.resetPosition();
        }
    }

    private static final class LayoutMetrics {
        private final int textTopOffset;
        private final int textTopPadding;
        private final int horizontalTextPadding;
        private final int rowHeight;

        private LayoutMetrics(int textTopOffset, int textTopPadding, int horizontalTextPadding, int rowHeight) {
            this.textTopOffset = textTopOffset;
            this.textTopPadding = textTopPadding;
            this.horizontalTextPadding = horizontalTextPadding;
            this.rowHeight = rowHeight;
        }

        private static PotionHUD.LayoutMetrics from(FeralFontRenderer renderer, float fontScale) {
            int textTopOffset = renderer.getTextTopOffset();
            int textBottomOffset = renderer.getTextBottomOffset();
            int textTopPadding = PotionHUD.getScaledHudPixels(2.0F, fontScale);
            int textBottomPadding = 0;
            int horizontalTextPadding = PotionHUD.getScaledHudPixels(2.0F, fontScale);
            int textBoxHeight = Math.max(1, textBottomOffset - textTopOffset);
            int rowHeight = Math.max(1, textBoxHeight + textTopPadding + textBottomPadding);
            return new PotionHUD.LayoutMetrics(textTopOffset, textTopPadding, horizontalTextPadding, rowHeight);
        }
    }

    private static final class PotionEntry {
        private final String label;
        private final String durationText;
        private final int labelWidth;
        private final int durationWidth;
        private final int gapWidth;
        private final int totalWidth;
        private final int durationTicks;
        private final int color;

        private PotionEntry(
                String label,
                String durationText,
                int labelWidth,
                int durationWidth,
                int gapWidth,
                int durationTicks,
                int color) {
            this.label = label;
            this.durationText = durationText;
            this.labelWidth = labelWidth;
            this.durationWidth = durationWidth;
            this.gapWidth = gapWidth;
            this.totalWidth = labelWidth + gapWidth + durationWidth;
            this.durationTicks = durationTicks;
            this.color = color;
        }

        private static PotionHUD.PotionEntry placeholder(FeralFontRenderer renderer) {
            String text = "No Potions Active";
            return new PotionHUD.PotionEntry(text, "", renderer.getStringWidth(text), 0, 0, 0, -1);
        }
    }

    private static final class RenderState {
        private final FeralFontRenderer renderer;
        private final PotionHUD.LayoutMetrics metrics;
        private final List<PotionHUD.PotionEntry> entries;
        private final int maxWidth;

        private RenderState(
                FeralFontRenderer renderer,
                PotionHUD.LayoutMetrics metrics,
                List<PotionHUD.PotionEntry> entries,
                int maxWidth) {
            this.renderer = renderer;
            this.metrics = metrics;
            this.entries = entries;
            this.maxWidth = maxWidth;
        }
    }
}
