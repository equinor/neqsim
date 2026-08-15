package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmState;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.NozzleFlowMeter.NozzleType;
import neqsim.process.measurementdevice.OrificeFlowMeter.TappingArrangement;
import neqsim.process.measurementdevice.OrificeFlowMeter.WetGasCorrelation;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Quantitative transaction, restart, and physical-trend evidence for the ISO 5167 differential-pressure flow-meter
 * family.
 */
class DifferentialPressureFlowMeterTransientStateTransactionTest extends neqsim.NeqSimTest {
  @Test
  void multiAreaRollbackRestoresFiveMeterFamilyAndExactSignalReplay() {
    StreamInterface source = createGasStream("meter source");
    OrificeFlowMeter orifice = new OrificeFlowMeter("FIT-O-100", source);
    NozzleFlowMeter nozzle = new NozzleFlowMeter("FIT-N-100", source);
    VenturiFlowMeter venturi = new VenturiFlowMeter("FIT-V-100", source);
    ConeFlowMeter cone = new ConeFlowMeter("FIT-C-100", source);
    WedgeFlowMeter wedge = new WedgeFlowMeter("FIT-W-100", source);

    configureMeter(orifice, 8101L);
    configureMeter(nozzle, 8102L);
    configureMeter(venturi, 8103L);
    configureMeter(cone, 8104L);
    configureMeter(wedge, 8105L);
    cone.setGeometry(0.25, 0.165, "m");
    wedge.setGeometry(0.25, 0.075, "m");
    orifice.setTappingArrangement(TappingArrangement.D_AND_D_HALF);
    nozzle.setNozzleType(NozzleType.LONG_RADIUS);
    venturi.setDischargeCoefficient(0.992);

    AlarmConfig alarmConfig = AlarmConfig.builder().highLimit(0.0).delay(2.0).unit("kg/hr").build();
    orifice.setAlarmConfig(alarmConfig);
    AlarmState originalAlarmState = orifice.getAlarmState();

    List<DifferentialPressureFlowMeter> meters = meters(orifice, nozzle, venturi, cone, wedge);
    for (DifferentialPressureFlowMeter meter : meters) {
      meter.getMeasuredValue("kg/hr");
      meter.getMeasuredValue("kg/hr");
    }

    ProcessSystem primaryDevices = new ProcessSystem("primary devices");
    primaryDevices.add(orifice);
    primaryDevices.add(nozzle);
    primaryDevices.add(venturi);
    ProcessSystem specialtyDevices = new ProcessSystem("specialty primary devices");
    specialtyDevices.add(cone);
    specialtyDevices.add(wedge);
    assertCompleteCoverage(primaryDevices.getTransientTransactionCoverage(), 3);
    assertCompleteCoverage(specialtyDevices.getTransientTransactionCoverage(), 2);

    ProcessModel model = new ProcessModel();
    model.add("primary", primaryDevices);
    model.add("specialty", specialtyDevices);
    assertCompleteCoverage(model.getTransientTransactionCoverage(), 5);

    List<String> identities = transientIdentities(meters);
    double[] originalBeta = betaRatios(meters);
    StreamInterface[] originalStreams = streams(meters);

    TransientStepTransaction transaction = model.beginTransientStepTransaction();
    List<double[]> trialSequence = new ArrayList<double[]>();
    for (int sample = 0; sample < 4; sample++) {
      trialSequence.add(readAll(meters));
    }
    assertTrue(orifice.evaluateAlarm(1.0e9, 1.0, 1.0).isEmpty());

    for (DifferentialPressureFlowMeter meter : meters) {
      meter.setName("trial " + meter.getName());
      meter.setUnit("kg/day");
      meter.setGeometry(0.4, 0.12, "m");
      meter.setDifferentialPressure(0.4, "bar");
      meter.setGasDensity(3.0, "kg/m3");
      meter.setIsentropicExponent(1.05);
      meter.setDynamicViscosity(0.2, "cP");
      meter.setDelaySteps(0);
      meter.setNoiseStdDev(5.0);
      meter.setRandomSeed(1L);
      meter.clearFault();
      meter.setAlarmConfig(null);
    }
    orifice.setTappingArrangement(TappingArrangement.CORNER);
    nozzle.setNozzleType(NozzleType.VENTURI_NOZZLE);
    venturi.setDischargeCoefficient(0.8);
    wedge.setWedgeRatio(0.55);

    transaction.rollback();

    assertEquals(identities, transientIdentities(meters));
    assertEquals(originalBeta.length, meters.size());
    for (int index = 0; index < meters.size(); index++) {
      assertEquals(originalBeta[index], meters.get(index).getBetaRatio(), 0.0);
      assertSame(originalStreams[index], meters.get(index).getStream());
    }
    assertEquals(TappingArrangement.D_AND_D_HALF, orifice.getTappingArrangement());
    assertEquals(NozzleType.LONG_RADIUS, nozzle.getNozzleType());
    assertEquals(0.992, venturi.getDischargeCoefficient(), 0.0);
    assertSame(alarmConfig, orifice.getAlarmConfig());
    assertSame(originalAlarmState, orifice.getAlarmState());

    for (double[] expected : trialSequence) {
      double[] actual = readAll(meters);
      for (int index = 0; index < expected.length; index++) {
        assertEquals(expected[index], actual[index], 0.0,
            "rollback must replay Reynolds, Gaussian, drift, filter, and delay state exactly");
      }
    }
    assertTrue(orifice.evaluateAlarm(1.0e9, 1.0, 1.0).isEmpty());
    assertEquals(1, orifice.evaluateAlarm(1.0e9, 1.0, 2.0).size());
  }

