package neqsim.process.equipment.watertreatment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain.StageType;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain.WaterTreatmentStage;

/**
 * Unit tests for ProducedWaterTreatmentTrain.
 *
 * @author ESOL
 * @version 1.0
 */
public class ProducedWaterTreatmentTrainTest {
  private ProducedWaterTreatmentTrain train;

  @BeforeEach
  void setUp() {
    train = new ProducedWaterTreatmentTrain("PWTT-01");
    train.clearStages(); // Clear default stages for controlled testing
  }

  @Test
  void testNCSLimit() {
    assertEquals(30.0, ProducedWaterTreatmentTrain.NCS_OIW_LIMIT_MGL, 0.01, "NCS OIW limit should be 30 mg/L");
  }

  @Test
  void testDefaultStageConfiguration() {
    // Use fractional efficiency values (0-1), not percentages
    train.addStage(new WaterTreatmentStage("Hydrocyclone", StageType.HYDROCYCLONE, 0.95));
    train.addStage(new WaterTreatmentStage("IGF", StageType.FLOTATION, 0.80));
    train.addStage(new WaterTreatmentStage("Skim Tank", StageType.SKIM_TANK, 0.70));

    train.setInletOilConcentration(1000.0);
    train.run();

    double expected = 1.0 - (1.0 - 0.95) * (1.0 - 0.80) * (1.0 - 0.70);
    assertEquals(expected, train.getOverallEfficiency(), 0.01, "Overall efficiency calculation");
  }

  @Test
  void testOilInWaterCalculation() {
    train.setInletOilConcentration(1000.0);
    train.addStage(new WaterTreatmentStage("Hydrocyclone", StageType.HYDROCYCLONE, 0.95));
    train.run();

    double outletOIW = train.getOilInWaterMgL();
    assertEquals(50.0, outletOIW, 1.0, "Outlet OIW should be ~50 mg/L");
  }

  @Test
  void testMultiStageOIWReduction() {
    train.setInletOilConcentration(500.0);
    train.addStage(new WaterTreatmentStage("Primary", StageType.HYDROCYCLONE, 0.95));
    train.addStage(new WaterTreatmentStage("Secondary", StageType.FLOTATION, 0.80));
    train.run();

    double outletOIW = train.getOilInWaterMgL();
    assertEquals(5.0, outletOIW, 0.5, "Multi-stage outlet OIW");
  }

  @Test
  void testComplianceCheck() {
    train.setInletOilConcentration(300.0);
    train.addStage(new WaterTreatmentStage("Hydrocyclone", StageType.HYDROCYCLONE, 0.85));
    train.run();
    assertFalse(train.isDischargeCompliant(), "Should not be compliant at 45 mg/L");

    train.addStage(new WaterTreatmentStage("IGF", StageType.FLOTATION, 0.70));
    train.run();
    assertTrue(train.isDischargeCompliant(), "Should be compliant after second stage");
  }

  @Test
  void testPpmConversion() {
    train.setInletOilConcentration(100.0);
    train.addStage(new WaterTreatmentStage("Test", StageType.HYDROCYCLONE, 0.50));
    train.run();

    double mgL = train.getOilInWaterMgL();
    double ppm = train.getOilInWaterPpm();
    assertEquals(mgL, ppm, 0.1, "mg/L and ppm should be approximately equal");
  }

  @Test
  void testAnnualDischarge() {
    train.setInletOilConcentration(200.0);
    train.setWaterFlowRate(416.67); // 10,000 m3/day = 416.67 m3/hr
    train.addStage(new WaterTreatmentStage("Primary", StageType.HYDROCYCLONE, 0.95));
    train.run();

    double annual = train.getAnnualOilDischargeTonnes(8760.0);
    assertTrue(annual > 30 && annual < 40, "Annual oil discharge should be ~36.5 tonnes/year");
  }

  @Test
  void testEmptyTrain() {
    // No stages added (already cleared in setUp)
    train.setInletOilConcentration(100.0);
    train.run();

    double outlet = train.getOilInWaterMgL();
    assertEquals(100.0, outlet, 0.01, "No stages = no treatment");
    assertEquals(0.0, train.getOverallEfficiency(), 0.01, "No stages = 0% efficiency");
  }

  @Test
  void testReportGeneration() {
    train.setInletOilConcentration(400.0);
    train.setWaterFlowRate(208.0);
    train.addStage(new WaterTreatmentStage("Hydrocyclone", StageType.HYDROCYCLONE, 0.95));
    train.addStage(new WaterTreatmentStage("IGF", StageType.FLOTATION, 0.80));
    train.run();

    String report = train.generateReport();
    assertNotNull(report, "Report should not be null");
    assertTrue(report.contains("Treatment"), "Report should contain 'Treatment'");
  }

  /**
   * Executes the documented separator-to-treatment handoff and current result APIs.
   */
  @Test
  void documentedSeparatorIntegrationUsesCurrentResultApis() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 42.0, 10.0);
    fluid.addComponent("methane", 72.0);
    fluid.addComponent("n-heptane", 14.0);
    fluid.addComponent("water", 40.0);
    fluid.setMixingRule(10);

    Stream feed = new Stream("Well stream", fluid);
    feed.setTemperature(72.0, "C");
    feed.setPressure(10.7, "bara");
    feed.setFlowRate(720.0, "kg/hr");
    feed.run();

    ThreePhaseSeparator separator = new ThreePhaseSeparator("Production Separator", feed);
    StreamInterface oilOut = separator.getOilOutStream();
    StreamInterface waterOut = separator.getWaterOutStream();
    ProducedWaterTreatmentTrain treatment =
        new ProducedWaterTreatmentTrain("PW Treatment", waterOut);
    treatment.setInletOilConcentration(800.0);

    ProcessSystem process = new ProcessSystem();
    process.add(separator);
    process.add(treatment);
    process.run();

    double waterMassFlowKgPerHour = waterOut.getFlowRate("kg/hr");
    double oilMassFlowKgPerHour = oilOut.getFlowRate("kg/hr");
    double producedLiquidMassFlowKgPerHour =
        oilMassFlowKgPerHour + waterMassFlowKgPerHour;
    assertTrue(producedLiquidMassFlowKgPerHour > 0.0);

    double producedLiquidWaterMassFraction =
        waterMassFlowKgPerHour / producedLiquidMassFlowKgPerHour;
    assertTrue(producedLiquidWaterMassFraction >= 0.0);
    assertTrue(producedLiquidWaterMassFraction <= 1.0);
    assertTrue(Double.isFinite(treatment.getOilInWaterMgL()));
    assertEquals(
        treatment.getOilInWaterMgL() <= ProducedWaterTreatmentTrain.NCS_OIW_LIMIT_MGL,
        treatment.isDischargeCompliant());
    assertNotNull(treatment.getTreatedWaterStream());
    assertNotNull(treatment.getRecoveredOilStream());
    assertTrue(treatment.generateReport().contains("NCS Compliant"));
  }

}
