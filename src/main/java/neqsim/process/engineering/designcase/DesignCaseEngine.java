package neqsim.process.engineering.designcase;

import java.util.ArrayList;
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
    Map<String, EngineeringDesignEnvelope.GoverningValue> governing =
        new LinkedHashMap<String, EngineeringDesignEnvelope.GoverningValue>();
    for (EngineeringDesignCase designCase : designCases) {
      DesignCaseResult result = new DesignCaseResult(designCase);
      try {
        ProcessSystem working = baseProcess.copy();
        designCase.configure(working);
        working.run();
        Map<EngineeringMetric, Double> evaluated = new LinkedHashMap<EngineeringMetric, Double>();
        for (EngineeringMetric metric : metrics) {
          double value = metric.extract(working);
          evaluated.put(metric, Double.valueOf(value));
        }
        for (Map.Entry<EngineeringMetric, Double> entry : evaluated.entrySet()) {
          EngineeringMetric metric = entry.getKey();
          double value = entry.getValue().doubleValue();
          result.addValue(metric.getId(), value);
          EngineeringDesignEnvelope.GoverningValue current = governing.get(metric.getId());
          if (current == null || governs(metric.getGoverningDirection(), value, current.getValue())) {
            governing.put(metric.getId(),
                new EngineeringDesignEnvelope.GoverningValue(metric, designCase.getId(), designCase.getName(), value));
          }
        }
      } catch (Exception ex) {
        result.fail(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      }
      results.add(result);
    }
    return new EngineeringDesignEnvelope(results, governing);
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
