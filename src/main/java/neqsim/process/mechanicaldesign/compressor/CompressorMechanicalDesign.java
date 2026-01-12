package neqsim.process.mechanicaldesign.compressor;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.costestimation.compressor.CompressorCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorMechanicalLosses;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.CompressorDesignStandard;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Mechanical design calculations for centrifugal compressors.
 *
 * <p>
 * This class provides sizing and design calculations for centrifugal compressors based on API 617
 * and industry practice. Calculations include:
 * <ul>
 * <li>Number of stages based on pressure ratio and head per stage limits</li>
 * <li>Impeller diameter sizing based on flow coefficient</li>
 * <li>Driver power sizing with mechanical losses margin</li>
 * <li>Casing design pressure and temperature</li>
 * <li>Shaft diameter estimation</li>
 * <li>Module footprint and weight estimation</li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 * <li>API 617 - Axial and Centrifugal Compressors and Expander-compressors</li>
 * <li>API 672 - Packaged, Integrally Geared Centrifugal Air Compressors</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 2.0
 */
public class CompressorMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Design Constants (API 617 / Industry Practice)
  // ============================================================================

  /** Maximum polytropic head per stage [kJ/kg] - typical for process gas. */
  private static final double MAX_HEAD_PER_STAGE = 30.0;

  /** Maximum impeller tip speed [m/s] - material limit for steel impellers. */
  private static final double MAX_TIP_SPEED = 350.0;

  /** Typical flow coefficient range for centrifugal impellers. */
  private static final double FLOW_COEFFICIENT_MIN = 0.01;
  private static final double FLOW_COEFFICIENT_MAX = 0.15;
  private static final double FLOW_COEFFICIENT_DESIGN = 0.05;

  /** Driver sizing margin per API 617. */
  private static final double DRIVER_MARGIN_SMALL = 1.25; // For power < 150 kW
  private static final double DRIVER_MARGIN_MEDIUM = 1.15; // For 150-750 kW
  private static final double DRIVER_MARGIN_LARGE = 1.10; // For power > 750 kW

  /** Design pressure margin. */
  private static final double DESIGN_PRESSURE_MARGIN = 1.10;

  /** Design temperature margin [C]. */
  private static final double DESIGN_TEMPERATURE_MARGIN = 30.0;

  // ============================================================================
  // Compressor Design Parameters
  // ============================================================================

  /** Compressor design factor from design standard. */
  private double compressorFactor = 1.0;

  /** Number of compression stages. */
  private int numberOfStages = 1;

  /** Impeller outer diameter [mm]. */
  private double impellerDiameter = 300.0;

  /** Shaft diameter at impeller [mm]. */
  private double shaftDiameter = 80.0;

  /** Impeller tip speed [m/s]. */
  private double tipSpeed = 250.0;

  /** Required driver power [kW]. */
  private double driverPower = 0.0;

  /** Driver power margin factor. */
  private double driverMargin = 1.10;

  /** Design pressure [bara]. */
  private double designPressure = 0.0;

  /** Design temperature [C]. */
  private double designTemperature = 0.0;

  /** Maximum continuous speed [rpm]. */
  private double maxContinuousSpeed = 0.0;

  /** Trip speed (typically 105% of max continuous) [rpm]. */
  private double tripSpeed = 0.0;

  /** First lateral critical speed [rpm]. */
  private double firstCriticalSpeed = 0.0;

  /** Polytropic head per stage [kJ/kg]. */
  private double headPerStage = 0.0;

  /** Casing type. */
  private CasingType casingType = CasingType.HORIZONTALLY_SPLIT;

  /** Casing weight [kg]. */
  private double casingWeight = 0.0;

  /** Rotor weight [kg]. */
  private double rotorWeight = 0.0;

  /** Bearing span [mm]. */
  private double bearingSpan = 0.0;

  /** Bundle (rotor + internals) weight [kg]. */
  private double bundleWeight = 0.0;

  /** Mechanical losses model reference. */
  private CompressorMechanicalLosses mechanicalLosses = null;

  /**
   * Casing type enumeration per API 617.
   */
  public enum CasingType {
    /** Horizontally split (barrel type for high pressure). */
    HORIZONTALLY_SPLIT,
    /** Vertically split (most common for process compressors). */
    VERTICALLY_SPLIT,
    /** Barrel type (for high pressure, sour gas). */
    BARREL
  }

  /**
   * Constructor for CompressorMechanicalDesign.
   *
   * @param equipment the compressor equipment
   */
  public CompressorMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    costEstimate = new CompressorCostEstimate(this);
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();

    if (getDesignStandard().containsKey("compressor design codes")) {
      CompressorDesignStandard compStandard =
          (CompressorDesignStandard) getDesignStandard().get("compressor design codes");
      compressorFactor = compStandard.getCompressorFactor();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Compressor compressor = (Compressor) getProcessEquipment();

    // Ensure compressor has been run
    if (compressor.getThermoSystem() == null) {
      return;
    }

    // Get operating conditions
    double suctionPressure = compressor.getInletStream().getPressure("bara");
    double dischargePressure = compressor.getOutletStream().getPressure("bara");
    double suctionTemperature = compressor.getInletStream().getTemperature("C");
    double dischargeTemperature = compressor.getOutletStream().getTemperature("C");
    double massFlowRate = compressor.getInletStream().getFlowRate("kg/hr");
    double volumeFlowRate = compressor.getInletStream().getFlowRate("m3/hr");
    double shaftPowerKW = compressor.getPower("kW");
    double polytropicHead = compressor.getPolytropicFluidHead(); // kJ/kg

    // Calculate design pressure and temperature
    designPressure = dischargePressure * DESIGN_PRESSURE_MARGIN;
    designTemperature = dischargeTemperature + DESIGN_TEMPERATURE_MARGIN;

    // Select casing type based on pressure
    selectCasingType(designPressure);

    // Calculate number of stages
    calculateNumberOfStages(polytropicHead);

    // Calculate impeller sizing
    calculateImpellerSizing(volumeFlowRate, polytropicHead, compressor.getSpeed());

    // Calculate shaft diameter
    calculateShaftDiameter(shaftPowerKW, compressor.getSpeed());

    // Calculate driver sizing
    calculateDriverSizing(shaftPowerKW);

    // Calculate rotor dynamics (critical speeds)
    calculateRotorDynamics(compressor.getSpeed());

    // Calculate weights
    calculateWeights(massFlowRate, designPressure);

    // Calculate module dimensions
    calculateModuleDimensions();

    // Get or create mechanical losses model
    if (compressor.getMechanicalLosses() != null) {
      mechanicalLosses = compressor.getMechanicalLosses();
      mechanicalLosses.setShaftDiameter(shaftDiameter);
    }
  }

  /**
   * Select casing type based on design pressure per API 617 guidelines.
   *
   * @param pressure design pressure in bara
   */
  private void selectCasingType(double pressure) {
    if (pressure > 100.0) {
      casingType = CasingType.BARREL;
    } else if (pressure > 40.0) {
      casingType = CasingType.HORIZONTALLY_SPLIT;
    } else {
      casingType = CasingType.VERTICALLY_SPLIT;
    }
  }

  /**
   * Calculate number of compression stages based on total head and max head per stage.
   *
   * @param totalPolytropicHead total polytropic head in kJ/kg
   */
  private void calculateNumberOfStages(double totalPolytropicHead) {
    if (totalPolytropicHead <= 0) {
      numberOfStages = 1;
      headPerStage = 0;
      return;
    }

    // Number of stages = ceiling(total head / max head per stage)
    numberOfStages = (int) Math.ceil(totalPolytropicHead / MAX_HEAD_PER_STAGE);
    numberOfStages = Math.max(1, numberOfStages);

    // Actual head per stage
    headPerStage = totalPolytropicHead / numberOfStages;
  }

  /**
   * Calculate impeller diameter and tip speed.
   *
   * @param volumeFlowM3hr inlet volume flow rate in m3/hr
   * @param polytropicHead total polytropic head in kJ/kg
   * @param speedRPM shaft speed in rpm
   */
  private void calculateImpellerSizing(double volumeFlowM3hr, double polytropicHead,
      double speedRPM) {
    if (speedRPM <= 0 || volumeFlowM3hr <= 0) {
      impellerDiameter = 300.0; // Default
      tipSpeed = 0.0;
      return;
    }

    // Convert volume flow to m3/s
    double volumeFlowM3s = volumeFlowM3hr / 3600.0;

    // Work coefficient (head coefficient) - typical range 0.4-0.6 for backward curved
    double workCoefficient = 0.50;

    // Calculate required tip speed from head: U^2 = H * g / (workCoeff * numStages)
    double headPerStageJ_kg = headPerStage * 1000.0; // J/kg
    tipSpeed = Math.sqrt(headPerStageJ_kg / workCoefficient);

    // Limit tip speed
    tipSpeed = Math.min(tipSpeed, MAX_TIP_SPEED);

    // Calculate impeller diameter from tip speed: D = U * 60 / (pi * N)
    impellerDiameter = (tipSpeed * 60.0) / (Math.PI * speedRPM) * 1000.0; // mm

    // Ensure reasonable range (100-1500 mm typical for process compressors)
    impellerDiameter = Math.max(100.0, Math.min(1500.0, impellerDiameter));

    // Verify flow coefficient is in acceptable range
    double flowCoefficient = volumeFlowM3s / (Math.pow(impellerDiameter / 1000.0, 2) * tipSpeed);
    if (flowCoefficient < FLOW_COEFFICIENT_MIN || flowCoefficient > FLOW_COEFFICIENT_MAX) {
      // Adjust impeller diameter to achieve design flow coefficient
      impellerDiameter = Math.sqrt(volumeFlowM3s / (FLOW_COEFFICIENT_DESIGN * tipSpeed)) * 1000.0;
      impellerDiameter = Math.max(100.0, Math.min(1500.0, impellerDiameter));
    }
  }

  /**
   * Calculate shaft diameter based on torque requirements.
   *
   * @param powerKW shaft power in kW
   * @param speedRPM shaft speed in rpm
   */
  private void calculateShaftDiameter(double powerKW, double speedRPM) {
    if (speedRPM <= 0 || powerKW <= 0) {
      shaftDiameter = 50.0; // Default minimum
      return;
    }

    // Torque = Power / angular velocity
    // T [Nm] = P [W] / omega [rad/s] = P [kW] * 1000 / (2pi * N/60)
    double torqueNm = (powerKW * 1000.0 * 60.0) / (2.0 * Math.PI * speedRPM);

    // Shaft diameter from allowable shear stress (typical: 40-60 MPa for alloy steel)
    // tau = 16T / (pi * d^3) => d = (16T / (pi * tau))^(1/3)
    double allowableShearMPa = 50.0;
    double allowableShearPa = allowableShearMPa * 1e6;

    shaftDiameter = Math.pow((16.0 * torqueNm) / (Math.PI * allowableShearPa), 1.0 / 3.0) * 1000.0;

    // Apply safety factor and round up
    shaftDiameter = shaftDiameter * 1.5;

    // Ensure reasonable range (30-300 mm typical)
    shaftDiameter = Math.max(30.0, Math.min(300.0, shaftDiameter));
  }

  /**
   * Calculate driver power requirement with API 617 margins.
   *
   * @param shaftPowerKW required shaft power in kW
   */
  private void calculateDriverSizing(double shaftPowerKW) {
    // Determine margin based on power level per API 617
    if (shaftPowerKW < 150.0) {
      driverMargin = DRIVER_MARGIN_SMALL;
    } else if (shaftPowerKW < 750.0) {
      driverMargin = DRIVER_MARGIN_MEDIUM;
    } else {
      driverMargin = DRIVER_MARGIN_LARGE;
    }

    // Add mechanical losses if available
    double mechanicalLossKW = 0.0;
    if (mechanicalLosses != null) {
      mechanicalLossKW = mechanicalLosses.getTotalMechanicalLoss();
    }

    // Total driver power
    driverPower = (shaftPowerKW + mechanicalLossKW) * driverMargin;

    // Store in base class
    setMaxDesignPower(driverPower);
  }

  /**
   * Calculate rotor dynamics parameters.
   *
   * @param operatingSpeedRPM operating speed in rpm
   */
  private void calculateRotorDynamics(double operatingSpeedRPM) {
    // Maximum continuous speed (typically 105% of rated)
    maxContinuousSpeed = operatingSpeedRPM * 1.05;

    // Trip speed (typically 110% of max continuous per API 617)
    tripSpeed = maxContinuousSpeed * 1.05;

    // Estimate bearing span based on number of stages and impeller diameter
    bearingSpan = numberOfStages * (impellerDiameter * 0.8) + impellerDiameter;

    // Simplified first critical speed estimation (Rayleigh-Ritz)
    double stiffnessFactor = 48.0 * 2.1e11 * Math.PI * Math.pow(shaftDiameter / 2000.0, 4) / 64.0;
    double massPerLength = 7850.0 * Math.PI * Math.pow(shaftDiameter / 2000.0, 2);
    if (bearingSpan > 0) {
      firstCriticalSpeed = (946.0 / (2.0 * Math.PI))
          * Math.sqrt(stiffnessFactor / (massPerLength * Math.pow(bearingSpan / 1000.0, 3))) * 60.0;
    }
  }

  /**
   * Calculate compressor weights.
   *
   * @param massFlowKghr mass flow rate in kg/hr
   * @param designPressureBara design pressure in bara
   */
  private void calculateWeights(double massFlowKghr, double designPressureBara) {
    // Empirical weight correlations based on industry data

    // Rotor weight (impellers + shaft)
    double impellerWeight = numberOfStages * 0.5 * Math.pow(impellerDiameter / 100.0, 2.5);
    double shaftWeight =
        bearingSpan / 1000.0 * 7850.0 * Math.PI * Math.pow(shaftDiameter / 2000.0, 2);
    rotorWeight = impellerWeight + shaftWeight;

    // Casing weight based on pressure and size
    double casingThickness = designPressureBara * impellerDiameter / (2.0 * 150.0); // mm
    casingThickness = Math.max(10.0, casingThickness); // minimum 10mm
    double casingLength = bearingSpan * 1.3;
    double casingOuterDiameter = impellerDiameter * 1.5;

    // Casing weight = pi * D * L * t * rho
    casingWeight = Math.PI * (casingOuterDiameter / 1000.0) * (casingLength / 1000.0)
        * (casingThickness / 1000.0) * 7850.0;

    // Add end caps (approximately 20% of shell)
    casingWeight = casingWeight * 1.2;

    // For barrel type, add extra 30%
    if (casingType == CasingType.BARREL) {
      casingWeight = casingWeight * 1.3;
    }

    // Bundle weight (rotor + internals like diaphragms, seals)
    double internalsWeight = numberOfStages * 20.0 * Math.pow(impellerDiameter / 300.0, 2);
    bundleWeight = rotorWeight + internalsWeight;

    // Total equipment weight
    double sealSystemWeight = 100.0 * (shaftDiameter / 100.0); // Dry gas seal panels etc.
    double lubeSystemWeight = 200.0 + driverPower * 0.1; // Lube oil console
    double baseWeight = casingWeight * 0.3; // Baseplate

    double emptyVesselWeight = casingWeight + baseWeight;
    double pipingWeight = emptyVesselWeight * 0.2;
    double electricalWeight = driverPower * 0.5; // Cables, junction boxes
    double structuralWeight = emptyVesselWeight * 0.15;

    double totalSkidWeight = emptyVesselWeight + bundleWeight + sealSystemWeight + lubeSystemWeight
        + pipingWeight + electricalWeight + structuralWeight;

    // Store results
    setWeigthVesselShell(casingWeight);
    setWeigthInternals(bundleWeight);
    setWeightNozzle(0.0); // Included in casing
    setWeightPiping(pipingWeight);
    setWeightElectroInstrument(electricalWeight);
    setWeightStructualSteel(structuralWeight);
    setWeightTotal(totalSkidWeight);
    weigthVesselShell = casingWeight;
    weigthInternals = bundleWeight;

    // Set dimensions for base class
    outerDiameter = impellerDiameter * 1.5 / 1000.0; // m
    innerDiameter = impellerDiameter / 1000.0; // m
    tantanLength = bearingSpan * 1.3 / 1000.0; // m
    wallThickness = (outerDiameter - innerDiameter) / 2.0;
  }

  /**
   * Calculate module dimensions for plot plan.
   */
  private void calculateModuleDimensions() {
    // Module dimensions based on compressor train layout
    // Length: compressor + driver + coupling + auxiliaries
    double compressorLength = bearingSpan * 1.5 / 1000.0; // m
    double driverLength = 0.5 + driverPower * 0.001; // Rough motor length estimate
    double couplingSpace = 0.5; // Guard and coupling space
    double auxiliarySpace = 2.0; // Lube oil console, seal gas panel

    moduleLength = compressorLength + driverLength + couplingSpace + auxiliarySpace;
    moduleLength = Math.max(4.0, moduleLength); // Minimum 4m

    // Width: compressor casing + piping + access
    moduleWidth = outerDiameter + 3.0; // 1.5m access each side
    moduleWidth = Math.max(3.0, moduleWidth);

    // Height: compressor centerline + piping + lifting
    moduleHeight = outerDiameter + 2.0;
    moduleHeight = Math.max(3.0, moduleHeight);

    setModuleLength(moduleLength);
    setModuleWidth(moduleWidth);
    setModuleHeight(moduleHeight);
  }

  /** {@inheritDoc} */
  @Override
  public void setDesign() {
    // Apply calculated design parameters back to compressor
    Compressor compressor = (Compressor) getProcessEquipment();

    // Initialize mechanical losses with calculated shaft diameter
    if (compressor.getMechanicalLosses() == null) {
      compressor.initMechanicalLosses(shaftDiameter);
    } else {
      compressor.getMechanicalLosses().setShaftDiameter(shaftDiameter);
    }

    // Set max design power
    maxDesignPower = driverPower;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResults() {
    JFrame dialog = new JFrame("Compressor Design: " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] names = {"Parameter", "Value", "Unit"};
    String[][] table = new String[22][3];

    int row = 0;
    table[row][0] = "Number of Stages";
    table[row][1] = String.valueOf(numberOfStages);
    table[row][2] = "-";

    row++;
    table[row][0] = "Head per Stage";
    table[row][1] = String.format("%.1f", headPerStage);
    table[row][2] = "kJ/kg";

    row++;
    table[row][0] = "Impeller Diameter";
    table[row][1] = String.format("%.0f", impellerDiameter);
    table[row][2] = "mm";

    row++;
    table[row][0] = "Tip Speed";
    table[row][1] = String.format("%.1f", tipSpeed);
    table[row][2] = "m/s";

    row++;
    table[row][0] = "Shaft Diameter";
    table[row][1] = String.format("%.0f", shaftDiameter);
    table[row][2] = "mm";

    row++;
    table[row][0] = "Bearing Span";
    table[row][1] = String.format("%.0f", bearingSpan);
    table[row][2] = "mm";

    row++;
    table[row][0] = "Design Pressure";
    table[row][1] = String.format("%.1f", designPressure);
    table[row][2] = "bara";

    row++;
    table[row][0] = "Design Temperature";
    table[row][1] = String.format("%.1f", designTemperature);
    table[row][2] = "C";

    row++;
    table[row][0] = "Casing Type";
    table[row][1] = casingType.toString();
    table[row][2] = "-";

    row++;
    table[row][0] = "Driver Power (with margin)";
    table[row][1] = String.format("%.1f", driverPower);
    table[row][2] = "kW";

    row++;
    table[row][0] = "Driver Margin";
    table[row][1] = String.format("%.0f", driverMargin * 100);
    table[row][2] = "%";

    row++;
    table[row][0] = "Max Continuous Speed";
    table[row][1] = String.format("%.0f", maxContinuousSpeed);
    table[row][2] = "rpm";

    row++;
    table[row][0] = "Trip Speed";
    table[row][1] = String.format("%.0f", tripSpeed);
    table[row][2] = "rpm";

    row++;
    table[row][0] = "First Critical Speed";
    table[row][1] = String.format("%.0f", firstCriticalSpeed);
    table[row][2] = "rpm";

    row++;
    table[row][0] = "Casing Weight";
    table[row][1] = String.format("%.0f", casingWeight);
    table[row][2] = "kg";

    row++;
    table[row][0] = "Bundle Weight";
    table[row][1] = String.format("%.0f", bundleWeight);
    table[row][2] = "kg";

    row++;
    table[row][0] = "Total Skid Weight";
    table[row][1] = String.format("%.0f", getWeightTotal());
    table[row][2] = "kg";

    row++;
    table[row][0] = "Module Length";
    table[row][1] = String.format("%.1f", moduleLength);
    table[row][2] = "m";

    row++;
    table[row][0] = "Module Width";
    table[row][1] = String.format("%.1f", moduleWidth);
    table[row][2] = "m";

    row++;
    table[row][0] = "Module Height";
    table[row][1] = String.format("%.1f", moduleHeight);
    table[row][2] = "m";

    JTable jTable = new JTable(table, names);
    JScrollPane scrollPane = new JScrollPane(jTable);
    dialogContentPane.add(scrollPane);
    dialog.setSize(500, 500);
    dialog.setVisible(true);
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Get the calculated number of stages.
   *
   * @return number of compression stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Set the number of stages (override calculated value).
   *
   * @param numberOfStages number of stages
   */
  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }

  /**
   * Get impeller diameter.
   *
   * @return impeller outer diameter in mm
   */
  public double getImpellerDiameter() {
    return impellerDiameter;
  }

  /**
   * Set impeller diameter (override calculated value).
   *
   * @param impellerDiameter diameter in mm
   */
  public void setImpellerDiameter(double impellerDiameter) {
    this.impellerDiameter = impellerDiameter;
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
   * Set shaft diameter (override calculated value).
   *
   * @param shaftDiameter diameter in mm
   */
  public void setShaftDiameter(double shaftDiameter) {
    this.shaftDiameter = shaftDiameter;
  }

  /**
   * Get impeller tip speed.
   *
   * @return tip speed in m/s
   */
  public double getTipSpeed() {
    return tipSpeed;
  }

  /**
   * Get required driver power with margin.
   *
   * @return driver power in kW
   */
  public double getDriverPower() {
    return driverPower;
  }

  /**
   * Get design pressure.
   *
   * @return design pressure in bara
   */
  public double getDesignPressure() {
    return designPressure;
  }

  /**
   * Get design temperature.
   *
   * @return design temperature in C
   */
  public double getDesignTemperature() {
    return designTemperature;
  }

  /**
   * Get driver margin factor.
   *
   * @return driver margin factor
   */
  public double getDriverMargin() {
    return driverMargin;
  }

  /**
   * Get maximum continuous speed.
   *
   * @return max continuous speed in rpm
   */
  public double getMaxContinuousSpeed() {
    return maxContinuousSpeed;
  }

  /**
   * Get trip speed.
   *
   * @return trip speed in rpm
   */
  public double getTripSpeed() {
    return tripSpeed;
  }

  /**
   * Get first lateral critical speed.
   *
   * @return first critical speed in rpm
   */
  public double getFirstCriticalSpeed() {
    return firstCriticalSpeed;
  }

  /**
   * Get casing type.
   *
   * @return casing type
   */
  public CasingType getCasingType() {
    return casingType;
  }

  /**
   * Set casing type.
   *
   * @param casingType casing type
   */
  public void setCasingType(CasingType casingType) {
    this.casingType = casingType;
  }

  /**
   * Get head per stage.
   *
   * @return polytropic head per stage in kJ/kg
   */
  public double getHeadPerStage() {
    return headPerStage;
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
   * Get casing weight.
   *
   * @return casing weight in kg
   */
  public double getCasingWeight() {
    return casingWeight;
  }

  /**
   * Get bundle (rotor + internals) weight.
   *
   * @return bundle weight in kg
   */
  public double getBundleWeight() {
    return bundleWeight;
  }

  /**
   * Get rotor weight.
   *
   * @return rotor weight in kg
   */
  public double getRotorWeight() {
    return rotorWeight;
  }

  /** {@inheritDoc} */
  @Override
  public double getOuterDiameter() {
    return outerDiameter;
  }

  /** {@inheritDoc} */
  @Override
  public double getWallThickness() {
    return wallThickness;
  }

  /** {@inheritDoc} */
  @Override
  public void setWallThickness(double wallThickness) {
    this.wallThickness = wallThickness;
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterDiameter(double outerDiameter) {
    this.outerDiameter = outerDiameter;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns a compressor-specific response with additional fields for staging, driver sizing, and
   * rotordynamic data.
   * </p>
   */
  @Override
  public CompressorMechanicalDesignResponse getResponse() {
    return new CompressorMechanicalDesignResponse(this);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns JSON with compressor-specific fields.
   * </p>
   */
  @Override
  public String toJson() {
    return getResponse().toJson();
  }
}
