package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for {@link CO2ImpurityKineticReactor}. */
public class CO2ImpurityKineticReactorTest extends NeqSimTest {
  private static final Logger logger = LogManager.getLogger(CO2ImpurityKineticReactorTest.class);
  private static final String[] REACTION_IDS = { "R1", "R2", "R3A", "R3B", "R4", "R5", "R6", "R7", "R8CS", "R8SS" };
  private static final String[] MODELED_SPECIES = { "H2S", "SO2", "NO2", "NO", "oxygen", "water", "H2SO4", "HNO3", "S8",
      "ammonia" };

  private Stream feedStream;
  private CO2ImpurityKineticReactor reactor;

  @BeforeEach
  void setUp() {
    feedStream = createReactingFeed();
    reactor = new CO2ImpurityKineticReactor("CO2 kinetic reactor", feedStream);
  }

  @Test
  void testReactorInitialization() {
    assertEquals("CO2 kinetic reactor", reactor.getName());
    assertEquals(200000.0, reactor.getReactorLength(), 1.0e-12);
    assertEquals(2.0, reactor.getFluidVelocity(), 1.0e-12);
    assertEquals(100000.0, reactor.getResidenceTime(), 1.0e-12);
    assertEquals("carbon_steel", reactor.getMaterial());
  }

  @Test
  void testReactingStreamChangesCompositionAndConservesElements() {
    disableAllReactions(reactor);
    reactor.setReactionConstants("R2", 1.0e4, 0.0);
    reactor.setResidenceTime(30.0);

    double[] inletElements = elementInventory(feedStream.getThermoSystem());
    double inletH2s = moles(feedStream.getThermoSystem(), "H2S");
    reactor.run();

    SystemInterface outlet = reactor.getOutletStream().getThermoSystem();
    assertNotNull(outlet);
    assertTrue(moles(outlet, "H2S") < inletH2s, "R2 must consume H2S");
    assertTrue(moles(outlet, "SO2") > moles(feedStream.getThermoSystem(), "SO2"), "R2 must produce SO2");
    assertTrue(moles(outlet, "NO") > moles(feedStream.getThermoSystem(), "NO"), "R2 must produce NO");

    assertElementConservation(inletElements, outlet);
    for (String species : MODELED_SPECIES) {
      assertTrue(moles(outlet, species) >= 0.0, species + " inventory must be non-negative");
    }
  }

  @Test
  void testR3bTreatsH2sAndNo2AsConservedCoCatalysts() {
    disableAllReactions(reactor);
    reactor.setReactionConstants("R3B", 1.0e4, 0.0);
    reactor.setResidenceTime(30.0);

    SystemInterface inlet = feedStream.getThermoSystem();
    double[] inletElements = elementInventory(inlet);
    double inletH2s = moles(inlet, "H2S");
    double inletNo2 = moles(inlet, "NO2");
    double inletSo2 = moles(inlet, "SO2");
    reactor.run();

    SystemInterface outlet = reactor.getOutletStream().getThermoSystem();
    assertTrue(moles(outlet, "SO2") < inletSo2, "R3B must consume SO2");
    assertEquals(inletH2s, moles(outlet, "H2S"), inletH2s * 1.0e-9);
    assertEquals(inletNo2, moles(outlet, "NO2"), inletNo2 * 1.0e-9);
    assertElementConservation(inletElements, outlet);
  }

  @Test
  void testLongerResidenceTimeIncreasesConversion() {
    CO2ImpurityKineticReactor shortReactor = createR8OnlyReactor("short reactor", "carbon_steel", 2.0);
    CO2ImpurityKineticReactor longReactor = createR8OnlyReactor("long reactor", "carbon_steel", 200.0);

    shortReactor.run();
    longReactor.run();

    double shortOutletH2s = moles(shortReactor.getOutletStream().getThermoSystem(), "H2S");
    double longOutletH2s = moles(longReactor.getOutletStream().getThermoSystem(), "H2S");
    assertTrue(longOutletH2s < shortOutletH2s, "Longer residence time must increase R8 conversion");
  }

  @Test
  void testCarbonSteelMaterialAcceleratesR8() {
    CO2ImpurityKineticReactor carbonSteel = createR8OnlyReactor("carbon-steel reactor", "carbon_steel", 60.0);
    CO2ImpurityKineticReactor stainlessSteel = createR8OnlyReactor("stainless-steel reactor", "stainless_steel", 60.0);

    carbonSteel.run();
    stainlessSteel.run();

    double carbonSteelH2s = moles(carbonSteel.getOutletStream().getThermoSystem(), "H2S");
    double stainlessSteelH2s = moles(stainlessSteel.getOutletStream().getThermoSystem(), "H2S");
    assertTrue(carbonSteelH2s < stainlessSteelH2s,
        "Carbon-steel R8 parameters must give more conversion than stainless-steel parameters");
  }

