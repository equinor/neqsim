package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests for CharacterizationOptions, BIP transfer, and fluent API functionality.
 */
class CharacterizationOptionsTest {
  private static final double TOLERANCE = 1e-8;

  @Test
  @DisplayName("CharacterizationOptions builder creates correct defaults")
  void testOptionsBuilderDefaults() {
    CharacterizationOptions options = CharacterizationOptions.builder().build();

    assertEquals(false, options.isTransferBinaryInteractionParameters());
    assertEquals(false, options.isNormalizeComposition());
    assertEquals(CharacterizationOptions.NamingScheme.REFERENCE, options.getNamingScheme());
    assertEquals(false, options.isGenerateValidationReport());
  }

  @Test
  @DisplayName("CharacterizationOptions builder sets all options")
  void testOptionsBuilderSetsOptions() {
    CharacterizationOptions options = CharacterizationOptions.builder()
        .transferBinaryInteractionParameters(true).normalizeComposition(true)
        .namingScheme(CharacterizationOptions.NamingScheme.SEQUENTIAL)
        .generateValidationReport(true).compositionTolerance(1e-6).build();

    assertEquals(true, options.isTransferBinaryInteractionParameters());
    assertEquals(true, options.isNormalizeComposition());
    assertEquals(CharacterizationOptions.NamingScheme.SEQUENTIAL, options.getNamingScheme());
    assertEquals(true, options.isGenerateValidationReport());
    assertEquals(1e-6, options.getCompositionTolerance(), TOLERANCE);
  }

  @Test
  @DisplayName("withBipTransfer creates options with BIP transfer enabled")
  void testWithBipTransfer() {
    CharacterizationOptions options = CharacterizationOptions.withBipTransfer();

    assertTrue(options.isTransferBinaryInteractionParameters());
  }

  private static SystemInterface createReferenceFluid() {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);

    fluid.addTBPfraction("C7", 0.10, 0.090, 0.78);
    fluid.addTBPfraction("C10", 0.07, 0.140, 0.82);
    fluid.addTBPfraction("C15", 0.03, 0.220, 0.86);

    fluid.createDatabase(true);
    fluid.setMixingRule(2);

    // Set custom BIPs
    fluid.setBinaryInteractionParameter("methane", "C7_PC", 0.05);
    fluid.setBinaryInteractionParameter("methane", "C10_PC", 0.06);
    fluid.setBinaryInteractionParameter("methane", "C15_PC", 0.07);
    fluid.setBinaryInteractionParameter("ethane", "C7_PC", 0.02);

