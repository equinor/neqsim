package neqsim.process.mechanicaldesign.tank;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Mechanical design calculations for storage tanks per API 650/620.
 *
 * <p>
 * This class provides sizing and design calculations for storage tanks based on:
 * </p>
 * <ul>
 * <li>API 650 - Welded Tanks for Oil Storage (atmospheric pressure)</li>
 * <li>API 620 - Design and Construction of Large, Welded, Low-pressure Storage Tanks</li>
 * </ul>
 *
 * <p>
 * Calculations include:
 * </p>
 * <ul>
 * <li>Shell course thicknesses based on hydrostatic head</li>
 * <li>Bottom plate thickness</li>
 * <li>Roof design (cone, dome, floating)</li>
 * <li>Wind and seismic loads</li>
 * <li>Appurtenance weights (nozzles, manholes, ladders, platforms)</li>
 * <li>Foundation requirements</li>
 * <li>Total weight and footprint</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class TankMechanicalDesign extends MechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Design Constants (API 650/620)
  // ============================================================================

  /** Steel density [kg/m³]. */
  private static final double STEEL_DENSITY = 7850.0;

  /** Gravity constant [m/s²]. */
  private static final double GRAVITY = 9.81;

  /** Design pressure margin factor. */
  private static final double DESIGN_PRESSURE_MARGIN = 1.10;

  /** Corrosion allowance [mm]. */
  private static final double CORROSION_ALLOWANCE = 3.0;

  /** Minimum shell thickness [mm] per API 650. */
  private static final double MIN_SHELL_THICKNESS = 5.0;

  /** Minimum bottom thickness [mm] per API 650. */
  private static final double MIN_BOTTOM_THICKNESS = 6.0;

  /** Minimum roof thickness [mm] per API 650. */
  private static final double MIN_ROOF_THICKNESS = 5.0;

  /** Allowable stress for A36 steel [MPa]. */
  private static final double ALLOWABLE_STRESS_A36 = 160.0;

  /** Joint efficiency for butt-welded joints. */
  private static final double JOINT_EFFICIENCY = 0.85;

  // ============================================================================
  // Tank Type Enumeration
  // ============================================================================

  /**
   * Tank type classification.
   */
  public enum TankType {
    /** Fixed cone roof tank. */
    FIXED_CONE_ROOF,
    /** Fixed dome roof tank. */
    FIXED_DOME_ROOF,
    /** External floating roof tank. */
    EXTERNAL_FLOATING_ROOF,
    /** Internal floating roof tank. */
    INTERNAL_FLOATING_ROOF,
    /** Spherical tank for pressurized storage. */
    SPHERICAL,
    /** Horizontal cylindrical tank. */
    HORIZONTAL_CYLINDRICAL
  }

  /**
   * Roof type for fixed roof tanks.
   */
  public enum RoofType {
    /** Self-supporting cone roof. */
    SELF_SUPPORTING_CONE,
    /** Supported cone roof. */
    SUPPORTED_CONE,
    /** Self-supporting dome roof. */
    DOME,
    /** Aluminum geodesic dome. */
    GEODESIC_DOME,
    /** Floating roof. */
    FLOATING
  }

  // ============================================================================
  // Tank Design Parameters
  // ============================================================================

  /** Tank type. */
  private TankType tankType = TankType.FIXED_CONE_ROOF;

  /** Roof type. */
  private RoofType roofType = RoofType.SELF_SUPPORTING_CONE;

  /** Tank nominal diameter [m]. */
  private double tankDiameter = 10.0;

  /** Tank height (shell height) [m]. */
  private double tankHeight = 10.0;

  /** Design liquid level [m]. */
  private double designLiquidLevel = 9.0;

  /** Number of shell courses. */
  private int numberOfCourses = 4;

  /** Shell course height [m] - typically 2.4m (8 ft). */
  private double courseHeight = 2.4;

  /** Shell thicknesses by course [mm]. */
  private double[] shellThicknesses;

  /** Bottom plate thickness [mm]. */
  private double bottomThickness = 6.0;

  /** Roof plate thickness [mm]. */
  private double roofThickness = 5.0;

  /** Design pressure [bara]. */
  private double designPressure = 1.0; // Atmospheric

  /** Design temperature [°C]. */
  private double designTemperature = 50.0;

  /** Design specific gravity. */
  private double designSpecificGravity = 1.0;

  /** Shell weight [kg]. */
  private double shellWeight = 0.0;

  /** Bottom weight [kg]. */
  private double bottomWeight = 0.0;

  /** Roof weight [kg]. */
  private double roofWeight = 0.0;

  /** Appurtenances weight (nozzles, manholes, etc.) [kg]. */
  private double appurtenancesWeight = 0.0;

  /** Structural weight (wind girders, rafters, columns) [kg]. */
  private double structuralWeight = 0.0;

  /** Foundation load (tank + contents) [kN]. */
  private double foundationLoad = 0.0;

  /** Tank capacity [m³]. */
  private double nominalCapacity = 0.0;

  /** Working capacity [m³]. */
  private double workingCapacity = 0.0;

  /** Number of shell nozzles. */
  private int numberOfNozzles = 4;

  /** Number of manholes. */
  private int numberOfManholes = 2;

  /** Has floating roof. */
  private boolean hasFloatingRoof = false;

  /**
   * Constructor for TankMechanicalDesign.
   *
   * @param equipment the tank equipment
   */
  public TankMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    super.calcDesign();

    Tank tank = (Tank) getProcessEquipment();

    // Get tank volume from equipment (volume is in m3)
    double volume = tank.getVolume();

    // Estimate liquid density if available
    if (tank.getLiquidOutStream() != null && tank.getLiquidOutStream().getThermoSystem() != null) {
      try {
        designSpecificGravity =
            tank.getLiquidOutStream().getThermoSystem().getDensity("kg/m3") / 1000.0;
      } catch (Exception e) {
        designSpecificGravity = 0.85; // Default for oil
      }
    } else {
      designSpecificGravity = 0.85;
    }

    // Design temperature from operating conditions
    designTemperature = 50.0; // Default
    designPressure = 1.03; // Slightly above atmospheric

    // Size the tank geometry
    sizeTankGeometry(volume);

    // Calculate shell thicknesses
    calculateShellThicknesses();

    // Calculate bottom thickness
    calculateBottomThickness();

    // Calculate roof design
    calculateRoofDesign();

    // Calculate weights
    calculateWeights();

    // Calculate foundation requirements
    calculateFoundationLoad();

    // Set module dimensions
    calculateModuleDimensions();
  }

  /**
   * Size tank geometry (diameter and height) for given volume. Optimizes for minimum surface area
   * (economic design).
   *
   * @param volumeM3 required volume in m³
   */
  private void sizeTankGeometry(double volumeM3) {
    if (volumeM3 <= 0) {
      volumeM3 = 1000.0; // Default 1000 m³
    }

    // Economic height/diameter ratio typically 0.5-1.0
    // For minimum surface area: H/D = 0.5
    // V = pi/4 * D² * H
    // With H = 0.6 * D: V = pi/4 * D² * 0.6D = 0.6 * pi/4 * D³
    // D = (V / (0.6 * pi/4))^(1/3)
    double hdRatio = 0.6;
    tankDiameter = Math.pow(volumeM3 / (hdRatio * Math.PI / 4.0), 1.0 / 3.0);
    tankHeight = hdRatio * tankDiameter;

    // Round diameter to practical values
    tankDiameter = Math.ceil(tankDiameter * 2.0) / 2.0; // Round to 0.5m increments

    // Recalculate height for exact volume
    tankHeight = volumeM3 / (Math.PI / 4.0 * tankDiameter * tankDiameter);

    // Round height to course increments
    numberOfCourses = (int) Math.ceil(tankHeight / courseHeight);
    tankHeight = numberOfCourses * courseHeight;

    // Design liquid level (90% of height)
    designLiquidLevel = tankHeight * 0.9;

    // Calculate capacities
    nominalCapacity = Math.PI / 4.0 * tankDiameter * tankDiameter * tankHeight;
    workingCapacity = Math.PI / 4.0 * tankDiameter * tankDiameter * designLiquidLevel;

    // Select tank type based on size and service
    selectTankType();
  }

  /**
   * Select tank type based on size and design pressure.
   */
  private void selectTankType() {
    if (designPressure > 1.07) {
      // Low pressure tank per API 620
      if (nominalCapacity > 5000) {
        tankType = TankType.SPHERICAL;
      } else {
        tankType = TankType.HORIZONTAL_CYLINDRICAL;
      }
    } else {
      // Atmospheric tank per API 650
      if (nominalCapacity > 50000) {
        tankType = TankType.EXTERNAL_FLOATING_ROOF;
        roofType = RoofType.FLOATING;
        hasFloatingRoof = true;
      } else if (nominalCapacity > 10000) {
        tankType = TankType.INTERNAL_FLOATING_ROOF;
        roofType = RoofType.SELF_SUPPORTING_CONE;
        hasFloatingRoof = true;
      } else {
        tankType = TankType.FIXED_CONE_ROOF;
        roofType = RoofType.SELF_SUPPORTING_CONE;
        hasFloatingRoof = false;
      }
    }
  }

  /**
   * Calculate shell thicknesses for each course per API 650 one-foot method.
   */
  private void calculateShellThicknesses() {
    shellThicknesses = new double[numberOfCourses];

    for (int i = 0; i < numberOfCourses; i++) {
      // Height from bottom of course to design liquid level
      double liquidHeight = designLiquidLevel - i * courseHeight;
      if (liquidHeight < 0) {
        liquidHeight = 0;
      }

      // Hydrostatic pressure at bottom of course
      // P = rho * g * h [Pa]
      double hydrostaticPressureMPa = designSpecificGravity * 1000.0 * GRAVITY * liquidHeight / 1e6;

      // Shell thickness per API 650: t = 4.9 * D * (H - 0.3) * G / (S * E)
      // Simplified: t = P * D / (2 * S * E) + CA
      double requiredThickness = (hydrostaticPressureMPa * tankDiameter * 1000.0)
          / (2.0 * ALLOWABLE_STRESS_A36 * JOINT_EFFICIENCY);

      // Add corrosion allowance
      requiredThickness += CORROSION_ALLOWANCE;

      // Apply minimum thickness
      shellThicknesses[i] = Math.max(requiredThickness, MIN_SHELL_THICKNESS);

      // Round up to standard plate thicknesses
      shellThicknesses[i] = roundToStandardThickness(shellThicknesses[i]);
    }
  }

  /**
   * Round thickness to standard plate sizes.
   *
   * @param thickness calculated thickness in mm
   * @return standard plate thickness
   */
  private double roundToStandardThickness(double thickness) {
    double[] standardThicknesses = {5.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0,
        28.0, 30.0, 32.0, 35.0, 38.0, 40.0, 45.0, 50.0};

    for (double std : standardThicknesses) {
      if (std >= thickness) {
        return std;
      }
    }
    return standardThicknesses[standardThicknesses.length - 1];
  }

  /**
   * Calculate bottom plate thickness.
   */
  private void calculateBottomThickness() {
    // API 650 minimum for nominal bottom
    bottomThickness = MIN_BOTTOM_THICKNESS + CORROSION_ALLOWANCE;

    // For larger tanks or higher specific gravity, increase thickness
    if (tankDiameter > 30.0 || designSpecificGravity > 1.0) {
      bottomThickness = Math.max(bottomThickness, 8.0 + CORROSION_ALLOWANCE);
    }

    bottomThickness = roundToStandardThickness(bottomThickness);
  }

  /**
   * Calculate roof design and thickness.
   */
  private void calculateRoofDesign() {
    if (hasFloatingRoof) {
      // Floating roof thickness (deck plates)
      roofThickness = 5.0; // Minimum per API 650 Appendix C
    } else if (roofType == RoofType.SELF_SUPPORTING_CONE) {
      // Self-supporting cone roof
      // Maximum diameter for self-supporting: ~20m
      if (tankDiameter > 20.0) {
        roofType = RoofType.SUPPORTED_CONE;
      }
      roofThickness = MIN_ROOF_THICKNESS + CORROSION_ALLOWANCE;
    } else if (roofType == RoofType.DOME) {
      // Dome roof thickness based on diameter
      roofThickness = Math.max(MIN_ROOF_THICKNESS, tankDiameter / 2000.0 * 1000.0);
    }

    roofThickness = roundToStandardThickness(roofThickness);
  }

  /**
   * Calculate all component weights.
   */
  private void calculateWeights() {
    // Shell weight
    shellWeight = 0.0;
    for (int i = 0; i < numberOfCourses; i++) {
      double courseArea = Math.PI * tankDiameter * courseHeight;
      double courseVolume = courseArea * (shellThicknesses[i] / 1000.0);
      shellWeight += courseVolume * STEEL_DENSITY;
    }

    // Bottom weight
    double bottomArea = Math.PI / 4.0 * Math.pow(tankDiameter + 0.1, 2); // Include annular ring
    bottomWeight = bottomArea * (bottomThickness / 1000.0) * STEEL_DENSITY;

    // Roof weight
    double roofArea;
    if (roofType == RoofType.SELF_SUPPORTING_CONE || roofType == RoofType.SUPPORTED_CONE) {
      // Cone roof area (slope 1:16)
      double roofSlope = 1.0 / 16.0;
      double roofHeight = tankDiameter / 2.0 * roofSlope;
      double slopeLength = Math.sqrt(Math.pow(tankDiameter / 2.0, 2) + Math.pow(roofHeight, 2));
      roofArea = Math.PI * (tankDiameter / 2.0) * slopeLength;
    } else if (roofType == RoofType.DOME) {
      // Dome roof area
      double roofRadius = tankDiameter * 0.8;
      double roofHeight =
          roofRadius - Math.sqrt(roofRadius * roofRadius - tankDiameter * tankDiameter / 4.0);
      roofArea = 2.0 * Math.PI * roofRadius * roofHeight;
    } else {
      // Flat or floating roof
      roofArea = Math.PI / 4.0 * tankDiameter * tankDiameter;
    }
    roofWeight = roofArea * (roofThickness / 1000.0) * STEEL_DENSITY;

    // Add roof support structure for supported roofs
    if (roofType == RoofType.SUPPORTED_CONE) {
      roofWeight *= 1.3; // 30% for rafters and columns
    }

    // Appurtenances (nozzles, manholes, ladders, platforms)
    appurtenancesWeight = estimateAppurtenancesWeight();

    // Structural (wind girders, stairways, platforms)
    structuralWeight = estimateStructuralWeight();

    // Total weight
    double emptyWeight =
        shellWeight + bottomWeight + roofWeight + appurtenancesWeight + structuralWeight;

    // Set base class weights
    weigthVesselShell = shellWeight;
    weightVessel = shellWeight + bottomWeight + roofWeight;
    weigthInternals = hasFloatingRoof ? roofWeight : 0.0;
    weightNozzle = appurtenancesWeight * 0.5;
    weightStructualSteel = structuralWeight;

    setWeightTotal(emptyWeight);
  }

  /**
   * Estimate appurtenances weight.
   *
   * @return appurtenances weight in kg
   */
  private double estimateAppurtenancesWeight() {
    double weight = 0.0;

    // Nozzles (estimate 100-500 kg each depending on size)
    numberOfNozzles = Math.max(4, (int) (tankDiameter / 5.0));
    weight += numberOfNozzles * 200.0;

    // Manholes (estimate 200-400 kg each)
    numberOfManholes = Math.max(2, (int) (tankHeight / 5.0));
    weight += numberOfManholes * 300.0;

    // Ladder and cage (estimate 50 kg/m height)
    weight += tankHeight * 50.0;

    // Platform at top (estimate)
    weight += Math.PI * (tankDiameter + 1.0) * 30.0; // Walkway around top

    // Roof vents, gauging equipment
    weight += 200.0;

    return weight;
  }

  /**
   * Estimate structural weight (wind girders, etc.).
   *
   * @return structural weight in kg
   */
  private double estimateStructuralWeight() {
    double weight = 0.0;

    // Wind girder if needed (tanks > 10m diameter without floating roof)
    if (tankDiameter > 10.0 && !hasFloatingRoof) {
      // Top wind girder
      weight += Math.PI * tankDiameter * 15.0; // kg/m estimate

      // Intermediate girders for tall tanks
      if (tankHeight > 15.0) {
        int numGirders = (int) (tankHeight / 10.0);
        weight += numGirders * Math.PI * tankDiameter * 12.0;
      }
    }

    // Stairway (alternative to ladder for larger tanks)
    if (tankDiameter > 15.0) {
      weight += tankHeight * 80.0; // Stairway heavier than ladder
    }

    // Anchor bolts and clips
    weight += Math.PI * tankDiameter * 10.0;

    return weight;
  }

  /**
   * Calculate foundation load requirements.
   */
  private void calculateFoundationLoad() {
    // Empty tank weight
    double emptyWeightKN = getWeightTotal() * GRAVITY / 1000.0;

    // Liquid contents at design level
    double liquidVolume = Math.PI / 4.0 * tankDiameter * tankDiameter * designLiquidLevel;
    double liquidMass = liquidVolume * designSpecificGravity * 1000.0;
    double liquidWeightKN = liquidMass * GRAVITY / 1000.0;

    // Total foundation load
    foundationLoad = emptyWeightKN + liquidWeightKN;
  }

  /**
   * Calculate module dimensions for plot plan.
   */
  private void calculateModuleDimensions() {
    // Module includes tank plus access requirements
    moduleWidth = tankDiameter + 3.0; // Access on one side
    moduleLength = tankDiameter + 3.0;
    moduleHeight = tankHeight + 3.0; // Roof peak

    // Set base class geometric properties
    innerDiameter = tankDiameter * 1000.0; // mm
    outerDiameter = (tankDiameter + 2.0 * shellThicknesses[0] / 1000.0) * 1000.0;
    tantanLength = tankHeight * 1000.0;
    this.wallThickness = shellThicknesses[0];
  }

  // ============================================================================
  // Getters for Design Results
  // ============================================================================

  /**
   * Get the tank type.
   *
   * @return tank type
   */
  public TankType getTankType() {
    return tankType;
  }

  /**
   * Set the tank type.
   *
   * @param tankType tank type to set
   */
  public void setTankType(TankType tankType) {
    this.tankType = tankType;
  }

  /**
   * Get the roof type.
   *
   * @return roof type
   */
  public RoofType getRoofType() {
    return roofType;
  }

  /**
   * Get the tank diameter.
   *
   * @return tank diameter in meters
   */
  public double getTankDiameter() {
    return tankDiameter;
  }

  /**
   * Get the tank height.
   *
   * @return tank height in meters
   */
  public double getTankHeight() {
    return tankHeight;
  }

  /**
   * Get the shell thicknesses by course.
   *
   * @return array of shell thicknesses in mm
   */
  public double[] getShellThicknesses() {
    return shellThicknesses.clone();
  }

  /**
   * Get the bottom plate thickness.
   *
   * @return bottom thickness in mm
   */
  public double getBottomThickness() {
    return bottomThickness;
  }

  /**
   * Get the roof plate thickness.
   *
   * @return roof thickness in mm
   */
  public double getRoofThickness() {
    return roofThickness;
  }

  /**
   * Get the number of shell courses.
   *
   * @return number of courses
   */
  public int getNumberOfCourses() {
    return numberOfCourses;
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
   * Get the nominal capacity.
   *
   * @return nominal capacity in m³
   */
  public double getNominalCapacity() {
    return nominalCapacity;
  }

  /**
   * Get the working capacity.
   *
   * @return working capacity in m³
   */
  public double getWorkingCapacity() {
    return workingCapacity;
  }

  /**
   * Get the shell weight.
   *
   * @return shell weight in kg
   */
  public double getShellWeight() {
    return shellWeight;
  }

  /**
   * Get the bottom weight.
   *
   * @return bottom weight in kg
   */
  public double getBottomWeight() {
    return bottomWeight;
  }

  /**
   * Get the roof weight.
   *
   * @return roof weight in kg
   */
  public double getRoofWeight() {
    return roofWeight;
  }

  /**
   * Get the foundation load.
   *
   * @return foundation load in kN (tank + contents)
   */
  public double getFoundationLoad() {
    return foundationLoad;
  }

  /**
   * Check if tank has floating roof.
   *
   * @return true if floating roof
   */
  public boolean hasFloatingRoof() {
    return hasFloatingRoof;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResults() {
    JFrame dialog = new JFrame("Tank Mechanical Design - " + getProcessEquipment().getName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] columnNames = {"Parameter", "Value", "Unit"};
    String[][] data =
        {{"Tank Type", tankType.toString(), ""}, {"Roof Type", roofType.toString(), ""},
            {"Diameter", String.format("%.1f", tankDiameter), "m"},
            {"Height", String.format("%.1f", tankHeight), "m"},
            {"Number of Courses", String.valueOf(numberOfCourses), ""},
            {"Bottom Course Thickness",
                String.format("%.1f", shellThicknesses != null ? shellThicknesses[0] : 0), "mm"},
            {"Top Course Thickness",
                String.format("%.1f",
                    shellThicknesses != null ? shellThicknesses[numberOfCourses - 1] : 0),
                "mm"},
            {"Bottom Thickness", String.format("%.1f", bottomThickness), "mm"},
            {"Roof Thickness", String.format("%.1f", roofThickness), "mm"},
            {"Nominal Capacity", String.format("%.0f", nominalCapacity), "m³"},
            {"Working Capacity", String.format("%.0f", workingCapacity), "m³"},
            {"Shell Weight", String.format("%.0f", shellWeight), "kg"},
            {"Total Weight", String.format("%.0f", getWeightTotal()), "kg"},
            {"Foundation Load", String.format("%.0f", foundationLoad), "kN"}};

    JTable table = new JTable(data, columnNames);
    JScrollPane scrollPane = new JScrollPane(table);
    dialogContentPane.add(scrollPane, BorderLayout.CENTER);

    dialog.setSize(400, 400);
    dialog.setVisible(true);
  }
}
