package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.util.fielddevelopment.WellScheduler.Intervention;
import neqsim.process.util.fielddevelopment.WellScheduler.InterventionType;
import neqsim.process.util.fielddevelopment.WellScheduler.ScheduleResult;
import neqsim.process.util.fielddevelopment.WellScheduler.WellRecord;
import neqsim.process.util.fielddevelopment.WellScheduler.WellStatus;

/**
 * Unit tests for {@link WellScheduler}.
 *
 * <p>
 * Tests well intervention scheduling, workover planning, and availability tracking.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class WellSchedulerTest {

  private WellScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new WellScheduler();
  }

  @Test
  @DisplayName("WellScheduler constructs with no arguments")
  void testConstruction() {
    assertNotNull(scheduler);
  }

  @Test
  @DisplayName("Add well creates record")
  void testAddWell() {
    String wellName = "Well-A1";
    double initialPotential = 500.0;
    String rateUnit = "Sm3/day";

    WellRecord record = scheduler.addWell(wellName, initialPotential, rateUnit);

    assertNotNull(record, "Well record should be created");
    assertEquals(wellName, record.getWellName(), "Well name mismatch");
    assertEquals(initialPotential, record.getCurrentPotential(), 0.01);
    assertEquals(rateUnit, record.getRateUnit());
  }

  @Test
  @DisplayName("Get well returns existing well")
  void testGetWell() {
    scheduler.addWell("TestWell", 1000.0, "bbl/day");

    WellRecord retrieved = scheduler.getWell("TestWell");

    assertNotNull(retrieved);
    assertEquals("TestWell", retrieved.getWellName());
  }

  @Test
  @DisplayName("Get well returns null for non-existing well")
  void testGetNonExistingWell() {
    WellRecord retrieved = scheduler.getWell("NonExistent");
    assertNull(retrieved);
  }

  @Test
  @DisplayName("Schedule intervention adds to well record")
  void testScheduleIntervention() {
    scheduler.addWell("Well-B2", 800.0, "Sm3/day");

    LocalDate startDate = LocalDate.of(2024, 9, 15);
    int durationDays = 14;

    Intervention intervention = Intervention.builder("Well-B2").type(InterventionType.WORKOVER_RIG)
        .startDate(startDate).durationDays(durationDays).description("Pump replacement").build();

    scheduler.scheduleIntervention(intervention);

    WellRecord record = scheduler.getWell("Well-B2");
    List<Intervention> interventions = record.getScheduledInterventions();

    assertEquals(1, interventions.size());
    assertEquals(InterventionType.WORKOVER_RIG, interventions.get(0).getType());
    assertEquals(startDate, interventions.get(0).getStartDate());
  }

  @Test
  @DisplayName("Intervention end date is calculated correctly")
  void testInterventionEndDate() {
    LocalDate startDate = LocalDate.of(2024, 6, 1);
    int duration = 10;

    Intervention intervention = Intervention.builder("Well").type(InterventionType.COILED_TUBING)
        .startDate(startDate).durationDays(duration).build();

    // End date should be start + duration - 1
    LocalDate expectedEnd = startDate.plusDays(duration - 1);
    assertEquals(expectedEnd, intervention.getEndDate());
  }

  @Test
  @DisplayName("Multiple interventions per well are tracked")
  void testMultipleInterventions() {
    scheduler.addWell("Well-C3", 350.0, "Sm3/day");

    scheduler.scheduleIntervention(Intervention.builder("Well-C3")
        .type(InterventionType.COILED_TUBING).startDate(LocalDate.of(2024, 3, 1)).durationDays(5)
        .description("Scale treatment").build());

    scheduler.scheduleIntervention(Intervention.builder("Well-C3").type(InterventionType.WIRELINE)
        .startDate(LocalDate.of(2024, 6, 1)).durationDays(3).description("Log run").build());

    scheduler.scheduleIntervention(Intervention.builder("Well-C3")
        .type(InterventionType.WORKOVER_RIG).startDate(LocalDate.of(2024, 9, 1)).durationDays(21)
        .description("ESP replacement").build());

    List<Intervention> interventions = scheduler.getWell("Well-C3").getScheduledInterventions();
    assertEquals(3, interventions.size(), "Should have 3 scheduled interventions");
  }

  @Test
  @DisplayName("Get all interventions returns sorted list")
  void testGetAllInterventions() {
    scheduler.addWell("Well-A", 500.0, "Sm3/day");
    scheduler.addWell("Well-B", 400.0, "Sm3/day");

    // Schedule out of order
    scheduler.scheduleIntervention(Intervention.builder("Well-A").type(InterventionType.WIRELINE)
        .startDate(LocalDate.of(2024, 9, 1)).durationDays(3).build());

    scheduler
        .scheduleIntervention(Intervention.builder("Well-B").type(InterventionType.COILED_TUBING)
            .startDate(LocalDate.of(2024, 3, 1)).durationDays(5).build());

    List<Intervention> all = scheduler.getAllInterventions();

    assertEquals(2, all.size());
    // Should be sorted by date
    assertTrue(all.get(0).getStartDate().isBefore(all.get(1).getStartDate()),
        "Interventions should be sorted by date");
  }

  @Test
  @DisplayName("WellRecord status changes are tracked")
  void testWellStatusChanges() {
    scheduler.addWell("Well-D", 600.0, "bbl/day");
    WellRecord record = scheduler.getWell("Well-D");

    assertEquals(WellStatus.PRODUCING, record.getCurrentStatus());

    record.setStatus(WellStatus.SHUT_IN, LocalDate.of(2024, 5, 1));
    assertEquals(WellStatus.SHUT_IN, record.getCurrentStatus());

    record.setStatus(WellStatus.PRODUCING, LocalDate.of(2024, 5, 15));
    assertEquals(WellStatus.PRODUCING, record.getCurrentStatus());
  }

  @Test
  @DisplayName("Calculate well availability")
  void testWellAvailability() {
    scheduler.addWell("Well-E", 300.0, "Sm3/day");
    WellRecord record = scheduler.getWell("Well-E");

    // Schedule 30 days of interventions
    record.addIntervention(Intervention.builder("Well-E").type(InterventionType.WORKOVER_RIG)
        .startDate(LocalDate.of(2024, 4, 1)).durationDays(15).build());
    record.addIntervention(Intervention.builder("Well-E").type(InterventionType.COILED_TUBING)
        .startDate(LocalDate.of(2024, 10, 1)).durationDays(15).build());

    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2024, 12, 31);

    double availability = record.calculateAvailability(start, end);

    // 366 days (2024 is leap year) - 30 intervention days = 336 producing days = 91.8%
    double expectedAvailability = (366.0 - 30.0) / 366.0;
    assertEquals(expectedAvailability, availability, 0.02, "Availability calculation mismatch");
  }

  @Test
  @DisplayName("Get all wells returns collection")
  void testGetAllWells() {
    scheduler.addWell("Alpha", 100.0, "Sm3/day");
    scheduler.addWell("Beta", 200.0, "Sm3/day");
    scheduler.addWell("Gamma", 300.0, "Sm3/day");

    Collection<WellRecord> wells = scheduler.getAllWells();

    assertEquals(3, wells.size(), "Should have 3 wells");
  }

  @Test
  @DisplayName("Intervention builder creates valid intervention")
  void testInterventionBuilder() {
    Intervention intervention = Intervention.builder("TestWell").type(InterventionType.WORKOVER_RIG)
        .startDate(LocalDate.of(2024, 8, 1)).durationDays(21).expectedGain(0.15)
        .cost(500000.0, "USD").description("Major overhaul").priority(1).build();

    assertEquals("TestWell", intervention.getWellName());
    assertEquals(InterventionType.WORKOVER_RIG, intervention.getType());
    assertEquals(21, intervention.getDurationDays());
    assertEquals(0.15, intervention.getExpectedProductionGain(), 0.001);
    assertEquals(500000.0, intervention.getCost(), 0.01);
    assertEquals("USD", intervention.getCurrency());
    assertEquals("Major overhaul", intervention.getDescription());
    assertEquals(1, intervention.getPriority());
  }

  @Test
  @DisplayName("Intervention cost and currency tracking")
  void testInterventionCost() {
    Intervention intervention = Intervention.builder("Well").type(InterventionType.DRILLING_RIG)
        .startDate(LocalDate.of(2024, 6, 1)).durationDays(45).cost(10_000_000.0, "NOK").build();

    assertEquals(10_000_000.0, intervention.getCost(), 0.01);
    assertEquals("NOK", intervention.getCurrency());
  }

  @Test
  @DisplayName("InterventionType enum values exist")
  void testInterventionTypeEnum() {
    assertNotNull(InterventionType.COILED_TUBING);
    assertNotNull(InterventionType.WIRELINE);
    assertNotNull(InterventionType.SLICKLINE);
    assertNotNull(InterventionType.WORKOVER_RIG);
    assertNotNull(InterventionType.DRILLING_RIG);
    assertNotNull(InterventionType.SUBSEA_INTERVENTION);
  }

  @Test
  @DisplayName("WellStatus enum values exist")
  void testWellStatusEnum() {
    assertNotNull(WellStatus.PRODUCING);
    assertNotNull(WellStatus.SHUT_IN);
    assertNotNull(WellStatus.WORKOVER);
    assertNotNull(WellStatus.SUSPENDED);
    assertNotNull(WellStatus.ABANDONED);
  }

  @Test
  @DisplayName("Optimize schedule produces result")
  void testOptimizeSchedule() {
    scheduler.addWell("Well-1", 500.0, "Sm3/day");
    scheduler.addWell("Well-2", 400.0, "Sm3/day");

    scheduler.scheduleIntervention(Intervention.builder("Well-1")
        .type(InterventionType.WORKOVER_RIG).startDate(LocalDate.of(2024, 6, 1)).durationDays(14)
        .priority(1).expectedGain(0.10).build());

    scheduler.scheduleIntervention(Intervention.builder("Well-2")
        .type(InterventionType.COILED_TUBING).startDate(LocalDate.of(2024, 6, 15)).durationDays(5)
        .priority(2).expectedGain(0.05).build());

    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2024, 12, 31);

    ScheduleResult result = scheduler.optimizeSchedule(start, end, 1);

    assertNotNull(result, "Schedule result should not be null");
  }

  @Test
  @DisplayName("WellRecord potential can be updated")
  void testUpdatePotential() {
    WellRecord record = scheduler.addWell("Well-F", 1000.0, "bbl/day");

    assertEquals(1000.0, record.getCurrentPotential(), 0.01);
    assertEquals(1000.0, record.getOriginalPotential(), 0.01);

    record.setCurrentPotential(800.0);

    assertEquals(800.0, record.getCurrentPotential(), 0.01);
    assertEquals(1000.0, record.getOriginalPotential(), 0.01);
  }

  @Test
  @DisplayName("Intervention isActiveOn works correctly")
  void testInterventionIsActiveOn() {
    Intervention intervention = Intervention.builder("Well").type(InterventionType.WIRELINE)
        .startDate(LocalDate.of(2024, 6, 10)).durationDays(5) // June 10-14
        .build();

    assertFalse(intervention.isActiveOn(LocalDate.of(2024, 6, 9)),
        "Day before should not be active");
    assertTrue(intervention.isActiveOn(LocalDate.of(2024, 6, 10)), "Start day should be active");
    assertTrue(intervention.isActiveOn(LocalDate.of(2024, 6, 12)), "Middle day should be active");
    assertTrue(intervention.isActiveOn(LocalDate.of(2024, 6, 14)), "End day should be active");
    assertFalse(intervention.isActiveOn(LocalDate.of(2024, 6, 15)),
        "Day after should not be active");
  }

  @Test
  @DisplayName("Well status during intervention is WORKOVER")
  void testStatusDuringIntervention() {
    scheduler.addWell("Well-G", 500.0, "Sm3/day");
    WellRecord record = scheduler.getWell("Well-G");

    record.addIntervention(Intervention.builder("Well-G").type(InterventionType.WORKOVER_RIG)
        .startDate(LocalDate.of(2024, 7, 1)).durationDays(10).build());

    // Before intervention - producing
    assertEquals(WellStatus.PRODUCING, record.getStatusOn(LocalDate.of(2024, 6, 30)));

    // During intervention - workover
    assertEquals(WellStatus.WORKOVER, record.getStatusOn(LocalDate.of(2024, 7, 5)));

    // After intervention - producing
    assertEquals(WellStatus.PRODUCING, record.getStatusOn(LocalDate.of(2024, 7, 15)));
  }

  @Test
  @DisplayName("Null well name throws exception")
  void testNullWellNameThrowsException() {
    assertThrows(NullPointerException.class, () -> {
      scheduler.addWell(null, 100.0, "Sm3/day");
    });
  }

  @Test
  @DisplayName("Negative potential throws exception")
  void testNegativePotentialThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> {
      scheduler.addWell("Well", -100.0, "Sm3/day");
    });
  }

  @Test
  @DisplayName("Schedule intervention for non-existing well throws exception")
  void testScheduleInterventionNonExistingWell() {
    Intervention intervention = Intervention.builder("NonExistent").type(InterventionType.WIRELINE)
        .startDate(LocalDate.of(2024, 6, 1)).durationDays(3).build();

    assertThrows(IllegalArgumentException.class, () -> {
      scheduler.scheduleIntervention(intervention);
    });
  }
}
