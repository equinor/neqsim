package neqsim.process.safety.release;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Weighted uncertainty, failure accounting, persistence and executable documentation. */
class ReleaseFlowEnsembleTest extends neqsim.NeqSimTest {
  private SystemInterface methane() {
    SystemInterface fluid = new SystemSrkEos(300.0, 5.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  private ReleaseFlowEnsemble.Case input(String id, double coefficient, double weight) {
    return new ReleaseFlowEnsemble.Case(id, new ReleaseFlowRequest(methane(), 0.01, coefficient, 1e5), weight,
        Collections.singletonMap("method", "discrete joint scenarios"));
  }

  @Test
  void documentationExampleHasAnalyticallyKnownWeightedStatistics() throws Exception {
    SystemInterface fluid = methane();
    List<ReleaseFlowEnsemble.Case> cases = new ArrayList<ReleaseFlowEnsemble.Case>();
    cases.add(new ReleaseFlowEnsemble.Case("low", new ReleaseFlowRequest(fluid, 0.01, 0.50, 1e5), 1.0,
        Collections.singletonMap("method", "discrete joint scenarios")));
    cases.add(new ReleaseFlowEnsemble.Case("base", new ReleaseFlowRequest(fluid, 0.01, 0.75, 1e5), 2.0,
        Collections.singletonMap("method", "discrete joint scenarios")));
    cases.add(new ReleaseFlowEnsemble.Case("high", new ReleaseFlowRequest(fluid, 0.01, 1.00, 1e5), 1.0,
        Collections.singletonMap("method", "discrete joint scenarios")));
    ReleaseFlowEnsemble ensemble = ReleaseFlowEnsemble.evaluate("opening-study",
        new HomogeneousEquilibriumReleaseModel(), cases);
    assertTrue(ensemble.isComplete());
    double meanKgS = ensemble.getMeanMassFlowRateKgS();
    double p90KgS = ensemble.getMassFlowRateQuantileKgS(0.90);
    List<SourceTermFrame> frames = ensemble.toFrames("scenario-1", "feed-opening",
        UUID.fromString("00000000-0000-0000-0000-000000000386"), 0.0, Instant.parse("2026-09-21T00:00:00Z"));
    double low = ensemble.getResults().get(0).getMassFlowRateKgS();
    assertEquals(low * 1.5, meanKgS, low * 1e-10);
    assertEquals(low * 2.0, p90KgS, low * 1e-10);
    assertEquals(low, ensemble.getMassFlowRateQuantileKgS(0.0));
    assertEquals(low, ensemble.getMassFlowRateQuantileKgS(0.25));
    assertEquals(low * 1.5, ensemble.getMassFlowRateQuantileKgS(0.50), low * 1e-10);
    assertEquals(low * 2.0, ensemble.getMassFlowRateQuantileKgS(1.0), low * 1e-10);
    assertEquals(Arrays.asList(0.25, 0.50, 0.25), ensemble.getProbabilities());
    assertEquals(1.0, ensemble.getUsableProbability());
    assertEquals(5.0, fluid.getPressure());
    cases.clear();
    assertEquals(3, ensemble.getCases().size());
    assertThrows(UnsupportedOperationException.class, () -> ensemble.getResults().clear());
    assertThrows(UnsupportedOperationException.class, () -> ensemble.getCases().get(0).getProvenance().clear());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(ensemble);
    }
    ReleaseFlowEnsemble restored;
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (ReleaseFlowEnsemble) in.readObject();
    }
    assertEquals(meanKgS, restored.getMeanMassFlowRateKgS());
    List<SourceTermFrame> repeated = restored.toFrames("scenario-1", "feed-opening",
        UUID.fromString("00000000-0000-0000-0000-000000000386"), 0.0, Instant.parse("2026-09-21T00:00:00Z"));
    Path directory = Paths.get("target", "source-term-contract-fixtures");
    Files.createDirectories(directory);
    for (int i = 0; i < frames.size(); i++) {
      assertEquals(frames.get(i).toJson(), repeated.get(i).toJson());
      SourceTermFrame.verifyEnvelope(frames.get(i).toJson());
      Files.write(directory.resolve("ensemble-" + i + ".json"),
          frames.get(i).toJson().getBytes(StandardCharsets.UTF_8));
    }
  }

