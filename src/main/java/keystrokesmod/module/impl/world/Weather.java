package keystrokesmod.module.impl.world;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import java.util.Set;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.network.packet.s2c.play.WorldTimeS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldData;

public class Weather extends Module {
    public final SliderSetting time;
    public final SliderSetting lightning;
    public final ButtonSetting rain;
    public Weather.Lightning mode = Weather.Lightning.Disabled;
    private static final Random RANDOM = new Random();
    private static Field activeChunkSet;
    private static Field updateLCG;
    private ClientWorld weatherWorld;
    private WorldData rainInfo;
    private long savedTime = -9L;
    private long savedRain = -1L;
    private boolean savedDaylightCycle;
    private boolean savedServerRain;
    private float savedRainStrength;
    private long lastThunder;

    public Weather() {
        super("Weather", Module.category.world);
        this.registerSetting(this.time = new SliderSetting("Time", "", "§eVanilla", 0.0, 0.0, 24.0, 0.3));
        this.registerSetting(
                this.lightning = new SliderSetting("Lightning", 0, new String[] {
                    "Disabled", "Vanilla", "Uncommon", "Common", "Frequent", "Intense", "Chaotic"
                }));
        this.lightning.setMinString("§cDisabled");
        this.registerSetting(this.rain = new ButtonSetting("Rain", false));
        this.liteModule = true;
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        this.synchronizeWorld();
        this.guiUpdate();
    }

    @Override
    public void guiUpdate() {
        this.mode = Weather.Lightning.values()[
                MathHelper.clamp((int) this.lightning.getInput(), 0, Weather.Lightning.values().length - 1)];
    }

    @Override
    public void onUpdate() {
        this.synchronizeWorld();
        if (this.weatherWorld != null) {
            this.guiUpdate();
            this.updateTime();
            this.updateRain();
            this.strike();
        }
    }

