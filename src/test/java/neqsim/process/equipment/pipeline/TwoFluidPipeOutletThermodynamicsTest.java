package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression coverage for initialized, composable outlet states after flow normalization (#3685). */
class TwoFluidPipeOutletThermodynamicsTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeOutletThermodynamicsTest.class);

  @Test
  void wetWellPublishesThreePhasesAndPropertiesWithoutASecondFlash() {
    Stream feed = wetFeed();
    SystemInterface originalFeed = feed.getFluid().clone();
    TwoFluidPipe well = pipe("well", feed, 3100.0, 0.23, 2380.0, 0.0);
    well.run();

    assertOutletState(well, 3);
    assertNotSame(feed.getFluid(), well.getOutletStream().getFluid());
    assertEquals(originalFeed.getPressure(), feed.getPressure(), 0.0);
    assertEquals(originalFeed.getTemperature(), feed.getTemperature(), 0.0);
    assertEquals(originalFeed.getCp("J/kgK"), feed.getFluid().getCp("J/kgK"), 0.0);
    assertComponentClosure(originalFeed, feed.getFluid());

    // Reuse the same connected pipe after changing both pressure and rate.
    feed.setPressure(220.0, "bara");
    feed.setFlowRate(1.1 * originalFeed.getFlowRate("kg/sec"), "kg/sec");
    feed.run();
    well.run();
    assertOutletState(well, 3);
  }

  @Test
  void connectedHeatedPipeMatchesAnExplicitlyReflashedHandoff() {
    Stream feed = wetFeed();
    TwoFluidPipe well = pipe("upstream well", feed, 3100.0, 0.23, 2380.0, 0.0);
    well.run();

    SystemInterface refreshed = well.getOutletStream().getFluid().clone();
    new ThermodynamicOperations(refreshed).TPflash();
    refreshed.initProperties();
    Stream referenceFeed = new Stream("explicitly refreshed handoff", refreshed);
    TwoFluidPipe reference = pipe("reference flowline", referenceFeed, 6320.0, 0.35, 320.0, 2.0);
    reference.run();

    // Consume the actual connected outlet, without running or refreshing that stream.
    TwoFluidPipe connected = pipe("connected flowline", well.getOutletStream(), 6320.0, 0.35, 320.0, 2.0);
    connected.run();

    assertTrue(reference.isSteadyStateConverged());
    assertTrue(connected.isSteadyStateConverged());
    assertEquals(reference.getOutletStream().getTemperature("K"), connected.getOutletStream().getTemperature("K"),
        1.0e-5, "Downstream heat transfer must not depend on an extra caller-side flash");
    assertEquals(reference.getOutletStream().getPressure("bara"), connected.getOutletStream().getPressure("bara"),
        1.0e-5);
    assertTrue(connected.getOutletStream().getTemperature("C") < well.getOutletStream().getTemperature("C") - 1.0,
        "The fixture must resolve cooling rather than an effectively adiabatic pipe");
    assertOutletState(well, 3);
    assertOutletState(connected, 3);
    assertComponentClosure(feed.getFluid(), connected.getOutletStream().getFluid());
  }

  @ParameterizedTest
  @ValueSource(ints = { 1, 2, 3 })
  void gasAndLiquidPhaseCombinationsExposeInitializedProperties(int phases) {
    SystemInterface fluid = new SystemSrkEos(298.15, 70.0);
    fluid.addComponent("methane", 1.0);
    if (phases >= 2) {
      fluid.addComponent("n-decane", 1.0);
    }
    if (phases == 3) {
      fluid.addComponent("water", 1.0);
    }
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("phase combination", fluid);
    feed.setFlowRate(0.5, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = pipe("short pipe", feed, 100.0, 0.3, 0.0, 0.0);
    pipe.run();

    assertOutletState(pipe, phases);
  }

  @Test
  void propertyInitializationFailureDoesNotReplaceTheConnectedOutlet() {
    PropertyFailureFluid fluid = new PropertyFailureFluid();
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);
    Stream feed = new Stream("failure fixture", fluid);
    feed.setFlowRate(0.5, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = pipe("publication failure", feed, 100.0, 0.3, 0.0, 0.0);
    pipe.run();
    SystemInterface acceptedOutlet = pipe.getOutletStream().getFluid();
    ((PropertyFailureFluid) feed.getFluid()).failProperties = true;

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> pipe.run());

    assertTrue(failure.getMessage().contains("outlet thermodynamic initialization failed"));
    assertEquals("Injected property initialization failure", failure.getCause().getMessage());
    assertSame(acceptedOutlet, pipe.getOutletStream().getFluid());
  }

  /** EOS fixture that fails only at the complete property-initialization boundary. */
  private static final class PropertyFailureFluid extends SystemSrkEos {
    private static final long serialVersionUID = 1L;
    private boolean failProperties;

    private PropertyFailureFluid() {
      super(298.15, 70.0);
    }

    @Override
    public void initProperties() {
      if (failProperties) {
        throw new IllegalStateException("Injected property initialization failure");
      }
      super.initProperties();
    }
  }

  private static void assertOutletState(TwoFluidPipe pipe, int phases) {
    assertTrue(pipe.isSteadyStateConverged(), "The steady solve must converge before publication");
    SystemInterface actual = pipe.getOutletStream().getFluid();
    double cp = actual.getCp("J/kgK");
    logger.info("{}: outlet P={} bara, T={} C, Cp={} J/kgK, phases={}", pipe.getName(), actual.getPressure("bara"),
        actual.getTemperature("C"), cp, actual.getNumberOfPhases());
    assertTrue(Double.isFinite(cp) && cp > 100.0 && cp < 20000.0,
        "Outlet Cp must be physically meaningful without a caller-side flash: " + cp);
    assertEquals(phases, actual.getNumberOfPhases());

    SystemInterface reference = pipe.getInletStream().getFluid().clone();
    reference.setPressure(actual.getPressure(), "bara");
    reference.setTemperature(actual.getTemperature(), "K");
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    assertEquals(phases, reference.getNumberOfPhases());
    assertEquals(reference.getCp("J/kgK"), cp, Math.abs(cp) * 1.0e-7);
    assertEquals(reference.getEnthalpy("J/kg"), actual.getEnthalpy("J/kg"),
        Math.max(1.0, Math.abs(reference.getEnthalpy("J/kg"))) * 1.0e-7);
    assertEquals(reference.getFlowRate("kg/sec"), actual.getFlowRate("kg/sec"),
        reference.getFlowRate("kg/sec") * 1.0e-10);
    assertComponentClosure(reference, actual);

    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < reference.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface expectedPhase = reference.getPhase(phaseIndex);
      String name = expectedPhase.getPhaseTypeName();
      assertTrue(actual.hasPhaseType(name), "Missing outlet phase " + name);
      PhaseInterface actualPhase = actual.getPhase(name);
      assertEquals(expectedPhase.getBeta(), actualPhase.getBeta(), 1.0e-8);
      assertEquals(expectedPhase.getDensity("kg/m3"), actualPhase.getDensity("kg/m3"),
          expectedPhase.getDensity("kg/m3") * 1.0e-7);
      double viscosity = actualPhase.getViscosity("kg/msec");
      double conductivity = actualPhase.getThermalConductivity("W/mK");
      assertTrue(Double.isFinite(viscosity) && viscosity > 0.0);
      assertTrue(Double.isFinite(conductivity) && conductivity > 0.0);
      betaSum += actualPhase.getBeta();
    }
    assertEquals(1.0, betaSum, 1.0e-10);
    int last = pipe.getPressureProfile().length - 1;
    assertEquals(pipe.getPressureProfile()[last] / 1.0e5, actual.getPressure(), 1.0e-10);
    assertEquals(pipe.getTemperatureProfile("K")[last], actual.getTemperature(), 1.0e-10);
  }

  private static void assertComponentClosure(SystemInterface expected, SystemInterface actual) {
    for (int component = 0; component < expected.getNumberOfComponents(); component++) {
      String name = expected.getComponent(component).getComponentName();
      double moles = expected.getComponent(component).getNumberOfmoles();
      double tolerance = Math.max(1.0, Math.abs(moles)) * 1.0e-10;
      assertEquals(moles, actual.getComponent(name).getNumberOfmoles(), tolerance, name + " total moles");
      double phaseMoles = 0.0;
      for (int phase = 0; phase < actual.getNumberOfPhases(); phase++) {
        phaseMoles += actual.getPhase(phase).getComponent(name).getNumberOfMolesInPhase();
      }
      assertEquals(moles, phaseMoles, tolerance, name + " phase inventories");
    }
  }

  private static Stream wetFeed() {
    double oilMass = 500.0 * 859.5 / 86400.0;
    double gasMass = 120000.0 * 0.854 / 86400.0;
    double waterMass = 250.0 * 1033.0 / 86400.0;
    String[] components = { "methane", "ethane", "propane", "nitrogen", "CO2" };
    double[] fractions = { 0.86, 0.07, 0.035, 0.015, 0.02 };
    double[] molarMasses = { 0.016043, 0.030070, 0.044097, 0.0280134, 0.04401 };
    double gasMolarMass = 0.0;
    for (int i = 0; i < fractions.length; i++) {
      gasMolarMass += fractions[i] * molarMasses[i];
    }
    SystemInterface fluid = new SystemSrkEos(363.15, 230.0);
    for (int i = 0; i < components.length; i++) {
      fluid.addComponent(components[i], gasMass / gasMolarMass * fractions[i]);
    }
    fluid.addTBPfraction("stock_oil", oilMass / 0.200, 0.200, 0.8595);
    fluid.addComponent("water", waterMass / 0.01801528);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("wet reservoir feed", fluid);
    feed.setFlowRate(oilMass + gasMass + waterMass, "kg/sec");
    feed.run();
    return feed;
  }

  private static TwoFluidPipe pipe(String name, StreamInterface feed, double length, double diameter, double rise,
      double heatTransfer) {
    TwoFluidPipe pipe = new TwoFluidPipe(name, feed);
    pipe.setLength(length);
    pipe.setDiameter(diameter);
    pipe.setRoughness(4.5e-5);
    int sections = 20;
    pipe.setNumberOfSections(sections);
    double[] elevation = new double[sections];
    for (int i = 0; i < sections; i++) {
      elevation[i] = rise * i / (sections - 1);
    }
    pipe.setElevationProfile(elevation);
    pipe.setEnableWaterOilSlip(true);
    pipe.setSteadyStateMaxIterations(250);
    pipe.setSteadyStateMaxWallClockTime(90.0);
    if (heatTransfer > 0.0) {
      pipe.setSurfaceTemperature(4.0, "C");
      pipe.setHeatTransferCoefficient(heatTransfer);
    }
    return pipe;
  }
}
