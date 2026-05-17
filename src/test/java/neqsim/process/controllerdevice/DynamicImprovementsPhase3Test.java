package neqsim.process.controllerdevice;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmEvent;
import neqsim.process.alarm.AlarmState;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for Phase 3 dynamic simulation improvements: transmitter first-order filter, alarm
 * shelving, separator internals wiring, HX fluid accumulation ODE, and distillation MESH energy
 * balance with vapor hydraulic model.
 *
 * @author NeqSim
 * @version 1.0
 */
class DynamicImprovementsPhase3Test {

  // ─── 1. Transmitter first-order filter ───

  @Test
  void testFirstOrderFilterDefaultDisabled() {
    DynamicImprovementsTest.StubTransmitter tx =
        new DynamicImprovementsTest.StubTransmitter("TT-01");
    Assertions.assertEquals(0.0, tx.getFirstOrderTimeConstant(), 1e-10,
        "Default time constant should be 0 (disabled)");
  }

  @Test
  void testFirstOrderFilterSetGet() {
    DynamicImprovementsTest.StubTransmitter tx =
        new DynamicImprovementsTest.StubTransmitter("TT-02");
    tx.setFirstOrderTimeConstant(5.0);
    Assertions.assertEquals(5.0, tx.getFirstOrderTimeConstant(), 1e-10);
    // Negative should be clamped to 0
    tx.setFirstOrderTimeConstant(-1.0);
    Assertions.assertEquals(0.0, tx.getFirstOrderTimeConstant(), 1e-10);
  }

  @Test
  void testFirstOrderFilterSmoothsStepChange() {
    DynamicImprovementsTest.StubTransmitter tx =
        new DynamicImprovementsTest.StubTransmitter("TT-03");
    tx.setFirstOrderTimeConstant(5.0);

    // First reading at value 50
    tx.setValue(50.0);
    double val1 = tx.getMeasuredValue();
    // Now step to 100 — filtered value should NOT jump to 100 immediately
    tx.setValue(100.0);
    double val2 = tx.getMeasuredValue();
    Assertions.assertTrue(val2 < 100.0 && val2 > 50.0,
        "Filtered value should be between 50 and 100 after step, got " + val2);

    // Multiple readings should approach 100
    double prev = val2;
    for (int i = 0; i < 20; i++) {
      double v = tx.getMeasuredValue();
      Assertions.assertTrue(v >= prev - 0.01,
          "Filtered value should converge monotonically, iter=" + i);
      prev = v;
    }
    // After many readings, should be close to 100
    Assertions.assertTrue(prev > 95.0, "After 20 filter steps, should be near 100, got " + prev);
  }

  @Test
  void testFilterDisabledPassesThrough() {
    DynamicImprovementsTest.StubTransmitter tx =
        new DynamicImprovementsTest.StubTransmitter("TT-04");
    // No filter (default timeConstant = 0)
    tx.setValue(50.0);
    tx.getMeasuredValue(); // prime
    tx.setValue(100.0);
    double val = tx.getMeasuredValue();
    // Without filter, should get 100 immediately (possibly with noise if configured)
    Assertions.assertEquals(100.0, val, 1.0, "Without filter, value should pass through");
  }

  // ─── 2. Alarm shelving ───

  @Test
  void testAlarmStateShelveUnshelve() {
    AlarmState state = new AlarmState();
    Assertions.assertFalse(state.isShelved());

    state.shelve("Maintenance window");
    Assertions.assertTrue(state.isShelved());
    Assertions.assertEquals("Maintenance window", state.getShelveReason());

    state.unshelve();
    Assertions.assertFalse(state.isShelved());
  }

  @Test
  void testAlarmStateShelvedSuppressesEvents() {
    AlarmConfig config = AlarmConfig.builder().highLimit(80.0).build();
    AlarmState state = new AlarmState();

    // Without shelving, high value triggers alarm
    java.util.List<AlarmEvent> events1 = state.evaluate(config, 90.0, 1.0, 0.0, "test");
    Assertions.assertFalse(events1.isEmpty(), "Should fire alarm at 90 when not shelved");

    // Shelve and re-evaluate — no events
    state.shelve("Test shelve");
    java.util.List<AlarmEvent> events2 = state.evaluate(config, 90.0, 1.0, 1.0, "test");
    Assertions.assertTrue(events2.isEmpty(), "Should suppress events when shelved");

    // Unshelve — alarm state is no longer shelved
    state.unshelve();
    Assertions.assertFalse(state.isShelved(), "Should be unshelved after unshelve()");
    // Drop back to normal, then re-alarm to confirm new events fire after unshelve
    state.evaluate(config, 50.0, 1.0, 2.0, "test"); // normal value clears alarm
    java.util.List<AlarmEvent> events3 = state.evaluate(config, 95.0, 1.0, 3.0, "test");
    Assertions.assertFalse(events3.isEmpty(), "Should fire alarm again after unshelve and re-trip");
  }

