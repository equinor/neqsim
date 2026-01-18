package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Collection of Pareto-optimal solutions forming the Pareto front.
 *
 * <p>
 * The Pareto front contains all non-dominated solutions found during multi-objective optimization.
 * It automatically maintains the non-dominated property when solutions are added.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public class ParetoFront implements Serializable, Iterable<ParetoSolution> {

  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final org.apache.logging.log4j.Logger logger =
      org.apache.logging.log4j.LogManager.getLogger(ParetoFront.class);

  /** The non-dominated solutions. */
  private final List<ParetoSolution> solutions;

  /** Whether to only keep feasible solutions. */
  private final boolean feasibleOnly;

  /**
   * Create an empty Pareto front.
   */
  public ParetoFront() {
    this(false);
  }

  /**
   * Create an empty Pareto front.
   *
   * @param feasibleOnly if true, only feasible solutions are kept
   */
  public ParetoFront(boolean feasibleOnly) {
    this.solutions = new ArrayList<>();
    this.feasibleOnly = feasibleOnly;
  }

  /**
   * Add a candidate solution to the front.
   *
   * <p>
   * If the candidate dominates any existing solutions, those are removed. If the candidate is
   * dominated by any existing solution, it is not added. This method is thread-safe.
   * </p>
   *
   * @param candidate the solution to add
   * @return true if the candidate was added to the front
   */
  public synchronized boolean add(ParetoSolution candidate) {
    if (candidate == null) {
      logger.debug("Attempted to add null candidate to Pareto front");
      return false;
    }

    if (feasibleOnly && !candidate.isFeasible()) {
      logger.debug("Rejected infeasible candidate: {}", candidate);
      return false;
    }

    // Check if candidate is dominated by any existing solution
    for (ParetoSolution existing : solutions) {
      if (existing.dominates(candidate)) {
        logger.debug("Candidate dominated by existing solution");
        return false; // Candidate is dominated, don't add
      }
    }

    // Remove solutions dominated by candidate
    int removedCount = 0;
    java.util.Iterator<ParetoSolution> it = solutions.iterator();
    while (it.hasNext()) {
      if (candidate.dominates(it.next())) {
        it.remove();
        removedCount++;
      }
    }
    if (removedCount > 0) {
      logger.debug("New candidate dominated {} existing solutions", removedCount);
    }

    // Add the candidate
    solutions.add(candidate);
    logger.debug("Added solution to Pareto front, new size: {}", solutions.size());
    return true;
  }

  /**
   * Get all solutions on the front.
   *
   * @return unmodifiable list of solutions (snapshot at time of call)
   */
  public synchronized List<ParetoSolution> getSolutions() {
    return Collections.unmodifiableList(new ArrayList<>(solutions));
  }

  /**
   * Get solutions sorted by a specific objective.
   *
   * @param objectiveIndex index of objective to sort by
   * @param ascending if true, sort ascending; otherwise descending
   * @return sorted list of solutions
   */
  public List<ParetoSolution> getSolutionsSortedBy(int objectiveIndex, boolean ascending) {
    List<ParetoSolution> sorted = new ArrayList<>(solutions);
    sorted.sort((a, b) -> {
      int cmp = Double.compare(a.getRawValue(objectiveIndex), b.getRawValue(objectiveIndex));
      return ascending ? cmp : -cmp;
    });
    return sorted;
  }

  /**
   * Get the number of solutions on the front.
   *
   * @return number of solutions
   */
  public int size() {
    return solutions.size();
  }

  /**
   * Check if the front is empty.
   *
   * @return true if empty
   */
  public boolean isEmpty() {
    return solutions.isEmpty();
  }

  /**
   * Clear all solutions from the front.
   */
  public void clear() {
    solutions.clear();
  }

  /**
   * Find the "knee" point on the Pareto front.
   *
   * <p>
   * The knee point is the solution with maximum distance from the line connecting the extreme
   * points. This often represents a good trade-off between objectives.
   * </p>
   *
   * @return the knee point solution, or null if front is empty
   */
  public ParetoSolution findKneePoint() {
    if (solutions.isEmpty()) {
      return null;
    }
    if (solutions.size() <= 2) {
      return solutions.get(0);
    }
    if (solutions.get(0).getNumObjectives() != 2) {
      // For more than 2 objectives, use normalized distance method
      return findKneePointHighDimensional();
    }

    // Sort by first objective
    List<ParetoSolution> sorted = getSolutionsSortedBy(0, true);
    ParetoSolution start = sorted.get(0);
    ParetoSolution end = sorted.get(sorted.size() - 1);

    // Find point with maximum perpendicular distance to line
    double maxDistance = 0;
    ParetoSolution knee = sorted.get(0);

    double x1 = start.getRawValue(0);
    double y1 = start.getRawValue(1);
    double x2 = end.getRawValue(0);
    double y2 = end.getRawValue(1);

    for (ParetoSolution sol : sorted) {
      double x0 = sol.getRawValue(0);
      double y0 = sol.getRawValue(1);
      // Distance from point to line
      double distance = Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1)
          / Math.sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1));

      if (distance > maxDistance) {
        maxDistance = distance;
        knee = sol;
      }
    }

    return knee;
  }

  /**
   * Find knee point for high-dimensional objectives using normalized Euclidean distance.
   *
   * @return knee point solution
   */
  private ParetoSolution findKneePointHighDimensional() {
    // Find ideal and nadir points
    int numObj = solutions.get(0).getNumObjectives();
    double[] ideal = new double[numObj];
    double[] nadir = new double[numObj];

    for (int i = 0; i < numObj; i++) {
      final int idx = i;
      ideal[i] = solutions.stream().mapToDouble(s -> s.getValue(idx)).max().orElse(0);
      nadir[i] = solutions.stream().mapToDouble(s -> s.getValue(idx)).min().orElse(0);
    }

    // Find solution with minimum normalized distance to ideal
    ParetoSolution best = null;
    double minDist = Double.MAX_VALUE;

    for (ParetoSolution sol : solutions) {
      double dist = 0;
      for (int i = 0; i < numObj; i++) {
        double range = ideal[i] - nadir[i];
        if (range > 0) {
          double normalized = (ideal[i] - sol.getValue(i)) / range;
          dist += normalized * normalized;
        }
      }
      dist = Math.sqrt(dist);
      if (dist < minDist) {
        minDist = dist;
        best = sol;
      }
    }

    return best;
  }

  /**
   * Find the solution that minimizes a specific objective.
   *
   * @param objectiveIndex index of objective
   * @return solution with minimum value for that objective
   */
  public ParetoSolution findMinimum(int objectiveIndex) {
    return solutions.stream().min(Comparator.comparingDouble(s -> s.getRawValue(objectiveIndex)))
        .orElse(null);
  }

  /**
   * Find the solution that maximizes a specific objective.
   *
   * @param objectiveIndex index of objective
   * @return solution with maximum value for that objective
   */
  public ParetoSolution findMaximum(int objectiveIndex) {
    return solutions.stream().max(Comparator.comparingDouble(s -> s.getRawValue(objectiveIndex)))
        .orElse(null);
  }

  /**
   * Calculate hypervolume indicator (2D case).
   *
   * <p>
   * The hypervolume is the area/volume dominated by the front with respect to a reference point.
   * Higher values indicate better convergence and diversity.
   * </p>
   *
   * @param referencePoint reference point (nadir point)
   * @return hypervolume value
   */
  public double calculateHypervolume2D(double[] referencePoint) {
    if (solutions.isEmpty() || solutions.get(0).getNumObjectives() != 2) {
      return 0.0;
    }

    // Sort by first objective (descending for maximization)
    List<ParetoSolution> sorted = getSolutionsSortedBy(0, false);

    double hypervolume = 0.0;
    double prevY = referencePoint[1];

    for (ParetoSolution sol : sorted) {
      double x = sol.getValue(0);
      double y = sol.getValue(1);
      if (x > referencePoint[0] && y > referencePoint[1]) {
        hypervolume += (x - referencePoint[0]) * (y - prevY);
        prevY = y;
      }
    }

    return hypervolume;
  }

  /**
   * Get spacing metric (uniformity of distribution).
   *
   * @return spacing metric (lower is more uniform)
   */
  public double calculateSpacing() {
    if (solutions.size() <= 1) {
      return 0.0;
    }

    // Calculate minimum distances
    List<Double> minDistances = new ArrayList<>();
    for (ParetoSolution sol : solutions) {
      double minDist = Double.MAX_VALUE;
      for (ParetoSolution other : solutions) {
        if (sol != other) {
          double dist = sol.distanceTo(other);
          minDist = Math.min(minDist, dist);
        }
      }
      minDistances.add(minDist);
    }

    // Calculate mean and standard deviation
    double mean = minDistances.stream().mapToDouble(d -> d).average().orElse(0);
    double variance =
        minDistances.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0);
    return Math.sqrt(variance);
  }

  /**
   * Export front to JSON format.
   *
   * @return JSON string representation
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

    List<Object> jsonSolutions = new ArrayList<>();
    for (ParetoSolution sol : solutions) {
      java.util.Map<String, Object> solMap = new java.util.LinkedHashMap<>();
      solMap.put("feasible", sol.isFeasible());
      solMap.put("decisionVariables", sol.getDecisionVariables());

      List<Object> objList = new ArrayList<>();
      for (int i = 0; i < sol.getNumObjectives(); i++) {
        java.util.Map<String, Object> objMap = new java.util.LinkedHashMap<>();
        objMap.put("name", sol.getObjectiveName(i));
        objMap.put("value", sol.getRawValue(i));
        objMap.put("unit", sol.getObjectiveUnit(i));
        objList.add(objMap);
      }
      solMap.put("objectives", objList);
      jsonSolutions.add(solMap);
    }

    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("size", solutions.size());
    result.put("spacing", calculateSpacing());
    if (!solutions.isEmpty()) {
      ParetoSolution knee = findKneePoint();
      if (knee != null) {
        java.util.Map<String, Object> kneeMap = new java.util.LinkedHashMap<>();
        kneeMap.put("decisionVariables", knee.getDecisionVariables());
        for (int i = 0; i < knee.getNumObjectives(); i++) {
          kneeMap.put(knee.getObjectiveName(i), knee.getRawValue(i));
        }
        result.put("kneePoint", kneeMap);
      }
    }
    result.put("solutions", jsonSolutions);

    return gson.toJson(result);
  }

  @Override
  public Iterator<ParetoSolution> iterator() {
    return solutions.iterator();
  }

  @Override
  public String toString() {
    if (solutions.isEmpty()) {
      return "ParetoFront{empty}";
    }
    StringBuilder sb = new StringBuilder("ParetoFront{size=").append(solutions.size());
    sb.append(", solutions=[\n");
    for (ParetoSolution sol : solutions) {
      sb.append("  ").append(sol).append("\n");
    }
    sb.append("]}");
    return sb.toString();
  }
}
