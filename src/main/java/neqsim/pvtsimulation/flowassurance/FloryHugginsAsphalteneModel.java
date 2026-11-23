package neqsim.pvtsimulation.flowassurance;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Flory-Huggins regular solution model for asphaltene precipitation prediction.
 *
 * <p>
 * This class implements the Flory-Huggins polymer solution theory applied to asphaltene precipitation, a widely-used
 * approach in the petroleum industry. The model treats the oil as a solvent and asphaltenes as a dissolved polymer,
 * using the mismatch in solubility parameters to predict precipitation.
 * </p>
 *
 * <h2>Theoretical Background</h2>
 * <p>
 * The chemical potential of asphaltene in oil is expressed as:
 * </p>
 *
 * <pre>
 * ln(x_a * gamma_a) = ln(phi_a) + (1 - V_a / V_L) * (1 - phi_a) + (V_a / RT) * (delta_a - delta_L) ^ 2 * (1 - phi_a)
 *     ^ 2
 * </pre>
 *
 * <p>
 * Where:
 * </p>
 * <ul>
 * <li>x_a = mole fraction of asphaltene</li>
 * <li>phi_a = volume fraction of asphaltene</li>
 * <li>V_a, V_L = molar volumes of asphaltene and liquid (cm3/mol)</li>
 * <li>delta_a, delta_L = solubility parameters of asphaltene and liquid (MPa^0.5)</li>
 * <li>R = gas constant, T = temperature (K)</li>
 * </ul>
 *
 * <p>
 * Onset occurs when delta_L drops below a critical value (typically when light ends evolve near bubble point).
 * </p>
 *
 * <h2>Key Correlations</h2>
 * <ul>
 * <li>Oil solubility parameter from density: delta_L = A * rho + B. The defaults A = 17.347e-3 MPa^0.5/(kg/m3) and B =
 * 2.904 MPa^0.5 follow from the refractive-index relation delta = 52.042 * F_RI + 2.904 (Wang and Buckley, 2001)
 * combined with the one-third rule F_RI / rho = 1/3 cm3/g (Vargas and Chapman, 2010).</li>
 * <li>Asphaltene solubility parameter: 19-24 MPa^0.5 (Barton, 1991), optionally linear in temperature: delta_a(T) =
 * delta_a(T_ref) + c * (T - T_ref).</li>
 * <li>Onset condition: the dilute Flory-Huggins solubility limit equals the asphaltene content of the oil.</li>
 * </ul>
 *
 * <h2>Predictive and calibrated use</h2>
 * <ul>
 * <li><b>Predictive (no onset data):</b> {@link #configureFromAPIGravity(double)},
 * {@link #configureFromSARA(double, double, double, double)} and optionally {@link #calibrateCorrelation(double)}. This
 * is a screening estimate.</li>
 * <li><b>Calibrated (measured onset pressures):</b> {@link #calibrateToOnsetPressure(double, double)} for one point or
 * {@link #fitToOnsetData(double[], double[])} for several temperatures. The asphaltene solubility parameter and its
 * temperature coefficient are regressed so that the model reproduces the measurements, and the calibrated model is then
 * used for other pressures, temperatures and compositions (for example gas injection).</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Hirschberg, A. et al. (1984). SPE J., 24(3), 283-293.</li>
 * <li>Flory, P.J. (1953). Principles of Polymer Chemistry. Cornell Univ. Press.</li>
 * <li>Huggins, M.L. (1941). J. Chem. Phys., 9, 440.</li>
 * <li>Wang, J.X., Buckley, J.S. (2001). Energy and Fuels, 15(5), 1004-1012.</li>
 * <li>Vargas, F.M., Chapman, W.G. (2010). Fluid Phase Equilibria, 290, 103-108.</li>
 * <li>Andersen, S.I., Speight, J.G. (1999). J. Petrol. Sci. Eng., 22, 53-66.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.1
 */
public class FloryHugginsAsphalteneModel {

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(FloryHugginsAsphalteneModel.class);

  /** Gas constant in J/(mol K). */
  private static final double R_GAS = 8.314;

  /** Molar volume of asphaltene (cm3/mol). Default for MW=750, rho=1100 kg/m3. */
  private double asphaltMolarVolume = 682.0;

  /** Solubility parameter of asphaltene (MPa^0.5). Typical range: 19-24. */
  private double asphalteneSolubilityParameter = 21.0;

  /** Molecular weight of asphaltene (g/mol). */
  private double asphalteneMW = 750.0;

  /** Density of asphaltene at standard conditions (kg/m3). */
  private double asphalteneDensity = 1100.0;

  /** Weight fraction of asphaltene in the oil. */
  private double asphalteneWeightFraction = 0.05;

  /**
   * Coefficient A in delta_L = A * rho + B correlation. Default 17.347e-3 MPa^0.5/(kg/m3) (Wang and Buckley, 2001, with
   * the one-third rule).
   */
  private double deltaRhoCoeffA = 17.347e-3;

  /**
   * Coefficient B in delta_L = A * rho + B correlation. Default 2.904 MPa^0.5 (Wang and Buckley, 2001).
   */
  private double deltaRhoCoeffB = 2.904;

  /** Pressure search step size for onset detection (bar). */
  private double pressureSearchStep = 2.0;

  /** Whether asphaltene properties were configured from API gravity. */
  private boolean configuredFromAPI = false;

  /** Reference to the thermodynamic system. */
  private SystemInterface system;

  /** Reservoir temperature (K). */
  private double reservoirTemperature;

  /** Reference temperature (K) at which {@link #asphalteneSolubilityParameter} applies. */
  private double solubilityParameterReferenceTemperature = 298.15;

  /** Temperature coefficient of the asphaltene solubility parameter (MPa^0.5/K). Zero means constant. */
  private double solubilityParameterTemperatureCoefficient = 0.0;

  /** Asphaltene solubility parameter before the SARA correction was applied, NaN when no correction is active. */
  private double solubilityParameterBeforeSara = Double.NaN;

  /** Precipitated weight fraction that defines the onset (detection limit of the onset measurement). */
  private double onsetDetectionThreshold = 0.001;

  /** Whether the asphaltene solubility parameter was regressed to measured onset data. */
  private boolean calibratedToOnsetData = false;

  /**
   * Default constructor.
   */
  public FloryHugginsAsphalteneModel() {
  }

  /**
   * Constructor with thermodynamic system.
   *
   * @param system the thermodynamic system
   * @param reservoirTemperature reservoir temperature (K)
   */
  public FloryHugginsAsphalteneModel(SystemInterface system, double reservoirTemperature) {
    this.system = system.clone();
    this.reservoirTemperature = reservoirTemperature;
  }

  /**
   * Configures asphaltene properties based on API gravity.
   *
   * <p>
   * Uses correlations from Akbarzadeh et al. (2005) and Andersen and Speight (1999) to estimate asphaltene molecular
   * weight, density, and solubility parameter from API gravity. This is critical for matching diverse oil types as
   * asphaltene properties vary significantly across the API range.
   * </p>
   *
   * <ul>
   * <li>Light oils (API &gt; 35): Lower MW (500-700), higher delta (21-23 MPa^0.5)</li>
   * <li>Medium oils (25-35): Medium MW (700-1200), moderate delta (20-21 MPa^0.5)</li>
   * <li>Heavy oils (API &lt; 25): Higher MW (1200-2500), lower delta (19-20 MPa^0.5)</li>
   * </ul>
   *
   * @param apiGravity API gravity of the crude oil
   */
  public void configureFromAPIGravity(double apiGravity) {
    // Continuous correlations for asphaltene properties vs API gravity.
    // Based on Akbarzadeh et al. (2005), Yarranton et al. (2000), and
    // Andersen & Speight (1999).
    //
    // MW: exponential decrease with API gravity. Heavier oils have more
    // polydisperse, higher-MW asphaltene aggregates.
    // Fitted to: API=20->2000, API=35->750, API=40->550
    asphalteneMW = Math.max(400.0, Math.min(3000.0, 7270.0 * Math.exp(-0.0645 * apiGravity)));

    // Solubility parameter: increases with API gravity. Light paraffinic oils
    // have poor solvency for asphaltenes, so the effective delta_a is higher
    // (greater mismatch with the low-delta oil). This matches the well-known
    // observation that asphaltene problems are worst in light undersaturated oils.
    asphalteneSolubilityParameter = Math.max(19.5, Math.min(23.0, 19.0 + 0.06 * apiGravity));

    // Asphaltene solid density varies minimally with oil type (1050-1200 kg/m3).
    asphalteneDensity = 1100.0;

    // Recalculate molar volume from MW and density
    asphaltMolarVolume = (asphalteneMW / asphalteneDensity) * 1000.0; // cm3/mol

    solubilityParameterBeforeSara = Double.NaN;
    calibratedToOnsetData = false;
    configuredFromAPI = true;

    logger.info("Configured FH asphaltene for API={}: MW={}, delta={}, Vm={}", apiGravity, asphalteneMW,
        asphalteneSolubilityParameter, asphaltMolarVolume);
  }

  /**
   * Calibrates the delta_L correlation coefficient A to the current thermodynamic system.
   *
   * <p>
   * The default Lian et al. (1994) correlation (A=0.017347) systematically underestimates the solubility parameter of
   * live oils at reservoir conditions compared to EOS-computed values (Hirschberg, 1984). This method performs a TP
   * flash to determine the actual oil density and molar volume, then calibrates A using the Flory-Huggins critical
   * condition so that:
   * </p>
   * <ul>
   * <li>The oil is stable at reservoir pressure (chi &lt; chi_critical)</li>
   * <li>Onset is detected when density drops slightly (typically 50-100 bar above Pbub)</li>
   * </ul>
   *
   * <p>
   * The critical chi is computed from the Flory-Huggins phase separation criterion: chi_c = r * (1 + 1/sqrt(r))^2 / 2,
   * where r = V_asphaltene / V_liquid. A stability buffer (default 0.35 MPa^0.5) keeps the system just below the
   * critical threshold at reservoir conditions.
   * </p>
   *
   * @param temperature reservoir temperature (K). Used for flash and chi calculation.
   */
  public void calibrateCorrelation(double temperature) {
    if (system == null) {
      logger.warn("Cannot calibrate FH correlation: no thermodynamic system set");
      return;
    }

    SystemInterface work = system.clone();
    work.setTemperature(temperature);

    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    try {
      ops.TPflash();
      work.initProperties();
    } catch (Exception e) {
      logger.error("Flash failed during FH calibration: {}", e.getMessage());
      return;
    }

    double oilDensity;
    double avgMW;
    if (work.hasPhaseType("oil")) {
      oilDensity = work.getPhase("oil").getDensity("kg/m3");
      avgMW = work.getPhase("oil").getMolarMass() * 1000.0; // kg/mol to g/mol
    } else if (work.getNumberOfPhases() > 0) {
      oilDensity = work.getPhase(0).getDensity("kg/m3");
      avgMW = work.getPhase(0).getMolarMass() * 1000.0;
    } else {
      logger.warn("No liquid phase found during calibration");
      return;
    }

    if (oilDensity < 300.0 || oilDensity > 1200.0) {
      logger.warn("Oil density {} kg/m3 outside valid range for calibration", oilDensity);
      return;
    }

    // Liquid molar volume (cm3/mol) from density and average MW
    double liquidMolarVolume = avgMW / (oilDensity / 1000.0);
    double molarVolumeRatio = asphaltMolarVolume / liquidMolarVolume;

    // FH critical condition: chi_c = r * (1 + 1/sqrt(r))^2 / 2
    double sqrtR = Math.sqrt(molarVolumeRatio);
    double criticalChi = molarVolumeRatio * Math.pow(1.0 + 1.0 / sqrtR, 2) / 2.0;

    // Convert to critical solubility parameter gap (MPa^0.5)
    double vaMeter3 = asphaltMolarVolume * 1e-6;
    double criticalDeltaGap = Math.sqrt(criticalChi * R_GAS * temperature / vaMeter3) / 1e3;

    // Set A so that at the current density, the gap is slightly below the critical value.
    // The stabilityBuffer ensures the oil is stable at P_res but onset occurs with
    // a modest density decrease (~2-5% drop, i.e. 50-100 bar above bubble point).
    double stabilityBuffer = 0.35; // MPa^0.5
    double targetGap = criticalDeltaGap - stabilityBuffer;
    if (targetGap < 1.0) {
      targetGap = 1.0; // minimum gap for numerical stability
    }

    double calibratedA = (getAsphalteneSolubilityParameter(temperature) - targetGap - deltaRhoCoeffB) / oilDensity;

    // Sanity check: A must be positive and within physically meaningful range
    if (calibratedA > 0.005 && calibratedA < 0.10) {
      deltaRhoCoeffA = calibratedA;
      logger.info("FH calibrated: A={}, oilDensity={}, criticalGap={}, targetGap={}", deltaRhoCoeffA, oilDensity,
          criticalDeltaGap, targetGap);
    } else {
      logger.warn("Calibrated A={} outside valid range [0.005, 0.10], keeping default", calibratedA);
    }
  }

  /**
   * Configures asphaltene properties from SARA fractions.
   *
   * <p>
   * Uses the asphaltene content and resin/asphaltene ratio to refine the solubility parameter. Higher R/A ratios
   * indicate better peptization, which effectively lowers the apparent asphaltene solubility parameter (they act more
   * dissolved).
   * </p>
   *
   * @param saturates weight fraction of saturates
   * @param aromatics weight fraction of aromatics
   * @param resins weight fraction of resins
   * @param asphaltenes weight fraction of asphaltenes
   */
  public void configureFromSARA(double saturates, double aromatics, double resins, double asphaltenes) {
    this.asphalteneWeightFraction = asphaltenes;

    // The correction is always applied to the uncorrected value, so repeated calls give the same result.
    if (Double.isNaN(solubilityParameterBeforeSara)) {
      solubilityParameterBeforeSara = asphalteneSolubilityParameter;
    }
    double factor = 1.0;

    // Resin-to-asphaltene ratio affects stability
    double raRatio = (asphaltenes > 0.001) ? resins / asphaltenes : 3.0;
    if (raRatio > 5.0) {
      factor *= 0.97;
    } else if (raRatio > 3.0) {
      factor *= 0.98;
    } else if (raRatio < 1.0) {
      factor *= 1.02;
    }

    // High saturate content reduces the solvency of the maltenes
    double satFrac = saturates / (saturates + aromatics + 1e-10);
    if (satFrac > 0.75) {
      factor *= 1.01;
    }
    asphalteneSolubilityParameter = solubilityParameterBeforeSara * factor;
  }

  /**
   * Sets the pressure search step size for onset detection.
   *
   * @param step pressure step in bar (default 2.0)
   */
  public void setPressureSearchStep(double step) {
    this.pressureSearchStep = step;
  }

  /**
   * Checks whether properties were configured from API gravity.
   *
   * @return true if configureFromAPIGravity was called
   */
  public boolean isConfiguredFromAPI() {
    return configuredFromAPI;
  }

  /**
   * Calculates the solubility parameter of the liquid phase from density.
   *
   * <p>
   * Uses the empirical correlation: delta_L = A * rho + B, where rho is in kg/m3 and delta_L is in MPa^0.5.
   * </p>
   *
   * @param liquidDensity liquid phase density (kg/m3)
   * @return solubility parameter (MPa^0.5)
   */
  public double calculateLiquidSolubilityParameter(double liquidDensity) {
    return deltaRhoCoeffA * liquidDensity + deltaRhoCoeffB;
  }

  /**
   * Calculates the Flory-Huggins interaction parameter (chi).
   *
   * <p>
   * chi = V_a / (RT) * (delta_a(T) - delta_L)^2, with delta_a(T) from
   * {@link #getAsphalteneSolubilityParameter(double)}.
   * </p>
   *
   * @param deltaLiquid solubility parameter of liquid phase (MPa^0.5)
   * @param temperature temperature (K)
   * @return Flory-Huggins chi parameter (dimensionless)
   */
  public double calculateChiParameter(double deltaLiquid, double temperature) {
    double deltaDiff = getAsphalteneSolubilityParameter(temperature) - deltaLiquid;
    // Convert molar volume from cm3/mol to m3/mol and delta from MPa^0.5 to Pa^0.5
    double vaM3 = asphaltMolarVolume * 1e-6;
    double deltaDiffPa = deltaDiff * 1e3; // MPa^0.5 to Pa^0.5 (1 MPa = 1e6 Pa, sqrt = 1e3)
    return vaM3 * deltaDiffPa * deltaDiffPa / (R_GAS * temperature);
  }

  /**
   * Calculates the maximum volume fraction of dissolved asphaltene at equilibrium.
   *
   * <p>
   * From the Flory-Huggins equation for phase equilibrium:
   * </p>
   *
   * <pre>
   * ln(phi_a) + (1 - V_a/V_L) * (1 - phi_a) + chi * (1 - phi_a)^2 = 0
   * </pre>
   *
   * <p>
   * The equation always has the trivial root phi_a = 1 (pure asphaltene). The solubility limit is the smallest root,
   * which is bracketed on a logarithmic grid and refined by bisection. If there is no root below one the asphaltene is
   * completely miscible and 1.0 is returned.
   * </p>
   *
   * @param deltaLiquid solubility parameter of liquid phase (MPa^0.5)
   * @param liquidMolarVolume molar volume of liquid phase (cm3/mol)
   * @param temperature temperature (K)
   * @return maximum dissolved asphaltene volume fraction
   */
  public double calculateMaxDissolvedFraction(double deltaLiquid, double liquidMolarVolume, double temperature) {
    double chi = calculateChiParameter(deltaLiquid, temperature);
    double molarVolumeRatio = asphaltMolarVolume / liquidMolarVolume;

    return solveDiluteRoot(chi, molarVolumeRatio);
  }

  /**
   * Residual of the Flory-Huggins solubility equation for a pure asphaltene phase.
   *
   * @param phiA asphaltene volume fraction in the oil
   * @param chi Flory-Huggins interaction parameter
   * @param molarVolumeRatio asphaltene molar volume divided by liquid molar volume
   * @return residual, negative below the solubility limit
   */
  private static double solubilityResidual(double phiA, double chi, double molarVolumeRatio) {
    double oneMinus = 1.0 - phiA;
    return Math.log(phiA) + (1.0 - molarVolumeRatio) * oneMinus + chi * oneMinus * oneMinus;
  }

  /**
   * Finds the smallest root of the Flory-Huggins solubility equation.
   *
   * @param chi Flory-Huggins interaction parameter
   * @param molarVolumeRatio asphaltene molar volume divided by liquid molar volume
   * @return dissolved asphaltene volume fraction at saturation, or 1.0 when completely miscible
   */
  private static double solveDiluteRoot(double chi, double molarVolumeRatio) {
    double upperLimit = 1.0 - 1.0e-6;
    double low = 1.0e-300;
    if (solubilityResidual(low, chi, molarVolumeRatio) >= 0.0) {
      return low;
    }
    double high = low;
    boolean bracketed = false;
    // 0.05 decade steps resolve the root before the residual can turn back
    for (double exponent = -299.95; exponent < 0.0; exponent += 0.05) {
      high = Math.min(Math.pow(10.0, exponent), upperLimit);
      if (solubilityResidual(high, chi, molarVolumeRatio) >= 0.0) {
        bracketed = true;
        break;
      }
      low = high;
      if (high >= upperLimit) {
        break;
      }
    }
    if (!bracketed) {
      return 1.0;
    }
    for (int iter = 0; iter < 100; iter++) {
      double mid = Math.sqrt(low * high);
      if (solubilityResidual(mid, chi, molarVolumeRatio) >= 0.0) {
        high = mid;
      } else {
        low = mid;
      }
      if (high / low - 1.0 < 1.0e-12) {
        break;
      }
    }
    return Math.sqrt(low * high);
  }

  /**
   * Flashes the fluid and returns the properties of the oil phase needed by the model.
   *
   * @param pressure pressure (bara)
   * @param temperature temperature (K)
   * @return array with oil density (kg/m3) and oil molar volume (cm3/mol), or null when the flash failed or no phase
   * exists
   */
  private double[] calculateOilState(double pressure, double temperature) {
    SystemInterface workSystem = system.clone();
    workSystem.setPressure(pressure);
    workSystem.setTemperature(temperature);
    ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
    try {
      ops.TPflash();
      workSystem.initProperties();
    } catch (Exception e) {
      logger.error("Flash failed at P={}, T={}: {}", pressure, temperature, e.getMessage());
      return null;
    }
    if (workSystem.getNumberOfPhases() < 1) {
      return null;
    }
    int phaseNumber = workSystem.hasPhaseType("oil") ? workSystem.getPhaseNumberOfPhase("oil") : 0;
    double oilDensity = workSystem.getPhase(phaseNumber).getDensity("kg/m3");
    double molarMass = workSystem.getPhase(phaseNumber).getMolarMass() * 1000.0; // kg/mol to g/mol
    if (!(oilDensity > 0.0) || !(molarMass > 0.0)) {
      return null;
    }
    return new double[] {oilDensity, molarMass / (oilDensity / 1000.0)};
  }

  /**
   * Converts an asphaltene volume fraction in the oil to a weight fraction.
   *
   * @param volumeFraction asphaltene volume fraction
   * @param oilDensity oil density (kg/m3)
   * @return asphaltene weight fraction
   */
  private double volumeToWeightFraction(double volumeFraction, double oilDensity) {
    return volumeFraction * asphalteneDensity
        / (volumeFraction * asphalteneDensity + (1.0 - volumeFraction) * oilDensity);
  }

  /**
   * Calculates weight fraction of precipitated asphaltene at given conditions.
   *
   * @param pressure pressure (bara)
   * @param temperature temperature (K)
   * @return weight fraction of precipitated asphaltene (0 = none, 1 = all)
   */
  public double calculatePrecipitatedFraction(double pressure, double temperature) {
    if (system == null) {
      logger.error("Thermodynamic system not set");
      return Double.NaN;
    }
    double[] oilState = calculateOilState(pressure, temperature);
    if (oilState == null) {
      return Double.NaN;
    }
    double deltaL = calculateLiquidSolubilityParameter(oilState[0]);
    double maxDissolved = calculateMaxDissolvedFraction(deltaL, oilState[1], temperature);
    double maxDissolvedMass = volumeToWeightFraction(maxDissolved, oilState[0]);
    if (asphalteneWeightFraction > maxDissolvedMass) {
      return asphalteneWeightFraction - maxDissolvedMass;
    }
    return 0.0;
  }

  /**
   * Checks whether the precipitated amount exceeds the onset detection threshold.
   *
   * @param pressure pressure (bara)
   * @param temperature temperature (K)
   * @return true when asphaltene precipitation is detectable, false when stable or when the flash failed
   */
  private boolean isPrecipitating(double pressure, double temperature) {
    double precipitated = calculatePrecipitatedFraction(pressure, temperature);
    return !Double.isNaN(precipitated) && precipitated > onsetDetectionThreshold;
  }

  /**
   * Refines a pressure where the precipitation state changes by bisection.
   *
   * @param stablePressure pressure where no precipitation is detected (bara)
   * @param unstablePressure pressure where precipitation is detected (bara)
   * @param temperature temperature (K)
   * @return pressure of the transition (bara)
   */
  private double bisectOnset(double stablePressure, double unstablePressure, double temperature) {
    double stable = stablePressure;
    double unstable = unstablePressure;
    for (int i = 0; i < 20 && Math.abs(stable - unstable) > 1.0e-3; i++) {
      double mid = (stable + unstable) / 2.0;
      if (isPrecipitating(mid, temperature)) {
        unstable = mid;
      } else {
        stable = mid;
      }
    }
    return (stable + unstable) / 2.0;
  }

  /**
   * Calculates the asphaltene onset pressure using solubility parameter approach.
   *
   * <p>
   * Scans pressure from the pressure of the fluid down to 1 bara, finding where the oil's solubility parameter drops
   * below the critical value needed to keep asphaltenes dissolved.
   * </p>
   *
   * @param temperature temperature (K)
   * @return onset pressure (bara) or NaN if not found
   */
  public double calculateOnsetPressure(double temperature) {
    if (system == null) {
      logger.error("Thermodynamic system not set");
      return Double.NaN;
    }
    return calculateOnsetPressure(temperature, system.getPressure(), 1.0);
  }

  /**
   * Calculates the upper asphaltene onset pressure within a pressure window.
   *
   * <p>
   * The pressure is scanned downwards from {@code startPressure} in steps of the pressure search step. The first
   * pressure where the precipitated weight fraction exceeds the onset detection threshold is refined by bisection. If
   * the oil is already unstable at {@code startPressure}, {@code startPressure} is returned. An onset within two search
   * steps of {@code minPressure} is treated as not found.
   * </p>
   *
   * @param temperature temperature (K)
   * @param startPressure pressure where the scan starts (bara)
   * @param minPressure lowest pressure of the scan (bara)
   * @return upper onset pressure (bara) or NaN if not found
   */
  public double calculateOnsetPressure(double temperature, double startPressure, double minPressure) {
    if (system == null) {
      logger.error("Thermodynamic system not set");
      return Double.NaN;
    }
    double previousP = startPressure;
    double onsetP = Double.NaN;
    for (double p = startPressure; p >= minPressure; p -= pressureSearchStep) {
      if (isPrecipitating(p, temperature)) {
        onsetP = previousP > p ? bisectOnset(previousP, p, temperature) : p;
        break;
      }
      previousP = p;
    }

    // If onset is found at very low pressure (near minP), it is likely a numerical artifact.
    if (!Double.isNaN(onsetP) && onsetP < minPressure + 2 * pressureSearchStep) {
      logger.info("FH onset at {} bar is near minimum search pressure - treating as no onset", onsetP);
      return Double.NaN;
    }
    return onsetP;
  }

  /**
   * Calculates the lower asphaltene onset pressure, where the asphaltene redissolves below the bubble point.
   *
   * <p>
   * Below the bubble point the oil loses light ends, its density and solubility parameter increase and the asphaltene
   * can redissolve. The scan starts at the upper onset and continues downwards until precipitation is no longer
   * detected.
   * </p>
   *
   * @param temperature temperature (K)
   * @param startPressure pressure where the scan for the upper onset starts (bara)
   * @param minPressure lowest pressure of the scan (bara)
   * @return lower onset pressure (bara), or NaN if there is no upper onset or the asphaltene does not redissolve above
   * {@code minPressure}
   */
  public double calculateLowerOnsetPressure(double temperature, double startPressure, double minPressure) {
    double upperOnset = calculateOnsetPressure(temperature, startPressure, minPressure);
    if (Double.isNaN(upperOnset)) {
      return Double.NaN;
    }
    double previousP = Double.NaN;
    for (double p = upperOnset - pressureSearchStep; p >= minPressure; p -= pressureSearchStep) {
      if (isPrecipitating(p, temperature)) {
        previousP = p;
      } else if (!Double.isNaN(previousP)) {
        return bisectOnset(p, previousP, temperature);
      } else if (!Double.isNaN(calculatePrecipitatedFraction(p, temperature))) {
        // stable directly below the upper onset: no resolvable precipitation window
        return Double.NaN;
      }
    }
    return Double.NaN;
  }

  /**
   * Calculates the asphaltene solubility parameter that places the onset exactly at a measured pressure.
   *
   * <p>
   * The fluid is flashed at the measured onset condition. With the oil solubility parameter and molar volume known, the
   * Flory-Huggins solubility equation is solved in closed form for the interaction parameter that makes the solubility
   * limit equal to the asphaltene content less the onset detection threshold. The model parameters are not changed.
   * </p>
   *
   * @param temperature temperature of the measured onset (K)
   * @param onsetPressure measured upper onset pressure (bara)
   * @return required asphaltene solubility parameter at {@code temperature} (MPa^0.5), or NaN when the flash fails or
   * the oil would dissolve the asphaltene for any solubility parameter
   * @throws IllegalStateException if no fluid is set or the asphaltene weight fraction does not exceed the onset
   * detection threshold
   */
  public double calculateRequiredSolubilityParameter(double temperature, double onsetPressure) {
    if (system == null) {
      throw new IllegalStateException("Thermodynamic system not set");
    }
    double targetWeightFraction = asphalteneWeightFraction - onsetDetectionThreshold;
    if (!(targetWeightFraction > 0.0)) {
      throw new IllegalStateException("Asphaltene weight fraction " + asphalteneWeightFraction
          + " must exceed the onset detection threshold " + onsetDetectionThreshold);
    }
    double[] oilState = calculateOilState(onsetPressure, temperature);
    if (oilState == null) {
      return Double.NaN;
    }
    double oilDensity = oilState[0];
    double phi = (targetWeightFraction / asphalteneDensity)
        / (targetWeightFraction / asphalteneDensity + (1.0 - targetWeightFraction) / oilDensity);
    double molarVolumeRatio = asphaltMolarVolume / oilState[1];
    double chi = -(Math.log(phi) + (1.0 - molarVolumeRatio) * (1.0 - phi)) / ((1.0 - phi) * (1.0 - phi));
    if (!(chi > 0.0)) {
      return Double.NaN;
    }
    double dilute = solveDiluteRoot(chi, molarVolumeRatio);
    if (Math.abs(dilute - phi) > 1.0e-6 * phi) {
      logger.warn("Asphaltene content is beyond the dilute Flory-Huggins branch at P={} bara, T={} K", onsetPressure,
          temperature);
    }
    double gap = Math.sqrt(chi * R_GAS * temperature / (asphaltMolarVolume * 1.0e-6)) / 1.0e3;
    return calculateLiquidSolubilityParameter(oilDensity) + gap;
  }

  /**
   * Calibrates the asphaltene solubility parameter to one measured upper onset pressure.
   *
   * <p>
   * After the call {@link #calculateOnsetPressure(double, double, double)} at the same temperature returns the measured
   * pressure. The reference temperature of the solubility parameter is set to {@code temperature}; an existing
   * temperature coefficient is kept.
   * </p>
   *
   * @param temperature temperature of the measured onset (K)
   * @param onsetPressure measured upper onset pressure (bara)
   * @return calibrated asphaltene solubility parameter (MPa^0.5)
   * @throws IllegalArgumentException if the measurement cannot be reproduced by the model
   * @throws IllegalStateException if no fluid is set or the asphaltene weight fraction is too low
   */
  public double calibrateToOnsetPressure(double temperature, double onsetPressure) {
    double required = calculateRequiredSolubilityParameter(temperature, onsetPressure);
    if (Double.isNaN(required)) {
      throw new IllegalArgumentException(
          "Onset at " + onsetPressure + " bara and " + temperature + " K cannot be reproduced by the model");
    }
    asphalteneSolubilityParameter = required;
    solubilityParameterReferenceTemperature = temperature;
    solubilityParameterBeforeSara = Double.NaN;
    calibratedToOnsetData = true;
    return required;
  }

  /**
   * Regresses the asphaltene solubility parameter and its temperature coefficient to measured onset pressures.
   *
   * <p>
   * Every measurement gives the solubility parameter required at its temperature (see
   * {@link #calculateRequiredSolubilityParameter(double, double)}). With measurements at two or more temperatures a
   * straight line delta_a(T) = delta_a(T_ref) + c * (T - T_ref) is fitted by least squares, with T_ref the mean
   * temperature. With one temperature only delta_a(T_ref) is fitted and the existing temperature coefficient is kept.
   * The result reports the onset pressures recalculated with the fitted parameters.
   * </p>
   *
   * @param temperatures temperatures of the measured onsets (K)
   * @param onsetPressures measured upper onset pressures (bara)
   * @return fit result with fitted parameters and deviations
   * @throws IllegalArgumentException if the arrays are empty, differ in length or contain a point the model cannot
   * reproduce
   * @throws IllegalStateException if no fluid is set or the asphaltene weight fraction is too low
   */
  public OnsetFitResult fitToOnsetData(double[] temperatures, double[] onsetPressures) {
    if (temperatures == null || onsetPressures == null || temperatures.length == 0
        || temperatures.length != onsetPressures.length) {
      throw new IllegalArgumentException("temperatures and onsetPressures must be non-empty and of equal length");
    }
    int n = temperatures.length;
    double[] required = new double[n];
    double meanT = 0.0;
    double meanDelta = 0.0;
    double maxPressure = 0.0;
    for (int i = 0; i < n; i++) {
      required[i] = calculateRequiredSolubilityParameter(temperatures[i], onsetPressures[i]);
      if (Double.isNaN(required[i])) {
        throw new IllegalArgumentException(
            "Onset at " + onsetPressures[i] + " bara and " + temperatures[i] + " K cannot be reproduced by the model");
      }
      meanT += temperatures[i] / n;
      meanDelta += required[i] / n;
      maxPressure = Math.max(maxPressure, onsetPressures[i]);
    }
    double sxx = 0.0;
    double sxy = 0.0;
    for (int i = 0; i < n; i++) {
      sxx += (temperatures[i] - meanT) * (temperatures[i] - meanT);
      sxy += (temperatures[i] - meanT) * (required[i] - meanDelta);
    }
    if (sxx > 1.0) {
      solubilityParameterTemperatureCoefficient = sxy / sxx;
    }
    asphalteneSolubilityParameter = meanDelta;
    solubilityParameterReferenceTemperature = meanT;
    solubilityParameterBeforeSara = Double.NaN;
    calibratedToOnsetData = true;

    double startPressure = Math.max(system.getPressure(), 1.25 * maxPressure);
    double[] predicted = new double[n];
    for (int i = 0; i < n; i++) {
      predicted[i] = calculateOnsetPressure(temperatures[i], startPressure, 1.0);
    }
    logger.info("FH fitted to {} onset points: delta_a={} MPa^0.5 at {} K, coefficient={} MPa^0.5/K", n,
        asphalteneSolubilityParameter, meanT, solubilityParameterTemperatureCoefficient);
    return new OnsetFitResult(asphalteneSolubilityParameter, meanT, solubilityParameterTemperatureCoefficient,
        temperatures, onsetPressures, required, predicted);
  }

  /**
   * Result of regressing the Flory-Huggins asphaltene model to measured onset pressures.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class OnsetFitResult implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    /** Fitted asphaltene solubility parameter at the reference temperature (MPa^0.5). */
    private final double solubilityParameter;
    /** Reference temperature (K). */
    private final double referenceTemperature;
    /** Fitted temperature coefficient (MPa^0.5/K). */
    private final double temperatureCoefficient;
    /** Temperatures of the measurements (K). */
    private final double[] temperatures;
    /** Measured onset pressures (bara). */
    private final double[] measuredOnsetPressures;
    /** Solubility parameter that reproduces each measurement exactly (MPa^0.5). */
    private final double[] requiredSolubilityParameters;
    /** Onset pressures recalculated with the fitted parameters (bara). */
    private final double[] predictedOnsetPressures;

    /**
     * Creates a fit result.
     *
     * @param solubilityParameter fitted solubility parameter at the reference temperature (MPa^0.5)
     * @param referenceTemperature reference temperature (K)
     * @param temperatureCoefficient fitted temperature coefficient (MPa^0.5/K)
     * @param temperatures temperatures of the measurements (K)
     * @param measuredOnsetPressures measured onset pressures (bara)
     * @param requiredSolubilityParameters solubility parameter reproducing each measurement (MPa^0.5)
     * @param predictedOnsetPressures onset pressures recalculated with the fitted parameters (bara)
     */
    OnsetFitResult(double solubilityParameter, double referenceTemperature, double temperatureCoefficient,
        double[] temperatures, double[] measuredOnsetPressures, double[] requiredSolubilityParameters,
        double[] predictedOnsetPressures) {
      this.solubilityParameter = solubilityParameter;
      this.referenceTemperature = referenceTemperature;
      this.temperatureCoefficient = temperatureCoefficient;
      this.temperatures = temperatures.clone();
      this.measuredOnsetPressures = measuredOnsetPressures.clone();
      this.requiredSolubilityParameters = requiredSolubilityParameters.clone();
      this.predictedOnsetPressures = predictedOnsetPressures.clone();
    }

    /**
     * Gets the fitted asphaltene solubility parameter at the reference temperature.
     *
     * @return solubility parameter (MPa^0.5)
     */
    public double getSolubilityParameter() {
      return solubilityParameter;
    }

    /**
     * Gets the reference temperature of the fitted solubility parameter.
     *
     * @return reference temperature (K)
     */
    public double getReferenceTemperature() {
      return referenceTemperature;
    }

    /**
     * Gets the fitted temperature coefficient of the solubility parameter.
     *
     * @return temperature coefficient (MPa^0.5/K)
     */
    public double getTemperatureCoefficient() {
      return temperatureCoefficient;
    }

    /**
     * Gets the temperatures of the measurements.
     *
     * @return temperatures (K)
     */
    public double[] getTemperatures() {
      return temperatures.clone();
    }

    /**
     * Gets the measured onset pressures.
     *
     * @return measured onset pressures (bara)
     */
    public double[] getMeasuredOnsetPressures() {
      return measuredOnsetPressures.clone();
    }

    /**
     * Gets the solubility parameter that reproduces each measurement exactly.
     *
     * @return required solubility parameters (MPa^0.5)
     */
    public double[] getRequiredSolubilityParameters() {
      return requiredSolubilityParameters.clone();
    }

    /**
     * Gets the onset pressures recalculated with the fitted parameters.
     *
     * @return predicted onset pressures (bara), NaN where the model finds no onset
     */
    public double[] getPredictedOnsetPressures() {
      return predictedOnsetPressures.clone();
    }

    /**
     * Gets the average absolute deviation between predicted and measured onset pressures.
     *
     * @return average absolute deviation (bar), NaN if any point has no predicted onset
     */
    public double getAverageAbsoluteDeviation() {
      double sum = 0.0;
      for (int i = 0; i < measuredOnsetPressures.length; i++) {
        sum += Math.abs(predictedOnsetPressures[i] - measuredOnsetPressures[i]);
      }
      return sum / measuredOnsetPressures.length;
    }

    /**
     * Gets the largest absolute deviation between predicted and measured onset pressures.
     *
     * @return maximum absolute deviation (bar), NaN if any point has no predicted onset
     */
    public double getMaximumAbsoluteDeviation() {
      double max = 0.0;
      for (int i = 0; i < measuredOnsetPressures.length; i++) {
        double deviation = Math.abs(predictedOnsetPressures[i] - measuredOnsetPressures[i]);
        if (Double.isNaN(deviation)) {
          return Double.NaN;
        }
        max = Math.max(max, deviation);
      }
      return max;
    }
  }

  /**
   * Generates precipitation curve: weight percent precipitated vs pressure.
   *
   * @param temperature temperature (K)
   * @param maxPressure maximum pressure (bara)
   * @param minPressure minimum pressure (bara)
   * @param numPoints number of calculation points
   * @return 2D array: [0]=pressures (bara), [1]=wt% precipitated
   */
  public double[][] generatePrecipitationCurve(double temperature, double maxPressure, double minPressure,
      int numPoints) {
    double[][] results = new double[2][numPoints];
    double step = (maxPressure - minPressure) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double p = maxPressure - i * step;
      results[0][i] = p;
      double precip = calculatePrecipitatedFraction(p, temperature);
      results[1][i] = Double.isNaN(precip) ? 0.0 : precip * 100.0;
    }

    return results;
  }

  /**
   * Generates solubility parameter profile vs pressure.
   *
   * @param temperature temperature (K)
   * @param maxPressure maximum pressure (bara)
   * @param minPressure minimum pressure (bara)
   * @param numPoints number of calculation points
   * @return 2D array: [0]=pressures, [1]=delta_L (MPa^0.5), [2]=delta_asphaltene
   */
  public double[][] generateSolubilityParameterProfile(double temperature, double maxPressure, double minPressure,
      int numPoints) {
    double[][] results = new double[3][numPoints];
    double step = (maxPressure - minPressure) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double p = maxPressure - i * step;
      results[0][i] = p;
      results[2][i] = getAsphalteneSolubilityParameter(temperature);

      if (system != null) {
        SystemInterface workSystem = system.clone();
        workSystem.setPressure(p);
        workSystem.setTemperature(temperature);

        ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
        try {
          ops.TPflash();
          workSystem.initProperties();

          double oilDensity;
          if (workSystem.hasPhaseType("oil")) {
            oilDensity = workSystem.getPhase("oil").getDensity("kg/m3");
          } else if (workSystem.getNumberOfPhases() > 0) {
            oilDensity = workSystem.getPhase(0).getDensity("kg/m3");
          } else {
            results[1][i] = Double.NaN;
            continue;
          }
          results[1][i] = calculateLiquidSolubilityParameter(oilDensity);
        } catch (Exception e) {
          results[1][i] = Double.NaN;
        }
      }
    }

    return results;
  }

  /**
   * Generates full results as a map for JSON serialization.
   *
   * @param temperature temperature for calculations (K)
   * @param maxPressure maximum pressure for curves (bara)
   * @param minPressure minimum pressure for curves (bara)
   * @return map containing all model results
   */
  public Map<String, Object> getResultsMap(double temperature, double maxPressure, double minPressure) {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("model", "Flory-Huggins Regular Solution");
    results.put("asphaltene_MW_gmol", asphalteneMW);
    results.put("asphaltene_density_kgm3", asphalteneDensity);
    results.put("asphaltene_solubility_parameter_MPa05", getAsphalteneSolubilityParameter(temperature));
    results.put("asphaltene_solubility_parameter_reference_temperature_K", solubilityParameterReferenceTemperature);
    results.put("asphaltene_solubility_parameter_temperature_coefficient_MPa05_per_K",
        solubilityParameterTemperatureCoefficient);
    results.put("calibrated_to_onset_data", calibratedToOnsetData);
    results.put("asphaltene_molar_volume_cm3mol", asphaltMolarVolume);
    results.put("asphaltene_weight_fraction", asphalteneWeightFraction);
    results.put("temperature_K", temperature);

    double onsetP = calculateOnsetPressure(temperature, maxPressure, minPressure);
    results.put("onset_pressure_bara", onsetP);
    results.put("lower_onset_pressure_bara", calculateLowerOnsetPressure(temperature, maxPressure, minPressure));

    return results;
  }

  // ───────────────── Getters and Setters ─────────────────

  /**
   * Gets the asphaltene molar volume.
   *
   * @return molar volume (cm3/mol)
   */
  public double getAsphaltMolarVolume() {
    return asphaltMolarVolume;
  }

  /**
   * Sets the asphaltene molar volume.
   *
   * @param asphaltMolarVolume molar volume (cm3/mol)
   */
  public void setAsphaltMolarVolume(double asphaltMolarVolume) {
    this.asphaltMolarVolume = asphaltMolarVolume;
  }

  /**
   * Gets the asphaltene solubility parameter at the reference temperature.
   *
   * @return solubility parameter (MPa^0.5)
   */
  public double getAsphalteneSolubilityParameter() {
    return asphalteneSolubilityParameter;
  }

  /**
   * Gets the asphaltene solubility parameter at a temperature.
   *
   * @param temperature temperature (K)
   * @return solubility parameter delta_a(T_ref) + c * (T - T_ref) (MPa^0.5)
   */
  public double getAsphalteneSolubilityParameter(double temperature) {
    return asphalteneSolubilityParameter
        + solubilityParameterTemperatureCoefficient * (temperature - solubilityParameterReferenceTemperature);
  }

  /**
   * Sets the asphaltene solubility parameter at the reference temperature.
   *
   * @param asphalteneSolubilityParameter solubility parameter (MPa^0.5)
   */
  public void setAsphalteneSolubilityParameter(double asphalteneSolubilityParameter) {
    this.asphalteneSolubilityParameter = asphalteneSolubilityParameter;
    this.solubilityParameterBeforeSara = Double.NaN;
  }

  /**
   * Sets the linear temperature dependence of the asphaltene solubility parameter.
   *
   * <p>
   * The default coefficient is zero (constant solubility parameter). Hirschberg et al. (1984) used a relative decrease
   * of 1.07e-3 per kelvin, about -0.021 MPa^0.5/K. A coefficient regressed with
   * {@link #fitToOnsetData(double[], double[])} is preferred when onset pressures at several temperatures exist.
   * </p>
   *
   * @param referenceTemperature temperature at which the solubility parameter applies (K)
   * @param temperatureCoefficient change of the solubility parameter with temperature (MPa^0.5/K)
   */
  public void setSolubilityParameterTemperatureDependence(double referenceTemperature, double temperatureCoefficient) {
    this.solubilityParameterReferenceTemperature = referenceTemperature;
    this.solubilityParameterTemperatureCoefficient = temperatureCoefficient;
  }

  /**
   * Gets the reference temperature of the asphaltene solubility parameter.
   *
   * @return reference temperature (K)
   */
  public double getSolubilityParameterReferenceTemperature() {
    return solubilityParameterReferenceTemperature;
  }

  /**
   * Gets the temperature coefficient of the asphaltene solubility parameter.
   *
   * @return temperature coefficient (MPa^0.5/K)
   */
  public double getSolubilityParameterTemperatureCoefficient() {
    return solubilityParameterTemperatureCoefficient;
  }

  /**
   * Sets the precipitated weight fraction that defines the onset.
   *
   * <p>
   * The default of 0.001 (0.1 wt%) represents the detection limit of an onset measurement. Use the detection limit of
   * the laboratory method when calibrating to data.
   * </p>
   *
   * @param threshold precipitated weight fraction at onset, zero or positive
   * @throws IllegalArgumentException if the threshold is negative or not finite
   */
  public void setOnsetDetectionThreshold(double threshold) {
    if (!(threshold >= 0.0) || Double.isInfinite(threshold)) {
      throw new IllegalArgumentException("Onset detection threshold must be zero or positive");
    }
    this.onsetDetectionThreshold = threshold;
  }

  /**
   * Gets the precipitated weight fraction that defines the onset.
   *
   * @return onset detection threshold (weight fraction)
   */
  public double getOnsetDetectionThreshold() {
    return onsetDetectionThreshold;
  }

  /**
   * Checks whether the asphaltene solubility parameter was regressed to measured onset data.
   *
   * @return true after {@link #calibrateToOnsetPressure(double, double)} or {@link #fitToOnsetData(double[], double[])}
   */
  public boolean isCalibratedToOnsetData() {
    return calibratedToOnsetData;
  }

  /**
   * Gets the asphaltene molecular weight.
   *
   * @return molecular weight (g/mol)
   */
  public double getAsphalteneMW() {
    return asphalteneMW;
  }

  /**
   * Sets the asphaltene molecular weight. Also recalculates molar volume.
   *
   * @param asphalteneMW molecular weight (g/mol)
   */
  public void setAsphalteneMW(double asphalteneMW) {
    this.asphalteneMW = asphalteneMW;
    this.asphaltMolarVolume = asphalteneMW / (asphalteneDensity / 1000.0);
  }

  /**
   * Gets the asphaltene density.
   *
   * @return density (kg/m3)
   */
  public double getAsphalteneDensity() {
    return asphalteneDensity;
  }

  /**
   * Sets the asphaltene density. Also recalculates molar volume.
   *
   * @param asphalteneDensity density (kg/m3)
   */
  public void setAsphalteneDensity(double asphalteneDensity) {
    this.asphalteneDensity = asphalteneDensity;
    this.asphaltMolarVolume = asphalteneMW / (asphalteneDensity / 1000.0);
  }

  /**
   * Gets the asphaltene weight fraction.
   *
   * @return weight fraction (0-1)
   */
  public double getAsphalteneWeightFraction() {
    return asphalteneWeightFraction;
  }

  /**
   * Sets the asphaltene weight fraction.
   *
   * @param asphalteneWeightFraction weight fraction (0-1)
   */
  public void setAsphalteneWeightFraction(double asphalteneWeightFraction) {
    this.asphalteneWeightFraction = asphalteneWeightFraction;
  }

  /**
   * Sets the density-solubility parameter correlation coefficients.
   *
   * @param coeffA slope coefficient (MPa^0.5 per kg/m3)
   * @param coeffB intercept (MPa^0.5)
   */
  public void setDeltaRhoCorrelation(double coeffA, double coeffB) {
    this.deltaRhoCoeffA = coeffA;
    this.deltaRhoCoeffB = coeffB;
  }

  /**
   * Gets the thermodynamic system.
   *
   * @return the thermodynamic system
   */
  public SystemInterface getSystem() {
    return system;
  }

  /**
   * Sets the thermodynamic system.
   *
   * @param system the thermodynamic system
   */
  public void setSystem(SystemInterface system) {
    this.system = system.clone();
  }

  /**
   * Gets the reservoir temperature.
   *
   * @return temperature (K)
   */
  public double getReservoirTemperature() {
    return reservoirTemperature;
  }

  /**
   * Sets the reservoir temperature.
   *
   * @param reservoirTemperature temperature (K)
   */
  public void setReservoirTemperature(double reservoirTemperature) {
    this.reservoirTemperature = reservoirTemperature;
  }
}