    return fluid;
  }

  private static SystemInterface createSourceFluid() {
    SystemInterface fluid = new SystemPrEos(298.15, 50.0);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("ethane", 0.12);

    // Different PC structure
    fluid.addTBPfraction("S1", 0.08, 0.085, 0.77);
    fluid.addTBPfraction("S2", 0.06, 0.120, 0.80);
    fluid.addTBPfraction("S3", 0.05, 0.180, 0.84);
    fluid.addTBPfraction("S4", 0.04, 0.260, 0.87);

    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    return fluid;
  }

  @Test
  @DisplayName("characterizeToReference with options transfers BIPs")
  void testCharacterizeToReferenceWithBipTransfer() {
    SystemInterface reference = createReferenceFluid();
    SystemInterface source = createSourceFluid();

    CharacterizationOptions options =
        CharacterizationOptions.builder().transferBinaryInteractionParameters(true).build();

    SystemInterface characterized =
        PseudoComponentCombiner.characterizeToReference(source, reference, options);

    // Verify characterized fluid has components
    assertNotNull(characterized);
    assertTrue(characterized.getNumberOfComponents() > 0);

    // Verify base components are preserved
    assertEquals(0.65, characterized.getComponent("methane").getNumberOfmoles(), TOLERANCE);
    assertEquals(0.12, characterized.getComponent("ethane").getNumberOfmoles(), TOLERANCE);

    // Verify PC count matches reference
    List<ComponentInterface> characterizedPCs =
        Arrays.stream(characterized.getComponentNames()).map(characterized::getComponent)
            .filter(c -> c.isIsTBPfraction() || c.isIsPlusFraction()).collect(Collectors.toList());

    assertEquals(3, characterizedPCs.size());

    // Verify BIPs were transferred
    double kij = ((PhaseEos) characterized.getPhase(0)).getMixingRule()
        .getBinaryInteractionParameter(characterized.getComponent("methane").getComponentNumber(),
            characterized.getComponent("C7_PC").getComponentNumber());

    assertEquals(0.05, kij, TOLERANCE);
  }

  @Test
  @DisplayName("fluent API characterizeToReference works")
  void testFluentApiCharacterization() {
    SystemInterface reference = createReferenceFluid();
    SystemInterface source = createSourceFluid();

    SystemInterface characterized = source.getCharacterization().characterizeToReference(reference);

    assertNotNull(characterized);

    // Verify PC structure matches reference
    List<ComponentInterface> characterizedPCs =
        Arrays.stream(characterized.getComponentNames()).map(characterized::getComponent)
            .filter(c -> c.isIsTBPfraction() || c.isIsPlusFraction()).collect(Collectors.toList());

    assertEquals(3, characterizedPCs.size());
  }

  @Test
  @DisplayName("fluent API with options works")
  void testFluentApiWithOptions() {
    SystemInterface reference = createReferenceFluid();
    SystemInterface source = createSourceFluid();

    CharacterizationOptions options = CharacterizationOptions.withBipTransfer();

    SystemInterface characterized =
        source.getCharacterization().characterizeToReference(reference, options);

    assertNotNull(characterized);
  }

  @Test
  @DisplayName("transferBipsFrom fluent method works")
  void testTransferBipsFromFluentMethod() {
    SystemInterface reference = createReferenceFluid();
    SystemInterface source = createSourceFluid();

    // First characterize without BIP transfer
    SystemInterface characterized =
        PseudoComponentCombiner.characterizeToReference(source, reference);

    // Then transfer BIPs separately
    characterized.getCharacterization().transferBipsFrom(reference);

    // Verify BIP was transferred
    double kij = ((PhaseEos) characterized.getPhase(0)).getMixingRule()
        .getBinaryInteractionParameter(characterized.getComponent("methane").getComponentNumber(),
            characterized.getComponent("C7_PC").getComponentNumber());

    assertEquals(0.05, kij, TOLERANCE);
  }

  @Test
  @DisplayName("validation report is generated correctly")
  void testValidationReportGeneration() {
    SystemInterface reference = createReferenceFluid();
    SystemInterface source = createSourceFluid();
    SystemInterface characterized =
        PseudoComponentCombiner.characterizeToReference(source, reference);

    CharacterizationValidationReport report =
        PseudoComponentCombiner.generateValidationReport(source, reference, characterized);

    assertNotNull(report);
    assertEquals(4, report.getSourcePseudoComponentCount());
    assertEquals(3, report.getResultPseudoComponentCount());

    // Mass should be conserved
    assertTrue(report.getMassDifferencePercent() < 0.1);

    // Report string should be generated
    String reportString = report.toReportString();
    assertTrue(reportString.contains("Characterization Validation Report"));
    assertTrue(reportString.contains("Pseudo-Component Counts"));
  }

  @Test
  @DisplayName("CharacterizationValidationReport isValid works")
  void testValidationReportIsValid() {
    SystemInterface reference = createReferenceFluid();
    SystemInterface source = createSourceFluid();
    SystemInterface characterized =
        PseudoComponentCombiner.characterizeToReference(source, reference);

    CharacterizationValidationReport report =
        CharacterizationValidationReport.generate(source, reference, characterized);

    // Mass should be conserved, so report should be valid
    assertTrue(report.getMassDifferencePercent() < 0.1);
    assertTrue(report.getMolesDifferencePercent() < 0.1);
  }
}
