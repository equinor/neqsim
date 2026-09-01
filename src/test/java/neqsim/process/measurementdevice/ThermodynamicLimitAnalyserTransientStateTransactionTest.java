package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmState;
import neqsim.process.dynamics.EventScheduler;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Transaction, replay, restart, and blocker evidence for local thermodynamic-limit analysers. */
class ThermodynamicLimitAnalyserTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void multiAreaRollbackRestoresScheduledConfigurationAndAlarmState() {
    StreamInterface originalStream = createGasStream("original gas", 22.0e-6);
    StreamInterface trialStream = createGasStream("trial gas", 35.0e-6);

    CricondenbarAnalyser cricondenbar = new CricondenbarAnalyser("cricondenbar", originalStream);
    HydrateEquilibriumTemperatureAnalyser hydrate = new HydrateEquilibriumTemperatureAnalyser("hydrate equilibrium",
        originalStream);
    HydrocarbonDewPointAnalyser hydrocarbon = new HydrocarbonDewPointAnalyser("hydrocarbon dew point", originalStream);
    WaterDewPointAnalyser water = new WaterDewPointAnalyser("water dew point", originalStream);
    water.setAlarmConfig(AlarmConfig.builder().highLimit(-30.0).delay(2.0).unit("C").build());
    AlarmState originalAlarmState = water.getAlarmState();
    double originalWaterDewPoint = water.getMeasuredValue("C");

    ProcessSystem cricondenbarArea = area("cricondenbar area", cricondenbar);
    ProcessSystem hydrateArea = area("hydrate area", hydrate);
    ProcessSystem hydrocarbonArea = area("hydrocarbon area", hydrocarbon);
    ProcessSystem waterArea = area("water area", water);

    ProcessModel model = new ProcessModel();
    model.add("cricondenbar", cricondenbarArea);
    model.add("hydrate", hydrateArea);
    model.add("hydrocarbon", hydrocarbonArea);
    model.add("water", waterArea);
    assertCompleteCoverage(model.getTransientTransactionCoverage(), 4);

    cricondenbarArea.getEventScheduler().scheduleTransactionalEvent(1.0, "change cricondenbar binding",
        () -> cricondenbar.setStream(trialStream), cricondenbar.getTransientStateIdentity());
    hydrateArea.getEventScheduler().scheduleTransactionalEvent(1.0, "change hydrate conditions", () -> {
      hydrate.setStream(trialStream);
      hydrate.setReferencePressure(80.0);
    }, hydrate.getTransientStateIdentity());
    hydrocarbonArea.getEventScheduler().scheduleTransactionalEvent(1.0, "change hydrocarbon conditions", () -> {
      hydrocarbon.setStream(trialStream);
      hydrocarbon.setReferencePressure(40.0);
      hydrocarbon.setMethod("trial method");
    }, hydrocarbon.getTransientStateIdentity());
    waterArea.getEventScheduler().scheduleTransactionalEvent(1.0, "change water conditions", () -> {
      water.setStream(trialStream);
      water.setReferencePressure(60.0);
      water.setMethod("multiphase");
    }, water.getTransientStateIdentity());

    String waterIdentity = water.getTransientStateIdentity();
    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    fireAll(cricondenbarArea, hydrateArea, hydrocarbonArea, waterArea);
    assertTrue(water.evaluateAlarm(-20.0, 1.0, 1.0).isEmpty());
    transaction.rollback();

    assertEquals(waterIdentity, water.getTransientStateIdentity());
    assertSame(originalStream, cricondenbar.getStream());
    assertSame(originalStream, hydrate.getStream());
    assertSame(originalStream, hydrocarbon.getStream());
    assertSame(originalStream, water.getStream());
    assertEquals(0.0, hydrate.getReferencePressure(), 0.0);
    assertEquals(50.0, hydrocarbon.getReferencePressure(), 0.0);
    assertEquals("EOS", hydrocarbon.getMethod());
    assertEquals(70.0, water.getReferencePressure(), 0.0);
    assertEquals("Bukacek", water.getMethod());
    assertSame(originalAlarmState, water.getAlarmState());
    assertEquals(originalWaterDewPoint, water.getMeasuredValue("C"), 0.0);
    assertSchedulerRestored(cricondenbarArea, hydrateArea, hydrocarbonArea, waterArea);

    TransientStepTransaction replay = model.beginTransientStepTransaction();
    fireAll(cricondenbarArea, hydrateArea, hydrocarbonArea, waterArea);
    assertSame(trialStream, cricondenbar.getStream());
    assertEquals(80.0, hydrate.getReferencePressure(), 0.0);
    assertEquals(40.0, hydrocarbon.getReferencePressure(), 0.0);
    assertEquals("trial method", hydrocarbon.getMethod());
    assertEquals(60.0, water.getReferencePressure(), 0.0);
    assertEquals("multiphase", water.getMethod());
    assertTrue(water.evaluateAlarm(-20.0, 1.0, 1.0).isEmpty());
    assertEquals(1, water.evaluateAlarm(-20.0, 1.0, 2.0).size());
    replay.commit();

