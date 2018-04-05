package com.amannmalik.robot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by amannmalik on 3/20/17.
 */
public class PeriodicTask {

    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private final AtomicReference<ScheduledFuture<?>> currentSchedule = new AtomicReference<>();
    private final AtomicReference<Future> currentTask = new AtomicReference<>();

    public PeriodicTask() {
        this.currentTask.set(executorService.submit(() -> {
        }));
    }

    public void start(int intervalMilliseconds, Runnable nextTask) {

        stop();

        currentSchedule.set(scheduledExecutorService.scheduleAtFixedRate(() -> {
            Future previousTask = currentTask.get();
            if (previousTask.isDone()) {
                currentTask.set(executorService.submit(nextTask));
            }
        }, intervalMilliseconds, intervalMilliseconds, TimeUnit.MILLISECONDS));
    }

    public void stop() {
        ScheduledFuture<?> scheduledFuture = currentSchedule.get();
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }


}
