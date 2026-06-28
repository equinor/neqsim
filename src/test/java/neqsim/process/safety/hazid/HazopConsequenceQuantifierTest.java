package neqsim.process.safety.hazid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.hazid.HAZOPTemplate.GuideWord;
import neqsim.process.safety.hazid.HAZOPTemplate.Parameter;
import neqsim.process.safety.hazid.HazopConsequenceFinding.Verdict;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link HazopConsequenceAutoPopulator#quantify(ProcessSystem, HazopQuantificationLimits)}.
 */
public class HazopConsequenceQuantifierTest {
  private ProcessSystem process;
  private Compressor compressor;
  private ThrottlingValve valve;

  /**
   * Build and run a small representative flowsheet (valve, separator, compressor, cooler).
   */
  @BeforeEach
  public void setUp() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");

    valve = new ThrottlingValve("inlet-valve", feed);
    valve.setOutletPressure(50.0);

    Separator separator = new Separator("hp-separator", valve.getOutletStream());

    compressor = new Compressor("export-compressor", separator.getGasOutStream());
    compressor.setOutletPressure(120.0);

    Cooler cooler = new Cooler("export-cooler", compressor.getOutletStream());
    cooler.setOutTemperature(313.15);

    process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.run();
  }

  @Test
  public void producesCompressorAndValveFindings() {
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HazopConsequenceFinding> findings = populator.quantify(process, new HazopQuantificationLimits());
    assertFalse(findings.isEmpty(), "quantify should produce findings");

    HazopConsequenceFinding compressorFinding = findFinding(findings, "export-compressor");
    assertNotNull(compressorFinding, "expected a compressor discharge-temperature finding");
    assertEquals(GuideWord.MORE, compressorFinding.getGuideWord());
    assertEquals(Parameter.TEMPERATURE, compressorFinding.getParameter());
    assertFalse(Double.isNaN(compressorFinding.getComputedValue()), "discharge temperature should be computed");

    HazopConsequenceFinding valveFinding = findFinding(findings, "inlet-valve");
    assertNotNull(valveFinding, "expected a valve auto-refrigeration finding");
    assertEquals(GuideWord.LESS, valveFinding.getGuideWord());
    assertEquals(Parameter.TEMPERATURE, valveFinding.getParameter());
    assertFalse(Double.isNaN(valveFinding.getComputedValue()), "valve outlet temperature should be computed");
  }

  @Test
  public void compressorVerdictIsPassOrExceeds() {
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HazopConsequenceFinding> findings = populator.quantify(process, new HazopQuantificationLimits());
    HazopConsequenceFinding compressorFinding = findFinding(findings, "export-compressor");
    assertNotNull(compressorFinding);
    assertTrue(compressorFinding.getVerdict() == Verdict.PASS || compressorFinding.getVerdict() == Verdict.EXCEEDS,
        "compressor verdict should be a quantified PASS or EXCEEDS");
  }

  @Test
  public void tightDischargeLimitTriggersExceeds() {
    HazopQuantificationLimits limits = new HazopQuantificationLimits().setMaxDischargeTemperatureC(-100.0);
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HazopConsequenceFinding> findings = populator.quantify(process, limits);
    HazopConsequenceFinding compressorFinding = findFinding(findings, "export-compressor");
    assertNotNull(compressorFinding);
    assertEquals(Verdict.EXCEEDS, compressorFinding.getVerdict(), "an absurdly low discharge limit should be exceeded");
    assertTrue(compressorFinding.exceedsLimit());
  }

  @Test
  public void looseMdmtKeepsValveWithinLimit() {
    HazopQuantificationLimits limits = new HazopQuantificationLimits().setMinDesignMetalTemperatureC(-273.0);
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HazopConsequenceFinding> findings = populator.quantify(process, limits);
    HazopConsequenceFinding valveFinding = findFinding(findings, "inlet-valve");
    assertNotNull(valveFinding);
    assertEquals(Verdict.PASS, valveFinding.getVerdict(), "a very low MDMT should not be breached");
  }

  @Test
  public void tightMdmtTriggersExceeds() {
    HazopQuantificationLimits limits = new HazopQuantificationLimits().setMinDesignMetalTemperatureC(200.0);
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HazopConsequenceFinding> findings = populator.quantify(process, limits);
    HazopConsequenceFinding valveFinding = findFinding(findings, "inlet-valve");
    assertNotNull(valveFinding);
    assertEquals(Verdict.EXCEEDS, valveFinding.getVerdict(),
        "an absurdly high MDMT should make any outlet temperature appear too cold");
  }

  @Test
  public void perUnitOverrideIsApplied() {
    HazopQuantificationLimits limits = new HazopQuantificationLimits();
    limits.setMaxDischargeTemperatureC("export-compressor", -100.0);
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HazopConsequenceFinding> findings = populator.quantify(process, limits);
    HazopConsequenceFinding compressorFinding = findFinding(findings, "export-compressor");
    assertNotNull(compressorFinding);
    assertEquals(Verdict.EXCEEDS, compressorFinding.getVerdict(), "per-unit override should drive the verdict");
  }

  @Test
  public void findingSerialisesToJson() {
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HazopConsequenceFinding> findings = populator.quantify(process, new HazopQuantificationLimits());
    HazopConsequenceFinding finding = findings.get(0);
    String json = finding.toJson();
    assertTrue(json.contains("verdict"), "JSON should contain the verdict field");
    assertTrue(json.contains("nodeId"), "JSON should contain the nodeId field");
  }

  @Test
  public void nullProcessThrows() {
    final HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        populator.quantify(null, new HazopQuantificationLimits());
      }
    });
  }

  /**
   * Find the first finding whose unit name matches.
   *
   * @param findings the findings to search
   * @param unitName the unit name to match
   * @return the matching finding, or null if none matches
   */
  private HazopConsequenceFinding findFinding(List<HazopConsequenceFinding> findings, String unitName) {
    for (HazopConsequenceFinding finding : findings) {
      if (unitName.equals(finding.getUnitName())) {
        return finding;
      }
    }
    return null;
  }
}
