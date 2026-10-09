package com.sumirelabs.kita.discord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.LoggerFactory;

public final class WorkExecutor implements AutoCloseable {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(64);

    public boolean submit(Runnable work) {
        if (!capacity.tryAcquire()) return false;
        executor.submit(() -> {
            try { work.run(); }
            catch (Exception error) {
                LoggerFactory.getLogger(WorkExecutor.class).error("Background operation failed", error);
            } finally { capacity.release(); }
        });
        return true;
    }

    @Override public void close() { executor.close(); }
}
