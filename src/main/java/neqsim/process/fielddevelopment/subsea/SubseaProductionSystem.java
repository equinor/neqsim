package neqsim.process.fielddevelopment.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.network.WellFlowlineNetwork;
import neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.subsea.SimpleFlowLine;
import neqsim.process.equipment.subsea.SubseaWell;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.TiebackAnalyzer;
import neqsim.process.fielddevelopment.tieback.TiebackOption;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Unified subsea production system for field development workflow integration.
 *
 * <p>
 * This class provides a high-level abstraction for modeling subsea production systems, integrating
 * multiple NeqSim components:
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

  // Host facility
  private HostFacility hostFacility;

  // Fluid
  private SystemInterface reservoirFluid;

  // ============================================================================
  // INTERNAL MODELS
  // ============================================================================

  private transient ProcessSystem subseaProcess;
  private transient List<SubseaWell> wells = new ArrayList<>();
  private transient List<ThrottlingValve> subseaChokes = new ArrayList<>();
  private transient List<SimpleFlowLine> flowlines = new ArrayList<>();
  private transient WellFlowlineNetwork network;
  private transient TiebackAnalyzer tiebackAnalyzer;
  private transient TiebackOption tiebackOption;

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
   * Creates the process equipment (wells, flowlines, manifolds) based on the configured
   * architecture and parameters.
   * </p>
   *
   * @return this for chaining
   */
  public SubseaProductionSystem build() {
    validateConfiguration();

    subseaProcess = new ProcessSystem();
    wells = new ArrayList<>();
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
   * Builds direct tieback architecture (wells -> individual flowlines -> host).
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

      // Create subsea choke
      ThrottlingValve choke = new ThrottlingValve(wellName + " choke", well.getOutletStream());
      choke.setOutletPressure(wellheadPressureBara - 5.0, "bara"); // 5 bar choke dP
      subseaChokes.add(choke);

      // Create flowline to host
      SimpleFlowLine flowline = new SimpleFlowLine(wellName + " flowline", choke.getOutletStream());
      configureFlowline(flowline, tiebackDistanceKm);
      flowlines.add(flowline);

      // Add to process
      subseaProcess.add(wellStream);
      subseaProcess.add(well);
      subseaProcess.add(choke);
      subseaProcess.add(flowline);
    }
  }

  /**
   * Builds manifold cluster architecture (wells -> manifold -> trunk flowline -> host).
   */
  private void buildManifoldCluster() {
    // Calculate wells per manifold
    int wellsPerManifold = (int) Math.ceil((double) wellCount / manifoldCount);
    int wellIndex = 0;

    for (int m = 0; m < manifoldCount; m++) {
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

        // Create subsea choke
        ThrottlingValve choke = new ThrottlingValve(wellName + " choke", well.getOutletStream());
        choke.setOutletPressure(wellheadPressureBara - 5.0, "bara");
        subseaChokes.add(choke);

        // Short infield line to manifold (typically 500m - 2km)
        double infieldLength = Math.min(2.0, tiebackDistanceKm * 0.1);
        SimpleFlowLine infieldLine =
            new SimpleFlowLine(wellName + " infield", choke.getOutletStream());
        configureFlowline(infieldLine, infieldLength);

        manifoldInputs.add(infieldLine.getOutletStream());

        subseaProcess.add(wellStream);
        subseaProcess.add(well);
        subseaProcess.add(choke);
        subseaProcess.add(infieldLine);

        wellIndex++;
      }

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

      // Create choke
      ThrottlingValve choke = new ThrottlingValve(wellName + " choke", well.getOutletStream());
      choke.setOutletPressure(wellheadPressureBara - 5.0 - (i * 2.0), "bara");
      subseaChokes.add(choke);

      subseaProcess.add(wellStream);
      subseaProcess.add(well);
      subseaProcess.add(choke);

      previousOutput = choke.getOutletStream();

      // Add flowline segment if not last well
      if (i < wellCount - 1) {
        SimpleFlowLine segment = new SimpleFlowLine(wellName + " segment", choke.getOutletStream());
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

  private void configureTubing(SubseaWell well) {
    AdiabaticTwoPhasePipe tubing = well.getPipeline();
    tubing.setDiameter(tubingDiameterInches * 0.0254); // Convert to meters
    tubing.setLength(wellDepthM);
    tubing.setInletElevation(-wellDepthM);
    tubing.setOutletElevation(-waterDepthM);
  }

  private void configureFlowline(SimpleFlowLine flowline, double lengthKm) {
    AdiabaticTwoPhasePipe pipe = flowline.getPipeline();
    pipe.setDiameter(flowlineDiameterInches * 0.0254); // Convert to meters
    pipe.setLength(lengthKm * 1000.0); // Convert to meters
    pipe.setInletElevation(-waterDepthM);
    pipe.setOutletElevation(-waterDepthM); // Assuming flat seabed for simplicity
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
      double wellheadP = wells.get(0).getOutletStream() != null
          ? wells.get(0).getOutletStream().getPressure("bara")
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

  private void estimateTiebackCosts() {
    // Use TiebackAnalyzer for cost estimation
    double pipelineCostPerKm = getPipelineCostPerKm();
    double subseaTreeCost = 25.0; // MUSD per tree
    double manifoldCost = 35.0; // MUSD base

    result.subseaTreeCostMusd = wellCount * subseaTreeCost;
    result.manifoldCostMusd = manifoldCount * manifoldCost;
    result.pipelineCostMusd = tiebackDistanceKm * pipelineCostPerKm;
    result.umbilicalCostMusd = umbilicalLengthKm * 1.0; // 1 MUSD/km

    // Add cost for flexible jumpers, controls, etc.
    double miscCost = wellCount * 5.0; // 5 MUSD per well for misc
    result.controlSystemCostMusd = wellCount * 3.0 + manifoldCount * 5.0;

    result.totalSubseaCapexMusd = result.subseaTreeCostMusd + result.manifoldCostMusd
        + result.pipelineCostMusd + result.umbilicalCostMusd + result.controlSystemCostMusd;
  }

  private double getPipelineCostPerKm() {
    // Cost per km depends on diameter and water depth
    double baseCost = 2.0; // MUSD/km for 10" pipe

    // Diameter factor
    double diameterFactor = flowlineDiameterInches / 10.0;
    diameterFactor = Math.pow(diameterFactor, 1.3); // Non-linear scaling

    // Water depth factor
    double depthFactor = 1.0;
    if (waterDepthM > 500) {
      depthFactor = 1.0 + (waterDepthM - 500) / 1000.0;
    }
    if (waterDepthM > 1000) {
      depthFactor = 1.5 + (waterDepthM - 1000) / 500.0;
    }

    // Material factor
    double materialFactor = 1.0;
    if ("CRA".equalsIgnoreCase(flowlineMaterial)) {
      materialFactor = 2.5;
    } else if ("Flexible".equalsIgnoreCase(flowlineMaterial)) {
      materialFactor = 3.0;
    }

    return baseCost * diameterFactor * depthFactor * materialFactor;
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
    private double pipelineCostMusd;
    private double umbilicalCostMusd;
    private double controlSystemCostMusd;
    private double totalSubseaCapexMusd;

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
      sb.append(String.format("| Pipeline | %.0f |\n", pipelineCostMusd));
      sb.append(String.format("| Umbilicals | %.0f |\n", umbilicalCostMusd));
      sb.append(String.format("| Control Systems | %.0f |\n", controlSystemCostMusd));
      sb.append(String.format("| **Total** | **%.0f** |\n", totalSubseaCapexMusd));

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
