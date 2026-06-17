package neqsim.process.util.exergy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for the plant-wide exergy analysis API on {@link ProcessSystem} and
 * {@link ProcessModel}.
 */
public class ExergyAnalysisTest {

  /**
   * Build a minimal compression + cooling + valve train for testing.
   *
   * @return a freshly run ProcessSystem
   */
  private ProcessSystem buildTrain() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");

    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(150.0);
    comp.setIsentropicEfficiency(0.75);

    Cooler cooler = new Cooler("cooler", comp.getOutletStream());
    cooler.setOutTemperature(273.15 + 30.0);

    ThrottlingValve valve = new ThrottlingValve("valve", cooler.getOutletStream());
    valve.setOutletPressure(80.0);

    ProcessSystem p = new ProcessSystem();
    p.add(feed);
    p.add(comp);
    p.add(cooler);
    p.add(valve);
    p.run();
    return p;
  }

  @Test
  void testProcessSystemExergyChangeHonorsUnits() {
    ProcessSystem p = buildTrain();
    double j = p.getExergyChange("J");
    double kj = p.getExergyChange("kJ");
    double mj = p.getExergyChange("MJ");
    assertEquals(j / 1.0e3, kj, Math.abs(j) * 1.0e-9 + 1.0e-9);
    assertEquals(j / 1.0e6, mj, Math.abs(j) * 1.0e-9 + 1.0e-9);
  }

  @Test
  void testProcessSystemExergyDestructionIsNonNegative() {
    ProcessSystem p = buildTrain();
    double destr = p.getExergyDestruction("kW");
    assertTrue(destr >= 0.0, "exergy destruction must be non-negative, got " + destr);
  }

  @Test
  void testExergyAnalysisReportHasEntryPerUnit() {
    ProcessSystem p = buildTrain();
    ExergyAnalysisReport report = p.getExergyAnalysis();
    assertNotNull(report);
    assertEquals(4, report.size(), "expected one entry per unit operation");

    double total = report.getTotalExergyDestruction("kW");
    assertTrue(total >= 0.0);
    assertEquals(p.getExergyDestruction("kW"), total, Math.abs(total) * 1.0e-9 + 1.0e-9);

    List<ExergyAnalysisReport.Entry> top = report.getTopDestructionHotspots(2);
    assertEquals(2, top.size());
    assertTrue(top.get(0).getExergyDestructionJ() >= top.get(1).getExergyDestructionJ());

    String json = report.toJson();
    assertTrue(json.contains("\"entries\""));
    assertTrue(json.contains("\"comp\""));
  }

  @Test
  void testProcessModelAggregatesAreas() {
    ProcessSystem a = buildTrain();
    ProcessSystem b = buildTrain();
    ProcessModel plant = new ProcessModel();
    plant.add("Area-A", a);
    plant.add("Area-B", b);

    double sumAreas = a.getExergyDestruction("kW") + b.getExergyDestruction("kW");
    assertEquals(sumAreas, plant.getExergyDestruction("kW"),
        Math.max(Math.abs(sumAreas), 1.0) * 1.0e-9);

    ExergyAnalysisReport report = plant.getExergyAnalysis();
    assertEquals(8, report.size(), "expected 4 units per area × 2 areas");

    Map<String, Double> byArea = report.getDestructionByArea("kW");
    assertEquals(2, byArea.size());
    assertTrue(byArea.containsKey("Area-A"));
    assertTrue(byArea.containsKey("Area-B"));
  }
}
