package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DesignCase enum.
 *
 * @author esol
 */
class DesignCaseTest {

  @Nested
  @DisplayName("Basic Properties Tests")
  class BasicPropertiesTests {

    @Test
    @DisplayName("Should have correct display names")
    void shouldHaveCorrectDisplayNames() {
      assertEquals("Normal", DesignCase.NORMAL.getDisplayName());
      assertEquals("Maximum", DesignCase.MAXIMUM.getDisplayName());
      assertEquals("Minimum", DesignCase.MINIMUM.getDisplayName());
      assertEquals("Upset", DesignCase.UPSET.getDisplayName());
      assertEquals("Emergency", DesignCase.EMERGENCY.getDisplayName());
    }

    @Test
    @DisplayName("Should have descriptions")
    void shouldHaveDescriptions() {
      for (DesignCase dc : DesignCase.values()) {
        assertNotNull(dc.getDescription());
        assertFalse(dc.getDescription().isEmpty());
      }
    }

    @Test
    @DisplayName("Should have typical load factors")
    void shouldHaveLoadFactors() {
      assertEquals(1.0, DesignCase.NORMAL.getTypicalLoadFactor());
      assertTrue(DesignCase.MAXIMUM.getTypicalLoadFactor() > 1.0);
      assertTrue(DesignCase.MINIMUM.getTypicalLoadFactor() < 1.0);
    }
  }

  @Nested
  @DisplayName("Sizing Tests")
  class SizingTests {

    @Test
    @DisplayName("Maximum case should be sizing critical")
    void maximumShouldBeSizingCritical() {
      assertTrue(DesignCase.MAXIMUM.isSizingCritical());
    }

    @Test
    @DisplayName("Early life case should be sizing critical")
    void earlyLifeShouldBeSizingCritical() {
      assertTrue(DesignCase.EARLY_LIFE.isSizingCritical());
    }

    @Test
    @DisplayName("Upset case should be sizing critical")
    void upsetShouldBeSizingCritical() {
      assertTrue(DesignCase.UPSET.isSizingCritical());
    }

    @Test
    @DisplayName("Normal case should not be sizing critical")
    void normalShouldNotBeSizingCritical() {
      assertFalse(DesignCase.NORMAL.isSizingCritical());
    }
  }

  @Nested
  @DisplayName("Turndown Tests")
  class TurndownTests {

    @Test
    @DisplayName("Minimum case should be turndown")
    void minimumShouldBeTurndown() {
      assertTrue(DesignCase.MINIMUM.isTurndownCase());
    }

    @Test
    @DisplayName("Late life case should be turndown")
    void lateLifeShouldBeTurndown() {
      assertTrue(DesignCase.LATE_LIFE.isTurndownCase());
    }

    @Test
    @DisplayName("Normal case should not be turndown")
    void normalShouldNotBeTurndown() {
      assertFalse(DesignCase.NORMAL.isTurndownCase());
    }

    @Test
    @DisplayName("Maximum case should not be turndown")
    void maximumShouldNotBeTurndown() {
      assertFalse(DesignCase.MAXIMUM.isTurndownCase());
    }
  }

  @Nested
  @DisplayName("Relief Tests")
  class ReliefTests {

    @Test
    @DisplayName("Upset case should require relief sizing")
    void upsetShouldRequireRelief() {
      assertTrue(DesignCase.UPSET.requiresReliefSizing());
    }

    @Test
    @DisplayName("Emergency case should require relief sizing")
    void emergencyShouldRequireRelief() {
      assertTrue(DesignCase.EMERGENCY.requiresReliefSizing());
    }

    @Test
    @DisplayName("Normal case should not require relief sizing")
    void normalShouldNotRequireRelief() {
      assertFalse(DesignCase.NORMAL.requiresReliefSizing());
    }

    @Test
    @DisplayName("Maximum case should not require relief sizing")
    void maximumShouldNotRequireRelief() {
      assertFalse(DesignCase.MAXIMUM.requiresReliefSizing());
    }
  }

  @Nested
  @DisplayName("Load Factor Tests")
  class LoadFactorTests {

    @Test
    @DisplayName("Upset should have higher load than normal")
    void upsetShouldHaveHigherLoad() {
      assertTrue(
          DesignCase.UPSET.getTypicalLoadFactor() > DesignCase.NORMAL.getTypicalLoadFactor());
    }

    @Test
    @DisplayName("Emergency should have highest load")
    void emergencyShouldHaveHighestLoad() {
      assertTrue(
          DesignCase.EMERGENCY.getTypicalLoadFactor() >= DesignCase.UPSET.getTypicalLoadFactor());
    }

    @Test
    @DisplayName("Late life should have lower load than normal")
    void lateLifeShouldHaveLowerLoad() {
      assertTrue(
          DesignCase.LATE_LIFE.getTypicalLoadFactor() < DesignCase.NORMAL.getTypicalLoadFactor());
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("ToString should include name and load percentage")
    void toStringShouldIncludeNameAndLoad() {
      String result = DesignCase.NORMAL.toString();
      assertTrue(result.contains("Normal"));
      assertTrue(result.contains("100%"));
    }
  }
}
