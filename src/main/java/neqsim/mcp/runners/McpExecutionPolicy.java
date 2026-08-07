package neqsim.mcp.runners;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.JsonObject;

/**
 * Shared execution policy for asynchronous MCP work: bounded workers, per-operation timeouts, and per-principal
 * concurrency limits.
 *
 * <p>
 * Background simulation previously ran on a fixed four-thread pool with no timeout, no per-caller limit, and no way to
 * size the pool for the host. A single caller could therefore occupy every worker, and an operation that never
 * converged occupied one forever. This class centralises those controls so a hosted deployment can be sized and cannot
 * be starved by one tenant.
 * </p>
 *
 * <p>
 * Configuration is read once at class initialisation from system properties, falling back to environment variables:
 * </p>
 *
 * <table>
 * <caption>Execution policy configuration</caption>
 * <tr>
 * <th>Property</th>
 * <th>Environment variable</th>
 * <th>Default</th>
 * </tr>
 * <tr>
 * <td>{@code neqsim.mcp.workers}</td>
 * <td>{@code NEQSIM_MCP_WORKERS}</td>
 * <td>available processors, clamped to 2..16</td>
 * </tr>
 * <tr>
 * <td>{@code neqsim.mcp.operationTimeoutSeconds}</td>
 * <td>{@code NEQSIM_MCP_OPERATION_TIMEOUT_SECONDS}</td>
 * <td>900</td>
 * </tr>
 * <tr>
 * <td>{@code neqsim.mcp.maxOperationsPerPrincipal}</td>
 * <td>{@code NEQSIM_MCP_MAX_OPERATIONS_PER_PRINCIPAL}</td>
 * <td>5</td>
 * </tr>
 * </table>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class McpExecutionPolicy {

  /** Worker pool size. */
  private static final int WORKERS = readInt("neqsim.mcp.workers", "NEQSIM_MCP_WORKERS", defaultWorkers(), 1, 64);

  /** Wall-clock timeout applied to each asynchronous operation, in seconds. */
  private static final int OPERATION_TIMEOUT_SECONDS = readInt("neqsim.mcp.operationTimeoutSeconds",
      "NEQSIM_MCP_OPERATION_TIMEOUT_SECONDS", 900, 5, 86400);

  /** Maximum concurrent operations one principal may hold. */
  private static final int MAX_OPERATIONS_PER_PRINCIPAL = readInt("neqsim.mcp.maxOperationsPerPrincipal",
      "NEQSIM_MCP_MAX_OPERATIONS_PER_PRINCIPAL", 5, 1, 100);

  /** Worker pool for asynchronous simulation work. */
  private static final ExecutorService WORKER_POOL = Executors.newFixedThreadPool(WORKERS,
      namedDaemonFactory("neqsim-mcp-worker"));

  /** Single-threaded scheduler used to enforce operation timeouts. */
  private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors
      .newSingleThreadScheduledExecutor(namedDaemonFactory("neqsim-mcp-timeout"));

  /** Live operation counts keyed by principal subject. */
  private static final ConcurrentHashMap<String, AtomicInteger> ACTIVE_PER_PRINCIPAL = new ConcurrentHashMap<String, AtomicInteger>();

  /**
   * Private constructor — utility class.
   */
  private McpExecutionPolicy() {
  }

  /**
   * Returns the shared worker pool for asynchronous MCP operations.
   *
   * @return the bounded worker pool
   */
  public static ExecutorService workerPool() {
    return WORKER_POOL;
  }

  /**
   * Returns the configured operation timeout.
   *
   * @return timeout in seconds
   */
  public static int getOperationTimeoutSeconds() {
    return OPERATION_TIMEOUT_SECONDS;
  }

  /**
   * Returns the configured worker count.
   *
   * @return number of worker threads
   */
  public static int getWorkerCount() {
    return WORKERS;
  }

  /**
   * Returns the per-principal concurrent operation limit.
   *
   * @return maximum concurrent operations per principal
   */
  public static int getMaxOperationsPerPrincipal() {
    return MAX_OPERATIONS_PER_PRINCIPAL;
  }

  /**
   * Reserves a concurrency slot for the current principal.
   *
   * @return true when the operation may start, false when the principal is at its limit
   */
  public static boolean tryAcquireSlot() {
    AtomicInteger counter = counterFor(McpRequestContext.currentSubject());
    if (counter.incrementAndGet() > MAX_OPERATIONS_PER_PRINCIPAL) {
      counter.decrementAndGet();
      return false;
    }
    return true;
  }

  /**
   * Releases a previously reserved concurrency slot.
   *
   * @param subject the principal subject the slot was reserved for
   */
  public static void releaseSlot(String subject) {
    AtomicInteger counter = ACTIVE_PER_PRINCIPAL.get(subject);
    if (counter != null && counter.decrementAndGet() <= 0) {
      ACTIVE_PER_PRINCIPAL.remove(subject, counter);
    }
  }

  /**
   * Returns the number of operations currently running for a principal.
   *
   * @param subject the principal subject
   * @return active operation count
   */
  public static int activeOperations(String subject) {
    AtomicInteger counter = ACTIVE_PER_PRINCIPAL.get(subject);
    return counter != null ? Math.max(0, counter.get()) : 0;
  }

  /**
   * Schedules a timeout action for an operation.
   *
   * @param onTimeout action invoked when the operation exceeds the configured timeout
   * @return a handle that must be cancelled when the operation finishes normally
   */
  public static java.util.concurrent.ScheduledFuture<?> scheduleTimeout(Runnable onTimeout) {
    return TIMEOUT_SCHEDULER.schedule(onTimeout, OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * Builds a JSON description of the active execution policy for capability discovery and status reporting.
   *
   * @return execution policy object
   */
  public static JsonObject describe() {
    JsonObject policy = new JsonObject();
    policy.addProperty("workerThreads", WORKERS);
    policy.addProperty("operationTimeoutSeconds", OPERATION_TIMEOUT_SECONDS);
    policy.addProperty("maxOperationsPerPrincipal", MAX_OPERATIONS_PER_PRINCIPAL);
    policy.addProperty("scope", "in-process; operations do not survive a server restart");
    policy.addProperty("configuration",
        "neqsim.mcp.workers, neqsim.mcp.operationTimeoutSeconds, neqsim.mcp.maxOperationsPerPrincipal "
            + "(or the equivalent NEQSIM_MCP_* environment variables)");
    return policy;
  }

  /**
   * Returns the counter for a principal, creating it when absent.
   *
   * @param subject the principal subject
   * @return the live counter
   */
  private static AtomicInteger counterFor(String subject) {
    AtomicInteger created = new AtomicInteger(0);
    AtomicInteger existing = ACTIVE_PER_PRINCIPAL.putIfAbsent(subject, created);
    return existing != null ? existing : created;
  }

  /**
   * Returns the default worker count derived from the host.
   *
   * @return processor count clamped to a sensible range
   */
  private static int defaultWorkers() {
    int processors = Runtime.getRuntime().availableProcessors();
    return Math.max(2, Math.min(16, processors));
  }

  /**
   * Reads an integer setting from a system property or environment variable.
   *
   * @param property the system property name
   * @param environment the environment variable name
   * @param fallback value used when neither is set or the value is unusable
   * @param min minimum accepted value
   * @param max maximum accepted value
   * @return the resolved, clamped value
   */
  private static int readInt(String property, String environment, int fallback, int min, int max) {
    String raw = System.getProperty(property);
    if (raw == null || raw.trim().isEmpty()) {
      raw = System.getenv(environment);
    }
    if (raw == null || raw.trim().isEmpty()) {
      return fallback;
    }
    try {
      return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /**
   * Creates a thread factory producing named daemon threads.
   *
   * @param namePrefix prefix for thread names
   * @return the thread factory
   */
  private static ThreadFactory namedDaemonFactory(final String namePrefix) {
    return new ThreadFactory() {
      private final AtomicInteger counter = new AtomicInteger(1);

      @Override
      public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, namePrefix + "-" + counter.getAndIncrement());
        thread.setDaemon(true);
        return thread;
      }
    };
  }
}
