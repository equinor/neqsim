package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.SlugProbe.Crossing;
import neqsim.process.equipment.pipeline.twophasepipe.SlugProbe.InterfaceType;

class SlugProbeTest {
  @Test
  void crossingTimesLengthsAndReverseDirectionsFollowLinearAcceptedMotion() {
    SlugProbe probe = new SlugProbe(2.0);
    probe.recordMotion(7, 10.0, 2.0, 1.0, 0.0, 5.0, 1.0);
    List<Crossing> first = probe.drainEvents();
    assertEquals(1, first.size());
    assertEquals(7, first.get(0).getSlugId());
    assertEquals(InterfaceType.FRONT, first.get(0).getInterfaceType());
    assertEquals(10.5, first.get(0).getTime(), 0.0);
    assertEquals(2.0, first.get(0).getVelocity(), 0.0);
    assertEquals(1.75, first.get(0).getSlugLength(), 0.0);
    assertEquals(1, first.get(0).getDirection());

    probe.recordMotion(7, 12.0, 2.0, 5.0, 1.0, 6.0, 2.0);
    assertEquals(14.0, probe.drainEvents().get(0).getTime(), 0.0, "An exact endpoint arrival is included");
    probe.recordMotion(7, 14.0, 2.0, 6.0, 2.0, 8.0, 4.0);
    assertTrue(probe.drainEvents().isEmpty(), "Departing an already observed endpoint is not counted twice");

    probe.recordMotion(7, 16.0, 4.0, 8.0, 4.0, 0.0, -4.0);
    List<Crossing> reverse = probe.drainEvents();
    assertEquals(2, reverse.size());
    assertEquals(InterfaceType.TAIL, reverse.get(0).getInterfaceType());
    assertEquals(17.0, reverse.get(0).getTime(), 0.0);
    assertEquals(InterfaceType.FRONT, reverse.get(1).getInterfaceType());
    assertEquals(19.0, reverse.get(1).getTime(), 0.0);
    for (Crossing event : reverse) {
      assertEquals(-1, event.getDirection());
      assertEquals(-2.0, event.getVelocity(), 0.0);
      assertEquals(2.0, event.getSpeed(), 0.0);
      assertEquals(4.0, event.getSlugLength(), 0.0);
    }
  }

  @Test
  void wholeStepCrossingsSurviveExitAndUseTheTrackerStartTime() {
    PipeSection[] sections = sections(2.0);
    LagrangianSlugTracker tracker = tracker();
    SlugProbe probe = tracker.addProbe(4.0);
    tracker.advanceTimeStep(sections, 2.0);
    SlugBubbleUnit slug = initialize(tracker, sections, 1.0, 3.0);

    tracker.advanceTimeStep(sections, 5.0);

    assertEquals(0, tracker.getSlugCount());
    assertEquals(1, tracker.getTotalSlugsExited());
    List<Crossing> events = probe.getEvents();
    assertEquals(2, events.size(), "Both interfaces are observed even when the entire slug exits in one step");
    assertEquals(InterfaceType.FRONT, events.get(0).getInterfaceType());
    assertEquals(InterfaceType.TAIL, events.get(1).getInterfaceType());
    assertEquals(2.0 + 1.0 / 2.1, events.get(0).getTime(), 1.0e-12);
    assertEquals(2.0 + 3.0 / 2.1, events.get(1).getTime(), 1.0e-12);
    for (Crossing event : events) {
      assertEquals(slug.id, event.getSlugId());
      assertEquals(2.1, event.getVelocity(), 1.0e-12);
      assertEquals(2.0, event.getSlugLength(), 1.0e-12);
    }
  }

  @Test
  void reversedTrackerMotionRecordsTailBeforeFront() {
    PipeSection[] sections = sections(-2.0);
    LagrangianSlugTracker tracker = tracker();
    SlugProbe probe = tracker.addProbe(4.0);
    initialize(tracker, sections, 5.0, 7.0);

    tracker.advanceTimeStep(sections, 2.0);

    List<Crossing> events = probe.drainEvents();
    assertEquals(2, events.size());
    assertEquals(InterfaceType.TAIL, events.get(0).getInterfaceType());
    assertEquals(InterfaceType.FRONT, events.get(1).getInterfaceType());
    assertEquals(1.0 / 2.1, events.get(0).getTime(), 1.0e-12);
    assertEquals(3.0 / 2.1, events.get(1).getTime(), 1.0e-12);
    assertEquals(-2.1, events.get(0).getVelocity(), 1.0e-12);
    assertEquals(-1, events.get(0).getDirection());
  }

