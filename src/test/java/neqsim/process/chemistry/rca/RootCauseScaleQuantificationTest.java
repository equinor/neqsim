package neqsim.process.chemistry.rca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link RootCauseAnalyser} quantifies a scale deposit with the coupled multi-mineral equilibrium and names
 * the dominant mineral.
 */
public class RootCauseScaleQuantificationTest {

  @Test
  void testBariteScaleQuantifiedInEvidence() {
    RootCauseAnalyser rca = new RootCauseAnalyser();
    rca.setTemperatureCelsius(70.0);
    rca.setPressureBara(100.0);
    rca.setCalciumMgL(500.0);
    rca.setBariumMgL(300.0);
    rca.setSulphateMgL(2000.0);
    rca.setBicarbonateMgL(300.0);
    rca.setTotalDissolvedSolidsMgL(60000.0);
    rca.setCO2PartialPressureBar(1.0);
    rca.addSymptom(new Symptom(Symptom.Category.DEPOSIT, "Hard white scale found in the choke")
        .withMeasurement("depositMassGrams", 120.0));
    rca.analyse();

    RootCauseCandidate primary = rca.getPrimary();
    assertNotNull(primary);
    assertEquals("MINERAL_SCALE", primary.getCode());
    // Evidence must carry the quantitative coupled-equilibrium result and the dominant mineral.
    assertTrue(primary.getEvidence().contains("Coupled scale equilibrium"),
        "Evidence should reference the coupled equilibrium: " + primary.getEvidence());
    assertTrue(primary.getEvidence().contains("BaSO4"),
        "Barite should be identified as the dominant scale: " + primary.getEvidence());
    assertTrue(primary.getScore() > 0.6, "Quantified scale should score highly");
  }

  @Test
  void testUndersaturatedBrineDownweightsScale() {
    // Deposit reported, but the brine (with anion data) is undersaturated -> scale is downweighted.
    RootCauseAnalyser rca = new RootCauseAnalyser();
    rca.setTemperatureCelsius(40.0);
    rca.setPressureBara(50.0);
    rca.setCalciumMgL(50.0);
    rca.setBariumMgL(0.1);
    rca.setSulphateMgL(5.0);
    rca.setBicarbonateMgL(20.0);
    rca.setTotalDissolvedSolidsMgL(5000.0);
    rca.setCO2PartialPressureBar(0.5);
    rca.addSymptom(new Symptom(Symptom.Category.DEPOSIT, "White crystalline deposit"));
    rca.analyse();

    boolean found = false;
    for (RootCauseCandidate c : rca.getCandidates()) {
      if ("MINERAL_SCALE".equals(c.getCode())) {
        found = true;
        assertTrue(c.getEvidence().contains("undersaturated"),
            "Undersaturated brine should be flagged: " + c.getEvidence());
      }
    }
    assertTrue(found, "A MINERAL_SCALE candidate should still be listed for follow-up");
  }
}
