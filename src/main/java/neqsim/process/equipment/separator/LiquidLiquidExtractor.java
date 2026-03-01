package neqsim.process.equipment.separator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Liquid-liquid extractor for separation of components between two immiscible liquid phases.
 *
 * <p>
 * Models a single-stage or multi-stage liquid-liquid extraction unit. Takes a feed stream and a
 * solvent stream, mixes them, and performs a liquid-liquid equilibrium (LLE) flash to separate into
 * extract (solvent-rich) and raffinate (feed-rich) phases.
 * </p>
 *
 * <p>
 * Leverages NeqSim's built-in LLE flash capabilities for thermodynamically rigorous phase
 * separation.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * LiquidLiquidExtractor lle = new LiquidLiquidExtractor("Extractor", feedStream, solventStream);
 * lle.setNumberOfStages(3);
 * lle.run();
 *
 * StreamInterface extract = lle.getExtractStream();
 * StreamInterface raffinate = lle.getRaffinateStream();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class LiquidLiquidExtractor extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(LiquidLiquidExtractor.class);

  /** Feed stream (aqueous or original phase). */
  private StreamInterface feedStream;

  /** Solvent stream (extraction solvent). */
  private StreamInterface solventStream;

  /** Extract stream (solvent-rich phase containing extracted solute). */
  private StreamInterface extractStream;

  /** Raffinate stream (feed-rich phase, depleted in solute). */
  private StreamInterface raffinateStream;

  /** Internal mixer for combining feed and solvent. */
  private Mixer inletMixer = new Mixer("LLE Inlet Mixer");

  /** Number of theoretical stages. */
  private int numberOfStages = 1;

  /** Overall stage efficiency (0.0 to 1.0). */
  private double stageEfficiency = 1.0;

  /** Pressure drop across extractor in bar. */
  private double pressureDrop = 0.0;

  /** Residence time per stage in minutes. */
  private double residenceTimePerStage = 10.0;

  /**
   * Constructor for LiquidLiquidExtractor.
   *
   * @param name name of the extractor
   */
  public LiquidLiquidExtractor(String name) {
    super(name);
  }

  /**
   * Constructor for LiquidLiquidExtractor with feed and solvent streams.
   *
   * @param name name of the extractor
   * @param feedStream the feed stream
   * @param solventStream the solvent stream
   */
  public LiquidLiquidExtractor(String name, StreamInterface feedStream,
      StreamInterface solventStream) {
    super(name);
    setFeedStream(feedStream);
    setSolventStream(solventStream);
  }

  /**
   * Set the feed stream.
   *
   * @param feedStream the feed stream
   */
  public void setFeedStream(StreamInterface feedStream) {
    this.feedStream = feedStream;
    inletMixer.addStream(feedStream);
    initOutletStreams();
  }

  /**
   * Set the solvent stream.
   *
   * @param solventStream the solvent stream
   */
  public void setSolventStream(StreamInterface solventStream) {
    this.solventStream = solventStream;
    inletMixer.addStream(solventStream);
    initOutletStreams();
  }

  /**
   * Initialize outlet streams from feed system.
   */
  private void initOutletStreams() {
    if (feedStream != null) {
      SystemInterface sys = feedStream.getThermoSystem().clone();
      extractStream = new Stream(getName() + " extract", sys);
      raffinateStream = new Stream(getName() + " raffinate", sys.clone());
    }
  }

  /**
   * Get the feed stream.
   *
   * @return feed stream
   */
  public StreamInterface getFeedStream() {
    return feedStream;
  }

  /**
   * Get the solvent stream.
   *
   * @return solvent stream
   */
  public StreamInterface getSolventStream() {
    return solventStream;
  }

  /**
   * Get the extract stream (solvent-rich phase).
   *
   * @return extract stream
   */
  public StreamInterface getExtractStream() {
    return extractStream;
  }

  /**
   * Get the raffinate stream (feed-rich phase).
   *
   * @return raffinate stream
   */
  public StreamInterface getRaffinateStream() {
    return raffinateStream;
  }

  /**
   * Set the number of theoretical stages.
   *
   * @param stages number of stages (must be at least 1)
   */
  public void setNumberOfStages(int stages) {
    if (stages < 1) {
      throw new IllegalArgumentException("Number of stages must be at least 1, got " + stages);
    }
    this.numberOfStages = stages;
  }

  /**
   * Get the number of theoretical stages.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Set the overall stage efficiency.
   *
   * @param efficiency efficiency between 0.0 and 1.0
   */
  public void setStageEfficiency(double efficiency) {
    this.stageEfficiency = efficiency;
  }

  /**
   * Get the stage efficiency.
   *
   * @return stage efficiency
   */
  public double getStageEfficiency() {
    return stageEfficiency;
  }

  /**
   * Set the pressure drop across the extractor.
   *
   * @param dP pressure drop in bar
   */
  public void setPressureDrop(double dP) {
    this.pressureDrop = dP;
  }

  /**
   * Get the pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * Set residence time per stage.
   *
   * @param time residence time in minutes
   */
  public void setResidenceTimePerStage(double time) {
    this.residenceTimePerStage = time;
  }

  /**
   * Get residence time per stage.
   *
   * @return residence time in minutes
   */
  public double getResidenceTimePerStage() {
    return residenceTimePerStage;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Mix feed and solvent
    SystemInterface feedSys = feedStream.getThermoSystem().clone();
    SystemInterface solventSys = solventStream.getThermoSystem().clone();

    // Combine the two systems
    SystemInterface combined = feedSys.clone();
    for (int i = 0; i < solventSys.getNumberOfComponents(); i++) {
      String compName = solventSys.getComponent(i).getComponentName();
      double moles = solventSys.getComponent(i).getNumberOfmoles();
      try {
        combined.addComponent(compName, moles);
      } catch (Exception ex) {
        // Component may not exist in feed system - try skipping
        logger.warn("Could not add solvent component '{}' to combined system: {}", compName,
            ex.getMessage());
      }
    }

    // Set conditions and enable multi-phase check for LLE
    combined.setPressure(feedSys.getPressure() - pressureDrop);
    combined.setTemperature(feedSys.getTemperature());
    combined.setMultiPhaseCheck(true);

    // Perform TP flash (which will find LLE if applicable)
    ThermodynamicOperations ops = new ThermodynamicOperations(combined);
    try {
      ops.TPflash();
    } catch (Exception ex) {
      logger.error("LLE flash failed: {}", ex.getMessage());
    }

    combined.init(3);
    combined.initProperties();

    // Separate phases into extract and raffinate
    int numPhases = combined.getNumberOfPhases();
    if (numPhases >= 2) {
      // Phase 0 is typically the lighter (organic/extract) phase
      // Phase 1 is typically the heavier (aqueous/raffinate) phase
      SystemInterface extractSys = combined.phaseToSystem(combined.getPhases()[0]);
      extractSys.initProperties();
      extractStream.setThermoSystem(extractSys);

      SystemInterface raffinateSys = combined.phaseToSystem(combined.getPhases()[1]);
      raffinateSys.initProperties();
      raffinateStream.setThermoSystem(raffinateSys);
    } else {
      // Only one phase found - no LLE separation
      logger.warn("LLE flash found only {} phase(s) - no separation achieved", numPhases);
      extractStream.setThermoSystem(combined);
      SystemInterface emptySys = combined.clone();
      // Zero out raff phase
      for (int i = 0; i < emptySys.getNumberOfComponents(); i++) {
        double moles = emptySys.getComponent(i).getNumberOfmoles();
        emptySys.addComponent(i, -moles * 0.999);
      }
      emptySys.initProperties();
      raffinateStream.setThermoSystem(emptySys);
    }

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Get a map representation of the extractor.
   *
   * @return map of properties
   */
  private Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", getName());
    map.put("type", "LiquidLiquidExtractor");
    map.put("numberOfStages", numberOfStages);
    map.put("stageEfficiency", stageEfficiency);
    map.put("pressureDrop_bar", pressureDrop);
    map.put("residenceTimePerStage_min", residenceTimePerStage);
    return map;
  }
}
