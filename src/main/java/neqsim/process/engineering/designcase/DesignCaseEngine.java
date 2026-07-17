package neqsim.process.engineering.designcase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/** Executes design cases on isolated process copies and selects governing metric values. */
public final class DesignCaseEngine {
  private DesignCaseEngine() {
  }

  public static EngineeringDesignEnvelope run(ProcessSystem baseProcess, List<EngineeringDesignCase> designCases,
      List<EngineeringMetric> metrics) {
    if (baseProcess == null) {
      throw new IllegalArgumentException("baseProcess must not be null");
    }
    if (designCases == null || designCases.isEmpty()) {
      throw new IllegalArgumentException("At least one executable design case is required");
    }
    if (metrics == null || metrics.isEmpty()) {
      throw new IllegalArgumentException("At least one engineering metric is required");
    }
    List<DesignCaseResult> results = new ArrayList<DesignCaseResult>();
    Map<String, EngineeringDesignEnvelope.GoverningValue> governing = new LinkedHashMap<String, EngineeringDesignEnvelope.GoverningValue>();
    List<EngineeringDesignCase> orderedCases = new ArrayList<EngineeringDesignCase>(designCases);
    Collections.sort(orderedCases, new Comparator<EngineeringDesignCase>() {
      @Override
      public int compare(EngineeringDesignCase first, EngineeringDesignCase second) {
        return Integer.compare(first.getPriority(), second.getPriority());
      }
    });
    for (EngineeringDesignCase designCase : orderedCases) {
      DesignCaseResult result = new DesignCaseResult(designCase);
      if (!designCase.isEnabled()) {
        result.skip("Design case is disabled");
        results.add(result);
        continue;
      }
      try {
        ProcessSystem working = baseProcess.copy();
        designCase.configure(working);
        working.run();
        for (EngineeringMetric metric : metrics) {
          try {
            double value = metric.extract(working);
            result.addValue(metric, value);
            EngineeringDesignEnvelope.GoverningValue current = governing.get(metric.getId());
            if (current == null || governs(metric.getGoverningDirection(), value, current.getValue())) {
              governing.put(metric.getId(), new EngineeringDesignEnvelope.GoverningValue(metric, designCase.getId(),
                  designCase.getName(), value));
            }
          } catch (Exception ex) {
            result.failMetric(metric, failureMessage(ex));
          }
        }
        result.finish();
      } catch (Exception ex) {
        result.fail(failureMessage(ex));
      }
      results.add(result);
    }
    return new EngineeringDesignEnvelope(results, governing);
  }

  private static String failureMessage(Exception ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
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
}
