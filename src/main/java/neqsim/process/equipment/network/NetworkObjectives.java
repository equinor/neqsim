package neqsim.process.equipment.network;

/**
 * Factory methods for common network objective terms.
 */
public final class NetworkObjectives {
  private NetworkObjectives() {
  }

  /**
   * Custom objective callback.
   */
  public interface Evaluator extends java.io.Serializable {
    /**
     * Evaluate a solved network.
     *
     * @param network network
     * @return unweighted value, larger is better
     */
    double evaluate(LoopedPipeNetwork network);
  }

  /**
   * Maximize total mass delivery.
   *
   * @param weight scalarization weight
   * @return objective
   */
  public static NetworkObjective maximizeThroughput(double weight) {
    return custom("throughputKgHr", weight, new Evaluator() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double evaluate(LoopedPipeNetwork network) {
        return network.getTotalSinkFlow() * 3600.0;
      }
    });
  }

  /**
   * Minimize compressor power.
   *
   * @param weight scalarization weight
   * @return objective
   */
  public static NetworkObjective minimizeCompressorPower(double weight) {
    return custom("negativeCompressorPowerKW", weight, new Evaluator() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double evaluate(LoopedPipeNetwork network) {
        double power = 0.0;
        for (String edgeName : network.getPipeNames()) {
          LoopedPipeNetwork.NetworkPipe edge = network.getPipe(edgeName);
          if (edge.getElementType() == LoopedPipeNetwork.NetworkElementType.COMPRESSOR) {
            power += edge.getCompressorPower();
          }
        }
        return -power;
      }
    });
  }

  /**
   * Create a custom Java callback objective.
   *
   * @param name term name
   * @param weight scalarization weight
   * @param evaluator callback
   * @return objective
   */
  public static NetworkObjective custom(final String name, final double weight, final Evaluator evaluator) {
    return new NetworkObjective() {
      private static final long serialVersionUID = 1000L;

      @Override
      public String getName() {
        return name;
      }

      @Override
      public double getWeight() {
        return weight;
      }

      @Override
      public double evaluate(LoopedPipeNetwork network) {
        return evaluator.evaluate(network);
      }
    };
  }
}
