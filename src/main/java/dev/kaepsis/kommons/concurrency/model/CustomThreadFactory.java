package dev.kaepsis.kommons.concurrency.model;


import org.jspecify.annotations.NonNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadFactory implements ThreadFactory {

    private final String baseName;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public CustomThreadFactory(String baseName) {
        this.baseName = baseName;
    }

    @Override
    public Thread newThread(@NonNull Runnable r) {
        Thread t = new Thread(r, baseName + " - " + threadNumber.getAndIncrement());
        t.setDaemon(true);
        return t;
    }
}
