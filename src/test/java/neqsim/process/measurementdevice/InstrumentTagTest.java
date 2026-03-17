package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the instrument tag and field data integration feature.
 *
 * @author ESOL
 */
class InstrumentTagTest {
  private SystemInterface fluid;
  private Stream feed;

  @BeforeEach
  void setUp() {
    fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
  }

  @Test
  void testDefaultTagRole() {
    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    assertEquals(InstrumentTagRole.VIRTUAL, pt.getTagRole());
    assertEquals("", pt.getTag());
    assertFalse(pt.hasFieldValue());
    assertTrue(Double.isNaN(pt.getFieldValue()));
  }

  @Test
  void testSetTagAndRole() {
    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setTag("20-PT-101");
    pt.setTagRole(InstrumentTagRole.INPUT);

    assertEquals("20-PT-101", pt.getTag());
    assertEquals(InstrumentTagRole.INPUT, pt.getTagRole());
  }

  @Test
  void testFieldValueSetAndClear() {
    TemperatureTransmitter tt = new TemperatureTransmitter("TT-201", feed);
    tt.setTag("20-TT-201");
    tt.setTagRole(InstrumentTagRole.BENCHMARK);

    assertFalse(tt.hasFieldValue());
    assertTrue(Double.isNaN(tt.getFieldValue()));

    tt.setFieldValue(300.0);
    assertTrue(tt.hasFieldValue());
    assertEquals(300.0, tt.getFieldValue(), 1e-10);

    // Clear by setting NaN
    tt.setFieldValue(Double.NaN);
    assertFalse(tt.hasFieldValue());
  }

  @Test
  void testDeviationBenchmark() {
    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setUnit("bara");
    pt.setTag("20-PT-101");
    pt.setTagRole(InstrumentTagRole.BENCHMARK);

    // No field value => deviation is NaN
    assertTrue(Double.isNaN(pt.getDeviation()));
    assertTrue(Double.isNaN(pt.getRelativeDeviation()));

    // Set field value and check deviation
    double modelValue = pt.getMeasuredValue();
    pt.setFieldValue(modelValue + 5.0);
    double deviation = pt.getDeviation();
    assertEquals(-5.0, deviation, 0.1);
  }

  @Test
  void testInputApplyFieldValuePressure() {
    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setUnit("bara");
    pt.setTag("20-PT-101");
    pt.setTagRole(InstrumentTagRole.INPUT);
    pt.setFieldValue(80.0);

    // Before apply, stream has original pressure
    double originalPressure = feed.getPressure();
    assertEquals(60.0, originalPressure, 0.1);

    // Apply field value
    pt.applyFieldValue();

    // Stream pressure should now be 80.0 bara
    assertEquals(80.0, feed.getPressure(), 0.1);
  }

  @Test
  void testInputApplyFieldValueTemperature() {
    TemperatureTransmitter tt = new TemperatureTransmitter("TT-201", feed);
    tt.setUnit("K");
    tt.setTag("20-TT-201");
    tt.setTagRole(InstrumentTagRole.INPUT);
    tt.setFieldValue(350.0);

    tt.applyFieldValue();
    assertEquals(350.0, feed.getTemperature(), 0.1);
  }

  @Test
  void testInputApplyFieldValueFlow() {
    VolumeFlowTransmitter ft = new VolumeFlowTransmitter("FT-301", feed);
    ft.setUnit("kg/hr");
    ft.setTag("20-FT-301");
    ft.setTagRole(InstrumentTagRole.INPUT);
    ft.setFieldValue(20000.0);

    ft.applyFieldValue();
    assertEquals(20000.0, feed.getFlowRate("kg/hr"), 1.0);
  }

  @Test
  void testApplyFieldValueNoOpForBenchmark() {
    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setUnit("bara");
    pt.setTag("20-PT-101");
    pt.setTagRole(InstrumentTagRole.BENCHMARK);
    pt.setFieldValue(80.0);

    double originalPressure = feed.getPressure();
    pt.applyFieldValue(); // Should not change anything for BENCHMARK role
    assertEquals(originalPressure, feed.getPressure(), 0.001);
  }

