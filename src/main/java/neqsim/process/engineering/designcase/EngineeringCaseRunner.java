package neqsim.process.engineering.designcase;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import neqsim.process.engineering.numerics.EngineeringNumericalHealthAnalyzer;
import neqsim.process.processmodel.ProcessSystem;

/** Executes engineering cases on independent process copies with deterministic ordering and fingerprints. */
public final class EngineeringCaseRunner {
  private EngineeringCaseRunner() {
  }

  public static EngineeringCaseRunReport run(ProcessSystem baseProcess, EngineeringCaseSet caseSet,
      EngineeringCaseRunOptions options) {
    if (baseProcess == null || caseSet == null) {
      throw new IllegalArgumentException("baseProcess and caseSet are required");
    }
    if (caseSet.getCases().isEmpty() || caseSet.getMetrics().isEmpty()) {
      throw new IllegalArgumentException("caseSet requires at least one case and one metric");
    }
    EngineeringCaseRunOptions effectiveOptions = options == null ? EngineeringCaseRunOptions.sequential() : options;
    List<DesignCaseResult> results = execute(baseProcess, caseSet, effectiveOptions);
    Map<String, EngineeringDesignEnvelope.GoverningValue> governing = governing(caseSet.getMetrics(), results);
    EngineeringDesignEnvelope envelope = new EngineeringDesignEnvelope(results, governing);
    String definitionFingerprint = fingerprint(caseSet.toMap());
    String resultFingerprint = fingerprint(envelope.toMap());
    return new EngineeringCaseRunReport(caseSet.getId(), definitionFingerprint, resultFingerprint, envelope);
  }

  private static List<DesignCaseResult> execute(ProcessSystem baseProcess, EngineeringCaseSet caseSet,
      EngineeringCaseRunOptions options) {
    List<EngineeringDesignCase> cases = caseSet.getCases();
    if (options.getParallelism() == 1 || cases.size() == 1) {
      List<DesignCaseResult> results = new ArrayList<DesignCaseResult>();
      for (EngineeringDesignCase designCase : cases) {
        results.add(executeOne(baseProcess, designCase, caseSet.getMetrics(), options));
      }
      return results;
    }
    ExecutorService executor = Executors.newFixedThreadPool(Math.min(options.getParallelism(), cases.size()));
    try {
      List<Future<DesignCaseResult>> futures = new ArrayList<Future<DesignCaseResult>>();
      for (final EngineeringDesignCase designCase : cases) {
        futures.add(executor.submit(new Callable<DesignCaseResult>() {
          @Override
          public DesignCaseResult call() {
            return executeOne(baseProcess, designCase, caseSet.getMetrics(), options);
          }
        }));
      }
      List<DesignCaseResult> results = new ArrayList<DesignCaseResult>();
      for (Future<DesignCaseResult> future : futures) {
        try {
          results.add(future.get());
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Engineering case execution interrupted", ex);
        } catch (ExecutionException ex) {
          throw new IllegalStateException("Engineering case execution failed", ex.getCause());
        }
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private static DesignCaseResult executeOne(ProcessSystem baseProcess, EngineeringDesignCase designCase,
      List<EngineeringMetric> metrics, EngineeringCaseRunOptions options) {
    DesignCaseResult result = new DesignCaseResult(designCase);
    if (!designCase.isEnabled()) {
      result.skip("Design case is disabled");
      return result;
    }
    try {
      ProcessSystem working = baseProcess.copy();
      designCase.configure(working);
      working.run();
      boolean converged = working.solved();
      if (options.getNumericalHealthCriteria() != null) {
        result.numericalHealthReport(
            new EngineeringNumericalHealthAnalyzer(working, options.getNumericalHealthCriteria()).analyze());
      }
      for (EngineeringMetric metric : metrics) {
        try {
          result.addValue(metric, metric.extract(working));
        } catch (RuntimeException ex) {
          result.failMetric(metric, failureMessage(ex));
        }
      }
      result.finish(!options.isConvergenceRequired() || converged);
    } catch (RuntimeException ex) {
      result.fail(failureMessage(ex));
    }
    return result;
  }

  private static Map<String, EngineeringDesignEnvelope.GoverningValue> governing(List<EngineeringMetric> metrics,
      List<DesignCaseResult> results) {
    Map<String, EngineeringDesignEnvelope.GoverningValue> governing = new LinkedHashMap<String, EngineeringDesignEnvelope.GoverningValue>();
    for (EngineeringMetric metric : metrics) {
      for (DesignCaseResult result : results) {
        Double candidate = result.getValues().get(metric.getId());
        if (candidate == null || !result.isConverged()) {
          continue;
        }
        EngineeringDesignEnvelope.GoverningValue current = governing.get(metric.getId());
        if (current == null || governs(metric.getGoverningDirection(), candidate.doubleValue(), current.getValue())) {
          governing.put(metric.getId(), new EngineeringDesignEnvelope.GoverningValue(metric,
              result.getDesignCase().getId(), result.getDesignCase().getName(), candidate.doubleValue()));
        }
      }
    }
    return governing;
  }

  private static boolean governs(EngineeringMetric.GoverningDirection direction, double candidate, double current) {
    if (direction == EngineeringMetric.GoverningDirection.MINIMUM) {
      return candidate < current;
    }
    if (direction == EngineeringMetric.GoverningDirection.MAXIMUM_ABSOLUTE) {
      return Math.abs(candidate) > Math.abs(current);
    }
    return candidate > current;
  }

  private static String fingerprint(Object value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(new Gson().toJson(value).getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : bytes) {
        result.append(String.format("%02x", Integer.valueOf(item & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private static String failureMessage(RuntimeException ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}
