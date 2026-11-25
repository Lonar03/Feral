package keystrokesmod;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.command.CommandManager;
import keystrokesmod.event.PostProfileLoadEvent;
import keystrokesmod.event.PostSetSliderEvent;
import keystrokesmod.helper.DebugHelper;
import keystrokesmod.helper.MouseHelper;
import keystrokesmod.helper.PingHelper;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.lag.handler.UnifiedLagHandler;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.script.ScriptDefaults;
import keystrokesmod.script.ScriptManager;
import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.NetworkPlayer;
import keystrokesmod.utility.BlockHighlightSharedHandler;
import keystrokesmod.utility.FrozenEntitySync;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.PacketsHandler;
import keystrokesmod.utility.PlayerRelationsManager;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.profile.Profile;
import keystrokesmod.utility.profile.ProfileManager;
import net.minecraft.client.Minecraft;

public class Feral {
    public static boolean DEBUG = false;
    public static final String VERSION = "1.0.0";
    public static Minecraft mc = Minecraft.getInstance();
    private static final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);
    private static final ExecutorService cachedExecutor = Executors.newCachedThreadPool();
    public static ModuleManager moduleManager;
    public static ClickGui clickGui;
    public static ProfileManager profileManager;
    public static ScriptManager scriptManager;
    public static CommandManager commandManager;
    public static PlayerRelationsManager playerRelationsManager;
    public static Profile currentProfile;
    public static PacketsHandler packetsHandler;
    public static UnifiedLagHandler lagHandler;
    private static boolean firstLoad;

    public Feral() {
        moduleManager = new ModuleManager();
    }

    public void initialize() {
        Runtime.getRuntime().addShutdownHook(new Thread(scheduledExecutor::shutdown));
        Runtime.getRuntime().addShutdownHook(new Thread(cachedExecutor::shutdown));
        ForgeEvents.MinecraftForge.EVENT_BUS.register(this);
        ForgeEvents.MinecraftForge.EVENT_BUS.register(new DebugHelper());
        ForgeEvents.MinecraftForge.EVENT_BUS.register(new MouseHelper());
        ForgeEvents.MinecraftForge.EVENT_BUS.register(RotationHelper.get());
        ForgeEvents.MinecraftForge.EVENT_BUS.register(new keystrokesmod.helper.KeyInputTracker());
        ForgeEvents.MinecraftForge.EVENT_BUS.register(new PingHelper());
        ForgeEvents.MinecraftForge.EVENT_BUS.register(packetsHandler = new PacketsHandler());
        ForgeEvents.MinecraftForge.EVENT_BUS.register(new ModuleUtils());
        ForgeEvents.MinecraftForge.EVENT_BUS.register(lagHandler = new UnifiedLagHandler());
        ReflectionUtils.setupFields();
        playerRelationsManager = new PlayerRelationsManager();
        playerRelationsManager.load();
        moduleManager.register();
        keystrokesmod.helper.zombies.ZombiesGameHelper.init();
        ForgeEvents.MinecraftForge.EVENT_BUS.register(new BlockHighlightSharedHandler());
        ForgeEvents.MinecraftForge.EVENT_BUS.register(new keystrokesmod.clickgui.BigCatEvents());
        scriptManager = new ScriptManager();
        clickGui = new ClickGui();
        profileManager = new ProfileManager();
        ScriptDefaults.reloadModules();
        scriptManager.loadScripts();
        profileManager.initializeProfiles();
        ReflectionUtils.setKeyBindings();
        commandManager = new CommandManager();
    }

    @ForgeEvents.SubscribeEvent
    public void onTick(ForgeEvents.TickEvent.ClientTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.END) {
            if (profileManager != null) {
                profileManager.tickAutoSave();
            }

            if (Utils.nullCheck()) {
                if (mc.player.ticks % 6000 == 0) {
                    Entity.clearCache();
                    NetworkPlayer.clearCache();
                    if (DebugHelper.BACKGROUND) {
                        Utils.sendMessage(
                                "&aticks % 6000 == 0 &7reached, clearing script caches. (&dEntity&7, &dNetworkPlayer&7)");
                    }
                }

                if (ReflectionUtils.ERROR) {
                    Utils.sendMessage("&cThere was an error, relaunch the game.");
                    ReflectionUtils.ERROR = false;
                }

                MouseHelper.updateWheelCache();

                for (Module module : getModuleManager().getModules()) {
                    if (mc.screen == null && module.canBeEnabled()) {
                        module.onKeyBind();
                    } else if (mc.screen instanceof ClickGui) {
                        module.guiUpdate();
                        module.syncKeyBindState();
                    } else {
                        module.syncKeyBindState();
                    }

                    if (module.isEnabled()) {
                        module.onUpdate();
                    }
                }

                if (mc.screen == null) {
                    for (Module module : scriptManager.scripts.values()) {
                        module.onKeyBind();
                    }
                } else {
                    for (Module module : scriptManager.scripts.values()) {
                        module.syncKeyBindState();
                    }

                    if (mc.screen instanceof ClickGui) {
                        if (this.applyKillAuraRangeConstraints()) {
                            clickGui.onSliderChange();
                        }

                        if (mc.player.getHealth() <= 0.0F) {
                            mc.openScreen(null);
                        }
                    }
                }
            }

        } else {
            MouseHelper.clearWheelCache();
            if (mc.screen == null && Utils.nullCheck()) {
                for (Profile profile : profileManager.profiles) {
                    profile.getModule().onKeyBind();
                }
            } else if (Utils.nullCheck()) {
                for (Profile profile : profileManager.profiles) {
                    profile.getModule().syncKeyBindState();
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPostProfileLoad(PostProfileLoadEvent e) {
        this.applyKillAuraRangeConstraints();
        clickGui.onSliderChange();
    }

    @ForgeEvents.SubscribeEvent
    public void onPostSetSlider(PostSetSliderEvent e) {
        this.applyKillAuraRangeConstraints();
        clickGui.onSliderChange();
    }

    @ForgeEvents.SubscribeEvent
    public void onEntityJoinWorld(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            if (!firstLoad) {
                firstLoad = true;
                profileManager.reloadAfterJoiningWorld();
            }

            Entity.clearCache();
            NetworkPlayer.clearCache();
            FrozenEntitySync.get().clearAll();
            if (DebugHelper.BACKGROUND) {
                Utils.sendMessage("&enew world&7, clearing script caches. (&dEntity&7, &dNetworkPlayer&7)");
            }
        }
    }

    public static ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    public static ExecutorService getCachedExecutor() {
        return cachedExecutor;
    }

    public static void handleFrozenKeybinds() {
        if (Utils.nullCheck()) {
            MouseHelper.updateWheelCache();
            if (mc.screen == null) {
                for (Module module : getModuleManager().getModules()) {
                    if (module.canBeEnabled()) {
                        module.onKeyBind();
                    }
                }

                for (Module module : scriptManager.scripts.values()) {
                    module.onKeyBind();
                }
            } else if (mc.screen instanceof ClickGui) {
                for (Module module : getModuleManager().getModules()) {
                    module.guiUpdate();
                    module.syncKeyBindState();
                }

                for (Module module : scriptManager.scripts.values()) {
                    module.syncKeyBindState();
                }
            } else {
                for (Module module : getModuleManager().getModules()) {
                    module.syncKeyBindState();
                }

                for (Module module : scriptManager.scripts.values()) {
                    module.syncKeyBindState();
                }
            }
        }
    }

    private boolean applyKillAuraRangeConstraints() {
        if (ModuleManager.killAura == null) {
            return false;
        }

        SliderSetting attackRange = ModuleManager.killAura.getAttackRangeSetting();
        SliderSetting swingRange = ModuleManager.killAura.getSwingRangeSetting();
        SliderSetting aimRange = ModuleManager.killAura.getAimRangeSetting();
        if (attackRange != null && swingRange != null && aimRange != null) {
            boolean changed = false;
            double attack = attackRange.getInput();
            double swing = swingRange.getInput();
            double aim = aimRange.getInput();
            if (swing < attack) {
                swingRange.setValue(attack);
                swing = swingRange.getInput();
                changed = true;
            }

            if (aim < swing) {
                aimRange.setValue(swing);
                changed = true;
            }

            return changed;
        } else {
            return false;
        }
    }
}