    @Override
    public void onDisable() {
        this.restoreTime();
        this.restoreRain();
        this.weatherWorld = null;
        this.lastThunder = 0L;
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldUnload(ForgeEvents.WorldEvent.Unload event) {
        if (event.world == this.weatherWorld) {
            this.onDisable();
        }
    }

    private void synchronizeWorld() {
        if (this.weatherWorld != mc.world) {
            this.restoreTime();
            this.restoreRain();
            this.weatherWorld = mc.world;
            this.lastThunder = 0L;
        }
    }

    private void updateTime() {
        if (this.time.getInput() != 0.0) {
            if (this.savedTime == -9L) {
                this.savedTime = this.weatherWorld.getTimeOfDay();
                this.savedDaylightCycle = this.weatherWorld.getGameRules().getBoolean("doDaylightCycle");
            }

            this.weatherWorld.setTimeOfDay((long) (-1000.0 * (16.0 + this.time.getInput())));
        } else {
            this.restoreTime();
        }
    }

    private void restoreTime() {
        if (this.savedTime != -9L && this.weatherWorld != null) {
            this.weatherWorld.setTimeOfDay(this.savedTime);
            this.weatherWorld.getGameRules().set("doDaylightCycle", Boolean.toString(this.savedDaylightCycle));
        }

        this.savedTime = -9L;
    }

    private void updateRain() {
        if (this.rain.isToggled()) {
            if (this.savedRain == -1L) {
                this.savedRain = this.weatherWorld.getData().isRaining() ? 1L : 0L;
                this.savedRainStrength = this.weatherWorld.getRain(1.0F);
                this.rainInfo = this.worldInfo();
                this.savedServerRain = this.rainInfo.isRaining();
            }

            this.rainInfo.setRaining(true);
            this.weatherWorld.getData().setRaining(true);
            this.weatherWorld.setRain(1.0F);
        } else {
            this.restoreRain();
        }
    }

    private void restoreRain() {
        if (this.savedRain != -1L) {
            if (this.rainInfo != null) {
                this.rainInfo.setRaining(this.savedServerRain);
            }

            if (this.weatherWorld != null) {
                this.weatherWorld.getData().setRaining(this.savedRain == 1L);
                this.weatherWorld.setRain(this.savedRainStrength);
            }
        }

        this.savedRain = -1L;
        this.rainInfo = null;
    }

    private WorldData worldInfo() {
        MinecraftServer server = mc.getServer();
        if (server != null && server.worlds != null && server.worlds.length > 0) {
            ServerWorld world = server.getWorld(this.weatherWorld.dimension.getId());
            if (world != null) {
                return world.getData();
            }
        }

        return this.weatherWorld.getData();
    }

    private void strike() {
        Minecraft mc = Minecraft.getInstance();
        if (this.lightning.getInput() != 0.0 && !mc.isPaused()) {
            try {
                if (activeChunkSet == null) {
                    activeChunkSet =
                            ClientPlatform.ReflectionHelper.findField(World.class, "activeChunkSet", "field_72993_I");
                    updateLCG = ClientPlatform.ReflectionHelper.findField(World.class, "updateLCG", "field_73005_l");
                }

                long l = System.currentTimeMillis();

                for (ChunkPos c : (Set<ChunkPos>) activeChunkSet.get(mc.world)) {
                    if (this.mode.shouldStrike()) {
                        int x = c.x * 16;
                        int z = c.z * 16;
                        int n3 = (Integer) updateLCG.get(mc.world);
                        n3 = n3 * 3 + 1013904223;
                        updateLCG.set(mc.world, n3);
                        int n4 = n3 >> 2;
                        BlockPos pos = this.target(new BlockPos(x + (n4 & 15), 0, z + (n4 >> 8 & 15)));
                        if (mc.world.hasSkyAccess(pos)) {
                            mc.world.addGlobalEntity(
                                    new LightningBoltEntity(mc.world, pos.getX(), pos.getY(), pos.getZ()));
                            if (Math.abs(l - this.lastThunder) > 250L) {
                                mc.world.playSound(
                                        pos.getX(),
                                        pos.getY(),
                                        pos.getZ(),
                                        "ambient.weather.thunder",
                                        10.0F,
                                        0.8F + RANDOM.nextFloat() * 0.2F,
                                        false);
                                this.lastThunder = l;
                            }
                        }
                    }
                }
            } catch (Exception var12) {
            }
        }
    }

    private BlockPos target(BlockPos blockPos) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos top = mc.world.getPrecipitationHeight(blockPos);
        BlockPos sky = new BlockPos(top.getX(), mc.world.getHeight(), top.getZ());
        Box box = new Box(top, sky).grown(3.0, 3.0, 3.0);
        List<LivingEntity> list = mc.world.getEntitiesOfType(LivingEntity.class, box, Weather::canStrike);
        return !list.isEmpty() ? list.get(RANDOM.nextInt(list.size())).getCommandSourceBlockPos() : top;
    }

    private static boolean canStrike(LivingEntity e) {
        return e != null && e.isAlive() && Minecraft.getInstance().world.hasSkyAccess(e.getCommandSourceBlockPos());
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent event) {
        if (this.time.getInput() != 0.0 && event.getPacket() instanceof WorldTimeS2CPacket && mc.world != null) {
            WorldTimeS2CPacket packet = (WorldTimeS2CPacket) event.getPacket();
            ClientWorld world = mc.world;
            event.setCanceled(true);
            Runnable update = () -> {
                if (mc.world == world) {
                    world.setTime(packet.getTime());
                    if (this.isEnabled() && this.time.getInput() != 0.0) {
                        this.synchronizeWorld();
                        this.savedTime = packet.getTimeOfDay();
                        this.savedDaylightCycle = this.savedTime >= 0L;
                    } else {
                        world.setTimeOfDay(packet.getTimeOfDay());
                        this.savedTime = -9L;
                    }
                }
            };
            if (mc.isOnSameThread()) {
                update.run();
            } else {
                mc.executeTask(update);
            }
        }
    }

    public enum Lightning {
        Disabled,
        Vanilla,
        Uncommon,
        Common,
        Frequent,
        Intense,
        Chaotic;

        public boolean shouldStrike() {
            if (this == Vanilla) {
                return Weather.RANDOM.nextInt(100000) == 0;
            }

            double d = Math.random();
            switch (this) {
                case Uncommon:
                    return d > 0.9999;
                case Common:
                    return d > 0.999;
                case Frequent:
                    return d > 0.99;
                case Intense:
                    return d > 0.9;
                case Chaotic:
                    return d > 0.3;
                default:
                    return false;
            }
        }
    }
}
