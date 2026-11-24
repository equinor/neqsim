package neqsim.process.fielddevelopment.olga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import neqsim.process.equipment.reservoir.InflowPerformance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link OlgaWellGenkeyExporter}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class OlgaWellGenkeyExporterTest {
  /** Reservoir pressure used throughout, bara. */
  private static final double RESERVOIR_PRESSURE = 70.68;
  /** Bubble point pressure used throughout, bara. */
  private static final double BUBBLE_POINT = 66.94;
  /** Productivity index used throughout, Sm3/(day.bar). */
  private static final double PRODUCTIVITY_INDEX = 200.0;

  /** Default JVM locale, saved and restored around the locale-safety test. */
  private Locale defaultLocaleBeforeTest;

  @BeforeEach
  void saveDefaultLocale() {
    defaultLocaleBeforeTest = Locale.getDefault();
  }

  @AfterEach
  void restoreDefaultLocale() {
    Locale.setDefault(defaultLocaleBeforeTest);
  }

  private static InflowPerformance compositeIpr() {
    return InflowPerformance.composite(PRODUCTIVITY_INDEX, RESERVOIR_PRESSURE, BUBBLE_POINT);
  }

  /**
   * Formats a number exactly the way {@link OlgaWellGenkeyExporter} does internally, so tests verify the VALUE is
   * correct rather than encode a specific decimal-place count as the thing under test -- hardcoding a literal like
   * {@code "0.0000"} previously hid a real precision-loss bug (see
   * {@code testSetPipeRoughnessConvertsMillimetresToMetres}'s own comment) because the wrong, rounded-to-zero value
   * happened to match the literal.
   */
  private static String fmt(double value) {
    return String.format(Locale.ROOT, "%.8f", value);
  }

  /** Applies every REQUIRED setter except the one under test, matching {@link #fullyConfiguredExporter()} minus one. */
  private static OlgaWellGenkeyExporter minimalExporter() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    exporter.setTrajectory(new double[] {0.0, 0.0}, new double[] {0.0, -1000.0}, new double[] {0.15});
    return exporter;
  }

  /**
   * Builds a fully configured exporter (3 nodes, 2 segments) ready for {@link OlgaWellGenkeyExporter#toGenkeyText()}.
   */
  private static OlgaWellGenkeyExporter fullyConfiguredExporter() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    exporter.setTrajectory(new double[] {0.0, 0.0, 0.0}, new double[] {0.0, -1000.0, -2000.0},
        new double[] {0.1500, 0.1240});
    exporter.setInflowPerformance(compositeIpr());
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setWall(0.012, "m", "CARBON_STEEL", 50.0, 500.0, 7850.0);
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");
    return exporter;
  }

  @Test
  void testConstructorRejectsNullOrBlankLabel() {
    assertThrows(IllegalArgumentException.class, () -> new OlgaWellGenkeyExporter(null));
    assertThrows(IllegalArgumentException.class, () -> new OlgaWellGenkeyExporter("   "));
  }

  @Test
  void testGetWellLabelReturnsConstructorValue() {
    assertEquals("WELL-1", new OlgaWellGenkeyExporter("WELL-1").getWellLabel());
  }

  @Test
  void testSetTrajectoryRejectsNullArrays() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setTrajectory(null, new double[] {0.0}, new double[0]));
  }

  @Test
  void testSetTrajectoryRejectsMismatchedXAndYLength() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class,
        () -> exporter.setTrajectory(new double[] {0.0, 1.0}, new double[] {0.0}, new double[] {0.1}));
  }

  @Test
  void testSetTrajectoryRejectsFewerThanTwoNodes() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class,
        () -> exporter.setTrajectory(new double[] {0.0}, new double[] {0.0}, new double[0]));
  }

  @Test
  void testSetTrajectoryRejectsWrongDiameterArrayLength() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setTrajectory(new double[] {0.0, 1.0, 2.0},
        new double[] {0.0, -1.0, -2.0}, new double[] {0.1})); // needs 2 entries, not 1
  }

  @Test
  void testSetTrajectoryRejectsNonFiniteCoordinates() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setTrajectory(new double[] {0.0, Double.NaN},
        new double[] {0.0, -1.0}, new double[] {0.1}));
  }

  @Test
  void testSetTrajectoryRejectsNonPositiveDiameter() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class,
        () -> exporter.setTrajectory(new double[] {0.0, 1.0}, new double[] {0.0, -1.0}, new double[] {0.0}));
  }

  @Test
  void testSetInflowPerformanceRejectsNull() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setInflowPerformance(null));
  }

  @Test
  void testSetIprCurvePointsRejectsFewerThanTwo() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setIprCurvePoints(1));
  }

  @Test
  void testSetReservoirTemperatureConvertsKelvinToCelsius() {
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    exporter.setReservoirTemperature(363.15, "K");
    assertTrue(exporter.toGenkeyText().contains("TEMPERATURE=" + fmt(90.0) + " C"));
  }

  @Test
  void testSetReservoirTemperatureRejectsUnrecognisedUnit() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setReservoirTemperature(90.0, "F"));
  }

  @Test
  void testSetPipeRoughnessConvertsMillimetresToMetres() {
    // Regression test: 0.02 mm = 2.0e-5 m. An earlier version of this test asserted the WRONG value
    // ("PIPE ROUGHNESS=0.0000") and still passed, because a 4-decimal-place formatter rounded this
    // exact roughness straight to zero -- a real, silent precision-loss bug the rule-check caught,
    // not this test. Asserting the computed, correct value instead of a literal closes that gap.
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    exporter.setPipeRoughness(0.02, "mm");
    assertTrue(exporter.toGenkeyText().contains("PIPE ROUGHNESS=" + fmt(2.0e-5) + " m"));
  }

  @Test
  void testSetPipeRoughnessRejectsNonPositiveValue() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setPipeRoughness(0.0, "m"));
  }

  @Test
  void testSetWallConvertsMillimetresToMetres() {
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    exporter.setWall(12.0, "mm", "CARBON_STEEL", 50.0, 500.0, 7850.0);
    assertTrue(exporter.toGenkeyText().contains("THICKNESS=(" + fmt(0.012) + ") m"));
  }

  @Test
  void testSetWallRejectsNonPositiveThickness() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setWall(0.0, "m", "STEEL", 50.0, 500.0, 7850.0));
  }

  @Test
  void testSetWallRejectsUnrecognisedThicknessUnit() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setWall(0.012, "in", "STEEL", 50.0, 500.0, 7850.0));
  }

  @Test
  void testSetWallRejectsBlankMaterialLabel() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setWall(0.012, "m", " ", 50.0, 500.0, 7850.0));
  }

  @Test
  void testSetWallRejectsNonPositiveConductivity() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setWall(0.012, "m", "STEEL", 0.0, 500.0, 7850.0));
  }

  @Test
  void testSetWallRejectsNonPositiveHeatCapacity() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setWall(0.012, "m", "STEEL", 50.0, 0.0, 7850.0));
  }

  @Test
  void testSetWallRejectsNonPositiveDensity() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setWall(0.012, "m", "STEEL", 50.0, 500.0, 0.0));
  }

  @Test
  void testSetHeatTransferAcceptsKelvinAmbientAndEquivalentCoefficientUnit() {
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    exporter.setHeatTransfer(6.5, "W/m2-K", 279.15, "K"); // 279.15 K = 6.0 C
    String genkey = exporter.toGenkeyText();
    assertTrue(genkey.contains("TAMBIENT=" + fmt(6.0) + " C"));
    assertTrue(genkey.contains("HAMBIENT=" + fmt(6.5) + " W/m2-C"));
  }

  @Test
  void testSetHeatTransferRejectsNonPositiveCoefficient() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setHeatTransfer(0.0, "W/m2-C", 6.0, "C"));
  }

  @Test
  void testSetHeatTransferRejectsUnrecognisedCoefficientUnit() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setHeatTransfer(6.5, "BTU/hr-ft2-F", 6.0, "C"));
  }

  @Test
  void testSetHeatTransferRejectsUnrecognisedAmbientUnit() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "F"));
  }

  @Test
  void testToGenkeyTextRequiresTrajectory() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    exporter.setInflowPerformance(compositeIpr());
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setWall(0.012, "m", "STEEL", 50.0, 500.0, 7850.0);
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");
    assertThrows(IllegalStateException.class, exporter::toGenkeyText);
  }

  @Test
  void testToGenkeyTextRequiresInflowPerformance() {
    OlgaWellGenkeyExporter exporter = minimalExporter();
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setWall(0.012, "m", "STEEL", 50.0, 500.0, 7850.0);
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");
    assertThrows(IllegalStateException.class, exporter::toGenkeyText);
  }

  @Test
  void testToGenkeyTextRequiresReservoirTemperature() {
    OlgaWellGenkeyExporter exporter = minimalExporter();
    exporter.setInflowPerformance(compositeIpr());
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setWall(0.012, "m", "STEEL", 50.0, 500.0, 7850.0);
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");
    assertThrows(IllegalStateException.class, exporter::toGenkeyText);
  }

  @Test
  void testToGenkeyTextRequiresPipeRoughness() {
    OlgaWellGenkeyExporter exporter = minimalExporter();
    exporter.setInflowPerformance(compositeIpr());
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setWall(0.012, "m", "STEEL", 50.0, 500.0, 7850.0);
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");
    assertThrows(IllegalStateException.class, exporter::toGenkeyText);
  }

  @Test
  void testToGenkeyTextRequiresWall() {
    OlgaWellGenkeyExporter exporter = minimalExporter();
    exporter.setInflowPerformance(compositeIpr());
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");
    assertThrows(IllegalStateException.class, exporter::toGenkeyText);
  }

  @Test
  void testToGenkeyTextRequiresHeatTransfer() {
    OlgaWellGenkeyExporter exporter = minimalExporter();
    exporter.setInflowPerformance(compositeIpr());
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setWall(0.012, "m", "STEEL", 50.0, 500.0, 7850.0);
    assertThrows(IllegalStateException.class, exporter::toGenkeyText);
  }

  @Test
  void testToGenkeyTextRejectsCoincidentTrajectoryNodes() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    exporter.setTrajectory(new double[] {0.0, 0.0}, new double[] {0.0, 0.0}, new double[] {0.15});
    exporter.setInflowPerformance(compositeIpr());
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setWall(0.012, "m", "STEEL", 50.0, 500.0, 7850.0);
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");
    assertThrows(IllegalStateException.class, exporter::toGenkeyText);
  }

  @Test
  void testFlowpathTagAndGeometryBlock() {
    String genkey = fullyConfiguredExporter().toGenkeyText();
    assertTrue(genkey.contains("NETWORKCOMPONENT TYPE=FLOWPATH, TAG=FLOWPATH_WELL-1"));
    // GEOMETRY's own YSTART is node 0's own elevation (0.0 in fullyConfiguredExporter's trajectory)
    // -- already-known real trajectory data, not a guess.
    assertTrue(genkey.contains("GEOMETRY LABEL=\"GEOM_WELL-1\", YSTART=" + fmt(0.0) + " m"));
  }

  @Test
  void testFlowpathTagSanitisesUnsafeCharacters() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL 1/A");
    exporter.setTrajectory(new double[] {0.0, 0.0}, new double[] {0.0, -1000.0}, new double[] {0.15});
    exporter.setInflowPerformance(compositeIpr());
    exporter.setReservoirTemperature(90.0, "C");
    exporter.setPipeRoughness(2.0e-5, "m");
    exporter.setWall(0.012, "m", "STEEL", 50.0, 500.0, 7850.0);
    exporter.setHeatTransfer(6.5, "W/m2-C", 6.0, "C");
    String genkey = exporter.toGenkeyText();
    assertTrue(genkey.contains("TAG=FLOWPATH_WELL_1_A"));
    assertTrue(genkey.contains("LABEL=\"GEOM_WELL_1_A\""));
    assertTrue(genkey.contains("WALL_WELL_1_A"));
  }

  @Test
  void testTabularBlockStartsAtZeroDrawdownAndEndsAtAbsoluteOpenFlow() {
    InflowPerformance ipr = compositeIpr();
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    String genkey = exporter.toGenkeyText();
    assertTrue(genkey.contains("TABULAR LABEL=\"MY_IPR_TAB\", MODELOPTION=VOLUME, DELTAP=(" + fmt(0.0) + ","));
    assertTrue(genkey.contains("STDFLOWRATE=(" + fmt(0.0) + ","));
    assertTrue(genkey.contains(fmt(RESERVOIR_PRESSURE)));
    assertTrue(genkey.contains(fmt(ipr.absoluteOpenFlow())));
    assertTrue(genkey.contains("PHASE=LIQUID"));
  }

  @Test
  void testTabularPointCountMatchesConfiguredCurvePoints() {
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    exporter.setIprCurvePoints(5);
    String genkey = exporter.toGenkeyText();
    int deltapStart = genkey.indexOf("DELTAP=(") + "DELTAP=(".length();
    int deltapEnd = genkey.indexOf(')', deltapStart);
    String deltapList = genkey.substring(deltapStart, deltapEnd);
    assertEquals(5, deltapList.split(",").length);
  }

  @Test
  void testCustomIprLabelIsUsedConsistentlyAcrossTabularAndReservoirContact() {
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    exporter.setIprLabel("CUSTOM_IPR");
    String genkey = exporter.toGenkeyText();
    assertTrue(genkey.contains("TABULAR LABEL=\"CUSTOM_IPR\""));
    assertTrue(genkey.contains("PRODIPR=\"CUSTOM_IPR\""));
    assertTrue(genkey.contains("INJIPR=\"CUSTOM_IPR\""));
    assertFalse(genkey.contains("MY_IPR_TAB"));
  }

  @Test
  void testPipeBlocksMatchTrajectoryNodeByNode() {
    String genkey = fullyConfiguredExporter().toGenkeyText();
    assertTrue(genkey.contains("LABEL=\"PIPE-1\""));
    assertTrue(genkey.contains("LABEL=\"PIPE-2\""));
    assertTrue(genkey.contains("NSEGMENT=1"));
    assertTrue(genkey.contains("LSEGMENT=(" + fmt(1000.0) + ") m"));
    assertTrue(genkey.contains("XEND=" + fmt(0.0) + " m, YEND=" + fmt(-1000.0)));
    assertTrue(genkey.contains("XEND=" + fmt(0.0) + " m, YEND=" + fmt(-2000.0)));
    assertTrue(genkey.contains("DIAMETER=" + fmt(0.1500) + " m"));
    assertTrue(genkey.contains("DIAMETER=" + fmt(0.1240) + " m"));
    assertTrue(genkey.contains("WALL=\"WALL_WELL-1\""));
    // Regression check: pipe roughness (2.0e-5 m) must survive formatting non-zero -- the bug a
    // 4-decimal-place formatter previously introduced (see testSetPipeRoughnessConvertsMillimetresToMetres).
    assertTrue(genkey.contains("PIPE ROUGHNESS=" + fmt(2.0e-5) + " m"));
  }

  @Test
  void testMaterialAndWallAreCaseLevelKeywordsBeforeTheFlowpathReferencingConsistentLabels() {
    // Regression test for real bugs caught by actual OLGA rule-checks against this exact output:
    // "Unknown keyword type WALL for FLOWPATH" (WALL nested inside the flowpath instead of at case
    // level), then "MATERIAL with LABEL or NUMBER STEEL ... is not found" (a bare material name
    // with no matching MATERIAL declaration -- OLGA has no built-in material by that name).
    String genkey = fullyConfiguredExporter().toGenkeyText();
    int materialIndex = genkey.indexOf("MATERIAL LABEL=");
    int wallIndex = genkey.indexOf("WALL LABEL=");
    int flowpathIndex = genkey.indexOf("NETWORKCOMPONENT TYPE=FLOWPATH");
    assertTrue(materialIndex >= 0 && materialIndex < flowpathIndex,
        "MATERIAL must appear before NETWORKCOMPONENT TYPE=FLOWPATH, not inside it");
    assertTrue(wallIndex >= 0 && wallIndex < flowpathIndex,
        "WALL must appear before NETWORKCOMPONENT TYPE=FLOWPATH, not inside it");
    // Every PIPE's own WALL= must reference the SAME label the case-level WALL keyword declares,
    // and that WALL's own MATERIAL=(...) must reference the SAME label the MATERIAL keyword
    // declares -- not just independently-plausible-looking strings.
    assertTrue(genkey.contains("WALL LABEL=\"WALL_WELL-1\""));
    assertTrue(genkey.contains("MATERIAL=(\"CARBON_STEEL\")"));
    assertTrue(genkey.contains("MATERIAL LABEL=\"CARBON_STEEL\""));
  }

  @Test
  void testTabularAndGeometryAndHeatTransferAreRequiredKeywordsPresent() {
    // Regression test for real bugs caught by actual OLGA rule-checks against this exact output
    // ("Unknown keyword type TABULAR for FLOWPATH", then "Missing required keywords. GEOMETRY" /
    // "Missing required keywords. HEATTRANSFER"): TABULAR must precede
    // "NETWORKCOMPONENT TYPE=FLOWPATH" (it is a case-level keyword, not nested inside it), while
    // GEOMETRY and HEATTRANSFER must both be present inside the FLOWPATH body.
    String genkey = fullyConfiguredExporter().toGenkeyText();
    int tabularIndex = genkey.indexOf("TABULAR LABEL=");
    int flowpathIndex = genkey.indexOf("NETWORKCOMPONENT TYPE=FLOWPATH");
    assertTrue(tabularIndex >= 0 && flowpathIndex >= 0 && tabularIndex < flowpathIndex,
        "TABULAR must appear before NETWORKCOMPONENT TYPE=FLOWPATH, not inside it");
    assertTrue(genkey.indexOf("GEOMETRY LABEL=") > flowpathIndex);
    assertTrue(genkey.indexOf("HEATTRANSFER LABEL=") > flowpathIndex);
  }

  @Test
  void testPositionReferencesTheFirstPipeAndEndsWithEndNetworkComponent() {
    // "Bottomhole" is physically at node 0 (the reservoir-contact end) -- the start of PIPE-1,
    // not the last pipe. Regression test for a real bug: an earlier version of this class
    // referenced the LAST pipe instead, confirmed wrong by cross-checking against the equivalent,
    // already-validated statement in agentic_flow_assurance's well-inflow-pi skill.
    String genkey = fullyConfiguredExporter().toGenkeyText();
    assertTrue(genkey.contains("POSITION LABEL=Bottomhole, PIPE=\"PIPE-1\", SECTION=1"));
    assertTrue(genkey.trim().endsWith("ENDNETWORKCOMPONENT"));
  }

  @Test
  void testNoFluidLabelProducesTodoCommentNotAFabricatedReference() {
    String genkey = fullyConfiguredExporter().toGenkeyText();
    // Check for the real, active keyword line specifically (a leading space then BRANCH, no "!"
    // comment marker) -- a naive contains("BRANCH FLUID=") would also match inside the TODO
    // comment's own text below, which mentions the keyword without emitting it.
    assertFalse(genkey.contains("\n BRANCH FLUID="));
    assertTrue(genkey.contains("TODO: BRANCH FLUID="));
  }

  @Test
  void testFluidLabelWhenSuppliedIsUsedAndNoTodoRemains() {
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    exporter.setPvtFluidLabel("MY_FLUID");
    String genkey = exporter.toGenkeyText();
    assertTrue(genkey.contains("BRANCH FLUID=\"MY_FLUID\""));
    assertFalse(genkey.contains("TODO: BRANCH FLUID="));
  }

  @Test
  void testNoGorOrWgrByDefault() {
    String genkey = fullyConfiguredExporter().toGenkeyText();
    assertFalse(genkey.contains("GORST="));
    assertFalse(genkey.contains("WGR="));
  }

  @Test
  void testGorAndWgrAppearOnlyWhenExplicitlySet() {
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    exporter.setGasOilRatio(120.5);
    exporter.setWaterGasRatio(0.0025);
    String genkey = exporter.toGenkeyText();
    assertTrue(genkey.contains("GORST=" + fmt(120.5) + " Sm3/Sm3"));
    assertTrue(genkey.contains("WGR=" + fmt(0.0025) + " Sm3/Sm3"));
  }

  @Test
  void testGasOilRatioRejectsNegativeValue() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setGasOilRatio(-1.0));
  }

  @Test
  void testWaterGasRatioRejectsNegativeValue() {
    OlgaWellGenkeyExporter exporter = new OlgaWellGenkeyExporter("WELL-1");
    assertThrows(IllegalArgumentException.class, () -> exporter.setWaterGasRatio(-1.0));
  }

  /**
   * A comma-decimal default JVM locale must never corrupt the numeric output -- the whole class formats with
   * {@link Locale#ROOT} internally for exactly this reason.
   */
  @Test
  void testOutputIsLocaleIndependent() {
    Locale.setDefault(Locale.GERMANY);
    String genkey = fullyConfiguredExporter().toGenkeyText();
    // A comma-decimal leak would show up as "0,00000000" fragments; only period-decimal is valid here.
    assertFalse(genkey.contains("," + "00000000"));
    assertTrue(genkey.contains(fmt(0.0)));
  }

  @Test
  void testWriteToWritesTheSameTextToggleFile(@TempDir Path tempDir) throws IOException {
    OlgaWellGenkeyExporter exporter = fullyConfiguredExporter();
    Path destination = tempDir.resolve("well.genkey");
    exporter.writeTo(destination);
    String written = new String(Files.readAllBytes(destination), StandardCharsets.UTF_8);
    assertEquals(exporter.toGenkeyText(), written);
  }
}
