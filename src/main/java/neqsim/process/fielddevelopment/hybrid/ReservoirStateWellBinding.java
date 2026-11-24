package neqsim.process.fielddevelopment.hybrid;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Applies a simulator-derived reservoir state to an existing production {@link WellSystem}.
 *
 * <p>
 * The binding is deliberately not a network or a scheduler. Call {@link #apply()} before solving the existing
 * {@code FieldWellNetworkCoupler}; advance the proxy explicitly with
 * {@link ReservoirStateModel#produce(double, double, double)}, {@link ReservoirStateModel#inject(double, double)} and
 * {@link ReservoirStateModel#elapse(double)}. The configured inlet stream object is retained so the well remains
 * connected to the canonical process and field topology.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ReservoirStateWellBinding {
  private final ReservoirStateModel state;
  private final WellSystem well;
  private final MolarFeedAdapter feedAdapter;
  private double lastAppliedPressureBara = Double.NaN;
  private double lastAppliedProductivityIndex = Double.NaN;

  /**
   * Creates a pressure and productivity-index binding that retains the well's configured fluid composition.
   *
   * @param state simulator-derived reservoir state
   * @param well existing canonical production well
   */
  public ReservoirStateWellBinding(ReservoirStateModel state, WellSystem well) {
    this(state, well, null);
  }

  /**
   * Creates a binding that can also update the wellstream composition from water cut, GOR or compositional proxy data.
   *
   * @param state simulator-derived reservoir state
   * @param well existing canonical production well
   * @param feedAdapter composition adapter, or null to retain the well's configured composition
   * @throws IllegalArgumentException if state or well is null
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The binding must update the caller's live proxy and existing canonical WellSystem")
  public ReservoirStateWellBinding(ReservoirStateModel state, WellSystem well, MolarFeedAdapter feedAdapter) {
    if (state == null || well == null) {
      throw new IllegalArgumentException("Reservoir state and WellSystem must not be null");
    }
    this.state = state;
    this.well = well;
    this.feedAdapter = feedAdapter;
  }

  /**
   * Applies the proxy's current pressure, linear oil PI and optional composition to the existing well boundary.
   *
   * @return this binding
   * @throws IllegalStateException if the proxy state or well inlet is incomplete
   */
  public ReservoirStateWellBinding apply() {
    double pressureBara = state.getReservoirPressure();
    double productivityIndex = state.getProductivityIndex();
    if (!(pressureBara > 0.0) || !Double.isFinite(pressureBara)) {
      throw new IllegalStateException("Reservoir state " + state.getName() + " has no positive finite pressure");
    }
    if (!(productivityIndex > 0.0) || !Double.isFinite(productivityIndex)) {
      throw new IllegalStateException(
          "Reservoir state " + state.getName() + " has no positive finite productivity index");
    }
    StreamInterface inlet = well.getInletStream();
    if (inlet == null || inlet.getFluid() == null) {
      throw new IllegalStateException("WellSystem " + well.getName() + " needs an inlet stream before binding");
    }

    if (feedAdapter != null) {
      updateComposition(inlet, pressureBara);
    }
    well.setReservoirPressure(pressureBara, "bara");
    well.setIPRModel(WellSystem.IPRModel.PRODUCTION_INDEX);
    well.setProductionIndex(productivityIndex, "Sm3/day/bar");
    lastAppliedPressureBara = pressureBara;
    lastAppliedProductivityIndex = productivityIndex;
    return this;
  }

  /**
   * Updates normalized wellstream composition while preserving the inlet stream object.
   *
   * @param inlet existing inlet stream
   * @param pressureBara proxy pressure in bara
   */
  private void updateComposition(StreamInterface inlet, double pressureBara) {
    double waterCut = state.getWaterCut();
    if (!(waterCut >= 0.0 && waterCut < 1.0) || !Double.isFinite(waterCut)) {
      throw new IllegalStateException("Reservoir state " + state.getName() + " has invalid water cut");
    }
    double gasOilRatio = state.getGasOilRatio();
    if (!state.hasComposition() && (!(gasOilRatio >= 0.0) || !Double.isFinite(gasOilRatio))) {
      throw new IllegalStateException("Reservoir state " + state.getName() + " has no finite gas-oil ratio");
    }
    if (!Double.isFinite(gasOilRatio)) {
      gasOilRatio = 0.0;
    }
    double oilBasisSm3PerDay = 1.0;
    double waterBasisSm3PerDay = waterCut / (1.0 - waterCut);
    SystemInterface fluid = feedAdapter.wellFeed(state, oilBasisSm3PerDay, waterBasisSm3PerDay, gasOilRatio, 0.0);
    double temperatureK = inlet.getTemperature("K");
    fluid.setPressure(pressureBara, "bara");
    fluid.setTemperature(temperatureK, "K");
    fluid.init(0);
    inlet.setFluid(fluid);
  }

  /**
   * Gets the bound reservoir state.
   *
   * @return reservoir state
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The binding intentionally exposes the live proxy whose cumulative state the caller advances")
  public ReservoirStateModel getState() {
    return state;
  }

  /**
   * Gets the existing production well.
   *
   * @return bound WellSystem
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The binding intentionally exposes the existing canonical WellSystem rather than a copy")
  public WellSystem getWell() {
    return well;
  }

  /**
   * Gets the pressure used by the last successful application.
   *
   * @return pressure in bara, or NaN before the first application
   */
  public double getLastAppliedPressureBara() {
    return lastAppliedPressureBara;
  }

  /**
   * Gets the linear oil PI used by the last successful application.
   *
   * @return PI in Sm3 oil/(day bar), or NaN before the first application
   */
  public double getLastAppliedProductivityIndex() {
    return lastAppliedProductivityIndex;
  }

  /**
   * Reports whether the proxy is currently outside the range of its source simulator data.
   *
   * @return true when the proxy is outside its validity range
   */
  public boolean isOutsideValidity() {
    return state.isOutsideValidity();
  }
}
