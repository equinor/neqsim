package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * End-to-end tests for the autonomous root cause analysis entry point.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class AutonomousInvestigationTest {

  private ProcessSystem process;
  private String compressorName;

  /**
   * Builds a simple compressor process for autonomous analysis.
   */
  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 30.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");

    compressorName = "TestCompressor";
    Compressor comp = new Compressor(compressorName, feed);
    comp.setOutletPressure(80.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();
  }

  /**
   * Verifies that autonomous analysis infers the symptom from the data, discovers relationships, and produces a ranked
   * report without a symptom being set.
   */
  @Test
  void autonomousInfersSymptomAndDiscoversRelationships() {
    int n = 60;
    double[] time = new double[n];
    double[] vibration = new double[n];
    double[] temperature = new double[n];
    for (int i = 0; i < n; i++) {
      time[i] = i * 60.0;
      vibration[i] = 3.0 + 0.05 * i; // rising
      temperature[i] = 150.0 + vibration[i] * 2.0; // follows vibration
    }
    vibration[n - 1] = 9.0; // breach at the end

    Map<String, double[]> historian = new HashMap<String, double[]>();
    historian.put("TestCompressor.vibration", vibration);
    historian.put("TestCompressor.temperature", temperature);

    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setHistorianData(historian, time);
    rca.setDesignLimit("TestCompressor.vibration", Double.NaN, 7.1);
    rca.setSimulationEnabled(false);

    RootCauseReport report = rca.analyzeAutonomous();

    assertNotNull(report);
    assertEquals(Symptom.HIGH_VIBRATION, report.getSymptom(), "symptom should be inferred from the data");
    assertFalse(rca.getLastAnomalies().isEmpty(), "anomalies should have been detected");
    assertFalse(rca.getLastRelationships().isEmpty(), "relationships should have been discovered");
    // Topology classification runs turnkey: tag-to-equipment is auto-derived from the process, so causal edges are
    // produced even though no map was supplied.
    assertFalse(rca.getLastCausalEdges().isEmpty(), "causal edges should be produced without a supplied tag map");
    assertEquals(CausalTopologyModel.Verdict.LOCAL, rca.getLastCausalEdges().get(0).getVerdict(),
        "both tags belong to the same compressor, so the edge is LOCAL");
  }

  /**
   * Verifies that autonomous analysis with a tag-to-equipment map produces topology-classified causal edges.
   */
  @Test
  void autonomousWithTopologyProducesCausalEdges() {
    int n = 60;
    double[] time = new double[n];
    double[] vibration = new double[n];
    double[] power = new double[n];
    for (int i = 0; i < n; i++) {
      time[i] = i * 60.0;
      vibration[i] = 3.0 + 0.05 * i;
      power[i] = 1000.0 + vibration[i] * 10.0;
    }
    vibration[n - 1] = 9.0;

    Map<String, double[]> historian = new HashMap<String, double[]>();
    historian.put("TestCompressor.vibration", vibration);
    historian.put("TestCompressor.power", power);

    Map<String, String> tagToEquipment = new HashMap<String, String>();
    tagToEquipment.put("TestCompressor.vibration", compressorName);
    tagToEquipment.put("TestCompressor.power", compressorName);

    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setHistorianData(historian, time);
    rca.setDesignLimit("TestCompressor.vibration", Double.NaN, 7.1);
    rca.setSimulationEnabled(false);

    RootCauseReport report = rca.analyzeAutonomous(tagToEquipment);
    assertNotNull(report);
    assertFalse(rca.getLastCausalEdges().isEmpty(), "topology-classified edges should be produced");
    // Both tags belong to the same equipment, so the edge is LOCAL.
    assertEquals(CausalTopologyModel.Verdict.LOCAL, rca.getLastCausalEdges().get(0).getVerdict());
  }

  /**
   * Verifies that autonomous analysis throws a helpful error when no symptom can be inferred.
   */
  @Test
  void autonomousThrowsWhenNoSymptomInferable() {
    int n = 30;
    double[] flat = new double[n];
    for (int i = 0; i < n; i++) {
      flat[i] = 42.0;
    }
    Map<String, double[]> historian = new HashMap<String, double[]>();
    historian.put("UnmappableTag123", flat);

    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setHistorianData(historian, null);
    rca.setSimulationEnabled(false);

    assertThrows(IllegalStateException.class, () -> rca.analyzeAutonomous());
  }
}
