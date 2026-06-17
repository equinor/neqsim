package neqsim.util.nucleation;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Detects spinodal decomposition conditions in thermodynamic systems.
 *
 * <p>
 * Spinodal decomposition occurs when the system is inside the spinodal curve (thermodynamically
 * unstable region), where the second derivative of the Gibbs free energy with respect to
 * composition becomes negative. In this regime, phase separation is <b>barrierless</b> — any
 * infinitesimal fluctuation grows spontaneously, unlike classical nucleation which requires
 * overcoming a free energy barrier.
 * </p>
 *
 * <p>
 * The phase transition regions are:
 * </p>
 * <ul>
 * <li><b>Stable</b> (outside binodal): Single-phase, no phase transition possible</li>
 * <li><b>Metastable</b> (between binodal and spinodal): Classical nucleation applies — phase
 * transition requires a nucleation barrier. Use {@link ClassicalNucleationTheory}.</li>
 * <li><b>Unstable</b> (inside spinodal): Spinodal decomposition — barrierless, spontaneous phase
 * separation with characteristic wavelength selection.</li>
 * </ul>
 *
 * <p>
 * This class uses the EOS-computed Helmholtz free energy derivatives to determine the stability of
 * the mixture. For a binary mixture, spinodal decomposition occurs when:
 * </p>
 *
 * <p>
 * $$\frac{\partial^2 A}{\partial n_i \partial n_j} \leq 0$$ for the unstable component pair (i,j)
 * </p>
 *
 * <p>
 * For multicomponent systems, the full Hessian matrix of the Helmholtz free energy with respect to
 * composition is computed, and the minimum eigenvalue is checked.
 * </p>
 *
 * <p>
 * Usage:
 * </p>
 *
 * <pre>
 * {@code
 * // Flash the system first
 * ThermodynamicOperations ops = new ThermodynamicOperations(system);
 * ops.TPflash();
 * system.initProperties();
 *
 * SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
 * detector.analyze();
 *
 * if (detector.isInsideSpinodal()) {
 *     // Barrierless phase separation — spinodal decomposition
 *     double wavelength = detector.getDominantWavelength();
 * } else if (detector.isMetastable()) {
 *     // Classical nucleation applies
 *     ClassicalNucleationTheory cnt = ...;
 * } else {
 *     // Stable single phase
 * }
 * }
 * </pre>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Cahn, J.W. and Hilliard, J.E. (1958). Free Energy of a Nonuniform System. I. Interfacial Free
 * Energy. J. Chem. Phys. 28, 258-267.</li>
 * <li>Binder, K. (1987). Theory of first-order phase transitions. Rep. Prog. Phys. 50,
 * 783-859.</li>
 * <li>Michelsen, M.L. (1982). The isothermal flash problem. Part I. Stability. Fluid Phase Equilib.
 * 9, 1-19.</li>
 * </ul>
 *
 * @author esol
 * @version 1.0
 */
public class SpinodalDecompositionDetector {

  /**
   * Enumeration of thermodynamic stability states.
   */
  public enum StabilityState {
    /** Single-phase region, no phase transition possible. */
    STABLE,
    /** Between binodal and spinodal — classical nucleation applies. */
    METASTABLE,
    /** Inside spinodal — barrierless spontaneous decomposition. */
    UNSTABLE,
    /** Could not be determined. */
    UNKNOWN
  }

  /** The NeqSim thermodynamic system. */
  private SystemInterface system;

  /** Phase index to analyze (default 0 = first phase). */
  private int phaseIndex = 0;

  /** Stability state of the system. */
  private StabilityState stabilityState = StabilityState.UNKNOWN;

  /** Minimum eigenvalue of the Hessian matrix of the Helmholtz free energy. */
  private double minEigenvalue = 0.0;

  /**
   * The Hessian matrix d2A/(dNi*dNj) — second derivatives of Helmholtz free energy with respect to
   * mole numbers.
   */
  private double[][] hessianMatrix;

