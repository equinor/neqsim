package neqsim.process.equipment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for FloatingSubstructure class.
 *
 * @author esol
 * @version 1.0
 */
class FloatingSubstructureTest {

  @Test
  void testSemiSubmersibleHydrostatics() {
    FloatingSubstructure fs = new FloatingSubstructure("Test Semi-Sub");
    fs.setConceptType(FloatingSubstructure.ConceptType.SEMI_SUBMERSIBLE);
    fs.setTurbineMass(1200.0);
    fs.setTowerMass(800.0);
    fs.setHubHeight(135.0);
    fs.setWaterDepth(265.0);
    fs.setSeawaterDensity(1025.0);
    fs.setNumberOfColumns(3);
    fs.setColumnDiameter(15.0);
    fs.setColumnHeight(35.0);
    fs.setColumnSpacing(70.0);
    fs.setPontoonWidth(10.0);
    fs.setPontoonHeight(5.0);
    fs.setDesignWindThrust(3000.0);
    fs.run();

    // Draft must be between pontoon height and column height
    assertTrue(fs.getDraft() > 5.0, "Draft should exceed pontoon height");
    assertTrue(fs.getDraft() < 35.0, "Draft should not exceed column height");

    // Displacement must equal total mass (hydrostatic equilibrium)
    assertEquals(fs.getTotalMass(), fs.getDisplacement(), 1.0,
        "Displacement must equal total mass");

    // GM must be positive for stable floating body
    assertTrue(fs.getMetacentricHeight() > 0, "GM must be positive for stability");

    // Freeboard must be positive
    assertTrue(fs.getFreeboard() > 0, "Freeboard must be positive");

    // Natural periods should be reasonable
    assertTrue(fs.getHeaveNaturalPeriod() > 5.0, "Heave period should exceed 5 s");
    assertTrue(fs.getHeaveNaturalPeriod() < 60.0, "Heave period should be below 60 s");

    // Steel weight should be reasonable (1000-5000 tonnes)
    assertTrue(fs.getSteelWeight() > 500, "Steel weight too low");
    assertTrue(fs.getSteelWeight() < 10000, "Steel weight too high");
  }

  @Test
  void testSparHydrostatics() {
    FloatingSubstructure fs = new FloatingSubstructure("Test Spar");
    fs.setConceptType(FloatingSubstructure.ConceptType.SPAR);
    fs.setTurbineMass(1200.0);
    fs.setTowerMass(800.0);
    fs.setHubHeight(135.0);
    fs.setColumnDiameter(15.0);
    fs.setSparHeight(80.0);
    fs.setWaterDepth(265.0);
    fs.run();

    // Spar should have deep draft (>60% of height)
    assertTrue(fs.getDraft() > 50.0, "Spar draft should be deep");

    // GM may be small for spar (stability from low KG)
    // But displacement must equal mass
    assertEquals(fs.getTotalMass(), fs.getDisplacement(), 1.0);

    assertTrue(fs.getSteelWeight() > 500, "Spar steel weight too low");
  }

  @Test
  void testBargeHydrostatics() {
    FloatingSubstructure fs = new FloatingSubstructure("Test Barge");
    fs.setConceptType(FloatingSubstructure.ConceptType.BARGE);
    fs.setTurbineMass(1200.0);
    fs.setTowerMass(800.0);
    fs.setHubHeight(135.0);
    fs.setBargeLength(50.0);
    fs.setBargeWidth(50.0);
    fs.setBargeDepth(10.0);
    fs.setWaterDepth(265.0);
    fs.run();

    // Barge should have large waterplane area
    assertTrue(fs.getWaterplaneArea() > 2000.0, "Barge waterplane area should be large");

    // GM should be large (wide waterplane)
    assertTrue(fs.getMetacentricHeight() > 5.0, "Barge GM should be large due to wide waterplane");

    assertEquals(fs.getTotalMass(), fs.getDisplacement(), 1.0);
  }

  @Test
  void testStabilityCheck() {
    FloatingSubstructure fs = new FloatingSubstructure("Stability Check");
    fs.setConceptType(FloatingSubstructure.ConceptType.SEMI_SUBMERSIBLE);
    fs.setTurbineMass(1200.0);
    fs.setTowerMass(800.0);
    fs.setNumberOfColumns(3);
    fs.setColumnDiameter(15.0);
    fs.setColumnHeight(35.0);
    fs.setColumnSpacing(70.0);
    fs.setPontoonWidth(10.0);
    fs.setPontoonHeight(5.0);
    fs.run();

    // A well-designed semi-sub should pass stability check
    // (GM > 1.0 m per DNV-ST-0119)
    assertTrue(fs.isStabilityAdequate(), "Semi-sub should have adequate stability (GM > 1.0 m)");
  }

  @Test
  void testDesignResultsMap() {
    FloatingSubstructure fs = new FloatingSubstructure("Results Test");
    fs.setConceptType(FloatingSubstructure.ConceptType.SEMI_SUBMERSIBLE);
    fs.setTurbineMass(1200.0);
    fs.setTowerMass(800.0);
    fs.run();

    Map<String, Object> results = fs.getDesignResults();
    assertTrue(results.containsKey("conceptType"));
    assertTrue(results.containsKey("GM_m"));
    assertTrue(results.containsKey("draft_m"));
    assertTrue(results.containsKey("displacement_tonnes"));
    assertTrue(results.containsKey("estimatedCost_MNOK"));
    assertEquals("Semi-submersible", results.get("conceptType"));
  }

  @Test
  void testTLP() {
    FloatingSubstructure fs = new FloatingSubstructure("Test TLP");
    fs.setConceptType(FloatingSubstructure.ConceptType.TLP);
    fs.setTurbineMass(1200.0);
    fs.setTowerMass(800.0);
    fs.setNumberOfColumns(3);
    fs.setColumnDiameter(15.0);
    fs.setColumnHeight(35.0);
    fs.setColumnSpacing(70.0);
    fs.run();

    // TLP should have excess buoyancy (positive)
    assertTrue(fs.getExcessBuoyancy() > 0,
        "TLP must have positive excess buoyancy for tendon pretension");
  }
}
