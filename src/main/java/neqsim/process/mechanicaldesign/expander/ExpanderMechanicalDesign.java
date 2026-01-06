package neqsim.process.mechanicaldesign.expander;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Mechanical design calculations for turboexpanders per API 617.
 *
 * <p>
 * This class provides sizing and design calculations for turboexpanders (power recovery turbines)
 * based on API 617 (Axial and Centrifugal Compressors and Expander-compressors). Calculations
 * include:
 * </p>
 * <ul>
 * <li>Wheel diameter sizing based on flow and enthalpy drop</li>
 * <li>Shaft sizing for torque transmission</li>
 * <li>Casing design for pressure containment</li>
 * <li>Generator/load sizing</li>
 * <li>Bearing system requirements</li>
 * <li>Seal system design</li>
 * <li>Module footprint and weight estimation</li>
 * </ul>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>API 617 - Axial and Centrifugal Compressors and Expander-compressors</li>
 * <li>API 612 - Petroleum, Petrochemical and Natural Gas Industries - Steam Turbines</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ExpanderMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Design Constants (API 617 / Industry Practice)
  // ============================================================================

  /** Maximum wheel tip speed [m/s] - material limit for titanium/steel. */
  private static final double MAX_TIP_SPEED = 450.0;

  /** Design pressure margin. */
  private static final double DESIGN_PRESSURE_MARGIN = 1.10;

  /** Design temperature margin [°C]. */
  private static final double DESIGN_TEMPERATURE_MARGIN = 30.0;

  /** Steel density [kg/m³]. */
  private static final double STEEL_DENSITY = 7850.0;

  /** Typical radial inflow turbine velocity ratio. */
  private static final double VELOCITY_RATIO = 0.7;

  // ============================================================================
  // Expander Type Enumeration
  // ============================================================================

  /**
   * Expander type classification.
   */
  public enum ExpanderType {
    /** Radial inflow turbine - most common for cryogenic and process gas. */
    RADIAL_INFLOW,
    /** Axial turbine - for very high flows. */
    AXIAL,
    /** Mixed flow turbine. */
    MIXED_FLOW
  }

  /**
   * Load type for power recovery.
   */
  public enum LoadType {
    /** Coupled to generator for power generation. */
    GENERATOR,
    /** Coupled to compressor (expander-compressor). */
    COMPRESSOR,
    /** Oil brake or hydraulic dynamometer (dissipative). */
    BRAKE
  }

  // ============================================================================
  // Expander Design Parameters
  // ============================================================================

  /** Expander type. */
  private ExpanderType expanderType = ExpanderType.RADIAL_INFLOW;

  /** Load type. */
  private LoadType loadType = LoadType.GENERATOR;

  /** Number of stages. */
  private int numberOfStages = 1;

  /** Wheel outer diameter [mm]. */
  private double wheelDiameter = 300.0;

  /** Wheel tip speed [m/s]. */
  private double tipSpeed = 350.0;

  /** Shaft diameter [mm]. */
  private double shaftDiameter = 60.0;

  /** Rated speed [rpm]. */
  private double ratedSpeed = 20000.0;

  /** Maximum continuous speed [rpm]. */
  private double maxContinuousSpeed = 21000.0;

  /** Trip speed [rpm]. */
  private double tripSpeed = 23100.0; // 110% of MCS

  /** First critical speed [rpm]. */
  private double firstCriticalSpeed = 0.0;

  /** Recovered power [kW]. */
  private double recoveredPower = 0.0;

  /** Isentropic efficiency. */
  private double isentropicEfficiency = 0.85;

  /** Design inlet pressure [bara]. */
  private double designInletPressure = 0.0;

  /** Design outlet pressure [bara]. */
  private double designOutletPressure = 0.0;

  /** Design inlet temperature [°C]. */
  private double designInletTemperature = 0.0;

  /** Design outlet temperature [°C]. */
  private double designOutletTemperature = 0.0;

  /** Casing design pressure [bara]. */
  private double casingDesignPressure = 0.0;

  /** Casing design temperature [°C]. */
  private double casingDesignTemperature = 0.0;

  /** Casing wall thickness [mm]. */
  private double casingWallThickness = 20.0;

  /** Wheel weight [kg]. */
  private double wheelWeight = 0.0;

  /** Casing weight [kg]. */
  private double casingWeight = 0.0;

  /** Shaft weight [kg]. */
  private double shaftWeight = 0.0;

  /** Generator weight [kg]. */
  private double generatorWeight = 0.0;

  /** Bearing span [mm]. */
  private double bearingSpan = 0.0;

  /** Seal type. */
  private String sealType = "Labyrinth";

  /** Bearing type. */
  private String bearingType = "Tilting Pad";

  /**
   * Constructor for ExpanderMechanicalDesign.
   *
   * @param equipment the expander equipment
   */
  public ExpanderMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Expander expander = (Expander) getProcessEquipment();

    // Ensure expander has been run
    if (expander.getInletStream() == null || expander.getInletStream().getThermoSystem() == null) {
      return;
    }

    // Get operating conditions
    double inletPressure = expander.getInletStream().getPressure("bara");
    double outletPressure = expander.getOutletStream().getPressure("bara");
    double inletTemperature = expander.getInletStream().getTemperature("C");
    double outletTemperature = expander.getOutletStream().getTemperature("C");
    double massFlowRate = expander.getInletStream().getFlowRate("kg/hr");
    double volumeFlowRateInlet = expander.getInletStream().getFlowRate("m3/hr");

    // Get power (negative for expander = power out)
    double power = Math.abs(expander.getPower("kW"));
    recoveredPower = power;

    // Get efficiency
    isentropicEfficiency = expander.getIsentropicEfficiency();
    if (isentropicEfficiency <= 0 || isentropicEfficiency > 1.0) {
      isentropicEfficiency = 0.85;
    }

    // Calculate isentropic enthalpy drop
    double enthalpyDropKJkg = 0.0;
    if (massFlowRate > 0) {
      enthalpyDropKJkg = (power * 3600.0) / massFlowRate; // kJ/kg
    }

    // Set design conditions
    designInletPressure = inletPressure;
    designOutletPressure = outletPressure;
    designInletTemperature = inletTemperature;
    designOutletTemperature = outletTemperature;

    // Casing design with margins
    casingDesignPressure = inletPressure * DESIGN_PRESSURE_MARGIN;
    casingDesignTemperature =
        Math.max(inletTemperature, outletTemperature) + DESIGN_TEMPERATURE_MARGIN;

    // Select expander type
    selectExpanderType(volumeFlowRateInlet, enthalpyDropKJkg);

    // Calculate wheel sizing
    calculateWheelSizing(volumeFlowRateInlet, enthalpyDropKJkg);

    // Calculate shaft sizing
    calculateShaftSizing(power);

    // Calculate rotor dynamics
    calculateRotorDynamics();

    // Calculate casing design
    calculateCasingDesign();

    // Calculate generator sizing
    calculateGeneratorSizing(power);

    // Calculate weights
    calculateWeights();

    // Calculate module dimensions
    calculateModuleDimensions();
  }

  /**
   * Select expander type based on flow and head.
   *
   * @param volumeFlowM3hr inlet volume flow in m³/h
   * @param enthalpyDropKJkg isentropic enthalpy drop in kJ/kg
   */
  private void selectExpanderType(double volumeFlowM3hr, double enthalpyDropKJkg) {
    // Specific speed approach for turbine selection
    // High Ns (>100) = axial, Low Ns (<50) = radial inflow

    // For process applications, radial inflow is most common
    if (volumeFlowM3hr > 100000) {
      expanderType = ExpanderType.AXIAL;
      numberOfStages = (int) Math.ceil(enthalpyDropKJkg / 50.0); // ~50 kJ/kg per axial stage
    } else {
      expanderType = ExpanderType.RADIAL_INFLOW;
      numberOfStages = 1; // Radial typically single stage
    }

    // Determine load type based on power level
    if (recoveredPower > 100) {
      loadType = LoadType.GENERATOR;
    } else {
      loadType = LoadType.BRAKE; // Small units often use oil brake
    }
  }

  /**
   * Calculate wheel diameter and speed.
   *
   * @param volumeFlowM3hr inlet volume flow in m³/h
   * @param enthalpyDropKJkg isentropic enthalpy drop in kJ/kg
   */
  private void calculateWheelSizing(double volumeFlowM3hr, double enthalpyDropKJkg) {
    if (enthalpyDropKJkg <= 0 || volumeFlowM3hr <= 0) {
      wheelDiameter = 300.0;
      tipSpeed = 300.0;
      ratedSpeed = 20000.0;
      return;
    }

    // For radial inflow turbine:
    // Spouting velocity: C0 = sqrt(2 * delta_h_is)
    double enthalpyDropJkg = enthalpyDropKJkg * 1000.0;
    double spoutingVelocity = Math.sqrt(2.0 * enthalpyDropJkg);

    // Optimal tip speed: U = velocity_ratio * C0
    tipSpeed = VELOCITY_RATIO * spoutingVelocity;

    // Limit tip speed to material allowable
    tipSpeed = Math.min(tipSpeed, MAX_TIP_SPEED);

    // Volume flow at outlet (expand for sizing)
    double volumeFlowM3s = volumeFlowM3hr / 3600.0;

    // Specific speed for turbine: Ns = N * sqrt(Q) / H^0.75
    // Target Ns around 50-100 for radial inflow
    double targetNs = 70.0;

    // Solve for speed: N = Ns * H^0.75 / sqrt(Q)
    double enthalpyPerStage = enthalpyDropKJkg / numberOfStages;
    ratedSpeed = targetNs * Math.pow(enthalpyPerStage, 0.75) / Math.sqrt(volumeFlowM3s * 60.0);

    // Limit speed to practical range
    ratedSpeed = Math.max(3000, Math.min(100000, ratedSpeed));

    // Calculate wheel diameter from tip speed and speed
    // U = pi * D * N / 60
    wheelDiameter = (tipSpeed * 60.0) / (Math.PI * ratedSpeed) * 1000.0; // mm

    // Ensure reasonable diameter range
    wheelDiameter = Math.max(100.0, Math.min(1000.0, wheelDiameter));

    // Recalculate speed for actual diameter
    ratedSpeed = (tipSpeed * 60.0) / (Math.PI * wheelDiameter / 1000.0);

    // Set trip and max continuous speed
    maxContinuousSpeed = ratedSpeed * 1.05;
    tripSpeed = ratedSpeed * 1.10;
  }

  /**
   * Calculate shaft diameter for torque.
   *
   * @param powerKW shaft power in kW
   */
  private void calculateShaftSizing(double powerKW) {
    if (ratedSpeed <= 0 || powerKW <= 0) {
      shaftDiameter = 50.0;
      return;
    }

    // Torque: T = P * 60 / (2 * pi * N)
    double torqueKNm = (powerKW * 60.0) / (2.0 * Math.PI * ratedSpeed);
    double torqueNm = torqueKNm * 1000.0;

    // Shaft diameter from shear stress
    // d = (16*T / (pi*tau))^(1/3)
    double allowableShear = 50.0e6; // Pa - higher for forged steel
    double shaftDiameterM = Math.pow((16.0 * torqueNm) / (Math.PI * allowableShear), 1.0 / 3.0);
    shaftDiameter = shaftDiameterM * 1000.0;

    // Apply safety factor for dynamic loads
    shaftDiameter *= 1.5;

    // Round up to standard sizes
    shaftDiameter = Math.max(30.0, Math.ceil(shaftDiameter / 5.0) * 5.0);
  }

  /**
   * Calculate rotor dynamics parameters.
   */
  private void calculateRotorDynamics() {
    // Bearing span based on wheel size
    bearingSpan = wheelDiameter * 2.5; // Typical ratio

    // First critical speed estimation (simplified Rayleigh-Ritz)
    // For stiff shaft: Nc = 946 * sqrt(d^4 / (m * L^3))
    double d = shaftDiameter / 1000.0; // m
    double L = bearingSpan / 1000.0; // m
    double massPerLength = STEEL_DENSITY * Math.PI * d * d / 4.0;

    firstCriticalSpeed = 946.0 * Math.sqrt(Math.pow(d, 4) / (massPerLength * Math.pow(L, 3)));
    firstCriticalSpeed = Math.max(firstCriticalSpeed, ratedSpeed * 0.5);

    // Select bearing type based on speed
    if (ratedSpeed > 50000) {
      bearingType = "Active Magnetic";
    } else if (ratedSpeed > 20000) {
      bearingType = "Tilting Pad";
    } else {
      bearingType = "Sleeve";
    }

    // Select seal type based on pressure and gas
    if (casingDesignPressure > 50.0) {
      sealType = "Dry Gas";
    } else {
      sealType = "Labyrinth";
    }
  }

  /**
   * Calculate casing design.
   */
  private void calculateCasingDesign() {
    // Casing inner diameter based on wheel + clearance
    double casingID = wheelDiameter * 1.3; // mm

    // Wall thickness per ASME
    double pressureMPa = casingDesignPressure * 0.1;
    double allowableStress = 137.9; // MPa for carbon steel

    // Temperature derating
    if (casingDesignTemperature > 200) {
      allowableStress *= 0.85;
    } else if (casingDesignTemperature < -50) {
      // Low temperature may require special materials
      allowableStress *= 0.9;
    }

    double jointEfficiency = 0.85;

    casingWallThickness =
        (pressureMPa * casingID) / (2.0 * allowableStress * jointEfficiency - 0.6 * pressureMPa);

    // Add corrosion allowance
    casingWallThickness += 3.0;

    // Minimum practical thickness
    casingWallThickness = Math.max(10.0, casingWallThickness);

    innerDiameter = casingID;
    outerDiameter = casingID + 2.0 * casingWallThickness;
    this.wallThickness = casingWallThickness;
  }

  /**
   * Calculate generator sizing.
   *
   * @param powerKW power output in kW
   */
  private void calculateGeneratorSizing(double powerKW) {
    if (loadType != LoadType.GENERATOR) {
      generatorWeight = 0.0;
      return;
    }

    // Generator weight based on power (empirical)
    if (powerKW <= 100) {
      generatorWeight = 100.0 + powerKW * 3.0;
    } else if (powerKW <= 1000) {
      generatorWeight = 400.0 + powerKW * 2.0;
    } else {
      generatorWeight = 2400.0 + powerKW * 1.0;
    }
  }

  /**
   * Calculate component weights.
   */
  private void calculateWeights() {
    // Wheel weight
    double wheelVolume = Math.PI * Math.pow(wheelDiameter / 2000.0, 2) * (wheelDiameter / 3000.0);
    wheelWeight = wheelVolume * STEEL_DENSITY * 0.7; // Hollowed wheel

    // Shaft weight
    double shaftLength = bearingSpan * 1.5;
    double shaftVolume = Math.PI * Math.pow(shaftDiameter / 2000.0, 2) * (shaftLength / 1000.0);
    shaftWeight = shaftVolume * STEEL_DENSITY;

    // Casing weight
    double casingLength = bearingSpan * 2.0; // mm
    double casingOuterDiameter = outerDiameter;
    double casingInnerDiameter = innerDiameter;
    double casingVolumeM3 = Math.PI / 4.0
        * (Math.pow(casingOuterDiameter / 1000.0, 2) - Math.pow(casingInnerDiameter / 1000.0, 2))
        * (casingLength / 1000.0);
    casingWeight = casingVolumeM3 * STEEL_DENSITY * 1.5; // Factor for nozzles, flanges

    // Bundle weight
    double bundleWeight = wheelWeight + shaftWeight + wheelWeight * 0.3; // + seals, bearings

    // Total equipment weight
    double equipmentWeight = casingWeight + bundleWeight + generatorWeight;

    // Skid items
    double lubeSystemWeight = 200.0 + recoveredPower * 0.2;
    double sealSystemWeight = sealType.equals("Dry Gas") ? 500.0 : 100.0;
    double baseplateWeight = equipmentWeight * 0.2;
    double pipingWeight = equipmentWeight * 0.15;
    double electricalWeight = loadType == LoadType.GENERATOR ? recoveredPower * 0.3 : 50.0;
    double structuralWeight = equipmentWeight * 0.1;

    // Set base class weights
    weightVessel = casingWeight;
    weigthInternals = bundleWeight;
    weightPiping = pipingWeight + lubeSystemWeight + sealSystemWeight;
    weightElectroInstrument = electricalWeight;
    weightStructualSteel = structuralWeight + baseplateWeight;

    double totalWeight = equipmentWeight + lubeSystemWeight + sealSystemWeight + baseplateWeight
        + pipingWeight + electricalWeight + structuralWeight;

    setWeightTotal(totalWeight);

    tantanLength = bearingSpan * 2.0;
  }

  /**
   * Calculate module dimensions.
   */
  private void calculateModuleDimensions() {
    // Module based on expander + generator layout
    double expanderLength = bearingSpan / 1000.0 * 2.5;
    double generatorLength =
        loadType == LoadType.GENERATOR ? 1.0 + Math.sqrt(recoveredPower) * 0.03 : 0.5;

    moduleLength = expanderLength + generatorLength + 2.0; // + access
    moduleWidth = Math.max(2.0, outerDiameter / 1000.0 * 2.0 + 1.0);
    moduleHeight = Math.max(1.5, outerDiameter / 1000.0 + 1.0);
  }

  // ============================================================================
  // Getters for Design Results
  // ============================================================================

  /**
   * Get the expander type.
   *
   * @return expander type
   */
  public ExpanderType getExpanderType() {
    return expanderType;
  }

  /**
   * Get the load type.
   *
   * @return load type
   */
  public LoadType getLoadType() {
    return loadType;
  }

  /**
   * Get the wheel diameter.
   *
   * @return wheel diameter in mm
   */
  public double getWheelDiameter() {
    return wheelDiameter;
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
   * Get the rated speed.
   *
   * @return rated speed in rpm
   */
  public double getRatedSpeed() {
    return ratedSpeed;
  }

  /**
   * Get the recovered power.
   *
   * @return recovered power in kW
   */
  public double getRecoveredPower() {
    return recoveredPower;
  }

  /**
   * Get the casing design pressure.
   *
   * @return design pressure in bara
   */
  public double getCasingDesignPressure() {
    return casingDesignPressure;
  }

  /**
   * Get the casing design temperature.
   *
   * @return design temperature in °C
   */
  public double getCasingDesignTemperature() {
    return casingDesignTemperature;
  }

  /**
   * Get the number of stages.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Get the first critical speed.
   *
   * @return first critical speed in rpm
   */
  public double getFirstCriticalSpeed() {
    return firstCriticalSpeed;
  }

  /**
   * Get the tip speed.
   *
   * @return tip speed in m/s
   */
  public double getTipSpeed() {
    return tipSpeed;
  }

  /**
   * Get the isentropic efficiency.
   *
   * @return isentropic efficiency (0-1)
   */
  public double getIsentropicEfficiency() {
    return isentropicEfficiency;
  }

  /**
   * Get the seal type.
   *
   * @return seal type description
   */
  public String getSealType() {
    return sealType;
  }

  /**
   * Get the bearing type.
   *
   * @return bearing type description
   */
  public String getBearingType() {
    return bearingType;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResults() {
    JFrame dialog = new JFrame("Expander Mechanical Design - " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] columnNames = {"Parameter", "Value", "Unit"};
    String[][] data =
        {{"Expander Type", expanderType.toString(), ""}, {"Load Type", loadType.toString(), ""},
            {"Number of Stages", String.valueOf(numberOfStages), ""},
            {"Wheel Diameter", String.format("%.1f", wheelDiameter), "mm"},
            {"Rated Speed", String.format("%.0f", ratedSpeed), "rpm"},
            {"Tip Speed", String.format("%.1f", tipSpeed), "m/s"},
            {"Shaft Diameter", String.format("%.1f", shaftDiameter), "mm"},
            {"Recovered Power", String.format("%.1f", recoveredPower), "kW"},
            {"Isentropic Efficiency", String.format("%.1f", isentropicEfficiency * 100), "%"},
            {"Design Inlet Pressure", String.format("%.1f", designInletPressure), "bara"},
            {"Design Outlet Pressure", String.format("%.1f", designOutletPressure), "bara"},
            {"Bearing Type", bearingType, ""}, {"Seal Type", sealType, ""},
            {"Total Weight", String.format("%.0f", getWeightTotal()), "kg"}};

    JTable table = new JTable(data, columnNames);
    JScrollPane scrollPane = new JScrollPane(table);
    dialogContentPane.add(scrollPane, BorderLayout.CENTER);

    dialog.setSize(400, 400);
    dialog.setVisible(true);
  }
}
