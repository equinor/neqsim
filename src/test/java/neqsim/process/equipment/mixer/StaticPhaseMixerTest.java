package neqsim.process.equipment.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for phase-preserving static mixing. */
class StaticPhaseMixerTest {

  @Test
  void testSeparatorRecombinationConservesMassAtBaseAndNearbyTemperatures() {
    double[] temperatures = new double[] { 288.15, 293.15 };

    for (double temperature : temperatures) {
      SystemInterface fluid = createSeparatorFeed(temperature);
      Stream feed = new Stream("feed", fluid);
      feed.setFlowRate(50000.0, "kg/hr");
      feed.run();

      Separator separator = new Separator("separator", feed);
      separator.run();

      StreamInterface gas = separator.getGasOutStream();
      StreamInterface liquid = separator.getLiquidOutStream();
      double gasMassBefore = gas.getFlowRate("kg/hr");
      double liquidMassBefore = liquid.getFlowRate("kg/hr");

      StaticPhaseMixer mixer = new StaticPhaseMixer("phase mixer");
      mixer.addStream(gas);
      mixer.addStream(liquid);

      ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
      ByteArrayOutputStream standardError = new ByteArrayOutputStream();
      PrintStream originalOutput = System.out;
      PrintStream originalError = System.err;
      PrintStream capturedOutput = new PrintStream(standardOutput, true);
      PrintStream capturedError = new PrintStream(standardError, true);
      double firstRunMass;
      double[] firstRunComponentMasses;
      try {
        System.setOut(capturedOutput);
        System.setErr(capturedError);
        mixer.run();
        firstRunMass = mixer.getOutletStream().getFlowRate("kg/hr");
        firstRunComponentMasses = componentMasses(mixer.getOutletStream().getThermoSystem());
        mixer.run();
      } finally {
        capturedOutput.flush();
        capturedError.flush();
        System.setOut(originalOutput);
        System.setErr(originalError);
      }

      assertEquals("", standardOutput.toString(), "normal operation must not write to stdout");
      assertEquals("", standardError.toString(), "normal operation must not write to stderr");
      assertEquals(gasMassBefore, gas.getFlowRate("kg/hr"), gasMassBefore * 1.0e-12,
          "mixer must not mutate the gas inlet");
      assertEquals(liquidMassBefore, liquid.getFlowRate("kg/hr"), liquidMassBefore * 1.0e-12,
          "mixer must not mutate the liquid inlet");

      double inletMass = gasMassBefore + liquidMassBefore;
      double outletMass = mixer.getOutletStream().getFlowRate("kg/hr");
      assertEquals(inletMass, outletMass, inletMass * 1.0e-10,
          "total mass must close after separator phase recombination");
      assertEquals(firstRunMass, outletMass, inletMass * 1.0e-12,
          "repeated runs must preserve the mixed mass inventory");

      assertComponentMassBalance(gas, liquid, mixer.getOutletStream());
      assertComponentMassesEqual(firstRunComponentMasses, mixer.getOutletStream().getThermoSystem());
      assertTrue(mixer.getOutletStream().getThermoSystem().hasPhaseType("gas"));
      assertTrue(mixer.getOutletStream().getThermoSystem().hasPhaseType("oil"),
          "the hydrocarbon-liquid phase assignment must be preserved");
      assertTrue(liquid.getThermoSystem().hasPhaseType("aqueous"),
          "the regression fluid must exercise the separator's aqueous phase");
      assertTrue(mixer.getOutletStream().getThermoSystem().hasPhaseType("aqueous"));
      assertTrue(Double.isFinite(mixer.getOutletStream().getThermoSystem().getDensity("kg/m3")));
    }
  }

