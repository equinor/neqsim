package neqsim.process.equipment.pump;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for verifying pump density correction.
 *
 * <p>
 * Pump curves are typically measured with water at standard conditions (~998 kg/m³). When pumping
 * fluids with different densities, the head must be corrected:
 * </p>
 * <p>
 * H_actual = H_chart × (ρ_chart / ρ_actual)
 * </p>
 *
 * @author NeqSim
 */
public class PumpDensityCorrectionTest extends neqsim.NeqSimTest {
  private PumpChart pumpChart;

  @BeforeEach
  void setUp() {
    pumpChart = new PumpChart();

    // Set up pump curve at reference speed
    double[] speed = new double[] {1000.0};
    double[][] flow = new double[][] {{10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0}};
    double[][] head = new double[][] {{120.0, 118.0, 115.0, 110.0, 103.0, 94.0, 83.0, 70.0}};
    double[][] efficiency = new double[][] {{60.0, 70.0, 78.0, 82.0, 81.0, 76.0, 68.0, 55.0}};

    // chartConditions: [refMW, refTemp, refPressure, refZ, refDensity]
    // Set reference density to 998 kg/m³ (water)
    double[] chartConditions = new double[] {18.0, 298.15, 1.0, 1.0, 998.0};

    pumpChart.setCurves(chartConditions, speed, flow, head, efficiency);
    pumpChart.setHeadUnit("meter");
  }

  @Test
  void testReferenceDensityFromChartConditions() {
    // Reference density should be set from chartConditions[4]
    Assertions.assertEquals(998.0, pumpChart.getReferenceDensity(), 0.01,
        "Reference density should be 998 kg/m³");
    Assertions.assertTrue(pumpChart.hasDensityCorrection(), "Density correction should be enabled");
  }

  @Test
  void testNoCorrectionWhenDensitySame() {
    // When actual density equals reference density, no correction
    double flow = 40.0;
    double speed = 1000.0;
    double actualDensity = 998.0; // Same as reference

    double chartHead = pumpChart.getHead(flow, speed);
    double correctedHead = pumpChart.getCorrectedHead(flow, speed, actualDensity);

    Assertions.assertEquals(chartHead, correctedHead, 0.01,
        "Head should be unchanged when density matches reference");
  }

  @Test
  void testCorrectionForLighterFluid() {
    // Lighter fluid (lower density) should result in higher head
    // H_actual = H_chart × (ρ_chart / ρ_actual)
    double flow = 40.0;
    double speed = 1000.0;
    double lighterDensity = 800.0; // e.g., light hydrocarbon

    double chartHead = pumpChart.getHead(flow, speed);
    double correctedHead = pumpChart.getCorrectedHead(flow, speed, lighterDensity);

    double expectedHead = chartHead * (998.0 / 800.0);
    Assertions.assertEquals(expectedHead, correctedHead, 0.01,
        "Head should increase for lighter fluid");
    Assertions.assertTrue(correctedHead > chartHead,
        "Corrected head should be higher for lighter fluid");
  }

  @Test
  void testCorrectionForHeavierFluid() {
    // Heavier fluid (higher density) should result in lower head
    double flow = 40.0;
    double speed = 1000.0;
    double heavierDensity = 1200.0; // e.g., brine

    double chartHead = pumpChart.getHead(flow, speed);
    double correctedHead = pumpChart.getCorrectedHead(flow, speed, heavierDensity);

    double expectedHead = chartHead * (998.0 / 1200.0);
    Assertions.assertEquals(expectedHead, correctedHead, 0.01,
        "Head should decrease for heavier fluid");
    Assertions.assertTrue(correctedHead < chartHead,
        "Corrected head should be lower for heavier fluid");
  }

