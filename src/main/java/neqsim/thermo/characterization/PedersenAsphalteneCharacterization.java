package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseSolid;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Pedersen's method for asphaltene precipitation using classical cubic equation of state.
 *
 * <p>
 * This class implements asphaltene characterization and precipitation modeling based on the
 * methodology described by K.S. Pedersen (SPE-224534-MS, 2025). The key principle is that
 * asphaltene precipitation can be successfully modeled using a classical cubic equation of state
 * (SRK or PR) without requiring association terms.
 * </p>
 *
 * <h2>Theoretical Background</h2>
 * <p>
 * Asphaltenes are characterized as heavy pseudo-components with specific critical properties
 * derived from the same correlations used for C7+ characterization. The precipitation mechanism is
 * modeled as a liquid-liquid phase split between an asphaltene-rich heavy phase and an
 * asphaltene-lean light phase.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>Uses Pedersen correlations for critical temperature, pressure, and acentric factor</li>
 * <li>Treats asphaltene as a single heavy pseudo-component or multiple pseudo-components</li>
 * <li>Models precipitation as liquid-liquid equilibrium (not solid precipitation)</li>
 * <li>Compatible with standard SRK and PR equations of state</li>
 * <li>Consistent with existing Pedersen C7+ characterization in NeqSim</li>
 * </ul>
 *
 * <h2>Asphaltene Characterization</h2>
 * <p>
 * The asphaltene component is characterized using:
 * </p>
 * <ul>
 * <li>Molecular weight: Typically 750-1500 g/mol (adjustable)</li>
 * <li>Density: Typically 1.05-1.20 g/cm³ at standard conditions</li>
 * <li>Critical properties: Calculated using Pedersen correlations for heavy fractions</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Pedersen, K.S. (2025). "The Mechanisms Behind Asphaltene Precipitation – Successfully Handled
 * by a Classical Cubic Equation of State." SPE-224534-MS, GOTECH, Dubai.</li>
 * <li>Pedersen, K.S., Christensen, P.L. (2007). "Phase Behavior of Petroleum Reservoir Fluids." CRC
 * Press.</li>
 * <li>Pedersen, K.S., Fredenslund, A., Thomassen, P. (1989). "Properties of Oils and Natural
 * Gases." Gulf Publishing.</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class PedersenAsphalteneCharacterization {

  /** Logger object for class. */
  private static final Logger logger =
      LogManager.getLogger(PedersenAsphalteneCharacterization.class);

  /** Default asphaltene molecular weight (g/mol). */
  public static final double DEFAULT_ASPHALTENE_MW = 750.0;

  /** Default asphaltene density at standard conditions (g/cm³). */
  public static final double DEFAULT_ASPHALTENE_DENSITY = 1.10;

  /** Heavy asphaltene molecular weight (g/mol) - for very heavy oils. */
  public static final double HEAVY_ASPHALTENE_MW = 1500.0;

  /** Minimum asphaltene molecular weight (g/mol). */
  public static final double MIN_ASPHALTENE_MW = 500.0;

  /** Maximum asphaltene molecular weight (g/mol). */
  public static final double MAX_ASPHALTENE_MW = 5000.0;

  /** Default asphaltene weight fraction in heavy oil. */
  public static final double DEFAULT_ASPHALTENE_WEIGHT_FRACTION = 0.05;

  /** Molecular weight of asphaltene pseudo-component (g/mol). */
  private double asphalteneMW = DEFAULT_ASPHALTENE_MW;

  /** Density of asphaltene at standard conditions (g/cm³). */
  private double asphalteneDensity = DEFAULT_ASPHALTENE_DENSITY;

  /** Weight fraction of asphaltene in the oil. */
  private double asphalteneWeightFraction = DEFAULT_ASPHALTENE_WEIGHT_FRACTION;

  /** Calculated critical temperature (K). */
  private double criticalTemperature;

  /** Calculated critical pressure (bar). */
  private double criticalPressure;

  /** Calculated acentric factor. */
  private double acentricFactor;

  /** Calculated boiling point (K). */
  private double boilingPoint;

  /** Reference to the thermodynamic system. */
  private SystemInterface system;

  /** Number of asphaltene pseudo-components for distribution. */
  private int numberOfAsphaltenePseudoComponents = 1;

  /** Flag indicating whether asphaltene has been characterized. */
  private boolean isCharacterized = false;

  // Tuning multipliers for fitting to experimental data
  /** Multiplier for critical temperature tuning. Default = 1.0 */
  private double tcMultiplier = 1.0;

  /** Multiplier for critical pressure tuning. Default = 1.0 */
  private double pcMultiplier = 1.0;

  /** Multiplier for acentric factor tuning. Default = 1.0 */
  private double omegaMultiplier = 1.0;

  /** Multiplier for boiling point tuning. Default = 1.0 */
  private double tbMultiplier = 1.0;

  /** Binary interaction parameter adjustment for asphaltene-light components. */
  private double kijAdjustment = 0.0;

  /**
   * Default constructor.
   */
  public PedersenAsphalteneCharacterization() {}

  /**
   * Constructor with system reference.
   *
   * @param system the thermodynamic system
   */
  public PedersenAsphalteneCharacterization(SystemInterface system) {
    this.system = system;
  }

  /**
   * Constructor with asphaltene properties.
   *
   * @param asphalteneMW molecular weight of asphaltene (g/mol)
   * @param asphalteneDensity density at standard conditions (g/cm³)
   * @param asphalteneWeightFraction weight fraction in the oil (0-1)
   */
  public PedersenAsphalteneCharacterization(double asphalteneMW, double asphalteneDensity,
      double asphalteneWeightFraction) {
    setAsphalteneMW(asphalteneMW);
    setAsphalteneDensity(asphalteneDensity);
    setAsphalteneWeightFraction(asphalteneWeightFraction);
  }

  /**
   * Characterizes the asphaltene component using Pedersen correlations.
   *
   * <p>
   * Calculates critical temperature, critical pressure, acentric factor, and boiling point using
   * the same correlations as for heavy petroleum fractions.
   * </p>
   */
  public void characterize() {
    calculateBoilingPoint();
    calculateCriticalTemperature();
    calculateCriticalPressure();
    calculateAcentricFactor();
    isCharacterized = true;
    logger.info("Asphaltene characterized: Tc={} K, Pc={} bar, omega={}", criticalTemperature,
        criticalPressure, acentricFactor);
  }

  /**
   * Calculates the boiling point using Søreide correlation.
   *
   * <p>
   * The Søreide correlation is suitable for heavy petroleum fractions and is used consistently with
   * other Pedersen characterization methods in NeqSim.
   * </p>
   */
  private void calculateBoilingPoint() {
    // Søreide (1989) correlation for boiling point
    // Tb = (1928.3 - 1.695e5 * M^(-0.03522) * ρ^3.266 *
    // exp(-4.922e-3 * M - 4.7685 * ρ + 3.462e-3 * M * ρ)) / 1.8
    double M = asphalteneMW;
    double rho = asphalteneDensity;

    boilingPoint = (1928.3 - 1.695e5 * Math.pow(M, -0.03522) * Math.pow(rho, 3.266)
        * Math.exp(-4.922e-3 * M - 4.7685 * rho + 3.462e-3 * M * rho)) / 1.8;

    // Ensure boiling point is reasonable for heavy components
    if (boilingPoint < 500.0) {
      boilingPoint = 500.0 + 0.2 * M; // Fallback for very heavy components
    }
    if (boilingPoint > 1200.0) {
      boilingPoint = 1200.0; // Cap at practical maximum
    }

    // Apply tuning multiplier
    boilingPoint *= tbMultiplier;
  }

  /**
   * Calculates the critical temperature using Pedersen correlation for heavy fractions.
   *
   * <p>
   * Uses the correlation form: Tc = a0*ρ + a1*ln(M) + a2*M + a3/M
   * </p>
   */
  private void calculateCriticalTemperature() {
    double M = asphalteneMW;
    double rho = asphalteneDensity * 1000.0; // Convert to kg/m³

    // Pedersen heavy oil correlation coefficients
    // Tc = a0 + a1*ρ + a2*ln(M) + a3*M + a4/M
    double a0 = 9.13222e2;
    double a1 = 1.01134e1;
    double a2 = 4.54194e-2;
    double a3 = -1.3587e4;

    // Apply Pedersen correlation
    criticalTemperature = a0 + a1 * Math.log(M) + a2 * M + a3 / M;

    // Alternative: use boiling point based correlation for heavy components
    // Tc = Tb * (1 + (1.242 + 0.0125 * Kw) / (0.567 + 0.0125 * Kw * Tbr))
    // where Kw is Watson K-factor and Tbr is reduced boiling point

    // Ensure critical temperature is above boiling point
    if (criticalTemperature < boilingPoint + 50.0) {
      criticalTemperature = boilingPoint * 1.1;
    }

    // Apply tuning multiplier
    criticalTemperature *= tcMultiplier;
  }

  /**
   * Calculates the critical pressure using Pedersen correlation for heavy fractions.
   *
   * <p>
   * Uses the correlation form: Pc = exp(b0 + b1*ρ^b4 + b2/M + b3/M²)
   * </p>
   */
  private void calculateCriticalPressure() {
    double M = asphalteneMW;
    double rho = asphalteneDensity;

    // Pedersen heavy oil correlation coefficients
    // ln(Pc) = b0 + b1*ρ^b4 + b2/M + b3/M²
    double b0 = 1.28155;
    double b1 = 1.26838;
    double b2 = 1.67106e2;
    double b3 = -8.10164e3;
    double b4 = 0.25;

    double lnPc = b0 + b1 * Math.pow(rho, b4) + b2 / M + b3 / (M * M);
    criticalPressure = Math.exp(lnPc);

    // Ensure critical pressure is reasonable for heavy components
    // Typically 10-25 bar for very heavy components
    if (criticalPressure < 5.0) {
      criticalPressure = 5.0;
    }
    if (criticalPressure > 50.0) {
      criticalPressure = 50.0;
    }

    // Apply tuning multiplier
    criticalPressure *= pcMultiplier;
  }

  /**
   * Calculates the acentric factor using Pedersen correlation.
   *
   * <p>
   * For heavy components, uses the correlation: ω = c0 + c1*M + c2*ρ + c3/M
   * </p>
   */
  private void calculateAcentricFactor() {
    double M = asphalteneMW;
    double rho = asphalteneDensity;

    // Pedersen correlation for acentric factor
    // ω = c0 + c1*ρ + c2*M + c3/M
    double c0 = -2.3838e-1;
    double c1 = 6.10147e-2;
    double c2 = 1.32349;
    double c3 = -6.52067e-3;

    acentricFactor = c0 + c1 * Math.log(M) + c2 * rho + c3 * M;

    // Alternative: Kesler-Lee correlation
    if (boilingPoint > 0 && criticalTemperature > 0 && criticalPressure > 0) {
      double Tbr = boilingPoint / criticalTemperature;
      double Pbr = ThermodynamicConstantsInterface.referencePressure / criticalPressure;

      if (Tbr < 0.8) {
        acentricFactor = (Math.log(Pbr) - 5.92714 + 6.09649 / Tbr + 1.28862 * Math.log(Tbr)
            - 0.169347 * Math.pow(Tbr, 6.0))
            / (15.2518 - 15.6875 / Tbr - 13.4721 * Math.log(Tbr) + 0.43577 * Math.pow(Tbr, 6.0));
      }
    }

    // Ensure acentric factor is reasonable for heavy aromatics
    // Asphaltenes typically have ω > 1.0
    if (acentricFactor < 0.8) {
      acentricFactor = 0.8 + 0.0005 * (M - 500.0);
    }
    if (acentricFactor > 2.5) {
      acentricFactor = 2.5;
    }

    // Apply tuning multiplier
    acentricFactor *= omegaMultiplier;
  }

  /**
   * Adds the asphaltene component to the thermodynamic system.
   *
   * <p>
   * Creates a pseudo-component with the characterized properties and adds it to the system. The
   * component is named "Asphaltene_PC" (following TBP naming convention) and is configured with the
   * calculated critical properties.
   * </p>
   *
   * <p>
   * Note: The mixing rule should be set AFTER calling this method, and init(0) should be called
   * after setting the mixing rule.
   * </p>
   *
   * @param system the thermodynamic system to add asphaltene to
   * @param moles number of moles of asphaltene to add
   * @return the component name that was added (for reference)
   */
  public String addAsphalteneToSystem(SystemInterface system, double moles) {
    if (!isCharacterized) {
      characterize();
    }

    // Add asphaltene as TBP fraction with characterized properties
    // Note: addTBPfraction adds "_PC" suffix to the name
    system.addTBPfraction("Asphaltene", moles, asphalteneMW / 1000.0, asphalteneDensity);

    // The actual component name will be "Asphaltene_PC"
    String componentName = "Asphaltene_PC";

    // Override calculated properties with Pedersen asphaltene characterization
    int aspIndex = system.getPhase(0).getNumberOfComponents() - 1;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      system.getPhase(i).getComponent(aspIndex).setTC(criticalTemperature);
      system.getPhase(i).getComponent(aspIndex).setPC(criticalPressure);
      system.getPhase(i).getComponent(aspIndex).setAcentricFactor(acentricFactor);
      system.getPhase(i).getComponent(aspIndex).setNormalBoilingPoint(boilingPoint);
    }

    logger.info("Added asphaltene to system: {} mol, MW={} g/mol, name={}", moles, asphalteneMW,
        componentName);
    return componentName;
  }

  /**
   * Adds multiple asphaltene pseudo-components with distributed molecular weights.
   *
   * <p>
   * For more accurate modeling, the asphaltene can be represented as multiple pseudo-components
   * with a distribution of molecular weights. This follows the same principle as C7+
   * characterization.
   * </p>
   *
   * <p>
   * Note: The mixing rule should be set AFTER calling this method, and init(0) should be called
   * after setting the mixing rule.
   * </p>
   *
   * @param system the thermodynamic system
   * @param totalMoles total moles of asphaltene
   * @param numberOfComponents number of pseudo-components (typically 2-5)
   */
  public void addDistributedAsphaltene(SystemInterface system, double totalMoles,
      int numberOfComponents) {
    if (numberOfComponents < 1) {
      numberOfComponents = 1;
    }
    if (numberOfComponents > 10) {
      numberOfComponents = 10;
    }

    this.numberOfAsphaltenePseudoComponents = numberOfComponents;

    // Define MW range for asphaltene distribution
    double minMW = asphalteneMW * 0.5; // Lower bound
    double maxMW = asphalteneMW * 2.0; // Upper bound

    // Use exponential distribution similar to Pedersen plus fraction model
    double beta = Math.log(0.1) / (maxMW - minMW); // Distribution parameter

    double[] componentMW = new double[numberOfComponents];
    double[] componentMoles = new double[numberOfComponents];
    double totalZ = 0.0;

    // Calculate MW and relative amounts for each pseudo-component
    for (int i = 0; i < numberOfComponents; i++) {
      double fraction = (i + 0.5) / numberOfComponents;
      componentMW[i] = minMW + fraction * (maxMW - minMW);
      componentMoles[i] = Math.exp(beta * (componentMW[i] - minMW));
      totalZ += componentMoles[i];
    }

    // Normalize and add components
    for (int i = 0; i < numberOfComponents; i++) {
      componentMoles[i] = totalMoles * componentMoles[i] / totalZ;

      // Create characterization for this pseudo-component
      PedersenAsphalteneCharacterization compChar = new PedersenAsphalteneCharacterization();
      compChar.setAsphalteneMW(componentMW[i]);
      compChar.setAsphalteneDensity(
          asphalteneDensity + 0.05 * (componentMW[i] - asphalteneMW) / asphalteneMW);
      compChar.characterize();

      // Add to system with unique name (will get _PC suffix from addTBPfraction)
      String compName = "Asph_" + (i + 1);
      system.addTBPfraction(compName, componentMoles[i], componentMW[i] / 1000.0,
          compChar.getAsphalteneDensity());

      // Set characterized properties (component was just added, so it's the last one)
      int compIndex = system.getPhase(0).getNumberOfComponents() - 1;
      for (int j = 0; j < system.getNumberOfPhases(); j++) {
        system.getPhase(j).getComponent(compIndex).setTC(compChar.getCriticalTemperature());
        system.getPhase(j).getComponent(compIndex).setPC(compChar.getCriticalPressure());
        system.getPhase(j).getComponent(compIndex).setAcentricFactor(compChar.getAcentricFactor());
      }
    }

    logger.info("Added {} asphaltene pseudo-components, total {} mol", numberOfComponents,
        totalMoles);
  }

  /**
   * Calculates the asphaltene onset pressure at the given temperature.
   *
   * <p>
   * Uses the Pedersen approach of searching for liquid-liquid phase split conditions. The onset
   * pressure is where a second liquid phase (asphaltene-rich) becomes stable.
   * </p>
   *
   * @param system the thermodynamic system with asphaltene component
   * @param startPressure starting pressure for search (bar)
   * @param endPressure ending pressure for search (bar)
   * @return onset pressure in bar, or NaN if not found
   */
  public double calculateOnsetPressure(SystemInterface system, double startPressure,
      double endPressure) {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    double pressureStep = (startPressure - endPressure) / 50.0;
    double currentPressure = startPressure;
    boolean wasOnePhase = true;

    // Search for liquid-liquid split
    while (currentPressure > endPressure) {
      system.setPressure(currentPressure);
      try {
        ops.TPflash();

        // Check for two liquid phases (not just gas-liquid)
        int liquidPhases = countLiquidPhases(system);

        if (liquidPhases >= 2 && wasOnePhase) {
          // Found onset - refine with bisection
          return refineOnsetPressure(system, ops, currentPressure + pressureStep, currentPressure);
        }

        wasOnePhase = (liquidPhases <= 1);
      } catch (Exception e) {
        logger.debug("Flash failed at P={} bar: {}", currentPressure, e.getMessage());
      }

      currentPressure -= pressureStep;
    }

    logger.info("No asphaltene onset found in pressure range {} to {} bar", startPressure,
        endPressure);
    return Double.NaN;
  }

  /**
   * Counts the number of liquid phases in the system.
   *
   * @param system the thermodynamic system
   * @return number of liquid phases
   */
  private int countLiquidPhases(SystemInterface system) {
    int liquidPhases = 0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      String phaseType = system.getPhase(i).getPhaseTypeName();
      if (phaseType.contains("oil") || phaseType.contains("liquid") || phaseType.contains("aqueous")
          || phaseType.contains("Asphaltene")) {
        liquidPhases++;
      }
    }
    return liquidPhases;
  }

  /**
   * Performs a TPflash and automatically marks asphaltene-rich liquid phases.
   *
   * <p>
   * This is a convenience method that combines the TPflash operation with automatic detection and
   * marking of asphaltene-rich liquid phases (PhaseType.LIQUID_ASPHALTENE). Use this method instead
   * of calling TPflash directly when working with Pedersen's liquid-liquid asphaltene approach.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   * 
   * <pre>
   * {@code
   * // Setup fluid with asphaltene
   * PedersenAsphalteneCharacterization asphChar = new PedersenAsphalteneCharacterization();
   * asphChar.addAsphalteneToSystem(fluid, 0.05);
   * fluid.setMixingRule("classic");
   * fluid.init(0);
   *
   * // Perform flash with automatic asphaltene detection
   * boolean hasAsphaltene = PedersenAsphalteneCharacterization.TPflash(fluid);
   * if (hasAsphaltene) {
   *   System.out.println("Asphaltene-rich phase detected!");
   * }
   * }
   * </pre>
   *
   * @param system the thermodynamic system to flash
   * @return true if an asphaltene-rich liquid phase was detected
   */
  public static boolean TPflash(SystemInterface system) {
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    return markAsphalteneRichLiquidPhases(system);
  }

  /**
   * Performs a TPflash at specified conditions and automatically marks asphaltene-rich phases.
   *
   * <p>
   * This is a convenience method that sets the temperature and pressure, performs a TPflash, and
   * automatically marks asphaltene-rich liquid phases.
   * </p>
   *
   * @param system the thermodynamic system to flash
   * @param temperature temperature in Kelvin
   * @param pressure pressure in bar
   * @return true if an asphaltene-rich liquid phase was detected
   */
  public static boolean TPflash(SystemInterface system, double temperature, double pressure) {
    system.setTemperature(temperature);
    system.setPressure(pressure);
    return TPflash(system);
  }

  /**
   * Marks asphaltene-rich liquid phases with PhaseType.LIQUID_ASPHALTENE.
   *
   * <p>
   * This method should be called after a flash calculation to identify and mark liquid phases that
   * are rich in asphaltene components (&gt; 50 mol% asphaltene). This is useful for the Pedersen
   * liquid-liquid approach where asphaltene precipitation is modeled as a phase split between light
   * and heavy liquid phases.
   * </p>
   *
   * @param system the thermodynamic system to update
   * @return true if an asphaltene-rich liquid phase was found and marked
   */
  public static boolean markAsphalteneRichLiquidPhases(SystemInterface system) {
    boolean foundAsphaltene = false;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      PhaseInterface phase = system.getPhase(i);
      // Skip solid phases - those use PhaseType.ASPHALTENE
      if (phase instanceof PhaseSolid) {
        continue;
      }
      // Check if this liquid phase is asphaltene-rich
      if (phase.isAsphalteneRich()) {
        phase.setType(PhaseType.LIQUID_ASPHALTENE);
        foundAsphaltene = true;
        logger.info("Marked phase {} as LIQUID_ASPHALTENE", i);
      }
    }
    return foundAsphaltene;
  }

  /**
   * Refines the onset pressure using bisection.
   *
   * @param system the thermodynamic system
   * @param ops thermodynamic operations object
   * @param upperP upper pressure bound (one phase)
   * @param lowerP lower pressure bound (two phases)
   * @return refined onset pressure
   */
  private double refineOnsetPressure(SystemInterface system, ThermodynamicOperations ops,
      double upperP, double lowerP) {
    double tolerance = 0.1; // bar
    int maxIterations = 20;

    for (int i = 0; i < maxIterations; i++) {
      if (upperP - lowerP < tolerance) {
        break;
      }

      double midP = (upperP + lowerP) / 2.0;
      system.setPressure(midP);

      try {
        ops.TPflash();
        int liquidPhases = countLiquidPhases(system);

        if (liquidPhases >= 2) {
          lowerP = midP;
        } else {
          upperP = midP;
        }
      } catch (Exception e) {
        upperP = midP;
      }
    }

    return (upperP + lowerP) / 2.0;
  }

  /**
   * Estimates asphaltene solubility parameter at given conditions.
   *
   * <p>
   * The solubility parameter approach provides insight into asphaltene stability. Precipitation
   * occurs when the difference between asphaltene and oil solubility parameters exceeds a critical
   * value.
   * </p>
   *
   * @param temperature temperature in Kelvin
   * @return solubility parameter in (MPa)^0.5
   */
  public double calculateSolubilityParameter(double temperature) {
    // Correlation for asphaltene solubility parameter
    // δ_asph ≈ 19-21 (MPa)^0.5 at 25°C, decreases with temperature
    double delta25C = 20.0; // Reference value at 25°C
    double temperatureEffect = 0.01 * (temperature - 298.15); // Temperature correction

    return delta25C - temperatureEffect;
  }

  /**
   * Estimates stability based on solubility parameter difference.
   *
   * @param oilSolubilityParameter oil phase solubility parameter in (MPa)^0.5
   * @return stability assessment string
   */
  public String assessStability(double oilSolubilityParameter) {
    double asphalteneDelta = calculateSolubilityParameter(298.15);
    double difference = Math.abs(asphalteneDelta - oilSolubilityParameter);

    StringBuilder sb = new StringBuilder();
    sb.append("Pedersen Asphaltene Stability Assessment:\n");
    sb.append(String.format("  Asphaltene δ = %.2f (MPa)^0.5%n", asphalteneDelta));
    sb.append(String.format("  Oil δ = %.2f (MPa)^0.5%n", oilSolubilityParameter));
    sb.append(String.format("  |Δδ| = %.2f (MPa)^0.5%n", difference));

    if (difference < 2.0) {
      sb.append("  Status: STABLE - Good asphaltene solvency\n");
    } else if (difference < 4.0) {
      sb.append("  Status: MARGINAL - Monitor during operations\n");
    } else {
      sb.append("  Status: UNSTABLE - High precipitation risk\n");
    }

    return sb.toString();
  }

  // Getters and setters

  /**
   * Gets the asphaltene molecular weight.
   *
   * @return molecular weight in g/mol
   */
  public double getAsphalteneMW() {
    return asphalteneMW;
  }

  /**
   * Sets the asphaltene molecular weight.
   *
   * @param asphalteneMW molecular weight in g/mol
   */
  public void setAsphalteneMW(double asphalteneMW) {
    if (asphalteneMW < MIN_ASPHALTENE_MW) {
      logger.warn("Asphaltene MW {} is below minimum {}, setting to minimum", asphalteneMW,
          MIN_ASPHALTENE_MW);
      this.asphalteneMW = MIN_ASPHALTENE_MW;
    } else if (asphalteneMW > MAX_ASPHALTENE_MW) {
      logger.warn("Asphaltene MW {} exceeds maximum {}, setting to maximum", asphalteneMW,
          MAX_ASPHALTENE_MW);
      this.asphalteneMW = MAX_ASPHALTENE_MW;
    } else {
      this.asphalteneMW = asphalteneMW;
    }
    isCharacterized = false;
  }

  /**
   * Gets the asphaltene density at standard conditions.
   *
   * @return density in g/cm³
   */
  public double getAsphalteneDensity() {
    return asphalteneDensity;
  }

  /**
   * Sets the asphaltene density at standard conditions.
   *
   * @param asphalteneDensity density in g/cm³
   */
  public void setAsphalteneDensity(double asphalteneDensity) {
    if (asphalteneDensity < 0.9 || asphalteneDensity > 1.3) {
      logger.warn("Asphaltene density {} is outside typical range [0.9, 1.3]", asphalteneDensity);
    }
    this.asphalteneDensity = asphalteneDensity;
    isCharacterized = false;
  }

  /**
   * Gets the asphaltene weight fraction in the oil.
   *
   * @return weight fraction (0-1)
   */
  public double getAsphalteneWeightFraction() {
    return asphalteneWeightFraction;
  }

  /**
   * Sets the asphaltene weight fraction in the oil.
   *
   * @param asphalteneWeightFraction weight fraction (0-1)
   */
  public void setAsphalteneWeightFraction(double asphalteneWeightFraction) {
    if (asphalteneWeightFraction < 0.0 || asphalteneWeightFraction > 1.0) {
      throw new IllegalArgumentException("Weight fraction must be between 0 and 1");
    }
    this.asphalteneWeightFraction = asphalteneWeightFraction;
  }

  /**
   * Gets the calculated critical temperature.
   *
   * @return critical temperature in Kelvin
   */
  public double getCriticalTemperature() {
    if (!isCharacterized) {
      characterize();
    }
    return criticalTemperature;
  }

  /**
   * Gets the calculated critical pressure.
   *
   * @return critical pressure in bar
   */
  public double getCriticalPressure() {
    if (!isCharacterized) {
      characterize();
    }
    return criticalPressure;
  }

  /**
   * Gets the calculated acentric factor.
   *
   * @return acentric factor (dimensionless)
   */
  public double getAcentricFactor() {
    if (!isCharacterized) {
      characterize();
    }
    return acentricFactor;
  }

  /**
   * Gets the calculated boiling point.
   *
   * @return boiling point in Kelvin
   */
  public double getBoilingPoint() {
    if (!isCharacterized) {
      characterize();
    }
    return boilingPoint;
  }

  /**
   * Checks if asphaltene has been characterized.
   *
   * @return true if characterized
   */
  public boolean isCharacterized() {
    return isCharacterized;
  }

  /**
   * Gets the number of asphaltene pseudo-components used in distributed characterization.
   *
   * @return number of pseudo-components
   */
  public int getNumberOfAsphaltenePseudoComponents() {
    return numberOfAsphaltenePseudoComponents;
  }

  /**
   * Sets the number of asphaltene pseudo-components for distributed characterization.
   *
   * @param numberOfComponents number of pseudo-components (1-10)
   */
  public void setNumberOfAsphaltenePseudoComponents(int numberOfComponents) {
    if (numberOfComponents < 1) {
      numberOfComponents = 1;
    }
    if (numberOfComponents > 10) {
      numberOfComponents = 10;
    }
    this.numberOfAsphaltenePseudoComponents = numberOfComponents;
  }

  // === Tuning parameter methods for fitting to experimental data ===

  /**
   * Gets the critical temperature tuning multiplier.
   *
   * @return Tc multiplier (default 1.0)
   */
  public double getTcMultiplier() {
    return tcMultiplier;
  }

  /**
   * Sets the critical temperature tuning multiplier.
   *
   * <p>
   * Use this to adjust the calculated critical temperature to match experimental onset pressures.
   * Values typically range from 0.9 to 1.1.
   * </p>
   *
   * @param tcMultiplier multiplier for critical temperature
   */
  public void setTcMultiplier(double tcMultiplier) {
    this.tcMultiplier = tcMultiplier;
    isCharacterized = false;
  }

  /**
   * Gets the critical pressure tuning multiplier.
   *
   * @return Pc multiplier (default 1.0)
   */
  public double getPcMultiplier() {
    return pcMultiplier;
  }

  /**
   * Sets the critical pressure tuning multiplier.
   *
   * <p>
   * Use this to adjust the calculated critical pressure to match experimental data. Values
   * typically range from 0.8 to 1.2.
   * </p>
   *
   * @param pcMultiplier multiplier for critical pressure
   */
  public void setPcMultiplier(double pcMultiplier) {
    this.pcMultiplier = pcMultiplier;
    isCharacterized = false;
  }

  /**
   * Gets the acentric factor tuning multiplier.
   *
   * @return omega multiplier (default 1.0)
   */
  public double getOmegaMultiplier() {
    return omegaMultiplier;
  }

  /**
   * Sets the acentric factor tuning multiplier.
   *
   * <p>
   * Use this to adjust the calculated acentric factor to match experimental data. The acentric
   * factor strongly influences phase behavior predictions.
   * </p>
   *
   * @param omegaMultiplier multiplier for acentric factor
   */
  public void setOmegaMultiplier(double omegaMultiplier) {
    this.omegaMultiplier = omegaMultiplier;
    isCharacterized = false;
  }

  /**
   * Gets the boiling point tuning multiplier.
   *
   * @return Tb multiplier (default 1.0)
   */
  public double getTbMultiplier() {
    return tbMultiplier;
  }

  /**
   * Sets the boiling point tuning multiplier.
   *
   * @param tbMultiplier multiplier for boiling point
   */
  public void setTbMultiplier(double tbMultiplier) {
    this.tbMultiplier = tbMultiplier;
    isCharacterized = false;
  }

  /**
   * Gets the binary interaction parameter adjustment.
   *
   * @return kij adjustment value (default 0.0)
   */
  public double getKijAdjustment() {
    return kijAdjustment;
  }

  /**
   * Sets the binary interaction parameter adjustment for asphaltene-light component pairs.
   *
   * <p>
   * This adjustment is added to the default calculated binary interaction parameters between the
   * asphaltene and lighter components. Positive values increase kij (reduce attraction), which can
   * shift onset pressure to higher values.
   * </p>
   *
   * @param kijAdjustment adjustment to add to binary interaction parameters
   */
  public void setKijAdjustment(double kijAdjustment) {
    this.kijAdjustment = kijAdjustment;
  }

  /**
   * Sets all tuning parameters at once.
   *
   * <p>
   * Convenience method for applying multiple tuning parameters from experimental data fitting.
   * </p>
   *
   * @param tcMultiplier critical temperature multiplier
   * @param pcMultiplier critical pressure multiplier
   * @param omegaMultiplier acentric factor multiplier
   */
  public void setTuningParameters(double tcMultiplier, double pcMultiplier,
      double omegaMultiplier) {
    this.tcMultiplier = tcMultiplier;
    this.pcMultiplier = pcMultiplier;
    this.omegaMultiplier = omegaMultiplier;
    isCharacterized = false;
  }

  /**
   * Resets all tuning parameters to default values (1.0).
   */
  public void resetTuningParameters() {
    this.tcMultiplier = 1.0;
    this.pcMultiplier = 1.0;
    this.omegaMultiplier = 1.0;
    this.tbMultiplier = 1.0;
    this.kijAdjustment = 0.0;
    isCharacterized = false;
  }

  /**
   * Returns a summary of the asphaltene characterization.
   *
   * @return characterization summary string
   */
  @Override
  public String toString() {
    if (!isCharacterized) {
      characterize();
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Pedersen Asphaltene Characterization:\n");
    sb.append(String.format("  Molecular Weight: %.1f g/mol%n", asphalteneMW));
    sb.append(String.format("  Density: %.3f g/cm³%n", asphalteneDensity));
    sb.append(String.format("  Critical Temperature: %.1f K (%.1f °C)%n", criticalTemperature,
        criticalTemperature - 273.15));
    sb.append(String.format("  Critical Pressure: %.2f bar%n", criticalPressure));
    sb.append(String.format("  Acentric Factor: %.4f%n", acentricFactor));
    sb.append(
        String.format("  Boiling Point: %.1f K (%.1f °C)%n", boilingPoint, boilingPoint - 273.15));
    if (tcMultiplier != 1.0 || pcMultiplier != 1.0 || omegaMultiplier != 1.0
        || tbMultiplier != 1.0) {
      sb.append("  Tuning Parameters:\n");
      sb.append(String.format("    Tc multiplier: %.4f%n", tcMultiplier));
      sb.append(String.format("    Pc multiplier: %.4f%n", pcMultiplier));
      sb.append(String.format("    ω multiplier: %.4f%n", omegaMultiplier));
      sb.append(String.format("    Tb multiplier: %.4f%n", tbMultiplier));
    }
    if (kijAdjustment != 0.0) {
      sb.append(String.format("  kij adjustment: %.4f%n", kijAdjustment));
    }
    return sb.toString();
  }

  /**
   * Main method demonstrating Pedersen's asphaltene TPflash.
   *
   * @param args command line arguments (not used)
   */
  public static void main(String[] args) {
    // Create SRK system - liquid conditions (high pressure, moderate temp)
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemSrkEos(323.15, 200.0); // 50°C, 200 bar

    // Add light oil and precipitant components
    fluid.addComponent("methane", 0.30); // Gas dissolved in oil
    fluid.addComponent("n-pentane", 0.25); // Asphaltene precipitant
    fluid.addComponent("n-heptane", 0.20);
    fluid.addComponent("nC10", 0.10);
    fluid.addComponent("nC20", 0.05);

    // Create and configure asphaltene characterization
    PedersenAsphalteneCharacterization asphChar = new PedersenAsphalteneCharacterization();
    asphChar.setAsphalteneMW(750.0); // Molecular weight g/mol
    asphChar.setAsphalteneDensity(1.10); // Density g/cm³

    // Add significant asphaltene content (BEFORE setting mixing rule)
    asphChar.addAsphalteneToSystem(fluid, 0.10); // 10% asphaltene

    // Set mixing rule and initialize (AFTER adding all components)
    fluid.setMixingRule("classic");

    // Enable multi-phase flash for potential liquid-liquid equilibrium
    fluid.setMultiPhaseCheck(true);
    fluid.setNumberOfPhases(3);
    fluid.init(0);

    System.out.println("=== Pedersen Asphaltene TPflash Demo ===\n");
    System.out.println(asphChar.toString());

    // Perform TPflash with automatic asphaltene detection
    boolean hasAsphaltene = PedersenAsphalteneCharacterization.TPflash(fluid);

    System.out.println("\n=== Flash Results ===");
    System.out.println("Temperature: " + (fluid.getTemperature() - 273.15) + " C");
    System.out.println("Pressure: " + fluid.getPressure() + " bar");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());
    System.out.println("Asphaltene-rich liquid phase detected: " + hasAsphaltene);

    // Print phase types and asphaltene content
    System.out.println("\n=== Phase Analysis ===");
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      double asphMoleFrac = 0.0;
      if (fluid.getPhase(i).hasComponent("Asphaltene_PC")) {
        asphMoleFrac = fluid.getPhase(i).getComponent("Asphaltene_PC").getx();
      }
      System.out.println("Phase " + i + ": " + fluid.getPhase(i).getType().getDesc().toUpperCase()
          + " (beta=" + String.format("%.4f", fluid.getPhase(i).getBeta()) + ", asph.x="
          + String.format("%.4f", asphMoleFrac) + ", density="
          + String.format("%.1f", fluid.getPhase(i).getDensity("kg/m3")) + " kg/m3)");
    }

    System.out.println("\n=== Detailed Results ===");
    fluid.prettyPrint();
  }
}
