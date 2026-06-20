package dev.kaepsis.kommons.concurrency;

import dev.kaepsis.kommons.concurrency.model.CustomThreadFactory;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A flexible asynchronous scheduler wrapper
 * <p>
 * This class abstracts the management of asynchronous execution pools, automatically
 * leveraging cutting-edge Java features (like Virtual Threads) when running on modern runtimes,
 * while transparently falling back to traditional platform thread pools on legacy environments.
 * </p>
 *
 * @author Alzyy
 * @version 260515
 * @since 260515
 */
public class AsyncScheduler {

    private final ExecutorService executor;
    private final ScheduledExecutorService timerExecutor;
    private final JavaPlugin instance;

    /**
     * Constructs a dynamic asynchronous scheduler.
     * <p>
     * If the server environment is running on <b>Java 21 or newer</b>, this constructor will bypass
     * traditional thread pools and instantiate a thread-per-task executor utilizing lightweight
     * <b>Virtual Threads</b>. This provides near-infinite scaling for I/O-bound operations.
     * </p>
     * <p>
     * If running on a <b>legacy Java version</b>, it falls back to an elastic {@link Executors#newCachedThreadPool()},
     * which dynamically provisions standard platform threads as needed.
     * </p>
     *
     * @param instance      the {@link JavaPlugin} instance initializing this scheduler
     * @param threadFactory an optional, custom {@link ThreadFactory} to use for platform threads;
     * if {@code null}, a default {@link CustomThreadFactory} with custom naming will be generated
     */
    public AsyncScheduler(JavaPlugin instance, @Nullable ThreadFactory threadFactory) {
        this.instance = instance;

        if (isJava21OrNewer()) {
            ThreadFactory virtualFactory = Thread.ofVirtual()
                    .name(instance.getName() + "-Virtual-", 0)
                    .factory();
            this.executor = Executors.newThreadPerTaskExecutor(virtualFactory);
            this.timerExecutor = Executors.newScheduledThreadPool(1, virtualFactory);
            instance.getLogger().info("Java 21+ detected. Using named Virtual Threads.");
        } else {
            if (threadFactory == null) {
                threadFactory = new CustomThreadFactory(instance.getName() + "-Async-Thread");
            }
            this.executor = Executors.newCachedThreadPool(threadFactory);
            this.timerExecutor = Executors.newScheduledThreadPool(2, threadFactory);
            instance.getLogger().info("Legacy Java detected. Using Platform Threads pool.");
        }
    }

    /**
     * Constructs a strictly bounded asynchronous scheduler with a fixed number of platform threads.
     * <p>
     * This constructor bypasses Virtual Threads intentionally and guarantees that no more than
     * the specified {@code threadCount} of operating system platform threads will ever run concurrently.
     * </p>
     * <p>
     * This approach is ideal for <b>CPU-heavy</b> background operations (such as custom world generation,
     * pathfinding algorithms, or heavy data crunching) where unbounded thread creation could severely
     * bottleneck the host machine's physical processor.
     * </p>
     *
     * @param instance    the {@link JavaPlugin} instance initializing this scheduler
     * @param threadCount the maximum number of concurrent platform threads allowed in the pool
     * @throws IllegalArgumentException if {@code threadCount} is less than or equal to 0
     */
    public AsyncScheduler(JavaPlugin instance, int threadCount) {
        this.instance = instance;

        if (threadCount <= 0) {
            throw new IllegalArgumentException("Thread count must be greater than 0");
        }

        ThreadFactory factory = new CustomThreadFactory(instance.getName() + "-Fixed-Thread");

        this.executor = Executors.newFixedThreadPool(threadCount, factory);
        this.timerExecutor = Executors.newScheduledThreadPool(Math.max(1, threadCount / 2), factory);
        instance.getLogger().info("Creating fixed async thread pool with " + threadCount + " threads.");
    }

