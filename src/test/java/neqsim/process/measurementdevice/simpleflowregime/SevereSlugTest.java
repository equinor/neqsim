package neqsim.process.measurementdevice.simpleflowregime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

public class SevereSlugTest {
  private static SystemInterface threePhaseFluid() {
    SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos((273.15 + 15.0), 10);
    fluid.addComponent("methane", 0.00015, "MSm^3/day");
    fluid.addComponent("n-heptane", 0.0015, "MSm^3/day");
    fluid.addComponent("propane", 0.00015, "MSm^3/day");
    fluid.addComponent("water", 0.00015, "MSm^3/day");
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    return fluid;
  }

  private static Pipe labPipe() {
    return new Pipe("lab", 0.05, 167.0, 7.7, 2.0);
  }

  private static FluidSevereSlug airWater() {
    return new FluidSevereSlug(1000.0, 0.001, 0.029);
  }

  @Test
  void testCheckFlowRegime1() {
    Stream inputStream = new Stream("test stream", threePhaseFluid());
    // Stream at 10 bara delivering to a riser outlet at 1 bara
    SevereSlugAnalyser analyser = new SevereSlugAnalyser("tmp", inputStream, 0.05, 167, 7.7, 0.1, 1.0e5, 15.0, 500.0,
        50000);
    assertEquals("Severe Slug", analyser.getPredictedFlowRegime());
    assertEquals(0.175, analyser.getMeasuredValue(), 0.02);
    // The pressure swing is a large part of, but cannot exceed, the liquid head of the full riser
    double riserHeadBar = analyser.fluidSevereS.getLiqDensity() * ThermodynamicConstantsInterface.gravity * 7.7 / 1.0e5;
    assertTrue(analyser.getPressureAmplitude() > 0.5 * riserHeadBar);
    assertTrue(analyser.getPressureAmplitude() < 1.05 * riserHeadBar);
  }

  @Test
  void higherOutletPressureStabilisesTheRiser() {
    Stream inputStream = new Stream("test stream", threePhaseFluid());
    // Default constructor uses the stream pressure (10 bara) as riser outlet pressure
    SevereSlugAnalyser highPressure = new SevereSlugAnalyser("tmp", inputStream, 0.05, 167, 7.7, 0.1);
    assertEquals(10.0e5, highPressure.getOutletPressure(), 1.0);
    assertEquals(10.0e5, highPressure.getGasReferencePressure(), 1.0);
    assertEquals("Stratified Flow", highPressure.getPredictedFlowRegime());
    assertTrue(highPressure.getMeasuredValue() < 0.01);
    assertTrue(highPressure.getMeanPressure() > 10.0);
  }

  @Test
  void lumpsOilAndWaterByVolumeAndUsesSpecificGasConstant() {
    SystemInterface fluid = threePhaseFluid();
    FluidSevereSlug lumped = new FluidSevereSlug(fluid);

    double oilDensity = fluid.getPhase("oil").getDensity("kg/m3");
    double waterDensity = fluid.getPhase("aqueous").getDensity("kg/m3");
    double oilVolume = fluid.getPhase("oil").getFlowRate("m3/sec");
    double waterVolume = fluid.getPhase("aqueous").getFlowRate("m3/sec");
    double expectedDensity = (oilVolume * oilDensity + waterVolume * waterDensity) / (oilVolume + waterVolume);

    assertEquals(expectedDensity, lumped.getLiqDensity(), 1.0e-9);
    assertTrue(lumped.getLiqDensity() >= oilDensity && lumped.getLiqDensity() <= waterDensity);
    assertEquals(waterVolume / (oilVolume + waterVolume), lumped.getWaterVolumeFraction(), 1.0e-12);
    assertEquals(ThermodynamicConstantsInterface.R / fluid.getPhase("gas").getMolarMass(), lumped.getGasConstant(),
        1.0e-9);
  }

  @Test
  void explicitFluidGasConstantFollowsMolarMass() {
    FluidSevereSlug air = airWater();
    assertEquals(286.7, air.getGasConstant(), 0.1);
    air.setMolecularWeight(0.016);
    assertEquals(519.7, air.getGasConstant(), 0.1);
    assertThrows(IllegalArgumentException.class, () -> new FluidSevereSlug(-1.0, 0.001, 0.029));
  }

