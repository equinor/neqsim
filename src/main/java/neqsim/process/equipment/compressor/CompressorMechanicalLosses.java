package neqsim.process.equipment.compressor;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Models mechanical losses and seal gas consumption for centrifugal compressors.
 *
 * <p>
 * This class provides calculations for:
 * <ul>
 * <li>Dry gas seal (DGS) consumption per API 692</li>
 * <li>Bearing power losses per API 617</li>
 * <li>Seal oil system requirements (for oil seals)</li>
 * <li>Buffer gas requirements</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 * <li>API 692 - Dry Gas Sealing Systems for Axial, Centrifugal, Rotary Screw Compressors</li>
 * <li>API 617 - Axial and Centrifugal Compressors and Expander-compressors</li>
 * <li>API 614 - Lubrication, Shaft-Sealing and Oil-Control Systems</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorMechanicalLosses implements Serializable {

  private static final long serialVersionUID = 1001L;
  private static final Logger logger = LogManager.getLogger(CompressorMechanicalLosses.class);

  // ============================================================================
  // Seal Configuration
  // ============================================================================

  /**
   * Seal type enumeration.
   */
  public enum SealType {
    /** Dry gas seal - tandem arrangement (most common). */
    DRY_GAS_TANDEM,
    /** Dry gas seal - double opposed arrangement. */
    DRY_GAS_DOUBLE,
    /** Dry gas seal - single seal. */
    DRY_GAS_SINGLE,
    /** Oil film seal (legacy). */
    OIL_FILM,
    /** Labyrinth seal (non-contacting). */
    LABYRINTH
  }

  /**
   * Bearing type enumeration.
   */
  public enum BearingType {
    /** Tilting pad journal bearing (most common). */
    TILTING_PAD,
    /** Plain sleeve bearing. */
    PLAIN_SLEEVE,
    /** Magnetic bearing (active). */
    MAGNETIC_ACTIVE,
    /** Gas foil bearing. */
    GAS_FOIL
  }

  // ============================================================================
  // Seal Parameters
  // ============================================================================

  /** Seal type (default: tandem dry gas seal). */
  private SealType sealType = SealType.DRY_GAS_TANDEM;

  /** Shaft diameter at seal location [mm]. */
  private double shaftDiameter = 100.0;

  /** Seal gas supply pressure [bara]. */
  private double sealGasSupplyPressure = 0.0;

  /** Seal gas supply temperature [°C]. */
  private double sealGasSupplyTemperature = 40.0;

  /** Primary seal leakage rate [Nm³/hr per seal]. */
  private double primarySealLeakage = 0.0;

  /** Secondary seal leakage rate [Nm³/hr per seal]. */
  private double secondarySealLeakage = 0.0;

  /** Number of seal assemblies (typically 2 for single shaft). */
  private int numberOfSeals = 2;

  /** Buffer gas flow rate [Nm³/hr per seal]. */
  private double bufferGasFlow = 0.0;

  /** Separation gas (nitrogen) flow rate [Nm³/hr per seal]. */
  private double separationGasFlow = 0.0;

  // ============================================================================
  // Bearing Parameters
  // ============================================================================

  /** Bearing type. */
  private BearingType bearingType = BearingType.TILTING_PAD;

  /** Number of radial bearings. */
  private int numberOfRadialBearings = 2;

  /** Number of thrust bearings. */
  private int numberOfThrustBearings = 1;

  /** Radial bearing power loss [kW per bearing]. */
  private double radialBearingLoss = 0.0;

  /** Thrust bearing power loss [kW]. */
  private double thrustBearingLoss = 0.0;

  /** Lube oil flow rate [L/min]. */
  private double lubeOilFlowRate = 0.0;

  /** Lube oil inlet temperature [°C]. */
  private double lubeOilInletTemp = 40.0;

  /** Lube oil outlet temperature [°C]. */
  private double lubeOilOutletTemp = 55.0;

  // ============================================================================
  // Operating Conditions (set from Compressor)
  // ============================================================================

  /** Compressor suction pressure [bara]. */
  private double suctionPressure = 1.0;

  /** Compressor discharge pressure [bara]. */
  private double dischargePressure = 10.0;

  /** Shaft speed [rpm]. */
  private double shaftSpeed = 10000.0;

  /** Gas molecular weight [kg/kmol]. */
  private double gasMolecularWeight = 18.0;

  /** Gas compressibility factor at seal conditions. */
  private double gasCompressibilityZ = 0.95;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor with typical values for medium-sized compressor.
   */
  public CompressorMechanicalLosses() {
    // Default initialization
  }

  /**
   * Constructor with shaft diameter specification.
   *
   * @param shaftDiameterMm shaft diameter in mm
   */
  public CompressorMechanicalLosses(double shaftDiameterMm) {
    this.shaftDiameter = shaftDiameterMm;
  }

  // ============================================================================
  // Dry Gas Seal Calculations (API 692)
  // ============================================================================

  /**
   * Calculate dry gas seal leakage rate based on API 692 methodology.
   *
   * <p>
   * The leakage through a dry gas seal depends on:
   * <ul>
   * <li>Seal differential pressure</li>
   * <li>Shaft diameter (seal size)</li>
   * <li>Gas properties (MW, Z-factor)</li>
   * <li>Seal design (gap, face geometry)</li>
   * </ul>
   *
   * <p>
   * Typical primary seal leakage: 0.5-3.0 Nm³/hr per seal for properly functioning DGS.
   * </p>
   *
   * @return primary seal leakage rate in Nm³/hr (total for all seals)
   */
  public double calculatePrimarySealLeakage() {
    // Empirical correlation based on API 692 guidelines and industry data
    // Q = K * D * sqrt(deltaP * MW / (T * Z))
    // Where K is an empirical constant depending on seal design

    double deltaP = getSealDifferentialPressure();
    double temperatureK = sealGasSupplyTemperature + 273.15;

    // Empirical constant (typical range 0.001-0.003 for modern DGS)
    double K = 0.002;

    // Account for seal type
    switch (sealType) {
      case DRY_GAS_SINGLE:
        K = 0.003; // Higher leakage for single seal
        break;
      case DRY_GAS_TANDEM:
        K = 0.002; // Standard tandem arrangement
        break;
      case DRY_GAS_DOUBLE:
        K = 0.0015; // Lower leakage for double opposed
        break;
      case LABYRINTH:
        K = 0.02; // Much higher for labyrinth
        break;
      case OIL_FILM:
        K = 0.0; // No gas leakage for oil seals
        break;
      default:
        K = 0.002;
    }

    // Calculate leakage per seal [Nm³/hr]
    if (deltaP > 0 && gasCompressibilityZ > 0) {
      primarySealLeakage = K * shaftDiameter
          * Math.sqrt(deltaP * gasMolecularWeight / (temperatureK * gasCompressibilityZ));
    } else {
      primarySealLeakage = 0.5; // Minimum leakage estimate
    }

    // Ensure reasonable range (0.3-5 Nm³/hr per seal is typical)
    primarySealLeakage = Math.max(0.3, Math.min(5.0, primarySealLeakage));

    return primarySealLeakage * numberOfSeals;
  }

  /**
   * Calculate secondary seal leakage for tandem arrangements.
   *
   * <p>
   * Secondary seal operates at lower pressure differential (typically to flare/vent).
   * </p>
   *
   * @return secondary seal leakage in Nm³/hr (total)
   */
  public double calculateSecondarySealLeakage() {
    if (sealType == SealType.DRY_GAS_TANDEM || sealType == SealType.DRY_GAS_DOUBLE) {
      // Secondary seal leakage is typically 10-30% of primary
      secondarySealLeakage = primarySealLeakage * 0.2;
    } else {
      secondarySealLeakage = 0.0;
    }
    return secondarySealLeakage * numberOfSeals;
  }

  /**
   * Calculate required buffer gas flow rate.
   *
   * <p>
   * Buffer gas is injected between primary and secondary seals to prevent process gas contamination
   * of the secondary seal and bearing area.
   * </p>
   *
   * @return buffer gas flow rate in Nm³/hr (total)
   */
  public double calculateBufferGasFlow() {
    if (sealType == SealType.DRY_GAS_TANDEM || sealType == SealType.DRY_GAS_DOUBLE) {
      // Buffer gas flow typically 2-5 Nm³/hr per seal
      // Higher for larger shafts and higher pressures
      bufferGasFlow = 2.0 + 0.02 * shaftDiameter;
      bufferGasFlow = Math.max(2.0, Math.min(10.0, bufferGasFlow));
    } else {
      bufferGasFlow = 0.0;
    }
    return bufferGasFlow * numberOfSeals;
  }

  /**
   * Calculate separation gas (nitrogen) flow rate.
   *
   * <p>
   * Separation gas prevents bearing oil mist from entering the seal area.
   * </p>
   *
   * @return separation gas flow rate in Nm³/hr (total)
   */
  public double calculateSeparationGasFlow() {
    // Separation gas flow typically 1-3 Nm³/hr per seal
    separationGasFlow = 1.5 + 0.01 * shaftDiameter;
    separationGasFlow = Math.max(1.0, Math.min(5.0, separationGasFlow));
    return separationGasFlow * numberOfSeals;
  }

  /**
   * Get seal gas supply pressure requirement.
   *
   * <p>
   * Seal gas must be supplied at a pressure higher than the reference pressure (suction or
   * discharge depending on seal location).
   * </p>
   *
   * @return required seal gas supply pressure in bara
   */
  public double getRequiredSealGasSupplyPressure() {
    // Seal gas supply typically 1.5-3 bar above reference pressure
    double referencePress = Math.max(suctionPressure, dischargePressure);
    return referencePress + 2.0;
  }

  /**
   * Get the seal differential pressure.
   *
   * @return differential pressure across primary seal [bar]
   */
  public double getSealDifferentialPressure() {
    if (sealGasSupplyPressure > 0) {
      return sealGasSupplyPressure - suctionPressure;
    }
    return dischargePressure - suctionPressure;
  }

  /**
   * Get total seal gas consumption (all flows combined).
   *
   * @return total seal gas consumption in Nm³/hr
   */
  public double getTotalSealGasConsumption() {
    double primary = calculatePrimarySealLeakage();
    double secondary = calculateSecondarySealLeakage();
    double buffer = calculateBufferGasFlow();
    return primary + secondary + buffer;
  }

  // ============================================================================
  // Bearing Loss Calculations (API 617)
  // ============================================================================

  /**
   * Calculate radial bearing power loss.
   *
   * <p>
   * Based on API 617 and bearing vendor correlations. Power loss depends on:
   * <ul>
   * <li>Shaft speed</li>
   * <li>Bearing load (rotor weight)</li>
   * <li>Oil viscosity</li>
   * <li>Bearing geometry</li>
   * </ul>
   *
   * @return radial bearing power loss in kW (total for all radial bearings)
   */
  public double calculateRadialBearingLoss() {
    // Empirical correlation: P = K * D^2 * N^1.5 * mu
    // Simplified to: P = K * D^2 * (N/1000)^1.8
    // Where D is shaft diameter [mm], N is speed [rpm]

    double K;
    switch (bearingType) {
      case TILTING_PAD:
        K = 0.00008; // Tilting pad - moderate loss
        break;
      case PLAIN_SLEEVE:
        K = 0.00012; // Plain sleeve - higher loss
        break;
      case MAGNETIC_ACTIVE:
        K = 0.00001; // Magnetic - very low mechanical loss (mostly electrical)
        break;
      case GAS_FOIL:
        K = 0.00002; // Gas foil - low loss
        break;
      default:
        K = 0.00008;
    }

    // Calculate loss per bearing [kW]
    double speedFactor = Math.pow(shaftSpeed / 1000.0, 1.8);
    radialBearingLoss = K * shaftDiameter * shaftDiameter * speedFactor / 1000.0;

    // Ensure reasonable range (0.5-20 kW per bearing typical)
    radialBearingLoss = Math.max(0.5, Math.min(20.0, radialBearingLoss));

    return radialBearingLoss * numberOfRadialBearings;
  }

  /**
   * Calculate thrust bearing power loss.
   *
   * <p>
   * Thrust bearing handles axial load from pressure differential across impellers.
   * </p>
   *
   * @return thrust bearing power loss in kW
   */
  public double calculateThrustBearingLoss() {
    // Thrust bearing loss is typically 1.5-2x radial bearing loss
    // due to larger contact area and higher specific loading

    double K;
    switch (bearingType) {
      case TILTING_PAD:
        K = 0.00015;
        break;
      case PLAIN_SLEEVE:
        K = 0.00020;
        break;
      case MAGNETIC_ACTIVE:
        K = 0.00002;
        break;
      case GAS_FOIL:
        K = 0.00003;
        break;
      default:
        K = 0.00015;
    }

    double speedFactor = Math.pow(shaftSpeed / 1000.0, 1.8);
    thrustBearingLoss = K * shaftDiameter * shaftDiameter * speedFactor / 1000.0;

    // Thrust bearing typically 1-30 kW
    thrustBearingLoss = Math.max(1.0, Math.min(30.0, thrustBearingLoss));

    return thrustBearingLoss * numberOfThrustBearings;
  }

  /**
   * Calculate total bearing power loss.
   *
   * @return total bearing mechanical loss in kW
   */
  public double getTotalBearingLoss() {
    return calculateRadialBearingLoss() + calculateThrustBearingLoss();
  }

  /**
   * Calculate lube oil flow rate requirement.
   *
   * <p>
   * Lube oil flow must be sufficient to remove heat generated by bearing friction.
   * </p>
   *
   * @return lube oil flow rate in L/min
   */
  public double calculateLubeOilFlowRate() {
    // Q = P / (rho * Cp * deltaT)
    // For typical mineral oil: rho = 860 kg/m³, Cp = 2.0 kJ/kg·K

    double totalBearingLoss = getTotalBearingLoss(); // kW
    double deltaT = lubeOilOutletTemp - lubeOilInletTemp; // °C
    double rho = 860.0; // kg/m³
    double Cp = 2.0; // kJ/kg·K

    if (deltaT > 0) {
      // Q [L/min] = P [kW] * 60 / (rho [kg/m³] * Cp [kJ/kg·K] * deltaT [K]) * 1000
      lubeOilFlowRate = totalBearingLoss * 60.0 * 1000.0 / (rho * Cp * deltaT);
    } else {
      lubeOilFlowRate = 50.0; // Default minimum
    }

    // Typical range: 20-200 L/min for medium compressors
    lubeOilFlowRate = Math.max(20.0, Math.min(200.0, lubeOilFlowRate));

    return lubeOilFlowRate;
  }

  /**
   * Calculate lube oil cooler heat duty.
   *
   * @return heat duty in kW
   */
  public double calculateLubeOilCoolerDuty() {
    // All bearing losses must be removed by the lube oil cooler
    // Plus some margin for oil pump losses
    return getTotalBearingLoss() * 1.1;
  }

  // ============================================================================
  // Total Mechanical Losses
  // ============================================================================

  /**
   * Calculate total mechanical power loss (bearings + seals).
   *
   * <p>
   * This represents the additional power required beyond the gas compression power to account for
   * mechanical friction.
   * </p>
   *
   * @return total mechanical loss in kW
   */
  public double getTotalMechanicalLoss() {
    double bearingLoss = getTotalBearingLoss();

    // Seal friction is typically small for DGS (non-contact)
    double sealFriction = 0.0;
    if (sealType == SealType.OIL_FILM) {
      // Oil seals have significant friction
      sealFriction = 0.0001 * shaftDiameter * shaftDiameter * Math.pow(shaftSpeed / 1000.0, 1.5)
          / 1000.0 * numberOfSeals;
    }

    return bearingLoss + sealFriction;
  }

  /**
   * Calculate mechanical efficiency.
   *
   * <p>
   * Mechanical efficiency = 1 - (mechanical losses / shaft power)
   * </p>
   *
   * @param shaftPowerKW shaft power in kW
   * @return mechanical efficiency (0-1)
   */
  public double getMechanicalEfficiency(double shaftPowerKW) {
    if (shaftPowerKW <= 0) {
      return 0.98; // Default for idle
    }
    double mechLoss = getTotalMechanicalLoss();
    double efficiency = 1.0 - mechLoss / shaftPowerKW;
    return Math.max(0.90, Math.min(0.995, efficiency));
  }

  // ============================================================================
  // Update from Compressor
  // ============================================================================

  /**
   * Update operating conditions from compressor.
   *
   * @param suctionPressure suction pressure [bara]
   * @param dischargePressure discharge pressure [bara]
   * @param shaftSpeed shaft speed [rpm]
   * @param gasMW gas molecular weight [kg/kmol]
   * @param gasZ gas compressibility factor
   */
  public void setOperatingConditions(double suctionPressure, double dischargePressure,
      double shaftSpeed, double gasMW, double gasZ) {
    this.suctionPressure = suctionPressure;
    this.dischargePressure = dischargePressure;
    this.shaftSpeed = shaftSpeed;
    this.gasMolecularWeight = gasMW;
    this.gasCompressibilityZ = gasZ;
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Get the seal type.
   *
   * @return seal type
   */
  public SealType getSealType() {
    return sealType;
  }

  /**
   * Set the seal type.
   *
   * @param sealType seal type
   */
  public void setSealType(SealType sealType) {
    this.sealType = sealType;
  }

  /**
   * Get the bearing type.
   *
   * @return bearing type
   */
  public BearingType getBearingType() {
    return bearingType;
  }

  /**
   * Set the bearing type.
   *
   * @param bearingType bearing type
   */
  public void setBearingType(BearingType bearingType) {
    this.bearingType = bearingType;
  }

  /**
   * Get shaft diameter.
   *
   * @return shaft diameter in mm
   */
  public double getShaftDiameter() {
    return shaftDiameter;
  }

  /**
   * Set shaft diameter.
   *
   * @param shaftDiameter shaft diameter in mm
   */
  public void setShaftDiameter(double shaftDiameter) {
    this.shaftDiameter = shaftDiameter;
  }

  /**
   * Get number of seals.
   *
   * @return number of seal assemblies
   */
  public int getNumberOfSeals() {
    return numberOfSeals;
  }

  /**
   * Set number of seals.
   *
   * @param numberOfSeals number of seal assemblies
   */
  public void setNumberOfSeals(int numberOfSeals) {
    this.numberOfSeals = numberOfSeals;
  }

  /**
   * Get number of radial bearings.
   *
   * @return number of radial bearings
   */
  public int getNumberOfRadialBearings() {
    return numberOfRadialBearings;
  }

  /**
   * Set number of radial bearings.
   *
   * @param numberOfRadialBearings number of radial bearings
   */
  public void setNumberOfRadialBearings(int numberOfRadialBearings) {
    this.numberOfRadialBearings = numberOfRadialBearings;
  }

  /**
   * Get seal gas supply pressure.
   *
   * @return seal gas supply pressure in bara
   */
  public double getSealGasSupplyPressure() {
    return sealGasSupplyPressure;
  }

  /**
   * Set seal gas supply pressure.
   *
   * @param pressure seal gas supply pressure in bara
   */
  public void setSealGasSupplyPressure(double pressure) {
    this.sealGasSupplyPressure = pressure;
  }

  /**
   * Get seal gas supply temperature.
   *
   * @return temperature in °C
   */
  public double getSealGasSupplyTemperature() {
    return sealGasSupplyTemperature;
  }

  /**
   * Set seal gas supply temperature.
   *
   * @param temperature temperature in °C
   */
  public void setSealGasSupplyTemperature(double temperature) {
    this.sealGasSupplyTemperature = temperature;
  }

  /**
   * Get lube oil inlet temperature.
   *
   * @return temperature in °C
   */
  public double getLubeOilInletTemp() {
    return lubeOilInletTemp;
  }

  /**
   * Set lube oil inlet temperature.
   *
   * @param temperature temperature in °C
   */
  public void setLubeOilInletTemp(double temperature) {
    this.lubeOilInletTemp = temperature;
  }

  /**
   * Get lube oil outlet temperature.
   *
   * @return temperature in °C
   */
  public double getLubeOilOutletTemp() {
    return lubeOilOutletTemp;
  }

  /**
   * Set lube oil outlet temperature.
   *
   * @param temperature temperature in °C
   */
  public void setLubeOilOutletTemp(double temperature) {
    this.lubeOilOutletTemp = temperature;
  }

  /**
   * Print summary of mechanical losses and seal gas consumption.
   */
  public void printSummary() {
    logger.info("=== Compressor Mechanical Losses Summary ===");
    logger.info("Seal Type: " + sealType);
    logger.info("Bearing Type: " + bearingType);
    logger.info("Shaft Diameter: " + shaftDiameter + " mm");
    logger.info("Shaft Speed: " + shaftSpeed + " rpm");
    logger.info("");
    logger.info("--- Seal Gas Consumption ---");
    logger.info("Primary Seal Leakage: " + String.format("%.2f", calculatePrimarySealLeakage())
        + " Nm³/hr");
    logger.info("Secondary Seal Leakage: " + String.format("%.2f", calculateSecondarySealLeakage())
        + " Nm³/hr");
    logger.info("Buffer Gas Flow: " + String.format("%.2f", calculateBufferGasFlow()) + " Nm³/hr");
    logger.info(
        "Separation Gas Flow: " + String.format("%.2f", calculateSeparationGasFlow()) + " Nm³/hr");
    logger
        .info("Total Seal Gas: " + String.format("%.2f", getTotalSealGasConsumption()) + " Nm³/hr");
    logger.info("");
    logger.info("--- Bearing Losses ---");
    logger.info(
        "Radial Bearing Loss: " + String.format("%.2f", calculateRadialBearingLoss()) + " kW");
    logger.info(
        "Thrust Bearing Loss: " + String.format("%.2f", calculateThrustBearingLoss()) + " kW");
    logger.info("Total Bearing Loss: " + String.format("%.2f", getTotalBearingLoss()) + " kW");
    logger.info("");
    logger.info("--- Lube Oil System ---");
    logger.info("Lube Oil Flow: " + String.format("%.1f", calculateLubeOilFlowRate()) + " L/min");
    logger.info(
        "Lube Oil Cooler Duty: " + String.format("%.2f", calculateLubeOilCoolerDuty()) + " kW");
    logger.info("");
    logger
        .info("Total Mechanical Loss: " + String.format("%.2f", getTotalMechanicalLoss()) + " kW");
  }
}
