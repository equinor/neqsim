package neqsim.process.mechanicaldesign.electrolyzer;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Mechanical design for water electrolyzers (PEM and alkaline).
 *
 * <p>
 * Covers stack configuration, membrane/electrode area sizing, power consumption estimation,
 * balance-of-plant equipment sizing, and weight/cost estimation. Applicable to both PEM
 * (proton-exchange-membrane) and alkaline electrolyzers.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class ElectrolyzerMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Faraday constant in C/mol. */
  private static final double FARADAY = 96485.3329;

  /** Molar mass of water in kg/mol. */
  private static final double MW_WATER = 0.018015;

  /** Molar mass of hydrogen in kg/mol. */
  private static final double MW_H2 = 0.002016;

  // ============================================================================
  // Design Parameters
  // ============================================================================

  /** Electrolyzer type: "PEM" or "ALKALINE". */
  private String electrolyzerType = "PEM";

  /** Cell voltage in V. */
  private double cellVoltage = 1.8;

  /** Current density in A/cm2. */
  private double currentDensity = 1.5;

  /** Cell active area in cm2 per cell. */
  private double cellActiveArea = 1500.0;

  /** Number of cells per stack. */
  private int cellsPerStack = 0;

  /** Number of stacks. */
  private int numberOfStacks = 0;

  /** Stack efficiency (HHV basis). */
  private double stackEfficiency = 0.0;

  /** Total power consumption in kW. */
  private double totalPowerKW = 0.0;

  /** Specific energy consumption in kWh/kgH2. */
  private double specificEnergyKWhPerKg = 0.0;

  /** Hydrogen production rate in kg/hr. */
  private double h2ProductionRateKgHr = 0.0;

  /** Total membrane area in m2. */
  private double totalMembraneArea = 0.0;

  /** Stack operating pressure in bara. */
  private double stackPressure = 30.0;

  /** Stack operating temperature in Celsius. */
  private double stackTemperatureC = 80.0;

  /** Design pressure in bara. */
  private double designPressure = 0.0;

  /** Estimated stack weight in kg. */
  private double stackWeightKg = 0.0;

  /** Estimated total system weight in kg (including BOP). */
  private double totalSystemWeightKg = 0.0;

  /** Water consumption in kg/hr. */
  private double waterConsumptionKgHr = 0.0;

  /** Module footprint in m2. */
  private double moduleFootprintM2 = 0.0;

  /**
   * Constructor for ElectrolyzerMechanicalDesign.
   *
   * @param equipment the electrolyzer equipment
   */
  public ElectrolyzerMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Electrolyzer electrolyzer = (Electrolyzer) getProcessEquipment();

    // Get hydrogen production target from outlet stream if available
    if (electrolyzer.getHydrogenOutStream() != null
        && electrolyzer.getHydrogenOutStream().getThermoSystem() != null) {
      h2ProductionRateKgHr = electrolyzer.getHydrogenOutStream().getFlowRate("kg/hr");
    }

    if (h2ProductionRateKgHr <= 0) {
      return;
    }

    // Get cell voltage from equipment
    cellVoltage = electrolyzer.getCellVoltage();
    if (cellVoltage <= 0) {
      cellVoltage = 1.8;
    }

    // === Electrochemistry ===
    // 2 H2O -> 2 H2 + O2
    // Each mole H2 requires 2 Faradays of charge
    double h2MolesPerSec = (h2ProductionRateKgHr / 3600.0) / MW_H2;
    double totalCurrentA = h2MolesPerSec * 2.0 * FARADAY;

    // === Stack Sizing ===
    // Required active area = total current / current density
    double currentDensityAcm2 = currentDensity;
    double requiredAreaCm2 = totalCurrentA / currentDensityAcm2;
    totalMembraneArea = requiredAreaCm2 / 1.0e4; // m2

    // Cells per stack (limited by manufacturing and voltage)
    int maxCellsPerStack = 200;
    double cellCurrentA = cellActiveArea * currentDensityAcm2;
    int totalCells = (int) Math.ceil(totalCurrentA / cellCurrentA);
    numberOfStacks = Math.max(1, (int) Math.ceil((double) totalCells / maxCellsPerStack));
    cellsPerStack = (int) Math.ceil((double) totalCells / numberOfStacks);

    // === Power and Efficiency ===
    totalPowerKW = totalCurrentA * cellVoltage * totalCells / totalCurrentA * cellCurrentA / 1000.0;
    // Simplified: P = n_cells * V_cell * I_cell
    totalPowerKW = totalCells * cellVoltage * cellCurrentA / 1000.0;
    specificEnergyKWhPerKg = totalPowerKW / (h2ProductionRateKgHr);

    // HHV of H2 = 39.4 kWh/kg
    stackEfficiency = 39.4 / specificEnergyKWhPerKg;
    // Cap at 1.0 — efficiency > 100% is not physically achievable
    stackEfficiency = Math.min(stackEfficiency, 1.0);

    // === Water Consumption ===
    // Stoichiometric: 9 kg water per kg H2
    // Practical: ~10-11 kg water per kg H2
    waterConsumptionKgHr = h2ProductionRateKgHr * 10.5;

    // === Pressure Design ===
    designPressure = stackPressure * 1.10;

    // === Weight Estimation ===
    if ("PEM".equals(electrolyzerType)) {
      // PEM: ~5 kg/kW typical for stack only
      stackWeightKg = totalPowerKW * 5.0;
    } else {
      // Alkaline: ~8 kg/kW typical
      stackWeightKg = totalPowerKW * 8.0;
    }
    // Balance of plant (BOP) adds ~60-80%
    totalSystemWeightKg = stackWeightKg * 1.7;

    // === Module Dimensions ===
    // Typical PEM: ~0.02 m2/kW
    moduleFootprintM2 = totalPowerKW * 0.02;
    moduleLength = Math.sqrt(moduleFootprintM2 * 2.0);
    moduleWidth = moduleFootprintM2 / moduleLength;
    moduleHeight = 3.0; // standard container height

    // === Set base class fields ===
    setMaxDesignPower(totalPowerKW);
    setWeightTotal(totalSystemWeightKg);
    setMaxOperationPressure(stackPressure);
  }

  /**
   * Gets the electrolyzer type.
   *
   * @return "PEM" or "ALKALINE"
   */
  public String getElectrolyzerType() {
    return electrolyzerType;
  }

  /**
   * Sets the electrolyzer type.
   *
   * @param type "PEM" or "ALKALINE"
   */
  public void setElectrolyzerType(String type) {
    this.electrolyzerType = type;
  }

  /**
   * Gets the current density.
   *
   * @return current density in A/cm2
   */
  public double getCurrentDensity() {
    return currentDensity;
  }

  /**
   * Sets the current density.
   *
   * @param currentDensity current density in A/cm2
   */
  public void setCurrentDensity(double currentDensity) {
    this.currentDensity = currentDensity;
  }

  /**
   * Gets the number of cells per stack.
   *
   * @return cells per stack
   */
  public int getCellsPerStack() {
    return cellsPerStack;
  }

  /**
   * Gets the number of stacks.
   *
   * @return number of stacks
   */
  public int getNumberOfStacks() {
    return numberOfStacks;
  }

  /**
   * Gets the total power consumption.
   *
   * @return power in kW
   */
  public double getTotalPowerKW() {
    return totalPowerKW;
  }

  /**
   * Gets the specific energy consumption.
   *
   * @return kWh per kg H2
   */
  public double getSpecificEnergyKWhPerKg() {
    return specificEnergyKWhPerKg;
  }

  /**
   * Gets the stack efficiency (HHV basis).
   *
   * @return efficiency (0-1)
   */
  public double getStackEfficiency() {
    return stackEfficiency;
  }

  /**
   * Gets the H2 production rate.
   *
   * @return rate in kg/hr
   */
  public double getH2ProductionRateKgHr() {
    return h2ProductionRateKgHr;
  }

  /**
   * Sets the H2 production rate target.
   *
   * @param rate rate in kg/hr
   */
  public void setH2ProductionRateKgHr(double rate) {
    this.h2ProductionRateKgHr = rate;
  }

  /**
   * Gets the total membrane area.
   *
   * @return area in m2
   */
  public double getTotalMembraneArea() {
    return totalMembraneArea;
  }

  /**
   * Gets the water consumption rate.
   *
   * @return rate in kg/hr
   */
  public double getWaterConsumptionKgHr() {
    return waterConsumptionKgHr;
  }

  /**
   * Gets the total system weight.
   *
   * @return weight in kg
   */
  public double getTotalSystemWeightKg() {
    return totalSystemWeightKg;
  }

  /**
   * Sets the cell active area.
   *
   * @param area area in cm2
   */
  public void setCellActiveArea(double area) {
    this.cellActiveArea = area;
  }

  /**
   * Sets the stack operating pressure.
   *
   * @param pressure pressure in bara
   */
  public void setStackPressure(double pressure) {
    this.stackPressure = pressure;
  }

  /**
   * Gets the stack weight.
   *
   * @return stack weight in kg
   */
  public double getStackWeightKg() {
    return stackWeightKg;
  }

  /**
   * Gets the module footprint.
   *
   * @return footprint in m2
   */
  public double getModuleFootprintM2() {
    return moduleFootprintM2;
  }
}
