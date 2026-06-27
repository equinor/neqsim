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
import neqsim.process.safety.hazid.HAZOPTemplate.GuideWord;
import neqsim.process.safety.hazid.HAZOPTemplate.HAZOPDeviation;
import neqsim.process.safety.hazid.HAZOPTemplate.Parameter;

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
}
