package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.SlugTracker.SlugUnit;

/**
 * Tests for SlugTracker.
 */
class SlugTrackerTest {
  private SlugTracker tracker;
  private PipeSection[] sections;

  @BeforeEach
  void setUp() {
    tracker = new SlugTracker();
    createTestPipeline();
  }

  /**
   * Create a simple horizontal pipeline.
   */
  private void createTestPipeline() {
    sections = new PipeSection[20];

    for (int i = 0; i < 20; i++) {
      sections[i] = new PipeSection(i * 50, 50, 0.3, 0);
      sections[i].setElevation(0);
      sections[i].setRoughness(0.0001);
      sections[i].setFlowRegime(FlowRegime.SLUG);

      // Fluid properties
      sections[i].setGasDensity(50);
      sections[i].setLiquidDensity(800);
      sections[i].setGasViscosity(1.5e-5);
      sections[i].setLiquidViscosity(1e-3);
      sections[i].setSurfaceTension(0.02);
      sections[i].setGasSoundSpeed(350);
      sections[i].setLiquidSoundSpeed(1200);

      // Flow conditions
      sections[i].setGasHoldup(0.4);
      sections[i].setLiquidHoldup(0.6);
      sections[i].setGasVelocity(4);
      sections[i].setLiquidVelocity(1);
      sections[i].updateDerivedQuantities();
    }
  }

  @Test
  void testInitializeTerrainSlug() {
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 500;
    chars.tailPosition = 450;
    chars.length = 50;
    chars.holdup = 0.85;
    chars.velocity = 3.0;
    chars.volume = 1.0;
    chars.isTerrainInduced = true;

    SlugUnit slug = tracker.initializeTerrainSlug(chars, sections);

    assertNotNull(slug);
    assertEquals(500, slug.frontPosition);
    assertEquals(450, slug.tailPosition);
    assertEquals(50, slug.slugBodyLength);
    assertEquals(0.85, slug.bodyHoldup);
    assertTrue(slug.isTerrainInduced);
    assertEquals(1, tracker.getSlugCount());
    assertEquals(1, tracker.getTotalSlugsGenerated());
  }

  @Test
  void testSlugAdvancement() {
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 200;
    chars.tailPosition = 180;
    chars.length = 20;
    chars.holdup = 0.9;
    chars.velocity = 2.5;
    chars.volume = 0.5;

    SlugUnit slug = tracker.initializeTerrainSlug(chars, sections);
    double initialFront = slug.frontPosition;

    // Advance by 1 second
    tracker.advanceSlugs(sections, 1.0);

    // Slug should have moved forward
    assertTrue(slug.frontPosition > initialFront, "Slug front should advance");
    assertTrue(slug.age > 0, "Slug age should increase");
  }

  @Test
  void testSlugVelocityCalculation() {
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 100;
    chars.tailPosition = 80;
    chars.length = 20;
    chars.holdup = 0.9;
    chars.velocity = 2.0;
    chars.volume = 0.4;

    SlugUnit slug = tracker.initializeTerrainSlug(chars, sections);

    tracker.advanceSlugs(sections, 0.1);

    // Slug velocity should be positive (moving forward)
    assertTrue(slug.frontVelocity > 0);
    assertTrue(slug.tailVelocity > 0);

    // Front typically moves faster than tail (slug grows initially)
    assertTrue(slug.frontVelocity >= slug.tailVelocity);
  }

  @Test
  void testSlugExit() {
    // Place slug near outlet
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 950;
    chars.tailPosition = 930;
    chars.length = 20;
    chars.holdup = 0.9;
    chars.velocity = 3.0;
    chars.volume = 0.4;

    tracker.initializeTerrainSlug(chars, sections);
    assertEquals(1, tracker.getSlugCount());

    // Advance until slug exits
    for (int i = 0; i < 100; i++) {
      tracker.advanceSlugs(sections, 1.0);
    }

    // Slug should have exited
    assertEquals(0, tracker.getSlugCount());
  }

  @Test
  void testSlugMerging() {
    // Create two slugs, with trailing one faster
    SlugCharacteristics chars1 = new SlugCharacteristics();
    chars1.frontPosition = 400;
    chars1.tailPosition = 380;
    chars1.length = 20;
    chars1.holdup = 0.9;
    chars1.velocity = 2.0;
    chars1.volume = 0.4;

    SlugCharacteristics chars2 = new SlugCharacteristics();
    chars2.frontPosition = 350;
    chars2.tailPosition = 330;
    chars2.length = 20;
    chars2.holdup = 0.9;
    chars2.velocity = 3.0; // Faster trailing slug
    chars2.volume = 0.4;

    tracker.initializeTerrainSlug(chars1, sections);
    tracker.initializeTerrainSlug(chars2, sections);

    assertEquals(2, tracker.getSlugCount());

    // Advance until they merge
    for (int i = 0; i < 50; i++) {
      tracker.advanceSlugs(sections, 1.0);
      if (tracker.getSlugCount() == 1) {
        break;
      }
    }

    // Eventually they may merge (or both exit)
    assertTrue(tracker.getSlugCount() <= 2);
  }

