package neqsim.process.fielddevelopment.tieback.capacity;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Host feed provider that recombines surface volumes into a molar feed with a characterised reservoir fluid.
 *
 * <p>
 * The reference fluid is flashed once to standard conditions (15 C, 1.01325 bara) into a stock-tank-oil and a gas
 * composition. A production load is then recombined: gas moles from the gas volume at the ideal-gas standard molar
 * volume, oil moles from the oil volume, density and molar mass, and water moles from the water volume. The load's GOR
 * and water cut are honoured exactly, so a changing GOR changes the feed composition. The host stream fluid must have
 * the same components in the same order as the reference fluid. Water is only recombined when the reference fluid has a
 * {@code water} component.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class RecombinedHostFeedProvider implements HostFeedProvider {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Standard temperature in K. */
  private static final double STANDARD_TEMPERATURE_K = 288.15;

  /** Standard pressure in bara. */
  private static final double STANDARD_PRESSURE_BARA = 1.01325;

  /** Ideal-gas standard molar volume in Sm3/kmol. */
  private static final double SM3_PER_KMOL = 23.6443;

  /** Produced-water density in kg/m3. */
  private static final double WATER_DENSITY = 998.0;

  /** Stock-tank-oil mole fractions. */
  private final double[] oilFractions;

  /** Standard-gas mole fractions. */
  private final double[] gasFractions;

  /** Reference fluid composition, returned for a zero load. */
  private final double[] referenceComposition;

  /** Stock-tank-oil molar mass in kg/kmol. */
  private final double oilMolarMass;

  /** Stock-tank-oil density in kg/m3. */
  private final double oilDensity;

  /** Index of the water component, or -1 when the fluid has none. */
  private final int waterIndex;

  /** Water molar mass in kg/kmol. */
  private final double waterMolarMass;

  /** Gas-oil ratio of the reference fluid in Sm3/Sm3. */
  private final double referenceGor;

  /**
   * Creates a provider from a characterised reservoir fluid.
   *
   * @param referenceFluid fluid with its EOS and mixing rule set; it is cloned and not modified
   * @throws IllegalArgumentException if the fluid does not split into gas and oil at standard conditions
   */
  public RecombinedHostFeedProvider(SystemInterface referenceFluid) {
    SystemInterface flashed = referenceFluid.clone();
    int n = flashed.getPhase(0).getNumberOfComponents();
    referenceComposition = flashed.getMolarComposition();
    flashed.setTemperature(STANDARD_TEMPERATURE_K);
    flashed.setPressure(STANDARD_PRESSURE_BARA);
    new ThermodynamicOperations(flashed).TPflash();
    flashed.initProperties();

    PhaseInterface gas = null;
    PhaseInterface oil = null;
    for (int i = 0; i < flashed.getNumberOfPhases(); i++) {
      PhaseInterface phase = flashed.getPhase(i);
      PhaseType type = phase.getType();
      if (type == PhaseType.GAS && gas == null) {
        gas = phase;
      } else if ((type == PhaseType.OIL || type == PhaseType.LIQUID) && oil == null) {
        oil = phase;
      }
    }
    if (gas == null || oil == null) {
      throw new IllegalArgumentException("the reference fluid does not split into gas and oil at standard conditions");
    }
    oilFractions = new double[n];
    gasFractions = new double[n];
    for (int i = 0; i < n; i++) {
      oilFractions[i] = oil.getComponent(i).getx();
      gasFractions[i] = gas.getComponent(i).getx();
    }
    oilMolarMass = oil.getMolarMass() * 1000.0;
    oilDensity = oil.getDensity("kg/m3");
    double gasMoles = gas.getNumberOfMolesInPhase();
    double oilVolume = oil.getNumberOfMolesInPhase() * oilMolarMass / oilDensity;
    referenceGor = oilVolume > 0.0 ? gasMoles * SM3_PER_KMOL / oilVolume : Double.NaN;

    int water = -1;
    double waterMass = 18.01528;
    if (flashed.getPhase(0).hasComponent("water")) {
      water = flashed.getPhase(0).getComponent("water").getComponentNumber();
      waterMass = flashed.getPhase(0).getComponent("water").getMolarMass() * 1000.0;
    }
    waterIndex = water;
    waterMolarMass = waterMass;
  }

  /**
   * Gets the GOR of the reference fluid.
   *
   * @return gas-oil ratio in Sm3/Sm3 from the standard-condition flash
   */
  public double getReferenceGor() {
    return referenceGor;
  }

  /** {@inheritDoc} */
  @Override
  public HostFeed getFeed(ProductionLoad load) {
    double gasKmolPerDay = load.getGasRateMSm3d() * 1.0e6 / SM3_PER_KMOL;
    double oilKmolPerDay = load.getOilRateBopd() * ProductionLoad.BARREL_TO_M3 * oilDensity / oilMolarMass;
    double[] kmolPerDay = new double[oilFractions.length];
    double total = 0.0;
    for (int i = 0; i < kmolPerDay.length; i++) {
      kmolPerDay[i] = gasKmolPerDay * gasFractions[i] + oilKmolPerDay * oilFractions[i];
    }
    if (waterIndex >= 0) {
      kmolPerDay[waterIndex] += load.getWaterRateM3d() * WATER_DENSITY / waterMolarMass;
    }
    for (double value : kmolPerDay) {
      total += value;
    }
    if (total <= 0.0) {
      return new HostFeed(referenceComposition, 0.0);
    }
    return new HostFeed(kmolPerDay, total * 1000.0 / 86400.0);
  }
}
