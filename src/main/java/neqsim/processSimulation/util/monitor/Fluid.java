package neqsim.processSimulation.util.monitor;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  static Logger logger = LogManager.getLogger(Fluid.class);
  public Double volumeFlow;
  public Double molarMass;
  public Double massDensity;
  public Double massflow;

  public Map<String, Double> compProp;

  public Map<String, Map<String, Double>> definedComponent;
  public Map<String, Map<String, Double>> oilComponent;

  public HashMap<String, HashMap<String, Value>> properties =
      new HashMap<String, HashMap<String, Value>>();
  public HashMap<String, HashMap<String, Value>> composition =
      new HashMap<String, HashMap<String, Value>>();
  public HashMap<String, HashMap<String, Value>> conditions =
      new HashMap<String, HashMap<String, Value>>();

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
  public Fluid(String fluidname, SystemInterface inputFluid) {
    this(fluidname);

    name = inputFluid.getFluidName();

    HashMap<String, Value> newdata = new HashMap<String, Value>();
    newdata.put("temperature",
        new Value(
            Double.toString(
                inputFluid.getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    newdata.put("pressure",
        new Value(
            Double.toString(inputFluid.getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));
    newdata.put("molar flow",
        new Value(
            Double.toString(inputFluid.getFlowRate(neqsim.util.unit.Units.getSymbol("molar flow"))),
            neqsim.util.unit.Units.getSymbol("molar flow")));
    newdata.put("mass flow",
        new Value(
            Double.toString(inputFluid.getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));
    newdata.put("fluid model", new Value(inputFluid.getModelName(), ""));
    newdata.put("enthalpy",
        new Value(
            Double.toString(inputFluid.getEnthalpy(neqsim.util.unit.Units.getSymbol("enthalpy"))),
            neqsim.util.unit.Units.getSymbol("enthalpy")));
    conditions.put(name, newdata);

    for (int i = 0; i < inputFluid.getNumberOfPhases(); i++) {
      String name = inputFluid.getPhase(i).getPhaseTypeName();
      newdata = new HashMap<String, Value>();
      newdata.put("temperature",
          new Value(
              Double.toString(inputFluid.getPhase(name)
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      newdata.put("pressure", new Value(
          Double.toString(
              inputFluid.getPhase(name).getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
      newdata.put("molar flow",
          new Value(
              Double.toString(inputFluid.getPhase(name)
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("molar flow"))),
              neqsim.util.unit.Units.getSymbol("molar flow")));
      newdata.put("mass flow", new Value(
          Double.toString(
              inputFluid.getPhase(name).getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")));
      newdata.put("fluid model", new Value(inputFluid.getModelName(), ""));
      newdata.put("enthalpy", new Value(
          Double.toString(
              inputFluid.getPhase(name).getEnthalpy(neqsim.util.unit.Units.getSymbol("enthalpy"))),
          neqsim.util.unit.Units.getSymbol("enthalpy")));
      conditions.put(name, newdata);
    }

    name = inputFluid.getFluidName();
    newdata = new HashMap<String, Value>();
    for (int i = 0; i < inputFluid.getNumberOfComponents(); i++) {
      newdata.put(inputFluid.getComponent(i).getComponentName(),
          new Value(Double.toString(inputFluid.getComponent(i).getz()), "mole fraction"));
    }
    composition.put(name, newdata);
    for (int j = 0; j < inputFluid.getNumberOfPhases(); j++) {
      newdata = new HashMap<String, Value>();
      HashMap<String, Value> newdata2 = new HashMap<String, Value>();
      for (int i = 0; i < inputFluid.getNumberOfComponents(); i++) {
        newdata2.put(inputFluid.getPhase(j).getComponent(i).getComponentName(), new Value(
            Double.toString(inputFluid.getPhase(j).getComponent(i).getx()), "mole fraction"));
        newdata.put(inputFluid.getPhase(j).getComponent(i).getComponentName(),
            new Value(Double.toString(inputFluid.getPhase(j).getWtFrac(i)), "weight fraction"));
      }
      composition.put(inputFluid.getPhase(j).getPhaseTypeName(), newdata2);
      composition.put(inputFluid.getPhase(j).getPhaseTypeName() + "_wt", newdata);
    }

    newdata = new HashMap<String, Value>();

    newdata.put("density",
        new Value(
            Double.toString(inputFluid.getDensity(neqsim.util.unit.Units.getSymbol("density"))),
            neqsim.util.unit.Units.getSymbol("density")));

    newdata.put("molar mass",
        new Value(
            Double
                .toString(inputFluid.getMolarMass(neqsim.util.unit.Units.getSymbol("Molar Mass"))),
            neqsim.util.unit.Units.getSymbol("Molar Mass")));

    newdata.put("flow rate",
        new Value(
            Double
                .toString(inputFluid.getFlowRate(neqsim.util.unit.Units.getSymbol("volume flow"))),
            neqsim.util.unit.Units.getSymbol("volume flow")));
    properties.put(inputFluid.getFluidName(), newdata);
    for (int i = 0; i < inputFluid.getNumberOfPhases(); i++) {
      newdata = new HashMap<String, Value>();
      String name = inputFluid.getPhase(i).getPhaseTypeName();
      newdata.put("density", new Value(
          Double.toString(
              inputFluid.getPhase(name).getDensity(neqsim.util.unit.Units.getSymbol("density"))),
          neqsim.util.unit.Units.getSymbol("density")));
      newdata.put("molar mass",
          new Value(
              Double.toString(inputFluid.getPhase(name)
                  .getMolarMass(neqsim.util.unit.Units.getSymbol("Molar Mass"))),
              neqsim.util.unit.Units.getSymbol("Molar Mass")));

      newdata.put("flow rate",
          new Value(
              Double.toString(inputFluid.getPhase(name)
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("volume flow"))),
              neqsim.util.unit.Units.getSymbol("volume flow")));
      properties.put(name, newdata);
    }

    for (int i = 0; i < inputFluid.getNumberOfComponents(); i++) {
      compProp = new HashMap<>();
      if (inputFluid.getPhase(0).getComponent(i).isIsTBPfraction()) {
        compProp.put("molFraction", inputFluid.getPhase(0).getComponent(i).getz());
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
