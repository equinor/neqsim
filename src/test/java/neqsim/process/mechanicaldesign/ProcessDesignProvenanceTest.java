package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.process.mechanicaldesign.data.ProcessDesignParameters;
import neqsim.process.mechanicaldesign.designstandards.CompressorDesignStandard;
import neqsim.process.mechanicaldesign.designstandards.StandardRegistry;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Engineering and ownership checks for the layered requirement workflow.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ProcessDesignProvenanceTest extends neqsim.NeqSimTest {
  /** Default, selected-company and legacy baseline rows retain distinct sources and ranges. */
  @Test
  void layeredRowsRetainTheirActualSources() {
    Map<String, ProcessDesignParameters.Value> values = ProcessDesignParameters.load("Separator", " equinor ");
    assertEquals(0.135, values.get("GasLoadFactor").midpoint(), 1e-12);
    assertEquals("Equinor", values.get("GasLoadFactor").company);
    assertEquals("default", values.get("FoamAllowanceFactor").company);
    assertEquals(1.2, values.get("FoamAllowanceFactor").minimum, 1e-12);
    assertEquals(1.5, values.get("FoamAllowanceFactor").maximum, 1e-12);
    assertEquals("StatoilTR", values.get("Fg").company);
    assertThrows(UnsupportedOperationException.class, () -> values.clear());
    assertThrows(IllegalStateException.class, () -> ProcessDesignParameters.require(values, "missing-key"));
    assertThrows(IllegalStateException.class, () -> ProcessDesignParameters.load("unknown-equipment", "default"));
  }

  /** Ownership does not depend on whether a user value numerically equals the old default. */
  @Test
  void equalValueOverrideIsPreservedAndCanBeReleased() {
    SeparatorMechanicalDesign design = new SeparatorMechanicalDesign(new Separator("separator"));
    design.setFoamAllowanceFactor(1.0);
    design.readDesignSpecifications();
    assertEquals(1.0, design.getFoamAllowanceFactor(), 1e-12);
    assertEquals("USER_OVERRIDE", design.getProcessDesignParameterSources().get("FoamAllowanceFactor"));
    assertTrue(design.isProcessDesignParameterOverridden("FoamAllowanceFactor"));
    design.clearProcessDesignParameterOverride("FoamAllowanceFactor");
    design.readDesignSpecifications();
    assertEquals(1.35, design.getFoamAllowanceFactor(), 1e-12);
    assertFalse(design.isProcessDesignParameterOverridden("FoamAllowanceFactor"));
    assertTrue(design.getProcessDesignParameterSources().get("FoamAllowanceFactor").contains(":default:"));
    assertThrows(UnsupportedOperationException.class, () -> design.getProcessDesignParameterSources().clear());
  }

  /** Reloading another company replaces company values but preserves the generic supplementary rows. */
  @Test
  void companyChangesReloadWithoutRetainingThePreviousCompanyValue() {
    CompressorMechanicalDesign design = new CompressorMechanicalDesign(new Compressor("compressor"));
    design.setCompanySpecificDesignStandards("Equinor");
    design.readDesignSpecifications();
    assertEquals(10.0, design.getSurgeMarginPercent(), 1e-12);
    assertTrue(design.getProcessDesignParameterSources().get("SurgeMargin").contains(":Equinor:"));
    design.setCompanySpecificDesignStandards("Statoil");
    design.readDesignSpecifications();
    assertEquals(12.5, design.getSurgeMarginPercent(), 1e-12);
    assertTrue(design.getProcessDesignParameterSources().get("SurgeMargin").contains(":default:"));
    assertFalse(design.validateEfficiency(50.0));
    assertFalse(design.validateEfficiency(Double.NaN));
  }

  /** International standard identities remain distinct from company lookup keys. */
  @Test
  void standardFactoryKeepsItsCodeAndExposesActualCompanyData() {
    MechanicalDesign design = new MechanicalDesign(new Compressor("compressor"));
    design.setCompanySpecificDesignStandards("Equinor");
    CompressorDesignStandard standard = (CompressorDesignStandard) StandardRegistry.createStandard(StandardType.API_617,
        design);
    assertTrue(standard.getStandardName().startsWith("API-617 "));
    assertEquals(1.0, standard.getCompressorFactor(), 1e-12);
    assertTrue(standard.getProcessDesignParameterSources().get("compressorFactor").contains(":StatoilTR:"));
  }

  /**
   * Serialized pipeline calculators preserve project ownership independently of company reloads.
   *
   * @throws Exception if the serialization round trip fails
   */
  @Test
  void serializedFactorOwnershipAndRecoveryAreStable() throws Exception {
    PipeMechanicalDesignCalculator original = new PipeMechanicalDesignCalculator(0.508, 15.0, 20.0);
    original.setCompanyDesignFactor(0.72, "company-test");
    original.setDesignFactor(0.60);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    PipeMechanicalDesignCalculator copy;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (PipeMechanicalDesignCalculator) input.readObject();
    }
    copy.setCompanyDesignFactor(0.70, "new-company-test");
    copy.calculateMinimumWallThickness();
    assertEquals(0.60, copy.getDesignFactor(), 1e-12);
    assertEquals("USER_OVERRIDE", copy.getDesignFactorSource());
    copy.clearDesignFactorOverride();
    assertEquals(0.70, copy.getDesignFactor(), 1e-12);
    assertEquals("new-company-test", copy.getDesignFactorSource());
    copy.setLocationClass(4);
    assertEquals(0.40, copy.getDesignFactor(), 1e-12);
    assertEquals("CODE_LOCATION", copy.getDesignFactorSource());
    for (double invalid : new double[] {0.0, -0.1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> copy.setDesignFactor(invalid));
    }
  }

  /** External company limits remain usable without disguising their generic process supplement. */
  @Test
  void registeredCsvCompaniesRetainTheirOwnLimitsAndExplicitProcessSources() {
    SeparatorMechanicalDesign design = new SeparatorMechanicalDesign(new Separator("separator"));
    design.setDesignDataSource(new neqsim.process.mechanicaldesign.data.CsvMechanicalDesignDataSource(
        java.nio.file.Paths.get("src", "test", "resources", "design_limits_test.csv")));
    design.setCompanySpecificDesignStandards("TestCo");
    design.readDesignSpecifications();
    assertEquals("TestCo", design.getCompanySpecificDesignStandards());
    assertEquals("default", design.getProcessDesignCompanyIdentifier());
    assertEquals(110.0, design.getDesignMaxPressureLimit(), 1e-12);
    assertEquals(1.35, design.getFoamAllowanceFactor(), 1e-12);
    assertTrue(design.getProcessDesignParameterSources().get("FoamAllowanceFactor").contains(":default:"));
    assertThrows(IllegalArgumentException.class, () -> design.setCompanySpecificDesignStandards("TestCoo"));
    assertEquals("TestCo", design.getCompanySpecificDesignStandards());
  }

  /** Explicit process-standard setters survive loading, with equipment setters taking precedence. */
  @Test
  void directStandardOverridesRemainEffective() {
    SeparatorMechanicalDesign design = new SeparatorMechanicalDesign(new Separator("separator"));
    design.setCompanySpecificDesignStandards("default");
    neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard standard = (neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard) design
        .getDesignStandard().get("separator process design");
    standard.setFg(0.4);
    standard.setVolumetricDesignFactor(1.5);
    design.readDesignSpecifications();
    design.loadProcessDesignParameters();
    assertEquals(0.4, design.getFg(), 1e-12);
    assertEquals(1.5, design.getVolumeSafetyFactor(), 1e-12);
    assertEquals("STANDARD_OVERRIDE", design.getProcessDesignParameterSources().get("Fg"));
    design.setFg(0.7);
    design.readDesignSpecifications();
    assertEquals(0.7, design.getFg(), 1e-12);
    assertEquals("USER_OVERRIDE", design.getProcessDesignParameterSources().get("Fg"));
  }

  /**
   * A corrupt range fails atomically, retaining the previously selected company and standards.
   *
   * @throws java.sql.SQLException if the test database cannot be closed
   */
  @Test
  void invalidRangeDoesNotPublishPartialCompanySelection() throws java.sql.SQLException {
    String company = "__PROCESS_RANGE_REGRESSION__";
    SeparatorMechanicalDesign design = new SeparatorMechanicalDesign(new Separator("separator"));
    design.setCompanySpecificDesignStandards("Equinor");
    Object previousStandard = design.getDesignStandard().get("separator process design");
    try (
        neqsim.util.database.NeqSimProcessDesignDataBase database = new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      database.execute("INSERT INTO TechnicalRequirements_Process "
          + "(ID,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,Company) "
          + "VALUES (910394,'Separator','FoamAllowanceFactor',3.0,1.0,'-','" + company + "')");
      assertThrows(IllegalStateException.class, () -> design.setCompanySpecificDesignStandards(company));
      assertEquals("Equinor", design.getCompanySpecificDesignStandards());
      assertEquals("Equinor", design.getProcessDesignCompanyIdentifier());
      assertTrue(previousStandard == design.getDesignStandard().get("separator process design"));
    } finally {
      try (
          neqsim.util.database.NeqSimProcessDesignDataBase database = new neqsim.util.database.NeqSimProcessDesignDataBase()) {
        database.execute("DELETE FROM TechnicalRequirements_Process WHERE Company='" + company + "'");
      }
    }
  }

  /** Analytical liquid inventory is preserved under foam/flow allowances and the length/diameter constraint. */
  @Test
  void bothSeparatorSizingPathsPreserveRequiredLiquidInventory() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.3);
    fluid.addComponent("n-decane", 0.7);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    Separator separator = new Separator("separator", feed);
    separator.setOrientation("horizontal");
    separator.run();
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.setRetentionTime(10000.0);
    design.setFoamAllowanceFactor(1.5);
    design.setVolumeSafetyFactor(1.2);
    design.setFg(0.8);
    design.calcDesign();
    assertLiquidVolume(design, separator);
    design.performSizingCalculations();
    assertLiquidVolume(design, separator);
    assertEquals(10000.0, design.getRetentionTime(), 1e-12);
  }

  /**
   * Compare actual cylindrical capacity with an independently evaluated flow-times-time inventory.
   *
   * @param design sized vessel
   * @param separator simulated flow basis
   */
  private void assertLiquidVolume(SeparatorMechanicalDesign design, Separator separator) {
    double required = separator.getThermoSystem().getLiquidVolume() / 1.0e5 * 10000.0 * 1.5 * 1.2;
    double available = Math.PI / 4.0 * Math.pow(design.getInnerDiameter(), 2.0) * design.getTantanLength() * 0.2;
    assertTrue(required > 0.0);
    assertEquals(required, available, required * 1e-10);
    assertTrue(design.getTantanLength() / design.getInnerDiameter() <= 5.0 + 1e-12);
  }
}