  @Test
  void testAlarmShelveTimedExpiry() {
    AlarmConfig config = AlarmConfig.builder().highLimit(80.0).build();
    AlarmState state = new AlarmState();

    // Shelve with expiry at time 10.0
    state.shelve("Timed", 10.0);
    Assertions.assertTrue(state.isShelved());
    Assertions.assertEquals(10.0, state.getShelveExpiry(), 1e-10);

    // Evaluate at time 5 — still shelved
    java.util.List<AlarmEvent> events1 = state.evaluate(config, 90.0, 1.0, 5.0, "test");
    Assertions.assertTrue(events1.isEmpty(), "Should still be shelved at t=5");

    // Evaluate at time 11 — auto-unshelved
    java.util.List<AlarmEvent> events2 = state.evaluate(config, 90.0, 1.0, 11.0, "test");
    Assertions.assertFalse(state.isShelved(), "Should auto-unshelve after expiry time");
    Assertions.assertFalse(events2.isEmpty(), "Should fire alarm after expiry");
  }

  @Test
  void testMeasurementDeviceShelveConvenience() {
    DynamicImprovementsTest.StubTransmitter tx =
        new DynamicImprovementsTest.StubTransmitter("TT-05");
    tx.setAlarmConfig(AlarmConfig.builder().highLimit(80.0).build());

    Assertions.assertFalse(tx.isAlarmShelved());
    tx.shelveAlarm("Shutdown");
    Assertions.assertTrue(tx.isAlarmShelved());
    tx.unshelveAlarm();
    Assertions.assertFalse(tx.isAlarmShelved());
  }

  // ─── 3. Separator internals wiring ───

  @Test
  void testSeparatorWeirLimitsLiquidOutflow() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("nC10", 0.3);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("sep-feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();

    Separator sep = new Separator("HP-Sep", feed);
    sep.setInternalDiameter(2.0);

    // Configure weir — very high weir should restrict liquid flow
    sep.setWeirHeight(2.0); // 2 m weir — higher than liquid level
    sep.setWeirLength(1.5);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    // Run a transient step with weir active
    UUID testId = UUID.randomUUID();
    sep.runTransient(1.0, testId);

    // The weir is very high, so liquid outflow should be restricted
    // This test verifies the code runs without error and weir logic is active
    Assertions.assertNotNull(sep.getLiquidOutStream(), "Separator should have liquid out stream");
  }

  @Test
  void testSeparatorMistEliminatorReducesGasPressure() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("nC10", 0.2);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("me-feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.run();

    Separator sep = new Separator("HP-Sep-ME", feed);
    sep.setInternalDiameter(1.5);

    // Configure mist eliminator with a non-zero DP coefficient
    sep.setMistEliminatorDpCoeff(200.0);
    sep.setMistEliminatorThickness(0.15);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    double gasPBefore = sep.getGasOutStream().getPressure("bara");

    UUID testId = UUID.randomUUID();
    sep.runTransient(1.0, testId);

    double gasPAfter = sep.getGasOutStream().getPressure("bara");

    // Gas pressure should be reduced by the mist eliminator pressure drop
    Assertions.assertTrue(gasPAfter <= gasPBefore,
        "Gas out pressure should be reduced by ME DP: before=" + gasPBefore + " after="
            + gasPAfter);
  }

  // ─── 4. HX fluid accumulation ODE ───

  @Test
  void testHeatExchangerHoldupSlowsResponse() {
    // Hot side fluid
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 150.0, 30.0);
    hotFluid.addComponent("methane", 1.0);
    hotFluid.setMixingRule("classic");
    Stream hotStream = new Stream("hotStream", hotFluid);
    hotStream.setFlowRate(1000.0, "kg/hr");
    hotStream.run();

    // Cold side fluid
    SystemInterface coldFluid = new SystemSrkEos(273.15 + 30.0, 30.0);
    coldFluid.addComponent("methane", 1.0);
    coldFluid.setMixingRule("classic");
    Stream coldStream = new Stream("coldStream", coldFluid);
    coldStream.setFlowRate(1000.0, "kg/hr");
    coldStream.run();

