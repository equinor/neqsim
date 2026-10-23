package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Dynamic thermal regressions for adiabatic advection, elevation work and stiff radial heat exchange.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeDynamicThermalReviewTest {
  /**
   * A boundary temperature change must enter finite cell inventory through heat transport, without a hidden reset.
   */
  @Test
  void inletTemperatureStepRetainsFiniteCellThermalInertia() {
    TwoFluidPipe pipe = initializedPipe("finite-inlet-inertia");
    pipe.setIncludeEnergyEquation(true);
    pipe.setHeatTransferCoefficient(0.0);
    double[] initial = pipe.getTemperatureProfile();
    double heatCapacity = pipe.getInletStream().getFluid().getCp("J/kgK");
    TwoFluidSection[] before = pipe.getSectionSnapshots();
    pipe.getInletStream().setTemperature(290.0, "K");
    pipe.getInletStream().run();

    pipe.runTransient(1.0e-3, null);

    double[] after = pipe.getTemperatureProfile();
    assertTrue(after[0] > 290.0 && after[0] < initial[0],
        "The first physical cell must cool gradually rather than jump to the external feed temperature");
    double sensibleChange = 0.0;
    for (int cell = 0; cell < before.length; cell++) {
      double mass = before[cell].getGasMassPerLength() + before[cell].getOilMassPerLength()
          + before[cell].getWaterMassPerLength();
      sensibleChange += mass * before[cell].getLength() * heatCapacity * (after[cell] - initial[cell]);
    }
    assertEquals(sensibleChange, pipe.getLastThermalEnergyBalanceReport().getFluidEnergyChangeJ(),
        Math.max(1.0e-3, 1.0e-3 * Math.abs(sensibleChange)),
        "The reported thermal change must include the first physical cell's response to the boundary event");
  }

  /**
   * Explicit energy selection must transport a temperature disturbance without an ambient heat path.
   */
  @Test
  void adiabaticEnergyEquationTransportsAnInletTemperatureStep() {
    TwoFluidPipe pipe = initializedPipe("adiabatic-temperature-step");
    pipe.setIncludeEnergyEquation(true);
    pipe.setHeatTransferCoefficient(0.0);
    double[] initial = pipe.getTemperatureProfile();
    pipe.getInletStream().setTemperature(290.0, "K");
    pipe.getInletStream().run();

    pipe.runTransient(1.0e-3, null);

    assertNotNull(pipe.getLastThermalEnergyBalanceReport(),
        "Selecting the energy equation must retain its advection diagnostic even with overall U equal to zero");
    assertTrue(pipe.getTemperatureProfile()[1] < initial[1] - 1.0e-8,
        "The cooler inlet disturbance must start entering the downstream cell without ambient heat transfer");
  }

  /**
   * A finite radial time step must not create an unforced temperature outside the reservoir bounds.
   */
  @Test
  void thinOuterCoatingRemainsBetweenFluidAndAmbientAtALargeStep() {
    MultilayerThermalCalculator calculator = new MultilayerThermalCalculator(0.1);
    calculator.createBareSubseaPipe(0.2, 0.02);
    calculator.setOuterHTC(500.0);
    double[] temperatures = new double[calculator.getNumberOfLayers()];
    for (int layer = 0; layer < temperatures.length; layer++) {
      temperatures[layer] = 300.0;
    }

    TwoFluidPipe.advanceMultilayerCellThermalState(calculator, temperatures, 300.0, 280.0, 50.0, 10.0);

    for (int layer = 0; layer < temperatures.length; layer++) {
      assertTrue(temperatures[layer] >= 280.0 && temperatures[layer] <= 300.0,
          "Passive radial layer " + layer + " left the initial/ambient bounds: " + temperatures[layer]);
    }
  }

  /**
   * Selecting radial layers must not remove the potential-energy work present in the simple thermal route.
   *
   * @throws Exception if the isolated cell thermal update cannot be invoked
   */
  @Test
  void multilayerThermalRouteIncludesUphillGravityWork() throws Exception {
    TwoFluidPipe pipe = initializedPipe("multilayer-gravity-work");
    pipe.configureSubseaThermalModel(0.02, 0.0, RadialThermalLayer.MaterialType.PU_FOAM);
    pipe.setSurfaceTemperature(300.0, "K");
    for (RadialThermalLayer layer : pipe.getThermalCalculator().getLayers()) {
      layer.initializeTemperature(300.0);
    }
    TwoFluidSection[] sections = pipe.getSectionSnapshots();
    sections[0].setElevation(0.0);
    sections[1].setElevation(10.0);
    sections[0].setTemperature(300.0);
    sections[1].setTemperature(300.0);
    Field field = TwoFluidPipe.class.getDeclaredField("sections");
    field.setAccessible(true);
    field.set(pipe, sections);
    Field wall = TwoFluidPipe.class.getDeclaredField("wallTemperatureProfile");
    wall.setAccessible(true);
    wall.set(pipe, new double[] {300.0, 300.0});
    for (String name : new String[] {"hydrateRiskSections", "waxRiskSections"}) {
      Field risks = TwoFluidPipe.class.getDeclaredField(name);
      risks.setAccessible(true);
      risks.set(pipe, new boolean[2]);
    }
    double[][] flux = {{6.0, 0.0, 0.0}, {6.0, 0.0, 0.0}, {6.0, 0.0, 0.0}};
    Method update = TwoFluidPipe.class.getDeclaredMethod("updateTransientTemperatureMultilayer", double[][].class,
        double[].class, double[].class, double.class, double.class, double.class);
    update.setAccessible(true);

    update.invoke(pipe, flux, new double[] {300.0, 300.0}, new double[2], 1.0e-3, 2000.0, 0.0);

    assertTrue(sections[1].getTemperature() < 300.0 - 1.0e-8,
        "Uphill advection must supply gravitational potential energy when Joule-Thomson and radial losses are zero");
  }

  /**
   * Build a compact uniform dry-gas state without wall heat transfer or Joule-Thomson effects.
   *
   * @param name equipment name prefix
   * @return initialized two-cell pipe
   */
  private static TwoFluidPipe initializedPipe(String name) {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream(name + "-feed", fluid);
    inlet.setFlowRate(6.0, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe(name, inlet);
    pipe.setLength(20.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(2);
    pipe.setEnableAdaptiveTimestepping(false);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }
}
