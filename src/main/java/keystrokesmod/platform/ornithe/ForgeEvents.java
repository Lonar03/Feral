package keystrokesmod.platform.ornithe;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import keystrokesmod.utility.WorldEntityScan;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;

public final class ForgeEvents {
    private ForgeEvents() {}

    @ForgeEvents.Cancelable
    public static final class AttackEntityEvent extends ForgeEvents.Event {
        public final PlayerEntity entityPlayer;
        public final Entity target;

        public AttackEntityEvent(PlayerEntity entityPlayer, Entity target) {
            this.entityPlayer = entityPlayer;
            this.target = target;
        }
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Cancelable {}

    @ForgeEvents.Cancelable
    public static final class ClientChatReceivedEvent extends ForgeEvents.Event {
        public final byte type;
        public Text message;

        public ClientChatReceivedEvent(byte type, Text message) {
            this.type = type;
            this.message = message;
        }
    }

    @ForgeEvents.Cancelable
    public static final class DrawBlockHighlightEvent extends ForgeEvents.Event {
        public final WorldRenderer context;
        public final PlayerEntity player;
        public final HitResult target;
        public final int subID;
        public final ItemStack currentItem;
        public final float partialTicks;

        public DrawBlockHighlightEvent(
                WorldRenderer context,
                PlayerEntity player,
                HitResult target,
                int subID,
                ItemStack currentItem,
                float partialTicks) {
            this.context = context;
            this.player = player;
            this.target = target;
            this.subID = subID;
            this.currentItem = currentItem;
            this.partialTicks = partialTicks;
        }
    }

    @ForgeEvents.Cancelable
    public static final class EntityJoinWorldEvent extends ForgeEvents.Event {
        public final Entity entity;
        public final World world;

        public EntityJoinWorldEvent(Entity entity, World world) {
            this.entity = entity;
            this.world = world;
        }
    }

    public static class Event {
        private boolean canceled;

        public final boolean isCanceled() {
            return this.canceled;
        }

        public final boolean isCancelable() {
            return this.getClass().isAnnotationPresent(ForgeEvents.Cancelable.class);
        }

        public final void setCanceled(boolean canceled) {
            if (!this.isCancelable()) {
                throw new IllegalStateException(
                        "Event cannot be canceled: " + this.getClass().getName());
            }

            this.canceled = canceled;
        }
    }

    public enum EventPriority {
        HIGHEST,
        HIGH,
        NORMAL,
        LOW,
        LOWEST;
    }

    public static final class FMLNetworkEvent {
        private FMLNetworkEvent() {}

        public static final class ClientDisconnectionFromServerEvent extends ForgeEvents.Event {}
    }

    @ForgeEvents.Cancelable
    public static final class GuiOpenEvent extends ForgeEvents.Event {
        public Screen gui;

        public GuiOpenEvent(Screen gui) {
            this.gui = gui;
        }
    }

    public static class GuiScreenEvent extends ForgeEvents.Event {
        public final Screen gui;

        GuiScreenEvent(Screen gui) {
            this.gui = gui;
        }

        public static class DrawScreenEvent extends ForgeEvents.GuiScreenEvent {
            public final int mouseX;
            public final int mouseY;
            public final float renderPartialTicks;

            DrawScreenEvent(Screen gui, int mouseX, int mouseY, float partialTicks) {
                super(gui);
                this.mouseX = mouseX;
                this.mouseY = mouseY;
                this.renderPartialTicks = partialTicks;
            }

            public static final class Post extends ForgeEvents.GuiScreenEvent.DrawScreenEvent {
                public Post(Screen gui, int mouseX, int mouseY, float partialTicks) {
                    super(gui, mouseX, mouseY, partialTicks);
                }
            }
        }

        public static class InitGuiEvent extends ForgeEvents.GuiScreenEvent {
            public List<ButtonWidget> buttonList;

            InitGuiEvent(Screen gui, List<ButtonWidget> buttonList) {
                super(gui);
                this.buttonList = buttonList;
            }

            public static final class Post extends ForgeEvents.GuiScreenEvent.InitGuiEvent {
                public Post(Screen gui, List<ButtonWidget> buttonList) {
                    super(gui, buttonList);
                }
            }

            @ForgeEvents.Cancelable
            public static final class Pre extends ForgeEvents.GuiScreenEvent.InitGuiEvent {
                public Pre(Screen gui, List<ButtonWidget> buttonList) {
                    super(gui, buttonList);
                }
            }
        }

