package neqsim.process.equipment.distillation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link RateBasedPackedColumn}.
 */
public class RateBasedPackedColumnTest {

  @Test
  public void testAbsorbsCarbonDioxideFromGas() {
    Stream gas = createGasStream("gas in", 0.10);
    Stream liquid = createLiquidStream("lean liquid", 0.0);
    double inletGasCo2 = moleFraction(gas.getThermoSystem(), "CO2");

    RateBasedPackedColumn column = configuredColumn(gas, liquid, 6.0);
    column.run();

    double outletGasCo2 = moleFraction(column.getGasOutStream().getThermoSystem(), "CO2");
    Assertions.assertTrue(outletGasCo2 < inletGasCo2, "CO2 mole fraction should decrease in gas outlet");
    Assertions.assertTrue(column.getComponentTransferTotals().get("CO2") > 0.0,
        "Positive CO2 transfer means gas-to-liquid absorption");
    Assertions.assertEquals(4, column.getSegmentResults().size());
    Assertions.assertTrue(column.getSegmentResults().get(0).getGasDiffusivity() > 0.0);
    assertComponentBalance(gas.getThermoSystem(), liquid.getThermoSystem(), column.getGasOutStream().getThermoSystem(),
        column.getLiquidOutStream().getThermoSystem(), "CO2", 1.0e-5);
  }

  @Test
  public void testStripsCarbonDioxideFromLiquid() {
    Stream gas = createGasStream("lean gas", 0.0);
    Stream liquid = createLiquidStream("rich liquid", 0.04);
    double inletGasCo2 = moleFraction(gas.getThermoSystem(), "CO2");

    RateBasedPackedColumn column = configuredColumn(gas, liquid, 6.0);
    column.run();

    double outletGasCo2 = moleFraction(column.getGasOutStream().getThermoSystem(), "CO2");
    Assertions.assertTrue(outletGasCo2 > inletGasCo2,
        "CO2 mole fraction should increase in gas outlet during stripping");
    Assertions.assertTrue(column.getComponentTransferTotals().get("CO2") < 0.0,
        "Negative CO2 transfer means liquid-to-gas stripping");
    assertComponentBalance(gas.getThermoSystem(), liquid.getThermoSystem(), column.getGasOutStream().getThermoSystem(),
        column.getLiquidOutStream().getThermoSystem(), "CO2", 1.0e-5);
  }

  @Test
  public void testUsesMaxwellStefanInterfaceAndHeatTransferByDefault() {
    Stream gas = createGasStream("warm gas with CO2", 0.10);
    Stream liquid = createLiquidStream("cool lean liquid", 0.0);

    RateBasedPackedColumn column = configuredColumn(gas, liquid, 6.0);
    column.run();

    RateBasedPackedColumn.SegmentResult firstSegment = column.getSegmentResults().get(0);
    Assertions.assertEquals(RateBasedPackedColumn.FilmModel.MAXWELL_STEFAN_MATRIX, column.getFilmModel());
    Assertions.assertEquals(RateBasedPackedColumn.HeatTransferModel.CHILTON_COLBURN_ANALOGY,
        column.getHeatTransferModel());
    Assertions.assertTrue(firstSegment.getOverallHeatTransferCoefficient() > 0.0,
        "Chilton-Colburn heat-transfer coefficient should be active");
    Assertions.assertTrue(firstSegment.getHeatTransferRateW() > 0.0, "Warm gas should transfer heat to cooler liquid");
    Assertions.assertTrue(firstSegment.getInterfaceTemperatureK() > liquid.getThermoSystem().getTemperature());
    Assertions.assertTrue(firstSegment.getInterfaceTemperatureK() < gas.getThermoSystem().getTemperature());
    Assertions.assertTrue(firstSegment.getInterfaceGasMoleFractions().containsKey("CO2"));
    Assertions.assertTrue(firstSegment.getInterfaceLiquidMoleFractions().containsKey("CO2"));
    Assertions.assertTrue(firstSegment.getInterfaceEquilibriumRatios().get("CO2") > 0.0);
    Assertions.assertTrue(column.toJson().contains("MAXWELL_STEFAN_MATRIX"));
    Assertions.assertTrue(column.toJson().contains("heatTransferRateW"));
  }

