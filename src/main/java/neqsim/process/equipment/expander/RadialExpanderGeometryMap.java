package neqsim.process.equipment.expander;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Geometry-based performance-map generator for a 90&deg; radial-inflow (IFR) turbo-expander.
 *
 * <p>
 * Commercial turbomachinery design codes (AxSTREAM, Concepts NREC) generate performance maps from blade geometry using
 * mean-line and through-flow methods. NeqSim is primarily map-driven; this class closes part of that gap by producing a
 * physically-shaped {@link ExpanderChartKhader} from a small set of mean-line geometric inputs. It is a
 * <em>preliminary</em> mean-line model intended for concept screening and to seed a digitised map when no OEM curve is
 * available, not a replacement for a full blade-to-blade or CFD design code.
 * </p>
 *
 * <p>
 * The efficiency model is a classic mean-line loss accounting for a 90&deg; IFR turbine (Dixon &amp; Hall, <i>Fluid
 * Mechanics and Thermodynamics of Turbomachinery</i>; Whitfield &amp; Baines, <i>Design of Radial Turbomachines</i>).
 * Working with the velocity ratio
 * </p>
 *
 * $$ \nu = \frac{U_2}{c_0}, \qquad c_0 = \sqrt{2\,\Delta h_{0s}} $$
 *
 * <p>
 * and an absolute rotor-inlet flow angle \( \alpha_2 \) measured from the meridional (radial) direction, the nominal
 * (zero-incidence) velocity ratio is
 * </p>
 *
 * $$ \nu_{opt} = \sqrt{1-R}\,\sin\alpha_2 $$
 *
 * <p>
 * where \(R\) is the stage degree of reaction. The total-to-static efficiency over a sweep of velocity ratio is then
 * </p>
 *
 * $$ \eta_{ts}(\nu) = 1 - \Big[ f_i(\nu_{opt}-\nu)^2 + \zeta_n(1-R) + \zeta_r\big(r_r^2\nu^2 + (1-R)\cos^2\alpha_2\big)
 * + (1-R)\cos^2\alpha_2 \Big] $$
 *
 * <p>
 * with incidence-loss factor \(f_i\), nozzle loss coefficient \(\zeta_n\), rotor loss coefficient \(\zeta_r\) and
 * exit/inlet radius ratio \(r_r = r_3/r_2\). The incidence term produces the characteristic efficiency peak near
 * \(\nu_{opt}\approx 0.7\); the friction term shifts the peak slightly below \(\nu_{opt}\); the constant nozzle/exit
 * terms cap the peak below unity.
 * </p>
 *
 * <p>
 * Inlet-guide-vane (IGV) modulation is represented by supplying one nozzle angle per IGV position; closing the vanes
 * increases \(\alpha_2\) (more swirl) which shifts \(\nu_{opt}\) and the peak efficiency. The available isentropic
 * stage head drop is assumed constant across the velocity-ratio sweep at a given IGV (it is fixed by the imposed
 * pressure ratio, not by the machine), and is fed to the chart in kJ/kg where {@link ExpanderChartKhader} rescales it
 * for the actual fluid sound speed.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class RadialExpanderGeometryMap implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(RadialExpanderGeometryMap.class);

  /** Impeller (rotor inlet) outer diameter in m. */
  private double impellerOuterDiameter = 0.424;

  /** Exit/inlet radius ratio r3/r2 (dimensionless). */
  private double radiusRatio = 0.45;

  /** Stage degree of reaction (0 = impulse, 0.5 = 50% reaction). */
  private double degreeOfReaction = 0.45;

  /** Nozzle (stator) kinetic-energy loss coefficient. */
  private double nozzleLossCoefficient = 0.08;

  /** Rotor relative kinetic-energy loss coefficient. */
  private double rotorLossCoefficient = 0.12;

  /** Incidence-loss factor applied to the off-nominal tangential relative velocity. */
  private double incidenceLossFactor = 0.65;

  /** Reference design isentropic stage head drop in kJ/kg at full IGV opening. */
  private double designHeadDropKjPerKg = 45.0;

  /** Lowest velocity ratio to evaluate on each generated curve. */
  private double minVelocityRatio = 0.40;

  /** Highest velocity ratio to evaluate on each generated curve. */
  private double maxVelocityRatio = 0.95;

  /** Number of velocity-ratio samples per generated curve. */
  private int pointsPerCurve = 11;

  /** Reference fluid used to normalise the generated map (may be {@code null}). */
  private SystemInterface referenceFluid = null;

  /**
   * Default constructor using representative cryogenic turbo-expander geometry.
   */
  public RadialExpanderGeometryMap() {
  }

  /**
   * Constructs a generator for a specific rotor geometry.
   *
   * @param impellerOuterDiameter the rotor inlet (tip) diameter in m
   * @param radiusRatio the exit/inlet radius ratio r3/r2 (0..1)
   * @param degreeOfReaction the stage degree of reaction (0..1)
   */
  public RadialExpanderGeometryMap(double impellerOuterDiameter, double radiusRatio, double degreeOfReaction) {
    setImpellerOuterDiameter(impellerOuterDiameter);
    setRadiusRatio(radiusRatio);
    setDegreeOfReaction(degreeOfReaction);
  }

  /**
   * Generates an {@link ExpanderChartKhader} from blade geometry for the supplied IGV schedule.
   *
   * @param igvPositions IGV positions (fraction of maximum area, 0..1), strictly increasing
   * @param nozzleAngleDeg absolute rotor-inlet flow angle (from the meridional/radial direction) in degrees, one value
   * per IGV position; larger angles correspond to more closed vanes
   * @return a populated {@link ExpanderChartKhader} ready for use by a turbo-expander model
   * @throws IllegalArgumentException if the inputs are null, empty or of mismatched length, or if any nozzle angle is
   * outside the open interval (0, 90) degrees
   */
  public ExpanderChartKhader generateChart(double[] igvPositions, double[] nozzleAngleDeg) {
    if (igvPositions == null || nozzleAngleDeg == null) {
      throw new IllegalArgumentException("igvPositions and nozzleAngleDeg must not be null");
    }
    if (igvPositions.length == 0 || igvPositions.length != nozzleAngleDeg.length) {
      throw new IllegalArgumentException("igvPositions and nozzleAngleDeg must be non-empty and of equal length");
    }
    int nIgv = igvPositions.length;
    double[][] uc = new double[nIgv][pointsPerCurve];
    double[][] eta = new double[nIgv][pointsPerCurve];
    double[][] headDrop = new double[nIgv][pointsPerCurve];

    for (int i = 0; i < nIgv; i++) {
      double alpha2 = Math.toRadians(nozzleAngleDeg[i]);
      if (nozzleAngleDeg[i] <= 0.0 || nozzleAngleDeg[i] >= 90.0) {
	throw new IllegalArgumentException(
	    "nozzleAngleDeg must be in the open interval (0, 90); got " + nozzleAngleDeg[i]);
      }
      double cosA = Math.cos(alpha2);
      double sinA = Math.sin(alpha2);
      double reactionTerm = 1.0 - degreeOfReaction;
      double nuOpt = Math.sqrt(reactionTerm) * sinA;
      // constant (velocity-ratio independent) loss contributions
      double constantLoss = nozzleLossCoefficient * reactionTerm + reactionTerm * cosA * cosA;
      // scale the available isentropic head modestly with IGV opening (more open => more flow/head)
      double headScale = 0.6 + 0.4 * clampUnit(igvPositions[i]);
      double headAtIgv = designHeadDropKjPerKg * headScale;

      for (int j = 0; j < pointsPerCurve; j++) {
	double nu = minVelocityRatio + (maxVelocityRatio - minVelocityRatio) * j / (double) (pointsPerCurve - 1);
	double incidence = incidenceLossFactor * (nuOpt - nu) * (nuOpt - nu);
	double rotorLoss = rotorLossCoefficient * (radiusRatio * radiusRatio * nu * nu + reactionTerm * cosA * cosA);
	double efficiency = 1.0 - (incidence + constantLoss + rotorLoss);
	if (efficiency < 0.0) {
	  efficiency = 0.0;
	}
	uc[i][j] = nu;
	eta[i][j] = efficiency;
	headDrop[i][j] = headAtIgv;
      }
    }

    ExpanderChartKhader chart = new ExpanderChartKhader(referenceFluid, impellerOuterDiameter);
    chart.setCurves(igvPositions, uc, eta, headDrop);
    logger.debug("RadialExpanderGeometryMap generated chart with {} IGV curves", nIgv);
    return chart;
  }

  /**
   * Computes the nominal (zero-incidence) velocity ratio for a given nozzle angle.
   *
   * @param nozzleAngleDeg absolute rotor-inlet flow angle from the meridional direction in degrees
   * @return the nominal velocity ratio U/C at peak efficiency
   * @throws IllegalArgumentException if the angle is not in the open interval (0, 90) degrees
   */
  public double nominalVelocityRatio(double nozzleAngleDeg) {
    if (nozzleAngleDeg <= 0.0 || nozzleAngleDeg >= 90.0) {
      throw new IllegalArgumentException("nozzleAngleDeg must be in the open interval (0, 90); got " + nozzleAngleDeg);
    }
    return Math.sqrt(1.0 - degreeOfReaction) * Math.sin(Math.toRadians(nozzleAngleDeg));
  }

  /**
   * Clamps a value to the closed unit interval [0, 1].
   *
   * @param value the value to clamp
   * @return the clamped value
   */
  private double clampUnit(double value) {
    if (value < 0.0) {
      return 0.0;
    }
    if (value > 1.0) {
      return 1.0;
    }
    return value;
  }

  /**
   * Sets the rotor inlet (tip) diameter.
   *
   * @param impellerOuterDiameter the rotor inlet diameter in m (must be positive)
   * @throws IllegalArgumentException if the diameter is not positive
   */
  public void setImpellerOuterDiameter(double impellerOuterDiameter) {
    if (impellerOuterDiameter <= 0.0) {
      throw new IllegalArgumentException("impellerOuterDiameter must be positive");
    }
    this.impellerOuterDiameter = impellerOuterDiameter;
  }

  /**
   * Sets the exit/inlet radius ratio r3/r2.
   *
   * @param radiusRatio the radius ratio in the open interval (0, 1)
   * @throws IllegalArgumentException if the ratio is not in (0, 1)
   */
  public void setRadiusRatio(double radiusRatio) {
    if (radiusRatio <= 0.0 || radiusRatio >= 1.0) {
      throw new IllegalArgumentException("radiusRatio must be in the open interval (0, 1)");
    }
    this.radiusRatio = radiusRatio;
  }

  /**
   * Sets the stage degree of reaction.
   *
   * @param degreeOfReaction the degree of reaction in the half-open interval [0, 1)
   * @throws IllegalArgumentException if the reaction is not in [0, 1)
   */
  public void setDegreeOfReaction(double degreeOfReaction) {
    if (degreeOfReaction < 0.0 || degreeOfReaction >= 1.0) {
      throw new IllegalArgumentException("degreeOfReaction must be in the half-open interval [0, 1)");
    }
    this.degreeOfReaction = degreeOfReaction;
  }

  /**
   * Sets the nozzle (stator) kinetic-energy loss coefficient.
   *
   * @param nozzleLossCoefficient the nozzle loss coefficient (must be non-negative)
   * @throws IllegalArgumentException if the coefficient is negative
   */
  public void setNozzleLossCoefficient(double nozzleLossCoefficient) {
    if (nozzleLossCoefficient < 0.0) {
      throw new IllegalArgumentException("nozzleLossCoefficient must be non-negative");
    }
    this.nozzleLossCoefficient = nozzleLossCoefficient;
  }

  /**
   * Sets the rotor relative kinetic-energy loss coefficient.
   *
   * @param rotorLossCoefficient the rotor loss coefficient (must be non-negative)
   * @throws IllegalArgumentException if the coefficient is negative
   */
  public void setRotorLossCoefficient(double rotorLossCoefficient) {
    if (rotorLossCoefficient < 0.0) {
      throw new IllegalArgumentException("rotorLossCoefficient must be non-negative");
    }
    this.rotorLossCoefficient = rotorLossCoefficient;
  }

  /**
   * Sets the incidence-loss factor applied to the off-nominal tangential relative velocity.
   *
   * @param incidenceLossFactor the incidence-loss factor (must be non-negative)
   * @throws IllegalArgumentException if the factor is negative
   */
  public void setIncidenceLossFactor(double incidenceLossFactor) {
    if (incidenceLossFactor < 0.0) {
      throw new IllegalArgumentException("incidenceLossFactor must be non-negative");
    }
    this.incidenceLossFactor = incidenceLossFactor;
  }

  /**
   * Sets the reference design isentropic stage head drop at full IGV opening.
   *
   * @param designHeadDropKjPerKg the design head drop in kJ/kg (must be positive)
   * @throws IllegalArgumentException if the head drop is not positive
   */
  public void setDesignHeadDropKjPerKg(double designHeadDropKjPerKg) {
    if (designHeadDropKjPerKg <= 0.0) {
      throw new IllegalArgumentException("designHeadDropKjPerKg must be positive");
    }
    this.designHeadDropKjPerKg = designHeadDropKjPerKg;
  }

  /**
   * Sets the velocity-ratio sweep range used when generating curves.
   *
   * @param minVelocityRatio the lowest velocity ratio (must be positive and below the maximum)
   * @param maxVelocityRatio the highest velocity ratio (must exceed the minimum)
   * @throws IllegalArgumentException if the range is invalid
   */
  public void setVelocityRatioRange(double minVelocityRatio, double maxVelocityRatio) {
    if (minVelocityRatio <= 0.0 || maxVelocityRatio <= minVelocityRatio) {
      throw new IllegalArgumentException("require 0 < minVelocityRatio < maxVelocityRatio");
    }
    this.minVelocityRatio = minVelocityRatio;
    this.maxVelocityRatio = maxVelocityRatio;
  }

  /**
   * Sets the number of velocity-ratio samples per generated curve.
   *
   * @param pointsPerCurve the number of points (must be at least 3)
   * @throws IllegalArgumentException if fewer than three points are requested
   */
  public void setPointsPerCurve(int pointsPerCurve) {
    if (pointsPerCurve < 3) {
      throw new IllegalArgumentException("pointsPerCurve must be at least 3");
    }
    this.pointsPerCurve = pointsPerCurve;
  }

  /**
   * Sets the reference fluid used to normalise the generated map.
   *
   * @param referenceFluid the reference fluid (may be {@code null} to skip composition correction)
   */
  public void setReferenceFluid(SystemInterface referenceFluid) {
    this.referenceFluid = referenceFluid;
  }

  /**
   * Gets the rotor inlet (tip) diameter.
   *
   * @return the impeller outer diameter in m
   */
  public double getImpellerOuterDiameter() {
    return impellerOuterDiameter;
  }

  /**
   * Gets the exit/inlet radius ratio.
   *
   * @return the radius ratio r3/r2
   */
  public double getRadiusRatio() {
    return radiusRatio;
  }

  /**
   * Gets the stage degree of reaction.
   *
   * @return the degree of reaction
   */
  public double getDegreeOfReaction() {
    return degreeOfReaction;
  }
}
