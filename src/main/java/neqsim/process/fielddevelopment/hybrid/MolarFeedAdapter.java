package neqsim.process.fielddevelopment.hybrid;

import java.util.Collection;
import neqsim.process.fielddevelopment.reservoir.VfpFluidFactory;
import neqsim.thermo.system.SystemInterface;

/**
 * Turns the solved well rates of the hybrid production model into compositional NeqSim feeds: component molar rates
 * instead of oil, gas and water volumes.
 *
 * <p>
 * Two routes are supported. With a black-oil reservoir simulation the surface gas, stock-tank oil and water are
 * recombined at the well's current oil rate, water cut and gas-oil ratio by a {@link VfpFluidFactory} on the oil basis
 * (for example {@code RecombinationFluidFactory} with rate basis OIL, WCT and GOR), so a changing gas-oil ratio or
 * water cut changes the composition. With a compositional simulation the state proxy carries the hydrocarbon wellstream
 * composition and its moles per stock-tank oil volume, and the feed is built directly from them plus the produced
 * water. The returned fluids carry their molar flow, so they can be used as the fluid of a NeqSim {@code Stream} that
 * feeds a network or process model.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class MolarFeedAdapter {
  private static final double WATER_MOLES_PER_SM3 = 1000.0 / 0.018015;
  private final VfpFluidFactory recombination;
  private final SystemInterface template;

  /**
   * Creates an adapter that recombines surface volumes (black-oil route).
   *
   * @param recombination fluid factory on the oil rate, water cut and gas-oil ratio basis
   */
  public MolarFeedAdapter(VfpFluidFactory recombination) {
    this(recombination, null);
  }

  /**
   * Creates an adapter that can use both routes.
   *
   * @param recombination fluid factory on the oil rate, water cut and gas-oil ratio basis, or null
   * @param template fluid with all components (including water), EOS and mixing rule, used for wells whose state proxy
   * carries a composition; or null. A copy is kept, so later changes to the argument do not reach the adapter
   * @throws IllegalArgumentException if both arguments are null
   */
  public MolarFeedAdapter(VfpFluidFactory recombination, SystemInterface template) {
    if (recombination == null && template == null) {
      throw new IllegalArgumentException("A recombination factory or a template fluid is needed");
    }
    this.recombination = recombination;
    this.template = template == null ? null : template.clone();
  }

  /**
   * Builds the feed of one well at its solved operating point.
   *
   * @param well solved well
   * @return fluid with composition and molar flow rate, or null for a well that does not flow
   * @throws IllegalStateException if the adapter has no route for the well
   */
  public SystemInterface wellFeed(HybridWell well) {
    return wellFeed(well.getState(), well.getOilRate(), well.getWaterRate(), well.getGasRate(), well.getGasLiftRate());
  }

  /**
   * Builds a feed from a reservoir-state proxy and explicit surface rates.
   *
   * <p>
   * This overload lets the proxy provide composition to the canonical {@code WellSystem} and field-network path without
   * constructing a {@link HybridWell} or a second gathering graph.
   * </p>
   *
   * @param state reservoir-state proxy at the current cumulative volumes
   * @param oilRateSm3PerDay stock-tank oil rate in Sm3/day
   * @param waterRateSm3PerDay produced-water rate in Sm3/day
   * @param gasRateSm3PerDay total produced-gas rate including gas lift in Sm3/day
   * @param gasLiftRateSm3PerDay gas-lift rate in Sm3/day
   * @return fluid with composition and molar flow rate, or null when the oil rate is zero
   * @throws IllegalArgumentException if state is null, a rate is negative or gas lift exceeds total gas
   * @throws IllegalStateException if the adapter has no route for the state
   */
  public SystemInterface wellFeed(ReservoirStateModel state, double oilRateSm3PerDay, double waterRateSm3PerDay,
      double gasRateSm3PerDay, double gasLiftRateSm3PerDay) {
    if (state == null) {
      throw new IllegalArgumentException("Reservoir state must not be null");
    }
    if (!Double.isFinite(oilRateSm3PerDay) || !Double.isFinite(waterRateSm3PerDay) || !Double.isFinite(gasRateSm3PerDay)
        || !Double.isFinite(gasLiftRateSm3PerDay) || oilRateSm3PerDay < 0.0 || waterRateSm3PerDay < 0.0
        || gasRateSm3PerDay < 0.0 || gasLiftRateSm3PerDay < 0.0 || gasLiftRateSm3PerDay > gasRateSm3PerDay) {
      throw new IllegalArgumentException("Surface rates must be finite, non-negative and gas lift cannot exceed gas");
    }
    if (!(oilRateSm3PerDay > 0.0)) {
      return null;
    }
    if (state.hasComposition() && template != null && state.getHydrocarbonMolesPerSm3() > 0.0
        && Double.isFinite(state.getHydrocarbonMolesPerSm3())) {
      return compositionalFeed(state, oilRateSm3PerDay, waterRateSm3PerDay);
    }
    if (recombination == null) {
      throw new IllegalStateException("Reservoir state " + state.getName()
          + " has no composition in its state proxy and the adapter has no recombination factory");
    }
    double waterCut = waterRateSm3PerDay / (oilRateSm3PerDay + waterRateSm3PerDay);
    double gasOilRatio = (gasRateSm3PerDay - gasLiftRateSm3PerDay) / oilRateSm3PerDay;
    return recombination.createWellStream(oilRateSm3PerDay, waterCut, gasOilRatio, gasLiftRateSm3PerDay);
  }

  /**
   * Builds the feed of a well from the composition of its state proxy.
   *
   * @param state reservoir-state proxy with a composition
   * @param oilRateSm3PerDay stock-tank oil rate in Sm3/day
   * @param waterRateSm3PerDay produced-water rate in Sm3/day
   * @return fluid with composition and molar flow rate
   * @throws IllegalStateException if a component of the proxy is missing in the template
   */
  private SystemInterface compositionalFeed(ReservoirStateModel state, double oilRateSm3PerDay,
      double waterRateSm3PerDay) {
    String[] names = state.getComponentNames();
    double[] z = state.getComposition();
    double hydrocarbonMolesPerDay = oilRateSm3PerDay * state.getHydrocarbonMolesPerSm3();
    int n = template.getPhase(0).getNumberOfComponents();
    double[] moles = new double[n];
    for (int k = 0; k < names.length; k++) {
      if (!template.getPhase(0).hasComponent(names[k])) {
        throw new IllegalStateException("Template fluid has no component " + names[k]);
      }
      moles[template.getPhase(0).getComponent(names[k]).getComponentNumber()] += hydrocarbonMolesPerDay * z[k];
    }
    if (waterRateSm3PerDay > 0.0) {
      if (!template.getPhase(0).hasComponent("water")) {
        throw new IllegalStateException("Template fluid has no water component for the produced water");
      }
      moles[template.getPhase(0).getComponent("water").getComponentNumber()] += waterRateSm3PerDay
          * WATER_MOLES_PER_SM3;
    }
    return fromMolesPerDay(template, moles);
  }

  /**
   * Builds the commingled feed of several wells.
   *
   * @param wells solved wells
   * @return fluid with the summed component molar rates, or null when no well flows
   */
  public SystemInterface commingledFeed(Collection<HybridWell> wells) {
    double[] sum = null;
    SystemInterface first = null;
    for (HybridWell well : wells) {
      SystemInterface feed = wellFeed(well);
      if (feed == null) {
        continue;
      }
      double[] rates = componentMolarRates(feed);
      if (sum == null) {
        sum = rates;
        first = feed;
      } else {
        for (int i = 0; i < sum.length; i++) {
          sum[i] += rates[i];
        }
      }
    }
    if (sum == null) {
      return null;
    }
    double[] perDay = new double[sum.length];
    for (int i = 0; i < sum.length; i++) {
      perDay[i] = sum[i] * 86400.0;
    }
    return fromMolesPerDay(first, perDay);
  }

  /**
   * Gets the component molar rates of a feed.
   *
   * @param feed fluid with a molar flow rate
   * @return component molar rates in mol/s, in the component order of the fluid
   */
  public static double[] componentMolarRates(SystemInterface feed) {
    double total = feed.getFlowRate("mole/sec");
    double[] z = feed.getMolarComposition();
    double[] rates = new double[z.length];
    for (int i = 0; i < z.length; i++) {
      rates[i] = z[i] * total;
    }
    return rates;
  }

  /**
   * Gets the component names of a feed.
   *
   * @param feed fluid
   * @return component names in the order of {@link #componentMolarRates(SystemInterface)}
   */
  public static String[] componentNames(SystemInterface feed) {
    int n = feed.getPhase(0).getNumberOfComponents();
    String[] names = new String[n];
    for (int i = 0; i < n; i++) {
      names[i] = feed.getPhase(0).getComponent(i).getComponentName();
    }
    return names;
  }

  /**
   * Creates a fluid from component molar amounts per day.
   *
   * @param base fluid to clone for components, EOS and mixing rule
   * @param molesPerDay component amounts in mol/day
   * @return fluid with normalised composition and molar flow rate
   */
  private static SystemInterface fromMolesPerDay(SystemInterface base, double[] molesPerDay) {
    double total = 0.0;
    for (double value : molesPerDay) {
      total += value;
    }
    double[] floored = new double[molesPerDay.length];
    for (int i = 0; i < floored.length; i++) {
      floored[i] = Math.max(molesPerDay[i], 1.0e-12 * total);
    }
    SystemInterface fluid = base.clone();
    fluid.setMolarComposition(floored);
    fluid.setTotalFlowRate(total / 86400.0, "mole/sec");
    fluid.init(0);
    return fluid;
  }
}
