package neqsim.processSimulation.util.monitor;

import java.util.HashMap;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.NamedBaseClass;

/**
 * <p>
 * Fluid class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Fluid extends NamedBaseClass {
  private static final long serialVersionUID = 1L;
  public Double volumeFlow;
  public Double molarMass;
  public Double massDensity;
  public Double massflow;

  public Map<String, Double> compProp;

  public Map<String, Map<String, Double>> definedComponent;
  public Map<String, Map<String, Double>> oilComponent;

  /**
   * <p>
   * Constructor for Fluid.
   * </p>
   */
  @Deprecated
  public Fluid() {
    this("Fluid");
  }

  /**
   * <p>
   * Constructor for Fluid. Sets name of inputFluid as name.
   * </p>
   * 
   * @param inputFluid a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Fluid(SystemInterface inputFluid) {
    this(inputFluid.getFluidName(), inputFluid);
  }

  /**
   * Constructor for Fluid.
   * 
   * @param name name of fluid
   */
  public Fluid(String name) {
    super(name);
    this.definedComponent = new HashMap<>();
    this.oilComponent = new HashMap<>();
  }

  /**
   * Constructor for Fluid.
   * 
   * @param name name of fluid
   * @param inputFluid input fluid
   */
  public Fluid(String name, SystemInterface inputFluid) {
    this(name);

    for (int i = 0; i < inputFluid.getNumberOfComponents(); i++) {
      compProp = new HashMap<>();
      if (inputFluid.getPhase(0).getComponent(i).isIsTBPfraction()) {
        compProp.put("molFraction", inputFluid.getPhase(0).getComponent(i).getz());
        compProp.put("massFlow", inputFluid.getPhase(0).getComponent(i).getFlowRate("kg/hr"));
        compProp.put("molarMass", inputFluid.getPhase(0).getComponent(i).getMolarMass());
        compProp.put("normalLiquidDensity",
            inputFluid.getPhase(0).getComponent(i).getNormalLiquidDensity());
        oilComponent.put(inputFluid.getPhase(0).getComponent(i).getComponentName(), compProp);
      } else {
        compProp.put("molFraction", inputFluid.getPhase(0).getComponent(i).getz());
        compProp.put("massFlow", inputFluid.getPhase(0).getComponent(i).getFlowRate("kg/hr"));
        definedComponent.put(
            inputFluid.getPhase(0).getComponent(i).getComponentName().replaceAll("-", ""),
            compProp);
      }
    }

    molarMass = inputFluid.getMolarMass();
    massDensity = inputFluid.getDensity("kg/m3");
    massflow = inputFluid.getFlowRate("kg/hr");
    volumeFlow = inputFluid.getFlowRate("m3/hr");
  }

  /**
   * @return SystemInterface
   */
  SystemInterface getNeqSimFluid() {
    SystemInterface tempFluid = new neqsim.thermo.system.SystemSrkEos();

    definedComponent.keySet().forEach(key -> {
      tempFluid.addComponent(key, definedComponent.get(key).get("molFraction"));
    });

    oilComponent.keySet().forEach(key -> {
      tempFluid.addTBPfraction(key, definedComponent.get(key).get("molFraction"),
          definedComponent.get(key).get("molarMass"),
          definedComponent.get(key).get("normalLiquidDensity"));
    });

    tempFluid.setMixingRule(2);

    return tempFluid;
  }

  /**
   * <p>
   * print.
   * </p>
   */
  public void print() {}
}