  @Test
  void testOnePhaseAndSparseComponentSlates() {
    SystemInterface gasFluid = new SystemSrkEos(303.15, 30.0);
    gasFluid.addComponent("methane", 0.9);
    gasFluid.addComponent("ethane", 0.1);
    gasFluid.setMixingRule("classic");
    gasFluid.setForceSinglePhase(PhaseType.GAS);
    Stream gas = new Stream("gas", gasFluid);
    gas.setFlowRate(1000.0, "kg/hr");
    gas.run();

    StaticPhaseMixer onePhaseMixer = new StaticPhaseMixer("one phase mixer");
    onePhaseMixer.addStream(gas);
    onePhaseMixer.run();
    assertEquals(gas.getFlowRate("kg/hr"), onePhaseMixer.getOutletStream().getFlowRate("kg/hr"),
        gas.getFlowRate("kg/hr") * 1.0e-10);
    assertTrue(onePhaseMixer.getOutletStream().getThermoSystem().hasPhaseType("gas"));

    SystemInterface liquidFluid = new SystemSrkEos(303.15, 30.0);
    liquidFluid.addComponent("methane", 0.2);
    liquidFluid.addComponent("n-heptane", 0.8);
    liquidFluid.addPlusFraction("C20", 0.1, 0.250, 0.85);
    liquidFluid.setMixingRule("classic");
    liquidFluid.setForceSinglePhase(PhaseType.LIQUID);
    Stream liquid = new Stream("liquid with sparse slate", liquidFluid);
    liquid.setFlowRate(750.0, "kg/hr");
    liquid.run();

    StaticPhaseMixer sparseMixer = new StaticPhaseMixer("sparse component mixer");
    sparseMixer.addStream(gas);
    sparseMixer.addStream(liquid);
    sparseMixer.run();

    double inletMass = gas.getFlowRate("kg/hr") + liquid.getFlowRate("kg/hr");
    assertEquals(inletMass, sparseMixer.getOutletStream().getFlowRate("kg/hr"), inletMass * 1.0e-10);
    assertTrue(sparseMixer.getOutletStream().getThermoSystem().getPhase(0).hasComponent("n-heptane"));
    assertTrue(sparseMixer.getOutletStream().getThermoSystem().getPhase(0).getComponent("C20_PC").isIsPlusFraction(),
        "sparse plus-fraction characterization must be preserved");
  }

  @Test
  void testActivePhaseReorderingDoesNotChangeInventory() {
    SystemInterface fluid = createSeparatorFeed(288.15);
    Stream feed = new Stream("reordering feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    Separator separator = new Separator("reordering separator", feed);
    separator.run();
    StreamInterface gas = separator.getGasOutStream();
    SystemInterface reorderedLiquidSystem = separator.getLiquidOutStream().getThermoSystem().clone();
    assertTrue(reorderedLiquidSystem.getNumberOfPhases() >= 2,
        "the regression fluid must provide two liquid-outlet phases to reorder");
    int firstPhysicalPhase = reorderedLiquidSystem.getPhaseIndex(0);
    int secondPhysicalPhase = reorderedLiquidSystem.getPhaseIndex(1);
    reorderedLiquidSystem.setPhaseIndex(0, secondPhysicalPhase);
    reorderedLiquidSystem.setPhaseIndex(1, firstPhysicalPhase);
    Stream reorderedLiquid = new Stream("reordered liquid", reorderedLiquidSystem);

    StaticPhaseMixer mixer = new StaticPhaseMixer("reordered phase mixer");
    mixer.addStream(gas);
    mixer.addStream(reorderedLiquid);
    mixer.run();

    double inletMass = gas.getFlowRate("kg/hr") + reorderedLiquid.getFlowRate("kg/hr");
    assertEquals(inletMass, mixer.getOutletStream().getFlowRate("kg/hr"), inletMass * 1.0e-10);
    assertComponentMassBalance(gas, reorderedLiquid, mixer.getOutletStream());
    assertTrue(mixer.getOutletStream().getThermoSystem().hasPhaseType("gas"));
    assertTrue(mixer.getOutletStream().getThermoSystem().hasPhaseType("oil"),
        "the hydrocarbon-liquid phase assignment must be preserved");
    assertTrue(mixer.getOutletStream().getThermoSystem().hasPhaseType("aqueous"));
  }

  @Test
  void testZeroFlowBypassKeepsFinitePhaseFractions() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream zeroFlow = new Stream("zero flow", fluid);
    zeroFlow.setFlowRate(0.0, "kg/hr");

    StaticPhaseMixer mixer = new StaticPhaseMixer("zero-flow phase mixer");
    mixer.addStream(zeroFlow);
    mixer.run();

    assertEquals(0.0, mixer.getOutletStream().getFlowRate("kg/hr"), 0.0);
    for (int phaseIndex = 0; phaseIndex < mixer.getOutletStream().getThermoSystem().getNumberOfPhases(); phaseIndex++) {
      assertTrue(Double.isFinite(mixer.getOutletStream().getThermoSystem().getBeta(phaseIndex)));
    }

    zeroFlow.setFlowRate(0.5, "kg/hr");
    mixer.setMinimumFlow(1.0);
    mixer.run();
    assertEquals(0.0, mixer.getOutletStream().getFlowRate("kg/hr"), 0.0,
        "sub-threshold inlet inventory must be cleared");
    assertTrue(!mixer.isActive(), "a sub-threshold mixer must be marked inactive");
  }

