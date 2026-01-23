package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DesignPhase enum.
 *
 * @author esol
 */
class DesignPhaseTest {
  @Nested
  @DisplayName("Basic Properties Tests")
  class BasicPropertiesTests {
    @Test
    @DisplayName("Should have correct display names")
    void shouldHaveCorrectDisplayNames() {
      assertEquals("Screening", DesignPhase.SCREENING.getDisplayName());
      assertEquals("Concept Select", DesignPhase.CONCEPT_SELECT.getDisplayName());
      assertEquals("Pre-FEED", DesignPhase.PRE_FEED.getDisplayName());
      assertEquals("FEED", DesignPhase.FEED.getDisplayName());
      assertEquals("Detail Design", DesignPhase.DETAIL_DESIGN.getDisplayName());
      assertEquals("As-Built", DesignPhase.AS_BUILT.getDisplayName());
    }

    @Test
    @DisplayName("Should have accuracy ranges")
    void shouldHaveAccuracyRanges() {
      assertNotNull(DesignPhase.SCREENING.getAccuracyRange());
      assertTrue(DesignPhase.SCREENING.getAccuracyRange().contains("40"));
    }

    @Test
    @DisplayName("Should have min and max accuracy")
    void shouldHaveMinMaxAccuracy() {
      assertTrue(DesignPhase.FEED.getMinAccuracy() > 0);
      assertTrue(DesignPhase.FEED.getMaxAccuracy() > 0);
      assertTrue(DesignPhase.FEED.getMinAccuracy() <= DesignPhase.FEED.getMaxAccuracy());
    }

    @Test
    @DisplayName("Should have descriptions")
    void shouldHaveDescriptions() {
      for (DesignPhase phase : DesignPhase.values()) {
        assertNotNull(phase.getDescription());
        assertFalse(phase.getDescription().isEmpty());
      }
    }
  }

  @Nested
  @DisplayName("Requirements Tests")
  class RequirementsTests {
    @Test
    @DisplayName("FEED and Detail Design require full mechanical design")
    void feedAndDetailRequireFullDesign() {
      assertTrue(DesignPhase.FEED.requiresFullMechanicalDesign());
      assertTrue(DesignPhase.DETAIL_DESIGN.requiresFullMechanicalDesign());
    }

    @Test
    @DisplayName("Early phases do not require full mechanical design")
    void earlyPhasesDoNotRequireFullDesign() {
      assertFalse(DesignPhase.SCREENING.requiresFullMechanicalDesign());
      assertFalse(DesignPhase.CONCEPT_SELECT.requiresFullMechanicalDesign());
      assertFalse(DesignPhase.PRE_FEED.requiresFullMechanicalDesign());
    }

    @Test
    @DisplayName("Late phases require detailed compliance")
    void latePhasesRequireDetailedCompliance() {
      assertTrue(DesignPhase.FEED.requiresDetailedCompliance());
      assertTrue(DesignPhase.DETAIL_DESIGN.requiresDetailedCompliance());
      assertTrue(DesignPhase.AS_BUILT.requiresDetailedCompliance());
    }

    @Test
    @DisplayName("Early phases do not require detailed compliance")
    void earlyPhasesDoNotRequireDetailedCompliance() {
      assertFalse(DesignPhase.SCREENING.requiresDetailedCompliance());
      assertFalse(DesignPhase.CONCEPT_SELECT.requiresDetailedCompliance());
    }
  }

  @Nested
  @DisplayName("Accuracy Tests")
  class AccuracyTests {
    @Test
    @DisplayName("Later phases should have tighter accuracy")
    void laterPhasesShouldHaveTighterAccuracy() {
      assertTrue(
          DesignPhase.DETAIL_DESIGN.getMaxAccuracy() < DesignPhase.SCREENING.getMaxAccuracy());
      assertTrue(DesignPhase.FEED.getMaxAccuracy() < DesignPhase.CONCEPT_SELECT.getMaxAccuracy());
    }

    @Test
    @DisplayName("As-Built should have minimal accuracy range")
    void asBuiltShouldHaveMinimalAccuracy() {
      assertEquals(0.0, DesignPhase.AS_BUILT.getMinAccuracy());
      assertTrue(DesignPhase.AS_BUILT.getMaxAccuracy() <= 0.05);
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {
    @Test
    @DisplayName("ToString should include name and accuracy")
    void toStringShouldIncludeNameAndAccuracy() {
      String result = DesignPhase.FEED.toString();
      assertTrue(result.contains("FEED"));
      assertTrue(result.contains("%"));
    }
  }
}
