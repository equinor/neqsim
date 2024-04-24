package neqsim.processSimulation.util.monitor;

import java.util.HashMap;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;

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
  public HashMap<String, HashMap<String, Value>> properties2 = new HashMap<String, HashMap<String, Value>>();

  public HashMap<String, Value> conditions = new HashMap<String, Value>();
  public HashMap<String, Value> properties = new HashMap<String, Value>();
  public HashMap<String, Value> composition = new HashMap<String, Value>();

  /**
   * <p>
   * Constructor for StreamResponse.
   * </p>
   *
   * @param inputStream a
   *                    {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *                    object
   */
  public StreamResponse(StreamInterface inputStream) {

    properties2.put(inputStream.getName(), new HashMap<String, Value>());
    properties2.get(inputStream.getName()).put("temperature",
        new Value(Double.toString(inputStream.getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));

    if (inputStream.getFluid().hasPhaseType(PhaseType.GAS)) {
      properties2.put("gas", new HashMap<String, Value>());
      properties2.get("gas").put("temperature",
          new Value(Double.toString(
              inputStream.getFluid()
                  .getPhase(PhaseType.GAS).getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
    }

    name = inputStream.getName();

    conditions.put("temperature",
        new Value(Double.toString(inputStream.getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
            neqsim.util.unit.Units.getSymbol("temperature")));
    conditions.put("pressure",
        new Value(Double.toString(inputStream.getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
            neqsim.util.unit.Units.getSymbol("pressure")));

    fluid = new Fluid(inputStream.getFluid());
    temperature = inputStream.getTemperature("C");
    pressure = inputStream.getPressure("bara");
    molarMass = inputStream.getFluid().getMolarMass();
    massDensity = inputStream.getFluid().getDensity("kg/m3");
    massflow = inputStream.getFluid().getFlowRate("kg/hr");
    volumeFlow = inputStream.getFluid().getFlowRate("m3/hr");

    if (inputStream.getFluid().hasPhaseType("gas")) {
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
  public void print() {
  }
}
