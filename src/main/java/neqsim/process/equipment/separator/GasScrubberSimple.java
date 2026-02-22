/*
 * GasScrubberSimple.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.process.equipment.separator;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * GasScrubberSimple class.
 * </p>
 *
 * <p>
 * A simplified gas scrubber model that is a vertical separator designed primarily for removing
 * liquid droplets from gas streams. Unlike standard separators, the key performance metric is the
 * K-value (Souders-Brown factor) rather than liquid retention time.
 * </p>
 *
 * <h2>Capacity Utilization Setup</h2>
 *
 * <p>
 * To get meaningful capacity utilization from {@link #getCapacityUtilization()}, set:
 * </p>
 * <ol>
 * <li>{@link #setInternalDiameter(double)} — scrubber inner diameter [m]</li>
 * <li>{@link #setDesignGasLoadFactor(double)} — design K-factor [m/s], typically 0.04–0.10 for
 * vertical scrubbers</li>
 * </ol>
 *
 * <p>
 * The orientation is automatically set to "vertical" and the design liquid level fraction defaults
 * to 0.1 (10%). For dry gas (no liquid phase), a default liquid density of 1000 kg/m³ is used.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GasScrubberSimple extends Separator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  SystemInterface gasSystem;
  SystemInterface waterSystem;
  SystemInterface liquidSystem;
  SystemInterface thermoSystemCloned;

  StreamInterface inletStream;
  StreamInterface gasOutStream;
  StreamInterface liquidOutStream;

  /**
   * Constructor for GasScrubberSimple.
   *
   * @param name name of scrubber
   */
  public GasScrubberSimple(String name) {
    super(name);
    this.setOrientation("vertical");
    this.setDesignLiquidLevelFraction(0.1);
    // Use only K-value constraint for gas scrubbers
    useGasScrubberConstraints();
  }

  /**
   * <p>
   * Constructor for GasScrubberSimple.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public GasScrubberSimple(String name, StreamInterface inletStream) {
    super(name, inletStream);
    this.setOrientation("vertical");
    this.setDesignLiquidLevelFraction(0.1);
    // Use only K-value constraint for gas scrubbers
    useGasScrubberConstraints();
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
    gasSystem = thermoSystem.phaseToSystem(0);
    gasOutStream = new Stream("gasOutStream", gasSystem);

    thermoSystem = inletStream.getThermoSystem().clone();
    liquidSystem = thermoSystem.phaseToSystem(1);
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
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.TPflash();
    if (separatorSection.size() > 0) {
      calcLiquidCarryoverFraction();
      thermoSystem.addLiquidToGas(getLiquidCarryoverFraction());
    }
    gasSystem = thermoSystem.phaseToSystem(0);
    gasSystem.setNumberOfPhases(1);
    gasSystem.initProperties();
    gasOutStream.setThermoSystem(gasSystem);

    thermoSystem = inletStream.getThermoSystem().clone();
    if (separatorSection.size() > 0) {
      thermoSystem.addGasToLiquid(getGasCarryunderFraction());
      liquidSystem = thermoSystem.phaseToSystem(1);
    }
    liquidSystem.setNumberOfPhases(1);
    liquidSystem.initProperties();
    liquidOutStream.setThermoSystem(liquidSystem);
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * calcLiquidCarryoverFraction.
   * </p>
   *
   * @return a double
   */
  public double calcLiquidCarryoverFraction() {
    double ktotal = 1.0;

    for (int i = 0; i < separatorSection.size(); i++) {
      ktotal *= (1.0 - separatorSection.get(i).getEfficiency());
    }
    System.out.println("Ktot " + (1.0 - ktotal));
    double area = getInternalDiameter() * getInternalDiameter() / 4.0 * 3.14;
    double gasVel =
        thermoSystem.getTotalNumberOfMoles() * thermoSystem.getMolarVolume() / 1e5 / area;
    setLiquidCarryoverFraction(ktotal);
    return gasVel;
  }
}
