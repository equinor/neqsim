package neqsim.process.equipment.network;

/**
 * Factory methods for common hard and soft network constraints.
 */
public final class NetworkConstraints {
  private NetworkConstraints() {
  }

  /** Custom violation residual callback. */
  public interface ResidualEvaluator extends java.io.Serializable {
    /**
     * Calculate a non-negative violation residual.
     *
     * @param network solved network
     * @return zero when satisfied, positive when violated
     */
    double evaluate(LoopedPipeNetwork network);
  }

  /**
   * Bound node pressure.
   *
   * @param nodeName node
   * @param minimumBara minimum absolute pressure
   * @param maximumBara maximum absolute pressure
   * @param hard hard/soft
   * @return constraint
   */
  public static NetworkConstraint nodePressure(final String nodeName, final double minimumBara,
      final double maximumBara, boolean hard) {
    return custom("node." + nodeName + ".pressure", hard, Math.max(maximumBara - minimumBara, 1.0), "bar",
        new ResidualEvaluator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double evaluate(LoopedPipeNetwork network) {
            double value = network.getNode(nodeName).getPressure() / 1.0e5;
            return boundResidual(value, minimumBara, maximumBara);
          }
        }, new ActiveEvaluator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public boolean isActive(LoopedPipeNetwork network) {
            double value = network.getNode(nodeName).getPressure() / 1.0e5;
            double tolerance = 0.01 * Math.max(maximumBara - minimumBara, 1.0);
            return Math.abs(value - minimumBara) <= tolerance || Math.abs(value - maximumBara) <= tolerance;
          }
        });
  }

  /**
   * Bound absolute edge mass flow.
   *
   * @param edgeName edge
   * @param minimumKgHr minimum flow
   * @param maximumKgHr maximum flow
   * @param hard hard/soft
   * @return constraint
   */
  public static NetworkConstraint edgeFlow(final String edgeName, final double minimumKgHr, final double maximumKgHr,
      boolean hard) {
    return custom("edge." + edgeName + ".flow", hard, Math.max(maximumKgHr - minimumKgHr, 1.0), "kg/hr",
        new ResidualEvaluator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double evaluate(LoopedPipeNetwork network) {
            double value = Math.abs(network.getPipe(edgeName).getFlowRate()) * 3600.0;
            return boundResidual(value, minimumKgHr, maximumKgHr);
          }
        }, null);
  }

  /**
   * Require solver convergence.
   *
   * @return hard convergence constraint
   */
  public static NetworkConstraint convergence() {
    return custom("solver.convergence", true, 1.0, "-", new ResidualEvaluator() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double evaluate(LoopedPipeNetwork network) {
        return network.isConverged() ? 0.0 : 1.0;
      }
    }, null);
  }

  /**
   * Bound mass-balance error.
   *
   * @param maximumKgS maximum residual
   * @param hard hard/soft
   * @return constraint
   */
  public static NetworkConstraint massBalance(final double maximumKgS, boolean hard) {
    return custom("network.massBalance", hard, Math.max(maximumKgS, 1.0e-12), "kg/s", new ResidualEvaluator() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double evaluate(LoopedPipeNetwork network) {
        return Math.max(0.0, network.getMassBalanceError() - maximumKgS);
      }
    }, null);
  }

  /**
   * Require every assigned point-specific quality profile to pass.
   *
   * @param hard hard/soft
   * @return quality constraint
   */
  public static NetworkConstraint qualityCompliance(boolean hard) {
    return custom("quality.compliance", hard, 1.0, "scaled", new ResidualEvaluator() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double evaluate(LoopedPipeNetwork network) {
        double residual = 0.0;
        for (NetworkQualityComplianceReport report : network.evaluateQualityProfiles().values()) {
          for (NetworkQualityResult result : report.getResults()) {
            if (result.getStatus() == NetworkQualityResult.Status.NOT_CALCULABLE) {
              residual += 1.0;
            } else if (result.getStatus() == NetworkQualityResult.Status.FAIL && result.getMargin() != null) {
              residual += Math.abs(result.getMargin());
            }
          }
        }
        return residual;
      }
    }, null);
  }

  /**
   * Create a custom scaled residual constraint.
   *
   * @param name name
   * @param hard hard/soft
   * @param scale positive residual scale
   * @param unit unit
   * @param evaluator residual callback
   * @return constraint
   */
  public static NetworkConstraint custom(final String name, final boolean hard, final double scale, final String unit,
      final ResidualEvaluator evaluator) {
    return custom(name, hard, scale, unit, evaluator, null);
  }

  private static NetworkConstraint custom(final String name, final boolean hard, final double scale, final String unit,
      final ResidualEvaluator evaluator, final ActiveEvaluator activeEvaluator) {
    return new NetworkConstraint() {
      private static final long serialVersionUID = 1000L;

      @Override
      public String getName() {
        return name;
      }

      @Override
      public boolean isHard() {
        return hard;
      }

      @Override
      public NetworkConstraintResult evaluate(LoopedPipeNetwork network) {
        double residual = Math.max(0.0, evaluator.evaluate(network));
        boolean active = activeEvaluator != null && activeEvaluator.isActive(network);
        return new NetworkConstraintResult(name, hard, residual, scale, active, unit,
            residual <= 0.0 ? "Satisfied" : "Violated");
      }
    };
  }

  private static double boundResidual(double value, double lower, double upper) {
    return Math.max(Math.max(lower - value, value - upper), 0.0);
  }

  private interface ActiveEvaluator extends java.io.Serializable {
    boolean isActive(LoopedPipeNetwork network);
  }
}
