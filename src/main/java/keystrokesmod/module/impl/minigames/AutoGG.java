// Ported from Meowtils (wtf.tatp.meowtils), modified for Meower and Feral
package keystrokesmod.module.impl.minigames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.ClientChatReceivedEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.EntityJoinWorldEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;

/**
 * Says gg in all chat when a Hypixel game ends and gl when it's about to start. Random mode picks from the lists
 * edited with .autogg / .autogl (saved in keystrokes/autogg_list.json and autogl_list.json).
 */
public class AutoGG extends Module {
    private static final String[] GAME_END_MESSAGES = {
        "1st Killer -",
        "1st Place -",
        "Winner:",
        "- Damage Dealt -",
        "Winning Team -",
        "1st -",
        "Winners:",
        "Winning Team:",
        " won the game!",
        "Top Seeker:",
        "1st Place:",
        "Last team standing!",
        "Winner #1 (",
        "Top Survivors",
        "Winners -",
        "Sumo Duel -",
        "WINNER",
        "1st Killer"
    };
    private static final File DIRECTORY = new File(Minecraft.getInstance().gameDir, "keystrokes");
    private static final File GG_FILE = new File(DIRECTORY, "autogg_list.json");
    private static final File GL_FILE = new File(DIRECTORY, "autogl_list.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RANDOM = new Random();
    private static final List<String> ggMessages = new ArrayList<>();
    private static final List<String> glMessages = new ArrayList<>();
    private static String lastGgMessage = "";
    private static String lastGlMessage = "";

    private final ButtonSetting ggEnabled;
    private final ButtonSetting ggRandom;
    private final ButtonSetting sendFirst;
    private final TextSetting firstMessage;
    private final SliderSetting firstDelay;
    private final ButtonSetting sendSecond;
    private final TextSetting secondMessage;
    private final SliderSetting secondDelay;
    private final ButtonSetting glEnabled;
    private final ButtonSetting glRandom;
    private final TextSetting glMessage;
    private final SliderSetting glSendAt;
    private final List<PendingMessage> pending = new ArrayList<>();
    // One gg per game; reset when the world changes.
    private boolean saidGg;

    public AutoGG() {
        super("AutoGG", Module.category.minigames);
        load();
        GroupSetting gg = new GroupSetting("Auto GG");
        this.registerSetting(gg);
        this.registerSetting(this.ggEnabled = new ButtonSetting(gg, "GG enabled", true));
        this.registerSetting(this.ggRandom = new ButtonSetting(gg, "GG random message", false));
        this.registerSetting(this.sendFirst = new ButtonSetting(gg, "Send first message", true));
        this.registerSetting(this.firstMessage = new TextSetting(gg, "First message", "gg", "Type a message...", 100));
        this.registerSetting(this.firstDelay = new SliderSetting(gg, "First delay", "ms", 0.0, 0.0, 1000.0, 50.0));
        this.registerSetting(this.sendSecond = new ButtonSetting(gg, "Send second message", false));
        this.registerSetting(
                this.secondMessage = new TextSetting(gg, "Second message", "<3", "Type a message...", 100));
        this.registerSetting(this.secondDelay = new SliderSetting(gg, "Second delay", "ms", 0.0, 0.0, 1000.0, 50.0));

        GroupSetting gl = new GroupSetting("Auto GL");
        this.registerSetting(gl);
        this.registerSetting(this.glEnabled = new ButtonSetting(gl, "GL enabled", false));
        this.registerSetting(this.glRandom = new ButtonSetting(gl, "GL random message", false));
        this.registerSetting(this.glMessage = new TextSetting(gl, "GL message", "glhf", "Type a message...", 100));
        this.registerSetting(this.glSendAt = new SliderSetting(gl, "Send at", "s", 5.0, 1.0, 15.0, 1.0));
    }

    @Override
    public void guiUpdate() {
        // Random mode draws from the .autogg/.autogl lists, so the fixed message boxes only matter without it.
        this.firstMessage.setVisible(this.sendFirst.isToggled() && !this.ggRandom.isToggled(), this);
        this.firstDelay.setVisible(this.sendFirst.isToggled(), this);
        this.secondMessage.setVisible(this.sendSecond.isToggled() && !this.ggRandom.isToggled(), this);
        this.secondDelay.setVisible(this.sendSecond.isToggled(), this);
        this.glMessage.setVisible(!this.glRandom.isToggled(), this);
    }

    @Override
    public void onDisable() {
        this.pending.clear();
        this.saidGg = false;
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.saidGg = false;
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || e.message == null || !Utils.isHypixel()) {
            return;
        }

        String msg = Utils.stripColor(e.message.getString());
        // Player chat always has a colon; game announcements don't.
        if (msg.contains(":")) {
            return;
        }

        if (this.ggEnabled.isToggled()
                && !this.saidGg
                && Arrays.stream(GAME_END_MESSAGES).anyMatch(msg::contains)) {
            this.saidGg = true;
            if (this.sendFirst.isToggled()) {
                this.queueGg(this.firstMessage, this.firstDelay);
            }

            if (this.sendSecond.isToggled()) {
                this.queueGg(this.secondMessage, this.secondDelay);
            }
        }

        int sendAt = (int) this.glSendAt.getInput();
        if (this.glEnabled.isToggled()
                && msg.contains("The game starts in " + sendAt + (sendAt == 1 ? " second!" : " seconds!"))) {
            String text = this.glRandom.isToggled()
                    ? lastGlMessage = pickRandom(glMessages, lastGlMessage)
                    : this.glMessage.getText();
            this.send(text, "AutoGL");
        }
    }

