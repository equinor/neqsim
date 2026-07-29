package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Stable candidate-evaluation contract for network optimization.
 */
public class NetworkCandidateEvaluation implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<String, Double> decisions = new LinkedHashMap<String, Double>();
  private final Map<String, String> decisionUnits = new LinkedHashMap<String, String>();
  private final Map<String, String> rateBases = new LinkedHashMap<String, String>();
  private final Map<String, Double> objectiveTerms = new LinkedHashMap<String, Double>();
  private final List<NetworkConstraintResult> constraints = new ArrayList<NetworkConstraintResult>();
  private final List<String> activeConstraints = new ArrayList<String>();
  private double objectiveValue;
  private double penalty;
  private boolean feasible;
  private boolean solverConverged;
  private String message;

  void addDecision(NetworkDecisionVariable variable, double value) {
    decisions.put(variable.getName(), value);
    decisionUnits.put(variable.getName(), variable.getUnit());
    rateBases.put(variable.getName(), variable.getRateBasis().name());
  }

  void addObjectiveTerm(String name, double value) {
    objectiveTerms.put(name, value);
  }

  void addConstraint(NetworkConstraintResult result) {
    constraints.add(result);
    if (result.isActive()) {
      activeConstraints.add(result.getName());
    }
  }

  void setObjectiveValue(double value) {
    objectiveValue = value;
  }

  void setPenalty(double value) {
    penalty = value;
  }

  void setFeasible(boolean value) {
    feasible = value;
  }

  void setSolverConverged(boolean value) {
    solverConverged = value;
  }

  void setMessage(String value) {
    message = value;
  }

  /** @return immutable decisions */
  public Map<String, Double> getDecisions() {
    return Collections.unmodifiableMap(decisions);
  }

  /** @return units by decision */
  public Map<String, String> getDecisionUnits() {
    return Collections.unmodifiableMap(decisionUnits);
  }

  /** @return explicit rate bases by decision */
  public Map<String, String> getRateBases() {
    return Collections.unmodifiableMap(rateBases);
  }

  /** @return unweighted objective terms */
  public Map<String, Double> getObjectiveTerms() {
    return Collections.unmodifiableMap(objectiveTerms);
  }

  /** @return scalar objective before penalty */
  public double getObjectiveValue() {
    return objectiveValue;
  }

  /** @return scaled soft/hard penalty */
  public double getPenalty() {
    return penalty;
  }

  /** @return true when all hard constraints and solver state are feasible */
  public boolean isFeasible() {
    return feasible;
  }

  /** @return solver convergence state */
  public boolean isSolverConverged() {
    return solverConverged;
  }

  /** @return all constraint results */
  public List<NetworkConstraintResult> getConstraints() {
    return Collections.unmodifiableList(constraints);
  }

  /** @return binding/nearly binding constraint names */
  public List<String> getActiveConstraints() {
    return Collections.unmodifiableList(activeConstraints);
  }

  /** @return diagnostic */
  public String getMessage() {
    return message;
  }

  /** @return stable JSON representation */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
