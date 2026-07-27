package neqsim.process.mechanicaldesign.designstandards;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardRequirementCapability.Kind;

/** Registry connecting cross-equipment standards to existing NeqSim capabilities. */
public final class StandardRequirementPackRegistry {
  /** Explicit lookup that distinguishes a missing pack from an empty one. */
  public static final class Lookup {
    private final StandardType standardType;
    private final StandardRequirementPack pack;

    private Lookup(StandardType standardType, StandardRequirementPack pack) {
      this.standardType = standardType;
      this.pack = pack;
    }

    /** @return requested standard */
    public StandardType getStandardType() {
      return standardType;
    }

    /** @return whether a capability mapping is registered */
    public boolean isImplemented() {
      return pack != null;
    }

    /**
     * Require the registered mapping.
     *
     * @return registered requirement pack
     * @throws IllegalStateException if the standard has no pack
     */
    public StandardRequirementPack requirePack() {
      if (pack == null) {
        throw new IllegalStateException("No requirement pack is implemented for " + standardType.getCode());
      }
      return pack;
    }
  }

  private static final Map<StandardType, StandardRequirementPack> PACKS;

  static {
    Map<StandardType, StandardRequirementPack> packs = new EnumMap<StandardType, StandardRequirementPack>(
        StandardType.class);
    register(packs,
        pack(StandardType.NORSOK_P_002,
            capability("line-sizing", Kind.CALCULATION_SCREENING,
                "neqsim.process.mechanicaldesign.pipeline.NorsokP002LineSizingValidator",
                "Velocity, pressure-gradient, and erosional screening; project criteria remain explicit inputs."),
            capability("process-limits", Kind.CALCULATION_SCREENING,
                "neqsim.process.safety.compliance.NorsokP002ComplianceChecker",
                "Point checks only; it does not establish completeness of the P-002 requirement set."),
            capability("process-review", Kind.REVIEW_WORKFLOW, "neqsim.process.safety.compliance.StandardsDesignReview",
                "Orchestrates supported model checks and reports unsupported requirements for review.")));
    register(packs,
        pack(StandardType.NORSOK_S_001,
            capability("technical-safety-review", Kind.REVIEW_WORKFLOW,
                "neqsim.process.safety.processsafetysystem.ProcessSafetySystemReviewEngine",
                "Structured technical-safety evidence review; engineering approval remains external."),
            capability("secondary-pressure-protection", Kind.CALCULATION_SCREENING,
                "neqsim.process.safety.processsafetysystem.S001SecondaryPressureProtectionCriteria",
                "Secondary pressure-protection criteria only; it is not a full S-001 conformity assessment.")));
    register(packs,
        pack(StandardType.ISO_10418, capability("offshore-process-safety-review", Kind.REVIEW_WORKFLOW,
            "neqsim.process.safety.processsafetysystem.ProcessSafetySystemReviewEngine",
            "Review workflow and calculated evidence; complete safe charts and independent verification are required."),
            capability("safe-chart", Kind.REVIEW_WORKFLOW, "neqsim.process.safety.api14c.Api14cSafeChartBuilder",
                "API 14C-style safe-chart support used as evidence, not an ISO 10418 certificate.")));
    register(packs,
        pack(StandardType.IEC_61511, capability(
            "hazop-lopa-srs", Kind.REVIEW_WORKFLOW, "neqsim.process.engineering.safety.lifecycle.HazopLopaSrsWorkflow",
            "Produces controlled evidence and an unapproved draft SRS; lifecycle independence remains external."),
            capability("sif-reliability", Kind.CALCULATION_SCREENING,
                "neqsim.process.engineering.safety.SafetyFunctionReliabilityStudy",
                "Quantitative reliability study only; systematic capability and functional-safety management are excluded.")));
    register(packs, pack(StandardType.API_520_PART_1,
        capability("relief-sizing", Kind.CALCULATION_SCREENING, "neqsim.process.util.fire.ReliefValveSizing",
            "Gas, liquid, and two-phase sizing equations; scenario completeness and valve certification are excluded."),
        capability("discrete-orifice-selection", Kind.CALCULATION_SCREENING,
            "neqsim.process.engineering.design.modules.ReliefDeviceDesignModule",
            "Discrete area selection for project design loops; installation and vendor checks are excluded.")));
    register(packs, pack(StandardType.IEC_60534, capability("control-valve-sizing", Kind.CALCULATION_SCREENING,
        "neqsim.process.mechanicaldesign.valve.ControlValveSizing_IEC_60534_full",
        "Configured liquid, gas, and noise calculations; edition-part applicability and vendor selection require review."),
        capability("design-loop-selection", Kind.CALCULATION_SCREENING,
            "neqsim.process.engineering.design.modules.ControlValveDesignModule",
            "Discrete Cv selection using the configured sizing method.")));
    register(packs,
        pack(StandardType.NORSOK_M_001,
            capability("material-selection", Kind.CALCULATION_SCREENING,
                "neqsim.process.corrosion.NorsokM001MaterialSelection",
                "Materials screening only; material data sheets, qualification, and project deviations are excluded."),
            capability("co2-corrosion", Kind.CALCULATION_SCREENING, "neqsim.process.corrosion.NorsokM506CorrosionRate",
                "Corrosion-rate calculation supporting selection; it does not replace M-001 conformity review.")));
    register(packs,
        pack(StandardType.API_650, capability("tank-mechanical-design", Kind.CALCULATION_SCREENING,
            "neqsim.process.mechanicaldesign.tank.TankMechanicalDesign",
            "Preliminary shell-course, bottom, and roof sizing; fabrication and complete code checks are excluded.")));
    register(packs,
        pack(StandardType.API_660,
            capability("shell-and-tube-design", Kind.CALCULATION_SCREENING,
                "neqsim.process.mechanicaldesign.heatexchanger.ShellAndTubeDesignCalculator",
                "Thermal and mechanical screening; full datasheet and vendor conformity are excluded.")));
    register(packs, pack(StandardType.DNV_ST_F101, capability("pipeline-mechanical-design", Kind.CALCULATION_SCREENING,
        "neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator",
        "Pressure-containment screening; load cases, safety classes, fabrication, and installation checks are incomplete.")));
    PACKS = Collections.unmodifiableMap(packs);
  }

  private StandardRequirementPackRegistry() {
    // Utility class.
  }

  /**
   * Look up a cross-equipment requirement pack.
   *
   * @param standardType standard to inspect
   * @return explicit implemented or missing lookup
   */
  public static Lookup lookup(StandardType standardType) {
    if (standardType == null) {
      throw new IllegalArgumentException("standardType cannot be null");
    }
    return new Lookup(standardType, PACKS.get(standardType));
  }

  private static StandardRequirementPack pack(StandardType standardType,
      StandardRequirementCapability... capabilities) {
    return new StandardRequirementPack(standardType, "1.0.0", Arrays.asList(capabilities));
  }

  private static StandardRequirementCapability capability(String id, Kind kind, String implementation,
      String boundary) {
    return new StandardRequirementCapability(id, kind, implementation, boundary);
  }

  private static void register(Map<StandardType, StandardRequirementPack> packs, StandardRequirementPack pack) {
    StandardType standardType = pack.getEdition().getStandardType();
    if (packs.put(standardType, pack) != null) {
      throw new IllegalStateException("Duplicate requirement pack for " + standardType.getCode());
    }
  }
}
