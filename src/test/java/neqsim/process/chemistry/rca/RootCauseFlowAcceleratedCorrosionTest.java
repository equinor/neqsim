package neqsim.process.chemistry.rca;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link RootCauseAnalyser} recognises flow-accelerated corrosion as a candidate distinct from
 * erosion-corrosion.
 *
 * @author ESOL
 * @version 1.0
 */
public class RootCauseFlowAcceleratedCorrosionTest {

  /**
   * Reports whether a candidate with the given code is present.
   *
   * @param candidates the ranked candidate list
   * @param code the candidate code to look for
   * @return true if a candidate with that code is present
   */
  private boolean hasCode(List<RootCauseCandidate> candidates, String code) {
    for (int i = 0; i < candidates.size(); i++) {
      if (code.equals(candidates.get(i).getCode())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Build an analyser configured for a hot, deaerated, carbon-steel closed loop.
   *
   * @return the configured analyser
   */
  private RootCauseAnalyser hotClosedLoop() {
    RootCauseAnalyser rca = new RootCauseAnalyser();
    rca.setTemperatureCelsius(150.0);
    rca.setPressureBara(20.0);
    rca.setPH(8.7);
    rca.setOxygenPpb(5.0);
    rca.setCO2PartialPressureBar(0.0);
    rca.setH2SPartialPressureBar(0.0);
    rca.setWallShearStressPa(80.0);
    rca.setMaterial("carbon_steel");
    return rca;
  }

  /**
   * Hot deaerated carbon steel with no acid gases must raise flow-accelerated corrosion, and must not be explained away
   * as sweet, sour or oxygen corrosion.
   */
  @Test
  void hotDeaeratedCarbonSteelRaisesFlowAcceleratedCorrosion() {
    RootCauseAnalyser rca = hotClosedLoop();
    rca.addSymptom(new Symptom(Symptom.Category.CORROSION, "Local wall thinning at circumferential welds and bends")
        .withMeasurement("corrosionRateMmYr", 0.8));
    rca.analyse();

    List<RootCauseCandidate> candidates = rca.getCandidates();
    assertTrue(hasCode(candidates, "FLOW_ACCELERATED_CORROSION"), "FAC must be raised for this signature");
    assertFalse(hasCode(candidates, "CO2_CORROSION"), "no CO2 is present, so sweet corrosion must not be raised");
    assertFalse(hasCode(candidates, "SOUR_CORROSION"), "no H2S is present, so sour corrosion must not be raised");
    assertFalse(hasCode(candidates, "OXYGEN_CORROSION"),
        "the loop is deaerated, so oxygen corrosion must not be raised");
  }

  /**
   * FAC and erosion-corrosion are different mechanisms needing different mitigation, so a high-shear case must offer
   * both rather than collapsing them into one.
   */
  @Test
  void facAndErosionCorrosionAreOfferedSeparately() {
    RootCauseAnalyser rca = hotClosedLoop();
    rca.setWallShearStressPa(200.0);
    rca.addSymptom(new Symptom(Symptom.Category.CORROSION, "Wall thinning downstream of a bend"));
    rca.analyse();

    List<RootCauseCandidate> candidates = rca.getCandidates();
    assertTrue(hasCode(candidates, "FLOW_ACCELERATED_CORROSION"), "FAC must be raised");
    assertTrue(hasCode(candidates, "EROSION_CORROSION"), "erosion-corrosion must also be raised at high shear");
  }

  /**
   * The FAC recommendation must point the investigator at the in-situ pH conversion and at a chromium-bearing
   * replacement material.
   */
  @Test
  void facRecommendationNamesTheUsefulNextSteps() {
    RootCauseAnalyser rca = hotClosedLoop();
    rca.addSymptom(new Symptom(Symptom.Category.CORROSION, "Local wall thinning at welds"));
    rca.analyse();

    String recommendation = null;
    List<RootCauseCandidate> candidates = rca.getCandidates();
    for (int i = 0; i < candidates.size(); i++) {
      if ("FLOW_ACCELERATED_CORROSION".equals(candidates.get(i).getCode())) {
        recommendation = candidates.get(i).getRecommendation();
      }
    }
    assertTrue(recommendation != null, "the FAC candidate must be present");
    assertTrue(recommendation.contains("AmineBufferedPH"), "must point at the in-situ pH conversion");
    assertTrue(recommendation.contains("P11"), "must name a chromium-bearing replacement material");
  }

  /**
   * A corrosion-resistant alloy, a cold system and a sweet system must each suppress the FAC candidate, so it is not
   * raised indiscriminately.
   */
  @Test
  void facIsNotRaisedOutsideItsSignature() {
    RootCauseAnalyser cra = hotClosedLoop();
    cra.setMaterial("duplex_stainless");
    cra.addSymptom(new Symptom(Symptom.Category.CORROSION, "Wall thinning"));
    cra.analyse();
    assertFalse(hasCode(cra.getCandidates(), "FLOW_ACCELERATED_CORROSION"),
        "a corrosion-resistant alloy must not raise FAC");

    RootCauseAnalyser cold = hotClosedLoop();
    cold.setTemperatureCelsius(30.0);
    cold.addSymptom(new Symptom(Symptom.Category.CORROSION, "Wall thinning"));
    cold.analyse();
    assertFalse(hasCode(cold.getCandidates(), "FLOW_ACCELERATED_CORROSION"),
        "a cold system is outside the magnetite solubility window");

    RootCauseAnalyser sweet = hotClosedLoop();
    sweet.setCO2PartialPressureBar(2.0);
    sweet.addSymptom(new Symptom(Symptom.Category.CORROSION, "Wall thinning"));
    sweet.analyse();
    assertFalse(hasCode(sweet.getCandidates(), "FLOW_ACCELERATED_CORROSION"),
        "with significant CO2 the attack is explained by sweet corrosion instead");
  }
}