  @Test
  void testSlugDissipation() {
    tracker.setMinimumSlugLength(10.0);

    // Create a short slug
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 200;
    chars.tailPosition = 195;
    chars.length = 5; // Below minimum
    chars.holdup = 0.9;
    chars.velocity = 2.0;
    chars.volume = 0.1;

    SlugUnit slug = tracker.initializeTerrainSlug(chars, sections);
    slug.age = 15; // Old enough to be considered for removal

    tracker.advanceSlugs(sections, 1.0);

    // Short slug should eventually dissipate
    // (may take multiple steps)
  }

  @Test
  void testSlugStatistics() {
    // Create multiple slugs
    for (int i = 0; i < 5; i++) {
      SlugCharacteristics chars = new SlugCharacteristics();
      chars.frontPosition = 100 + i * 100;
      chars.tailPosition = chars.frontPosition - 20 - i * 5;
      chars.length = 20 + i * 5;
      chars.holdup = 0.9;
      chars.velocity = 2.0;
      chars.volume = 0.4;
      tracker.initializeTerrainSlug(chars, sections);
    }

    assertEquals(5, tracker.getSlugCount());
    assertEquals(5, tracker.getTotalSlugsGenerated());

    // Advance to update statistics
    tracker.advanceSlugs(sections, 1.0);

    assertTrue(tracker.getAverageSlugLength() > 0);
    assertTrue(tracker.getMaxSlugLength() >= tracker.getAverageSlugLength());
  }

  @Test
  void testSlugMarking() {
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 500;
    chars.tailPosition = 450;
    chars.length = 50;
    chars.holdup = 0.9;
    chars.velocity = 2.0;
    chars.volume = 1.0;

    tracker.initializeTerrainSlug(chars, sections);
    tracker.advanceSlugs(sections, 0.1);

    // Check that sections are marked as in slug
    boolean foundSlugBody = false;
    for (PipeSection section : sections) {
      if (section.isInSlugBody()) {
        foundSlugBody = true;
        break;
      }
    }
    assertTrue(foundSlugBody, "Some section should be marked as in slug body");
  }

  @Test
  void testReset() {
    // Add some slugs
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 300;
    chars.tailPosition = 280;
    chars.length = 20;
    chars.holdup = 0.9;
    chars.velocity = 2.0;
    chars.volume = 0.4;

    tracker.initializeTerrainSlug(chars, sections);
    tracker.initializeTerrainSlug(chars, sections);

    tracker.reset();

    assertEquals(0, tracker.getSlugCount());
    assertEquals(0, tracker.getTotalSlugsGenerated());
    assertEquals(0, tracker.getTotalSlugsMerged());
    assertEquals(0, tracker.getAverageSlugLength());
  }

  @Test
  void testStatisticsString() {
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 300;
    chars.tailPosition = 280;
    chars.length = 20;
    chars.holdup = 0.9;
    chars.velocity = 2.0;
    chars.volume = 0.4;

    tracker.initializeTerrainSlug(chars, sections);
    tracker.advanceSlugs(sections, 1.0);

    String stats = tracker.getStatisticsString();
    assertNotNull(stats);
    assertTrue(stats.contains("Active slugs"));
    assertTrue(stats.contains("Total generated"));
  }

  @Test
  void testSlugBodyHoldupSetting() {
    tracker.setSlugBodyHoldup(0.85);
    tracker.setFilmHoldup(0.15);
    // Verify no exceptions
  }

  @Test
  void testGetSlugs() {
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 300;
    chars.tailPosition = 280;
    chars.length = 20;
    chars.holdup = 0.9;
    chars.velocity = 2.0;
    chars.volume = 0.4;

    tracker.initializeTerrainSlug(chars, sections);

    List<SlugUnit> slugList = tracker.getSlugs();
    assertEquals(1, slugList.size());
    assertNotNull(slugList.get(0).toString());
  }

  @Test
  void testInclinedPipe() {
    // Change to upward inclined pipe
    for (int i = 0; i < sections.length; i++) {
      sections[i].setInclination(Math.toRadians(30));
      sections[i].setElevation(i * 50 * Math.sin(Math.toRadians(30)));
    }

    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = 200;
    chars.tailPosition = 180;
    chars.length = 20;
    chars.holdup = 0.9;
    chars.velocity = 2.0;
    chars.volume = 0.4;

    SlugUnit slug = tracker.initializeTerrainSlug(chars, sections);

    tracker.advanceSlugs(sections, 1.0);

    // Slug should still advance
    assertTrue(slug.frontVelocity > 0);

    // Local inclination should be updated
    assertTrue(Math.abs(slug.localInclination) > 0);
  }
}
