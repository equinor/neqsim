package neqsim.process.safety.scenario;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.processmodel.ProcessSystem;

/** Isolated transient initiating event, protection logic, and measurable safe-state criteria. */
public final class DynamicSafetyScenario implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Applies controlled case configuration or an initiating event to an isolated process. */
  public interface ProcessManipulator extends Serializable {
    void apply(ProcessSystem process);
  }

  /** Builds logic bound to equipment in the isolated process copy. */
  public interface LogicFactory extends Serializable {
    ProcessLogic create(ProcessSystem process);
  }

  private final String id;
  private final String name;
  private final double durationSeconds;
  private final double timeStepSeconds;
  private final double triggerTimeSeconds;
  private final ProcessManipulator caseConfiguration;
  private final ProcessManipulator initiatingEvent;
  private final List<LogicFactory> logicFactories;
  private final List<DynamicScenarioCriterion> criteria;
  private final List<String> evidenceReferences;

  private DynamicSafetyScenario(Builder builder) {
    id = requireText(builder.id, "id");
    name = requireText(builder.name, "name");
    durationSeconds = builder.durationSeconds;
    timeStepSeconds = builder.timeStepSeconds;
    triggerTimeSeconds = builder.triggerTimeSeconds;
    caseConfiguration = builder.caseConfiguration;
    initiatingEvent = builder.initiatingEvent;
    logicFactories = Collections.unmodifiableList(new ArrayList<LogicFactory>(builder.logicFactories));
    criteria = Collections.unmodifiableList(new ArrayList<DynamicScenarioCriterion>(builder.criteria));
    evidenceReferences = Collections.unmodifiableList(new ArrayList<String>(builder.evidenceReferences));
  }

  public static Builder builder(String id, String name) {
    return new Builder(id, name);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public double getDurationSeconds() {
    return durationSeconds;
  }

  public double getTimeStepSeconds() {
    return timeStepSeconds;
  }

  public double getTriggerTimeSeconds() {
    return triggerTimeSeconds;
  }

  ProcessManipulator getCaseConfiguration() {
    return caseConfiguration;
  }

  ProcessManipulator getInitiatingEvent() {
    return initiatingEvent;
  }

  public List<LogicFactory> getLogicFactories() {
    return logicFactories;
  }

  public List<DynamicScenarioCriterion> getCriteria() {
    return criteria;
  }

  public List<String> getEvidenceReferences() {
    return evidenceReferences;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", id);
    result.put("name", name);
    result.put("durationSeconds", Double.valueOf(durationSeconds));
    result.put("timeStepSeconds", Double.valueOf(timeStepSeconds));
    result.put("triggerTimeSeconds", Double.valueOf(triggerTimeSeconds));
    result.put("logicCount", Integer.valueOf(logicFactories.size()));
    List<Map<String, Object>> criterionMaps = new ArrayList<Map<String, Object>>();
    for (DynamicScenarioCriterion criterion : criteria) {
      criterionMaps.add(criterion.toMap());
    }
    result.put("criteria", criterionMaps);
    result.put("evidenceReferences", new ArrayList<String>(evidenceReferences));
    result.put("silTargetInferred", Boolean.FALSE);
    return result;
  }

  /** Builder for a dynamic safety scenario. */
  public static final class Builder {
    private final String id;
    private final String name;
    private double durationSeconds = 60.0;
    private double timeStepSeconds = 1.0;
    private double triggerTimeSeconds;
    private ProcessManipulator caseConfiguration;
    private ProcessManipulator initiatingEvent;
    private final List<LogicFactory> logicFactories = new ArrayList<LogicFactory>();
    private final List<DynamicScenarioCriterion> criteria = new ArrayList<DynamicScenarioCriterion>();
    private final List<String> evidenceReferences = new ArrayList<String>();

    private Builder(String id, String name) {
      this.id = id;
      this.name = name;
    }

    public Builder durationSeconds(double value) {
      durationSeconds = positive(value, "durationSeconds");
      return this;
    }

    public Builder timeStepSeconds(double value) {
      timeStepSeconds = positive(value, "timeStepSeconds");
      return this;
    }

    public Builder triggerTimeSeconds(double value) {
      if (!Double.isFinite(value) || value < 0.0) {
        throw new IllegalArgumentException("triggerTimeSeconds must be finite and non-negative");
      }
      triggerTimeSeconds = value;
      return this;
    }

    public Builder configureCase(ProcessManipulator value) {
      caseConfiguration = value;
      return this;
    }

    public Builder initiatingEvent(ProcessManipulator value) {
      initiatingEvent = value;
      return this;
    }

    public Builder addLogic(LogicFactory value) {
      if (value == null) {
        throw new IllegalArgumentException("logic factory must not be null");
      }
      logicFactories.add(value);
      return this;
    }

    public Builder addCriterion(DynamicScenarioCriterion value) {
      if (value == null) {
        throw new IllegalArgumentException("criterion must not be null");
      }
      for (DynamicScenarioCriterion existing : criteria) {
        if (existing.getId().equals(value.getId())) {
          throw new IllegalArgumentException("Duplicate scenario criterion " + value.getId());
        }
      }
      criteria.add(value);
      return this;
    }

    public Builder addEvidenceReference(String value) {
      String normalized = value == null ? "" : value.trim();
      if (!normalized.isEmpty() && !evidenceReferences.contains(normalized)) {
        evidenceReferences.add(normalized);
      }
      return this;
    }

    public DynamicSafetyScenario build() {
      if (triggerTimeSeconds > durationSeconds) {
        throw new IllegalStateException("trigger time must not exceed scenario duration");
      }
      if (initiatingEvent == null) {
        throw new IllegalStateException("initiating event is required");
      }
      if (logicFactories.isEmpty()) {
        throw new IllegalStateException("at least one protection logic factory is required");
      }
      if (criteria.isEmpty()) {
        throw new IllegalStateException("at least one response criterion is required");
      }
      return new DynamicSafetyScenario(this);
    }
  }

  private static double positive(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