  @Test
  void lowRatesSlugAndHighGasRateIsStable() {
    SevereSlugAnalyser lowRate = new SevereSlugAnalyser("low", 0.1, 0.5, 1.0e5, 20.0, 500.0, 20000);
    assertEquals("Severe Slug", lowRate.getPredictedFlowRegime(airWater(), labPipe(), lowRate));
    assertTrue(lowRate.getSlugValue() > 0.1);

    SevereSlugAnalyser highGas = new SevereSlugAnalyser("high", 0.1, 5.0, 1.0e5, 20.0, 500.0, 20000);
    String regime = highGas.getPredictedFlowRegime(airWater(), labPipe(), highGas);
    assertFalse(regime.startsWith("Severe Slug"));
    assertEquals(0.0, highGas.getSlugValue(), 1.0e-6);
    assertEquals(0.0, highGas.getPressureAmplitude(), 1.0e-6);
  }

  @Test
  void simulationTimeIsHonouredAndResultIsResolutionIndependent() {
    SevereSlugAnalyser fine = new SevereSlugAnalyser("fine", 0.1, 0.5, 1.0e5, 20.0, 500.0, 20000);
    fine.checkFlowRegime(airWater(), labPipe(), fine);
    double[] time = fine.getTimeTrend();
    assertEquals(20000, time.length);
    assertEquals(500.0, time[time.length - 1], 1.0e-9);
    assertEquals(20000, fine.getPressureTrend().length);

    SevereSlugAnalyser coarse = new SevereSlugAnalyser("coarse", 0.1, 0.5, 1.0e5, 20.0, 500.0, 5000);
    coarse.checkFlowRegime(airWater(), labPipe(), coarse);
    assertEquals(fine.getSlugValue(), coarse.getSlugValue(), 0.02 * fine.getSlugValue());
  }

  @Test
  void repeatedRunsAreReproducible() {
    SevereSlugAnalyser analyser = new SevereSlugAnalyser("repeat", 0.3, 0.3, 1.0e5, 20.0, 300.0, 6000);
    analyser.checkFlowRegime(airWater(), labPipe(), analyser);
    double first = analyser.getSlugValue();
    analyser.checkFlowRegime(airWater(), labPipe(), analyser);
    assertEquals(first, analyser.getSlugValue(), 0.0);
    assertFalse(Double.isNaN(first));
  }

  @Test
  void stratifiedHoldUpIsBoundedMonotonicAndInputsAreValidated() {
    Pipe pipe = labPipe();
    FluidSevereSlug fluid = airWater();
    double previous = 0.0;
    for (double usl : new double[] { 0.01, 0.05, 0.1, 0.5, 1.0 }) {
      SevereSlugAnalyser analyser = new SevereSlugAnalyser("holdup", usl, 0.5);
      double holdUp = analyser.stratifiedHoldUp(fluid, pipe, analyser);
      assertTrue(holdUp > previous && holdUp < 1.0, "holdup at usl=" + usl + " was " + holdUp);
      previous = holdUp;
    }
    SevereSlugAnalyser noLiquid = new SevereSlugAnalyser("dry", 0.0, 0.5);
    assertEquals(0.0, noLiquid.stratifiedHoldUp(fluid, pipe, noLiquid), 0.0);

    assertThrows(IllegalArgumentException.class, () -> new Pipe("bad", -0.05, 167.0, 7.7, 2.0));
    assertThrows(IllegalArgumentException.class, () -> new SevereSlugAnalyser("bad", 1.0e5, 20.0, -1.0, 100));

    Stream inputStream = new Stream("test stream", threePhaseFluid());
    SevereSlugAnalyser upward = new SevereSlugAnalyser("up", inputStream, 0.05, 167, 7.7, -1.0);
    assertTrue(upward.getPredictedFlowRegime().startsWith("Regime cannot be detected"));
    assertEquals(0.0, upward.getMeasuredValue(), 0.0);
  }
}
