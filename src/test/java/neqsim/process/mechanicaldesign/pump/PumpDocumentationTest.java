package neqsim.process.mechanicaldesign.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.DataSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Executes the complete Java example in {@code docs/process/equipment/pumps.md}. */
public class PumpDocumentationTest {
  @Test
  public void testPumpAndApi610GuideExample() {
    SystemInterface fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("pump feed", fluid);
    feed.setFlowRate(100.0, "m3/hr");
    feed.run();

    Pump pump = new Pump("P-100", feed);
    double[] speed = new double[] { 1000.0 };
    double[][] flow = new double[][] { { 50.0, 75.0, 100.0, 125.0, 150.0 } };
    double[][] head = new double[][] { { 120.0, 115.0, 105.0, 90.0, 70.0 } };
    double[][] efficiency = new double[][] { { 65.0, 75.0, 82.0, 78.0, 68.0 } };
    double[][] npshRequired = new double[][] { { 2.0, 2.4, 3.0, 4.0, 5.5 } };

    pump.getPumpChart().setCurves(new double[] {}, speed, flow, head, efficiency);
    pump.getPumpChart().setHeadUnit("meter");
    pump.getPumpChart().setNPSHCurve(npshRequired);
    pump.setSpeed(1000.0);
    pump.setCheckNPSH(true);
    pump.setNPSHMargin(1.15);
    pump.run();

    double powerKw = pump.getPower("kW");
    double vendorHeadM = pump.getPumpChart().getHead(feed.getFlowRate("m3/hr"), pump.getSpeed());
    double npshAvailableM = pump.getNPSHAvailable();
    double npshRequiredM = pump.getNPSHRequired();

    assertTrue(powerKw > 0.0);
    assertEquals(105.0, vendorHeadM, 1.0e-9);
    assertTrue(Double.isFinite(npshAvailableM));
    assertTrue(npshAvailableM > npshRequiredM);
    assertEquals(3.0, npshRequiredM, 1.0e-9);
    assertFalse(pump.isCavitating());

    PumpMechanicalDesign design = pump.getMechanicalDesign();
    design.setApi610PumpType(PumpApi610DesignCalculator.Api610PumpType.OH2);
    design.setMaximumSuctionPressure(8.0);
    design.setFurnishedCasingMawp(25.0);
    design.calcDesign();

    PumpApi610DesignCalculator assessment = design.getApi610Assessment();
    PumpApi610DesignCalculator.AssessmentStatus status = assessment.getAssessmentStatus();
    String responseJson = design.getResponse().toJson();

    assertNotNull(status);
    assertEquals(DataSource.VENDOR_CURVE, assessment.getBepSource());
    assertEquals(DataSource.VENDOR_CURVE, assessment.getNpshrSource());
    assertEquals("POR", assessment.getOperatingRegion());
    assertEquals(1.0, assessment.getOperatingFlowRatio(), 0.05);
    assertTrue(assessment.getSelectedDriverPowerKw() > powerKw);
    assertTrue(assessment.getRequiredCasingPressureBara() <= 25.0);
    assertFalse(assessment.getChecks().isEmpty());
    assertTrue(responseJson.contains("\"api610Screening\""));
    assertTrue(responseJson.contains("\"api610TypeCode\":\"OH2\""));
  }
}
