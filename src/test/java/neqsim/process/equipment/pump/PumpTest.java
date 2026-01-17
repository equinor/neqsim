package neqsim.process.equipment.pump;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;

/**
 * <p>
 * PumpTest class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class PumpTest extends neqsim.NeqSimTest {
  @Test
  void testRun() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 20.0, 10.00);
    feedGas.addComponent("water", 1.0);

    Stream feedGasStream = new Stream("feed fluid", feedGas);
    feedGasStream.setFlowRate(4000.0 * 1000, "kg/hr");
    feedGasStream.setTemperature(20.0, "C");
    feedGasStream.setPressure(1.0, "bara");
    feedGasStream.run();

    Pump pump1 = new Pump("pump1", feedGasStream);
    pump1.setOutletPressure(12.6);
    pump1.calculateAsCompressor(false);

    pump1.run();
    double[] chartConditions = new double[] {0.3, 1.0, 1.0, 1.0};
    double[] speed = new double[] {350.0, 1000.0};
    double[][] flow = new double[][] {
        {2789.1285, 3174.0375, 3689.2288, 4179.4503, 4570.2768, 4954.7728, 5246.0329, 5661.0331},
        {2571.1753, 2943.7254, 3440.2675, 3837.4448, 4253.0898, 4668.6643, 4997.1926, 5387.4952}};
    double[][] head =
        new double[][] {{80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728},
            {72.2122, 71.8369, 68.9009, 65.8341, 60.7167, 54.702, 47.2749, 35.7471},
            {65.1576, 64.5253, 62.6118, 59.1619, 54.0455, 47.0059, 39.195, 31.6387},
            {58.6154, 56.9627, 54.6647, 50.4462, 44.4322, 38.4144, 32.9084, 28.8109},
            {52.3295, 51.0573, 49.5283, 46.3326, 42.3685, 37.2502, 31.4884, 25.598},
            {40.6578, 39.6416, 37.6008, 34.6603, 30.9503, 27.1116, 23.2713, 20.4546},
            {35.2705, 34.6359, 32.7228, 31.0645, 27.0985, 22.7482, 18.0113},
            {32.192, 31.1756, 29.1329, 26.833, 23.8909, 21.3324, 18.7726, 16.3403},};
    double[][] polyEff = new double[][] {
        {77.2452238409573, 79.4154186459363, 80.737960012489, 80.5229826589649, 79.2210931638144,
            75.4719133864634, 69.6034181197298, 58.7322388482707},
        {77.0107837113504, 79.3069974136389, 80.8941189021135, 80.7190194665918, 79.5313242980328,
            75.5912622896367, 69.6846136362097, 60.0043057990909},
        {77.0043065299874, 79.1690958847856, 80.8038169975675, 80.6543975614197, 78.8532389102705,
            73.6664774270613, 66.2735600426727, 57.671664571658},
        {77.0716623789093, 80.4629750233093, 81.1390811169072, 79.6374242667478, 75.380928428817,
            69.5332969549779, 63.7997587622339, 58.8120614497758},
        {76.9705872525642, 79.8335492585324, 80.9468133671171, 80.5806471927835, 78.0462158225426,
            73.0403707523258, 66.5572286338589, 59.8624822515064},
        {77.5063036680357, 80.2056198362559, 81.0339108025933, 79.6085962687939, 76.3814534404405,
            70.8027503005902, 64.6437367160571, 60.5299349982342},
        {77.8175271586685, 80.065165942218, 81.0631362122632, 79.8955051771299, 76.1983240929369,
            69.289982774309, 60.8567149372229},
        {78.0924334304045, 80.9353551568667, 80.7904437766234, 78.8639325223295, 75.2170936751143,
            70.3105081673411, 65.5507568533569, 61.0391468300337}};
    pump1.getPumpChart().setCurves(chartConditions, speed, flow, head, polyEff);
    pump1.getPumpChart().setHeadUnit("meter");
    pump1.setSpeed(500);
    pump1.run();
  }

  @Test
  void testSimplePumpCurve() {
    neqsim.thermo.system.SystemInterface feedDecane =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 20.0, 10.00);
    feedDecane.addComponent("n-pentane", 0.5, "kg/sec");
    feedDecane.addComponent("n-hexane", 0.5, "kg/sec");

    Stream feedC10Stream = new Stream("feed decane", feedDecane);
    feedC10Stream.setFlowRate(30000, "kg/hr");
    feedC10Stream.setTemperature(30.0, "C");
    feedC10Stream.setPressure(1.0, "bara");
    feedC10Stream.run();

    System.out.println("flow " + feedC10Stream.getFlowRate("m3/hr"));
    double[] chartConditions = new double[] {};
    double[] speed = new double[] {500.0};
    double[][] flow =
        new double[][] {{27.1285, 31.0375, 36.2288, 41.4503, 45.2768, 49.7728, 52.0329, 56.0331}};
    double[][] head =
        new double[][] {{80.0375, 78.8934, 76.2142, 71.8678, 67.0062, 60.6061, 53.0499, 39.728}};
    double[][] polyEff = new double[][] {{77.2452238409573, 79.4154186459363, 80.737960012489,
        80.5229826589649, 79.2210931638144, 75.4719133864634, 69.6034181197298, 58.7322388482707}};

    Pump pump1 = new Pump("pump1", feedC10Stream);
    pump1.getPumpChart().setCurves(chartConditions, speed, flow, head, polyEff);
    pump1.getPumpChart().setHeadUnit("meter");
    pump1.setSpeed(500);
    pump1.run();

    // Corrected expected value based on proper head calculation: ΔP = ρ·g·H
    // With density ~630-650 kg/m³ and head ~72 m, pressure rise is ~4.5 bar
    Assertions.assertEquals(5.02, pump1.getOutletPressure(), 0.10);
  }

  @Test
  void testSeparatorandPump() {
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 20.0, 10.00);
    feedGas.addComponent("methane", 1.0);
    feedGas.addComponent("water", 1.0e-5);

    Stream feedGasStream = new Stream("feed fluid", feedGas);
    feedGasStream.setFlowRate(4000.0, "kg/hr");
    feedGasStream.setTemperature(20.0, "C");
    feedGasStream.setPressure(1.5, "bara");
    feedGasStream.run();

    Separator sep = new Separator("separator", feedGasStream);
    sep.run();

    Pump pump1 = new Pump("pump1", sep.getLiquidOutStream());
    pump1.setOutletPressure(12.6);
    pump1.setMinimumFlow(1e-20);
    pump1.run();

    Stream outStream = feedGasStream.clone();

    Recycle res1 = new Recycle("recycle");
    res1.addStream(pump1.getOutletStream());
    res1.setOutletStream(outStream);
    res1.setTolerance(1e-5);
    res1.setMinimumFlow(1e-20);
    res1.run();

    Assertions.assertEquals(0.0, pump1.getOutletStream().getFlowRate("kg/sec"), 1e-20);
  }

  @Test
  void testPumpAutoSizing() {
    // Create liquid feed stream
    neqsim.thermo.system.SystemInterface feedLiquid =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 5.0);
    feedLiquid.addComponent("water", 1.0);

    Stream feedStream = new Stream("feed", feedLiquid);
    feedStream.setFlowRate(100000.0, "kg/hr"); // Use mass flow rate
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(2.0, "bara");
    feedStream.run();

    // Create pump and run
    Pump pump = new Pump("TestPump", feedStream);
    pump.setOutletPressure(10.0, "bara");
    pump.setIsentropicEfficiency(0.75);
    pump.run();

    // Auto-size with 20% safety factor
    Assertions.assertFalse(pump.isAutoSized());
    pump.autoSize(1.2);
    Assertions.assertTrue(pump.isAutoSized());

    // Check sizing report
    String report = pump.getSizingReport();
    Assertions.assertTrue(report.contains("Pump Auto-Sizing Report"));
    Assertions.assertTrue(report.contains("Auto-sized: true"));

    // Check JSON report
    String jsonReport = pump.getSizingReportJson();
    Assertions.assertTrue(jsonReport.contains("\"autoSized\": true"));
    Assertions.assertTrue(jsonReport.contains("\"equipmentType\": \"Pump\""));

    // Check power is calculated
    Assertions.assertTrue(pump.getPower("kW") > 0);
  }

  @Test
  void testPumpCapacityConstraints() {
    // Create liquid feed stream
    neqsim.thermo.system.SystemInterface feedLiquid =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 5.0);
    feedLiquid.addComponent("water", 1.0);

    Stream feedStream = new Stream("feed", feedLiquid);
    feedStream.setFlowRate(100000.0, "kg/hr"); // Use mass flow rate
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(2.0, "bara");
    feedStream.run();

    // Create pump
    Pump pump = new Pump("TestPump", feedStream);
    pump.setOutletPressure(10.0, "bara");
    pump.setIsentropicEfficiency(0.75);
    pump.getMechanicalDesign().setMaxDesignPower(50000.0); // 50 kW limit
    pump.run();

    // Check capacity constraints
    java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> constraints =
        pump.getCapacityConstraints();
    Assertions.assertFalse(constraints.isEmpty());
    Assertions.assertTrue(constraints.containsKey("power"));

    // Get utilization
    double utilization = pump.getMaxUtilization();
    Assertions.assertTrue(utilization >= 0);
  }
}
