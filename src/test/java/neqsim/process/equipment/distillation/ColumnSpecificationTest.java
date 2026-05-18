package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.validation.ValidationResult;

/**
 * Tests for the {@link ColumnSpecification} class and the specification-driven column solving in
 * {@link DistillationColumn}.
 *
 * @author esol
 * @version 1.0
 */
@Tag("slow")
public class ColumnSpecificationTest {

  /**
   * Test that ColumnSpecification validates inputs correctly.
   */
  @Test
  public void testSpecificationValidation() {
    // Valid product purity spec
    ColumnSpecification spec =
        new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
            ColumnSpecification.ProductLocation.TOP, 0.95, "methane");
    assertEquals(ColumnSpecification.SpecificationType.PRODUCT_PURITY, spec.getType());
    assertEquals(ColumnSpecification.ProductLocation.TOP, spec.getLocation());
    assertEquals(0.95, spec.getTargetValue(), 1e-10);
    assertEquals("methane", spec.getComponentName());

    // Valid reflux ratio (no component needed)
    ColumnSpecification refluxSpec =
        new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
            ColumnSpecification.ProductLocation.TOP, 3.0);
    assertEquals(3.0, refluxSpec.getTargetValue(), 1e-10);

    // Purity spec without component name should throw
    assertThrows(IllegalArgumentException.class,
        () -> new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
            ColumnSpecification.ProductLocation.TOP, 0.95));

    // Component recovery without component name should throw
    assertThrows(IllegalArgumentException.class,
        () -> new ColumnSpecification(ColumnSpecification.SpecificationType.COMPONENT_RECOVERY,
            ColumnSpecification.ProductLocation.BOTTOM, 0.8));

    // Purity out of range should throw
    assertThrows(IllegalArgumentException.class,
        () -> new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
            ColumnSpecification.ProductLocation.TOP, 1.5, "methane"));

    // Negative reflux ratio should throw
    assertThrows(IllegalArgumentException.class,
        () -> new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
            ColumnSpecification.ProductLocation.TOP, -1.0));

    assertThrows(IllegalArgumentException.class,
        () -> new ColumnSpecification(ColumnSpecification.SpecificationType.DUTY,
            ColumnSpecification.ProductLocation.TOP, Double.NaN));
  }

  /**
   * Test the tolerance and maxIterations settings on ColumnSpecification.
   */
  @Test
  public void testSpecificationSettings() {
    ColumnSpecification spec =
        new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
            ColumnSpecification.ProductLocation.TOP, 2.0);
    assertEquals(1.0e-4, spec.getTolerance(), 1e-10);
    assertEquals(20, spec.getMaxIterations());

    spec.setTolerance(1.0e-3);
    assertEquals(1.0e-3, spec.getTolerance(), 1e-10);

    spec.setMaxIterations(30);
    assertEquals(30, spec.getMaxIterations());

    assertThrows(IllegalArgumentException.class, () -> spec.setTolerance(-1.0));
    assertThrows(IllegalArgumentException.class, () -> spec.setMaxIterations(0));
  }

  /**
   * Test specification homotopy configuration on a column.
   */
  @Test
  public void testSpecificationHomotopySettings() {
    DistillationColumn column = new DistillationColumn("HomotopySettingsColumn", 3, true, true);

    assertEquals(1, column.getSpecificationHomotopySteps());
    assertEquals(0, column.getLastSpecificationHomotopyStepCount());

    column.setSpecificationHomotopySteps(4);
    assertEquals(4, column.getSpecificationHomotopySteps());
    assertThrows(IllegalArgumentException.class, () -> column.setSpecificationHomotopySteps(0));
  }

  /**
   * Test that the toString method returns a readable representation.
   */
  @Test
  public void testToString() {
    ColumnSpecification spec =
        new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
            ColumnSpecification.ProductLocation.TOP, 0.9, "propane");
    String str = spec.toString();
    assertTrue(str.contains("PRODUCT_PURITY"));
    assertTrue(str.contains("TOP"));
    assertTrue(str.contains("propane"));
  }

  /**
   * Test that convenience methods set specs correctly on the column.
   */
  @Test
  public void testConvenienceSpecMethods() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 25.0, 15.0);
    testSystem.addComponent("methane", 0.5);
    testSystem.addComponent("ethane", 0.3);
    testSystem.addComponent("propane", 0.2);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("feed", testSystem);
    feed.setFlowRate(100.0, "kg/hr");

    DistillationColumn column = new DistillationColumn("TestCol", 5, true, true);
    column.addFeedStream(feed, 3);

    // Test top product purity
    column.setTopProductPurity("methane", 0.95);
    assertNotNull(column.getTopSpecification());
    assertEquals(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
        column.getTopSpecification().getType());
    assertEquals(0.95, column.getTopSpecification().getTargetValue(), 1e-10);

    // Test bottom product purity
    column.setBottomProductPurity("propane", 0.50);
    assertNotNull(column.getBottomSpecification());
    assertEquals(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
        column.getBottomSpecification().getType());
    assertEquals(0.50, column.getBottomSpecification().getTargetValue(), 1e-10);

    // Test reflux ratio convenience
    column.setCondenserRefluxRatio(3.5);
    assertEquals(ColumnSpecification.SpecificationType.REFLUX_RATIO,
        column.getTopSpecification().getType());

    // Test boilup ratio convenience
    column.setReboilerBoilupRatio(1.5);
    assertEquals(ColumnSpecification.SpecificationType.REFLUX_RATIO,
        column.getBottomSpecification().getType());

    // Test component recovery convenience
    column.setTopComponentRecovery("methane", 0.99);
    assertEquals(ColumnSpecification.SpecificationType.COMPONENT_RECOVERY,
        column.getTopSpecification().getType());

    // Test product flow rate convenience
    column.setBottomProductFlowRate(50.0, "mol/hr");
    assertEquals(ColumnSpecification.SpecificationType.PRODUCT_FLOW_RATE,
        column.getBottomSpecification().getType());
  }

  /**
   * Test that wrong location is rejected when setting specs.
   */
  @Test
  public void testLocationValidation() {
    DistillationColumn column = new DistillationColumn("TestCol", 3, true, true);

    // Setting a BOTTOM spec as top should throw
    ColumnSpecification bottomSpec =
        new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
            ColumnSpecification.ProductLocation.BOTTOM, 2.0);
    assertThrows(IllegalArgumentException.class, () -> column.setTopSpecification(bottomSpec));

    // Setting a TOP spec as bottom should throw
    ColumnSpecification topSpec =
        new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
            ColumnSpecification.ProductLocation.TOP, 2.0);
    assertThrows(IllegalArgumentException.class, () -> column.setBottomSpecification(topSpec));
  }

  /**
   * Test that validation reports component specifications that do not match the feed composition.
   */
  @Test
  public void validateSetupReportsMissingSpecificationComponent() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 25.0, 15.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("ethane", 0.3);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("validation feed", testSystem);
    feed.setFlowRate(100.0, "kg/hr");

    DistillationColumn column = new DistillationColumn("ValidationColumn", 5, true, true);
    column.addFeedStream(feed, 2);
    column.setTopPressure(15.0);
    column.setBottomPressure(15.5);
    column.setTopProductPurity("propane", 0.90);

    ValidationResult result = column.validateSpecifications();

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("propane"));
  }

  /**
   * Test that validation warns when an adjustable top specification has no condenser handle.
   */
  @Test
  public void validateSetupWarnsWhenTopSpecHasNoCondenser() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 25.0, 15.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("ethane", 0.3);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("validation feed 2", testSystem);
    feed.setFlowRate(100.0, "kg/hr");

    DistillationColumn column =
        new DistillationColumn("ValidationColumnNoCondenser", 5, true, false);
    column.addFeedStream(feed, 2);
    column.setTopPressure(15.0);
    column.setBottomPressure(15.5);
    column.setTopProductPurity("ethane", 0.20);

    ValidationResult result = column.validateSetup();

    assertTrue(result.isValid());
    assertTrue(result.hasWarnings());
    assertTrue(result.getReport().contains("condenser/reboiler handle"));
  }

  /**
   * Test that specification screening rejects product flow above total feed flow.
   */
  @Test
  public void validateSpecificationsRejectsProductFlowAboveFeed() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 25.0, 15.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("ethane", 0.3);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("flow validation feed", testSystem);
    feed.setFlowRate(100.0, "kg/hr");

    DistillationColumn column = new DistillationColumn("FlowValidationColumn", 5, true, true);
    column.addFeedStream(feed, 2);
    column.setBottomProductFlowRate(1.0e9, "mol/hr");

    ValidationResult result = column.validateSpecifications();

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("Product-flow target"));
  }

  /**
   * Test that paired top and bottom flow specifications cannot exceed total feed flow.
   */
  @Test
  public void feasibilityScreenRejectsProductFlowSumAboveFeed() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 25.0, 15.0);
    testSystem.addComponent("methane", 0.7);
    testSystem.addComponent("ethane", 0.3);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("paired flow validation feed", testSystem);
    feed.setFlowRate(100.0, "kg/hr");

    DistillationColumn column = new DistillationColumn("PairedFlowValidationColumn", 5, true, true);
    column.addFeedStream(feed, 2);
    column.setTopProductFlowRate(1.0e9, "mol/hr");
    column.setBottomProductFlowRate(1.0e9, "mol/hr");

    ValidationResult result = column.screenSpecificationFeasibility();

    assertFalse(result.isValid());
    assertTrue(result.getReport().contains("Top and bottom product-flow targets"));
  }

  /**
   * Test that AUTO can seed an ordinary fractionator from FUG estimates without replacing specs.
   */
  @Test
  public void autoShortcutSeedAppliesFugEstimatesWithoutChangingSpecs() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 45.0, 8.0);
    testSystem.addComponent("propane", 0.45);
    testSystem.addComponent("n-butane", 0.40);
    testSystem.addComponent("n-pentane", 0.15);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("shortcut seed feed", testSystem);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("ShortcutSeedColumn", 6, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(8.0);
    column.setBottomPressure(8.2);
    column.setTopComponentRecovery("propane", 0.90);
    column.setBottomComponentRecovery("n-butane", 0.90);

    StringBuilder summary = new StringBuilder();
    boolean applied = column.tryAutomaticShortcutInitialization(summary);

    assertTrue(applied);
    assertNotNull(column.getLastShortcutInitializationResult());
    assertTrue(column.getLastShortcutInitializationResult().isInitialized());
    assertTrue(summary.toString().contains("SHORTCUT_INITIALIZATION"));
    assertEquals(ColumnSpecification.SpecificationType.COMPONENT_RECOVERY,
        column.getTopSpecification().getType());
    assertEquals(0.90, column.getTopSpecification().getTargetValue(), 1.0e-12);
    assertEquals(ColumnSpecification.SpecificationType.COMPONENT_RECOVERY,
        column.getBottomSpecification().getType());
    assertEquals(0.90, column.getBottomSpecification().getTargetValue(), 1.0e-12);
  }

  /**
   * Test non-invasive Wilson K-value profile seeding for AUTO initialization.
   */
  @Test
  public void thermodynamicProfileSeedSetsTraySeedsAndReport() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 45.0, 8.0);
    testSystem.addComponent("propane", 0.45);
    testSystem.addComponent("n-butane", 0.40);
    testSystem.addComponent("n-pentane", 0.15);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("profile seed feed", testSystem);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("ProfileSeedColumn", 6, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(8.0);
    column.setBottomPressure(8.2);

    StringBuilder summary = new StringBuilder();
    boolean applied = column.tryThermodynamicProfileInitialization(summary);

    assertTrue(applied);
    assertTrue(column.hasSeedTemperatures());
    assertTrue(column.getLastInitializationReport().contains("Thermodynamic profile seed"));
    assertTrue(summary.toString().contains("THERMODYNAMIC_PROFILE"));
    assertTrue(Double.isFinite(column.getSeedTemperature(0)));
    assertTrue(Double.isFinite(column.getSeedTemperature(5)));
  }

  /**
   * Test reflux ratio specification — this should be directly set on the condenser/reboiler without
   * an outer loop.
   */
  @Test
  public void testRefluxRatioSpec() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 50.0, 10.0);
    testSystem.addComponent("propane", 0.5);
    testSystem.addComponent("n-butane", 0.5);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("feed", testSystem);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("TestCol", 5, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);

    // Use condenser reflux ratio spec and reboiler temperature
    column.setCondenserRefluxRatio(2.0);
    column.getCondenser().setOutTemperature(273.15 + 25.0);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.setMaxNumberOfIterations(50);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(1.0e-1);
    column.run();

    // Should produce output (may not fully converge with this simple case)
    double gasFlow = column.getGasOutStream().getFlowRate("kg/hr");
    double liquidFlow = column.getLiquidOutStream().getFlowRate("kg/hr");
    assertTrue(gasFlow > 0, "Gas out should have positive flow");
    assertTrue(liquidFlow > 0, "Liquid out should have positive flow");
  }

  /**
   * Test that automatic solver mode records which concrete solver completed the run.
   */
  @Test
  public void testAutoSolverRecordsSelectedSolver() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 45.0, 12.0);
    testSystem.addComponent("propane", 0.45);
    testSystem.addComponent("n-butane", 0.35);
    testSystem.addComponent("n-pentane", 0.20);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("auto feed", testSystem);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("AutoCol", 4, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(12.0);
    column.setBottomPressure(12.2);
    column.getCondenser().setOutTemperature(273.15 + 35.0);
    column.getReboiler().setOutTemperature(273.15 + 90.0);
    column.setCondenserRefluxRatio(1.5);
    column.setSolverType(DistillationColumn.SolverType.AUTO);
    column.setMaxNumberOfIterations(40);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(1.0e-1);

    column.run();

    assertEquals(DistillationColumn.SolverType.AUTO, column.getSolverType());
    assertNotNull(column.getLastSolverTypeUsed());
    assertNotEquals(DistillationColumn.SolverType.AUTO, column.getLastSolverTypeUsed());
    assertTrue(column.getGasOutStream().getFlowRate("kg/hr") >= 0.0);
    assertTrue(column.getLiquidOutStream().getFlowRate("kg/hr") >= 0.0);
    assertTrue(column.getLastAutoSolverSummary().contains("DAMPED_SUBSTITUTION"));
    assertTrue(column.getLastAutoSolverSummary().contains("FEASIBILITY_SCREEN"));
    assertFalse(column.getLastAutoSolverHistory().isEmpty());
    assertTrue(column.getLastAutoFeasibilityReport().contains("Validation Report"));
    assertTrue(column.getConvergenceDiagnostics().contains("Last solver used"));
    assertTrue(column.getConvergenceDiagnostics().contains("Automatic solver candidates"));
  }

  /**
   * Test that inside-out telemetry exposes rigorous and simplified model work.
   */
  @Test
  public void insideOutTelemetryRecordsModelWork() {
    DistillationColumn column = createBinaryFractionator("InsideOutTelemetry", "propane",
        "n-butane", "n-pentane", 10.0, 273.15 + 45.0, 273.15 + 30.0, 273.15 + 90.0);
    column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
    column.setInnerLoopSteps(2);
    column.run();

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertTrue(column.getLastInsideOutOuterFlashSweeps() > 0);
    assertTrue(column.getLastInsideOutInnerLoopIterations() >= 0);
    assertTrue(Double.isFinite(column.getLastInsideOutKValueResidual())
        || Double.isInfinite(column.getLastInsideOutKValueResidual()));
    assertTrue(Double.isNaN(column.getLastInsideOutSurrogateResidual())
      || Double.isFinite(column.getLastInsideOutSurrogateResidual()));
    assertTrue(column.getLastInsideOutSurrogateResetCount() >= 0);
    assertTrue(column.getConvergenceDiagnostics().contains("Inside-out model"));
  }

  /**
   * Test that Naphtali-Sandholm exposes semi-analytic Jacobian speed telemetry.
   */
  @Test
  public void naphtaliSandholmTelemetryRecordsJacobianWork() {
    DistillationColumn column = createBinaryFractionator("NaphtaliTelemetry", "propane", "n-butane",
        "n-pentane", 10.0, 273.15 + 45.0, 273.15 + 30.0, 273.15 + 90.0);
    column.setSolverType(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    column.setMaxNumberOfIterations(20);

    column.run();

    assertTrue(column.getGasOutStream().getFlowRate("kg/hr") >= 0.0);
    assertTrue(column.getLastNaphtaliAnalyticJacobianColumns() > 0);
    assertTrue(column.getLastNaphtaliFiniteDifferenceJacobianColumns() >= 0);
    assertTrue(column.getLastNaphtaliThermoEvaluationCount() > 0);
    assertTrue(column.getLastNaphtaliThermoCacheHitCount() >= 0);
    assertTrue(column.getLastNaphtaliJacobianBuildTimeSeconds() >= 0.0);
    assertTrue(column.getLastNaphtaliBlockLinearSolveCount() > 0);
    assertTrue(column.getLastNaphtaliDenseLinearSolveCount() >= 0);
    assertTrue(column.getLastNaphtaliLinearSolveTimeSeconds() >= 0.0);
    assertTrue(column.getConvergenceDiagnostics().contains("Naphtali-Sandholm Jacobian"));
    assertTrue(column.getConvergenceDiagnostics().contains("thermodynamic cache hits"));
    assertTrue(column.getConvergenceDiagnostics().contains("block linear solves"));
  }

  /**
   * Test active-bound and phase-stability feasibility diagnostics used by AUTO.
   */
  @Test
  public void feasibilityScreenReportsCommercialActiveBoundDiagnostics() {
    DistillationColumn column = createBinaryFractionator("ActiveBoundDiagnostics", "propane",
        "n-butane", "n-pentane", 10.0, 273.15 + 45.0, 273.15 + 30.0,
        273.15 + 90.0);
    column.setTopProductPurity("propane", 0.99999);
    column.setReboilerDutySpecification(0.0);
    column.addSideDrawFlowSpecification(2, DistillationColumn.SideDrawPhase.LIQUID, 1.0e9,
      "mol/hr");
    column.addLiquidPumparound("PA-1", 2, 4, 0.95, 10.0);

    ValidationResult result = column.screenSpecificationFeasibility();

    assertTrue(result.hasWarnings());
    assertTrue(result.getReport().contains("specification.activeBound"));
    assertTrue(result.getReport().contains("sidedraw.activeBound"));
    assertTrue(result.getReport().contains("pumparound.activeBound"));
  }

  /**
   * Test a small commercial-style AUTO regression bank across common hydrocarbon splits.
   */
  @Test
  public void autoSolverHandlesCommercialHydrocarbonRegressionBank() {
    assertCommercialAutoCase(createBinaryFractionator("BankDepropanizer", "propane", "n-butane",
        "n-pentane", 10.0, 273.15 + 45.0, 273.15 + 30.0, 273.15 + 90.0));
    assertCommercialAutoCase(createBinaryFractionator("BankDebutanizer", "n-butane", "n-pentane",
        "n-hexane", 6.0, 273.15 + 80.0, 273.15 + 45.0, 273.15 + 130.0));
    assertCommercialAutoCase(createLeanGasFractionator());
  }

  /**
   * Test that AUTO reports deferred candidate fallbacks instead of rerunning damped substitution
   * inside every rejected probe.
   */
  @Test
  public void autoSolverDefersCandidateFallbackWork() {
    DistillationColumn column = createLeanGasFractionator();

    column.run();

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertTrue(column.getLastAutoSolverSummary().contains("duplicate damped probe skipped"));
    assertTrue(column.getLastAutoSolverSummary().contains("damped fallback deferred")
        || column.getLastSolverTypeUsed() != DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
  }

  /**
   * Test the broader commercial regression bank with pre-solve screening and profile seeding.
   */
  @Test
  public void commercialRegressionBankScreensTwentyIndustrialCases() {
    CommercialCase[] cases = commercialCaseBank();
    assertEquals(20, cases.length);
    for (CommercialCase regressionCase : cases) {
      DistillationColumn column = createCommercialRegressionCase(regressionCase);
      ValidationResult result = column.screenSpecificationFeasibility();
      String report = result.getReport();
      assertNotNull(report, regressionCase.name);
      assertTrue(report.contains("Validation Report"), regressionCase.name);

      StringBuilder summary = new StringBuilder();
      boolean seeded = column.tryThermodynamicProfileInitialization(summary);
      assertTrue(seeded || column.getLastInitializationReport().contains("skipped"),
          regressionCase.name);
    }
  }

  /**
   * Test product purity specification using a deethanizer case. The top spec is methane + ethane
   * purity in the overhead, and the bottom spec is reboiler temperature (traditional).
   */
  @Test
  public void testProductPuritySpecDeethanizer() {
    SystemSrkEos gasToDeethanizer = new SystemSrkEos(216, 30.0);
    gasToDeethanizer.addComponent("methane", 0.50);
    gasToDeethanizer.addComponent("ethane", 0.20);
    gasToDeethanizer.addComponent("propane", 0.17);
    gasToDeethanizer.addComponent("n-butane", 0.08);
    gasToDeethanizer.addComponent("n-pentane", 0.05);
    gasToDeethanizer.setMixingRule("classic");

    Stream feed = new Stream("feed", gasToDeethanizer);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("Deethanizer", 7, true, false);
    column.addFeedStream(feed, 4);
    column.setTopPressure(30.0);
    column.setBottomPressure(31.0);
    column.getReboiler().setOutTemperature(273.15 + 100.0);
    column.setTemperatureTolerance(1.0e-2);
    column.setMassBalanceTolerance(1.0e-1);
    column.setEnthalpyBalanceTolerance(1.0e-1);
    column.setMaxNumberOfIterations(50);

    // Specify: ethane mole fraction in top product > 0.25
    column.setTopProductPurity("ethane", 0.25);
    column.getTopSpecification().setTolerance(0.05);
    column.getTopSpecification().setMaxIterations(15);

    column.run();

    // Verify the column produced output
    double gasFlow = column.getGasOutStream().getFlowRate("kg/hr");
    assertTrue(gasFlow > 0, "Gas out flow should be positive");

    // The ethane mole fraction in top product should be approximately at the target
    double ethaneInTop = column.getGasOutStream().getFluid().getComponent("ethane").getz();
    // Allow tolerance since we're solving iteratively
    assertTrue(ethaneInTop > 0.1,
        "Ethane mole fraction in top product should be significant, got: " + ethaneInTop);
  }

  /**
   * Test that a product specification dispatches the run through the specification adjustment loop.
   */
  @Test
  public void productSpecificationActivatesOuterSolve() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 50.0, 10.0);
    testSystem.addComponent("propane", 0.5);
    testSystem.addComponent("n-butane", 0.5);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("specDispatchFeed", testSystem);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("SpecDispatchColumn", 5, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.setTopProductPurity("propane", 0.8);
    column.getTopSpecification().setTolerance(1.0);
    column.getTopSpecification().setMaxIterations(3);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setMaxNumberOfIterations(30);

    assertFalse(column.getCondenser().isSetOutTemperature());

    column.run();

    assertTrue(column.getCondenser().isSetOutTemperature(),
        "Product specifications should activate the outer solve and set a condenser temperature");
    assertTrue(column.getLastIterationCount() > 0);
    assertTrue(Double.isFinite(column.getLastTopSpecificationResidual()));
    assertTrue(Math.abs(column.getLastTopSpecificationResidual()) <= column.getTopSpecification()
        .getTolerance());
  }

  /**
   * Test that staged specification homotopy reaches the final public target without mutating it.
   */
  @Test
  public void productSpecificationHomotopyUsesFinalTarget() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 50.0, 10.0);
    testSystem.addComponent("propane", 0.5);
    testSystem.addComponent("n-butane", 0.5);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("homotopySpecFeed", testSystem);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("HomotopySpecColumn", 5, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.setTopProductPurity("propane", 0.8);
    column.getTopSpecification().setTolerance(1.0);
    column.getTopSpecification().setMaxIterations(3);
    column.setSpecificationHomotopySteps(3);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setMaxNumberOfIterations(30);

    column.run();

    assertEquals(3, column.getLastSpecificationHomotopyStepCount());
    assertEquals(0.8, column.getTopSpecification().getTargetValue(), 1.0e-12);
    assertTrue(column.getCondenser().isSetOutTemperature());
    assertTrue(Double.isFinite(column.getLastTopSpecificationResidual()));
    assertTrue(Math.abs(column.getLastTopSpecificationResidual()) <= column.getTopSpecification()
        .getTolerance());
    assertTrue(column.getConvergenceDiagnostics().contains("Specification homotopy"));
  }

  /**
   * Test that automatic solver mode stages difficult product specifications by default.
   */
  @Test
  public void autoSolverUsesHomotopyForAdjustableProductSpecification() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 50.0, 10.0);
    testSystem.addComponent("propane", 0.5);
    testSystem.addComponent("n-butane", 0.5);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("autoHomotopySpecFeed", testSystem);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("AutoHomotopySpecColumn", 5, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.0);
    column.getReboiler().setOutTemperature(273.15 + 75.0);
    column.setTopProductPurity("propane", 0.8);
    column.getTopSpecification().setTolerance(1.0);
    column.getTopSpecification().setMaxIterations(3);
    column.setSolverType(DistillationColumn.SolverType.AUTO);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setMaxNumberOfIterations(30);

    column.run();

    assertEquals(DistillationColumn.SolverType.AUTO, column.getSolverType());
    assertNotEquals(DistillationColumn.SolverType.AUTO, column.getLastSolverTypeUsed());
    assertEquals(3, column.getSpecificationHomotopySteps());
    assertEquals(3, column.getLastSpecificationHomotopyStepCount());
    assertEquals(0.8, column.getTopSpecification().getTargetValue(), 1.0e-12);
    assertTrue(column.getCondenser().isSetOutTemperature());
    assertTrue(column.getLastAutoSolverSummary().contains("INSIDE_OUT"));
    assertTrue(column.getConvergenceDiagnostics().contains("Automatic solver candidates"));
    assertTrue(column.getConvergenceDiagnostics().contains("Specification homotopy"));
  }

  /**
   * Test that solved() rejects a column with mass residual above the configured tolerance.
   *
   * @throws Exception if reflective field access fails
   */
  @Test
  public void solvedRequiresMassBalanceTolerance() throws Exception {
    DistillationColumn column = new DistillationColumn("SolvedContractColumn", 1, true, true);
    column.setError(0.0);
    column.setMassBalanceTolerance(1.0e-3);

    Field massResidual = DistillationColumn.class.getDeclaredField("lastMassResidual");
    massResidual.setAccessible(true);
    massResidual.setDouble(column, 1.0e-2);

    assertFalse(column.solved(), "A low temperature residual must not hide mass imbalance");
  }

  /** Commercial regression case descriptor used by screening-bank tests. */
  private static final class CommercialCase {
  /** Case name. */
  private final String name;
  /** Component names. */
  private final String[] components;
  /** Component molar amounts. */
  private final double[] amounts;
  /** Feed temperature in Kelvin. */
  private final double feedTemperature;
  /** Column pressure in bara. */
  private final double pressure;
  /** Number of trays. */
  private final int trays;
  /** Whether the column has a condenser. */
  private final boolean condenser;
  /** Whether the column has a reboiler. */
  private final boolean reboiler;
  /** Add a side-draw specification. */
  private final boolean sideDraw;
  /** Add a pumparound circuit. */
  private final boolean pumparound;
  /** Add deliberately poor temperature seeds. */
  private final boolean badSeeds;

  /**
   * Create a regression case descriptor.
   *
   * @param name case name
   * @param components component names
   * @param amounts component molar amounts
   * @param feedTemperature feed temperature in Kelvin
   * @param pressure pressure in bara
   * @param trays tray count
   * @param condenser whether the column has a condenser
   * @param reboiler whether the column has a reboiler
   * @param sideDraw whether to add a side-draw spec
   * @param pumparound whether to add a pumparound circuit
   * @param badSeeds whether to add deliberately poor temperature seeds
   */
  private CommercialCase(String name, String[] components, double[] amounts,
    double feedTemperature, double pressure, int trays, boolean condenser, boolean reboiler,
    boolean sideDraw, boolean pumparound, boolean badSeeds) {
    this.name = name;
    this.components = components;
    this.amounts = amounts;
    this.feedTemperature = feedTemperature;
    this.pressure = pressure;
    this.trays = trays;
    this.condenser = condenser;
    this.reboiler = reboiler;
    this.sideDraw = sideDraw;
    this.pumparound = pumparound;
    this.badSeeds = badSeeds;
  }
  }

  /**
   * Build the commercial regression-screening case bank.
   *
   * @return array with twenty industrial-style case descriptors
   */
  private CommercialCase[] commercialCaseBank() {
  return new CommercialCase[] {
    commercialCase("total condenser C3-C5", new String[] {"propane", "n-butane", "n-pentane"},
      new double[] {0.35, 0.45, 0.20}, 318.15, 10.0, 6, true, true, false, false,
      false),
    commercialCase("partial condenser C1-C4",
      new String[] {"methane", "ethane", "propane", "n-butane"},
      new double[] {0.30, 0.25, 0.25, 0.20}, 250.0, 28.0, 8, true, true, false,
      false, false),
    commercialCase("absorber no condenser reboiler",
      new String[] {"methane", "ethane", "propane"}, new double[] {0.70, 0.20, 0.10},
      298.15, 50.0, 5, false, false, false, false, false),
    commercialCase("stripper no condenser", new String[] {"propane", "n-butane", "n-pentane"},
      new double[] {0.20, 0.50, 0.30}, 350.0, 8.0, 6, false, true, false, false,
      false),
    commercialCase("narrow butane pentane", new String[] {"i-butane", "n-butane", "n-pentane"},
      new double[] {0.25, 0.45, 0.30}, 330.0, 6.0, 8, true, true, false, false,
      false),
    commercialCase("wide boiling C1-C7",
      new String[] {"methane", "propane", "n-hexane", "n-heptane"},
      new double[] {0.40, 0.30, 0.20, 0.10}, 310.0, 35.0, 10, true, true, false,
      false, false),
    commercialCase("sour gas trace H2S",
      new String[] {"methane", "CO2", "H2S", "ethane", "propane"},
      new double[] {0.70, 0.08, 0.02, 0.15, 0.05}, 285.0, 45.0, 8, true, true,
      false, false, false),
    commercialCase("CO2 rich demethanizer", new String[] {"methane", "CO2", "ethane"},
      new double[] {0.45, 0.35, 0.20}, 240.0, 55.0, 8, true, true, false, false,
      false),
    commercialCase("water rich hydrocarbon", new String[] {"methane", "CO2", "water"},
      new double[] {0.60, 0.10, 0.30}, 310.0, 20.0, 6, true, true, false, false,
      false),
    commercialCase("bad initial guesses", new String[] {"propane", "n-butane", "n-pentane"},
      new double[] {0.40, 0.40, 0.20}, 318.15, 9.0, 6, true, true, false, false,
      true),
    commercialCase("side draw fractionator", new String[] {"propane", "n-butane", "n-pentane"},
      new double[] {0.25, 0.50, 0.25}, 320.0, 9.0, 7, true, true, true, false,
      false),
    commercialCase("pumparound fractionator", new String[] {"n-butane", "n-pentane", "n-hexane"},
      new double[] {0.30, 0.45, 0.25}, 360.0, 5.0, 8, true, true, false, true,
      false),
    commercialCase("low reflux startup", new String[] {"ethane", "propane", "n-butane"},
      new double[] {0.30, 0.45, 0.25}, 300.0, 18.0, 6, true, true, false, false,
      false),
    commercialCase("high pressure demethanizer", new String[] {"methane", "ethane", "propane"},
      new double[] {0.65, 0.25, 0.10}, 220.0, 70.0, 8, true, true, false, false,
      false),
    commercialCase("vacuum debutanizer", new String[] {"n-butane", "n-pentane", "n-hexane"},
      new double[] {0.35, 0.45, 0.20}, 340.0, 1.5, 8, true, true, false, false,
      false),
    commercialCase("near critical rich gas", new String[] {"methane", "ethane", "propane"},
      new double[] {0.40, 0.35, 0.25}, 305.0, 45.0, 8, true, true, false, false,
      false),
    commercialCase("nitrogen rich gas", new String[] {"nitrogen", "methane", "ethane"},
      new double[] {0.20, 0.65, 0.15}, 230.0, 40.0, 6, true, true, false, false,
      false),
    commercialCase("heavy NGL splitter", new String[] {"n-pentane", "n-hexane", "n-heptane"},
      new double[] {0.35, 0.40, 0.25}, 380.0, 4.0, 8, true, true, false, false,
      false),
    commercialCase("wet gas stabilizer", new String[] {"methane", "ethane", "water", "n-butane"},
      new double[] {0.55, 0.25, 0.05, 0.15}, 300.0, 30.0, 7, true, true, false,
      false, false),
    commercialCase("lean methane absorber", new String[] {"methane", "ethane", "n-butane"},
      new double[] {0.82, 0.12, 0.06}, 295.0, 60.0, 5, false, false, false, false,
      false)};
  }

  /**
   * Create a commercial case descriptor.
   *
   * @param name case name
   * @param components component names
   * @param amounts component molar amounts
   * @param feedTemperature feed temperature in Kelvin
   * @param pressure pressure in bara
   * @param trays tray count
   * @param condenser whether the column has a condenser
   * @param reboiler whether the column has a reboiler
   * @param sideDraw whether to add a side-draw spec
   * @param pumparound whether to add a pumparound circuit
   * @param badSeeds whether to add deliberately poor temperature seeds
   * @return configured case descriptor
   */
  private CommercialCase commercialCase(String name, String[] components, double[] amounts,
    double feedTemperature, double pressure, int trays, boolean condenser, boolean reboiler,
    boolean sideDraw, boolean pumparound, boolean badSeeds) {
  return new CommercialCase(name, components, amounts, feedTemperature, pressure, trays,
    condenser, reboiler, sideDraw, pumparound, badSeeds);
  }

  /**
   * Create a small three-component fractionator case for regression testing.
   *
   * @param columnName column name
   * @param lightComponent light component name
   * @param middleComponent middle component name
   * @param heavyComponent heavy component name
   * @param pressure pressure in bara
   * @param feedTemperature feed temperature in Kelvin
   * @param condenserTemperature condenser temperature in Kelvin
   * @param reboilerTemperature reboiler temperature in Kelvin
   * @return configured AUTO column
   */
  private DistillationColumn createBinaryFractionator(String columnName, String lightComponent,
      String middleComponent, String heavyComponent, double pressure, double feedTemperature,
      double condenserTemperature, double reboilerTemperature) {
    SystemSrkEos system = new SystemSrkEos(feedTemperature, pressure);
    system.addComponent(lightComponent, 0.35);
    system.addComponent(middleComponent, 0.45);
    system.addComponent(heavyComponent, 0.20);
    system.setMixingRule("classic");

    Stream feed = new Stream(columnName + " feed", system);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn(columnName, 6, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(pressure);
    column.setBottomPressure(pressure + 0.2);
    column.getCondenser().setOutTemperature(condenserTemperature);
    column.getReboiler().setOutTemperature(reboilerTemperature);
    column.setCondenserRefluxRatio(1.8);
    column.setSolverType(DistillationColumn.SolverType.AUTO);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    return column;
  }

  /**
   * Create a lean-gas demethanizer-style regression case.
   *
   * @return configured AUTO column
   */
  private DistillationColumn createLeanGasFractionator() {
    SystemSrkEos system = new SystemSrkEos(216.0, 30.0);
    system.addComponent("methane", 0.55);
    system.addComponent("ethane", 0.20);
    system.addComponent("propane", 0.15);
    system.addComponent("n-butane", 0.07);
    system.addComponent("n-pentane", 0.03);
    system.setMixingRule("classic");

    Stream feed = new Stream("lean gas bank feed", system);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("BankLeanGasDeethanizer", 7, true, false);
    column.addFeedStream(feed, 4);
    column.setTopPressure(30.0);
    column.setBottomPressure(31.0);
    column.getReboiler().setOutTemperature(273.15 + 100.0);
    column.setSolverType(DistillationColumn.SolverType.AUTO);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    return column;
  }

  /**
   * Create a configured column from a commercial regression descriptor.
   *
   * @param regressionCase regression descriptor
   * @return configured column ready for screening or solving
   */
  private DistillationColumn createCommercialRegressionCase(CommercialCase regressionCase) {
    SystemSrkEos system = new SystemSrkEos(regressionCase.feedTemperature, regressionCase.pressure);
    for (int componentIndex = 0; componentIndex < regressionCase.components.length;
        componentIndex++) {
      system.addComponent(regressionCase.components[componentIndex],
          regressionCase.amounts[componentIndex]);
    }
    system.setMixingRule("classic");

    Stream feed = new Stream(regressionCase.name + " feed", system);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn(regressionCase.name, regressionCase.trays,
      regressionCase.reboiler, regressionCase.condenser);
    column.addFeedStream(feed, Math.max(0, regressionCase.trays / 2));
    column.setTopPressure(regressionCase.pressure);
    column.setBottomPressure(regressionCase.pressure + Math.max(0.1, 0.02 * regressionCase.pressure));
    if (regressionCase.condenser) {
      column.getCondenser().setOutTemperature(
          Math.max(80.0, regressionCase.feedTemperature - 25.0));
      column.setCondenserRefluxRatio(regressionCase.name.contains("low reflux") ? 0.05 : 1.5);
    }
    if (regressionCase.reboiler) {
      column.getReboiler().setOutTemperature(regressionCase.feedTemperature + 45.0);
    }
    if (regressionCase.sideDraw) {
      column.addSideDrawFlowSpecification(Math.max(1, regressionCase.trays / 2),
          DistillationColumn.SideDrawPhase.LIQUID, 25.0, "mol/hr");
    }
    if (regressionCase.pumparound) {
      column.addLiquidPumparound(regressionCase.name + " PA", Math.max(1, regressionCase.trays / 3),
          Math.min(regressionCase.trays - 1, regressionCase.trays * 2 / 3), 0.15, 10.0);
    }
    if (regressionCase.badSeeds) {
      column.setSeedTemperature(0, regressionCase.feedTemperature + 100.0);
      column.setSeedTemperature(regressionCase.trays - 1, regressionCase.feedTemperature - 100.0);
    }
    column.setSolverType(DistillationColumn.SolverType.AUTO);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    return column;
  }

  /**
   * Run and assert a commercial AUTO regression case.
   *
   * @param column column to run
   */
  private void assertCommercialAutoCase(DistillationColumn column) {
    column.run();

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertTrue(column.getGasOutStream().getFlowRate("kg/hr") >= 0.0);
    assertTrue(column.getLiquidOutStream().getFlowRate("kg/hr") >= 0.0);
    assertFalse(column.getLastAutoSolverHistory().isEmpty());
    assertTrue(column.getConvergenceDiagnostics().contains("Automatic solver"));
  }

  /**
   * Test the Builder pattern with column specifications.
   */
  @Test
  public void testBuilderWithSpecs() {
    SystemSrkEos testSystem = new SystemSrkEos(273.15 + 25.0, 15.0);
    testSystem.addComponent("methane", 0.5);
    testSystem.addComponent("ethane", 0.3);
    testSystem.addComponent("propane", 0.2);
    testSystem.setMixingRule("classic");

    Stream feed = new Stream("feed", testSystem);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = DistillationColumn.builder("TestCol").numberOfTrays(5)
        .withCondenserAndReboiler().topPressure(15.0, "bara").bottomPressure(15.0, "bara")
        .topProductPurity("methane", 0.80)
        .bottomSpecification(
            new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
                ColumnSpecification.ProductLocation.BOTTOM, 0.5))
        .addFeedStream(feed, 3).build();

    assertNotNull(column.getTopSpecification());
    assertEquals(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
        column.getTopSpecification().getType());
    assertNotNull(column.getBottomSpecification());
    assertEquals(ColumnSpecification.SpecificationType.REFLUX_RATIO,
        column.getBottomSpecification().getType());
  }
}
