package neqsim.process.equipment.reservoir;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.NamedBaseClass;

/**
 * <p>
 * Well class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Well extends NamedBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private StreamInterface stream = null;
  double x;
  double y;
  double z;

  /**
   * <p>
   * Constructor for Well.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Well(String name) {
    super(name);
  }

  /**
   * <p>
   * Getter for the field <code>stream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getStream() {
    return stream;
  }

  /**
   * <p>
   * Setter for the field <code>stream</code>.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setStream(StreamInterface stream) {
    this.stream = stream;
  }

  /**
   * <p>
   * getGOR.
   * </p>
   *
   * @return a double
   */
  public double getGOR() {
    SystemInterface locStream = (stream.getFluid()).clone();
    locStream.setTemperature(288.15);
    locStream.setPressure(ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
    ops.TPflash();
    double GOR = Double.NaN;
    if (locStream.hasPhaseType("gas") && locStream.hasPhaseType("oil")) {
      GOR = locStream.getPhase("gas").getVolume("m3") / locStream.getPhase("oil").getVolume("m3");
    }
    return GOR;
  }

  /**
   * <p>
   * getStdGasProduction.
   * </p>
   *
   * @return a double
   */
  public double getStdGasProduction() {
    SystemInterface locStream = (stream.getFluid()).clone();
    locStream.setTemperature(288.15);
    locStream.setPressure(ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
    ops.TPflash();
    double volume = 0;
    if (locStream.hasPhaseType("gas")) {
      volume = locStream.getPhase("gas").getVolume("m3");
    }
    return volume;
  }

  /**
   * <p>
   * getStdOilProduction.
   * </p>
   *
   * @return a double
   */
  public double getStdOilProduction() {
    SystemInterface locStream = (stream.getFluid()).clone();
    locStream.setTemperature(288.15);
    locStream.setPressure(ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
    ops.TPflash();
    double volume = 0;
    if (locStream.hasPhaseType("oil")) {
      volume = locStream.getPhase("oil").getVolume("m3");
    }
    return volume;
  }

  /**
   * <p>
   * getStdWaterProduction.
   * </p>
   *
   * @return a double
   */
  public double getStdWaterProduction() {
    SystemInterface locStream = (stream.getFluid()).clone();
    locStream.setTemperature(288.15);
    locStream.setPressure(ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations ops = new ThermodynamicOperations(locStream);
    ops.TPflash();
    double volume = 0;
    if (locStream.hasPhaseType("aqueous")) {
      volume = locStream.getPhase("aqueous").getVolume("m3");
    }
    return volume;
  }
}
