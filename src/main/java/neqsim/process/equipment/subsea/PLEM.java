package neqsim.process.equipment.subsea;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.subsea.PLEMMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Pipeline End Manifold (PLEM) equipment class.
 *
 * <p>
 * A PLEM is a subsea structure that provides multiple pipeline terminations and connections,
 * typically used to:
 * </p>
 * <ul>
 * <li>Connect multiple flowlines to a single export line</li>
 * <li>Provide manifolding for multiple wells</li>
 * <li>Enable pipeline crossings with valved connections</li>
 * <li>Allow routing changes between pipelines</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API RP 17G - Subsea Production System Design</li>
 * <li>API RP 17N - Subsea Production System Reliability</li>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems</li>
 * <li>NORSOK U-001 - Subsea Production Systems</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create PLEM for 4-slot manifold
 * PLEM plem = new PLEM("PLEM-A", 4);
 * plem.setWaterDepth(450.0);
 * plem.setDesignPressure(200.0);
 * plem.addInletStream(well1Stream);
 * plem.addInletStream(well2Stream);
 * plem.run();
 *
 * Stream comingledStream = plem.getOutletStream();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see PLEMMechanicalDesign
 * @see PLET
 * @see SubseaManifold
 */
public class PLEM extends ProcessEquipmentBaseClass {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * PLEM configuration type.
   */
  public enum ConfigurationType {
    /** Simple through-flow PLEM. */
    THROUGH_FLOW("Through Flow"),
    /** Mixing/commingling PLEM. */
    COMMINGLING("Commingling"),
    /** Distribution PLEM. */
    DISTRIBUTION("Distribution"),
    /** Crossover PLEM for pipeline routing. */
    CROSSOVER("Crossover");

    private final String displayName;

