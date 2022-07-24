package neqsim.processSimulation.processEquipment.util;

import java.util.UUID;
import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * StreamSaturatorUtil class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class StreamSaturatorUtil extends TwoPortEquipment {
  private static final long serialVersionUID = 1000;

  SystemInterface thermoSystem;
  private boolean multiPhase = true;

  /**
   * <p>
   * Constructor for StreamSaturatorUtil.
   * </p>
   *
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public StreamSaturatorUtil(StreamInterface inletStream) {
    this("StreamSaturatorUtil", inletStream);
  }

  /**
   * Constructor for StreamSaturatorUtil.
   *
   * @param name name of unit opeation
   * @param inStream input stream
   */
  public StreamSaturatorUtil(String name, StreamInterface inStream) {
    super(name);
    setInletStream(inStream);
  }

  /**
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   *
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;

    thermoSystem = inletStream.getThermoSystem().clone();
    outStream = new Stream("outStream", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    boolean changeBack = false;
    thermoSystem = inStream.getThermoSystem().clone();
    if (multiPhase && !thermoSystem.doMultiPhaseCheck()) {
      thermoSystem.setMultiPhaseCheck(true);
      changeBack = true;
    }
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.saturateWithWater();
    thermoSystem.init(3);
    if (changeBack) {
      thermoSystem.setMultiPhaseCheck(false);
    }
    outStream.setThermoSystem(thermoSystem);
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * isMultiPhase.
   * </p>
   *
   * @return a boolean
   */
  public boolean isMultiPhase() {
    return multiPhase;
  }

  /**
   * <p>
   * Setter for the field <code>multiPhase</code>.
   * </p>
   *
   * @param multiPhase a boolean
   */
  public void setMultiPhase(boolean multiPhase) {
    this.multiPhase = multiPhase;
  }
}
