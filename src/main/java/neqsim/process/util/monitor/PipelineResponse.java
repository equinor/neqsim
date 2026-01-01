package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.equipment.pipeline.Pipeline;

/**
 * <p>
 * PipelineResponse class for JSON serialization of Pipeline equipment.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class PipelineResponse extends BaseResponse {
  /** Data map containing pipeline properties. */
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /** Inlet stream response. */
  public StreamResponse inlet;

  /** Outlet stream response. */
  public StreamResponse outlet;

  /**
   * <p>
   * Constructor for PipelineResponse.
   * </p>
   *
   * @param pipeline a {@link neqsim.process.equipment.pipeline.Pipeline} object
   */
  public PipelineResponse(Pipeline pipeline) {
    super(pipeline);
    if (pipeline.getInletStream() != null) {
      inlet = new StreamResponse(pipeline.getInletStream());
      data.put("inlet mass flow", new Value(
          Double.toString(
              pipeline.getInletStream().getFlowRate(neqsim.util.unit.Units.getSymbol("mass flow"))),
          neqsim.util.unit.Units.getSymbol("mass flow")));
      data.put("inlet temperature",
          new Value(
              Double.toString(pipeline.getInletStream()
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      data.put("inlet pressure", new Value(
          Double.toString(
              pipeline.getInletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
    }
    if (pipeline.getOutletStream() != null) {
      outlet = new StreamResponse(pipeline.getOutletStream());
      data.put("outlet temperature",
          new Value(
              Double.toString(pipeline.getOutletStream()
                  .getTemperature(neqsim.util.unit.Units.getSymbol("temperature"))),
              neqsim.util.unit.Units.getSymbol("temperature")));
      data.put("outlet pressure", new Value(
          Double.toString(
              pipeline.getOutletStream().getPressure(neqsim.util.unit.Units.getSymbol("pressure"))),
          neqsim.util.unit.Units.getSymbol("pressure")));
    }
  }
}