    ConfigurationType(String displayName) {
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

  /** Number of connection slots. */
  private int numberOfSlots = 4;

  /** Configuration type. */
  private ConfigurationType configurationType = ConfigurationType.COMMINGLING;

  /** Header pipe size in inches. */
  private double headerSizeInches = 12.0;

  /** Branch pipe size in inches. */
  private double branchSizeInches = 8.0;

  // ============ Valves ============
  /** Whether each branch has isolation valve. */
  private boolean branchIsolationValves = true;

  /** Internal branch isolation valves. */
  private List<ThrottlingValve> branchValves = new ArrayList<ThrottlingValve>();

  /** Whether header has isolation valves. */
  private boolean headerIsolationValves = true;

  // ============ Streams ============
  /** Inlet streams. */
  private List<StreamInterface> inletStreams = new ArrayList<StreamInterface>();

  /** Outlet stream. */
  private StreamInterface outletStream;

  /** Internal mixer for commingling. */
  private Mixer internalMixer;

  /** Internal splitter for distribution. */
  private Splitter internalSplitter;

  // ============ Structure ============
  /** Structure length in meters. */
  private double structureLength = 12.0;

  /** Structure width in meters. */
  private double structureWidth = 8.0;

  /** Structure height in meters. */
  private double structureHeight = 4.0;

  /** Dry weight in tonnes. */
  private double dryWeight = 80.0;

  /** Submerged weight in tonnes. */
  private double submergedWeight = 68.0;

  /** Foundation type (gravity, piled, suction). */
  private String foundationType = "Gravity Base";

  /** Mechanical design instance. */
  private PLEMMechanicalDesign mechanicalDesign;

  /**
   * Default constructor.
   */
  public PLEM() {
    super("PLEM");
    initializeInternalEquipment();
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public PLEM(String name) {
    super(name);
    initializeInternalEquipment();
  }

  /**
   * Constructor with name and number of slots.
   *
   * @param name equipment name
   * @param numberOfSlots number of connection slots
   */
  public PLEM(String name, int numberOfSlots) {
    super(name);
    this.numberOfSlots = numberOfSlots;
    initializeInternalEquipment();
  }

  /**
   * Initialize internal mixing/splitting equipment.
   */
  private void initializeInternalEquipment() {
    internalMixer = new Mixer(getName() + " Internal Mixer");
    internalSplitter = new Splitter(getName() + " Internal Splitter");
  }

  /**
   * Add inlet stream to PLEM.
   *
   * @param stream inlet stream to add
   */
  public void addInletStream(StreamInterface stream) {
    if (inletStreams.size() >= numberOfSlots) {
      throw new IllegalStateException(
          "Cannot add more streams than available slots (" + numberOfSlots + ")");
    }
    inletStreams.add(stream);

    // Add corresponding isolation valve
    if (branchIsolationValves) {
      ThrottlingValve valve =
          new ThrottlingValve(getName() + " Branch Valve " + inletStreams.size(), stream);
      valve.setPercentValveOpening(100.0);
      branchValves.add(valve);
    }

    // Add to internal mixer
    internalMixer.addStream(stream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inletStreams.isEmpty()) {
      return;
    }

    // Run branch valves if present
    if (branchIsolationValves) {
      for (int i = 0; i < branchValves.size(); i++) {
        ThrottlingValve valve = branchValves.get(i);
        valve.setInletStream(inletStreams.get(i));
        valve.run(id);
        // Update mixer with valve outlet
        // Note: mixer already has original streams, we'd need to update for valved flow
      }
    }

    // Run mixing operation
    internalMixer.run(id);

    // Get mixed stream
    outletStream = internalMixer.getOutletStream();

    // Apply header pressure drop
    double headerPressureDrop = calculateHeaderPressureDrop();
    if (headerPressureDrop > 0 && outletStream != null) {
      SystemInterface fluid = outletStream.getFluid().clone();
      double outPressure = fluid.getPressure() - headerPressureDrop;
      if (outPressure > 0) {
        fluid.setPressure(outPressure);
        outletStream.setFluid(fluid);
      }
    }

    setCalculationIdentifier(id);
  }

  /**
   * Calculate pressure drop through header.
   *
   * @return pressure drop in bar
   */
  private double calculateHeaderPressureDrop() {
    // Simplified pressure drop calculation
    // In reality would use detailed hydraulics
    double baseDrop = 0.2; // Base header drop
    baseDrop += inletStreams.size() * 0.05; // Additional per branch
    return baseDrop;
  }

  /**
   * Set branch valve opening.
   *
   * @param branchIndex branch index (0-based)
   * @param percentOpen valve opening percentage (0-100)
   */
  public void setBranchValveOpening(int branchIndex, double percentOpen) {
    if (branchIndex >= 0 && branchIndex < branchValves.size()) {
      branchValves.get(branchIndex).setPercentValveOpening(percentOpen);
    }
  }

  /**
   * Close a branch valve.
   *
   * @param branchIndex branch index (0-based)
   */
  public void closeBranch(int branchIndex) {
    setBranchValveOpening(branchIndex, 0.0);
  }

  /**
   * Open a branch valve.
   *
   * @param branchIndex branch index (0-based)
   */
  public void openBranch(int branchIndex) {
    setBranchValveOpening(branchIndex, 100.0);
  }

  /**
   * Get outlet stream.
   *
   * @return outlet stream
   */
  public StreamInterface getOutletStream() {
    return outletStream;
  }

  /**
   * Get mechanical design.
   *
   * @return mechanical design instance
   */
  public PLEMMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new PLEMMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new PLEMMechanicalDesign(this);
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
   * Get number of slots.
   *
   * @return number of connection slots
   */
  public int getNumberOfSlots() {
    return numberOfSlots;
  }

  /**
   * Set number of slots.
   *
   * @param numberOfSlots number of connection slots
   */
  public void setNumberOfSlots(int numberOfSlots) {
    this.numberOfSlots = numberOfSlots;
  }

  /**
   * Get configuration type.
   *
   * @return configuration type
   */
  public ConfigurationType getConfigurationType() {
    return configurationType;
  }

  /**
   * Set configuration type.
   *
   * @param configurationType configuration type
   */
  public void setConfigurationType(ConfigurationType configurationType) {
    this.configurationType = configurationType;
  }

  /**
   * Get header size.
   *
   * @return header size in inches
   */
  public double getHeaderSizeInches() {
    return headerSizeInches;
  }

  /**
   * Set header size.
   *
   * @param headerSizeInches header size in inches
   */
  public void setHeaderSizeInches(double headerSizeInches) {
    this.headerSizeInches = headerSizeInches;
  }

  /**
   * Get branch size.
   *
   * @return branch size in inches
   */
  public double getBranchSizeInches() {
    return branchSizeInches;
  }

  /**
   * Set branch size.
   *
   * @param branchSizeInches branch size in inches
   */
  public void setBranchSizeInches(double branchSizeInches) {
    this.branchSizeInches = branchSizeInches;
  }

  /**
   * Check if branches have isolation valves.
   *
   * @return true if branches have isolation valves
   */
  public boolean hasBranchIsolationValves() {
    return branchIsolationValves;
  }

  /**
   * Set whether branches have isolation valves.
   *
   * @param branchIsolationValves true to include branch isolation valves
   */
  public void setBranchIsolationValves(boolean branchIsolationValves) {
    this.branchIsolationValves = branchIsolationValves;
  }

  /**
   * Check if header has isolation valves.
   *
   * @return true if header has isolation valves
   */
  public boolean hasHeaderIsolationValves() {
    return headerIsolationValves;
  }

  /**
   * Set whether header has isolation valves.
   *
   * @param headerIsolationValves true to include header isolation valves
   */
  public void setHeaderIsolationValves(boolean headerIsolationValves) {
    this.headerIsolationValves = headerIsolationValves;
  }

  /**
   * Get inlet streams.
   *
   * @return list of inlet streams
   */
  public List<StreamInterface> getInletStreams() {
    return inletStreams;
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
   * Get foundation type.
   *
   * @return foundation type
   */
  public String getFoundationType() {
    return foundationType;
  }

  /**
   * Set foundation type.
   *
   * @param foundationType foundation type (Gravity Base, Piled, Suction Anchor)
   */
  public void setFoundationType(String foundationType) {
    this.foundationType = foundationType;
  }

  /**
   * Get branch valves.
   *
   * @return list of branch isolation valves
   */
  public List<ThrottlingValve> getBranchValves() {
    return branchValves;
  }
}
