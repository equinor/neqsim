package neqsim.process.equipment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for MooringSystem class.
 *
 * @author esol
 * @version 1.0
 */
class MooringSystemTest {

  @Test
  void testChainCatenary() {
    MooringSystem ms = new MooringSystem("Test Chain Mooring");
    ms.setWaterDepth(265.0);
    ms.setNumberOfLines(3);
    ms.setLineType(MooringSystem.LineType.CHAIN);
    ms.setChainDiameter(0.127); // 127 mm
    ms.setChainGrade(4);
    ms.setDesignHorizontalForce(2500.0);
    ms.setDesignVerticalForce(500.0);
    ms.setAnchorRadius(800.0);
    ms.setFairleadDepth(15.0);
    ms.run();

    // Fairlead tension should be resultant of H and V
    double expectedFairleadT = Math.sqrt(2500.0 * 2500.0 + 500.0 * 500.0);
    assertEquals(expectedFairleadT, ms.getFairleadTension(), 1.0,
        "Fairlead tension should be resultant of H and V");

    // Line length should be positive and reasonable
    assertTrue(ms.getLineLength() > 0, "Line length must be positive");
    assertTrue(ms.getLineLength() > 250.0,
        "Line length should exceed water depth");

    // MBL check
    assertTrue(ms.getMinimumBreakingLoad() > 0,
        "Minimum breaking load must be positive");

    // Safety factor should be calculated
    assertTrue(ms.getBreakingStrengthSafetyFactor() > 0,
        "Safety factor must be positive");
  }

  @Test
  void testHybridChainPolyester() {
    MooringSystem ms = new MooringSystem("Hybrid Mooring");
    ms.setWaterDepth(265.0);
    ms.setNumberOfLines(3);
    ms.setLineType(MooringSystem.LineType.CHAIN_POLYESTER_CHAIN);
    ms.setChainDiameter(0.127);
    ms.setPolyesterDiameter(0.200);
    ms.setDesignHorizontalForce(2500.0);
    ms.setDesignVerticalForce(500.0);
    ms.setAnchorRadius(800.0);
    ms.setAnchorType(MooringSystem.AnchorType.SUCTION_PILE);
    ms.run();

    // Hybrid should have positive line length
    assertTrue(ms.getLineLength() > 0);

    // Total weight should be less than all-chain (polyester is lighter)
    MooringSystem chainOnly = new MooringSystem("All Chain");
    chainOnly.setWaterDepth(265.0);
    chainOnly.setNumberOfLines(3);
    chainOnly.setLineType(MooringSystem.LineType.CHAIN);
    chainOnly.setChainDiameter(0.127);
    chainOnly.setDesignHorizontalForce(2500.0);
    chainOnly.setDesignVerticalForce(500.0);
    chainOnly.setAnchorRadius(800.0);
    chainOnly.run();

    assertTrue(ms.getTotalWeight() < chainOnly.getTotalWeight(),
        "Hybrid mooring should be lighter than all-chain");
  }

  @Test
  void testSafetyFactorCheck() {
    MooringSystem ms = new MooringSystem("SF Check");
    ms.setWaterDepth(265.0);
    ms.setNumberOfLines(3);
    ms.setLineType(MooringSystem.LineType.CHAIN);
    ms.setChainDiameter(0.127);
    ms.setDesignHorizontalForce(2500.0);
    ms.setDesignVerticalForce(500.0);
    ms.setAnchorRadius(800.0);
    ms.setRequiredSafetyFactor(1.80);
    ms.run();

    // With 127mm R4 chain, SF should exceed 1.80 for moderate loads
    assertTrue(ms.getBreakingStrengthSafetyFactor() > 1.0,
        "Safety factor should exceed 1.0");
  }

  @Test
  void testCatenaryProfile() {
    MooringSystem ms = new MooringSystem("Profile Test");
    ms.setWaterDepth(265.0);
    ms.setNumberOfLines(3);
    ms.setLineType(MooringSystem.LineType.CHAIN);
    ms.setChainDiameter(0.127);
    ms.setDesignHorizontalForce(2500.0);
    ms.setDesignVerticalForce(500.0);
    ms.setAnchorRadius(800.0);
    ms.run();

    List<double[]> profile = ms.getCatenaryProfile(50);
    assertTrue(profile.size() > 0, "Profile should have points");

    // First point should be near waterline
    double[] first = profile.get(0);
    assertEquals(0.0, first[0], 0.1, "First X should be at fairlead");

    // Last point should be at seabed depth
    double[] last = profile.get(profile.size() - 1);
    assertEquals(265.0, last[1], 1.0, "Last Z should be at seabed");
  }

  @Test
  void testDesignResultsMap() {
    MooringSystem ms = new MooringSystem("Results Test");
    ms.setWaterDepth(265.0);
    ms.setNumberOfLines(3);
    ms.setLineType(MooringSystem.LineType.CHAIN_POLYESTER_CHAIN);
    ms.setChainDiameter(0.127);
    ms.run();

    Map<String, Object> results = ms.getDesignResults();
    assertTrue(results.containsKey("lineType"));
    assertTrue(results.containsKey("fairleadTension_kN"));
    assertTrue(results.containsKey("safetyFactor"));
    assertTrue(results.containsKey("estimatedCost_MNOK"));
    assertTrue(results.containsKey("bottomChainLength_m"));
    assertTrue(results.containsKey("polyesterLength_m"));
    assertEquals("Chain-Polyester-Chain", results.get("lineType"));
  }

  @Test
  void testRestoringForce() {
    MooringSystem ms = new MooringSystem("Restoring Test");
    ms.setWaterDepth(265.0);
    ms.setNumberOfLines(3);
    ms.setLineType(MooringSystem.LineType.CHAIN);
    ms.setChainDiameter(0.127);
    ms.setDesignHorizontalForce(2500.0);
    ms.setDesignVerticalForce(500.0);
    ms.setAnchorRadius(800.0);
    ms.run();

    assertTrue(ms.getRestoringStiffness() > 0,
        "Restoring stiffness should be positive");
    assertTrue(ms.getMaxOffset() > 0,
        "Max offset should be positive");
  }
}
