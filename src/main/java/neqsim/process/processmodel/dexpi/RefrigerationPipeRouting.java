package neqsim.process.processmodel.dexpi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes long liquid feeds and returning vapour lines above the equipment row on refrigeration drawings.
 *
 * @author NeqSim
 * @version 1.0
 */
final class RefrigerationPipeRouting {
  private final Map<Double, Integer> risers = new HashMap<>();
  private final List<double[]> extents;
  private double elevation;

  /**
   * Starts the pipe runs above equipment data bars and controller bubbles.
   *
   * @param nozzles source-backed nozzle coordinates in mm
   * @param extents additional points used to size the sheet and its battery limit
   */
  RefrigerationPipeRouting(Map<String, double[]> nozzles, List<double[]> extents) {
    this.extents = extents;
    for (double[] position : nozzles.values()) {
      elevation = Math.max(elevation, position[1]);
    }
    elevation += DexpiLayoutEngine.INSTRUMENT_OFFSET_Y + 16.0;
  }

  /**
   * Places connections on the boundary of the matching catalogue shape, spacing independent ports down each side.
   *
   * @param type physical equipment class
   * @param position symbol centre
   * @param inlets ordered inlet identities
   * @param outlets ordered outlet identities
   * @param positions nozzle coordinates in the shared drawing space
   */
  static void placePorts(String type, DexpiLayoutEngine.EquipmentPosition position, List<String> inlets,
      List<String> outlets, Map<String, double[]> positions) {
    if (position == null) {
      return;
    }
    double width = "OperatedValve".equals(type) ? 5.0 : "HeatExchanger".equals(type) ? 12.0 : 10.0;
    placeSide(inlets, position.x - width, position.y, positions);
    placeSide(outlets, position.x + width, position.y, positions);
  }

  /**
   * Places each port on a distinct point of the vertical side of a symbol.
   *
   * @param ids nozzle identifiers
   * @param x side coordinate
   * @param y symbol centre elevation
   * @param positions output coordinates
   */
  private static void placeSide(List<String> ids, double x, double y, Map<String, double[]> positions) {
    for (int i = 0; i < ids.size(); i++) {
      double offset = ids.size() == 1 ? 0.0 : 4.0 - 8.0 * i / (ids.size() - 1);
      positions.put(ids.get(i), new double[] {x, y + offset});
    }
  }

  /**
   * Returns a direct route for adjacent forward flow and a separate overhead lane for a return or a long run.
   *
   * @param from source nozzle
   * @param to receiving nozzle
   * @return orthogonal centreline, including both endpoints
   */
  double[][] route(double[] from, double[] to) {
    double span = to[0] - from[0];
    if (span > 0.0 && span < 100.0) {
      return DexpiLayoutEngine.routeConnection(from[0], from[1], to[0], to[1]);
    }
    double departure = from[0] + offset(from[0]);
    double approach = to[0] - offset(to[0]);
    elevation += 12.0;
    extents.add(new double[] {Math.max(departure, approach), elevation});
    return new double[][] {from, {departure, from[1]}, {departure, elevation}, {approach, elevation}, {approach, to[1]},
        to};
  }

  /**
   * Separates risers attached to the same side of a multi-pass exchanger.
   *
   * @param x nozzle column
   * @return horizontal clearance in mm
   */
  private double offset(double x) {
    int ordinal = risers.getOrDefault(x, 0);
    risers.put(x, ordinal + 1);
    return 8.0 + 3.0 * ordinal;
  }
}
