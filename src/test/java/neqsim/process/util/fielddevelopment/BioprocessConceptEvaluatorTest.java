package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.digestion.ModelFidelity;
import neqsim.process.util.fielddevelopment.BioprocessConceptEvaluator.ConceptCase;
import neqsim.process.util.fielddevelopment.BioprocessConceptEvaluator.ConceptResult;
import neqsim.process.util.fielddevelopment.BioprocessConceptEvaluator.RankingMetric;

/** Tests for {@link BioprocessConceptEvaluator}. */
class BioprocessConceptEvaluatorTest {

  @Test
  void conceptsCanBeRankedOnEconomicAndEnvironmentalMetrics() {
    BioprocessConceptEvaluator evaluator = new BioprocessConceptEvaluator();
    evaluator.setEconomicBasis(0.10, 20);
    evaluator.addConcept(new ConceptCase("lower cost").setAnnualProduct(1000.0, "unit").setEnergyMWhPerUnit(0.010)
        .setProductPricePerUnit(100.0).setCosts(100.0, 10.0).setAnnualEmissionsTCO2Equivalent(1.0).setClosure(1.0, 1.0)
        .setQualification(ModelFidelity.ENGINEERING, "calculation case A"));
    evaluator.addConcept(new ConceptCase("lower carbon").setAnnualProduct(1000.0, "unit").setEnergyMWhPerUnit(0.010)
        .setProductPricePerUnit(90.0).setCosts(200.0, 20.0).setAnnualEmissionsTCO2Equivalent(0.5).setClosure(1.0, 1.0)
        .setQualification(ModelFidelity.ENGINEERING, "calculation case B"));

    evaluator.evaluate();
    assertEquals("lower cost", evaluator.getRankedResults(RankingMetric.NET_PRESENT_VALUE).get(0).getName());
    assertEquals("lower carbon", evaluator.getRankedResults(RankingMetric.CARBON_INTENSITY).get(0).getName());
    assertTrue(evaluator.getRankedResults(RankingMetric.NET_PRESENT_VALUE).get(0).isClosureQualified());
  }

  @Test
  void missingClosureAndScreeningFidelityRemainVisible() {
    BioprocessConceptEvaluator evaluator = new BioprocessConceptEvaluator();
    evaluator.addConcept(new ConceptCase("unqualified").setAnnualProduct(1000.0, "unit").setEnergyMWhPerUnit(0.010)
        .setProductPricePerUnit(100.0).setCosts(100.0, 10.0).setAnnualEmissionsTCO2Equivalent(1.0));

    ConceptResult result = evaluator.getRankedResults(RankingMetric.LEVELIZED_PRODUCT_COST).get(0);
    assertFalse(result.isClosureQualified());
    assertEquals(3, result.getWarnings().size());
  }
}
