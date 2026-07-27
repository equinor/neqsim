package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyType;

/** Generic fuel-fired prime mover with optional part-load efficiency curve. */
public class PrimeMover extends LoadMappedEnergyConverter {
  private static final long serialVersionUID = 1000L;

  public PrimeMover(String name) {
    super(name, EnergyType.CHEMICAL, EnergyType.SHAFT_WORK);
    setEfficiency(0.35);
  }

  public PrimeMover(String name, double efficiency) {
    this(name);
    setEfficiency(efficiency);
  }
}
