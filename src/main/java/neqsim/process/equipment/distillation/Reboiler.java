package neqsim.process.equipment.distillation;

import java.util.UUID;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Reboiler class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Reboiler extends neqsim.process.equipment.distillation.SimpleTray {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double refluxRatio = 0.1;
  boolean refluxIsSet = false;
  double duty = 0.0;

  /**
   * <p>
   * Constructor for Reboiler.
   * </p>
   *
   * @param name name of unit operation
   */
  public Reboiler(String name) {
    super(name);
  }

  /**
   * <p>
   * Getter for the field <code>refluxRatio</code>.
   * </p>
   *
   * @return the refluxRatio
   */
  public double getRefluxRatio() {
    return refluxRatio;
  }

  /**
   * <p>
   * Setter for the field <code>refluxRatio</code>.
   * </p>
   *
   * @param refluxRatio the refluxRatio to set
   */
  public void setRefluxRatio(double refluxRatio) {
    this.refluxRatio = refluxRatio;
    refluxIsSet = true;
  }

  /**
   * <p>
   * Getter for the field <code>duty</code>.
   * </p>
   *
   * @return a double
   */
  public double getDuty() {
    return duty;
    // return calcMixStreamEnthalpy();
  }

  /**
   * <p>
   * getDuty.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getDuty(String unit) {
    neqsim.util.unit.PowerUnit powerUnit = new neqsim.util.unit.PowerUnit(duty, "W");
    return powerUnit.getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (!refluxIsSet) {
      UUID oldid = getCalculationIdentifier();
      super.run(id);
      mixedStream.setCalculationIdentifier(oldid);
      setCalculationIdentifier(oldid);
    } else {
      SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
      // System.out.println("total number of moles " +
      // thermoSystem2.getTotalNumberOfMoles());
      mixedStream.setThermoSystem(thermoSystem2);
      ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
      testOps.PVrefluxFlash(refluxRatio, 1);
    }
    // System.out.println("enthalpy: " +
    // mixedStream.getThermoSystem().getEnthalpy());
    // System.out.println("enthalpy: " + enthalpy);
    // System.out.println("temperature: " +
    // mixedStream.getThermoSystem().getTemperature());

    // System.out.println("beta " + mixedStream.getThermoSystem().getBeta())
    duty = mixedStream.getFluid().getEnthalpy() - calcMixStreamEnthalpy0();

    mixedStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }
}