  @Test
  void serializedWetGasOrificeSnapshotReplaysAndInvalidatesDerivedCache() throws Exception {
    OrificeFlowMeter meter = new OrificeFlowMeter("FIT-O-200", createGasStream("restart source"));
    configureMeter(meter, 7151L);
    meter.setTappingArrangement(TappingArrangement.FLANGE);
    meter.setWetGasCorrelation(WetGasCorrelation.ISO_TR_11583);
    meter.setLiquidToGasMassRatio(0.10);
    meter.setLiquidDensity(800.0, "kg/m3");
    meter.getMeasuredValue("kg/hr");
    meter.getMeasuredValue("kg/hr");

    String identity = meter.getTransientStateIdentity();
    DifferentialPressureFlowMeter.DifferentialPressureFlowMeterState snapshot = meter.captureTransientState();
    double expectedNextValue = meter.getMeasuredValue("kg/hr");
    assertTrue(Double.isFinite(expectedNextValue));

    byte[] serialized;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(meter);
      output.writeObject(snapshot);
      serialized = bytes.toByteArray();
    }

    OrificeFlowMeter restoredMeter;
    DifferentialPressureFlowMeter.DifferentialPressureFlowMeterState restoredSnapshot;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
      restoredMeter = (OrificeFlowMeter) input.readObject();
      restoredSnapshot = (DifferentialPressureFlowMeter.DifferentialPressureFlowMeterState) input.readObject();
    }

    restoredMeter.setWetGasCorrelation(WetGasCorrelation.NONE);
    restoredMeter.setTappingArrangement(TappingArrangement.CORNER);
    restoredMeter.setDifferentialPressure(0.4, "bar");
    restoredMeter.restoreTransientState(restoredSnapshot);

    assertEquals(identity, restoredMeter.getTransientStateIdentity());
    assertEquals(WetGasCorrelation.ISO_TR_11583, restoredMeter.getWetGasCorrelation());
    assertEquals(TappingArrangement.FLANGE, restoredMeter.getTappingArrangement());
    assertEquals(expectedNextValue, restoredMeter.getMeasuredValue("kg/hr"), 0.0);
  }

  @Test
  void nearbyDifferentialPressurePointFollowsSquareRootPhysicalTrend() {
    List<DifferentialPressureFlowMeter> meters = meters(new OrificeFlowMeter("O", createGasStream("O source")),
        new NozzleFlowMeter("N", createGasStream("N source")), new VenturiFlowMeter("V", createGasStream("V source")),
        new ConeFlowMeter("C", createGasStream("C source")), new WedgeFlowMeter("W", createGasStream("W source")));

    for (DifferentialPressureFlowMeter meter : meters) {
      configureMeter(meter, 1L);
      meter.setNoiseStdDev(0.0);
      meter.setDelaySteps(0);
      meter.setFirstOrderTimeConstant(0.0);
      meter.clearFault();
      if (meter instanceof ConeFlowMeter) {
        meter.setGeometry(0.25, 0.165, "m");
      } else if (meter instanceof WedgeFlowMeter) {
        meter.setGeometry(0.25, 0.075, "m");
      }
      meter.setDifferentialPressure(0.10, "bar");
      double lowFlow = meter.getMeasuredValue("kg/hr");
      meter.setDifferentialPressure(0.40, "bar");
      double highFlow = meter.getMeasuredValue("kg/hr");
      assertTrue(Double.isFinite(lowFlow));
      assertTrue(highFlow > 1.8 * lowFlow);
      assertTrue(highFlow < 2.2 * lowFlow);
      // The current classical Venturi path is Reynolds-independent and does not publish Re,D.
      if (!(meter instanceof VenturiFlowMeter)) {
        assertTrue(meter.getReynoldsNumberPipe() > 0.0);
      }
    }
  }

  @Test
  void descendantsOnlineModeAndForeignSnapshotsFailClosed() {
    StreamInterface source = createGasStream("coverage source");
    ProcessSystem process = new ProcessSystem("primary-device blocker coverage");
    process.add(new OrificeFlowMeter("O", source) {
      private static final long serialVersionUID = 1000L;
    });
    process.add(new NozzleFlowMeter("N", source) {
      private static final long serialVersionUID = 1000L;
    });
    process.add(new VenturiFlowMeter("V", source) {
      private static final long serialVersionUID = 1000L;
    });
    process.add(new ConeFlowMeter("C", source) {
      private static final long serialVersionUID = 1000L;
    });
    process.add(new WedgeFlowMeter("W", source) {
      private static final long serialVersionUID = 1000L;
    });

    TransientTransactionCoverage coverage = process.getTransientTransactionCoverage();
    assertEquals(5, coverage.getProcessElementCount());
    assertEquals(5, coverage.getParticipantCount());
    assertFalse(coverage.isComplete());
    assertEquals(5, coverage.getBlockingIssues().size());
    for (String issue : coverage.getBlockingIssues()) {
      assertTrue(issue.contains("subclass-owned mutable state"));
    }

    ConeFlowMeter online = new ConeFlowMeter("online", source);
    online.setIsOnlineSignal(true, "plant", "tag");
    ProcessSystem onlineProcess = new ProcessSystem("online blocker");
    onlineProcess.add(online);
    assertFalse(onlineProcess.getTransientTransactionCoverage().isComplete());
    assertTrue(onlineProcess.getTransientTransactionCoverage().getBlockingIssues().get(0).contains("external I/O"));

    OrificeFlowMeter first = new OrificeFlowMeter("first", source);
    OrificeFlowMeter second = new OrificeFlowMeter("second", source);
    configureMeter(first, 11L);
    configureMeter(second, 12L);
    DifferentialPressureFlowMeter.DifferentialPressureFlowMeterState foreign = first.captureTransientState();
    assertThrows(IllegalArgumentException.class, () -> second.restoreTransientState(foreign));
  }

  private static void configureMeter(DifferentialPressureFlowMeter meter, long seed) {
    meter.setGeometry(0.25, 0.125, "m");
    meter.setDifferentialPressure(0.10, "bar");
    meter.setGasDensity(40.0, "kg/m3");
    meter.setIsentropicExponent(1.30);
    meter.setDynamicViscosity(0.012, "cP");
    meter.setDelaySteps(2);
    meter.setNoiseStdDev(0.05);
    meter.setRandomSeed(seed);
    meter.setFirstOrderTimeConstant(2.0);
    meter.setFault(SensorFaultType.LINEAR_DRIFT, 0.01);
  }

  private static List<DifferentialPressureFlowMeter> meters(DifferentialPressureFlowMeter... values) {
    List<DifferentialPressureFlowMeter> result = new ArrayList<DifferentialPressureFlowMeter>();
    for (DifferentialPressureFlowMeter value : values) {
      result.add(value);
    }
    return result;
  }

  private static double[] readAll(List<DifferentialPressureFlowMeter> meters) {
    double[] values = new double[meters.size()];
    for (int index = 0; index < meters.size(); index++) {
      values[index] = meters.get(index).getMeasuredValue("kg/hr");
    }
    return values;
  }

  private static List<String> transientIdentities(List<DifferentialPressureFlowMeter> meters) {
    List<String> identities = new ArrayList<String>();
    for (DifferentialPressureFlowMeter meter : meters) {
      identities.add(meter.getTransientStateIdentity());
    }
    return identities;
  }

  private static double[] betaRatios(List<DifferentialPressureFlowMeter> meters) {
    double[] values = new double[meters.size()];
    for (int index = 0; index < meters.size(); index++) {
      values[index] = meters.get(index).getBetaRatio();
    }
    return values;
  }

  private static StreamInterface[] streams(List<DifferentialPressureFlowMeter> meters) {
    StreamInterface[] values = new StreamInterface[meters.size()];
    for (int index = 0; index < meters.size(); index++) {
      values[index] = meters.get(index).getStream();
    }
    return values;
  }

  private static void assertCompleteCoverage(TransientTransactionCoverage coverage, int expectedCount) {
    assertEquals(expectedCount, coverage.getProcessElementCount());
    assertEquals(expectedCount, coverage.getParticipantCount());
    assertTrue(coverage.isComplete(), coverage.getBlockingIssues().toString());
  }

  private static StreamInterface createGasStream(String name) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(1000.0, "kg/hr");
    stream.run();
    return stream;
  }
}
