package neqsim.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
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
    return Executors.newFixedThreadPool(poolSize, r -> {
      Thread t = DEFAULT_FACTORY.newThread(r);
      t.setDaemon(true);
      t.setName("NeqSim-Worker-" + THREAD_COUNTER.getAndIncrement());
      return t;
    });
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
