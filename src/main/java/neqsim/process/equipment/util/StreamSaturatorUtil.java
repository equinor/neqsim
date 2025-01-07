package neqsim.process.equipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * StreamSaturatorUtil class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class StreamSaturatorUtil extends TwoPortEquipment {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(StreamSaturatorUtil.class);
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  SystemInterface thermoSystem;
  private boolean multiPhase = true;
  private double approachToSaturation = 1.0;

  protected double oldInletFlowRate = 0.0;

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
   * {@inheritDoc}
   *
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;

    thermoSystem = inletStream.getThermoSystem().clone();
    outStream = new Stream("outStream", thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public boolean needRecalculation() {
    if (outStream == null || inStream == null) {
      return true;
    }
    if (inStream.getTemperature() == outStream.getTemperature()
        && inStream.getPressure() == outStream.getPressure()
        && Math.abs(inStream.getFlowRate("kg/hr") - oldInletFlowRate)
            / inStream.getFlowRate("kg/hr") < 1e-3) {
      return false;
    } else {
      return true;
    }
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

    if (thermoSystem.getPhase(0).hasComponent("water") && approachToSaturation < 1.0) {
      try {
        thermoSystem.addComponent("water",
            -thermoSystem.getComponent("water").getNumberOfmoles() * (1.0 - approachToSaturation));
        thermoOps.TPflash();
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }

    thermoSystem.init(3);
    if (changeBack) {
      thermoSystem.setMultiPhaseCheck(false);
    }

    outStream.setThermoSystem(thermoSystem);
    oldInletFlowRate = inStream.getFlowRate("kg/hr");
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

  /**
   * <p>
   * setApprachToSaturation.
   * </p>
   *
   * @param approachToSaturation a double
   */
  public void setApprachToSaturation(double approachToSaturation) {
    this.approachToSaturation = approachToSaturation;
  }
}
