package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Whole-call rejection and publication regressions for opt-in transient transactions. */
class TwoFluidPipeTransactionalTest {
  @Test
  void incompleteIntervalDiscardsHydrodynamicsComponentsHistoriesAndAllClocks() throws Exception {
    for (boolean components : new boolean[] { false, true }) {
      TwoFluidPipe pipe = createPipe(components);
      assertFalse(pipe.isTransactionalTransientEnabled());
      pipe.setTransactionalTransientEnabled(true);
      if (components) {
        pipe.setWallProperties(0.005, 1000.0, 100.0);
        pipe.setHeatTransferCoefficient(5000.0);
        pipe.setSurfaceTemperature(290.0, "K");
      }
      pipe.runTransient(1.0e-4, UUID.randomUUID());
      if (components) {
        assertTrue(pipe.getTemperatureProfile()[0] < 300.0);
      }
      pipe.setMaximumTransientSubsteps(1);
      Object equations = field(pipe, "equations");
      Object integrator = field(pipe, "timeIntegrator");
      StreamInterface outlet = pipe.getOutletStream();
      SystemInterface fluid = outlet.getFluid();
      byte[] before = SerializationUtils.serialize(pipe);
      double adaptive = (Double) field(pipe, "adaptiveDtFactor");
      IllegalStateException failure = assertThrows(IllegalStateException.class,
          () -> pipe.runTransient(0.01, UUID.randomUUID()));
      assertTrue(failure.getMessage().contains("of requested"));
      assertArrayEquals(before, SerializationUtils.serialize(pipe));
      assertEquals(adaptive, (Double) field(pipe, "adaptiveDtFactor"), 0.0);
      assertSame(equations, field(pipe, "equations"));
      assertSame(integrator, field(pipe, "timeIntegrator"));
      assertSame(outlet, pipe.getOutletStream());
      assertSame(fluid, outlet.getFluid());
      pipe.setMaximumTransientSubsteps(10000);
      pipe.runTransient(1.0e-4, UUID.randomUUID());
      assertEquals(2.0e-4, pipe.getSimulationTime(), 1.0e-15);
    }
  }

  @Test
  void successfulTransactionMatchesLegacyNumericsAndPreservesConnections() throws Exception {
    for (double dt : new double[] { 1.0e-4, 2.0e-4 }) {
      TwoFluidPipe pipe = createPipe(true);
      TwoFluidPipe legacy = SerializationUtils.clone(pipe);
      fieldHandle("adaptiveDtFactor").set(pipe, 0.37);
      fieldHandle("adaptiveDtFactor").set(legacy, 0.37);
      pipe.setTransactionalTransientEnabled(true);
      StreamInterface inlet = pipe.getInletStream();
      StreamInterface outlet = pipe.getOutletStream();
      SystemInterface inletFluid = inlet.getFluid();
      byte[] inletBefore = SerializationUtils.serialize(inletFluid);
      UUID id = UUID.randomUUID();
      legacy.runTransient(dt, id);
      pipe.runTransient(dt, id);
      TwoFluidSection[] expected = legacy.getSectionSnapshots();
      TwoFluidSection[] actual = pipe.getSectionSnapshots();
      for (int cell = 0; cell < expected.length; cell++) {
        assertArrayEquals(expected[cell].getStateVector(), actual[cell].getStateVector(), 0.0);
        assertEquals(expected[cell].getPressure(), actual[cell].getPressure(), 0.0);
        assertEquals(expected[cell].getTemperature(), actual[cell].getTemperature(), 0.0);
      }
      assertArrayEquals(SerializationUtils.serialize(legacy.getLastMassBalanceReport()),
          SerializationUtils.serialize(pipe.getLastMassBalanceReport()));
      assertArrayEquals(SerializationUtils.serialize(legacy.getLastComponentConservationReport()),
          SerializationUtils.serialize(pipe.getLastComponentConservationReport()));
      assertEquals(legacy.getSimulationTime(), pipe.getSimulationTime(), 0.0);
      assertEquals(legacy.getTime(), pipe.getTime(), 0.0);
      assertEquals(field(legacy, "adaptiveDtFactor"), field(pipe, "adaptiveDtFactor"));
      assertEquals(legacy.getOutletStream().getFlowRate("kg/sec"), outlet.getFlowRate("kg/sec"), 1.0e-14);
      assertArrayEquals(legacy.getOutletStream().getFluid().getMolarComposition(),
          outlet.getFluid().getMolarComposition(), 1.0e-14);
      assertSame(id, pipe.getCalculationIdentifier());
      assertSame(inlet, pipe.getInletStream());
      assertSame(inletFluid, inlet.getFluid());
      assertArrayEquals(inletBefore, SerializationUtils.serialize(inletFluid));
      assertSame(outlet, pipe.getOutletStream());
    }
  }

