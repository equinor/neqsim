package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * VolumeFlowTransmitter class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class VolumeFlowTransmitter extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private int measuredPhaseNumber = 0;

  /**
   * <p>
   * Constructor for VolumeFlowTransmitter.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public VolumeFlowTransmitter(StreamInterface stream) {
    this("volume flow rate", stream);
  }

  /**
   * <p>
   * Constructor for VolumeFlowTransmitter.
   * </p>
   *
   * @param name Name of VolumeFlowTransmitter
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public VolumeFlowTransmitter(String name, StreamInterface stream) {
    super(name, "m^3/hr", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured volume " + Double.toString(getMeasuredValue()) + " " + unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    stream.getThermoSystem().initPhysicalProperties();
    if (unit.equals("kg/hr")) {
      return stream.getFlowRate(unit);
    } else if (unit.equals("m^3/hr")) {
      return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
          * stream.getThermoSystem().getPhase(measuredPhaseNumber).getMolarMass() / stream
              .getThermoSystem().getPhase(measuredPhaseNumber).getPhysicalProperties().getDensity()
          * 3600.0;
    } else if (unit.equals("Nm^3/day")) {
      return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
          * neqsim.thermo.ThermodynamicConstantsInterface.R
          * neqsim.thermo.ThermodynamicConstantsInterface.normalStateTemperature
          / ThermodynamicConstantsInterface.atm * 3600.0 * 24;
    } else if (unit.equals("Sm^3/day")) {
      return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
          * neqsim.thermo.ThermodynamicConstantsInterface.R
          * neqsim.thermo.ThermodynamicConstantsInterface.standardStateTemperature
          / ThermodynamicConstantsInterface.atm * 3600.0 * 24;
    } else {
      return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
          * stream.getThermoSystem().getPhase(measuredPhaseNumber).getMolarMass() / stream
              .getThermoSystem().getPhase(measuredPhaseNumber).getPhysicalProperties().getDensity()
          * 3600.0;
    }
  }

  /**
   * <p>
   * Getter for the field <code>measuredPhaseNumber</code>.
   * </p>
   *
   * @return a int
   */
  public int getMeasuredPhaseNumber() {
    return measuredPhaseNumber;
  }

  /**
   * <p>
   * Setter for the field <code>measuredPhaseNumber</code>.
   * </p>
   *
   * @param measuredPhase a int
   */
  public void setMeasuredPhaseNumber(int measuredPhase) {
    this.measuredPhaseNumber = measuredPhase;
  }
}
