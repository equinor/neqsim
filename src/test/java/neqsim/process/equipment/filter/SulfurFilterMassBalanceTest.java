package neqsim.process.equipment.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.reactor.SulfurOxidationReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Material-balance regressions for solid S8 capture, including issue #3621.
 */
class SulfurFilterMassBalanceTest extends neqsim.NeqSimTest {
  @ParameterizedTest
  @ValueSource(doubles = { 0.0, 0.9, 1.0 })
  void gasSolidCaptureConservesMassAndComponents(double efficiency) {
    checkCapture(createFeed(false), efficiency, false);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.0, 0.9, 1.0 })
  void wetMultiphaseCaptureConservesMassAndComponents(double efficiency) {
    checkCapture(createFeed(true), efficiency, true);
  }

  @Test
  void reactorFeedLosesExactlyTheReportedCapturedSulfur() {
    SystemInterface fluid = new SystemPrEos(308.15, 9.0);
    fluid.addComponent("methane", 0.979);
    fluid.addComponent("H2S", 0.02);
    fluid.addComponent("oxygen", 0.001);
    fluid.addComponent("water", 0.001);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(1000.0, "kg/hr");
    Stream inlet = new Stream("inlet", fluid);
    inlet.run();

    SulfurOxidationReactor reactor = new SulfurOxidationReactor("reactor", inlet);
    reactor.setH2SConversionTarget(0.025);
    reactor.setSolidFlashEnabled(false);
    reactor.run();

    checkCapture(reactor.getOutStream(), 0.9, false);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 0.0, 1.0e-20 })
  void feedWithoutSolidSulfurResetsCaptureAndSupersaturation(double s8Moles) {
    SulfurFilter filter = new SulfurFilter("filter", createFeed(false));
    filter.setDeltaP(0.0);
    filter.setRemovalEfficiency(0.9);
    filter.run();
    assertTrue(filter.getSolidSulfurRemovalRate() > 0.0);

    SystemInterface fluid = new SystemPrEos(373.15, 9.0);
    fluid.addComponent("methane", 0.98);
    fluid.addComponent("H2S", 0.02);
    if (s8Moles > 0.0) {
      fluid.addComponent("S8", s8Moles);
    }
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1000.0, "kg/hr");
    Stream cleanFeed = new Stream("clean feed", fluid);
    cleanFeed.run();
    filter.setInletStream(cleanFeed);
    filter.run();

    assertFalse(filter.isSolidS8Detected());
    assertEquals(0.0, filter.getSolidSulfurRemovalRate());
    assertEquals(0.0, filter.getSolidS8MassFractionInlet());
    assertEquals(1.0, filter.getSupersaturationRatio());
    assertEquals(cleanFeed.getFlowRate("kg/hr"), filter.getOutStream().getFlowRate("kg/hr"), 1.0e-7);
    assertInventories(filter.getOutStream().getThermoSystem(), componentMoles(cleanFeed.getThermoSystem()), 0.0);
  }

  @Test
  void transientLoadingMatchesCapturedMass() {
    Stream feed = createFeed(false);
    SulfurFilter filter = new SulfurFilter("filter", feed);
    filter.setDeltaP(0.0);
    filter.setRemovalEfficiency(0.9);
    filter.setFilterElementCapacity(1000.0);
    filter.setNumberOfElements(1);
    filter.setPressureDropIncreaseAtCapacity(0.0);
    filter.setCalculateSteadyState(false);
    filter.runTransient(600.0, UUID.randomUUID());

    double massLoss = feed.getFlowRate("kg/hr") - filter.getOutStream().getFlowRate("kg/hr");
    assertTrue(massLoss > 0.0);
    assertEquals(massLoss, filter.getSolidSulfurRemovalRate(), 1.0e-7);
    assertEquals(massLoss * 600.0 / 3600.0, filter.getSolidsLoading(), 1.0e-7);
  }

  /**
   * Creates a gas/solid feed with an optional separate aqueous phase.
   *
   * @param wet whether water is present
   * @return initialized feed
   */
  private Stream createFeed(boolean wet) {
    SystemInterface fluid = new SystemPrEos(308.15, 9.0);
    fluid.addComponent("methane", 0.979);
    fluid.addComponent("H2S", 0.02);
    fluid.addComponent("S8", 0.001);
    if (wet) {
      fluid.addComponent("water", 0.02);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setSolidPhaseCheck("S8");
    fluid.setTotalFlowRate(1000.0, "kg/hr");
    Stream feed = new Stream("feed", fluid);
    feed.run();
    return feed;
  }

  /**
   * Checks capture against the independently flashed solid-phase inventory.
   *
   * @param feed filter inlet
   * @param efficiency solid removal fraction
   * @param requireAqueous whether a separate aqueous phase must be present
   */
  private void checkCapture(StreamInterface feed, double efficiency, boolean requireAqueous) {
    SystemInterface inlet = feed.getThermoSystem();
    double[] inletMoles = componentMoles(inlet);
    double inletMass = feed.getFlowRate("kg/hr");
    SystemInterface equilibrium = inlet.clone();
    equilibrium.setMultiPhaseCheck(true);
    equilibrium.setSolidPhaseCheck("S8");
    new ThermodynamicOperations(equilibrium).TPSolidflash();
    equilibrium.initProperties();
    assertTrue(equilibrium.hasPhaseType(PhaseType.GAS));
    assertTrue(equilibrium.hasPhaseType(PhaseType.SOLID));
    if (requireAqueous) {
      assertTrue(equilibrium.hasPhaseType(PhaseType.AQUEOUS));
    }

    double solidS8Moles = 0.0;
    for (int phase = 0; phase < equilibrium.getNumberOfPhases(); phase++) {
      if (equilibrium.getPhase(phase).getType() == PhaseType.SOLID) {
        solidS8Moles += equilibrium.getPhase(phase).getComponent("S8").getNumberOfMolesInPhase();
      }
    }
    assertTrue(solidS8Moles > 0.0);
    double capturedMoles = solidS8Moles * efficiency;
    double molarMass = inlet.getComponent("S8").getMolarMass();
    double capturedMass = capturedMoles * molarMass * 3600.0;

    SulfurFilter filter = new SulfurFilter("filter", feed);
    filter.setDeltaP(0.0);
    filter.setRemovalEfficiency(efficiency);
    UUID calculationId = UUID.randomUUID();
    filter.run(calculationId);

    assertEquals(calculationId, filter.getOutStream().getCalculationIdentifier());
    assertEquals(capturedMass, filter.getSolidSulfurRemovalRate(), 1.0e-7);
    assertEquals(capturedMass, inletMass - filter.getOutStream().getFlowRate("kg/hr"), 1.0e-7);
    assertEquals(solidS8Moles * molarMass * 3600.0 / inletMass, filter.getSolidS8MassFractionInlet(), 1.0e-10);
    assertInventories(inlet, inletMoles, 0.0);
    assertInventories(filter.getOutStream().getThermoSystem(), inletMoles, capturedMoles);

    filter.run(UUID.randomUUID());
    assertEquals(capturedMass, filter.getSolidSulfurRemovalRate(), 1.0e-7);
    assertEquals(capturedMass, inletMass - filter.getOutStream().getFlowRate("kg/hr"), 1.0e-7);
    assertInventories(inlet, inletMoles, 0.0);
    assertInventories(filter.getOutStream().getThermoSystem(), inletMoles, capturedMoles);
    assertEquals(0.0, filter.getSolidsLoading());
  }

  /**
   * Reads the system component inventories once, without summing their replicated global counters.
   *
   * @param fluid thermodynamic system
   * @return component molar flows in mol/s
   */
  private double[] componentMoles(SystemInterface fluid) {
    double[] moles = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < moles.length; component++) {
      moles[component] = fluid.getComponent(component).getNumberOfmoles();
    }
    return moles;
  }

  /**
   * Checks both the overall component ledger and the sum of physical phase inventories.
   *
   * @param fluid thermodynamic system to check
   * @param inletMoles original component molar flows
   * @param capturedS8 captured S8 molar flow
   */
  private void assertInventories(SystemInterface fluid, double[] inletMoles, double capturedS8) {
    for (int component = 0; component < inletMoles.length; component++) {
      String name = fluid.getComponent(component).getComponentName();
      double expected = inletMoles[component] - ("S8".equals(name) ? capturedS8 : 0.0);
      assertEquals(expected, fluid.getComponent(component).getNumberOfmoles(), 1.0e-9, name);
      double phaseMoles = 0.0;
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        double moles = fluid.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
        assertTrue(moles >= -1.0e-12, name + " has a negative phase inventory");
        phaseMoles += moles;
      }
      assertEquals(expected, phaseMoles, 1.0e-8, name + " phase balance");
    }
  }
}
