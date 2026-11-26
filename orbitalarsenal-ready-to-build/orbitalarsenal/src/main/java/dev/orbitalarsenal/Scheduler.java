package dev.orbitalarsenal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny replacement for BukkitRunnable / runTaskTimer. Ticked from END_SERVER_TICK. */
final class Scheduler {
    interface Task {
        /** @return true when the task is finished and should be removed */
        boolean run();
    }

    private static final class Entry {
        int countdown;
        final int period;
        final Task task;

        Entry(int delay, int period, Task task) {
            this.countdown = delay;
            this.period = Math.max(1, period);
            this.task = task;
        }
    }

    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final List<Entry> PENDING = new ArrayList<>();

    private Scheduler() {}

    /** Like runTaskTimer(plugin, delay, period). */
    static void repeat(int delay, int period, Task task) {
        PENDING.add(new Entry(delay, period, task));
    }

    /** Like runTaskLater. */
    static void later(int delay, Runnable r) {
        repeat(delay, 1, () -> {
            r.run();
            return true;
        });
    }

    static void tick() {
        ACTIVE.addAll(PENDING);
        PENDING.clear();
        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.countdown > 0) {
                e.countdown--;
                continue;
            }
            boolean done;
            try {
                done = e.task.run();
            } catch (Throwable t) {
                OrbitalArsenal.LOGGER.error("Scheduled task failed", t);
                done = true;
            }
            if (done) it.remove();
            else e.countdown = e.period - 1;
        }
    }

    static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }
}
