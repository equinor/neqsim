package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.entrainment.SeparatorPerformanceCalculator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Contract tests for opt-in conservative accumulated-target separator energy integration.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SeparatorConservativeTransientEnergyIntegrationTest {
  /** The policy is explicit, default-off, and bounded to the qualified concrete algorithm. */
  @Test
  void policySelectionIsExplicitAndRejectsUnsupportedAlgorithms() {
    Separator separator = new Separator("unwired");
    assertFalse(separator.isConservativeTransientEnergyIntegrationEnabled());
    assertTrue(Double.isNaN(separator.getConservativeTransientEnergyTargetJ()));
    separator.setConservativeTransientEnergyIntegrationEnabled(true);
    assertTrue(separator.isConservativeTransientEnergyIntegrationEnabled());
    assertTrue(Double.isNaN(separator.getConservativeTransientEnergyTargetJ()));
    assertThrows(IllegalArgumentException.class, () -> separator.runTransient(0.0, id(1)));
    separator.setConservativeTransientEnergyIntegrationEnabled(false);
    assertTrue(Double.isNaN(separator.getConservativeTransientEnergyTargetJ()));

    ThreePhaseSeparator unsupported = new ThreePhaseSeparator("three-phase");
    assertThrows(UnsupportedOperationException.class,
        () -> unsupported.setConservativeTransientEnergyIntegrationEnabled(true));
  }

  /** The exact accumulated target is independent of the observational tracker. */
  @Test
  void accumulatedTargetCorrectsLocalDefectWithoutTrackerCoupling() {
    Fixture trackingOff = fixture();
    Fixture trackingOn = fixture();
    for (Fixture fixture : new Fixture[] {trackingOff, trackingOn}) {
      closeAllBoundaries(fixture);
      fixture.separator.setHeatInput(1000.0);
      fixture.separator.setConservativeTransientEnergyIntegrationEnabled(true);
    }
    trackingOn.separator.setTransientEnergyBalanceTrackingEnabled(true);

    double initial = trackingOff.separator.getThermoSystem().getInternalEnergy();
    for (int i = 0; i < 8; i++) {
      trackingOff.separator.runTransient(0.25, id(i));
      trackingOn.separator.runTransient(0.25, id(i));
      assertPhysicalStateEquals(trackingOff.separator, trackingOn.separator);
    }

    double expectedTarget = initial + 2000.0;
    assertEquals(expectedTarget, trackingOff.separator.getConservativeTransientEnergyTargetJ(), 1.0e-8);
    assertEquals(expectedTarget, trackingOn.separator.getConservativeTransientEnergyTargetJ(), 1.0e-8);
    SeparatorTransientEnergyBalanceReport report = trackingOn.separator.getLastTransientEnergyBalanceReport();
    assertTrue(report.isComplete());
    assertEquals(expectedTarget, report.getTargetEnergyJ(), 1.0e-8);
    assertEquals(report.getPublishedEnergyJ() - expectedTarget, report.getCumulativeDebtJ(), 1.0e-8);
  }

  /** Snapshot replay and Java serialization retain the exact continuation target. */
  @Test
  void snapshotReplayAndSerializationRetainContinuationTarget() throws Exception {
    Fixture fixture = fixture();
    fixture.separator.setConservativeTransientEnergyIntegrationEnabled(true);
    fixture.separator.runTransient(0.1, id(1));
    Separator.TransientState checkpoint = fixture.separator.captureTransientState();
    Stream.TransientState feedCheckpoint = fixture.feed.captureTransientState();
    double checkpointTarget = fixture.separator.getConservativeTransientEnergyTargetJ();

    fixture.separator.runTransient(0.1, id(2));
    double expectedTarget = fixture.separator.getConservativeTransientEnergyTargetJ();
    double expectedEnergy = fixture.separator.getThermoSystem().getInternalEnergy();
    fixture.feed.restoreTransientState(feedCheckpoint);
    fixture.separator.restoreTransientState(checkpoint);
    assertEquals(checkpointTarget, fixture.separator.getConservativeTransientEnergyTargetJ());
    fixture.separator.runTransient(0.1, id(2));
    assertEquals(expectedTarget, fixture.separator.getConservativeTransientEnergyTargetJ());
    assertEquals(expectedEnergy, fixture.separator.getThermoSystem().getInternalEnergy());

    Separator copy = (Separator) deserialize(serialize(fixture.separator));
    assertTrue(copy.isConservativeTransientEnergyIntegrationEnabled());
    assertEquals(expectedTarget, copy.getConservativeTransientEnergyTargetJ());
    assertEquals(expectedEnergy, copy.getThermoSystem().getInternalEnergy());
  }

  /** A post-flash exception rolls back the physical state and integration target together. */
  @Test
  void postFlashFailureRollsBackIntegrationTarget() {
    Fixture fixture = fixture();
    fixture.separator.setConservativeTransientEnergyIntegrationEnabled(true);
    fixture.separator.runTransient(0.1, id(1));
    double target = fixture.separator.getConservativeTransientEnergyTargetJ();
    double energy = fixture.separator.getThermoSystem().getInternalEnergy();
    double mass = fixture.separator.getThermoSystem().getMass("kg");
    fixture.separator.setPerformanceCalculator(new FailingPerformanceCalculator());
    fixture.separator.setDetailedEntrainmentCalculation(true);

    assertThrows(IllegalStateException.class, () -> fixture.separator.runTransient(0.1, id(2)));
    assertEquals(target, fixture.separator.getConservativeTransientEnergyTargetJ());
    assertEquals(energy, fixture.separator.getThermoSystem().getInternalEnergy());
    assertEquals(mass, fixture.separator.getThermoSystem().getMass("kg"));
  }

  /** Explicit inventory lifecycle changes and skipped updates cannot retain a stale target. */
  @Test
  void lifecycleBoundariesResetTheAccumulatedTarget() {
    Fixture fixture = fixture();
    fixture.separator.setConservativeTransientEnergyIntegrationEnabled(true);
    fixture.separator.runTransient(0.1, id(1));
    assertTrue(Double.isFinite(fixture.separator.getConservativeTransientEnergyTargetJ()));
    fixture.separator.initializeTransientCalculation();
    assertTrue(Double.isNaN(fixture.separator.getConservativeTransientEnergyTargetJ()));

    fixture.separator.runTransient(0.1, id(2));
    fixture.separator.getThermoSystem().setTotalNumberOfMoles(0.0);
    fixture.separator.runTransient(0.1, id(3));
    assertTrue(Double.isNaN(fixture.separator.getConservativeTransientEnergyTargetJ()));
  }

  /** Builds a native two-phase separator fixture with configured geometry. */
  private static Fixture fixture() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 40.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("n-heptane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run(id(0));
    Separator separator = new Separator("vessel", feed);
    separator.setInternalDiameter(1.0);
    separator.setSeparatorLength(3.0);
    separator.setLiquidLevel(0.5);
    separator.run(id(0));
    separator.setCalculateSteadyState(false);
    separator.initializeTransientCalculation();
    return new Fixture(feed, separator);
  }

  /** Sets actual inlet and outlet boundary flows to zero. */
  private static void closeAllBoundaries(Fixture fixture) {
    fixture.feed.setFlowRate(0.0, "kg/sec");
    fixture.separator.getGasOutStream().setFlowRate(0.0, "kg/sec");
    fixture.separator.getLiquidOutStream().setFlowRate(0.0, "kg/sec");
  }

  /** Compares physical state without using the diagnostic as an oracle. */
  private static void assertPhysicalStateEquals(Separator expected, Separator actual) {
    assertEquals(expected.getThermoSystem().getPressure(), actual.getThermoSystem().getPressure());
    assertEquals(expected.getThermoSystem().getTemperature(), actual.getThermoSystem().getTemperature());
    assertEquals(expected.getThermoSystem().getInternalEnergy(), actual.getThermoSystem().getInternalEnergy());
    assertEquals(expected.getThermoSystem().getMass("kg"), actual.getThermoSystem().getMass("kg"));
    assertEquals(expected.getLiquidLevel(), actual.getLiquidLevel());
  }

  /** Serializes one policy participant or checkpoint. */
  private static byte[] serialize(Object value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
      stream.writeObject(value);
    }
    return bytes.toByteArray();
  }

  /** Restores one serialized policy participant or checkpoint. */
  private static Object deserialize(byte[] bytes) throws Exception {
    try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return stream.readObject();
    }
  }

  /** Returns a deterministic physical-step identity. */
  private static UUID id(int step) {
    return new UUID(0L, step);
  }

  /** Native fixture whose feed is an external snapshot participant. */
  private static final class Fixture {
    private final Stream feed;
    private final Separator separator;

    /** Creates one test fixture. */
    private Fixture(Stream feed, Separator separator) {
      this.feed = feed;
      this.separator = separator;
    }
  }

  /** Test-only genuine post-VU extension failure. */
  private static final class FailingPerformanceCalculator extends SeparatorPerformanceCalculator {
    private static final long serialVersionUID = 1L;

    /** {@inheritDoc} */
    @Override
    public void calculate(double gasDensity, double oilDensity, double waterDensity, double gasViscosity,
        double oilViscosity, double waterViscosity, double gasVelocity, double vesselDiameter, double vesselLength,
        String orientation, double liquidLevelFraction) {
      throw new IllegalStateException("test-only post-VU performance failure");
    }
  }
}
