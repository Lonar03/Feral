package keystrokesmod.mixin.impl.accessor;

import java.util.Queue;
import java.util.concurrent.FutureTask;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TickTimer;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(Minecraft.class)
public interface IAccessorMinecraft {
    @Accessor("tasks")
    Queue<FutureTask<?>> getScheduledTasks();

    @Accessor("timer")
    TickTimer getTimer();

    @Accessor("connection")
    Connection getMyNetworkManager();

    @Accessor("useKeyCooldown")
    int getRightClickDelayTimer();

    @Accessor("useKeyCooldown")
    void setRightClickDelayTimer(int var1);

    @Accessor("attackCooldown")
    int getLeftClickCounter();

    @Accessor("attackCooldown")
    void setLeftClickCounter(int var1);

    @Invoker("doUse")
    void callRightClickMouse();

    @Invoker("doAttack")
    void callClickMouse();

    @Invoker("handleGuiKeyBindings")
    void invokeDispatchKeypresses();
}
