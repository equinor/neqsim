package neqsim.process.equipment.separator;

import java.util.ArrayList;
import java.util.UUID;
import neqsim.process.equipment.separator.sectiontype.SeparatorSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * NeqGasScrubber class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NeqGasScrubber extends Separator {
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

  ArrayList<SeparatorSection> scrubberSection = null;

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
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public NeqGasScrubber(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.setOrientation("vertical");
  }

  /** {@inheritDoc} */
  @Override
  public GasScrubberMechanicalDesign getMechanicalDesign() {
    return new GasScrubberMechanicalDesign(this);
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
    gasSystem.initProperties();
    gasOutStream.setThermoSystem(gasSystem);

    thermoSystem = inletStream.getThermoSystem().clone();
    liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    liquidSystem.setNumberOfPhases(1);
    liquidSystem.initProperties();
    liquidOutStream.setThermoSystem(liquidSystem);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}
}
