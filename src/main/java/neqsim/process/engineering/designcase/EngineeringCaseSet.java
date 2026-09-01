package neqsim.process.engineering.designcase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered, duplicate-safe collection of executable engineering cases and envelope metrics. */
public final class EngineeringCaseSet implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String id;
  private final List<EngineeringDesignCase> cases = new ArrayList<EngineeringDesignCase>();
  private final List<EngineeringMetric> metrics = new ArrayList<EngineeringMetric>();

  public EngineeringCaseSet(String id) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    this.id = id.trim();
  }

  public EngineeringCaseSet addCase(EngineeringDesignCase designCase) {
    if (designCase == null) {
      throw new IllegalArgumentException("designCase must not be null");
    }
    for (EngineeringDesignCase existing : cases) {
      if (existing.getId().equals(designCase.getId())) {
        throw new IllegalArgumentException("Duplicate design case " + designCase.getId());
      }
    }
    cases.add(designCase);
    return this;
  }

  public EngineeringCaseSet addMetric(EngineeringMetric metric) {
    if (metric == null) {
      throw new IllegalArgumentException("metric must not be null");
    }
    for (EngineeringMetric existing : metrics) {
      if (existing.getId().equals(metric.getId())) {
        throw new IllegalArgumentException("Duplicate engineering metric " + metric.getId());
      }
    }
    metrics.add(metric);
    return this;
  }

  public String getId() {
    return id;
  }

  public List<EngineeringDesignCase> getCases() {
    List<EngineeringDesignCase> ordered = new ArrayList<EngineeringDesignCase>(cases);
    Collections.sort(ordered, new Comparator<EngineeringDesignCase>() {
      @Override
      public int compare(EngineeringDesignCase first, EngineeringDesignCase second) {
        int priorityComparison = Integer.compare(first.getPriority(), second.getPriority());
        return priorityComparison != 0 ? priorityComparison : first.getId().compareTo(second.getId());
      }
    });
    return Collections.unmodifiableList(ordered);
  }

  public List<EngineeringMetric> getMetrics() {
    return Collections.unmodifiableList(metrics);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    List<Map<String, Object>> caseMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringDesignCase designCase : getCases()) {
      caseMaps.add(designCase.toMap());
    }
    result.put("cases", caseMaps);
    List<Map<String, Object>> metricMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringMetric metric : metrics) {
      metricMaps.add(metric.toMap());
    }
    result.put("metrics", metricMaps);
    return result;
  }
}
