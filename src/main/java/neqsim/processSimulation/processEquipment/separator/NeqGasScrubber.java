package neqsim.processSimulation.processEquipment.separator;

import java.util.ArrayList;
import java.util.UUID;
import neqsim.processSimulation.mechanicalDesign.separator.GasScrubberMechanicalDesign;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * NeqGasScrubber class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NeqGasScrubber extends Separator {
  private static final long serialVersionUID = 1000;

  SystemInterface thermoSystem;
  SystemInterface gasSystem;
  SystemInterface waterSystem;
  SystemInterface liquidSystem;
  SystemInterface thermoSystemCloned;
  ArrayList<SeparatorSection> scrubberSection = null;
  StreamInterface inletStream;
  StreamInterface gasOutStream;
  StreamInterface liquidOutStream;
  String name = new String();

  /**
   * <p>
   * Constructor for NeqGasScrubber.
   * </p>
   */
  @Deprecated
  public NeqGasScrubber() {
    this("NeqGasScrubber");
  }

  /**
   * <p>
   * Constructor for NeqGasScrubber.
   * </p>
   *
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public NeqGasScrubber(StreamInterface inletStream) {
    this("NeqGasScrubber", inletStream);
  }

  /**
   * Constructor for NeqGasScrubber.
   *
   * @param name name of unit operation
   */
  public NeqGasScrubber(String name) {
    super(name);
    this.setOrientation("vertical");
  }

  /**
   * <p>
   * Constructor for NeqGasScrubber.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public NeqGasScrubber(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.setOrientation("vertical");
  }


  /**
   * @return GasScrubberMechanicalDesign
   */
  public GasScrubberMechanicalDesign getMechanicalDesign() {
    return new GasScrubberMechanicalDesign(this);
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
    this.inletStream = inletStream;

    thermoSystem = inletStream.getThermoSystem().clone();
    gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
    gasOutStream = new Stream("gasOutStream", gasSystem);

    thermoSystem = inletStream.getThermoSystem().clone();
    liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    liquidOutStream = new Stream("liquidOutStream", liquidSystem);
  }

  /**
   * <p>
   * addScrubberSection.
   * </p>
   *
   * @param type a {@link java.lang.String} object
   */
  public void addScrubberSection(String type) {
    scrubberSection.add(new SeparatorSection("section" + scrubberSection.size() + 1, type, this));
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
  public void displayResult() {}
}
