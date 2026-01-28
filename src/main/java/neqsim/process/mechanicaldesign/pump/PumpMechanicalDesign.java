package neqsim.process.mechanicaldesign.pump;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Mechanical design calculations for centrifugal pumps per API 610.
 *
 * <p>
 * This class provides sizing and design calculations for centrifugal pumps based on API 610
 * (Centrifugal Pumps for Petroleum, Petrochemical and Natural Gas Industries) and Hydraulic
 * Institute standards. Calculations include:
 * </p>
 * <ul>
 * <li>Impeller diameter sizing based on head and flow requirements</li>
 * <li>Shaft sizing for torque transmission</li>
 * <li>Casing design for pressure containment</li>
 * <li>Driver (motor) sizing with appropriate margins</li>
 * <li>NPSH analysis and cavitation risk assessment</li>
 * <li>Seal system requirements</li>
 * <li>Baseplate and foundation requirements</li>
 * <li>Module footprint and weight estimation</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>API 610 - Centrifugal Pumps for Petroleum, Petrochemical and Natural Gas Industries</li>
 * <li>API 674 - Positive Displacement Pumps - Reciprocating</li>
 * <li>API 675 - Positive Displacement Pumps - Controlled Volume Metering</li>
 * <li>Hydraulic Institute Standards</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PumpMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Design Constants (API 610 / Hydraulic Institute)
  // ============================================================================

  /** Gravity constant [m/s²]. */
  private static final double GRAVITY = 9.81;

  /** Driver sizing margin for small pumps (&lt; 22 kW) per API 610. */
  private static final double DRIVER_MARGIN_SMALL = 1.25;

  /** Driver sizing margin for medium pumps (22-55 kW) per API 610. */
  private static final double DRIVER_MARGIN_MEDIUM = 1.15;

  /** Driver sizing margin for large pumps (&gt; 55 kW) per API 610. */
  private static final double DRIVER_MARGIN_LARGE = 1.10;

  /** Design pressure margin factor. */
  private static final double DESIGN_PRESSURE_MARGIN = 1.10;

  /** Design temperature margin [C]. */
  private static final double DESIGN_TEMPERATURE_MARGIN = 30.0;

  /** Typical pump efficiency for estimation when not available. */
  private static final double DEFAULT_PUMP_EFFICIENCY = 0.75;

  /** Steel density [kg/m³]. */
  private static final double STEEL_DENSITY = 7850.0;

  /** Allowable stress for carbon steel at ambient [MPa]. */
  private static final double ALLOWABLE_STRESS = 137.9;

  // ============================================================================
  // Pump Type Enumeration
  // ============================================================================

  /**
   * Pump type classification per API 610.
   */
  public enum PumpType {
    /** Overhung, single stage, end suction (OH1, OH2, OH3). */
    OVERHUNG,
    /** Between bearings, single or multistage (BB1-BB5). */
    BETWEEN_BEARINGS,
    /** Vertically suspended (VS1-VS7). */
    VERTICALLY_SUSPENDED
  }

  /**
   * Seal type classification.
   */
  public enum SealType {
    /** Packed gland - simple, allows leakage. */
    PACKED,
    /** Single mechanical seal - most common. */
    SINGLE_MECHANICAL,
    /** Double mechanical seal - for hazardous service. */
    DOUBLE_MECHANICAL,
    /** Magnetic drive - sealless. */
    MAGNETIC_DRIVE,
    /** Canned motor - sealless. */
    CANNED_MOTOR
  }

  // ============================================================================
  // Pump Design Parameters
  // ============================================================================

  /** Pump type classification. */
  private PumpType pumpType = PumpType.OVERHUNG;

  /** Seal type. */
  private SealType sealType = SealType.SINGLE_MECHANICAL;

  /** Impeller outer diameter [mm]. */
  private double impellerDiameter = 200.0;

  /** Impeller width [mm]. */
  private double impellerWidth = 30.0;

  /** Shaft diameter at impeller [mm]. */
  private double shaftDiameter = 40.0;

  /** Required driver power [kW]. */
  private double driverPower = 0.0;

  /** Driver power margin factor. */
  private double driverMargin = 1.15;

  /** Casing wall thickness [mm]. */
  private double casingWallThickness = 10.0;

  /** Number of stages. */
  private int numberOfStages = 1;

  /** Rated speed [rpm]. */
  private double ratedSpeed = 2950.0; // 50 Hz 2-pole motor

  /** Specific speed (Ns) - dimensionless. */
  private double specificSpeed = 0.0;

  /** Suction specific speed (Nss) - dimensionless. */
  private double suctionSpecificSpeed = 0.0;

  /** Design pressure [bara]. */
  private double designPressure = 0.0;

  /** Design temperature [C]. */
  private double designTemperature = 0.0;

  /** Maximum allowable working pressure [bara]. */
  private double maxAllowableWorkingPressure = 0.0;

  /** Best efficiency point flow [m³/h]. */
  private double bepFlow = 0.0;

  /** Best efficiency point head [m]. */
  private double bepHead = 0.0;

  /** Pump efficiency at operating point. */
  private double pumpEfficiency = DEFAULT_PUMP_EFFICIENCY;

  /** NPSH required [m]. */
  private double npshRequired = 0.0;

  /** NPSH available [m]. */
  private double npshAvailable = 0.0;

  /** NPSH margin (NPSHa / NPSHr). */
  private double npshMargin = 0.0;

  /** Casing weight [kg]. */
  private double casingWeight = 0.0;

  /** Impeller weight [kg]. */
  private double impellerWeight = 0.0;

  /** Shaft weight [kg]. */
  private double shaftWeight = 0.0;

  /** Motor weight [kg]. */
  private double motorWeight = 0.0;

  /** Baseplate weight [kg]. */
  private double baseplateWeight = 0.0;

  /** Seal system weight [kg]. */
  private double sealWeight = 0.0;

  /** Coupling weight [kg]. */
  private double couplingWeight = 0.0;

  /** Suction nozzle size [inches]. */
  private double suctionNozzleSize = 4.0;

  /** Discharge nozzle size [inches]. */
  private double dischargeNozzleSize = 3.0;

  /** Minimum continuous stable flow [m³/h]. */
  private double minimumFlow = 0.0;

  /** Maximum allowable flow [m³/h]. */
  private double maximumFlow = 0.0;

  // ============================================================================
  // Process Design Parameters (from design standards database)
  // ============================================================================

  /** NPSH available margin factor over required. */
  private double npshMarginFactor = 1.15;

  /** Hydraulic power sizing margin. */
  private double hydraulicPowerMargin = 1.10;

  /** Minimum continuous flow as fraction of BEP. */
  private double minContinuousFlowFraction = 0.70;

  /** Maximum continuous flow as fraction of BEP. */
  private double maxContinuousFlowFraction = 1.20;

  /** Preferred operating region - low limit as fraction of BEP. */
  private double porLowFraction = 0.80;

  /** Preferred operating region - high limit as fraction of BEP. */
  private double porHighFraction = 1.10;

  /** Allowable operating region - low limit as fraction of BEP. */
  private double aorLowFraction = 0.60;

  /** Allowable operating region - high limit as fraction of BEP. */
  private double aorHighFraction = 1.30;

  /** Maximum suction specific speed for stable operation. */
  private double maxSuctionSpecificSpeed = 11000.0;

  /** Head margin factor. */
  private double headMarginFactor = 1.05;

  /**
   * Constructor for PumpMechanicalDesign.
   *
   * @param equipment the pump equipment
   */
  public PumpMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }


  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Pump pump = (Pump) getProcessEquipment();

    // Ensure pump has been run
    if (pump.getInletStream() == null || pump.getInletStream().getThermoSystem() == null) {
      return;
    }

    // Get operating conditions
    double suctionPressure = pump.getInletStream().getPressure("bara");
    double dischargePressure = pump.getOutletStream().getPressure("bara");
    double suctionTemperature = pump.getInletStream().getTemperature("C");
    double dischargeTemperature = pump.getOutletStream().getTemperature("C");
    double massFlowRate = pump.getInletStream().getFlowRate("kg/hr");
    double volumeFlowRate = pump.getInletStream().getFlowRate("m3/hr");
    double density = pump.getInletStream().getThermoSystem().getDensity("kg/m3");
    double hydraulicPowerKW = pump.getPower("kW");

    // Calculate differential pressure and head
    double differentialPressure = dischargePressure - suctionPressure;
    double pumpHead = (differentialPressure * 1e5) / (density * GRAVITY); // meters

    // Get or estimate pump efficiency
    pumpEfficiency = pump.getIsentropicEfficiency();
    if (pumpEfficiency <= 0 || pumpEfficiency > 1.0) {
      pumpEfficiency = DEFAULT_PUMP_EFFICIENCY;
    }

    // Calculate design conditions with margins
    designPressure = dischargePressure * DESIGN_PRESSURE_MARGIN;
    designTemperature =
        Math.max(suctionTemperature, dischargeTemperature) + DESIGN_TEMPERATURE_MARGIN;

    // Calculate specific speeds
    if (volumeFlowRate > 0 && pumpHead > 0) {
      ratedSpeed = estimateOptimalSpeed(volumeFlowRate, pumpHead);
      calculateSpecificSpeeds(volumeFlowRate, pumpHead, ratedSpeed);
    }

    // Select pump type based on specific speed
    selectPumpType();

    // Calculate impeller sizing
    calculateImpellerSizing(volumeFlowRate, pumpHead, ratedSpeed);

    // Calculate shaft sizing
    calculateShaftSizing(hydraulicPowerKW, ratedSpeed, pumpEfficiency);

    // Calculate casing design
    calculateCasingDesign(designPressure, designTemperature);

    // Calculate driver sizing
    calculateDriverSizing(hydraulicPowerKW, pumpEfficiency);

    // Estimate NPSH required
    estimateNpshRequired(volumeFlowRate, ratedSpeed);

    // Calculate nozzle sizes
    calculateNozzleSizes(volumeFlowRate);

    // Calculate weights
    calculateWeights(volumeFlowRate, designPressure);

    // Calculate module dimensions
    calculateModuleDimensions();

    // Set operating range
    bepFlow = volumeFlowRate;
    bepHead = pumpHead;
    minimumFlow = volumeFlowRate * 0.3; // Typical minimum stable flow
    maximumFlow = volumeFlowRate * 1.2; // Typical maximum flow
  }

  /**
   * Estimate optimal pump speed based on flow and head.
   *
   * @param flowM3h volume flow rate in m³/h
   * @param headM pump head in meters
   * @return estimated optimal speed in rpm
   */
  private double estimateOptimalSpeed(double flowM3h, double headM) {
    // Target specific speed around 150-200 for best efficiency
    double targetNs = 175.0;

    // Ns = N * sqrt(Q) / H^0.75 (metric)
    // N = Ns * H^0.75 / sqrt(Q)
    double flowM3min = flowM3h / 60.0;
    double estimatedSpeed = targetNs * Math.pow(headM, 0.75) / Math.sqrt(flowM3min);

    // Round to standard motor speeds (50 Hz)
    double[] standardSpeeds = {2950, 1475, 985, 740};
    double closestSpeed = standardSpeeds[0];
    double minDiff = Math.abs(estimatedSpeed - closestSpeed);

    for (double speed : standardSpeeds) {
      double diff = Math.abs(estimatedSpeed - speed);
      if (diff < minDiff) {
        minDiff = diff;
        closestSpeed = speed;
      }
    }

    return closestSpeed;
  }

  /**
   * Calculate specific speeds for pump classification.
   *
   * @param flowM3h volume flow rate in m³/h
   * @param headM pump head in meters
   * @param speedRpm pump speed in rpm
   */
  private void calculateSpecificSpeeds(double flowM3h, double headM, double speedRpm) {
    double flowM3min = flowM3h / 60.0;

    // Specific speed: Ns = N * sqrt(Q) / H^0.75
    specificSpeed = speedRpm * Math.sqrt(flowM3min) / Math.pow(headM, 0.75);

    // Suction specific speed: Nss = N * sqrt(Q) / NPSHr^0.75
    // Estimate NPSHr first, then calculate
    double estimatedNpshr = 3.0 + 0.001 * speedRpm * Math.sqrt(flowM3min);
    suctionSpecificSpeed = speedRpm * Math.sqrt(flowM3min) / Math.pow(estimatedNpshr, 0.75);
  }

  /**
   * Select pump type based on specific speed and application.
   */
  private void selectPumpType() {
    // General guidelines for pump type selection
    if (specificSpeed < 50) {
      // Low flow, high head - multistage
      pumpType = PumpType.BETWEEN_BEARINGS;
      numberOfStages = (int) Math.ceil(300.0 / specificSpeed);
    } else if (specificSpeed < 300) {
      // Normal range - single stage overhung
      pumpType = PumpType.OVERHUNG;
      numberOfStages = 1;
    } else {
      // High flow, low head - mixed/axial flow
      pumpType = PumpType.OVERHUNG;
      numberOfStages = 1;
    }
  }

  /**
   * Calculate impeller diameter and geometry.
   *
   * @param flowM3h volume flow rate in m³/h
   * @param headM pump head per stage in meters
   * @param speedRpm pump speed in rpm
   */
  private void calculateImpellerSizing(double flowM3h, double headM, double speedRpm) {
    if (speedRpm <= 0 || flowM3h <= 0 || headM <= 0) {
      impellerDiameter = 200.0; // Default
      return;
    }

    // Head per stage for multistage pumps
    double headPerStage = headM / numberOfStages;

    // Peripheral velocity coefficient (typical 0.9-1.1)
    double ku = 1.0;

    // Calculate tip speed: u2 = ku * sqrt(2 * g * H)
    double tipSpeed = ku * Math.sqrt(2.0 * GRAVITY * headPerStage);

    // Calculate impeller diameter: D2 = 60 * u2 / (pi * N)
    impellerDiameter = (60.0 * tipSpeed) / (Math.PI * speedRpm) * 1000.0; // mm

    // Ensure reasonable range (50-1000 mm typical for process pumps)
    impellerDiameter = Math.max(50.0, Math.min(1000.0, impellerDiameter));

    // Estimate impeller width based on flow and diameter
    double flowM3s = flowM3h / 3600.0;
    double d2m = impellerDiameter / 1000.0;

    // Flow coefficient approach: Q = pi * D2 * b2 * c_m2
    // Assume c_m2 ~ 0.1 * u2
    double cm2 = 0.1 * tipSpeed;
    impellerWidth = (flowM3s / (Math.PI * d2m * cm2)) * 1000.0; // mm
    impellerWidth = Math.max(10.0, Math.min(200.0, impellerWidth));
  }

  /**
   * Calculate shaft diameter for torque transmission.
   *
   * @param powerKW shaft power in kW
   * @param speedRpm shaft speed in rpm
   * @param efficiency pump efficiency
   */
  private void calculateShaftSizing(double powerKW, double speedRpm, double efficiency) {
    if (speedRpm <= 0) {
      shaftDiameter = 40.0; // Default
      return;
    }

    // Shaft power considering efficiency
    double shaftPowerKW = powerKW / efficiency;

    // Calculate torque: T = P * 60 / (2 * pi * N) [kNm]
    double torqueKNm = (shaftPowerKW * 60.0) / (2.0 * Math.PI * speedRpm);
    double torqueNm = torqueKNm * 1000.0;

    // Shaft diameter from shear stress: d = (16*T / (pi*tau))^(1/3)
    // Allowable shear stress ~ 40 MPa for steel shaft
    double allowableShear = 40.0e6; // Pa
    double shaftDiameterM = Math.pow((16.0 * torqueNm) / (Math.PI * allowableShear), 1.0 / 3.0);
    shaftDiameter = shaftDiameterM * 1000.0; // mm

    // Apply safety factor and round up
    shaftDiameter = shaftDiameter * 1.2;
    shaftDiameter = Math.max(20.0, Math.ceil(shaftDiameter / 5.0) * 5.0);
  }

  /**
   * Calculate casing design parameters.
   *
   * @param designPressureBara design pressure in bara
   * @param designTemperatureC design temperature in °C
   */
  private void calculateCasingDesign(double designPressureBara, double designTemperatureC) {
    // Estimate casing inner diameter based on impeller + clearance
    double casingID = impellerDiameter * 1.15; // mm

    // Calculate wall thickness per ASME pressure vessel code (simplified)
    // t = P * D / (2 * S * E - 0.6 * P)
    double pressureMPa = designPressureBara * 0.1;
    double allowableStressMPa = ALLOWABLE_STRESS;

    // Temperature derating (simplified)
    if (designTemperatureC > 200) {
      allowableStressMPa *= 0.85;
    }

    double jointEfficiency = 1.0; // Seamless casting
    double casingDiameterMm = casingID;

    casingWallThickness = (pressureMPa * casingDiameterMm)
        / (2.0 * allowableStressMPa * jointEfficiency - 0.6 * pressureMPa);

    // Add corrosion allowance
    casingWallThickness += 3.0; // mm

    // Minimum practical thickness
    casingWallThickness = Math.max(6.0, casingWallThickness);

    // Maximum allowable working pressure
    maxAllowableWorkingPressure =
        (2.0 * allowableStressMPa * jointEfficiency * (casingWallThickness - 3.0))
            / (casingDiameterMm + 0.6 * (casingWallThickness - 3.0)) * 10.0; // bara
  }

  /**
   * Calculate driver sizing with appropriate margins per API 610.
   *
   * @param hydraulicPowerKW hydraulic power in kW
   * @param efficiency pump efficiency
   */
  private void calculateDriverSizing(double hydraulicPowerKW, double efficiency) {
    // Shaft power = hydraulic power / efficiency
    double shaftPowerKW = hydraulicPowerKW / efficiency;

    // Select margin based on power range per API 610
    if (shaftPowerKW < 22.0) {
      driverMargin = DRIVER_MARGIN_SMALL;
    } else if (shaftPowerKW < 55.0) {
      driverMargin = DRIVER_MARGIN_MEDIUM;
    } else {
      driverMargin = DRIVER_MARGIN_LARGE;
    }

    // Driver power with margin
    driverPower = shaftPowerKW * driverMargin;

    // Round up to standard motor sizes (IEC frame sizes)
    double[] standardPowers = {0.75, 1.1, 1.5, 2.2, 3.0, 4.0, 5.5, 7.5, 11.0, 15.0, 18.5, 22.0,
        30.0, 37.0, 45.0, 55.0, 75.0, 90.0, 110.0, 132.0, 160.0, 200.0, 250.0, 315.0, 400.0};

    for (double standardPower : standardPowers) {
      if (standardPower >= driverPower) {
        driverPower = standardPower;
        break;
      }
    }
  }

  /**
   * Estimate NPSH required based on suction specific speed correlation.
   *
   * @param flowM3h volume flow rate in m³/h
   * @param speedRpm pump speed in rpm
   */
  private void estimateNpshRequired(double flowM3h, double speedRpm) {
    double flowM3min = flowM3h / 60.0;

    // NPSH required correlation (Hydraulic Institute)
    // NPSHr = (N * sqrt(Q) / Nss)^(4/3) where Nss ~ 8500 (US units)
    // Converted for metric: NPSHr ~ 3 + 0.0015 * N * sqrt(Q)
    npshRequired = 3.0 + 0.0015 * speedRpm * Math.sqrt(flowM3min);

    // Minimum practical value
    npshRequired = Math.max(2.0, npshRequired);
  }

  /**
   * Calculate nozzle sizes based on flow rate and velocity limits.
   *
   * @param flowM3h volume flow rate in m³/h
   */
  private void calculateNozzleSizes(double flowM3h) {
    double flowM3s = flowM3h / 3600.0;

    // Suction velocity ~ 2-3 m/s, discharge velocity ~ 4-6 m/s
    double suctionVelocity = 2.5;
    double dischargeVelocity = 5.0;

    // Calculate required areas
    double suctionArea = flowM3s / suctionVelocity;
    double dischargeArea = flowM3s / dischargeVelocity;

    // Calculate diameters
    double suctionDiameterMm = 2.0 * Math.sqrt(suctionArea / Math.PI) * 1000.0;
    double dischargeDiameterMm = 2.0 * Math.sqrt(dischargeArea / Math.PI) * 1000.0;

    // Convert to standard pipe sizes (inches)
    double[] standardSizes = {1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0};

    for (double size : standardSizes) {
      if (size * 25.4 >= suctionDiameterMm) {
        suctionNozzleSize = size;
        break;
      }
    }

    for (double size : standardSizes) {
      if (size * 25.4 >= dischargeDiameterMm) {
        dischargeNozzleSize = size;
        break;
      }
    }

    // Typically discharge is one size smaller than suction
    if (dischargeNozzleSize >= suctionNozzleSize) {
      int suctionIndex = 0;
      for (int i = 0; i < standardSizes.length; i++) {
        if (standardSizes[i] == suctionNozzleSize) {
          suctionIndex = i;
          break;
        }
      }
      if (suctionIndex > 0) {
        dischargeNozzleSize = standardSizes[suctionIndex - 1];
      }
    }
  }

  /**
   * Calculate pump component weights.
   *
   * @param flowM3h volume flow rate in m³/h
   * @param designPressureBara design pressure in bara
   */
  private void calculateWeights(double flowM3h, double designPressureBara) {
    // Impeller weight
    double impellerVolume =
        Math.PI * Math.pow(impellerDiameter / 2000.0, 2) * (impellerWidth / 1000.0) * 0.3;
    impellerWeight = impellerVolume * STEEL_DENSITY;

    // Shaft weight (estimate length based on pump type)
    double shaftLength = (pumpType == PumpType.OVERHUNG) ? 500.0 : 1000.0; // mm
    double shaftVolume = Math.PI * Math.pow(shaftDiameter / 2000.0, 2) * (shaftLength / 1000.0);
    shaftWeight = shaftVolume * STEEL_DENSITY;

    // Casing weight
    double casingOD = (impellerDiameter * 1.15 + 2.0 * casingWallThickness) / 1000.0; // m
    double casingID = impellerDiameter * 1.15 / 1000.0; // m
    double casingLength = (impellerWidth + 100.0) / 1000.0 * numberOfStages; // m
    double casingVolume =
        Math.PI / 4.0 * (Math.pow(casingOD, 2) - Math.pow(casingID, 2)) * casingLength;
    casingWeight = casingVolume * STEEL_DENSITY * 2.0; // Factor for volute shape

    // Motor weight based on power (empirical correlation)
    if (driverPower <= 0) {
      motorWeight = 50.0;
    } else if (driverPower <= 10) {
      motorWeight = 20.0 + driverPower * 3.0;
    } else if (driverPower <= 100) {
      motorWeight = 50.0 + driverPower * 2.5;
    } else {
      motorWeight = 300.0 + driverPower * 1.5;
    }

    // Baseplate weight (typically 50-100% of pump + motor)
    baseplateWeight = (casingWeight + motorWeight) * 0.6;

    // Seal weight based on shaft size
    sealWeight = 5.0 + shaftDiameter * 0.5;

    // Coupling weight
    couplingWeight = 10.0 + driverPower * 0.1;

    // Total weight
    double emptyWeight = casingWeight + impellerWeight + shaftWeight + motorWeight + baseplateWeight
        + sealWeight + couplingWeight;

    // Add piping, supports, instrumentation (30% factor)
    weightPiping = emptyWeight * 0.15;
    weightElectroInstrument = emptyWeight * 0.10;
    weightStructualSteel = emptyWeight * 0.05;

    setWeightTotal(emptyWeight + weightPiping + weightElectroInstrument + weightStructualSteel);
  }

  /**
   * Calculate module dimensions for plot plan.
   */
  private void calculateModuleDimensions() {
    // Module width based on pump size
    moduleWidth = Math.max(1.0, impellerDiameter / 500.0 + 0.5);

    // Module length (pump + motor + access)
    double pumpLength = impellerDiameter / 1000.0 * 2.0;
    double motorLength = 0.3 + Math.sqrt(driverPower) * 0.05;
    moduleLength = pumpLength + motorLength + 1.0; // + access space

    // Module height
    moduleHeight = Math.max(0.5, impellerDiameter / 1000.0 + 0.3);

    // Set outer diameter for reporting
    outerDiameter = impellerDiameter * 1.15 + 2.0 * casingWallThickness;
    innerDiameter = impellerDiameter * 1.15;
    tantanLength = impellerWidth * numberOfStages;
    this.wallThickness = casingWallThickness;
  }

  // ============================================================================
  // Getters for Design Results
  // ============================================================================

  /**
   * Get the pump type.
   *
   * @return pump type classification
   */
  public PumpType getPumpType() {
    return pumpType;
  }

  /**
   * Set the pump type.
   *
   * @param pumpType pump type classification
   */
  public void setPumpType(PumpType pumpType) {
    this.pumpType = pumpType;
  }

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
   * Get the impeller diameter.
   *
   * @return impeller diameter in mm
   */
  public double getImpellerDiameter() {
    return impellerDiameter;
  }

  /**
   * Get the shaft diameter.
   *
   * @return shaft diameter in mm
   */
  public double getShaftDiameter() {
    return shaftDiameter;
  }

  /**
   * Get the required driver power.
   *
   * @return driver power in kW
   */
  public double getDriverPower() {
    return driverPower;
  }

  /**
   * Get the design pressure.
   *
   * @return design pressure in bara
   */
  public double getDesignPressure() {
    return designPressure;
  }

  /**
   * Get the design temperature.
   *
   * @return design temperature in °C
   */
  public double getDesignTemperature() {
    return designTemperature;
  }

  /**
   * Get the specific speed.
   *
   * @return dimensionless specific speed (metric)
   */
  public double getSpecificSpeed() {
    return specificSpeed;
  }

  /**
   * Get the suction specific speed.
   *
   * @return dimensionless suction specific speed
   */
  public double getSuctionSpecificSpeed() {
    return suctionSpecificSpeed;
  }

  /**
   * Get the number of stages.
   *
   * @return number of pump stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Get the rated speed.
   *
   * @return rated speed in rpm
   */
  public double getRatedSpeed() {
    return ratedSpeed;
  }

  /**
   * Get the NPSH required.
   *
   * @return NPSH required in meters
   */
  public double getNpshRequired() {
    return npshRequired;
  }

  /**
   * Get the casing weight.
   *
   * @return casing weight in kg
   */
  public double getCasingWeight() {
    return casingWeight;
  }

  /**
   * Get the motor weight.
   *
   * @return motor weight in kg
   */
  public double getMotorWeight() {
    return motorWeight;
  }

  /**
   * Get the baseplate weight.
   *
   * @return baseplate weight in kg
   */
  public double getBaseplateWeight() {
    return baseplateWeight;
  }

  /**
   * Get the suction nozzle size.
   *
   * @return suction nozzle size in inches
   */
  public double getSuctionNozzleSize() {
    return suctionNozzleSize;
  }

  /**
   * Get the discharge nozzle size.
   *
   * @return discharge nozzle size in inches
   */
  public double getDischargeNozzleSize() {
    return dischargeNozzleSize;
  }

  /**
   * Get the minimum continuous stable flow.
   *
   * @return minimum flow in m³/h
   */
  public double getMinimumFlow() {
    return minimumFlow;
  }

  /**
   * Get the maximum allowable flow.
   *
   * @return maximum flow in m³/h
   */
  public double getMaximumFlow() {
    return maximumFlow;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResults() {
    JFrame dialog = new JFrame("Pump Mechanical " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] columnNames = {"Parameter", "Value", "Unit"};
    String[][] data = {{"Pump Type", pumpType.toString(), ""},
        {"Number of Stages", String.valueOf(numberOfStages), ""},
        {"Rated Speed", String.format("%.0f", ratedSpeed), "rpm"},
        {"Specific Speed", String.format("%.1f", specificSpeed), "-"},
        {"Impeller Diameter", String.format("%.1f", impellerDiameter), "mm"},
        {"Shaft Diameter", String.format("%.1f", shaftDiameter), "mm"},
        {"Driver Power", String.format("%.1f", driverPower), "kW"},
        {"Design Pressure", String.format("%.1f", designPressure), "bara"},
        {"Design Temperature", String.format("%.1f", designTemperature), "°C"},
        {"NPSHr", String.format("%.1f", npshRequired), "m"},
        {"Suction Nozzle", String.format("%.0f", suctionNozzleSize), "inch"},
        {"Discharge Nozzle", String.format("%.0f", dischargeNozzleSize), "inch"},
        {"Total Weight", String.format("%.0f", getWeightTotal()), "kg"}};

    JTable table = new JTable(data, columnNames);
    JScrollPane scrollPane = new JScrollPane(table);
    dialogContentPane.add(scrollPane, BorderLayout.CENTER);

    dialog.setSize(400, 350);
    dialog.setVisible(true);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns a pump-specific response with additional fields for hydraulic data, driver sizing, and
   * NPSH requirements.
   * </p>
   */
  @Override
  public PumpMechanicalDesignResponse getResponse() {
    return new PumpMechanicalDesignResponse(this);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns JSON with pump-specific fields.
   * </p>
   */
  @Override
  public String toJson() {
    return getResponse().toJson();
  }

  // ============================================================================
  // Process Design Parameter Getters/Setters
  // ============================================================================

  /**
   * Gets the NPSH margin factor requirement.
   *
   * @return NPSH margin factor (NPSHa/NPSHr)
   */
  public double getNpshMarginFactor() {
    return npshMarginFactor;
  }

  /**
   * Sets the NPSH margin factor requirement.
   *
   * @param factor NPSH margin factor (typically 1.1-1.3)
   */
  public void setNpshMarginFactor(double factor) {
    this.npshMarginFactor = factor;
  }

  /**
   * Gets the hydraulic power margin.
   *
   * @return hydraulic power margin factor
   */
  public double getHydraulicPowerMargin() {
    return hydraulicPowerMargin;
  }

  /**
   * Sets the hydraulic power margin.
   *
   * @param margin power margin factor (typically 1.05-1.10)
   */
  public void setHydraulicPowerMargin(double margin) {
    this.hydraulicPowerMargin = margin;
  }

  /**
   * Gets the preferred operating region low limit as fraction of BEP.
   *
   * @return POR low limit fraction
   */
  public double getPorLowFraction() {
    return porLowFraction;
  }

  /**
   * Sets the preferred operating region low limit.
   *
   * @param fraction POR low limit as fraction of BEP (typically 0.80)
   */
  public void setPorLowFraction(double fraction) {
    this.porLowFraction = fraction;
  }

  /**
   * Gets the preferred operating region high limit as fraction of BEP.
   *
   * @return POR high limit fraction
   */
  public double getPorHighFraction() {
    return porHighFraction;
  }

  /**
   * Sets the preferred operating region high limit.
   *
   * @param fraction POR high limit as fraction of BEP (typically 1.10)
   */
  public void setPorHighFraction(double fraction) {
    this.porHighFraction = fraction;
  }

  /**
   * Gets the allowable operating region low limit as fraction of BEP.
   *
   * @return AOR low limit fraction
   */
  public double getAorLowFraction() {
    return aorLowFraction;
  }

  /**
   * Sets the allowable operating region low limit.
   *
   * @param fraction AOR low limit as fraction of BEP (typically 0.60-0.70)
   */
  public void setAorLowFraction(double fraction) {
    this.aorLowFraction = fraction;
  }

  /**
   * Gets the allowable operating region high limit as fraction of BEP.
   *
   * @return AOR high limit fraction
   */
  public double getAorHighFraction() {
    return aorHighFraction;
  }

  /**
   * Sets the allowable operating region high limit.
   *
   * @param fraction AOR high limit as fraction of BEP (typically 1.20-1.30)
   */
  public void setAorHighFraction(double fraction) {
    this.aorHighFraction = fraction;
  }

  /**
   * Gets the maximum suction specific speed.
   *
   * @return max Nss value
   */
  public double getMaxSuctionSpecificSpeed() {
    return maxSuctionSpecificSpeed;
  }

  /**
   * Sets the maximum suction specific speed.
   *
   * @param maxNss max Nss value (typically 11000-13000)
   */
  public void setMaxSuctionSpecificSpeed(double maxNss) {
    this.maxSuctionSpecificSpeed = maxNss;
  }

  /**
   * Gets the head margin factor.
   *
   * @return head margin factor as fraction (typically 1.05-1.10)
   */
  public double getHeadMarginFactor() {
    return headMarginFactor;
  }

  /**
   * Sets the head margin factor.
   *
   * @param factor head margin factor as fraction (typically 1.05-1.10)
   */
  public void setHeadMarginFactor(double factor) {
    this.headMarginFactor = factor;
  }

  // ============================================================================
  // Validation Methods
  // ============================================================================

  /**
   * Validates that NPSH available meets the required margin.
   *
   * @param npshAvailableM NPSH available in meters
   * @param npshRequiredM NPSH required in meters
   * @return true if NPSH margin is adequate
   */
  public boolean validateNpshMargin(double npshAvailableM, double npshRequiredM) {
    if (npshRequiredM <= 0) {
      return true;
    }
    return npshAvailableM >= npshRequiredM * npshMarginFactor;
  }

  /**
   * Validates that operating flow is within the preferred operating region.
   *
   * @param operatingFlowM3h operating flow rate in m³/h
   * @param bepFlowM3h BEP flow rate in m³/h
   * @return true if operating in POR
   */
  public boolean validateOperatingInPOR(double operatingFlowM3h, double bepFlowM3h) {
    if (bepFlowM3h <= 0) {
      return false;
    }
    double ratio = operatingFlowM3h / bepFlowM3h;
    return ratio >= porLowFraction && ratio <= porHighFraction;
  }

  /**
   * Validates that operating flow is within the allowable operating region.
   *
   * @param operatingFlowM3h operating flow rate in m³/h
   * @param bepFlowM3h BEP flow rate in m³/h
   * @return true if operating in AOR
   */
  public boolean validateOperatingInAOR(double operatingFlowM3h, double bepFlowM3h) {
    if (bepFlowM3h <= 0) {
      return false;
    }
    double ratio = operatingFlowM3h / bepFlowM3h;
    return ratio >= aorLowFraction && ratio <= aorHighFraction;
  }

  /**
   * Validates that suction specific speed is within acceptable limits.
   *
   * @param actualNss actual suction specific speed
   * @return true if Nss is acceptable
   */
  public boolean validateSuctionSpecificSpeed(double actualNss) {
    return actualNss <= maxSuctionSpecificSpeed;
  }

  /**
   * Performs comprehensive validation of pump design.
   *
   * @return PumpValidationResult with status and any issues found
   */
  public PumpValidationResult validateDesign() {
    PumpValidationResult result = new PumpValidationResult();

    // Validate NPSH margin
    if (npshRequired > 0 && npshAvailable > 0) {
      if (!validateNpshMargin(npshAvailable, npshRequired)) {
        result.addIssue("NPSH margin " + String.format("%.2f", npshAvailable / npshRequired)
            + " below required " + String.format("%.2f", npshMarginFactor));
      }
    }

    // Validate operating point vs BEP
    if (bepFlow > 0) {
      double operatingFlow = bepFlow; // Assuming design point = BEP
      if (!validateOperatingInPOR(operatingFlow, bepFlow)) {
        result.addIssue("Operating point outside Preferred Operating Region (POR)");
      }
    }

    // Validate suction specific speed
    if (suctionSpecificSpeed > 0 && !validateSuctionSpecificSpeed(suctionSpecificSpeed)) {
      result.addIssue("Suction specific speed " + String.format("%.0f", suctionSpecificSpeed)
          + " exceeds maximum " + String.format("%.0f", maxSuctionSpecificSpeed));
    }

    // Validate design margins
    if (driverMargin < 1.05) {
      result.addIssue(
          "Driver power margin " + String.format("%.2f", driverMargin) + " below recommended 1.05");
    }

    result.setValid(result.getIssues().isEmpty());
    return result;
  }

  /**
   * Loads pump design parameters from the database.
   */
  public void loadProcessDesignParameters() {
    try {
      neqsim.util.database.NeqSimProcessDesignDataBase database =
          new neqsim.util.database.NeqSimProcessDesignDataBase();
      java.sql.ResultSet dataSet =
          database.getResultSet("SELECT * FROM technicalrequirements_process WHERE "
              + "EQUIPMENTTYPE='Pump' AND Company='" + getCompanySpecificDesignStandards() + "'");

      while (dataSet.next()) {
        String spec = dataSet.getString("SPECIFICATION");
        double minVal = dataSet.getDouble("MINVALUE");
        double maxVal = dataSet.getDouble("MAXVALUE");
        double value = (minVal + maxVal) / 2.0;

        switch (spec) {
          case "NPSHMarginFactor":
            this.npshMarginFactor = value;
            break;
          case "HydraulicPowerMargin":
            this.hydraulicPowerMargin = value;
            break;
          case "PreferredOperatingRegionLow":
            this.porLowFraction = value;
            break;
          case "PreferredOperatingRegionHigh":
            this.porHighFraction = value;
            break;
          case "AllowableOperatingRegionLow":
            this.aorLowFraction = value;
            break;
          case "AllowableOperatingRegionHigh":
            this.aorHighFraction = value;
            break;
          case "SuctionSpecificSpeedMax":
            this.maxSuctionSpecificSpeed = value;
            break;
          default:
            // Ignore unknown parameters
            break;
        }
      }
      dataSet.close();
    } catch (Exception ex) {
      // Use default values if database lookup fails
    }
  }

  /**
   * Inner class to hold validation results.
   */
  public static class PumpValidationResult {
    private boolean valid = true;
    private java.util.List<String> issues = new java.util.ArrayList<>();

    public boolean isValid() {
      return valid;
    }

    public void setValid(boolean valid) {
      this.valid = valid;
    }

    public java.util.List<String> getIssues() {
      return issues;
    }

    public void addIssue(String issue) {
      issues.add(issue);
    }
  }
}