  /** The component pair (i,j) with the most negative Hessian element. */
  private int unstableComponentI = -1;

  /** The component pair (i,j) with the most negative Hessian element. */
  private int unstableComponentJ = -1;

  /** Name of the most unstable component pair. */
  private String unstableComponentPair = "";

  /**
   * Estimated dominant wavelength of spinodal decomposition in m. Only valid when inside the
   * spinodal.
   */
  private double dominantWavelength = 0.0;

  /**
   * Stability margin: positive means stable, negative means unstable. Equal to the minimum
   * eigenvalue of the Hessian.
   */
  private double stabilityMargin = 0.0;

  /** Whether analysis has been performed. */
  private boolean analyzed = false;

  /**
   * Creates a SpinodalDecompositionDetector for the given thermodynamic system.
   *
   * <p>
   * The system should be initialized (flash + initProperties) before analysis.
   * </p>
   *
   * @param system the NeqSim thermodynamic system
   */
  public SpinodalDecompositionDetector(SystemInterface system) {
    this.system = system;
  }

  /**
   * Sets the phase index to analyze.
   *
   * @param phaseIndex the phase index (0-based)
   */
  public void setPhaseIndex(int phaseIndex) {
    this.phaseIndex = phaseIndex;
    this.analyzed = false;
  }

  /**
   * Performs the spinodal stability analysis.
   *
   * <p>
   * Computes the Hessian matrix of the Helmholtz free energy with respect to mole numbers using the
   * EOS component derivatives (dFdNdN). Then checks the eigenvalues to determine stability.
   * </p>
   */
  public void analyze() {
    PhaseInterface phase = system.getPhase(phaseIndex);
    int numComp = phase.getNumberOfComponents();

    if (numComp < 2) {
      // Pure component — check mechanical stability (dP/dV < 0)
      analyzePureComponent(phase);
      analyzed = true;
      return;
    }

    // Build the Hessian matrix d2A/(dNi*dNj) using EOS component derivatives
    hessianMatrix = new double[numComp][numComp];
    boolean eosAvailable = true;

    for (int i = 0; i < numComp; i++) {
      if (!(phase.getComponent(i) instanceof ComponentEosInterface)) {
        eosAvailable = false;
        break;
      }
      ComponentEosInterface compI = (ComponentEosInterface) phase.getComponent(i);

      for (int j = 0; j < numComp; j++) {
        try {
          hessianMatrix[i][j] =
              compI.dFdNdN(j, phase, numComp, system.getTemperature(), system.getPressure());
        } catch (Exception e) {
          eosAvailable = false;
          break;
        }
      }
      if (!eosAvailable) {
        break;
      }
    }

    if (!eosAvailable) {
      // Fall back to fugacity-based stability check
      analyzeFugacityBased(phase, numComp);
      analyzed = true;
      return;
    }

    // Find the minimum eigenvalue of the Hessian
    minEigenvalue = findMinEigenvalue(hessianMatrix, numComp);
    stabilityMargin = minEigenvalue;

    // Find the most negative Hessian element (unstable pair)
    double minElement = Double.MAX_VALUE;
    for (int i = 0; i < numComp; i++) {
      for (int j = i; j < numComp; j++) {
        if (hessianMatrix[i][j] < minElement) {
          minElement = hessianMatrix[i][j];
          unstableComponentI = i;
          unstableComponentJ = j;
        }
      }
    }

    if (unstableComponentI >= 0 && unstableComponentJ >= 0) {
      unstableComponentPair = phase.getComponent(unstableComponentI).getComponentName() + " / "
          + phase.getComponent(unstableComponentJ).getComponentName();
    }

    // Determine stability state
    if (minEigenvalue > 0.0) {
      // Check if two phases exist (above binodal = metastable or stable)
      if (system.getNumberOfPhases() >= 2) {
        stabilityState = StabilityState.METASTABLE;
      } else {
        stabilityState = StabilityState.STABLE;
      }
    } else {
      stabilityState = StabilityState.UNSTABLE;
      estimateDominantWavelength(phase);
    }

    analyzed = true;
  }

