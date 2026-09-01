package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;

/**
 * Immutable input for transparent DNV-RP-F109 on-bottom stability screening.
 *
 * <p>
 * No project-controlled engineering value is defaulted. Missing or invalid values are reported by the typed engineering
 * kernel before calculation.
 * </p>
 */
public final class DnvRpF109OnBottomStabilityInput implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Subsea cylindrical asset represented by the calculation. */
  public enum AssetType {
    /** Rigid or flexible submarine pipeline. */
    PIPELINE,
    /** Submarine cable. */
    CABLE,
    /** Submarine umbilical. */
    UMBILICAL
  }

  /** Lateral stability route used for one environmental load case. */
  public enum LateralMethod {
    /** Transparent Morison-load and Coulomb/passive-resistance static screening. */
    ABSOLUTE_STATIC,
    /** Check an externally calculated response against a 0.5-diameter displacement limit. */
    EXTERNAL_RESPONSE_0_5D,
    /** Check an externally calculated response against a 10-diameter displacement limit. */
    EXTERNAL_RESPONSE_10D,
    /** Check an externally calculated response against a project-defined displacement limit. */
    EXTERNAL_RESPONSE_USER_DEFINED
  }

  /**
   * Immutable environmental, hydrodynamic, soil, and design-condition load case.
   */
  public static final class LoadCase implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String caseId;
    private final double submergedWeightNPerM;
    private final double currentVelocityMPerS;
    private final double waveVelocityMPerS;
    private final double waveAccelerationMPerS2;
    private final double currentDirectionRelativeToPipeDeg;
    private final double waveDirectionRelativeToPipeDeg;
    private final double dragCoefficient;
    private final double liftCoefficient;
    private final double inertiaCoefficient;
    private final double horizontalLoadReductionFactor;
    private final double verticalLoadReductionFactor;
    private final double soilFrictionCoefficient;
    private final double passiveSoilResistanceNPerM;
    private final double horizontalSafetyFactor;
    private final double verticalSafetyFactor;
    private final double stormDurationHours;
    private final double oscillationCount;
    private final LateralMethod lateralMethod;
    private final double predictedLateralDisplacementM;
    private final double allowableDisplacementDiameters;
    private final Boolean responseModelWithinValidatedRange;
    private final String responseModelBasis;

    private LoadCase(Builder builder) {
      caseId = builder.caseId;
      submergedWeightNPerM = builder.submergedWeightNPerM;
      currentVelocityMPerS = builder.currentVelocityMPerS;
      waveVelocityMPerS = builder.waveVelocityMPerS;
      waveAccelerationMPerS2 = builder.waveAccelerationMPerS2;
      currentDirectionRelativeToPipeDeg = builder.currentDirectionRelativeToPipeDeg;
      waveDirectionRelativeToPipeDeg = builder.waveDirectionRelativeToPipeDeg;
      dragCoefficient = builder.dragCoefficient;
      liftCoefficient = builder.liftCoefficient;
      inertiaCoefficient = builder.inertiaCoefficient;
      horizontalLoadReductionFactor = builder.horizontalLoadReductionFactor;
      verticalLoadReductionFactor = builder.verticalLoadReductionFactor;
      soilFrictionCoefficient = builder.soilFrictionCoefficient;
      passiveSoilResistanceNPerM = builder.passiveSoilResistanceNPerM;
      horizontalSafetyFactor = builder.horizontalSafetyFactor;
      verticalSafetyFactor = builder.verticalSafetyFactor;
      stormDurationHours = builder.stormDurationHours;
      oscillationCount = builder.oscillationCount;
      lateralMethod = builder.lateralMethod;
      predictedLateralDisplacementM = builder.predictedLateralDisplacementM;
      allowableDisplacementDiameters = builder.allowableDisplacementDiameters;
      responseModelWithinValidatedRange = builder.responseModelWithinValidatedRange;
      responseModelBasis = builder.responseModelBasis;
    }

    /** @return new load-case builder with all numeric values unset */
    public static Builder builder() {
      return new Builder();
    }

    /** @return traceable case identifier */
    public String getCaseId() {
      return caseId;
    }

    /** @return actual submerged weight in N/m */
    public double getSubmergedWeightNPerM() {
      return submergedWeightNPerM;
    }

    /** @return design current velocity magnitude at the pipe elevation in m/s */
    public double getCurrentVelocityMPerS() {
      return currentVelocityMPerS;
    }

    /** @return design wave-induced velocity magnitude at the pipe elevation in m/s */
    public double getWaveVelocityMPerS() {
      return waveVelocityMPerS;
    }

    /** @return design wave acceleration magnitude at the pipe elevation in m/s2 */
    public double getWaveAccelerationMPerS2() {
      return waveAccelerationMPerS2;
    }

    /** @return current direction relative to the pipe axis in degrees */
    public double getCurrentDirectionRelativeToPipeDeg() {
      return currentDirectionRelativeToPipeDeg;
    }

    /** @return wave direction relative to the pipe axis in degrees */
    public double getWaveDirectionRelativeToPipeDeg() {
      return waveDirectionRelativeToPipeDeg;
    }

    /** @return project hydrodynamic drag coefficient */
    public double getDragCoefficient() {
      return dragCoefficient;
    }

    /** @return project hydrodynamic lift coefficient */
    public double getLiftCoefficient() {
      return liftCoefficient;
    }

    /** @return project hydrodynamic inertia coefficient */
    public double getInertiaCoefficient() {
      return inertiaCoefficient;
    }

    /** @return project horizontal hydrodynamic load reduction factor */
    public double getHorizontalLoadReductionFactor() {
      return horizontalLoadReductionFactor;
    }

    /** @return project vertical hydrodynamic load reduction factor */
    public double getVerticalLoadReductionFactor() {
      return verticalLoadReductionFactor;
    }

    /** @return project soil friction coefficient */
    public double getSoilFrictionCoefficient() {
      return soilFrictionCoefficient;
    }

    /** @return validated passive soil resistance in N/m */
    public double getPassiveSoilResistanceNPerM() {
      return passiveSoilResistanceNPerM;
    }

    /** @return project horizontal load safety factor */
    public double getHorizontalSafetyFactor() {
      return horizontalSafetyFactor;
    }

    /** @return project vertical load safety factor */
    public double getVerticalSafetyFactor() {
      return verticalSafetyFactor;
    }

    /** @return design storm duration in hours */
    public double getStormDurationHours() {
      return stormDurationHours;
    }

    /** @return number of oscillations represented by the response case */
    public double getOscillationCount() {
      return oscillationCount;
    }

    /** @return selected lateral assessment route */
    public LateralMethod getLateralMethod() {
      return lateralMethod;
    }

    /** @return externally calculated lateral displacement in m */
    public double getPredictedLateralDisplacementM() {
      return predictedLateralDisplacementM;
    }

    /** @return user-defined displacement limit as a multiple of outside diameter */
    public double getAllowableDisplacementDiameters() {
      return allowableDisplacementDiameters;
    }

    /** @return explicit confirmation of the external response model validity range */
    public Boolean getResponseModelWithinValidatedRange() {
      return responseModelWithinValidatedRange;
    }

    /** @return traceable external response-model basis */
    public String getResponseModelBasis() {
      return responseModelBasis;
    }

    /** @return complete immutable provenance map for this load case */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("caseId", caseId);
      result.put("submergedWeightNPerM", Double.valueOf(submergedWeightNPerM));
      result.put("currentVelocityMPerS", Double.valueOf(currentVelocityMPerS));
      result.put("waveVelocityMPerS", Double.valueOf(waveVelocityMPerS));
      result.put("waveAccelerationMPerS2", Double.valueOf(waveAccelerationMPerS2));
      result.put("currentDirectionRelativeToPipeDeg", Double.valueOf(currentDirectionRelativeToPipeDeg));
      result.put("waveDirectionRelativeToPipeDeg", Double.valueOf(waveDirectionRelativeToPipeDeg));
      result.put("dragCoefficient", Double.valueOf(dragCoefficient));
      result.put("liftCoefficient", Double.valueOf(liftCoefficient));
      result.put("inertiaCoefficient", Double.valueOf(inertiaCoefficient));
      result.put("horizontalLoadReductionFactor", Double.valueOf(horizontalLoadReductionFactor));
      result.put("verticalLoadReductionFactor", Double.valueOf(verticalLoadReductionFactor));
      result.put("soilFrictionCoefficient", Double.valueOf(soilFrictionCoefficient));
      result.put("passiveSoilResistanceNPerM", Double.valueOf(passiveSoilResistanceNPerM));
      result.put("horizontalSafetyFactor", Double.valueOf(horizontalSafetyFactor));
      result.put("verticalSafetyFactor", Double.valueOf(verticalSafetyFactor));
      result.put("stormDurationHours", Double.valueOf(stormDurationHours));
      result.put("oscillationCount", Double.valueOf(oscillationCount));
      result.put("lateralMethod", lateralMethod == null ? null : lateralMethod.name());
      result.put("predictedLateralDisplacementM", Double.valueOf(predictedLateralDisplacementM));
      result.put("allowableDisplacementDiameters", Double.valueOf(allowableDisplacementDiameters));
      result.put("responseModelWithinValidatedRange", responseModelWithinValidatedRange);
      result.put("responseModelBasis", responseModelBasis);
      return Collections.unmodifiableMap(result);
    }

    /** Builder for one explicit stability load case. */
    public static final class Builder {
      private String caseId;
      private double submergedWeightNPerM = Double.NaN;
      private double currentVelocityMPerS = Double.NaN;
      private double waveVelocityMPerS = Double.NaN;
      private double waveAccelerationMPerS2 = Double.NaN;
      private double currentDirectionRelativeToPipeDeg = Double.NaN;
      private double waveDirectionRelativeToPipeDeg = Double.NaN;
      private double dragCoefficient = Double.NaN;
      private double liftCoefficient = Double.NaN;
      private double inertiaCoefficient = Double.NaN;
      private double horizontalLoadReductionFactor = Double.NaN;
      private double verticalLoadReductionFactor = Double.NaN;
      private double soilFrictionCoefficient = Double.NaN;
      private double passiveSoilResistanceNPerM = Double.NaN;
      private double horizontalSafetyFactor = Double.NaN;
      private double verticalSafetyFactor = Double.NaN;
      private double stormDurationHours = Double.NaN;
      private double oscillationCount = Double.NaN;
      private LateralMethod lateralMethod;
      private double predictedLateralDisplacementM = Double.NaN;
      private double allowableDisplacementDiameters = Double.NaN;
      private Boolean responseModelWithinValidatedRange;
      private String responseModelBasis;

      private Builder() {
      }

      /**
       * Set the traceable load-case identifier.
       *
       * @param value load-case identifier
       * @return this builder
       */
      public Builder caseId(String value) {
        caseId = value;
        return this;
      }

      /**
       * Set the actual submerged weight.
       *
       * @param value submerged weight in N/m
       * @return this builder
       */
      public Builder submergedWeightNPerM(double value) {
        submergedWeightNPerM = value;
        return this;
      }

      /**
       * Set the current velocity magnitude at asset elevation.
       *
       * @param value current velocity in m/s
       * @return this builder
       */
      public Builder currentVelocityMPerS(double value) {
        currentVelocityMPerS = value;
        return this;
      }

      /**
       * Set the wave-induced velocity magnitude at asset elevation.
       *
       * @param value wave velocity in m/s
       * @return this builder
       */
      public Builder waveVelocityMPerS(double value) {
        waveVelocityMPerS = value;
        return this;
      }

      /**
       * Set the wave acceleration magnitude at asset elevation.
       *
       * @param value wave acceleration in m/s2
       * @return this builder
       */
      public Builder waveAccelerationMPerS2(double value) {
        waveAccelerationMPerS2 = value;
        return this;
      }

      /**
       * Set the current direction relative to the asset axis.
       *
       * @param value relative current direction in degrees
       * @return this builder
       */
      public Builder currentDirectionRelativeToPipeDeg(double value) {
        currentDirectionRelativeToPipeDeg = value;
        return this;
      }

      /**
       * Set the wave direction relative to the asset axis.
       *
       * @param value relative wave direction in degrees
       * @return this builder
       */
      public Builder waveDirectionRelativeToPipeDeg(double value) {
        waveDirectionRelativeToPipeDeg = value;
        return this;
      }

      /**
       * Set the project drag coefficient.
       *
       * @param value drag coefficient
       * @return this builder
       */
      public Builder dragCoefficient(double value) {
        dragCoefficient = value;
        return this;
      }

      /**
       * Set the project lift coefficient.
       *
       * @param value lift coefficient
       * @return this builder
       */
      public Builder liftCoefficient(double value) {
        liftCoefficient = value;
        return this;
      }

      /**
       * Set the project inertia coefficient.
       *
       * @param value inertia coefficient
       * @return this builder
       */
      public Builder inertiaCoefficient(double value) {
        inertiaCoefficient = value;
        return this;
      }

      /**
       * Set the horizontal hydrodynamic load-reduction factor.
       *
       * @param value reduction factor in (0, 1]
       * @return this builder
       */
      public Builder horizontalLoadReductionFactor(double value) {
        horizontalLoadReductionFactor = value;
        return this;
      }

      /**
       * Set the vertical hydrodynamic load-reduction factor.
       *
       * @param value reduction factor in (0, 1]
       * @return this builder
       */
      public Builder verticalLoadReductionFactor(double value) {
        verticalLoadReductionFactor = value;
        return this;
      }

      /**
       * Set the project soil-friction coefficient.
       *
       * @param value soil-friction coefficient
       * @return this builder
       */
      public Builder soilFrictionCoefficient(double value) {
        soilFrictionCoefficient = value;
        return this;
      }

      /**
       * Set the validated passive soil resistance.
       *
       * @param value passive resistance in N/m
       * @return this builder
       */
      public Builder passiveSoilResistanceNPerM(double value) {
        passiveSoilResistanceNPerM = value;
        return this;
      }

      /**
       * Set the horizontal load safety factor.
       *
       * @param value horizontal safety factor of at least one
       * @return this builder
       */
      public Builder horizontalSafetyFactor(double value) {
        horizontalSafetyFactor = value;
        return this;
      }

      /**
       * Set the vertical load safety factor.
       *
       * @param value vertical safety factor of at least one
       * @return this builder
       */
      public Builder verticalSafetyFactor(double value) {
        verticalSafetyFactor = value;
        return this;
      }

      /**
       * Set the design storm duration.
       *
       * @param value storm duration in hours
       * @return this builder
       */
      public Builder stormDurationHours(double value) {
        stormDurationHours = value;
        return this;
      }

      /**
       * Set the represented oscillation count.
       *
       * @param value oscillation count
       * @return this builder
       */
      public Builder oscillationCount(double value) {
        oscillationCount = value;
        return this;
      }

      /**
       * Set the selected lateral assessment route.
       *
       * @param value lateral method
       * @return this builder
       */
      public Builder lateralMethod(LateralMethod value) {
        lateralMethod = value;
        return this;
      }

      /**
       * Set the externally calculated lateral displacement.
       *
       * @param value displacement in m
       * @return this builder
       */
      public Builder predictedLateralDisplacementM(double value) {
        predictedLateralDisplacementM = value;
        return this;
      }

      /**
       * Set the user-defined displacement limit.
       *
       * @param value allowable displacement as a multiple of diameter
       * @return this builder
       */
      public Builder allowableDisplacementDiameters(double value) {
        allowableDisplacementDiameters = value;
        return this;
      }

      /**
       * Confirm whether the external response model is within its validated range.
       *
       * @param value validation-range confirmation
       * @return this builder
       */
      public Builder responseModelWithinValidatedRange(boolean value) {
        responseModelWithinValidatedRange = Boolean.valueOf(value);
        return this;
      }

      /**
       * Set the traceable external response-model basis.
       *
       * @param value response-model basis
       * @return this builder
       */
      public Builder responseModelBasis(String value) {
        responseModelBasis = value;
        return this;
      }

      /** @return immutable load case */
      public LoadCase build() {
        return new LoadCase(this);
      }
    }
  }

  private final StandardEdition edition;
  private final AssetType assetType;
  private final String equipmentType;
  private final double outsideDiameterM;
  private final double seawaterDensityKgM3;
  private final double gravitationalAccelerationMPerS2;
  private final String engineeringBasis;
  private final List<LoadCase> loadCases;

  private DnvRpF109OnBottomStabilityInput(Builder builder) {
    edition = builder.edition;
    assetType = builder.assetType;
    equipmentType = builder.equipmentType;
    outsideDiameterM = builder.outsideDiameterM;
    seawaterDensityKgM3 = builder.seawaterDensityKgM3;
    gravitationalAccelerationMPerS2 = builder.gravitationalAccelerationMPerS2;
    engineeringBasis = builder.engineeringBasis;
    loadCases = Collections.unmodifiableList(new ArrayList<LoadCase>(builder.loadCases));
  }

  /** @return new builder with all project-controlled values unset */
  public static Builder builder() {
    return new Builder();
  }

  /** @return explicit recommended-practice edition */
  public StandardEdition getEdition() {
    return edition;
  }

  /** @return represented subsea asset type */
  public AssetType getAssetType() {
    return assetType;
  }

  /** @return NeqSim equipment type used for applicability */
  public String getEquipmentType() {
    return equipmentType;
  }

  /** @return total hydrodynamic outside diameter in m */
  public double getOutsideDiameterM() {
    return outsideDiameterM;
  }

  /** @return seawater density in kg/m3 */
  public double getSeawaterDensityKgM3() {
    return seawaterDensityKgM3;
  }

  /** @return gravitational acceleration in m/s2 */
  public double getGravitationalAccelerationMPerS2() {
    return gravitationalAccelerationMPerS2;
  }

  /** @return traceable project engineering basis */
  public String getEngineeringBasis() {
    return engineeringBasis;
  }

  /** @return immutable environmental and design-condition cases */
  public List<LoadCase> getLoadCases() {
    return loadCases;
  }

  /** @return complete immutable input provenance */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("edition", edition == null ? null : edition.getDisplayName());
    result.put("assetType", assetType == null ? null : assetType.name());
    result.put("equipmentType", equipmentType);
    result.put("outsideDiameterM", Double.valueOf(outsideDiameterM));
    result.put("seawaterDensityKgM3", Double.valueOf(seawaterDensityKgM3));
    result.put("gravitationalAccelerationMPerS2", Double.valueOf(gravitationalAccelerationMPerS2));
    result.put("engineeringBasis", engineeringBasis);
    List<Map<String, Object>> cases = new ArrayList<Map<String, Object>>();
    for (LoadCase loadCase : loadCases) {
      cases.add(loadCase.toMap());
    }
    result.put("loadCases", cases);
    return Collections.unmodifiableMap(result);
  }

  /** Builder for complete on-bottom stability input. */
  public static final class Builder {
    private StandardEdition edition;
    private AssetType assetType;
    private String equipmentType;
    private double outsideDiameterM = Double.NaN;
    private double seawaterDensityKgM3 = Double.NaN;
    private double gravitationalAccelerationMPerS2 = Double.NaN;
    private String engineeringBasis;
    private final List<LoadCase> loadCases = new ArrayList<LoadCase>();

    private Builder() {
    }

    /**
     * Set the explicit recommended-practice edition.
     *
     * @param value standard edition
     * @return this builder
     */
    public Builder edition(StandardEdition value) {
      edition = value;
      return this;
    }

    /**
     * Set the represented subsea asset type.
     *
     * @param value asset type
     * @return this builder
     */
    public Builder assetType(AssetType value) {
      assetType = value;
      return this;
    }

    /**
     * Set the NeqSim equipment type used for applicability.
     *
     * @param value equipment type
     * @return this builder
     */
    public Builder equipmentType(String value) {
      equipmentType = value;
      return this;
    }

    /**
     * Set the total hydrodynamic outside diameter.
     *
     * @param value outside diameter in m
     * @return this builder
     */
    public Builder outsideDiameterM(double value) {
      outsideDiameterM = value;
      return this;
    }

    /**
     * Set the seawater density.
     *
     * @param value seawater density in kg/m3
     * @return this builder
     */
    public Builder seawaterDensityKgM3(double value) {
      seawaterDensityKgM3 = value;
      return this;
    }

    /**
     * Set gravitational acceleration.
     *
     * @param value gravitational acceleration in m/s2
     * @return this builder
     */
    public Builder gravitationalAccelerationMPerS2(double value) {
      gravitationalAccelerationMPerS2 = value;
      return this;
    }

    /**
     * Set the traceable project engineering basis.
     *
     * @param value engineering basis
     * @return this builder
     */
    public Builder engineeringBasis(String value) {
      engineeringBasis = value;
      return this;
    }

    /**
     * Add one environmental and design-condition load case.
     *
     * @param value load case
     * @return this builder
     */
    public Builder addLoadCase(LoadCase value) {
      if (value != null) {
        loadCases.add(value);
      }
      return this;
    }

    /** @return immutable complete input */
    public DnvRpF109OnBottomStabilityInput build() {
      return new DnvRpF109OnBottomStabilityInput(this);
    }
  }
}
