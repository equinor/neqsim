package neqsim.process.equipment.tank;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link MountainCavern}.
 *
 * @author NeqSim
 */
class MountainCavernTest {
  private Stream createFeedStream(double flowKgPerHr) {
    SystemInterface fluid = new SystemSrkEos(288.15, 2.0);
    fluid.addComponent("methane", 0.02);
    fluid.addComponent("ethane", 0.03);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-heptane", 0.92);
    fluid.setMixingRule(2);

    Stream feed = new Stream("cavern feed", fluid);
    feed.setFlowRate(flowKgPerHr, "kg/hr");
    feed.setTemperature(9.0, "C");
    feed.setPressure(2.0, "bara");
    feed.run();
    return feed;
  }

  @Test
  void testPressureAccumulatesWhenWithdrawalIsStopped() {
    MountainCavern cavern = new MountainCavern("TE21901", createFeedStream(50000.0));
    cavern.setTotalVolume(50000.0);
    cavern.run(UUID.randomUUID());
    double startPressure = cavern.getCavernPressure();

    cavern.setLiquidWithdrawalRate(0.0); // downstream (SCUP) stop
    double dt = 1.0; // hours
    double pressure = startPressure;
    for (int i = 0; i < 10; i++) {
      cavern.runTransient(dt, UUID.randomUUID());
      pressure = cavern.getCavernPressure();
    }

    assertTrue(pressure > startPressure, "Cavern pressure should rise once the downstream withdrawal stops (start="
        + startPressure + ", end=" + pressure + ")");
    assertTrue(cavern.getLiquidLevelFraction() > 0.0, "Liquid should be accumulating");
  }

  @Test
  void testVentSubstantiallyReducesThePressureRiseComparedToNoVent() {
    // A vent set well below the natural bubble-point pressure of the residual liquid (once the
    // free gas cap has been stripped) cannot push the equilibrium pressure below that bubble
    // point - venting only removes free gas, it cannot remove dissolved light ends still in
    // equilibrium with the liquid. The meaningful, composition-independent check is therefore a
    // RELATIVE one: with ample vent capacity, opening the vent substantially reduces the
    // pressure rise relative to an otherwise-identical vessel with no vent at all.
    MountainCavern vented = new MountainCavern("TE21901-vented", createFeedStream(50000.0));
    vented.setTotalVolume(50000.0);
    vented.enableVent(1.5, 5.0e6);
    vented.run(UUID.randomUUID());
    vented.setLiquidWithdrawalRate(0.0);
    double startVented = vented.getCavernPressure();

    MountainCavern unvented = new MountainCavern("TE21901-novent", createFeedStream(50000.0));
    unvented.setTotalVolume(50000.0);
    unvented.run(UUID.randomUUID());
    unvented.setLiquidWithdrawalRate(0.0);
    double startUnvented = unvented.getCavernPressure();

    double dt = 2.0; // hours
    for (int i = 0; i < 40; i++) {
      vented.runTransient(dt, UUID.randomUUID());
      unvented.runTransient(dt, UUID.randomUUID());
    }

    double ventedRise = vented.getCavernPressure() - startVented;
    double unventedRise = unvented.getCavernPressure() - startUnvented;
    assertTrue(ventedRise < 0.5 * unventedRise,
        "Opening the vent should substantially reduce the pressure rise relative to the " + "no-vent case (ventedRise="
            + ventedRise + ", unventedRise=" + unventedRise + ")");
  }

  @Test
  void testLargerDropLineRelaxationTimeDampensTheEarlyPressureRise() {
    double dt = 0.5; // hours
    int steps = 6;
    // A gentle feed rate relative to the cavern volume, so the composition shifts smoothly step
    // to step and the fixed-volume flash converges reliably (a much larger feed rate can push
    // the mixture through a numerically stiff, near-critical region every step, which is a
    // solver-robustness question orthogonal to the relaxation-lag behaviour under test here).
    double feedRateKgPerHr = 5000.0;

    // Seed ONE cavern, then fork "fast" from its exact inventory (rather than independently
    // seeding two instances): a nearly-incompressible, liquid-dominated cavern can amplify even
    // tiny mole/composition differences between two independent bisection-seeded states into a
    // large pressure difference, which would otherwise swamp the (much smaller) effect of the
    // relaxation time constant under test here.
    MountainCavern damped = new MountainCavern("damped", createFeedStream(feedRateKgPerHr));
    damped.setTotalVolume(50000.0);
    damped.setDropLineRelaxationTime(20.0); // strong drop-line lag
    damped.run(UUID.randomUUID());
    damped.setLiquidWithdrawalRate(0.0);
    double startDamped = damped.getCavernPressure();

    MountainCavern fast = new MountainCavern("fast", createFeedStream(feedRateKgPerHr));
    fast.setTotalVolume(50000.0);
    fast.setDropLineRelaxationTime(1.0e-6); // ~instant equilibrium
    fast.copyInventoryFrom(damped);
    fast.setLiquidWithdrawalRate(0.0);
    double startFast = fast.getCavernPressure();

    for (int i = 0; i < steps; i++) {
      fast.runTransient(dt, UUID.randomUUID());
      damped.runTransient(dt, UUID.randomUUID());
    }

    double fastRise = fast.getCavernPressure() - startFast;
    double dampedRise = damped.getCavernPressure() - startDamped;
    assertTrue(dampedRise < fastRise,
        "A larger drop-line relaxation time constant should visibly slow the early pressure "
            + "rise relative to the near-instant-equilibrium case (fast=" + fastRise + ", damped=" + dampedRise + ")");
  }
}
