package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Proposed values, checks, and evidence produced by one engineering design module. */
public final class EngineeringDesignModuleResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String moduleId;
  private final String method;
  private final String methodVersion;
  private final List<EngineeringDesignUpdate> updates;
  private final List<EngineeringDesignConstraint> constraints;
  private final Map<String, Object> evidence;
  private final List<String> warnings;

  private EngineeringDesignModuleResult(Builder builder) {
    moduleId = builder.moduleId;
    method = builder.method;
    methodVersion = builder.methodVersion;
    updates = Collections.unmodifiableList(new ArrayList<EngineeringDesignUpdate>(builder.updates));
    constraints = Collections.unmodifiableList(new ArrayList<EngineeringDesignConstraint>(builder.constraints));
    evidence = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(builder.evidence));
    warnings = Collections.unmodifiableList(new ArrayList<String>(builder.warnings));
  }

  public static Builder builder(String moduleId, String method, String methodVersion) {
    return new Builder(moduleId, method, methodVersion);
  }

  public String getModuleId() {
    return moduleId;
  }

  public String getMethod() {
    return method;
  }

  public String getMethodVersion() {
    return methodVersion;
  }

  public List<EngineeringDesignUpdate> getUpdates() {
    return updates;
  }

  public List<EngineeringDesignConstraint> getConstraints() {
    return constraints;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("moduleId", moduleId);
    result.put("method", method);
    result.put("methodVersion", methodVersion);
    result.put("evidence", new LinkedHashMap<String, Object>(evidence));
    result.put("warnings", new ArrayList<String>(warnings));
    result.put("proposedUpdateCount", Integer.valueOf(updates.size()));
    result.put("constraintCount", Integer.valueOf(constraints.size()));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public static final class Builder {
    private final String moduleId;
    private final String method;
    private final String methodVersion;
    private final List<EngineeringDesignUpdate> updates = new ArrayList<EngineeringDesignUpdate>();
    private final List<EngineeringDesignConstraint> constraints = new ArrayList<EngineeringDesignConstraint>();
    private final Map<String, Object> evidence = new LinkedHashMap<String, Object>();
    private final List<String> warnings = new ArrayList<String>();

    private Builder(String moduleId, String method, String methodVersion) {
      this.moduleId = moduleId;
      this.method = method;
      this.methodVersion = methodVersion;
    }

    public Builder addUpdate(EngineeringDesignUpdate value) {
      updates.add(value);
      return this;
    }

    public Builder addConstraint(EngineeringDesignConstraint value) {
      constraints.add(value);
      return this;
    }

    public Builder evidence(String name, Object value) {
      evidence.put(name, value);
      return this;
    }

    public Builder warning(String value) {
      if (value != null && !value.trim().isEmpty()) {
        warnings.add(value.trim());
      }
      return this;
    }

    public EngineeringDesignModuleResult build() {
      return new EngineeringDesignModuleResult(this);
    }
  }
}
