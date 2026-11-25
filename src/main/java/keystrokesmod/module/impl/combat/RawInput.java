package keystrokesmod.module.impl.combat;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.Utils;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Mouse;

public class RawInput extends Module {
    private net.minecraft.client.Mouse originalHelper;
    private RawInput.RawMouseHelper rawHelper;
    private RawInput.RawMouseThread pollThread;

    public RawInput() {
        super("Raw Input", Module.category.combat, 0);
        this.registerSetting(new DescriptionSetting("Bypasses OS mouse acceleration."));
    }

    @Override
    public void onEnable() {
        if (!isRawInputSupported()) {
            Utils.sendMessage("&cRaw input libraries not found.");
            this.disable();
        } else {
            this.originalHelper = mc.mouse;
            this.pollThread = new RawInput.RawMouseThread();
            this.pollThread.start();
            this.rawHelper = new RawInput.RawMouseHelper(this.pollThread);
            mc.mouse = this.rawHelper;
        }
    }

    @Override
    public void onDisable() {
        if (this.originalHelper != null) {
            mc.mouse = this.originalHelper;
            this.originalHelper = null;
        }

        if (this.pollThread != null) {
            this.pollThread.running = false;
            this.pollThread = null;
        }

        this.rawHelper = null;
    }

    private static boolean isRawInputSupported() {
        String path = System.getProperty("java.library.path");
        if (path == null) {
            return false;
        }

        String separator = File.pathSeparator;
        String mapped = System.mapLibraryName("jinput-raw");

        for (String dir : path.split(separator.isEmpty() ? ";" : separator)) {
            if (new File(dir, mapped).exists()) {
                return true;
            }
        }

        String mappedDx = System.mapLibraryName("jinput-dx8");

        for (String dir : path.split(separator.isEmpty() ? ";" : separator)) {
            if (new File(dir, mappedDx).exists()) {
                return true;
            }
        }

        return false;
    }

    private static String getEnvironmentClass() {
        String path = System.getProperty("java.library.path");
        if (path == null) {
            return null;
        }

        String separator = File.pathSeparator;
        boolean hasDx = false;
        boolean hasRaw = false;
        String mappedDx = System.mapLibraryName("jinput-dx8");
        String mappedRaw = System.mapLibraryName("jinput-raw");

        for (String dir : path.split(separator.isEmpty() ? ";" : separator)) {
            if (new File(dir, mappedDx).exists()) {
                hasDx = true;
            }

            if (new File(dir, mappedRaw).exists()) {
                hasRaw = true;
            }
        }

        if (hasDx && !hasRaw) {
            return "net.java.games.input.DirectInputEnvironmentPlugin";
        } else {
            return hasRaw ? "net.java.games.input.DirectAndRawInputEnvironmentPlugin" : null;
        }
    }

    static class RawMouseHelper extends net.minecraft.client.Mouse {
        private final RawInput.RawMouseThread thread;
        private int fails;

        RawMouseHelper(RawInput.RawMouseThread thread) {
            this.thread = thread;
        }

        public void lock() {
            this.thread.reset();
            super.lock();
        }

        public void tick() {
            if (!this.thread.mice.isEmpty() && this.thread.isAlive()) {
                boolean movement = false;
                this.x = this.thread.dx.getAndSet(0);
                this.y = this.thread.dy.getAndSet(0);
                movement = this.x != 0 || this.y != 0;
                if ((Math.abs(org.lwjgl.input.Mouse.getDX()) > 5 || Math.abs(org.lwjgl.input.Mouse.getDY()) > 5)
                        && !movement) {
                    if (this.fails++ > 5) {
                        this.thread.rescan();
                    }
                } else if (movement) {
                    this.fails = 0;
                }
            } else {
                super.tick();
            }
        }
    }

    static class RawMouseThread extends Thread {
        final AtomicInteger dx = new AtomicInteger(0);
        final AtomicInteger dy = new AtomicInteger(0);
        volatile boolean running = true;
        volatile List<Mouse> mice = new ArrayList<>();

        RawMouseThread() {
            super("Feral Raw Mouse Input");
            this.setDaemon(true);
        }

        @Override
        public void run() {
            this.rescan();

            while (this.running) {
                try {
                    if (this.mice.isEmpty()) {
                        Thread.sleep(50L);
                    } else {
                        Iterator ignored = this.mice.iterator();

                        while (true) {
                            if (ignored.hasNext()) {
                                Mouse m = (Mouse) ignored.next();
                                if (m.poll()) {
                                    this.dx.addAndGet((int) m.getX().getPollData());
                                    this.dy.addAndGet(-((int) m.getY().getPollData()));
                                    continue;
                                }

                                this.rescan();
                            }

                            Thread.sleep(1L);
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }

        void rescan() {
            try {
                String envClass = RawInput.getEnvironmentClass();
                if (envClass == null) {
                    return;
                }

                Constructor<?> ctor = Class.forName(envClass).getDeclaredConstructor();
                ctor.setAccessible(true);
                ControllerEnvironment env = (ControllerEnvironment) ctor.newInstance();
                List<Mouse> found = new ArrayList<>();

                for (Controller c : env.getControllers()) {
                    if (c instanceof Mouse) {
                        found.add((Mouse) c);
                    }
                }

                this.mice = found;
            } catch (Exception var9) {
            }
        }

        void reset() {
            this.dx.set(0);
            this.dy.set(0);
        }
    }
}