  @Test
  void downstreamPublicationFailureRestoresFluidAndKeepsAcceptedPipe() {
    TwoFluidPipe pipe = createPipe(true);
    pipe.setTransactionalTransientEnabled(true);
    RejectingPublicationStream outlet = new RejectingPublicationStream(pipe.getOutletStream().getFluid());
    pipe.setOutletStream(outlet);
    outlet.rejectPublication = true;
    SystemInterface acceptedFluid = outlet.getFluid();
    byte[] before = SerializationUtils.serialize(pipe);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> pipe.runTransient(1.0e-4, UUID.randomUUID()));
    assertEquals("injected downstream publication failure", failure.getMessage());
    assertTrue(outlet.rejected, "Candidate calculation must complete before live publication is attempted");
    assertSame(acceptedFluid, outlet.getFluid());
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
  }

  @Test
  void legacyPartialIntervalContractRemainsAvailable() {
    TwoFluidPipe pipe = createPipe(false);
    pipe.setMaximumTransientSubsteps(1);
    double before = pipe.getSimulationTime();
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertTrue(pipe.getSimulationTime() > before);
    assertNotEquals(0.0, pipe.getLastMassBalanceReport().getElapsedTimeSeconds());
  }

  @Test
  void serializationRetainsSelectionAndInvalidClocksRejectBeforeWork() {
    TwoFluidPipe pipe = createPipe(false);
    pipe.setTransactionalTransientEnabled(true);
    pipe = SerializationUtils.clone(pipe);
    assertTrue(pipe.isTransactionalTransientEnabled());
    pipe.setTime(Double.MAX_VALUE);
    final TwoFluidPipe invalid = pipe;
    byte[] before = SerializationUtils.serialize(pipe);
    assertThrows(IllegalArgumentException.class, () -> invalid.runTransient(0.01, UUID.randomUUID()));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
  }

  @Test
  void configuredTransientOilWaterClosuresSurviveAcceptance() throws Exception {
    TwoFluidPipe pipe = createPipe(false);
    pipe.setTransactionalTransientEnabled(true);
    TwoFluidSection[] sections = (TwoFluidSection[]) field(pipe, "sections");
    sections[1].getOilWaterDetector().setCriticalWeber(2.4);
    sections[1].getOilWaterDetector().setInversionConstant(0.7);
    pipe.runTransient(1.0e-4, UUID.randomUUID());
    TwoFluidSection after = pipe.getSectionSnapshots()[1];
    assertEquals(2.4, after.getOilWaterDetector().getCriticalWeber(), 0.0);
    assertEquals(0.7, after.getOilWaterDetector().getInversionConstant(), 0.0);
  }

  @Test
  void connectedStorageKeepsItsIdentityAndAcceptedConservativeTransfers() {
    TwoFluidPipe pipe = createPipe(false);
    pipe.setTransactionalTransientEnabled(true);
    UpstreamCompressibleVolume storage = pipe.initializeUpstreamCompressibleVolume(1.0e5);
    double initial = storage.getTotalMassKg();
    pipe.runTransient(0.001, UUID.randomUUID());
    assertSame(storage, pipe.getUpstreamCompressibleVolume());
    double transferred = pipe.getLastMassBalanceReport().getInletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL);
    assertTrue(transferred > 0.0);
    assertEquals(initial - transferred, storage.getTotalMassKg(), 1.0e-8);
    pipe.setMaximumTransientSubsteps(1);
    byte[] before = SerializationUtils.serialize(pipe);
    byte[] storageBefore = SerializationUtils.serialize(storage);
    assertThrows(IllegalStateException.class, () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertSame(storage, pipe.getUpstreamCompressibleVolume());
    assertArrayEquals(storageBefore, SerializationUtils.serialize(storage));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
  }

  @Test
  void aliasedPortsAndUnspecifiedSubclassCommitHooksRejectBeforeEvaluation() {
    TwoFluidPipe pipe = createPipe(false);
    pipe.setTransactionalTransientEnabled(true);
    pipe.setOutletStream(pipe.getInletStream());
    byte[] before = SerializationUtils.serialize(pipe);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertTrue(failure.getMessage().contains("distinct inlet and outlet"));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
    TwoFluidPipe extension = new TwoFluidPipe("unsupported extension") {
      private static final long serialVersionUID = 1L;
    };
    extension.setTransactionalTransientEnabled(true);
    failure = assertThrows(IllegalStateException.class, () -> extension.runTransient(0.01, UUID.randomUUID()));
    assertTrue(failure.getMessage().contains("subclasses need"));
    assertEquals(0.0, extension.getTime(), 0.0);
  }

  @Test
  void anUnauditedTransientEosHelperCannotBeSilentlyLostDuringStaging() {
    TwoFluidPipe pipe = createPipe(false);
    pipe.setTransactionalTransientEnabled(true);
    pipe.getInletStream().setFluid(new neqsim.thermo.system.SystemAmmoniaEos(300.0, 60.0));
    byte[] before = SerializationUtils.serialize(pipe);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> pipe.runTransient(0.01, UUID.randomUUID()));
    assertTrue(failure.getMessage().contains("PhaseAmmoniaEos"));
    assertArrayEquals(before, SerializationUtils.serialize(pipe));
  }

  private static TwoFluidPipe createPipe(boolean components) {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("transaction feed", fluid);
    inlet.setFlowRate(0.1, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("transaction pipe", inlet);
    pipe.setLength(40.0);
    pipe.setDiameter(0.2);
    pipe.setNumberOfSections(4);
    pipe.setElevationProfile(new double[4]);
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableJouleThomson(false);
    pipe.setThermodynamicUpdateInterval(Integer.MAX_VALUE);
    pipe.setComponentTransportEnabled(components);
    pipe.setStoreComponentConservationHistory(components);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }

  private static Object field(TwoFluidPipe pipe, String name) throws Exception {
    return fieldHandle(name).get(pipe);
  }

  private static Field fieldHandle(String name) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  /** Fail after replacing a live stream fluid; serialization deliberately removes the test trigger. */
  private static final class RejectingPublicationStream extends Stream {
    private static final long serialVersionUID = 1L;
    private final SystemInterface acceptedFluid;
    private transient boolean rejectPublication;
    private transient boolean rejected;

    private RejectingPublicationStream(SystemInterface fluid) {
      super("rejecting outlet", fluid);
      acceptedFluid = fluid;
    }

    @Override
    public void setFluid(SystemInterface fluid) {
      super.setFluid(fluid);
      if (rejectPublication && fluid != acceptedFluid) {
        rejected = true;
        throw new IllegalStateException("injected downstream publication failure");
      }
    }
  }
}
