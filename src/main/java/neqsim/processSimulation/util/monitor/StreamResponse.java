package neqsim.processSimulation.util.monitor;

import java.util.HashMap;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * StreamResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StreamResponse {
  public String name;
  public Fluid fluid;
  public Double temperature;
  public Double pressure;
  public Double volumeFlow;
  public Double molarMass;
  public Double massDensity;
  public Double massflow;
  public Double massflowGas;
  public Double massflowOil;
  public Double massflowAqueous;
  public HashMap<String, HashMap<String, Value>> properties =
      new HashMap<String, HashMap<String, Value>>();
  public HashMap<String, HashMap<String, Value>> conditions =
      new HashMap<String, HashMap<String, Value>>();
  public HashMap<String, HashMap<String, Value>> composition =
      new HashMap<String, HashMap<String, Value>>();

  /**
   * <p>
   * Constructor for StreamResponse.
   * </p>
   *
   * @param inputStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public StreamResponse(StreamInterface inputStream) {

    name = inputStream.getName();

    HashMap<String, Value> newdata = new HashMap<String, Value>();
    newdata.put("temperature",
        new Value(
            Double.toString(
                inputStream.getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    newdata.put("pressure",
        new Value(
            Double.toString(inputStream.getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));
    newdata.put("molar flow",
        new Value(
            Double
                .toString(inputStream.getFlowRate(neqsim.util.unit.Units.getSymbol("molar flow"))),
            neqsim.util.unit.Units.getSymbol("molar flow")));
    newdata.put("mass flow",
        new Value(
            Double.toString(inputStream.getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
            neqsim.util.unit.Units.getSymbol("mass flow")));
    newdata.put("fluid model", new Value(inputStream.getFluid().getModelName(), ""));
    newdata.put("enthalpy",
        new Value(
            Double.toString(
                inputStream.getFluid().getEnthalpy(neqsim.util.unit.Units.getSymbol("enthalpy"))),
            neqsim.util.unit.Units.getSymbol("enthalpy")));
    conditions.put(name, newdata);

    for (int i = 0; i < inputStream.getFluid().getNumberOfPhases(); i++) {
      String name = inputStream.getFluid().getPhase(i).getPhaseTypeName();
      newdata = new HashMap<String, Value>();
      newdata.put("temperature",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      newdata.put("pressure",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
              neqsim.util.unit.Units.getSymbol("pressure")));
      newdata.put("molar flow",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("molar flow"))),
              neqsim.util.unit.Units.getSymbol("molar flow")));
      newdata.put("mass flow",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
              neqsim.util.unit.Units.getSymbol("mass flow")));
      newdata.put("fluid model", new Value(inputStream.getFluid().getModelName(), ""));
      newdata.put("enthalpy",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getEnthalpy(neqsim.util.unit.Units.getSymbol("enthalpy"))),
              neqsim.util.unit.Units.getSymbol("enthalpy")));
      conditions.put(name, newdata);
    }


    newdata = new HashMap<String, Value>();
    for (int i = 0; i < inputStream.getFluid().getNumberOfComponents(); i++) {
      newdata.put(inputStream.getFluid().getComponent(i).getComponentName(), new Value(
          Double.toString(inputStream.getFluid().getComponent(i).getz()), "mole fraction"));
    }
    composition.put(name, newdata);
    for (int j = 0; j < inputStream.getFluid().getNumberOfPhases(); j++) {
      newdata = new HashMap<String, Value>();
      HashMap<String, Value> newdata2 = new HashMap<String, Value>();
      for (int i = 0; i < inputStream.getFluid().getNumberOfComponents(); i++) {
        newdata2.put(inputStream.getFluid().getPhase(j).getComponent(i).getComponentName(),
            new Value(Double.toString(inputStream.getFluid().getPhase(j).getComponent(i).getx()),
                "mole fraction"));
        newdata.put(inputStream.getFluid().getPhase(j).getComponent(i).getComponentName(),
            new Value(Double.toString(inputStream.getFluid().getPhase(j).getWtFrac(i)),
                "weight fraction"));
      }
      composition.put(inputStream.getFluid().getPhase(j).getPhaseTypeName(), newdata);
    }



    newdata = new HashMap<String, Value>();

    newdata.put("density",
        new Value(
            Double.toString(
                inputStream.getFluid().getDensity(neqsim.util.unit.Units.getSymbol("density"))),
            neqsim.util.unit.Units.getSymbol("density")));

    newdata.put("molar mass", new Value(
        Double.toString(
            inputStream.getFluid().getMolarMass(neqsim.util.unit.Units.getSymbol("Molar Mass"))),
        neqsim.util.unit.Units.getSymbol("Molar Mass")));
    properties.put(inputStream.getName(), newdata);

    newdata.put("flow rate", new Value(
        Double.toString(
            inputStream.getFluid().getFlowRate(neqsim.util.unit.Units.getSymbol("volume flow"))),
        neqsim.util.unit.Units.getSymbol("volume flow")));
    properties.put(inputStream.getName(), newdata);
    for (int i = 0; i < inputStream.getFluid().getNumberOfPhases(); i++) {
      newdata = new HashMap<String, Value>();
      String name = inputStream.getFluid().getPhase(i).getPhaseTypeName();
      newdata.put("density",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getDensity(neqsim.util.unit.Units.getSymbol("density"))),
              neqsim.util.unit.Units.getSymbol("density")));
      newdata.put("molar mass",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getMolarMass(neqsim.util.unit.Units.getSymbol("Molar Mass"))),
              neqsim.util.unit.Units.getSymbol("Molar Mass")));

      newdata.put("flow rate",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("volume flow"))),
              neqsim.util.unit.Units.getSymbol("volume flow")));
      properties.put(name, newdata);
    }

    fluid = new Fluid(inputStream.getFluid());
    temperature = inputStream.getTemperature("C");
    pressure = inputStream.getPressure("bara");
    molarMass = inputStream.getFluid().getMolarMass();
    massDensity = inputStream.getFluid().getDensity("kg/m3");
    massflow = inputStream.getFluid().getFlowRate("kg/hr");
    volumeFlow = inputStream.getFluid().getFlowRate("m3/hr");

    if (inputStream.getFluid().hasPhaseType("gas"))

    {
      massflowGas = inputStream.getFluid().getPhase("gas").getFlowRate("kg/hr");
    } else {
      massflowGas = 0.0;
    }
    if (inputStream.getFluid().hasPhaseType("aqueous")) {
      massflowAqueous = inputStream.getFluid().getPhase("aqueous").getFlowRate("kg/hr");
    } else {
      massflowAqueous = 0.0;
    }
    if (inputStream.getFluid().hasPhaseType("oil")) {
      massflowOil = inputStream.getFluid().getPhase("oil").getFlowRate("kg/hr");
    } else {
      massflowOil = 0.0;
    }
  }

  /**
   * <p>
   * print.
   * </p>
   */
  public void print() {}
}
