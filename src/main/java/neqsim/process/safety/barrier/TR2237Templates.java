package neqsim.process.safety.barrier;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.safety.barrier.PerformanceStandard.DemandMode;
import neqsim.process.safety.barrier.SafetyBarrier.BarrierStatus;
import neqsim.process.safety.barrier.SafetyBarrier.BarrierType;

/**
 * Factory for TR2237-style performance-standard templates.
 *
 * <p>
 * The templates provide a starting register for agent-assisted standards reviews. They deliberately
 * contain acceptance criteria and barrier links, but no project evidence; agents that read technical
 * documentation should attach {@link DocumentEvidence} items before crediting the barriers.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class TR2237Templates {

  /**
   * Utility class constructor.
   */
  private TR2237Templates() {}

  /**
   * Creates an onshore process-safety barrier register template.
   *
   * @return barrier register with TR2237-style performance standards and starter barriers
   */
  public static BarrierRegister createOnshoreTemplate() {
    BarrierRegister register = new BarrierRegister("TR2237-ONSHORE")
        .setName("TR2237 onshore performance standard template");

    addStandardWithBarrier(register, "PS-01", "Process containment",
        "Maintain containment of hazardous process inventory during normal and abnormal operation.",
        DemandMode.CONTINUOUS, Double.NaN, 0.995, Double.NaN, Double.NaN,
        "Pressure boundary design, inspection, isolation, and mechanical integrity evidence are traceable.",
        BarrierType.PREVENTION);
    addStandardWithBarrier(register, "PS-02", "Pressure protection",
        "Prevent unacceptable overpressure by relief, HIPPS, or depressurization functions.",
        DemandMode.LOW_DEMAND, 0.01, Double.NaN, 8760.0, 30.0,
        "Scenario relief loads and SIL/LOPA targets are documented and verified.",
        BarrierType.PREVENTION);
    addStandardWithBarrier(register, "PS-03", "Fire and gas detection",
        "Detect fire or gas release early enough to initiate required executive actions.",
        DemandMode.CONTINUOUS, Double.NaN, 0.99, Double.NaN, 10.0,
        "Detector coverage and voting logic meet the project fire and gas philosophy.",
        BarrierType.BOTH);
    addStandardWithBarrier(register, "PS-04", "Emergency shutdown",
        "Isolate hazardous inventories and place equipment in a safe state on demand.",
        DemandMode.LOW_DEMAND, 0.01, Double.NaN, 8760.0, 30.0,
        "Shutdown valves, logic solver, and final elements meet assigned SIL targets.",
        BarrierType.MITIGATION);
    addStandardWithBarrier(register, "PS-05", "Emergency depressurization",
        "Reduce pressure and inventory before escalation or rupture in fire scenarios.",
        DemandMode.LOW_DEMAND, 0.01, Double.NaN, 8760.0, 900.0,
        "Depressurization calculations demonstrate acceptable pressure, inventory, and fire rate.",
        BarrierType.MITIGATION);
    addStandardWithBarrier(register, "PS-06", "Ignition source control",
        "Prevent ignition of flammable releases by equipment selection and operational controls.",
        DemandMode.CONTINUOUS, Double.NaN, 0.99, Double.NaN, Double.NaN,
        "Area classification and ignition control evidence are available for each hazardous area.",
        BarrierType.PREVENTION);
    addStandardWithBarrier(register, "PS-07", "Active fire protection",
        "Control or suppress fires to prevent unacceptable escalation.", DemandMode.LOW_DEMAND,
        0.10, Double.NaN, 4380.0, 120.0,
        "Firewater, deluge, monitors, and foam systems meet demand, coverage, and duration needs.",
        BarrierType.MITIGATION);
    addStandardWithBarrier(register, "PS-08", "Passive fire protection",
        "Maintain structural and equipment integrity for the required fire endurance period.",
        DemandMode.CONTINUOUS, Double.NaN, 0.995, Double.NaN, Double.NaN,
        "PFP scope, rating, substrate, and inspection records are traceable to fire scenarios.",
        BarrierType.MITIGATION);
    addStandardWithBarrier(register, "PS-09", "Emergency power",
        "Supply power to safety-critical loads for the required endurance time.",
        DemandMode.LOW_DEMAND, 0.05, Double.NaN, 4380.0, 15.0,
        "Emergency power capacity, start reliability, autonomy, and load list are verified.",
        BarrierType.MITIGATION);
    addStandardWithBarrier(register, "PS-10", "Escape and evacuation",
        "Provide safe escape, evacuation, and rescue functions during major accident events.",
        DemandMode.CONTINUOUS, Double.NaN, 0.99, Double.NaN, Double.NaN,
        "Escape routes, muster, evacuation means, and drills are documented and maintained.",
        BarrierType.MITIGATION);

    return register;
  }

  /**
   * Creates a starter mapping between template performance standards and NORSOK S-001 topics.
   *
   * @return ordered mapping from performance-standard id to relevant NORSOK S-001 topic
   */
  public static Map<String, String> createNorsokS001Mapping() {
    Map<String, String> mapping = new LinkedHashMap<String, String>();
    mapping.put("PS-01", "NORSOK S-001: containment, layout, and area classification basis");
    mapping.put("PS-02", "NORSOK S-001: pressure protection and process safety design");
    mapping.put("PS-03", "NORSOK S-001: fire and gas detection");
    mapping.put("PS-04", "NORSOK S-001: emergency shutdown and isolation");
    mapping.put("PS-05", "NORSOK S-001: depressurization and blowdown");
    mapping.put("PS-06", "NORSOK S-001: ignition control and hazardous area design");
    mapping.put("PS-07", "NORSOK S-001: active fire protection");
    mapping.put("PS-08", "NORSOK S-001: passive fire protection");
    mapping.put("PS-09", "NORSOK S-001: emergency power and essential safety systems");
    mapping.put("PS-10", "NORSOK S-001: escape, evacuation, and rescue");
    return mapping;
  }

  /**
   * Adds one performance standard and a linked starter barrier to a register.
   *
   * @param register register receiving the standard and barrier
   * @param id performance standard identifier
   * @param title performance standard title
   * @param safetyFunction safety function description
   * @param demandMode demand mode
   * @param targetPfd target PFD, or NaN when not applicable
   * @param availability required availability, or NaN when not applicable
   * @param proofTestIntervalHours proof test interval in hours, or NaN when not applicable
   * @param responseTimeSeconds response time in seconds, or NaN when not applicable
   * @param acceptanceCriterion acceptance criterion text
   * @param barrierType barrier type
   */
  private static void addStandardWithBarrier(BarrierRegister register, String id, String title,
      String safetyFunction, DemandMode demandMode, double targetPfd, double availability,
      double proofTestIntervalHours, double responseTimeSeconds, String acceptanceCriterion,
      BarrierType barrierType) {
    PerformanceStandard standard = new PerformanceStandard(id).setTitle(title)
        .setSafetyFunction(safetyFunction).setDemandMode(demandMode).setTargetPfd(targetPfd)
        .setRequiredAvailability(availability).setProofTestIntervalHours(proofTestIntervalHours)
        .setResponseTimeSeconds(responseTimeSeconds).addAcceptanceCriterion(acceptanceCriterion);
    SafetyBarrier barrier = new SafetyBarrier(id + "-BARRIER").setName(title)
        .setDescription(safetyFunction).setType(barrierType).setStatus(BarrierStatus.AVAILABLE)
        .setPerformanceStandard(standard).addEquipmentTag("TBD");
    register.addPerformanceStandard(standard).addBarrier(barrier);
  }
}