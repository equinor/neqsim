package neqsim.util.nucleation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for {@link SpinodalDecompositionDetector}.
 *
 * @author esol
 * @version 1.0
 */
class SpinodalDecompositionDetectorTest {

  @Test
  void testStableSinglePhaseSystem() {
    // High T, moderate P — well above cricondentherm — single phase gas
    SystemInterface system = new SystemSrkEos(350.0, 30.0); // 77 C, 30 bara
    system.addComponent("methane", 0.90);
    system.addComponent("ethane", 0.10);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    assertTrue(detector.isAnalyzed());
    // Verify the detector produces a definite state (not UNKNOWN)
    assertFalse(
        detector.getStabilityState() == SpinodalDecompositionDetector.StabilityState.UNKNOWN,
        "State should not be UNKNOWN for a valid system");
    // Min eigenvalue should be finite
    assertTrue(Double.isFinite(detector.getMinEigenvalue()));
    // Recommendation should be non-empty
    assertNotNull(detector.getRecommendation());
    assertFalse(detector.getRecommendation().isEmpty());
  }

  @Test
  void testTwoPhaseMetastable() {
    // System that flashes to two phases = metastable or unstable
    SystemInterface system = new SystemSrkEos(253.15, 30.0); // -20 C, 30 bara
    system.addComponent("methane", 0.80);
    system.addComponent("n-heptane", 0.20);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    assertTrue(detector.isAnalyzed());
    // With two phases, should be metastable or unstable (depending on Hessian)
    assertFalse(
        detector.getStabilityState() == SpinodalDecompositionDetector.StabilityState.UNKNOWN,
        "State should not be UNKNOWN for a valid two-phase system");
  }

  @Test
  void testHessianMatrixComputed() {
    SystemInterface system = new SystemSrkEos(273.15, 50.0);
    system.addComponent("methane", 0.70);
    system.addComponent("propane", 0.30);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    double[][] hessian = detector.getHessianMatrix();
    assertNotNull(hessian);
    assertTrue(hessian.length >= 2, "Hessian should have rows for each component");
    assertEquals(hessian.length, hessian[0].length, "Hessian should be square");
  }

  @Test
  void testMinEigenvalue() {
    SystemInterface system = new SystemSrkEos(273.15, 50.0);
    system.addComponent("methane", 0.70);
    system.addComponent("n-hexane", 0.30);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    // minEigenvalue should be finite
    double eig = detector.getMinEigenvalue();
    assertTrue(Double.isFinite(eig), "Min eigenvalue should be finite");
    // Stability margin should match eigenvalue
    assertEquals(eig, detector.getStabilityMargin(), 1e-20);
  }

  @Test
  void testPureComponentFallback() {
    // Pure component — should not crash
    SystemInterface system = new SystemSrkEos(300.0, 50.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    assertTrue(detector.isAnalyzed());
    // Pure component at normal conditions should be stable
    assertTrue(detector.isStable() || detector.isMetastable(),
        "Pure component should be stable or metastable");
  }

  @Test
  void testPhaseIndexSetter() {
    SystemInterface system = new SystemSrkEos(253.15, 30.0);
    system.addComponent("methane", 0.80);
    system.addComponent("n-heptane", 0.20);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.setPhaseIndex(0);
    detector.analyze();
    assertTrue(detector.isAnalyzed());

    // Reset and analyze phase 1 (if exists)
    if (system.getNumberOfPhases() > 1) {
      SpinodalDecompositionDetector detector2 = new SpinodalDecompositionDetector(system);
      detector2.setPhaseIndex(1);
      detector2.analyze();
      assertTrue(detector2.isAnalyzed());
    }
  }

  @Test
  void testGetRecommendation() {
    SystemInterface system = new SystemSrkEos(350.0, 30.0);
    system.addComponent("methane", 0.90);
    system.addComponent("ethane", 0.10);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    String rec = detector.getRecommendation();
    assertNotNull(rec);
    assertFalse(rec.isEmpty());
    assertTrue(rec.contains("stable") || rec.contains("metastable") || rec.contains("spinodal")
        || rec.contains("determined"), "Recommendation should mention stability state");
  }

  @Test
  void testToMap() {
    SystemInterface system = new SystemSrkEos(273.15, 30.0);
    system.addComponent("methane", 0.70);
    system.addComponent("propane", 0.30);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    Map<String, Object> map = detector.toMap();
    assertNotNull(map);
    assertTrue(map.containsKey("stabilityState"));
    assertTrue(map.containsKey("isInsideSpinodal"));
    assertTrue(map.containsKey("recommendation"));
    assertTrue(map.containsKey("systemConditions"));
  }

  @Test
  void testToJson() {
    SystemInterface system = new SystemSrkEos(273.15, 30.0);
    system.addComponent("methane", 0.70);
    system.addComponent("propane", 0.30);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    String json = detector.toJson();
    assertNotNull(json);
    assertTrue(json.contains("stabilityState"));
    assertTrue(json.contains("temperature_K"));
    assertTrue(json.contains("pressure_bara"));
  }

  @Test
  void testToString() {
    SystemInterface system = new SystemSrkEos(300.0, 50.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    String before = detector.toString();
    assertTrue(before.contains("not analyzed"));

    detector.analyze();
    String after = detector.toString();
    assertTrue(after.contains("SpinodalDetector"));
    assertTrue(after.contains("state="));
  }

  @Test
  void testMulticomponentSystem() {
    // Test with more components to exercise larger Hessian / Gershgorin path
    SystemInterface system = new SystemSrkEos(253.15, 30.0);
    system.addComponent("methane", 0.60);
    system.addComponent("ethane", 0.10);
    system.addComponent("propane", 0.10);
    system.addComponent("n-butane", 0.10);
    system.addComponent("n-pentane", 0.10);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    assertTrue(detector.isAnalyzed());
    double[][] hessian = detector.getHessianMatrix();
    assertNotNull(hessian);
    assertEquals(5, hessian.length, "Hessian should be 5x5 for 5 components");

    // If unstable, dominant wavelength should be set
    if (detector.isInsideSpinodal()) {
      assertTrue(detector.getDominantWavelength() > 0.0,
          "Dominant wavelength should be positive inside spinodal");
    }
  }

  @Test
  void testUnstableComponentPair() {
    SystemInterface system = new SystemSrkEos(253.15, 30.0);
    system.addComponent("methane", 0.70);
    system.addComponent("n-heptane", 0.30);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();

    SpinodalDecompositionDetector detector = new SpinodalDecompositionDetector(system);
    detector.analyze();

    String pair = detector.getUnstableComponentPair();
    assertNotNull(pair);
    // For a binary, the pair should contain both component names
    if (!pair.isEmpty()) {
      assertTrue(pair.contains("/"), "Component pair should contain '/' separator");
    }
  }
}
