package neqsim.process.fielddevelopment.tieback.capacity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for load-dependent host feed composition in the tie-in capacity planner.
 *
 * @author ESOL
 * @version 1.0
 */
class HostFeedProviderTest {
  /** Ideal-gas standard molar volume in Sm3/kmol used by the provider. */
  private static final double SM3_PER_KMOL = 23.6443;

  /**
   * Verifies the recombined feed honours the gas volume and moves to a heavier composition when oil is added.
   */
  @Test
  void recombinedFeedHonoursVolumesAndGetsHeavierWithOil() {
    RecombinedHostFeedProvider provider = new RecombinedHostFeedProvider(createReservoirFluid());
    assertTrue(provider.getReferenceGor() > 50.0);

    HostFeed gasOnly = provider.getFeed(new ProductionLoad(2030, 1.0, 0.0, 0.0, 0.0));
    double expectedMolPerSec = 1.0e6 / SM3_PER_KMOL * 1000.0 / 86400.0;
    assertEquals(expectedMolPerSec, gasOnly.getMolarRateMolPerSec(), 1.0e-6 * expectedMolPerSec);

    HostFeed withOil = provider.getFeed(new ProductionLoad(2030, 1.0, 5000.0, 0.0, 0.0));
    int heavy = gasOnly.getNumberOfComponents() - 1;
    assertTrue(withOil.getMoleFractions()[heavy] > gasOnly.getMoleFractions()[heavy]);
    assertTrue(withOil.getMolarRateMolPerSec() > gasOnly.getMolarRateMolPerSec());
  }

  /**
   * Verifies a zero load returns a zero-rate feed on the reference composition.
   */
  @Test
  void zeroLoadReturnsZeroRateFeed() {
    RecombinedHostFeedProvider provider = new RecombinedHostFeedProvider(createReservoirFluid());
    HostFeed feed = provider.getFeed(new ProductionLoad(2030, 0.0, 0.0, 0.0, 0.0));
    assertEquals(0.0, feed.getMolarRateMolPerSec(), 0.0);
    assertEquals(1.0, sum(feed.getMoleFractions()), 1.0e-12);
  }

  /**
   * Verifies the feed object rejects invalid compositions.
   */
  @Test
  void hostFeedValidatesInput() {
    assertThrows(IllegalArgumentException.class, () -> new HostFeed(new double[0], 1.0));
    assertThrows(IllegalArgumentException.class, () -> new HostFeed(new double[] {0.0, 0.0}, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new HostFeed(new double[] {-0.1, 1.1}, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new HostFeed(new double[] {1.0}, -1.0));
  }

  /**
   * Verifies the planner solves the host model with the recombined composition, accepts the satellite share that fits a
   * mass-flow limit, accepts less gas when the satellite carries oil, and restores the stream afterwards.
   */
  @Test
  void plannerUsesFeedProviderAndRestoresStream() {
    SystemInterface reservoir = createReservoirFluid();
    RecombinedHostFeedProvider provider = new RecombinedHostFeedProvider(reservoir);
    double baseMass = massKgPerHour(reservoir, provider.getFeed(new ProductionLoad(2030, 1.0, 0.0, 0.0, 0.0)));

    final Stream hostFeed = new Stream("Host Feed", reservoir.clone());
    hostFeed.setFlowRate(1000.0, "kg/hr");
    hostFeed.addCapacityConstraint(new CapacityConstraint("hostFeedFlow", "kg/hr", ConstraintType.HARD)
        .setDesignValue(2.5 * baseMass).setValueSupplier(() -> hostFeed.getFlowRate("kg/hr")));
    ProcessSystem process = new ProcessSystem("host process");
    process.add(hostFeed);
    process.run();
    double[] originalComposition = hostFeed.getFluid().getMolarComposition();

    HostFacility host = HostFacility.builder("Host F").gasCapacity(50.0).processSystem(process).build();
    HostTieInPoint tieIn = new HostTieInPoint("Host Feed", "kg/hr").setFeedProvider(provider);
    ProductionProfileSeries base = new ProductionProfileSeries("base").addPeriod(2030, 1.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries leanSatellite = new ProductionProfileSeries("sat").addPeriod(2030, 4.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries oilySatellite = new ProductionProfileSeries("sat").addPeriod(2030, 4.0, 20000.0, 0.0, 0.0);

    TieInPeriodResult lean = new TieInCapacityPlanner(host).setHostProductionProfile(base)
        .setSatelliteProductionProfile(leanSatellite).setTieInPoint(tieIn).setProcessUtilizationLimit(1.0).run()
        .getPeriodResults().get(0);
    assertTrue(lean.isProcessModelUsed());
    assertEquals(1.5, lean.getAcceptedSatellite().getGasRateMSm3d(), 0.02);

    TieInPeriodResult oily = new TieInCapacityPlanner(host).setHostProductionProfile(base)
        .setSatelliteProductionProfile(oilySatellite).setTieInPoint(tieIn).setProcessUtilizationLimit(1.0).run()
        .getPeriodResults().get(0);
    assertTrue(oily.getAcceptedSatellite().getGasRateMSm3d() < lean.getAcceptedSatellite().getGasRateMSm3d());

    assertEquals(1000.0, hostFeed.getFlowRate("kg/hr"), 1.0e-6);
    assertArrayEquals(originalComposition, hostFeed.getFluid().getMolarComposition(), 1.0e-9);
  }

  /**
   * Sums an array.
   *
   * @param values values to add
   * @return the sum
   */
  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }

  /**
   * Mass flow of a host feed.
   *
   * @param fluid fluid that defines the component molar masses
   * @param feed molar feed on the component order of the fluid
   * @return mass flow in kg/hr
   */
  private static double massKgPerHour(SystemInterface fluid, HostFeed feed) {
    double molarMass = 0.0;
    double[] x = feed.getMoleFractions();
    for (int i = 0; i < x.length; i++) {
      molarMass += x[i] * fluid.getPhase(0).getComponent(i).getMolarMass();
    }
    return feed.getMolarRateMolPerSec() * molarMass * 3600.0;
  }

  /**
   * Creates a gas-condensate reservoir fluid for the tests.
   *
   * @return flashed SRK fluid with five components
   */
  private static SystemInterface createReservoirFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 90.0, 250.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-hexane", 0.05);
    fluid.addComponent("n-decane", 0.22);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    return fluid;
  }
}