    private void queueGg(TextSetting message, SliderSetting delay) {
        String text =
                this.ggRandom.isToggled() ? lastGgMessage = pickRandom(ggMessages, lastGgMessage) : message.getText();
        this.pending.add(new PendingMessage(System.currentTimeMillis() + (long) delay.getInput(), text));
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent e) {
        if (e.phase != Phase.END || this.pending.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Iterator<PendingMessage> it = this.pending.iterator(); it.hasNext(); ) {
            PendingMessage message = it.next();
            if (now >= message.sendAt) {
                it.remove();
                this.send(message.text, "AutoGG");
            }
        }
    }

    private void send(String text, String tag) {
        if (!Utils.nullCheck()) {
            return;
        }

        if (text == null || text.trim().isEmpty()) {
            Utils.sendMessage("&9[" + tag + "] &fNo message set!");
        } else {
            mc.player.sendChat("/ac " + text.trim());
        }
    }

    // Never the same message twice in a row when there's a choice.
    private static String pickRandom(List<String> messages, String last) {
        if (messages.isEmpty()) {
            return "";
        } else if (messages.size() == 1) {
            return messages.get(0);
        }

        String next;
        do {
            next = messages.get(RANDOM.nextInt(messages.size()));
        } while (next.equals(last));

        return next;
    }

    // ============================== message lists (.autogg / .autogl) ==============================

    public static List<String> getMessages(boolean gl) {
        return gl ? glMessages : ggMessages;
    }

    public static boolean addMessage(boolean gl, String message) {
        List<String> list = getMessages(gl);
        if (list.contains(message)) {
            return false;
        }

        list.add(message);
        save();
        return true;
    }

    public static boolean removeMessage(boolean gl, String message) {
        boolean removed = getMessages(gl).remove(message);
        if (removed) {
            save();
        }

        return removed;
    }

    private static void load() {
        boolean createdGg = read(GG_FILE, ggMessages, Arrays.asList("gg", "Good Game", "gf"));
        boolean createdGl = read(GL_FILE, glMessages, Arrays.asList("Have a nice game!", "glhf"));
        if (createdGg || createdGl) {
            save();
        }
    }

    /** Fills the list from the file, or from the defaults when there's no file yet (returns true then). */
    private static boolean read(File file, List<String> target, List<String> defaults) {
        target.clear();
        if (!file.isFile() || file.length() == 0L) {
            target.addAll(defaults);
            return true;
        }

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            List<String> data = GSON.fromJson(reader, new TypeToken<List<String>>() {}.getType());
            if (data != null) {
                target.addAll(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private static void save() {
        DIRECTORY.mkdirs();
        write(GG_FILE, ggMessages);
        write(GL_FILE, glMessages);
    }

    private static void write(File file, List<String> messages) {
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(messages, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final class PendingMessage {
        private final long sendAt;
        private final String text;

        private PendingMessage(long sendAt, String text) {
            this.sendAt = sendAt;
            this.text = text;
        }
    }
}
