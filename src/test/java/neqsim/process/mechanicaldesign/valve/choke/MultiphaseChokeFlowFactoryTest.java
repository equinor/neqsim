package neqsim.process.mechanicaldesign.valve.choke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for the MultiphaseChokeFlowFactory.
 *
 * @author esol
 */
public class MultiphaseChokeFlowFactoryTest {

  @Test
  void testCreateSachdevaModel() {
    MultiphaseChokeFlow model =
        MultiphaseChokeFlowFactory.createModel(MultiphaseChokeFlowFactory.ModelType.SACHDEVA);

    assertNotNull(model);
    assertTrue(model instanceof SachdevaChokeFlow);
    assertEquals("Sachdeva et al. (1986)", model.getModelName());
  }

  @Test
  void testCreateGilbertModel() {
    MultiphaseChokeFlow model =
        MultiphaseChokeFlowFactory.createModel(MultiphaseChokeFlowFactory.ModelType.GILBERT);

    assertNotNull(model);
    assertTrue(model instanceof GilbertChokeFlow);
    assertEquals("Gilbert (1954)", model.getModelName());
  }

  @Test
  void testCreateBaxendellModel() {
    MultiphaseChokeFlow model =
        MultiphaseChokeFlowFactory.createModel(MultiphaseChokeFlowFactory.ModelType.BAXENDELL);

    assertNotNull(model);
    assertTrue(model instanceof GilbertChokeFlow);
    assertEquals("Baxendell (1958)", model.getModelName());
  }

  @Test
  void testCreateRosModel() {
    MultiphaseChokeFlow model =
        MultiphaseChokeFlowFactory.createModel(MultiphaseChokeFlowFactory.ModelType.ROS);

    assertNotNull(model);
    assertTrue(model instanceof GilbertChokeFlow);
    assertEquals("Ros (1960)", model.getModelName());
  }

  @Test
  void testCreateAchongModel() {
    MultiphaseChokeFlow model =
        MultiphaseChokeFlowFactory.createModel(MultiphaseChokeFlowFactory.ModelType.ACHONG);

    assertNotNull(model);
    assertTrue(model instanceof GilbertChokeFlow);
    assertEquals("Achong (1961)", model.getModelName());
  }

  @Test
  void testCreateModelWithDiameter() {
    double diameter = 0.0254; // 1 inch
    MultiphaseChokeFlow model = MultiphaseChokeFlowFactory
        .createModel(MultiphaseChokeFlowFactory.ModelType.SACHDEVA, diameter);

    assertNotNull(model);
    assertEquals(diameter, model.getChokeDiameter(), 1e-6);
  }

  @Test
  void testCreateModelFromString() {
    // Test various string inputs
    MultiphaseChokeFlow sachdeva = MultiphaseChokeFlowFactory.createModel("sachdeva");
    assertEquals("Sachdeva et al. (1986)", sachdeva.getModelName());

    MultiphaseChokeFlow gilbert = MultiphaseChokeFlowFactory.createModel("gilbert");
    assertEquals("Gilbert (1954)", gilbert.getModelName());

    MultiphaseChokeFlow baxendell = MultiphaseChokeFlowFactory.createModel("baxendell");
    assertEquals("Baxendell (1958)", baxendell.getModelName());

    MultiphaseChokeFlow ros = MultiphaseChokeFlowFactory.createModel("ros");
    assertEquals("Ros (1960)", ros.getModelName());

    MultiphaseChokeFlow achong = MultiphaseChokeFlowFactory.createModel("achong");
    assertEquals("Achong (1961)", achong.getModelName());
  }

  @Test
  void testCreateModelFromStringCaseInsensitive() {
    MultiphaseChokeFlow model1 = MultiphaseChokeFlowFactory.createModel("SACHDEVA");
    MultiphaseChokeFlow model2 = MultiphaseChokeFlowFactory.createModel("Sachdeva");
    MultiphaseChokeFlow model3 = MultiphaseChokeFlowFactory.createModel("sachdeva1986");

    assertEquals(model1.getModelName(), model2.getModelName());
    assertEquals(model2.getModelName(), model3.getModelName());
  }

  @Test
  void testCreateDefaultModel() {
    MultiphaseChokeFlow model = MultiphaseChokeFlowFactory.createDefaultModel();

    assertNotNull(model);
    assertTrue(model instanceof SachdevaChokeFlow);
  }

  @Test
  void testUnknownModelDefaultsToSachdeva() {
    MultiphaseChokeFlow model = MultiphaseChokeFlowFactory.createModel("unknown_model");

    assertNotNull(model);
    assertTrue(model instanceof SachdevaChokeFlow);
  }

  @Test
  void testRecommendModelSubcritical() {
    // Subcritical flow should always recommend Sachdeva
    MultiphaseChokeFlowFactory.ModelType recommended =
        MultiphaseChokeFlowFactory.recommendModel(1000, false);

    assertEquals(MultiphaseChokeFlowFactory.ModelType.SACHDEVA, recommended);
  }

  @Test
  void testRecommendModelHighGLR() {
    // High GLR critical flow should recommend Achong
    MultiphaseChokeFlowFactory.ModelType recommended =
        MultiphaseChokeFlowFactory.recommendModel(10000, true);

    assertEquals(MultiphaseChokeFlowFactory.ModelType.ACHONG, recommended);
  }

  @Test
  void testRecommendModelLowGLR() {
    // Low GLR critical flow should recommend Gilbert
    MultiphaseChokeFlowFactory.ModelType recommended =
        MultiphaseChokeFlowFactory.recommendModel(50, true);

    assertEquals(MultiphaseChokeFlowFactory.ModelType.GILBERT, recommended);
  }

  @Test
  void testRecommendModelModerateGLR() {
    // Moderate GLR critical flow should recommend Sachdeva
    MultiphaseChokeFlowFactory.ModelType recommended =
        MultiphaseChokeFlowFactory.recommendModel(500, true);

    assertEquals(MultiphaseChokeFlowFactory.ModelType.SACHDEVA, recommended);
  }
}
