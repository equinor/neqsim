package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EvidenceSynthesis}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class EvidenceSynthesisTest {

  /**
   * A hypothesis corroborated by more distinct source types outranks a single-source hypothesis with a similar net
   * score, and the multi-source one is labelled at least MODERATE.
   */
  @Test
  void multiSourceCorroborationOutranksSingleSource() {
    EvidenceSynthesis es = new EvidenceSynthesis();
    es.addHypothesis("H1", "Sulfur deposition");
    es.addHypothesis("H2", "Instrument drift");
    es.addEvidence("H1", "historian", true, 0.6, "dP trend");
    es.addEvidence("H1", "literature", true, 0.5, "S8 letdown study");
    es.addEvidence("H1", "simulation", true, 0.6, "NeqSim S8 saturation");
    es.addEvidence("H2", "historian", true, 0.9, "single tag noise");

    List<EvidenceSynthesis.RankedHypothesis> ranked = es.rank();
    assertEquals("H1", ranked.get(0).getId());
    assertEquals(1, ranked.get(0).getRank());
    assertTrue(ranked.get(0).isCorroborated());
    assertEquals("STRONG", ranked.get(0).getConfidence());
    assertEquals(3, ranked.get(0).getSupportingSourceCount());
    // H2 is supported by a single source type -> WEAK.
    assertEquals("WEAK", ranked.get(1).getConfidence());
  }

  /** Contradicting evidence at least as strong as supporting evidence marks a hypothesis DISPUTED. */
  @Test
  void contradictingEvidenceMarksDisputed() {
    EvidenceSynthesis es = new EvidenceSynthesis();
    es.addHypothesis("H1", "Fouling");
    es.addEvidence("H1", "historian", true, 0.5, "supports");
    es.addEvidence("H1", "maintenance", false, 0.6, "recent clean found no fouling");
    List<EvidenceSynthesis.RankedHypothesis> ranked = es.rank();
    assertEquals("UNSUPPORTED", ranked.get(0).getConfidence());
    assertTrue(ranked.get(0).getNetScore() < 0.0);
  }

  /** The single-source warning fires when the top hypothesis has only one supporting source type. */
  @Test
  void singleSourceWarningInJson() {
    EvidenceSynthesis es = new EvidenceSynthesis();
    es.addHypothesis("H1", "Only historian");
    es.addEvidence("H1", "historian", true, 0.8, "one source");
    String json = es.toJson();
    assertTrue(json.contains("\"singleSourceWarning\": true"));
    assertTrue(json.contains("\"topHypothesisId\": \"H1\""));
    assertTrue(json.contains("\"schemaVersion\": \"1.0\""));
  }

  /** Invalid inputs are rejected. */
  @Test
  void invalidInputsRejected() {
    EvidenceSynthesis es = new EvidenceSynthesis();
    es.addHypothesis("H1", "x");
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        es.addHypothesis("H1", "duplicate");
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        es.addEvidence("H1", "historian", true, 1.5, "out of range");
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        es.addEvidence("missing", "historian", true, 0.5, "unknown hypothesis");
      }
    });
  }

  /** An empty synthesis produces valid JSON with no warning. */
  @Test
  void emptySynthesisIsValid() {
    EvidenceSynthesis es = new EvidenceSynthesis();
    String json = es.toJson();
    assertTrue(json.contains("\"hypotheses\": []"));
    assertFalse(json.contains("\"singleSourceWarning\": true"));
  }
}