    assertSchedulerCommitted(cricondenbarArea, hydrateArea, hydrocarbonArea, waterArea);
  }

  @Test
  void serializedAnalysersAndSnapshotsRestoreRestartState() throws Exception {
    StreamInterface originalStream = createGasStream("restart gas", 22.0e-6);
    StreamInterface trialStream = createGasStream("trial restart gas", 35.0e-6);

    CricondenbarAnalyser cricondenbar = new CricondenbarAnalyser("cricondenbar", originalStream);
    CricondenbarAnalyser.CricondenbarAnalyserState cricondenbarState = cricondenbar.captureTransientState();
    cricondenbar.setStream(trialStream);

    HydrateEquilibriumTemperatureAnalyser hydrate = new HydrateEquilibriumTemperatureAnalyser("hydrate",
        originalStream);
    hydrate.setReferencePressure(65.0);
    HydrateEquilibriumTemperatureAnalyser.HydrateAnalyserState hydrateState = hydrate.captureTransientState();
    hydrate.setReferencePressure(90.0);

    HydrocarbonDewPointAnalyser hydrocarbon = new HydrocarbonDewPointAnalyser("hydrocarbon", originalStream);
    hydrocarbon.setReferencePressure(45.0);
    HydrocarbonDewPointAnalyser.HydrocarbonDewPointAnalyserState hydrocarbonState = hydrocarbon.captureTransientState();
    hydrocarbon.setMethod("trial method");

    WaterDewPointAnalyser water = new WaterDewPointAnalyser("water", originalStream);
    water.setMethod("Bukacek");
    WaterDewPointAnalyser.WaterDewPointAnalyserState waterState = water.captureTransientState();
    water.setReferencePressure(55.0);

    Object[] restored = roundTrip(cricondenbar, cricondenbarState, hydrate, hydrateState, hydrocarbon, hydrocarbonState,
        water, waterState);

    CricondenbarAnalyser restoredCricondenbar = (CricondenbarAnalyser) restored[0];
    restoredCricondenbar.restoreTransientState((CricondenbarAnalyser.CricondenbarAnalyserState) restored[1]);
    assertEquals("restart gas", restoredCricondenbar.getStream().getName());

    HydrateEquilibriumTemperatureAnalyser restoredHydrate = (HydrateEquilibriumTemperatureAnalyser) restored[2];
    restoredHydrate.restoreTransientState((HydrateEquilibriumTemperatureAnalyser.HydrateAnalyserState) restored[3]);
    assertEquals(65.0, restoredHydrate.getReferencePressure(), 0.0);

    HydrocarbonDewPointAnalyser restoredHydrocarbon = (HydrocarbonDewPointAnalyser) restored[4];
    restoredHydrocarbon
        .restoreTransientState((HydrocarbonDewPointAnalyser.HydrocarbonDewPointAnalyserState) restored[5]);
    assertEquals(45.0, restoredHydrocarbon.getReferencePressure(), 0.0);
    assertEquals("EOS", restoredHydrocarbon.getMethod());

    WaterDewPointAnalyser restoredWater = (WaterDewPointAnalyser) restored[6];
    restoredWater.restoreTransientState((WaterDewPointAnalyser.WaterDewPointAnalyserState) restored[7]);
    assertEquals(70.0, restoredWater.getReferencePressure(), 0.0);
    assertEquals("Bukacek", restoredWater.getMethod());

    assertEquals(cricondenbar.getTransientStateIdentity(), restoredCricondenbar.getTransientStateIdentity());
    assertEquals(hydrate.getTransientStateIdentity(), restoredHydrate.getTransientStateIdentity());
    assertEquals(hydrocarbon.getTransientStateIdentity(), restoredHydrocarbon.getTransientStateIdentity());
    assertEquals(water.getTransientStateIdentity(), restoredWater.getTransientStateIdentity());
  }

  @Test
  void descendantsOnlineAndForeignSnapshotsFailClosed() {
    StreamInterface stream = createGasStream("blocker gas", 22.0e-6);

    assertBlocked(new CricondenbarAnalyser("subclass", stream) {
      private static final long serialVersionUID = 1000L;
    }, "subclass-owned mutable state");
    assertBlocked(new HydrateEquilibriumTemperatureAnalyser("subclass", stream) {
      private static final long serialVersionUID = 1000L;
    }, "subclass-owned mutable state");
    assertBlocked(new HydrocarbonDewPointAnalyser("subclass", stream) {
      private static final long serialVersionUID = 1000L;
    }, "subclass-owned mutable state");
    assertBlocked(new WaterDewPointAnalyser("subclass", stream) {
      private static final long serialVersionUID = 1000L;
    }, "subclass-owned mutable state");

    WaterDewPointAnalyser online = new WaterDewPointAnalyser("online", stream);
    online.setIsOnlineSignal(true, "plant", "WDP-ONLINE");
    assertBlocked(online, "external I/O");

    CricondenbarAnalyser firstCricondenbar = new CricondenbarAnalyser("first", stream);
    CricondenbarAnalyser secondCricondenbar = new CricondenbarAnalyser("second", stream);
    assertThrows(IllegalArgumentException.class,
        () -> secondCricondenbar.restoreTransientState(firstCricondenbar.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstCricondenbar.restoreTransientState(null));

    HydrateEquilibriumTemperatureAnalyser firstHydrate = new HydrateEquilibriumTemperatureAnalyser("first", stream);
    HydrateEquilibriumTemperatureAnalyser secondHydrate = new HydrateEquilibriumTemperatureAnalyser("second", stream);
    assertThrows(IllegalArgumentException.class,
        () -> secondHydrate.restoreTransientState(firstHydrate.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstHydrate.restoreTransientState(null));

    HydrocarbonDewPointAnalyser firstHydrocarbon = new HydrocarbonDewPointAnalyser("first", stream);
    HydrocarbonDewPointAnalyser secondHydrocarbon = new HydrocarbonDewPointAnalyser("second", stream);
    assertThrows(IllegalArgumentException.class,
        () -> secondHydrocarbon.restoreTransientState(firstHydrocarbon.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstHydrocarbon.restoreTransientState(null));

    WaterDewPointAnalyser firstWater = new WaterDewPointAnalyser("first", stream);
    WaterDewPointAnalyser secondWater = new WaterDewPointAnalyser("second", stream);
    assertThrows(IllegalArgumentException.class,
        () -> secondWater.restoreTransientState(firstWater.captureTransientState()));
    assertThrows(IllegalArgumentException.class, () -> firstWater.restoreTransientState(null));
  }

  private static StreamInterface createGasStream(String name, double waterFraction) {
    SystemInterface fluid = new SystemSrkEos(298.15, 70.0);
    fluid.addComponent("methane", 1.0 - waterFraction);
    fluid.addComponent("water", waterFraction);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.run();
    return stream;
  }

  private static ProcessSystem area(String name, MeasurementDeviceInterface analyser) {
    ProcessSystem area = new ProcessSystem(name);
    area.setEventScheduler(new EventScheduler());
    area.add(analyser);
    assertCompleteCoverage(area.getTransientTransactionCoverage(), 1);
    return area;
  }

  private static void fireAll(ProcessSystem... areas) {
    for (ProcessSystem area : areas) {
      assertEquals(1, area.getEventScheduler().fireDueEvents(1.0));
    }
  }

  private static void assertSchedulerRestored(ProcessSystem... areas) {
    for (ProcessSystem area : areas) {
      assertEquals(1, area.getEventScheduler().getPendingEvents().size());
      assertEquals(0, area.getEventScheduler().getFiredEvents().size());
    }
  }

  private static void assertSchedulerCommitted(ProcessSystem... areas) {
    for (ProcessSystem area : areas) {
      assertEquals(0, area.getEventScheduler().getPendingEvents().size());
      assertEquals(1, area.getEventScheduler().getFiredEvents().size());
    }
  }

  private static Object[] roundTrip(Object... values) throws Exception {
    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      for (Object value : values) {
        output.writeObject(value);
      }
      serialized = bytes.toByteArray();
    }
    Object[] restored = new Object[values.length];
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      for (int i = 0; i < restored.length; i++) {
        restored[i] = input.readObject();
      }
    }
    return restored;
  }

  private static void assertBlocked(MeasurementDeviceInterface analyser, String expectedText) {
    ProcessSystem process = new ProcessSystem("thermodynamic-limit blocker");
    process.add(analyser);
    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(1, coverage.getProcessElementCount());
    assertEquals(1, coverage.getParticipantCount());
    assertEquals(1, coverage.getBlockingIssues().size());
    assertTrue(coverage.getBlockingIssues().get(0).contains(expectedText));
  }

  private static void assertCompleteCoverage(TransientTransactionCoverage coverage, int expectedCount) {
    assertEquals(expectedCount, coverage.getProcessElementCount());
    assertEquals(expectedCount, coverage.getParticipantCount());
    assertTrue(coverage.isComplete(), coverage.getBlockingIssues().toString());
  }
}
