package neqsim.process.equipment.separator;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Hydrocyclone class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Hydrocyclone extends Separator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double PDR = 1.4;
  double rejectRatio = 0.9;
  double underflowPressure = 1.0;
  double overflowPressure = 1.0;
  double separationEfficiency = 0.9;
  double oilInAqueous = 100e-6;
  StreamInterface waterOutStream = new Stream("waterOutStream", waterSystem);

  /**
   * Constructor for Hydrocyclone.
   *
   * @param name name of hydrosycolen
   */
  public Hydrocyclone(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Hydrocyclone.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Hydrocyclone(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    super.setInletStream(inletStream);

    thermoSystem = inletStream.getThermoSystem().clone();
    waterSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    waterOutStream = new Stream("waterOutStream", waterSystem);
  }

  /**
   * <p>
   * Getter for the field <code>waterOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getWaterOutStream() {
    return waterOutStream;
  }

  /**
   * <p>
   * getOilOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOilOutStream() {
    return liquidOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    inletStreamMixer.run(id);
    thermoSystem = inletStreamMixer.getOutletStream().getThermoSystem().clone();

    // double oilInWaterIn = 0.0001;
    // thermoSystem.getPhase("aqueous").getOilMolarConcentration();
    // double oilInWaterOut = 0.0;
    double inPressure = thermoSystem.getPressure("bara");
    underflowPressure = inPressure / 2.0;
    overflowPressure = inPressure + (inPressure - underflowPressure) / 1.0 / PDR;
    separationEfficiency = 0.9;
    // oilInWaterOut = oilInWaterIn * separationEfficiency;

    thermoSystem.setMultiPhaseCheck(true);
    thermoSystem.setPressure(underflowPressure);
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.TPflash();

    thermoSystem.addPhaseFractionToPhase(oilInAqueous, "mole", "oil", "aqueous");
    // thermoOps.TPflash();
    thermoSystem.addPhaseFractionToPhase(1.0, "mole", "oil", "gas");
    thermoSystem.addPhaseFractionToPhase(0.02, "mole", "aqueous", "gas");
    // thermoOps.TPflash();

    if (thermoSystem.hasPhaseType("gas")) {
      gasOutStream.setThermoSystemFromPhase(thermoSystem, "gas");
    } else {
      gasOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
    }

    // liquidSystem = thermoSystem.phaseToSystem(1);
    // liquidOutStream.setThermoSystem(liquidSystem);
    if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
      neqsim.thermo.system.SystemInterface fluid =
          thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
      liquidOutStream.setThermoSystemFromPhase(fluid, "liquid");
      liquidOutStream.getFluid().initProperties();
    } else {
      liquidOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
    }
    // sOutStream.setPressure(overflowPressure, "bara");
    gasOutStream.run(id);
    liquidOutStream.run(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    thermoSystem.display("from here " + getName());
  }
}
