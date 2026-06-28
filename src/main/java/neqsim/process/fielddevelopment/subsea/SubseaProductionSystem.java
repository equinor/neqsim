package neqsim.process.fielddevelopment.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.costestimation.CostEstimateBasis;
import neqsim.process.costestimation.CostEstimateResult;
import neqsim.process.costestimation.EstimateClass;
import neqsim.process.costestimation.MaterialTakeOffItem;
import neqsim.process.equipment.network.WellFlowlineNetwork;
import neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.subsea.FlexiblePipe;
import neqsim.process.equipment.subsea.PLEM;
import neqsim.process.equipment.subsea.PLET;
import neqsim.process.equipment.subsea.SimpleFlowLine;
import neqsim.process.equipment.subsea.SubseaJumper;
import neqsim.process.equipment.subsea.SubseaManifold;
import neqsim.process.equipment.subsea.SubseaTree;
import neqsim.process.equipment.subsea.SubseaWell;
import neqsim.process.equipment.subsea.Umbilical;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.TiebackAnalyzer;
import neqsim.process.fielddevelopment.tieback.TiebackOption;
import neqsim.process.mechanicaldesign.subsea.SURFCostEstimator;
import neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator;
import neqsim.process.mechanicaldesign.subsea.WellCostEstimator;
import neqsim.process.mechanicaldesign.subsea.WellCostEstimator.WellLocationType;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Unified subsea production system for field development workflow integration.
 *
 * <p>
 * This class provides a high-level abstraction for modeling subsea production systems, integrating multiple NeqSim
 * components:
 * </p>
 * <ul>
 * <li>{@link SubseaWell} - Well tubing from reservoir to seabed</li>
 * <li>{@link SimpleFlowLine} - Flowline from well to manifold/host</li>
 * <li>{@link WellFlowlineNetwork} - Multi-well gathering network with manifolds</li>
 * <li>{@link TiebackAnalyzer} - Cost estimation for subsea infrastructure</li>
 * </ul>
 *
 * <h2>Subsea Architecture Types</h2>
 * <ul>
 * <li><b>DIRECT_TIEBACK</b> - Wells tied directly to host (short distances, &lt;10km)</li>
 * <li><b>MANIFOLD_CLUSTER</b> - Wells grouped at subsea manifold then tied back</li>
 * <li><b>DAISY_CHAIN</b> - Wells connected in series along flowline</li>
 * <li><b>TEMPLATE</b> - Multiple wells from single seabed template</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Create subsea system
 * SubseaProductionSystem subsea = new SubseaProductionSystem("Snohvit Satellite");
 * subsea.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER);
 * subsea.setWaterDepthM(350.0);
 * subsea.setTiebackDistanceKm(25.0);
 *
 * // Configure wells
 * subsea.setWellCount(4);
 * subsea.setWellheadPressureBara(180.0);
 * subsea.setWellheadTemperatureC(80.0);
 *
 * // Set fluid
 * subsea.setReservoirFluid(gasCondensateFluid);
 *
 * // Configure flowline
 * subsea.setFlowlineDiameterInches(12.0);
 * subsea.setSeabedTemperatureC(4.0);
 *
 * // Define host facility
 * HostFacility host = HostFacility.builder("Snohvit LNG").location(71.3, 20.8).waterDepth(350)
 *     .gasCapacity(20.0, "MSm3/d").build();
 * subsea.setHostFacility(host);
 *
 * // Build and run
 * subsea.build();
 * subsea.run();
 *
 * // Get results
 * double arrivalPressure = subsea.getArrivalPressureBara();
 * double arrivalTemperature = subsea.getArrivalTemperatureC();
 * SubseaSystemResult result = subsea.getResult();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaWell
 * @see SimpleFlowLine
 * @see WellFlowlineNetwork
 * @see TiebackAnalyzer
 */
