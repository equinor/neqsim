package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.SlugTracker.SlugUnit;

/**
 * Deterministic outlet-statistic contracts between the trackers and TwoFluidPipe.
 *
 * <p>
 * Explicit section/tracker fixtures isolate event accounting from thermodynamic flashes and stochastic slug initiation.
 * Reflection invokes the pipe's existing private statistic-collection seams without advancing its unrelated solver.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TwoFluidPipeSlugOutletContractTest {

  @Test
  void testSimplifiedArrivalUsesPhysicalOutletForDifferentGrids() throws Exception {
    for (int cellCount : new int[] { 4, 20 }) {
      TwoFluidPipe pipe = new TwoFluidPipe("outlet-grid-" + cellCount);
      TwoFluidSection[] sections = configureSections(pipe, cellCount);
      SlugTracker tracker = pipe.getSlugTracker();
      tracker.initializeTerrainSlug(characteristics(80.0, 70.0, sections[0].getArea()), sections);
      List<SlugUnit> advancedSlugs = tracker.getSlugs();
      tracker.advanceSlugs(sections, 0.01);

      collectSimplifiedOutlet(pipe, advancedSlugs, 0.01);

      assertEquals(0, pipe.getOutletSlugCount(),
          "A slug still 20 m upstream must not count as an outlet arrival on either grid");
      assertEquals(0.0, pipe.getLastSlugArrivalTime(), 0.0);
    }
  }

  @Test
  void testSimplifiedWholeBodyCrossingIsCountedOnceAtCompletedSubstepTime() throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("outlet-whole-step");
    TwoFluidSection[] sections = configureSections(pipe, 4);
    SlugTracker tracker = pipe.getSlugTracker();
    SlugUnit slug = tracker.initializeTerrainSlug(characteristics(99.0, 97.0, sections[0].getArea()), sections);
    List<SlugUnit> advancedSlugs = tracker.getSlugs();

    tracker.advanceSlugs(sections, 2.0);
    assertEquals(0, tracker.getSlugCount(), "The whole slug should leave within this tracking step");
    assertTrue(slug.tailPosition >= pipe.getLength());
    setPipeField(pipe, "simulationTime", 10.0);

    collectSimplifiedOutlet(pipe, advancedSlugs, 12.0);
    assertEquals(1, pipe.getOutletSlugCount(), "Removal during the step must not hide the front crossing");
    assertEquals(12.0, pipe.getLastSlugArrivalTime(), 0.0);
    assertEquals(slug.slugBodyLength, pipe.getMaxSlugLengthAtOutlet(), 1.0e-12);
    assertEquals(slug.liquidVolume, pipe.getTotalSlugVolumeAtOutlet(), 1.0e-12);

    collectSimplifiedOutlet(pipe, advancedSlugs, 13.0);
    assertEquals(1, pipe.getOutletSlugCount(), "One physical slug must not be counted twice");
    assertEquals(12.0, pipe.getLastSlugArrivalTime(), 0.0);
    assertEquals(slug.liquidVolume, pipe.getTotalSlugVolumeAtOutlet(), 1.0e-12);
  }

  @Test
  void testLagrangianOutletStatisticsUseExitedBodiesAndExitClock() throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("outlet-lagrangian-history");
    TwoFluidSection[] sections = configureSections(pipe, 4);
    LagrangianSlugTracker tracker = pipe.getLagrangianSlugTracker();
    tracker.setEnableInletSlugGeneration(false);
    tracker.setEnableWakeEffects(false);
    tracker.initializeTerrainSlug(characteristics(102.1, 100.1, sections[0].getArea()), sections);
    tracker.advanceTimeStep(sections, 0.01);
    assertEquals(1, tracker.getTotalSlugsExited());

    SlugBubbleUnit active = tracker.initializeTerrainSlug(characteristics(50.0, 30.0, sections[0].getArea()), sections);
    tracker.advanceTimeStep(sections, 0.01);
    assertTrue(active.slugLength > Collections.max(tracker.getOutletSlugLengths()));

    Method collect = TwoFluidPipe.class.getDeclaredMethod("trackOutletSlugsLagrangian");
    collect.setAccessible(true);
    collect.invoke(pipe);

    assertEquals(1, pipe.getOutletSlugCount());
    assertEquals(Collections.max(tracker.getOutletSlugLengths()), pipe.getMaxSlugLengthAtOutlet(), 1.0e-12,
        "A larger upstream body is not the maximum length observed at the outlet");
    assertEquals(tracker.getOutletSlugVolumes().get(0), pipe.getTotalSlugVolumeAtOutlet(), 1.0e-12);
    assertEquals(tracker.getMaxSlugVolumeAtOutlet(), pipe.getMaxSlugVolumeAtOutlet(), 1.0e-12);
    assertEquals(tracker.getLastOutletArrivalTime(), pipe.getLastSlugArrivalTime(), 0.0);
    assertEquals(0.01, pipe.getLastSlugArrivalTime(), 1.0e-12,
        "The outlet clock must record the exit event, independently of later steps");
  }

  @Test
  void testAbsorbedBodyIsNotAnAdditionalOutletEvent() throws Exception {
    TwoFluidPipe pipe = new TwoFluidPipe("outlet-merged-step");
    TwoFluidSection[] sections = configureSections(pipe, 4);
    SlugTracker tracker = pipe.getSlugTracker();
    tracker.initializeTerrainSlug(characteristics(101.0, 99.0, sections[0].getArea()), sections);
    SlugUnit survivor = tracker.initializeTerrainSlug(characteristics(98.5, 96.5, sections[0].getArea()), sections);
    List<SlugUnit> advancedSlugs = tracker.getSlugs();

    tracker.advanceSlugs(sections, 2.0);
    assertEquals(1, tracker.getTotalSlugsMerged());
    assertEquals(0, tracker.getSlugCount());
    assertTrue(survivor.hasExited);

    collectSimplifiedOutlet(pipe, advancedSlugs, 2.0);
    assertEquals(1, pipe.getOutletSlugCount(), "The absorbed snapshot entry is not an independently exiting body");
    assertEquals(survivor.liquidVolume, pipe.getTotalSlugVolumeAtOutlet(), 1.0e-12);
  }

  /**
   * Configure a pipe with explicit uniform, midpoint-positioned sections and finite two-phase properties.
   *
   * @param pipe pipe fixture
   * @param count number of cells
   * @return sections installed in the pipe
   * @throws Exception if the fixture cannot set the pipe sections
   */
  private TwoFluidSection[] configureSections(TwoFluidPipe pipe, int count) throws Exception {
    pipe.setLength(100.0);
    pipe.setDiameter(0.1);
    pipe.setNumberOfSections(count);
    TwoFluidSection[] sections = new TwoFluidSection[count];
    double dx = pipe.getLength() / count;
    for (int i = 0; i < count; i++) {
      TwoFluidSection section = new TwoFluidSection((i + 0.5) * dx, dx, 0.1, 0.0);
      section.setLiquidDensity(800.0);
      section.setGasDensity(50.0);
      section.setLiquidHoldup(0.2);
      section.setGasHoldup(0.8);
      section.setLiquidVelocity(1.0);
      section.setGasVelocity(3.0);
      section.updateDerivedQuantities();
      sections[i] = section;
    }
    setPipeField(pipe, "sections", sections);
    return sections;
  }

  /**
   * Create a slug with matching initial length and volume.
   *
   * @param front front position (m)
   * @param tail tail position (m)
   * @param area cross-sectional area (m2)
   * @return slug characteristics
   */
  private SlugCharacteristics characteristics(double front, double tail, double area) {
    SlugCharacteristics chars = new SlugCharacteristics();
    chars.frontPosition = front;
    chars.tailPosition = tail;
    chars.length = front - tail;
    chars.holdup = 0.85;
    chars.velocity = 2.0;
    chars.volume = chars.length * area * chars.holdup;
    return chars;
  }

  /**
   * Invoke the pipe's collection of advanced simplified-slug events.
   *
   * @param pipe pipe fixture
   * @param slugs references captured before tracking, including bodies removed during the step
   * @param stepEndTime accepted substep end time (s)
   * @throws Exception if statistic collection fails
   */
  private void collectSimplifiedOutlet(TwoFluidPipe pipe, List<SlugUnit> slugs, double stepEndTime) throws Exception {
    Method collect = TwoFluidPipe.class.getDeclaredMethod("trackOutletSlugs", List.class, double.class);
    collect.setAccessible(true);
    collect.invoke(pipe, slugs, stepEndTime);
  }

  /**
   * Install a fixture field without invoking the pipe's flow solver.
   *
   * @param pipe pipe fixture
   * @param name field name
   * @param value field value
   * @throws Exception if the field cannot be installed
   */
  private void setPipeField(TwoFluidPipe pipe, String name, Object value) throws Exception {
    Field field = TwoFluidPipe.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(pipe, value);
  }
}
