package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyType;

/**
 * Generator converting shaft work to electrical power.
 *
 * @author NeqSim
 * @version 1.0
 */
public class Generator extends EnergyConverter {
  private static final long serialVersionUID = 1000L;

  /**
   * Creates a generator with 97 percent efficiency.
   *
   * @param name equipment name
   */
  public Generator(String name) {
    super(name, EnergyType.SHAFT_WORK, EnergyType.ELECTRICAL);
    setEfficiency(0.97);
  }

  /**
   * Creates a generator.
   *
   * @param name equipment name
   * @param efficiency shaft-to-electrical efficiency
   */
  public Generator(String name, double efficiency) {
    this(name);
    setEfficiency(efficiency);
  }
}