  @Disabled("Tracked in #4094: simultaneous segment solver enthalpy conservation remains unqualified")
  @Test
  @Tag("failing")
  public void testSimultaneousResidualSolverConservesEnthalpy() {
    Stream gas = createGasStream("simultaneous warm gas", 0.10);
    Stream liquid = createLiquidStream("simultaneous cool liquid", 0.0);
    double inletTotalEnthalpy = gas.getThermoSystem().getEnthalpy() + liquid.getThermoSystem().getEnthalpy();

    RateBasedPackedColumn column = configuredColumn(gas, liquid, 6.0);
    column.setSegmentSolver(RateBasedPackedColumn.SegmentSolver.SIMULTANEOUS_RESIDUAL);
    column.setSegmentResidualTolerance(1.0e-5);
    column.run();

    RateBasedPackedColumn.SegmentResult firstSegment = column.getSegmentResults().get(0);
    double outletTotalEnthalpy = column.getGasOutStream().getThermoSystem().getEnthalpy()
        + column.getLiquidOutStream().getThermoSystem().getEnthalpy();
    double relativeEnthalpyError = Math.abs(outletTotalEnthalpy - inletTotalEnthalpy)
        / Math.max(Math.abs(inletTotalEnthalpy), 1.0);

    Assertions.assertEquals(RateBasedPackedColumn.SegmentSolver.SIMULTANEOUS_RESIDUAL.name(),
        firstSegment.getSegmentSolver());
    Assertions.assertTrue(firstSegment.getResidualIterations() >= 0);
    Assertions.assertTrue(Double.isFinite(firstSegment.getMaxFluxResidualMolPerSec()));
    Assertions.assertTrue(Double.isFinite(firstSegment.getHeatBalanceResidualW()));
    Assertions.assertTrue(relativeEnthalpyError < 1.0e-5,
        "Simultaneous residual solver should conserve total gas/liquid enthalpy");
    Assertions.assertTrue(column.toJson().contains("heatBalanceResidualW"));
  }

  /**
   * Reject an exhausted profile and preserve the previously accepted outlets and profile.
   */
  @Test
  public void testFixedPointRejectsExhaustedRerun() {
    RateBasedPackedColumn column = configuredColumn(createGasStream("gas", 0.10), createLiquidStream("liquid", 0.0),
        6.0);
    column.run();
    Assertions.assertTrue(column.solved());
    Assertions.assertTrue(column.getLastConvergenceResidual() <= column.getConvergenceTolerance());
    SystemInterface previousGas = column.getGasOutStream().getThermoSystem();
    SystemInterface previousLiquid = column.getLiquidOutStream().getThermoSystem();
    double previousTransfer = column.getTotalAbsoluteMolarTransfer();
    column.setMaxIterations(2);

    IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, column::run);