  private static SystemInterface createSeparatorFeed(double temperature) {
    SystemInterface fluid = new SystemSrkEos(temperature, 55.0);
    fluid.addComponent("nitrogen", 0.010);
    fluid.addComponent("CO2", 0.025);
    fluid.addComponent("methane", 0.720);
    fluid.addComponent("ethane", 0.090);
    fluid.addComponent("propane", 0.060);
    fluid.addComponent("i-butane", 0.018);
    fluid.addComponent("n-butane", 0.025);
    fluid.addComponent("i-pentane", 0.012);
    fluid.addComponent("n-pentane", 0.012);
    fluid.addComponent("n-hexane", 0.010);
    fluid.addComponent("n-heptane", 0.008);
    fluid.addComponent("n-octane", 0.005);
    fluid.addComponent("water", 0.005);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static void assertComponentMassBalance(StreamInterface gas, StreamInterface liquid, StreamInterface outlet) {
    SystemInterface outletSystem = outlet.getThermoSystem();
    for (int componentIndex = 0; componentIndex < outletSystem.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface outletComponent = outletSystem.getPhase(0).getComponent(componentIndex);
      String componentName = outletComponent.getName();
      double inletComponentMass = componentMass(gas.getThermoSystem(), componentName)
          + componentMass(liquid.getThermoSystem(), componentName);
      double outletComponentMass = componentMass(outletSystem, componentName);
      double tolerance = Math.max(1.0e-12, inletComponentMass * 1.0e-10);
      assertEquals(inletComponentMass, outletComponentMass, tolerance,
          "component mass must close for " + componentName);
    }
  }

  private static double componentMass(SystemInterface system, String componentName) {
    double mass = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      ComponentInterface component = system.getPhase(phaseIndex).getComponent(componentName);
      mass += component.getNumberOfMolesInPhase() * component.getMolarMass();
    }
    return mass;
  }

  private static double[] componentMasses(SystemInterface system) {
    double[] masses = new double[system.getPhase(0).getNumberOfComponents()];
    for (int componentIndex = 0; componentIndex < masses.length; componentIndex++) {
      masses[componentIndex] = componentMass(system, system.getPhase(0).getComponent(componentIndex).getName());
    }
    return masses;
  }

  private static void assertComponentMassesEqual(double[] expectedMasses, SystemInterface system) {
    for (int componentIndex = 0; componentIndex < expectedMasses.length; componentIndex++) {
      String componentName = system.getPhase(0).getComponent(componentIndex).getName();
      double tolerance = Math.max(1.0e-12, expectedMasses[componentIndex] * 1.0e-12);
      assertEquals(expectedMasses[componentIndex], componentMass(system, componentName), tolerance,
          "repeated runs must preserve component mass for " + componentName);
    }
  }
}
