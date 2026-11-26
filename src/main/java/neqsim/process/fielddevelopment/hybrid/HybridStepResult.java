package neqsim.process.fielddevelopment.hybrid;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Snapshot of the hybrid production model at one point in time: well operating points, node pressures, field totals and
 * solver diagnostics.
 *
 * @author ESOL
 * @version 1.0
 */
public class HybridStepResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Operating point and reservoir state of one well.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class WellResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String node;
    private final String controlMode;
    private final double oilRate;
    private final double waterRate;
    private final double gasRate;
    private final double bottomHolePressure;
    private final double wellheadPressure;
    private final double nodePressure;
    private final double reservoirPressure;
    private final double waterCut;
    private final double gasOilRatio;
    private final double productivityIndex;
    private final double cumulativeOil;
    private final boolean outsideProxyValidity;

    /**
     * Captures the current solution of a well.
     *
     * @param well the well
     * @param node node the well is tied to
     */
    WellResult(HybridWell well, String node) {
      this.name = well.getName();
      this.node = node;
      this.controlMode = well.getControlMode().name();
      this.oilRate = well.getOilRate();
      this.waterRate = well.getWaterRate();
      this.gasRate = well.getGasRate();
      this.bottomHolePressure = well.getBottomHolePressure();
      this.wellheadPressure = well.getWellheadPressure();
      this.nodePressure = well.getNodePressure();
      this.reservoirPressure = well.getReservoirPressure();
      this.waterCut = well.getState().getWaterCut();
      this.gasOilRatio = well.getState().getGasOilRatio();
      this.productivityIndex = well.getState().getProductivityIndex();
      this.cumulativeOil = well.getState().getCumulative(CumulativeQuantity.OIL_PRODUCED);
      boolean outside = well.getState().isOutsideValidity();
      if (well.getDrainageUnit() != null) {
        outside = outside || well.getDrainageUnit().isOutsideValidity();
      }
      this.outsideProxyValidity = outside;
    }

    /**
     * Gets the well name.
     *
     * @return well name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the node the well is tied to.
     *
     * @return node name
     */
    public String getNode() {
      return node;
    }

    /**
     * Gets what limits the well.
     *
     * @return name of the {@link HybridWell.ControlMode}
     */
    public String getControlMode() {
      return controlMode;
    }

    /**
     * Gets the oil rate.
     *
     * @return oil rate in Sm3/day
     */
    public double getOilRate() {
      return oilRate;
    }

    /**
     * Gets the water rate.
     *
     * @return water rate in Sm3/day
     */
    public double getWaterRate() {
      return waterRate;
    }

    /**
     * Gets the gas rate.
     *
     * @return gas rate in Sm3/day
     */
    public double getGasRate() {
      return gasRate;
    }

    /**
     * Gets the flowing bottom-hole pressure.
     *
     * @return bottom-hole pressure in bara, NaN for a well that does not flow
     */
    public double getBottomHolePressure() {
      return bottomHolePressure;
    }

    /**
     * Gets the wellhead pressure upstream of the choke.
     *
     * @return wellhead pressure in bara
     */
    public double getWellheadPressure() {
      return wellheadPressure;
    }

    /**
     * Gets the pressure of the node the well is tied to.
     *
     * @return node pressure in bara
     */
    public double getNodePressure() {
      return nodePressure;
    }

    /**
     * Gets the reservoir pressure used by the inflow performance.
     *
     * @return reservoir pressure in bara
     */
    public double getReservoirPressure() {
      return reservoirPressure;
    }

    /**
     * Gets the water cut.
     *
     * @return water fraction of the liquid
     */
    public double getWaterCut() {
      return waterCut;
    }

    /**
     * Gets the gas-oil ratio.
     *
     * @return gas-oil ratio in Sm3/Sm3
     */
    public double getGasOilRatio() {
      return gasOilRatio;
    }

    /**
     * Gets the productivity index.
     *
     * @return productivity index in Sm3 oil/(day bar)
     */
    public double getProductivityIndex() {
      return productivityIndex;
    }

    /**
     * Gets the cumulative oil produced by the well.
     *
     * @return cumulative oil in Sm3
     */
    public double getCumulativeOil() {
      return cumulativeOil;
    }

    /**
     * Tells whether a reservoir-state proxy of the well is evaluated outside its simulator data.
     *
     * @return true if a new simulator run is needed to trust the state
     */
    public boolean isOutsideProxyValidity() {
      return outsideProxyValidity;
    }
  }

  private final double timeDays;
  private final boolean converged;
  private final int iterations;
  private final double pressureResidualBar;
  private final boolean calibrated;
  private final double largestOffsetChangeBar;
  private final int allocationPasses;
  private final String bindingConstraint;
  private final long liftEvaluations;
  private final long referenceEvaluations;
  private final double solveSeconds;
  private final Map<String, Double> nodePressures;
  private final List<WellResult> wells;

  /**
   * Creates a snapshot.
   *
   * @param timeDays model time in days
   * @param converged whether the well-network iteration converged
   * @param iterations number of well-network iterations of the last solve
   * @param pressureResidualBar largest node pressure residual in bar
   * @param calibrated whether the lift curves were corrected against the reference models at this step
   * @param largestOffsetChangeBar largest change of a lift curve offset in bar at this step
   * @param allocationPasses number of facility-capacity allocation passes
   * @param bindingConstraint name of the binding facility constraint, or an empty string
   * @param liftEvaluations cumulative number of surrogate lift evaluations
   * @param referenceEvaluations cumulative number of reference lift evaluations
   * @param solveSeconds wall time of the solve in seconds
   * @param nodePressures node pressures in bara keyed by node name
   * @param wells well results
   */
  HybridStepResult(double timeDays, boolean converged, int iterations, double pressureResidualBar, boolean calibrated,
      double largestOffsetChangeBar, int allocationPasses, String bindingConstraint, long liftEvaluations,
      long referenceEvaluations, double solveSeconds, Map<String, Double> nodePressures, List<WellResult> wells) {
    this.timeDays = timeDays;
    this.converged = converged;
    this.iterations = iterations;
    this.pressureResidualBar = pressureResidualBar;
    this.calibrated = calibrated;
    this.largestOffsetChangeBar = largestOffsetChangeBar;
    this.allocationPasses = allocationPasses;
    this.bindingConstraint = bindingConstraint;
    this.liftEvaluations = liftEvaluations;
    this.referenceEvaluations = referenceEvaluations;
    this.solveSeconds = solveSeconds;
    this.nodePressures = new LinkedHashMap<String, Double>(nodePressures);
    this.wells = new ArrayList<WellResult>(wells);
  }

  /**
   * Gets the model time.
   *
   * @return time in days
   */
  public double getTimeDays() {
    return timeDays;
  }

  /**
   * Tells whether the well-network iteration converged. Do not use the rates of a step that did not converge.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Gets the number of well-network iterations of the last solve.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Gets the largest node pressure residual.
   *
   * @return residual in bar
   */
  public double getPressureResidualBar() {
    return pressureResidualBar;
  }

  /**
   * Tells whether the lift curves were corrected against the reference models at this step.
   *
   * @return true if a correction was made
   */
  public boolean isCalibrated() {
    return calibrated;
  }

  /**
   * Gets the largest change of a lift curve offset at this step.
   *
   * @return offset change in bar, 0 when no correction was made
   */
  public double getLargestOffsetChangeBar() {
    return largestOffsetChangeBar;
  }

  /**
   * Gets the number of facility-capacity allocation passes.
   *
   * @return number of passes, 0 when no capacity was binding
   */
  public int getAllocationPasses() {
    return allocationPasses;
  }

  /**
   * Gets the binding facility constraint.
   *
   * @return name of the binding {@link FacilityConstraints.Quantity}, or an empty string
   */
  public String getBindingConstraint() {
    return bindingConstraint;
  }

  /**
   * Gets the cumulative number of surrogate lift evaluations.
   *
   * @return evaluation count since the model was created
   */
  public long getLiftEvaluations() {
    return liftEvaluations;
  }

  /**
   * Gets the cumulative number of reference (high-fidelity) lift evaluations.
   *
   * @return evaluation count since the model was created
   */
  public long getReferenceEvaluations() {
    return referenceEvaluations;
  }

  /**
   * Gets the wall time of the solve.
   *
   * @return time in seconds
   */
  public double getSolveSeconds() {
    return solveSeconds;
  }

  /**
   * Gets the node pressures.
   *
   * @return copy of the pressures in bara keyed by node name, in network order
   */
  public Map<String, Double> getNodePressures() {
    return new LinkedHashMap<String, Double>(nodePressures);
  }

  /**
   * Gets the well results.
   *
   * @return copy of the list of well results, in network order
   */
  public List<WellResult> getWells() {
    return new ArrayList<WellResult>(wells);
  }

  /**
   * Gets the result of one well.
   *
   * @param wellName well name
   * @return the well result
   * @throws IllegalArgumentException if the well is not in the result
   */
  public WellResult getWell(String wellName) {
    for (WellResult well : wells) {
      if (well.getName().equals(wellName)) {
        return well;
      }
    }
    throw new IllegalArgumentException("Unknown well " + wellName);
  }

  /**
   * Gets the field oil rate.
   *
   * @return sum of the well oil rates in Sm3/day
   */
  public double getFieldOilRate() {
    double sum = 0.0;
    for (WellResult well : wells) {
      sum += well.getOilRate();
    }
    return sum;
  }

  /**
   * Gets the field water rate.
   *
   * @return sum of the well water rates in Sm3/day
   */
  public double getFieldWaterRate() {
    double sum = 0.0;
    for (WellResult well : wells) {
      sum += well.getWaterRate();
    }
    return sum;
  }

  /**
   * Gets the field gas rate.
   *
   * @return sum of the well gas rates in Sm3/day
   */
  public double getFieldGasRate() {
    double sum = 0.0;
    for (WellResult well : wells) {
      sum += well.getGasRate();
    }
    return sum;
  }

  /**
   * Tells whether any reservoir-state proxy is evaluated outside its simulator data.
   *
   * @return true if a new simulator run is needed
   */
  public boolean isOutsideProxyValidity() {
    for (WellResult well : wells) {
      if (well.isOutsideProxyValidity()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Header of the comma-separated rows written by {@link #toCsvRows()}.
   *
   * @return header line
   */
  public static String csvHeader() {
    return "time_days,well,node,control,oil_sm3d,water_sm3d,gas_sm3d,bhp_bara,whp_bara,node_bara,pres_bara,"
        + "wct,gor,pi,cum_oil_sm3,converged,iterations,calibrated,outside_proxy";
  }

  /**
   * Writes one comma-separated row per well.
   *
   * @return rows without header
   */
  public List<String> toCsvRows() {
    List<String> rows = new ArrayList<String>();
    for (WellResult well : wells) {
      rows.add(
          String.format(Locale.ROOT, "%.4f,%s,%s,%s,%.6f,%.6f,%.6f,%.5f,%.5f,%.5f,%.5f,%.6f,%.5f,%.6f,%.3f,%b,%d,%b,%b",
              timeDays, well.getName(), well.getNode(), well.getControlMode(), well.getOilRate(), well.getWaterRate(),
              well.getGasRate(), well.getBottomHolePressure(), well.getWellheadPressure(), well.getNodePressure(),
              well.getReservoirPressure(), well.getWaterCut(), well.getGasOilRatio(), well.getProductivityIndex(),
              well.getCumulativeOil(), converged, iterations, calibrated, well.isOutsideProxyValidity()));
    }
    return rows;
  }
}
