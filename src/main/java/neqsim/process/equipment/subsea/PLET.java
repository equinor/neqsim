package neqsim.process.equipment.subsea;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.subsea.PLETMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Pipeline End Termination (PLET) equipment class.
 *
 * <p>
 * A PLET is a subsea structure located at the end of a flowline or pipeline that provides:
 * </p>
 * <ul>
 * <li>Connection interface for flowlines, jumpers, or risers</li>
 * <li>Isolation valve functionality</li>
 * <li>Tie-in capability for future expansions</li>
 * <li>Pigging facilities (pig launcher/receiver)</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API RP 17G - Recommended Practice for Completion/Workover Risers</li>
 * <li>API RP 17N - Recommended Practice for Subsea Production System Reliability</li>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems</li>
 * <li>NORSOK U-001 - Subsea Production Systems</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create PLET at flowline termination
 * PLET plet = new PLET("PLET-1", flowlineOutletStream);
 * plet.setWaterDepth(350.0);
 * plet.setDesignPressure(150.0);
 * plet.setDesignTemperature(80.0);
 * plet.setConnectionType(PLET.ConnectionType.VERTICAL_HUB);
 * plet.setHasIsolationValve(true);
 * plet.setHasPiggingFacility(true);
 * plet.run();
 *
 * // Get mechanical design
 * PLETMechanicalDesign design = (PLETMechanicalDesign) plet.getMechanicalDesign();
 * design.calcDesign();
 * String json = design.toJson();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see PLETMechanicalDesign
 * @see PLEM
 * @see SubseaManifold
 */
public class PLET extends TwoPortEquipment {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Connection type for PLET interfaces.
   */
  public enum ConnectionType {
    /** Vertical connection hub (standard). */
    VERTICAL_HUB("Vertical Hub"),
    /** Horizontal connection hub. */
    HORIZONTAL_HUB("Horizontal Hub"),
    /** Clamp connector. */
    CLAMP_CONNECTOR("Clamp Connector"),
    /** Collet connector. */
    COLLET_CONNECTOR("Collet Connector"),
    /** Diver-operated flange. */
    DIVER_FLANGE("Diver Flange");

    private final String displayName;

    ConnectionType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  /**
   * PLET structure type.
   */
  public enum StructureType {
    /** Gravity-based foundation. */
    GRAVITY_BASE("Gravity Base"),
    /** Pile-founded structure. */
    PILED("Piled"),
    /** Suction anchor foundation. */
    SUCTION_ANCHOR("Suction Anchor"),
    /** Mudmat foundation. */
    MUDMAT("Mudmat");

    private final String displayName;

    StructureType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  // ============ Configuration ============
  /** Water depth in meters. */
  private double waterDepth = 350.0;

  /** Design pressure in bara. */
  private double designPressure = 150.0;

  /** Design temperature in Celsius. */
  private double designTemperature = 80.0;

  /** Nominal bore size in inches. */
  private double nominalBoreInches = 10.0;

  /** Connection type. */
  private ConnectionType connectionType = ConnectionType.VERTICAL_HUB;

  /** Structure type. */
  private StructureType structureType = StructureType.GRAVITY_BASE;

  /** Number of connection hubs. */
  private int numberOfHubs = 2;

  // ============ Valve Configuration ============
  /** Whether PLET has isolation valve. */
  private boolean hasIsolationValve = true;

  /** Internal isolation valve. */
  private ThrottlingValve isolationValve;

  /** Valve type (ball, gate). */
  private String valveType = "Ball";

  /** Valve actuator type (ROV, hydraulic). */
  private String actuatorType = "Hydraulic";

  // ============ Pigging Facilities ============
  /** Whether PLET has pigging facilities. */
  private boolean hasPiggingFacility = false;

  /** Pig launcher/receiver type. */
  private String piggingType = "none";

  // ============ Future Tie-in ============
  /** Whether PLET is configured for future tie-in. */
  private boolean hasFutureTieIn = false;

  /** Number of spare hubs for future connections. */
  private int spareHubs = 0;

  // ============ Structure Dimensions ============
  /** Structure length in meters. */
  private double structureLength = 6.0;

  /** Structure width in meters. */
  private double structureWidth = 4.0;

  /** Structure height in meters. */
  private double structureHeight = 3.0;

  /** Dry weight in tonnes. */
  private double dryWeight = 25.0;

  /** Submerged weight in tonnes. */
  private double submergedWeight = 21.0;

  /** Mechanical design instance. */
  private PLETMechanicalDesign mechanicalDesign;

