package neqsim.process.safety.hazid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.hazid.HAZOPTemplate.GuideWord;
import neqsim.process.safety.hazid.HAZOPTemplate.HAZOPDeviation;
import neqsim.process.safety.hazid.HAZOPTemplate.Parameter;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for {@link HazopConsequenceAutoPopulator}.
 *
 * @author ESOL
 * @version 1.0
 */
public class HazopConsequenceAutoPopulatorTest {

  /**
   * The catalogue exposes the documented overpressure and runaway-reaction mappings.
   */
  @Test
  public void catalogueContainsKeyDeviations() {
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HazopConsequenceMapping> catalogue = populator.catalogue();
    assertFalse(catalogue.isEmpty());

    HazopConsequenceMapping overpressure = populator.mappingFor(GuideWord.MORE, Parameter.PRESSURE);
    assertNotNull(overpressure);
    assertTrue(overpressure.getRecommendedCalculator().contains("ReliefValveSizing"));
    assertTrue(overpressure.getStandardReference().contains("API 520"));

    HazopConsequenceMapping runaway = populator.mappingFor(GuideWord.OTHER_THAN, Parameter.REACTION);
    assertNotNull(runaway);
    assertEquals("RunawayReactionAnalyzer", runaway.getRecommendedCalculator());
  }

  /**
   * Vacuum and deadhead deviations map to the dedicated screening calculators.
   */
  @Test
  public void partialGapDeviationsMapToNewCalculators() {
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();

    HazopConsequenceMapping vacuum = populator.mappingFor(GuideWord.LESS, Parameter.PRESSURE);
    assertNotNull(vacuum);
    assertEquals("VacuumCollapseAnalyzer", vacuum.getRecommendedCalculator());

    HazopConsequenceMapping deadhead = populator.mappingFor(GuideWord.NO, Parameter.FLOW);
    assertNotNull(deadhead);
    assertEquals("PumpDeadheadAnalyzer", deadhead.getRecommendedCalculator());

    HazopConsequenceMapping reverse = populator.mappingFor(GuideWord.REVERSE, Parameter.FLOW);
    assertNotNull(reverse);
    assertEquals("WaterHammerStudy", reverse.getRecommendedCalculator());
  }

  /**
   * Deviations without a catalogue entry resolve to null.
   */
  @Test
  public void unknownDeviationReturnsNull() {
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    assertNull(populator.mappingFor(GuideWord.AS_WELL_AS, Parameter.LEVEL));
    assertNull(populator.mappingFor(null, Parameter.FLOW));
  }

  /**
   * Populating a grid replaces placeholder cells with mapped consequence and safeguard content.
   */
  @Test
  public void populateReplacesPlaceholders() {
    HAZOPTemplate node = new HAZOPTemplate("Node-1: V-100", "Hold inventory");
    node.generateGrid(Parameter.PRESSURE);
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    HAZOPTemplate populated = populator.populate(node);

    boolean foundOverpressure = false;
    for (HAZOPDeviation d : populated.getDeviations()) {
      if (d.guideWord == GuideWord.MORE && d.parameter == Parameter.PRESSURE) {
        foundOverpressure = true;
        assertFalse("TBD".equals(d.consequence));
        assertTrue(d.consequence.toLowerCase().contains("overpressure"));
        assertFalse("TBD".equals(d.safeguard));
        assertNotNull(d.recommendation);
        assertTrue(d.recommendation.contains("ReliefValveSizing"));
      }
    }
    assertTrue(foundOverpressure);
  }

  /**
   * Existing (non-placeholder) consequence text is preserved rather than overwritten.
   */
  @Test
  public void populatePreservesExistingContent() {
    HAZOPTemplate node = new HAZOPTemplate("Node-2: pump", "Deliver flow");
    node.addDeviation(GuideWord.NO, Parameter.FLOW, "Closed discharge valve", "Custom consequence text",
        "Existing safeguard", "Existing recommendation");
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    HAZOPTemplate populated = populator.populate(node);

    HAZOPDeviation d = populated.getDeviations().get(0);
    assertEquals("Custom consequence text", d.consequence);
    assertEquals("Existing safeguard", d.safeguard);
    assertEquals("Existing recommendation", d.recommendation);
  }

  /**
   * Populating a null source raises an {@link IllegalArgumentException}.
   */
  @Test
  public void populateRejectsNullSource() {
    final HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    assertThrows(IllegalArgumentException.class, new Executable() {
      @Override
      public void execute() {
        populator.populate(null);
      }
    });
  }

