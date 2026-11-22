package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.pipeline.Pipeline;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.CompressorDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.GasScrubberDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.MaterialPipeDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;

/**
 * End-to-end regressions for company lookup failures, loading and factor precedence.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ProcessDesignLookupTest extends neqsim.NeqSimTest {
  /** Reject unknown companies instead of silently constructing another company's standards. */
  @Test
  void unknownCompanyIsRejected() {
    MechanicalDesign design = new MechanicalDesign(new Separator("company"));
    design.setCompanySpecificDesignStandards("Equinor");
    assertThrows(IllegalArgumentException.class, () -> design.setCompanySpecificDesignStandards("Equnior"));
    assertEquals("Equinor", design.getCompanySpecificDesignStandards());
    assertThrows(IllegalArgumentException.class, () -> design.setCompanySpecificDesignStandards(null));
    assertThrows(IllegalArgumentException.class, () -> design.setCompanySpecificDesignStandards(" "));
  }

  /** Direct standard construction and the pipeline data source must use the same failure policy. */
  @Test
  void directReadersRejectUnknownCompany() {
    assertThrows(IllegalArgumentException.class, () -> new SeparatorDesignStandard("unknown-company", null));
    assertThrows(IllegalArgumentException.class, () -> new GasScrubberDesignStandard("unknown-company", null));
    assertThrows(IllegalArgumentException.class, () -> new CompressorDesignStandard("unknown-company", null));
    assertThrows(IllegalArgumentException.class,
        () -> new PipelineMechanicalDesignDataSource().loadDesignFactors("unknown-company"));
  }

  /** Default process rows must be applied before separator sizing and kept under named companies. */
  @Test
  void separatorSpecificationsLoadDefaultAndCompanyRows() {
    SeparatorMechanicalDesign design = new SeparatorMechanicalDesign(new Separator("separator"));
    design.setCompanySpecificDesignStandards("Statoil_TR1414");
    design.readDesignSpecifications();
    assertEquals(1.35, design.getFoamAllowanceFactor(), 1e-12);
    assertEquals(350.0, design.getDropletDiameterLiquidLiquid(), 1e-12);
    assertEquals(2.0, design.getMaxGasVelocityLimit(), 1e-12);
    assertEquals(2.5, design.getMinOilRetentionTime(), 1e-12);
    assertEquals(0.137, design.getGasLoadFactor(), 1e-12);
  }

  /** Explicit project values survive both automatic and explicitly requested reloads. */
  @Test
  void separatorOverridesSurviveReloads() {
    SeparatorMechanicalDesign design = new SeparatorMechanicalDesign(new Separator("separator"));
    design.setFoamAllowanceFactor(1.8);
    design.setRetentionTime(240.0);
    design.setGasLoadFactor(0.09);
    design.setCompanySpecificDesignStandards("default");
    design.readDesignSpecifications();
    design.loadProcessDesignParameters();
    assertEquals(1.8, design.getFoamAllowanceFactor(), 1e-12);
    assertEquals(240.0, design.getRetentionTime(), 1e-12);
    assertEquals(0.09, design.getGasLoadFactor(), 1e-12);
  }

  /** Compressor keys and fractions must agree with the actual table and percent-based public API. */
  @Test
  void compressorKeysAndUnitsAreLoadedAutomatically() {
    CompressorMechanicalDesign design = new CompressorMechanicalDesign(new Compressor("compressor"));
    design.setCompanySpecificDesignStandards("default");
    design.readDesignSpecifications();
    assertEquals(12.5, design.getSurgeMarginPercent(), 1e-12);
    assertEquals(7.5, design.getStonewallMarginPercent(), 1e-12);
    assertEquals(75.0, design.getTurndownPercent(), 1e-12);
    assertEquals(80.0, design.getMinPolytropicEfficiency(), 1e-12);
    assertEquals(165.0, design.getMaxDischargeTemperatureC(), 1e-12);
    assertTrue(design.validateEfficiency(81.0));
  }

  /** Company values take precedence over default rows while user values take precedence over both. */
  @Test
  void compressorOverridesSurviveReloads() {
    CompressorMechanicalDesign design = new CompressorMechanicalDesign(new Compressor("compressor"));
    design.setCompanySpecificDesignStandards("Equinor");
    design.setSurgeMarginPercent(18.0);
    design.setMinPolytropicEfficiency(78.0);
    design.readDesignSpecifications();
    design.loadProcessDesignParameters();
    assertEquals(18.0, design.getSurgeMarginPercent(), 1e-12);
    assertEquals(78.0, design.getMinPolytropicEfficiency(), 1e-12);
  }

  /** Heat exchanger parameters load through the ordinary specification workflow. */
  @Test
  void exchangerSpecificationsLoadAndPreserveOverrides() {
    HeatExchangerMechanicalDesign design = new HeatExchangerMechanicalDesign(new HeatExchanger("exchanger"));
    design.setCompanySpecificDesignStandards("Equinor");
    design.setAreaMarginFactor(1.4);
    design.readDesignSpecifications();
    design.loadProcessDesignParameters();
    assertEquals(1.4, design.getAreaMarginFactor(), 1e-12);
    assertEquals(7.5, design.getMinApproachTemperatureC(), 1e-12);
    assertEquals(2.75, design.getMaxTubeVelocity(), 1e-12);
    assertEquals(0.000264, design.getFoulingResistanceShellWater(), 1e-12);
  }

  /** Restrictive project factors survive repeated wall and MAOP calculations. */
  @Test
  void pipelineFactorSurvivesSizingAndRespectsLocationCeiling() {
    PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator(0.508, 15.0, 20.0);
    calc.setDesignFactor(0.60);
    double wall = calc.calculateMinimumWallThickness();
    double analyticalWall = (15.0 * 0.508 / (2.0 * 448.0 * 0.60) + 0.003) / 0.875;
    assertEquals(analyticalWall, wall, 1e-12);
    assertEquals(0.60, calc.getDesignFactor(), 1e-12);
    calc.setNominalWallThickness(wall);
    assertEquals(15.0, calc.calculateMAOP(), 1e-9);
    calc.setLocationClass(4);
    assertEquals(0.40, calc.getDesignFactor(), 1e-12);
    assertTrue(calc.calculateMinimumWallThickness() > wall);
    calc.setLocationClass(1);
    assertEquals(0.60, calc.getDesignFactor(), 1e-12);
  }

  /** Reloading company rows must not downgrade a project-selected location class. */
  @Test
  void companyReloadPreservesExplicitLocationClass() {
    PipelineMechanicalDesignDataSource source = new PipelineMechanicalDesignDataSource();
    PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator(0.508, 15.0, 20.0);
    calc.setLocationClass(4);
    source.loadIntoCalculator("X65", "default", calc);
    assertEquals(4, calc.getLocationClass());
    assertEquals(0.40, calc.getDesignFactor(), 1e-12);
    calc.clearLocationClassOverride();
    assertEquals(1, calc.getLocationClass());
    assertEquals(0.72, calc.getDesignFactor(), 1e-12);
    PipelineMechanicalDesign design = new PipelineMechanicalDesign(new Pipeline("pipeline"));
    design.setLocationClass(4);
    design.setCompanySpecificDesignStandards("Equinor");
    design.calcDesign();
    assertEquals(4, design.getLocationClass());
    assertEquals(0.40, design.getCalculator().getDesignFactor(), 1e-12);
  }

  /** The same precedence must hold after the mechanical-design facade maps the code again. */
  @Test
  void pipelineFacadeKeepsMaterialOverrideDuringSizing() {
    PipelineMechanicalDesign design = new PipelineMechanicalDesign(new Pipeline("pipeline"));
    design.setCompanySpecificDesignStandards("Equinor");
    MaterialPipeDesignStandard standard = (MaterialPipeDesignStandard) design.getDesignStandard()
        .get("material pipe design codes");
    standard.setDesignFactor(0.60);
    design.calcDesign();
    assertEquals(0.60, design.getCalculator().getDesignFactor(), 1e-12);
  }
}