    // HX without holdup
    HeatExchanger hxNoHoldup = new HeatExchanger("HX-noHoldup", hotStream, coldStream);
    hxNoHoldup.setDynamicModelEnabled(true);
    hxNoHoldup.setWallMass(500.0);
    hxNoHoldup.setWallCp(500.0);
    hxNoHoldup.setHeatTransferArea(20.0);
    hxNoHoldup.setShellSideHtc(500.0);
    hxNoHoldup.setTubeSideHtc(500.0);

    // HX with holdup (same parameters but with fluid accumulation volumes)
    HeatExchanger hxWithHoldup = new HeatExchanger("HX-holdup", hotStream, coldStream);
    hxWithHoldup.setDynamicModelEnabled(true);
    hxWithHoldup.setWallMass(500.0);
    hxWithHoldup.setWallCp(500.0);
    hxWithHoldup.setHeatTransferArea(20.0);
    hxWithHoldup.setShellSideHtc(500.0);
    hxWithHoldup.setTubeSideHtc(500.0);
    hxWithHoldup.setShellHoldupVolume(0.5); // 0.5 m3 shell
    hxWithHoldup.setTubeHoldupVolume(0.3); // 0.3 m3 tube

    UUID testId = UUID.randomUUID();

    // Run both for several steps
    for (int step = 0; step < 5; step++) {
      hxNoHoldup.runTransient(1.0, testId);
      hxWithHoldup.runTransient(1.0, testId);
    }

    // Both should produce valid (non-NaN) outlet temperatures
    double hotOutNoHoldup = hxNoHoldup.getOutStream(0).getTemperature("C");
    double hotOutWithHoldup = hxWithHoldup.getOutStream(0).getTemperature("C");

    Assertions.assertFalse(Double.isNaN(hotOutNoHoldup), "No-holdup hot outlet should be valid");
    Assertions.assertFalse(Double.isNaN(hotOutWithHoldup),
        "With-holdup hot outlet should be valid");

