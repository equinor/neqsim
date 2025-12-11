package neqsim.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A global thread pool for NeqSim concurrent operations.
 *
 * <p>
 * This class provides a centralized, managed thread pool for executing tasks asynchronously. It is
 * designed to replace direct {@code new Thread(...)} calls throughout the codebase, providing
 * better resource management and control over concurrent execution.
 * </p>
 *
 * <p>
 * The thread pool size defaults to the number of available processors but can be configured. The
 * pool uses daemon threads so it won't prevent JVM shutdown.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class NeqSimThreadPool {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqSimThreadPool.class);

  /** Default pool size based on available processors. */
  private static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors();

  /** The shared ExecutorService instance. */
  private static volatile ExecutorService pool;

  /** Lock object for thread-safe initialization. */
  private static final Object LOCK = new Object();

  /** Configured pool size (can be changed before first use). */
  private static int poolSize = DEFAULT_POOL_SIZE;

  /** Counter for thread naming. */
  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

  /** Default thread factory for creating threads with proper settings. */
  private static final ThreadFactory DEFAULT_FACTORY = Executors.defaultThreadFactory();

  /**
   * Maximum queue capacity for bounded queue mode. Set to 0 or negative for unbounded queue
   * (default).
   */
  private static int maxQueueCapacity = 0;

  /**
   * Whether to allow core threads to time out and terminate when idle. Default is false (threads
   * stay alive forever).
   */
  private static boolean allowCoreThreadTimeout = false;

  /**
   * Keep-alive time for idle threads in seconds. Only applies when allowCoreThreadTimeout is true.
   * Default is 60 seconds.
   */
  private static long keepAliveTimeSeconds = 600;

  /**
   * Private constructor to prevent instantiation.
   */
  private NeqSimThreadPool() {
    // Utility class - prevent instantiation
  }

  /**
   * Gets the shared ExecutorService instance. The pool is lazily initialized on first access.
   *
   * @return the shared {@link ExecutorService} instance
   */
  public static ExecutorService getPool() {
    if (pool == null || pool.isShutdown()) {
      synchronized (LOCK) {
        if (pool == null || pool.isShutdown()) {
          pool = createPool();
        }
      }
    }
    return pool;
  }

  /**
   * Creates a new thread pool with the configured size.
   *
   * @return a new {@link ExecutorService} instance
   */
  private static ExecutorService createPool() {
    logger.info("Creating NeqSim thread pool with {} threads", poolSize);

    ThreadFactory threadFactory = r -> {
      Thread t = DEFAULT_FACTORY.newThread(r);
      t.setDaemon(true);
      t.setName("NeqSim-Worker-" + THREAD_COUNTER.getAndIncrement());
      t.setUncaughtExceptionHandler((th, ex) -> {
        logger.error("Uncaught exception in thread " + th.getName(), ex);
      });
      return t;
    };

    ThreadPoolExecutor executor;
    if (maxQueueCapacity > 0) {
      // Bounded queue for extreme load scenarios (HPC)
      logger.info("Using bounded queue with capacity {}", maxQueueCapacity);
      RejectedExecutionHandler rejectionHandler = (runnable, ex) -> {
        logger.warn("Task rejected due to queue overflow. Consider increasing queue capacity.");
        throw new java.util.concurrent.RejectedExecutionException(
            "Task rejected: queue capacity exceeded (" + maxQueueCapacity + ")");
      };
      executor = new ThreadPoolExecutor(poolSize, poolSize, keepAliveTimeSeconds, TimeUnit.SECONDS,
          new LinkedBlockingQueue<>(maxQueueCapacity), threadFactory, rejectionHandler);
    } else {
      // Unbounded queue (default behavior)
      executor = new ThreadPoolExecutor(poolSize, poolSize, keepAliveTimeSeconds, TimeUnit.SECONDS,
          new LinkedBlockingQueue<>(), threadFactory);
    }

    // Allow core threads to timeout when idle (for memory efficiency in long-running processes)
    if (allowCoreThreadTimeout) {
      executor.allowCoreThreadTimeOut(true);
      logger.info("Core thread timeout enabled with {} seconds keep-alive", keepAliveTimeSeconds);
    }

    return executor;
  }

  /**
   * Submits a Runnable task for execution and returns a Future representing that task.
   *
   * @param task the task to submit
   * @return a Future representing pending completion of the task
   */
  public static Future<?> submit(Runnable task) {
    return getPool().submit(task);
  }

  /**
   * Submits a Callable task for execution and returns a Future representing the pending result.
   *
   * <p>
   * This overload allows tasks to return results directly, which is useful for integration with
   * external systems like Python via JPype.
   * </p>
   *
   * @param <T> the type of the result
   * @param task the task to submit
   * @return a Future representing pending completion of the task with its result
   */
  public static <T> Future<T> submit(Callable<T> task) {
    return getPool().submit(task);
  }

  /**
   * Executes the given task sometime in the future without waiting for completion.
   *
   * @param task the task to execute
   */
  public static void execute(Runnable task) {
    getPool().execute(task);
  }

  /**
   * Creates a new {@link CompletionService} backed by the shared thread pool.
   *
   * <p>
   * A CompletionService allows you to submit multiple tasks and retrieve their results in
   * completion order (i.e., as each task finishes) rather than submission order. This is useful
   * when you want to process results as soon as they become available.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   * 
   * <pre>
   * {@code
   * CompletionService<Double> cs = NeqSimThreadPool.newCompletionService();
   * for (ProcessSystem p : processes) {
   *   cs.submit(() -> {
   *     p.run();
   *     return p.getResult();
   *   });
   * }
   * for (int i = 0; i < processes.size(); i++) {
   *   Double result = cs.take().get(); // Results arrive in completion order
   *   System.out.println("Got result: " + result);
   * }
   * }
   * </pre>
   *
   * @param <T> the type of results produced by the tasks
   * @return a new {@link CompletionService} backed by the shared thread pool
   */
  public static <T> CompletionService<T> newCompletionService() {
    return new ExecutorCompletionService<>(getPool());
  }

  /**
   * Sets the pool size. This must be called before the pool is first accessed. If called after the
   * pool has been created, it will recreate the pool with the new size (existing tasks will
   * complete).
   *
   * @param size the desired pool size (must be greater than 0)
   * @throws IllegalArgumentException if size is less than 1
   */
  public static void setPoolSize(int size) {
    if (size < 1) {
      throw new IllegalArgumentException("Pool size must be at least 1, was: " + size);
    }
    synchronized (LOCK) {
      poolSize = size;
      if (pool != null && !pool.isShutdown()) {
        // Shutdown existing pool gracefully and create new one
        shutdownAndAwait(5, TimeUnit.SECONDS);
        pool = createPool();
        logger.info("NeqSim thread pool resized to {} threads", size);
      }
    }
  }

  /**
   * Gets the current pool size configuration.
   *
   * @return the configured pool size
   */
  public static int getPoolSize() {
    return poolSize;
  }

  /**
   * Sets the maximum queue capacity for bounded queue mode.
   *
   * <p>
   * For HPC or extreme load scenarios, you can limit the queue size to prevent memory exhaustion.
   * When the queue is full, new task submissions will be rejected with a
   * {@link java.util.concurrent.RejectedExecutionException}.
   * </p>
   *
   * <p>
   * Set to 0 or negative to use unbounded queue (default). This must be called before the pool is
   * first accessed, or the pool will be recreated.
   * </p>
   *
   * @param capacity the maximum number of tasks that can wait in the queue (0 = unbounded)
   */
  public static void setMaxQueueCapacity(int capacity) {
    synchronized (LOCK) {
      maxQueueCapacity = capacity;
      if (pool != null && !pool.isShutdown()) {
        shutdownAndAwait(5, TimeUnit.SECONDS);
        pool = createPool();
        logger.info("NeqSim thread pool queue capacity set to {}",
            capacity > 0 ? capacity : "unbounded");
      }
    }
  }

  /**
   * Gets the current maximum queue capacity.
   *
   * @return the configured queue capacity (0 = unbounded)
   */
  public static int getMaxQueueCapacity() {
    return maxQueueCapacity;
  }

  /**
   * Enables or disables core thread timeout.
   *
   * <p>
   * When enabled, idle core threads will be terminated after the keep-alive time. This is useful
   * for long-running Python processes or when memory efficiency is important.
   * </p>
   *
   * <p>
   * By default, core threads stay alive forever. When enabled with the default keep-alive time of
   * 60 seconds, idle threads will be freed after 60 seconds of inactivity.
   * </p>
   *
   * @param allow true to allow core threads to timeout, false to keep them alive forever
   */
  public static void setAllowCoreThreadTimeout(boolean allow) {
    synchronized (LOCK) {
      allowCoreThreadTimeout = allow;
      if (pool != null && !pool.isShutdown()) {
        shutdownAndAwait(5, TimeUnit.SECONDS);
        pool = createPool();
        logger.info("Core thread timeout {}", allow ? "enabled" : "disabled");
      }
    }
  }

  /**
   * Checks if core thread timeout is enabled.
   *
   * @return true if core threads are allowed to timeout when idle
   */
  public static boolean isAllowCoreThreadTimeout() {
    return allowCoreThreadTimeout;
  }

  /**
   * Sets the keep-alive time for idle threads.
   *
   * <p>
   * This only takes effect when core thread timeout is enabled via
   * {@link #setAllowCoreThreadTimeout(boolean)}.
   * </p>
   *
   * @param seconds the time in seconds that idle threads should wait before terminating
   * @throws IllegalArgumentException if seconds is less than 1
   */
  public static void setKeepAliveTimeSeconds(long seconds) {
    if (seconds < 1) {
      throw new IllegalArgumentException(
          "Keep-alive time must be at least 1 second, was: " + seconds);
    }
    synchronized (LOCK) {
      keepAliveTimeSeconds = seconds;
      if (pool != null && !pool.isShutdown() && allowCoreThreadTimeout) {
        shutdownAndAwait(5, TimeUnit.SECONDS);
        pool = createPool();
        logger.info("Keep-alive time set to {} seconds", seconds);
      }
    }
  }

  /**
   * Gets the current keep-alive time for idle threads.
   *
   * @return the keep-alive time in seconds
   */
  public static long getKeepAliveTimeSeconds() {
    return keepAliveTimeSeconds;
  }

  /**
   * Gets the default pool size (number of available processors).
   *
   * @return the default pool size
   */
  public static int getDefaultPoolSize() {
    return DEFAULT_POOL_SIZE;
  }

  /**
   * Resets the pool size to the default (number of available processors).
   */
  public static void resetPoolSize() {
    setPoolSize(DEFAULT_POOL_SIZE);
  }

  /**
   * Initiates an orderly shutdown of the thread pool. Previously submitted tasks are executed, but
   * no new tasks will be accepted.
   */
  public static void shutdown() {
    synchronized (LOCK) {
      if (pool != null) {
        pool.shutdown();
        logger.debug("NeqSim thread pool shutdown initiated");
      }
    }
  }

  /**
   * Attempts to stop all actively executing tasks and halts the processing of waiting tasks.
   */
  public static void shutdownNow() {
    synchronized (LOCK) {
      if (pool != null) {
        pool.shutdownNow();
        logger.debug("NeqSim thread pool immediate shutdown initiated");
      }
    }
  }

  /**
   * Shuts down the pool and waits for termination.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return {@code true} if the pool terminated, {@code false} if timeout elapsed
   */
  public static boolean shutdownAndAwait(long timeout, TimeUnit unit) {
    synchronized (LOCK) {
      if (pool != null) {
        pool.shutdown();
        try {
          boolean terminated = pool.awaitTermination(timeout, unit);
          if (!terminated) {
            pool.shutdownNow();
            logger.warn("NeqSim thread pool did not terminate gracefully, forcing shutdown");
          }
          return terminated;
        } catch (InterruptedException e) {
          pool.shutdownNow();
          Thread.currentThread().interrupt();
          logger.warn("Thread interrupted while waiting for pool shutdown", e);
          return false;
        }
      }
      return true;
    }
  }

  /**
   * Checks if the pool has been shutdown.
   *
   * @return {@code true} if the pool has been shutdown
   */
  public static boolean isShutdown() {
    synchronized (LOCK) {
      return pool == null || pool.isShutdown();
    }
  }

  /**
   * Checks if all tasks have completed following shutdown.
   *
   * @return {@code true} if all tasks have completed
   */
  public static boolean isTerminated() {
    synchronized (LOCK) {
      return pool == null || pool.isTerminated();
    }
  }
}
