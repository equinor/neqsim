package neqsim.pvtsimulation.util;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.annotation.AIExposable;

/**
 * Gas pseudopressure (real gas potential) calculator.
 *
 * <p>
 * Calculates the real gas pseudopressure integral used in gas well deliverability analysis:
 *
 * $$ m(P) = 2 \int_{P_{ref}}^{P} \frac{P'}{\mu(P') \cdot Z(P')} \, dP' $$
 *
 * where $\mu$ is the gas viscosity and $Z$ is the gas compressibility factor.
 *
 * <p>
 * <b>Default units:</b> All public methods use Kelvin and bara. The EOS-based path produces results in bara^2/cP. The
 * correlation-based path also uses bara/K and produces bara^2/cP.
 *
 * <p>
 * Two calculation modes are supported:
 * <ul>
 * <li><b>EOS-based</b>: Uses a NeqSim thermodynamic system to compute Z and viscosity at each pressure step via
 * rigorous equation of state. Most accurate for known compositions.</li>
 * <li><b>Correlation-based</b>: Uses empirical correlations for Z-factor and viscosity. Suitable when only gas specific
 * gravity is known.</li>
 * </ul>
 *
 * <p>
 * <b>Usage Example (EOS-based):</b>
 *
 * <pre>
 * SystemInterface gas = new SystemSrkEos(373.15, 200.0);
 * gas.addComponent("methane", 0.90);
 * gas.addComponent("ethane", 0.05);
 * gas.addComponent("propane", 0.03);
 * gas.addComponent("CO2", 0.02);
 * gas.setMixingRule("classic");
 *
 * GasPseudoPressure calc = new GasPseudoPressure(gas);
 * double mP = calc.calculate(200.0, 1.01325); // m(200 bara) - m(1.01325 bara)
 * </pre>
 *
 * <p>
 * <b>Usage Example (Correlation-based):</b>
 *
 * <pre>
 * // 200 bara, ref 1.01325 bara, 366.48 K (200 F), gammaG=0.65, MW=16.04
 * double mP = GasPseudoPressure.calculateFromCorrelation(200.0, 1.01325, 366.48, 0.65, 16.04);
 * </pre>
 *
 * @author ESOL
 * @version 2.0
 * @see BlackOilCorrelations
 */
public class GasPseudoPressure {

  /** Thermodynamic system used for EOS-based calculations. */
  private SystemInterface fluid;

  /** Number of integration steps (higher = more accurate). */
  private int numberOfSteps = 100;

  /** Conversion: Kelvin to Rankine. */
  private static final double K_TO_R = 9.0 / 5.0;
  /** Conversion: bara to psia. */
  private static final double BARA_TO_PSIA = 14.5038;

  /**
   * Construct a GasPseudoPressure calculator with a NeqSim thermodynamic system.
   *
   * @param fluid NeqSim thermodynamic system (must be a gas-phase system with components defined)
   */
  public GasPseudoPressure(SystemInterface fluid) {
    this.fluid = fluid.clone();
  }

  /**
   * Calculate the pseudopressure difference m(P) - m(Pref) using an EOS-based approach.
   *
   * @param pressure Upper pressure limit (bara)
   * @param referencePressure Lower pressure limit (bara)
   * @return Pseudopressure difference (bara^2/cP)
   */
  public double calculate(double pressure, double referencePressure) {
    return integrateEOS(referencePressure, pressure);
  }

  /**
   * Calculate the pseudopressure difference with specified pressure units.
   *
   * @param pressure Upper pressure limit
   * @param referencePressure Lower pressure limit
   * @param pressureUnit Pressure unit: "bara", "psia", "Pa", "MPa", "atm"
   * @return Pseudopressure difference in consistent units (pressure^2 / viscosity)
   */
  public double calculate(double pressure, double referencePressure, String pressureUnit) {
    double pBara = convertToBara(pressure, pressureUnit);
    double pRefBara = convertToBara(referencePressure, pressureUnit);
    double result = integrateEOS(pRefBara, pBara);

    // Convert result back to input-consistent units
    if ("psia".equalsIgnoreCase(pressureUnit)) {
      double factor = BARA_TO_PSIA * BARA_TO_PSIA; // (psia/bara)^2
      return result * factor;
    }
    return result;
  }

  /**
   * Calculate the pseudopressure at a single pressure (relative to atmospheric).
   *
   * @param pressure Pressure (bara)
   * @return Pseudopressure value (bara^2/cP)
   */
  public double pseudoPressureAt(double pressure) {
    double pRef = 1.01325; // bara (1 atm)
    return integrateEOS(pRef, pressure);
  }