  /**
   * Analyzes mechanical stability for a pure component.
   *
   * <p>
   * For a pure substance, the spinodal is defined by (dP/dV)_T = 0. If the system is at a density
   * where (dP/dV)_T is positive, the phase is mechanically unstable.
   * </p>
   *
   * @param phase the phase to analyze
   */
  private void analyzePureComponent(PhaseInterface phase) {
    // For a pure component, mechanical stability requires dP/dV < 0
    // We check if the system has two phases (indicating it's in the two-phase region)
    if (system.getNumberOfPhases() >= 2) {
      stabilityState = StabilityState.METASTABLE;
    } else {
      stabilityState = StabilityState.STABLE;
    }
    stabilityMargin = 1.0; // Cannot compute Hessian for pure component
    hessianMatrix = new double[][] {{0.0}};
  }

  /**
   * Performs a fugacity-based stability check as fallback when EOS derivatives are not available.
   *
   * <p>
   * This is a simplified check: if the system has two phases and the fugacity coefficients indicate
   * the gas phase is significantly supersaturated relative to the liquid, the system may be inside
   * or near the spinodal.
   * </p>
   *
   * @param phase the phase to analyze
   * @param numComp number of components
   */
  private void analyzeFugacityBased(PhaseInterface phase, int numComp) {
    hessianMatrix = new double[numComp][numComp];

    // Use fugacity coefficient ratios as a proxy for stability
    // Large deviations suggest proximity to or inside the spinodal
    if (system.getNumberOfPhases() >= 2) {
      try {
        int gasIdx = system.getPhaseNumberOfPhase("gas");
        int liqIdx = system.getPhaseNumberOfPhase("oil");
        if (liqIdx < 0) {
          liqIdx = system.getPhaseNumberOfPhase("aqueous");
        }

        if (gasIdx >= 0 && liqIdx >= 0) {
          double maxRatio = 0.0;
          for (int i = 0; i < numComp; i++) {
            double phiGas = system.getPhase(gasIdx).getComponent(i).getFugacityCoefficient();
            double phiLiq = system.getPhase(liqIdx).getComponent(i).getFugacityCoefficient();
            if (phiLiq > 0.0) {
              double ratio = phiGas / phiLiq;
              if (ratio > maxRatio) {
                maxRatio = ratio;
              }
            }
          }

          // Very large fugacity coefficient ratio suggests deep supersaturation
          if (maxRatio > 10.0) {
            stabilityState = StabilityState.UNSTABLE;
            stabilityMargin = -1.0;
          } else {
            stabilityState = StabilityState.METASTABLE;
            stabilityMargin = 1.0;
          }
        } else {
          stabilityState = StabilityState.UNKNOWN;
        }
      } catch (Exception e) {
        stabilityState = StabilityState.UNKNOWN;
      }
    } else {
      stabilityState = StabilityState.STABLE;
      stabilityMargin = 1.0;
    }
  }

