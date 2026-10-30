package neqsim.thermodynamicoperations;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, separately calculated liquid-water, ice and hydrate boundaries for one CPA fluid inventory.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class WaterPhaseBoundaryResult implements Serializable {
  /** Serialization identifier. */
  private static final long serialVersionUID = 1L;

  /**
   * Independent outcome of one boundary calculation.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  public enum Status {
    /** A finite numerical root met the boundary-specific residual criterion. */
    CONVERGED,
    /** The numerical calculation failed to establish a root. */
    FAILED,
    /** The requested boundary definition does not apply to the supplied material state or inventory. */
    NOT_APPLICABLE
  }

  /**
   * Immutable numerical outcome and reference-fluid basis of one boundary.
   *
   * @author NeqSim contributors
   * @version 1.0
   */
  public static final class BoundaryResult implements Serializable {
    /** Serialization identifier. */
    private static final long serialVersionUID = 1L;
    private final Status status;
    private final double temperatureC;
    private final double residual;
    private final double waterActivity;
    private final String diagnostic;
    private final String phaseReference;

    /**
     * Creates one boundary outcome; unavailable temperatures are always NaN.
     *
     * @param status calculation status
     * @param temperatureC calculated temperature in Celsius
     * @param residual dimensionless equilibrium residual
     * @param waterActivity fluid-water fugacity divided by pure CPA-liquid-water fugacity
     * @param diagnostic calculation or failure explanation
     * @param phaseReference material phases and condensed reference used
     */
    BoundaryResult(Status status, double temperatureC, double residual, double waterActivity, String diagnostic,
        String phaseReference) {
      if (status == Status.CONVERGED && (!Double.isFinite(temperatureC) || !Double.isFinite(residual))) {
        throw new IllegalArgumentException("A converged boundary requires finite temperature and residual");
      }
      this.status = status;
      this.temperatureC = status == Status.CONVERGED ? temperatureC : Double.NaN;
      this.residual = residual;
      this.waterActivity = waterActivity;
      this.diagnostic = diagnostic;
      this.phaseReference = phaseReference;
    }

    /** @return numerical or applicability status */
    public Status getStatus() {
      return status;
    }

    /** @return true only for a finite, converged boundary */
    public boolean isConverged() {
      return status == Status.CONVERGED;
    }

    /** @return temperature in Celsius, or NaN when unavailable */
    public double getTemperatureC() {
      return temperatureC;
    }

    /** @return dimensionless residual; see the boundary-specific diagnostic for its definition */
    public double getResidual() {
      return residual;
    }

    /** @return water fugacity relative to pure same-CPA liquid water, or NaN if unavailable */
    public double getWaterActivity() {
      return waterActivity;
    }

    /** @return numerical, applicability or failure explanation */
    public String getDiagnostic() {
      return diagnostic;
    }

    /** @return actual coexisting fluid phases and the condensed reference */
    public String getPhaseReference() {
      return phaseReference;
    }
  }

  private final double pressureBara;
  private final String model;
  private final Map<String, Double> moleFractions;
  private final BoundaryResult liquidWater;
  private final BoundaryResult ice;
  private final BoundaryResult hydrate;

  /**
   * Creates a complete comparison without retaining mutable fluid objects.
   *
   * @param pressureBara absolute calculation pressure in bar
   * @param model CPA model identity
   * @param moleFractions overall wet-fluid mole fractions, including inhibitors and oil fractions
   * @param liquidWater liquid-water result
   * @param ice ice result
   * @param hydrate hydrate result
   */
  WaterPhaseBoundaryResult(double pressureBara, String model, Map<String, Double> moleFractions,
      BoundaryResult liquidWater, BoundaryResult ice, BoundaryResult hydrate) {
    this.pressureBara = pressureBara;
    this.model = model;
    this.moleFractions = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(moleFractions));
    this.liquidWater = liquidWater;
    this.ice = ice;
    this.hydrate = hydrate;
  }

  /** @return absolute calculation pressure in bara */
  public double getPressureBara() {
    return pressureBara;
  }

  /** @return configured CPA model identity */
  public String getModel() {
    return model;
  }

  /** @return the required CPA mixing-rule number */
  public int getMixingRule() {
    return 10;
  }

  /** @return immutable overall wet-fluid composition */
  public Map<String, Double> getMoleFractions() {
    return moleFractions;
  }

  /** @return overall water mole fraction on the complete wet-fluid basis */
  public double getWaterMoleFraction() {
    return moleFractions.get("water");
  }

  /** @return liquid-water boundary, using metastable liquid below freezing */
  public BoundaryResult getLiquidWater() {
    return liquidWater;
  }

  /** @return ice boundary against the complete coexisting fluid inventory */
  public BoundaryResult getIce() {
    return ice;
  }

  /** @return hydrate boundary against the complete coexisting fluid inventory */
  public BoundaryResult getHydrate() {
    return hydrate;
  }

  /** @return true when all three independent boundary calculations converged */
  public boolean isComplete() {
    return liquidWater.isConverged() && ice.isConverged() && hydrate.isConverged();
  }

  /** @return interpretation that must accompany a comparison of the three numerical temperatures */
  public String getExplanation() {
    return "Separate equilibrium calculations on the same initial inventory: liquid water is metastable below "
        + "freezing; ice is a pure solid-water reference; hydrate is a water/guest clathrate. "
        + "Ice and hydrate use the actual coexisting fluid split, including oil and inhibitors. "
        + "These are not a sequential cooling path: precipitation changes the remaining water inventory. "
        + "Equilibrium convergence does not establish nucleation kinetics or experimental accuracy.";
  }
}
