package neqsim.process.equipment.separator;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TwoPhaseSeparator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TwoPhaseSeparator extends Separator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  SystemInterface thermoSystem;

  SystemInterface gasSystem;
  SystemInterface waterSystem;
  SystemInterface liquidSystem;
  SystemInterface thermoSystemCloned;

  StreamInterface inletStream;
  StreamInterface gasOutStream;
  StreamInterface liquidOutStream;

  /**
   * Constructor for TwoPhaseSeparator.
   *
   * @param name name of separator
   */
  public TwoPhaseSeparator(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for TwoPhaseSeparator.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public TwoPhaseSeparator(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;

    thermoSystem = inletStream.getThermoSystem().clone();
    gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
    gasOutStream = new Stream("gasOutStream", gasSystem);

    thermoSystem = inletStream.getThermoSystem().clone();
    liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    liquidOutStream = new Stream("liquidOutStream", liquidSystem);
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGas() {
    return getGasOutStream();
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getLiquid() {
    return getLiquidOutStream();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    thermoSystem = inletStream.getThermoSystem().clone();
    gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
    gasSystem.setNumberOfPhases(1);
    gasOutStream.setThermoSystem(gasSystem);

    thermoSystem = inletStream.getThermoSystem().clone();
    liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    liquidSystem.setNumberOfPhases(1);
    liquidOutStream.setThermoSystem(liquidSystem);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}
}