  /**
   * Default constructor.
   */
  public PLET() {
    super("PLET");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public PLET(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public PLET(String name, StreamInterface inStream) {
    super(name, inStream);
    if (hasIsolationValve) {
      initializeIsolationValve();
    }
  }

  /**
   * Initialize the internal isolation valve.
   */
  private void initializeIsolationValve() {
    if (inStream != null) {
      isolationValve = new ThrottlingValve(getName() + " Isolation Valve", inStream);
      isolationValve.setPercentValveOpening(100.0); // Fully open by default
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inStream == null) {
      return;
    }

    SystemInterface outFluid;

    if (hasIsolationValve && isolationValve != null) {
      // Run through isolation valve
      isolationValve.setInletStream(inStream);
      isolationValve.run(id);
      outFluid = isolationValve.getOutletStream().getFluid().clone();
    } else {
      // Pass through
      outFluid = inStream.getFluid().clone();
    }

    // Apply any pressure drop through PLET (typically minimal)
    double pletPressureDrop = calculatePressureDrop();
    double outPressure = outFluid.getPressure() - pletPressureDrop;
    if (outPressure > 0) {
      outFluid.setPressure(outPressure);
    }

    outStream.setFluid(outFluid);
    setCalculationIdentifier(id);
  }

  /**
   * Calculate pressure drop through PLET.
   *
   * @return pressure drop in bar
   */
  private double calculatePressureDrop() {
    // Minimal pressure drop for PLET (primarily piping losses)
    // Approximately 0.1-0.5 bar depending on configuration
    double baseDrop = 0.1;
    if (hasIsolationValve) {
      baseDrop += 0.1; // Additional drop for valve
    }
    if (hasPiggingFacility) {
      baseDrop += 0.1; // Additional drop for pigging loop
    }
    return baseDrop;
  }

  /**
   * Set isolation valve opening percentage.
   *
   * @param percentOpen valve opening (0-100%)
   */
  public void setIsolationValveOpening(double percentOpen) {
    if (isolationValve != null) {
      isolationValve.setPercentValveOpening(percentOpen);
    }
  }

  /**
   * Get isolation valve opening percentage.
   *
   * @return valve opening (0-100%)
   */
  public double getIsolationValveOpening() {
    if (isolationValve != null) {
      return isolationValve.getPercentValveOpening();
    }
    return 100.0;
  }

  /**
   * Close the isolation valve.
   */
  public void closeIsolationValve() {
    setIsolationValveOpening(0.0);
  }

  /**
   * Open the isolation valve.
   */
  public void openIsolationValve() {
    setIsolationValveOpening(100.0);
  }

  /** {@inheritDoc} */
  @Override
  public PLETMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new PLETMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new PLETMechanicalDesign(this);
  }

  // ============ Getters and Setters ============

  /**
   * Get water depth.
   *
   * @return water depth in meters
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth water depth in meters
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
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
   * Set design pressure.
   *
   * @param designPressure design pressure in bara
   */
  public void setDesignPressure(double designPressure) {
    this.designPressure = designPressure;
  }

  /**
   * Get design temperature.
   *
   * @return design temperature in Celsius
   */
  public double getDesignTemperature() {
    return designTemperature;
  }

  /**
   * Set design temperature.
   *
   * @param designTemperature design temperature in Celsius
   */
  public void setDesignTemperature(double designTemperature) {
    this.designTemperature = designTemperature;
  }

  /**
   * Get nominal bore size.
   *
   * @return nominal bore in inches
   */
  public double getNominalBoreInches() {
    return nominalBoreInches;
  }

  /**
   * Set nominal bore size.
   *
   * @param nominalBoreInches nominal bore in inches
   */
  public void setNominalBoreInches(double nominalBoreInches) {
    this.nominalBoreInches = nominalBoreInches;
  }

  /**
   * Get connection type.
   *
   * @return connection type
   */
  public ConnectionType getConnectionType() {
    return connectionType;
  }

  /**
   * Set connection type.
   *
   * @param connectionType connection type
   */
  public void setConnectionType(ConnectionType connectionType) {
    this.connectionType = connectionType;
  }

  /**
   * Get structure type.
   *
   * @return structure type
   */
  public StructureType getStructureType() {
    return structureType;
  }

  /**
   * Set structure type.
   *
   * @param structureType structure type
   */
  public void setStructureType(StructureType structureType) {
    this.structureType = structureType;
  }

  /**
   * Get number of hubs.
   *
   * @return number of connection hubs
   */
  public int getNumberOfHubs() {
    return numberOfHubs;
  }

  /**
   * Set number of hubs.
   *
   * @param numberOfHubs number of connection hubs
   */
  public void setNumberOfHubs(int numberOfHubs) {
    this.numberOfHubs = numberOfHubs;
  }

  /**
   * Check if has isolation valve.
   *
   * @return true if has isolation valve
   */
  public boolean hasIsolationValve() {
    return hasIsolationValve;
  }

  /**
   * Set whether has isolation valve.
   *
   * @param hasIsolationValve true to include isolation valve
   */
  public void setHasIsolationValve(boolean hasIsolationValve) {
    this.hasIsolationValve = hasIsolationValve;
    if (hasIsolationValve && isolationValve == null && inStream != null) {
      initializeIsolationValve();
    }
  }

  /**
   * Get valve type.
   *
   * @return valve type
   */
  public String getValveType() {
    return valveType;
  }

  /**
   * Set valve type.
   *
   * @param valveType valve type (Ball, Gate)
   */
  public void setValveType(String valveType) {
    this.valveType = valveType;
  }

  /**
   * Get actuator type.
   *
   * @return actuator type
   */
  public String getActuatorType() {
    return actuatorType;
  }

  /**
   * Set actuator type.
   *
   * @param actuatorType actuator type (ROV, Hydraulic)
   */
  public void setActuatorType(String actuatorType) {
    this.actuatorType = actuatorType;
  }

  /**
   * Check if has pigging facility.
   *
   * @return true if has pigging facility
   */
  public boolean hasPiggingFacility() {
    return hasPiggingFacility;
  }

  /**
   * Set whether has pigging facility.
   *
   * @param hasPiggingFacility true to include pigging
   */
  public void setHasPiggingFacility(boolean hasPiggingFacility) {
    this.hasPiggingFacility = hasPiggingFacility;
  }

  /**
   * Get pigging type.
   *
   * @return pigging type
   */
  public String getPiggingType() {
    return piggingType;
  }

  /**
   * Set pigging type.
   *
   * @param piggingType pigging type (launcher, receiver, bidirectional)
   */
  public void setPiggingType(String piggingType) {
    this.piggingType = piggingType;
  }

  /**
   * Check if has future tie-in capability.
   *
   * @return true if configured for future tie-in
   */
  public boolean hasFutureTieIn() {
    return hasFutureTieIn;
  }

  /**
   * Set whether has future tie-in capability.
   *
   * @param hasFutureTieIn true for future tie-in capability
   */
  public void setHasFutureTieIn(boolean hasFutureTieIn) {
    this.hasFutureTieIn = hasFutureTieIn;
  }

  /**
   * Get spare hubs.
   *
   * @return number of spare hubs
   */
  public int getSpareHubs() {
    return spareHubs;
  }

  /**
   * Set spare hubs.
   *
   * @param spareHubs number of spare hubs for future connections
   */
  public void setSpareHubs(int spareHubs) {
    this.spareHubs = spareHubs;
  }

  /**
   * Get structure length.
   *
   * @return structure length in meters
   */
  public double getStructureLength() {
    return structureLength;
  }

  /**
   * Set structure length.
   *
   * @param structureLength structure length in meters
   */
  public void setStructureLength(double structureLength) {
    this.structureLength = structureLength;
  }

  /**
   * Get structure width.
   *
   * @return structure width in meters
   */
  public double getStructureWidth() {
    return structureWidth;
  }

  /**
   * Set structure width.
   *
   * @param structureWidth structure width in meters
   */
  public void setStructureWidth(double structureWidth) {
    this.structureWidth = structureWidth;
  }

  /**
   * Get structure height.
   *
   * @return structure height in meters
   */
  public double getStructureHeight() {
    return structureHeight;
  }

  /**
   * Set structure height.
   *
   * @param structureHeight structure height in meters
   */
  public void setStructureHeight(double structureHeight) {
    this.structureHeight = structureHeight;
  }

  /**
   * Get dry weight.
   *
   * @return dry weight in tonnes
   */
  public double getDryWeight() {
    return dryWeight;
  }

  /**
   * Set dry weight.
   *
   * @param dryWeight dry weight in tonnes
   */
  public void setDryWeight(double dryWeight) {
    this.dryWeight = dryWeight;
  }

  /**
   * Get submerged weight.
   *
   * @return submerged weight in tonnes
   */
  public double getSubmergedWeight() {
    return submergedWeight;
  }

  /**
   * Set submerged weight.
   *
   * @param submergedWeight submerged weight in tonnes
   */
  public void setSubmergedWeight(double submergedWeight) {
    this.submergedWeight = submergedWeight;
  }

  /**
   * Get the internal isolation valve.
   *
   * @return isolation valve or null if not configured
   */
  public ThrottlingValve getIsolationValve() {
    return isolationValve;
  }
}