  @Test
  void testProcessSystemTagLookup() {
    ProcessSystem process = new ProcessSystem();

    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setTag("20-PT-101");
    pt.setTagRole(InstrumentTagRole.INPUT);

    TemperatureTransmitter tt = new TemperatureTransmitter("TT-201", feed);
    tt.setTag("20-TT-201");
    tt.setTagRole(InstrumentTagRole.BENCHMARK);

    PressureTransmitter ptVirtual = new PressureTransmitter("PT-102", feed);
    ptVirtual.setTag("20-PT-102");
    ptVirtual.setTagRole(InstrumentTagRole.VIRTUAL);

    process.add(pt);
    process.add(tt);
    process.add(ptVirtual);

    // Lookup by tag
    assertNotNull(process.getMeasurementDeviceByTag("20-PT-101"));
    assertEquals("PT-101", process.getMeasurementDeviceByTag("20-PT-101").getName());
    assertNull(process.getMeasurementDeviceByTag("nonexistent"));

    // Filter by role
    List<MeasurementDeviceInterface> inputs =
        process.getMeasurementDevicesByRole(InstrumentTagRole.INPUT);
    assertEquals(1, inputs.size());
    assertEquals("PT-101", inputs.get(0).getName());

    List<MeasurementDeviceInterface> benchmarks =
        process.getMeasurementDevicesByRole(InstrumentTagRole.BENCHMARK);
    assertEquals(1, benchmarks.size());

    List<MeasurementDeviceInterface> virtuals =
        process.getMeasurementDevicesByRole(InstrumentTagRole.VIRTUAL);
    assertEquals(1, virtuals.size());
  }

  @Test
  void testProcessSystemSetFieldData() {
    ProcessSystem process = new ProcessSystem();

    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setUnit("bara");
    pt.setTag("20-PT-101");
    pt.setTagRole(InstrumentTagRole.INPUT);

    TemperatureTransmitter tt = new TemperatureTransmitter("TT-201", feed);
    tt.setUnit("K");
    tt.setTag("20-TT-201");
    tt.setTagRole(InstrumentTagRole.BENCHMARK);

    process.add(pt);
    process.add(tt);

    // Bulk set field data
    Map<String, Double> fieldData = new HashMap<String, Double>();
    fieldData.put("20-PT-101", 75.0);
    fieldData.put("20-TT-201", 310.0);
    fieldData.put("NONEXISTENT", 999.0); // Should be silently ignored
    process.setFieldData(fieldData);

    assertTrue(pt.hasFieldValue());
    assertEquals(75.0, pt.getFieldValue(), 1e-10);
    assertTrue(tt.hasFieldValue());
    assertEquals(310.0, tt.getFieldValue(), 1e-10);
  }

  @Test
  void testProcessSystemApplyFieldInputs() {
    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setUnit("bara");
    pt.setTag("20-PT-101");
    pt.setTagRole(InstrumentTagRole.INPUT);

    TemperatureTransmitter tt = new TemperatureTransmitter("TT-201", feed);
    tt.setUnit("K");
    tt.setTag("20-TT-201");
    tt.setTagRole(InstrumentTagRole.BENCHMARK); // Should NOT be applied

    process.add(pt);
    process.add(tt);

    Map<String, Double> fieldData = new HashMap<String, Double>();
    fieldData.put("20-PT-101", 80.0);
    fieldData.put("20-TT-201", 350.0);
    process.setFieldData(fieldData);

    process.applyFieldInputs();

    // INPUT pressure should have been applied
    assertEquals(80.0, feed.getPressure(), 0.1);
    // BENCHMARK temperature should NOT have been applied
    assertEquals(273.15 + 25.0, feed.getTemperature(), 0.1);
  }

  @Test
  void testBenchmarkDeviations() {
    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setUnit("bara");
    pt.setTag("20-PT-101");
    pt.setTagRole(InstrumentTagRole.BENCHMARK);

    TemperatureTransmitter tt = new TemperatureTransmitter("TT-201", feed);
    tt.setUnit("K");
    tt.setTag("20-TT-201");
    tt.setTagRole(InstrumentTagRole.BENCHMARK);

    process.add(pt);
    process.add(tt);

    pt.setFieldValue(55.0); // model = 60, field = 55 => deviation = +5
    tt.setFieldValue(300.0); // model = 298.15, field = 300 => deviation ≈ -1.85

    Map<String, Double> deviations = process.getBenchmarkDeviations();
    assertEquals(2, deviations.size());
    assertEquals(5.0, deviations.get("20-PT-101"), 0.1);
    assertTrue(deviations.get("20-TT-201") < 0);
  }

  @Test
  void testNullSafety() {
    PressureTransmitter pt = new PressureTransmitter("PT-101", feed);
    pt.setTag(null); // Should default to empty
    assertEquals("", pt.getTag());

    pt.setTagRole(null); // Should default to VIRTUAL
    assertEquals(InstrumentTagRole.VIRTUAL, pt.getTagRole());
  }
}
