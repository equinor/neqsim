package neqsim.process.mechanicaldesign.pump;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.costestimation.pump.PumpCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.pump.PumpChartInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.Api610PumpType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.DataSource;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Mechanical design calculations for centrifugal pumps per API 610.
 *
 * <p>
 * This class provides sizing and design calculations for centrifugal pumps based on API 610 (Centrifugal Pumps for
 * Petroleum, Petrochemical and Natural Gas Industries) and Hydraulic Institute standards. Calculations include:
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
  private double porLowFraction = 0.70;

  /** Preferred operating region - high limit as fraction of BEP. */
  private double porHighFraction = 1.20;

  /** Rated-point region - low limit as fraction of furnished BEP. */
  private double ratedPointLowFraction = 0.80;

  /** Rated-point region - high limit as fraction of furnished BEP. */
  private double ratedPointHighFraction = 1.10;

  /** Allowable operating region - low limit as fraction of BEP. */
  private double aorLowFraction = 0.60;

  /** Allowable operating region - high limit as fraction of BEP. */
  private double aorHighFraction = 1.30;

  /** Maximum suction specific speed for stable operation. */
  private double maxSuctionSpecificSpeed = 11000.0;

  /** Head margin factor. */
  private double headMarginFactor = 1.05;

  /** Minimum absolute NPSH margin [m] used by the API 610 screening profile. */
  private double minimumNpshMarginM = 1.0;

  /** Exact API 610 construction type selected or recommended. */
  private Api610PumpType api610PumpType = Api610PumpType.UNSPECIFIED;

  /** True when the exact API 610 type was supplied rather than estimated. */
  private boolean api610PumpTypeUserSpecified = false;

  /** Current rated/duty flow [m3/h]. */
  private double ratedFlow = Double.NaN;

  /** Current rated/duty head [m]. */
  private double ratedHead = Double.NaN;

  /** Absorbed shaft power from the process pump [kW]. */
  private double absorbedPower = Double.NaN;

  /** Hydraulic liquid power at the duty point [kW]. */
  private double hydraulicPower = Double.NaN;

  /** Maximum suction pressure used for casing screening [bara]. */
  private double maximumSuctionPressure = Double.NaN;

  /** True when maximum suction pressure was explicitly supplied by the purchaser. */
  private boolean maximumSuctionPressureUserSpecified = false;

  /** Furnished casing MAWP supplied by purchaser or vendor [bara]. */
  private double furnishedCasingMawp = Double.NaN;

  /** Shutoff head from a vendor curve or purchaser input [m]. */
  private double shutoffHead = Double.NaN;

  /** True when shutoff head was explicitly supplied by the purchaser. */
  private boolean shutoffHeadUserSpecified = false;

  /** Source of the BEP values. */
  private DataSource bepDataSource = DataSource.NOT_AVAILABLE;

  /** Source of the NPSHr value. */
  private DataSource npshrDataSource = DataSource.NOT_AVAILABLE;

  /** Source of the shutoff head value. */
  private DataSource shutoffHeadDataSource = DataSource.NOT_AVAILABLE;

  /** API 610 screening calculator and auditable result. */
  private final PumpApi610DesignCalculator api610Assessment = new PumpApi610DesignCalculator();

  /**
   * Constructor for PumpMechanicalDesign.
   *
   * @param equipment the pump equipment
   */
  public PumpMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    costEstimate = new PumpCostEstimate(this);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Pump pump = (Pump) getProcessEquipment();

    // Ensure pump has been run
    if (pump.getInletStream() == null || pump.getOutletStream() == null
        || pump.getInletStream().getThermoSystem() == null) {
      return;
    }

    // Get operating conditions
    double suctionPressure = pump.getInletStream().getPressure("bara");
    double dischargePressure = pump.getOutletStream().getPressure("bara");
    double suctionTemperature = pump.getInletStream().getTemperature("C");
    double dischargeTemperature = pump.getOutletStream().getTemperature("C");
    double volumeFlowRate = pump.getInletStream().getFlowRate("m3/hr");
    double density = pump.getInletStream().getThermoSystem().getDensity("kg/m3");
    absorbedPower = pump.getPower("kW");

    // Calculate differential pressure and head
    double differentialPressure = dischargePressure - suctionPressure;
    double pumpHead = density > 0.0 ? (differentialPressure * 1e5) / (density * GRAVITY) : Double.NaN;
    ratedFlow = volumeFlowRate;
    ratedHead = pumpHead;
    hydraulicPower = density > 0.0 && volumeFlowRate > 0.0 && pumpHead > 0.0
        ? density * GRAVITY * volumeFlowRate / 3600.0 * pumpHead / 1000.0
        : Double.NaN;

    // Get or estimate pump efficiency
    pumpEfficiency = normalizeEfficiency(pump.getIsentropicEfficiency());
    if (pumpEfficiency <= 0 || pumpEfficiency > 1.0) {
      pumpEfficiency = DEFAULT_PUMP_EFFICIENCY;
    }

    if (!(absorbedPower > 0.0) && hydraulicPower > 0.0) {
      absorbedPower = hydraulicPower / pumpEfficiency;
    }

    // Preserve the process-model shaft speed. Estimate only if it is unavailable.
    ratedSpeed = pump.getSpeed();
    if (!(ratedSpeed > 0.0) && volumeFlowRate > 0.0 && pumpHead > 0.0) {
      ratedSpeed = estimateOptimalSpeed(volumeFlowRate, pumpHead);
    }

    // Prefer vendor performance data for BEP, shutoff head and NPSHr.
    populateVendorCurveData(pump, ratedSpeed, density);

    npshRequired = pump.getNPSHRequired();
    npshAvailable = pump.getNPSHAvailable();
    if (pump.getPumpChart() != null && pump.getPumpChart().isUsePumpChart()
        && pump.getPumpChart().hasNPSHCurve()) {
      npshrDataSource = DataSource.VENDOR_CURVE;
    } else if (npshRequired > 0.0) {
      npshrDataSource = DataSource.SCREENING_ESTIMATE;
    } else {
      npshrDataSource = DataSource.NOT_AVAILABLE;
    }
    npshMargin = Double.isFinite(npshAvailable) && npshRequired > 0.0 ? npshAvailable - npshRequired : Double.NaN;

    // Calculate specific speeds on explicit SI and customary suction-specific-speed bases.
    double specificSpeedFlow = bepFlow > 0.0 ? bepFlow : volumeFlowRate;
    double specificSpeedHead = bepHead > 0.0 ? bepHead : pumpHead;
    if (specificSpeedFlow > 0.0 && specificSpeedHead > 0.0 && ratedSpeed > 0.0) {
      calculateSpecificSpeeds(specificSpeedFlow, specificSpeedHead, ratedSpeed);
    }

    // Select a preliminary construction type only when the purchaser has not fixed one.
    selectPumpType(pumpHead);

    if (!maximumSuctionPressureUserSpecified) {
      maximumSuctionPressure = suctionPressure;
    }
    double governingShutoffHead = shutoffHead > 0.0 ? shutoffHead : pumpHead * 1.10;
    designPressure = maximumSuctionPressure + density * GRAVITY * governingShutoffHead / 1.0e5;
    designPressure = Math.max(designPressure, dischargePressure);
    designTemperature = Math.max(suctionTemperature, dischargeTemperature) + DESIGN_TEMPERATURE_MARGIN;

    // Calculate impeller sizing
    calculateImpellerSizing(volumeFlowRate, pumpHead, ratedSpeed);

    // Calculate shaft sizing
    calculateShaftSizing(absorbedPower, ratedSpeed);

    // Calculate casing design
    calculateCasingDesign(designPressure, designTemperature);

    // Calculate driver sizing
    calculateDriverSizing(absorbedPower);

    // Calculate nozzle sizes
    calculateNozzleSizes(volumeFlowRate);

    // Calculate weights
    calculateWeights(volumeFlowRate, designPressure);

    // Calculate module dimensions
    calculateModuleDimensions();

    // Set operating range. Vendor BEP is preferred; current duty is only a preliminary fallback.
    double operatingRangeBasis = bepFlow > 0.0 ? bepFlow : volumeFlowRate;
    minimumFlow = operatingRangeBasis * minContinuousFlowFraction;
    maximumFlow = operatingRangeBasis * maxContinuousFlowFraction;
    maxDesignVolumeFlow = maximumFlow;
    maxDesignPower = driverPower;

    configureAndRunApi610Assessment(suctionPressure, density);
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
    double[] standardSpeeds = { 2950, 1475, 985, 740 };
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

  private double normalizeEfficiency(double efficiency) {
    if (efficiency > 1.0 && efficiency <= 100.0) {
      return efficiency / 100.0;
    }
    return efficiency;
  }

  private void populateVendorCurveData(Pump pump, double speedRpm, double densityKgM3) {
    bepFlow = Double.NaN;
    bepHead = Double.NaN;
    bepDataSource = DataSource.NOT_AVAILABLE;
    if (!shutoffHeadUserSpecified) {
      shutoffHead = Double.NaN;
      shutoffHeadDataSource = DataSource.NOT_AVAILABLE;
    }
    PumpChartInterface chart = pump.getPumpChart();
    if (chart == null || !chart.isUsePumpChart() || !(speedRpm > 0.0)) {
      return;
    }
    double chartBepFlow = chart.getBestEfficiencyFlowRate(speedRpm);
    if (chartBepFlow > 0.0) {
      double chartBepHead = convertChartHeadToMeters(chart.getHead(chartBepFlow, speedRpm), chart.getHeadUnit(),
          densityKgM3);
      if (chartBepHead > 0.0) {
        bepFlow = chartBepFlow;
        bepHead = chartBepHead;
        bepDataSource = DataSource.VENDOR_CURVE;
      }
    }
    if (!shutoffHeadUserSpecified) {
      double chartShutoffHead =
          convertChartHeadToMeters(chart.getHead(0.0, speedRpm), chart.getHeadUnit(), densityKgM3);
      if (chartShutoffHead > 0.0) {
        shutoffHead = chartShutoffHead;
        shutoffHeadDataSource = DataSource.VENDOR_CURVE;
      }
    }
  }

  private double convertChartHeadToMeters(double chartHead, String unit, double densityKgM3) {
    if (!(chartHead > 0.0) || unit == null) {
      return Double.NaN;
    }
    if (unit.equals("meter") || unit.equals("m")) {
      return chartHead;
    }
    if (unit.equals("kJ/kg")) {
      return chartHead * 1000.0 / GRAVITY;
    }
    if (unit.equals("bar") && densityKgM3 > 0.0) {
      return chartHead * 1.0e5 / (densityKgM3 * GRAVITY);
    }
    return Double.NaN;
  }

  private void configureAndRunApi610Assessment(double suctionPressureBara, double densityKgM3) {
    api610Assessment.setPumpType(api610PumpType,
        api610PumpTypeUserSpecified ? DataSource.PURCHASER_INPUT : DataSource.SCREENING_ESTIMATE);
    api610Assessment.setDutyPoint(ratedFlow, ratedHead, ratedSpeed, densityKgM3, absorbedPower);
    api610Assessment.setDutyPointSource(DataSource.PROCESS_MODEL);
    api610Assessment.setBepPoint(bepFlow, bepHead, bepDataSource);
    api610Assessment.setNpsh(npshAvailable, npshRequired, npshrDataSource);
    api610Assessment.setOperatingRegions(porLowFraction, porHighFraction, aorLowFraction, aorHighFraction);
    api610Assessment.setRatedPointRegion(ratedPointLowFraction, ratedPointHighFraction);
    api610Assessment.setNpshCriteria(npshMarginFactor, minimumNpshMarginM);
    api610Assessment.setDriverCriteria(driverMargin, null);
    api610Assessment.setPressureBasis(
        Double.isFinite(maximumSuctionPressure) ? maximumSuctionPressure : suctionPressureBara,
        furnishedCasingMawp, shutoffHead, shutoffHeadDataSource);
    api610Assessment.setSeallessPump(sealType == SealType.MAGNETIC_DRIVE || sealType == SealType.CANNED_MOTOR);
    api610Assessment.calculate();
    double selectedDriver = api610Assessment.getSelectedDriverPowerKw();
    if (selectedDriver > 0.0) {
      driverPower = selectedDriver;
      maxDesignPower = selectedDriver;
    }
  }

  /**
   * Calculate specific speeds for pump classification.
   *
   * @param flowM3h volume flow rate in m³/h
   * @param headM pump head in meters
   * @param speedRpm pump speed in rpm
   */
  private void calculateSpecificSpeeds(double flowM3h, double headM, double speedRpm) {
    double flowM3s = flowM3h / 3600.0;

    // SI specific speed: N * sqrt(Q[m3/s]) / H[m]^0.75.
    specificSpeed = speedRpm * Math.sqrt(flowM3s) / Math.pow(headM, 0.75);

    // Conventional US suction specific speed, compatible with common API purchaser limits.
    if (npshRequired > 0.0) {
      double flowPerEyeGpm = flowM3h * 4.402867;
      if (api610PumpType == Api610PumpType.BB1) {
        flowPerEyeGpm /= 2.0;
      }
      double npshFeet = npshRequired * 3.28084;
      suctionSpecificSpeed = speedRpm * Math.sqrt(flowPerEyeGpm) / Math.pow(npshFeet, 0.75);
    } else {
      suctionSpecificSpeed = Double.NaN;
    }
  }

  /**
   * Select pump type based on specific speed and application.
   */
  private void selectPumpType(double totalHeadM) {
    if (api610PumpTypeUserSpecified) {
      mapApi610TypeToLegacyFamily();
      return;
    }
    if (totalHeadM > 250.0) {
      pumpType = PumpType.BETWEEN_BEARINGS;
      numberOfStages = Math.max(2, (int) Math.ceil(totalHeadM / 200.0));
      api610PumpType = numberOfStages > 2 ? Api610PumpType.BB3 : Api610PumpType.BB2;
    } else {
      pumpType = PumpType.OVERHUNG;
      numberOfStages = 1;
      api610PumpType = Api610PumpType.OH2;
    }
  }

  private void mapApi610TypeToLegacyFamily() {
    String code = api610PumpType.name();
    if (code.startsWith("OH")) {
      pumpType = PumpType.OVERHUNG;
      numberOfStages = 1;
    } else if (code.startsWith("BB")) {
      pumpType = PumpType.BETWEEN_BEARINGS;
      if (api610PumpType == Api610PumpType.BB3 || api610PumpType == Api610PumpType.BB4
          || api610PumpType == Api610PumpType.BB5) {
        numberOfStages = Math.max(2, numberOfStages);
      }
    } else if (code.startsWith("VS")) {
      pumpType = PumpType.VERTICALLY_SUSPENDED;
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
   */
  private void calculateShaftSizing(double powerKW, double speedRpm) {
    if (speedRpm <= 0) {
      shaftDiameter = 40.0; // Default
      return;
    }

    // Calculate torque: T = P * 60 / (2 * pi * N) [kNm]
    double torqueKNm = (powerKW * 60.0) / (2.0 * Math.PI * speedRpm);
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
    maxAllowableWorkingPressure = (2.0 * allowableStressMPa * jointEfficiency * (casingWallThickness - 3.0))
        / (casingDiameterMm + 0.6 * (casingWallThickness - 3.0)) * 10.0; // bara
  }

  /**
   * Calculate driver sizing with appropriate margins per API 610.
   *
   * @param absorbedPowerKW absorbed shaft power in kW
   */
  private void calculateDriverSizing(double absorbedPowerKW) {
    // Select margin based on power range per API 610
    if (absorbedPowerKW < 22.0) {
      driverMargin = DRIVER_MARGIN_SMALL;
    } else if (absorbedPowerKW <= 55.0) {
      driverMargin = DRIVER_MARGIN_MEDIUM;
    } else {
      driverMargin = DRIVER_MARGIN_LARGE;
    }

    // Driver power with margin
    driverPower = absorbedPowerKW * driverMargin;

    // Round up to standard motor sizes (IEC frame sizes)
    double[] standardPowers = { 0.75, 1.1, 1.5, 2.2, 3.0, 4.0, 5.5, 7.5, 11.0, 15.0, 18.5, 22.0, 30.0, 37.0, 45.0, 55.0,
        75.0, 90.0, 110.0, 132.0, 160.0, 200.0, 250.0, 315.0, 400.0 };

    double selectedPower = PumpApi610DesignCalculator.selectDriverRating(driverPower, standardPowers);
    if (selectedPower > 0.0) {
      driverPower = selectedPower;
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
    double[] standardSizes = { 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0 };

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
    double impellerVolume = Math.PI * Math.pow(impellerDiameter / 2000.0, 2) * (impellerWidth / 1000.0) * 0.3;
    impellerWeight = impellerVolume * STEEL_DENSITY;

    // Shaft weight (estimate length based on pump type)
    double shaftLength = (pumpType == PumpType.OVERHUNG) ? 500.0 : 1000.0; // mm
    double shaftVolume = Math.PI * Math.pow(shaftDiameter / 2000.0, 2) * (shaftLength / 1000.0);
    shaftWeight = shaftVolume * STEEL_DENSITY;

    // Casing weight
    double casingOD = (impellerDiameter * 1.15 + 2.0 * casingWallThickness) / 1000.0; // m
    double casingID = impellerDiameter * 1.15 / 1000.0; // m
    double casingLength = (impellerWidth + 100.0) / 1000.0 * numberOfStages; // m
    double casingVolume = Math.PI / 4.0 * (Math.pow(casingOD, 2) - Math.pow(casingID, 2)) * casingLength;
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
    double emptyWeight = casingWeight + impellerWeight + shaftWeight + motorWeight + baseplateWeight + sealWeight
        + couplingWeight;

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
    outerDiameter = (impellerDiameter * 1.15 + 2.0 * casingWallThickness) / 1000.0;
    innerDiameter = impellerDiameter * 1.15 / 1000.0;
    tantanLength = impellerWidth * numberOfStages / 1000.0;
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
    if (pumpType == PumpType.OVERHUNG) {
      setApi610PumpType(Api610PumpType.OH2);
    } else if (pumpType == PumpType.BETWEEN_BEARINGS) {
      setApi610PumpType(Api610PumpType.BB3);
    } else if (pumpType == PumpType.VERTICALLY_SUSPENDED) {
      setApi610PumpType(Api610PumpType.VS1);
    }
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
   * Get the estimated impeller outlet width.
   *
   * @return impeller width in mm
   */
  public double getImpellerWidth() {
    return impellerWidth;
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
   * Get the driver sizing multiplier applied to absorbed power.
   *
   * @return driver margin factor
   */
  public double getDriverMargin() {
    return driverMargin;
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
   * Get NPSH available from the process suction state.
   *
   * @return NPSHa in m, or NaN when unavailable
   */
  public double getNpshAvailable() {
    return npshAvailable;
  }

  /**
   * Get absolute NPSH margin.
   *
   * @return NPSHa minus NPSHr in m, or NaN when unavailable
   */
  public double getNpshMargin() {
    return npshMargin;
  }

  /**
   * Get NPSH ratio.
   *
   * @return NPSHa divided by NPSHr, or NaN when unavailable
   */
  public double getNpshMarginRatio() {
    return npshRequired > 0.0 && Double.isFinite(npshAvailable) ? npshAvailable / npshRequired : Double.NaN;
  }

  /**
   * Get casing wall thickness from the preliminary pressure-boundary estimate.
   *
   * @return casing wall thickness in mm
   */
  public double getCasingWallThickness() {
    return casingWallThickness;
  }

  /**
   * Get preliminary calculated casing MAWP.
   *
   * @return estimated MAWP in bara
   */
  public double getMaxAllowableWorkingPressure() {
    return maxAllowableWorkingPressure;
  }

  /**
   * Get rated process flow.
   *
   * @return rated flow in m3/h
   */
  public double getRatedFlow() {
    return ratedFlow;
  }

  /**
   * Get rated process head.
   *
   * @return rated head in m
   */
  public double getRatedHead() {
    return ratedHead;
  }

  /**
   * Get vendor BEP flow.
   *
   * @return BEP flow in m3/h, or NaN when a vendor curve is unavailable
   */
  public double getBepFlow() {
    return bepFlow;
  }

  /**
   * Get vendor BEP head.
   *
   * @return BEP head in m, or NaN when a vendor curve is unavailable
   */
  public double getBepHead() {
    return bepHead;
  }

  /**
   * Get normalized pump efficiency.
   *
   * @return efficiency as a fraction
   */
  public double getPumpEfficiency() {
    return pumpEfficiency;
  }

  /**
   * Get absorbed shaft power.
   *
   * @return absorbed power in kW
   */
  public double getAbsorbedPower() {
    return absorbedPower;
  }

  /**
   * Get hydraulic liquid power.
   *
   * @return hydraulic power in kW
   */
  public double getHydraulicPower() {
    return hydraulicPower;
  }

  /**
   * Get exact API 610 construction type.
   *
   * @return selected or recommended construction type
   */
  public Api610PumpType getApi610PumpType() {
    return api610PumpType;
  }

  /**
   * Set exact API 610 construction type from purchaser or vendor data.
   *
   * @param type exact API 610 construction type
   */
  public void setApi610PumpType(Api610PumpType type) {
    this.api610PumpType = type == null ? Api610PumpType.UNSPECIFIED : type;
    this.api610PumpTypeUserSpecified = this.api610PumpType != Api610PumpType.UNSPECIFIED;
    if (this.api610PumpTypeUserSpecified) {
      mapApi610TypeToLegacyFamily();
    }
  }

  /**
   * Set maximum suction pressure used for maximum-discharge-pressure screening.
   *
   * @param pressureBara maximum suction pressure in bara
   */
  public void setMaximumSuctionPressure(double pressureBara) {
    this.maximumSuctionPressure = pressureBara;
    this.maximumSuctionPressureUserSpecified = Double.isFinite(pressureBara) && pressureBara > 0.0;
  }

  /**
   * Set furnished casing MAWP for verification.
   *
   * @param mawpBara furnished casing MAWP in bara
   */
  public void setFurnishedCasingMawp(double mawpBara) {
    this.furnishedCasingMawp = mawpBara;
  }

  /**
   * Set purchaser or vendor shutoff head.
   *
   * @param headM shutoff head in m
   */
  public void setShutoffHead(double headM) {
    this.shutoffHead = headM;
    this.shutoffHeadUserSpecified = Double.isFinite(headM) && headM > 0.0;
    this.shutoffHeadDataSource =
        this.shutoffHeadUserSpecified ? DataSource.PURCHASER_INPUT : DataSource.NOT_AVAILABLE;
  }

  /**
   * Get the auditable API 610 design screening calculator and latest result.
   *
   * <p>
   * Optional vendor evidence such as bearing ratings, shaft deflection, critical speed, nozzle-load utilization and
   * vibration may be configured directly on this value-only object before the next {@link #calcDesign()} call.
   * </p>
   *
   * @return API 610 screening calculator
   */
  public PumpApi610DesignCalculator getApi610Assessment() {
    return api610Assessment;
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

    String[] columnNames = { "Parameter", "Value", "Unit" };
    String[][] data = { { "Pump Type", pumpType.toString(), "" },
        { "Number of Stages", String.valueOf(numberOfStages), "" },
        { "Rated Speed", String.format("%.0f", ratedSpeed), "rpm" },
        { "Specific Speed", String.format("%.1f", specificSpeed), "-" },
        { "Impeller Diameter", String.format("%.1f", impellerDiameter), "mm" },
        { "Shaft Diameter", String.format("%.1f", shaftDiameter), "mm" },
        { "Driver Power", String.format("%.1f", driverPower), "kW" },
        { "Design Pressure", String.format("%.1f", designPressure), "bara" },
        { "Design Temperature", String.format("%.1f", designTemperature), "°C" },
        { "NPSHr", String.format("%.1f", npshRequired), "m" },
        { "Suction Nozzle", String.format("%.0f", suctionNozzleSize), "inch" },
        { "Discharge Nozzle", String.format("%.0f", dischargeNozzleSize), "inch" },
        { "Total Weight", String.format("%.0f", getWeightTotal()), "kg" } };

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
   * Returns a pump-specific response with additional fields for hydraulic data, driver sizing, and NPSH requirements.
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
   * Gets the minimum absolute NPSH margin.
   *
   * @return minimum NPSHa minus NPSHr in m
   */
  public double getMinimumNpshMarginM() {
    return minimumNpshMarginM;
  }

  /**
   * Sets the minimum absolute NPSH margin.
   *
   * @param minimumMarginM minimum NPSHa minus NPSHr in m
   */
  public void setMinimumNpshMarginM(double minimumMarginM) {
    this.minimumNpshMarginM = minimumMarginM;
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
        result.addIssue("NPSH margin " + String.format("%.2f", npshAvailable / npshRequired) + " below required "
            + String.format("%.2f", npshMarginFactor));
      }
    }

    // Validate operating point vs BEP
    if (bepFlow > 0 && ratedFlow > 0) {
      if (!validateOperatingInPOR(ratedFlow, bepFlow)) {
        result.addIssue("Operating point outside Preferred Operating Region (POR)");
      }
    }

    // Validate suction specific speed
    if (suctionSpecificSpeed > 0 && !validateSuctionSpecificSpeed(suctionSpecificSpeed)) {
      result.addIssue("Suction specific speed " + String.format("%.0f", suctionSpecificSpeed) + " exceeds maximum "
          + String.format("%.0f", maxSuctionSpecificSpeed));
    }

    // Validate design margins
    if (driverMargin < 1.05) {
      result.addIssue("Driver power margin " + String.format("%.2f", driverMargin) + " below recommended 1.05");
    }

    for (PumpApi610DesignCalculator.Check check : api610Assessment.getChecks()) {
      if (check.getStatus() == PumpApi610DesignCalculator.CheckStatus.FAIL
          || check.getStatus() == PumpApi610DesignCalculator.CheckStatus.WARNING) {
        result.addIssue(check.getId() + " [" + check.getStatus() + "]: " + check.getMessage());
      }
    }

    result.setValid(result.getIssues().isEmpty());
    return result;
  }

  /**
   * Loads pump design parameters from the database.
   */
  public void loadProcessDesignParameters() {
    try (
        neqsim.util.database.NeqSimProcessDesignDataBase database = new neqsim.util.database.NeqSimProcessDesignDataBase();
        java.sql.ResultSet dataSet = database.getResultSet("SELECT * FROM technicalrequirements_process WHERE "
            + "EQUIPMENTTYPE='Pump' AND Company='" + getCompanySpecificDesignStandards() + "'")) {

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
          // Legacy data rows contain the 80-110% rated-point band, not the wider POR.
          this.ratedPointLowFraction = value;
          break;
        case "PreferredOperatingRegionHigh":
          this.ratedPointHighFraction = value;
          break;
        case "AllowableOperatingRegionLow":
          this.aorLowFraction = value;
          break;
        case "AllowableOperatingRegionHigh":
          this.aorHighFraction = value;
          break;
        case "MinContinuousFlow":
          this.minContinuousFlowFraction = value;
          break;
        case "MaxContinuousFlow":
          this.maxContinuousFlowFraction = value;
          break;
        case "SuctionSpecificSpeedMax":
          this.maxSuctionSpecificSpeed = value;
          break;
        default:
          // Ignore unknown parameters
          break;
        }
      }
    } catch (Exception ex) {
      // Use default values if database lookup fails
    }
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    loadProcessDesignParameters();
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
