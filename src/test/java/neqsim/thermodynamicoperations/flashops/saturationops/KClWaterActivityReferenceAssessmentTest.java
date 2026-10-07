package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** No-fit KCl-water reference and controlled CO2-addition assessment for issue 4234. */
class KClWaterActivityReferenceAssessmentTest {
  private static final Logger logger = LogManager.getLogger(KClWaterActivityReferenceAssessmentTest.class);
  private static final double WATER_MOLAR_MASS_KG_PER_MOL = 0.01801528;
  private static final double REFERENCE_TEMPERATURE_K = 273.15;
  private static final double REFERENCE_PRESSURE_BARA = 1.01325;

  @Tag("slow")
  @Test
  void saltOnlyReferenceAndControlledPressureCo2SequenceRemainSeparate() throws Exception {
    Reference[] references = {new Reference("K10", 1.49371, 0.95369, 0.9515361468935398),
        new Reference("K15", 2.36154, 0.92701, 0.9223375797090876)};
    double[] controlledPressuresBara = {14.15, 35.75};
    List<String> report = new ArrayList<String>();
    report.add("recipe,stage,molality_mol_per_kg,temperature_K,pressure_bara,co2_moles,"
        + "model_water_activity,archer_water_activity,model_minus_archer,aqueous_x_co2,fluid_phases");

    for (Reference reference : references) {
      Result saltOnlyReference = calculate(reference, REFERENCE_PRESSURE_BARA, 0.0);
      assertEquals(reference.expectedModelWaterActivity, saltOnlyReference.waterActivity, 1.0e-8,
          reference.recipe + " salt-only water-activity regression changed");
      report.add(saltOnlyReference.toCsv(reference, "salt-only-reference"));

      for (double pressureBara : controlledPressuresBara) {
        Result pressureOnly = calculate(reference, pressureBara, 0.0);
        assertEquals(saltOnlyReference.waterActivity, pressureOnly.waterActivity, 2.0e-4,
            reference.recipe + " pressure-only water activity changed unexpectedly");
        report.add(pressureOnly.toCsv(reference, "pressure-only"));

        Result co2Added = calculate(reference, pressureBara, 10.0);
        assertTrue(co2Added.aqueousCo2MoleFraction > 0.0);
        report.add(co2Added.toCsv(reference, "co2-added"));
      }
    }

    assertEquals(11, report.size());
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get("target/kcl-water-activity-archer-assessment.csv"), report, StandardCharsets.UTF_8);
    for (String row : report) {
      logger.info("KCL_WATER_ACTIVITY_DIAGNOSTIC,{}", row);
    }
  }

  private static Result calculate(Reference reference, double pressureBara, double co2Moles) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(REFERENCE_TEMPERATURE_K, pressureBara);
    if (co2Moles > 0.0) {
      fluid.addComponent("CO2", co2Moles);
    }
    fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS_KG_PER_MOL);
    fluid.addComponent("K+", reference.molality);
    fluid.addComponent("Cl-", reference.molality);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(co2Moles > 0.0);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    PhaseInterface aqueous = findIonRichPhase(fluid);
    int waterIndex = aqueous.getComponent("water").getComponentNumber();
    double waterActivity = aqueous.getComponent("water").getx() * aqueous.getActivityCoefficient(waterIndex);
    double aqueousCo2MoleFraction = aqueous.hasComponent("CO2") ? aqueous.getComponent("CO2").getx() : 0.0;
    assertTrue(Double.isFinite(waterActivity));
    assertTrue(Double.isFinite(aqueousCo2MoleFraction));
    assertTrue(waterActivity > 0.0 && waterActivity <= 1.0);
    assertEquals(reference.molality, aqueous.getComponent("K+").getNumberOfMolesInPhase(), 1.0e-12);
    assertEquals(reference.molality, aqueous.getComponent("Cl-").getNumberOfMolesInPhase(), 1.0e-12);

    List<String> phaseTypes = new ArrayList<String>();
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      phaseTypes.add(fluid.getPhase(phaseIndex).getType().toString());
    }
    return new Result(pressureBara, co2Moles, waterActivity, aqueousCo2MoleFraction, String.join("+", phaseTypes));
  }

  private static PhaseInterface findIonRichPhase(SystemInterface fluid) {
    PhaseInterface selected = null;
    double largestPotassiumFraction = -1.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface candidate = fluid.getPhase(phaseIndex);
      if (candidate.hasComponent("K+") && candidate.getComponent("K+").getx() > largestPotassiumFraction) {
        selected = candidate;
        largestPotassiumFraction = candidate.getComponent("K+").getx();
      }
    }
    assertNotNull(selected);
    return selected;
  }

  private static final class Reference {
    private final String recipe;
    private final double molality;
    private final double archerWaterActivity;
    private final double expectedModelWaterActivity;

    private Reference(String recipe, double molality, double archerWaterActivity, double expectedModelWaterActivity) {
      this.recipe = recipe;
      this.molality = molality;
      this.archerWaterActivity = archerWaterActivity;
      this.expectedModelWaterActivity = expectedModelWaterActivity;
    }
  }

  private static final class Result {
    private final double pressureBara;
    private final double co2Moles;
    private final double waterActivity;
    private final double aqueousCo2MoleFraction;
    private final String phaseTypes;

    private Result(double pressureBara, double co2Moles, double waterActivity, double aqueousCo2MoleFraction,
        String phaseTypes) {
      this.pressureBara = pressureBara;
      this.co2Moles = co2Moles;
      this.waterActivity = waterActivity;
      this.aqueousCo2MoleFraction = aqueousCo2MoleFraction;
      this.phaseTypes = phaseTypes;
    }

    private String toCsv(Reference reference, String stage) {
      return reference.recipe + "," + stage + "," + reference.molality + "," + REFERENCE_TEMPERATURE_K + ","
          + pressureBara + "," + co2Moles + "," + waterActivity + "," + reference.archerWaterActivity + ","
          + (waterActivity - reference.archerWaterActivity) + "," + aqueousCo2MoleFraction + "," + phaseTypes;
    }
  }
}
