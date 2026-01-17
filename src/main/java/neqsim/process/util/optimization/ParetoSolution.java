package neqsim.process.util.optimization;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single solution on the Pareto front.
 *
 * <p>
 * A Pareto solution contains the objective values at a specific decision point (e.g., flow rate)
 * and can be compared to other solutions for dominance.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public class ParetoSolution implements Serializable, Comparable<ParetoSolution> {

  private static final long serialVersionUID = 1L;

  /** Objective values (normalized: higher is always better). */
  private final double[] objectiveValues;

  /** Objective names corresponding to values. */
  private final String[] objectiveNames;

  /** Objective units for display. */
  private final String[] objectiveUnits;

  /** Raw (non-normalized) objective values. */
  private final double[] rawObjectiveValues;

  /** Decision variables that produced this solution. */
  private final Map<String, Double> decisionVariables;

  /** Whether this solution is feasible (satisfies all hard constraints). */
  private final boolean feasible;

  /** Additional metadata. */
  private final Map<String, Object> metadata;

  /**
   * Constructor for ParetoSolution.
   *
   * @param objectiveValues normalized objective values (higher is better)
   * @param rawObjectiveValues original objective values before normalization
   * @param objectiveNames names of objectives
   * @param objectiveUnits units for each objective
   * @param decisionVariables decision variable values
   * @param feasible whether solution is feasible
   */
  public ParetoSolution(double[] objectiveValues, double[] rawObjectiveValues,
      String[] objectiveNames, String[] objectiveUnits, Map<String, Double> decisionVariables,
      boolean feasible) {
    this.objectiveValues = Arrays.copyOf(objectiveValues, objectiveValues.length);
    this.rawObjectiveValues = Arrays.copyOf(rawObjectiveValues, rawObjectiveValues.length);
    this.objectiveNames = Arrays.copyOf(objectiveNames, objectiveNames.length);
    this.objectiveUnits = Arrays.copyOf(objectiveUnits, objectiveUnits.length);
    this.decisionVariables = new HashMap<>(decisionVariables);
    this.feasible = feasible;
    this.metadata = new HashMap<>();
  }

  /**
   * Get the number of objectives.
   *
   * @return number of objectives
   */
  public int getNumObjectives() {
    return objectiveValues.length;
  }

  /**
   * Get normalized objective value at index (higher is better).
   *
   * @param index objective index
   * @return normalized value
   */
  public double getValue(int index) {
    return objectiveValues[index];
  }

  /**
   * Get raw (original) objective value at index.
   *
   * @param index objective index
   * @return raw value
   */
  public double getRawValue(int index) {
    return rawObjectiveValues[index];
  }

  /**
   * Get objective name at index.
   *
   * @param index objective index
   * @return objective name
   */
  public String getObjectiveName(int index) {
    return objectiveNames[index];
  }

  /**
   * Get objective unit at index.
   *
   * @param index objective index
   * @return unit string
   */
  public String getObjectiveUnit(int index) {
    return objectiveUnits[index];
  }

  /**
   * Get all normalized objective values.
   *
   * @return copy of objective values array
   */
  public double[] getObjectiveValues() {
    return Arrays.copyOf(objectiveValues, objectiveValues.length);
  }

  /**
   * Get all raw objective values.
   *
   * @return copy of raw values array
   */
  public double[] getRawObjectiveValues() {
    return Arrays.copyOf(rawObjectiveValues, rawObjectiveValues.length);
  }

  /**
   * Get decision variables.
   *
   * @return unmodifiable map of decision variables
   */
  public Map<String, Double> getDecisionVariables() {
    return Collections.unmodifiableMap(decisionVariables);
  }

  /**
   * Check if this solution is feasible.
   *
   * @return true if feasible
   */
  public boolean isFeasible() {
    return feasible;
  }

  /**
   * Add metadata to this solution.
   *
   * @param key metadata key
   * @param value metadata value
   */
  public void addMetadata(String key, Object value) {
    metadata.put(key, value);
  }

  /**
   * Get metadata value.
   *
   * @param key metadata key
   * @return metadata value or null
   */
  public Object getMetadata(String key) {
    return metadata.get(key);
  }

  /**
   * Check if this solution dominates another solution.
   *
   * <p>
   * Solution A dominates B if A is at least as good as B on all objectives and strictly better on
   * at least one.
   * </p>
   *
   * @param other the other solution to compare
   * @return true if this dominates other
   */
  public boolean dominates(ParetoSolution other) {
    if (other == null || this.objectiveValues.length != other.objectiveValues.length) {
      return false;
    }

    boolean dominated = false;
    for (int i = 0; i < objectiveValues.length; i++) {
      if (this.objectiveValues[i] < other.objectiveValues[i]) {
        return false; // Worse on at least one objective
      }
      if (this.objectiveValues[i] > other.objectiveValues[i]) {
        dominated = true; // Better on at least one objective
      }
    }
    return dominated;
  }

  /**
   * Calculate Euclidean distance to another solution in objective space.
   *
   * @param other the other solution
   * @return Euclidean distance
   */
  public double distanceTo(ParetoSolution other) {
    if (other == null || this.objectiveValues.length != other.objectiveValues.length) {
      return Double.MAX_VALUE;
    }

    double sumSq = 0.0;
    for (int i = 0; i < objectiveValues.length; i++) {
      double diff = this.objectiveValues[i] - other.objectiveValues[i];
      sumSq += diff * diff;
    }
    return Math.sqrt(sumSq);
  }

  /**
   * Calculate crowding distance contribution from neighbors.
   *
   * @param leftNeighbor left neighbor in sorted order (can be null)
   * @param rightNeighbor right neighbor in sorted order (can be null)
   * @param objectiveIndex objective to calculate distance for
   * @param objectiveRange total range of objective values
   * @return crowding distance contribution
   */
  public double crowdingDistance(ParetoSolution leftNeighbor, ParetoSolution rightNeighbor,
      int objectiveIndex, double objectiveRange) {
    if (objectiveRange <= 0) {
      return 0.0;
    }
    if (leftNeighbor == null || rightNeighbor == null) {
      return Double.POSITIVE_INFINITY; // Boundary solutions
    }
    double distance = rightNeighbor.objectiveValues[objectiveIndex]
        - leftNeighbor.objectiveValues[objectiveIndex];
    return distance / objectiveRange;
  }

  @Override
  public int compareTo(ParetoSolution other) {
    // Default comparison: lexicographic on objective values
    for (int i = 0; i < objectiveValues.length; i++) {
      int cmp = Double.compare(this.objectiveValues[i], other.objectiveValues[i]);
      if (cmp != 0) {
        return -cmp; // Higher is better, so reverse
      }
    }
    return 0;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ParetoSolution)) {
      return false;
    }
    ParetoSolution other = (ParetoSolution) obj;
    return Arrays.equals(objectiveValues, other.objectiveValues)
        && decisionVariables.equals(other.decisionVariables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(objectiveValues), decisionVariables);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("ParetoSolution{");
    for (int i = 0; i < objectiveNames.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(objectiveNames[i]).append("=").append(String.format("%.2f", rawObjectiveValues[i]))
          .append(" ").append(objectiveUnits[i]);
    }
    sb.append(", feasible=").append(feasible);
    if (!decisionVariables.isEmpty()) {
      sb.append(", vars=").append(decisionVariables);
    }
    sb.append("}");
    return sb.toString();
  }

  /**
   * Builder for creating ParetoSolution instances.
   */
  public static class Builder {
    private List<ObjectiveFunction> objectives;
    private double[] rawValues;
    private Map<String, Double> decisionVariables = new HashMap<>();
    private boolean feasible = true;

    /**
     * Set objectives and their evaluated values.
     *
     * @param objectives list of objective functions
     * @param rawValues evaluated raw values
     * @return this builder
     */
    public Builder objectives(List<ObjectiveFunction> objectives, double[] rawValues) {
      this.objectives = objectives;
      this.rawValues = rawValues;
      return this;
    }

    /**
     * Set decision variables.
     *
     * @param variables map of variable names to values
     * @return this builder
     */
    public Builder decisionVariables(Map<String, Double> variables) {
      this.decisionVariables = new HashMap<>(variables);
      return this;
    }

    /**
     * Add a decision variable.
     *
     * @param name variable name
     * @param value variable value
     * @return this builder
     */
    public Builder decisionVariable(String name, double value) {
      this.decisionVariables.put(name, value);
      return this;
    }

    /**
     * Set feasibility status.
     *
     * @param feasible whether solution is feasible
     * @return this builder
     */
    public Builder feasible(boolean feasible) {
      this.feasible = feasible;
      return this;
    }

    /**
     * Build the ParetoSolution.
     *
     * @return new ParetoSolution instance
     */
    public ParetoSolution build() {
      int n = objectives.size();
      double[] normalizedValues = new double[n];
      String[] names = new String[n];
      String[] units = new String[n];

      for (int i = 0; i < n; i++) {
        ObjectiveFunction obj = objectives.get(i);
        names[i] = obj.getName();
        units[i] = obj.getUnit();
        // Normalize: higher is always better
        normalizedValues[i] =
            obj.getDirection() == ObjectiveFunction.Direction.MAXIMIZE ? rawValues[i]
                : -rawValues[i];
      }

      return new ParetoSolution(normalizedValues, rawValues, names, units, decisionVariables,
          feasible);
    }
  }
}