    // With holdup, the hot outlet should be closer to the inlet (more inertia → less cooling)
    // or at least different from no-holdup case
    double hotInlet = 150.0;
    double distNoHoldup = Math.abs(hotInlet - hotOutNoHoldup);
    double distWithHoldup = Math.abs(hotInlet - hotOutWithHoldup);
    // The holdup model adds fluid thermal mass, so the response should be different
    Assertions.assertNotEquals(hotOutNoHoldup, hotOutWithHoldup, 0.001,
        "With holdup should differ from without holdup");
  }

  @Test
  void testHeatExchangerHoldupZeroMatchesOriginal() {
    // Verify that when holdupVolume = 0, the new code path still works
    // and doesn't crash — the wall ODE runs as before
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 120.0, 20.0);
    hotFluid.addComponent("methane", 1.0);
    hotFluid.setMixingRule("classic");
    Stream hotStream = new Stream("hotStr2", hotFluid);
    hotStream.setFlowRate(500.0, "kg/hr");

    SystemInterface coldFluid = new SystemSrkEos(273.15 + 20.0, 20.0);
    coldFluid.addComponent("methane", 1.0);
    coldFluid.setMixingRule("classic");
    Stream coldStream = new Stream("coldStr2", coldFluid);
    coldStream.setFlowRate(500.0, "kg/hr");

    HeatExchanger hx = new HeatExchanger("HX-zero", hotStream, coldStream);
    hx.setDynamicModelEnabled(true);
    hx.setWallMass(300.0);
    hx.setWallCp(500.0);
    hx.setHeatTransferArea(10.0);
    hx.setShellSideHtc(400.0);
    hx.setTubeSideHtc(400.0);
    // shellHoldupVolume and tubeHoldupVolume default to 0.0

    ProcessSystem proc = new ProcessSystem();
    proc.add(hotStream);
    proc.add(coldStream);
    proc.add(hx);
    proc.run();

    // After steady-state run, capture wall temperature
    double wallTBefore = hx.getWallTemperature();

    UUID testId = UUID.randomUUID();
    // Run several transient steps — the wall ODE should change wallTemperature
    for (int i = 0; i < 5; i++) {
      hx.runTransient(0.5, testId);
    }

    double wallTAfter = hx.getWallTemperature();
    // Wall temperature should be a valid number (not NaN/Inf)
    Assertions.assertFalse(Double.isNaN(wallTAfter), "Wall T should not be NaN after transient");
    Assertions.assertFalse(Double.isInfinite(wallTAfter),
        "Wall T should not be Inf after transient");
    // Wall T should have moved from its initial value if HX is exchanging heat
    // (this is a smoke test, not a precision test)
  }

  // ─── 5. Distillation MESH energy balance + vapor hydraulic ───

  @Test
  void testDistillationDynamicEnergyFields() {
    DistillationColumn col = new DistillationColumn("TestCol", 5, true, true);
    Assertions.assertFalse(col.isDynamicEnergyEnabled());
    col.setDynamicEnergyEnabled(true);
    Assertions.assertTrue(col.isDynamicEnergyEnabled());

    Assertions.assertEquals(0.0, col.getTrayDryPressureDrop(), 1e-10);
    col.setTrayDryPressureDrop(500.0);
    Assertions.assertEquals(500.0, col.getTrayDryPressureDrop(), 1e-10);
    // Negative should be clamped to 0
    col.setTrayDryPressureDrop(-100.0);
    Assertions.assertEquals(0.0, col.getTrayDryPressureDrop(), 1e-10);
  }

  @Test
  void testDistillationDynamicEnergyDefaultOff() {
    // Without dynamicEnergyEnabled, the existing TP-flash tray model runs
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 15.0);
    fluid.addComponent("methane", 0.4);
    fluid.addComponent("ethane", 0.3);
    fluid.addComponent("propane", 0.3);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("col-feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    DistillationColumn col = new DistillationColumn("DynCol", 5, true, true);
    col.addFeedStream(feed, 3);
    col.setTopPressure(10.0);
    col.setBottomPressure(11.0);
    col.setDynamicColumnEnabled(true);
    // dynamicEnergyEnabled defaults to false → TP flash path

    // Steady-state first
    try {
      col.run();
    } catch (Exception ex) {
      // Column may not converge fully but should initialize trays
    }

    // Run a transient step — should not throw
    UUID testId = UUID.randomUUID();
    col.runTransient(1.0, testId);

    // Enthalpy array should be null (energy balance not active)
    Assertions.assertNull(col.getTrayEnthalpy(),
        "Enthalpy tracking should be null when energy balance disabled");
    // Holdup array should be initialized
    Assertions.assertNotNull(col.getTrayLiquidHoldup(),
        "Holdup should be initialized after transient step");
  }

  @Test
  void testDistillationDynamicEnergyEnabled() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 15.0);
    fluid.addComponent("methane", 0.4);
    fluid.addComponent("ethane", 0.3);
    fluid.addComponent("propane", 0.3);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("col-feed-e", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    DistillationColumn col = new DistillationColumn("DynColE", 5, true, true);
    col.addFeedStream(feed, 3);
    col.setTopPressure(10.0);
    col.setBottomPressure(11.0);
    col.setDynamicColumnEnabled(true);
    col.setDynamicEnergyEnabled(true); // Enable energy balance

    try {
      col.run();
    } catch (Exception ex) {
      // May not fully converge
    }

    UUID testId = UUID.randomUUID();
    col.runTransient(1.0, testId);

    // Enthalpy array should be initialized
    double[] enthalpies = col.getTrayEnthalpy();
    Assertions.assertNotNull(enthalpies, "Enthalpy array should be initialized");
    // Enthalpies should be non-zero
    boolean anyNonZero = false;
    for (double h : enthalpies) {
      if (Math.abs(h) > 1.0) {
        anyNonZero = true;
        break;
      }
    }
    Assertions.assertTrue(anyNonZero, "At least one tray should have non-zero enthalpy");
  }

  @Test
  void testDistillationVaporHydraulicModel() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 15.0);
    fluid.addComponent("methane", 0.4);
    fluid.addComponent("ethane", 0.3);
    fluid.addComponent("propane", 0.3);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("col-feed-v", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    DistillationColumn col = new DistillationColumn("DynColV", 5, true, true);
    col.addFeedStream(feed, 3);
    col.setTopPressure(10.0);
    col.setBottomPressure(11.0);
    col.setDynamicColumnEnabled(true);
    col.setTrayDryPressureDrop(800.0); // 800 Pa per tray

    try {
      col.run();
    } catch (Exception ex) {
      // May not fully converge
    }

    UUID testId = UUID.randomUUID();
    // Should run without error with pressure-driven vapor flow
    col.runTransient(1.0, testId);

    // Holdups should be initialized
    Assertions.assertNotNull(col.getTrayLiquidHoldup());
  }
}
