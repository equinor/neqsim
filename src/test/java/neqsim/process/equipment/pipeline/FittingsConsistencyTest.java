package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for fitting catalogue consistency and pressure-loss lookup safety.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FittingsConsistencyTest {
  /** Verify the radius-qualified database names against their built-in equivalents. */
  @Test
  void databaseElbowsMatchBuiltInDefaults() {
    assertDatabaseMatchesStandard("Standard elbow (R=1.5D), 90deg", "elbow_90_long_radius", 16.0);
    assertDatabaseMatchesStandard("Standard elbow (R=1.5D), 45deg", "elbow_45_long_radius", 10.0);
    assertDatabaseMatchesStandard("Standard elbow (R=1D), 90deg", "elbow_90_standard", 30.0);
    assertDatabaseMatchesStandard("Standard elbow (R=1D), 45deg", "elbow_45_standard", 16.0);
  }

  /** Verify that the additional bore-specific database values retain their independent scope. */
  @Test
  void handbookValveValuesRemainAvailable() {
    Fittings fittings = new Fittings();
    fittings.add("Gate Valve, full bore, full open");
    fittings.add("Globe Valve, full bore, full open");
    fittings.add("Gate Valve, full bore, half open");
    assertEquals(748.0, fittings.getTotalLdRatio(), 1.0e-12);
    assertEquals(160.0, Fittings.getStandardFittingTypes().get("valve_gate_1_2_open"), 1.0e-12);
  }

  /** Verify that a missing database name cannot silently reduce the calculated loss. */
  @Test
  void unknownNameFailsWithoutAddingAFitting() {
    Pipeline pipe = new Pipeline("lookup");
    pipe.addFitting("existing", 12.0);
    assertThrows(IllegalArgumentException.class,
        () -> pipe.addFittingFromDatabase("Standard elbow (R=1.5D), 90deg TYPO"));
    assertEquals(1, pipe.getFittings().size());
    assertEquals(12.0, pipe.getTotalFittingsLdRatio(), 1.0e-12);
  }

  /** Verify that a name containing SQL punctuation is treated as a name. */
  @Test
  void quotedNameCannotSelectAnUnrelatedRow() {
    Fittings fittings = new Fittings();
    assertThrows(IllegalArgumentException.class, () -> fittings.add("' OR '1'='1"));
    assertTrue(fittings.isEmpty());
  }

  /** Verify that null and blank names cannot be interpreted as an assumed loss. */
  @Test
  void missingNameIsRejected() {
    Fittings fittings = new Fittings();
    assertThrows(IllegalArgumentException.class, () -> fittings.add(null));
    assertThrows(IllegalArgumentException.class, () -> fittings.add("  "));
    assertTrue(fittings.isEmpty());
  }

  /** Verify identifier lookup is independent of the host's language settings. */
  @Test
  void builtInIdentifiersUseLocaleIndependentCaseMatching() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      Fittings fittings = new Fittings();
      fittings.add("ELBOW_90_LONG_RADIUS");
      assertTrue(fittings.addStandard("ELBOW_90_LONG_RADIUS"));
      assertTrue(fittings.addStandardMultiple("ELBOW_90_LONG_RADIUS", 2));
      assertEquals(64.0, fittings.getTotalLdRatio(), 1.0e-12);
    } finally {
      Locale.setDefault(original);
    }
  }

  /** Verify the equivalent K uses an explicit Darcy factor and the current L/D. */
  @Test
  void equivalentKDependsOnDarcyFactorAndCurrentLd() {
    Fittings fittings = new Fittings();
    fittings.addStandard("elbow_90_long_radius");
    Fittings.Fitting elbow = fittings.getFittingsList().get(0);
    assertEquals(0.32, elbow.getKFactor(0.02), 1.0e-12);
    assertEquals(0.432, elbow.getKFactor(0.027), 1.0e-12);
    elbow.setLtoD(30.0);
    assertEquals(0.81, elbow.getKFactor(0.027), 1.0e-12);
    for (double invalid : new double[] {0.0, -0.02, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> elbow.getKFactor(invalid));
    }
  }

  /** Verify unset metadata cannot be mistaken for a calculated zero-loss coefficient. */
  @Test
  @SuppressWarnings("deprecation")
  void legacyKMetadataIsExplicitlyUnsetAndDoesNotControlLoss() {
    Fittings fittings = new Fittings();
    fittings.add("elbow", 16.0);
    Fittings.Fitting elbow = fittings.getFittingsList().get(0);
    assertTrue(Double.isNaN(elbow.getKFactor()));
    elbow.setKFactor(0.8);
    assertEquals(0.8, elbow.getKFactor(), 1.0e-12);
    assertEquals(0.32, elbow.getKFactor(0.02), 1.0e-12);
    assertEquals(3.2, elbow.getEquivalentLength(0.2), 1.0e-12);
  }

  /**
   * Verify fitting loss data and unset metadata survive serialization.
   *
   * @throws Exception if serialization or deserialization fails
   */
  @Test
  @SuppressWarnings("deprecation")
  void fittingCollectionCanBeSerialized() throws Exception {
    Fittings fittings = new Fittings();
    fittings.add("elbow", 16.0);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(fittings);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      Fittings restored = (Fittings) input.readObject();
      assertEquals(16.0, restored.getTotalLdRatio(), 1.0e-12);
      assertEquals(0.432, restored.getFittingsList().get(0).getKFactor(0.027), 1.0e-12);
      assertTrue(Double.isNaN(restored.getFittingsList().get(0).getKFactor()));
    }
  }

  /** Verify the incremental liquid loss against the Darcy-Weisbach equation. */
  @Test
  void liquidFittingLossMatchesDarcyWeisbach() {
    Stream feed = createFeed(true);
    IncompressiblePipeFlow straight = (IncompressiblePipeFlow) createPipe(0, feed, "straight");
    IncompressiblePipeFlow fitted = (IncompressiblePipeFlow) createPipe(0, feed, "fitted");
    fitted.addFittingFromDatabase("Standard elbow (R=1.5D), 90deg");
    straight.run();
    fitted.run();
    SystemInterface fluid = feed.getThermoSystem().clone();
    fluid.initProperties();
    double density = fluid.getPhase(0).getPhysicalProperties().getDensity();
    double velocity = fluid.getPhase(0).getFlowRate("m3/sec") / (Math.PI * 0.2 * 0.2 / 4.0);
    double reynolds = velocity * 0.2 / fluid.getPhase(0).getPhysicalProperties().getKinematicViscosity();
    double darcyFactor = fitted.calcWallFrictionFactor(reynolds);
    double expectedExtraLoss = darcyFactor * 16.0 * density * velocity * velocity / 2.0 / 1.0e5;
    double actualExtraLoss = straight.getOutletStream().getPressure("bara")
        - fitted.getOutletStream().getPressure("bara");
    assertTrue(expectedExtraLoss > 0.0);
    assertEquals(expectedExtraLoss, actualExtraLoss, 1.0e-10);
  }

  /** Verify equivalent pressure drops for liquid and gas through the existing pipe models. */
  @Test
  void databaseAndBuiltInElbowsGiveTheSamePressureDrop() {
    for (int model = 0; model < 3; model++) {
      Stream feed = createFeed(model == 0);
      Pipeline fromDatabase = createPipe(model, feed, "database");
      Pipeline builtIn = createPipe(model, feed, "built-in");
      Pipeline straight = createPipe(model, feed, "straight");
      fromDatabase.addFittingFromDatabase("Standard elbow (R=1.5D), 90deg");
      assertTrue(builtIn.addStandardFitting("elbow_90_long_radius"));
      fromDatabase.run();
      builtIn.run();
      straight.run();
      double outlet = fromDatabase.getOutletStream().getPressure("bara");
      assertEquals(builtIn.getOutletStream().getPressure("bara"), outlet, 1.0e-10);
      assertEquals(103.2, fromDatabase.getEffectiveLength(), 1.0e-12);
      assertTrue(Double.isFinite(outlet) && outlet > 0.0);
      assertTrue(outlet < straight.getOutletStream().getPressure("bara"));
      assertEquals(feed.getFlowRate("kg/hr"), fromDatabase.getOutletStream().getFlowRate("kg/hr"),
          feed.getFlowRate("kg/hr") * 1.0e-10);
      fromDatabase.setUseFittings(false);
      fromDatabase.run();
      assertEquals(straight.getOutletStream().getPressure("bara"), fromDatabase.getOutletStream().getPressure("bara"),
          1.0e-10);
    }
  }

  /**
   * Compare a database elbow with the corresponding built-in fitting.
   *
   * @param databaseName exact database name
   * @param standardType built-in identifier
   * @param expectedLd expected equivalent-length ratio
   */
  private void assertDatabaseMatchesStandard(String databaseName, String standardType, double expectedLd) {
    Fittings database = new Fittings();
    Fittings standard = new Fittings();
    database.add(databaseName);
    assertTrue(standard.addStandard(standardType));
    assertEquals(expectedLd, database.getTotalLdRatio(), 1.0e-12);
    assertEquals(standard.getTotalLdRatio(), database.getTotalLdRatio(), 1.0e-12);
  }

  /**
   * Create a single-phase synthetic stream for the pressure-loss regressions.
   *
   * @param liquid true for liquid water, false for methane gas
   * @return initialized feed stream
   */
  private Stream createFeed(boolean liquid) {
    SystemInterface fluid = new SystemSrkEos(298.15, liquid ? 10.0 : 50.0);
    fluid.addComponent(liquid ? "water" : "methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(liquid ? 100000.0 : 10000.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Create a pipe with common geometry for each supported pressure-loss model.
   *
   * @param model zero for incompressible, one for adiabatic gas, two for Beggs and Brill
   * @param feed initialized inlet
   * @param name equipment name
   * @return configured pipe
   */
  private Pipeline createPipe(int model, Stream feed, String name) {
    Pipeline pipe;
    if (model == 0) {
      pipe = new IncompressiblePipeFlow(name, feed);
    } else if (model == 1) {
      pipe = new AdiabaticPipe(name, feed);
    } else {
      pipe = new PipeBeggsAndBrills(name, feed);
    }
    pipe.setLength(100.0);
    pipe.setDiameter(0.2);
    pipe.setPipeWallRoughness(4.5e-5);
    return pipe;
  }
}
