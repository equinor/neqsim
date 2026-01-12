package neqsim.pvtsimulation.simulation;

import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SolutionGasWaterRatio class. Calculates the Solution Gas-Water Ratio (Rsw), which represents the
 * amount of gas dissolved in water at reservoir conditions that will be released when pressure is
 * reduced to standard conditions.
 * </p>
 *
 * <p>
 * Rsw is typically expressed in Sm³ gas/Sm³ water at standard conditions (15°C, 1.01325 bara).
 * </p>
 *
 * <p>
 * Three calculation methods are available:
 * </p>
 * <ul>
 * <li><b>McCain (Culberson-McKetta)</b>: Empirical correlation for methane-water systems with
 * salinity correction</li>
 * <li><b>Søreide-Whitson</b>: EoS-based method using modified Peng-Robinson with salinity
 * effects</li>
 * <li><b>Electrolyte CPA</b>: Rigorous EoS method using CPA equation of state for electrolyte
 * systems</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Culberson, O.L. and McKetta, J.J. (1951): "Phase Equilibria in Hydrocarbon-Water Systems III
 * - The Solubility of Methane in Water at Pressures to 10,000 psia", JPT.</li>
 * <li>McCain, W.D. (1990): "The Properties of Petroleum Fluids", PennWell Books.</li>
 * <li>Søreide, I. and Whitson, C.H. (1992): "Peng-Robinson predictions for hydrocarbons, CO2, N2,
 * and H2S with pure water and NaCl brine", Fluid Phase Equilibria.</li>
 * </ul>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SolutionGasWaterRatio extends BasePVTsimulation {

  /**
   * Calculation method for Rsw.
   */
  public enum CalculationMethod {
    /** McCain/Culberson-McKetta empirical correlation. */
    MCCAIN,
    /** Søreide-Whitson modified Peng-Robinson EoS. */
    SOREIDE_WHITSON,
    /** Electrolyte CPA equation of state. */
    ELECTROLYTE_CPA
  }

  private double[] temperatures = null;
  private double[] pressures = null;
  private double[] rsw = null;
  private double salinity = 0.0; // mol NaCl per kg water (molality)
  private CalculationMethod method = CalculationMethod.ELECTROLYTE_CPA;

  /**
   * <p>
   * Constructor for SolutionGasWaterRatio.
   * </p>
   *
   * @param tempSystem a {@link neqsim.thermo.system.SystemInterface} object representing the
   *        reservoir gas/fluid
   */
  public SolutionGasWaterRatio(SystemInterface tempSystem) {
    super(tempSystem);
    temperatures = new double[1];
    pressures = new double[1];
    temperatures[0] = tempSystem.getTemperature();
    pressures[0] = tempSystem.getPressure();
  }

  /**
   * <p>
   * Set the calculation method.
   * </p>
   *
   * @param method the calculation method to use
   */
  public void setCalculationMethod(CalculationMethod method) {
    this.method = method;
  }

  /**
   * <p>
   * Set the calculation method using a string.
   * </p>
   *
   * @param methodName the name of the calculation method ("McCain", "Soreide-Whitson", or
   *        "Electrolyte-CPA")
   */
  public void setCalculationMethod(String methodName) {
    if (methodName == null) {
      throw new IllegalArgumentException("Method name cannot be null");
    }
    String normalizedName = methodName.toLowerCase().trim();
    if (normalizedName.contains("mccain") || normalizedName.contains("culberson")
        || normalizedName.contains("mcketta")) {
      this.method = CalculationMethod.MCCAIN;
    } else if (normalizedName.contains("soreide") || normalizedName.contains("whitson")) {
      this.method = CalculationMethod.SOREIDE_WHITSON;
    } else if (normalizedName.contains("cpa") || normalizedName.contains("electrolyte")) {
      this.method = CalculationMethod.ELECTROLYTE_CPA;
    } else {
      throw new IllegalArgumentException("Unknown calculation method: " + methodName);
    }
  }

  /**
   * <p>
   * Get the current calculation method.
   * </p>
   *
   * @return the calculation method
   */
  public CalculationMethod getCalculationMethod() {
    return method;
  }

  /**
   * <p>
   * Set the salinity of the water.
   * </p>
   *
   * @param salinity salinity in mol NaCl per kg water (molality)
   */
  public void setSalinity(double salinity) {
    this.salinity = salinity;
  }

  /**
   * <p>
   * Set the salinity of the water with unit specification.
   * </p>
   *
   * @param salinity the salinity value
   * @param unit the unit: "molal" (mol/kg water), "wt%" (weight percent NaCl), or "ppm" (mg/L)
   */
  public void setSalinity(double salinity, String unit) {
    if (unit == null) {
      throw new IllegalArgumentException("Unit cannot be null");
    }
    String normalizedUnit = unit.toLowerCase().trim();
    double molarMassNaCl = 58.44; // g/mol
    if (normalizedUnit.equals("molal") || normalizedUnit.equals("mol/kg")) {
      this.salinity = salinity;
    } else if (normalizedUnit.equals("wt%") || normalizedUnit.equals("weight%")) {
      // Convert wt% to molality: m = (wt% / 100) / (MNaCl/1000) / (1 - wt%/100)
      double wtFraction = salinity / 100.0;
      this.salinity = (wtFraction / (molarMassNaCl / 1000.0)) / (1.0 - wtFraction);
    } else if (normalizedUnit.equals("ppm") || normalizedUnit.equals("mg/l")) {
      // Approximate: ppm ≈ mg/L, assume water density ~1 kg/L
      // molality = (ppm/1e6) / (MNaCl/1000) ≈ ppm / (MNaCl * 1000)
      this.salinity = salinity / (molarMassNaCl * 1000.0);
    } else {
      throw new IllegalArgumentException("Unknown salinity unit: " + unit);
    }
  }

  /**
   * <p>
   * Get the salinity in molality (mol NaCl / kg water).
   * </p>
   *
   * @return the salinity in molality
   */
  public double getSalinity() {
    return salinity;
  }

  /**
   * <p>
   * Set the temperatures and pressures for the calculation.
   * </p>
   *
   * @param temperatures array of temperatures in Kelvin
   * @param pressures array of pressures in bara
   */
  public void setTemperaturesAndPressures(double[] temperatures, double[] pressures) {
    if (temperatures.length != pressures.length) {
      throw new IllegalArgumentException("Temperature and pressure arrays must have same length");
    }
    this.temperatures = temperatures;
    this.pressures = pressures;
  }

  /**
   * <p>
   * Run the Rsw calculation.
   * </p>
   */
  public void runCalc() {
    rsw = new double[pressures.length];
    for (int i = 0; i < pressures.length; i++) {
      rsw[i] = calculateRsw(temperatures[i], pressures[i]);
    }
  }

  /**
   * <p>
   * Calculate Rsw at a single temperature and pressure.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return Rsw in Sm³ gas / Sm³ water at standard conditions
   */
  public double calculateRsw(double temperatureK, double pressureBara) {
    switch (method) {
      case MCCAIN:
        return calculateRswMcCain(temperatureK, pressureBara);
      case SOREIDE_WHITSON:
        return calculateRswSoreideWhitson(temperatureK, pressureBara);
      case ELECTROLYTE_CPA:
      default:
        return calculateRswElectrolyteCPA(temperatureK, pressureBara);
    }
  }

  /**
   * <p>
   * Calculate Rsw using McCain (Culberson-McKetta) correlation.
   * </p>
   *
   * <p>
   * The correlation calculates gas solubility in pure water, then applies a salinity correction
   * factor. Valid for methane-water systems at temperatures up to 350°F (177°C) and pressures up to
   * 10,000 psia (690 bar).
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return Rsw in Sm³ gas / Sm³ water
   */
  private double calculateRswMcCain(double temperatureK, double pressureBara) {
    // Convert to field units for correlation
    double temperatureF = (temperatureK - 273.15) * 9.0 / 5.0 + 32.0; // °F
    double pressurePsia = pressureBara * 14.5038; // psia

    // Culberson-McKetta correlation for methane solubility in pure water
    // Rsw_pure = A + B*p + C*p^2
    // where coefficients are temperature-dependent

    double A = 8.15839 - 6.12265e-2 * temperatureF + 1.91663e-4 * temperatureF * temperatureF
        - 2.1654e-7 * temperatureF * temperatureF * temperatureF;

    double B = 1.01021e-2 - 7.44241e-5 * temperatureF + 3.05553e-7 * temperatureF * temperatureF
        - 2.94883e-10 * temperatureF * temperatureF * temperatureF;

    double C = (-9.02505 + 0.130237 * temperatureF - 8.53425e-4 * temperatureF * temperatureF
        + 2.34122e-6 * temperatureF * temperatureF * temperatureF
        - 2.37049e-9 * temperatureF * temperatureF * temperatureF * temperatureF) * 1e-7;

    // Gas solubility in pure water (scf/STB)
    double rswPure = A + B * pressurePsia + C * pressurePsia * pressurePsia;

    // Apply salinity correction (McCain)
    // Salinity effect: Rsw_brine = Rsw_pure * 10^(-Cs*S)
    // where S is salinity in wt% NaCl, Cs is temperature-dependent coefficient
    double salinityWtPercent = getSalinityWtPercent();
    double cs = calculateSalinityCoefficient(temperatureF, pressurePsia);
    double salinityCorrection = Math.pow(10.0, -cs * salinityWtPercent);

    double rswBrine = rswPure * salinityCorrection;

    // Convert from scf/STB to Sm³/Sm³
    // 1 scf/STB = 0.178108 Sm³/Sm³
    double rswSm3 = rswBrine * 0.178108;

    return Math.max(0.0, rswSm3);
  }

  /**
   * <p>
   * Calculate salinity coefficient for McCain correlation.
   * </p>
   *
   * @param temperatureF temperature in Fahrenheit
   * @param pressurePsia pressure in psia
   * @return salinity coefficient Cs
   */
  private double calculateSalinityCoefficient(double temperatureF, double pressurePsia) {
    // McCain's salinity correction coefficient
    // Cs = (S1 + S2*p + S3*p^2) * 1e-4
    double s1 = 2.12 + 3.085e-3 * temperatureF - 3.878e-6 * temperatureF * temperatureF;
    double s2 = -1.0071e-4 - 1.272e-6 * temperatureF + 9.46e-9 * temperatureF * temperatureF;
    double s3 = (-5.0e-5 + 4.285e-7 * temperatureF - 1.14e-9 * temperatureF * temperatureF) * 1e-4;

    return (s1 + s2 * pressurePsia + s3 * pressurePsia * pressurePsia) * 1e-4;
  }

  /**
   * <p>
   * Get salinity in weight percent NaCl.
   * </p>
   *
   * @return salinity in wt% NaCl
   */
  private double getSalinityWtPercent() {
    // Convert molality (mol/kg water) to wt%
    double molarMassNaCl = 58.44; // g/mol
    // wt% = (m * MNaCl) / (1000 + m * MNaCl) * 100
    double massNaCl = salinity * molarMassNaCl; // g NaCl per kg water
    return (massNaCl / (1000.0 + massNaCl)) * 100.0;
  }

  /**
   * <p>
   * Calculate Rsw using Søreide-Whitson method.
   * </p>
   *
   * <p>
   * Uses the modified Peng-Robinson EoS with Søreide-Whitson alpha function and mixing rules that
   * account for salinity effects on gas-water equilibrium.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return Rsw in Sm³ gas / Sm³ water
   */
  private double calculateRswSoreideWhitson(double temperatureK, double pressureBara) {
    // Create Søreide-Whitson system at reservoir conditions
    SystemSoreideWhitson system = new SystemSoreideWhitson(temperatureK, pressureBara);

    // Get source system and calculate total moles for normalization
    SystemInterface sourceSystem = getBaseThermoSystem();
    double totalSourceMoles = sourceSystem.getTotalNumberOfMoles();
    if (totalSourceMoles <= 0) {
      // If total moles not available, calculate from components
      totalSourceMoles = 0.0;
      for (int i = 0; i < sourceSystem.getNumberOfComponents(); i++) {
        totalSourceMoles += sourceSystem.getComponent(i).getNumberOfmoles();
      }
    }

    // Add gas components from the input system with proper scaling
    double totalGasMoles = 0.0;
    for (int i = 0; i < sourceSystem.getNumberOfComponents(); i++) {
      String componentName = sourceSystem.getComponent(i).getComponentName();
      double moles = sourceSystem.getComponent(i).getNumberOfmoles();
      // Skip water as we add it separately
      if (!componentName.equalsIgnoreCase("water") && !componentName.equalsIgnoreCase("H2O")) {
        // Scale to reasonable amounts (use fraction * 10 for numerical stability)
        double scaledMoles = (moles / totalSourceMoles) * 10.0;
        system.addComponent(componentName, scaledMoles, "mole/sec");
        totalGasMoles += scaledMoles;
      }
    }

    // Add water (100 moles for large water excess)
    double waterMoles = 100.0;
    system.addComponent("water", waterMoles, "mole/sec");

    // Add salinity (molality * kg water gives moles of NaCl)
    double kgWater = waterMoles * 18.015 / 1000.0; // kg
    double molesNaCl = salinity * kgWater;
    system.addSalinity(molesNaCl, "mole/sec");

    // Set total flow rate (required for Søreide-Whitson!)
    system.setTotalFlowRate(totalGasMoles + waterMoles, "mole/sec");

    // Set mixing rule
    system.setMixingRule(11); // Søreide-Whitson mixing rule
    system.setMultiPhaseCheck(true);

    // Run flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Log the exception and return 0 for now
      System.err.println("Søreide-Whitson flash failed: " + e.getMessage());
      return 0.0;
    }

    // Calculate Rsw from phase compositions
    return calculateRswFromFlash(system, temperatureK, pressureBara);
  }

  /**
   * <p>
   * Calculate Rsw using Electrolyte CPA method.
   * </p>
   *
   * <p>
   * Uses the CPA equation of state with electrolyte extensions for accurate modeling of
   * hydrocarbon-water-salt systems.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return Rsw in Sm³ gas / Sm³ water
   */
  private double calculateRswElectrolyteCPA(double temperatureK, double pressureBara) {
    SystemInterface system;

    // Use electrolyte CPA if salinity > 0, otherwise use regular CPA
    if (salinity > 0) {
      system = new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);
    } else {
      system = new SystemSrkCPAstatoil(temperatureK, pressureBara);
    }

    // Add components from the input gas system
    addGasComponentsToSystem(system);

    // Add water
    system.addComponent("water", 100.0);

    // Add NaCl as ions if using electrolyte system
    if (salinity > 0) {
      double molesWater = 100.0;
      double kgWater = molesWater * 18.015 / 1000.0; // kg
      double molesNaCl = salinity * kgWater;
      system.addComponent("Na+", molesNaCl);
      system.addComponent("Cl-", molesNaCl);
    }

    // Create database and set mixing rule
    system.createDatabase(true);
    system.setMixingRule(10); // CPA mixing rule
    system.setMultiPhaseCheck(true);

    // Run flash calculation
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Calculate Rsw from phase compositions
    return calculateRswFromFlash(system, temperatureK, pressureBara);
  }

  /**
   * <p>
   * Add gas components from the base system to a new system.
   * </p>
   *
   * @param targetSystem the system to add components to
   */
  private void addGasComponentsToSystem(SystemInterface targetSystem) {
    SystemInterface sourceSystem = getBaseThermoSystem();

    // Calculate total moles for normalization
    double totalSourceMoles = sourceSystem.getTotalNumberOfMoles();
    if (totalSourceMoles <= 0) {
      totalSourceMoles = 0.0;
      for (int i = 0; i < sourceSystem.getNumberOfComponents(); i++) {
        totalSourceMoles += sourceSystem.getComponent(i).getNumberOfmoles();
      }
    }

    for (int i = 0; i < sourceSystem.getNumberOfComponents(); i++) {
      String componentName = sourceSystem.getComponent(i).getComponentName();
      double moles = sourceSystem.getComponent(i).getNumberOfmoles();
      // Skip water as we add it separately
      if (!componentName.equalsIgnoreCase("water") && !componentName.equalsIgnoreCase("H2O")) {
        // Scale to reasonable amounts (use fraction * 10 for numerical stability)
        double scaledMoles = (moles / totalSourceMoles) * 10.0;
        targetSystem.addComponent(componentName, scaledMoles);
      }
    }
  }

  /**
   * <p>
   * Calculate Rsw from flash calculation results.
   * </p>
   *
   * <p>
   * Rsw = (moles of dissolved gas in aqueous phase at reservoir conditions) converted to standard
   * conditions volume / (volume of water at standard conditions)
   * </p>
   *
   * @param system the flashed system
   * @param temperatureK reservoir temperature in Kelvin
   * @param pressureBara reservoir pressure in bara
   * @return Rsw in Sm³ gas / Sm³ water
   */
  private double calculateRswFromFlash(SystemInterface system, double temperatureK,
      double pressureBara) {
    // Find aqueous phase
    int aqueousPhaseIndex = -1;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i).getType() == PhaseType.AQUEOUS) {
        aqueousPhaseIndex = i;
        break;
      }
    }

    if (aqueousPhaseIndex < 0) {
      // Try to find by water content - look for phase with highest water mole fraction
      double maxWaterFrac = 0.0;
      for (int i = 0; i < system.getNumberOfPhases(); i++) {
        for (int j = 0; j < system.getPhase(i).getNumberOfComponents(); j++) {
          String compName = system.getPhase(i).getComponent(j).getComponentName();
          if (compName.equalsIgnoreCase("water") || compName.equalsIgnoreCase("H2O")) {
            double waterMoleFrac = system.getPhase(i).getComponent(j).getx();
            if (waterMoleFrac > maxWaterFrac && waterMoleFrac > 0.5) {
              maxWaterFrac = waterMoleFrac;
              aqueousPhaseIndex = i;
            }
            break;
          }
        }
      }
    }

    if (aqueousPhaseIndex < 0) {
      return 0.0; // No aqueous phase found
    }

    // Calculate mole fractions of dissolved gas in aqueous phase
    double waterMoleFrac = 0.0;
    double gasMoleFrac = 0.0;

    for (int i = 0; i < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); i++) {
      String compName = system.getPhase(aqueousPhaseIndex).getComponent(i).getComponentName();
      double x = system.getPhase(aqueousPhaseIndex).getComponent(i).getx();

      if (compName.equalsIgnoreCase("water") || compName.equalsIgnoreCase("H2O")) {
        waterMoleFrac += x;
      } else if (!compName.equalsIgnoreCase("Na+") && !compName.equalsIgnoreCase("Cl-")
          && !compName.equalsIgnoreCase("NaCl")) {
        gasMoleFrac += x;
      }
    }

    if (waterMoleFrac <= 0 || gasMoleFrac <= 0) {
      return 0.0;
    }

    // Calculate molar ratio of gas to water in aqueous phase
    double gasToWaterMolarRatio = gasMoleFrac / waterMoleFrac;

    // Calculate volumes at standard conditions
    // Molar volume of ideal gas at standard conditions (15°C, 1.01325 bar)
    double stdTemp = ThermodynamicConstantsInterface.standardStateTemperature; // 288.15 K
    double stdPres = ThermodynamicConstantsInterface.referencePressure; // 1.01325 bar
    // V = nRT/P, for 1 mol: V = RT/P, where P must be in Pa
    // 1 bar = 100000 Pa, R = 8.314 J/(mol·K) = 8.314 Pa·m³/(mol·K)
    double stdPressPa = stdPres * 100000.0; // Convert bar to Pa
    double molarVolumeGasStd = ThermodynamicConstantsInterface.R * stdTemp / stdPressPa; // m³/mol

    // Molar volume of water at standard conditions
    // Water density ≈ 1000 kg/m³, molar mass = 18.015 g/mol = 0.018015 kg/mol
    // Molar volume = molar mass / density = 0.018015 / 1000 = 0.000018015 m³/mol
    double molarVolumeWaterStd = 0.018015 / 1000.0; // m³/mol

    // Rsw = (moles gas / moles water) * (molar volume gas / molar volume water)
    // Units: (mol/mol) * (m³/mol) / (m³/mol) = Sm³/Sm³
    double rswValue = gasToWaterMolarRatio * molarVolumeGasStd / molarVolumeWaterStd;

    return Math.max(0.0, rswValue);
  }

  /**
   * <p>
   * Get the calculated Rsw values.
   * </p>
   *
   * @return array of Rsw values in Sm³ gas / Sm³ water
   */
  public double[] getRsw() {
    return rsw;
  }

  /**
   * <p>
   * Get Rsw at a specific index.
   * </p>
   *
   * @param index the index
   * @return Rsw value
   */
  public double getRsw(int index) {
    if (rsw == null || index < 0 || index >= rsw.length) {
      throw new IndexOutOfBoundsException("Invalid Rsw index: " + index);
    }
    return rsw[index];
  }

  /**
   * <p>
   * Print results to console.
   * </p>
   */
  public void printResults() {
    System.out.println("Solution Gas-Water Ratio (Rsw) Calculation Results");
    System.out.println("Method: " + method.name());
    System.out.println(
        "Salinity: " + salinity + " mol/kg water (" + getSalinityWtPercent() + " wt% NaCl)");
    System.out.println("--------------------------------------------------");
    System.out.printf("%-12s %-12s %-15s%n", "T (K)", "P (bara)", "Rsw (Sm3/Sm3)");
    System.out.println("--------------------------------------------------");
    for (int i = 0; i < pressures.length; i++) {
      System.out.printf("%-12.2f %-12.2f %-15.6f%n", temperatures[i], pressures[i], rsw[i]);
    }
  }

  /**
   * <p>
   * Example main method demonstrating usage.
   * </p>
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // Create a simple gas system (reservoir gas composition)
    SystemInterface gas = new SystemSrkCPAstatoil(373.15, 200.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("propane", 0.04);
    gas.addComponent("CO2", 0.03);
    gas.setMixingRule(10);

    // Create Rsw calculator
    SolutionGasWaterRatio rswCalc = new SolutionGasWaterRatio(gas);

    // Set salinity (3.5 wt% NaCl - typical seawater)
    rswCalc.setSalinity(3.5, "wt%");

    // Set temperature and pressure points
    double[] temps = {373.15, 373.15, 373.15, 373.15, 373.15};
    double[] pres = {200.0, 150.0, 100.0, 50.0, 10.0};
    rswCalc.setTemperaturesAndPressures(temps, pres);

    // Calculate using McCain correlation
    System.out.println("\n=== McCain (Culberson-McKetta) Correlation ===");
    rswCalc.setCalculationMethod(CalculationMethod.MCCAIN);
    rswCalc.runCalc();
    rswCalc.printResults();

    // Calculate using Søreide-Whitson method
    System.out.println("\n=== Soreide-Whitson Method ===");
    rswCalc.setCalculationMethod(CalculationMethod.SOREIDE_WHITSON);
    rswCalc.runCalc();
    rswCalc.printResults();

    // Calculate using Electrolyte CPA method
    System.out.println("\n=== Electrolyte CPA Method ===");
    rswCalc.setCalculationMethod(CalculationMethod.ELECTROLYTE_CPA);
    rswCalc.runCalc();
    rswCalc.printResults();
  }
}