  /**
   * Calculate pseudopressure profile over a pressure range.
   *
   * @param pressureMin Minimum pressure (bara)
   * @param pressureMax Maximum pressure (bara)
   * @param nPoints Number of pressure points
   * @return 2D array [pressures (bara), pseudopressures (bara^2/cP)]
   */
  public double[][] pseudoPressureProfile(double pressureMin, double pressureMax, int nPoints) {
    double[] pressures = new double[nPoints];
    double[] mP = new double[nPoints];
    double step = (pressureMax - pressureMin) / (nPoints - 1);

    for (int i = 0; i < nPoints; i++) {
      pressures[i] = pressureMin + i * step;
      mP[i] = integrateEOS(pressureMin, pressures[i]);
    }

    return new double[][] {pressures, mP};
  }

  /**
   * Calculate pseudopressure using correlation-based approach (no EOS).
   *
   * <p>
   * Uses the Lee-Gonzalez-Eakin viscosity correlation and Hall-Yarborough Z-factor. Suitable when only basic gas
   * properties are available (no compositional data).
   *
   * @param pressureBara Upper pressure (bara)
   * @param referencePressureBara Lower pressure (bara)
   * @param temperatureK Temperature (Kelvin)
   * @param gammaG Gas specific gravity (air = 1.0)
   * @param molecularWeight Gas molecular weight (g/mol, numerically equal to kg/kmol)
   * @return Pseudopressure difference (bara^2/cP)
   * @throws IllegalArgumentException if an input is non-finite, either absolute pressure or the molecular weight is not
   * positive, the Standing gas-gravity range is exceeded, or Hall-Yarborough reduced-property bounds are exceeded
   */
  @AIExposable(description = "Calculate signed gas pseudopressure with Standing, Hall-Yarborough, and Lee correlations", category = "reservoir", safe = true)
  public static double calculateFromCorrelation(double pressureBara, double referencePressureBara, double temperatureK,
      double gammaG, double molecularWeight) {
    validateCorrelationInputs(pressureBara, referencePressureBara, temperatureK, gammaG, molecularWeight);
    double result = integrateCorrelation(referencePressureBara, pressureBara, temperatureK, gammaG, molecularWeight,
        200);
    if (!Double.isFinite(result)) {
      throw new IllegalArgumentException("Correlation pseudopressure result must be finite");
    }
    return result;
  }

  /**
   * Calculate delta pseudopressure between two pressures using correlations.
   *
   * @param p1Bara First pressure (bara)
   * @param p2Bara Second pressure (bara)
   * @param temperatureK Temperature (Kelvin)
   * @param gammaG Gas specific gravity (air = 1.0)
   * @param molecularWeight Gas molecular weight (g/mol, numerically equal to kg/kmol)
   * @return |m(p1) - m(p2)| in bara^2/cP
   * @throws IllegalArgumentException if an input is non-finite, either absolute pressure or the molecular weight is not
   * positive, the Standing gas-gravity range is exceeded, or Hall-Yarborough reduced-property bounds are exceeded
   */
  @AIExposable(description = "Calculate absolute gas pseudopressure difference with empirical correlations", category = "reservoir", safe = true)
  public static double deltaPseudoPressure(double p1Bara, double p2Bara, double temperatureK, double gammaG,
      double molecularWeight) {
    return Math.abs(calculateFromCorrelation(p1Bara, p2Bara, temperatureK, gammaG, molecularWeight));
  }

  // ==================== INTERNAL INTEGRATION METHODS ====================

  /**
   * Integrate pseudopressure using EOS-based Z and viscosity (Simpson's rule).
   *
   * @param pLow Lower pressure (bara)
   * @param pHigh Upper pressure (bara)
   * @return Pseudopressure integral (bara^2/cP)
   */
  private double integrateEOS(double pLow, double pHigh) {
    if (Math.abs(pHigh - pLow) < 1e-10) {
      return 0.0;
    }

    int n = numberOfSteps;
    if (n % 2 != 0) {
      n++;
    }

    double h = (pHigh - pLow) / n;
    double sum = evaluateIntegrandEOS(pLow) + evaluateIntegrandEOS(pHigh);

    for (int i = 1; i < n; i++) {
      double p = pLow + i * h;
      double f = evaluateIntegrandEOS(p);
      if (i % 2 == 0) {
        sum += 2.0 * f;
      } else {
        sum += 4.0 * f;
      }
    }

    return (h / 3.0) * sum;
  }