  @Test
  void unsupportedCasesRetainProbabilityAndProhibitStatistics() throws Exception {
    SystemInterface unsupported = methane();
    unsupported.setHydrateCheck(true);
    List<ReleaseFlowEnsemble.Case> cases = Arrays.asList(input("gas", 0.62, 1.0), new ReleaseFlowEnsemble.Case(
        "solid-risk", new ReleaseFlowRequest(unsupported, 0.01, 0.62, 1e5), 3.0, Collections.emptyMap()));
    ReleaseFlowEnsemble ensemble = ReleaseFlowEnsemble.evaluate("incomplete", new HomogeneousEquilibriumReleaseModel(),
        cases);
    assertFalse(ensemble.isComplete());
    assertEquals(0.25, ensemble.getUsableProbability());
    assertEquals(1, ensemble.getStatusCounts().get(ReleaseFlowResult.Status.UNSUPPORTED).intValue());
    assertThrows(IllegalStateException.class, ensemble::getMeanMassFlowRateKgS);
    assertThrows(IllegalStateException.class, () -> ensemble.getMassFlowRateQuantileKgS(0.5));
    List<SourceTermFrame> frames = ensemble.toFrames("scenario", "feed", new UUID(0, 1), 0.0, Instant.EPOCH);
    JsonObject failure = JsonParser.parseString(frames.get(1).toJson()).getAsJsonObject();
    assertFalse(failure.has("source"));
    assertEquals("0.75", failure.getAsJsonObject("provenance").get("ensembleProbability").getAsString());
    assertEquals("solid-risk", failure.getAsJsonObject("provenance").get("ensembleCaseId").getAsString());
    Path directory = Paths.get("target", "source-term-contract-fixtures");
    Files.createDirectories(directory);
    Files.write(directory.resolve("ensemble-unsupported.json"),
        frames.get(1).toJson().getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void genuineZeroParticipatesAndTinyUpperTailRetainsMaximumQuantile() {
    ReleaseFlowEnsemble.Case zero = new ReleaseFlowEnsemble.Case("closed-pressure-gradient",
        new ReleaseFlowRequest(methane(), 0.01, 0.62, 6e5), 1.0, Collections.emptyMap());
    ReleaseFlowEnsemble.Case flowing = input("tiny-upper-tail", 0.62, 1e-30);
    ReleaseFlowEnsemble ensemble = ReleaseFlowEnsemble.evaluate("tail", new HomogeneousEquilibriumReleaseModel(),
        Arrays.asList(flowing, zero));
    assertTrue(ensemble.isComplete());
    double rate = ensemble.getResults().get(0).getMassFlowRateKgS();
    assertTrue(rate > 0.0);
    assertEquals(0.0, ensemble.getMassFlowRateQuantileKgS(0.0));
    assertEquals(0.0, ensemble.getMassFlowRateQuantileKgS(0.5));
    assertEquals(rate, ensemble.getMassFlowRateQuantileKgS(1.0));
    assertEquals(rate * 1e-30, ensemble.getMeanMassFlowRateKgS(), rate * 1e-40);
  }

  @Test
  void modelExceptionsNullAndIdentityMismatchDoNotDropCases() {
    AtomicInteger calls = new AtomicInteger();
    ReleaseFlowModel broken = new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      @Override
      public String getModelId() {
        return "broken-test-model";
      }

      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        int call = calls.getAndIncrement();
        if (call == 0) {
          throw new IllegalStateException("synthetic failure");
        }
        if (call == 1) {
          return null;
        }
        return ReleaseFlowResult.failure(new HomogeneousEquilibriumReleaseModel(), true, "WRONG_MODEL", "test");
      }
    };
    ReleaseFlowEnsemble ensemble = ReleaseFlowEnsemble.evaluate("broken", broken,
        Arrays.asList(input("a", 0.5, 1), input("b", 0.6, 1), input("c", 0.7, 1)));
    assertEquals(3, calls.get());
    assertEquals(0.0, ensemble.getUsableProbability());
    assertEquals(3, ensemble.getStatusCounts().get(ReleaseFlowResult.Status.INVALID).intValue());
    assertEquals("MODEL_EXCEPTION", ensemble.getResults().get(0).getDiagnostics().get(0).getCode());
    assertEquals("MODEL_RETURNED_NULL", ensemble.getResults().get(1).getDiagnostics().get(0).getCode());
    assertEquals("MODEL_IDENTITY_MISMATCH", ensemble.getResults().get(2).getDiagnostics().get(0).getCode());
    assertThrows(IllegalStateException.class, ensemble::getMeanMassFlowRateKgS);
  }

  @Test
  void weightsAreValidatedBeforeCalculationsAndNormalizedWithoutOverflow() {
    AtomicInteger calls = new AtomicInteger();
    ReleaseFlowModel model = new ReleaseFlowModel() {
      private static final long serialVersionUID = 1L;

      @Override
      public String getModelId() {
        return "counting-test-model";
      }

      @Override
      public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
        calls.incrementAndGet();
        return ReleaseFlowResult.failure(this, true, "TEST", "test");
      }
    };
    assertThrows(IllegalArgumentException.class, () -> input("bad", 0.5, 0.0));
    assertThrows(IllegalArgumentException.class, () -> input("bad", 0.5, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> ReleaseFlowEnsemble.evaluate("duplicate", model, Arrays.asList(input("a", 0.5, 1), input("a", 0.6, 1))));
    assertThrows(IllegalArgumentException.class, () -> ReleaseFlowEnsemble.evaluate("underflow", model,
        Arrays.asList(input("a", 0.5, Double.MIN_VALUE), input("b", 0.6, Double.MAX_VALUE))));
    assertEquals(0, calls.get());
    ReleaseFlowEnsemble ensemble = ReleaseFlowEnsemble.evaluate("large-weights", model,
        Arrays.asList(input("a", 0.5, Double.MAX_VALUE), input("b", 0.6, Double.MAX_VALUE)));
    assertEquals(Arrays.asList(0.5, 0.5), ensemble.getProbabilities());
    assertEquals(2, calls.get());
    assertThrows(IllegalArgumentException.class, () -> ensemble.getMassFlowRateQuantileKgS(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> ensemble.getMassFlowRateQuantileKgS(-0.1));
    assertThrows(IllegalArgumentException.class, () -> ensemble.getMassFlowRateQuantileKgS(1.1));
  }
}
