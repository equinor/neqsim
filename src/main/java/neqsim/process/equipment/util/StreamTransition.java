package neqsim.process.equipment.util;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * StreamTransition class.
 * </p>
 *
 * <p>
 * This class provides functionality for transferring fluid properties between streams with
 * potentially different thermodynamic models and component sets. It enables seamless transitions
 * between process units that may use different thermodynamic frameworks.
 * </p>
 *
 * <p>
 * <b>Note:</b> Currently, transitions are performed on a temperature-pressure (TP) basis only.
 * </p>
 *
 * <p>
 * TODO: Consider implementing pressure-enthalpy (PH) based transitions as an alternative mode of
 * operation for cases where energy conservation is critical.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StreamTransition extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for StreamTransition.
   * </p>
   *
   * @param name name of unit operation
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   * @param outletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamTransition(String name, StreamInterface inletStream, StreamInterface outletStream) {
    super(name);
    this.inStream = inletStream;
    this.outStream = outletStream;
  }

  /** {@inheritDoc} */
  /**
   * {@inheritDoc}
   *
   * <p>
   * Performs the stream transition by transferring properties from the inlet stream to the outlet
   * stream. Only components that exist in both systems will be transferred.
   * </p>
   *
   * <p>
   * <b>Note:</b> Currently operates on TP (temperature-pressure) basis only.
   * </p>
   *
   * <p>
   * TODO: Add support for PH (pressure-enthalpy) based transitions in the future.
   * </p>
   */
  @Override
  public void run(UUID id) {
    SystemInterface outThermoSystem = null;
    if (outStream != null) {
      outThermoSystem = outStream.getFluid().clone();
    } else {
      outThermoSystem = inStream.getFluid().clone();
    }
    outThermoSystem.setEmptyFluid();
    outThermoSystem.setTemperature(inStream.getTemperature());
    outThermoSystem.setPressure(inStream.getPressure());

    for (int i = 0; i < inStream.getFluid().getNumberOfComponents(); i++) {
      if (outThermoSystem.getPhase(0).hasComponent(inStream.getFluid().getComponent(i).getName())) {
        outThermoSystem.addComponent(inStream.getFluid().getComponent(i).getName(),
            inStream.getFluid().getComponent(i).getNumberOfmoles());
      }
    }
    outStream.setThermoSystem(outThermoSystem);
    outStream.run(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    outStream.getFluid().display();
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
    ProcessSystem offshoreProcessoperations = ProcessSystem.open("c:/temp/offshorePro.neqsim");
    ProcessSystem TEGprocess = ProcessSystem.open("c:/temp//TEGprocessHX.neqsim");
    StreamTransition trans =
        new StreamTransition("tmp", (StreamInterface) offshoreProcessoperations.getUnit("rich gas"),
            (StreamInterface) TEGprocess.getUnit("dry feed gas"));

    UUID id = UUID.randomUUID();

    offshoreProcessoperations.run(id);
    trans.run(id);
    offshoreProcessoperations.getUnit("rich gas").displayResult();
    // ((StreamInterface) TEGprocess.getUnit("dry feed gas")).displayResult();
    trans.displayResult();
    TEGprocess.run(id);
    TEGprocess.getUnit("dry feed gas").displayResult();

    // ((StreamInterface) TEGprocess.getUnit("dry feed gas")).displayResult();
  }
}