public class SubseaProductionSystem implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Subsea architecture configuration.
   */
  public enum SubseaArchitecture {
    /** Wells tied directly to host (no manifold). */
    DIRECT_TIEBACK,
    /** Wells grouped at subsea manifold before tieback. */
    MANIFOLD_CLUSTER,
    /** Wells connected in series (daisy chain). */
    DAISY_CHAIN,
    /** Multiple wells from single seabed template. */
    TEMPLATE
  }

  // ============================================================================
  // CONFIGURATION
  // ============================================================================

  private String name;
  private SubseaArchitecture architecture = SubseaArchitecture.MANIFOLD_CLUSTER;

  // Field parameters
  private double waterDepthM = 350.0;
  private double tiebackDistanceKm = 25.0;
  private double discoveryLatitude = 60.0;
  private double discoveryLongitude = 3.0;

  // Well parameters
  private int wellCount = 4;
  private double reservoirPressureBara = 350.0;
  private double reservoirTemperatureC = 100.0;
  private double wellheadPressureBara = 180.0;
  private double wellheadTemperatureC = 80.0;
  private double ratePerWellSm3d = 1.0e6;
  private double wellDepthM = 3000.0;
  private double tubingDiameterInches = 6.0;
  private WellLocationType wellLocationType = WellLocationType.SUBSEA_WET_TREE;
  private SubseaWell.RigType rigType = SubseaWell.RigType.SEMI_SUBMERSIBLE;
  private SubseaWell.CompletionType completionType = SubseaWell.CompletionType.CASED_PERFORATED;
  private SubseaCostEstimator.Region costRegion = SubseaCostEstimator.Region.NORWAY;
  private double reservoirDevelopmentCostMusd = Double.NaN;

  // Manifold parameters
  private int manifoldCount = 1;
  private double manifoldPressureDropBara = 2.0;

  // Flowline parameters
  private double flowlineDiameterInches = 12.0;
  private double flowlineWallThicknessMm = 25.0;
  private double seabedTemperatureC = 4.0;
  private double insulationThicknessMm = 50.0;
  private String flowlineMaterial = "Carbon Steel"; // CRA, Flexible, etc.

  // Umbilical parameters
  private double umbilicalLengthKm; // Auto-calculated if not set

  // Riser parameters
  private boolean includeRisers = true;
  private boolean flexibleRiser = true;
  private int productionRiserCount = 1;
  private double riserDiameterInches = 0.0;

  // Host facility
  private HostFacility hostFacility;

  // Fluid
  private SystemInterface reservoirFluid;

  // ============================================================================
  // INTERNAL MODELS
  // ============================================================================

  private transient ProcessSystem subseaProcess;
  private transient List<SubseaWell> wells = new ArrayList<>();
  private transient List<SubseaTree> trees = new ArrayList<>();
  private transient List<SubseaJumper> jumpers = new ArrayList<>();
  private transient List<SubseaManifold> manifolds = new ArrayList<>();
  private transient List<PLET> plets = new ArrayList<>();
  private transient List<PLEM> plems = new ArrayList<>();
  private transient List<Umbilical> umbilicals = new ArrayList<>();
  private transient List<FlexiblePipe> risers = new ArrayList<>();
  private transient List<SimpleFlowLine> steelRisers = new ArrayList<>();
  private transient List<ThrottlingValve> subseaChokes = new ArrayList<>();
  private transient List<SimpleFlowLine> flowlines = new ArrayList<>();
  private transient WellFlowlineNetwork network;
  private transient TiebackAnalyzer tiebackAnalyzer;
  private transient TiebackOption tiebackOption;
  private transient SURFCostEstimator surfCostEstimator;

  // ============================================================================
  // RESULTS
  // ============================================================================

  private SubseaSystemResult result;
  private boolean hasRun = false;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new subsea production system.
   *
   * @param name system name
   */
  public SubseaProductionSystem(String name) {
    this.name = name;
    this.tiebackAnalyzer = new TiebackAnalyzer();
  }

  // ============================================================================
  // CONFIGURATION METHODS (Fluent API)
  // ============================================================================

  /**
   * Sets the subsea architecture type.
   *
   * @param arch architecture type
   * @return this for chaining
   */
  public SubseaProductionSystem setArchitecture(SubseaArchitecture arch) {
    this.architecture = arch;
    return this;
  }

  /**
   * Sets water depth.
   *
   * @param depthM water depth in meters
   * @return this for chaining
   */
  public SubseaProductionSystem setWaterDepthM(double depthM) {
    this.waterDepthM = depthM;
    return this;
  }

  /**
   * Sets tieback distance from discovery to host.
   *
   * @param distanceKm distance in kilometers
   * @return this for chaining
   */
  public SubseaProductionSystem setTiebackDistanceKm(double distanceKm) {
    this.tiebackDistanceKm = distanceKm;
    return this;
  }

  /**
   * Sets discovery location.
   *
   * @param latitude latitude in degrees
   * @param longitude longitude in degrees
   * @return this for chaining
   */
  public SubseaProductionSystem setDiscoveryLocation(double latitude, double longitude) {
    this.discoveryLatitude = latitude;
    this.discoveryLongitude = longitude;
    return this;
  }

  /**
   * Sets the number of production wells.
   *
   * @param count well count
   * @return this for chaining
   */
  public SubseaProductionSystem setWellCount(int count) {
    this.wellCount = count;
    return this;
  }

  /**
   * Sets reservoir conditions.
   *
   * @param pressureBara reservoir pressure in bara
   * @param temperatureC reservoir temperature in Celsius
   * @return this for chaining
   */
  public SubseaProductionSystem setReservoirConditions(double pressureBara, double temperatureC) {
    this.reservoirPressureBara = pressureBara;
    this.reservoirTemperatureC = temperatureC;
    return this;
  }

  /**
   * Sets wellhead conditions.
   *
   * @param pressureBara wellhead pressure in bara
   * @param temperatureC wellhead temperature in Celsius
   * @return this for chaining
   */
  public SubseaProductionSystem setWellheadConditions(double pressureBara, double temperatureC) {
    this.wellheadPressureBara = pressureBara;
    this.wellheadTemperatureC = temperatureC;
    return this;
  }

  /**
   * Sets production rate per well.
   *
   * @param rateSm3d rate in Sm3/day
   * @return this for chaining
   */
  public SubseaProductionSystem setRatePerWell(double rateSm3d) {
    this.ratePerWellSm3d = rateSm3d;
    return this;
  }

  /**
   * Sets well depth (measured depth).
   *
   * @param depthM well depth in meters
   * @return this for chaining
   */
  public SubseaProductionSystem setWellDepthM(double depthM) {
    this.wellDepthM = depthM;
    return this;
  }

  /**
   * Sets tubing diameter.
   *
   * @param diameterInches tubing ID in inches
   * @return this for chaining
   */
  public SubseaProductionSystem setTubingDiameterInches(double diameterInches) {
    this.tubingDiameterInches = diameterInches;
    return this;
  }

  /**
   * Sets well location and tree installation basis.
   *
   * @param locationType dry-tree or wet-tree location basis
   * @return this for chaining
   */
  public SubseaProductionSystem setWellLocationType(WellLocationType locationType) {
    this.wellLocationType = locationType == null ? WellLocationType.SUBSEA_WET_TREE : locationType;
    return this;
  }

  /**
   * Sets drilling rig type for generated wells.
   *
   * @param rigType drilling rig type
   * @return this for chaining
   */
  public SubseaProductionSystem setRigType(SubseaWell.RigType rigType) {
    this.rigType = rigType == null ? SubseaWell.RigType.SEMI_SUBMERSIBLE : rigType;
    return this;
  }

  /**
   * Sets completion type for generated wells.
   *
   * @param completionType completion type
   * @return this for chaining
   */
  public SubseaProductionSystem setCompletionType(SubseaWell.CompletionType completionType) {
    this.completionType = completionType == null ? SubseaWell.CompletionType.CASED_PERFORATED : completionType;
    return this;
  }

  /**
   * Sets cost estimation region for wells and SURF equipment.
   *
   * @param costRegion regional cost basis
   * @return this for chaining
   */
  public SubseaProductionSystem setCostRegion(SubseaCostEstimator.Region costRegion) {
    this.costRegion = costRegion == null ? SubseaCostEstimator.Region.NORWAY : costRegion;
    return this;
  }

  /**
   * Sets reservoir appraisal and reservoir-management CAPEX.
   *
   * @param costMusd reservoir development cost in MUSD
   * @return this for chaining
   */
  public SubseaProductionSystem setReservoirDevelopmentCostMusd(double costMusd) {
    this.reservoirDevelopmentCostMusd = Math.max(0.0, costMusd);
    return this;
  }

  /**
   * Sets flowline diameter.
   *
   * @param diameterInches flowline ID in inches
   * @return this for chaining
   */
  public SubseaProductionSystem setFlowlineDiameterInches(double diameterInches) {
    this.flowlineDiameterInches = diameterInches;
    return this;
  }

  /**
   * Sets flowline wall thickness.
   *
   * @param thicknessMm wall thickness in millimeters
   * @return this for chaining
   */
  public SubseaProductionSystem setFlowlineWallThicknessMm(double thicknessMm) {
    this.flowlineWallThicknessMm = thicknessMm;
    return this;
  }

  /**
   * Sets seabed temperature.
   *
   * @param temperatureC seabed temperature in Celsius
   * @return this for chaining
   */
  public SubseaProductionSystem setSeabedTemperatureC(double temperatureC) {
    this.seabedTemperatureC = temperatureC;
    return this;
  }

  /**
   * Sets flowline material.
   *
   * @param material material type (e.g., "Carbon Steel", "CRA", "Flexible")
   * @return this for chaining
   */
  public SubseaProductionSystem setFlowlineMaterial(String material) {
    this.flowlineMaterial = material;
    return this;
  }

  /**
   * Sets routed umbilical length.
   *
   * @param lengthKm umbilical length in km
   * @return this for chaining
   */
  public SubseaProductionSystem setUmbilicalLengthKm(double lengthKm) {
    this.umbilicalLengthKm = lengthKm;
    return this;
  }

  /**
   * Sets whether risers are included in the generated SURF system.
   *
   * @param includeRisers true to include production risers
   * @return this for chaining
   */
  public SubseaProductionSystem setIncludeRisers(boolean includeRisers) {
    this.includeRisers = includeRisers;
    return this;
  }

  /**
   * Sets whether generated risers are flexible risers.
   *
   * @param flexibleRiser true for flexible risers, false for rigid risers in the cost estimate
   * @return this for chaining
   */
  public SubseaProductionSystem setFlexibleRiser(boolean flexibleRiser) {
    this.flexibleRiser = flexibleRiser;
    return this;
  }

  /**
   * Sets number of production risers.
   *
   * @param productionRiserCount number of production risers
   * @return this for chaining
   */
  public SubseaProductionSystem setProductionRiserCount(int productionRiserCount) {
    this.productionRiserCount = Math.max(0, productionRiserCount);
    return this;
  }

  /**
   * Sets the host facility for tieback.
   *
   * @param host host facility
   * @return this for chaining
   */
  public SubseaProductionSystem setHostFacility(HostFacility host) {
    this.hostFacility = host;
    return this;
  }

  /**
   * Sets the reservoir fluid.
   *
   * @param fluid thermodynamic system
   * @return this for chaining
   */
  public SubseaProductionSystem setReservoirFluid(SystemInterface fluid) {
    this.reservoirFluid = fluid;
    return this;
  }

  /**
   * Sets the number of manifolds (for clustered architectures).
   *
   * @param count manifold count
   * @return this for chaining
   */
  public SubseaProductionSystem setManifoldCount(int count) {
    this.manifoldCount = count;
    return this;
  }

  // ============================================================================
  // BUILD METHODS
  // ============================================================================

  /**
   * Builds the subsea production system model.
   *
   * <p>
   * Creates the process equipment (wells, flowlines, manifolds) based on the configured architecture and parameters.
   * </p>
   *
   * @return this for chaining
   */
  public SubseaProductionSystem build() {
    validateConfiguration();

    subseaProcess = new ProcessSystem();
    wells = new ArrayList<>();
    trees = new ArrayList<>();
    jumpers = new ArrayList<>();
    manifolds = new ArrayList<>();
    plets = new ArrayList<>();
    plems = new ArrayList<>();
    umbilicals = new ArrayList<>();
    risers = new ArrayList<>();
    steelRisers = new ArrayList<>();
    subseaChokes = new ArrayList<>();
    flowlines = new ArrayList<>();

    switch (architecture) {
    case DIRECT_TIEBACK:
      buildDirectTieback();
      break;
    case MANIFOLD_CLUSTER:
      buildManifoldCluster();
      break;
    case DAISY_CHAIN:
      buildDaisyChain();
      break;
    case TEMPLATE:
      buildTemplate();
      break;
    }

    // Calculate umbilical length if not set
    if (umbilicalLengthKm <= 0) {
      umbilicalLengthKm = tiebackDistanceKm * 1.05; // 5% extra for routing
    }

    buildTerminations();
    buildControlsAndRisers();

    return this;
  }

  private void validateConfiguration() {
    if (reservoirFluid == null) {
      throw new IllegalStateException("Reservoir fluid must be set before building subsea system");
    }
    if (wellCount <= 0) {
      throw new IllegalStateException("Well count must be positive");
    }
    if (tiebackDistanceKm <= 0) {
      throw new IllegalStateException("Tieback distance must be positive");
    }
  }

  /**
   * Builds direct tieback architecture (wells to individual flowlines to host).
   */
  private void buildDirectTieback() {
    for (int i = 0; i < wellCount; i++) {
      String wellName = name + " Well " + (i + 1);

      // Create well stream
      SystemInterface wellFluid = reservoirFluid.clone();
      wellFluid.setTemperature(wellheadTemperatureC, "C");
      wellFluid.setPressure(wellheadPressureBara, "bara");

      Stream wellStream = new Stream(wellName + " stream", wellFluid);
      wellStream.setFlowRate(ratePerWellSm3d, "Sm3/day");

      // Create subsea well
      SubseaWell well = new SubseaWell(wellName, wellStream);
      configureTubing(well);
      wells.add(well);

      SubseaTree tree = createTree(wellName, well);

      // Create subsea choke
      ThrottlingValve choke = new ThrottlingValve(wellName + " choke", tree.getOutletStream());
      choke.setOutletPressure(wellheadPressureBara - 5.0, "bara"); // 5 bar choke dP
      subseaChokes.add(choke);

      SubseaJumper jumper = createJumper(wellName + " jumper", choke.getOutletStream());

      // Create flowline to host
      SimpleFlowLine flowline = new SimpleFlowLine(wellName + " flowline", jumper.getOutletStream());
      configureFlowline(flowline, tiebackDistanceKm);
      flowlines.add(flowline);

      // Add to process
      subseaProcess.add(wellStream);
      subseaProcess.add(well);
      subseaProcess.add(tree);
      subseaProcess.add(choke);
      subseaProcess.add(jumper);
      subseaProcess.add(flowline);
    }
  }

  /**
   * Builds manifold cluster architecture (wells to manifold to trunk flowline to host).
   */
  private void buildManifoldCluster() {
    // Calculate wells per manifold
    int wellsPerManifold = (int) Math.ceil((double) wellCount / manifoldCount);
    int wellIndex = 0;

    for (int m = 0; m < manifoldCount; m++) {
      SubseaManifold manifold = createManifold(name + " Manifold " + (m + 1), wellsPerManifold);
      manifolds.add(manifold);

      // Create wells for this manifold
      List<StreamInterface> manifoldInputs = new ArrayList<>();

      int wellsThisManifold = Math.min(wellsPerManifold, wellCount - wellIndex);
      for (int w = 0; w < wellsThisManifold; w++) {
        String wellName = name + " Well " + (wellIndex + 1);

        // Create well stream
        SystemInterface wellFluid = reservoirFluid.clone();
        wellFluid.setTemperature(wellheadTemperatureC, "C");
        wellFluid.setPressure(wellheadPressureBara, "bara");

        Stream wellStream = new Stream(wellName + " stream", wellFluid);
        wellStream.setFlowRate(ratePerWellSm3d, "Sm3/day");

        // Create subsea well
        SubseaWell well = new SubseaWell(wellName, wellStream);
        configureTubing(well);
        wells.add(well);

        SubseaTree tree = createTree(wellName, well);

        // Create subsea choke
        ThrottlingValve choke = new ThrottlingValve(wellName + " choke", tree.getOutletStream());
        choke.setOutletPressure(wellheadPressureBara - 5.0, "bara");
        subseaChokes.add(choke);

        SubseaJumper jumper = createJumper(wellName + " jumper", choke.getOutletStream());

        // Short infield line to manifold (typically 500m - 2km)
        double infieldLength = Math.min(2.0, tiebackDistanceKm * 0.1);
        SimpleFlowLine infieldLine = new SimpleFlowLine(wellName + " infield", jumper.getOutletStream());
        configureFlowline(infieldLine, infieldLength);

        manifoldInputs.add(infieldLine.getOutletStream());
        manifold.addWellStream(infieldLine.getOutletStream(), wellName);

        subseaProcess.add(wellStream);
        subseaProcess.add(well);
        subseaProcess.add(tree);
        subseaProcess.add(choke);
        subseaProcess.add(jumper);
        subseaProcess.add(infieldLine);

        wellIndex++;
      }

      subseaProcess.add(manifold);

      // Create trunk flowline from manifold to host
      // For simplicity, use first input stream as basis (would be mixed at manifold)
      if (!manifoldInputs.isEmpty()) {
        String trunkName = name + " Trunk M" + (m + 1);
        SimpleFlowLine trunkLine = new SimpleFlowLine(trunkName, manifoldInputs.get(0));
        configureFlowline(trunkLine, tiebackDistanceKm / manifoldCount);
        flowlines.add(trunkLine);
        subseaProcess.add(trunkLine);
      }
    }
  }

  /**
   * Builds daisy chain architecture (wells connected in series).
   */
  private void buildDaisyChain() {
    double segmentLength = tiebackDistanceKm / (wellCount + 1);
    StreamInterface previousOutput = null;

    for (int i = 0; i < wellCount; i++) {
      String wellName = name + " Well " + (i + 1);

      // Create well stream
      SystemInterface wellFluid = reservoirFluid.clone();
      wellFluid.setTemperature(wellheadTemperatureC, "C");
      wellFluid.setPressure(wellheadPressureBara, "bara");

      Stream wellStream = new Stream(wellName + " stream", wellFluid);
      wellStream.setFlowRate(ratePerWellSm3d, "Sm3/day");

      // Create subsea well
      SubseaWell well = new SubseaWell(wellName, wellStream);
      configureTubing(well);
      wells.add(well);

      SubseaTree tree = createTree(wellName, well);

      // Create choke
      ThrottlingValve choke = new ThrottlingValve(wellName + " choke", tree.getOutletStream());
      choke.setOutletPressure(wellheadPressureBara - 5.0 - (i * 2.0), "bara");
      subseaChokes.add(choke);

      SubseaJumper jumper = createJumper(wellName + " jumper", choke.getOutletStream());

      subseaProcess.add(wellStream);
      subseaProcess.add(well);
      subseaProcess.add(tree);
      subseaProcess.add(choke);
      subseaProcess.add(jumper);

      previousOutput = jumper.getOutletStream();

      // Add flowline segment if not last well
      if (i < wellCount - 1) {
        SimpleFlowLine segment = new SimpleFlowLine(wellName + " segment", jumper.getOutletStream());
        configureFlowline(segment, segmentLength);
        subseaProcess.add(segment);
        previousOutput = segment.getOutletStream();
      }
    }

    // Final trunk line to host
    if (previousOutput != null) {
      SimpleFlowLine trunkLine = new SimpleFlowLine(name + " trunk", previousOutput);
      configureFlowline(trunkLine, segmentLength);
      flowlines.add(trunkLine);
      subseaProcess.add(trunkLine);
    }
  }

  /**
   * Builds template architecture (multiple wells from single location).
   */
  private void buildTemplate() {
    // Similar to manifold cluster but with shared template infrastructure
    buildManifoldCluster(); // Simplified for now
  }

  /**
   * Configures tubing and well design metadata for a generated well.
   *
   * @param well generated subsea or dry-tree well to configure
   */
  private void configureTubing(SubseaWell well) {
    well.setWellLocationType(wellLocationType);
    well.setRigType(rigType);
    well.setCompletionType(completionType);
    well.setMeasuredDepth(wellDepthM);
    well.setProductionCasingDepth(wellDepthM);
    well.setWaterDepth(waterDepthM);
    well.setReservoirPressure(reservoirPressureBara);
    well.setReservoirTemperature(reservoirTemperatureC);
    well.setMaxWellheadPressure(Math.max(reservoirPressureBara, wellheadPressureBara));
    AdiabaticTwoPhasePipe tubing = well.getPipeline();
    tubing.setDiameter(tubingDiameterInches * 0.0254); // Convert to meters
    tubing.setLength(wellDepthM);
    tubing.setInletElevation(-wellDepthM);
    tubing.setOutletElevation(-waterDepthM);
  }

  /**
   * Configures a generated subsea flowline segment.
   *
   * @param flowline flowline equipment to configure
   * @param lengthKm routed flowline length in km
   */
  private void configureFlowline(SimpleFlowLine flowline, double lengthKm) {
    AdiabaticTwoPhasePipe pipe = flowline.getPipeline();
    pipe.setDiameter(flowlineDiameterInches * 0.0254); // Convert to meters
    pipe.setLength(lengthKm * 1000.0); // Convert to meters
    pipe.setInletElevation(-waterDepthM);
    pipe.setOutletElevation(-waterDepthM); // Assuming flat seabed for simplicity
  }

  /**
   * Creates the Christmas tree for a generated well.
   *
   * @param wellName generated well name used as the tree tag prefix
   * @param well well connected to the tree inlet
   * @return configured tree equipment
   */
  private SubseaTree createTree(String wellName, SubseaWell well) {
    SubseaTree tree = new SubseaTree(wellName + " XTree", well.getOutletStream());
    tree.setWaterDepth(wellLocationType == WellLocationType.SUBSEA_WET_TREE ? waterDepthM : 0.0);
    tree.setBoreSizeInches(tubingDiameterInches);
    tree.setPressureRating(selectTreePressureRating());
    tree.setChokeOpening(100.0);
    trees.add(tree);
    return tree;
  }

  /**
   * Selects a tree pressure-rating class from reservoir and wellhead pressures.
   *
   * @return pressure rating for generated trees
   */
  private SubseaTree.PressureRating selectTreePressureRating() {
    double pressurePsi = Math.max(reservoirPressureBara, wellheadPressureBara) * 14.5038;
    if (pressurePsi <= 5000.0) {
      return SubseaTree.PressureRating.PR5000;
    } else if (pressurePsi <= 10000.0) {
      return SubseaTree.PressureRating.PR10000;
    } else if (pressurePsi <= 15000.0) {
      return SubseaTree.PressureRating.PR15000;
    }
    return SubseaTree.PressureRating.PR20000;
  }

  /**
   * Creates a standard rigid M-shape jumper between a tree/choke and downstream equipment.
   *
   * @param jumperName generated jumper name
   * @param inletStream stream entering the jumper
   * @return configured jumper equipment
   */
  private SubseaJumper createJumper(String jumperName, StreamInterface inletStream) {
    SubseaJumper jumper = SubseaJumper.createRigidMShape(jumperName, inletStream, 30.0);
    jumper.setWaterDepth(waterDepthM);
    jumper.setNominalBoreInches(Math.max(4.0, tubingDiameterInches));
    jumper.setDesignPressure(Math.max(reservoirPressureBara, wellheadPressureBara));
    jumper.setDesignTemperature(reservoirTemperatureC);
    jumpers.add(jumper);
    return jumper;
  }

  /**
   * Creates a production manifold for clustered wells.
   *
   * @param manifoldName generated manifold name
   * @param slotCount number of production slots
   * @return configured manifold equipment
   */
  private SubseaManifold createManifold(String manifoldName, int slotCount) {
    int slots = Math.max(1, slotCount);
    SubseaManifold manifold = new SubseaManifold(manifoldName, slots);
    manifold.setWaterDepth(waterDepthM);
    manifold.setDesignPressure(Math.max(reservoirPressureBara, wellheadPressureBara));
    manifold.setDesignTemperature(reservoirTemperatureC);
    manifold.setProductionHeaderSizeInches(flowlineDiameterInches);
    manifold.setBranchSizeInches(Math.max(4.0, tubingDiameterInches));
    manifold.setHasTestHeader(true);
    return manifold;
  }

  /**
   * Adds pipeline end terminations and a host-side PLEM for generated export flowlines.
   */
  private void buildTerminations() {
    if (wellLocationType != WellLocationType.SUBSEA_WET_TREE || flowlines.isEmpty()) {
      return;
    }

    List<StreamInterface> terminationOutlets = new ArrayList<StreamInterface>();
    for (int i = 0; i < flowlines.size(); i++) {
      StreamInterface inlet = flowlines.get(i).getOutletStream();
      if (inlet == null) {
        continue;
      }
      PLET plet = new PLET(name + " PLET " + (i + 1), inlet);
      configurePlet(plet);
      plets.add(plet);
      terminationOutlets.add(plet.getOutletStream());
      subseaProcess.add(plet);
    }

    if (!terminationOutlets.isEmpty()) {
      PLEM plem = new PLEM(name + " Export PLEM", Math.max(1, terminationOutlets.size()));
      configurePlem(plem, terminationOutlets.size());
      for (StreamInterface outlet : terminationOutlets) {
        plem.addInletStream(outlet);
      }
      plems.add(plem);
      subseaProcess.add(plem);
    }
  }

  /**
   * Configures a generated PLET.
   *
   * @param plet pipeline end termination to configure
   */
  private void configurePlet(PLET plet) {
    plet.setWaterDepth(waterDepthM);
    plet.setDesignPressure(Math.max(reservoirPressureBara, wellheadPressureBara));
    plet.setDesignTemperature(reservoirTemperatureC);
    plet.setNominalBoreInches(flowlineDiameterInches);
    plet.setHasIsolationValve(true);
    plet.setHasPiggingFacility(true);
    plet.setHasFutureTieIn(true);
  }

  /**
   * Configures a generated PLEM.
   *
   * @param plem pipeline end manifold to configure
   * @param slotCount number of connected slots
   */
  private void configurePlem(PLEM plem, int slotCount) {
    plem.setWaterDepth(waterDepthM);
    plem.setDesignPressure(Math.max(reservoirPressureBara, wellheadPressureBara));
    plem.setDesignTemperature(reservoirTemperatureC);
    plem.setNumberOfSlots(Math.max(1, slotCount));
    plem.setConfigurationType(slotCount > 1 ? PLEM.ConfigurationType.COMMINGLING : PLEM.ConfigurationType.THROUGH_FLOW);
    plem.setHeaderSizeInches(flowlineDiameterInches);
    plem.setBranchSizeInches(Math.max(4.0, tubingDiameterInches));
    plem.setBranchIsolationValves(true);
    plem.setHeaderIsolationValves(true);
  }

  /**
   * Adds typical wet-tree controls umbilical and production risers to the generated system.
   */
  private void buildControlsAndRisers() {
    if (wellLocationType != WellLocationType.SUBSEA_WET_TREE) {
      return;
    }

    Umbilical umbilical = new Umbilical(name + " Control Umbilical");
    umbilical.setLength(umbilicalLengthKm * 1000.0);
    umbilical.setWaterDepth(waterDepthM);
    umbilical.addHydraulicLine("Tree controls", 12.7, Math.max(345.0, wellheadPressureBara));
    umbilical.addHydraulicLine("Manifold controls", 12.7, Math.max(345.0, wellheadPressureBara));
    umbilical.addChemicalLine("MEG", 25.4, Math.max(345.0, wellheadPressureBara));
    umbilical.addChemicalLine("Scale inhibitor", 12.7, Math.max(345.0, wellheadPressureBara));
    umbilical.addElectricalCable("Power and signal", 4, 3000.0);
    umbilical.addFiberOptic("Controls fiber", 12);
    umbilicals.add(umbilical);
    subseaProcess.add(umbilical);

    if (includeRisers) {
      int riserCount = Math.max(1, productionRiserCount);
      double diameter = riserDiameterInches > 0 ? riserDiameterInches : flowlineDiameterInches;
      StreamInterface riserInlet = getRiserInletStream();
      if (riserInlet == null) {
        return;
      }
      for (int i = 0; i < riserCount; i++) {
        if (flexibleRiser) {
          FlexiblePipe riser = FlexiblePipe.createDynamicRiser(name + " Flexible Riser " + (i + 1), riserInlet,
              FlexiblePipe.RiserConfiguration.LAZY_WAVE);
          riser.setInnerDiameterInches(diameter);
          riser.setLength(waterDepthM * 1.5);
          riser.setWaterDepth(waterDepthM);
          riser.setDesignPressure(Math.max(reservoirPressureBara, wellheadPressureBara));
          riser.setDesignTemperature(reservoirTemperatureC);
          risers.add(riser);
          subseaProcess.add(riser);
        } else {
          SimpleFlowLine steelRiser = new SimpleFlowLine(name + " Steel Riser " + (i + 1), riserInlet);
          configureSteelRiser(steelRiser, waterDepthM * 1.1, diameter);
          steelRisers.add(steelRiser);
          subseaProcess.add(steelRiser);
        }
      }
    }
  }

  /**
   * Gets the outlet stream feeding generated risers.
   *
   * @return stream downstream of PLEM, PLET or final flowline
   */
  private StreamInterface getRiserInletStream() {
    if (!plets.isEmpty()) {
      return plets.get(plets.size() - 1).getOutletStream();
    }
    if (!plems.isEmpty()) {
      return plems.get(plems.size() - 1).getOutletStream();
    }
    return flowlines.isEmpty() ? null : flowlines.get(flowlines.size() - 1).getOutletStream();
  }

  /**
   * Configures a generated steel riser represented by a vertical SimpleFlowLine.
   *
   * @param steelRiser steel riser flowline equipment
   * @param lengthM riser length in m
   * @param diameterInches riser inner diameter in inches
   */
  private void configureSteelRiser(SimpleFlowLine steelRiser, double lengthM, double diameterInches) {
    AdiabaticTwoPhasePipe pipe = steelRiser.getPipeline();
    pipe.setDiameter(diameterInches * 0.0254);
    pipe.setLength(lengthM);
    pipe.setInletElevation(-waterDepthM);
    pipe.setOutletElevation(0.0);
  }

  // ============================================================================
  // EXECUTION METHODS
  // ============================================================================

  /**
   * Runs the subsea production system simulation.
   */
  public void run() {
    if (subseaProcess == null) {
      build();
    }

    subseaProcess.run();
    hasRun = true;

    // Build result
    result = new SubseaSystemResult(name);
    extractResults();
  }

  /**
   * Runs the subsea system with a specific calculation ID.
   *
   * @param id calculation identifier
   */
  public void run(UUID id) {
    if (subseaProcess == null) {
      build();
    }

    subseaProcess.run(id);
    hasRun = true;

    result = new SubseaSystemResult(name);
    extractResults();
  }

  private void extractResults() {
    // Extract arrival conditions from last flowline
    if (!flowlines.isEmpty()) {
      SimpleFlowLine lastFlowline = flowlines.get(flowlines.size() - 1);
      StreamInterface outlet = lastFlowline.getOutletStream();
      if (outlet != null && outlet.getFluid() != null) {
        result.arrivalPressureBara = outlet.getPressure("bara");
        result.arrivalTemperatureC = outlet.getTemperature("C");
        result.arrivalFlowrateSm3d = outlet.getFluid().getFlowRate("Sm3/day");
      }
    }

    // Extract total flow rate
    result.totalProductionSm3d = 0;
    for (SubseaWell well : wells) {
      StreamInterface outlet = well.getOutletStream();
      if (outlet != null && outlet.getFluid() != null) {
        result.totalProductionSm3d += outlet.getFluid().getFlowRate("Sm3/day");
      }
    }

    // Calculate pressure drops
    if (!wells.isEmpty() && !flowlines.isEmpty()) {
      double wellheadP = wells.get(0).getOutletStream() != null ? wells.get(0).getOutletStream().getPressure("bara")
          : wellheadPressureBara;
      result.totalPressureDropBara = wellheadP - result.arrivalPressureBara;
    }

    // Run tieback cost estimation
    estimateTiebackCosts();

    // Set configuration parameters in result
    result.waterDepthM = waterDepthM;
    result.tiebackDistanceKm = tiebackDistanceKm;
    result.wellCount = wellCount;
    result.flowlineDiameterInches = flowlineDiameterInches;
    result.architecture = architecture;
  }

  /**
   * Estimates reservoir, well and SURF CAPEX for the generated field-development concept.
   */
  private void estimateTiebackCosts() {
    result.wellLocationType = wellLocationType;
    result.reservoirCostMusd = estimateReservoirDevelopmentCostMusd();
    result.wellCostMusd = estimateWellCostsMusd();

    if (wellLocationType != WellLocationType.SUBSEA_WET_TREE) {
      result.subseaTreeCostMusd = 0.0;
      result.manifoldCostMusd = 0.0;
      result.jumperAndPletCostMusd = 0.0;
      result.pipelineCostMusd = 0.0;
      result.umbilicalCostMusd = 0.0;
      result.riserCostMusd = 0.0;
      result.controlSystemCostMusd = 0.0;
      result.totalSubseaCapexMusd = 0.0;
      result.totalDevelopmentCapexMusd = result.reservoirCostMusd + result.wellCostMusd;
      result.surfDetailedEstimateResult = null;
      return;
    }

    surfCostEstimator = createSurfCostEstimator();
    surfCostEstimator.calculate();
    result.surfDetailedEstimateResult = surfCostEstimator.getDetailedEstimateResult();

    result.subseaTreeCostMusd = sumSurfLineItemsMusd("Christmas Trees");
    result.manifoldCostMusd = sumSurfLineItemsMusd("Manifold");
    result.jumperAndPletCostMusd = sumSurfLineItemsMusd("Jumpers") + sumSurfLineItemsMusd("PLETs");
    result.pipelineCostMusd = surfCostEstimator.getFlowlineCostUSD() / 1.0e6;
    result.umbilicalCostMusd = surfCostEstimator.getUmbilicalCostUSD() / 1.0e6;
    result.riserCostMusd = surfCostEstimator.getRiserCostUSD() / 1.0e6;
    result.controlSystemCostMusd = 0.0;
    result.totalSubseaCapexMusd = surfCostEstimator.getTotalSURFCostUSD() / 1.0e6;
    result.totalDevelopmentCapexMusd = result.reservoirCostMusd + result.wellCostMusd + result.totalSubseaCapexMusd;
  }

  /**
   * Creates and configures the integrated SURF cost estimator from system settings.
   *
   * @return configured SURF cost estimator
   */
  private SURFCostEstimator createSurfCostEstimator() {
    SURFCostEstimator surf = new SURFCostEstimator(wellCount, waterDepthM, costRegion);
    surf.setNumberOfWells(wellCount);
    surf.setWaterDepthM(waterDepthM);
    surf.setRegion(costRegion);
    surf.setTreePressureRatingPsi(selectTreePressureRating().getPsi());
    surf.setTreeBoreSizeInches(Math.max(5.0, tubingDiameterInches));
    surf.setHorizontalTrees(true);
    surf.setDualBoreTrees(false);
    surf.setManifoldSlots(Math.max(wellCount, manifoldCount));
    surf.setManifoldWeightTonnes(Math.max(80.0, 80.0 + 10.0 * wellCount));
    surf.setManifoldHasTestHeader(true);
    surf.setNumberOfPLETs(plets.isEmpty() ? Math.max(2, manifoldCount * 2) : plets.size());
    surf.setNumberOfPLEMs(plems.size());
    surf.setPlemHeaderSizeInches(flowlineDiameterInches);
    surf.setNumberOfJumpers(jumpers.size() > 0 ? jumpers.size() : wellCount);
    surf.setJumperLengthM(30.0);
    surf.setJumperDiameterInches(Math.max(4.0, tubingDiameterInches));
    surf.setRigidJumpers(true);
    surf.setUmbilicalLengthKm(umbilicalLengthKm);
    surf.setUmbilicalHydraulicLines(Math.max(4, wellCount * 2));
    surf.setUmbilicalChemicalLines(2);
    surf.setUmbilicalElectricalCables(2);
    surf.setUmbilicalDynamic(includeRisers);
    surf.setIncludeRisers(includeRisers);
    surf.setFlexibleRiser(flexibleRiser);
    surf.setNumberOfProductionRisers(Math.max(1, productionRiserCount));
    surf.setRiserDiameterInches(riserDiameterInches > 0 ? riserDiameterInches : flowlineDiameterInches);
    surf.setRiserLengthM(waterDepthM * 1.5);
    surf.setRiserHasBuoyancy(flexibleRiser);
    surf.setInfieldFlowlineLengthKm(getInfieldFlowlineLengthKm());
    surf.setInfieldFlowlineDiameterInches(flowlineDiameterInches);
    surf.setInfieldFlowlineFlexible("Flexible".equalsIgnoreCase(flowlineMaterial));
    surf.setExportPipelineLengthKm(tiebackDistanceKm);
    surf.setExportPipelineDiameterInches(flowlineDiameterInches);
    surf.setPipelineWallThicknessMm(flowlineWallThicknessMm);
    surf.setPipelineDesignPressureBar(Math.max(reservoirPressureBara, wellheadPressureBara));
    return surf;
  }

  /**
   * Estimates generated infield flowline length for the selected architecture.
   *
   * @return infield flowline length in km
   */
  private double getInfieldFlowlineLengthKm() {
    if (architecture == SubseaArchitecture.MANIFOLD_CLUSTER || architecture == SubseaArchitecture.TEMPLATE) {
      return Math.min(2.0, tiebackDistanceKm * 0.1) * wellCount;
    } else if (architecture == SubseaArchitecture.DAISY_CHAIN) {
      return tiebackDistanceKm;
    }
    return 0.0;
  }

  /**
   * Sums SURF line items whose descriptions contain a token.
   *
   * @param descriptionToken token to match in the line-item description
   * @return summed line-item cost in MUSD
   */
  private double sumSurfLineItemsMusd(String descriptionToken) {
    if (surfCostEstimator == null) {
      return 0.0;
    }
    double cost = 0.0;
    for (Map<String, Object> item : surfCostEstimator.getLineItems()) {
      Object description = item.get("description");
      Object totalCost = item.get("totalCostUSD");
      if (description != null && description.toString().contains(descriptionToken) && totalCost instanceof Number) {
        cost += ((Number) totalCost).doubleValue();
      }
    }
    return cost / 1.0e6;
  }

  /**
   * Estimates reservoir appraisal and reservoir-management cost.
   *
   * @return reservoir cost in MUSD
   */
  private double estimateReservoirDevelopmentCostMusd() {
    if (!Double.isNaN(reservoirDevelopmentCostMusd)) {
      return reservoirDevelopmentCostMusd;
    }
    return Math.max(10.0, 4.0 * wellCount);
  }

  /**
   * Estimates drilling and completion cost for generated wells.
   *
   * @return total well cost in MUSD
   */
  private double estimateWellCostsMusd() {
    double totalWellCost = 0.0;
    int count = wells.isEmpty() ? wellCount : wells.size();
    for (int i = 0; i < count; i++) {
      SubseaWell well = wells.isEmpty() ? null : wells.get(i);
      WellCostEstimator estimator = new WellCostEstimator(costRegion);
      String wellType = well == null ? SubseaWell.WellType.OIL_PRODUCER.name() : well.getWellType().name();
      String rig = well == null ? rigType.name() : well.getRigType().name();
      String completion = well == null ? completionType.name() : well.getCompletionType().name();
      double measuredDepth = well == null ? wellDepthM : well.getMeasuredDepth();
      double waterDepth = well == null ? waterDepthM : well.getWaterDepth();
      double drillingDays = well == null ? 55.0 : well.getDrillingDays();
      double completionDays = well == null ? 20.0 : well.getCompletionDays();
      double rigDayRate = well == null ? 0.0 : well.getRigDayRate();
      boolean hasDHSV = well == null || well.hasDHSV();
      int casingStrings = well == null ? 4 : well.getNumberOfCasingStrings();
      estimator.calculateWellCost(wellType, rig, completion, measuredDepth, waterDepth, drillingDays, completionDays,
          rigDayRate, hasDHSV, casingStrings, wellLocationType);
      double cost = estimator.getTotalCost();
      if (wellLocationType == WellLocationType.SUBSEA_WET_TREE) {
        cost -= estimator.getWellheadCost() * (1.0 + estimator.getContingencyPct());
      }
      totalWellCost += Math.max(0.0, cost);
    }
    return totalWellCost / 1.0e6;
  }

  // ============================================================================
  // RESULT ACCESS
  // ============================================================================

  /**
   * Gets the subsea system result.
   *
   * @return subsea system result
   */
  public SubseaSystemResult getResult() {
    if (!hasRun) {
      run();
    }
    return result;
  }

  /**
   * Gets arrival pressure at host.
   *
   * @return arrival pressure in bara
   */
  public double getArrivalPressureBara() {
    if (!hasRun) {
      run();
    }
    return result.arrivalPressureBara;
  }

  /**
   * Gets arrival temperature at host.
   *
   * @return arrival temperature in Celsius
   */
  public double getArrivalTemperatureC() {
    if (!hasRun) {
      run();
    }
    return result.arrivalTemperatureC;
  }

  /**
   * Gets the internal process system.
   *
   * @return process system
   */
  public ProcessSystem getProcessSystem() {
    if (subseaProcess == null) {
      build();
    }
    return subseaProcess;
  }

  /**
   * Gets the list of subsea wells.
   *
   * @return list of wells
   */
  public List<SubseaWell> getWells() {
    return wells;
  }

  /**
   * Gets the list of generated Christmas trees.
   *
   * @return list of trees
   */
  public List<SubseaTree> getTrees() {
    return trees;
  }

  /**
   * Gets the list of generated jumpers.
   *
   * @return list of jumpers
   */
  public List<SubseaJumper> getJumpers() {
    return jumpers;
  }

  /**
   * Gets the list of generated manifolds.
   *
   * @return list of manifolds
   */
  public List<SubseaManifold> getManifolds() {
    return manifolds;
  }

  /**
   * Gets the list of generated pipeline end terminations.
   *
   * @return list of PLET equipment
   */
  public List<PLET> getPLETs() {
    return plets;
  }

  /**
   * Gets the list of generated pipeline end manifolds.
   *
   * @return list of PLEM equipment
   */
  public List<PLEM> getPLEMs() {
    return plems;
  }

  /**
   * Gets the list of generated umbilicals.
   *
   * @return list of umbilicals
   */
  public List<Umbilical> getUmbilicals() {
    return umbilicals;
  }

  /**
   * Gets the list of generated risers.
   *
   * @return list of risers
   */
  public List<FlexiblePipe> getRisers() {
    return risers;
  }

  /**
   * Gets the list of generated rigid or steel risers.
   *
   * @return list of steel riser flowline equipment
   */
  public List<SimpleFlowLine> getSteelRisers() {
    return steelRisers;
  }

  /**
   * Gets the list of flowlines.
   *
   * @return list of flowlines
   */
  public List<SimpleFlowLine> getFlowlines() {
    return flowlines;
  }

  /**
   * Gets the tieback option analysis.
   *
   * @return tieback option if analyzed, null otherwise
   */
  public TiebackOption getTiebackOption() {
    return tiebackOption;
  }

  // ============================================================================
  // GETTERS
  // ============================================================================

  /**
   * Gets the system name.
   *
   * @return system name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the architecture type.
   *
   * @return architecture
   */
  public SubseaArchitecture getArchitecture() {
    return architecture;
  }

  /**
   * Gets water depth.
   *
   * @return water depth in meters
   */
  public double getWaterDepthM() {
    return waterDepthM;
  }

  /**
   * Gets tieback distance.
   *
   * @return tieback distance in km
   */
  public double getTiebackDistanceKm() {
    return tiebackDistanceKm;
  }

  /**
   * Gets well count.
   *
   * @return number of wells
   */
  public int getWellCount() {
    return wellCount;
  }

  /**
   * Gets flowline diameter.
   *
   * @return flowline diameter in inches
   */
  public double getFlowlineDiameterInches() {
    return flowlineDiameterInches;
  }

  /**
   * Gets well location and tree installation basis.
   *
   * @return well location type
   */
  public WellLocationType getWellLocationType() {
    return wellLocationType;
  }

  // ============================================================================
  // INNER CLASS - RESULT
  // ============================================================================

  /**
   * Result container for subsea production system analysis.
   */
  public static class SubseaSystemResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    // Identification
    private String systemName;

    // Configuration (copied for reference)
    private SubseaArchitecture architecture;
    private double waterDepthM;
    private double tiebackDistanceKm;
    private int wellCount;
    private double flowlineDiameterInches;

    // Operating results
    private double arrivalPressureBara;
    private double arrivalTemperatureC;
    private double arrivalFlowrateSm3d;
    private double totalProductionSm3d;
    private double totalPressureDropBara;

    // Flow assurance
    private double hydrateFormationTempC;
    private double hydrateMarginC;
    private boolean requiresHeating;
    private boolean requiresInsulation;
    private boolean requiresMEG;

    // CAPEX breakdown (MUSD)
    private double subseaTreeCostMusd;
    private double manifoldCostMusd;
    private double jumperAndPletCostMusd;
    private double pipelineCostMusd;
    private double umbilicalCostMusd;
    private double riserCostMusd;
    private double controlSystemCostMusd;
    private double totalSubseaCapexMusd;
    private double wellCostMusd;
    private double reservoirCostMusd;
    private double totalDevelopmentCapexMusd;
    private WellLocationType wellLocationType;
    private CostEstimateResult surfDetailedEstimateResult;

    /**
     * Creates a new result.
     *
     * @param systemName system name
     */
    public SubseaSystemResult(String systemName) {
      this.systemName = systemName;
    }

    /**
     * Gets a summary report.
     *
     * @return markdown-formatted summary
     */
    public String getSummary() {
      StringBuilder sb = new StringBuilder();
      sb.append("# Subsea Production System: ").append(systemName).append("\n\n");

      sb.append("## Configuration\n\n");
      sb.append("| Parameter | Value |\n");
      sb.append("|-----------|-------|\n");
      sb.append(String.format("| Architecture | %s |\n", architecture));
      sb.append(String.format("| Water Depth | %.0f m |\n", waterDepthM));
      sb.append(String.format("| Tieback Distance | %.1f km |\n", tiebackDistanceKm));
      sb.append(String.format("| Well Count | %d |\n", wellCount));
      sb.append(String.format("| Flowline Diameter | %.0f\" |\n", flowlineDiameterInches));
      sb.append("\n");

      sb.append("## Operating Conditions\n\n");
      sb.append("| Parameter | Value |\n");
      sb.append("|-----------|-------|\n");
      sb.append(String.format("| Arrival Pressure | %.1f bara |\n", arrivalPressureBara));
      sb.append(String.format("| Arrival Temperature | %.1f °C |\n", arrivalTemperatureC));
      sb.append(String.format("| Total Production | %.2f MSm3/d |\n", totalProductionSm3d / 1e6));
      sb.append(String.format("| Total Pressure Drop | %.1f bar |\n", totalPressureDropBara));
      sb.append("\n");

      sb.append("## Subsea CAPEX\n\n");
      sb.append("| Component | Cost (MUSD) |\n");
      sb.append("|-----------|-------------|\n");
      sb.append(String.format("| Subsea Trees | %.0f |\n", subseaTreeCostMusd));
      sb.append(String.format("| Manifolds | %.0f |\n", manifoldCostMusd));
      sb.append(String.format("| Jumpers and PLETs | %.0f |\n", jumperAndPletCostMusd));
      sb.append(String.format("| Flowlines and Pipelines | %.0f |\n", pipelineCostMusd));
      sb.append(String.format("| Umbilicals | %.0f |\n", umbilicalCostMusd));
      sb.append(String.format("| Risers | %.0f |\n", riserCostMusd));
      sb.append(String.format("| **SURF Total** | **%.0f** |\n", totalSubseaCapexMusd));
      sb.append(String.format("| Wells | %.0f |\n", wellCostMusd));
      sb.append(String.format("| Reservoir/Appraisal | %.0f |\n", reservoirCostMusd));
      sb.append(String.format("| **Development Total** | **%.0f** |\n", totalDevelopmentCapexMusd));

      return sb.toString();
    }

    // Getters

    /**
     * Gets arrival pressure at host.
     *
     * @return pressure in bara
     */
    public double getArrivalPressureBara() {
      return arrivalPressureBara;
    }

    /**
     * Gets arrival temperature at host.
     *
     * @return temperature in Celsius
     */
    public double getArrivalTemperatureC() {
      return arrivalTemperatureC;
    }

    /**
     * Gets total subsea CAPEX.
     *
     * @return CAPEX in MUSD
     */
    public double getTotalSubseaCapexMusd() {
      return totalSubseaCapexMusd;
    }

    /**
     * Gets total development CAPEX including reservoir, wells and SURF.
     *
     * @return development CAPEX in MUSD
     */
    public double getTotalDevelopmentCapexMusd() {
      return totalDevelopmentCapexMusd;
    }

    /**
     * Gets well CAPEX.
     *
     * @return well cost in MUSD
     */
    public double getWellCostMusd() {
      return wellCostMusd;
    }

    /**
     * Gets reservoir appraisal and development CAPEX.
     *
     * @return reservoir cost in MUSD
     */
    public double getReservoirCostMusd() {
      return reservoirCostMusd;
    }

    /**
     * Gets pipeline cost.
     *
     * @return pipeline cost in MUSD
     */
    public double getPipelineCostMusd() {
      return pipelineCostMusd;
    }

    /**
     * Gets subsea trees cost.
     *
     * @return trees cost in MUSD
     */
    public double getSubseaTreeCostMusd() {
      return subseaTreeCostMusd;
    }

    /**
     * Gets manifold cost.
     *
     * @return manifold cost in MUSD
     */
    public double getManifoldCostMusd() {
      return manifoldCostMusd;
    }

    /**
     * Gets umbilical cost.
     *
     * @return umbilical cost in MUSD
     */
    public double getUmbilicalCostMusd() {
      return umbilicalCostMusd;
    }

    /**
     * Gets jumper and PLET cost.
     *
     * @return jumper and PLET cost in MUSD
     */
    public double getJumperAndPletCostMusd() {
      return jumperAndPletCostMusd;
    }

    /**
     * Gets riser cost.
     *
     * @return riser cost in MUSD
     */
    public double getRiserCostMusd() {
      return riserCostMusd;
    }

    /**
     * Gets the detailed SURF estimate result with basis and material take-off.
     *
     * @return detailed SURF estimate result, or {@code null} when the concept has no subsea wet-tree SURF scope
     */
    public CostEstimateResult getSurfDetailedEstimateResult() {
      return surfDetailedEstimateResult;
    }

    /**
     * Gets a detailed development CAPEX result covering reservoir, wells and SURF.
     *
     * <p>
     * SURF material take-off lines are copied from the detailed SURF estimate when available. Reservoir and well lines
     * are represented as screening-level scope placeholders until detailed drilling and completion material take-off is
     * available.
     * </p>
     *
     * @return detailed development CAPEX estimate result
     */
    public CostEstimateResult getDetailedDevelopmentEstimateResult() {
      EstimateClass estimateClass = surfDetailedEstimateResult == null ? EstimateClass.CLASS_4
          : surfDetailedEstimateResult.getBasis().getEstimateClass();
      CostEstimateBasis basis = new CostEstimateBasis().setEstimateClass(estimateClass)
          .setEstimatingMethod("field-development cost rollup")
          .setDataSource("subsea production system screening correlations")
          .setNotes("Development CAPEX rollup from reservoir/appraisal, drilling/completion wells and SURF estimates.");

      CostEstimateResult result = new CostEstimateResult()
          .setIdentification(systemName + " development", systemName, "subsea-development").setBasis(basis)
          .addCapitalCost("reservoirAppraisal", reservoirCostMusd * 1.0e6).addCapitalCost("wells", wellCostMusd * 1.0e6)
          .addCapitalCost("subseaTrees", subseaTreeCostMusd * 1.0e6)
          .addCapitalCost("manifolds", manifoldCostMusd * 1.0e6)
          .addCapitalCost("jumpersAndPLETs", jumperAndPletCostMusd * 1.0e6)
          .addCapitalCost("pipelines", pipelineCostMusd * 1.0e6).addCapitalCost("umbilicals", umbilicalCostMusd * 1.0e6)
          .addCapitalCost("risers", riserCostMusd * 1.0e6)
          .addCapitalCost("controlSystems", controlSystemCostMusd * 1.0e6)
          .addCapitalCostSummary("totalSURF", totalSubseaCapexMusd * 1.0e6)
          .addCapitalCostSummary("totalDevelopment", totalDevelopmentCapexMusd * 1.0e6);

      if (reservoirCostMusd > 0.0) {
        result.addMaterialTakeOff(new MaterialTakeOffItem("Reservoir appraisal and management", "reservoir",
            "study and appraisal", Math.max(1, wellCount), "well-basis", Double.NaN, reservoirCostMusd * 1.0e6,
            "field-development-screening"));
      }
      if (wellCostMusd > 0.0) {
        result.addMaterialTakeOff(new MaterialTakeOffItem("Drilling and completion wells", "wells",
            wellLocationType == null ? "well" : wellLocationType.name(), Math.max(1, wellCount), "well", Double.NaN,
            wellCostMusd * 1.0e6, "field-development-screening"));
      }
      if (surfDetailedEstimateResult == null) {
        result.addQualityFlag("No detailed SURF estimate was available for this development concept.");
      } else {
        for (MaterialTakeOffItem item : surfDetailedEstimateResult.getMaterialTakeOff()) {
          result.addMaterialTakeOff(item);
        }
      }
      result.addQualityFlag(
          "Reservoir and well material take-off lines are screening placeholders until detailed well mechanical MTO is available.");
      return result;
    }

    /**
     * Gets well location and tree installation basis.
     *
     * @return well location type
     */
    public WellLocationType getWellLocationType() {
      return wellLocationType;
    }

    /**
     * Gets total production rate.
     *
     * @return production in Sm3/day
     */
    public double getTotalProductionSm3d() {
      return totalProductionSm3d;
    }

    /**
     * Gets total pressure drop.
     *
     * @return pressure drop in bara
     */
    public double getTotalPressureDropBara() {
      return totalPressureDropBara;
    }
  }
}