  /**
   * Each mapping serialises to non-empty JSON carrying its key fields.
   */
  @Test
  public void mappingSerialisesToJson() {
    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    HazopConsequenceMapping mapping = populator.mappingFor(GuideWord.LESS, Parameter.TEMPERATURE);
    assertNotNull(mapping);
    String json = mapping.toJson();
    assertTrue(json.contains("MDMTCalculator"));
    assertTrue(json.contains("UCS-66"));
    assertTrue(populator.catalogueToJson().contains("RunawayReactionAnalyzer"));
  }

  /**
   * Process-derived nodes select an equipment-compatible no-flow mapping or an explicit neutral fallback.
   */
  @Test
  public void processDerivedNoFlowMappingsAreEquipmentAware() {
    SystemSrkEos fluid = new SystemSrkEos(308.15, 72.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed stream", fluid);
    ThrottlingValve valve = new ThrottlingValve("Inlet valve", feed);
    valve.setOutletPressure(55.0, "bara");
    Separator separator = new Separator("HP separator", valve.getOutletStream());
    Compressor compressor = new Compressor("Export compressor", separator.getGasOutStream());
    Cooler cooler = new Cooler("Export cooler", compressor.getOutletStream());
    Pump pump = new Pump("Feed pump", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.add(pump);

    HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
    List<HAZOPTemplate> nodes = HAZOPTemplate.fromProcessSystem(process);
    String[] neutralTypes = { "Stream", "ThrottlingValve", "Separator", "Cooler" };
    for (String equipmentType : neutralTypes) {
      HAZOPTemplate populated = populator.populate(findNode(nodes, equipmentType));
      HAZOPDeviation noFlow = findDeviation(populated, GuideWord.NO, Parameter.FLOW);
      assertFalse(noFlow.consequence.contains("Pump deadhead"));
      assertFalse(noFlow.recommendation.contains("PumpDeadheadAnalyzer"));
      assertTrue(noFlow.consequence.toLowerCase().contains("facilitator confirmation required"));
      assertTrue(noFlow.recommendation.toLowerCase().contains("facilitator confirmation required"));
    }

    HAZOPDeviation compressorNoFlow = findDeviation(populator.populate(findNode(nodes, "Compressor")), GuideWord.NO,
        Parameter.FLOW);
    assertTrue(compressorNoFlow.consequence.toLowerCase().contains("surge"));
    assertFalse(compressorNoFlow.recommendation.contains("PumpDeadheadAnalyzer"));

    HAZOPDeviation pumpNoFlow = findDeviation(populator.populate(findNode(nodes, "Pump")), GuideWord.NO,
        Parameter.FLOW);
    assertTrue(pumpNoFlow.consequence.contains("Pump deadhead"));
    assertTrue(pumpNoFlow.recommendation.contains("PumpDeadheadAnalyzer"));
  }

  /**
   * A manually authored template without equipment metadata retains the legacy generic catalogue.
   */
  @Test
  public void manualTemplateRetainsGenericNoFlowMapping() {
    HAZOPTemplate manual = new HAZOPTemplate("Manual node", "Facilitator-defined design intent");
    manual.addDeviation(GuideWord.NO, Parameter.FLOW, "TBD", "TBD", "TBD", null);

    HAZOPDeviation noFlow = new HazopConsequenceAutoPopulator().populate(manual).getDeviations().get(0);
    assertTrue(noFlow.consequence.contains("Pump deadhead"));
    assertTrue(noFlow.recommendation.contains("PumpDeadheadAnalyzer"));
  }

  /**
   * Find a deviation row by guide word and parameter.
   *
   * @param node populated HAZOP node
   * @param guideWord guide word to find
   * @param parameter parameter to find
   * @return matching deviation
   */
  private HAZOPDeviation findDeviation(HAZOPTemplate node, GuideWord guideWord, Parameter parameter) {
    for (HAZOPDeviation deviation : node.getDeviations()) {
      if (deviation.guideWord == guideWord && deviation.parameter == parameter) {
        return deviation;
      }
    }
    throw new IllegalStateException("deviation not found: " + guideWord + " " + parameter);
  }

  /**
   * Find a process-derived node by its equipment metadata.
   *
   * @param nodes process-derived HAZOP nodes
   * @param equipmentType expected simple equipment class name
   * @return matching node
   */
  private HAZOPTemplate findNode(List<HAZOPTemplate> nodes, String equipmentType) {
    for (HAZOPTemplate node : nodes) {
      if (equipmentType.equals(node.getEquipmentType())) {
        return node;
      }
    }
    throw new IllegalStateException("node not found for equipment type " + equipmentType);
  }
}
