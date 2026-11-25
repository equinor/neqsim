package neqsim.process.fielddevelopment.hybrid;

import java.io.Serializable;

/**
 * Fast proxy of the reservoir state seen by a well or a drainage unit, post-processed from reservoir simulator results.
 *
 * <p>
 * The proxy answers one question: given what has been produced and injected so far, what is the reservoir pressure,
 * water cut, gas-oil ratio, productivity index and (optionally) wellstream composition? It replaces the reservoir
 * simulator inside a time loop; it is not a reservoir simulator. Implementations keep the cumulative volumes as their
 * state so that a time-stepping model only has to report what was produced and injected in each step.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see ReservoirStateProxy
 * @see ScenarioReservoirState
 */
public interface ReservoirStateModel extends Serializable {
  /**
   * Gets the name of the well or drainage unit the proxy describes.
   *
   * @return proxy name
   */
  String getName();

  /**
   * Gets the reservoir pressure at the current cumulative volumes.
   *
   * @return reservoir pressure in bara
   */
  double getReservoirPressure();

  /**
   * Gets the water cut at the current cumulative volumes.
   *
   * @return water fraction of the produced liquid, 0 when the proxy has no water cut data
   */
  double getWaterCut();

  /**
   * Gets the producing gas-oil ratio at the current cumulative volumes.
   *
   * @return gas-oil ratio in Sm3/Sm3, NaN when the proxy has no such data
   */
  double getGasOilRatio();

  /**
   * Gets the oil productivity index at the current cumulative volumes.
   *
   * @return productivity index in Sm3 oil/(day bar), NaN when the proxy has no such data
   */
  double getProductivityIndex();

  /**
   * Tells whether the proxy carries a hydrocarbon wellstream composition.
   *
   * @return true if {@link #getComposition()} returns data
   */
  boolean hasComposition();

  /**
   * Gets the component names of the composition.
   *
   * @return component names, or null without a composition
   */
  String[] getComponentNames();

  /**
   * Gets the hydrocarbon wellstream composition at the current cumulative volumes.
   *
   * @return mole fractions in the order of {@link #getComponentNames()}, or null without a composition
   */
  double[] getComposition();

  /**
   * Gets the hydrocarbon wellstream moles per stock-tank oil volume.
   *
   * @return mol hydrocarbon per Sm3 oil, NaN without a composition
   */
  double getHydrocarbonMolesPerSm3();

  /**
   * Adds produced volumes to the cumulative state.
   *
   * @param oilSm3 stock-tank oil in Sm3
   * @param waterSm3 water in Sm3
   * @param gasSm3 gas in Sm3
   */
  void produce(double oilSm3, double waterSm3, double gasSm3);

  /**
   * Adds injected volumes to the cumulative state.
   *
   * @param waterSm3 water in Sm3
   * @param gasSm3 gas in Sm3
   */
  void inject(double waterSm3, double gasSm3);

  /**
   * Adds elapsed production time to the state.
   *
   * @param days elapsed time in days
   */
  void elapse(double days);

  /**
   * Gets a cumulative volume.
   *
   * @param quantity cumulative quantity
   * @return cumulative volume in Sm3
   */
  double getCumulative(CumulativeQuantity quantity);

  /**
   * Gets a copy of the cumulative state, for saving before a trial step.
   *
   * @return cumulative volumes indexed by {@link CumulativeQuantity#ordinal()}
   */
  double[] getCumulativeState();

  /**
   * Restores the cumulative state.
   *
   * @param state cumulative volumes indexed by {@link CumulativeQuantity#ordinal()}
   */
  void setCumulativeState(double[] state);

  /**
   * Tells whether the current cumulative volumes lie outside the range covered by the simulator results, so that the
   * returned state is held at the edge of the data and a new simulator run is needed.
   *
   * @return true if the proxy is evaluated outside its data
   */
  boolean isOutsideValidity();
}
