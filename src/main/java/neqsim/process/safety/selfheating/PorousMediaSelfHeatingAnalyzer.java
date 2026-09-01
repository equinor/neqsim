package neqsim.process.safety.selfheating;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Frank-Kamenetskii steady-state criticality screening for self-heating in a reactive porous body.
 *
 * <p>
 * Solves the classical thermal-explosion question for a body that generates heat internally by a slow exothermic
 * reaction and loses it by conduction to its boundary: <i>does a stable steady-state temperature profile exist, or must
 * the body run away to ignition?</i> This is the governing question for lagging fires, where a combustible liquid has
 * soaked into porous thermal insulation.
 * </p>
 *
 * <p>
 * The steady-state energy balance with an Arrhenius volumetric source
 * </p>
 *
 * <p>
 * {@code lambda * grad^2(T) + P * exp(-E / (R * T)) = 0}
 * </p>
 *
 * <p>
 * is reduced by the Frank-Kamenetskii exponential approximation about the boundary temperature to a single
 * dimensionless group
 * </p>
 *
 * <p>
 * {@code delta = (E * P * r^2) / (lambda * R * Ta^2) * exp(-E / (R * Ta))}
 * </p>
 *
 * <p>
 * where {@code P = A * Q * rho} is the volumetric heat-release pre-exponential factor [W/m3], {@code E} the activation
 * energy [J/mol], {@code r} the characteristic half-dimension [m], {@code lambda} the effective thermal conductivity of
 * the wetted porous medium [W/(m K)] and {@code Ta} the boundary temperature [K]. A steady state exists only while
 * {@code delta <= deltaCrit}, where {@code deltaCrit} depends only on the body shape (see {@link SelfHeatingGeometry}).
 * </p>
 *
 * <p>
 * Because {@code delta} scales with {@code r^2}, doubling the insulation thickness quadruples the criticality
 * parameter. This size dependence is the reason a sample that is perfectly stable in a laboratory dish can ignite when
 * the same material is applied as a thick layer, and it is why a lumped adiabatic screening such as
 * {@link neqsim.process.safety.reaction.RunawayReactionAnalyzer} cannot answer this question.
 * </p>
 *
 * <p>
 * The kinetic inputs {@code E} and {@code P} are not obtainable from equilibrium thermodynamics and must come from
 * hot-storage (basket) testing; see {@link BasketTestRegression}.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * PorousMediaSelfHeatingAnalyzer analyzer = new PorousMediaSelfHeatingAnalyzer();
 * analyzer.setGeometry(SelfHeatingGeometry.SLAB);
 * analyzer.setCharacteristicDimension(50.0, "mm");
 * analyzer.setEffectiveThermalConductivity(0.09);
 * analyzer.setActivationEnergy(110.0, "kJ/mol");
 * analyzer.setVolumetricHeatReleasePreFactor(2.5e12);
 * analyzer.setBoundaryTemperature(180.0, "C");
 * PorousMediaSelfHeatingResult result = analyzer.analyze();
 * </pre>
 *
 * <p>
 * References: Frank-Kamenetskii, <i>Diffusion and Heat Transfer in Chemical Kinetics</i>, 2nd ed.; Bowes,
 * <i>Self-Heating: Evaluating and Controlling the Hazards</i>, 1984; Babrauskas, <i>Ignition Handbook</i>, 2003.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PorousMediaSelfHeatingAnalyzer {
  private static final Logger logger = LogManager.getLogger(PorousMediaSelfHeatingAnalyzer.class);

  /** Universal gas constant [J/(mol K)]. */
  private static final double R_GAS = 8.314462618;

  /** Default ratio of delta to deltaCrit above which a subcritical case is reported as marginal. */
  private static final double DEFAULT_MARGINAL_RATIO = 0.7;

  /** Lower bound of the critical-temperature bisection search [K]. */
  private static final double T_SEARCH_MIN_K = 150.0;

  /** Upper bound of the critical-temperature bisection search [K]. */
  private static final double T_SEARCH_MAX_K = 2000.0;

  /** Bisection convergence tolerance on temperature [K]. */
  private static final double T_SEARCH_TOL_K = 1.0e-4;

  /** Maximum number of bisection iterations. */
  private static final int MAX_BISECTION_ITERATIONS = 200;

  private SelfHeatingGeometry geometry = SelfHeatingGeometry.SLAB;
  private double characteristicDimensionM = Double.NaN;
  private double effectiveConductivityWPerMK = Double.NaN;
  private double activationEnergyJPerMol = Double.NaN;
  private double volumetricPreFactorWPerM3 = Double.NaN;
  private double boundaryTemperatureK = Double.NaN;
  private double marginalRatio = DEFAULT_MARGINAL_RATIO;
  private final List<String> configurationNotes = new ArrayList<String>();

  /**
   * Create a Frank-Kamenetskii self-heating analyzer with no inputs set.
   */
  public PorousMediaSelfHeatingAnalyzer() {
  }

  /**
   * Set the body shape. The characteristic dimension supplied to {@link #setCharacteristicDimension(double, String)}
   * must match the shape convention reported by {@link SelfHeatingGeometry#getDimensionDescription()}.
   *
   * @param geometry the body shape; must not be null
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if geometry is null
   */
  public PorousMediaSelfHeatingAnalyzer setGeometry(SelfHeatingGeometry geometry) {
    if (geometry == null) {
      throw new IllegalArgumentException("Geometry must not be null");
    }
    this.geometry = geometry;
    return this;
  }

  /**
   * Set the characteristic half-dimension of the reactive body.
   *
   * @param value dimension value; must be positive
   * @param unit length unit ("m", "cm", "mm" or "in")
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the value is not positive or the unit is unsupported
   */
  public PorousMediaSelfHeatingAnalyzer setCharacteristicDimension(double value, String unit) {
    this.characteristicDimensionM = toMetres(value, unit);
    return this;
  }

  /**
   * Set the effective thermal conductivity of the wetted porous medium. This is the conductivity of the
   * insulation-plus-absorbed-liquid composite, which is typically higher than the dry insulation value because the
   * liquid displaces air in the pore space.
   *
   * @param wPerMK effective thermal conductivity [W/(m K)]; must be positive
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public PorousMediaSelfHeatingAnalyzer setEffectiveThermalConductivity(double wPerMK) {
    if (!(wPerMK > 0.0)) {
      throw new IllegalArgumentException("Effective thermal conductivity must be positive");
    }
    this.effectiveConductivityWPerMK = wPerMK;
    return this;
  }

  /**
   * Set the apparent activation energy of the self-heating reaction.
   *
   * @param value activation-energy value; must be positive
   * @param unit energy unit ("J/mol" or "kJ/mol")
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the value is not positive or the unit is unsupported
   */
  public PorousMediaSelfHeatingAnalyzer setActivationEnergy(double value, String unit) {
    double joulesPerMol;
    if ("kJ/mol".equalsIgnoreCase(unit)) {
      joulesPerMol = value * 1000.0;
    } else if ("J/mol".equalsIgnoreCase(unit)) {
      joulesPerMol = value;
    } else {
      throw new IllegalArgumentException("Unsupported activation-energy unit: " + unit + " (use J/mol or kJ/mol)");
    }
    if (!(joulesPerMol > 0.0)) {
      throw new IllegalArgumentException("Activation energy must be positive");
    }
    this.activationEnergyJPerMol = joulesPerMol;
    return this;
  }

  /**
   * Set the volumetric heat-release pre-exponential factor {@code P = A * Q * rho} directly.
   *
   * @param wPerM3 volumetric heat-release pre-exponential factor [W/m3]; must be positive
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public PorousMediaSelfHeatingAnalyzer setVolumetricHeatReleasePreFactor(double wPerM3) {
    if (!(wPerM3 > 0.0)) {
      throw new IllegalArgumentException("Volumetric heat-release pre-factor must be positive");
    }
    this.volumetricPreFactorWPerM3 = wPerM3;
    return this;
  }

  /**
   * Set the volumetric heat-release pre-exponential factor from separate kinetic and loading data, computing
   * {@code P = A * Q * rho}.
   *
   * @param preExponentialFactorPerSecond Arrhenius pre-exponential factor {@code A} [1/s]; must be positive
   * @param heatOfReactionJPerKg specific heat of the oxidation reaction {@code Q} [J/kg of reactive liquid]; the
   * magnitude is used, so either sign convention is accepted
   * @param reactiveBulkDensityKgPerM3 mass of reactive liquid per unit bulk volume of the porous body {@code rho}
   * [kg/m3]; must be positive
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if any argument is not positive
   */
  public PorousMediaSelfHeatingAnalyzer setKinetics(double preExponentialFactorPerSecond, double heatOfReactionJPerKg,
      double reactiveBulkDensityKgPerM3) {
    if (!(preExponentialFactorPerSecond > 0.0)) {
      throw new IllegalArgumentException("Pre-exponential factor must be positive");
    }
    if (!(Math.abs(heatOfReactionJPerKg) > 0.0)) {
      throw new IllegalArgumentException("Heat of reaction must be non-zero");
    }
    if (!(reactiveBulkDensityKgPerM3 > 0.0)) {
      throw new IllegalArgumentException("Reactive bulk density must be positive");
    }
    this.volumetricPreFactorWPerM3 = preExponentialFactorPerSecond * Math.abs(heatOfReactionJPerKg)
        * reactiveBulkDensityKgPerM3;
    return this;
  }

  /**
   * Set the boundary (ambient or surface) temperature that the body is held at.
   *
   * @param value temperature value
   * @param unit temperature unit ("K" or "C")
   * @return this analyzer for chaining
   */
  public PorousMediaSelfHeatingAnalyzer setBoundaryTemperature(double value, String unit) {
    this.boundaryTemperatureK = new neqsim.util.unit.TemperatureUnit(value, unit).getValue("K");
    return this;
  }

  /**
   * Set the ratio of delta to the critical delta above which a subcritical case is reported as
   * {@link SelfHeatingVerdict#MARGINAL}.
   *
   * @param ratio marginal ratio, strictly between 0 and 1
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the ratio is outside the open interval (0, 1)
   */
  public PorousMediaSelfHeatingAnalyzer setMarginalRatio(double ratio) {
    if (!(ratio > 0.0) || !(ratio < 1.0)) {
      throw new IllegalArgumentException("Marginal ratio must be between 0 and 1 (exclusive)");
    }
    this.marginalRatio = ratio;
    return this;
  }

  /**
   * Configure the analyzer for a layer of liquid-wetted insulation on a hot pipe or vessel wall.
   *
   * <p>
   * The layer is treated as an infinite slab whose characteristic half-dimension equals the <i>full</i> insulation
   * thickness and whose boundary temperature equals the pipe wall temperature. This is deliberately conservative: the
   * hot process surface supplies heat rather than removing it, so the pipe-side face behaves as an adiabatic symmetry
   * plane, and taking the whole layer at the pipe wall temperature bounds the real profile, which falls towards ambient
   * at the outer face. A case that screens as {@link SelfHeatingVerdict#SUBCRITICAL} on this basis is safe; a case that
   * screens as {@link SelfHeatingVerdict#SELF_IGNITION} should be refined with {@link SelfHeatingInductionSolver} using
   * the true boundary temperatures before any decision is taken.
   * </p>
   *
   * @param insulationThickness insulation layer thickness; must be positive
   * @param thicknessUnit length unit of the thickness ("m", "cm", "mm" or "in")
   * @param pipeWallTemperature process-side wall temperature
   * @param temperatureUnit temperature unit ("K" or "C")
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the thickness is not positive or a unit is unsupported
   */
  public PorousMediaSelfHeatingAnalyzer forPipeInsulation(double insulationThickness, String thicknessUnit,
      double pipeWallTemperature, String temperatureUnit) {
    setGeometry(SelfHeatingGeometry.SLAB);
    setCharacteristicDimension(insulationThickness, thicknessUnit);
    setBoundaryTemperature(pipeWallTemperature, temperatureUnit);
    configurationNotes.add("Pipe-insulation screening: slab half-dimension set to the full insulation thickness and "
        + "boundary temperature set to the pipe wall temperature (conservative bounding assumption)");
    return this;
  }

  /**
   * Run the Frank-Kamenetskii criticality screening.
   *
   * @return an immutable screening result
   * @throws IllegalStateException if required inputs are missing or physically invalid
   */
  public PorousMediaSelfHeatingResult analyze() {
    validateInputs();
    List<String> warnings = new ArrayList<String>(configurationNotes);

    double deltaCrit = geometry.getDeltaCrit();
    double delta = criticalityParameter(boundaryTemperatureK, characteristicDimensionM);
    double deltaRatio = delta / deltaCrit;

    double criticalTemperatureK = solveCriticalTemperature(characteristicDimensionM, deltaCrit);
    double criticalDimensionM = criticalDimension(boundaryTemperatureK, deltaCrit);

    double temperatureMarginK = Double.isNaN(criticalTemperatureK) ? Double.NaN
        : criticalTemperatureK - boundaryTemperatureK;
    double dimensionMarginM = criticalDimensionM - characteristicDimensionM;
    double fkTemperatureScaleK = R_GAS * boundaryTemperatureK * boundaryTemperatureK / activationEnergyJPerMol;

    SelfHeatingVerdict verdict;
    if (deltaRatio > 1.0) {
      verdict = SelfHeatingVerdict.SELF_IGNITION;
    } else if (deltaRatio >= marginalRatio) {
      verdict = SelfHeatingVerdict.MARGINAL;
    } else {
      verdict = SelfHeatingVerdict.SUBCRITICAL;
    }

    if (Double.isNaN(criticalTemperatureK)) {
      warnings.add("Critical ambient temperature could not be bracketed between " + T_SEARCH_MIN_K + " K and "
          + T_SEARCH_MAX_K + " K; check the kinetic parameters");
    }
    warnings.add("Frank-Kamenetskii theory assumes a uniform boundary temperature, conduction-controlled heat loss "
        + "(large Biot number) and negligible reactant consumption; use SemenovSelfHeatingAnalyzer when surface "
        + "cooling controls");

    logger.info("Self-heating screening: geometry={}, delta={}, deltaCrit={}, T_crit={} K, r_crit={} m, verdict={}",
        geometry, delta, deltaCrit, criticalTemperatureK, criticalDimensionM, verdict);

    return new PorousMediaSelfHeatingResult(geometry, characteristicDimensionM, effectiveConductivityWPerMK,
        activationEnergyJPerMol, volumetricPreFactorWPerM3, boundaryTemperatureK, delta, deltaCrit, deltaRatio,
        criticalTemperatureK, criticalDimensionM, temperatureMarginK, dimensionMarginM, fkTemperatureScaleK, verdict,
        warnings);
  }

  /**
   * Evaluate the Frank-Kamenetskii criticality parameter at a given boundary temperature and body size.
   *
   * <p>
   * Evaluated in logarithmic form to avoid overflow of the Arrhenius term at low temperature.
   * </p>
   *
   * @param boundaryTemperature boundary temperature [K]; must be positive
   * @param dimension characteristic half-dimension [m]; must be positive
   * @return the dimensionless criticality parameter delta
   */
  private double criticalityParameter(double boundaryTemperature, double dimension) {
    double lnDelta = Math.log(activationEnergyJPerMol) + Math.log(volumetricPreFactorWPerM3) + 2.0 * Math.log(dimension)
        - Math.log(effectiveConductivityWPerMK) - Math.log(R_GAS) - 2.0 * Math.log(boundaryTemperature)
        - activationEnergyJPerMol / (R_GAS * boundaryTemperature);
    return Math.exp(lnDelta);
  }

  /**
   * Compute the critical half-dimension at which delta reaches its critical value for the current boundary temperature.
   *
   * @param boundaryTemperature boundary temperature [K]; must be positive
   * @param deltaCrit critical Frank-Kamenetskii parameter for the shape
   * @return critical half-dimension [m]
   */
  private double criticalDimension(double boundaryTemperature, double deltaCrit) {
    double lnRcrit = 0.5 * (Math.log(deltaCrit) + Math.log(effectiveConductivityWPerMK) + Math.log(R_GAS)
        + 2.0 * Math.log(boundaryTemperature) + activationEnergyJPerMol / (R_GAS * boundaryTemperature)
        - Math.log(activationEnergyJPerMol) - Math.log(volumetricPreFactorWPerM3));
    return Math.exp(lnRcrit);
  }

  /**
   * Solve for the boundary temperature at which delta equals its critical value for a fixed body size.
   *
   * <p>
   * Delta increases monotonically with temperature over the physically relevant range, so a simple bisection is robust.
   * </p>
   *
   * @param dimension characteristic half-dimension [m]; must be positive
   * @param deltaCrit critical Frank-Kamenetskii parameter for the shape
   * @return the critical boundary temperature [K], or {@link Double#NaN} if it cannot be bracketed
   */
  private double solveCriticalTemperature(double dimension, double deltaCrit) {
    double low = T_SEARCH_MIN_K;
    double high = T_SEARCH_MAX_K;
    double fLow = criticalityParameter(low, dimension) - deltaCrit;
    double fHigh = criticalityParameter(high, dimension) - deltaCrit;
    if (fLow > 0.0 || fHigh < 0.0) {
      return Double.NaN;
    }
    for (int i = 0; i < MAX_BISECTION_ITERATIONS && (high - low) > T_SEARCH_TOL_K; i++) {
      double mid = 0.5 * (low + high);
      if (criticalityParameter(mid, dimension) - deltaCrit < 0.0) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Validate that all mandatory inputs are present and physically meaningful.
   *
   * @throws IllegalStateException if any mandatory input is missing or invalid
   */
  private void validateInputs() {
    if (Double.isNaN(characteristicDimensionM) || characteristicDimensionM <= 0.0) {
      throw new IllegalStateException("Characteristic dimension must be set to a positive value");
    }
    if (Double.isNaN(effectiveConductivityWPerMK) || effectiveConductivityWPerMK <= 0.0) {
      throw new IllegalStateException("Effective thermal conductivity must be set to a positive value");
    }
    if (Double.isNaN(activationEnergyJPerMol) || activationEnergyJPerMol <= 0.0) {
      throw new IllegalStateException("Activation energy must be set to a positive value");
    }
    if (Double.isNaN(volumetricPreFactorWPerM3) || volumetricPreFactorWPerM3 <= 0.0) {
      throw new IllegalStateException(
          "Volumetric heat-release pre-factor must be set, either directly or via setKinetics");
    }
    if (Double.isNaN(boundaryTemperatureK) || boundaryTemperatureK <= 0.0) {
      throw new IllegalStateException("Boundary temperature must be set to a positive value");
    }
  }

  /**
   * Convert a length to metres.
   *
   * @param value length value; must be positive
   * @param unit length unit ("m", "cm", "mm" or "in")
   * @return the length in metres
   * @throws IllegalArgumentException if the value is not positive or the unit is unsupported
   */
  static double toMetres(double value, String unit) {
    if (!(value > 0.0)) {
      throw new IllegalArgumentException("Length must be positive");
    }
    if ("m".equalsIgnoreCase(unit)) {
      return value;
    } else if ("cm".equalsIgnoreCase(unit)) {
      return value * 0.01;
    } else if ("mm".equalsIgnoreCase(unit)) {
      return value * 0.001;
    } else if ("in".equalsIgnoreCase(unit)) {
      return value * 0.0254;
    }
    throw new IllegalArgumentException("Unsupported length unit: " + unit + " (use m, cm, mm or in)");
  }
}
