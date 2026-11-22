package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationResult.ProductResult;
import neqsim.process.equipment.heatexchanger.FiredHeater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.DoeBigHillSweetAssay;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * End-to-end DOE Big Hill assay, heating, atmospheric, and vacuum integration.
 *
 * <p>
 * Every material stream derives from one public DOE Big Hill modeled assay. The atmospheric operating point repeats the
 * campaign's numerical integration qualification; the vacuum operating point and fired-heater factors are explicit
 * engineering assumptions. This test qualifies software integration and closure, not plant yields or ASTM D1160
 * performance.
 * </p>
 */
public class DoeBigHillAtmosphericVacuumIntegrationTest {
  private static final int SIDE_DRAW_TRAY = 4;
  private static final double BALANCE_TOLERANCE = 5.0e-2;
  private static final double FEED_FLOW_KG_PER_HOUR = 5000.0;
  private static final double FURNACE_EFFICIENCY = 0.85;
  private static final double FUEL_LHV_J_PER_KG = 48.0e6;
  private static final double FUEL_CO2_KG_PER_KG = 2.75;

  /** Carry one assay identity through heating and both rigorous columns. */
  @Test
  @Timeout(value = 300, unit = TimeUnit.SECONDS)
  public void connectedFrontEndClosesMaterialEnergyAndUtilityReceipts() {
    UUID id = UUID.randomUUID();
    Stream crudeInlet = createCrudeInlet();
    FiredHeater furnace = createFurnace(crudeInlet);
    crudeInlet.run(id);
    furnace.run(id);
    assertUtilityAndEmissionsReceipt(furnace);

    DistillationColumn atmospheric = createAtmosphericColumn(furnace.getOutletStream());
    atmospheric.run(id);
    assertAcceptedColumn(atmospheric);
    assertTrue(atmospheric.getMassBalanceError() <= BALANCE_TOLERANCE);
    assertTrue(atmospheric.getEnergyBalanceError() <= BALANCE_TOLERANCE);

    StreamInterface atmosphericBottoms = atmospheric.getLiquidOutStream();
    double[] handoffComposition = atmosphericBottoms.getThermoSystem().getMolarComposition().clone();
    DoeBigHillVacuumFractionationCase vacuum = DoeBigHillVacuumFractionationCase
        .createFromAtmosphericBottoms("DOE Big Hill connected vacuum", atmosphericBottoms, vacuumInputs());
    assertArrayEquals(handoffComposition, vacuum.getFeedStream().getThermoSystem().getMolarComposition(), 1.0e-12);

    vacuum.getColumn().run(id);
    assertAcceptedColumn(vacuum.getColumn());
    DoeBigHillVacuumFractionationResult vacuumResult = DoeBigHillVacuumFractionationResult.evaluate(vacuum);
    assertTrue(vacuumResult.getMassClosureRelativeError() <= BALANCE_TOLERANCE);
    assertTrue(vacuumResult.getMaximumComponentMolarClosureRelativeError() <= BALANCE_TOLERANCE);
    assertTrue(vacuumResult.getColumnEnergyBalanceError() <= BALANCE_TOLERANCE);

    StreamInterface atmosphericOverhead = atmospheric.getGasOutStream();
    StreamInterface atmosphericSideDraw = atmospheric.getSideDrawStream(SIDE_DRAW_TRAY,
        DistillationColumn.SideDrawPhase.LIQUID);
    StreamInterface vacuumOverhead = vacuum.getColumn().getGasOutStream();
    StreamInterface vacuumBottoms = vacuum.getColumn().getLiquidOutStream();
    StreamInterface[] finalProducts = {atmosphericOverhead, atmosphericSideDraw, vacuumOverhead, vacuumBottoms};
    assertOverallMaterialClosure(furnace.getOutletStream(), finalProducts);

    ProductBoilingPointDistribution atmosphericOverheadBoiling = ProductBoilingPointDistribution
        .from(atmosphericOverhead);
    ProductBoilingPointDistribution atmosphericSideDrawBoiling = ProductBoilingPointDistribution
        .from(atmosphericSideDraw);
    assertTrue(atmosphericOverheadBoiling.getMeanNormalBoilingPointKelvin() < atmosphericSideDrawBoiling
        .getMeanNormalBoilingPointKelvin());

    ProductResult vacuumDistillate = vacuumResult.getProduct("Overhead");
    ProductResult vacuumResidue = vacuumResult.getProduct("Bottoms");
    assertTrue(vacuumDistillate.getMeanNormalBoilingPointKelvin() < vacuumResidue.getMeanNormalBoilingPointKelvin());
    assertTrue(Double.isFinite(vacuumDistillate.getNormalBoilingPointQuantileCelsius(0.10)));
    assertTrue(Double.isFinite(vacuumDistillate.getNormalBoilingPointQuantileCelsius(0.90)));
    assertTrue(Double.isFinite(vacuumResidue.getNormalBoilingPointQuantileCelsius(0.10)));
    assertTrue(Double.isFinite(vacuumResidue.getNormalBoilingPointQuantileCelsius(0.90)));
  }

