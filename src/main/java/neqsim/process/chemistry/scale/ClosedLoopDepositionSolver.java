package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

/**
 * Closed-loop deposition-flow solver.
 *
 * <p>
 * Iteratively couples a {@link ScaleDepositionAccumulator} with the host {@link PipeBeggsAndBrills}
 * pipe so that the wall thickness build-up shrinks the effective internal diameter, raises the
 * local velocity and shear, and feeds back into the next deposition pass. The loop terminates when
 * the maximum thickness change between successive iterations falls below a tolerance, or after a
 * maximum number of iterations.
 *
 * <p>
 * This captures one of the most important physical effects missing from screening-level scale
 * predictors: the run-away condition where a thin deposit accelerates further deposition through
 * higher local mass-transfer rates and ultimately blocks the line.
 *
 * <p>
 * Algorithm per iteration k:
 * <ol>
 * <li>Run pipe hydraulics with current effective diameter {@code d_k}.</li>
 * <li>Run {@link ScaleDepositionAccumulator#evaluate()} → max thickness {@code th_k}.</li>
 * <li>Update effective diameter {@code d_(k+1) = d_0 - 2 * th_k / 1000}.</li>
 * <li>Stop when {@code |d_(k+1) - d_k| < tol_m} or {@code k >= maxIter} or
 * {@code d_(k+1) <= 0}.</li>
 * </ol>
 *
 * <p>
 * The original pipe diameter is restored after the solve so that the host {@code ProcessSystem}
 * state is unchanged.
 *
 * @author ESOL
 * @version 1.0
 */
public class ClosedLoopDepositionSolver implements Serializable {

  private static final long serialVersionUID = 1000L;

  private final transient PipeBeggsAndBrills pipe;
  private final transient ScaleDepositionAccumulator accumulator;
  private double toleranceM = 1e-4;
  private int maxIterations = 10;

  private final List<Double> diameterHistoryM = new ArrayList<Double>();
  private final List<Double> maxThicknessHistoryMm = new ArrayList<Double>();
  private final List<Double> velocityHistoryMs = new ArrayList<Double>();
  private boolean converged = false;
  private int iterationsTaken = 0;
  private double finalEffectiveDiameterM = 0.0;

  /**
   * Constructs a solver bound to the given pipe and accumulator.
   *
   * @param pipe the host Beggs-and-Brills pipe
   * @param accumulator the scale deposition accumulator (already configured with brine chemistry)
   */
  public ClosedLoopDepositionSolver(PipeBeggsAndBrills pipe,
      ScaleDepositionAccumulator accumulator) {
    this.pipe = pipe;
    this.accumulator = accumulator;
  }

  /**
   * Sets the convergence tolerance on the effective diameter (m).
   *
   * @param tolM tolerance [m], default 1e-4 m
   * @return this for chaining
   */
  public ClosedLoopDepositionSolver setToleranceM(double tolM) {
    this.toleranceM = Math.max(1e-7, tolM);
    return this;
  }

  /**
   * Sets the maximum number of iterations.
   *
   * @param n maximum iterations, default 10
   * @return this for chaining
   */
  public ClosedLoopDepositionSolver setMaxIterations(int n) {
    this.maxIterations = Math.max(1, n);
    return this;
  }

  /**
   * Runs the coupled deposition-flow loop.
   *
   * @return this for chaining
   */
  public ClosedLoopDepositionSolver solve() {
    diameterHistoryM.clear();
    maxThicknessHistoryMm.clear();
    velocityHistoryMs.clear();
    converged = false;

    final double originalDiameter = pipe.getDiameter();
    double currentD = originalDiameter;
    double previousD = originalDiameter;
    iterationsTaken = 0;

    try {
      for (int k = 0; k < maxIterations; k++) {
        iterationsTaken = k + 1;
        pipe.setDiameter(currentD);
        try {
          pipe.run();
        } catch (Exception ignore) {
          // pipe may have already converged; tolerate failure and proceed with last profile
        }

        accumulator.evaluate();
        double thMm = accumulator.getMaxThicknessMm();

        diameterHistoryM.add(currentD);
        maxThicknessHistoryMm.add(thMm);

        double avgVelocity = 0.0;
        try {
          List<Double> vProfile = pipe.getMixtureSuperficialVelocityProfile();
          if (vProfile != null && !vProfile.isEmpty()) {
            double sum = 0.0;
            for (Double v : vProfile) {
              sum += v;
            }
            avgVelocity = sum / vProfile.size();
          }
        } catch (Exception ignore) {
          // velocity profile not always available; default 0
        }
        velocityHistoryMs.add(avgVelocity);

        double newD = originalDiameter - 2.0 * thMm / 1000.0;
        if (newD <= 0.0) {
          // pipe blocked
          finalEffectiveDiameterM = 0.0;
          converged = true;
          break;
        }

        if (Math.abs(newD - previousD) < toleranceM) {
          converged = true;
          finalEffectiveDiameterM = newD;
          break;
        }
        previousD = currentD;
        currentD = newD;
      }
      if (!converged) {
        finalEffectiveDiameterM = currentD;
      }
    } finally {
      // restore original geometry so process state is unchanged
      pipe.setDiameter(originalDiameter);
    }
    return this;
  }

  /**
   * Returns the iteration history of effective diameters.
   *
   * @return list of diameters [m]
   */
  public List<Double> getDiameterHistoryM() {
    return new ArrayList<Double>(diameterHistoryM);
  }

  /**
   * Returns the iteration history of maximum thicknesses.
   *
   * @return list of thicknesses [mm]
   */
  public List<Double> getMaxThicknessHistoryMm() {
    return new ArrayList<Double>(maxThicknessHistoryMm);
  }

  /**
   * Returns the iteration history of average mixture velocities.
   *
   * @return list of velocities [m/s]
   */
  public List<Double> getVelocityHistoryMs() {
    return new ArrayList<Double>(velocityHistoryMs);
  }

  /**
   * Returns whether the solver converged within tolerance.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Returns the number of iterations taken.
   *
   * @return iteration count
   */
  public int getIterationsTaken() {
    return iterationsTaken;
  }

  /**
   * Returns the final effective inner diameter after deposition.
   *
   * @return diameter [m]; zero if pipe was blocked
   */
  public double getFinalEffectiveDiameterM() {
    return finalEffectiveDiameterM;
  }

  /**
   * Returns a structured map representation.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("iterationsTaken", iterationsTaken);
    map.put("converged", converged);
    map.put("toleranceM", toleranceM);
    map.put("maxIterations", maxIterations);
    map.put("finalEffectiveDiameterM", finalEffectiveDiameterM);
    map.put("diameterHistoryM", new ArrayList<Double>(diameterHistoryM));
    map.put("maxThicknessHistoryMm", new ArrayList<Double>(maxThicknessHistoryMm));
    map.put("velocityHistoryMs", new ArrayList<Double>(velocityHistoryMs));
    return map;
  }

  /**
   * Returns a JSON representation.
   *
   * @return pretty-printed JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls()
        .serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
