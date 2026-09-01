package neqsim.process.equipment.distillation;

import java.util.UUID;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Reboiler class.
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
   * Constructor for Reboiler.
   *
   * @param name name of unit operation
   */
  public Reboiler(String name) {
    super(name);
    registerEnergyPort("heatDuty", EnergyType.HEAT, EnergyPortDirection.INPUT, EnergyPortMode.CALCULATED);
  }

  /**
   * Connects an external heat-duty specification using the legacy single-stream API.
   *
   * @param energyStream heat-duty stream
   */
  @Override
  public void setEnergyStream(EnergyStream energyStream) {
    super.connectEnergyStream("heatDuty", energyStream, EnergyPortMode.SPECIFICATION);
  }

  /** {@inheritDoc} */
  @Override
  public void connectEnergyStream(String portName, EnergyStream stream) {
    if ("heatDuty".equals(portName)) {
      super.connectEnergyStream(portName, stream, EnergyPortMode.SPECIFICATION);
    } else {
      super.connectEnergyStream(portName, stream);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void disconnectEnergyStream(String portName) {
    super.disconnectEnergyStream(portName);
    if ("heatDuty".equals(portName)) {
      getEnergyPort(portName).setMode(EnergyPortMode.CALCULATED);
    }
  }

  /**
   * Getter for the field <code>refluxRatio</code>.
   *
   * @return the refluxRatio
   */
  public double getRefluxRatio() {
    return refluxRatio;
  }

  /**
   * Setter for the field <code>refluxRatio</code>.
   *
   * @param refluxRatio finite non-negative vapor boilup-to-bottoms ratio
   * @throws IllegalArgumentException if the ratio is negative or non-finite
   */
  public void setRefluxRatio(double refluxRatio) {
    if (!Double.isFinite(refluxRatio) || refluxRatio < 0.0) {
      throw new IllegalArgumentException("Reboiler vapor boilup ratio must be finite and >= 0");
    }
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
   * Getter for the field <code>duty</code>.
   *
   * @return a double
   */
  public double getDuty() {
    return duty;
    // return calcMixStreamEnthalpy();
  }

  /**
   * getDuty.
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
    if (refluxIsSet && (!Double.isFinite(refluxRatio) || refluxRatio < 0.0)) {
      throw new IllegalStateException("Reboiler " + getName() + " has invalid vapor boilup ratio " + refluxRatio);
    }
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
    if (!isSetEnergyStream()) {
      getEnergyPort("heatDuty").setDuty(duty);
    }

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