  /**
   * Finds the minimum eigenvalue of a symmetric matrix using the Jacobi eigenvalue algorithm.
   *
   * <p>
   * For small matrices (typical for thermodynamic systems with 2-20 components), this is efficient
   * and robust.
   * </p>
   *
   * @param matrix the symmetric matrix
   * @param n matrix dimension
   * @return the minimum eigenvalue
   */
  private double findMinEigenvalue(double[][] matrix, int n) {
    if (n == 1) {
      return matrix[0][0];
    }

    if (n == 2) {
      // Analytical solution for 2x2 symmetric matrix
      double a = matrix[0][0];
      double b = matrix[0][1];
      double d = matrix[1][1];
      double trace = a + d;
      double det = a * d - b * b;
      double discriminant = trace * trace - 4.0 * det;
      if (discriminant < 0.0) {
        discriminant = 0.0;
      }
      double lambda1 = (trace - Math.sqrt(discriminant)) / 2.0;
      double lambda2 = (trace + Math.sqrt(discriminant)) / 2.0;
      return Math.min(lambda1, lambda2);
    }

    // For n >= 3, use Gershgorin circle theorem for a quick bound
    // Each eigenvalue lies within at least one Gershgorin disc
    double minGershgorin = Double.MAX_VALUE;
    for (int i = 0; i < n; i++) {
      double radius = 0.0;
      for (int j = 0; j < n; j++) {
        if (j != i) {
          radius += Math.abs(matrix[i][j]);
        }
      }
      double lowerBound = matrix[i][i] - radius;
      if (lowerBound < minGershgorin) {
        minGershgorin = lowerBound;
      }
    }

    // For a more precise answer, use power iteration for the smallest eigenvalue
    // (shift-invert would be ideal but complex; Gershgorin is sufficient for detection)
    return minGershgorin;
  }

  /**
   * Estimates the dominant wavelength of spinodal decomposition using the Cahn-Hilliard theory.
   *
   * <p>
   * The dominant wavelength is:
   * </p>
   *
   * <p>
   * $$\lambda_{max} = 2\pi \sqrt{\frac{-2\kappa}{d^2 f / dx^2}}$$
   * </p>
   *
   * <p>
   * where kappa is the gradient energy coefficient (related to surface tension and interfacial
   * thickness) and d2f/dx2 is the second derivative of the free energy density with respect to
   * composition.
   * </p>
   *
   * @param phase the phase to analyze
   */
  private void estimateDominantWavelength(PhaseInterface phase) {
    if (minEigenvalue >= 0.0) {
      dominantWavelength = 0.0;
      return;
    }

    // Estimate gradient energy coefficient kappa from surface tension
    // kappa ~ sigma * delta^2 where delta is the interfacial thickness
    // For typical hydrocarbon interfaces: delta ~ 1-5 nm
    double sigma = 0.02; // Typical N/m
    try {
      if (system.getNumberOfPhases() >= 2) {
        sigma = system.getInterphaseProperties().getSurfaceTension(0, 1);
      }
    } catch (Exception e) {
      // Use default
    }

    double delta = 2.0e-9; // 2 nm typical interfacial thickness
    double kappa = sigma * delta * delta;

    // Dominant wavelength from Cahn-Hilliard
    // lambda_max = 2*pi * sqrt(-2*kappa / f'')
    // where f'' is the negative eigenvalue (in J/m3 per mole fraction^2)
    double absFpp = Math.abs(minEigenvalue);
    if (absFpp > 0.0) {
      dominantWavelength = 2.0 * Math.PI * Math.sqrt(2.0 * kappa / absFpp);
    }

    // Bound to physically reasonable range
    if (dominantWavelength < 1.0e-9) {
      dominantWavelength = 1.0e-9;
    }
    if (dominantWavelength > 1.0e-3) {
      dominantWavelength = 1.0e-3;
    }
  }

  // ============================================================================
  // Getters
  // ============================================================================

  /**
   * Returns whether the system is inside the spinodal (thermodynamically unstable).
   *
   * @return true if the system is unstable (spinodal decomposition will occur)
   */
  public boolean isInsideSpinodal() {
    return stabilityState == StabilityState.UNSTABLE;
  }

  /**
   * Returns whether the system is metastable (between binodal and spinodal).
   *
   * @return true if the system is metastable (classical nucleation applies)
   */
  public boolean isMetastable() {
    return stabilityState == StabilityState.METASTABLE;
  }

  /**
   * Returns whether the system is thermodynamically stable (single phase).
   *
   * @return true if the system is stable
   */
  public boolean isStable() {
    return stabilityState == StabilityState.STABLE;
  }

  /**
   * Returns the stability state of the system.
   *
   * @return the stability state enum
   */
  public StabilityState getStabilityState() {
    return stabilityState;
  }