  @Test
  void individualCrossingsAreRecordedBeforeTheirMarkersMerge() {
    PipeSection[] sections = sections(2.0);
    LagrangianSlugTracker tracker = tracker();
    SlugProbe probe = tracker.addProbe(4.0);
    SlugBubbleUnit following = initialize(tracker, sections, 1.0, 3.0);
    SlugBubbleUnit leading = initialize(tracker, sections, 3.5, 5.0);

    tracker.advanceTimeStep(sections, 0.6);

    assertEquals(1, tracker.getSlugCount());
    assertEquals(1, tracker.getTotalSlugsMerged());
    List<Crossing> events = probe.drainEvents();
    assertEquals(2, events.size());
    assertEquals(leading.id, events.get(0).getSlugId());
    assertEquals(InterfaceType.TAIL, events.get(0).getInterfaceType());
    assertEquals(following.id, events.get(1).getSlugId());
    assertEquals(InterfaceType.FRONT, events.get(1).getInterfaceType());
    assertEquals(0.5 / 2.1, events.get(0).getTime(), 1.0e-12);
    assertEquals(1.0 / 2.1, events.get(1).getTime(), 1.0e-12);
    assertEquals(2.0, events.get(1).getSlugLength(), 1.0e-12, "The front event retains its pre-merge body length");
  }

  @Test
  void overlappingBodiesProduceOneArrivalAndDepartureWithUnionLength() {
    SlugProbe probe = new SlugProbe(2.0);
    probe.beginStep();
    probe.recordMotion(1, 0.0, 1.0, 1.0, 0.0, 4.0, 3.0);
    probe.recordMotion(2, 0.0, 1.0, 0.5, -0.5, 3.5, 2.5);
    probe.endStep();

    List<Crossing> events = probe.getEvents();
    assertEquals(2, events.size());
    assertEquals(1, events.get(0).getSlugId());
    assertEquals(InterfaceType.FRONT, events.get(0).getInterfaceType());
    assertEquals(2, events.get(1).getSlugId());
    assertEquals(InterfaceType.TAIL, events.get(1).getInterfaceType());
    assertEquals(1.5, events.get(0).getSlugLength(), 1.0e-12);
    assertEquals(1.5, events.get(1).getSlugLength(), 1.0e-12);
  }

  @Test
  void collisionWithinAcceptedStepSuppressesInteriorEndpointCrossings() {
    PipeSection[] sections = sections(1.0);
    sections[0].setPosition(1.5);
    sections[0].setLength(3.0);
    sections[0].setGasVelocity(3.0);
    sections[0].setLiquidVelocity(3.0);
    sections[0].updateDerivedQuantities();
    sections[1].setPosition(6.5);
    sections[1].setLength(7.0);
    LagrangianSlugTracker tracker = tracker();
    SlugProbe probe = tracker.addProbe(4.0);
    SlugBubbleUnit following = initialize(tracker, sections, 1.0, 3.0);
    SlugBubbleUnit leading = initialize(tracker, sections, 3.5, 5.0);

    tracker.advanceTimeStep(sections, 0.6);

    assertEquals(1, tracker.getTotalSlugsMerged());
    assertTrue(following.frontPosition > 4.0 && leading.tailPosition > 4.0);
    assertTrue(probe.getEvents().isEmpty(),
        "The following front and leading tail are internal after collision; the probe remains continuously wet");
  }

  @Test
  void birthAndInstantaneousMergeGeometryDoNotCreateCrossings() {
    PipeSection[] sections = sections(0.0);
    LagrangianSlugTracker tracker = tracker();
    SlugProbe probe = tracker.addProbe(4.8);
    initialize(tracker, sections, 2.5, 4.5);
    initialize(tracker, sections, 5.0, 7.0);
    assertTrue(probe.getEvents().isEmpty());

    tracker.advanceTimeStep(sections, 0.1);

    assertEquals(1, tracker.getTotalSlugsMerged());
    assertTrue(probe.getEvents().isEmpty(), "Filling a gap by merging is not an advected interface crossing");
  }

