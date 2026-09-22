package neqsim.process.ml.surrogate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.ml.surrogate.SurrogateModelRegistry.SurrogateMetadata;
import neqsim.process.ml.surrogate.SurrogateModelRegistry.SurrogateModel;

/**
 * Regression tests for the input, output and fallback contract reported in issue #3906.
 */
class SurrogateModelRegistryValidationTest {
  private SurrogateModelRegistry registry;
  private boolean previousFallback;

  @BeforeEach
  void setUp() {
    registry = SurrogateModelRegistry.getInstance();
    previousFallback = registry.isEnableFallback();
    registry.setEnableFallback(true);
    registry.clear();
  }

  @AfterEach
  void tearDown() {
    registry.clear();
    registry.setEnableFallback(previousFallback);
  }

  @Test
  void rejectsMalformedRequestsBeforeEitherCallbackWithOrWithoutBounds() {
    double[][] invalid = {null, {}, {Double.NaN}, {Double.POSITIVE_INFINITY}, {Double.NEGATIVE_INFINITY}};
    for (boolean bounded : new boolean[] {false, true}) {
      AtomicInteger calls = new AtomicInteger();
      SurrogateMetadata metadata = new SurrogateMetadata();
      if (bounded) {
        metadata.setInputBounds(new double[] {0.0}, new double[] {1.0});
      }
      registry.register("invalid-input", input -> new double[] {calls.incrementAndGet()}, metadata);
      for (double[] input : invalid) {
        assertFalse(metadata.isInputValid(input));
        assertThrows(IllegalArgumentException.class, () -> registry.predictWithFallback("invalid-input", input,
            values -> new double[] {calls.incrementAndGet()}));
      }
      assertEquals(0, calls.get());
      assertEquals(0, metadata.getPredictionCount());
      assertEquals(0, metadata.getFailureCount());
      assertEquals(0.0, metadata.getExtrapolationRate());
    }
  }

  @Test
  void missingModelStillRejectsMalformedInputAndInvalidPhysicsResults() {
    AtomicInteger calls = new AtomicInteger();
    assertThrows(IllegalArgumentException.class, () -> registry.predictWithFallback("missing",
        new double[] {Double.NaN}, values -> new double[] {calls.incrementAndGet()}));
    assertEquals(0, calls.get());
    for (double[] output : invalidOutputs()) {
      assertThrows(IllegalStateException.class,
          () -> registry.predictWithFallback("missing", new double[] {1.0}, values -> output));
    }
  }

  @Test
  void enforcesExactBoundsDimensionBeforeFallback() {
    AtomicInteger calls = new AtomicInteger();
    SurrogateMetadata metadata = boundedMetadata();
    registry.register("bounds", input -> new double[] {calls.incrementAndGet()}, metadata);
    for (double[] input : new double[][] {{0.5}, {0.5, 0.5, 9.0}}) {
      assertFalse(metadata.isInputValid(input));
      assertThrows(IllegalArgumentException.class,
          () -> registry.predictWithFallback("bounds", input, values -> new double[] {calls.incrementAndGet()}));
    }
    assertEquals(0, calls.get());
  }

  @Test
  void enforcesModelInputDimensionEvenWithoutBounds() {
    AtomicInteger fallbackCalls = new AtomicInteger();
    registry.register("schema", dimensionedModel(2, 1, new double[] {7.0}));
    for (double[] input : new double[][] {{0.5}, {0.5, 0.5, 0.5}}) {
      assertThrows(IllegalArgumentException.class, () -> registry.predictWithFallback("schema", input,
          values -> new double[] {fallbackCalls.incrementAndGet()}));
    }
    assertEquals(0, fallbackCalls.get());
    assertArrayEquals(new double[] {7.0}, registry.predictWithFallback("schema", new double[] {0.5, 0.5}, null));
  }

