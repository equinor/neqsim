package neqsim.process.engineering.design.modules;

import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.design.EngineeringDesignModule;
import neqsim.process.engineering.design.EngineeringDesignModuleResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.design.EngineeringDesignUpdate;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.processmodel.ProcessSystem;

/** Produces a preliminary, review-required materials and corrosion design basis. */
public final class MaterialSelectionDesignModule implements EngineeringDesignModule {
  private static final long serialVersionUID = 1000L;
  private final String equipmentTag;
  private final boolean wetCo2;
  private final boolean sourService;
  private final boolean seawaterExposure;
  private final double designLifeYears;
  private final double corrosionAllowanceMm;
  private final double minimumDesignMetalTemperatureC;

  public MaterialSelectionDesignModule(String equipmentTag, boolean wetCo2, boolean sourService,
      boolean seawaterExposure, double designLifeYears, double corrosionAllowanceMm,
      double minimumDesignMetalTemperatureC) {
    this.equipmentTag = equipmentTag;
    this.wetCo2 = wetCo2;
    this.sourService = sourService;
    this.seawaterExposure = seawaterExposure;
    this.designLifeYears = designLifeYears;
    this.corrosionAllowanceMm = corrosionAllowanceMm;
    this.minimumDesignMetalTemperatureC = minimumDesignMetalTemperatureC;
  }

  @Override
  public String getId() {
    return "70-material-selection-" + equipmentTag;
  }

  @Override
  public EngineeringDesignModuleResult evaluate(ProcessSystem process, EngineeringCaseRunReport caseReport,
      EngineeringDesignState state, EngineeringCalculationContext context) {
    String preliminaryMaterial = sourService || seawaterExposure ? "CORROSION_RESISTANT_ALLOY_REVIEW"
        : wetCo2 ? "CARBON_STEEL_WITH_CORROSION_CONTROL_REVIEW" : "CARBON_STEEL_REVIEW";
    return EngineeringDesignModuleResult.builder(getId(), "NORSOK M-001 preliminary degradation screening", "1.0")
        .addUpdate(EngineeringDesignUpdate.builder(equipmentTag + ".designLife", designLifeYears, "year").build())
        .addUpdate(
            EngineeringDesignUpdate.builder(equipmentTag + ".corrosionAllowance", corrosionAllowanceMm, "mm").build())
        .addUpdate(EngineeringDesignUpdate
            .builder(equipmentTag + ".minimumDesignMetalTemperature", minimumDesignMetalTemperatureC, "C").build())
        .evidence("preliminaryMaterialClass", preliminaryMaterial).evidence("wetCo2", Boolean.valueOf(wetCo2))
        .evidence("sourService", Boolean.valueOf(sourService))
        .evidence("seawaterExposure", Boolean.valueOf(seawaterExposure)).evidence("standard", "NORSOK M-001:2025")
        .warning("Corrosion assessment, chemical compatibility, welding and final material selection require approval")
        .build();
  }
}
