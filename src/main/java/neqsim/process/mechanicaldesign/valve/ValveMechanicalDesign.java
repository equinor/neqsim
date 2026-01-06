package neqsim.process.mechanicaldesign.valve;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.costestimation.valve.ValveCostEstimate;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.ValveDesignStandard;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ValveMechanicalDesign class provides mechanical design calculations for control valves.
 * </p>
 *
 * <p>
 * This class calculates valve sizing, weight, dimensions, and actuator requirements based on
 * industry standards including ANSI/ISA-75, IEC 60534, and API 6D.
 * </p>
 *
 * <p>
 * Design calculations include:
 * </p>
 * <ul>
 * <li>Valve body sizing and pressure rating selection</li>
 * <li>Body wall thickness estimation</li>
 * <li>Valve weight calculation based on size and rating</li>
 * <li>Face-to-face dimensions per ANSI/ISA standards</li>
 * <li>Actuator sizing for control valves</li>
 * <li>Module dimensions for installation planning</li>
 * </ul>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ValveMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  // ============================================================================
  // Valve Design Constants
  // ============================================================================

  /** Design pressure margin factor. */
  private static final double DESIGN_PRESSURE_MARGIN = 1.10;

  /** ANSI Class 150 maximum pressure at ambient [bara]. */
  private static final double ANSI_150_MAX_PRESSURE = 19.6;

  /** ANSI Class 300 maximum pressure at ambient [bara]. */
  private static final double ANSI_300_MAX_PRESSURE = 51.1;

  /** ANSI Class 600 maximum pressure at ambient [bara]. */
  private static final double ANSI_600_MAX_PRESSURE = 102.1;

  /** ANSI Class 900 maximum pressure at ambient [bara]. */
  private static final double ANSI_900_MAX_PRESSURE = 153.2;

  /** ANSI Class 1500 maximum pressure at ambient [bara]. */
  private static final double ANSI_1500_MAX_PRESSURE = 255.3;

  /** ANSI Class 2500 maximum pressure at ambient [bara]. */
  private static final double ANSI_2500_MAX_PRESSURE = 425.5;

  // ============================================================================
  // Valve Parameters
  // ============================================================================

  double valveCvMax = 1.0;
  double valveWeight = 100.0;
  double inletPressure = 0.0;
  double outletPressure = 0.0;
  double dP = 0.0;
  double diameter = 8 * 0.0254;
  double diameterInlet = 8 * 0.0254;
  double diameterOutlet = 8 * 0.0254;
  double xT = 0.137;
  double FL = 1.0;
  double FD = 1.0;
  boolean allowChoked = false;
  boolean allowLaminar = true;
  boolean fullOutput = true;
  String valveSizingStandard = "default"; // IEC 60534";
  String valveCharacterization = "linear";
  ValveCharacteristic valveCharacterizationMethod = null;

  // ============================================================================
  // Mechanical Design Results
  // ============================================================================

  /** ANSI pressure class (150, 300, 600, 900, 1500, 2500). */
  private int ansiPressureClass = 300;

  /** Valve body nominal size in inches. */
  private double nominalSizeInches = 4.0;

  /** Face-to-face dimension [mm]. */
  private double faceToFace = 0.0;

  /** Valve body wall thickness [mm]. */
  private double bodyWallThickness = 0.0;

  /** Valve body weight [kg]. */
  private double bodyWeight = 0.0;

  /** Actuator weight [kg]. */
  private double actuatorWeight = 0.0;

  /** Required actuator thrust [N]. */
  private double requiredActuatorThrust = 0.0;

  /** Valve stem diameter [mm]. */
  private double stemDiameter = 10.0;

  /** Design pressure [bara]. */
  private double designPressure = 0.0;

  /** Design temperature [C]. */
  private double designTemperature = 0.0;

  /** Valve type description. */
  private String valveType = "Globe";

  /** Flange connection type. */
  private String flangeType = "RF"; // Raised Face

  /**
   * <p>
   * Getter for the field <code>valveCharacterization</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getValveCharacterization() {
    return valveCharacterization;
  }

  /**
   * <p>
   * Getter for the field <code>valveCharacterizationMethod</code>.
   * </p>
   *
   * @return a {@link neqsim.process.mechanicaldesign.valve.ValveCharacteristic} object
   */
  public ValveCharacteristic getValveCharacterizationMethod() {
    return valveCharacterizationMethod;
  }

  /**
   * <p>
   * Setter for the field <code>valveCharacterizationMethod</code>.
   * </p>
   *
   * @param valveCharacterizationMethod a
   *        {@link neqsim.process.mechanicaldesign.valve.ValveCharacteristic} object
   */
  public void setValveCharacterizationMethod(ValveCharacteristic valveCharacterizationMethod) {
    this.valveCharacterizationMethod = valveCharacterizationMethod;
  }

  /**
   * <p>
   * Getter for the field <code>valveSizingStandard</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getValveSizingStandard() {
    return valveSizingStandard;
  }

  /**
   * <p>
   * Setter for the field <code>valveSizingStandard</code>.
   * </p>
   *
   * @param valveSizingStandard a {@link java.lang.String} object
   */
  public void setValveSizingStandard(String valveSizingStandard) {
    this.valveSizingStandard = valveSizingStandard;
    // valveSizing.
    if (valveSizingStandard.equals("IEC 60534")) {
      valveSizingMethod = new ControlValveSizing_IEC_60534(this);
    } else if (valveSizingStandard.equals("IEC 60534 full")) {
      valveSizingMethod = new ControlValveSizing_IEC_60534_full(this);
    } else if (valveSizingStandard.equals("prod choke")) {
      valveSizingMethod = new ControlValveSizing_simple(this);
    } else {
      valveSizingMethod = new ControlValveSizing(this);
    }
  }

  /**
   * Sets the valve characterization type.
   * 
   * <p>
   * Available valve characteristics:
   * </p>
   * <ul>
   * <li><b>linear</b> - Flow is directly proportional to valve opening. Best when pressure drop is
   * constant.</li>
   * <li><b>equal percentage</b> - Equal increments produce equal percentage changes in flow. Most
   * common for process control.</li>
   * <li><b>quick opening</b> - Large flow change at small openings. Used for on/off and safety
   * applications.</li>
   * <li><b>modified parabolic</b> - Compromise between linear and equal percentage.</li>
   * </ul>
   *
   * @param valveCharacterization the characterization type: "linear", "equal percentage", "quick
   *        opening", or "modified parabolic"
   */
  public void setValveCharacterization(String valveCharacterization) {
    this.valveCharacterization = valveCharacterization;
    if (valveCharacterization.equals("linear")) {
      valveCharacterizationMethod = new LinearCharacteristic();
    } else if (valveCharacterization.equals("equal percentage")) {
      valveCharacterizationMethod = new EqualPercentageCharacteristic();
    } else if (valveCharacterization.equals("quick opening")) {
      valveCharacterizationMethod = new QuickOpeningCharacteristic();
    } else if (valveCharacterization.equals("modified parabolic")) {
      valveCharacterizationMethod = new ModifiedParabolicCharacteristic();
    } else {
      valveCharacterizationMethod = new LinearCharacteristic();
    }
  }

  ControlValveSizingInterface valveSizingMethod = null;

  /**
   * <p>
   * Constructor for ValveMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public ValveMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    costEstimate = new ValveCostEstimate(this);
    valveSizingMethod = new ControlValveSizing(this);
    valveCharacterizationMethod = new LinearCharacteristic();
  }

  /**
   * <p>
   * getValveSizingMethod.
   * </p>
   *
   * @return a {@link neqsim.process.mechanicaldesign.valve.ControlValveSizingInterface} object
   */
  public ControlValveSizingInterface getValveSizingMethod() {
    return valveSizingMethod;
  }

  /**
   * Calculates the valve size based on the fluid properties and operating conditions.
   *
   * @return a map containing the calculated valve size and related parameters. If fullOutput is
   *         false, the map will be null.
   */
  public Map<String, Object> calcValveSize() {

    Map<String, Object> result = fullOutput ? new HashMap<>() : null;

    result = valveSizingMethod
        .calcValveSize(((ValveInterface) getProcessEquipment()).getPercentValveOpening());

    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    super.readDesignSpecifications();

    if (getDesignStandard().containsKey("valve design codes")) {
      System.out.println("valve code standard: "
          + getDesignStandard().get("valve design codes").getStandardName());
      valveCvMax =
          ((ValveDesignStandard) getDesignStandard().get("valve design codes")).getValveCvMax();
    } else {
      System.out.println("no valve code standard specified......using default");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();
    ThrottlingValve valve1 = (ThrottlingValve) getProcessEquipment();

    // Ensure valve has been run
    if (valve1.getInletStream() == null || valve1.getOutletStream() == null) {
      return;
    }

    // Get operating conditions
    inletPressure = valve1.getInletPressure();
    outletPressure = valve1.getOutletPressure();
    dP = inletPressure - outletPressure;

    double inletTemperature = valve1.getInletStream().getTemperature("C");

    // Calculate design pressure and temperature
    designPressure = inletPressure * DESIGN_PRESSURE_MARGIN;
    designTemperature = inletTemperature + 30.0;

    // Calculate valve sizing
    Map<String, Object> result = calcValveSize();
    this.valveCvMax = (double) result.get("Cv");

    // Select ANSI pressure class
    selectPressureClass(designPressure);

    // Calculate nominal valve size from Cv
    calculateNominalSize(valveCvMax);

    // Calculate face-to-face dimension
    calculateFaceToFace();

    // Calculate body wall thickness
    calculateBodyWallThickness();

    // Calculate actuator requirements
    calculateActuatorSizing();

    // Calculate weights
    calculateWeights();

    // Calculate module dimensions
    calculateModuleDimensions();
  }

  /**
   * Select ANSI pressure class based on design pressure.
   *
   * @param pressure design pressure in bara
   */
  private void selectPressureClass(double pressure) {
    if (pressure <= ANSI_150_MAX_PRESSURE) {
      ansiPressureClass = 150;
    } else if (pressure <= ANSI_300_MAX_PRESSURE) {
      ansiPressureClass = 300;
    } else if (pressure <= ANSI_600_MAX_PRESSURE) {
      ansiPressureClass = 600;
    } else if (pressure <= ANSI_900_MAX_PRESSURE) {
      ansiPressureClass = 900;
    } else if (pressure <= ANSI_1500_MAX_PRESSURE) {
      ansiPressureClass = 1500;
    } else {
      ansiPressureClass = 2500;
    }
  }

  /**
   * Calculate nominal valve size from Cv using ISA correlation.
   *
   * <p>
   * For globe valves: Cv ≈ 10 × d² (approximate for full-ported valves).
   * </p>
   *
   * @param cv valve Cv coefficient
   */
  private void calculateNominalSize(double cv) {
    // For globe valves, Cv ≈ 10 × d² where d is in inches
    // Rearranging: d = sqrt(Cv / 10)
    double calculatedSize = Math.sqrt(cv / 10.0);

    // Round to standard pipe sizes
    double[] standardSizes =
        {0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0};

    nominalSizeInches = standardSizes[0];
    for (double size : standardSizes) {
      if (calculatedSize <= size * 1.2) {
        nominalSizeInches = size;
        break;
      }
      nominalSizeInches = size;
    }

    // Update diameter fields
    diameter = nominalSizeInches * 0.0254; // Convert to meters
    diameterInlet = diameter;
    diameterOutlet = diameter;

    // Estimate stem diameter from valve size
    stemDiameter = 6.0 + nominalSizeInches * 1.5; // mm
  }

  /**
   * Calculate face-to-face dimension per ANSI/ISA-75.08.
   */
  private void calculateFaceToFace() {
    // Face-to-face dimensions for globe valves per ISA-75.08 (approximate)
    // Values in mm for ANSI Class 150-600
    if (nominalSizeInches <= 1.0) {
      faceToFace = 108.0;
    } else if (nominalSizeInches <= 1.5) {
      faceToFace = 117.0;
    } else if (nominalSizeInches <= 2.0) {
      faceToFace = 152.0;
    } else if (nominalSizeInches <= 3.0) {
      faceToFace = 203.0;
    } else if (nominalSizeInches <= 4.0) {
      faceToFace = 241.0;
    } else if (nominalSizeInches <= 6.0) {
      faceToFace = 292.0;
    } else if (nominalSizeInches <= 8.0) {
      faceToFace = 356.0;
    } else if (nominalSizeInches <= 10.0) {
      faceToFace = 432.0;
    } else if (nominalSizeInches <= 12.0) {
      faceToFace = 495.0;
    } else {
      faceToFace = 508.0 + (nominalSizeInches - 12.0) * 30.0;
    }

    // Increase for higher pressure classes
    if (ansiPressureClass >= 900) {
      faceToFace = faceToFace * 1.15;
    } else if (ansiPressureClass >= 600) {
      faceToFace = faceToFace * 1.05;
    }
  }

  /**
   * Calculate body wall thickness based on pressure rating.
   */
  private void calculateBodyWallThickness() {
    // Simplified calculation based on ASME B16.34 for steel bodies
    // t = P × R / (S × E - 0.6 × P)
    // where P = pressure (MPa), R = inner radius (mm), S = allowable stress, E = joint efficiency

    double pressureMPa = designPressure / 10.0;
    double innerRadiusMm = (nominalSizeInches * 25.4) / 2.0;
    double allowableStressMPa = 138.0; // Typical for carbon steel at ambient
    double jointEfficiency = 1.0;

    bodyWallThickness =
        (pressureMPa * innerRadiusMm) / (allowableStressMPa * jointEfficiency - 0.6 * pressureMPa);

    // Add corrosion allowance
    bodyWallThickness += getCorrosionAllowance();

    // Minimum wall thickness
    bodyWallThickness = Math.max(3.0, bodyWallThickness);
  }

  /**
   * Calculate actuator sizing requirements.
   */
  private void calculateActuatorSizing() {
    ThrottlingValve valve1 = (ThrottlingValve) getProcessEquipment();

    // Seat area calculation
    double seatDiameterMm = nominalSizeInches * 25.4 * 0.85; // 85% of nominal
    double seatAreaMm2 = Math.PI * Math.pow(seatDiameterMm / 2.0, 2);

    // Thrust to overcome fluid forces
    double fluidForceN = designPressure * 0.1 * seatAreaMm2; // MPa × mm² = N

    // Packing friction (typical 10-20% of fluid force)
    double packingFrictionN = fluidForceN * 0.15;

    // Seat load for tight shutoff (typical 5-10 N/mm of seat perimeter)
    double seatPerimeterMm = Math.PI * seatDiameterMm;
    double seatLoadN = seatPerimeterMm * 7.0;

    // Total required thrust
    requiredActuatorThrust = fluidForceN + packingFrictionN + seatLoadN;

    // Safety factor
    requiredActuatorThrust = requiredActuatorThrust * 1.25;

    // Estimate actuator weight from thrust
    // Typical pneumatic spring-diaphragm actuators: ~0.1 kg per N of thrust (rough estimate)
    actuatorWeight = requiredActuatorThrust * 0.015 + 5.0;

    // Minimum actuator weight
    actuatorWeight = Math.max(10.0, actuatorWeight);
  }

  /**
   * Calculate valve weights.
   */
  private void calculateWeights() {
    // Body weight correlation based on size and pressure class
    // Empirical formula: W = k × d^n × (Class/150)^m
    // where k, n, m are empirical constants

    double sizeFactor = Math.pow(nominalSizeInches, 2.5);
    double classFactor = Math.pow(ansiPressureClass / 150.0, 0.5);

    // Globe valve body weight (empirical, kg)
    bodyWeight = 2.5 * sizeFactor * classFactor;

    // Trim and bonnet (approximately 30% of body)
    double trimWeight = bodyWeight * 0.3;

    // Total valve weight
    valveWeight = bodyWeight + trimWeight + actuatorWeight;

    // Store results in base class
    setWeigthVesselShell(bodyWeight);
    setWeigthInternals(trimWeight);
    setWeightTotal(valveWeight);
  }

  /**
   * Calculate module dimensions for installation.
   */
  private void calculateModuleDimensions() {
    // Valve body length
    tantanLength = faceToFace / 1000.0; // Convert to meters

    // Body diameter (approximate)
    innerDiameter = nominalSizeInches * 0.0254; // Nominal pipe size
    outerDiameter = innerDiameter + 2.0 * bodyWallThickness / 1000.0;

    // Module dimensions including actuator
    double valveHeight = faceToFace * 1.5 + 300.0; // Actuator adds height
    setModuleHeight(valveHeight / 1000.0);
    setModuleWidth(faceToFace / 1000.0);
    setModuleLength(faceToFace / 1000.0);

    // Wall thickness
    wallThickness = bodyWallThickness / 1000.0;
  }

  // ============================================================================
  // Getters for Design Results
  // ============================================================================

  /**
   * Get the ANSI pressure class.
   *
   * @return ANSI class (150, 300, 600, 900, 1500, or 2500)
   */
  public int getAnsiPressureClass() {
    return ansiPressureClass;
  }

  /**
   * Get the nominal valve size in inches.
   *
   * @return nominal size in inches
   */
  public double getNominalSizeInches() {
    return nominalSizeInches;
  }

  /**
   * Get the face-to-face dimension.
   *
   * @return face-to-face in mm
   */
  public double getFaceToFace() {
    return faceToFace;
  }

  /**
   * Get the body wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getBodyWallThickness() {
    return bodyWallThickness;
  }

  /**
   * Get the required actuator thrust.
   *
   * @return thrust in Newtons
   */
  public double getRequiredActuatorThrust() {
    return requiredActuatorThrust;
  }

  /**
   * Get the actuator weight.
   *
   * @return weight in kg
   */
  public double getActuatorWeight() {
    return actuatorWeight;
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
   * @return design temperature in Celsius
   */
  public double getDesignTemperature() {
    return designTemperature;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResults() {
    JFrame dialog = new JFrame("Unit design " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] names = {"Name", "Value", "Unit"};

    String[][] table = new String[20][3];

    int row = 0;
    table[row][0] = "Valve Name";
    table[row][1] = getProcessEquipment().getName();
    table[row][2] = "";
    row++;

    table[row][0] = "Valve Cv";
    table[row][1] = String.format("%.2f", valveCvMax);
    table[row][2] = "-";
    row++;

    table[row][0] = "Nominal Size";
    table[row][1] = String.format("%.1f", nominalSizeInches);
    table[row][2] = "inches";
    row++;

    table[row][0] = "ANSI Pressure Class";
    table[row][1] = Integer.toString(ansiPressureClass);
    table[row][2] = "";
    row++;

    table[row][0] = "Design Pressure";
    table[row][1] = String.format("%.1f", designPressure);
    table[row][2] = "bara";
    row++;

    table[row][0] = "Design Temperature";
    table[row][1] = String.format("%.1f", designTemperature);
    table[row][2] = "°C";
    row++;

    table[row][0] = "Inlet Pressure";
    table[row][1] = String.format("%.2f", inletPressure);
    table[row][2] = "bara";
    row++;

    table[row][0] = "Outlet Pressure";
    table[row][1] = String.format("%.2f", outletPressure);
    table[row][2] = "bara";
    row++;

    table[row][0] = "Face-to-Face";
    table[row][1] = String.format("%.0f", faceToFace);
    table[row][2] = "mm";
    row++;

    table[row][0] = "Body Wall Thickness";
    table[row][1] = String.format("%.1f", bodyWallThickness);
    table[row][2] = "mm";
    row++;

    table[row][0] = "Body Weight";
    table[row][1] = String.format("%.1f", bodyWeight);
    table[row][2] = "kg";
    row++;

    table[row][0] = "Actuator Thrust Required";
    table[row][1] = String.format("%.0f", requiredActuatorThrust);
    table[row][2] = "N";
    row++;

    table[row][0] = "Actuator Weight";
    table[row][1] = String.format("%.1f", actuatorWeight);
    table[row][2] = "kg";
    row++;

    table[row][0] = "Total Valve Weight";
    table[row][1] = String.format("%.1f", valveWeight);
    table[row][2] = "kg";
    row++;

    table[row][0] = "Module Height";
    table[row][1] = String.format("%.3f", getModuleHeight());
    table[row][2] = "m";
    row++;

    table[row][0] = "Module Width";
    table[row][1] = String.format("%.3f", getModuleWidth());
    table[row][2] = "m";

    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.setSize(800, 600);
    dialog.setVisible(true);
  }
}