  /**
   * Sweep the vacuum reboiler temperature without mutating the connected atmospheric-bottoms handoff.
   *
   * <p>
   * This connected screen solves one atmospheric column and two independent vacuum columns. Allow headroom for the
   * supported Java 8 runtime while retaining a bounded integration-test timeout.
   * </p>
   */
  @Test
  @Timeout(value = 600, unit = TimeUnit.SECONDS)
  public void connectedVacuumSensitivityQualifiesIndependentPoints() {
    UUID id = UUID.randomUUID();
    Stream crudeInlet = createCrudeInlet();
    FiredHeater furnace = createFurnace(crudeInlet);
    crudeInlet.run(id);
    furnace.run(id);

    DistillationColumn atmospheric = createAtmosphericColumn(furnace.getOutletStream());
    atmospheric.run(id);
    assertAcceptedColumn(atmospheric);
    StreamInterface atmosphericBottoms = atmospheric.getLiquidOutStream();
    double sourceMassFlow = atmosphericBottoms.getFlowRate("kg/hr");
    double[] sourceComposition = atmosphericBottoms.getThermoSystem().getMolarComposition().clone();
    double[] temperatures = {698.0, 702.0};

    DoeBigHillVacuumReboilerTemperatureSensitivity sensitivity = DoeBigHillVacuumReboilerTemperatureSensitivity
        .run("DOE Big Hill connected vacuum screen", atmosphericBottoms, vacuumInputs(), temperatures);

    temperatures[0] = 999.0;
    assertEquals(sourceMassFlow, atmosphericBottoms.getFlowRate("kg/hr"), 0.0);
    assertArrayEquals(sourceComposition, atmosphericBottoms.getThermoSystem().getMolarComposition(), 0.0);
    assertEquals(2, sensitivity.getPoints().length);
    assertNotSame(sensitivity.getPoints(), sensitivity.getPoints());
    assertEquals(698.0, sensitivity.getPoint(0).getReboilerTemperatureKelvin(), 0.0);
    assertEquals(702.0, sensitivity.getPoint(1).getReboilerTemperatureKelvin(), 0.0);
    assertTrue(sensitivity.getMinimumOverheadMassFraction() > 0.0);
    assertTrue(sensitivity.getMaximumOverheadMassFraction() < 1.0);
    assertTrue(sensitivity.getMaximumMassClosureRelativeError() <= BALANCE_TOLERANCE);
    assertTrue(sensitivity.getMaximumComponentMolarClosureRelativeError() <= BALANCE_TOLERANCE);
    assertTrue(sensitivity.getMaximumColumnEnergyBalanceError() <= BALANCE_TOLERANCE);

    for (DoeBigHillVacuumReboilerTemperatureSensitivity.PointResult point : sensitivity.getPoints()) {
      ProductResult overhead = point.getFractionationResult().getProduct("Overhead");
      ProductResult bottoms = point.getFractionationResult().getProduct("Bottoms");
      assertTrue(overhead.getMeanNormalBoilingPointKelvin() < bottoms.getMeanNormalBoilingPointKelvin());
      assertTrue(Double.isFinite(point.getFractionationResult().getMeshResidualNorm()));
      assertTrue(point.getFractionationResult().getMeshResidualNorm() >= 0.0);
    }
  }

