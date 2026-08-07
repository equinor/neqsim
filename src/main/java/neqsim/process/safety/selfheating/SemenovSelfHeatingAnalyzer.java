package neqsim.process.safety.selfheating;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Semenov criticality screening for self-heating controlled by surface cooling.
 *
 * <p>
 * Complements {@link PorousMediaSelfHeatingAnalyzer}. Where the Frank-Kamenetskii model assumes internal conduction
 * controls and the body develops a strong internal temperature profile (large Biot number), the Semenov model assumes
 * the opposite limit: the body is thermally thin, internal gradients are negligible, and the resistance to heat loss
 * lies entirely in the surface film. This is the appropriate limit for a drained pool of liquid, a small test sample,
 * or a thin wetted film on a metal surface.
 * </p>
 *
 * <p>
 * Criticality is reached when the Arrhenius heat-generation curve becomes tangent to the Newtonian cooling line, giving
 * the dimensionless group
 * </p>
 *
 * <p>
 * {@code psi = (E * V * P * exp(-E / (R * Ta))) / (h * S * R * Ta^2)}
 * </p>
 *
 * <p>
 * with the critical value {@code psi_crit = 1 / e = 0.3679}. At criticality the steady self-heating excess above the
 * surroundings is exactly the Semenov temperature rise {@code R * Ta^2 / E}, which for typical organic oxidation is
 * only some tens of kelvin — the reason self-heating is so easily missed by routine temperature monitoring.
 * </p>
 *
 * <p>
 * References: Semenov, <i>Zeitschrift fur Physik</i> 48 (1928); Bowes, <i>Self-Heating: Evaluating and Controlling the
 * Hazards</i>, 1984; Babrauskas, <i>Ignition Handbook</i>, 2003.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class SemenovSelfHeatingAnalyzer {
  private static final Logger logger = LogManager.getLogger(SemenovSelfHeatingAnalyzer.class);

  /** Universal gas constant [J/(mol K)]. */
  private static final double R_GAS = 8.314462618;

  /** Critical Semenov parameter, equal to 1/e. */
  public static final double PSI_CRIT = 1.0 / Math.E;

  /** Default ratio of psi to the critical psi above which a subcritical case is reported as marginal. */
  private static final double DEFAULT_MARGINAL_RATIO = 0.7;

  /** Lower bound of the critical-temperature bisection search [K]. */
  private static final double T_SEARCH_MIN_K = 150.0;

  /** Upper bound of the critical-temperature bisection search [K]. */
  private static final double T_SEARCH_MAX_K = 2000.0;

  /** Bisection convergence tolerance on temperature [K]. */
  private static final double T_SEARCH_TOL_K = 1.0e-4;

  /** Maximum number of bisection iterations. */
  private static final int MAX_BISECTION_ITERATIONS = 200;

  private double volumeM3 = Double.NaN;
  private double surfaceAreaM2 = Double.NaN;
  private double heatTransferCoefficientWPerM2K = Double.NaN;
  private double activationEnergyJPerMol = Double.NaN;
  private double volumetricPreFactorWPerM3 = Double.NaN;
  private double ambientTemperatureK = Double.NaN;
  private double marginalRatio = DEFAULT_MARGINAL_RATIO;

  /**
   * Create a Semenov self-heating analyzer with no inputs set.
   */
  public SemenovSelfHeatingAnalyzer() {
  }

  /**
   * Set the reacting volume and the heat-loss surface area of the body.
   *
   * @param volumeM3 reacting volume [m3]; must be positive
   * @param surfaceAreaM2 heat-loss surface area [m2]; must be positive
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if either argument is not positive
   */
  public SemenovSelfHeatingAnalyzer setBodySize(double volumeM3, double surfaceAreaM2) {
    if (!(volumeM3 > 0.0)) {
      throw new IllegalArgumentException("Volume must be positive");
    }
    if (!(surfaceAreaM2 > 0.0)) {
      throw new IllegalArgumentException("Surface area must be positive");
    }
    this.volumeM3 = volumeM3;
    this.surfaceAreaM2 = surfaceAreaM2;
    return this;
  }

  /**
   * Set the external heat-transfer coefficient governing surface cooling.
   *
   * @param wPerM2K heat-transfer coefficient [W/(m2 K)]; must be positive
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public SemenovSelfHeatingAnalyzer setHeatTransferCoefficient(double wPerM2K) {
    if (!(wPerM2K > 0.0)) {
      throw new IllegalArgumentException("Heat-transfer coefficient must be positive");
    }
    this.heatTransferCoefficientWPerM2K = wPerM2K;
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
  public SemenovSelfHeatingAnalyzer setActivationEnergy(double value, String unit) {
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
   * Set the volumetric heat-release pre-exponential factor {@code P = A * Q * rho}.
   *
   * @param wPerM3 volumetric heat-release pre-exponential factor [W/m3]; must be positive
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the value is not positive
   */
  public SemenovSelfHeatingAnalyzer setVolumetricHeatReleasePreFactor(double wPerM3) {
    if (!(wPerM3 > 0.0)) {
      throw new IllegalArgumentException("Volumetric heat-release pre-factor must be positive");
    }
    this.volumetricPreFactorWPerM3 = wPerM3;
    return this;
  }

  /**
   * Set the surrounding (ambient) temperature.
   *
   * @param value temperature value
   * @param unit temperature unit ("K" or "C")
   * @return this analyzer for chaining
   */
  public SemenovSelfHeatingAnalyzer setAmbientTemperature(double value, String unit) {
    this.ambientTemperatureK = new neqsim.util.unit.TemperatureUnit(value, unit).getValue("K");
    return this;
  }

  /**
   * Set the ratio of psi to the critical psi above which a subcritical case is reported as
   * {@link SelfHeatingVerdict#MARGINAL}.
   *
   * @param ratio marginal ratio, strictly between 0 and 1
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the ratio is outside the open interval (0, 1)
   */
  public SemenovSelfHeatingAnalyzer setMarginalRatio(double ratio) {
    if (!(ratio > 0.0) || !(ratio < 1.0)) {
      throw new IllegalArgumentException("Marginal ratio must be between 0 and 1 (exclusive)");
    }
    this.marginalRatio = ratio;
    return this;
  }

  /**
   * Run the Semenov criticality screening.
   *
   * @return an immutable screening result
   * @throws IllegalStateException if required inputs are missing or physically invalid
   */
  public SemenovSelfHeatingResult analyze() {
    validateInputs();
    List<String> warnings = new ArrayList<String>();

    double psi = semenovParameter(ambientTemperatureK);
    double psiRatio = psi / PSI_CRIT;
    double criticalTemperatureK = solveCriticalTemperature();
    double temperatureMarginK = Double.isNaN(criticalTemperatureK) ? Double.NaN
        : criticalTemperatureK - ambientTemperatureK;
    double criticalRiseK = R_GAS * ambientTemperatureK * ambientTemperatureK / activationEnergyJPerMol;

    SelfHeatingVerdict verdict;
    if (psiRatio > 1.0) {
      verdict = SelfHeatingVerdict.SELF_IGNITION;
    } else if (psiRatio >= marginalRatio) {
      verdict = SelfHeatingVerdict.MARGINAL;
    } else {
      verdict = SelfHeatingVerdict.SUBCRITICAL;
    }

    if (Double.isNaN(criticalTemperatureK)) {
      warnings.add("Critical ambient temperature could not be bracketed between " + T_SEARCH_MIN_K + " K and "
          + T_SEARCH_MAX_K + " K; check the kinetic parameters");
    }
    warnings.add("Semenov theory assumes a spatially uniform body temperature (small Biot number); use "
        + "PorousMediaSelfHeatingAnalyzer when internal conduction controls");

    logger.info("Semenov self-heating screening: psi={}, psiCrit={}, T_crit={} K, verdict={}", psi, PSI_CRIT,
        criticalTemperatureK, verdict);

    return new SemenovSelfHeatingResult(volumeM3, surfaceAreaM2, heatTransferCoefficientWPerM2K,
        activationEnergyJPerMol, volumetricPreFactorWPerM3, ambientTemperatureK, psi, PSI_CRIT, psiRatio,
        criticalTemperatureK, temperatureMarginK, criticalRiseK, verdict, warnings);
  }

  /**
   * Evaluate the Semenov criticality parameter at a given ambient temperature.
   *
   * <p>
   * Evaluated in logarithmic form to avoid overflow of the Arrhenius term at low temperature.
   * </p>
   *
   * @param ambientTemperature ambient temperature [K]; must be positive
   * @return the dimensionless Semenov parameter psi
   */
  private double semenovParameter(double ambientTemperature) {
    double lnPsi = Math.log(activationEnergyJPerMol) + Math.log(volumeM3) + Math.log(volumetricPreFactorWPerM3)
        - Math.log(heatTransferCoefficientWPerM2K) - Math.log(surfaceAreaM2) - Math.log(R_GAS)
        - 2.0 * Math.log(ambientTemperature) - activationEnergyJPerMol / (R_GAS * ambientTemperature);
    return Math.exp(lnPsi);
  }

  /**
   * Solve for the ambient temperature at which the Semenov parameter reaches its critical value.
   *
   * @return the critical ambient temperature [K], or {@link Double#NaN} if it cannot be bracketed
   */
  private double solveCriticalTemperature() {
    double low = T_SEARCH_MIN_K;
    double high = T_SEARCH_MAX_K;
    if (semenovParameter(low) - PSI_CRIT > 0.0 || semenovParameter(high) - PSI_CRIT < 0.0) {
      return Double.NaN;
    }
    for (int i = 0; i < MAX_BISECTION_ITERATIONS && (high - low) > T_SEARCH_TOL_K; i++) {
      double mid = 0.5 * (low + high);
      if (semenovParameter(mid) - PSI_CRIT < 0.0) {
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
    if (Double.isNaN(volumeM3) || volumeM3 <= 0.0 || Double.isNaN(surfaceAreaM2) || surfaceAreaM2 <= 0.0) {
      throw new IllegalStateException("Body volume and surface area must be set to positive values");
    }
    if (Double.isNaN(heatTransferCoefficientWPerM2K) || heatTransferCoefficientWPerM2K <= 0.0) {
      throw new IllegalStateException("Heat-transfer coefficient must be set to a positive value");
    }
    if (Double.isNaN(activationEnergyJPerMol) || activationEnergyJPerMol <= 0.0) {
      throw new IllegalStateException("Activation energy must be set to a positive value");
    }
    if (Double.isNaN(volumetricPreFactorWPerM3) || volumetricPreFactorWPerM3 <= 0.0) {
      throw new IllegalStateException("Volumetric heat-release pre-factor must be set to a positive value");
    }
    if (Double.isNaN(ambientTemperatureK) || ambientTemperatureK <= 0.0) {
      throw new IllegalStateException("Ambient temperature must be set to a positive value");
    }
  }
}
