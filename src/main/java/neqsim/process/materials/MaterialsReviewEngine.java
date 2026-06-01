package neqsim.process.materials;

import java.util.List;
import neqsim.process.corrosion.AmmoniaCompatibility;
import neqsim.process.corrosion.ChlorideSCCAssessment;
import neqsim.process.corrosion.DensePhaseCO2Corrosion;
import neqsim.process.corrosion.HydrogenMaterialAssessment;
import neqsim.process.corrosion.NelsonCurveAssessment;
import neqsim.process.corrosion.NorsokM001MaterialSelection;
import neqsim.process.corrosion.NorsokM506CorrosionRate;
import neqsim.process.corrosion.OxygenCorrosionAssessment;
import neqsim.process.corrosion.SourServiceAssessment;
import neqsim.process.mechanicaldesign.designstandards.CUIRiskAssessment;
import neqsim.process.mechanicaldesign.designstandards.CUIRiskAssessment.CUIRisk;
import neqsim.process.mechanicaldesign.designstandards.CUIRiskAssessment.InsulationType;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Process-wide materials selection, corrosion, degradation, and integrity review engine.
 *
 * <p>
 * The engine orchestrates NeqSim's existing point calculators into one review workflow for projects
 * and assets. It accepts normalized process/materials-register data, evaluates credible damage
 * mechanisms, checks material compatibility, estimates remaining life when inspection data is
 * available, and returns an auditable JSON-ready report.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class MaterialsReviewEngine {

  /** Default constructor. */
  public MaterialsReviewEngine() {}

  /**
   * Evaluates a normalized materials review input.
   *
   * @param input normalized review input
   * @return materials review report
   */
  public MaterialsReviewReport evaluate(MaterialsReviewInput input) {
    MaterialsReviewInput effectiveInput = input == null ? new MaterialsReviewInput() : input;
    MaterialsReviewReport report = new MaterialsReviewReport();
    report.setProjectName(effectiveInput.getProjectName());
    report.addLimitation("Screening-level review. Final material selection and integrity decisions "
        + "require discipline engineer approval and project-specific TR/STID evidence.");
    report.addLimitation("STID and technical database records are consumed as normalized JSON; "
        + "database retrieval and document OCR are handled outside the Java core.");
    for (MaterialReviewItem item : effectiveInput.getItems()) {
      MaterialReviewResult result = evaluateItem(item, effectiveInput.getDesignLifeYears());
      result.finalizeVerdict();
      report.addResult(result);
    }
    report.finalizeVerdict();
    return report;
  }

  /**
   * Extracts process conditions from a process system, overlays material-register data, and
   * evaluates the result.
   *
   * @param process process system supplying temperatures, pressures, and stream compositions
   * @param input optional material register and defaults to overlay by tag
   * @return materials review report
   */
  public MaterialsReviewReport evaluate(ProcessSystem process, MaterialsReviewInput input) {
    MaterialsReviewInput processInput = MaterialsReviewInput.fromProcessSystem(process);
    processInput.mergeFrom(input);
    return evaluate(processInput);
  }

  /**
   * Evaluates one item.
   *
   * @param item material review item
   * @param defaultDesignLifeYears default design life in years
   * @return item review result
   */
  private MaterialReviewResult evaluateItem(MaterialReviewItem item,
      double defaultDesignLifeYears) {
    MaterialReviewResult result = new MaterialReviewResult(item);
    MaterialServiceEnvelope service = item.getServiceEnvelope();
    double temperatureC = value(service, 25.0, "temperature_C", "operatingTemperatureC", "tempC");
    double designTemperatureC = value(service, temperatureC, "design_temperature_C",
        "designTemperatureC", "maxDesignTemperatureC");
    double pressureBara = value(service, 1.01325, "pressure_bara", "operatingPressureBara",
        "pressureBar", "pressure_barg");
    double designLifeYears =
        value(service, defaultDesignLifeYears, "design_life_years", "designLifeYears");
    double co2MoleFraction =
        fractionValue(service, "co2_mole_fraction", "CO2", "co2", "co2MoleFraction");
    double h2sMoleFraction =
        fractionValue(service, "h2s_mole_fraction", "H2S", "h2s", "h2sMoleFraction");
    double h2MoleFraction =
        fractionValue(service, "h2_mole_fraction", "H2", "hydrogen", "h2MoleFraction");
    double chlorideMgL = value(service, 0.0, "chloride_mg_per_l", "chloride_mg_L", "chloride_mg_l",
        "chloride_mg_per_L", "chlorideMgL", "chlorideConcentrationMgL");
    double pH = value(service, 6.5, "pH", "ph", "aqueous_pH", "aqueousPH");
    boolean freeWater = service.getBoolean("free_water", service.getBoolean("freeWater", false));
    String material =
        item.getExistingMaterial().isEmpty() ? service.getString("materialType", "Carbon Steel")
            : item.getExistingMaterial();

    NorsokM506CorrosionRate co2Corrosion = runCo2Corrosion(service, result, temperatureC,
        pressureBara, co2MoleFraction, h2sMoleFraction, chlorideMgL, pH, freeWater);
    runMaterialSelection(service, result, co2Corrosion, designTemperatureC, designLifeYears,
        chlorideMgL, pH, freeWater);
    runSourService(service, result, material, temperatureC, pressureBara, co2MoleFraction,
        h2sMoleFraction, chlorideMgL, pH, freeWater);
    runChlorideScc(service, result, material, temperatureC, chlorideMgL, pH);
    runOxygenCorrosion(service, result, material, temperatureC, chlorideMgL);
    runDenseCo2(service, result, material, temperatureC, pressureBara, co2MoleFraction,
        h2sMoleFraction, h2MoleFraction);
    runHydrogen(service, result, material, temperatureC, pressureBara, h2MoleFraction,
        h2sMoleFraction, chlorideMgL, freeWater, designLifeYears);
    runNelsonCurve(service, result, material, temperatureC, pressureBara, h2MoleFraction);
    runAmmonia(service, result, material, temperatureC, pressureBara);
    runCui(service, result, material, temperatureC);
    runScreeningMechanisms(service, result, material, temperatureC, chlorideMgL, freeWater);
    runIntegrityLife(service, result, co2Corrosion);
    result.setConfidence(calculateConfidence(item));
    return result;
  }

  /**
   * Runs NORSOK M-506 CO2 corrosion when relevant.
   *
   * @param service service envelope
   * @param result item result
   * @param temperatureC operating temperature in Celsius
   * @param pressureBara operating pressure in bara
   * @param co2MoleFraction CO2 mole fraction
   * @param h2sMoleFraction H2S mole fraction
   * @param chlorideMgL chloride concentration in mg/L
   * @param pH aqueous pH
   * @param freeWater true when free water is present
   * @return configured and calculated corrosion model
   */
  private NorsokM506CorrosionRate runCo2Corrosion(MaterialServiceEnvelope service,
      MaterialReviewResult result, double temperatureC, double pressureBara, double co2MoleFraction,
      double h2sMoleFraction, double chlorideMgL, double pH, boolean freeWater) {
    NorsokM506CorrosionRate corrosion = new NorsokM506CorrosionRate();
    corrosion.setTemperatureCelsius(temperatureC);
    corrosion.setTotalPressureBara(pressureBara);
    corrosion.setCO2MoleFraction(co2MoleFraction);
    corrosion.setH2SMoleFraction(h2sMoleFraction);
    corrosion.setActualPH(pH);
    corrosion.setBicarbonateConcentrationMgL(
        value(service, 0.0, "bicarbonate_mg_per_l", "bicarbonateMgL"));
    corrosion.setIonicStrengthMolL(value(service, Math.max(0.0, chlorideMgL / 35450.0),
        "ionic_strength_mol_per_l", "ionicStrengthMolL"));
    corrosion.setFlowVelocityMs(
        value(service, 1.0, "flow_velocity_m_per_s", "flowVelocityMS", "velocityMS"));
    corrosion.setPipeDiameterM(value(service, 0.1, "pipe_diameter_m", "pipeDiameterM"));
    corrosion.setLiquidDensityKgM3(
        value(service, 1000.0, "liquid_density_kg_per_m3", "liquidDensityKgM3"));
    corrosion.setLiquidViscosityPas(
        value(service, 0.001, "liquid_viscosity_pa_s", "liquidViscosityPas"));
    corrosion
        .setInhibitorEfficiency(value(service, 0.0, "inhibitor_efficiency", "inhibitorEfficiency"));
    corrosion.setGlycolWeightFraction(
        value(service, 0.0, "glycol_weight_fraction", "glycolWeightFraction"));
    corrosion.calculate();
    if (!freeWater && co2MoleFraction > 0.0) {
      result.addAssessment(DamageMechanismAssessment
          .pass("CO2 corrosion", "NORSOK M-506",
              "CO2 is present but free water was not indicated in the supplied service envelope.",
              "Confirm dry operation and water dew-point margin in the technical database.")
          .addDetail("co2PartialPressure_bar", corrosion.getCO2PartialPressureBar())
          .addDetail("calculatedRate_mm_per_year", corrosion.getCorrectedCorrosionRate()));
      return corrosion;
    }
    if (co2MoleFraction <= 1.0e-8 && !freeWater) {
      result.addAssessment(DamageMechanismAssessment.info("CO2 corrosion", "NORSOK M-506",
          "No CO2/free-water corrosion driver was identified.",
          "No CO2 corrosion action from the supplied data."));
      return corrosion;
    }
    double rate = corrosion.getCorrectedCorrosionRate();
    String severity = normalizeSeverity(corrosion.getCorrosionSeverity());
    String status =
        "LOW".equals(severity) ? "PASS" : ("MEDIUM".equals(severity) ? "WARNING" : "FAIL");
    DamageMechanismAssessment assessment =
        new DamageMechanismAssessment("CO2 corrosion", "NORSOK M-506", status, severity,
            "Predicted CO2 corrosion rate is " + round(rate) + " mm/year.",
            rate > 1.0
                ? "Consider corrosion-resistant alloy, cladding, inhibitor availability, or "
                    + "increased corrosion allowance."
                : "Track corrosion allowance and confirm inhibition/water handling assumptions.");
    assessment.addDetail("corrosionRate_mm_per_year", rate);
    assessment.addDetail("co2Fugacity_bar", corrosion.getCO2FugacityBar());
    assessment.addDetail("co2PartialPressure_bar", corrosion.getCO2PartialPressureBar());
    assessment.addDetail("h2sPartialPressure_bar", corrosion.getH2SPartialPressureBar());
    assessment.addDetail("effectivePH", corrosion.getEffectivePH());
    assessment.addDetail("sourSeverity", corrosion.getSourSeverityClassification());
    result.addAssessment(assessment);
    return corrosion;
  }

  /**
   * Runs NORSOK M-001 material selection and stores the recommendation.
   *
   * @param service service envelope
   * @param result item result
   * @param co2Corrosion calculated CO2 corrosion model
   * @param designTemperatureC design temperature in Celsius
   * @param designLifeYears design life in years
   * @param chlorideMgL chloride concentration in mg/L
   * @param pH aqueous pH
   * @param freeWater true when free water is present
   */
  private void runMaterialSelection(MaterialServiceEnvelope service, MaterialReviewResult result,
      NorsokM506CorrosionRate co2Corrosion, double designTemperatureC, double designLifeYears,
      double chlorideMgL, double pH, boolean freeWater) {
    NorsokM001MaterialSelection selection = new NorsokM001MaterialSelection();
    selection.setCO2CorrosionRateMmyr(co2Corrosion.getCorrectedCorrosionRate());
    selection.setH2SPartialPressureBar(co2Corrosion.getH2SPartialPressureBar());
    selection.setDesignTemperatureC(designTemperatureC);
    selection.setMaxDesignTemperatureC(
        value(service, designTemperatureC, "max_design_temperature_C", "maxDesignTemperatureC"));
    selection.setChlorideConcentrationMgL(chlorideMgL);
    selection.setDesignLifeYears(designLifeYears);
    selection.setAqueousPH(pH);
    selection.setCO2PartialPressureBar(co2Corrosion.getCO2PartialPressureBar());
    selection.setFreeWaterPresent(freeWater);
    selection.evaluate();
    MaterialRecommendation recommendation = new MaterialRecommendation();
    recommendation.setRecommendedMaterial(selection.getRecommendedMaterial());
    recommendation
        .setRecommendedCorrosionAllowanceMm(selection.getRecommendedCorrosionAllowanceMm());
    recommendation.setRationale("NORSOK M-001 service category " + selection.getServiceCategory()
        + ", sour class " + selection.getSourClassification() + ", chloride SCC risk "
        + selection.getChlorideSCCRisk() + ".");
    List<String> alternatives = selection.getAlternativeMaterials();
    for (String alternative : alternatives) {
      recommendation.addAlternativeMaterial(alternative);
    }
    for (String note : selection.getNotes()) {
      recommendation.addAction(note);
    }
    recommendation.addStandard("NORSOK M-001");
    recommendation.addStandard("NORSOK M-506");
    result.setRecommendation(recommendation);
    result.addAssessment(DamageMechanismAssessment
        .info("Material selection", "NORSOK M-001",
            "Recommended material: " + selection.getRecommendedMaterial() + ", corrosion allowance "
                + round(selection.getRecommendedCorrosionAllowanceMm()) + " mm.",
            "Verify selected MDS/material class against project TR and procurement constraints.")
        .addDetail("serviceCategory", selection.getServiceCategory())
        .addDetail("recommendedMaterial", selection.getRecommendedMaterial()).addDetail(
            "recommendedCorrosionAllowance_mm", selection.getRecommendedCorrosionAllowanceMm()));
  }

  /**
   * Runs sour-service assessment.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @param co2MoleFraction CO2 mole fraction
   * @param h2sMoleFraction H2S mole fraction
   * @param chlorideMgL chloride concentration in mg/L
   * @param pH aqueous pH
   * @param freeWater true when free water is present
   */
  private void runSourService(MaterialServiceEnvelope service, MaterialReviewResult result,
      String material, double temperatureC, double pressureBara, double co2MoleFraction,
      double h2sMoleFraction, double chlorideMgL, double pH, boolean freeWater) {
    double h2sPartialPressureBar = pressureBara * h2sMoleFraction;
    if (h2sPartialPressureBar <= 0.0003 && !freeWater) {
      result.addAssessment(DamageMechanismAssessment.info("Sour service", "ISO 15156 / NACE MR0175",
          "No sour-service driver was identified from the supplied H2S/free-water data.",
          "Confirm H2S units and free-water flag in the source register."));
      return;
    }
    SourServiceAssessment sour = new SourServiceAssessment();
    sour.setH2SPartialPressureBar(h2sPartialPressureBar);
    sour.setTotalPressureBar(pressureBara);
    sour.setCO2PartialPressureBar(pressureBara * co2MoleFraction);
    sour.setInSituPH(pH);
    sour.setTemperatureC(temperatureC);
    sour.setChlorideConcentrationMgL(chlorideMgL);
    sour.setMaterialGrade(material);
    sour.setYieldStrengthMPa(value(service, 450.0, "yield_strength_MPa", "smysMPa", "SMYS_MPa"));
    sour.setHardnessHRC(value(service, 22.0, "hardness_HRC", "hardnessHRC"));
    sour.setPWHTApplied(service.getBoolean("pwht", service.getBoolean("pwhtApplied", false)));
    sour.setFreeWaterPresent(freeWater);
    sour.setElementalSulfurPresent(service.getBoolean("elemental_sulfur", false));
    sour.evaluate();
    boolean acceptable =
        sour.isSSCAcceptable() && sour.isHICAcceptable() && sour.isSOHICAcceptable();
    String severity = normalizeSeverity(sour.getOverallRiskLevel());
    DamageMechanismAssessment assessment = acceptable
        ? DamageMechanismAssessment.pass("Sour service", "ISO 15156 / NACE MR0175",
            "Sour-service check is acceptable with overall risk " + sour.getOverallRiskLevel()
                + ".",
            "Maintain hardness, PWHT, and material certificate controls.")
        : DamageMechanismAssessment.fail("Sour service", "ISO 15156 / NACE MR0175", severity,
            "Sour-service check is not acceptable with overall risk " + sour.getOverallRiskLevel()
                + ".",
            "Upgrade material or revise hardness/PWHT limits according to ISO 15156/NACE MR0175.");
    assessment.addDetail("h2sPartialPressure_bar", h2sPartialPressureBar);
    assessment.addDetail("sourRegion", sour.getSourRegion());
    assessment.addDetail("sscRisk", sour.getSSCRiskLevel());
    assessment.addDetail("hicRisk", sour.getHICRiskLevel());
    assessment.addDetail("recommendedMaterial", sour.getRecommendedMaterial());
    result.addAssessment(assessment);
  }

  /**
   * Runs chloride SCC assessment.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   * @param chlorideMgL chloride concentration in mg/L
   * @param pH aqueous pH
   */
  private void runChlorideScc(MaterialServiceEnvelope service, MaterialReviewResult result,
      String material, double temperatureC, double chlorideMgL, double pH) {
    if (chlorideMgL <= 0.0 && !looksStainless(material)) {
      return;
    }
    ChlorideSCCAssessment scc = new ChlorideSCCAssessment();
    scc.setTemperatureC(temperatureC);
    scc.setChlorideConcentrationMgL(chlorideMgL);
    scc.setMaterialType(material);
    scc.setStressRatio(value(service, 0.7, "stress_ratio", "stressRatio"));
    scc.setOxygenPresent(value(service, 0.0, "dissolved_o2_ppb", "dissolvedO2Ppb") > 10.0
        || service.getBoolean("oxygen_present", false));
    scc.setAqueousPH(pH);
    scc.evaluate();
    DamageMechanismAssessment assessment = scc.isSCCAcceptable()
        ? DamageMechanismAssessment.pass("Chloride SCC", "NORSOK M-001 / ISO 21457",
            "Chloride SCC risk is " + scc.getRiskLevel() + ".",
            "Maintain chloride/temperature limits and oxygen control.")
        : DamageMechanismAssessment.fail("Chloride SCC", "NORSOK M-001 / ISO 21457",
            normalizeSeverity(scc.getRiskLevel()),
            "Chloride SCC risk is " + scc.getRiskLevel() + " for the supplied service.",
            "Use a higher alloy material or lower chloride, oxygen, stress, or temperature exposure.");
    assessment.addDetail("maxAllowableTemperature_C", scc.getMaxAllowableTemperatureC());
    assessment.addDetail("temperatureMargin_C", scc.getTemperatureMarginC());
    assessment.addDetail("recommendedUpgrade", scc.getRecommendedUpgrade());
    result.addAssessment(assessment);
  }

  /**
   * Runs dissolved oxygen corrosion assessment.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   * @param chlorideMgL chloride concentration in mg/L
   */
  private void runOxygenCorrosion(MaterialServiceEnvelope service, MaterialReviewResult result,
      String material, double temperatureC, double chlorideMgL) {
    double o2Ppb = value(service, 0.0, "dissolved_o2_ppb", "dissolvedO2Ppb", "oxygenPpb");
    if (o2Ppb <= 0.0) {
      return;
    }
    OxygenCorrosionAssessment oxygen = new OxygenCorrosionAssessment();
    oxygen.setDissolvedO2Ppb(o2Ppb);
    oxygen.setTemperatureC(temperatureC);
    oxygen.setChlorideMgL(chlorideMgL);
    oxygen.setVelocityMS(value(service, 1.0, "flow_velocity_m_per_s", "velocityMS"));
    oxygen.setMaterialType(material);
    oxygen.setScavengerApplied(service.getBoolean("oxygen_scavenger", false));
    oxygen.setDeaerationApplied(service.getBoolean("deaeration", false));
    oxygen.setSystemType(service.getString("system_type", "closed"));
    oxygen.evaluate();
    String severity = normalizeSeverity(oxygen.getRiskLevel());
    String status = oxygen.isMeetsO2Target() && !"HIGH".equals(severity) ? "PASS" : "WARNING";
    result.addAssessment(
        new DamageMechanismAssessment("Oxygen corrosion", "NORSOK M-001", status, severity,
            "Dissolved oxygen risk is " + oxygen.getRiskLevel() + ", pitting rate "
                + round(oxygen.getPittingRateMmYr()) + " mm/year.",
            oxygen.getRecommendedTreatment()).addDetail("dissolvedO2_ppb", o2Ppb)
                .addDetail("corrosionRate_mm_per_year", oxygen.getCorrosionRateMmYr())
                .addDetail("pittingRate_mm_per_year", oxygen.getPittingRateMmYr())
                .addDetail("targetO2_ppb", oxygen.getTargetO2Ppb()));
  }

  /**
   * Runs dense-phase CO2 impurity and wet-corrosion assessment.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @param co2MoleFraction CO2 mole fraction
   * @param h2sMoleFraction H2S mole fraction
   * @param h2MoleFraction H2 mole fraction
   */
  private void runDenseCo2(MaterialServiceEnvelope service, MaterialReviewResult result,
      String material, double temperatureC, double pressureBara, double co2MoleFraction,
      double h2sMoleFraction, double h2MoleFraction) {
    if (co2MoleFraction < 0.5 || pressureBara < 50.0) {
      return;
    }
    DensePhaseCO2Corrosion dense = new DensePhaseCO2Corrosion();
    dense.setTemperatureC(temperatureC);
    dense.setPressureBara(pressureBara);
    dense.setCo2PurityMolPct(co2MoleFraction * 100.0);
    dense.setWaterContentPpmv(value(service, 0.0, "water_ppmv", "waterContentPpmv"));
    dense.setO2ContentPpmv(value(service, 0.0, "o2_ppmv", "o2ContentPpmv"));
    dense.setSo2ContentPpmv(value(service, 0.0, "so2_ppmv", "so2ContentPpmv"));
    dense.setNoxContentPpmv(value(service, 0.0, "nox_ppmv", "noxContentPpmv"));
    dense.setH2sContentPpmv(h2sMoleFraction * 1.0e6);
    dense.setH2ContentMolPct(h2MoleFraction * 100.0);
    dense.setN2ContentMolPct(value(service, 0.0, "n2_mole_fraction", "N2") * 100.0);
    dense.setArContentMolPct(value(service, 0.0, "ar_mole_fraction", "Ar") * 100.0);
    dense.setMaterialType(material);
    dense.evaluate();
    String severity = normalizeSeverity(dense.getRiskLevel());
    String status = dense.isMeetsImpuritySpecs() && !dense.isFreeWaterRisk() ? "PASS" : "WARNING";
    result.addAssessment(new DamageMechanismAssessment("Dense CO2 corrosion",
        "DNV-RP-F104 / ISO 27913", status, severity,
        "Dense CO2 risk is " + dense.getRiskLevel() + ", phase state " + dense.getCo2PhaseState()
            + ".",
        dense.getRecommendation())
            .addDetail("wetCorrosionRate_mm_per_year", dense.getWetCorrosionRateMmYr())
            .addDetail("freeWaterRisk", Boolean.valueOf(dense.isFreeWaterRisk()))
            .addDetail("waterMargin_ppmv", dense.getWaterMarginPpmv()));
  }

  /**
   * Runs hydrogen service assessment.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @param h2MoleFraction hydrogen mole fraction
   * @param h2sMoleFraction H2S mole fraction
   * @param chlorideMgL chloride concentration in mg/L
   * @param freeWater true when free water is present
   * @param designLifeYears design life in years
   */
  private void runHydrogen(MaterialServiceEnvelope service, MaterialReviewResult result,
      String material, double temperatureC, double pressureBara, double h2MoleFraction,
      double h2sMoleFraction, double chlorideMgL, boolean freeWater, double designLifeYears) {
    double h2PartialPressure = pressureBara * h2MoleFraction;
    if (h2PartialPressure <= 0.1 && h2sMoleFraction <= 0.0) {
      return;
    }
    HydrogenMaterialAssessment hydrogen = new HydrogenMaterialAssessment();
    hydrogen.setH2PartialPressureBar(h2PartialPressure);
    hydrogen.setTotalPressureBar(pressureBara);
    hydrogen.setH2MoleFractionGas(h2MoleFraction);
    hydrogen.setH2SPartialPressureBar(pressureBara * h2sMoleFraction);
    hydrogen.setDesignTemperatureC(
        value(service, temperatureC, "design_temperature_C", "designTemperatureC"));
    hydrogen.setMaxOperatingTemperatureC(temperatureC);
    hydrogen.setMaterialGrade(material);
    hydrogen.setSmysMPa(value(service, 450.0, "smysMPa", "yield_strength_MPa"));
    hydrogen.setHardnessHRC(value(service, 22.0, "hardness_HRC", "hardnessHRC"));
    hydrogen
        .setWallThicknessMm(value(service, 10.0, "nominal_wall_thickness_mm", "wallThicknessMm"));
    hydrogen.setPwhtApplied(service.getBoolean("pwht", service.getBoolean("pwhtApplied", false)));
    hydrogen.setFreeWaterPresent(freeWater);
    hydrogen.setChlorideMgL(chlorideMgL);
    hydrogen.setDesignLifeYears(designLifeYears);
    hydrogen.setCyclicService(service.getBoolean("cyclic_service", false));
    hydrogen.evaluate();
    boolean acceptable = hydrogen.isHydrogenEmbrittlementAcceptable() && hydrogen.isHTHAAcceptable()
        && hydrogen.isSourServiceOk();
    DamageMechanismAssessment assessment = acceptable
        ? DamageMechanismAssessment.pass("Hydrogen service", "API 941 / ASME B31.12 / ISO 15156",
            "Hydrogen material assessment risk is " + hydrogen.getOverallRiskLevel() + ".",
            "Track hydrogen derating and material certification for the selected material.")
        : DamageMechanismAssessment.fail("Hydrogen service", "API 941 / ASME B31.12 / ISO 15156",
            normalizeSeverity(hydrogen.getOverallRiskLevel()),
            "Hydrogen material assessment risk is " + hydrogen.getOverallRiskLevel() + ".",
            "Review material upgrade, hardness limits, PWHT, and hydrogen derating.");
    assessment.addDetail("h2PartialPressure_bar", hydrogen.getH2PartialPressureBar());
    assessment.addDetail("embrittlementRisk", hydrogen.getHydrogenEmbrittlementRisk());
    assessment.addDetail("hthaRisk", hydrogen.getHTHARisk());
    assessment.addDetail("hydrogenDeratingFactor", hydrogen.getHydrogenDeratingFactor());
    assessment.addDetail("recommendedMaterial", hydrogen.getRecommendedMaterial());
    result.addAssessment(assessment);
  }

  /**
   * Runs API 941 Nelson curve screening when hydrogen and high temperature are relevant.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   * @param h2MoleFraction hydrogen mole fraction
   */
  private void runNelsonCurve(MaterialServiceEnvelope service, MaterialReviewResult result,
      String material, double temperatureC, double pressureBara, double h2MoleFraction) {
    double h2PartialPressure = pressureBara * h2MoleFraction;
    if (h2PartialPressure <= 0.1 || temperatureC < 200.0) {
      return;
    }
    NelsonCurveAssessment nelson = new NelsonCurveAssessment();
    nelson.setTemperatureC(temperatureC);
    nelson.setH2PartialPressureBar(h2PartialPressure);
    nelson.setMaterialType(material);
    nelson.evaluate();
    DamageMechanismAssessment assessment = nelson.isBelowNelsonCurve()
        ? DamageMechanismAssessment.pass("High-temperature hydrogen attack", "API 941",
            "Service is below the selected Nelson curve.",
            "Maintain design temperature and hydrogen partial pressure envelope.")
        : DamageMechanismAssessment.fail("High-temperature hydrogen attack", "API 941",
            normalizeSeverity(nelson.getRiskLevel()), "Service exceeds the selected Nelson curve.",
            nelson.getRecommendedUpgrade());
    assessment.addDetail("temperatureMargin_C", nelson.getTemperatureMarginC());
    assessment.addDetail("maxAllowableTemperature_C", nelson.getMaxAllowableTemperatureC());
    assessment.addDetail("recommendedUpgrade", nelson.getRecommendedUpgrade());
    result.addAssessment(assessment);
  }

  /**
   * Runs ammonia compatibility screening.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   * @param pressureBara pressure in bara
   */
  private void runAmmonia(MaterialServiceEnvelope service, MaterialReviewResult result,
      String material, double temperatureC, double pressureBara) {
    double nh3WtPct =
        value(service, 0.0, "nh3_wt_percent", "nh3ConcentrationWtPct", "ammonia_wt_percent");
    if (nh3WtPct <= 0.0) {
      return;
    }
    AmmoniaCompatibility ammonia = new AmmoniaCompatibility();
    ammonia.setTemperatureC(temperatureC);
    ammonia.setPressureBara(pressureBara);
    ammonia.setNh3ConcentrationWtPct(nh3WtPct);
    ammonia.setAnhydrous(service.getBoolean("anhydrous", false));
    ammonia.setWaterContentWtPct(value(service, 0.0, "water_wt_percent", "waterContentWtPct"));
    ammonia.setO2InhibitorWtPct(value(service, 0.0, "o2_inhibitor_wt_percent", "o2InhibitorWtPct"));
    ammonia.setMaterialType(material);
    ammonia.setStressRatio(value(service, 0.7, "stress_ratio", "stressRatio"));
    ammonia.setPwhtApplied(service.getBoolean("pwht", service.getBoolean("pwhtApplied", false)));
    ammonia.setHardnessHRC(value(service, 22.0, "hardness_HRC", "hardnessHRC"));
    ammonia.evaluate();
    DamageMechanismAssessment assessment = ammonia.isCompatible()
        ? DamageMechanismAssessment.pass("Ammonia compatibility", "ISO 11120 / EIGA guidance",
            "Ammonia compatibility risk is " + ammonia.getRiskLevel() + ".",
            "Maintain inhibitor and stress-relief requirements.")
        : DamageMechanismAssessment.fail("Ammonia compatibility", "ISO 11120 / EIGA guidance",
            normalizeSeverity(ammonia.getRiskLevel()),
            "Ammonia compatibility risk is " + ammonia.getRiskLevel() + ".",
            "Review material upgrade, O2 inhibitor, hardness, and stress relief.");
    assessment.addDetail("primaryMechanism", ammonia.getPrimaryMechanism());
    assessment.addDetail("recommendedMaterial", ammonia.getRecommendedMaterial());
    assessment.addDetail("requiredO2Inhibitor_wt_percent", ammonia.getRequiredO2InhibitorWtPct());
    result.addAssessment(assessment);
  }

  /**
   * Runs corrosion-under-insulation screening.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   */
  private void runCui(MaterialServiceEnvelope service, MaterialReviewResult result, String material,
      double temperatureC) {
    boolean insulated = service.getBoolean("insulated", false)
        || !service.getString("insulation_type", "").isEmpty();
    if (!insulated) {
      return;
    }
    InsulationType insulation = parseInsulation(
        service.getString("insulation_type", service.getString("insulationType", "MINERAL_WOOL")));
    CUIRisk risk = CUIRiskAssessment.assessRisk(temperatureC, looksStainless(material), insulation,
        value(service, 0.0, "coating_age_years", "coatingAgeYears"),
        service.getBoolean("marine_environment", service.getBoolean("marineEnvironment", false)));
    String severity = risk == CUIRisk.VERY_HIGH ? "CRITICAL" : risk.name();
    String status = risk == CUIRisk.LOW ? "PASS" : (risk == CUIRisk.MEDIUM ? "WARNING" : "FAIL");
    result.addAssessment(new DamageMechanismAssessment("Corrosion under insulation",
        "API 581 / API 583 / NORSOK M-501", status, normalizeSeverity(severity),
        "CUI screening risk is " + risk.name() + ".",
        "Inspect every " + CUIRiskAssessment.recommendedInspectionIntervalYears(risk)
            + " years using " + CUIRiskAssessment.recommendedInspectionMethods(risk) + ".")
                .addDetail("cuiRisk", risk.name()).addDetail("insulationType", insulation.name())
                .addDetail("inspectionInterval_years",
                    CUIRiskAssessment.recommendedInspectionIntervalYears(risk))
                .addDetail("insulationSuitable", Boolean
                    .valueOf(CUIRiskAssessment.isInsulationSuitable(insulation, temperatureC))));
  }

  /**
   * Runs direct screening checks for mechanisms that do not yet have dedicated calculators.
   *
   * @param service service envelope
   * @param result item result
   * @param material material grade/type
   * @param temperatureC temperature in Celsius
   * @param chlorideMgL chloride concentration in mg/L
   * @param freeWater true when free water is present
   */
  private void runScreeningMechanisms(MaterialServiceEnvelope service, MaterialReviewResult result,
      String material, double temperatureC, double chlorideMgL, boolean freeWater) {
    double velocity = value(service, 1.0, "flow_velocity_m_per_s", "velocityMS");
    double sand = value(service, 0.0, "sand_mg_per_l", "sandMgL");
    if (velocity > 12.0 || sand > 10.0) {
      result.addAssessment(DamageMechanismAssessment.warning("Erosion-corrosion", "API RP 14E",
          velocity > 20.0 ? "HIGH" : "MEDIUM",
          "Velocity or sand loading indicates erosion-corrosion screening risk.",
          "Check erosional velocity, solids management, bends, reducers, and inspection locations.")
          .addDetail("velocity_m_per_s", velocity).addDetail("sand_mg_per_l", sand));
    }
    boolean stagnant = velocity < 0.3 || service.getBoolean("stagnant", false);
    if (freeWater && stagnant && temperatureC >= 5.0 && temperatureC <= 80.0) {
      result.addAssessment(DamageMechanismAssessment.warning(
          "Microbiologically influenced corrosion", "NORSOK M-001 / AMPP guidance", "MEDIUM",
          "Free water and stagnant/moderate temperature conditions are favourable for MIC.",
          "Review biocide program, pigging/flushing, dead-leg register, and microbiological sampling.")
          .addDetail("stagnant", Boolean.valueOf(stagnant))
          .addDetail("temperature_C", temperatureC));
    }
    if (service.getBoolean("dissimilar_metals", false)) {
      result.addAssessment(DamageMechanismAssessment.warning("Galvanic corrosion", "NORSOK M-001",
          "MEDIUM", "Dissimilar metals were identified in the supplied register.",
          "Verify electrical isolation, area ratio, coating continuity, and sacrificial protection.")
          .addDetail("baseMaterial", material)
          .addDetail("coupledMaterial", service.getString("coupled_material", "not specified")));
    }
    if (service.getBoolean("cyclic_service", false) || service.getBoolean("vibration", false)) {
      result.addAssessment(DamageMechanismAssessment.warning("Fatigue and vibration", "DNV-RP-C203",
          chlorideMgL > 0.0 ? "HIGH" : "MEDIUM",
          "Cyclic service or vibration was indicated in the material register.",
          "Perform fatigue/FIV assessment, review supports, small-bore connections, and weld details."));
    }
  }

  /**
   * Runs remaining-life screening from wall thickness and corrosion rate.
   *
   * @param service service envelope
   * @param result item result
   * @param co2Corrosion CO2 corrosion model supplying default corrosion rate
   */
  private void runIntegrityLife(MaterialServiceEnvelope service, MaterialReviewResult result,
      NorsokM506CorrosionRate co2Corrosion) {
    double nominal = value(service, Double.NaN, "nominal_wall_thickness_mm", "originalThicknessMm",
        "nominalWallThicknessMm");
    double current = value(service, Double.NaN, "current_wall_thickness_mm", "currentThicknessMm");
    double minimum =
        value(service, Double.NaN, "minimum_required_thickness_mm", "minimumRequiredThicknessMm");
    double rate = value(service, co2Corrosion.getCorrectedCorrosionRate(),
        "corrosion_rate_mm_per_year", "measuredCorrosionRateMmYr");
    if (!Double.isNaN(nominal) && !Double.isNaN(current) && !Double.isNaN(minimum)) {
      result.setIntegrityLifeAssessment(
          IntegrityLifeAssessment.fromWallThickness(nominal, current, minimum, rate));
    } else {
      result.setIntegrityLifeAssessment(new IntegrityLifeAssessment().setVerdict("NOT_ASSESSED")
          .addNote("Wall thickness data were not sufficient for remaining-life calculation."));
    }
  }

  /**
   * Calculates confidence from supplied item data completeness.
   *
   * @param item review item
   * @return confidence score between 0 and 1
   */
  private double calculateConfidence(MaterialReviewItem item) {
    double score = 0.4;
    MaterialServiceEnvelope service = item.getServiceEnvelope();
    if (!item.getTag().isEmpty()) {
      score += 0.1;
    }
    if (!item.getExistingMaterial().isEmpty()) {
      score += 0.1;
    }
    if (service.has("temperature_C") || service.has("operatingTemperatureC")) {
      score += 0.1;
    }
    if (service.has("pressure_bara") || service.has("operatingPressureBara")) {
      score += 0.1;
    }
    if (service.has("free_water") || service.has("freeWater")) {
      score += 0.1;
    }
    if (!item.getSourceReferences().isEmpty()) {
      score += 0.1;
    }
    return Math.min(1.0, score);
  }

  /**
   * Reads the first available numeric value from an envelope.
   *
   * @param service service envelope
   * @param defaultValue default value
   * @param keys candidate keys
   * @return numeric value
   */
  private double value(MaterialServiceEnvelope service, double defaultValue, String... keys) {
    for (String key : keys) {
      if (service.has(key)) {
        return service.getDouble(key, defaultValue);
      }
    }
    return defaultValue;
  }

  /**
   * Reads a fraction from mole fraction or percent-style fields.
   *
   * @param service service envelope
   * @param keys candidate keys
   * @return value as fraction from 0 to 1
   */
  private double fractionValue(MaterialServiceEnvelope service, String... keys) {
    double value = value(service, 0.0, keys);
    if (value > 1.0 && value <= 100.0) {
      return value / 100.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }

  /**
   * Normalizes severity text to LOW, MEDIUM, HIGH, or CRITICAL.
   *
   * @param severity source severity text
   * @return normalized severity
   */
  private String normalizeSeverity(String severity) {
    if (severity == null) {
      return "LOW";
    }
    String normalized = severity.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    if (normalized.contains("VERY_HIGH") || normalized.contains("CRITICAL")
        || normalized.contains("SEVERE")) {
      return "CRITICAL";
    }
    if (normalized.contains("HIGH") || normalized.contains("FAIL") || normalized.contains("NOT")) {
      return "HIGH";
    }
    if (normalized.contains("MED") || normalized.contains("MODERATE")
        || normalized.contains("WARNING")) {
      return "MEDIUM";
    }
    return "LOW";
  }

  /**
   * Tests if a material string appears to represent stainless steel.
   *
   * @param material material text
   * @return true if stainless steel is likely
   */
  private boolean looksStainless(String material) {
    String text = material == null ? "" : material.toLowerCase();
    return text.contains("stainless") || text.contains("316") || text.contains("304")
        || text.contains("duplex") || text.contains("22cr") || text.contains("25cr");
  }

  /**
   * Parses insulation type text.
   *
   * @param insulation insulation text from a register
   * @return insulation type enum
   */
  private InsulationType parseInsulation(String insulation) {
    String text = insulation == null ? ""
        : insulation.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    for (InsulationType type : InsulationType.values()) {
      if (type.name().equals(text)) {
        return type;
      }
    }
    if (text.contains("CELL") || text.contains("FOAMGLAS")) {
      return InsulationType.CELLULAR_GLASS;
    }
    if (text.contains("PIR")) {
      return InsulationType.PIR_FOAM;
    }
    if (text.contains("AEROGEL")) {
      return InsulationType.AEROGEL;
    }
    if (text.contains("PERLITE")) {
      return InsulationType.PERLITE;
    }
    if (text.contains("CALCIUM")) {
      return InsulationType.CALCIUM_SILICATE;
    }
    return InsulationType.MINERAL_WOOL;
  }

  /**
   * Rounds a value to three decimals for compact messages.
   *
   * @param value numeric value
   * @return rounded value
   */
  private double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
