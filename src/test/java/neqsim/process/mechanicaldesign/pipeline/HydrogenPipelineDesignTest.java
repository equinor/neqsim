package neqsim.process.mechanicaldesign.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for HydrogenPipelineDesign class.
 */
public class HydrogenPipelineDesignTest extends neqsim.NeqSimTest {

  @Test
  public void testWallThicknessCalculation() {
    HydrogenPipelineDesign design = new HydrogenPipelineDesign(null);
    design.setOuterDiameter(0.508); // 20 inch
    design.setDesignPressure(100.0);
    design.setDesignTemperature(60.0);
    design.setMaterialGrade("X52");
    design.setHydrogenMoleFraction(1.0);
    design.setDesignFactor(0.50);
    design.setCorrosionAllowance(1.0);
    design.calcDesign();

    double wt = design.getRequiredWallThickness();
    assertTrue(wt > 5.0, "Wall thickness should be > 5 mm for pure H2 at 100 bar");
    assertTrue(wt < 50.0, "Wall thickness should be reasonable");
  }

  @Test
  public void testMaterialCompatibility() {
    // X52 should be compatible
    HydrogenPipelineDesign design = new HydrogenPipelineDesign(null);
    design.setMaterialGrade("X52");
    design.setHydrogenMoleFraction(1.0);
    design.setDesignPressure(100.0);
    design.calcDesign();
    assertTrue(design.isMaterialCompatible(), "X52 should be compatible with H2");

    // X70 should NOT be compatible
    HydrogenPipelineDesign designX70 = new HydrogenPipelineDesign(null);
    designX70.setMaterialGrade("X70");
    designX70.setHydrogenMoleFraction(1.0);
    designX70.setDesignPressure(100.0);
    designX70.calcDesign();
    assertFalse(designX70.isMaterialCompatible(), "X70 should NOT be compatible with H2");
  }

  @Test
  public void testHydrogenDeratingFactor() {
    HydrogenPipelineDesign design = new HydrogenPipelineDesign(null);
    design.setHydrogenMoleFraction(1.0);
    design.setMaterialGrade("X42");
    design.setDesignPressure(100.0);
    design.calcDesign();
    assertEquals(1.0, design.getHydrogenDeratingFactor(), 0.01);

    design.setMaterialGrade("X65");
    design.calcDesign();
    assertEquals(0.85, design.getHydrogenDeratingFactor(), 0.02);
  }

  @Test
  public void testPartialHydrogenBlend() {
    // 20% hydrogen blend should have minimal derating
    HydrogenPipelineDesign design = new HydrogenPipelineDesign(null);
    design.setMaterialGrade("X65");
    design.setDesignPressure(100.0);
    design.setHydrogenMoleFraction(0.20);
    design.calcDesign();

    double factor = design.getHydrogenDeratingFactor();
    assertTrue(factor > 0.90, "Partial blend should have derating > 0.90");
    assertTrue(factor < 1.0, "Should still have some derating");
  }

  @Test
  public void testPermeationRate() {
    HydrogenPipelineDesign design = new HydrogenPipelineDesign(null);
    design.setOuterDiameter(0.508);
    design.setMaterialGrade("X52");
    design.setDesignPressure(100.0);
    design.setHydrogenMoleFraction(1.0);
    design.calcDesign();

    double permeation = design.getPermeationRate();
    assertTrue(permeation > 0, "Permeation through steel should be > 0");
    assertTrue(permeation < 1e-5, "Permeation should be small");
  }

  @Test
  public void testDesignResults() {
    HydrogenPipelineDesign design = new HydrogenPipelineDesign(null);
    design.setOuterDiameter(0.508);
    design.setDesignPressure(100.0);
    design.setDesignTemperature(60.0);
    design.setMaterialGrade("X52");
    design.setHydrogenMoleFraction(1.0);
    design.calcDesign();

    Map<String, Object> results = design.getDesignResults();
    assertTrue(results.containsKey("requiredWallThickness_mm"));
    assertTrue(results.containsKey("designStandard"));
    assertEquals("ASME B31.12", results.get("designStandard"));
  }
}
