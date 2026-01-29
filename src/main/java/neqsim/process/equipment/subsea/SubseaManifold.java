package neqsim.process.equipment.subsea;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.subsea.SubseaManifoldMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Subsea Production Manifold equipment class.
 *
 * <p>
 * A subsea manifold gathers production from multiple wells and routes it to flowlines for transport
 * to the host facility. Key features include:
 * </p>
 * <ul>
 * <li>Production headers for well gathering</li>
 * <li>Test headers for individual well testing</li>
 * <li>Service/utility headers for chemicals and hydraulics</li>
 * <li>Valve skids with production and isolation valves</li>
 * <li>Flowline connections to export pipelines</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API RP 17G - Recommended Practice for Completion/Workover Risers</li>
 * <li>API RP 17N - Subsea Production System Reliability</li>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems</li>
 * <li>NORSOK U-001 - Subsea Production Systems</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create 6-slot subsea manifold
 * SubseaManifold manifold = new SubseaManifold("Manifold-A", 6);
 * manifold.setWaterDepth(380.0);
 * manifold.setDesignPressure(350.0);
 * manifold.setHasTestHeader(true);
 *
 * // Add well streams
 * manifold.addWellStream(well1Stream);
 * manifold.addWellStream(well2Stream);
 * manifold.addWellStream(well3Stream);
 *
 * // Run manifold
 * manifold.run();
 *
 * // Get production and test streams
 * StreamInterface productionStream = manifold.getProductionStream();
 * StreamInterface testStream = manifold.getTestStream();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaManifoldMechanicalDesign
 * @see PLEM
 * @see PLET
 */
public class SubseaManifold extends ProcessEquipmentBaseClass {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Manifold type configuration.
   */
  public enum ManifoldType {
    /** Production manifold only. */
    PRODUCTION_ONLY("Production Only"),
    /** Production with test header. */
    PRODUCTION_TEST("Production + Test"),
    /** Full manifold with injection capability. */
    FULL_SERVICE("Full Service"),
    /** Injection manifold. */
    INJECTION("Injection");

    private final String displayName;

