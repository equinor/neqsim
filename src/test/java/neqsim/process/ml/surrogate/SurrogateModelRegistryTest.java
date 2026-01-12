package neqsim.process.ml.surrogate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for SurrogateModelRegistry ML model management.
 */
public class SurrogateModelRegistryTest {

  private SurrogateModelRegistry registry;

  @BeforeEach
  void setUp() {
    registry = SurrogateModelRegistry.getInstance();
    registry.clear();
  }

  @AfterEach
  void tearDown() {
    registry.clear();
  }

  @Test
  void testSingletonInstance() {
    SurrogateModelRegistry instance1 = SurrogateModelRegistry.getInstance();
    SurrogateModelRegistry instance2 = SurrogateModelRegistry.getInstance();
    assertEquals(instance1, instance2);
  }

  @Test
  void testRegisterModel() {
    SurrogateModelRegistry.SurrogateModel model = input -> new double[] {input[0] * 2};

    registry.register("test-model", model);

    assertTrue(registry.hasModel("test-model"));
  }

  @Test
  void testGetModel() {
    SurrogateModelRegistry.SurrogateModel model = input -> new double[] {input[0] * 2};
    registry.register("test-model", model);

    Optional<SurrogateModelRegistry.SurrogateModel> retrieved = registry.get("test-model");

    assertTrue(retrieved.isPresent());
  }

  @Test
  void testGetNonExistentModel() {
    Optional<SurrogateModelRegistry.SurrogateModel> retrieved = registry.get("non-existent");

    assertFalse(retrieved.isPresent());
  }

  @Test
  void testUnregisterModel() {
    SurrogateModelRegistry.SurrogateModel model = input -> new double[] {input[0] * 2};
    registry.register("test-model", model);

    assertTrue(registry.hasModel("test-model"));

    boolean removed = registry.unregister("test-model");

    assertTrue(removed);
    assertFalse(registry.hasModel("test-model"));
  }

  @Test
  void testGetAllModels() {
    registry.register("model1", input -> new double[] {1.0});
    registry.register("model2", input -> new double[] {2.0});

    Map<String, SurrogateModelRegistry.SurrogateMetadata> allModels = registry.getAllModels();

    assertEquals(2, allModels.size());
    assertTrue(allModels.containsKey("model1"));
    assertTrue(allModels.containsKey("model2"));
  }

  @Test
  void testPredictWithFallback() {
    SurrogateModelRegistry.SurrogateModel model = input -> new double[] {input[0] * 2};
    registry.register("test-model", model);

    double[] input = new double[] {5.0};
    double[] result = registry.predictWithFallback("test-model", input, x -> new double[] {0.0});

    assertArrayEquals(new double[] {10.0}, result, 0.001);
  }

  @Test
  void testFallbackWhenModelNotRegistered() {
    double[] input = new double[] {5.0};
    double[] fallbackResult = new double[] {99.0};

    double[] result = registry.predictWithFallback("non-existent", input, x -> fallbackResult);

    assertArrayEquals(fallbackResult, result, 0.001);
  }

  @Test
  void testMetadataRetrieval() {
    SurrogateModelRegistry.SurrogateMetadata metadata =
        new SurrogateModelRegistry.SurrogateMetadata();
    metadata.setModelType("neural-network");
    metadata.setTrainingDataSource("flash-data-2024");

    registry.register("test-model", input -> new double[] {0.0}, metadata);

    Optional<SurrogateModelRegistry.SurrogateMetadata> retrieved =
        registry.getMetadata("test-model");

    assertTrue(retrieved.isPresent());
    assertEquals("neural-network", retrieved.get().getModelType());
  }

  @Test
  void testClear() {
    registry.register("model1", input -> new double[] {1.0});
    registry.register("model2", input -> new double[] {2.0});

    registry.clear();

    assertFalse(registry.hasModel("model1"));
    assertFalse(registry.hasModel("model2"));
  }

  @Test
  void testEnableFallback() {
    registry.setEnableFallback(true);
    assertTrue(registry.isEnableFallback());

    registry.setEnableFallback(false);
    assertFalse(registry.isEnableFallback());
  }

  @Test
  void testPersistenceDirectory() {
    registry.setPersistenceDirectory("/custom/path");
    assertEquals("/custom/path", registry.getPersistenceDirectory());
  }

  @Test
  void testMetadataInputBounds() {
    SurrogateModelRegistry.SurrogateMetadata metadata =
        new SurrogateModelRegistry.SurrogateMetadata();
    metadata.setInputBounds(new double[] {0.0, 0.0}, new double[] {100.0, 100.0});

    // Within bounds
    assertTrue(metadata.isInputValid(new double[] {50.0, 50.0}));

    // Outside bounds
    assertFalse(metadata.isInputValid(new double[] {150.0, 50.0}));
  }

  @Test
  void testMetadataNoInputBounds() {
    SurrogateModelRegistry.SurrogateMetadata metadata =
        new SurrogateModelRegistry.SurrogateMetadata();

    // Without bounds, any input should be considered valid
    assertTrue(metadata.isInputValid(new double[] {1000.0, -1000.0}));
  }

  @Test
  void testSurrogateModelInterface() {
    SurrogateModelRegistry.SurrogateModel model = new SurrogateModelRegistry.SurrogateModel() {
      @Override
      public double[] predict(double[] input) {
        return new double[] {input[0] + input[1]};
      }

      @Override
      public int getInputDimension() {
        return 2;
      }

      @Override
      public int getOutputDimension() {
        return 1;
      }
    };

    assertEquals(2, model.getInputDimension());
    assertEquals(1, model.getOutputDimension());
    assertArrayEquals(new double[] {7.0}, model.predict(new double[] {3.0, 4.0}), 0.001);
  }

  @Test
  void testDefaultDimensions() {
    SurrogateModelRegistry.SurrogateModel model = input -> new double[] {0.0};

    assertEquals(-1, model.getInputDimension());
    assertEquals(-1, model.getOutputDimension());
  }

  @Test
  void testMetadataTrainedAt() {
    SurrogateModelRegistry.SurrogateMetadata metadata =
        new SurrogateModelRegistry.SurrogateMetadata();

    assertNotNull(metadata.getTrainedAt());
  }
}
