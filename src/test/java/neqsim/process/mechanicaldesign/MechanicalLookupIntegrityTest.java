package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.Pipeline;
import neqsim.process.mechanicaldesign.compressor.CompressorCasingDesignCalculator;
import neqsim.process.mechanicaldesign.designstandards.CompressorDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.GasScrubberDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.JointEfficiencyPlateStandard;
import neqsim.process.mechanicaldesign.designstandards.MaterialPipeDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard;
import neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.safety.rupture.MaterialStrengthCurve;

/**
 * Regression tests for mechanical data keys and explicit lookup failures.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MechanicalLookupIntegrityTest extends neqsim.NeqSimTest {
  /** Every casing grade comes from its own bundled material row. */
  @Test
  void casingGradesLoadMaterialPropertiesAndRejectUnknownGrades() {
    CompressorCasingDesignCalculator calc = new CompressorCasingDesignCalculator();
    calc.setMaterialGrade("SA-182-F316");
    calc.calculate();
    assertEquals(205.0, calc.getSmysMPa(), 1e-12);
    assertEquals(115.0, calc.getAllowableStressMPa(), 1e-12);
    calc.setMaterialGrade("SA-564-Tp630");
    calc.calculate();
    assertEquals(725.0, calc.getSmysMPa(), 1e-12);
    assertEquals(185.0, calc.getAllowableStressMPa(), 1e-12);
    calc.setMaterialGrade("unknown-casing-grade");
    assertThrows(IllegalArgumentException.class, calc::calculate);
    assertTrue(Double.isNaN(calc.getSmysMPa()));
  }

  /** API 5L aliases use matching strengths rather than retaining X65. */
  @Test
  void pipeGradeAliasesAreCanonical() {
    PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();
    calc.setMaterialGrade(" x52 ");
    assertEquals(359.0, calc.getSmys(), 1e-12);
    calc.setMaterialGrade("L360");
    assertEquals("X52", calc.getMaterialGrade());
    assertEquals(359.0, calc.getSmys(), 1e-12);
    assertTrue(PipeMechanicalDesignCalculator.isSupportedMaterialGrade("l360"));
  }

  /** Fire screening must not use Grade B for unregistered grades. */
  @Test
  void fireStrengthGradesHaveExplicitFallbacksOnly() {
    assertEquals(620.0e6, MaterialStrengthCurve.forApi5LPipeGrade("X90").getAmbientYieldStrengthPa(), 1.0);
    assertTrue(MaterialStrengthCurve.forApi5LPipeGrade("X90").getDataSource().contains("built-in"));
    assertTrue(MaterialStrengthCurve.forApi5LPipeGrade("X52").getDataSource().contains("MaterialPipeProperties"));
    assertEquals(MaterialStrengthCurve.forApi5LPipeGrade("X52").getAmbientYieldStrengthPa(),
        MaterialStrengthCurve.forApi5LPipeGrade("L360").getAmbientYieldStrengthPa(), 1.0);
    assertThrows(IllegalArgumentException.class, () -> MaterialStrengthCurve.forApi5LPipeGrade("unknown"));
  }

  /** Joint efficiency follows the selected joint and inspection row. */
  @Test
  void jointEfficiencyReadsTheInspectionTable() {
    JointEfficiencyPlateStandard standard = new JointEfficiencyPlateStandard("ASME VIII", null);
    standard.readJointEfficiencyStandard("Double-Welded Butt Joints", "Spot radiographed");
    assertEquals(0.85, standard.getJEFactor(), 1e-12);
    standard.readJointEfficiencyStandard("Single Welded Butt Joint", "No radiograph");
    assertEquals(0.65, standard.getJEFactor(), 1e-12);
    assertThrows(IllegalArgumentException.class,
        () -> standard.readJointEfficiencyStandard("unknown joint", "No radiograph"));
    assertThrows(IllegalStateException.class, standard::getJEFactor);
  }

  /** The default pipe material uses the specification column, and misses are explicit. */
  @Test
  void pipeStandardUsesApiSpecificationAndRejectsMisses() {
    MaterialPipeDesignStandard standard = new MaterialPipeDesignStandard("StatoilTR", null);
    assertEquals(25000 * 0.00689475729, standard.getMinimumYeildStrength(), 1e-10);
    assertThrows(IllegalArgumentException.class, () -> standard.readMaterialDesignStandard("API5L", "unknown"));
    assertThrows(IllegalStateException.class, standard::getMinimumYeildStrength);
  }

  /** Company-loaded pipeline factors cannot be overwritten by material defaults. */
  @Test
  void pipelineCompanyFactorSurvivesMaterialStandardDefaults() {
    PipelineMechanicalDesign design = new PipelineMechanicalDesign(new Pipeline("lookup test"));
    design.setCompanySpecificDesignStandards("Equinor");
    design.readDesignSpecifications();
    assertEquals(0.72, design.getCalculator().getDesignFactor(), 1e-12);
    MaterialPipeDesignStandard material = (MaterialPipeDesignStandard) design.getDesignStandard()
        .get("material pipe design codes");
    material.setDesignFactor(0.6);
    material.setEfactor(0.85);
    design.readDesignSpecifications();
    assertEquals(0.6, design.getCalculator().getDesignFactor(), 1e-12);
    assertEquals(0.85, design.getCalculator().getJointFactor(), 1e-12);
  }

  /** The historical company spellings address the same bundled process rows. */
  @Test
  void companyAliasesLoadCompressorAndSeparatorValues() {
    assertEquals(1.0, new CompressorDesignStandard("Statoil_TR1414", null).getCompressorFactor(), 1e-12);
    assertEquals(0.137, new SeparatorDesignStandard("Statoil", null).getGasLoadFactor(), 1e-12);
    GasScrubberDesignStandard scrubber = new GasScrubberDesignStandard("Statoil_TR1414", null);
    assertEquals(1.1, scrubber.getVolumetricDesignFactor(), 1e-12);
  }
}
