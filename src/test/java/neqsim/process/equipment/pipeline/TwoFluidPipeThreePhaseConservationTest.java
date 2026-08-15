package neqsim.process.equipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Steady-state conservation of the oil and water phases in three-phase (gas/oil/water) flow.
 *
 * <p>
 * The oil/water holdup split used to be produced by scaling the upstream water cut by a "stratification factor" while
 * the phase velocities were set independently. That marched downstream as a compounding recursion and satisfied no mass
 * balance, so the water mass flux more than doubled along a 5 km line. The split is now closed on the phase mass
 * balance through an oil-over-water slip ratio, which these tests pin.
 * </p>
 */
public class TwoFluidPipeThreePhaseConservationTest {

  private static final double PIPE_LENGTH = 5000.0;
  private static final double PIPE_DIAMETER = 0.30;
  private static final int SECTIONS = 40;
  private static final double MASS_FLOW_KG_PER_HOUR = 180000.0;

  private static SystemInterface threePhaseFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 50.0, 60.0);
    fluid.addComponent("methane", 60.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 3.0);
    fluid.addComponent("n-heptane", 20.0);
    fluid.addComponent("nC10", 12.0);
    fluid.addComponent("water", 25.0);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static TwoFluidPipe runPipe() {
    Stream feed = new Stream("feed", threePhaseFluid());
    feed.setFlowRate(MASS_FLOW_KG_PER_HOUR, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(PIPE_LENGTH);
    pipe.setDiameter(PIPE_DIAMETER);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS]);
    pipe.run();
    return pipe;
  }

  private static double inletPhaseMassFlow(Stream feed, String phase) {
    return feed.getFluid().getPhase(phase).getFlowRate("kg/sec");
  }

  @Test
  void testThreePhaseFluidActuallySplitsIntoThreePhases() {
    Stream feed = new Stream("feed", threePhaseFluid());
    feed.setFlowRate(MASS_FLOW_KG_PER_HOUR, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    Assertions.assertEquals(3, feed.getFluid().getNumberOfPhases(), "test fixture must produce gas, oil and water");
    Assertions.assertTrue(feed.getFluid().hasPhaseType("gas"));
    Assertions.assertTrue(feed.getFluid().hasPhaseType("oil"));
    Assertions.assertTrue(feed.getFluid().hasPhaseType("aqueous"));
  }

  @Test
  void testWaterMassFluxIsConservedAlongThePipe() {
    TwoFluidPipe pipe = runPipe();
    double[] waterFlow = pipe.getWaterMassFlowProfile();
    Assertions.assertEquals(SECTIONS, waterFlow.length);

    // Skip the inlet boundary cell, which carries the injected no-slip condition.
    double reference = waterFlow[1];
    Assertions.assertTrue(reference > 0.0, "three-phase case must transport water");
    for (int i = 1; i < waterFlow.length; i++) {
      double drift = Math.abs(waterFlow[i] - reference) / reference;
      Assertions.assertTrue(drift < 0.02, "water mass flux must not drift along the pipe, but section " + i
          + " carries " + waterFlow[i] + " kg/s against " + reference + " kg/s at section 1");
    }
  }

  @Test
  void testInteriorPhaseFluxesMatchTheInletPhaseFlows() {
    Stream feed = new Stream("feed", threePhaseFluid());
    feed.setFlowRate(MASS_FLOW_KG_PER_HOUR, "kg/hr");
    feed.setTemperature(50.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();
    double inletWater = inletPhaseMassFlow(feed, "aqueous");
    double inletOil = inletPhaseMassFlow(feed, "oil");

    TwoFluidPipe pipe = new TwoFluidPipe("pipe", feed);
    pipe.setLength(PIPE_LENGTH);
    pipe.setDiameter(PIPE_DIAMETER);
    pipe.setNumberOfSections(SECTIONS);
    pipe.setElevationProfile(new double[SECTIONS]);
    pipe.run();

    double[] waterFlow = pipe.getWaterMassFlowProfile();
    double[] oilFlow = pipe.getOilMassFlowProfile();

    // Condensation redistributes a little mass between gas and oil along the line, so the
    // oil tolerance is looser than the water one.
    for (int i = 1; i < SECTIONS; i++) {
      Assertions.assertEquals(inletWater, waterFlow[i], 0.02 * inletWater,
          "water mass flux at section " + i + " must match the injected water flow");
      Assertions.assertEquals(inletOil, oilFlow[i], 0.03 * inletOil,
          "oil mass flux at section " + i + " must match the injected oil flow");
    }
  }

  @Test
  void testWaterHoldupFractionIsAtLeastTheTransportedFraction() {
    TwoFluidPipe pipe = runPipe();
    double[] waterHoldup = pipe.getWaterHoldupProfile();
    double[] oilHoldup = pipe.getOilHoldupProfile();
    double[] waterVelocity = pipe.getWaterVelocityProfile();
    double[] oilVelocity = pipe.getOilVelocityProfile();

    for (int i = 1; i < SECTIONS; i++) {
      Assertions.assertTrue(waterHoldup[i] > 0.0 && oilHoldup[i] > 0.0, "both liquids must be present at section " + i);
      // Water is denser, so it may lag the oil but must never run ahead of it.
      Assertions.assertTrue(waterVelocity[i] <= oilVelocity[i] * 1.001,
          "water must not travel faster than oil at section " + i + ": v_w=" + waterVelocity[i] + " v_o="
              + oilVelocity[i]);
    }
  }

  @Test
  void testLiquidInventoryStaysBelowThePipeVolume() {
    TwoFluidPipe pipe = runPipe();
    double pipeVolume = Math.PI * PIPE_DIAMETER * PIPE_DIAMETER / 4.0 * PIPE_LENGTH;
    double inventory = pipe.getLiquidInventory("m3");
    Assertions.assertTrue(inventory > 0.0, "three-phase line must hold liquid");
    Assertions.assertTrue(inventory < pipeVolume,
        "liquid inventory " + inventory + " m3 cannot exceed the pipe volume " + pipeVolume + " m3");
  }
}
