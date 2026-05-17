package neqsim.thermo.util.amines;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Convenience wrapper for creating and configuring amine thermodynamic systems.
 *
 * <p>
 * Provides a simplified API for setting up MEA, DEA, MDEA, and aMDEA (activated MDEA with
 * piperazine) systems with proper components, reactions, mixing rules, and physical property
 * models. This replaces the simplistic Kent-Eisenberg approach with a rigorous electrolyte-CPA
 * model that supports:
 * </p>
 * <ul>
 * <li>VLE with speciation (molecular and ionic species)</li>
 * <li>Heat of reaction from the thermodynamic model</li>
 * <li>Viscosity via Weiland et al. (1998) correlations for loaded solutions</li>
 * <li>CO2 and H2S acid gas absorption</li>
 * </ul>
 *
 * <p>
 * Supported amine systems and their components:
 * </p>
 *
 * <table>
 * <caption>Amine systems and their required components</caption>
 * <tr>
 * <th>Amine</th>
 * <th>Neutral</th>
 * <th>Protonated</th>
 * <th>Carbamate</th>
 * <th>Max Loading</th>
 * </tr>
 * <tr>
 * <td>MEA</td>
 * <td>MEA</td>
 * <td>MEA+</td>
 * <td>MEACOO-</td>
 * <td>0.5</td>
 * </tr>
 * <tr>
 * <td>DEA</td>
 * <td>DEA</td>
 * <td>DEA+</td>
 * <td>DEACOO-</td>
 * <td>0.5</td>
 * </tr>
 * <tr>
 * <td>MDEA</td>
 * <td>MDEA</td>
 * <td>MDEA+</td>
 * <td>(none)</td>
 * <td>1.0</td>
 * </tr>
 * <tr>
 * <td>aMDEA</td>
 * <td>MDEA, Piperazine</td>
 * <td>MDEA+, Piperazine+</td>
 * <td>PZCOO-</td>
 * <td>1.0</td>
 * </tr>
 * </table>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AmineSystem implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AmineSystem.class);

  /**
   * Supported amine types.
   */
  public enum AmineType {
    /** Monoethanolamine (primary amine, fast kinetics, high heat of absorption). */
    MEA,
    /** Diethanolamine (secondary amine, moderate kinetics). */
    DEA,
    /** Methyldiethanolamine (tertiary amine, low heat, high capacity). */
    MDEA,
    /** Activated MDEA (MDEA + Piperazine blend, combines fast kinetics with high capacity). */
    AMDEA
  }

  /** Common ionic species required for all amine systems. */
  private static final List<String> COMMON_IONS =
      Collections.unmodifiableList(Arrays.asList("H3O+", "OH-", "HCO3-", "CO3--"));

  /**
   * Map of amine type to their required species (neutral amine, ions, carbamate).
   */
  private static final Map<AmineType, List<String>> AMINE_SPECIES;

  static {
    Map<AmineType, List<String>> map = new HashMap<AmineType, List<String>>();
    map.put(AmineType.MEA, Arrays.asList("MEA", "MEA+", "MEACOO-"));
    map.put(AmineType.DEA, Arrays.asList("DEA", "DEA+", "DEACOO-"));
    map.put(AmineType.MDEA, Arrays.asList("MDEA", "MDEA+"));
    map.put(AmineType.AMDEA, Arrays.asList("MDEA", "MDEA+", "Piperazine", "Piperazine+", "PZCOO-"));
    AMINE_SPECIES = Collections.unmodifiableMap(map);
  }

  private SystemInterface system;
  private AmineType amineType;
  private double temperature; // Kelvin
  private double pressure; // bara
  private double amineMolFraction;
  private double co2MolFraction;
  private double h2sMolFraction;
  private double waterMolFraction;
  private double piperazineMolFraction;
  private boolean hasH2S = false;
  private boolean initialized = false;
  private AmineHeatOfAbsorption heatCalc;

  /**
   * Creates an amine system with default conditions (40 C, 1.01325 bara).
   *
   * @param amineType the type of amine to use
   */
  public AmineSystem(AmineType amineType) {
    this(amineType, 273.15 + 40.0, 1.01325);
  }

  /**
   * Creates an amine system at specified conditions.
   *
   * @param amineType the type of amine to use
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   */
  public AmineSystem(AmineType amineType, double temperatureK, double pressureBara) {
    this.amineType = amineType;
    this.temperature = temperatureK;
    this.pressure = pressureBara;
    this.heatCalc = new AmineHeatOfAbsorption();
    configureHeatCalcType();
  }

  /**
   * Configures the AmineHeatOfAbsorption calculator for the current amine type.
   */
  private void configureHeatCalcType() {
    switch (amineType) {
      case MEA:
        heatCalc.setAmineType(AmineHeatOfAbsorption.AmineType.MEA);
        break;
      case DEA:
        heatCalc.setAmineType(AmineHeatOfAbsorption.AmineType.DEA);
        break;
      case MDEA:
        heatCalc.setAmineType(AmineHeatOfAbsorption.AmineType.MDEA);
        break;
      case AMDEA:
        heatCalc.setAmineType(AmineHeatOfAbsorption.AmineType.AMDEA);
        break;
      default:
        heatCalc.setAmineType(AmineHeatOfAbsorption.AmineType.MEA);
    }
  }

  /**
   * Sets the amine concentration as mass fraction of the aqueous solution.
   *
   * <p>
   * Calculates required mole fractions of amine and water from the mass fraction. Common values:
   * MEA 30 wt%, DEA 30-50 wt%, MDEA 50 wt%, aMDEA 45/5 wt%.
   * </p>
   *
   * @param massFraction mass fraction (0 to 1, e.g. 0.30 for 30 wt%)
   */
  public void setAmineConcentration(double massFraction) {
    double amineMW = getAmineMolarMass();
    double waterMW = 18.015;

    // For aMDEA, massFraction is the total amine mass fraction (MDEA + PZ)
    amineMolFraction =
        (massFraction / amineMW) / (massFraction / amineMW + (1.0 - massFraction) / waterMW);
    waterMolFraction = 1.0 - amineMolFraction;

    heatCalc.setAmineConcentration(massFraction);
  }

  /**
   * Sets the CO2 loading in moles CO2 per mole amine.
   *
   * @param loading CO2 loading (dimensionless, typically 0 to 0.5 for MEA/DEA, 0 to 1.0 for MDEA)
   */
  public void setCO2Loading(double loading) {
    this.co2MolFraction = loading * amineMolFraction;
    heatCalc.setCO2Loading(loading);
  }

  /**
   * Sets the H2S loading in moles H2S per mole amine.
   *
   * @param loading H2S loading (dimensionless)
   */
  public void setH2SLoading(double loading) {
    this.h2sMolFraction = loading * amineMolFraction;
    this.hasH2S = loading > 0;
  }

  /**
   * Sets the piperazine concentration for aMDEA systems.
   *
   * <p>
   * Only applicable when amineType is AMDEA. Specifies the piperazine mass fraction of the total
   * solution.
   * </p>
   *
   * @param massFraction piperazine mass fraction (e.g. 0.05 for 5 wt%)
   */
  public void setPiperazineConcentration(double massFraction) {
    double pzMW = 86.14;
    double waterMW = 18.015;
    piperazineMolFraction =
        (massFraction / pzMW) / (massFraction / pzMW + (1.0 - massFraction) / waterMW);
  }

  /**
   * Creates and returns the configured thermodynamic system.
   *
   * <p>
   * Uses the electrolyte-CPA EOS (SystemElectrolyteCPAstatoil) which provides rigorous VLE with
   * speciation, including:
   * </p>
   * <ul>
   * <li>SRK cubic equation of state for non-ideal gas behavior</li>
   * <li>CPA association term for hydrogen bonding (water, amines)</li>
   * <li>Electrostatic terms (Born, MSA) for ionic interactions</li>
   * <li>Chemical equilibrium for acid gas reactions</li>
   * </ul>
   *
   * @return the configured {@link SystemInterface} ready for flash calculations
   */
  public SystemInterface createSystem() {
    system = new SystemElectrolyteCPAstatoil(temperature, pressure);

    // Add acid gas species
    if (co2MolFraction > 0) {
      system.addComponent("CO2", co2MolFraction);
    }
    if (h2sMolFraction > 0) {
      system.addComponent("H2S", h2sMolFraction);
    }

    // Add water
    double effectiveWater = waterMolFraction;
    if (effectiveWater <= 0) {
      effectiveWater = 0.7; // default if not set
    }
    system.addComponent("water", effectiveWater);

    // Add amine species
    List<String> species = AMINE_SPECIES.get(amineType);
    if (species != null) {
      for (String comp : species) {
        double molFrac = getInitialMolFraction(comp);
        if (molFrac > 0) {
          system.addComponent(comp, molFrac);
        }
      }
    }

    // Add common ionic species
    for (String ion : COMMON_IONS) {
      system.addComponent(ion, 1.0e-20);
    }

    // Add H2S ionic species if needed
    if (hasH2S) {
      system.addComponent("HS-", 1.0e-20);
      system.addComponent("S--", 1.0e-20);
    }

    // Enable chemical reactions
    system.chemicalReactionInit();

    // Set electrolyte CPA mixing rule
    system.setMixingRule(10);

    // Set physical property model to amine
    system.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);

    initialized = true;
    return system;
  }

  /**
   * Gets the initial mole fraction for a given component based on the system configuration.
   *
   * @param componentName the name of the component
   * @return the initial mole fraction
   */
  private double getInitialMolFraction(String componentName) {
    switch (amineType) {
      case MEA:
        if ("MEA".equals(componentName)) {
          return amineMolFraction;
        }
        break;
      case DEA:
        if ("DEA".equals(componentName)) {
          return amineMolFraction;
        }
        break;
      case MDEA:
        if ("MDEA".equals(componentName)) {
          return amineMolFraction;
        }
        break;
      case AMDEA:
        if ("MDEA".equals(componentName)) {
          return amineMolFraction;
        }
        if ("Piperazine".equals(componentName)) {
          return piperazineMolFraction;
        }
        break;
      default:
        break;
    }
    // Ions start at trace amounts
    return 1.0e-20;
  }

  /**
   * Runs a TP flash calculation on the system.
   *
   * @return the resulting thermodynamic system after flash
   */
  public SystemInterface runTPflash() {
    if (!initialized || system == null) {
      createSystem();
    }
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    return system;
  }

  /**
   * Runs a bubble point pressure calculation.
   *
   * @return the bubble point pressure in bara
   */
  public double calcBubblePointPressure() {
    if (!initialized || system == null) {
      createSystem();
    }
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      throw new RuntimeException("Bubble point pressure flash failed", ex);
    }
    return system.getPressure();
  }

  /**
   * Gets the CO2 equilibrium partial pressure over the loaded solution.
   *
   * <p>
   * Runs a bubble point pressure flash and extracts the CO2 partial pressure from the vapor phase.
   * This is the key quantity for amine absorber/stripper design.
   * </p>
   *
   * @return CO2 partial pressure in bara
   */
  public double getCO2PartialPressure() {
    if (!initialized || system == null) {
      createSystem();
    }
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error("Bubble point flash failed", ex);
      return Double.NaN;
    }
    try {
      return system.getPhase(0).getComponent("CO2").getx() * system.getPressure();
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Gets the pH of the loaded amine solution.
   *
   * @return the pH value
   */
  public double getpH() {
    if (!initialized || system == null) {
      runTPflash();
    }
    try {
      return system.getPhase(1).getpH();
    } catch (Exception e) {
      logger.error("pH calculation failed", e);
      return Double.NaN;
    }
  }

  /**
   * Gets the heat of absorption of CO2 at current conditions.
   *
   * @return heat of absorption in kJ/mol CO2 (negative for exothermic)
   */
  public double getHeatOfAbsorptionCO2() {
    heatCalc.setTemperature(temperature);
    return heatCalc.calcHeatOfAbsorptionCO2();
  }

  /**
   * Gets the heat of absorption of H2S at current conditions.
   *
   * @return heat of absorption in kJ/mol H2S (negative for exothermic)
   */
  public double getHeatOfAbsorptionH2S() {
    heatCalc.setTemperature(temperature);
    return heatCalc.calcHeatOfAbsorptionH2S();
  }

  /**
   * Gets the total heat released for the current CO2 loading.
   *
   * @return total heat in kJ per mol amine (positive = heat released)
   */
  public double getTotalHeatReleased() {
    heatCalc.setTemperature(temperature);
    return heatCalc.calcTotalHeatReleased();
  }

  /**
   * Gets the underlying thermodynamic system.
   *
   * @return the SystemInterface, or null if not yet created
   */
  public SystemInterface getSystem() {
    return system;
  }

  /**
   * Gets the amine type.
   *
   * @return the amine type
   */
  public AmineType getAmineType() {
    return amineType;
  }

  /**
   * Gets the heat of absorption calculator.
   *
   * @return the AmineHeatOfAbsorption instance
   */
  public AmineHeatOfAbsorption getHeatCalculator() {
    return heatCalc;
  }

  /**
   * Gets the molar mass of the primary amine.
   *
   * @return molar mass in g/mol
   */
  private double getAmineMolarMass() {
    switch (amineType) {
      case MEA:
        return 61.08;
      case DEA:
        return 105.14;
      case MDEA:
        return 119.16;
      case AMDEA:
        return 119.16; // MDEA is the primary amine
      default:
        return 119.16;
    }
  }

  /**
   * Returns a formatted summary of the amine system properties.
   *
   * @return multi-line string with system configuration and key properties
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("AmineSystem [").append(amineType.name()).append("]\n");
    sb.append("Temperature: ").append(String.format("%.1f", temperature - 273.15)).append(" C\n");
    sb.append("Pressure: ").append(String.format("%.2f", pressure)).append(" bara\n");
    sb.append("Amine mol fraction: ").append(String.format("%.4f", amineMolFraction)).append("\n");
    if (co2MolFraction > 0) {
      sb.append("CO2 mol fraction: ").append(String.format("%.4f", co2MolFraction)).append("\n");
    }
    if (h2sMolFraction > 0) {
      sb.append("H2S mol fraction: ").append(String.format("%.4f", h2sMolFraction)).append("\n");
    }
    sb.append("Heat of absorption CO2: ").append(String.format("%.1f", getHeatOfAbsorptionCO2()))
        .append(" kJ/mol\n");
    return sb.toString();
  }
}
