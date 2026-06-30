package neqsim.process.safety.hazid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.hazid.HAZOPTemplate.GuideWord;
import neqsim.process.safety.hazid.HAZOPTemplate.Parameter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the Gap D limit-basis provenance in quantified HAZOP findings: {@link HazopConsequenceFinding},
 * {@link HazopQuantificationLimits} and {@link HazopConsequenceAutoPopulator#quantify}.
 */
class HazopConsequenceProvenanceTest {

  /**
   * Builds a small run flowsheet with a single compressor delivering a high compression ratio so the discharge
   * temperature exceeds the screening default.
   *
   * @return the run process system
   */
  private ProcessSystem buildCompressorProcess() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(10.0, "bara");

    Compressor compressor = new Compressor("2nd Stage", feed);
    compressor.setOutletPressure(120.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.run();
    return process;
  }

  /**
   * Verifies the eleven-argument finding constructor defaults the limit basis to "not specified" and the
   * twelve-argument constructor carries the explicit basis.
   */
  @Test
  void testFindingLimitBasisDefaultAndExplicit() {
    HazopConsequenceFinding withoutBasis = new HazopConsequenceFinding("Node-01", "C-100", GuideWord.MORE,
        Parameter.TEMPERATURE, 180.0, 150.0, "C", HazopConsequenceFinding.Verdict.EXCEEDS, "calc", "API 617", "msg");
    assertEquals("not specified", withoutBasis.getLimitBasis());

    HazopConsequenceFinding withBasis = new HazopConsequenceFinding("Node-01", "C-100", GuideWord.MORE,
        Parameter.TEMPERATURE, 180.0, 150.0, "C", HazopConsequenceFinding.Verdict.EXCEEDS, "calc", "API 617", "msg",
        "explicit basis");
    assertEquals("explicit basis", withBasis.getLimitBasis());
  }

  /**
   * Verifies the limits holder resolves per-unit overrides and produces auditable basis strings.
   */
  @Test
  void testLimitsResolutionAndBasis() {
    HazopQuantificationLimits limits = new HazopQuantificationLimits();
    assertEquals(HazopQuantificationLimits.DEFAULT_MAX_DISCHARGE_TEMPERATURE_C,
        limits.maxDischargeTemperatureC("anything"), 1e-9);

    limits.setMaxDischargeTemperatureC("2nd Stage", 170.0);
    assertEquals(170.0, limits.maxDischargeTemperatureC("2nd Stage"), 1e-9);
    assertEquals(HazopQuantificationLimits.DEFAULT_MAX_DISCHARGE_TEMPERATURE_C,
        limits.maxDischargeTemperatureC("other"), 1e-9);

    String overrideBasis = limits.basisForMaxDischargeTemperature("2nd Stage");
    assertTrue(overrideBasis.contains("per-unit override"));
    assertTrue(overrideBasis.contains("2nd Stage"));

    String defaultBasis = limits.basisForMaxDischargeTemperature("other");
    assertTrue(defaultBasis.contains("screening default"));

    String mdmtBasis = limits.basisForMinDesignMetalTemperature(null);
    assertTrue(mdmtBasis.contains("MDMT"));
    assertNotNull(limits.toJson());
  }

  /**
   * Verifies the auto-populator quantifies the compressor discharge temperature deviation and attaches a non-empty
   * limit basis to the finding.
   */
  @Test
  void testQuantifyAttachesLimitBasis() {
    ProcessSystem process = buildCompressorProcess();
    HazopQuantificationLimits limits = new HazopQuantificationLimits();
    List<HazopConsequenceFinding> findings = new HazopConsequenceAutoPopulator().quantify(process, limits);

    assertFalse(findings.isEmpty(), "Expected at least one quantified finding for the compressor");
    HazopConsequenceFinding compressorFinding = null;
    for (HazopConsequenceFinding f : findings) {
      if ("2nd Stage".equals(f.getUnitName())) {
        compressorFinding = f;
        break;
      }
    }
    assertNotNull(compressorFinding, "Expected a finding for the compressor unit");
    assertEquals(GuideWord.MORE, compressorFinding.getGuideWord());
    assertEquals(Parameter.TEMPERATURE, compressorFinding.getParameter());
    assertNotNull(compressorFinding.getLimitBasis());
    assertFalse(compressorFinding.getLimitBasis().trim().isEmpty());
    assertFalse("not specified".equals(compressorFinding.getLimitBasis()),
        "quantify() should attach an auditable basis, not the placeholder");
    assertNotNull(compressorFinding.getStandardReference());
  }

  /**
   * Verifies the populator catalogue exposes the documented deviation-to-calculation mappings.
   */
  @Test
  void testCatalogueExposesMappings() {
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    assertFalse(populator.catalogue().isEmpty());
    assertNotNull(populator.mappingFor(GuideWord.MORE, Parameter.PRESSURE));
    assertNotNull(populator.mappingFor(GuideWord.LESS, Parameter.TEMPERATURE));
  }
}