  /**
   * Evaluate the pseudopressure integrand 2P/(mu*Z) at a given pressure using EOS.
   *
   * @param pBara Pressure in bara
   * @return Integrand value: 2 * P / (mu * Z) in bara/cP
   */
  private double evaluateIntegrandEOS(double pBara) {
    try {
      SystemInterface tempFluid = fluid.clone();
      tempFluid.setPressure(pBara, "bara");
      ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
      ops.TPflash();
      tempFluid.initProperties();

      double z = tempFluid.getPhase(0).getZ();
      double muPaS = tempFluid.getPhase(0).getViscosity();
      double muCP = muPaS * 1000.0;

      if (z <= 0.0 || muCP <= 0.0) {
        return 0.0;
      }

      return 2.0 * pBara / (muCP * z);
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Integrate pseudopressure using correlation-based Z and viscosity.
   *
   * @param pLow Lower pressure (bara)
   * @param pHigh Upper pressure (bara)
   * @param temperatureK Temperature (Kelvin)
   * @param gammaG Gas specific gravity
   * @param mw Gas molecular weight
   * @param nSteps Number of integration steps
   * @return Pseudopressure integral (bara^2/cP)
   */
  private static double integrateCorrelation(double pLow, double pHigh, double temperatureK, double gammaG, double mw,
      int nSteps) {
    if (Math.abs(pHigh - pLow) < 1e-10) {
      return 0.0;
    }

    int n = nSteps;
    if (n % 2 != 0) {
      n++;
    }

    double h = (pHigh - pLow) / n;
    double sum = evaluateIntegrandCorrelation(pLow, temperatureK, gammaG, mw)
        + evaluateIntegrandCorrelation(pHigh, temperatureK, gammaG, mw);

    for (int i = 1; i < n; i++) {
      double p = pLow + i * h;
      double f = evaluateIntegrandCorrelation(p, temperatureK, gammaG, mw);
      if (i % 2 == 0) {
        sum += 2.0 * f;
      } else {
        sum += 4.0 * f;
      }
    }

    return (h / 3.0) * sum;
  }

  /**
   * Evaluate the pseudopressure integrand using correlations.
   *
   * <p>
   * Internally converts K to Rankine and bara to psia for correlation coefficients, then returns the integrand in
   * bara/cP so the integral result is in bara^2/cP.
   *
   * @param pBara Pressure (bara)
   * @param temperatureK Temperature (Kelvin)
   * @param gammaG Gas specific gravity
   * @param mw Gas molecular weight
   * @return Integrand value: 2P/(mu*Z) in bara/cP
   */
  private static double evaluateIntegrandCorrelation(double pBara, double temperatureK, double gammaG, double mw) {
    double tRankine = temperatureK * K_TO_R;
    double pPsia = pBara * BARA_TO_PSIA;

    // Use the canonical Standing implementation for pseudocritical properties.
    double tPcK = GasPseudoCriticalProperties.pseudoCriticalTemperatureStanding(gammaG);
    double pPcBara = GasPseudoCriticalProperties.pseudoCriticalPressureStanding(gammaG);

    // Reduced properties (dimensionless)
    double tPR = temperatureK / tPcK;
    double pPR = pBara / pPcBara;

    // Z-factor using Hall-Yarborough method
    double z = hallYarboroughZ(tPR, pPR);

    // Gas density for viscosity (lb/ft3)
    double rhoG = pPsia * mw / (z * 10.7316 * tRankine);

    // Gas viscosity using Lee-Gonzalez-Eakin (expects Rankine, lb/ft3)
    double mu = BlackOilCorrelations.gasViscosityLeeGonzalezEakin(tRankine, rhoG, mw);

    if (!Double.isFinite(mu) || mu <= 0.0) {
      throw new IllegalArgumentException("Lee-Gonzalez-Eakin gas viscosity must be finite and positive");
    }

    // Return integrand in bara/cP (use pBara, not pPsia)
    return 2.0 * pBara / (mu * z);
  }

  /**
   * Hall-Yarborough Z-factor correlation (1973).
   *
   * <p>
   * Solves the Hall-Yarborough equation implicitly using Newton-Raphson iteration. Valid for Tpr &gt; 1.0 and Ppr &lt;
   * 25.
   *
   * @param tPR Pseudoreduced temperature (T/Tc)
   * @param pPR Pseudoreduced pressure (P/Pc)
   * @return Gas compressibility factor Z
   */
  static double hallYarboroughZ(double tPR, double pPR) {
    if (!Double.isFinite(tPR) || !Double.isFinite(pPR) || tPR <= 1.0 || pPR < 0.0 || pPR >= 25.0) {
      throw new IllegalArgumentException(
          "Hall-Yarborough requires finite reduced properties with Tpr > 1.0 and 0 <= Ppr < 25.0");
    }
    if (pPR < 1e-10) {
      return 1.0;
    }

    double t = 1.0 / tPR;
    double a = -0.06125 * t * Math.exp(-1.2 * (1.0 - t) * (1.0 - t));
    double b = 14.76 * t - 9.76 * t * t + 4.58 * t * t * t;
    double c = 90.7 * t - 242.2 * t * t + 42.4 * t * t * t;
    double d = 2.18 + 2.82 * t;

    double y = 0.001;
    boolean converged = false;
    for (int iter = 0; iter < 100; iter++) {
      double fy = a * pPR + (y + y * y + y * y * y - y * y * y * y) / Math.pow(1.0 - y, 3) - b * y * y
          + c * Math.pow(y, d);
      double dfy = (1.0 + 4.0 * y + 4.0 * y * y - 4.0 * y * y * y + y * y * y * y) / Math.pow(1.0 - y, 4) - 2.0 * b * y
          + c * d * Math.pow(y, d - 1.0);

      if (Math.abs(dfy) < 1e-30) {
        break;
      }

      double yNew = y - fy / dfy;
      if (yNew < 0.0) {
        yNew = y / 2.0;
      }
      if (yNew > 1.0) {
        yNew = (y + 1.0) / 2.0;
      }

      if (Math.abs(yNew - y) < 1e-12) {
        y = yNew;
        converged = true;
        break;
      }
      y = yNew;
    }

    if (!converged) {
      throw new IllegalArgumentException("Hall-Yarborough correlation did not converge");
    }
    double z = -a * pPR / y;
    if (!Double.isFinite(z) || z <= 0.0) {
      throw new IllegalArgumentException("Hall-Yarborough compressibility factor must be finite and positive");
    }
    return z;
  }

  /**
   * Validate the public correlation contract against its constituent correlation bounds.
   *
   * @param pressureBara first absolute pressure (bara)
   * @param referencePressureBara second absolute pressure (bara)
   * @param temperatureK absolute temperature (K)
   * @param gammaG gas specific gravity relative to air
   * @param molecularWeight gas molecular weight (g/mol)
   * @throws IllegalArgumentException if an input or reduced property is outside the supported domain
   */
  private static void validateCorrelationInputs(double pressureBara, double referencePressureBara, double temperatureK,
      double gammaG, double molecularWeight) {
    if (!Double.isFinite(pressureBara) || pressureBara <= 0.0 || !Double.isFinite(referencePressureBara)
        || referencePressureBara <= 0.0) {
      throw new IllegalArgumentException("Absolute pressures must be finite and positive in bara");
    }
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("Temperature must be finite and positive in Kelvin");
    }
    if (!Double.isFinite(molecularWeight) || molecularWeight <= 0.0) {
      throw new IllegalArgumentException("Molecular weight must be finite and positive");
    }

    double tPcK = GasPseudoCriticalProperties.pseudoCriticalTemperatureStanding(gammaG);
    double pPcBara = GasPseudoCriticalProperties.pseudoCriticalPressureStanding(gammaG);
    double tPR = temperatureK / tPcK;
    double maxPPR = Math.max(pressureBara, referencePressureBara) / pPcBara;
    if (tPR <= 1.0 || maxPPR >= 25.0) {
      throw new IllegalArgumentException(
          "Correlation requires Hall-Yarborough reduced properties with Tpr > 1.0 and max Ppr < 25.0");
    }
  }

  // ==================== UTILITY ====================

  /**
   * Convert pressure to bara.
   *
   * @param pressure Pressure value
   * @param unit Pressure unit string
   * @return Pressure in bara
   */
  private static double convertToBara(double pressure, String unit) {
    if (unit == null || "bara".equalsIgnoreCase(unit)) {
      return pressure;
    }
    if ("psia".equalsIgnoreCase(unit)) {
      return pressure / BARA_TO_PSIA;
    }
    if ("Pa".equalsIgnoreCase(unit)) {
      return pressure / 1e5;
    }
    if ("MPa".equalsIgnoreCase(unit)) {
      return pressure * 10.0;
    }
    if ("atm".equalsIgnoreCase(unit)) {
      return pressure * 1.01325;
    }
    throw new IllegalArgumentException("Unknown pressure unit: " + unit);
  }

  /**
   * Get the number of integration steps.
   *
   * @return Number of integration steps
   */
  public int getNumberOfSteps() {
    return numberOfSteps;
  }

  /**
   * Set the number of integration steps (higher = more accurate but slower).
   *
   * @param numberOfSteps Number of steps (must be at least 10)
   */
  public void setNumberOfSteps(int numberOfSteps) {
    if (numberOfSteps < 10) {
      throw new IllegalArgumentException("Number of steps must be at least 10");
    }
    this.numberOfSteps = numberOfSteps;
  }
}
