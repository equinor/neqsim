package neqsim.process.optimization.valuechain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Evaluates a batch of decision vectors in parallel, preserving input order.
 *
 * <p>
 * The agentic optimizer evaluates candidates one at a time; for parameter sweeps, Latin-hypercube
 * sampling or population-based search the trials are independent and can run concurrently. This
 * utility maps a list of decision vectors over a thread pool and returns the results in the same
 * order as the inputs, turning an expensive sequential sweep into a parallel one.
 * </p>
 *
 * <p>
 * Each trial is scored by a caller-supplied {@link SweepEvaluator}. Because NeqSim flowsheet objects
 * are not thread-safe, the evaluator must give every trial its own state — the canonical pattern is
 * to call {@code ProcessSystem.copy()} (or rebuild the flowsheet) inside the evaluator so each
 * worker thread owns an independent model. Evaluators that only read immutable inputs and return a
 * fresh result object are safe as written.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ParallelSweep implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Number of worker threads to use. */
  private int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());

  /**
   * Functional evaluator that scores one decision vector and returns a result object.
   *
   * @param <R> the result type produced for each decision vector
   */
  public interface SweepEvaluator<R> {
    /**
     * Scores one decision vector.
     *
     * <p>
     * Implementations must be safe to call concurrently from multiple threads; give each trial its
     * own flowsheet state (e.g. via {@code ProcessSystem.copy()}).
     * </p>
     *
     * @param input the decision vector to evaluate
     * @return the result of evaluating the decision vector
     */
    R evaluate(double[] input);
  }

  /**
   * Creates a parallel sweep using a default thread count equal to the number of available
   * processors.
   */
  public ParallelSweep() {}

  /**
   * Sets the number of worker threads.
   *
   * @param parallelism the number of worker threads (must be positive)
   * @return this sweep for method chaining
   */
  public ParallelSweep setParallelism(int parallelism) {
    if (parallelism <= 0) {
      throw new IllegalArgumentException("parallelism must be positive");
    }
    this.parallelism = parallelism;
    return this;
  }

  /**
   * Gets the configured number of worker threads.
   *
   * @return the number of worker threads
   */
  public int getParallelism() {
    return parallelism;
  }

  /**
   * Evaluates all decision vectors in parallel and returns the results in input order.
   *
   * @param <R> the result type produced for each decision vector
   * @param inputs the decision vectors to evaluate (must not be null)
   * @param evaluator the per-trial evaluator (must not be null and must be thread-safe)
   * @return a list of results in the same order as {@code inputs}
   */
  public <R> List<R> run(List<double[]> inputs, final SweepEvaluator<R> evaluator) {
    if (inputs == null) {
      throw new IllegalArgumentException("inputs must not be null");
    }
    if (evaluator == null) {
      throw new IllegalArgumentException("evaluator must not be null");
    }
    List<R> results = new ArrayList<R>(inputs.size());
    if (inputs.isEmpty()) {
      return results;
    }
    int threads = Math.min(parallelism, inputs.size());
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<R>> futures = new ArrayList<Future<R>>(inputs.size());
      for (final double[] input : inputs) {
        futures.add(pool.submit(new Callable<R>() {
          @Override
          public R call() {
            return evaluator.evaluate(input);
          }
        }));
      }
      for (Future<R> future : futures) {
        try {
          results.add(future.get());
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Parallel sweep interrupted", ex);
        } catch (ExecutionException ex) {
          throw new RuntimeException("Parallel sweep evaluation failed", ex.getCause());
        }
      }
    } finally {
      pool.shutdownNow();
    }
    return results;
  }
}