  @Test
  void testBackwardCompatibilityWithoutDensity() {
    // Test that charts without density in chartConditions work as before
    PumpChart chartNoDensity = new PumpChart();

    double[] speed = new double[] {1000.0};
    double[][] flow = new double[][] {{10.0, 20.0, 30.0, 40.0, 50.0}};
    double[][] head = new double[][] {{120.0, 118.0, 115.0, 110.0, 103.0}};
    double[][] efficiency = new double[][] {{60.0, 70.0, 78.0, 82.0, 81.0}};

    // Only 4 elements - no density
    double[] chartConditions = new double[] {18.0, 298.15, 1.0, 1.0};

    chartNoDensity.setCurves(chartConditions, speed, flow, head, efficiency);
    chartNoDensity.setHeadUnit("meter");

    Assertions.assertFalse(chartNoDensity.hasDensityCorrection(),
        "Density correction should be disabled when not specified");
    Assertions.assertEquals(-1.0, chartNoDensity.getReferenceDensity(), 0.01,
        "Reference density should be -1.0 when not specified");

    // getCorrectedHead should return same as getHead
    double chartHead = chartNoDensity.getHead(40.0, 1000.0);
    double correctedHead = chartNoDensity.getCorrectedHead(40.0, 1000.0, 800.0);

    Assertions.assertEquals(chartHead, correctedHead, 0.01,
        "Head should be unchanged when density correction is disabled");
  }

  @Test
  void testSetReferenceDensityDirectly() {
    // Test setting reference density via setter method
    PumpChart chart = new PumpChart();

    double[] speed = new double[] {1000.0};
    double[][] flow = new double[][] {{10.0, 20.0, 30.0, 40.0}};
    double[][] head = new double[][] {{120.0, 118.0, 115.0, 110.0}};
    double[][] efficiency = new double[][] {{60.0, 70.0, 78.0, 82.0}};

    chart.setCurves(new double[] {}, speed, flow, head, efficiency);
    chart.setHeadUnit("meter");

    Assertions.assertFalse(chart.hasDensityCorrection(),
        "Density correction should initially be disabled");

    // Set reference density via setter
    chart.setReferenceDensity(998.0);

    Assertions.assertTrue(chart.hasDensityCorrection(),
        "Density correction should be enabled after setting");
    Assertions.assertEquals(998.0, chart.getReferenceDensity(), 0.01);

    // Disable by setting negative
    chart.setReferenceDensity(-1.0);
    Assertions.assertFalse(chart.hasDensityCorrection(),
        "Density correction should be disabled with negative value");
  }

  @Test
  void testPumpWithDensityCorrection() {
    // Integration test with Pump class
    SystemInterface lightFluid = new SystemSrkEos(298.15, 2.0);
    lightFluid.addComponent("n-hexane", 1.0);
    lightFluid.setTotalFlowRate(1.0, "kg/sec");
    lightFluid.init(0);
    lightFluid.initPhysicalProperties();

    Stream feedStream = new Stream("Feed", lightFluid);
    feedStream.run();

    Pump pump = new Pump("TestPump", feedStream);
    pump.setSpeed(1000.0);

    // Set pump chart with water reference density
    double[] speed = new double[] {1000.0};
    double[][] flow = new double[][] {{10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0}};
    double[][] head = new double[][] {{120.0, 118.0, 115.0, 110.0, 103.0, 94.0, 83.0, 70.0}};
    double[][] efficiency = new double[][] {{60.0, 70.0, 78.0, 82.0, 81.0, 76.0, 68.0, 55.0}};
    double[] chartConditions = new double[] {18.0, 298.15, 1.0, 1.0, 998.0};

    pump.getPumpChart().setCurves(chartConditions, speed, flow, head, efficiency);
    pump.getPumpChart().setHeadUnit("meter");
    pump.run();

    // Pump should run without errors and produce reasonable pressure rise
    double outletPressure = pump.getOutletPressure();
    Assertions.assertTrue(outletPressure > feedStream.getPressure(),
        "Outlet pressure should be higher than inlet");
  }
}
