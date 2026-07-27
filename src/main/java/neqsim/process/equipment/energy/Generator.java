package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyType;

/**
 * Generator converting shaft work to electrical power.
 *
 * <p>
 * An optional rated output and {@link LoadEfficiencyCurve} provide part-load performance while the default remains
 * constant 97 percent efficiency.
 * </p>
 *
 * @author NeqSim
 * @version 2.0
 */
public class Generator extends LoadMappedEnergyConverter {
  private static final long serialVersionUID = 1000L;

  /** Creates a generator with 97 percent efficiency. */
  public Generator(String name) {
    super(name, EnergyType.SHAFT_WORK, EnergyType.ELECTRICAL);
    setEfficiency(0.97);
  }

  /** Creates a generator with specified nominal efficiency. */
  public Generator(String name, double efficiency) {
    this(name);
    setEfficiency(efficiency);
  }
}
