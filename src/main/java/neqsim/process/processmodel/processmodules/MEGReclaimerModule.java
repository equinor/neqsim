package neqsim.process.processmodel.processmodules;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModuleBaseClass;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * MEGReclaimerModule class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class MEGReclaimerModule extends ProcessModuleBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MEGReclaimerModule.class);

  protected StreamInterface streamToReclaimer = null;
  protected StreamInterface streamToWaterRemoval = null;
  protected StreamInterface streamFromBoosterCompressor = null;
  protected StreamInterface streamWithWaste = null;
  ThrottlingValve inletValve = null;
  Mixer inletMixer = null;
  protected Separator flashSeparator = null;
  Pump MEGRecircPump = null;
  Heater MEGrecircHeater = null;
  ThrottlingValve recircValve = null;
  Heater vacumCooler = null;

  double reclaimerPressure = 0.17;

  /**
   * <p>
   * Constructor for MEGReclaimerModule.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public MEGReclaimerModule(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void addInputStream(String streamName, StreamInterface stream) {
    if (streamName.equals("streamToReclaimer")) {
      this.streamToReclaimer = stream;
    }
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutputStream(String streamName) {
    if (!isInitializedStreams) {
      initializeStreams();
    }
    if (streamName.equals("streamToWaterRemoval")) {
      return this.streamToWaterRemoval;
    } else {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initializeStreams() {
    isInitializedStreams = true;
    try {
      this.streamToWaterRemoval = this.streamToReclaimer.clone("Desalted MEG stream");
      this.streamFromBoosterCompressor =
          this.streamToReclaimer.clone("Stream from Booster Compressor");
      this.streamWithWaste = this.streamToReclaimer.clone("Reclaimer Waste Stream");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initializeModule() {
    isInitializedModule = true;

    inletValve = new ThrottlingValve("inletValve", streamToReclaimer);
    inletValve.setOutletPressure(reclaimerPressure);
    inletValve.setIsoThermal(true);

    inletMixer = new Mixer("inletMixer");
    inletMixer.addStream(inletValve.getOutletStream());

    flashSeparator = new Separator("flashSeparator", inletMixer.getOutletStream());

    MEGRecircPump = new Pump("MEGRecircPump", flashSeparator.getLiquidOutStream());
    MEGRecircPump.setMolarFlow(50.0);
    MEGRecircPump.setOutletPressure(5.0);

    MEGrecircHeater = new Heater("MEGrecircHeater", MEGRecircPump.getOutletStream());
    // MEGrecircHeater.setEnergyInput(5000.0);
    MEGrecircHeater.setOutTemperature(273 + 68.9);

    recircValve = new ThrottlingValve("recircValve", MEGrecircHeater.getOutletStream());
    recircValve.setOutletPressure(reclaimerPressure);
    recircValve.setIsoThermal(true);

    inletMixer.addStream(recircValve.getOutletStream());

    vacumCooler = new Heater("vacumCooler", flashSeparator.getGasOutStream());

    getOperations().add(streamToReclaimer);
    getOperations().add(inletValve);
    getOperations().add(inletMixer);
    getOperations().add(flashSeparator);
    getOperations().add(MEGRecircPump);
    getOperations().add(MEGrecircHeater);
    getOperations().add(recircValve);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (!isInitializedModule) {
      initializeModule();
    }
    for (int i = 0; i < 2; i++) {
      getOperations().run(id);
      flashSeparator.displayResult();
      System.out.println("flow to vacuum separator "
          + inletMixer.getOutletStream().getThermoSystem().getTotalNumberOfMoles());
    }

    streamToWaterRemoval = flashSeparator.getGasOutStream();
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * setOperationPressure.
   * </p>
   *
   * @param pressure a double
   */
  public void setOperationPressure(double pressure) {
    reclaimerPressure = pressure;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 30.0), 10.0);

    testSystem.addComponent("methane", 0.001);
    testSystem.addComponent("CO2", 0.001);
    testSystem.addComponent("MEG", 0.3);
    testSystem.addComponent("water", 0.7);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    Stream inletStream = new Stream("inletStream", testSystem);
    inletStream.run();
    inletStream.displayResult();
    MEGReclaimerModule reclaimer = new MEGReclaimerModule("reclaimer");
    reclaimer.addInputStream("streamToReclaimer", inletStream);
    reclaimer.setOperationPressure(0.17);

    reclaimer.run();
    // reclaimer.displayResult();
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    // design is done here
  }

  /** {@inheritDoc} */
  @Override
  public void setDesign() {
    // set design is done here
  }
}
