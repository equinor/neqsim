package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.CompressorThermalNode.NodeType;

/** Tests for the compressor lumped thermal-network solver. */
public class CompressorThermalModelTest {

  @Test
  void solvesAnalyticalThreeNodeSteadyState() {
    CompressorThermalModel model = new CompressorThermalModel("analytical network");
    model.addNode(new CompressorThermalNode("cold", NodeType.FLUID_BOUNDARY, 300.0, 0.0, true));
    model.addNode(new CompressorThermalNode("hot", NodeType.FLUID_BOUNDARY, 400.0, 0.0, true));
    CompressorThermalNode metal = new CompressorThermalNode("metal", NodeType.SHAFT, 320.0, 1000.0, false);
    metal.setHeatGenerationW(100.0);
    model.addNode(metal);
    model.addLink(new CompressorThermalLink("cold", "metal", 10.0));
    model.addLink(new CompressorThermalLink("hot", "metal", 10.0));

    model.solveSteadyState();

    assertEquals(355.0, model.getTemperature("metal", "K"), 1.0e-10);
    assertEquals(81.85, model.getTemperature("metal", "C"), 1.0e-10);
  }

  @Test
  void implicitTransientStepMatchesEnergyBalance() {
    CompressorThermalModel model = new CompressorThermalModel("first order network");
    model.addNode(new CompressorThermalNode("boundary", NodeType.FLUID_BOUNDARY, 400.0, 0.0, true));
    model.addNode(new CompressorThermalNode("metal", NodeType.SHAFT, 300.0, 1000.0, false));
    model.addLink(new CompressorThermalLink("boundary", "metal", 10.0));

    model.solveTransient(100.0);

    assertEquals(350.0, model.getTemperature("metal", "K"), 1.0e-10);
    model.solveTransient(100.0);
    assertEquals(375.0, model.getTemperature("metal", "K"), 1.0e-10);
  }

  @Test
  void detectsInvalidAndSingularNetworks() {
    assertThrows(IllegalArgumentException.class,
        () -> new CompressorThermalLink("a", "b", 0.0));

    CompressorThermalModel noBoundary = new CompressorThermalModel();
    noBoundary.addNode(new CompressorThermalNode("metal", NodeType.SHAFT, 300.0, 1000.0, false));
    assertThrows(IllegalStateException.class, noBoundary::solveSteadyState);

    CompressorThermalModel isolated = new CompressorThermalModel();
    isolated.addNode(new CompressorThermalNode("boundary", NodeType.FLUID_BOUNDARY, 300.0, 0.0, true));
    isolated.addNode(new CompressorThermalNode("metal", NodeType.SHAFT, 300.0, 1000.0, false));
    assertThrows(IllegalStateException.class, isolated::solveSteadyState);
  }

  @Test
  void jsonRoundTripCreatesIndependentNetwork() {
    CompressorThermalModel original = CompressorCatalog.createDefaultCatalog()
        .get("generic-centrifugal-single-stage").getThermalModel();
    CompressorThermalModel restored = CompressorThermalModel.fromJson(original.toJson());

    restored.getNode(CompressorThermalModel.AMBIENT).setTemperatureK(310.0);

    assertEquals(298.15, original.getTemperature(CompressorThermalModel.AMBIENT, "K"), 1.0e-12);
    assertEquals(310.0, restored.getTemperature(CompressorThermalModel.AMBIENT, "K"), 1.0e-12);
    assertTrue(restored.getLinks().size() >= 10);
  }
}