  private static Stream createCrudeInlet() {
    SystemInterface crude = new SystemSrkEos(298.15, 1.5);
    OilAssayCharacterisation assay = DoeBigHillSweetAssay.create(crude);
    assay.apply();
    crude.setMixingRule("classic");

    Stream feed = new Stream("DOE Big Hill crude inlet", crude);
    feed.setFlowRate(FEED_FLOW_KG_PER_HOUR, "kg/hr");
    feed.setTemperature(298.15, "K");
    feed.setPressure(1.5, "bara");
    return feed;
  }

  private static FiredHeater createFurnace(Stream crudeInlet) {
    FiredHeater furnace = new FiredHeater("DOE Big Hill crude furnace", crudeInlet);
    furnace.setOutletTemperature(550.0, "K");
    furnace.setOutletPressure(1.5, "bara");
    furnace.setThermalEfficiency(FURNACE_EFFICIENCY);
    furnace.setFuelLHV(FUEL_LHV_J_PER_KG);
    furnace.setFuelCO2Factor(FUEL_CO2_KG_PER_KG);
    furnace.setNoxFactor(0.08);
    furnace.setStackTemperature(423.15);
    return furnace;
  }

  private static DistillationColumn createAtmosphericColumn(StreamInterface heatedFeed) {
    DistillationColumn column = new DistillationColumn("DOE Big Hill connected atmospheric", 8, true, true);
    column.addFeedStream(heatedFeed, 4);
    column.setTopPressure(1.2);
    column.setBottomPressure(1.5);
    column.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    column.getReboiler().setOutletTemperature(650.0);
    column.setCondenserRefluxRatio(1.0);
    column.setLiquidSideDrawFraction(SIDE_DRAW_TRAY, 0.10);
    column.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
    column.setMaxNumberOfIterations(300);
    column.setTemperatureTolerance(0.20);
    column.setMassBalanceTolerance(BALANCE_TOLERANCE);
    column.setEnthalpyBalanceTolerance(BALANCE_TOLERANCE);
    column.setEnforceEnergyBalanceTolerance(true);
    return column;
  }

  private static OperatingInputs vacuumInputs() {
    return new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5);
  }

  private static void assertAcceptedColumn(DistillationColumn column) {
    String diagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), diagnostics);
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, column.getLastSolverTypeUsed(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FAILED, column.getLastSolveStatus(), diagnostics);
  }

  private static void assertUtilityAndEmissionsReceipt(FiredHeater furnace) {
    double absorbedDutyW = furnace.getAbsorbedDuty("W");
    double firedDutyW = furnace.getFiredDuty("W");
    double fuelKgPerHour = furnace.getFuelConsumption("kg/hr");
    double co2KgPerHour = furnace.getCO2Emissions("kg/hr");
    assertTrue(Double.isFinite(absorbedDutyW) && absorbedDutyW > 0.0);
    assertEquals(absorbedDutyW / FURNACE_EFFICIENCY, firedDutyW, 1.0e-10 * firedDutyW);
    assertEquals(firedDutyW / FUEL_LHV_J_PER_KG * 3600.0, fuelKgPerHour, 1.0e-10 * fuelKgPerHour);
    assertEquals(fuelKgPerHour * FUEL_CO2_KG_PER_KG, co2KgPerHour, 1.0e-10 * co2KgPerHour);
  }

  private static void assertOverallMaterialClosure(StreamInterface feed, StreamInterface[] products) {
    double feedMassFlow = feed.getFlowRate("kg/hr");
    double productMassFlow = 0.0;
    for (StreamInterface product : products) {
      productMassFlow += product.getFlowRate("kg/hr");
    }
    assertEquals(feedMassFlow, productMassFlow, BALANCE_TOLERANCE * feedMassFlow);

    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double feedMolarFlow = feed.getFlowRate("mol/hr");
    for (int i = 0; i < feedComposition.length; i++) {
      double feedComponentFlow = feedMolarFlow * feedComposition[i];
      double productComponentFlow = 0.0;
      for (StreamInterface product : products) {
        assertEquals(feedComposition.length, product.getThermoSystem().getNumberOfComponents());
        productComponentFlow += product.getFlowRate("mol/hr") * product.getThermoSystem().getMolarComposition()[i];
      }
      assertEquals(feedComponentFlow, productComponentFlow,
          Math.max(1.0e-7, BALANCE_TOLERANCE * Math.abs(feedComponentFlow)));
    }
  }
}