  @Test
  void testGeometryResidenceTimeUsesCalculatedDensity() {
    reactor.setReactorGeometry(6.5, 300.0, 50.0);
    double density = feedStream.getThermoSystem().getDensity("kg/m3");
    double expectedSeconds = 300.0e-6 * density * 1000.0 / 50.0 * 3600.0;

    double calculatedSeconds = reactor.calculateGeometryResidenceTime(298.15, 100.0);
    assertEquals(expectedSeconds, calculatedSeconds, expectedSeconds * 1.0e-8);
    assertTrue(reactor.generateReactorReport().contains("NeqSim fluid density"));
  }

  @Test
  void testLegacyGuideJavaExample() {
    SystemInterface fluid = new SystemSrkEos(248.15, 25.0);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("H2S", 10.0e-6);
    fluid.addComponent("SO2", 10.0e-6);
    fluid.addComponent("NO2", 10.0e-6);
    fluid.addComponent("oxygen", 10.0e-6);
    fluid.addComponent("water", 10.0e-6);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("CO2 feed", fluid);
    feed.run();

    CO2ImpurityKineticReactor guideReactor = new CO2ImpurityKineticReactor("CO2 impurity reactor", feed);
    guideReactor.setReactorGeometry(6.50, 300.0, 50.0);
    guideReactor.setReactionConstants("R3B", 2.13e8, 15.0);

    String report = guideReactor.generateReactorReport();
    logger.info("{}", report);
    assertTrue(report.contains("Reactor geometry"));
  }

  @Test
  void testPublicInputValidation() {
    assertThrows(IllegalArgumentException.class, () -> reactor.setMaterial("wood"));
    assertThrows(IllegalArgumentException.class, () -> reactor.setMaterial(null));
    assertThrows(IllegalArgumentException.class, () -> reactor.setReactionConstants("unknown", 1.0, 10.0));
    assertThrows(IllegalArgumentException.class, () -> reactor.setReactionConstants("R2", Double.NaN, 10.0));
    assertThrows(IllegalArgumentException.class, () -> reactor.setResidenceTime(-1.0));
    assertThrows(IllegalArgumentException.class, () -> reactor.setReactorGeometry(0.0, 300.0, 50.0));
  }

  private Stream createReactingFeed() {
    SystemSrkEos system = new SystemSrkEos(298.15, 100.0);
    system.addComponent("CO2", 1.0);
    system.addComponent("H2S", 1.0e-4);
    system.addComponent("SO2", 1.0e-5);
    system.addComponent("NO2", 5.0e-4);
    system.addComponent("NO", 1.0e-8);
    system.addComponent("oxygen", 5.0e-4);
    system.addComponent("water", 5.0e-4);
    system.addComponent("H2SO4", 1.0e-20);
    system.addComponent("HNO3", 1.0e-20);
    system.addComponent("S8", 1.0e-20);
    system.addComponent("ammonia", 1.0e-20);
    system.setMixingRule("classic");
    Stream stream = new Stream("reacting CO2 feed", system);
    stream.run();
    return stream;
  }

  private CO2ImpurityKineticReactor createR8OnlyReactor(String name, String material, double residenceTimeSeconds) {
    CO2ImpurityKineticReactor r8Reactor = new CO2ImpurityKineticReactor(name, createReactingFeed());
    disableAllReactions(r8Reactor);
    r8Reactor.setReactionConstants("R8CS", 1.0, 0.0);
    r8Reactor.setReactionConstants("R8SS", 0.01, 0.0);
    r8Reactor.setMaterial(material);
    r8Reactor.setResidenceTime(residenceTimeSeconds);
    return r8Reactor;
  }

  private void disableAllReactions(CO2ImpurityKineticReactor target) {
    for (String reactionId : REACTION_IDS) {
      target.setReactionConstants(reactionId, 0.0, 0.0);
    }
  }

  private double[] elementInventory(SystemInterface system) {
    double hydrogen = 2.0 * moles(system, "H2S") + 2.0 * moles(system, "water") + 2.0 * moles(system, "H2SO4")
        + moles(system, "HNO3") + 3.0 * moles(system, "ammonia");
    double nitrogen = moles(system, "NO2") + moles(system, "NO") + moles(system, "HNO3") + moles(system, "ammonia");
    double oxygen = 2.0 * moles(system, "SO2") + 2.0 * moles(system, "NO2") + moles(system, "NO")
        + 2.0 * moles(system, "oxygen") + moles(system, "water") + 4.0 * moles(system, "H2SO4")
        + 3.0 * moles(system, "HNO3");
    double sulfur = moles(system, "H2S") + moles(system, "SO2") + moles(system, "H2SO4") + 8.0 * moles(system, "S8");
    return new double[] { hydrogen, nitrogen, oxygen, sulfur };
  }

  private void assertElementConservation(double[] inletElements, SystemInterface outlet) {
    double[] outletElements = elementInventory(outlet);
    for (int i = 0; i < inletElements.length; i++) {
      assertEquals(inletElements[i], outletElements[i], Math.max(1.0e-12, inletElements[i] * 1.0e-9));
    }
  }

  private double moles(SystemInterface system, String component) {
    return system.hasComponent(component) ? system.getComponent(component).getNumberOfmoles() : 0.0;
  }
}
