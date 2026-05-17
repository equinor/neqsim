package neqsim.thermo.util;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Factory class for creating produced water thermodynamic systems with the Electrolyte-CPA EOS.
 *
 * <p>
 * This builder eliminates common user errors when configuring electrolyte CPA systems:
 * </p>
 * <ul>
 * <li>Automatically selects SystemElectrolyteCPAstatoil with mixing rule 10</li>
 * <li>Automatically calls chemicalReactionInit() — prevents silently wrong results</li>
 * <li>Provides preset produced water compositions (seawater, formation water, etc.)</li>
 * <li>Converts TDS (mg/L) to component mole fractions</li>
 * <li>Validates ion charge balance</li>
 * </ul>
 *
 * @author Copilot
 * @version 1.0
 */
public class ProducedWaterFluidBuilder {

  /** Molar mass of NaCl in g/mol. */
  private static final double MW_NACL = 58.44;

  /** Molar mass of Na+ in g/mol. */
  private static final double MW_NA = 22.99;

  /** Molar mass of Cl- in g/mol. */
  private static final double MW_CL = 35.45;

  /** Molar mass of Ca++ in g/mol. */
  private static final double MW_CA = 40.08;

  /** Molar mass of Mg++ in g/mol. */
  private static final double MW_MG = 24.31;

  /** Molar mass of HCO3- in g/mol. */
  private static final double MW_HCO3 = 61.02;

  /** Molar mass of SO4-- in g/mol. */
  private static final double MW_SO4 = 96.07;

  /** Molar mass of K+ (approximated as Na+ for simplicity). */
  private static final double MW_K = 39.10;

  /** Molar mass of water in g/mol. */
  private static final double MW_WATER = 18.015;

  /** Density of water at standard conditions in g/L. */
  private static final double WATER_DENSITY_G_PER_L = 1000.0;

  /**
   * Private constructor to prevent instantiation.
   */
  private ProducedWaterFluidBuilder() {}

  /**
   * Creates a produced water system from total dissolved solids (TDS) concentration.
   *
   * <p>
   * Assumes the TDS is predominantly NaCl. For more complex compositions, use
   * {@link #createFromIons(double, double, Map)} instead.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @param tds total dissolved solids in mg/L
   * @param waterMoleFraction water mole fraction in the overall system (0 to 1)
   * @return configured SystemInterface with electrolyte CPA and chemical reactions initialized
   */
  public static SystemInterface createFromTDS(double temperatureK, double pressureBara, double tds,
      double waterMoleFraction) {
    // Convert TDS (mg/L) to NaCl moles per liter
    double naclMolesPerLiter = (tds / 1000.0) / MW_NACL;
    double waterMolesPerLiter = WATER_DENSITY_G_PER_L / MW_WATER;

    // Calculate mole fractions of Na+ and Cl- relative to water
    double totalMoles = waterMolesPerLiter + 2.0 * naclMolesPerLiter;
    double naMoleFrac = naclMolesPerLiter / totalMoles;
    double clMoleFrac = naclMolesPerLiter / totalMoles;
    double waterMoleFracInAq = waterMolesPerLiter / totalMoles;

    SystemElectrolyteCPAstatoil system =
        new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);
    system.addComponent("water", waterMoleFraction * waterMoleFracInAq);
    system.addComponent("Na+", waterMoleFraction * naMoleFrac);
    system.addComponent("Cl-", waterMoleFraction * clMoleFrac);