    ManifoldType(String displayName) {
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
   * Valve skid configuration for each slot.
   */
  public static class ValveSkid {
    /** Slot number. */
    private int slotNumber;
    /** Well name connected to slot. */
    private String wellName;
    /** Production wing valve. */
    private ThrottlingValve productionValve;
    /** Test wing valve. */
    private ThrottlingValve testValve;
    /** Crossover valve. */
    private ThrottlingValve crossoverValve;
    /** Connected stream. */
    private StreamInterface connectedStream;
    /** Whether slot is active (well connected). */
    private boolean active = false;
    /** Routing (production or test). */
    private String routing = "production";

    /**
     * Constructor.
     *
     * @param slotNumber slot number
     */
    public ValveSkid(int slotNumber) {
      this.slotNumber = slotNumber;
    }

    // Getters and setters
    public int getSlotNumber() {
      return slotNumber;
    }

    public String getWellName() {
      return wellName;
    }

    public void setWellName(String wellName) {
      this.wellName = wellName;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    public String getRouting() {
      return routing;
    }

    public void setRouting(String routing) {
      this.routing = routing;
    }

    public StreamInterface getConnectedStream() {
      return connectedStream;
    }

    public void setConnectedStream(StreamInterface stream) {
      this.connectedStream = stream;
    }

    public ThrottlingValve getProductionValve() {
      return productionValve;
    }

    public void setProductionValve(ThrottlingValve valve) {
      this.productionValve = valve;
    }

    public ThrottlingValve getTestValve() {
      return testValve;
    }

    public void setTestValve(ThrottlingValve valve) {
      this.testValve = valve;
    }
  }

  // ============ Configuration ============
  /** Water depth in meters. */
  private double waterDepth = 350.0;

  /** Design pressure in bara. */
  private double designPressure = 350.0;

  /** Design temperature in Celsius. */
  private double designTemperature = 120.0;

  /** Number of well slots. */
  private int numberOfSlots = 6;

  /** Manifold type. */
  private ManifoldType manifoldType = ManifoldType.PRODUCTION_TEST;

  /** Production header size in inches. */
  private double productionHeaderSizeInches = 12.0;

  /** Test header size in inches. */
  private double testHeaderSizeInches = 8.0;

  /** Branch/jumper connection size in inches. */
  private double branchSizeInches = 6.0;

  // ============ Headers ============
  /** Whether has test header. */
  private boolean hasTestHeader = true;

  /** Whether has service/utility header. */
  private boolean hasServiceHeader = false;

  /** Whether has injection header. */
  private boolean hasInjectionHeader = false;

  // ============ Valve Skids ============
  /** Valve skids for each slot. */
  private List<ValveSkid> valveSkids = new ArrayList<ValveSkid>();

  /** Map of well names to slot numbers. */
  private Map<String, Integer> wellSlotMap = new HashMap<String, Integer>();

  // ============ Internal Mixers ============
  /** Production header mixer. */
  private Mixer productionMixer;

  /** Test header mixer. */
  private Mixer testMixer;

  // ============ Output Streams ============
  /** Production outlet stream. */
  private StreamInterface productionStream;

  /** Test outlet stream. */
  private StreamInterface testStream;

  // ============ Structure ============
  /** Structure length in meters. */
  private double structureLength = 25.0;

  /** Structure width in meters. */
  private double structureWidth = 12.0;

  /** Structure height in meters. */
  private double structureHeight = 6.0;

  /** Dry weight in tonnes. */
  private double dryWeight = 250.0;

  /** Submerged weight in tonnes. */
  private double submergedWeight = 210.0;

  /** Foundation type. */
  private String foundationType = "Gravity Base";

  /** Mechanical design instance. */
  private SubseaManifoldMechanicalDesign mechanicalDesign;

  /**
   * Default constructor.
   */
  public SubseaManifold() {
    super("Subsea Manifold");
    initializeManifold();
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public SubseaManifold(String name) {
    super(name);
    initializeManifold();
  }

  /**
   * Constructor with name and number of slots.
   *
   * @param name equipment name
   * @param numberOfSlots number of well slots
   */
  public SubseaManifold(String name, int numberOfSlots) {
    super(name);
    this.numberOfSlots = numberOfSlots;
    initializeManifold();
  }

  /**
   * Initialize manifold components.
   */
  private void initializeManifold() {
    // Create valve skids for each slot
    valveSkids.clear();
    for (int i = 0; i < numberOfSlots; i++) {
      ValveSkid skid = new ValveSkid(i + 1);
      valveSkids.add(skid);
    }

    // Create internal mixers
    productionMixer = new Mixer(getName() + " Production Header");
    testMixer = new Mixer(getName() + " Test Header");
  }

  /**
   * Add well stream to next available slot.
   *
   * @param wellStream well inlet stream
   * @return slot number assigned
   */
  public int addWellStream(StreamInterface wellStream) {
    return addWellStream(wellStream, wellStream.getName());
  }

  /**
   * Add well stream with well name.
   *
   * @param wellStream well inlet stream
   * @param wellName well name
   * @return slot number assigned
   */
  public int addWellStream(StreamInterface wellStream, String wellName) {
    // Find next available slot
    for (ValveSkid skid : valveSkids) {
      if (!skid.isActive()) {
        skid.setWellName(wellName);
        skid.setConnectedStream(wellStream);
        skid.setActive(true);

        // Create production valve
        ThrottlingValve prodValve =
            new ThrottlingValve(getName() + " Slot " + skid.getSlotNumber() + " Prod", wellStream);
        prodValve.setPercentValveOpening(100.0);
        skid.setProductionValve(prodValve);

        // Create test valve if test header exists
        if (hasTestHeader) {
          ThrottlingValve testValve = new ThrottlingValve(
              getName() + " Slot " + skid.getSlotNumber() + " Test", wellStream);
          testValve.setPercentValveOpening(0.0); // Closed by default
          skid.setTestValve(testValve);
        }

        // Add to mixers based on routing
        if ("production".equals(skid.getRouting())) {
          productionMixer.addStream(wellStream);
        } else if ("test".equals(skid.getRouting()) && hasTestHeader) {
          testMixer.addStream(wellStream);
        }

        wellSlotMap.put(wellName, skid.getSlotNumber());
        return skid.getSlotNumber();
      }
    }
    throw new IllegalStateException("No available slots in manifold");
  }

  /**
   * Route well to production header.
   *
   * @param wellName well name
   */
  public void routeToProduction(String wellName) {
    Integer slotNum = wellSlotMap.get(wellName);
    if (slotNum != null) {
      ValveSkid skid = valveSkids.get(slotNum - 1);
      skid.setRouting("production");
      if (skid.getProductionValve() != null) {
        skid.getProductionValve().setPercentValveOpening(100.0);
      }
      if (skid.getTestValve() != null) {
        skid.getTestValve().setPercentValveOpening(0.0);
      }
    }
  }

  /**
   * Route well to test header.
   *
   * @param wellName well name
   */
  public void routeToTest(String wellName) {
    if (!hasTestHeader) {
      throw new IllegalStateException("Manifold does not have test header");
    }
    Integer slotNum = wellSlotMap.get(wellName);
    if (slotNum != null) {
      ValveSkid skid = valveSkids.get(slotNum - 1);
      skid.setRouting("test");
      if (skid.getProductionValve() != null) {
        skid.getProductionValve().setPercentValveOpening(0.0);
      }
      if (skid.getTestValve() != null) {
        skid.getTestValve().setPercentValveOpening(100.0);
      }
    }
  }

  /**
   * Shut in a well.
   *
   * @param wellName well name
   */
  public void shutInWell(String wellName) {
    Integer slotNum = wellSlotMap.get(wellName);
    if (slotNum != null) {
      ValveSkid skid = valveSkids.get(slotNum - 1);
      if (skid.getProductionValve() != null) {
        skid.getProductionValve().setPercentValveOpening(0.0);
      }
      if (skid.getTestValve() != null) {
        skid.getTestValve().setPercentValveOpening(0.0);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Rebuild mixers based on current routing
    productionMixer = new Mixer(getName() + " Production Header");
    testMixer = new Mixer(getName() + " Test Header");

    for (ValveSkid skid : valveSkids) {
      if (skid.isActive() && skid.getConnectedStream() != null) {
        if ("production".equals(skid.getRouting())) {
          // Run through production valve
          if (skid.getProductionValve() != null
              && skid.getProductionValve().getPercentValveOpening() > 0) {
            skid.getProductionValve().setInletStream(skid.getConnectedStream());
            skid.getProductionValve().run(id);
            productionMixer.addStream(skid.getProductionValve().getOutletStream());
          }
        } else if ("test".equals(skid.getRouting()) && hasTestHeader) {
          // Run through test valve
          if (skid.getTestValve() != null && skid.getTestValve().getPercentValveOpening() > 0) {
            skid.getTestValve().setInletStream(skid.getConnectedStream());
            skid.getTestValve().run(id);
            testMixer.addStream(skid.getTestValve().getOutletStream());
          }
        }
      }
    }

    // Run production header
    if (productionMixer.getNumberOfInputStreams() > 0) {
      productionMixer.run(id);
      productionStream = productionMixer.getOutletStream();

      // Apply header pressure drop
      double prodDrop = calculateHeaderPressureDrop(productionHeaderSizeInches);
      applyPressureDrop(productionStream, prodDrop);
    }

    // Run test header
    if (hasTestHeader && testMixer.getNumberOfInputStreams() > 0) {
      testMixer.run(id);
      testStream = testMixer.getOutletStream();

      // Apply header pressure drop
      double testDrop = calculateHeaderPressureDrop(testHeaderSizeInches);
      applyPressureDrop(testStream, testDrop);
    }

    setCalculationIdentifier(id);
  }

  /**
   * Calculate header pressure drop.
   *
   * @param headerSizeInches header size in inches
   * @return pressure drop in bar
   */
  private double calculateHeaderPressureDrop(double headerSizeInches) {
    // Simplified calculation
    double baseDrop = 0.3;
    // Smaller headers have higher pressure drop
    baseDrop *= (12.0 / headerSizeInches);
    return baseDrop;
  }

  /**
   * Apply pressure drop to stream.
   *
   * @param stream stream to modify
   * @param pressureDrop pressure drop in bar
   */
  private void applyPressureDrop(StreamInterface stream, double pressureDrop) {
    if (stream != null && stream.getFluid() != null) {
      SystemInterface fluid = stream.getFluid();
      double newPressure = fluid.getPressure() - pressureDrop;
      if (newPressure > 0) {
        fluid.setPressure(newPressure);
      }
    }
  }

  /**
   * Get production stream.
   *
   * @return production outlet stream
   */
  public StreamInterface getProductionStream() {
    return productionStream;
  }

  /**
   * Get test stream.
   *
   * @return test outlet stream
   */
  public StreamInterface getTestStream() {
    return testStream;
  }

  /**
   * Get number of active wells.
   *
   * @return count of active wells
   */
  public int getActiveWellCount() {
    int count = 0;
    for (ValveSkid skid : valveSkids) {
      if (skid.isActive()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get mechanical design.
   *
   * @return mechanical design instance
   */
  public SubseaManifoldMechanicalDesign getMechanicalDesign() {
    if (mechanicalDesign == null) {
      mechanicalDesign = new SubseaManifoldMechanicalDesign(this);
    }
    return mechanicalDesign;
  }

  /**
   * Initialize mechanical design.
   */
  public void initMechanicalDesign() {
    mechanicalDesign = new SubseaManifoldMechanicalDesign(this);
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
   * @return number of well slots
   */
  public int getNumberOfSlots() {
    return numberOfSlots;
  }

  /**
   * Set number of slots.
   *
   * @param numberOfSlots number of well slots
   */
  public void setNumberOfSlots(int numberOfSlots) {
    this.numberOfSlots = numberOfSlots;
    initializeManifold();
  }

  /**
   * Get manifold type.
   *
   * @return manifold type
   */
  public ManifoldType getManifoldType() {
    return manifoldType;
  }

  /**
   * Set manifold type.
   *
   * @param manifoldType manifold type
   */
  public void setManifoldType(ManifoldType manifoldType) {
    this.manifoldType = manifoldType;
  }

  /**
   * Get production header size.
   *
   * @return production header size in inches
   */
  public double getProductionHeaderSizeInches() {
    return productionHeaderSizeInches;
  }

  /**
   * Set production header size.
   *
   * @param productionHeaderSizeInches production header size in inches
   */
  public void setProductionHeaderSizeInches(double productionHeaderSizeInches) {
    this.productionHeaderSizeInches = productionHeaderSizeInches;
  }

  /**
   * Get test header size.
   *
   * @return test header size in inches
   */
  public double getTestHeaderSizeInches() {
    return testHeaderSizeInches;
  }

  /**
   * Set test header size.
   *
   * @param testHeaderSizeInches test header size in inches
   */
  public void setTestHeaderSizeInches(double testHeaderSizeInches) {
    this.testHeaderSizeInches = testHeaderSizeInches;
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
   * Check if has test header.
   *
   * @return true if has test header
   */
  public boolean hasTestHeader() {
    return hasTestHeader;
  }

  /**
   * Set whether has test header.
   *
   * @param hasTestHeader true to include test header
   */
  public void setHasTestHeader(boolean hasTestHeader) {
    this.hasTestHeader = hasTestHeader;
  }

  /**
   * Check if has service header.
   *
   * @return true if has service header
   */
  public boolean hasServiceHeader() {
    return hasServiceHeader;
  }

  /**
   * Set whether has service header.
   *
   * @param hasServiceHeader true to include service header
   */
  public void setHasServiceHeader(boolean hasServiceHeader) {
    this.hasServiceHeader = hasServiceHeader;
  }

  /**
   * Check if has injection header.
   *
   * @return true if has injection header
   */
  public boolean hasInjectionHeader() {
    return hasInjectionHeader;
  }

  /**
   * Set whether has injection header.
   *
   * @param hasInjectionHeader true to include injection header
   */
  public void setHasInjectionHeader(boolean hasInjectionHeader) {
    this.hasInjectionHeader = hasInjectionHeader;
  }

  /**
   * Get valve skids.
   *
   * @return list of valve skids
   */
  public List<ValveSkid> getValveSkids() {
    return valveSkids;
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
}