  @Test
  void validatesBoundsAtomicallyAndCopiesCallerArrays() {
    SurrogateMetadata metadata = boundedMetadata();
    double[][][] invalid = {{null, {1.0}}, {{0.0}, null}, {{}, {}}, {{0.0}, {1.0, 2.0}}, {{Double.NaN}, {1.0}},
        {{0.0}, {Double.NaN}}, {{Double.NEGATIVE_INFINITY}, {1.0}}, {{0.0}, {Double.POSITIVE_INFINITY}},
        {{2.0}, {1.0}}};
    for (double[][] bounds : invalid) {
      assertThrows(IllegalArgumentException.class, () -> metadata.setInputBounds(bounds[0], bounds[1]));
      assertTrue(metadata.isInputValid(new double[] {0.5, 0.5}));
    }
    double[] min = {0.0};
    double[] max = {1.0};
    metadata.setInputBounds(min, max);
    min[0] = 9.0;
    max[0] = -9.0;
    assertTrue(metadata.isInputValid(new double[] {0.0}));
    assertTrue(metadata.isInputValid(new double[] {1.0}));
    metadata.setInputBounds(new double[] {0.5}, new double[] {0.5});
    assertTrue(metadata.isInputValid(new double[] {0.5}));
    assertFalse(metadata.isInputValid(new double[] {0.6}));
  }

  @Test
  void rejectsInconsistentOrInvalidDeclaredDimensions() {
    assertThrows(IllegalArgumentException.class,
        () -> registry.register("mismatch", dimensionedModel(1, 1, new double[] {1.0}), boundedMetadata()));
    for (int dimension : new int[] {0, -2}) {
      assertThrows(IllegalArgumentException.class,
          () -> registry.register("input-schema", dimensionedModel(dimension, 1, new double[] {1.0})));
      assertThrows(IllegalArgumentException.class,
          () -> registry.register("output-schema", dimensionedModel(1, dimension, new double[] {1.0})));
    }
    assertFalse(registry.hasModel("mismatch"));
  }

  @Test
  void boundsChangesAfterRegistrationCannotBypassRequestSchema() {
    SurrogateMetadata metadata = boundedMetadata();
    registry.register("updated-bounds", dimensionedModel(2, 1, new double[] {1.0}), metadata);
    metadata.setInputBounds(new double[] {0.0}, new double[] {1.0});
    AtomicInteger fallbackCalls = new AtomicInteger();
    assertThrows(IllegalArgumentException.class, () -> registry.predictWithFallback("updated-bounds",
        new double[] {0.5, 0.5}, input -> new double[] {fallbackCalls.incrementAndGet()}));
    assertEquals(0, fallbackCalls.get());
    assertEquals(0, metadata.getPredictionCount());
    assertEquals(0, metadata.getFailureCount());
  }

  @Test
  void invalidPredictionsUsePhysicsAndCountAsFailures() {
    for (double[] output : invalidOutputs()) {
      SurrogateMetadata metadata = new SurrogateMetadata();
      AtomicInteger fallbackCalls = new AtomicInteger();
      registry.register("bad-prediction", input -> output, metadata);
      double[] result = registry.predictWithFallback("bad-prediction", new double[] {0.5}, values -> {
        fallbackCalls.incrementAndGet();
        return new double[] {42.0};
      });
      assertArrayEquals(new double[] {42.0}, result);
      assertEquals(1, fallbackCalls.get());
      assertEquals(0, metadata.getPredictionCount());
      assertEquals(1, metadata.getFailureCount());
      assertEquals(1.0, metadata.getFailureRate());
    }
  }