    Assertions.assertTrue(failure.getMessage().contains("Fixed-point packed column did not converge"));
    Assertions.assertEquals(2, column.getLastIterationCount());
    Assertions.assertTrue(Double.isFinite(column.getLastConvergenceResidual()));
    Assertions.assertTrue(column.getLastConvergenceResidual() > column.getConvergenceTolerance());
    Assertions.assertFalse(column.solved());
    Assertions.assertSame(previousGas, column.getGasOutStream().getThermoSystem());
    Assertions.assertSame(previousLiquid, column.getLiquidOutStream().getThermoSystem());
    Assertions.assertEquals(previousTransfer, column.getTotalAbsoluteMolarTransfer(), 0.0);
  }

  /**
   * A single pass has no previous outlet against which convergence can be established.
   */
  @Test
  public void testFixedPointRejectsNonFiniteResidual() {
    RateBasedPackedColumn column = configuredColumn(createGasStream("gas", 0.10), createLiquidStream("liquid", 0.0),
        6.0);
    column.setMaxIterations(1);
    Assertions.assertThrows(IllegalStateException.class, column::run);
    Assertions.assertEquals(Double.POSITIVE_INFINITY, column.getLastConvergenceResidual());
    Assertions.assertFalse(column.solved());
  }

  /**
   * Reproduce the simultaneous-segment fixed-point exhaustion reported in issue 4111.
   */
  @Test
  public void testSimultaneousFixedPointRejectsUnconvergedProfile() {
    RateBasedPackedColumn column = configuredColumn(createGasStream("gas", 0.10), createLiquidStream("liquid", 0.0),
        6.0);
    column.setSegmentSolver(RateBasedPackedColumn.SegmentSolver.SIMULTANEOUS_RESIDUAL);
    Assertions.assertThrows(IllegalStateException.class, column::run);
    Assertions.assertEquals(column.getMaxIterations(), column.getLastIterationCount());
    Assertions.assertTrue(column.getLastConvergenceResidual() > column.getConvergenceTolerance());
    Assertions.assertFalse(column.solved());
  }

  /**
   * An exhausted fixed-point seed must still reach the independent EO residual test.
   */
  @Test
  public void testEquationOrientedDoesNotAcceptOrRejectItsSeedAsFinal() {
    RateBasedPackedColumn column = configuredColumn(createGasStream("gas", 0.10), createLiquidStream("liquid", 0.0),
        6.0);
    column.setMaxIterations(1);
    column.setColumnSolver(RateBasedPackedColumn.ColumnSolver.EQUATION_ORIENTED);
    IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, column::run);
    Assertions.assertTrue(failure.getMessage().contains("Equation-oriented packed column did not converge"));
    Assertions.assertTrue(Double.isFinite(column.getLastColumnResidualNorm()));
    Assertions.assertTrue(column.getSegmentResults().isEmpty(), "An internal seed must not be published");
    Assertions.assertFalse(column.solved());
  }

  /**
   * Reject the reported CO2 case instead of accepting a finite but unconverged profile.
   */
  @Test
  public void testEquationOrientedColumnRejectsUnconvergedProfile() {
    Stream gas = createGasStream("eo gas with CO2", 0.10);
    Stream liquid = createLiquidStream("eo lean liquid", 0.0);
    RateBasedPackedColumn column = configuredColumn(gas, liquid, 6.0);
    column.run();
    double previousOutletCo2 = moleFraction(column.getGasOutStream().getThermoSystem(), "CO2");
    column.setColumnSolver(RateBasedPackedColumn.ColumnSolver.EQUATION_ORIENTED);

    IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, column::run);

    Assertions.assertTrue(failure.getMessage().contains("did not converge"));
    Assertions.assertTrue(column.getLastColumnResidualNorm() > column.getColumnResidualTolerance());
    Assertions.assertEquals(previousOutletCo2, moleFraction(column.getGasOutStream().getThermoSystem(), "CO2"), 0.0,
        "A rejected EO profile must not overwrite the previous outlet");
    Assertions.assertFalse(column.solved(), "A failed rerun must clear the previous solved flag");
    Assertions.assertTrue(column.getLastColumnResidualIterations() > 0);
  }

  /**
   * Keep the TEG case as a regression for rejection with the shipped EO settings.
   */
  @Test
  public void testEquationOrientedTegRejectsUnconvergedProfile() {
    Stream wetGas = createWetNaturalGasStream("eo wet natural gas");
    Stream leanTeg = createLeanTegStream("eo lean TEG circulation", 50.0);
    RateBasedPackedColumn column = new RateBasedPackedColumn("EO TEG structured packing", wetGas, leanTeg);
    column.setColumnDiameter(1.2);
    column.setPackedHeight(10.0);
    column.setNumberOfSegments(3);
    column.setMaxIterations(10);
    column.setPackingType("Mellapak-250Y");
    column.setTransferComponents("water");
    column.setMassTransferCorrectionFactor(20.0);
    column.setColumnSolver(RateBasedPackedColumn.ColumnSolver.EQUATION_ORIENTED);
    column.setConvergenceTolerance(1.0e-8);

    Assertions.assertThrows(IllegalStateException.class, column::run);
    Assertions.assertTrue(column.getLastColumnResidualNorm() > column.getColumnResidualTolerance());
    Assertions.assertFalse(column.solved());
  }

  @Test
  public void testCanDisableExplicitHeatTransfer() {
    RateBasedPackedColumn column = configuredColumn(createGasStream("warm gas", 0.10),
        createLiquidStream("cool liquid", 0.0), 6.0);
    column.setHeatTransferModel(RateBasedPackedColumn.HeatTransferModel.NONE);

    column.run();

    RateBasedPackedColumn.SegmentResult firstSegment = column.getSegmentResults().get(0);
    Assertions.assertEquals(0.0, firstSegment.getOverallHeatTransferCoefficient(), 1.0e-12);
    Assertions.assertEquals(0.0, firstSegment.getHeatTransferRateW(), 1.0e-12);
  }

  @Test
  public void testDehydratesNaturalGasUsingTEG() {
    Stream wetGas = createWetNaturalGasStream("wet natural gas");
    Stream leanTeg = createLeanTegStream("lean TEG");
    double inletGasWater = componentMoles(wetGas.getThermoSystem(), "water");
    double inletTegWater = componentMoles(leanTeg.getThermoSystem(), "water");

    RateBasedPackedColumn column = new RateBasedPackedColumn("TEG dehydration contactor", wetGas, leanTeg);
    column.setColumnDiameter(1.2);
    column.setPackedHeight(8.0);
    column.setNumberOfSegments(5);
    column.setMaxIterations(20);
    column.setPackingType("Mellapak-250Y");
    column.setTransferComponents("water");
    column.setMassTransferCorrectionFactor(5.0);
    column.setConvergenceTolerance(1.0e-9);

    Assertions.assertThrows(IllegalStateException.class, column::run);
    Assertions.assertTrue(column.getLastConvergenceResidual() > column.getConvergenceTolerance());
    Assertions.assertFalse(column.solved());
    column.setMaxIterations(40);
    column.run();
    Assertions.assertTrue(column.solved());
    Assertions.assertTrue(Double.isFinite(column.getLastConvergenceResidual()));
    Assertions.assertTrue(column.getLastConvergenceResidual() <= column.getConvergenceTolerance());

    Double waterTransfer = column.getComponentTransferTotals().get("water");
    double outletGasWater = componentMoles(column.getGasOutStream().getThermoSystem(), "water");
    double outletTegWater = componentMoles(column.getLiquidOutStream().getThermoSystem(), "water");
    Assertions.assertNotNull(waterTransfer);
    Assertions.assertTrue(waterTransfer.doubleValue() > 0.0, "Positive water transfer means gas-to-TEG dehydration");
    Assertions.assertTrue(outletGasWater < inletGasWater,
        "Wet natural gas should contain less water after TEG contact");
    Assertions.assertTrue(outletTegWater > inletTegWater, "Lean TEG should pick up water from the gas");
    Assertions.assertTrue(column.getSegmentResults().get(0).getWettedArea() > 0.0);
    assertComponentBalance(wetGas.getThermoSystem(), leanTeg.getThermoSystem(),
        column.getGasOutStream().getThermoSystem(), column.getLiquidOutStream().getThermoSystem(), "water", 1.0e-6);
  }

  @Test
  public void testTegCirculationGivesTypicalWaterRemovalRatio() {
    Stream wetGas = createWetNaturalGasStream("wet natural gas for circulation test");
    Stream leanTeg = createLeanTegStream("typical lean TEG circulation", 50.0);
    double inletGasWaterKgHr = componentMassFlowKgPerHour(wetGas.getThermoSystem(), "water");

    RateBasedPackedColumn column = new RateBasedPackedColumn("typical TEG contactor", wetGas, leanTeg);
    column.setColumnDiameter(1.2);
    column.setPackedHeight(12.0);
    column.setNumberOfSegments(6);
    column.setMaxIterations(25);
    column.setPackingType("Mellapak-250Y");
    column.setTransferComponents("water");
    column.setMassTransferCorrectionFactor(20.0);
    column.setConvergenceTolerance(1.0e-9);

    column.run();

    double outletGasWaterKgHr = componentMassFlowKgPerHour(column.getGasOutStream().getThermoSystem(), "water");
    double waterRemovedKgHr = inletGasWaterKgHr - outletGasWaterKgHr;
    double tegCirculationKgHr = leanTeg.getFlowRate("kg/hr");
    double waterRemovedPerTegCirculation = waterRemovedKgHr / tegCirculationKgHr;
    double tegDensityKgPerLiter = 1.125;
    double tegLiterPerKgWaterRemoved = tegCirculationKgHr / tegDensityKgPerLiter / waterRemovedKgHr;

    Assertions.assertTrue(waterRemovedKgHr > 0.0, "The TEG contactor should remove water from the wet gas");
    Assertions.assertTrue(waterRemovedKgHr < inletGasWaterKgHr,
        "The contactor should not remove more water than enters with the gas");
    Assertions.assertTrue(waterRemovedPerTegCirculation > 0.02 && waterRemovedPerTegCirculation < 0.07,
        "Water removed per TEG circulation should be in a typical 15-40 L TEG/kg H2O range, was "
            + waterRemovedPerTegCirculation + " kg/kg");
    Assertions.assertTrue(tegLiterPerKgWaterRemoved > 15.0 && tegLiterPerKgWaterRemoved < 40.0,
        "Equivalent circulation should be typical for TEG absorbers, was " + tegLiterPerKgWaterRemoved
            + " L/kg water removed");
  }

  @Test
  public void testZeroPackedHeightGivesNoTransfer() {
    RateBasedPackedColumn column = configuredColumn(createGasStream("gas in", 0.10),
        createLiquidStream("lean liquid", 0.0), 0.0);
    column.run();

    Assertions.assertEquals(0.0, column.getTotalAbsoluteMolarTransfer(), 1.0e-12);
    Assertions.assertTrue(column.getComponentTransferTotals().isEmpty());
  }

  @Test
  public void testMoreHeightImprovesAbsorption() {
    RateBasedPackedColumn shortColumn = configuredColumn(createGasStream("gas in short", 0.10),
        createLiquidStream("lean liquid short", 0.0), 1.0);
    shortColumn.run();

    RateBasedPackedColumn tallColumn = configuredColumn(createGasStream("gas in tall", 0.10),
        createLiquidStream("lean liquid tall", 0.0), 8.0);
    tallColumn.run();

    double shortCo2 = moleFraction(shortColumn.getGasOutStream().getThermoSystem(), "CO2");
    double tallCo2 = moleFraction(tallColumn.getGasOutStream().getThermoSystem(), "CO2");
    Assertions.assertTrue(tallCo2 < shortCo2, "Taller packing should remove more CO2 for the same inputs");
  }

  @Test
  public void testJsonAndStreamIntrospection() {
    RateBasedPackedColumn column = configuredColumn(createGasStream("gas in", 0.10),
        createLiquidStream("lean liquid", 0.0), 6.0);
    column.run();

    Assertions.assertEquals(2, column.getInletStreams().size());
    Assertions.assertEquals(2, column.getOutletStreams().size());
    String json = column.toJson();
    Assertions.assertTrue(json.contains("componentTransferMolPerSec"));
    Assertions.assertTrue(json.contains("Pall-Ring-50"));
  }

  @Test
  public void testValidationReportsMissingStreams() {
    RateBasedPackedColumn column = new RateBasedPackedColumn("invalid column");
    Assertions.assertFalse(column.validateSetup().isValid());
  }

  /**
   * Create a configured rate-based column.
   *
   * @param gas gas inlet stream
   * @param liquid liquid inlet stream
   * @param height packed height in metres
   * @return configured column
   */
  private RateBasedPackedColumn configuredColumn(Stream gas, Stream liquid, double height) {
    RateBasedPackedColumn column = new RateBasedPackedColumn("rate based column", gas, liquid);
    column.setColumnDiameter(1.0);
    column.setPackedHeight(height);
    column.setNumberOfSegments(4);
    column.setMaxIterations(20);
    column.setPackingType("Pall-Ring-50");
    column.setTransferComponents("CO2");
    column.setMassTransferCorrectionFactor(3.0);
    column.setConvergenceTolerance(1.0e-9);
    return column;
  }

  /**
   * Create a gas stream with methane and optional carbon dioxide.
   *
   * @param name stream name
   * @param co2Fraction carbon dioxide mole fraction from zero to one
   * @return gas stream
   */
  private Stream createGasStream(String name, double co2Fraction) {
    SystemInterface gas = new SystemSrkEos(313.15, 50.0);
    gas.addComponent("methane", 1.0 - co2Fraction);
    gas.addComponent("CO2", co2Fraction);
    gas.setMixingRule("classic");
    Stream stream = new Stream(name, gas);
    stream.setFlowRate(1000.0, "kg/hr");
    stream.run();
    stream.getThermoSystem().initProperties();
    return stream;
  }

  /**
   * Create a liquid stream with water and optional dissolved carbon dioxide.
   *
   * @param name stream name
   * @param co2Fraction carbon dioxide mole fraction from zero to one
   * @return liquid stream
   */
  private Stream createLiquidStream(String name, double co2Fraction) {
    SystemInterface liquid = new SystemSrkEos(303.15, 50.0);
    liquid.addComponent("water", 1.0 - co2Fraction);
    liquid.addComponent("CO2", co2Fraction);
    liquid.setMixingRule("classic");
    Stream stream = new Stream(name, liquid);
    stream.setFlowRate(2000.0, "kg/hr");
    stream.run();
    stream.getThermoSystem().initProperties();
    return stream;
  }

  /**
   * Create a wet natural gas stream for TEG dehydration testing.
   *
   * @param name stream name
   * @return wet natural gas stream
   */
  private Stream createWetNaturalGasStream(String name) {
    SystemInterface gas = new SystemSrkCPAstatoil(313.15, 70.0);
    gas.addComponent("methane", 0.94);
    gas.addComponent("ethane", 0.04);
    gas.addComponent("propane", 0.018);
    gas.addComponent("water", 0.002);
    gas.createDatabase(true);
    gas.setMixingRule(10);
    Stream stream = new Stream(name, gas);
    stream.setFlowRate(1000.0, "kg/hr");
    stream.run();
    stream.getThermoSystem().initProperties();
    return stream;
  }

  /**
   * Create a lean TEG stream with a small water loading.
   *
   * @param name stream name
   * @return lean TEG stream
   */
  private Stream createLeanTegStream(String name) {
    return createLeanTegStream(name, 2000.0);
  }

  /**
   * Create a lean TEG stream with a small water loading and specified circulation rate.
   *
   * @param name stream name
   * @param flowRateKgHr lean TEG solution circulation rate in kg/hr
   * @return lean TEG stream
   */
  private Stream createLeanTegStream(String name, double flowRateKgHr) {
    SystemInterface teg = new SystemSrkCPAstatoil(303.15, 70.0);
    teg.addComponent("TEG", 0.9999);
    teg.addComponent("water", 0.0001);
    teg.createDatabase(true);
    teg.setMixingRule(10);
    Stream stream = new Stream(name, teg);
    stream.setFlowRate(flowRateKgHr, "kg/hr");
    stream.run();
    stream.getThermoSystem().initProperties();
    return stream;
  }

  /**
   * Get component mole fraction in phase zero.
   *
   * @param system thermodynamic system
   * @param componentName component name
   * @return component mole fraction, or zero when absent
   */
  private double moleFraction(SystemInterface system, String componentName) {
    if (!system.getPhase(0).hasComponent(componentName)) {
      return 0.0;
    }
    return system.getPhase(0).getComponent(componentName).getx();
  }

  /**
   * Assert component material balance across the column.
   *
   * @param gasIn gas inlet system
   * @param liquidIn liquid inlet system
   * @param gasOut gas outlet system
   * @param liquidOut liquid outlet system
   * @param componentName component name
   * @param tolerance balance tolerance in mol/s
   */
  private void assertComponentBalance(SystemInterface gasIn, SystemInterface liquidIn, SystemInterface gasOut,
      SystemInterface liquidOut, String componentName, double tolerance) {
    double inlet = componentMoles(gasIn, componentName) + componentMoles(liquidIn, componentName);
    double outlet = componentMoles(gasOut, componentName) + componentMoles(liquidOut, componentName);
    Assertions.assertEquals(inlet, outlet, tolerance);
  }

  /**
   * Get component moles in a system.
   *
   * @param system thermodynamic system
   * @param componentName component name
   * @return component moles
   */
  private double componentMoles(SystemInterface system, String componentName) {
    if (!system.getPhase(0).hasComponent(componentName)) {
      return 0.0;
    }
    return system.getPhase(0).getComponent(componentName).getNumberOfmoles();
  }

  /**
   * Get component mass flow in a system.
   *
   * @param system thermodynamic system
   * @param componentName component name
   * @return component mass flow in kg/hr
   */
  private double componentMassFlowKgPerHour(SystemInterface system, String componentName) {
    if (!system.getPhase(0).hasComponent(componentName)) {
      return 0.0;
    }
    return system.getPhase(0).getComponent(componentName).getNumberOfmoles()
        * system.getPhase(0).getComponent(componentName).getMolarMass() * 3600.0;
  }
}
