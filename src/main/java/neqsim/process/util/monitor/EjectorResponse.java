package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.ejector.Ejector;

/**
 * <p>
 * EjectorResponse class for JSON serialization of Ejector equipment.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class EjectorResponse extends BaseResponse {
  /** Data map containing ejector properties. */
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * <p>
   * Constructor for EjectorResponse.
   * </p>
   *
   * @param ejector a {@link neqsim.process.equipment.ejector.Ejector} object
   */
  public EjectorResponse(Ejector ejector) {
    super(ejector);
    if (ejector.getMotiveStream() != null) {
      data.put("motive mass flow", new Value(
          Double.toString(
              ejector.getMotiveStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("motive pressure", new Value(
          Double.toString(
              ejector.getMotiveStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
    }
    if (ejector.getSuctionStream() != null) {
      data.put("suction mass flow",
          new Value(
              Double.toString(ejector.getSuctionStream()
                  .getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
              neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("suction pressure", new Value(
          Double.toString(
              ejector.getSuctionStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
    }
    if (ejector.getMixedStream() != null) {
      data.put("discharge mass flow", new Value(
          Double.toString(
              ejector.getMixedStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("discharge pressure", new Value(
          Double.toString(
              ejector.getMixedStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
      data.put("discharge temperature",
          new Value(
              Double.toString(ejector.getMixedStream()
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
    }
    data.put("isentropic efficiency",
        new Value(Double.toString(ejector.getEfficiencyIsentropic()), ""));
    data.put("suction nozzle efficiency",
        new Value(Double.toString(ejector.getSuctionNozzleEfficiency()), ""));
    data.put("mixing efficiency", new Value(Double.toString(ejector.getMixingEfficiency()), ""));
    data.put("diffuser efficiency",
        new Value(Double.toString(ejector.getDiffuserEfficiency()), ""));
    data.put("entrainment ratio", new Value(Double.toString(ejector.getEntrainmentRatio()), ""));
    data.put("compression ratio", new Value(Double.toString(ejector.getCompressionRatio()), ""));
    data.put("expansion ratio", new Value(Double.toString(ejector.getExpansionRatio()), ""));
    data.put("area ratio", new Value(Double.toString(ejector.getAreaRatio()), ""));
    data.put("critical back pressure", new Value(Double.toString(ejector.getCriticalBackPressure()),
        neqsim.util.unit.Units.getSymbol("pressure")));
    data.put("motive nozzle Mach", new Value(Double.toString(ejector.getMotiveNozzleMach()), ""));
    data.put("suction Mach", new Value(Double.toString(ejector.getSuctionMach()), ""));
    data.put("mixing Mach", new Value(Double.toString(ejector.getMixingMach()), ""));
    data.put("motive choked", new Value(Boolean.toString(ejector.isMotiveChoked()), ""));
    data.put("suction choked", new Value(Boolean.toString(ejector.isSuctionChoked()), ""));
    data.put("in breakdown", new Value(Boolean.toString(ejector.isInBreakdown()), ""));
  }
}
