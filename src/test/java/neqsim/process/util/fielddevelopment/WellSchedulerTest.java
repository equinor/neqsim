package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
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
    scheduler = new WellScheduler("TestScheduler");
  }

  @Test
  @DisplayName("Add well creates record with PENDING status")
  void testAddWell() {
    String wellName = "Well-A1";
    LocalDate drillingDate = LocalDate.of(2024, 6, 1);
    double initialRate = 500.0;

    scheduler.addWell(wellName, drillingDate, initialRate);

    WellRecord record = scheduler.getWellRecord(wellName);
    assertNotNull(record, "Well record should exist");
    assertEquals(wellName, record.getWellName(), "Well name mismatch");
    assertEquals(WellStatus.PENDING, record.getStatus(), "New well should be PENDING");
  }

  @Test
  @DisplayName("Schedule intervention sets correct dates")
  void testScheduleIntervention() {
    String wellName = "Well-B2";
    scheduler.addWell(wellName, LocalDate.of(2024, 1, 1), 400.0);

    LocalDate interventionDate = LocalDate.of(2024, 9, 15);
    int durationDays = 14;

    Intervention intervention = scheduler.scheduleIntervention(wellName,
        InterventionType.WORKOVER_RIG, interventionDate, durationDays, "Pump replacement");

    assertNotNull(intervention, "Intervention should be created");
    assertEquals(wellName, intervention.getWellName(), "Well name mismatch");
    assertEquals(InterventionType.WORKOVER_RIG, intervention.getType(),
        "Intervention type mismatch");
    assertEquals(interventionDate, intervention.getStartDate(), "Start date mismatch");
    assertEquals(interventionDate.plusDays(durationDays), intervention.getEndDate(),
        "End date mismatch");
  }

  @Test
  @DisplayName("Multiple interventions are tracked per well")
  void testMultipleInterventions() {
    String wellName = "Well-C3";
    scheduler.addWell(wellName, LocalDate.of(2023, 1, 1), 350.0);

    scheduler.scheduleIntervention(wellName, InterventionType.COILED_TUBING,
        LocalDate.of(2024, 3, 1), 5, "Scale treatment");
    scheduler.scheduleIntervention(wellName, InterventionType.WIRELINE, LocalDate.of(2024, 6, 1), 3,
        "Log run");
    scheduler.scheduleIntervention(wellName, InterventionType.WORKOVER_RIG,
        LocalDate.of(2024, 9, 1), 21, "ESP replacement");

    List<Intervention> interventions = scheduler.getInterventions(wellName);
    assertEquals(3, interventions.size(), "Should have 3 scheduled interventions");
  }

  @Test
  @DisplayName("Interventions are prioritized by NPV")
  void testInterventionPrioritization() {
    String well1 = "Well-High";
    String well2 = "Well-Low";

    scheduler.addWell(well1, LocalDate.of(2023, 1, 1), 600.0);
    scheduler.addWell(well2, LocalDate.of(2023, 1, 1), 200.0);

    Intervention int1 = scheduler.scheduleIntervention(well1, InterventionType.WORKOVER_RIG,
        LocalDate.of(2024, 6, 1), 14, "High NPV job");
    int1.setEstimatedNpv(5_000_000.0);

    Intervention int2 = scheduler.scheduleIntervention(well2, InterventionType.COILED_TUBING,
        LocalDate.of(2024, 6, 1), 7, "Low NPV job");
    int2.setEstimatedNpv(500_000.0);

    List<Intervention> prioritized = scheduler.getPrioritizedInterventions();

    assertTrue(prioritized.get(0).getEstimatedNpv() >= prioritized.get(1).getEstimatedNpv(),
        "Higher NPV intervention should be first");
  }

  @Test
  @DisplayName("Well status transitions correctly")
  void testWellStatusTransitions() {
    String wellName = "Well-D4";
    scheduler.addWell(wellName, LocalDate.of(2024, 1, 1), 450.0);

    // Start production
    scheduler.updateWellStatus(wellName, WellStatus.PRODUCING);
    assertEquals(WellStatus.PRODUCING, scheduler.getWellRecord(wellName).getStatus());

    // Shut in for intervention
    scheduler.updateWellStatus(wellName, WellStatus.SHUT_IN);
    assertEquals(WellStatus.SHUT_IN, scheduler.getWellRecord(wellName).getStatus());

    // Perform workover
    scheduler.updateWellStatus(wellName, WellStatus.WORKOVER);
    assertEquals(WellStatus.WORKOVER, scheduler.getWellRecord(wellName).getStatus());

    // Return to production
    scheduler.updateWellStatus(wellName, WellStatus.PRODUCING);
    assertEquals(WellStatus.PRODUCING, scheduler.getWellRecord(wellName).getStatus());
  }

  @Test
  @DisplayName("Calculate well availability")
  void testWellAvailability() {
    String wellName = "Well-E5";
    scheduler.addWell(wellName, LocalDate.of(2024, 1, 1), 300.0);
    scheduler.updateWellStatus(wellName, WellStatus.PRODUCING);

    // Schedule 30 days of interventions in a year
    scheduler.scheduleIntervention(wellName, InterventionType.WORKOVER_RIG,
        LocalDate.of(2024, 4, 1), 15, "Spring workover");
    scheduler.scheduleIntervention(wellName, InterventionType.COILED_TUBING,
        LocalDate.of(2024, 10, 1), 15, "Fall cleanup");

    LocalDate startDate = LocalDate.of(2024, 1, 1);
    LocalDate endDate = LocalDate.of(2024, 12, 31);

    double availability = scheduler.calculateAvailability(wellName, startDate, endDate);

    // 365 days - 30 intervention days = 335 producing days = 91.8% availability
    double expectedAvailability = (365.0 - 30.0) / 365.0;
    assertEquals(expectedAvailability, availability, 0.01, "Availability calculation mismatch");
  }

  @Test
  @DisplayName("Generate schedule for date range")
  void testGenerateSchedule() {
    scheduler.addWell("Well-F1", LocalDate.of(2024, 1, 15), 400.0);
    scheduler.addWell("Well-F2", LocalDate.of(2024, 3, 1), 350.0);
    scheduler.addWell("Well-F3", LocalDate.of(2024, 5, 15), 300.0);

    scheduler.scheduleIntervention("Well-F1", InterventionType.COILED_TUBING,
        LocalDate.of(2024, 7, 1), 5, "Stimulation");
    scheduler.scheduleIntervention("Well-F2", InterventionType.WIRELINE, LocalDate.of(2024, 8, 1),
        3, "Perforation");

    LocalDate startDate = LocalDate.of(2024, 1, 1);
    LocalDate endDate = LocalDate.of(2024, 12, 31);

    ScheduleResult result = scheduler.generateSchedule(startDate, endDate);

    assertNotNull(result, "Schedule result should not be null");
    assertEquals(3, result.getWellCount(), "Should have 3 wells");
    assertEquals(2, result.getTotalInterventions(), "Should have 2 interventions");
  }

  @Test
  @DisplayName("Conflict detection for overlapping interventions")
  void testInterventionConflictDetection() {
    String wellName = "Well-G1";
    scheduler.addWell(wellName, LocalDate.of(2023, 1, 1), 500.0);

    // First intervention from June 1-15
    scheduler.scheduleIntervention(wellName, InterventionType.WORKOVER_RIG,
        LocalDate.of(2024, 6, 1), 14, "Primary workover");

    // Try to schedule overlapping intervention June 10-17
    boolean hasConflict = scheduler.hasSchedulingConflict(wellName, LocalDate.of(2024, 6, 10), 7);

    assertTrue(hasConflict, "Should detect scheduling conflict");
  }

  @Test
  @DisplayName("No conflict for non-overlapping interventions")
  void testNoConflictForSeparateInterventions() {
    String wellName = "Well-H1";
    scheduler.addWell(wellName, LocalDate.of(2023, 1, 1), 500.0);

    // First intervention from June 1-15
    scheduler.scheduleIntervention(wellName, InterventionType.WORKOVER_RIG,
        LocalDate.of(2024, 6, 1), 14, "Primary workover");

    // Check for July 1 - should be no conflict
    boolean hasConflict = scheduler.hasSchedulingConflict(wellName, LocalDate.of(2024, 7, 1), 7);

    assertFalse(hasConflict, "Should not detect conflict for separate dates");
  }

  @Test
  @DisplayName("Get all wells returns correct list")
  void testGetAllWells() {
    scheduler.addWell("Alpha", LocalDate.of(2024, 1, 1), 100.0);
    scheduler.addWell("Beta", LocalDate.of(2024, 2, 1), 200.0);
    scheduler.addWell("Gamma", LocalDate.of(2024, 3, 1), 300.0);

    List<WellRecord> wells = scheduler.getAllWells();

    assertEquals(3, wells.size(), "Should have 3 wells");
  }

  @Test
  @DisplayName("Intervention cost calculation")
  void testInterventionCost() {
    String wellName = "Well-I1";
    scheduler.addWell(wellName, LocalDate.of(2023, 1, 1), 400.0);

    Intervention intervention = scheduler.scheduleIntervention(wellName,
        InterventionType.WORKOVER_RIG, LocalDate.of(2024, 8, 1), 21, "Major overhaul");

    double dailyRate = 150_000.0; // $150k/day
    intervention.setDailyCost(dailyRate);

    double totalCost = intervention.getTotalCost();
    double expectedCost = dailyRate * 21;

    assertEquals(expectedCost, totalCost, 0.01, "Total cost calculation mismatch");
  }

  @Test
  @DisplayName("Gantt chart export contains required data")
  void testGanttChartExport() {
    scheduler.addWell("Well-J1", LocalDate.of(2024, 2, 1), 350.0);
    scheduler.scheduleIntervention("Well-J1", InterventionType.COILED_TUBING,
        LocalDate.of(2024, 5, 1), 7, "Spring service");

    ScheduleResult result =
        scheduler.generateSchedule(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

    String ganttData = result.toGanttFormat();

    assertNotNull(ganttData, "Gantt data should not be null");
    assertTrue(ganttData.contains("Well-J1"), "Gantt data should contain well name");
    assertTrue(ganttData.contains("COILED_TUBING") || ganttData.contains("Coiled"),
        "Gantt data should contain intervention type");
  }

  @Test
  @DisplayName("Well decommissioning changes status")
  void testWellDecommissioning() {
    String wellName = "Well-K1";
    scheduler.addWell(wellName, LocalDate.of(2020, 1, 1), 200.0);
    scheduler.updateWellStatus(wellName, WellStatus.PRODUCING);

    // Decommission the well
    scheduler.updateWellStatus(wellName, WellStatus.ABANDONED);

    WellRecord record = scheduler.getWellRecord(wellName);
    assertEquals(WellStatus.ABANDONED, record.getStatus(), "Well should be abandoned");
  }
}