        public static class MouseInputEvent extends ForgeEvents.GuiScreenEvent {
            MouseInputEvent(Screen gui) {
                super(gui);
            }

            @ForgeEvents.Cancelable
            public static final class Pre extends ForgeEvents.GuiScreenEvent.MouseInputEvent {
                public Pre(Screen gui) {
                    super(gui);
                }
            }
        }
    }

    public static class LivingEvent extends ForgeEvents.Event {
        public final LivingEntity entityLiving;
        public final Entity entity;

        LivingEvent(LivingEntity entity) {
            this.entityLiving = entity;
            this.entity = entity;
        }

        public static final class LivingJumpEvent extends ForgeEvents.LivingEvent {
            public LivingJumpEvent(LivingEntity entity) {
                super(entity);
            }
        }

        @ForgeEvents.Cancelable
        public static final class LivingUpdateEvent extends ForgeEvents.LivingEvent {
            public LivingUpdateEvent(LivingEntity entity) {
                super(entity);
            }
        }
    }

    public static final class LivingSetAttackTargetEvent extends ForgeEvents.LivingEvent {
        public final LivingEntity target;

        public LivingSetAttackTargetEvent(LivingEntity entity, LivingEntity target) {
            super(entity);
            this.target = target;
        }
    }

    public static final class MinecraftForge {
        public static final OrnitheEventBus EVENT_BUS = new OrnitheEventBus();

        private MinecraftForge() {}
    }

    @ForgeEvents.Cancelable
    public static final class MouseEvent extends ForgeEvents.Event {
        public final int x;
        public final int y;
        public final int dx;
        public final int dy;
        public final int dwheel;
        public final int button;
        public final boolean buttonstate;
        public final long nanoseconds;

        public MouseEvent() {
            this(
                    Mouse.getEventButton(),
                    Mouse.getEventButtonState(),
                    Mouse.getEventX(),
                    Mouse.getEventY(),
                    Mouse.getEventDX(),
                    Mouse.getEventDY(),
                    Mouse.getEventDWheel(),
                    Mouse.getEventNanoseconds());
        }

        public MouseEvent(int button, boolean pressed, int x, int y, int dx, int dy, int wheel, long time) {
            this.button = button;
            this.buttonstate = pressed;
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.dwheel = wheel;
            this.nanoseconds = time;
        }
    }

    public static class PlayerUseItemEvent extends ForgeEvents.Event {
        public final PlayerEntity entityPlayer;
        public final ItemStack item;
        public int duration;

        PlayerUseItemEvent(PlayerEntity entityPlayer, ItemStack item, int duration) {
            this.entityPlayer = entityPlayer;
            this.item = item;
            this.duration = duration;
        }

        public static final class Finish extends ForgeEvents.PlayerUseItemEvent {
            public ItemStack result;

            public Finish(PlayerEntity entityPlayer, ItemStack item, int duration, ItemStack result) {
                super(entityPlayer, item, duration);
                this.result = result;
            }
        }
    }

    public static class RenderLivingEvent<T extends LivingEntity> extends ForgeEvents.Event {
        public final LivingEntity entity;
        public final LivingEntityRenderer<T> renderer;
        public final double x;
        public final double y;
        public final double z;

        RenderLivingEvent(LivingEntity entity, LivingEntityRenderer<T> renderer, double x, double y, double z) {
            this.entity = entity;
            this.renderer = renderer;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static final class Post<T extends LivingEntity> extends ForgeEvents.RenderLivingEvent<T> {
            public Post(LivingEntity entity, LivingEntityRenderer<T> renderer, double x, double y, double z) {
                super(entity, renderer, x, y, z);
            }
        }

        @ForgeEvents.Cancelable
        public static final class Pre<T extends LivingEntity> extends ForgeEvents.RenderLivingEvent<T> {
            public Pre(LivingEntity entity, LivingEntityRenderer<T> renderer, double x, double y, double z) {
                super(entity, renderer, x, y, z);
            }
        }

        public static class Specials<T extends LivingEntity> extends ForgeEvents.RenderLivingEvent<T> {
            Specials(LivingEntity entity, LivingEntityRenderer<T> renderer, double x, double y, double z) {
                super(entity, renderer, x, y, z);
            }

            @ForgeEvents.Cancelable
            public static final class Pre<T extends LivingEntity> extends ForgeEvents.RenderLivingEvent.Specials<T> {
                public Pre(LivingEntity entity, LivingEntityRenderer<T> renderer, double x, double y, double z) {
                    super(entity, renderer, x, y, z);
                }
            }
        }
    }

