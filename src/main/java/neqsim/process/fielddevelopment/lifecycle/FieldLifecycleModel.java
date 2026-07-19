package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Assembled NeqSim model used by the field-lifecycle simulator.
 *
 * <p>
 * The model provides explicit connection points between subsurface material balance and the surface process. The
 * process model can be a normal {@link ProcessSystem} or a multi-area {@link ProcessModel}; detailed user-built
 * flowsheets can therefore replace the reference model without changing the lifecycle simulator.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class FieldLifecycleModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final SimpleReservoir reservoir;
  private final ProcessSystem processSystem;
  private final ProcessModel processModel;
  private final ProcessSystem existingSurfSystem;
  private final String facilityAreaName;
  private final String surfAreaName;
  private final StreamInterface reservoirOilProducer;
  private final StreamInterface reservoirWaterProducer;
  private final StreamInterface reservoirGasInjector;
  private final StreamInterface recoveredGas;
  private final Splitter gasAllocationSplitter;
  private final StreamInterface stabilizedOilExport;
  private final StreamInterface gasExport;
  private final StreamInterface compressedInjectionGas;
  private final StreamInterface hostOilFeed;
  private final StreamInterface hostGasFeed;
  private final StreamInterface hostWaterFeed;
  private final StreamInterface treatedWaterDischarge;
  private FieldProductionPotentialProvider productionPotentialProvider;
  private FieldProductQualityProvider productQualityProvider;

  /**
   * Starts an explicit connection-point builder for a user-supplied detailed NeqSim process.
   *
   * @param name lifecycle model name
   * @param reservoir mutable reservoir material-balance model
   * @param processSystem existing brownfield or newly assembled greenfield process
   * @return connection-point builder
   */
  public static Builder builder(String name, SimpleReservoir reservoir, ProcessSystem processSystem) {
    return new Builder(name, reservoir, processSystem, null, null, null, null);
  }

  /**
   * Starts a connection-point builder for a user-supplied multi-area process model.
   *
   * <p>
   * The complete {@code ProcessModel} is executed and used for plant-wide power, sizing and bottleneck analysis. The
   * named facility area is retained as the primary process system for backwards-compatible access and connection-point
   * inspection.
   * </p>
   *
   * @param name lifecycle model name
   * @param reservoir mutable reservoir material-balance model
   * @param processModel detailed multi-area SURF and facility model
   * @param facilityAreaName process-area name containing the receiving facility
   * @return connection-point builder
   */
  public static Builder builder(String name, SimpleReservoir reservoir, ProcessModel processModel,
      String facilityAreaName) {
    ProcessSystem facility = requireArea(processModel, facilityAreaName, "facilityAreaName");
    return new Builder(name, reservoir, facility, processModel, facilityAreaName, null, null);
  }

  /**
   * Starts a builder for an existing detailed process plant used as a brownfield host.
   *
   * @param name lifecycle model name
   * @param reservoir new-field reservoir model
   * @param existingProcessSystem existing plant flowsheet, including its equipment constraints
   * @return connection-point builder
   */
  public static Builder existingFacility(String name, SimpleReservoir reservoir,
      ProcessSystem existingProcessSystem) {
    return builder(name, reservoir, existingProcessSystem);
  }

  /** Starts a builder for an existing facility represented by a multi-area process model. */
  public static Builder existingFacility(String name, SimpleReservoir reservoir,
      ProcessModel existingProcessModel, String facilityAreaName) {
    return builder(name, reservoir, existingProcessModel, facilityAreaName);
  }

  /**
   * Starts a builder for a new-field tie-in through separate existing SURF and facility process systems.
   *
   * <p>
   * The two systems must already be connected by shared NeqSim streams. They are assembled into a {@link ProcessModel}
   * so their topology is solved together and bottlenecks in the shared subsea system are visible alongside topsides
   * constraints.
   * </p>
   */
  public static Builder existingSurfAndFacility(String name, SimpleReservoir reservoir,
      ProcessSystem existingSurfSystem, ProcessSystem existingFacilitySystem) {
    require(existingSurfSystem, "existingSurfSystem");
    require(existingFacilitySystem, "existingFacilitySystem");
    if (existingSurfSystem == existingFacilitySystem) {
      throw new IllegalArgumentException("existing SURF and facility process systems must be distinct");
    }
    ProcessModel infrastructure = new ProcessModel();
    infrastructure.add("existing SURF", existingSurfSystem);
    infrastructure.add("existing facility", existingFacilitySystem);
    return new Builder(name, reservoir, existingFacilitySystem, infrastructure,
        "existing facility", existingSurfSystem, "existing SURF");
  }

  /**
   * Starts a builder for an existing SURF-to-facility route already represented by a multi-area process model.
   */
  public static Builder existingSurfAndFacility(String name, SimpleReservoir reservoir,
      ProcessModel existingInfrastructure, String surfAreaName, String facilityAreaName) {
    ProcessSystem surf = requireArea(existingInfrastructure, surfAreaName, "surfAreaName");
    ProcessSystem facility = requireArea(existingInfrastructure, facilityAreaName, "facilityAreaName");
    if (surf == facility) {
      throw new IllegalArgumentException("SURF and facility area names must identify distinct process systems");
    }
    return new Builder(name, reservoir, facility, existingInfrastructure, facilityAreaName, surf,
        surfAreaName);
  }

  /**
   * Creates an assembled lifecycle model.
   *
   * @param name model/concept name
   * @param reservoir reservoir material-balance model
   * @param processSystem wells, SURF and facility process model
   * @param reservoirOilProducer oil producer stream removed from the reservoir
   * @param reservoirWaterProducer water producer stream removed from the reservoir
   * @param reservoirGasInjector gas stream added to the reservoir
   * @param recoveredGas combined produced gas upstream of export/injection allocation
   * @param gasAllocationSplitter splitter allocating gas between export and injection
   * @param stabilizedOilExport stabilized oil product stream
   * @param gasExport sales/export gas stream
   * @param compressedInjectionGas gas stream downstream of injection compression
   */
  public FieldLifecycleModel(String name, SimpleReservoir reservoir, ProcessSystem processSystem,
      StreamInterface reservoirOilProducer, StreamInterface reservoirWaterProducer,
      StreamInterface reservoirGasInjector, StreamInterface recoveredGas, Splitter gasAllocationSplitter,
      StreamInterface stabilizedOilExport, StreamInterface gasExport, StreamInterface compressedInjectionGas) {
    this(name, reservoir, processSystem, reservoirOilProducer, reservoirWaterProducer, reservoirGasInjector,
        recoveredGas, gasAllocationSplitter, stabilizedOilExport, gasExport, compressedInjectionGas, null, null, null);
  }

  /**
   * Creates an assembled lifecycle model with explicit existing-host feed streams for tieback studies.
   *
   * <p>
   * The detailed {@code ProcessSystem} should mix these streams with the new-field SURF arrival stream upstream of
   * shared processing equipment. The lifecycle simulator updates all three host rates every year before solving the
   * combined process.
   * </p>
   *
   * @param name model/concept name
   * @param reservoir reservoir material-balance model
   * @param processSystem wells, SURF and shared-facility process model
   * @param reservoirOilProducer new-field oil producer stream
   * @param reservoirWaterProducer new-field water producer stream
   * @param reservoirGasInjector new-field gas injector stream
   * @param recoveredGas combined recovered gas upstream of allocation
   * @param gasAllocationSplitter gas export/injection splitter
   * @param stabilizedOilExport combined stabilized-oil product stream
   * @param gasExport combined sales-gas stream
   * @param compressedInjectionGas compressed injection-gas stream
   * @param hostOilFeed existing-host oil feed, or null for greenfield
   * @param hostGasFeed existing-host free-gas feed, or null for greenfield
   * @param hostWaterFeed existing-host water feed, or null for greenfield
   */
  public FieldLifecycleModel(String name, SimpleReservoir reservoir, ProcessSystem processSystem,
      StreamInterface reservoirOilProducer, StreamInterface reservoirWaterProducer,
      StreamInterface reservoirGasInjector, StreamInterface recoveredGas, Splitter gasAllocationSplitter,
      StreamInterface stabilizedOilExport, StreamInterface gasExport, StreamInterface compressedInjectionGas,
      StreamInterface hostOilFeed, StreamInterface hostGasFeed, StreamInterface hostWaterFeed) {
    this(name, reservoir, processSystem, reservoirOilProducer, reservoirWaterProducer,
        reservoirGasInjector, recoveredGas, gasAllocationSplitter, stabilizedOilExport, gasExport,
        compressedInjectionGas, hostOilFeed, hostGasFeed, hostWaterFeed, null);
  }

  /** Creates a tieback-capable lifecycle model with an explicit treated-water discharge stream. */
  public FieldLifecycleModel(String name, SimpleReservoir reservoir, ProcessSystem processSystem,
      StreamInterface reservoirOilProducer, StreamInterface reservoirWaterProducer,
      StreamInterface reservoirGasInjector, StreamInterface recoveredGas, Splitter gasAllocationSplitter,
      StreamInterface stabilizedOilExport, StreamInterface gasExport, StreamInterface compressedInjectionGas,
      StreamInterface hostOilFeed, StreamInterface hostGasFeed, StreamInterface hostWaterFeed,
      StreamInterface treatedWaterDischarge) {
    this(name, reservoir, processSystem, null, null, null, null, reservoirOilProducer,
        reservoirWaterProducer, reservoirGasInjector, recoveredGas, gasAllocationSplitter,
        stabilizedOilExport, gasExport, compressedInjectionGas, hostOilFeed, hostGasFeed,
        hostWaterFeed, treatedWaterDischarge);
  }

  private FieldLifecycleModel(String name, SimpleReservoir reservoir, ProcessSystem processSystem,
      ProcessModel processModel, String facilityAreaName, ProcessSystem existingSurfSystem,
      String surfAreaName, StreamInterface reservoirOilProducer,
      StreamInterface reservoirWaterProducer, StreamInterface reservoirGasInjector,
      StreamInterface recoveredGas, Splitter gasAllocationSplitter,
      StreamInterface stabilizedOilExport, StreamInterface gasExport,
      StreamInterface compressedInjectionGas, StreamInterface hostOilFeed,
      StreamInterface hostGasFeed, StreamInterface hostWaterFeed,
      StreamInterface treatedWaterDischarge) {
    this.name = require(name, "name");
    this.reservoir = require(reservoir, "reservoir");
    this.processSystem = require(processSystem, "processSystem");
    this.processModel = processModel;
    this.facilityAreaName = facilityAreaName;
    this.existingSurfSystem = existingSurfSystem;
    this.surfAreaName = surfAreaName;
    this.reservoirOilProducer = require(reservoirOilProducer, "reservoirOilProducer");
    this.reservoirWaterProducer = require(reservoirWaterProducer, "reservoirWaterProducer");
    this.reservoirGasInjector = require(reservoirGasInjector, "reservoirGasInjector");
    this.recoveredGas = require(recoveredGas, "recoveredGas");
    this.gasAllocationSplitter = require(gasAllocationSplitter, "gasAllocationSplitter");
    this.stabilizedOilExport = require(stabilizedOilExport, "stabilizedOilExport");
    this.gasExport = require(gasExport, "gasExport");
    this.compressedInjectionGas = require(compressedInjectionGas, "compressedInjectionGas");
    this.hostOilFeed = hostOilFeed;
    this.hostGasFeed = hostGasFeed;
    this.hostWaterFeed = hostWaterFeed;
    this.treatedWaterDischarge = treatedWaterDischarge;
  }

  private static <T> T require(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static ProcessSystem requireArea(ProcessModel processModel, String areaName,
      String parameterName) {
    require(processModel, "processModel");
    require(areaName, parameterName);
    ProcessSystem area = processModel.get(areaName);
    if (area == null) {
      throw new IllegalArgumentException(parameterName + " '" + areaName
          + "' is not present in process model; available areas: "
          + processModel.getProcessSystemNames());
    }
    return area;
  }

  /** Returns the model or concept name. */
  public String getName() {
    return name;
  }

  /** Returns the mutable reservoir material-balance model. */
  public SimpleReservoir getReservoir() {
    return reservoir;
  }

  /** Returns the assembled wells, SURF and process-plant flowsheet. */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /** Returns the complete multi-area process model, or null for a single-process lifecycle model. */
  public ProcessModel getProcessModel() {
    return processModel;
  }

  /** Returns whether lifecycle execution covers a complete multi-area process model. */
  public boolean hasProcessModel() {
    return processModel != null;
  }

  /** Returns the receiving facility area name, or null for a single-process model. */
  public String getFacilityAreaName() {
    return facilityAreaName;
  }

  /** Returns the explicit existing SURF process system, or null when one was not identified separately. */
  public ProcessSystem getExistingSurfSystem() {
    return existingSurfSystem;
  }

  /** Returns whether an existing shared SURF process was identified as part of the tie-in route. */
  public boolean hasExistingSurfSystem() {
    return existingSurfSystem != null;
  }

  /** Returns the existing SURF process-area name, or null when it was not identified separately. */
  public String getSurfAreaName() {
    return surfAreaName;
  }

  /** Returns the oil-producing stream removed from the reservoir. */
  public StreamInterface getReservoirOilProducer() {
    return reservoirOilProducer;
  }

  /** Returns the water-producing stream removed from the reservoir. */
  public StreamInterface getReservoirWaterProducer() {
    return reservoirWaterProducer;
  }

  /** Returns the compressed-gas stream added to the reservoir. */
  public StreamInterface getReservoirGasInjector() {
    return reservoirGasInjector;
  }

  /** Returns recovered gas upstream of sales and injection allocation. */
  public StreamInterface getRecoveredGas() {
    return recoveredGas;
  }

  /** Returns the splitter that allocates recovered gas. */
  public Splitter getGasAllocationSplitter() {
    return gasAllocationSplitter;
  }

  /** Returns the stabilized oil export stream. */
  public StreamInterface getStabilizedOilExport() {
    return stabilizedOilExport;
  }

  /** Returns the compressed sales-gas export stream. */
  public StreamInterface getGasExport() {
    return gasExport;
  }

  /** Returns the gas stream downstream of injection compression. */
  public StreamInterface getCompressedInjectionGas() {
    return compressedInjectionGas;
  }

  /** Returns true when the detailed process exposes all existing-host feed streams. */
  public boolean hasHostProductionFeeds() {
    return hostOilFeed != null && hostGasFeed != null && hostWaterFeed != null;
  }

  /** Returns the existing-host oil feed, or null for a greenfield model. */
  public StreamInterface getHostOilFeed() {
    return hostOilFeed;
  }

  /** Returns the existing-host free-gas feed, or null for a greenfield model. */
  public StreamInterface getHostGasFeed() {
    return hostGasFeed;
  }

  /** Returns the existing-host water feed, or null for a greenfield model. */
  public StreamInterface getHostWaterFeed() {
    return hostWaterFeed;
  }

  /** Returns treated-water discharge stream, or null when quality is supplied by a custom provider. */
  public StreamInterface getTreatedWaterDischarge() {
    return treatedWaterDischarge;
  }

  /**
   * Sets an optional detailed-well, network, reservoir-schedule or surrogate production-potential provider.
   *
   * @param provider provider to use instead of the default linear PI/water-cut calculation
   * @return this model
   */
  public FieldLifecycleModel setProductionPotentialProvider(FieldProductionPotentialProvider provider) {
    productionPotentialProvider = provider;
    return this;
  }

  /** Returns the optional custom production-potential provider. */
  public FieldProductionPotentialProvider getProductionPotentialProvider() {
    return productionPotentialProvider;
  }

  /** Sets an optional process analyser or external product-quality provider. */
  public FieldLifecycleModel setProductQualityProvider(FieldProductQualityProvider provider) {
    productQualityProvider = provider;
    return this;
  }

  /** Returns the optional custom product-quality provider. */
  public FieldProductQualityProvider getProductQualityProvider() {
    return productQualityProvider;
  }

  /** Builder that maps lifecycle roles to streams in a detailed process system or process model. */
  public static final class Builder {
    private final String name;
    private final SimpleReservoir reservoir;
    private final ProcessSystem processSystem;
    private final ProcessModel processModel;
    private final String facilityAreaName;
    private final ProcessSystem existingSurfSystem;
    private final String surfAreaName;
    private StreamInterface reservoirOilProducer;
    private StreamInterface reservoirWaterProducer;
    private StreamInterface reservoirGasInjector;
    private StreamInterface recoveredGas;
    private Splitter gasAllocationSplitter;
    private StreamInterface stabilizedOilExport;
    private StreamInterface gasExport;
    private StreamInterface compressedInjectionGas;
    private StreamInterface hostOilFeed;
    private StreamInterface hostGasFeed;
    private StreamInterface hostWaterFeed;
    private StreamInterface treatedWaterDischarge;
    private FieldProductionPotentialProvider productionPotentialProvider;
    private FieldProductQualityProvider productQualityProvider;

    private Builder(String name, SimpleReservoir reservoir, ProcessSystem processSystem,
        ProcessModel processModel, String facilityAreaName, ProcessSystem existingSurfSystem,
        String surfAreaName) {
      this.name = require(name, "name");
      this.reservoir = require(reservoir, "reservoir");
      this.processSystem = require(processSystem, "processSystem");
      this.processModel = processModel;
      this.facilityAreaName = facilityAreaName;
      this.existingSurfSystem = existingSurfSystem;
      this.surfAreaName = surfAreaName;
    }

    /** Maps new-field oil/water production and gas-injection reservoir streams. */
    public Builder reservoirStreams(StreamInterface oilProducer, StreamInterface waterProducer,
        StreamInterface gasInjector) {
      reservoirOilProducer = oilProducer;
      reservoirWaterProducer = waterProducer;
      reservoirGasInjector = gasInjector;
      return this;
    }

    /** Maps recovered gas, its export/injection splitter, and compressed injection gas. */
    public Builder gasHandling(StreamInterface recoveredGasStream, Splitter allocationSplitter,
        StreamInterface compressedGasForInjection) {
      recoveredGas = recoveredGasStream;
      gasAllocationSplitter = allocationSplitter;
      compressedInjectionGas = compressedGasForInjection;
      return this;
    }

    /** Maps stabilized-oil and sales-gas product streams. */
    public Builder exportStreams(StreamInterface oilExportStream, StreamInterface gasExportStream) {
      stabilizedOilExport = oilExportStream;
      gasExport = gasExportStream;
      return this;
    }

    /**
     * Maps existing-host oil, free-gas and water feeds connected upstream of shared equipment.
     *
     * <p>
     * The feeds may enter the existing SURF process or the receiving facility. Their location in the user-built
     * flowsheet determines which shared hydraulic and processing equipment sees the host load.
     * </p>
     */
    public Builder hostFeeds(StreamInterface oilFeed, StreamInterface gasFeed,
        StreamInterface waterFeed) {
      hostOilFeed = oilFeed;
      hostGasFeed = gasFeed;
      hostWaterFeed = waterFeed;
      return this;
    }

    /** Maps the treated-water discharge stream used for oil-in-water compliance. */
    public Builder treatedWaterDischarge(StreamInterface stream) {
      treatedWaterDischarge = stream;
      return this;
    }

    /** Sets a detailed well/network or external reservoir-schedule provider. */
    public Builder productionPotentialProvider(FieldProductionPotentialProvider provider) {
      productionPotentialProvider = provider;
      return this;
    }

    /** Sets a process-specific product and discharge analyser provider. */
    public Builder productQualityProvider(FieldProductQualityProvider provider) {
      productQualityProvider = provider;
      return this;
    }

    /** Validates every required connection point and creates the lifecycle model. */
    public FieldLifecycleModel build() {
      boolean hasAnyHostFeed = hostOilFeed != null || hostGasFeed != null || hostWaterFeed != null;
      boolean hasAllHostFeeds = hostOilFeed != null && hostGasFeed != null && hostWaterFeed != null;
      if (hasAnyHostFeed && !hasAllHostFeeds) {
        throw new IllegalArgumentException("host oil, gas, and water feeds must be supplied together");
      }
      FieldLifecycleModel model = new FieldLifecycleModel(name, reservoir, processSystem,
          processModel, facilityAreaName, existingSurfSystem, surfAreaName, reservoirOilProducer,
          reservoirWaterProducer, reservoirGasInjector, recoveredGas, gasAllocationSplitter,
          stabilizedOilExport, gasExport, compressedInjectionGas, hostOilFeed, hostGasFeed,
          hostWaterFeed, treatedWaterDischarge);
      model.setProductionPotentialProvider(productionPotentialProvider);
      model.setProductQualityProvider(productQualityProvider);
      return model;
    }
  }
}
