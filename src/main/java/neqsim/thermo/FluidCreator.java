package neqsim.thermo;

import neqsim.thermo.system.SystemInterface;

/**
 * Used to generate fluids of type SystemInterface
 * <p>
 * FluidCreator class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidCreator {

  public static boolean hasWater = false;
  public static boolean autoSelectModel = false;
  public static String thermoModel = "srk";
  public static String thermoMixingRule = "classic";
  
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