  @Test
  void observationsAndTrialQueriesDoNotChangePhysics() {
    PipeSection[] observedSections = sections(2.0);
    PipeSection[] plainSections = sections(2.0);
    LagrangianSlugTracker observed = tracker();
    LagrangianSlugTracker plain = tracker();
    SlugProbe probe = observed.addProbe(4.0);
    SlugBubbleUnit observedSlug = initialize(observed, observedSections, 1.0, 3.0);
    SlugBubbleUnit plainSlug = initialize(plain, plainSections, 1.0, 3.0);
    observed.getMaximumInterfaceSpeed(observedSections);
    observed.advanceTimeStep(observedSections, Double.NaN);
    assertTrue(probe.getEvents().isEmpty());

    observed.advanceTimeStep(observedSections, 1.0);
    plain.advanceTimeStep(plainSections, 1.0);

    assertFalse(probe.getEvents().isEmpty());
    assertEquals(plainSlug.frontPosition, observedSlug.frontPosition, 0.0);
    assertEquals(plainSlug.tailPosition, observedSlug.tailPosition, 0.0);
    assertEquals(plainSlug.slugLiquidMass, observedSlug.slugLiquidMass, 0.0);
    assertEquals(plain.getMassConservationError(), observed.getMassConservationError(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> probe.getEvents().clear());
    observed.reset();
    assertEquals(1, observed.getProbes().size(), "Reset preserves observation configuration");
    assertTrue(probe.getEvents().isEmpty());
    assertEquals(0, probe.getDroppedEventCount());
  }

  @Test
  void boundedHistoriesAndConfigurationCopiesAreExplicit() {
    SlugProbe probe = new SlugProbe(2.0, 1);
    probe.recordMotion(1, 0.0, 1.0, 1.0, 0.0, 4.0, 3.0);
    assertEquals(1, probe.getEvents().size());
    assertEquals(1, probe.getDroppedEventCount());
    SlugProbe configurationCopy = probe.copyConfiguration();
    assertEquals(probe.getPosition(), configurationCopy.getPosition(), 0.0);
    assertEquals(probe.getCapacity(), configurationCopy.getCapacity());
    assertTrue(configurationCopy.getEvents().isEmpty());
    assertEquals(0, configurationCopy.getDroppedEventCount());
    assertEquals(1, probe.drainEvents().size());
    assertTrue(probe.getEvents().isEmpty());
    assertEquals(1, probe.getDroppedEventCount(), "Draining must not hide an incomplete observation history");
    probe.clearEvents();
    assertEquals(0, probe.getDroppedEventCount());
  }

  @Test
  void serializationPreservesEventsClockAndIndependentContinuation() throws Exception {
    PipeSection[] sections = sections(2.0);
    LagrangianSlugTracker original = tracker();
    original.addProbe(4.0);
    initialize(original, sections, 1.0, 3.0);
    original.advanceTimeStep(sections, 1.0);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    LagrangianSlugTracker restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (LagrangianSlugTracker) input.readObject();
    }

    assertEquals(1, restored.getProbes().get(0).getEvents().size());
    restored.advanceTimeStep(sections, 1.0);

    List<Crossing> events = restored.getProbes().get(0).getEvents();
    assertEquals(2, events.size());
    assertEquals(3.0 / 2.1, events.get(1).getTime(), 1.0e-12);
    assertEquals(1, original.getProbes().get(0).getEvents().size(), "Deserialized histories are independent");
  }

  private static LagrangianSlugTracker tracker() {
    LagrangianSlugTracker tracker = new LagrangianSlugTracker(13L);
    tracker.setConservativeFilmCouplingEnabled(true);
    tracker.setEnableInletSlugGeneration(false);
    tracker.setEnableWakeEffects(false);
    return tracker;
  }

  private static PipeSection[] sections(double velocity) {
    PipeSection[] sections = new PipeSection[2];
    for (int i = 0; i < sections.length; i++) {
      PipeSection section = new PipeSection();
      section.setPosition(2.5 + 5.0 * i);
      section.setLength(5.0);
      section.setDiameter(0.1);
      section.setGasDensity(1000.0);
      section.setLiquidDensity(1000.0);
      section.setGasVelocity(velocity);
      section.setLiquidVelocity(velocity);
      section.setGasHoldup(0.2);
      section.setLiquidHoldup(0.8);
      section.updateDerivedQuantities();
      sections[i] = section;
    }
    return sections;
  }

  private static SlugBubbleUnit initialize(LagrangianSlugTracker tracker, PipeSection[] sections, double tail,
      double front) {
    SlugCharacteristics characteristics = new SlugCharacteristics();
    characteristics.tailPosition = tail;
    characteristics.frontPosition = front;
    characteristics.length = front - tail;
    characteristics.holdup = 0.8;
    characteristics.volume = (front - tail) * sections[0].getArea() * characteristics.holdup;
    return tracker.initializeTerrainSlug(characteristics, sections);
  }
}