    public static class RenderPlayerEvent extends ForgeEvents.Event {
        public final PlayerEntity entityPlayer;
        public final Entity entity;
        public final PlayerRenderer renderer;
        public final float partialRenderTick;
        public final double x;
        public final double y;
        public final double z;

        RenderPlayerEvent(
                PlayerEntity entity, PlayerRenderer renderer, float partialTicks, double x, double y, double z) {
            this.entityPlayer = entity;
            this.entity = entity;
            this.renderer = renderer;
            this.partialRenderTick = partialTicks;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static final class Post extends ForgeEvents.RenderPlayerEvent {
            public Post(
                    PlayerEntity entity, PlayerRenderer renderer, float partialTicks, double x, double y, double z) {
                super(entity, renderer, partialTicks, x, y, z);
            }
        }

        @ForgeEvents.Cancelable
        public static final class Pre extends ForgeEvents.RenderPlayerEvent {
            public Pre(PlayerEntity entity, PlayerRenderer renderer, float partialTicks, double x, double y, double z) {
                super(entity, renderer, partialTicks, x, y, z);
            }
        }
    }

    public static final class RenderWorldLastEvent extends ForgeEvents.Event {
        public final WorldRenderer context;
        public final float partialTicks;
        private WorldEntityScan entityScan;

        public RenderWorldLastEvent(WorldRenderer context, float partialTicks) {
            this.context = context;
            this.partialTicks = partialTicks;
        }

        /** Reuse ordered discovery on this render post without capturing any mutable render state. */
        public List<Entity> entityCandidates(World world, int kinds, WorldEntityScan.Kind kind) {
            this.entityScan = WorldEntityScan.refresh(this.entityScan, world, kinds | kind.bit());
            return this.entityScan == null ? world.entities : this.entityScan.candidates(kind);
        }

        public List<Entity> existingEntityCandidates(World world, WorldEntityScan.Kind kind) {
            return this.entityScan == null ? world.entities : this.entityScan.candidatesIfFresh(world, kind);
        }
    }

    public enum Side {
        CLIENT,
        SERVER;

        public boolean isClient() {
            return this == CLIENT;
        }

        public boolean isServer() {
            return this == SERVER;
        }
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface SideOnly {
        ForgeEvents.Side value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface SubscribeEvent {
        ForgeEvents.EventPriority priority() default ForgeEvents.EventPriority.NORMAL;

        boolean receiveCanceled() default false;
    }

    public static class TickEvent extends ForgeEvents.Event {
        public final ForgeEvents.TickEvent.Phase phase;
        public final ForgeEvents.Side side;

        TickEvent(ForgeEvents.TickEvent.Phase phase, ForgeEvents.Side side) {
            this.phase = phase;
            this.side = side;
        }

        public static final class ClientTickEvent extends ForgeEvents.TickEvent {
            private WorldEntityScan entityScan;

            public ClientTickEvent(ForgeEvents.TickEvent.Phase phase) {
                super(phase, ForgeEvents.Side.CLIENT);
            }

            /** Not the render snapshot, and never reused across ticks or phases. */
            public List<Entity> entityCandidates(World world, int kinds, WorldEntityScan.Kind kind) {
                this.entityScan = WorldEntityScan.refresh(this.entityScan, world, kinds | kind.bit());
                return this.entityScan == null ? world.entities : this.entityScan.candidates(kind);
            }
        }

        public enum Phase {
            START,
            END;
        }

        public static final class PlayerTickEvent extends ForgeEvents.TickEvent {
            public final PlayerEntity player;

            public PlayerTickEvent(ForgeEvents.TickEvent.Phase phase, PlayerEntity player) {
                super(phase, player.world.isClient ? ForgeEvents.Side.CLIENT : ForgeEvents.Side.SERVER);
                this.player = player;
            }
        }

        public static final class RenderTickEvent extends ForgeEvents.TickEvent {
            public final float renderTickTime;

            public RenderTickEvent(ForgeEvents.TickEvent.Phase phase, float renderTickTime) {
                super(phase, ForgeEvents.Side.CLIENT);
                this.renderTickTime = renderTickTime;
            }
        }
    }

    public static class WorldEvent extends ForgeEvents.Event {
        public final World world;

        WorldEvent(World world) {
            this.world = world;
        }

        public static final class Load extends ForgeEvents.WorldEvent {
            public Load(World world) {
                super(world);
            }
        }

        public static final class Unload extends ForgeEvents.WorldEvent {
            public Unload(World world) {
                super(world);
            }
        }
    }
}
