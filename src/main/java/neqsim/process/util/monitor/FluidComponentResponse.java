package neqsim.process.util.monitor;

import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Fluid class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class FluidComponentResponse {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FluidComponentResponse.class);
  public String name;
  public HashMap<String, HashMap<String, Value>> properties =
      new HashMap<String, HashMap<String, Value>>();

  /**
   * <p>
   * Constructor for FluidComponentResponse. Sets name of inputFluid as name.
   * </p>
   *
   * @param inputFluid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FluidComponentResponse(SystemInterface inputFluid) {
    this(inputFluid.getFluidName(), inputFluid);
  }

  /**
   * Constructor for FluidComponentResponse.
   *
   * @param nameinp name of fluid
   */
  public FluidComponentResponse(String nameinp) {
    this.name = nameinp;
  }

  /**
   * Constructor for FluidComponentResponse.
   *
   * @param fluidname name of fluid
   * @param fluid input fluid
   */
  public FluidComponentResponse(String fluidname, SystemInterface fluid) {
    this(fluidname);
    name = fluidname;
    SystemInterface inputFluid = fluid.clone();
    inputFluid.init(0);

    HashMap<String, Value> newdata = new HashMap<String, Value>();

    for (int i = 0; i < inputFluid.getNumberOfComponents(); i++) {
      String name = inputFluid.getPhase(0).getComponent(i).getComponentName();
      ComponentInterface component = inputFluid.getPhase(0).getComponent(i);
      PhaseInterface phase = inputFluid.getPhase(0);
      newdata = new HashMap<String, Value>();
      newdata.put("Acentric Factor",
          new Value(Double.toString(component.getAcentricFactor()), "-"));
      newdata.put("Mole Fraction", new Value(Double.toString(component.getz()), "-"));
      newdata.put("Weigth Fraction", new Value(Double.toString(phase.getWtFrac(i)), "-"));
      newdata.put("Critical Temperature",
          new Value(
              Double.toString(component.getTC(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      newdata.put("Critical Pressure",
          new Value(Double.toString(component.getPC(neqsim.util.unit.Units.getSymbol("pressure"))),
              neqsim.util.unit.Units.getSymbol("pressure")));
      newdata.put("Normal Liquid Density",
          new Value(
              Double.toString(
                  component.getNormalLiquidDensity(neqsim.util.unit.Units.getSymbol("density"))),
              neqsim.util.unit.Units.getSymbol("density")));

      properties.put(name, newdata);
    }
  }

  /**
   * @return SystemInterface
   */
  SystemInterface getNeqSimFluid() {
    SystemInterface tempFluid = new neqsim.thermo.system.SystemSrkEos();
    /*
     * definedComponent.keySet().forEach(key -> { tempFluid.addComponent(key,
     * definedComponent.get(key).get("molFraction")); });
     *
     * oilComponent.keySet().forEach(key -> { tempFluid.addTBPfraction(key,
     * definedComponent.get(key).get("molFraction"), definedComponent.get(key).get("molarMass"),
     * definedComponent.get(key).get("normalLiquidDensity")); });
     *
     * tempFluid.setMixingRule(2);
     */
    return tempFluid;
  }

  /**
   * <p>
   * print.
   * </p>
   */
  public void print() {}
}