  @Test
  void wrongOutputDimensionUsesSameSchemaForBothPaths() {
    registry.register("output-schema", dimensionedModel(1, 2, new double[] {1.0}));
    assertArrayEquals(new double[] {2.0, 3.0},
        registry.predictWithFallback("output-schema", new double[] {1.0}, values -> new double[] {2.0, 3.0}));
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> registry.predictWithFallback("output-schema", new double[] {1.0}, values -> new double[] {2.0}));
    assertTrue(failure.getMessage().contains("fallback"));
    assertNotNull(failure.getCause());
    assertEquals(1, failure.getSuppressed().length);
  }

  @Test
  void invalidPhysicsOutputFailsExplicitlyAfterSurrogateFailure() {
    SurrogateMetadata metadata = new SurrogateMetadata();
    registry.register("both-fail", input -> {
      throw new IllegalStateException("surrogate unavailable");
    }, metadata);
    for (double[] output : invalidOutputs()) {
      IllegalStateException failure = assertThrows(IllegalStateException.class,
          () -> registry.predictWithFallback("both-fail", new double[] {1.0}, values -> output));
      assertTrue(failure.getMessage().contains("fallback"));
      assertEquals("surrogate unavailable", failure.getSuppressed()[0].getMessage());
    }
    assertEquals(invalidOutputs().length, metadata.getFailureCount());
    assertEquals(0, metadata.getPredictionCount());
  }

  @Test
  void preservesOriginalInputForFallbackAfterMutatingSurrogateFailure() {
    registry.register("mutating-model", input -> {
      input[0] = Double.NaN;
      throw new IllegalStateException("failed after mutation");
    });
    double[] input = {3.0};
    assertArrayEquals(new double[] {3.0}, registry.predictWithFallback("mutating-model", input, values -> values));
    assertArrayEquals(new double[] {3.0}, input);
  }

  @Test
  void outOfRangeInputUsesValidatedPhysicsWithoutSurrogatePrediction() {
    SurrogateMetadata metadata = boundedMetadata();
    AtomicInteger predictions = new AtomicInteger();
    registry.register("outside", input -> new double[] {predictions.incrementAndGet()}, metadata);
    assertArrayEquals(new double[] {42.0},
        registry.predictWithFallback("outside", new double[] {2.0, 0.5}, values -> new double[] {42.0}));
    assertThrows(IllegalStateException.class,
        () -> registry.predictWithFallback("outside", new double[] {2.0, 0.5}, values -> new double[] {Double.NaN}));
    assertEquals(0, predictions.get());
    assertEquals(0, metadata.getPredictionCount());
    assertEquals(0, metadata.getFailureCount());
    assertEquals(1.0, metadata.getExtrapolationRate());
  }

  @Test
  void disabledFallbackNeverInvokesPhysicsOrExtrapolates() {
    registry.setEnableFallback(false);
    AtomicInteger calls = new AtomicInteger();
    registry.register("bad", input -> new double[] {Double.NaN});
    assertThrows(IllegalStateException.class, () -> registry.predictWithFallback("bad", new double[] {0.5},
        values -> new double[] {calls.incrementAndGet()}));
    assertThrows(IllegalStateException.class, () -> registry.predictWithFallback("missing", new double[] {0.5},
        values -> new double[] {calls.incrementAndGet()}));
    registry.register("bounded", input -> new double[] {calls.incrementAndGet()}, boundedMetadata());
    assertThrows(IllegalStateException.class, () -> registry.predictWithFallback("bounded", new double[] {2.0, 0.5},
        values -> new double[] {calls.incrementAndGet()}));
    assertEquals(0, calls.get());
  }

  @Test
  void countersDistinguishSuccessFailureAndExtrapolation() {
    SurrogateMetadata metadata = boundedMetadata();
    AtomicInteger modelCalls = new AtomicInteger();
    registry.register("counters", input -> modelCalls.incrementAndGet() == 1 ? new double[] {1.0} : null, metadata);
    registry.predictWithFallback("counters", new double[] {0.5, 0.5}, values -> new double[] {42.0});
    registry.predictWithFallback("counters", new double[] {0.5, 0.5}, values -> new double[] {42.0});
    registry.predictWithFallback("counters", new double[] {0.5, 0.5}, values -> new double[] {42.0});
    registry.predictWithFallback("counters", new double[] {2.0, 0.5}, values -> new double[] {42.0});
    assertEquals(1, metadata.getPredictionCount());
    assertEquals(2, metadata.getFailureCount());
    assertEquals(2.0 / 3.0, metadata.getFailureRate());
    assertEquals(0.25, metadata.getExtrapolationRate());
    assertNotNull(metadata.getLastUsed());
  }

  @Test
  void validModelDoesNotRequireFallbackButNeededMissingFallbackFailsClearly() {
    registry.register("valid", input -> new double[] {1.0});
    assertArrayEquals(new double[] {1.0}, registry.predictWithFallback("valid", new double[] {2.0}, null));
    assertThrows(IllegalStateException.class, () -> registry.predictWithFallback("missing", new double[] {2.0}, null));
  }

  @Test
  void physicsExceptionKeepsBothFailureCauses() {
    IllegalArgumentException modelFailure = new IllegalArgumentException("inference failure");
    IllegalArgumentException physicsFailure = new IllegalArgumentException("physics failure");
    registry.register("exceptions", input -> {
      throw modelFailure;
    });
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> registry.predictWithFallback("exceptions", new double[] {1.0}, input -> {
          throw physicsFailure;
        }));
    assertEquals(physicsFailure, failure.getCause());
    assertEquals(modelFailure, failure.getSuppressed()[0]);
    assertEquals(1, registry.getMetadata("exceptions").get().getFailureCount());
  }

  @Test
  void persistedModelRetainsSchemaBoundsAndStatistics(@TempDir Path directory) throws Exception {
    SurrogateMetadata metadata = boundedMetadata();
    registry.register("saved", dimensionedModel(2, 1, new double[] {7.0}), metadata);
    registry.predictWithFallback("saved", new double[] {0.5, 0.5}, null);
    String file = directory.resolve("surrogate.ser").toString();
    registry.saveModel("saved", file);
    registry.loadModel("loaded", file);
    assertEquals(1, registry.getMetadata("loaded").get().getPredictionCount());
    assertArrayEquals(new double[] {7.0}, registry.predictWithFallback("loaded", new double[] {0.5, 0.5}, null));
    assertThrows(IllegalArgumentException.class,
        () -> registry.predictWithFallback("loaded", new double[] {0.5}, values -> new double[] {42.0}));
  }

  @Test
  void rejectsLegacySerializedInvalidBoundsBeforeReplacingModel(@TempDir Path directory) throws Exception {
    SurrogateMetadata metadata = boundedMetadata();
    registry.register("saved-invalid", dimensionedModel(2, 1, new double[] {7.0}), metadata);
    // Simulate a model file produced before bounds validation was introduced.
    Field minimum = SurrogateMetadata.class.getDeclaredField("inputMin");
    minimum.setAccessible(true);
    minimum.set(metadata, new double[] {Double.NaN, 0.0});
    String file = directory.resolve("invalid-surrogate.ser").toString();
    registry.saveModel("saved-invalid", file);
    registry.register("loaded", input -> new double[] {42.0});
    assertThrows(InvalidObjectException.class, () -> registry.loadModel("loaded", file));
    assertArrayEquals(new double[] {42.0}, registry.predictWithFallback("loaded", new double[] {1.0}, null));
  }

  private static SurrogateMetadata boundedMetadata() {
    SurrogateMetadata metadata = new SurrogateMetadata();
    metadata.setInputBounds(new double[] {0.0, 0.0}, new double[] {1.0, 1.0});
    return metadata;
  }

  private static double[][] invalidOutputs() {
    return new double[][] {null, {}, {Double.NaN}, {Double.POSITIVE_INFINITY}, {Double.NEGATIVE_INFINITY}};
  }

  private static SurrogateModel dimensionedModel(int inputs, int outputs, double[] prediction) {
    return new SurrogateModel() {
      private static final long serialVersionUID = 1L;

      @Override
      public double[] predict(double[] input) {
        return prediction;
      }

      @Override
      public int getInputDimension() {
        return inputs;
      }

      @Override
      public int getOutputDimension() {
        return outputs;
      }
    };
  }
}
