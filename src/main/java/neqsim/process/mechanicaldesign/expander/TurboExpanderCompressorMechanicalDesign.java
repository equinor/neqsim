package neqsim.process.mechanicaldesign.expander;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.expander.TurboExpanderCompressor;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Mechanical design for a coupled turbo-expander-compressor unit per API 617.
 *
 * <p>
 * Unlike the standalone {@link ExpanderMechanicalDesign} or
 * {@link neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign}, this class
 * considers <em>both</em> ends of the shared shaft simultaneously:
 * </p>
 * <ul>
 * <li>Expander wheel sizing (radial inflow, axial, mixed-flow selection)</li>
 * <li>Compressor impeller staging and sizing</li>
 * <li>Shared-shaft diameter from combined torque</li>
 * <li>Coupled rotor-dynamics (critical speeds across full bearing span)</li>
 * <li>Independent casing design for each end (different pressures)</li>
 * <li>Bearing system (journal and thrust) for combined radial and axial loads</li>
 * <li>Seal system (potentially different types at each end)</li>
 * <li>Module weight and footprint</li>
 * </ul>
 *
 * <p>
 * After {@link #calcDesign()} sizes the hardware at design-point conditions, call
 * {@link #evaluateDesignAtConditions} or {@link #evaluateDesignWithFluid} to check whether the
 * <em>fixed</em> design can handle different operating points (turndown, changed compositions,
 * different pressures).
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>API 617 - Axial and Centrifugal Compressors and Expander-compressors</li>
 * <li>API 612 - Steam Turbines</li>
 * <li>ASME Section VIII Division 1 - Pressure Vessels</li>
 * <li>ISO 10439 - Petroleum, petrochemical and natural gas industries - Centrifugal compressors
 * </li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class TurboExpanderCompressorMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  // ============================================================================
  // Design Constants (API 617 / ASME / Industry Practice)
  // ============================================================================

  /** Maximum expander wheel tip speed [m/s] - titanium or high-alloy steel. */
  private static final double MAX_EXPANDER_TIP_SPEED = 450.0;

  /** Maximum compressor impeller tip speed [m/s] - carbon steel. */
  private static final double MAX_COMPRESSOR_TIP_SPEED = 350.0;

  /** Design pressure margin factor. */
  private static final double DESIGN_PRESSURE_MARGIN = 1.10;

  /** Design temperature margin [deg C]. */
  private static final double DESIGN_TEMPERATURE_MARGIN = 30.0;

  /** Steel density [kg/m^3]. */
  private static final double STEEL_DENSITY = 7850.0;

  /** Typical radial-inflow turbine velocity ratio. */
  private static final double VELOCITY_RATIO = 0.7;

  /** Maximum polytropic head per compressor stage [kJ/kg]. */
  private static final double MAX_HEAD_PER_STAGE = 30.0;

  /** Minimum API 617 critical speed separation margin [fraction]. */
  private static final double MIN_CRITICAL_SPEED_SEPARATION = 0.15;

  /** Allowable shear stress for forged alloy steel [Pa]. */
  private static final double ALLOWABLE_SHEAR_STRESS = 50.0e6;

  /** Maximum compressor discharge temperature [deg C]. */
  private static final double MAX_DISCHARGE_TEMP_C = 150.0;

  /** Default shear pin safety factor (operating torque should be below break / SF). */
  private static final double SHEAR_PIN_SAFETY_FACTOR = 1.5;

  /** Minimum required seal gas differential pressure above process [bar]. */
  private static final double MIN_SEAL_GAS_DP_BAR = 1.5;

  /** Anti-surge control line margin above surge [fraction]. */
  private static final double ANTI_SURGE_CONTROL_LINE_MARGIN = 0.10;

  // ============================================================================
  // Expander-side design results
  // ============================================================================

  /** Expander wheel outer diameter [mm]. */
  private double expanderWheelDiameter = 300.0;

  /** Expander wheel tip speed [m/s]. */
  private double expanderTipSpeed = 300.0;

  /** Expander type (RADIAL_INFLOW, AXIAL, MIXED_FLOW). */
  private String expanderType = "RADIAL_INFLOW";

  /** Number of expander stages. */
  private int expanderStages = 1;

  /** Expander design inlet pressure [bara]. */
  private double expanderDesignInletPressure = 0.0;

  /** Expander design outlet pressure [bara]. */
  private double expanderDesignOutletPressure = 0.0;

  /** Expander design inlet temperature [deg C]. */
  private double expanderDesignInletTemperature = 0.0;

  /** Expander design outlet temperature [deg C]. */
  private double expanderDesignOutletTemperature = 0.0;

  /** Expander casing design pressure [bara]. */
  private double expanderCasingDesignPressure = 0.0;

  /** Expander casing design temperature [deg C]. */
  private double expanderCasingDesignTemperature = 0.0;

  /** Expander casing wall thickness [mm]. */
  private double expanderCasingWallThickness = 20.0;

  /** Expander recovered power [kW]. */
  private double expanderPowerKW = 0.0;

  /** Expander isentropic enthalpy drop [kJ/kg]. */
  private double expanderEnthalpyDropKJkg = 0.0;

  /** Expander wheel weight [kg]. */
  private double expanderWheelWeight = 0.0;

  /** Expander casing weight [kg]. */
  private double expanderCasingWeight = 0.0;

  // ============================================================================
  // Compressor-side design results
  // ============================================================================

  /** Compressor impeller diameter [mm]. */
  private double compressorImpellerDiameter = 300.0;

  /** Compressor tip speed [m/s]. */
  private double compressorTipSpeed = 250.0;

  /** Number of compressor stages. */
  private int compressorStages = 1;

  /** Compressor polytropic head [kJ/kg]. */
  private double compressorPolytropicHead = 0.0;

  /** Head per compressor stage [kJ/kg]. */
  private double compressorHeadPerStage = 0.0;

  /** Compressor design suction pressure [bara]. */
  private double compressorDesignSuctionPressure = 0.0;

  /** Compressor design discharge pressure [bara]. */
  private double compressorDesignDischargePressure = 0.0;

  /** Compressor design suction temperature [deg C]. */
  private double compressorDesignSuctionTemperature = 0.0;

  /** Compressor design discharge temperature [deg C]. */
  private double compressorDesignDischargeTemperature = 0.0;

  /** Compressor casing design pressure [bara]. */
  private double compressorCasingDesignPressure = 0.0;

  /** Compressor casing design temperature [deg C]. */
  private double compressorCasingDesignTemperature = 0.0;

  /** Compressor casing wall thickness [mm]. */
  private double compressorCasingWallThickness = 20.0;

  /** Compressor power absorbed [kW]. */
  private double compressorPowerKW = 0.0;

  /** Compressor impeller weight [kg]. */
  private double compressorImpellerWeight = 0.0;

  /** Compressor casing weight [kg]. */
  private double compressorCasingWeight = 0.0;

  /** Compressor casing type description. */
  private String compressorCasingType = "VERTICALLY_SPLIT";

  /** Design surge flow [m3/hr]. */
  private double designSurgeFlowM3hr = 0.0;

  /** Design stonewall flow [m3/hr]. */
  private double designStonewallFlowM3hr = 0.0;

  /** Design actual volume flow through compressor [m3/hr]. */
  private double designCompressorVolumeFlowM3hr = 0.0;

  // ============================================================================
  // Shared-shaft design results
  // ============================================================================

  /** Shaft diameter [mm] (sized for combined torque). */
  private double shaftDiameter = 60.0;

  /** Shaft weight [kg]. */
  private double shaftWeight = 0.0;

  /** Matched operating speed [rpm]. */
  private double operatingSpeed = 0.0;

  /** Maximum continuous speed [rpm]. */
  private double maxContinuousSpeed = 0.0;

  /** Trip speed [rpm]. */
  private double tripSpeed = 0.0;

  /** First lateral critical speed [rpm]. */
  private double firstCriticalSpeed = 0.0;

  /** Second lateral critical speed [rpm]. */
  private double secondCriticalSpeed = 0.0;

  /** Bearing span [mm]. */
  private double bearingSpan = 0.0;

  /** Bearing type. */
  private String bearingType = "Tilting Pad";

  /** Expander-end seal type. */
  private String expanderSealType = "Labyrinth";

  /** Compressor-end seal type. */
  private String compressorSealType = "Dry Gas";

  /** Gear ratio (1.0 for direct-coupled). */
  private double gearRatio = 1.0;

  /** Combined shaft torque at design [Nm]. */
  private double designTorqueNm = 0.0;

  /** Shaft torsional stress at design [MPa]. */
  private double designShaftStressMPa = 0.0;

  /** Maximum expander nozzle throat area for choke estimation [m2]. */
  private double expanderNozzleThroatArea = 0.0;

  // ============================================================================
  // Shear pin (brytepinner) design parameters
  // ============================================================================

  /** Shear pin breaking torque [Nm]. */
  private double shearPinBreakingTorqueNm = 0.0;

  /** Number of shear pins. */
  private int numberOfShearPins = 2;

  /** Shear pin diameter [mm]. */
  private double shearPinDiameterMm = 10.0;

  /** Shear pin material (e.g. AISI 316, Inconel 718). */
  private String shearPinMaterial = "AISI 316";

  /** Shear pin material ultimate shear strength [MPa]. */
  private double shearPinShearStrengthMPa = 310.0;

  /** Shear pin radial position from shaft centre [mm]. */
  private double shearPinRadialPositionMm = 0.0;

  // ============================================================================
  // Thrust balance design parameters
  // ============================================================================

  /** Balance piston (balance drum) diameter [mm]. */
  private double balancePistonDiameterMm = 0.0;

  /** Thrust bearing rated capacity [N]. */
  private double thrustBearingCapacityN = 0.0;

  /** Design-point net axial thrust [N] (positive towards compressor). */
  private double designNetThrustN = 0.0;

  /** Design thrust direction sign (+1 or -1). */
  private double designThrustDirectionSign = 0.0;

  // ============================================================================
  // Seal gas system parameters (tetningsgass for dry gas seals)
  // ============================================================================

  /** Seal gas supply pressure [bara]. */
  private double sealGasSupplyPressureBara = 0.0;

  /** Required seal gas differential pressure above process [bar]. */
  private double sealGasRequiredDpBar = MIN_SEAL_GAS_DP_BAR;

  /** Seal gas consumption per seal [Nm3/hr]. */
  private double sealGasFlowRateNm3hr = 0.0;

  /** Seal gas type (e.g. "Treated process gas", "N2", "Instrument air"). */
  private String sealGasType = "Treated process gas";

  /** Primary seal arrangement (TANDEM, DOUBLE, SINGLE). */
  private String sealArrangement = "TANDEM";

  /** Seal gas filter differential pressure alarm [bar]. */
  private double sealGasFilterDpAlarmBar = 0.5;

  // ============================================================================
  // Oil seal system parameters (oljetetning for bearing and buffer oil)
  // ============================================================================

  /** Seal oil supply pressure [bara]. */
  private double sealOilSupplyPressureBara = 0.0;

  /** Seal oil differential pressure above process gas [bar]. */
  private double sealOilDpAboveProcessBar = 0.0;

  /** Seal oil flow rate [litres/min]. */
  private double sealOilFlowRateLpm = 0.0;

  /** Seal oil ISO viscosity grade (e.g. ISO VG 32, 46, 68). */
  private String sealOilGrade = "ISO VG 32";

  /** Seal oil supply temperature [deg C]. */
  private double sealOilTemperatureC = 40.0;

  /** Seal oil tank capacity [litres]. */
  private double sealOilTankCapacityL = 0.0;

  // ============================================================================
  // Anti-surge system parameters
  // ============================================================================

  /** Anti-surge valve Cv. */
  private double antiSurgeValveCv = 0.0;

  /** Anti-surge valve full stroke time [seconds]. */
  private double antiSurgeValveStrokeTimeS = 2.0;

  /** Surge control line margin above surge [fraction, 0.10 = 10 %]. */
  private double surgeControlLineMarginFrac = ANTI_SURGE_CONTROL_LINE_MARGIN;

  /** Anti-surge controller type (e.g. "PID", "CCC", "Triconex"). */
  private String antiSurgeControllerType = "PID";

  /** Minimum recycle flow at full anti-surge [m3/hr actual]. */
  private double minimumRecycleFlowM3hr = 0.0;

  /** Hot gas recycle (true) or cold recycle with aftercooler (false). */
  private boolean hotGasRecycle = true;

  // ============================================================================
  // Constructor
  // ============================================================================

  /**
   * Construct a mechanical design for a TurboExpanderCompressor.
   *
   * @param equipment the {@link TurboExpanderCompressor} process equipment
   */
  public TurboExpanderCompressorMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  // ============================================================================
  // Main design calculation
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    TurboExpanderCompressor tec = (TurboExpanderCompressor) getProcessEquipment();

    // Guard: equipment must have been run
    if (tec.getExpanderFeedStream() == null
        || tec.getExpanderFeedStream().getThermoSystem() == null) {
      return;
    }

    // --- 1. Extract operating conditions ---
    double expInP = tec.getExpanderFeedStream().getPressure("bara");
    double expOutP = tec.getExpanderOutletStream().getPressure("bara");
    double expInT = tec.getExpanderFeedStream().getTemperature("C");
    double expOutT = tec.getExpanderOutletStream().getTemperature("C");
    double expMassFlowKghr = tec.getExpanderFeedStream().getFlowRate("kg/hr");
    double expVolFlowM3hr = tec.getExpanderFeedStream().getFlowRate("m3/hr");

    double compInP = tec.getCompressorFeedStream().getPressure("bara");
    double compOutP = tec.getCompressorOutletStream().getPressure("bara");
    double compInT = tec.getCompressorFeedStream().getTemperature("C");
    double compOutT = tec.getCompressorOutletStream().getTemperature("C");
    double compMassFlowKghr = tec.getCompressorFeedStream().getFlowRate("kg/hr");
    double compVolFlowM3hr = tec.getCompressorFeedStream().getFlowRate("m3/hr");

    operatingSpeed = tec.getSpeed();
    gearRatio = tec.getGearRatio();
    expanderPowerKW = Math.abs(tec.getPowerExpander("kW"));
    compressorPowerKW = Math.abs(tec.getPowerCompressor("kW"));
    compressorPolytropicHead = tec.getCompressorPolytropicHead();

    // --- 2. Store design conditions ---
    expanderDesignInletPressure = expInP;
    expanderDesignOutletPressure = expOutP;
    expanderDesignInletTemperature = expInT;
    expanderDesignOutletTemperature = expOutT;

    compressorDesignSuctionPressure = compInP;
    compressorDesignDischargePressure = compOutP;
    compressorDesignSuctionTemperature = compInT;
    compressorDesignDischargeTemperature = compOutT;
    designCompressorVolumeFlowM3hr = compVolFlowM3hr;

    // --- 3. Casing design pressures/temperatures ---
    expanderCasingDesignPressure = expInP * DESIGN_PRESSURE_MARGIN;
    expanderCasingDesignTemperature = Math.max(expInT, expOutT) + DESIGN_TEMPERATURE_MARGIN;

    compressorCasingDesignPressure = compOutP * DESIGN_PRESSURE_MARGIN;
    compressorCasingDesignTemperature = Math.max(compInT, compOutT) + DESIGN_TEMPERATURE_MARGIN;

    // --- 4. Expander enthalpy drop ---
    if (expMassFlowKghr > 0) {
      expanderEnthalpyDropKJkg = (expanderPowerKW * 3600.0) / expMassFlowKghr;
    }

    // --- 5. Expander wheel sizing ---
    sizeExpanderWheel(expVolFlowM3hr, expanderEnthalpyDropKJkg);

    // --- 6. Compressor impeller sizing ---
    sizeCompressorImpeller(compVolFlowM3hr, compressorPolytropicHead);

    // --- 7. Shared shaft sizing ---
    sizeShaft(expanderPowerKW + compressorPowerKW, operatingSpeed);

    // --- 8. Rotor dynamics ---
    calculateRotorDynamics();

    // --- 9. Bearing & seal selection ---
    selectBearingsAndSeals();

    // --- 10. Casing wall thickness ---
    calculateExpanderCasing();
    calculateCompressorCasing();

    // --- 11. Nozzle throat area for choke estimation ---
    estimateExpanderNozzleThroatArea(expVolFlowM3hr, expanderEnthalpyDropKJkg);

    // --- 12. Surge/stonewall estimation for compressor ---
    estimateSurgeStonewall(compVolFlowM3hr);

    // --- 13. Weights & module ---
    calculateWeights();
    calculateModuleDimensions();
  }

  // ============================================================================
  // Sizing sub-methods
  // ============================================================================

  /**
   * Size the expander wheel per API 617 radial-inflow turbine practice.
   *
   * @param volFlowM3hr inlet volume flow [m3/hr]
   * @param enthalpyDropKJkg isentropic enthalpy drop [kJ/kg]
   */
  private void sizeExpanderWheel(double volFlowM3hr, double enthalpyDropKJkg) {
    if (enthalpyDropKJkg <= 0 || volFlowM3hr <= 0) {
      expanderWheelDiameter = 300.0;
      expanderTipSpeed = 300.0;
      return;
    }

    // Type selection
    if (volFlowM3hr > 100000) {
      expanderType = "AXIAL";
      expanderStages = (int) Math.ceil(enthalpyDropKJkg / 50.0);
    } else {
      expanderType = "RADIAL_INFLOW";
      expanderStages = 1;
    }

    // Spouting velocity: C0 = sqrt(2 * dh_is)
    double enthalpyDropJkg = enthalpyDropKJkg * 1000.0;
    double spoutingVelocity = Math.sqrt(2.0 * enthalpyDropJkg);

    // Optimal tip speed
    expanderTipSpeed = VELOCITY_RATIO * spoutingVelocity;
    expanderTipSpeed = Math.min(expanderTipSpeed, MAX_EXPANDER_TIP_SPEED);

    // Specific speed approach: target Ns ~ 70 for radial inflow
    double volFlowM3s = volFlowM3hr / 3600.0;
    double targetNs = 70.0;
    double enthalpyPerStage = enthalpyDropKJkg / expanderStages;
    double calcSpeed = targetNs * Math.pow(enthalpyPerStage, 0.75) / Math.sqrt(volFlowM3s * 60.0);
    calcSpeed = Math.max(3000, Math.min(100000, calcSpeed));

    // Use matched operating speed if available, otherwise use calculated
    double speedForSizing = operatingSpeed > 0 ? operatingSpeed : calcSpeed;

    // Wheel diameter from tip speed: D = U * 60 / (pi * N) [mm]
    expanderWheelDiameter = (expanderTipSpeed * 60.0) / (Math.PI * speedForSizing) * 1000.0;
    expanderWheelDiameter = Math.max(100.0, Math.min(1500.0, expanderWheelDiameter));

    // Recalculate actual tip speed for the chosen diameter and speed
    expanderTipSpeed = Math.PI * (expanderWheelDiameter / 1000.0) * speedForSizing / 60.0;
  }

  /**
   * Size the compressor impeller per API 617 centrifugal compressor practice.
   *
   * @param volFlowM3hr suction volume flow [m3/hr]
   * @param polytropicHead total polytropic head [kJ/kg]
   */
  private void sizeCompressorImpeller(double volFlowM3hr, double polytropicHead) {
    if (polytropicHead <= 0 || volFlowM3hr <= 0) {
      compressorImpellerDiameter = 300.0;
      compressorStages = 1;
      compressorHeadPerStage = 0.0;
      return;
    }

    // Number of stages
    compressorStages = (int) Math.ceil(polytropicHead / MAX_HEAD_PER_STAGE);
    compressorStages = Math.max(1, compressorStages);
    compressorHeadPerStage = polytropicHead / compressorStages;

    // Work coefficient: U^2 = H / psi
    double workCoefficient = 0.50;
    double headPerStageJkg = compressorHeadPerStage * 1000.0;
    compressorTipSpeed = Math.sqrt(headPerStageJkg / workCoefficient);
    compressorTipSpeed = Math.min(compressorTipSpeed, MAX_COMPRESSOR_TIP_SPEED);

    // Impeller diameter from tip speed and operating speed
    double speedForSizing = operatingSpeed > 0 ? operatingSpeed : 10000.0;
    compressorImpellerDiameter = (compressorTipSpeed * 60.0) / (Math.PI * speedForSizing) * 1000.0;
    compressorImpellerDiameter = Math.max(100.0, Math.min(1500.0, compressorImpellerDiameter));

    // Recalculate actual tip speed
    compressorTipSpeed = Math.PI * (compressorImpellerDiameter / 1000.0) * speedForSizing / 60.0;

    // Casing type
    if (compressorCasingDesignPressure > 100.0) {
      compressorCasingType = "BARREL";
    } else if (compressorCasingDesignPressure > 40.0) {
      compressorCasingType = "HORIZONTALLY_SPLIT";
    } else {
      compressorCasingType = "VERTICALLY_SPLIT";
    }
  }

  /**
   * Size the shared shaft for the combined torque from both ends.
   *
   * @param totalPowerKW total power transmitted through shaft [kW]
   * @param speedRPM shaft speed [rpm]
   */
  private void sizeShaft(double totalPowerKW, double speedRPM) {
    if (speedRPM <= 0 || totalPowerKW <= 0) {
      shaftDiameter = 50.0;
      return;
    }

    // Torque: T [Nm] = P [W] / omega [rad/s]
    designTorqueNm = (totalPowerKW * 1000.0 * 60.0) / (2.0 * Math.PI * speedRPM);

    // Shaft diameter: d = (16T / (pi * tau))^(1/3)
    double d = Math.pow((16.0 * designTorqueNm) / (Math.PI * ALLOWABLE_SHEAR_STRESS), 1.0 / 3.0);
    shaftDiameter = d * 1000.0; // m to mm

    // Safety factor for fatigue and dynamic loads
    shaftDiameter *= 1.5;

    // Round up to nearest 5 mm
    shaftDiameter = Math.max(30.0, Math.ceil(shaftDiameter / 5.0) * 5.0);

    // Actual stress at design
    double dMeters = shaftDiameter / 1000.0;
    double polarMoment = Math.PI * Math.pow(dMeters, 4) / 32.0;
    designShaftStressMPa = (designTorqueNm * dMeters / 2.0) / polarMoment / 1.0e6;
  }

  /**
   * Calculate coupled rotor dynamics across the full bearing span.
   */
  private void calculateRotorDynamics() {
    // Bearing span: expander wheel + gap + compressor impellers
    double expanderEnd = expanderWheelDiameter * 1.3; // mm
    double compressorEnd =
        compressorStages * compressorImpellerDiameter * 0.8 + compressorImpellerDiameter; // mm
    bearingSpan = expanderEnd + compressorEnd + 200.0; // 200 mm coupling/gap region

    // Maximum continuous speed
    maxContinuousSpeed = operatingSpeed * 1.05;
    tripSpeed = maxContinuousSpeed * 1.05;

    // First critical speed (Rayleigh-Ritz simplified)
    double dSh = shaftDiameter / 1000.0;
    double lSpan = bearingSpan / 1000.0;
    double massPerLength = STEEL_DENSITY * Math.PI * dSh * dSh / 4.0;

    if (lSpan > 0 && massPerLength > 0) {
      double stiffness = 48.0 * 2.1e11 * Math.PI * Math.pow(dSh, 4) / 64.0;
      firstCriticalSpeed = (946.0 / (2.0 * Math.PI))
          * Math.sqrt(stiffness / (massPerLength * Math.pow(lSpan, 3))) * 60.0;
    }

    // Second critical: roughly 4x first for uniform shaft
    secondCriticalSpeed = firstCriticalSpeed * 4.0;
  }

  /**
   * Select bearing and seal types based on speed and pressure.
   */
  private void selectBearingsAndSeals() {
    // Bearing type based on speed
    if (operatingSpeed > 50000) {
      bearingType = "Active Magnetic";
    } else if (operatingSpeed > 20000) {
      bearingType = "Tilting Pad";
    } else {
      bearingType = "Sleeve";
    }

    // Expander seal: labyrinth for low pressure, dry gas for high
    expanderSealType = expanderCasingDesignPressure > 50.0 ? "Dry Gas" : "Labyrinth";

    // Compressor seal: almost always dry gas
    compressorSealType = compressorCasingDesignPressure > 20.0 ? "Dry Gas" : "Labyrinth";
  }

  /**
   * Calculate expander casing wall thickness per ASME VIII Div.1.
   */
  private void calculateExpanderCasing() {
    double casingID = expanderWheelDiameter * 1.3; // mm
    double pressureMPa = expanderCasingDesignPressure * 0.1;
    double allowableStress = getTemperatureDeratedStress(expanderCasingDesignTemperature);
    double jointEfficiency = 0.85;

    expanderCasingWallThickness =
        (pressureMPa * casingID) / (2.0 * allowableStress * jointEfficiency - 0.6 * pressureMPa);
    expanderCasingWallThickness += 3.0; // corrosion allowance
    expanderCasingWallThickness = Math.max(10.0, expanderCasingWallThickness);
  }

  /**
   * Calculate compressor casing wall thickness per ASME VIII Div.1.
   */
  private void calculateCompressorCasing() {
    double casingID = compressorImpellerDiameter * 1.3; // mm
    double pressureMPa = compressorCasingDesignPressure * 0.1;
    double allowableStress = getTemperatureDeratedStress(compressorCasingDesignTemperature);
    double jointEfficiency = 0.85;

    compressorCasingWallThickness =
        (pressureMPa * casingID) / (2.0 * allowableStress * jointEfficiency - 0.6 * pressureMPa);
    compressorCasingWallThickness += 3.0; // corrosion allowance
    compressorCasingWallThickness = Math.max(10.0, compressorCasingWallThickness);
  }

  /**
   * Estimate the expander nozzle throat area for choke calculations.
   *
   * @param volFlowM3hr inlet volume flow [m3/hr]
   * @param enthalpyDropKJkg isentropic enthalpy drop [kJ/kg]
   */
  private void estimateExpanderNozzleThroatArea(double volFlowM3hr, double enthalpyDropKJkg) {
    if (volFlowM3hr <= 0 || enthalpyDropKJkg <= 0) {
      expanderNozzleThroatArea = 0.01; // default 100 cm2
      return;
    }
    // Nozzle velocity ~ fraction of spouting velocity
    double nozzleVelocity = 0.95 * Math.sqrt(2.0 * enthalpyDropKJkg * 1000.0);
    double volFlowM3s = volFlowM3hr / 3600.0;
    expanderNozzleThroatArea = volFlowM3s / Math.max(nozzleVelocity, 1.0);
    // Add 20% margin for design
    expanderNozzleThroatArea *= 1.2;
  }

  /**
   * Estimate compressor surge and stonewall flows.
   *
   * @param designVolFlowM3hr design actual volume flow [m3/hr]
   */
  private void estimateSurgeStonewall(double designVolFlowM3hr) {
    // Typical surge at 60-70% of design flow for centrifugal
    designSurgeFlowM3hr = designVolFlowM3hr * 0.65;
    // Stonewall at 120-130% of design flow
    designStonewallFlowM3hr = designVolFlowM3hr * 1.25;
  }

  /**
   * Calculate component and total weights.
   */
  private void calculateWeights() {
    // Expander wheel
    double wheelVol =
        Math.PI * Math.pow(expanderWheelDiameter / 2000.0, 2) * (expanderWheelDiameter / 3000.0);
    expanderWheelWeight = wheelVol * STEEL_DENSITY * 0.7;

    // Compressor impellers (all stages)
    double impVol = compressorStages * Math.PI * Math.pow(compressorImpellerDiameter / 2000.0, 2)
        * (compressorImpellerDiameter / 3000.0);
    compressorImpellerWeight = impVol * STEEL_DENSITY * 0.7;

    // Shaft
    double shaftLength = bearingSpan * 1.5;
    double shaftVol = Math.PI * Math.pow(shaftDiameter / 2000.0, 2) * (shaftLength / 1000.0);
    shaftWeight = shaftVol * STEEL_DENSITY;

    // Expander casing
    double expCasingOD = expanderWheelDiameter * 1.3 + 2.0 * expanderCasingWallThickness;
    double expCasingID = expanderWheelDiameter * 1.3;
    double expCasingLength = expanderWheelDiameter * 2.0;
    double expCasingVol =
        Math.PI / 4.0 * (Math.pow(expCasingOD / 1000.0, 2) - Math.pow(expCasingID / 1000.0, 2))
            * (expCasingLength / 1000.0);
    expanderCasingWeight = expCasingVol * STEEL_DENSITY * 1.5;

    // Compressor casing
    double compCasingOD = compressorImpellerDiameter * 1.3 + 2.0 * compressorCasingWallThickness;
    double compCasingID = compressorImpellerDiameter * 1.3;
    double compCasingLength = compressorStages * compressorImpellerDiameter * 1.2;
    double compCasingVol =
        Math.PI / 4.0 * (Math.pow(compCasingOD / 1000.0, 2) - Math.pow(compCasingID / 1000.0, 2))
            * (compCasingLength / 1000.0);
    compressorCasingWeight = compCasingVol * STEEL_DENSITY * 1.5;

    // Bundle weight
    double bundleWeight = expanderWheelWeight + compressorImpellerWeight + shaftWeight;
    bundleWeight *= 1.3; // add seals, bearings, balance piston

    // Auxiliary systems
    double lubeSystemWeight = 200.0 + (expanderPowerKW + compressorPowerKW) * 0.15;
    double sealSystemWeight = compressorSealType.equals("Dry Gas") ? 500.0 : 100.0;
    double baseplateWeight = (expanderCasingWeight + compressorCasingWeight + bundleWeight) * 0.2;
    double pipingWeight = (expanderCasingWeight + compressorCasingWeight) * 0.15;
    double electricalWeight = 50.0;
    double structuralWeight = (expanderCasingWeight + compressorCasingWeight + bundleWeight) * 0.1;

    // Set base-class weights
    weightVessel = expanderCasingWeight + compressorCasingWeight;
    weigthInternals = bundleWeight;
    weightPiping = pipingWeight + lubeSystemWeight + sealSystemWeight;
    weightElectroInstrument = electricalWeight;
    weightStructualSteel = structuralWeight + baseplateWeight;

    double totalWeight = weightVessel + weigthInternals + weightPiping + weightElectroInstrument
        + weightStructualSteel;
    setWeightTotal(totalWeight);
  }

  /**
   * Calculate module footprint and height.
   */
  private void calculateModuleDimensions() {
    double expanderLen = expanderWheelDiameter / 1000.0 * 2.5;
    double compressorLen = compressorStages * compressorImpellerDiameter / 1000.0 * 1.2 + 1.0;
    moduleLength = expanderLen + compressorLen + 3.0; // access space
    double maxCasingOD = Math.max(expanderWheelDiameter * 1.3 + 2.0 * expanderCasingWallThickness,
        compressorImpellerDiameter * 1.3 + 2.0 * compressorCasingWallThickness);
    moduleWidth = Math.max(2.5, maxCasingOD / 1000.0 * 2.0 + 1.5);
    moduleHeight = Math.max(2.0, maxCasingOD / 1000.0 + 1.5);
  }

  // ============================================================================
  // Off-design evaluation
  // ============================================================================

  /**
   * Evaluate whether the fixed mechanical design can handle the given operating conditions.
   *
   * <p>
   * All pressures in bara, temperatures in deg C, flows in kg/hr, molar masses in kg/kmol.
   * </p>
   *
   * @param expInletP expander inlet pressure [bara]
   * @param expOutletP expander outlet pressure [bara]
   * @param expInletT expander inlet temperature [deg C]
   * @param compInletP compressor suction pressure [bara]
   * @param compDischargeP compressor discharge pressure [bara]
   * @param compInletT compressor suction temperature [deg C]
   * @param expMassFlow expander mass flow [kg/hr]
   * @param compMassFlow compressor mass flow [kg/hr]
   * @param expMolarMass expander gas molar mass [kg/kmol]
   * @param compMolarMass compressor gas molar mass [kg/kmol]
   * @return evaluation result with margins and pass/fail
   */
  public DesignEvaluationResult evaluateDesignAtConditions(double expInletP, double expOutletP,
      double expInletT, double compInletP, double compDischargeP, double compInletT,
      double expMassFlow, double compMassFlow, double expMolarMass, double compMolarMass) {

    DesignEvaluationResult result = new DesignEvaluationResult();

    // --- 1. Estimate new enthalpy drop and tip speed for expander ---
    // Approximate isentropic enthalpy drop using ideal gas ratio
    double pressureRatioExp = expInletP / Math.max(expOutletP, 0.01);
    double gammaExp = 1.3; // typical for natural gas
    // dh_is ≈ Cp * T_in * [1 - (P2/P1)^((gamma-1)/gamma)]
    double CpExp = 1000.0 * gammaExp / (gammaExp - 1.0) * 8.314 / expMolarMass;
    double T_inK = expInletT + 273.15;
    double dhIsJkg =
        CpExp * T_inK * (1.0 - Math.pow(1.0 / pressureRatioExp, (gammaExp - 1.0) / gammaExp));

    // Speed matching: reuse the design speed (fixed hardware)
    double N = operatingSpeed;
    double U = Math.PI * (expanderWheelDiameter / 1000.0) * N / 60.0;
    double actualExpanderTipSpeed = U;

    // Tip speed margin for expander
    result.setExpanderTipSpeedMargin(
        (MAX_EXPANDER_TIP_SPEED - actualExpanderTipSpeed) / MAX_EXPANDER_TIP_SPEED);

    // --- 2. Compressor tip speed check ---
    double compU = Math.PI * (compressorImpellerDiameter / 1000.0) * N / 60.0;
    result
        .setCompressorTipSpeedMargin((MAX_COMPRESSOR_TIP_SPEED - compU) / MAX_COMPRESSOR_TIP_SPEED);

    // --- 3. Casing pressure checks ---
    result.setExpanderCasingPressureMargin(
        (expanderCasingDesignPressure - expInletP) / expanderCasingDesignPressure);
    result.setCompressorCasingPressureMargin(
        (compressorCasingDesignPressure - compDischargeP) / compressorCasingDesignPressure);

    // --- 4. Shaft stress check ---
    // Estimate off-design torque. When rated expander power and design conditions are known,
    // scale the design torque proportionally to the mass flow ratio. This avoids the inaccuracy
    // of ideal-gas Cp at high-pressure cryogenic conditions.
    double offDesignTorque = 0.0;
    if (designTorqueNm > 0 && expanderPowerKW > 0 && N > 0) {
      // Use the known design mass flow (derive from power and enthalpy drop)
      double designMassFlowKghr = 0.0;
      if (expanderEnthalpyDropKJkg > 0) {
        // P = mdot * dh * eta => mdot = P / (dh * eta)
        designMassFlowKghr = (expanderPowerKW * 3600.0) / expanderEnthalpyDropKJkg;
      }
      if (designMassFlowKghr > 0) {
        // Scale design torque by mass flow ratio
        double flowRatio = expMassFlow / designMassFlowKghr;
        offDesignTorque = designTorqueNm * flowRatio;
      } else {
        // Fallback: scale by pressure-ratio change
        offDesignTorque = designTorqueNm;
      }
    } else if (N > 0) {
      // No design baseline — fall back to ideal-gas estimate
      double offDesignPowerW = (expMassFlow / 3600.0) * Math.abs(dhIsJkg) * 0.85;
      offDesignTorque = (offDesignPowerW * 60.0) / (2.0 * Math.PI * N);
    }
    double dSh = shaftDiameter / 1000.0;
    double polarMoment = Math.PI * Math.pow(dSh, 4) / 32.0;
    double offDesignStressMPa = 0.0;
    if (polarMoment > 0) {
      offDesignStressMPa = (offDesignTorque * dSh / 2.0) / polarMoment / 1.0e6;
    }
    double allowableShearMPa = ALLOWABLE_SHEAR_STRESS / 1.0e6;
    result.setShaftStressMargin((allowableShearMPa - offDesignStressMPa) / allowableShearMPa);

    // --- 5. Critical speed separation ---
    if (firstCriticalSpeed > 0) {
      double separation = Math.abs(N - firstCriticalSpeed) / firstCriticalSpeed;
      result.setCriticalSpeedSeparationMargin(
          (separation - MIN_CRITICAL_SPEED_SEPARATION) / MIN_CRITICAL_SPEED_SEPARATION);
    }

    // --- 6. Compressor surge margin ---
    // Estimate actual volume flow at off-design
    // rho = P[Pa] * M[kg/mol] / (R * T) = (P_bara * 1e5) * (M_kgkmol / 1e3) / (R * T)
    // = P_bara * 100 * M_kgkmol / (R * T)
    double compDensity = compInletP * 100.0 * compMolarMass / (8.314 * (compInletT + 273.15));
    double compVolFlowM3hr = 0.0;
    if (compDensity > 0) {
      compVolFlowM3hr = compMassFlow / compDensity;
    }
    if (designSurgeFlowM3hr > 0) {
      result
          .setCompressorSurgeMargin((compVolFlowM3hr - designSurgeFlowM3hr) / designSurgeFlowM3hr);
    }

    // --- 7. Expander choke margin ---
    if (expanderNozzleThroatArea > 0 && dhIsJkg > 0) {
      double nozzleVelocity = 0.95 * Math.sqrt(2.0 * Math.abs(dhIsJkg));
      double expDensity = expInletP * 100.0 * expMolarMass / (8.314 * T_inK);
      double requiredArea = 0.0;
      if (nozzleVelocity > 0 && expDensity > 0) {
        requiredArea = (expMassFlow / 3600.0) / (expDensity * nozzleVelocity);
      }
      result.setExpanderChokeMargin(
          (expanderNozzleThroatArea - requiredArea) / expanderNozzleThroatArea);
    }

    // --- 8. Discharge temperature margin ---
    // Rough estimate of compressor discharge temperature
    double gammaComp = 1.3;
    double prComp = compDischargeP / Math.max(compInletP, 0.01);
    double etaPoly = 0.80;
    double T_suctionK = compInletT + 273.15;
    double T_dischargeK = T_suctionK * Math.pow(prComp, (gammaComp - 1.0) / (gammaComp * etaPoly));
    double T_dischargeC = T_dischargeK - 273.15;
    result.setDischargeTemperatureMargin(
        (MAX_DISCHARGE_TEMP_C - T_dischargeC) / MAX_DISCHARGE_TEMP_C);

    // --- 9. Thrust bearing load margin (enhanced with thrust balance details) ---
    // Simplified: thrust ~ pressure difference * area * correction factor
    double expanderThrust = (expInletP - expOutletP) * 1.0e5 * Math.PI
        * Math.pow(expanderWheelDiameter / 2000.0, 2) * 0.3;
    double compressorThrust = (compDischargeP - compInletP) * 1.0e5 * Math.PI
        * Math.pow(compressorImpellerDiameter / 2000.0, 2) * 0.3;

    // Balance piston contribution (if present)
    double balancePistonThrust = 0.0;
    if (balancePistonDiameterMm > 0) {
      // Balance piston typically counteracts the net thrust from compressor side
      double balanceDp = (compDischargeP - compInletP) * 0.7; // typical recovery
      balancePistonThrust =
          balanceDp * 1.0e5 * Math.PI * Math.pow(balancePistonDiameterMm / 2000.0, 2) * 0.25;
    }

    // Net thrust: positive = towards compressor end
    double netThrust = expanderThrust - compressorThrust + balancePistonThrust;

    // Detect thrust reversal compared to design
    boolean thrustReversal = false;
    if (designThrustDirectionSign != 0.0) {
      double currentSign = Math.signum(netThrust);
      thrustReversal = (currentSign != 0.0 && currentSign != designThrustDirectionSign);
    }

    double absNetThrust = Math.abs(netThrust);
    double thrustCapacity = thrustBearingCapacityN;

    // Fall back to 2x design net thrust if no vendor capacity specified
    if (thrustCapacity <= 0) {
      double designExpThrust = (expanderDesignInletPressure - expanderDesignOutletPressure) * 1.0e5
          * Math.PI * Math.pow(expanderWheelDiameter / 2000.0, 2) * 0.3;
      double designCompThrust =
          (compressorDesignDischargePressure - compressorDesignSuctionPressure) * 1.0e5 * Math.PI
              * Math.pow(compressorImpellerDiameter / 2000.0, 2) * 0.3;
      double designBalPiston = 0.0;
      if (balancePistonDiameterMm > 0) {
        double designBalDp =
            (compressorDesignDischargePressure - compressorDesignSuctionPressure) * 0.7;
        designBalPiston =
            designBalDp * 1.0e5 * Math.PI * Math.pow(balancePistonDiameterMm / 2000.0, 2) * 0.25;
      }
      double designNetThrust = Math.abs(designExpThrust - designCompThrust + designBalPiston);
      thrustCapacity = designNetThrust * 2.0;
    }

    if (thrustCapacity > 0) {
      result.setThrustBearingLoadMargin((thrustCapacity - absNetThrust) / thrustCapacity);
    }
    result.setThrustBalanceDetails(expanderThrust, compressorThrust, netThrust, thrustReversal);

    // --- 10. Shear pin torque margin ---
    if (shearPinBreakingTorqueNm > 0) {
      // Operating torque from step 4 (offDesignTorque already calculated above)
      double allowableTorque = shearPinBreakingTorqueNm / SHEAR_PIN_SAFETY_FACTOR;
      result.setShearPinTorqueMargin((allowableTorque - offDesignTorque) / allowableTorque);
    } else if (numberOfShearPins > 0 && shearPinDiameterMm > 0 && shearPinRadialPositionMm > 0) {
      // Calculate breaking torque from pin geometry and material
      double pinArea = Math.PI * Math.pow(shearPinDiameterMm / 2.0, 2); // mm2
      double shearForcePerPin = pinArea * shearPinShearStrengthMPa; // N
      double totalBreakingTorque =
          numberOfShearPins * shearForcePerPin * (shearPinRadialPositionMm / 1000.0); // Nm
      double allowableTorque = totalBreakingTorque / SHEAR_PIN_SAFETY_FACTOR;
      result.setShearPinTorqueMargin((allowableTorque - offDesignTorque) / allowableTorque);
    }

    // --- 11. Seal gas differential pressure ---
    if (sealGasSupplyPressureBara > 0) {
      // Process pressure at compressor discharge (highest seal pressure)
      double maxProcessP = Math.max(expInletP, compDischargeP);
      double availableDp = sealGasSupplyPressureBara - maxProcessP;
      result.setSealGasDpMargin((availableDp - sealGasRequiredDpBar) / sealGasRequiredDpBar);
    }

    // --- 12. Anti-surge margin ---
    // Surge control line = surge flow * (1 + margin)
    double surgeControlLineFlow = designSurgeFlowM3hr * (1.0 + surgeControlLineMarginFrac);
    if (surgeControlLineFlow > 0 && compVolFlowM3hr > 0) {
      result.setAntiSurgeMargin((compVolFlowM3hr - surgeControlLineFlow) / surgeControlLineFlow);
    }

    return result;
  }

  /**
   * Evaluate the design using full thermodynamic fluid objects for more accurate results.
   *
   * <p>
   * Performs isentropic flash calculations to determine actual enthalpy drops rather than relying
   * on ideal-gas approximations.
   * </p>
   *
   * @param expanderFluid expander inlet fluid (will be cloned, not modified)
   * @param expOutletP expander outlet pressure [bara]
   * @param compressorFluid compressor inlet fluid (will be cloned, not modified)
   * @param compDischargeP compressor discharge pressure [bara]
   * @return evaluation result with margins and pass/fail
   */
  public DesignEvaluationResult evaluateDesignWithFluid(SystemInterface expanderFluid,
      double expOutletP, SystemInterface compressorFluid, double compDischargeP) {

    // Clone and initialise expander fluid
    SystemInterface expFluid = expanderFluid.clone();
    expFluid.initThermoProperties();
    double expInletP = expFluid.getPressure("bara");
    double expInletT = expFluid.getTemperature("C");
    double expMassFlow = expFluid.getFlowRate("kg/hr");
    double expMolarMass = expFluid.getMolarMass("kg/mol") * 1000.0;

    // Clone and initialise compressor fluid
    SystemInterface compFluid = compressorFluid.clone();
    compFluid.initThermoProperties();
    double compInletP = compFluid.getPressure("bara");
    double compInletT = compFluid.getTemperature("C");
    double compMassFlow = compFluid.getFlowRate("kg/hr");
    double compMolarMass = compFluid.getMolarMass("kg/mol") * 1000.0;

    // Run scalar evaluation first (handles tip speed, casing, thrust, etc.)
    DesignEvaluationResult result =
        evaluateDesignAtConditions(expInletP, expOutletP, expInletT, compInletP, compDischargeP,
            compInletT, expMassFlow, compMassFlow, expMolarMass, compMolarMass);

    // Override shaft stress with EOS-accurate enthalpy drop from isentropic flash
    try {
      double h1 = expFluid.getEnthalpy("J/kg");
      double s1 = expFluid.getEntropy("J/kgK");
      SystemInterface expFlashClone = expFluid.clone();
      expFlashClone.setPressure(expOutletP, "bara");
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(expFlashClone);
      ops.PSflash(s1, "J/kgK");
      double h2s = expFlashClone.getEnthalpy("J/kg");
      double dhIsActual = Math.abs(h1 - h2s);

      if (dhIsActual > 0 && operatingSpeed > 0) {
        double actualPowerW = (expMassFlow / 3600.0) * dhIsActual * 0.85;
        double actualTorque = (actualPowerW * 60.0) / (2.0 * Math.PI * operatingSpeed);
        double dSh = shaftDiameter / 1000.0;
        double polarMom = Math.PI * Math.pow(dSh, 4) / 32.0;
        if (polarMom > 0) {
          double stressMPa = (actualTorque * dSh / 2.0) / polarMom / 1.0e6;
          double allowMPa = ALLOWABLE_SHEAR_STRESS / 1.0e6;
          result.setShaftStressMargin((allowMPa - stressMPa) / allowMPa);
        }
      }
    } catch (Exception ex) {
      // If flash fails, keep the scalar-based shaft stress margin
    }

    return result;
  }

  // ============================================================================
  // Utility methods
  // ============================================================================

  /**
   * Get allowable stress with temperature derating.
   *
   * @param temperatureC design temperature [deg C]
   * @return allowable stress [MPa]
   */
  private double getTemperatureDeratedStress(double temperatureC) {
    double baseStress = 137.9; // MPa, SA-516-70
    if (temperatureC > 200) {
      return baseStress * 0.85;
    } else if (temperatureC < -50) {
      return baseStress * 0.90;
    }
    return baseStress;
  }

  // ============================================================================
  // JSON reporting
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    JsonObject json = new JsonObject();

    json.addProperty("equipmentName", getProcessEquipment().getName());
    json.addProperty("designClass", "TurboExpanderCompressorMechanicalDesign");

    // Expander side
    JsonObject expander = new JsonObject();
    expander.addProperty("type", expanderType);
    expander.addProperty("stages", expanderStages);
    expander.addProperty("wheelDiameter_mm", expanderWheelDiameter);
    expander.addProperty("tipSpeed_ms", expanderTipSpeed);
    expander.addProperty("power_kW", expanderPowerKW);
    expander.addProperty("enthalpyDrop_kJkg", expanderEnthalpyDropKJkg);
    expander.addProperty("designInletPressure_bara", expanderDesignInletPressure);
    expander.addProperty("designOutletPressure_bara", expanderDesignOutletPressure);
    expander.addProperty("designInletTemperature_C", expanderDesignInletTemperature);
    expander.addProperty("designOutletTemperature_C", expanderDesignOutletTemperature);
    expander.addProperty("casingDesignPressure_bara", expanderCasingDesignPressure);
    expander.addProperty("casingDesignTemperature_C", expanderCasingDesignTemperature);
    expander.addProperty("casingWallThickness_mm", expanderCasingWallThickness);
    expander.addProperty("sealType", expanderSealType);
    expander.addProperty("wheelWeight_kg", expanderWheelWeight);
    expander.addProperty("casingWeight_kg", expanderCasingWeight);
    json.add("expander", expander);

    // Compressor side
    JsonObject compressor = new JsonObject();
    compressor.addProperty("stages", compressorStages);
    compressor.addProperty("impellerDiameter_mm", compressorImpellerDiameter);
    compressor.addProperty("tipSpeed_ms", compressorTipSpeed);
    compressor.addProperty("polytropicHead_kJkg", compressorPolytropicHead);
    compressor.addProperty("headPerStage_kJkg", compressorHeadPerStage);
    compressor.addProperty("power_kW", compressorPowerKW);
    compressor.addProperty("casingType", compressorCasingType);
    compressor.addProperty("designSuctionPressure_bara", compressorDesignSuctionPressure);
    compressor.addProperty("designDischargePressure_bara", compressorDesignDischargePressure);
    compressor.addProperty("designSuctionTemperature_C", compressorDesignSuctionTemperature);
    compressor.addProperty("designDischargeTemperature_C", compressorDesignDischargeTemperature);
    compressor.addProperty("casingDesignPressure_bara", compressorCasingDesignPressure);
    compressor.addProperty("casingDesignTemperature_C", compressorCasingDesignTemperature);
    compressor.addProperty("casingWallThickness_mm", compressorCasingWallThickness);
    compressor.addProperty("sealType", compressorSealType);
    compressor.addProperty("surgeFlow_m3hr", designSurgeFlowM3hr);
    compressor.addProperty("stonewallFlow_m3hr", designStonewallFlowM3hr);
    compressor.addProperty("impellerWeight_kg", compressorImpellerWeight);
    compressor.addProperty("casingWeight_kg", compressorCasingWeight);
    json.add("compressor", compressor);

    // Shared shaft
    JsonObject shaft = new JsonObject();
    shaft.addProperty("diameter_mm", shaftDiameter);
    shaft.addProperty("weight_kg", shaftWeight);
    shaft.addProperty("designTorque_Nm", designTorqueNm);
    shaft.addProperty("designShaftStress_MPa", designShaftStressMPa);
    shaft.addProperty("operatingSpeed_rpm", operatingSpeed);
    shaft.addProperty("maxContinuousSpeed_rpm", maxContinuousSpeed);
    shaft.addProperty("tripSpeed_rpm", tripSpeed);
    shaft.addProperty("firstCriticalSpeed_rpm", firstCriticalSpeed);
    shaft.addProperty("secondCriticalSpeed_rpm", secondCriticalSpeed);
    shaft.addProperty("bearingSpan_mm", bearingSpan);
    shaft.addProperty("bearingType", bearingType);
    shaft.addProperty("gearRatio", gearRatio);
    json.add("shaft", shaft);

    // Shear pin system
    JsonObject shearPin = new JsonObject();
    shearPin.addProperty("breakingTorque_Nm", shearPinBreakingTorqueNm);
    shearPin.addProperty("numberOfPins", numberOfShearPins);
    shearPin.addProperty("pinDiameter_mm", shearPinDiameterMm);
    shearPin.addProperty("pinMaterial", shearPinMaterial);
    shearPin.addProperty("shearStrength_MPa", shearPinShearStrengthMPa);
    shearPin.addProperty("radialPosition_mm", shearPinRadialPositionMm);
    shearPin.addProperty("safetyFactor", SHEAR_PIN_SAFETY_FACTOR);
    json.add("shearPins", shearPin);

    // Thrust balance
    JsonObject thrustBalance = new JsonObject();
    thrustBalance.addProperty("balancePistonDiameter_mm", balancePistonDiameterMm);
    thrustBalance.addProperty("thrustBearingCapacity_N", thrustBearingCapacityN);
    thrustBalance.addProperty("designNetThrust_N", designNetThrustN);
    json.add("thrustBalance", thrustBalance);

    // Seal gas system
    JsonObject sealGas = new JsonObject();
    sealGas.addProperty("supplyPressure_bara", sealGasSupplyPressureBara);
    sealGas.addProperty("requiredDp_bar", sealGasRequiredDpBar);
    sealGas.addProperty("flowRate_Nm3hr", sealGasFlowRateNm3hr);
    sealGas.addProperty("gasType", sealGasType);
    sealGas.addProperty("arrangement", sealArrangement);
    sealGas.addProperty("filterDpAlarm_bar", sealGasFilterDpAlarmBar);
    json.add("sealGasSystem", sealGas);

    // Oil seal system
    JsonObject oilSeal = new JsonObject();
    oilSeal.addProperty("supplyPressure_bara", sealOilSupplyPressureBara);
    oilSeal.addProperty("dpAboveProcess_bar", sealOilDpAboveProcessBar);
    oilSeal.addProperty("flowRate_lpm", sealOilFlowRateLpm);
    oilSeal.addProperty("oilGrade", sealOilGrade);
    oilSeal.addProperty("temperature_C", sealOilTemperatureC);
    oilSeal.addProperty("tankCapacity_L", sealOilTankCapacityL);
    json.add("oilSealSystem", oilSeal);

    // Anti-surge system
    JsonObject antiSurge = new JsonObject();
    antiSurge.addProperty("valveCv", antiSurgeValveCv);
    antiSurge.addProperty("valveStrokeTime_s", antiSurgeValveStrokeTimeS);
    antiSurge.addProperty("surgeControlLineMargin_pct", surgeControlLineMarginFrac * 100.0);
    antiSurge.addProperty("controllerType", antiSurgeControllerType);
    antiSurge.addProperty("minimumRecycleFlow_m3hr", minimumRecycleFlowM3hr);
    antiSurge.addProperty("hotGasRecycle", hotGasRecycle);
    antiSurge.addProperty("surgeLineFlow_m3hr", designSurgeFlowM3hr);
    antiSurge.addProperty("surgeControlLineFlow_m3hr",
        designSurgeFlowM3hr * (1.0 + surgeControlLineMarginFrac));
    json.add("antiSurgeSystem", antiSurge);

    // Module
    JsonObject module = new JsonObject();
    module.addProperty("length_m", moduleLength);
    module.addProperty("width_m", moduleWidth);
    module.addProperty("height_m", moduleHeight);
    module.addProperty("totalWeight_kg", getWeightTotal());
    json.add("module", module);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(json);
  }

  // ============================================================================
  // Display
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResults() {
    JFrame dialog = new JFrame(
        "TurboExpanderCompressor Mechanical Design - " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[][] data = {{"=== EXPANDER SIDE ===", "", ""}, {"Expander Type", expanderType, ""},
        {"Stages", String.valueOf(expanderStages), ""},
        {"Wheel Diameter", String.format("%.1f", expanderWheelDiameter), "mm"},
        {"Tip Speed", String.format("%.1f", expanderTipSpeed), "m/s"},
        {"Power", String.format("%.1f", expanderPowerKW), "kW"},
        {"Casing Design Pressure", String.format("%.1f", expanderCasingDesignPressure), "bara"},
        {"Seal Type", expanderSealType, ""}, {"=== COMPRESSOR SIDE ===", "", ""},
        {"Stages", String.valueOf(compressorStages), ""},
        {"Impeller Diameter", String.format("%.1f", compressorImpellerDiameter), "mm"},
        {"Tip Speed", String.format("%.1f", compressorTipSpeed), "m/s"},
        {"Casing Type", compressorCasingType, ""},
        {"Power", String.format("%.1f", compressorPowerKW), "kW"},
        {"Casing Design Pressure", String.format("%.1f", compressorCasingDesignPressure), "bara"},
        {"Seal Type", compressorSealType, ""},
        {"Surge Flow", String.format("%.1f", designSurgeFlowM3hr), "m3/hr"},
        {"=== SHARED SHAFT ===", "", ""},
        {"Shaft Diameter", String.format("%.1f", shaftDiameter), "mm"},
        {"Operating Speed", String.format("%.0f", operatingSpeed), "rpm"},
        {"1st Critical Speed", String.format("%.0f", firstCriticalSpeed), "rpm"},
        {"Trip Speed", String.format("%.0f", tripSpeed), "rpm"}, {"Bearing Type", bearingType, ""},
        {"Bearing Span", String.format("%.0f", bearingSpan), "mm"}, {"=== SHEAR PINS ===", "", ""},
        {"Number of Pins", String.valueOf(numberOfShearPins), ""},
        {"Pin Diameter", String.format("%.1f", shearPinDiameterMm), "mm"},
        {"Pin Material", shearPinMaterial, ""},
        {"Breaking Torque", String.format("%.0f", shearPinBreakingTorqueNm), "Nm"},
        {"=== THRUST BALANCE ===", "", ""},
        {"Balance Piston Dia", String.format("%.1f", balancePistonDiameterMm), "mm"},
        {"Bearing Capacity", String.format("%.0f", thrustBearingCapacityN), "N"},
        {"=== SEAL GAS SYSTEM ===", "", ""},
        {"Supply Pressure", String.format("%.1f", sealGasSupplyPressureBara), "bara"},
        {"Required DP", String.format("%.1f", sealGasRequiredDpBar), "bar"},
        {"Gas Type", sealGasType, ""}, {"Arrangement", sealArrangement, ""},
        {"=== OIL SEAL SYSTEM ===", "", ""},
        {"Supply Pressure", String.format("%.1f", sealOilSupplyPressureBara), "bara"},
        {"DP Above Process", String.format("%.1f", sealOilDpAboveProcessBar), "bar"},
        {"Oil Grade", sealOilGrade, ""}, {"=== ANTI-SURGE ===", "", ""},
        {"Valve Cv", String.format("%.1f", antiSurgeValveCv), ""},
        {"Stroke Time", String.format("%.1f", antiSurgeValveStrokeTimeS), "s"},
        {"Control Line Margin", String.format("%.0f", surgeControlLineMarginFrac * 100), "%"},
        {"Controller Type", antiSurgeControllerType, ""}, {"=== TOTALS ===", "", ""},
        {"Total Weight", String.format("%.0f", getWeightTotal()), "kg"}, {"Module L x W x H",
            String.format("%.1f x %.1f x %.1f", moduleLength, moduleWidth, moduleHeight), "m"}};

    String[] columnNames = {"Parameter", "Value", "Unit"};
    JTable table = new JTable(data, columnNames);
    JScrollPane scrollPane = new JScrollPane(table);
    dialogContentPane.add(scrollPane, BorderLayout.CENTER);

    dialog.setSize(500, 600);
    dialog.setVisible(true);
  }

  // ============================================================================
  // Getters
  // ============================================================================

  /**
   * Get expander wheel diameter.
   *
   * @return diameter in mm
   */
  public double getExpanderWheelDiameter() {
    return expanderWheelDiameter;
  }

  /**
   * Get expander tip speed.
   *
   * @return tip speed in m/s
   */
  public double getExpanderTipSpeed() {
    return expanderTipSpeed;
  }

  /**
   * Get expander type.
   *
   * @return expander type string
   */
  public String getExpanderType() {
    return expanderType;
  }

  /**
   * Get number of expander stages.
   *
   * @return number of stages
   */
  public int getExpanderStages() {
    return expanderStages;
  }

  /**
   * Get expander design inlet pressure.
   *
   * @return pressure in bara
   */
  public double getExpanderDesignInletPressure() {
    return expanderDesignInletPressure;
  }

  /**
   * Get expander design outlet pressure.
   *
   * @return pressure in bara
   */
  public double getExpanderDesignOutletPressure() {
    return expanderDesignOutletPressure;
  }

  /**
   * Get expander casing design pressure.
   *
   * @return pressure in bara
   */
  public double getExpanderCasingDesignPressure() {
    return expanderCasingDesignPressure;
  }

  /**
   * Get expander casing design temperature.
   *
   * @return temperature in deg C
   */
  public double getExpanderCasingDesignTemperature() {
    return expanderCasingDesignTemperature;
  }

  /**
   * Get expander recovered power.
   *
   * @return power in kW
   */
  public double getExpanderPowerKW() {
    return expanderPowerKW;
  }

  /**
   * Get expander isentropic enthalpy drop.
   *
   * @return enthalpy drop in kJ/kg
   */
  public double getExpanderEnthalpyDropKJkg() {
    return expanderEnthalpyDropKJkg;
  }

  /**
   * Get compressor impeller diameter.
   *
   * @return diameter in mm
   */
  public double getCompressorImpellerDiameter() {
    return compressorImpellerDiameter;
  }

  /**
   * Get compressor tip speed.
   *
   * @return tip speed in m/s
   */
  public double getCompressorTipSpeed() {
    return compressorTipSpeed;
  }

  /**
   * Get number of compressor stages.
   *
   * @return number of stages
   */
  public int getCompressorStages() {
    return compressorStages;
  }

  /**
   * Get compressor polytropic head.
   *
   * @return head in kJ/kg
   */
  public double getCompressorPolytropicHead() {
    return compressorPolytropicHead;
  }

  /**
   * Get compressor head per stage.
   *
   * @return head per stage in kJ/kg
   */
  public double getCompressorHeadPerStage() {
    return compressorHeadPerStage;
  }

  /**
   * Get compressor casing type.
   *
   * @return casing type string
   */
  public String getCompressorCasingType() {
    return compressorCasingType;
  }

  /**
   * Get compressor design suction pressure.
   *
   * @return pressure in bara
   */
  public double getCompressorDesignSuctionPressure() {
    return compressorDesignSuctionPressure;
  }

  /**
   * Get compressor design discharge pressure.
   *
   * @return pressure in bara
   */
  public double getCompressorDesignDischargePressure() {
    return compressorDesignDischargePressure;
  }

  /**
   * Get compressor casing design pressure.
   *
   * @return pressure in bara
   */
  public double getCompressorCasingDesignPressure() {
    return compressorCasingDesignPressure;
  }

  /**
   * Get compressor casing design temperature.
   *
   * @return temperature in deg C
   */
  public double getCompressorCasingDesignTemperature() {
    return compressorCasingDesignTemperature;
  }

  /**
   * Get compressor power.
   *
   * @return power in kW
   */
  public double getCompressorPowerKW() {
    return compressorPowerKW;
  }

  /**
   * Get shaft diameter.
   *
   * @return diameter in mm
   */
  public double getShaftDiameter() {
    return shaftDiameter;
  }

  /**
   * Get shaft weight.
   *
   * @return weight in kg
   */
  public double getShaftWeight() {
    return shaftWeight;
  }

  /**
   * Get operating speed.
   *
   * @return speed in rpm
   */
  public double getOperatingSpeed() {
    return operatingSpeed;
  }

  /**
   * Get maximum continuous speed.
   *
   * @return speed in rpm
   */
  public double getMaxContinuousSpeed() {
    return maxContinuousSpeed;
  }

  /**
   * Get trip speed.
   *
   * @return speed in rpm
   */
  public double getTripSpeed() {
    return tripSpeed;
  }

  /**
   * Get first lateral critical speed.
   *
   * @return speed in rpm
   */
  public double getFirstCriticalSpeed() {
    return firstCriticalSpeed;
  }

  /**
   * Get second lateral critical speed.
   *
   * @return speed in rpm
   */
  public double getSecondCriticalSpeed() {
    return secondCriticalSpeed;
  }

  /**
   * Get bearing span.
   *
   * @return bearing span in mm
   */
  public double getBearingSpan() {
    return bearingSpan;
  }

  /**
   * Get bearing type.
   *
   * @return bearing type description
   */
  public String getBearingType() {
    return bearingType;
  }

  /**
   * Get expander seal type.
   *
   * @return seal type description
   */
  public String getExpanderSealType() {
    return expanderSealType;
  }

  /**
   * Get compressor seal type.
   *
   * @return seal type description
   */
  public String getCompressorSealType() {
    return compressorSealType;
  }

  /**
   * Get gear ratio.
   *
   * @return gear ratio
   */
  public double getGearRatio() {
    return gearRatio;
  }

  /**
   * Get design shaft torque.
   *
   * @return torque in Nm
   */
  public double getDesignTorqueNm() {
    return designTorqueNm;
  }

  /**
   * Get design shaft stress.
   *
   * @return stress in MPa
   */
  public double getDesignShaftStressMPa() {
    return designShaftStressMPa;
  }

  /**
   * Get design surge flow.
   *
   * @return flow in m3/hr
   */
  public double getDesignSurgeFlowM3hr() {
    return designSurgeFlowM3hr;
  }

  /**
   * Get design stonewall flow.
   *
   * @return flow in m3/hr
   */
  public double getDesignStonewallFlowM3hr() {
    return designStonewallFlowM3hr;
  }

  /**
   * Get expander nozzle throat area.
   *
   * @return area in m2
   */
  public double getExpanderNozzleThroatArea() {
    return expanderNozzleThroatArea;
  }

  /**
   * Get expander wheel weight.
   *
   * @return weight in kg
   */
  public double getExpanderWheelWeight() {
    return expanderWheelWeight;
  }

  /**
   * Get expander casing weight.
   *
   * @return weight in kg
   */
  public double getExpanderCasingWeight() {
    return expanderCasingWeight;
  }

  /**
   * Get compressor impeller weight.
   *
   * @return weight in kg
   */
  public double getCompressorImpellerWeight() {
    return compressorImpellerWeight;
  }

  /**
   * Get compressor casing weight.
   *
   * @return weight in kg
   */
  public double getCompressorCasingWeight() {
    return compressorCasingWeight;
  }

  // ============================================================================
  // Setters — for loading vendor datasheet values
  // ============================================================================

  /**
   * Set expander wheel diameter from vendor datasheet.
   *
   * @param diameter wheel diameter in mm
   */
  public void setExpanderWheelDiameter(double diameter) {
    this.expanderWheelDiameter = diameter;
  }

  /**
   * Set compressor impeller diameter from vendor datasheet.
   *
   * @param diameter impeller diameter in mm
   */
  public void setCompressorImpellerDiameter(double diameter) {
    this.compressorImpellerDiameter = diameter;
  }

  /**
   * Set number of compressor stages.
   *
   * @param stages number of stages
   */
  public void setCompressorStages(int stages) {
    this.compressorStages = stages;
  }

  /**
   * Set number of expander stages.
   *
   * @param stages number of stages
   */
  public void setExpanderStages(int stages) {
    this.expanderStages = stages;
  }

  /**
   * Set expander type.
   *
   * @param type expander type string (RADIAL_INFLOW, AXIAL, MIXED_FLOW)
   */
  public void setExpanderType(String type) {
    this.expanderType = type;
  }

  /**
   * Set operating speed from vendor datasheet.
   *
   * @param speed speed in rpm
   */
  public void setOperatingSpeed(double speed) {
    this.operatingSpeed = speed;
  }

  /**
   * Set maximum continuous speed from vendor datasheet.
   *
   * @param speed speed in rpm
   */
  public void setMaxContinuousSpeed(double speed) {
    this.maxContinuousSpeed = speed;
  }

  /**
   * Set trip speed from vendor datasheet.
   *
   * @param speed speed in rpm
   */
  public void setTripSpeed(double speed) {
    this.tripSpeed = speed;
  }

  /**
   * Set first lateral critical speed from vendor test data.
   *
   * @param speed critical speed in rpm
   */
  public void setFirstCriticalSpeed(double speed) {
    this.firstCriticalSpeed = speed;
  }

  /**
   * Set second lateral critical speed from vendor test data.
   *
   * @param speed critical speed in rpm
   */
  public void setSecondCriticalSpeed(double speed) {
    this.secondCriticalSpeed = speed;
  }

  /**
   * Set shaft diameter from vendor datasheet.
   *
   * @param diameter diameter in mm
   */
  public void setShaftDiameter(double diameter) {
    this.shaftDiameter = diameter;
  }

  /**
   * Set bearing span from vendor datasheet.
   *
   * @param span span in mm
   */
  public void setBearingSpan(double span) {
    this.bearingSpan = span;
  }

  /**
   * Set bearing type.
   *
   * @param type bearing type description
   */
  public void setBearingType(String type) {
    this.bearingType = type;
  }

  /**
   * Set expander seal type.
   *
   * @param type seal type description
   */
  public void setExpanderSealType(String type) {
    this.expanderSealType = type;
  }

  /**
   * Set compressor seal type.
   *
   * @param type seal type description
   */
  public void setCompressorSealType(String type) {
    this.compressorSealType = type;
  }

  /**
   * Set gear ratio (1.0 for direct-coupled).
   *
   * @param ratio gear ratio
   */
  public void setGearRatio(double ratio) {
    this.gearRatio = ratio;
  }

  /**
   * Set expander casing design pressure from vendor datasheet or nameplate.
   *
   * @param pressure pressure in bara
   */
  public void setExpanderCasingDesignPressure(double pressure) {
    this.expanderCasingDesignPressure = pressure;
  }

  /**
   * Set expander casing design temperature from vendor datasheet or nameplate.
   *
   * @param temperature temperature in deg C
   */
  public void setExpanderCasingDesignTemperature(double temperature) {
    this.expanderCasingDesignTemperature = temperature;
  }

  /**
   * Set compressor casing design pressure from vendor datasheet or nameplate.
   *
   * @param pressure pressure in bara
   */
  public void setCompressorCasingDesignPressure(double pressure) {
    this.compressorCasingDesignPressure = pressure;
  }

  /**
   * Set compressor casing design temperature from vendor datasheet or nameplate.
   *
   * @param temperature temperature in deg C
   */
  public void setCompressorCasingDesignTemperature(double temperature) {
    this.compressorCasingDesignTemperature = temperature;
  }

  /**
   * Set compressor casing type.
   *
   * @param type casing type (BARREL, HORIZONTALLY_SPLIT, VERTICALLY_SPLIT)
   */
  public void setCompressorCasingType(String type) {
    this.compressorCasingType = type;
  }

  /**
   * Set expander design inlet pressure (rated conditions).
   *
   * @param pressure pressure in bara
   */
  public void setExpanderDesignInletPressure(double pressure) {
    this.expanderDesignInletPressure = pressure;
  }

  /**
   * Set expander design outlet pressure (rated conditions).
   *
   * @param pressure pressure in bara
   */
  public void setExpanderDesignOutletPressure(double pressure) {
    this.expanderDesignOutletPressure = pressure;
  }

  /**
   * Set expander design inlet temperature (rated conditions).
   *
   * @param temperature temperature in deg C
   */
  public void setExpanderDesignInletTemperature(double temperature) {
    this.expanderDesignInletTemperature = temperature;
  }

  /**
   * Set expander design outlet temperature (rated conditions).
   *
   * @param temperature temperature in deg C
   */
  public void setExpanderDesignOutletTemperature(double temperature) {
    this.expanderDesignOutletTemperature = temperature;
  }

  /**
   * Set compressor design suction pressure (rated conditions).
   *
   * @param pressure pressure in bara
   */
  public void setCompressorDesignSuctionPressure(double pressure) {
    this.compressorDesignSuctionPressure = pressure;
  }

  /**
   * Set compressor design discharge pressure (rated conditions).
   *
   * @param pressure pressure in bara
   */
  public void setCompressorDesignDischargePressure(double pressure) {
    this.compressorDesignDischargePressure = pressure;
  }

  /**
   * Set compressor design suction temperature (rated conditions).
   *
   * @param temperature temperature in deg C
   */
  public void setCompressorDesignSuctionTemperature(double temperature) {
    this.compressorDesignSuctionTemperature = temperature;
  }

  /**
   * Set compressor design discharge temperature (rated conditions).
   *
   * @param temperature temperature in deg C
   */
  public void setCompressorDesignDischargeTemperature(double temperature) {
    this.compressorDesignDischargeTemperature = temperature;
  }

  /**
   * Set design surge flow from vendor performance data.
   *
   * @param flow surge flow in m3/hr (actual conditions)
   */
  public void setDesignSurgeFlowM3hr(double flow) {
    this.designSurgeFlowM3hr = flow;
  }

  /**
   * Set design stonewall flow from vendor performance data.
   *
   * @param flow stonewall flow in m3/hr (actual conditions)
   */
  public void setDesignStonewallFlowM3hr(double flow) {
    this.designStonewallFlowM3hr = flow;
  }

  /**
   * Set design compressor actual volume flow.
   *
   * @param flow volume flow in m3/hr
   */
  public void setDesignCompressorVolumeFlowM3hr(double flow) {
    this.designCompressorVolumeFlowM3hr = flow;
  }

  /**
   * Set expander nozzle throat area from vendor drawing data.
   *
   * @param area throat area in m2
   */
  public void setExpanderNozzleThroatArea(double area) {
    this.expanderNozzleThroatArea = area;
  }

  /**
   * Set rated expander power from vendor datasheet.
   *
   * @param power power in kW
   */
  public void setExpanderPowerKW(double power) {
    this.expanderPowerKW = power;
  }

  /**
   * Set rated compressor power from vendor datasheet.
   *
   * @param power power in kW
   */
  public void setCompressorPowerKW(double power) {
    this.compressorPowerKW = power;
  }

  /**
   * Set expander isentropic enthalpy drop from vendor datasheet.
   *
   * @param drop enthalpy drop in kJ/kg
   */
  public void setExpanderEnthalpyDropKJkg(double drop) {
    this.expanderEnthalpyDropKJkg = drop;
  }

  /**
   * Set compressor polytropic head from vendor datasheet.
   *
   * @param head polytropic head in kJ/kg
   */
  public void setCompressorPolytropicHead(double head) {
    this.compressorPolytropicHead = head;
  }

  /**
   * Set compressor head per stage from vendor datasheet.
   *
   * @param head head per stage in kJ/kg
   */
  public void setCompressorHeadPerStage(double head) {
    this.compressorHeadPerStage = head;
  }

  // ============================================================================
  // Shear pin setters
  // ============================================================================

  /**
   * Set the shear pin breaking torque from vendor data.
   *
   * @param torqueNm breaking torque in Nm
   */
  public void setShearPinBreakingTorqueNm(double torqueNm) {
    this.shearPinBreakingTorqueNm = torqueNm;
  }

  /**
   * Set the number of shear pins.
   *
   * @param count number of pins
   */
  public void setNumberOfShearPins(int count) {
    this.numberOfShearPins = count;
  }

  /**
   * Set the shear pin diameter.
   *
   * @param diameterMm pin diameter in mm
   */
  public void setShearPinDiameterMm(double diameterMm) {
    this.shearPinDiameterMm = diameterMm;
  }

  /**
   * Set the shear pin material.
   *
   * @param material material designation (e.g. "AISI 316", "Inconel 718")
   */
  public void setShearPinMaterial(String material) {
    this.shearPinMaterial = material;
  }

  /**
   * Set the shear pin material ultimate shear strength.
   *
   * @param strengthMPa shear strength in MPa
   */
  public void setShearPinShearStrengthMPa(double strengthMPa) {
    this.shearPinShearStrengthMPa = strengthMPa;
  }

  /**
   * Set the shear pin radial position from shaft centre.
   *
   * @param radiusMm radial position in mm
   */
  public void setShearPinRadialPositionMm(double radiusMm) {
    this.shearPinRadialPositionMm = radiusMm;
  }

  /**
   * Get the number of shear pins.
   *
   * @return number of pins
   */
  public int getNumberOfShearPins() {
    return numberOfShearPins;
  }

  /**
   * Get the shear pin diameter.
   *
   * @return pin diameter in mm
   */
  public double getShearPinDiameterMm() {
    return shearPinDiameterMm;
  }

  /**
   * Get the shear pin material ultimate shear strength.
   *
   * @return shear strength in MPa
   */
  public double getShearPinShearStrengthMPa() {
    return shearPinShearStrengthMPa;
  }

  /**
   * Get the shear pin radial position from shaft centre.
   *
   * @return radial position in mm
   */
  public double getShearPinRadialPositionMm() {
    return shearPinRadialPositionMm;
  }

  // ============================================================================
  // Thrust balance setters
  // ============================================================================

  /**
   * Set the balance piston (balance drum) diameter.
   *
   * @param diameterMm balance piston outer diameter in mm
   */
  public void setBalancePistonDiameterMm(double diameterMm) {
    this.balancePistonDiameterMm = diameterMm;
  }

  /**
   * Set the thrust bearing rated load capacity from vendor data.
   *
   * @param capacityN rated capacity in N
   */
  public void setThrustBearingCapacityN(double capacityN) {
    this.thrustBearingCapacityN = capacityN;
  }

  /**
   * Set the design-point net thrust and direction.
   *
   * @param netThrustN net axial thrust in N (positive = towards compressor)
   */
  public void setDesignNetThrustN(double netThrustN) {
    this.designNetThrustN = netThrustN;
    this.designThrustDirectionSign = Math.signum(netThrustN);
  }

  // ============================================================================
  // Seal gas system setters (tetningsgass)
  // ============================================================================

  /**
   * Set seal gas supply pressure.
   *
   * @param pressureBara supply pressure in bara
   */
  public void setSealGasSupplyPressureBara(double pressureBara) {
    this.sealGasSupplyPressureBara = pressureBara;
  }

  /**
   * Set required seal gas differential pressure above process.
   *
   * @param dpBar differential pressure in bar
   */
  public void setSealGasRequiredDpBar(double dpBar) {
    this.sealGasRequiredDpBar = dpBar;
  }

  /**
   * Get seal gas supply pressure.
   *
   * @return supply pressure in bara
   */
  public double getSealGasSupplyPressureBara() {
    return sealGasSupplyPressureBara;
  }

  /**
   * Get required seal gas differential pressure above process.
   *
   * @return differential pressure in bar
   */
  public double getSealGasRequiredDpBar() {
    return sealGasRequiredDpBar;
  }

  /**
   * Set seal gas flow rate per seal.
   *
   * @param flowNm3hr flow rate in Nm3/hr
   */
  public void setSealGasFlowRateNm3hr(double flowNm3hr) {
    this.sealGasFlowRateNm3hr = flowNm3hr;
  }

  /**
   * Set seal gas type.
   *
   * @param type gas type (e.g. "Treated process gas", "N2")
   */
  public void setSealGasType(String type) {
    this.sealGasType = type;
  }

  /**
   * Set primary seal arrangement.
   *
   * @param arrangement seal arrangement (e.g. "TANDEM", "DOUBLE", "SINGLE")
   */
  public void setSealArrangement(String arrangement) {
    this.sealArrangement = arrangement;
  }

  /**
   * Set seal gas filter DP alarm setpoint.
   *
   * @param dpBar alarm setpoint in bar
   */
  public void setSealGasFilterDpAlarmBar(double dpBar) {
    this.sealGasFilterDpAlarmBar = dpBar;
  }

  // ============================================================================
  // Oil seal system setters (oljetetning)
  // ============================================================================

  /**
   * Set seal oil supply pressure.
   *
   * @param pressureBara supply pressure in bara
   */
  public void setSealOilSupplyPressureBara(double pressureBara) {
    this.sealOilSupplyPressureBara = pressureBara;
  }

  /**
   * Set seal oil differential pressure above process gas.
   *
   * @param dpBar differential pressure in bar
   */
  public void setSealOilDpAboveProcessBar(double dpBar) {
    this.sealOilDpAboveProcessBar = dpBar;
  }

  /**
   * Set seal oil flow rate.
   *
   * @param flowLpm flow rate in litres/min
   */
  public void setSealOilFlowRateLpm(double flowLpm) {
    this.sealOilFlowRateLpm = flowLpm;
  }

  /**
   * Set seal oil ISO viscosity grade.
   *
   * @param grade oil grade (e.g. "ISO VG 32", "ISO VG 46")
   */
  public void setSealOilGrade(String grade) {
    this.sealOilGrade = grade;
  }

  /**
   * Set seal oil supply temperature.
   *
   * @param temperatureC temperature in deg C
   */
  public void setSealOilTemperatureC(double temperatureC) {
    this.sealOilTemperatureC = temperatureC;
  }

  /**
   * Set seal oil tank capacity.
   *
   * @param capacityL capacity in litres
   */
  public void setSealOilTankCapacityL(double capacityL) {
    this.sealOilTankCapacityL = capacityL;
  }

  // ============================================================================
  // Anti-surge system setters
  // ============================================================================

  /**
   * Set anti-surge valve Cv.
   *
   * @param cv valve flow coefficient
   */
  public void setAntiSurgeValveCv(double cv) {
    this.antiSurgeValveCv = cv;
  }

  /**
   * Set anti-surge valve full stroke time.
   *
   * @param strokeTimeS stroke time in seconds
   */
  public void setAntiSurgeValveStrokeTimeS(double strokeTimeS) {
    this.antiSurgeValveStrokeTimeS = strokeTimeS;
  }

  /**
   * Set surge control line margin above surge line.
   *
   * @param marginFrac margin as fraction (0.10 = 10 %)
   */
  public void setSurgeControlLineMarginFrac(double marginFrac) {
    this.surgeControlLineMarginFrac = marginFrac;
  }

  /**
   * Set anti-surge controller type.
   *
   * @param type controller type (e.g. "PID", "CCC", "Triconex")
   */
  public void setAntiSurgeControllerType(String type) {
    this.antiSurgeControllerType = type;
  }

  /**
   * Set minimum recycle flow at full anti-surge.
   *
   * @param flowM3hr minimum recycle flow in m3/hr actual
   */
  public void setMinimumRecycleFlowM3hr(double flowM3hr) {
    this.minimumRecycleFlowM3hr = flowM3hr;
  }

  /**
   * Set whether anti-surge uses hot gas recycle or cold recycle with aftercooler.
   *
   * @param hotRecycle true for hot gas recycle, false for cold recycle
   */
  public void setHotGasRecycle(boolean hotRecycle) {
    this.hotGasRecycle = hotRecycle;
  }

  // ============================================================================
  // Datasheet loading — bulk setter for vendor/nameplate data
  // ============================================================================

  /**
   * Load all key mechanical design parameters from a vendor datasheet specification.
   *
   * <p>
   * This allows evaluating an <em>existing</em> TEX unit against new operating scenarios without
   * needing to run {@link #calcDesign()} first. Call this method with the manufacturer's rated
   * values, then call {@link #evaluateDesignAtConditions} for each off-design case.
   * </p>
   *
   * @param expanderWheelDiameterMm expander wheel outer diameter [mm]
   * @param compressorImpellerDiameterMm compressor impeller diameter [mm]
   * @param ratedSpeedRpm rated operating speed [rpm]
   * @param maxContSpeedRpm maximum continuous speed [rpm]
   * @param tripSpeedRpm overspeed trip speed [rpm]
   * @param firstCritRpm first lateral critical speed [rpm]
   * @param secondCritRpm second lateral critical speed [rpm]
   * @param shaftDiameterMm shaft diameter [mm]
   * @param bearingSpanMm bearing span [mm]
   * @param expCasingDesignP expander casing design pressure [bara]
   * @param expCasingDesignT expander casing design temperature [deg C]
   * @param compCasingDesignP compressor casing design pressure [bara]
   * @param compCasingDesignT compressor casing design temperature [deg C]
   */
  public void setFromDatasheet(double expanderWheelDiameterMm, double compressorImpellerDiameterMm,
      double ratedSpeedRpm, double maxContSpeedRpm, double tripSpeedRpm, double firstCritRpm,
      double secondCritRpm, double shaftDiameterMm, double bearingSpanMm, double expCasingDesignP,
      double expCasingDesignT, double compCasingDesignP, double compCasingDesignT) {
    this.expanderWheelDiameter = expanderWheelDiameterMm;
    this.compressorImpellerDiameter = compressorImpellerDiameterMm;
    this.operatingSpeed = ratedSpeedRpm;
    this.maxContinuousSpeed = maxContSpeedRpm;
    this.tripSpeed = tripSpeedRpm;
    this.firstCriticalSpeed = firstCritRpm;
    this.secondCriticalSpeed = secondCritRpm;
    this.shaftDiameter = shaftDiameterMm;
    this.bearingSpan = bearingSpanMm;
    this.expanderCasingDesignPressure = expCasingDesignP;
    this.expanderCasingDesignTemperature = expCasingDesignT;
    this.compressorCasingDesignPressure = compCasingDesignP;
    this.compressorCasingDesignTemperature = compCasingDesignT;
  }

  /**
   * Set rated operating conditions from vendor performance guarantee.
   *
   * @param expInletP expander rated inlet pressure [bara]
   * @param expOutletP expander rated outlet pressure [bara]
   * @param expInletT expander rated inlet temperature [deg C]
   * @param expOutletT expander rated outlet temperature [deg C]
   * @param compSuctionP compressor rated suction pressure [bara]
   * @param compDischargeP compressor rated discharge pressure [bara]
   * @param compSuctionT compressor rated suction temperature [deg C]
   * @param compDischargeT compressor rated discharge temperature [deg C]
   */
  public void setRatedConditions(double expInletP, double expOutletP, double expInletT,
      double expOutletT, double compSuctionP, double compDischargeP, double compSuctionT,
      double compDischargeT) {
    this.expanderDesignInletPressure = expInletP;
    this.expanderDesignOutletPressure = expOutletP;
    this.expanderDesignInletTemperature = expInletT;
    this.expanderDesignOutletTemperature = expOutletT;
    this.compressorDesignSuctionPressure = compSuctionP;
    this.compressorDesignDischargePressure = compDischargeP;
    this.compressorDesignSuctionTemperature = compSuctionT;
    this.compressorDesignDischargeTemperature = compDischargeT;
  }

  /**
   * Set rated performance data from vendor datasheet.
   *
   * @param expanderPower rated expander power [kW]
   * @param compressorPower rated compressor power [kW]
   * @param expanderEnthalpyDrop isentropic enthalpy drop [kJ/kg]
   * @param compPolytropicHead compressor polytropic head [kJ/kg]
   * @param surgeFlow surge line flow [m3/hr actual]
   * @param stonewallFlow stonewall flow [m3/hr actual]
   */
  public void setRatedPerformance(double expanderPower, double compressorPower,
      double expanderEnthalpyDrop, double compPolytropicHead, double surgeFlow,
      double stonewallFlow) {
    this.expanderPowerKW = expanderPower;
    this.compressorPowerKW = compressorPower;
    this.expanderEnthalpyDropKJkg = expanderEnthalpyDrop;
    this.compressorPolytropicHead = compPolytropicHead;
    this.designSurgeFlowM3hr = surgeFlow;
    this.designStonewallFlowM3hr = stonewallFlow;
    // Recompute design torque from rated total power and speed
    if (operatingSpeed > 0) {
      this.designTorqueNm =
          ((expanderPower + compressorPower) * 1000.0 * 60.0) / (2.0 * Math.PI * operatingSpeed);
    }
  }

  // ============================================================================
  // Multi-scenario evaluation
  // ============================================================================

  /**
   * Evaluate the fixed design against multiple operating scenarios simultaneously.
   *
   * <p>
   * Each scenario is defined as a {@code Map&lt;String, Double&gt;} with the following keys (all
   * mandatory):
   * </p>
   * <ul>
   * <li>{@code expInletP} — expander inlet pressure [bara]</li>
   * <li>{@code expOutletP} — expander outlet pressure [bara]</li>
   * <li>{@code expInletT} — expander inlet temperature [deg C]</li>
   * <li>{@code compInletP} — compressor suction pressure [bara]</li>
   * <li>{@code compDischargeP} — compressor discharge pressure [bara]</li>
   * <li>{@code compInletT} — compressor suction temperature [deg C]</li>
   * <li>{@code expMassFlow} — expander mass flow [kg/hr]</li>
   * <li>{@code compMassFlow} — compressor mass flow [kg/hr]</li>
   * <li>{@code expMolarMass} — expander gas molar mass [kg/kmol]</li>
   * <li>{@code compMolarMass} — compressor gas molar mass [kg/kmol]</li>
   * </ul>
   *
   * @param scenarioNames descriptive name for each scenario
   * @param scenarios list of scenario parameter maps
   * @return list of evaluation results, one per scenario
   */
  public List<DesignEvaluationResult> evaluateMultipleScenarios(List<String> scenarioNames,
      List<Map<String, Double>> scenarios) {
    List<DesignEvaluationResult> results = new ArrayList<DesignEvaluationResult>();

    for (int i = 0; i < scenarios.size(); i++) {
      Map<String, Double> s = scenarios.get(i);
      String name = i < scenarioNames.size() ? scenarioNames.get(i) : "Scenario " + (i + 1);

      DesignEvaluationResult result = evaluateDesignAtConditions(getScenarioValue(s, "expInletP"),
          getScenarioValue(s, "expOutletP"), getScenarioValue(s, "expInletT"),
          getScenarioValue(s, "compInletP"), getScenarioValue(s, "compDischargeP"),
          getScenarioValue(s, "compInletT"), getScenarioValue(s, "expMassFlow"),
          getScenarioValue(s, "compMassFlow"), getScenarioValue(s, "expMolarMass"),
          getScenarioValue(s, "compMolarMass"));
      result.setScenarioName(name);
      results.add(result);
    }
    return results;
  }

  /**
   * Get a value from a scenario map with a default of 0.0.
   *
   * @param scenario the scenario parameter map
   * @param key the parameter key
   * @return the value, or 0.0 if not present
   */
  private double getScenarioValue(Map<String, Double> scenario, String key) {
    Double val = scenario.get(key);
    return val != null ? val.doubleValue() : 0.0;
  }

  /**
   * Generate a comprehensive evaluation report comparing the design against multiple scenarios.
   *
   * @param scenarioNames names for each scenario
   * @param scenarios list of scenario parameter maps
   * @return JSON string with full evaluation report
   */
  public String evaluationReportToJson(List<String> scenarioNames,
      List<Map<String, Double>> scenarios) {

    List<DesignEvaluationResult> results = evaluateMultipleScenarios(scenarioNames, scenarios);

    JsonObject report = new JsonObject();
    report.addProperty("equipmentName", getProcessEquipment().getName());
    report.addProperty("reportType", "TEX Technical Evaluation");

    // Design basis
    JsonObject designBasis = new JsonObject();
    designBasis.addProperty("expanderWheelDiameter_mm", expanderWheelDiameter);
    designBasis.addProperty("compressorImpellerDiameter_mm", compressorImpellerDiameter);
    designBasis.addProperty("expanderType", expanderType);
    designBasis.addProperty("compressorStages", compressorStages);
    designBasis.addProperty("ratedSpeed_rpm", operatingSpeed);
    designBasis.addProperty("maxContinuousSpeed_rpm", maxContinuousSpeed);
    designBasis.addProperty("tripSpeed_rpm", tripSpeed);
    designBasis.addProperty("firstCriticalSpeed_rpm", firstCriticalSpeed);
    designBasis.addProperty("secondCriticalSpeed_rpm", secondCriticalSpeed);
    designBasis.addProperty("shaftDiameter_mm", shaftDiameter);
    designBasis.addProperty("bearingSpan_mm", bearingSpan);
    designBasis.addProperty("bearingType", bearingType);
    designBasis.addProperty("expanderSealType", expanderSealType);
    designBasis.addProperty("compressorSealType", compressorSealType);
    designBasis.addProperty("expanderCasingDesignPressure_bara", expanderCasingDesignPressure);
    designBasis.addProperty("expanderCasingDesignTemperature_C", expanderCasingDesignTemperature);
    designBasis.addProperty("compressorCasingDesignPressure_bara", compressorCasingDesignPressure);
    designBasis.addProperty("compressorCasingDesignTemperature_C",
        compressorCasingDesignTemperature);
    designBasis.addProperty("designSurgeFlow_m3hr", designSurgeFlowM3hr);
    designBasis.addProperty("designStonewallFlow_m3hr", designStonewallFlowM3hr);
    designBasis.addProperty("shearPinBreakingTorque_Nm", shearPinBreakingTorqueNm);
    designBasis.addProperty("shearPinCount", numberOfShearPins);
    designBasis.addProperty("shearPinMaterial", shearPinMaterial);
    designBasis.addProperty("balancePistonDiameter_mm", balancePistonDiameterMm);
    designBasis.addProperty("thrustBearingCapacity_N", thrustBearingCapacityN);
    designBasis.addProperty("sealGasSupplyPressure_bara", sealGasSupplyPressureBara);
    designBasis.addProperty("sealGasRequiredDp_bar", sealGasRequiredDpBar);
    designBasis.addProperty("sealGasType", sealGasType);
    designBasis.addProperty("sealArrangement", sealArrangement);
    designBasis.addProperty("sealOilSupplyPressure_bara", sealOilSupplyPressureBara);
    designBasis.addProperty("sealOilGrade", sealOilGrade);
    designBasis.addProperty("antiSurgeValveCv", antiSurgeValveCv);
    designBasis.addProperty("antiSurgeStrokeTime_s", antiSurgeValveStrokeTimeS);
    designBasis.addProperty("surgeControlLineMargin_pct", surgeControlLineMarginFrac * 100.0);
    designBasis.addProperty("antiSurgeControllerType", antiSurgeControllerType);
    report.add("designBasis", designBasis);

    // Rated conditions
    JsonObject ratedCond = new JsonObject();
    ratedCond.addProperty("expanderInletPressure_bara", expanderDesignInletPressure);
    ratedCond.addProperty("expanderOutletPressure_bara", expanderDesignOutletPressure);
    ratedCond.addProperty("expanderInletTemperature_C", expanderDesignInletTemperature);
    ratedCond.addProperty("expanderOutletTemperature_C", expanderDesignOutletTemperature);
    ratedCond.addProperty("compressorSuctionPressure_bara", compressorDesignSuctionPressure);
    ratedCond.addProperty("compressorDischargePressure_bara", compressorDesignDischargePressure);
    ratedCond.addProperty("compressorSuctionTemperature_C", compressorDesignSuctionTemperature);
    ratedCond.addProperty("compressorDischargeTemperature_C", compressorDesignDischargeTemperature);
    ratedCond.addProperty("expanderPower_kW", expanderPowerKW);
    ratedCond.addProperty("compressorPower_kW", compressorPowerKW);
    ratedCond.addProperty("expanderEnthalpyDrop_kJkg", expanderEnthalpyDropKJkg);
    ratedCond.addProperty("compressorPolytropicHead_kJkg", compressorPolytropicHead);
    report.add("ratedConditions", ratedCond);

    // Scenario evaluations
    JsonArray scenarioResults = new JsonArray();
    int overallFailures = 0;
    int overallWarnings = 0;

    for (int i = 0; i < results.size(); i++) {
      DesignEvaluationResult r = results.get(i);
      JsonObject scenObj = new JsonObject();
      scenObj.addProperty("scenarioName", r.getScenarioName());
      scenObj.addProperty("acceptable", r.isAcceptable());
      scenObj.addProperty("failureCount", r.getFailures().size());
      scenObj.addProperty("warningCount", r.getWarnings().size());

      // Input conditions
      if (i < scenarios.size()) {
        Map<String, Double> s = scenarios.get(i);
        JsonObject inputs = new JsonObject();
        for (Map.Entry<String, Double> entry : s.entrySet()) {
          inputs.addProperty(entry.getKey(), entry.getValue());
        }
        scenObj.add("inputConditions", inputs);
      }

      // Margins
      JsonObject margins = new JsonObject();
      for (Map.Entry<String, Double> entry : r.getMargins().entrySet()) {
        margins.addProperty(entry.getKey(), Math.round(entry.getValue() * 1000.0) / 10.0);
      }
      scenObj.add("margins_percent", margins);

      // Thrust balance details per scenario
      JsonObject thrustDetail = new JsonObject();
      thrustDetail.addProperty("expanderAxialThrust_N", r.getExpanderAxialThrustN());
      thrustDetail.addProperty("compressorAxialThrust_N", r.getCompressorAxialThrustN());
      thrustDetail.addProperty("netAxialThrust_N", r.getNetAxialThrustN());
      thrustDetail.addProperty("thrustReversalDetected", r.isThrustReversalDetected());
      scenObj.add("thrustBalance", thrustDetail);

      // Issues
      if (!r.getFailures().isEmpty()) {
        JsonArray failures = new JsonArray();
        for (String f : r.getFailures()) {
          failures.add(f);
        }
        scenObj.add("failures", failures);
        overallFailures += r.getFailures().size();
      }

      if (!r.getWarnings().isEmpty()) {
        JsonArray warnings = new JsonArray();
        for (String w : r.getWarnings()) {
          warnings.add(w);
        }
        scenObj.add("warnings", warnings);
        overallWarnings += r.getWarnings().size();
      }

      scenarioResults.add(scenObj);
    }
    report.add("scenarioEvaluations", scenarioResults);

    // Summary
    JsonObject summary = new JsonObject();
    summary.addProperty("totalScenarios", results.size());
    int acceptable = 0;
    for (DesignEvaluationResult r : results) {
      if (r.isAcceptable()) {
        acceptable++;
      }
    }
    summary.addProperty("acceptableScenarios", acceptable);
    summary.addProperty("rejectedScenarios", results.size() - acceptable);
    summary.addProperty("totalFailures", overallFailures);
    summary.addProperty("totalWarnings", overallWarnings);

    String overallVerdict;
    if (acceptable == results.size()) {
      overallVerdict = "PASS - Design acceptable for all evaluated scenarios";
    } else if (acceptable > 0) {
      overallVerdict = "CONDITIONAL - Design acceptable for " + acceptable + " of " + results.size()
          + " scenarios";
    } else {
      overallVerdict = "FAIL - Design not acceptable for any evaluated scenario";
    }
    summary.addProperty("overallVerdict", overallVerdict);
    report.add("summary", summary);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(report);
  }
}
