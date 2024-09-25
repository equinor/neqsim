package neqsim.thermo;

import neqsim.thermo.system.SystemInterface;

/**
 * Used to generate fluids of type SystemInterface.
 *
 * @author esol
 * @version $Id: $Id
 */
public final class FluidCreator {
  /** Constant <code>hasWater=false</code>. */
  public static boolean hasWater = false;
  /** Constant <code>autoSelectModel=false</code>. */
  public static boolean autoSelectModel = false;
  /** Constant <code>thermoModel="srk"</code>. */
  public static String thermoModel = "srk";
  /** Constant <code>thermoMixingRule="classic"</code>. */
  public static String thermoMixingRule = "classic";

  /**
   * Dummy constructor, not for use. Class is to be considered static.
   */
  private FluidCreator() {}

  /**
   * Create SystemInterface.
   *
   * @param componentNames name of components to be added to a fluid
   * @return a fluid object (SystemInterface)
   */
  public static SystemInterface create(String[] componentNames) {
    Fluid fluid = new Fluid();
    fluid.setHasWater(hasWater);
    fluid.setAutoSelectModel(autoSelectModel);
    fluid.setThermoModel(thermoModel);
    fluid.setThermoModel(thermoModel);
    return fluid.create2(componentNames);
  }

  /**
   * Create SystemInterface.
   *
   * @param componentNames name of components to be added to a fluid
   * @param flowrate flow rate
   * @param unit unit of flow rate
   * @return a fluid object (SystemInterface)
   */
  public static SystemInterface create(String[] componentNames, double[] flowrate, String unit) {
    Fluid fluid = new Fluid();
    fluid.setHasWater(hasWater);
    fluid.setAutoSelectModel(autoSelectModel);
    fluid.setThermoModel(thermoModel);
    fluid.setThermoModel(thermoModel);
    return fluid.createFluid(componentNames, flowrate, unit);
  }

  /**
   * Create SystemInterface.
   *
   * @param fluidType fluid type can be "dry gas", "water", "air", "gas condensate", "combustion
   *        air"...
   * @return a fluid object (SystemInterface)
   */
  public static SystemInterface create(String fluidType) {
    Fluid fluid = new Fluid();
    fluid.setHasWater(hasWater);
    fluid.setAutoSelectModel(autoSelectModel);
    fluid.setThermoModel(thermoModel);
    fluid.setThermoModel(thermoModel);
    return fluid.create(fluidType);
  }
}
