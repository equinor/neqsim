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
 * This class implements the Flory-Huggins polymer solution theory applied to asphaltene
 * precipitation, a widely-used approach in the petroleum industry. The model treats the oil as a
 * solvent and asphaltenes as a dissolved polymer, using the mismatch in solubility parameters to
 * predict precipitation.
 * </p>
 *
 * <h2>Theoretical Background</h2>
 * <p>
 * The chemical potential of asphaltene in oil is expressed as:
 * </p>
 *
 * <pre>
 * ln(x_a * gamma_a) = ln(phi_a) + (1 - V_a / V_L) * (1 - phi_a) + (V_a / RT) * (delta_a - delta_L)
 *     ^ 2 * (1 - phi_a) ^ 2
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
 * Onset occurs when delta_L drops below a critical value (typically when light ends evolve near
 * bubble point).
 * </p>
 *
 * <h2>Key Correlations</h2>
 * <ul>
 * <li>Oil solubility parameter from density: delta_L = A * rho + B (Lian et al., 1994)</li>
 * <li>Asphaltene solubility parameter: 19-24 MPa^0.5 (Barton, 1991)</li>
 * <li>Onset condition: delta_L = delta_onset (fitted parameter)</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Hirschberg, A. et al. (1984). SPE J., 24(3), 283-293.</li>
 * <li>Flory, P.J. (1953). Principles of Polymer Chemistry. Cornell Univ. Press.</li>
 * <li>Huggins, M.L. (1941). J. Chem. Phys., 9, 440.</li>
 * <li>Lian, H., Lin, J.R., Yen, T.F. (1994). Fuel, 73(3), 423-428.</li>
 * <li>Andersen, S.I., Speight, J.G. (1999). J. Petrol. Sci. Eng., 22, 53-66.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
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
   * Coefficient A in delta_L = A * rho + B correlation. Default from Lian et al. (1994): 17.347e-3
   * MPa^0.5/(kg/m3).
   */
  private double deltaRhoCoeffA = 17.347e-3;

  /**
   * Coefficient B in delta_L = A * rho + B correlation. Default from Lian et al. (1994): 2.904
   * MPa^0.5.
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

  /** Onset solubility parameter (fitted). Null until fitted. */
  private Double onsetDelta = null;

  /**
   * Default constructor.
   */
  public FloryHugginsAsphalteneModel() {}

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
   * Uses correlations from Akbarzadeh et al. (2005) and Andersen and Speight (1999) to estimate
   * asphaltene molecular weight, density, and solubility parameter from API gravity. This is
   * critical for matching diverse oil types as asphaltene properties vary significantly across the
   * API range.
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
    asphalteneDensity = Math.max(1050.0, Math.min(1200.0, 1100.0));

    // Recalculate molar volume from MW and density
    asphaltMolarVolume = (asphalteneMW / asphalteneDensity) * 1000.0; // cm3/mol

    configuredFromAPI = true;

    logger.info("Configured FH asphaltene for API={}: MW={}, delta={}, Vm={}", apiGravity,
        asphalteneMW, asphalteneSolubilityParameter, asphaltMolarVolume);
  }

  /**
   * Calibrates the delta_L correlation coefficient A to the current thermodynamic system.
   *
   * <p>
   * The default Lian et al. (1994) correlation (A=0.017347) systematically underestimates the
   * solubility parameter of live oils at reservoir conditions compared to EOS-computed values
   * (Hirschberg, 1984). This method performs a TP flash to determine the actual oil density and
   * molar volume, then calibrates A using the Flory-Huggins critical condition so that:
   * </p>
   * <ul>
   * <li>The oil is stable at reservoir pressure (chi &lt; chi_critical)</li>
   * <li>Onset is detected when density drops slightly (typically 50-100 bar above Pbub)</li>
   * </ul>
   *
   * <p>
   * The critical chi is computed from the Flory-Huggins phase separation criterion: chi_c = r * (1
   * + 1/sqrt(r))^2 / 2, where r = V_asphaltene / V_liquid. A stability buffer (default 0.35
   * MPa^0.5) keeps the system just below the critical threshold at reservoir conditions.
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

    double calibratedA = (asphalteneSolubilityParameter - targetGap - deltaRhoCoeffB) / oilDensity;

    // Sanity check: A must be positive and within physically meaningful range
    if (calibratedA > 0.005 && calibratedA < 0.10) {
      deltaRhoCoeffA = calibratedA;
      logger.info("FH calibrated: A={}, oilDensity={}, criticalGap={}, targetGap={}",
          deltaRhoCoeffA, oilDensity, criticalDeltaGap, targetGap);
    } else {
      logger.warn("Calibrated A={} outside valid range [0.005, 0.10], keeping default",
          calibratedA);
    }
  }

  /**
   * Configures asphaltene properties from SARA fractions.
   *
   * <p>
   * Uses the asphaltene content and resin/asphaltene ratio to refine the solubility parameter.
   * Higher R/A ratios indicate better peptization, which effectively lowers the apparent asphaltene
   * solubility parameter (they act more dissolved).
   * </p>
   *
   * @param saturates weight fraction of saturates
   * @param aromatics weight fraction of aromatics
   * @param resins weight fraction of resins
   * @param asphaltenes weight fraction of asphaltenes
   */
  public void configureFromSARA(double saturates, double aromatics, double resins,
      double asphaltenes) {
    this.asphalteneWeightFraction = asphaltenes;

    // Resin/asphaltene ratio affects effective solubility parameter.
    // Only apply corrections for extreme R/A ratios:
    // - Very high R/A (>5) = excellent peptization = asphaltenes well-solvated
    // - High R/A (>3) = good peptization
    // - Low R/A (<1) = poor peptization = higher effective delta
    // Moderate R/A (1-3) gets no correction to avoid over-tuning.
    double raRatio = (asphaltenes > 0.001) ? resins / asphaltenes : 3.0;
    if (raRatio > 5.0) {
      asphalteneSolubilityParameter *= 0.97;
    } else if (raRatio > 3.0) {
      asphalteneSolubilityParameter *= 0.98;
    } else if (raRatio < 1.0) {
      asphalteneSolubilityParameter *= 1.02;
    }

    // Higher saturate fraction = poorer asphaltene solvent.
    // Only apply for very high saturate ratios (>0.75 of saturates+aromatics).
    double satFrac = saturates / (saturates + aromatics + 1e-10);
    if (satFrac > 0.75) {
      asphalteneSolubilityParameter *= 1.01;
    }
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
   * Uses the empirical correlation: delta_L = A * rho + B, where rho is in kg/m3 and delta_L is in
   * MPa^0.5.
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
   * chi = V_a / (RT) * (delta_a - delta_L)^2
   * </p>
   *
   * @param deltaLiquid solubility parameter of liquid phase (MPa^0.5)
   * @param temperature temperature (K)
   * @return Flory-Huggins chi parameter (dimensionless)
   */
  public double calculateChiParameter(double deltaLiquid, double temperature) {
    double deltaDiff = asphalteneSolubilityParameter - deltaLiquid;
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
   * Solved iteratively for phi_a (maximum asphaltene that can remain dissolved).
   * </p>
   *
   * @param deltaLiquid solubility parameter of liquid phase (MPa^0.5)
   * @param liquidMolarVolume molar volume of liquid phase (cm3/mol)
   * @param temperature temperature (K)
   * @return maximum dissolved asphaltene volume fraction
   */
  public double calculateMaxDissolvedFraction(double deltaLiquid, double liquidMolarVolume,
      double temperature) {
    double chi = calculateChiParameter(deltaLiquid, temperature);
    double molarVolumeRatio = asphaltMolarVolume / liquidMolarVolume;

    // Iteratively solve for phi_a using Newton-Raphson
    double phiA = 0.01; // Initial guess
    for (int iter = 0; iter < 50; iter++) {
      double f = Math.log(phiA) + (1.0 - molarVolumeRatio) * (1.0 - phiA)
          + chi * (1.0 - phiA) * (1.0 - phiA);
      double dfdphi = 1.0 / phiA - (1.0 - molarVolumeRatio) - 2.0 * chi * (1.0 - phiA);

      double delta = f / dfdphi;
      phiA = phiA - delta;

      if (phiA <= 0) {
        phiA = 1e-20;
      }
      if (phiA > 1.0) {
        phiA = 0.99;
      }
      if (Math.abs(delta) < 1e-10) {
        break;
      }
    }

    return phiA;
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

    SystemInterface workSystem = system.clone();
    workSystem.setPressure(pressure);
    workSystem.setTemperature(temperature);

    ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
    try {
      ops.TPflash();
      workSystem.initProperties();
    } catch (Exception e) {
      logger.error("Flash failed at P={}, T={}: {}", pressure, temperature, e.getMessage());
      return Double.NaN;
    }

    // Get oil phase density
    double oilDensity;
    if (workSystem.hasPhaseType("oil")) {
      oilDensity = workSystem.getPhase("oil").getDensity("kg/m3");
    } else if (workSystem.getNumberOfPhases() > 0) {
      oilDensity = workSystem.getPhase(0).getDensity("kg/m3");
    } else {
      return 0.0;
    }

    double deltaL = calculateLiquidSolubilityParameter(oilDensity);

    // Estimate liquid molar volume from density and average MW
    double avgMW;
    if (workSystem.hasPhaseType("oil")) {
      avgMW = workSystem.getPhase("oil").getMolarMass() * 1000.0; // kg/mol to g/mol
    } else {
      avgMW = workSystem.getPhase(0).getMolarMass() * 1000.0;
    }
    double liquidMolarVolume = avgMW / (oilDensity / 1000.0); // cm3/mol

    double maxDissolved = calculateMaxDissolvedFraction(deltaL, liquidMolarVolume, temperature);

    // Convert from volume fraction to weight fraction
    double maxDissolvedMass = maxDissolved * asphalteneDensity
        / (maxDissolved * asphalteneDensity + (1.0 - maxDissolved) * oilDensity);

    if (asphalteneWeightFraction > maxDissolvedMass) {
      return asphalteneWeightFraction - maxDissolvedMass;
    }
    return 0.0;
  }

  /**
   * Calculates the asphaltene onset pressure using solubility parameter approach.
   *
   * <p>
   * Scans pressure from high to low, finding where the oil's solubility parameter drops below the
   * critical value needed to keep asphaltenes dissolved.
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

    double startP = system.getPressure();
    double minP = 1.0;

    double previousDelta = Double.NaN;
    double previousP = startP;
    boolean foundOnset = false;
    double onsetP = Double.NaN;

    for (double p = startP; p >= minP; p -= pressureSearchStep) {
      SystemInterface workSystem = system.clone();
      workSystem.setPressure(p);
      workSystem.setTemperature(temperature);

      ThermodynamicOperations ops = new ThermodynamicOperations(workSystem);
      try {
        ops.TPflash();
        workSystem.initProperties();
      } catch (Exception e) {
        continue;
      }

      double oilDensity;
      if (workSystem.hasPhaseType("oil")) {
        oilDensity = workSystem.getPhase("oil").getDensity("kg/m3");
      } else if (workSystem.getNumberOfPhases() > 0) {
        oilDensity = workSystem.getPhase(0).getDensity("kg/m3");
      } else {
        continue;
      }

      double deltaL = calculateLiquidSolubilityParameter(oilDensity);
      double precip = calculatePrecipitatedFraction(p, temperature);

      if (precip > 0.001 && !foundOnset) {
        foundOnset = true;
        // Refine by bisection
        double high = previousP;
        double low = p;
        for (int i = 0; i < 15; i++) {
          double mid = (high + low) / 2.0;
          double midPrecip = calculatePrecipitatedFraction(mid, temperature);
          if (midPrecip > 0.001) {
            low = mid;
          } else {
            high = mid;
          }
        }
        onsetP = (high + low) / 2.0;
        break;
      }

      previousDelta = deltaL;
      previousP = p;
    }

    // Validate: onset must be above approximate bubble point.
    // If onset is found near the minimum search pressure, it's likely a numerical artifact.
    if (!Double.isNaN(onsetP) && onsetP < minP + 2 * pressureSearchStep) {
      logger.info("FH onset at {:.1f} bar is near minimum search pressure - treating as no onset",
          onsetP);
      return Double.NaN;
    }

    return onsetP;
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
  public double[][] generatePrecipitationCurve(double temperature, double maxPressure,
      double minPressure, int numPoints) {
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
  public double[][] generateSolubilityParameterProfile(double temperature, double maxPressure,
      double minPressure, int numPoints) {
    double[][] results = new double[3][numPoints];
    double step = (maxPressure - minPressure) / (numPoints - 1);

    for (int i = 0; i < numPoints; i++) {
      double p = maxPressure - i * step;
      results[0][i] = p;
      results[2][i] = asphalteneSolubilityParameter;

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
  public Map<String, Object> getResultsMap(double temperature, double maxPressure,
      double minPressure) {
    Map<String, Object> results = new LinkedHashMap<String, Object>();
    results.put("model", "Flory-Huggins Regular Solution");
    results.put("asphaltene_MW_gmol", asphalteneMW);
    results.put("asphaltene_density_kgm3", asphalteneDensity);
    results.put("asphaltene_solubility_parameter_MPa05", asphalteneSolubilityParameter);
    results.put("asphaltene_molar_volume_cm3mol", asphaltMolarVolume);
    results.put("asphaltene_weight_fraction", asphalteneWeightFraction);
    results.put("temperature_K", temperature);

    double onsetP = calculateOnsetPressure(temperature);
    results.put("onset_pressure_bara", onsetP);

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
   * Gets the asphaltene solubility parameter.
   *
   * @return solubility parameter (MPa^0.5)
   */
  public double getAsphalteneSolubilityParameter() {
    return asphalteneSolubilityParameter;
  }

  /**
   * Sets the asphaltene solubility parameter.
   *
   * @param asphalteneSolubilityParameter solubility parameter (MPa^0.5)
   */
  public void setAsphalteneSolubilityParameter(double asphalteneSolubilityParameter) {
    this.asphalteneSolubilityParameter = asphalteneSolubilityParameter;
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
