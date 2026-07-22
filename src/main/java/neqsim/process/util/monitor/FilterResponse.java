package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.filter.Filter;

/**
 * FilterResponse class for JSON serialization of Filter equipment.
 *
 * @author esol
 * @version $Id: $Id
 */
public class FilterResponse extends BaseResponse {
  /** Data map containing filter properties. */
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * Constructor for FilterResponse.
   *
   * @param filter a {@link neqsim.process.equipment.filter.Filter} object
   */
  public FilterResponse(Filter filter) {
    super(filter);
    if (filter.getInletStream() != null) {
      data.put("mass flow",
          new Value(Double.toString(filter.getInletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
              neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("inlet temperature",
          new Value(
              Double.toString(filter.getInletStream().getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      data.put("inlet pressure",
          new Value(Double.toString(filter.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
              neqsim.util.unit.Units.getSymbol("pressure")));
    }
    if (filter.getOutletStream() != null) {
      data.put("outlet temperature",
          new Value(
              Double.toString(filter.getOutletStream().getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      data.put("outlet pressure",
          new Value(Double.toString(filter.getOutletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
              neqsim.util.unit.Units.getSymbol("pressure")));
    }
    data.put("pressure drop",
        new Value(Double.toString(filter.getDeltaP()), neqsim.util.unit.Units.getSymbol("pressure")));
    data.put("filter type", new Value(filter.getFilterServiceType().getDisplayName(), ""));
    data.put("pressure drop model", new Value(filter.getPressureDropModel().name(), ""));
    data.put("clean pressure drop", new Value(Double.toString(filter.getCalculatedCleanDeltaP()), "bar"));
    data.put("unrestricted pressure drop", new Value(Double.toString(filter.getUnrestrictedDeltaP()), "bar"));
    data.put("terminal pressure drop", new Value(Double.toString(filter.getTerminalDeltaP()), "bar"));
    data.put("differential pressure utilization",
        new Value(Double.toString(filter.getDifferentialPressureUtilization()), ""));
    data.put("Cv factor", new Value(Double.toString(filter.getCvFactor()), ""));
    data.put("holdup volume", new Value(Double.toString(filter.getHoldupVolume()), "m3"));
    data.put("holdup residence time", new Value(Double.toString(filter.getHoldupResidenceTime()), "s"));
    data.put("solids loading", new Value(Double.toString(filter.getSolidsLoading()), "kg"));
    data.put("loading capacity", new Value(Double.toString(filter.getLoadingCapacity()), "kg"));
    data.put("loading fraction", new Value(Double.toString(filter.getLoadingFraction()), ""));
    data.put("breakthrough fraction", new Value(Double.toString(filter.getBreakthroughFraction()), ""));
    data.put("particle size", new Value(Double.toString(filter.getParticleSize()), "um"));
    data.put("nominal removal efficiency", new Value(Double.toString(filter.getNominalRemovalEfficiency()), ""));
    data.put("current removal efficiency", new Value(Double.toString(filter.getCurrentRemovalEfficiency()), ""));
    data.put("inlet particle concentration",
        new Value(Double.toString(filter.getInletParticleConcentration()), "mg/kg"));
    data.put("outlet particle concentration",
        new Value(Double.toString(filter.getOutletParticleConcentration()), "mg/kg"));
    data.put("captured particle rate", new Value(Double.toString(filter.getCalculatedCapturedRate()), "kg/hr"));
    data.put("bypass fraction", new Value(Double.toString(filter.getBypassFraction()), ""));
    data.put("element integrity verified", new Value(Boolean.toString(filter.isElementIntegrityVerified()), ""));
    data.put("backwash active", new Value(Boolean.toString(filter.isBackwashActive()), ""));
    data.put("regeneration active", new Value(Boolean.toString(filter.isRegenerationActive()), ""));
  }
}
