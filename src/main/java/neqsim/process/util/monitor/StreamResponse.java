package neqsim.process.util.monitor;

import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.standards.gasquality.Standard_ISO6976;

/**
 * <p>
 * StreamResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StreamResponse extends BaseResponse {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(StreamResponse.class);
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
   * @param inputStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamResponse(StreamInterface inputStream) {
    super(inputStream);

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
    conditions.put("overall", newdata);

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

    name = inputStream.getName();
    newdata = new HashMap<String, Value>();
    for (int i = 0; i < inputStream.getFluid().getNumberOfComponents(); i++) {
      newdata.put(inputStream.getFluid().getComponent(i).getComponentName(), new Value(
          Double.toString(inputStream.getFluid().getComponent(i).getz()), "mole fraction"));
    }
    composition.put("overall", newdata);
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
      composition.put(inputStream.getFluid().getPhase(j).getPhaseTypeName(), newdata2);
      composition.put(inputStream.getFluid().getPhase(j).getPhaseTypeName() + "_wt", newdata);
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

    newdata.put("flow rate", new Value(
        Double.toString(
            inputStream.getFluid().getFlowRate(neqsim.util.unit.Units.getSymbol("volume flow"))),
        neqsim.util.unit.Units.getSymbol("volume flow")));

    newdata.put("heat capacity (Cp)", new Value(
        Double.toString(
            inputStream.getFluid().getCp(neqsim.util.unit.Units.getSymbol("Heat Capacity (Cp)"))),
        neqsim.util.unit.Units.getSymbol("Heat Capacity (Cp)")));

    newdata.put("heat capacity (Cv)", new Value(
        Double.toString(
            inputStream.getFluid().getCv(neqsim.util.unit.Units.getSymbol("Heat Capacity (Cv)"))),
        neqsim.util.unit.Units.getSymbol("Heat Capacity (Cv)")));

    newdata.put("enthalpy",
        new Value(
            Double.toString(
                inputStream.getFluid().getEnthalpy(neqsim.util.unit.Units.getSymbol("enthalpy"))),
            neqsim.util.unit.Units.getSymbol("enthalpy")));

    newdata.put("entropy",
        new Value(
            Double.toString(
                inputStream.getFluid().getEntropy(neqsim.util.unit.Units.getSymbol("entropy"))),
            neqsim.util.unit.Units.getSymbol("entropy")));

    properties.put("overall", newdata);
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

      newdata.put("heat capacity (Cp)",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getCp(neqsim.util.unit.Units.getSymbol("Heat Capacity (Cp)"))),
              neqsim.util.unit.Units.getSymbol("Heat Capacity (Cp)")));

      newdata.put("heat capacity (Cv)",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getCv(neqsim.util.unit.Units.getSymbol("Heat Capacity (Cv)"))),
              neqsim.util.unit.Units.getSymbol("Heat Capacity (Cv)")));

      newdata.put("enthalpy",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getEnthalpy(neqsim.util.unit.Units.getSymbol("enthalpy"))),
              neqsim.util.unit.Units.getSymbol("enthalpy")));

      newdata.put("entropy",
          new Value(
              Double.toString(inputStream.getFluid().getPhase(name)
                  .getEntropy(neqsim.util.unit.Units.getSymbol("entropy"))),
              neqsim.util.unit.Units.getSymbol("entropy")));

      if (name.equals("oil")) {
        try {
          newdata.put("TVP",
              new Value(
                  Double.toString(
                      inputStream.getTVP(37.8, "C", neqsim.util.unit.Units.getSymbol("pressure"))),
                  neqsim.util.unit.Units.getSymbol("pressure")));
        } catch (Exception ex) {
          logger.error(ex.getMessage());
        }
        try {
          newdata.put("RVP",
              new Value(
                  Double.toString(
                      inputStream.getRVP(37.8, "C", neqsim.util.unit.Units.getSymbol("pressure"))),
                  neqsim.util.unit.Units.getSymbol("pressure")));
          newdata.put("relative density", new Value(
              Double.toString(inputStream.getFluid().getPhase(name).getDensity("kg/m3") / 1000.0),
              "-"));
        } catch (Exception ex) {
          logger.error(ex.getMessage());
        }
      } else if (name.equals("gas")) {
        Standard_ISO6976 standard = inputStream.getISO6976("volume", 15.0, 15.0);
        standard.calculate();
        newdata.put("GCV (15/15)", new Value(
            Double.toString(standard.getValue("SuperiorCalorificValue") / 1e3), "MJ/Sm3 @15C,15C"));
        newdata.put("WI (15/15)", new Value(
            Double.toString(standard.getValue("SuperiorWobbeIndex") / 1e3), "MJ/Sm3 @15C,15C"));
        newdata.put("standard flow rate",
            new Value(
                Double.toString(inputStream.getFluid().getPhase(name)
                    .getFlowRate(neqsim.util.unit.Units.getSymbol("standard volume flow"))),
                neqsim.util.unit.Units.getSymbol("standard volume flow")));
        newdata.put("relative density",
            new Value(Double.toString(standard.getValue("RelativeDensity")), "[-]"));
        newdata.put("gas flow",
            new Value(Double.toString(inputStream.getFlowRate("Sm3/hr")), "Sm3/hr"));
      }
      properties.put(name, newdata);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void applyConfig(ReportConfig cfg) {
    DetailLevel level = getDetailLevel(cfg);
    if (level == DetailLevel.SUMMARY) {
      composition = null;
      properties = null;
    } else if (level == DetailLevel.MINIMUM) {
      composition = null;
      properties = null;
      if (conditions != null) {
        HashMap<String, Value> overall = conditions.get("overall");
        if (overall != null) {
          HashMap<String, Value> minimal = new HashMap<>();
          if (overall.get("temperature") != null) {
            minimal.put("temperature", overall.get("temperature"));
          }
          if (overall.get("pressure") != null) {
            minimal.put("pressure", overall.get("pressure"));
          }
          if (overall.get("molar flow") != null) {
            minimal.put("molar flow", overall.get("molar flow"));
          }
          conditions = new HashMap<>();
          conditions.put("overall", minimal);
        } else {
          conditions = null;
        }
      }
    }
  }

  /**
   * <p>
   * print.
   * </p>
   */
  public void print() {}
}
