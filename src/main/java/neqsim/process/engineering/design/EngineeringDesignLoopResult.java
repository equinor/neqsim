package neqsim.process.engineering.design;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/** Final process copy, design state, and convergence evidence from the engineering design loop. */
public final class EngineeringDesignLoopResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final boolean converged;
  private final String terminationReason;
  private final EngineeringDesignState state;
  private final List<EngineeringDesignIteration> iterations;
  private final ProcessSystem designedProcess;

  EngineeringDesignLoopResult(boolean converged, String terminationReason, EngineeringDesignState state,
      List<EngineeringDesignIteration> iterations, ProcessSystem designedProcess) {
    this.converged = converged;
    this.terminationReason = terminationReason;
    this.state = state;
    this.iterations = Collections.unmodifiableList(new ArrayList<EngineeringDesignIteration>(iterations));
    this.designedProcess = designedProcess;
  }

  public boolean isConverged() {
    return converged;
  }

  public String getTerminationReason() {
    return terminationReason;
  }

  public EngineeringDesignState getState() {
    return state;
  }

  public List<EngineeringDesignIteration> getIterations() {
    return iterations;
  }

  public ProcessSystem getDesignedProcess() {
    return designedProcess;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "engineering_design_loop.v1");
    result.put("converged", Boolean.valueOf(converged));
    result.put("terminationReason", terminationReason);
    result.put("state", state.toMap());
    List<Map<String, Object>> iterationMaps = new ArrayList<Map<String, Object>>();
    for (EngineeringDesignIteration iteration : iterations) {
      iterationMaps.add(iteration.toMap());
    }
    result.put("iterations", iterationMaps);
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }
}
