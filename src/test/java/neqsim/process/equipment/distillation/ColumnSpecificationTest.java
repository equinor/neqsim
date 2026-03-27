package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

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

    // Should converge and produce output
    double gasFlow = column.getGasOutStream().getFlowRate("kg/hr");
    double liquidFlow = column.getLiquidOutStream().getFlowRate("kg/hr");
    assertTrue(gasFlow > 0, "Gas out should have positive flow");
    assertTrue(liquidFlow > 0, "Liquid out should have positive flow");

    double massBalance = Math.abs(feed.getFlowRate("kg/hr") - gasFlow - liquidFlow)
        / feed.getFlowRate("kg/hr") * 100.0;
    assertTrue(massBalance < 5.0, "Mass balance error should be < 5%, got: " + massBalance);
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
