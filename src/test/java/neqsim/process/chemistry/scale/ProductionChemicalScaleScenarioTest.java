package neqsim.process.chemistry.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.chemistry.ProductionChemical;
import neqsim.process.chemistry.rca.RootCauseAnalyser;
import neqsim.process.chemistry.rca.RootCauseCandidate;
import neqsim.process.chemistry.rca.Symptom;
import neqsim.pvtsimulation.flowassurance.MultiMineralScaleEquilibrium;

/** Tests for {@link ProductionChemicalScaleScenario}. */
public class ProductionChemicalScaleScenarioTest {

  private ProductionChemicalScaleScenario carbonateBrine() {
    return new ProductionChemicalScaleScenario().setTemperatureCelsius(60.0).setPressureBara(50.0).setPH(6.5)
        .setCalciumMgL(2500.0).setBariumMgL(50.0).setStrontiumMgL(200.0).setIronMgL(20.0)
        .setSodiumMgL(50000.0).setSulphateMgL(500.0).setBicarbonateMgL(1000.0)
        .setTotalDissolvedSolidsMgL(100000.0).setActivityModel(
            MultiMineralScaleEquilibrium.ActivityModel.PITZER_BINARY);
  }

  /** Base-equivalent dosing raises pH and produces auditable calcite SI evidence. */
  @Test
  public void phStabilizerRaisesPHAndCalciteSaturation() {
    ProductionChemicalScaleScenario scenario = carbonateBrine()
        .addChemical(ProductionChemical.phStabilizer("MDEA pH stabiliser", 1000.0));
    scenario.evaluate();

    assertTrue(scenario.getTreatedPH() > 6.5);
    assertTrue(scenario.getSaturationIndexChange("CaCO3") > 0.25);
    assertTrue(scenario.getWarnings().containsKey("chemical_induced_calcite_risk"));
  }

  /** Practical MEA-triazine capacity is applied to dissolved H2S on an active-mass basis. */
  @Test
  public void h2sScavengerConsumesLoadStoichiometrically() {
    ProductionChemicalScaleScenario scenario = carbonateBrine().setDissolvedH2SMgL(100.0)
        .addChemical(ProductionChemical.h2sScavenger("MEA-triazine", 1000.0));
    scenario.evaluate();

    // 1000 mg/L product * 40% / 189.26 g/mol * 34.08 g/mol = 72.02 mg/L capacity.
    assertEquals(27.98, scenario.getResidualDissolvedH2SMgL(), 0.2);
    assertTrue(scenario.getWarnings().containsKey("h2s_scavenger_under_capacity"));
    assertTrue(scenario.getWarnings().containsKey("triazine_spent_product"));
  }

  /** Vendor capacity data overrides the chemistry-family screening default. */
  @Test
  public void vendorH2SCapacityOverridesDefaultStoichiometry() {
    ProductionChemical scavenger = ProductionChemical.h2sScavenger("qualified product", 1000.0);
    scavenger.setH2SCapacityKgPerKgActive(0.30);
    ProductionChemicalScaleScenario scenario = carbonateBrine().setDissolvedH2SMgL(100.0)
        .addChemical(scavenger);
    scenario.evaluate();

    // 1000 mg/L product * 40% active * 0.30 kg H2S/kg active = 120 mg/L capacity.
    assertEquals(0.0, scenario.getResidualDissolvedH2SMgL(), 1.0e-12);
  }

  /** Threshold inhibition remains separate from thermodynamic saturation. */
  @Test
  public void scaleInhibitorDoesNotAlterThermodynamicSI() {
    ProductionChemicalScaleScenario scenario = carbonateBrine()
        .addChemical(ProductionChemical.scaleInhibitor("phosphonate", 20.0));
    scenario.evaluate();

    assertEquals(scenario.getBaselineSaturationIndex("CaCO3"), scenario.getTreatedSaturationIndex("CaCO3"), 1.0e-12);
    assertTrue(scenario.getWarnings().containsKey("inhibitor_is_kinetic"));
    assertEquals(Boolean.FALSE, scenario.toMap().get("scaleInhibitorChangesThermodynamicSI"));
  }

  /** RCA promotes treatment-model evidence into explainable competing root causes. */
  @Test
  public void rootCauseAnalysisUsesTreatmentEvidence() {
    ProductionChemicalScaleScenario scenario = carbonateBrine().setDissolvedH2SMgL(100.0)
        .addChemical(ProductionChemical.phStabilizer("MDEA", 1000.0))
        .addChemical(ProductionChemical.h2sScavenger("MEA-triazine", 1000.0));

    RootCauseAnalyser rca = new RootCauseAnalyser();
    rca.setChemicalTreatmentScenario(scenario);
    rca.addSymptom(new Symptom(Symptom.Category.DEPOSIT, "White organic/mineral deposit downstream of injection"));
    rca.analyse();

    boolean chemicalScale = false;
    boolean scavengerDeposit = false;
    boolean scavengerCapacity = false;
    for (RootCauseCandidate candidate : rca.getCandidates()) {
      chemicalScale |= "CHEMICAL_INDUCED_CARBONATE_SCALE".equals(candidate.getCode());
      scavengerDeposit |= "SCAVENGER_SPENT_PRODUCT_DEPOSIT".equals(candidate.getCode());
      scavengerCapacity |= "H2S_SCAVENGER_UNDER_CAPACITY".equals(candidate.getCode());
    }
    assertTrue(chemicalScale);
    assertTrue(scavengerDeposit);
    assertTrue(scavengerCapacity);
    assertTrue(rca.toJson().contains("chemicalTreatmentScenario"));
  }
}
