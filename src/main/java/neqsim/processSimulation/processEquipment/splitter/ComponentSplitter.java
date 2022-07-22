package neqsim.processSimulation.processEquipment.splitter;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Splitter class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSplitter extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(ComponentSplitter.class);

  SystemInterface thermoSystem;
  StreamInterface inletStream;
  StreamInterface[] splitStream;
  protected int splitNumber = 1;
  double[] splitFactor = new double[1];

  /**
   * <p>
   * Constructor for Splitter.
   * </p>
   */
  public ComponentSplitter() {
    super("Component Splitter");
  }

  /**
   * Constructor for Splitter.
   * 
   * @param name name of splitter
   */
  public ComponentSplitter(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Splitter.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   *
   */
  public ComponentSplitter(String name, StreamInterface inletStream) {
    this(name);
    this.setInletStream(inletStream);
  }

  public void setSplitFactors(double[] factors) {
    splitFactor = factors;
  }

  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    splitStream = new Stream[2];
    try {
      for (int i = 0; i < splitStream.length; i++) {
        splitStream[i] = new Stream("Split Stream", inletStream.getThermoSystem().clone());
      }
    } catch (Exception e) {
      logger.error(e.getMessage());
    }
  }

  public StreamInterface getSplitStream(int i) {
    return splitStream[i];
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    for (int i = 0; i < 2; i++) {
      thermoSystem = inletStream.getThermoSystem().clone();
      thermoSystem.setEmptyFluid();
      if (i == 0) {
        for (int k = 0; k < thermoSystem.getNumberOfComponents(); k++) {
          thermoSystem.addComponent(k,
              inletStream.getThermoSystem().getComponent(k).getNumberOfmoles() * splitFactor[k]);
        }
      } else {
        for (int k = 0; k < thermoSystem.getNumberOfComponents(); k++) {
          thermoSystem.addComponent(k,
              inletStream.getThermoSystem().getComponent(k).getNumberOfmoles()
                  * (1.0 - splitFactor[k]));
        }
      }

      thermoSystem.init(0);
      splitStream[i].setThermoSystem(thermoSystem);
      ThermodynamicOperations thermoOps =
          new ThermodynamicOperations(splitStream[i].getThermoSystem());
      thermoOps.TPflash();
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
  }
}
