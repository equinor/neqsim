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
   * Clear the explicit vapor boilup/reflux ratio and return to equilibrium operation.
   */
  public void clearRefluxRatio() {
    refluxIsSet = false;
  }

  /**
   * Checks whether an explicit vapor boilup/reflux ratio is configured.
   *
   * @return {@code true} when an explicit ratio is active, otherwise {@code false}
   */
  public boolean isRefluxSet() {
    return refluxIsSet;
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
      prepareMixedStreamForRefluxFlash();
      ThermodynamicOperations testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
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

  /**
   * Prepare the mixed stream before a reboiler reflux flash.
   *
   * @throws IllegalStateException if no inlet streams are connected
   */
  private void prepareMixedStreamForRefluxFlash() {
    if (streams.isEmpty()) {
      throw new IllegalStateException("Reboiler has no inlet streams");
    }
    SystemInterface thermoSystem = streams.get(0).getThermoSystem().clone();
    mixedStream.setThermoSystem(thermoSystem);
    mixedStream.getThermoSystem().setNumberOfPhases(2);
    mixedStream.getThermoSystem().init(0);
    mixStream();
  }
}
