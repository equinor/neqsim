package neqsim.process.processmodel.processmodules;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModuleBaseClass;

/**
 * <p>
 * CO2RemovalModule class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CO2RemovalModule extends ProcessModuleBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CO2RemovalModule.class);

  protected StreamInterface streamToAbsorber = null, streamFromAbsorber = null,
      gasFromCO2Stripper = null;

  protected Separator inletSeparator = null;

  /**
   * <p>
   * Constructor for CO2RemovalModule.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public CO2RemovalModule(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void addInputStream(String streamName, StreamInterface stream) {
    if (streamName.equals("streamToAbsorber")) {
      this.streamToAbsorber = stream;
    }
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutputStream(String streamName) {
    if (!isInitializedStreams) {
      initializeStreams();
    }
    if (streamName.equals("streamFromAbsorber")) {
      return this.streamFromAbsorber;
    } else {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (!isInitializedModule) {
      initializeModule();
    }
    getOperations().run(id);

    streamFromAbsorber = inletSeparator.getGasOutStream().clone();
    streamFromAbsorber.getThermoSystem().addComponent("CO2", -streamFromAbsorber.getThermoSystem()
        .getPhase(0).getComponent("CO2").getNumberOfMolesInPhase() * 0.99);
    streamFromAbsorber.getThermoSystem().addComponent("MEG", -streamFromAbsorber.getThermoSystem()
        .getPhase(0).getComponent("MEG").getNumberOfMolesInPhase() * 0.99);
    streamFromAbsorber.getThermoSystem().init(1);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void initializeStreams() {
    isInitializedStreams = true;
    try {
      this.streamFromAbsorber = this.streamToAbsorber.clone("Stream from Absorber");
      this.gasFromCO2Stripper = this.streamToAbsorber.clone("Gas stream from Stripper");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initializeModule() {
    isInitializedModule = true;
    inletSeparator = new Separator("inletSeparator", streamToAbsorber);

    getOperations().add(inletSeparator);
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