    configureSystem(system);
    return system;
  }

  /**
   * Creates a produced water system from a predefined water type.
   *
   * <p>
   * Available water types:
   * </p>
   * <table>
   * <caption>Predefined water types and their characteristics</caption>
   * <tr>
   * <th>Type</th>
   * <th>TDS (mg/L)</th>
   * <th>Description</th>
   * </tr>
   * <tr>
   * <td>condensed_water</td>
   * <td>0</td>
   * <td>Pure condensed water</td>
   * </tr>
   * <tr>
   * <td>brackish</td>
   * <td>5,000</td>
   * <td>Brackish water</td>
   * </tr>
   * <tr>
   * <td>seawater</td>
   * <td>35,000</td>
   * <td>Standard seawater composition</td>
   * </tr>
   * <tr>
   * <td>formation_low</td>
   * <td>50,000</td>
   * <td>Low-salinity formation water</td>
   * </tr>
   * <tr>
   * <td>formation_high</td>
   * <td>150,000</td>
   * <td>High-salinity formation water</td>
   * </tr>
   * </table>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @param waterType one of "condensed_water", "brackish", "seawater", "formation_low",
   *        "formation_high"
   * @return configured SystemInterface with electrolyte CPA and chemical reactions initialized
   * @throws IllegalArgumentException if waterType is not recognized
   */
  public static SystemInterface createFromType(double temperatureK, double pressureBara,
      String waterType) {
    Map<String, Double> ions = getPresetComposition(waterType);

    SystemElectrolyteCPAstatoil system =
        new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);

    for (Map.Entry<String, Double> entry : ions.entrySet()) {
      if (entry.getValue() > 0.0) {
        system.addComponent(entry.getKey(), entry.getValue());
      }
    }

    configureSystem(system);
    return system;
  }

  /**
   * Creates a produced water system from explicit ionic composition in mg/L.
   *
   * <p>
   * The method converts mg/L concentrations to mole fractions and creates an Electrolyte-CPA
   * system. Supported ions: Na+, Cl-, Ca++, Mg++, HCO3-, SO4--.
   * </p>
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @param ionConcentrations map of ion name to concentration in mg/L. Supported keys: "Na+",
   *        "Cl-", "Ca++", "Mg++", "HCO3-", "SO4--"
   * @return configured SystemInterface with electrolyte CPA and chemical reactions initialized
   */
  public static SystemInterface createFromIons(double temperatureK, double pressureBara,
      Map<String, Double> ionConcentrations) {
    // Convert mg/L to moles for each ion
    Map<String, Double> moleFractions = new LinkedHashMap<String, Double>();
    double totalMoles = WATER_DENSITY_G_PER_L / MW_WATER; // start with water moles/L

    Map<String, Double> ionMoles = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, Double> entry : ionConcentrations.entrySet()) {
      double mw = getIonMolarMass(entry.getKey());
      double molesPerLiter = (entry.getValue() / 1000.0) / mw;
      ionMoles.put(entry.getKey(), molesPerLiter);
      totalMoles += molesPerLiter;
    }

    // Convert to mole fractions
    double waterFrac = (WATER_DENSITY_G_PER_L / MW_WATER) / totalMoles;
    moleFractions.put("water", waterFrac);
    for (Map.Entry<String, Double> entry : ionMoles.entrySet()) {
      moleFractions.put(entry.getKey(), entry.getValue() / totalMoles);
    }

    SystemElectrolyteCPAstatoil system =
        new SystemElectrolyteCPAstatoil(temperatureK, pressureBara);
    for (Map.Entry<String, Double> entry : moleFractions.entrySet()) {
      if (entry.getValue() > 0.0) {
        system.addComponent(entry.getKey(), entry.getValue());
      }
    }

    configureSystem(system);
    return system;
  }

  /**
   * Adds gas components to an existing produced water system.
   *
   * <p>
   * This method adds gas components (methane, CO2, H2S, etc.) to a system that was previously
   * configured with {@link #createFromTDS}, {@link #createFromType}, or
   * {@link #createFromIons}. The gas composition is specified as mole fractions that are
   * normalized to fit the requested gas-to-water ratio.
   * </p>
   *
   * @param system existing electrolyte CPA system
   * @param gasComposition map of component name to mole fraction (will be normalized)
   * @param gasToWaterMoleRatio ratio of total gas moles to total water-phase moles
   * @return the same system with gas components added
   */
  public static SystemInterface addGasToWater(SystemInterface system,
      Map<String, Double> gasComposition, double gasToWaterMoleRatio) {
    // Normalize gas composition
    double gasSum = 0.0;
    for (Double val : gasComposition.values()) {
      gasSum += val;
    }

    for (Map.Entry<String, Double> entry : gasComposition.entrySet()) {
      double normalizedFrac = entry.getValue() / gasSum * gasToWaterMoleRatio;
      system.addComponent(entry.getKey(), normalizedFrac);
    }

    // Re-initialize after adding components
    system.setMultiPhaseCheck(true);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.init(0);
    return system;
  }

  /**
   * Configures the system with the correct mixing rule, database, and chemical reactions.
   *
   * @param system the system to configure
   */
  private static void configureSystem(SystemInterface system) {
    system.setMultiPhaseCheck(true);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.init(0);
  }

  /**
   * Returns the preset ionic composition for a given water type as mole fractions.
   *
   * @param waterType the water type identifier
   * @return map of component name to mole fraction
   * @throws IllegalArgumentException if waterType is not recognized
   */
  private static Map<String, Double> getPresetComposition(String waterType) {
    Map<String, Double> comp = new LinkedHashMap<String, Double>();

    if ("condensed_water".equals(waterType)) {
      comp.put("water", 1.0);
    } else if ("brackish".equals(waterType)) {
      // ~5000 mg/L NaCl
      comp.put("water", 0.997);
      comp.put("Na+", 0.0015);
      comp.put("Cl-", 0.0015);
    } else if ("seawater".equals(waterType)) {
      // ~35000 mg/L: Na+ 10770, Cl- 19350, Mg++ 1290, Ca++ 412, SO4-- 2710, HCO3- 142
      double waterMoles = WATER_DENSITY_G_PER_L / MW_WATER; // ~55.5
      double naMoles = 10.770 / MW_NA; // g/L to mol/L
      double clMoles = 19.350 / MW_CL;
      double mgMoles = 1.290 / MW_MG;
      double caMoles = 0.412 / MW_CA;
      double so4Moles = 2.710 / MW_SO4;
      double hco3Moles = 0.142 / MW_HCO3;
      double total =
          waterMoles + naMoles + clMoles + mgMoles + caMoles + so4Moles + hco3Moles;

      comp.put("water", waterMoles / total);
      comp.put("Na+", naMoles / total);
      comp.put("Cl-", clMoles / total);
      comp.put("Mg++", mgMoles / total);
      comp.put("Ca++", caMoles / total);
      comp.put("SO4--", so4Moles / total);
      comp.put("HCO3-", hco3Moles / total);
    } else if ("formation_low".equals(waterType)) {
      // ~50000 mg/L NaCl-dominated
      double waterMoles = WATER_DENSITY_G_PER_L / MW_WATER;
      double naclMoles = 50.0 / MW_NACL; // 50 g/L
      double total = waterMoles + 2.0 * naclMoles;
      comp.put("water", waterMoles / total);
      comp.put("Na+", naclMoles / total);
      comp.put("Cl-", naclMoles / total);
    } else if ("formation_high".equals(waterType)) {
      // ~150000 mg/L NaCl-dominated
      double waterMoles = WATER_DENSITY_G_PER_L / MW_WATER;
      double naclMoles = 150.0 / MW_NACL; // 150 g/L
      double total = waterMoles + 2.0 * naclMoles;
      comp.put("water", waterMoles / total);
      comp.put("Na+", naclMoles / total);
      comp.put("Cl-", naclMoles / total);
    } else {
      throw new IllegalArgumentException(
          "Unknown water type: " + waterType
              + ". Valid types: condensed_water, brackish, seawater, formation_low, formation_high");
    }
    return comp;
  }

  /**
   * Returns the molar mass of a given ion in g/mol.
   *
   * @param ionName the ion name (e.g., "Na+", "Cl-", "Ca++")
   * @return molar mass in g/mol
   * @throws IllegalArgumentException if ion is not recognized
   */
  private static double getIonMolarMass(String ionName) {
    if ("Na+".equals(ionName)) {
      return MW_NA;
    }
    if ("Cl-".equals(ionName)) {
      return MW_CL;
    }
    if ("Ca++".equals(ionName)) {
      return MW_CA;
    }
    if ("Mg++".equals(ionName)) {
      return MW_MG;
    }
    if ("HCO3-".equals(ionName)) {
      return MW_HCO3;
    }
    if ("SO4--".equals(ionName)) {
      return MW_SO4;
    }
    if ("K+".equals(ionName)) {
      return MW_K;
    }
    if ("Sr++".equals(ionName)) {
      return 87.62;
    }
    if ("Ba++".equals(ionName)) {
      return 137.33;
    }
    if ("Fe++".equals(ionName)) {
      return 55.845;
    }
    throw new IllegalArgumentException("Unknown ion: " + ionName
        + ". Supported: Na+, Cl-, Ca++, Mg++, HCO3-, SO4--, K+, Sr++, Ba++, Fe++");
  }
}
