package neqsim.process.electricaldesign.system;

import neqsim.process.electricaldesign.loadanalysis.ElectricalLoadList;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Plant-wide electrical design summary and power distribution analysis.
 *
 * <p>
 * Aggregates electrical loads across all equipment in a {@link ProcessSystem}, calculates power
 * distribution requirements, and sizes the main incoming transformer and emergency/essential power
 * systems. This class complements equipment-level electrical designs by providing a system-level
 * view.
 * </p>
 *
 * <p>
 * Typical plant-wide additions beyond equipment loads:
 * </p>
 * <ul>
 * <li><b>Utility loads:</b> HVAC, plant lighting, fire and gas detection (5-10% of process
 * load)</li>
 * <li><b>UPS loads:</b> Critical instrumentation and safety systems (1-3% of process load)</li>
 * <li><b>Future expansion margin:</b> Typically 10-20% of total demand</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemElectricalDesign implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private ProcessSystem processSystem;

  // === System-level loads ===
  private double utilityLoadKW = 0.0;
  private double upsLoadKW = 0.0;
  private double futureExpansionFraction = 0.15;

  // === Power distribution ===
  private double mainBusVoltageV = 11000;
  private double distributionVoltageV = 400;
  private double frequencyHz = 50.0;

  // === Calculated values ===
  private double totalProcessLoadKW;
  private double totalPlantLoadKW;
  private double mainTransformerKVA;
  private double emergencyGeneratorKVA;
  private double overallPowerFactor;
  private ElectricalLoadList loadList;

  /**
   * Constructor for SystemElectricalDesign.
   *
   * @param processSystem the process system to analyse
   */
  public SystemElectricalDesign(ProcessSystem processSystem) {
    this.processSystem = processSystem;
  }

  /**
   * Run the system-level electrical design.
   *
   * <p>
   * Calls runAllElectricalDesigns() on the process system, then aggregates loads and sizes the main
   * transformer and emergency power.
   * </p>
   */
  public void calcDesign() {
    // Run all equipment-level designs
    processSystem.runAllElectricalDesigns();

    // Build load list
    loadList = processSystem.getElectricalLoadList();

    totalProcessLoadKW = loadList.getMaximumDemandKW();
    overallPowerFactor = loadList.getOverallPowerFactor();
    if (overallPowerFactor <= 0) {
      overallPowerFactor = 0.85;
    }

    // Add system-level loads
    if (utilityLoadKW <= 0) {
      utilityLoadKW = totalProcessLoadKW * 0.07;
    }
    if (upsLoadKW <= 0) {
      upsLoadKW = totalProcessLoadKW * 0.02;
    }

    totalPlantLoadKW = totalProcessLoadKW + utilityLoadKW + upsLoadKW;

    // Main transformer sizing with expansion margin
    double totalWithMargin = totalPlantLoadKW * (1.0 + futureExpansionFraction);
    mainTransformerKVA = totalWithMargin / overallPowerFactor;

    // Emergency generator sized for essential loads (typically 30-40% of total)
    emergencyGeneratorKVA = totalPlantLoadKW * 0.35 / overallPowerFactor;
  }

  /**
   * Get the electrical load list for all process equipment.
   *
   * @return the load list, or null if calcDesign() has not been called
   */
  public ElectricalLoadList getLoadList() {
    return loadList;
  }

  /**
   * Get total process electrical load (maximum demand) in kW.
   *
   * @return total process load in kW
   */
  public double getTotalProcessLoadKW() {
    return totalProcessLoadKW;
  }

  /**
   * Get total plant electrical load including utility and UPS in kW.
   *
   * @return total plant load in kW
   */
  public double getTotalPlantLoadKW() {
    return totalPlantLoadKW;
  }

  /**
   * Get the required main transformer rating in kVA.
   *
   * @return main transformer kVA
   */
  public double getMainTransformerKVA() {
    return mainTransformerKVA;
  }

  /**
   * Get the required emergency generator rating in kVA.
   *
   * @return emergency generator kVA
   */
  public double getEmergencyGeneratorKVA() {
    return emergencyGeneratorKVA;
  }

  /**
   * Get the overall power factor for the plant.
   *
   * @return overall power factor
   */
  public double getOverallPowerFactor() {
    return overallPowerFactor;
  }

  /**
   * Get utility load in kW.
   *
   * @return utility load in kW
   */
  public double getUtilityLoadKW() {
    return utilityLoadKW;
  }

  /**
   * Set utility load in kW. Set to 0 to use automatic estimation (7% of process load).
   *
   * @param utilityLoadKW utility load in kW
   */
  public void setUtilityLoadKW(double utilityLoadKW) {
    this.utilityLoadKW = utilityLoadKW;
  }

  /**
   * Get UPS load in kW.
   *
   * @return UPS load in kW
   */
  public double getUpsLoadKW() {
    return upsLoadKW;
  }

  /**
   * Set UPS load in kW. Set to 0 to use automatic estimation (2% of process load).
   *
   * @param upsLoadKW UPS load in kW
   */
  public void setUpsLoadKW(double upsLoadKW) {
    this.upsLoadKW = upsLoadKW;
  }

  /**
   * Get future expansion margin fraction.
   *
   * @return expansion fraction (e.g. 0.15 for 15%)
   */
  public double getFutureExpansionFraction() {
    return futureExpansionFraction;
  }

  /**
   * Set future expansion margin fraction.
   *
   * @param futureExpansionFraction expansion fraction (e.g. 0.15 for 15%)
   */
  public void setFutureExpansionFraction(double futureExpansionFraction) {
    this.futureExpansionFraction = futureExpansionFraction;
  }

  /**
   * Get main bus voltage in volts.
   *
   * @return main bus voltage in V
   */
  public double getMainBusVoltageV() {
    return mainBusVoltageV;
  }

  /**
   * Set main bus voltage in volts.
   *
   * @param mainBusVoltageV main bus voltage in V
   */
  public void setMainBusVoltageV(double mainBusVoltageV) {
    this.mainBusVoltageV = mainBusVoltageV;
  }

  /**
   * Get distribution voltage in volts.
   *
   * @return distribution voltage in V
   */
  public double getDistributionVoltageV() {
    return distributionVoltageV;
  }

  /**
   * Set distribution voltage in volts.
   *
   * @param distributionVoltageV distribution voltage in V
   */
  public void setDistributionVoltageV(double distributionVoltageV) {
    this.distributionVoltageV = distributionVoltageV;
  }

  /**
   * Get system frequency in Hz.
   *
   * @return frequency in Hz
   */
  public double getFrequencyHz() {
    return frequencyHz;
  }

  /**
   * Set system frequency in Hz.
   *
   * @param frequencyHz frequency in Hz
   */
  public void setFrequencyHz(double frequencyHz) {
    this.frequencyHz = frequencyHz;
  }

  /**
   * Get the process system.
   *
   * @return the process system
   */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }
}
