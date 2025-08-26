package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CompressorCapacityCheckTest {

  @Test
  public void testOperatingEnvelopeSingleSpeed() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 100.0);
    gas.setMixingRule(2);
    Stream inlet = new Stream("inlet", gas);
    Compressor comp = new Compressor("comp", inlet);

    CompressorChart chart = new CompressorChart();
    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    double[] speed = new double[] {1000.0};
    double[][] flow = new double[][] {{1000.0, 1500.0, 2000.0, 2500.0}};
    double[][] head = new double[][] {{80.0, 70.0, 60.0, 50.0}};
    double[][] eff = new double[][] {{80.0, 80.0, 80.0, 80.0}};
    chart.setCurves(chartConditions, speed, flow, head, eff);
    double[] surgeFlow = new double[] {900.0, 1200.0, 1500.0, 1800.0};
    double[] surgeHead = new double[] {85.0, 75.0, 65.0, 55.0};
    chart.getSurgeCurve().setCurve(chartConditions, surgeFlow, surgeHead);
    double[] stoneFlow = new double[] {2500.0, 2600.0, 2700.0, 2800.0};
    double[] stoneHead = new double[] {80.0, 70.0, 60.0, 50.0};
    chart.getStoneWallCurve().setCurve(chartConditions, stoneFlow, stoneHead);
    comp.setCompressorChart(chart);

    double headInside = chart.getPolytropicHead(1700.0, 1000.0);
    assertTrue(comp.isWithinOperatingEnvelope(1700.0, headInside));
    assertFalse(comp.isWithinOperatingEnvelope(1100.0, 70.0));
    assertFalse(comp.isWithinOperatingEnvelope(3000.0, 60.0));
  }

  @Test
  public void testOperatingEnvelopeSpeedLimits() {
    SystemInterface gas = new SystemSrkEos(298.15, 50.0);
    gas.addComponent("methane", 100.0);
    gas.setMixingRule(2);
    Stream inlet = new Stream("inlet", gas);
    Compressor comp = new Compressor("comp", inlet);

    CompressorChart chart = new CompressorChart();
    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    double[] speed = new double[] {1000.0, 2000.0};
    double[][] flow = new double[][] {{1000.0, 1500.0, 2000.0}, {1000.0, 1500.0, 2000.0}};
    double[][] head = new double[][] {{80.0, 70.0, 60.0}, {320.0, 280.0, 240.0}};
    double[][] eff = new double[][] {{80.0, 80.0, 80.0}, {80.0, 80.0, 80.0}};
    chart.setCurves(chartConditions, speed, flow, head, eff);
    double[] surgeFlow = new double[] {0.0, 1.0, 2.0, 3.0};
    double[] surgeHead = new double[] {0.0, 10.0, 20.0, 30.0};
    chart.getSurgeCurve().setCurve(chartConditions, surgeFlow, surgeHead);
    double[] stoneFlow = new double[] {10000.0, 10001.0, 10002.0, 10003.0};
    double[] stoneHead = new double[] {0.0, 10.0, 20.0, 30.0};
    chart.getStoneWallCurve().setCurve(chartConditions, stoneFlow, stoneHead);
    comp.setCompressorChart(chart);

    double headMid = chart.getPolytropicHead(1500.0, 1500.0);
    assertTrue(comp.isWithinOperatingEnvelope(1500.0, headMid));

    double headBelow = chart.getPolytropicHead(1500.0, 500.0);
    assertFalse(comp.isWithinOperatingEnvelope(1500.0, headBelow));
    assertTrue(comp.isWithinOperatingEnvelope(1500.0, headBelow, false, true));
    assertTrue(comp.isWithinOperatingEnvelope(1500.0, headBelow, false));
    comp.setIncludeMinSpeedLimit(false);
    assertFalse(comp.isIncludeMinSpeedLimit());
    assertTrue(comp.isWithinOperatingEnvelope(1500.0, headBelow));
    comp.setIncludeMinSpeedLimit(true);

    double headAbove = chart.getPolytropicHead(1500.0, 2500.0);
    assertFalse(comp.isWithinOperatingEnvelope(1500.0, headAbove));
    assertTrue(comp.isWithinOperatingEnvelope(1500.0, headAbove, true, false));
    assertTrue(comp.isWithinOperatingEnvelope(1500.0, headAbove, false));
    comp.setIncludeMaxSpeedLimit(false);
    assertFalse(comp.isIncludeMaxSpeedLimit());
    assertTrue(comp.isWithinOperatingEnvelope(1500.0, headAbove));
  }
}
