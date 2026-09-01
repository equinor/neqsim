package neqsim.process.mechanicaldesign.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Immutable input for DNV-ST-F101 pipeline limit-state screening. */
public final class DnvStF101PipelineDesignInput implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Safety class and the corresponding screening resistance factor. */
  public enum SafetyClass {
    LOW(1.046), MEDIUM(1.138), HIGH(1.308);

    private final double resistanceFactor;

    SafetyClass(double resistanceFactor) {
      this.resistanceFactor = resistanceFactor;
    }

    /** @return screening resistance factor from the NeqSim standards dataset */
    public double getResistanceFactor() {
      return resistanceFactor;
    }
  }

  /** Traceable line-pipe fabrication route. */
  public enum FabricationRoute {
    SEAMLESS, UOE, JCOE, HFW, OTHER
  }

  /** One constant-amplitude fatigue spectrum bin. */
  public static final class FatigueBin implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double stressRangeMPa;
    private final double cycles;

    /**
     * Create a fatigue spectrum bin.
     *
     * @param stressRangeMPa nominal stress range in MPa
     * @param cycles expected number of cycles during the design life
     */
    public FatigueBin(double stressRangeMPa, double cycles) {
      this.stressRangeMPa = stressRangeMPa;
      this.cycles = cycles;
    }

    /** @return nominal stress range in MPa */
    public double getStressRangeMPa() {
      return stressRangeMPa;
    }

    /** @return expected number of cycles */
    public double getCycles() {
      return cycles;
    }

    /** @return deterministic map for calculation provenance */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("stressRangeMPa", Double.valueOf(stressRangeMPa));
      result.put("cycles", Double.valueOf(cycles));
      return Collections.unmodifiableMap(result);
    }
  }

  private final StandardEdition edition;
  private final String equipmentType;
  private final SafetyClass safetyClass;
  private final FabricationRoute fabricationRoute;
  private final double outsideDiameterM;
  private final double nominalWallThicknessM;
  private final double corrosionAllowanceM;
  private final double fabricationToleranceFraction;
  private final double ovalityFraction;
  private final double maximumAllowableOvalityFraction;
  private final double smysMPa;
  private final double smtsMPa;
  private final double youngsModulusMPa;
  private final double poissonRatio;
  private final double strengthDeratingFactor;
  private final double smysStrengthFactor;
  private final double smtsStrengthFactor;
  private final double materialResistanceFactor;
  private final double fabricationFactor;
  private final double localOperatingPressureMPa;
  private final double localIncidentalPressureMPa;
  private final double externalPressureMPa;
  private final double minimumInternalPressureMPa;
  private final double systemTestPressureMPa;
  private final double testExternalPressureMPa;
  private final double designAxialForceKN;
  private final double designBendingMomentKNm;
  private final double designTorsionMomentKNm;
  private final double installationAxialStrainFraction;
  private final double installationBendingStrainFraction;
  private final double accumulatedPlasticStrainFraction;
  private final double allowableInstallationStrainFraction;
  private final double fatigueSnLogA;
  private final double fatigueSnSlope;
  private final double fatigueStressConcentrationFactor;
  private final double fatigueDesignFactor;
  private final List<FatigueBin> fatigueSpectrum;

  private DnvStF101PipelineDesignInput(Builder builder) {
    edition = builder.edition;
    equipmentType = builder.equipmentType;
    safetyClass = builder.safetyClass;
    fabricationRoute = builder.fabricationRoute;
    outsideDiameterM = builder.outsideDiameterM;
    nominalWallThicknessM = builder.nominalWallThicknessM;
    corrosionAllowanceM = builder.corrosionAllowanceM;
    fabricationToleranceFraction = builder.fabricationToleranceFraction;
    ovalityFraction = builder.ovalityFraction;
    maximumAllowableOvalityFraction = builder.maximumAllowableOvalityFraction;
    smysMPa = builder.smysMPa;
    smtsMPa = builder.smtsMPa;
    youngsModulusMPa = builder.youngsModulusMPa;
    poissonRatio = builder.poissonRatio;
    strengthDeratingFactor = builder.strengthDeratingFactor;
    smysStrengthFactor = builder.smysStrengthFactor;
    smtsStrengthFactor = builder.smtsStrengthFactor;
    materialResistanceFactor = builder.materialResistanceFactor;
    fabricationFactor = builder.fabricationFactor;
    localOperatingPressureMPa = builder.localOperatingPressureMPa;
    localIncidentalPressureMPa = builder.localIncidentalPressureMPa;
    externalPressureMPa = builder.externalPressureMPa;
    minimumInternalPressureMPa = builder.minimumInternalPressureMPa;
    systemTestPressureMPa = builder.systemTestPressureMPa;
    testExternalPressureMPa = builder.testExternalPressureMPa;
    designAxialForceKN = builder.designAxialForceKN;
    designBendingMomentKNm = builder.designBendingMomentKNm;
    designTorsionMomentKNm = builder.designTorsionMomentKNm;
    installationAxialStrainFraction = builder.installationAxialStrainFraction;
    installationBendingStrainFraction = builder.installationBendingStrainFraction;
    accumulatedPlasticStrainFraction = builder.accumulatedPlasticStrainFraction;
    allowableInstallationStrainFraction = builder.allowableInstallationStrainFraction;
    fatigueSnLogA = builder.fatigueSnLogA;
    fatigueSnSlope = builder.fatigueSnSlope;
    fatigueStressConcentrationFactor = builder.fatigueStressConcentrationFactor;
    fatigueDesignFactor = builder.fatigueDesignFactor;
    fatigueSpectrum = Collections.unmodifiableList(new ArrayList<FatigueBin>(builder.fatigueSpectrum));
  }

  /** @return a new fail-closed input builder */
  public static Builder builder() {
    return new Builder();
  }

  /** @return explicit standard edition */
  public StandardEdition getEdition() {
    return edition;
  }

  /** @return simple equipment type used for standard applicability */
  public String getEquipmentType() {
    return equipmentType;
  }

  /** @return selected project safety class */
  public SafetyClass getSafetyClass() {
    return safetyClass;
  }

  /** @return traceable line-pipe fabrication route */
  public FabricationRoute getFabricationRoute() {
    return fabricationRoute;
  }

  /** @return outside diameter in metres */
  public double getOutsideDiameterM() {
    return outsideDiameterM;
  }

  /** @return nominal wall thickness in metres */
  public double getNominalWallThicknessM() {
    return nominalWallThicknessM;
  }

  /** @return corrosion allowance in metres */
  public double getCorrosionAllowanceM() {
    return corrosionAllowanceM;
  }

  /** @return negative wall-thickness tolerance as a fraction */
  public double getFabricationToleranceFraction() {
    return fabricationToleranceFraction;
  }

  /** @return specified or measured ovality as a fraction */
  public double getOvalityFraction() {
    return ovalityFraction;
  }

  /** @return project ovality limit as a fraction */
  public double getMaximumAllowableOvalityFraction() {
    return maximumAllowableOvalityFraction;
  }

  /** @return specified minimum yield strength in MPa before de-rating */
  public double getSmysMPa() {
    return smysMPa;
  }

  /** @return specified minimum tensile strength in MPa before de-rating */
  public double getSmtsMPa() {
    return smtsMPa;
  }

  /** @return Young's modulus in MPa */
  public double getYoungsModulusMPa() {
    return youngsModulusMPa;
  }

  /** @return Poisson ratio */
  public double getPoissonRatio() {
    return poissonRatio;
  }

  /** @return project strength de-rating factor */
  public double getStrengthDeratingFactor() {
    return strengthDeratingFactor;
  }

  /** @return characteristic SMYS strength factor */
  public double getSmysStrengthFactor() {
    return smysStrengthFactor;
  }

  /** @return characteristic SMTS strength factor */
  public double getSmtsStrengthFactor() {
    return smtsStrengthFactor;
  }

  /** @return material resistance factor */
  public double getMaterialResistanceFactor() {
    return materialResistanceFactor;
  }

  /** @return collapse/propagation fabrication factor for the selected route */
  public double getFabricationFactor() {
    return fabricationFactor;
  }

  /** @return local operating pressure in MPa */
  public double getLocalOperatingPressureMPa() {
    return localOperatingPressureMPa;
  }

  /** @return local incidental pressure in MPa */
  public double getLocalIncidentalPressureMPa() {
    return localIncidentalPressureMPa;
  }

  /** @return governing external pressure in MPa */
  public double getExternalPressureMPa() {
    return externalPressureMPa;
  }

  /** @return minimum internal pressure used for collapse in MPa */
  public double getMinimumInternalPressureMPa() {
    return minimumInternalPressureMPa;
  }

  /** @return local system-test pressure in MPa */
  public double getSystemTestPressureMPa() {
    return systemTestPressureMPa;
  }

  /** @return external pressure during system test in MPa */
  public double getTestExternalPressureMPa() {
    return testExternalPressureMPa;
  }

  /** @return signed design axial force in kN */
  public double getDesignAxialForceKN() {
    return designAxialForceKN;
  }

  /** @return design bending moment in kN m */
  public double getDesignBendingMomentKNm() {
    return designBendingMomentKNm;
  }

  /** @return design torsion moment in kN m */
  public double getDesignTorsionMomentKNm() {
    return designTorsionMomentKNm;
  }

  /** @return signed installation axial strain as a fraction */
  public double getInstallationAxialStrainFraction() {
    return installationAxialStrainFraction;
  }

  /** @return signed installation bending strain as a fraction */
  public double getInstallationBendingStrainFraction() {
    return installationBendingStrainFraction;
  }

  /** @return accumulated plastic strain as a fraction */
  public double getAccumulatedPlasticStrainFraction() {
    return accumulatedPlasticStrainFraction;
  }

  /** @return project allowable installation strain as a fraction */
  public double getAllowableInstallationStrainFraction() {
    return allowableInstallationStrainFraction;
  }

  /** @return base-10 S-N curve intercept */
  public double getFatigueSnLogA() {
    return fatigueSnLogA;
  }

  /** @return S-N curve slope */
  public double getFatigueSnSlope() {
    return fatigueSnSlope;
  }

  /** @return fatigue stress concentration factor */
  public double getFatigueStressConcentrationFactor() {
    return fatigueStressConcentrationFactor;
  }

  /** @return design fatigue factor applied to cumulative damage */
  public double getFatigueDesignFactor() {
    return fatigueDesignFactor;
  }

  /** @return immutable fatigue spectrum */
  public List<FatigueBin> getFatigueSpectrum() {
    return fatigueSpectrum;
  }

  /** @return complete deterministic input map for calculation provenance */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("edition", edition == null ? null : edition.getDisplayName());
    result.put("equipmentType", equipmentType);
    result.put("safetyClass", safetyClass == null ? null : safetyClass.name());
    result.put("fabricationRoute", fabricationRoute == null ? null : fabricationRoute.name());
    result.put("outsideDiameterM", Double.valueOf(outsideDiameterM));
    result.put("nominalWallThicknessM", Double.valueOf(nominalWallThicknessM));
    result.put("corrosionAllowanceM", Double.valueOf(corrosionAllowanceM));
    result.put("fabricationToleranceFraction", Double.valueOf(fabricationToleranceFraction));
    result.put("ovalityFraction", Double.valueOf(ovalityFraction));
    result.put("maximumAllowableOvalityFraction", Double.valueOf(maximumAllowableOvalityFraction));
    result.put("smysMPa", Double.valueOf(smysMPa));
    result.put("smtsMPa", Double.valueOf(smtsMPa));
    result.put("youngsModulusMPa", Double.valueOf(youngsModulusMPa));
    result.put("poissonRatio", Double.valueOf(poissonRatio));
    result.put("strengthDeratingFactor", Double.valueOf(strengthDeratingFactor));
    result.put("smysStrengthFactor", Double.valueOf(smysStrengthFactor));
    result.put("smtsStrengthFactor", Double.valueOf(smtsStrengthFactor));
    result.put("materialResistanceFactor", Double.valueOf(materialResistanceFactor));
    result.put("fabricationFactor", Double.valueOf(fabricationFactor));
    result.put("localOperatingPressureMPa", Double.valueOf(localOperatingPressureMPa));
    result.put("localIncidentalPressureMPa", Double.valueOf(localIncidentalPressureMPa));
    result.put("externalPressureMPa", Double.valueOf(externalPressureMPa));
    result.put("minimumInternalPressureMPa", Double.valueOf(minimumInternalPressureMPa));
    result.put("systemTestPressureMPa", Double.valueOf(systemTestPressureMPa));
    result.put("testExternalPressureMPa", Double.valueOf(testExternalPressureMPa));
    result.put("designAxialForceKN", Double.valueOf(designAxialForceKN));
    result.put("designBendingMomentKNm", Double.valueOf(designBendingMomentKNm));
    result.put("designTorsionMomentKNm", Double.valueOf(designTorsionMomentKNm));
    result.put("installationAxialStrainFraction", Double.valueOf(installationAxialStrainFraction));
    result.put("installationBendingStrainFraction", Double.valueOf(installationBendingStrainFraction));
    result.put("accumulatedPlasticStrainFraction", Double.valueOf(accumulatedPlasticStrainFraction));
    result.put("allowableInstallationStrainFraction", Double.valueOf(allowableInstallationStrainFraction));
    result.put("fatigueSnLogA", Double.valueOf(fatigueSnLogA));
    result.put("fatigueSnSlope", Double.valueOf(fatigueSnSlope));
    result.put("fatigueStressConcentrationFactor", Double.valueOf(fatigueStressConcentrationFactor));
    result.put("fatigueDesignFactor", Double.valueOf(fatigueDesignFactor));
    List<Map<String, Object>> spectrum = new ArrayList<Map<String, Object>>();
    for (FatigueBin bin : fatigueSpectrum) {
      spectrum.add(bin.toMap());
    }
    result.put("fatigueSpectrum", Collections.unmodifiableList(spectrum));
    return Collections.unmodifiableMap(result);
  }

  /** Builder that leaves project-controlled values unset until supplied explicitly. */
  public static final class Builder {
    private StandardEdition edition = StandardEdition.defaultEdition(StandardType.DNV_ST_F101);
    private String equipmentType = "Pipeline";
    private SafetyClass safetyClass;
    private FabricationRoute fabricationRoute;
    private double outsideDiameterM = Double.NaN;
    private double nominalWallThicknessM = Double.NaN;
    private double corrosionAllowanceM = Double.NaN;
    private double fabricationToleranceFraction = Double.NaN;
    private double ovalityFraction = Double.NaN;
    private double maximumAllowableOvalityFraction = Double.NaN;
    private double smysMPa = Double.NaN;
    private double smtsMPa = Double.NaN;
    private double youngsModulusMPa = Double.NaN;
    private double poissonRatio = Double.NaN;
    private double strengthDeratingFactor = Double.NaN;
    private double smysStrengthFactor = Double.NaN;
    private double smtsStrengthFactor = Double.NaN;
    private double materialResistanceFactor = Double.NaN;
    private double fabricationFactor = Double.NaN;
    private double localOperatingPressureMPa = Double.NaN;
    private double localIncidentalPressureMPa = Double.NaN;
    private double externalPressureMPa = Double.NaN;
    private double minimumInternalPressureMPa = Double.NaN;
    private double systemTestPressureMPa = Double.NaN;
    private double testExternalPressureMPa = Double.NaN;
    private double designAxialForceKN = Double.NaN;
    private double designBendingMomentKNm = Double.NaN;
    private double designTorsionMomentKNm = Double.NaN;
    private double installationAxialStrainFraction = Double.NaN;
    private double installationBendingStrainFraction = Double.NaN;
    private double accumulatedPlasticStrainFraction = Double.NaN;
    private double allowableInstallationStrainFraction = Double.NaN;
    private double fatigueSnLogA = Double.NaN;
    private double fatigueSnSlope = Double.NaN;
    private double fatigueStressConcentrationFactor = Double.NaN;
    private double fatigueDesignFactor = Double.NaN;
    private final List<FatigueBin> fatigueSpectrum = new ArrayList<FatigueBin>();

    /**
     * Set the explicit standard edition.
     *
     * @param value standard edition
     * @return this builder
     */
    public Builder edition(StandardEdition value) {
      edition = value;
      return this;
    }

    /**
     * Set the simple pipeline equipment type.
     *
     * @param value equipment type
     * @return this builder
     */
    public Builder equipmentType(String value) {
      equipmentType = value;
      return this;
    }

    /**
     * Set the project safety class.
     *
     * @param value safety class
     * @return this builder
     */
    public Builder safetyClass(SafetyClass value) {
      safetyClass = value;
      return this;
    }

    /**
     * Set the line-pipe fabrication route.
     *
     * @param value fabrication route
     * @return this builder
     */
    public Builder fabricationRoute(FabricationRoute value) {
      fabricationRoute = value;
      return this;
    }

    /**
     * Set pipeline geometry and corrosion allowance.
     *
     * @param outsideDiameterM outside diameter in metres
     * @param nominalWallThicknessM nominal wall thickness in metres
     * @param corrosionAllowanceM corrosion allowance in metres
     * @return this builder
     */
    public Builder geometry(double outsideDiameterM, double nominalWallThicknessM, double corrosionAllowanceM) {
      this.outsideDiameterM = outsideDiameterM;
      this.nominalWallThicknessM = nominalWallThicknessM;
      this.corrosionAllowanceM = corrosionAllowanceM;
      return this;
    }

    /**
     * Set fabrication and ovality inputs.
     *
     * @param toleranceFraction negative thickness tolerance as a fraction
     * @param ovalityFraction ovality as a fraction
     * @param maximumAllowableOvalityFraction project ovality limit as a fraction
     * @param fabricationFactor collapse/propagation fabrication factor
     * @return this builder
     */
    public Builder fabrication(double toleranceFraction, double ovalityFraction, double maximumAllowableOvalityFraction,
        double fabricationFactor) {
      fabricationToleranceFraction = toleranceFraction;
      this.ovalityFraction = ovalityFraction;
      this.maximumAllowableOvalityFraction = maximumAllowableOvalityFraction;
      this.fabricationFactor = fabricationFactor;
      return this;
    }

    /**
     * Set material strength and elastic properties.
     *
     * @param smysMPa specified minimum yield strength in MPa
     * @param smtsMPa specified minimum tensile strength in MPa
     * @param youngsModulusMPa Young's modulus in MPa
     * @param poissonRatio Poisson ratio
     * @return this builder
     */
    public Builder material(double smysMPa, double smtsMPa, double youngsModulusMPa, double poissonRatio) {
      this.smysMPa = smysMPa;
      this.smtsMPa = smtsMPa;
      this.youngsModulusMPa = youngsModulusMPa;
      this.poissonRatio = poissonRatio;
      return this;
    }

    /**
     * Set material and resistance factors.
     *
     * @param strengthDeratingFactor temperature/material de-rating factor
     * @param smysStrengthFactor characteristic SMYS factor
     * @param smtsStrengthFactor characteristic SMTS factor
     * @param materialResistanceFactor material resistance factor
     * @return this builder
     */
    public Builder resistanceFactors(double strengthDeratingFactor, double smysStrengthFactor,
        double smtsStrengthFactor, double materialResistanceFactor) {
      this.strengthDeratingFactor = strengthDeratingFactor;
      this.smysStrengthFactor = smysStrengthFactor;
      this.smtsStrengthFactor = smtsStrengthFactor;
      this.materialResistanceFactor = materialResistanceFactor;
      return this;
    }

    /**
     * Set all operating, incidental, collapse, and test pressure cases.
     *
     * @param localOperatingPressureMPa local operating pressure in MPa
     * @param localIncidentalPressureMPa local incidental pressure in MPa
     * @param externalPressureMPa governing external pressure in MPa
     * @param minimumInternalPressureMPa minimum internal pressure for collapse in MPa
     * @param systemTestPressureMPa system-test pressure in MPa
     * @param testExternalPressureMPa external pressure during test in MPa
     * @return this builder
     */
    public Builder pressures(double localOperatingPressureMPa, double localIncidentalPressureMPa,
        double externalPressureMPa, double minimumInternalPressureMPa, double systemTestPressureMPa,
        double testExternalPressureMPa) {
      this.localOperatingPressureMPa = localOperatingPressureMPa;
      this.localIncidentalPressureMPa = localIncidentalPressureMPa;
      this.externalPressureMPa = externalPressureMPa;
      this.minimumInternalPressureMPa = minimumInternalPressureMPa;
      this.systemTestPressureMPa = systemTestPressureMPa;
      this.testExternalPressureMPa = testExternalPressureMPa;
      return this;
    }

    /**
     * Set load-controlled structural actions.
     *
     * @param axialForceKN signed axial force in kN
     * @param bendingMomentKNm bending moment in kN m
     * @param torsionMomentKNm torsion moment in kN m
     * @return this builder
     */
    public Builder designLoads(double axialForceKN, double bendingMomentKNm, double torsionMomentKNm) {
      designAxialForceKN = axialForceKN;
      designBendingMomentKNm = bendingMomentKNm;
      designTorsionMomentKNm = torsionMomentKNm;
      return this;
    }

    /**
     * Set installation strain history and limit.
     *
     * @param axialStrainFraction signed axial strain fraction
     * @param bendingStrainFraction signed bending strain fraction
     * @param accumulatedPlasticStrainFraction accumulated plastic strain fraction
     * @param allowableStrainFraction project allowable strain fraction
     * @return this builder
     */
    public Builder installationStrains(double axialStrainFraction, double bendingStrainFraction,
        double accumulatedPlasticStrainFraction, double allowableStrainFraction) {
      installationAxialStrainFraction = axialStrainFraction;
      installationBendingStrainFraction = bendingStrainFraction;
      this.accumulatedPlasticStrainFraction = accumulatedPlasticStrainFraction;
      allowableInstallationStrainFraction = allowableStrainFraction;
      return this;
    }

    /**
     * Set the project-approved fatigue curve and factors.
     *
     * @param snLogA base-10 S-N intercept
     * @param snSlope S-N slope
     * @param stressConcentrationFactor fatigue stress concentration factor
     * @param designFatigueFactor design fatigue factor
     * @return this builder
     */
    public Builder fatigueCurve(double snLogA, double snSlope, double stressConcentrationFactor,
        double designFatigueFactor) {
      fatigueSnLogA = snLogA;
      fatigueSnSlope = snSlope;
      fatigueStressConcentrationFactor = stressConcentrationFactor;
      fatigueDesignFactor = designFatigueFactor;
      return this;
    }

    /**
     * Add a constant-amplitude fatigue bin.
     *
     * @param stressRangeMPa stress range in MPa
     * @param cycles expected cycles during design life
     * @return this builder
     */
    public Builder addFatigueBin(double stressRangeMPa, double cycles) {
      fatigueSpectrum.add(new FatigueBin(stressRangeMPa, cycles));
      return this;
    }

    /** @return immutable input; the kernel reports missing values as readiness blockers */
    public DnvStF101PipelineDesignInput build() {
      return new DnvStF101PipelineDesignInput(this);
    }
  }
}
