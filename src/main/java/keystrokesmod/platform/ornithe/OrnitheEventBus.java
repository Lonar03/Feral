package keystrokesmod.platform.ornithe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class OrnitheEventBus {
    private volatile List<OrnitheEventBus.Listener> listeners = Collections.emptyList();

    public synchronized void register(Object target) {
        for (OrnitheEventBus.Listener listener : this.listeners) {
            if (listener.target == target) {
                return;
            }
        }

        List<OrnitheEventBus.Listener> updated = new ArrayList<>(this.listeners);

        for (Method method : target.getClass().getMethods()) {
            ForgeEvents.SubscribeEvent subscription = method.getAnnotation(ForgeEvents.SubscribeEvent.class);
            if (subscription != null && !method.isBridge()) {
                if (method.getParameterCount() != 1
                        || !ForgeEvents.Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    throw new IllegalArgumentException("Invalid event listener: " + method);
                }

                method.setAccessible(true);
                updated.add(new OrnitheEventBus.Listener(target, method, subscription));
            }
        }

        updated.sort(Comparator.comparing(listenerx -> listenerx.subscription.priority()));
        this.listeners = updated;
    }

    public synchronized void unregister(Object target) {
        List<OrnitheEventBus.Listener> updated = new ArrayList<>(this.listeners);
        if (updated.removeIf(listener -> listener.target == target)) {
            this.listeners = updated;
        }
    }

    public boolean post(ForgeEvents.Event event) {
        for (OrnitheEventBus.Listener listener : this.listeners) {
            if (listener.eventType.isInstance(event)
                    && (!event.isCanceled() || listener.subscription.receiveCanceled())) {
                listener.invoke(event);
            }
        }

        return event.isCanceled();
    }

    private static final class Listener {
        private final Object target;
        private final Method method;
        private final Class<?> eventType;
        private final ForgeEvents.SubscribeEvent subscription;

        private Listener(Object target, Method method, ForgeEvents.SubscribeEvent subscription) {
            this.target = target;
            this.method = method;
            this.eventType = method.getParameterTypes()[0];
            this.subscription = subscription;
        }

        private void invoke(ForgeEvents.Event event) {
            try {
                this.method.invoke(this.target, event);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not dispatch " + this.method, exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                } else if (cause instanceof Error) {
                    throw (Error) cause;
                } else {
                    throw new IllegalStateException("Event listener failed: " + this.method, cause);
                }
            }
        }
    }
}
