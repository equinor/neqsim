package neqsim.process.chemistry.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link StreamScaleAnalyzer} — the bridge between a process stream and the coupled multi-mineral scale
 * equilibrium, including the mg/L to kg/day scaling-rate conversion.
 */
public class StreamScaleAnalyzerTest {

  private ScalePredictionCalculator bariteBrine() {
    ScalePredictionCalculator p = new ScalePredictionCalculator();
    p.setTemperatureCelsius(70.0);
    p.setPressureBara(100.0);
    p.setCalciumConcentration(500.0);
    p.setBariumConcentration(300.0);
    p.setSulphateConcentration(2000.0);
    p.setBicarbonateConcentration(300.0);
    p.setTotalDissolvedSolids(60000.0);
    p.setCO2PartialPressure(1.0);
    p.enableAutoPH();
    return p;
  }

  @Test
  void testScaleRateConversion() {
    StreamScaleAnalyzer a = new StreamScaleAnalyzer(bariteBrine()).setWaterFlowLitrePerDay(100000.0);
    a.analyze();

    double totalMgL = a.getTotalScaleMassMgPerL();
    assertTrue(totalMgL > 0.0, "Barite brine should precipitate scale");
    assertEquals("BaSO4", a.getDominantScale(), "Barite should dominate this brine");

    double expectedRate = totalMgL * 100000.0 / 1.0e6;
    assertEquals(expectedRate, a.getTotalScaleRateKgPerDay(), 1.0e-9, "kg/day rate must equal mg/L * L/day / 1e6");
    assertTrue(a.getScaleRateKgPerDay("BaSO4") > 0.0, "Barite scaling rate must be positive");
  }

  @Test
  void testJsonReportContainsRateFields() {
    StreamScaleAnalyzer a = new StreamScaleAnalyzer(bariteBrine()).setWaterFlowLitrePerDay(50000.0);
    String json = a.toJson();
    assertTrue(json.contains("totalScaleRate_kgPerDay"), "JSON must include the kg/day rate");
    assertTrue(json.contains("dominantScale"), "JSON must include the dominant scale");
    assertTrue(json.contains("scalesAtRisk"), "JSON must include the at-risk mineral list");
  }

  @Test
  void testFromStreamRunsWithoutError() {
    // A non-electrolyte fluid carries no ions -> no scale, but the wiring and water-flow path must
    // still execute and return finite values.
    SystemSrkEos sys = new SystemSrkEos(298.15, 50.0);
    sys.addComponent("methane", 80.0);
    sys.addComponent("CO2", 5.0);
    sys.addComponent("water", 15.0);
    sys.setMixingRule("classic");
    sys.setMultiPhaseCheck(true);
    Stream s = new Stream("produced", sys);
    s.setFlowRate(1000.0, "kg/hr");
    s.setTemperature(25.0, "C");
    s.setPressure(50.0, "bara");
    s.run();

    StreamScaleAnalyzer a = StreamScaleAnalyzer.fromStream((StreamInterface) s);
    a.analyze();

    assertTrue(a.getWaterFlowLitrePerDay() >= 0.0, "Water flow must be non-negative");
    assertFalse(Double.isNaN(a.getTotalScaleMassMgPerL()), "Total scale mass must be finite");
    assertFalse(Double.isNaN(a.getTotalScaleRateKgPerDay()), "Total scale rate must be finite");
    assertNotNull(a.toJson());
  }
}