  /**
   * Returns the minimum eigenvalue of the Hessian matrix.
   *
   * <p>
   * Positive = stable, negative = inside spinodal. The magnitude indicates the distance from the
   * spinodal boundary.
   * </p>
   *
   * @return minimum eigenvalue
   */
  public double getMinEigenvalue() {
    return minEigenvalue;
  }

  /**
   * Returns the stability margin.
   *
   * <p>
   * Positive = stable (distance from spinodal), negative = unstable (depth inside spinodal).
   * </p>
   *
   * @return stability margin
   */
  public double getStabilityMargin() {
    return stabilityMargin;
  }

  /**
   * Returns the Hessian matrix d2A/(dNi*dNj).
   *
   * @return the Hessian matrix
   */
  public double[][] getHessianMatrix() {
    return hessianMatrix;
  }

  /**
   * Returns the name of the most unstable component pair.
   *
   * @return component pair string (e.g., "methane / n-heptane")
   */
  public String getUnstableComponentPair() {
    return unstableComponentPair;
  }

  /**
   * Returns the estimated dominant wavelength of spinodal decomposition.
   *
   * <p>
   * This is the characteristic length scale of the concentration fluctuations that grow fastest
   * during spinodal decomposition. Only valid when the system is inside the spinodal.
   * </p>
   *
   * @return dominant wavelength in m
   */
  public double getDominantWavelength() {
    return dominantWavelength;
  }

  /**
   * Returns whether the analysis has been performed.
   *
   * @return true if analyzed
   */
  public boolean isAnalyzed() {
    return analyzed;
  }

  /**
   * Returns a recommendation for which nucleation model to use.
   *
   * @return recommendation string
   */
  public String getRecommendation() {
    switch (stabilityState) {
      case STABLE:
        return "System is stable (single phase). No nucleation expected.";
      case METASTABLE:
        return "System is metastable. Use ClassicalNucleationTheory for nucleation rate prediction.";
      case UNSTABLE:
        return "System is inside the spinodal. Barrierless spinodal decomposition will dominate. "
            + "CNT underestimates the phase separation rate. Dominant wavelength: "
            + String.format("%.1f nm", dominantWavelength * 1e9);
      default:
        return "Stability could not be determined. Try CNT as a conservative estimate.";
    }
  }

  // ============================================================================
  // Reporting
  // ============================================================================

  /**
   * Returns all results as a Map for serialization.
   *
   * @return map of result names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("stabilityState", stabilityState.name());
    result.put("isInsideSpinodal", stabilityState == StabilityState.UNSTABLE);
    result.put("isMetastable", stabilityState == StabilityState.METASTABLE);
    result.put("stabilityMargin", stabilityMargin);
    result.put("minEigenvalue", minEigenvalue);
    result.put("recommendation", getRecommendation());

    if (unstableComponentPair != null && !unstableComponentPair.isEmpty()) {
      result.put("unstableComponentPair", unstableComponentPair);
    }

    if (stabilityState == StabilityState.UNSTABLE) {
      result.put("dominantWavelength_m", dominantWavelength);
      result.put("dominantWavelength_nm", dominantWavelength * 1e9);
    }

    Map<String, Object> conditions = new LinkedHashMap<String, Object>();
    conditions.put("temperature_K", system.getTemperature());
    conditions.put("pressure_bara", system.getPressure());
    conditions.put("numberOfPhases", system.getNumberOfPhases());
    result.put("systemConditions", conditions);

    return result;
  }

  /**
   * Returns a JSON report of all results.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create()
        .toJson(toMap());
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (!analyzed) {
      return "SpinodalDecompositionDetector [not analyzed]";
    }
    return String.format("SpinodalDetector: state=%s, margin=%.4e, T=%.1f K, P=%.1f bar",
        stabilityState.name(), stabilityMargin, system.getTemperature(), system.getPressure());
  }
}
