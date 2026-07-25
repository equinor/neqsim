package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyType;

/**
 * Electric motor converting electrical power to shaft work.
 *
 * @author NeqSim
 * @version 1.0
 */
public class ElectricMotor extends EnergyConverter {
  private static final long serialVersionUID = 1000L;

  /**
   * Creates an electric motor with 95 percent efficiency.
   *
   * @param name equipment name
   */
  public ElectricMotor(String name) {
    super(name, EnergyType.ELECTRICAL, EnergyType.SHAFT_WORK);
    setEfficiency(0.95);
  }

  /**
   * Creates an electric motor.
   *
   * @param name equipment name
   * @param efficiency electrical-to-shaft efficiency
   */
  public ElectricMotor(String name, double efficiency) {
    this(name);
    setEfficiency(efficiency);
  }
}
