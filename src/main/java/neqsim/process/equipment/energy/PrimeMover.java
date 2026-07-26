package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyType;

/**
 * Generic fuel-fired prime mover converting chemical energy to shaft work.
 *
 * <p>
 * The input power is the fuel lower- or higher-heating-value rate chosen by the model author. Cost and emissions can be
 * assigned to the chemical-energy producer port and are included in the upstream bus report.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PrimeMover extends EnergyConverter {
  private static final long serialVersionUID = 1000L;

  /**
   * Creates a prime mover with 35 percent efficiency.
   *
   * @param name equipment name
   */
  public PrimeMover(String name) {
    super(name, EnergyType.CHEMICAL, EnergyType.SHAFT_WORK);
    setEfficiency(0.35);
  }

  /**
   * Creates a prime mover.
   *
   * @param name equipment name
   * @param efficiency fuel-to-shaft efficiency
   */
  public PrimeMover(String name, double efficiency) {
    this(name);
    setEfficiency(efficiency);
  }
}