    /**
     * Gracefully shuts down the underlying executor pools.
     * <p>
     * <b>Important:</b> This method must be invoked within your plugin's {@code onDisable()} method
     * to prevent persistent memory leaks and lingering threads during server reloads.
     * </p>
     */
    public void shutdown() {
        instance.getLogger().info("Shutting down AsyncScheduler...");
        executor.shutdown();
        timerExecutor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!timerExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                timerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            timerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Executes a task asynchronously.
     *
     * @param task the {@link Runnable} task to execute background
     */
    public void runTask(Runnable task) {
        if (executor.isShutdown()) {
            instance.getLogger().warning("Cannot submit task. The scheduler has been shut down.");
            return;
        }
        executor.execute(task);
    }

    /**
     * Submits a value-returning task for asynchronous execution.
     *
     * @param <T>  the type of the task's result
     * @param task the {@link Callable} task to execute background
     * @return a {@link Future} representing pending completion of the task
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (executor.isShutdown()) {
            throw new IllegalStateException("Cannot submit task. The scheduler has been shut down.");
        }
        return executor.submit(task);
    }

    /**
     * Submits a task for asynchronous execution and returns a modern {@link CompletableFuture}.
     * <p>
     * This allows developers using your API to pipeline asynchronous operations cleanly
     * using fluent interfaces like {@link CompletableFuture#thenAccept(java.util.function.Consumer)}.
     * </p>
     *
     * @param <T>  the type of the task's result
     * @param task the {@link Supplier} task returning a value
     * @return a {@link CompletableFuture} representing the async task outcome
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        if (executor.isShutdown()) {
            throw new IllegalStateException("Cannot submit task. The scheduler has been shut down.");
        }
        return CompletableFuture.supplyAsync(task, executor);
    }

    /**
     * Executes a task asynchronously and, once completed, runs a callback back on Bukkit's Main Thread.
     *
     * @param <T>          the type of data processed asynchronously
     * @param asyncTask code that runs on the async pool (e.g., database queries or HTTP requests)
     * @param syncCallback code that processes the result back on the Minecraft main server thread
     */
    public <T> void runTaskWithCallback(Callable<T> asyncTask, Consumer<T> syncCallback) {
        this.runTask(() -> {
            try {
                T result = asyncTask.call();
                Bukkit.getScheduler().runTask(instance, () -> syncCallback.accept(result));
            } catch (Exception e) {
                instance.getLogger().severe("An error occurred during an asynchronous task execution with callback!");
                e.printStackTrace();
            }
        });
    }

    /**
     * Runs a task asynchronously after a specific delay.
     *
     * @param task  the {@link Runnable} task to run
     * @param delay the time to delay execution
     * @param unit  the time unit of the delay parameter
     * @return a {@link ScheduledFuture} representing pending completion of the task
     */
    public ScheduledFuture<?> runTaskLater(Runnable task, long delay, TimeUnit unit) {
        if (timerExecutor.isShutdown()) {
            throw new IllegalStateException("Cannot schedule task. The scheduler has been shut down.");
        }
        return timerExecutor.schedule(() -> runTask(task), delay, unit);
    }

    /**
     * Runs a repeating task asynchronously.
     *
     * @param task         the {@link Runnable} task to repeat
     * @param initialDelay the time to delay first execution
     * @param period       the period between successive executions
     * @param unit         the time unit of the delay and period parameters
     * @return a {@link ScheduledFuture} representing pending completion of the task
     */
    public ScheduledFuture<?> runTaskTimer(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (timerExecutor.isShutdown()) {
            throw new IllegalStateException("Cannot schedule periodic task. The scheduler has been shut down.");
        }
        return timerExecutor.scheduleAtFixedRate(() -> runTask(task), initialDelay, period, unit);
    }

    private boolean isJava21OrNewer() {
        try {
            String version = System.getProperty("java.version");
            if (version.startsWith("1.")) {
                version = version.substring(2, 3);
            } else {
                int dot = version.indexOf(".");
                if (dot != -1) {
                    version = version.substring(0, dot);
                }
            }
            return Integer.parseInt(version) >= 21;
        } catch (Exception e) {
            return false;
        }
    }
}