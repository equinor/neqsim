package neqsim.process.util.monitor;

import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector.OilWaterFlowRegime;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * Response object for a solved {@link TwoFluidPipe} multiphase-flow calculation.
 *
 * <p>
 * Scalar fields provide a concise engineering summary. The {@link #profile} field contains the section-by-section
 * multiphase results and is included at {@link DetailLevel#FULL} detail only. Units are encoded in field names to keep
 * the JSON contract unambiguous.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TwoFluidPipeResponse extends BaseResponse {
  public Double simulationTimeSeconds;
  public Double inletPressureBara;
  public Double outletPressureBara;
  public Double pressureDropBar;
  public Double inletTemperatureC;
  public Double outletTemperatureC;
  public Double averageLiquidHoldup;
  public String dominantFlowRegime;
  public Double liquidInventoryM3;
  public Double averageMixtureDensityKgM3;
  public Double maximumMixtureVelocityMS;
  public Double erosionalVelocityMargin;
  public Integer hydrateRiskSectionCount;
  public Double distanceToHydrateRiskM;
  public Boolean hasWaxRisk;
  public Integer outletSlugCount;
  public Double totalSlugVolumeAtOutletM3;
  public Double maxSlugLengthAtOutletM;
  public Double maxSlugVolumeAtOutletM3;
  public Profile profile;

  /** Constructor for JSON deserialization. */
  public TwoFluidPipeResponse() {
  }

  /**
   * Create a response snapshot from a solved two-fluid pipe.
   *
   * @param pipe solved multiphase pipe
   */
  public TwoFluidPipeResponse(TwoFluidPipe pipe) {
    super(pipe);
    simulationTimeSeconds = pipe.getSimulationTime();
    inletPressureBara = pipe.getInletPressure();
    outletPressureBara = pipe.getOutletPressure();
    pressureDropBar = inletPressureBara - outletPressureBara;
    inletTemperatureC = pipe.getInletStream().getTemperature("C");
    outletTemperatureC = pipe.getOutletStream().getTemperature("C");
    averageLiquidHoldup = pipe.getAverageLiquidHoldup();
    dominantFlowRegime = pipe.getDominantFlowRegime();
    liquidInventoryM3 = pipe.getLiquidInventory("m3");
    averageMixtureDensityKgM3 = pipe.getAverageMixtureDensity();
    maximumMixtureVelocityMS = pipe.getMaxMixtureVelocity();
    erosionalVelocityMargin = pipe.getErosionalVelocityMargin(122.0);
    hydrateRiskSectionCount = pipe.getHydrateRiskSectionCount();
    distanceToHydrateRiskM = pipe.getDistanceToHydrateRisk();
    hasWaxRisk = pipe.hasWaxRisk();
    outletSlugCount = pipe.getOutletSlugCount();
    totalSlugVolumeAtOutletM3 = pipe.getTotalSlugVolumeAtOutlet();
    maxSlugLengthAtOutletM = pipe.getMaxSlugLengthAtOutlet();
    maxSlugVolumeAtOutletM3 = pipe.getMaxSlugVolumeAtOutlet();
    profile = new Profile(pipe);
  }

  /** {@inheritDoc} */
  @Override
  public void applyConfig(ReportConfig cfg) {
    DetailLevel level = getDetailLevel(cfg);
    if (level == DetailLevel.HIDE) {
      tagName = null;
      name = null;
      simulationTimeSeconds = null;
      inletPressureBara = null;
      outletPressureBara = null;
      pressureDropBar = null;
      inletTemperatureC = null;
      outletTemperatureC = null;
      averageLiquidHoldup = null;
      dominantFlowRegime = null;
      liquidInventoryM3 = null;
      averageMixtureDensityKgM3 = null;
      maximumMixtureVelocityMS = null;
      erosionalVelocityMargin = null;
      hydrateRiskSectionCount = null;
      distanceToHydrateRiskM = null;
      hasWaxRisk = null;
      outletSlugCount = null;
      totalSlugVolumeAtOutletM3 = null;
      maxSlugLengthAtOutletM = null;
      maxSlugVolumeAtOutletM3 = null;
      profile = null;
    } else if (level == DetailLevel.SUMMARY) {
      profile = null;
    } else if (level == DetailLevel.MINIMUM) {
      profile = null;
      simulationTimeSeconds = null;
      inletTemperatureC = null;
      outletTemperatureC = null;
      liquidInventoryM3 = null;
      averageMixtureDensityKgM3 = null;
      maximumMixtureVelocityMS = null;
      erosionalVelocityMargin = null;
      hydrateRiskSectionCount = null;
      distanceToHydrateRiskM = null;
      hasWaxRisk = null;
      outletSlugCount = null;
      totalSlugVolumeAtOutletM3 = null;
      maxSlugLengthAtOutletM = null;
      maxSlugVolumeAtOutletM3 = null;
    }
  }

  /** Section-by-section multiphase-flow results. */
  public static class Profile {
    public double[] positionM;
    public double[] pressureBara;
    public double[] temperatureC;
    public double[] liquidHoldup;
    public double[] waterCut;
    public double[] oilHoldup;
    public double[] waterHoldup;
    public double[] gasVelocityMS;
    public double[] liquidVelocityMS;
    public double[] oilVelocityMS;
    public double[] waterVelocityMS;
    public String[] flowRegime;
    public String[] oilWaterFlowRegime;
    public boolean[] waterWetting;
    public boolean[] waterDropoutRisk;
    public double[] entrainmentFraction;
    public double[] entrainedDropletDiameterM;
    public double[] inclinedSectionGasCarryoverNumber;
    public boolean[] inclinedSectionLiquidFallbackPotential;
    public double[] severeSluggingNumber;
    public boolean[] severeSlugPotential;

    /** Constructor for JSON deserialization. */
    public Profile() {
    }

    /**
     * Create a profile snapshot from a solved pipe.
     *
     * @param pipe solved multiphase pipe
     */
    public Profile(TwoFluidPipe pipe) {
      positionM = pipe.getPositionProfile();
      pressureBara = convertPressureToBara(pipe.getPressureProfile());
      temperatureC = convertTemperatureToC(pipe.getTemperatureProfile());
      liquidHoldup = pipe.getLiquidHoldupProfile();
      waterCut = pipe.getWaterCutProfile();
      oilHoldup = pipe.getOilHoldupProfile();
      waterHoldup = pipe.getWaterHoldupProfile();
      gasVelocityMS = pipe.getGasVelocityProfile();
      liquidVelocityMS = pipe.getLiquidVelocityProfile();
      oilVelocityMS = pipe.getOilVelocityProfile();
      waterVelocityMS = pipe.getWaterVelocityProfile();
      flowRegime = flowRegimeNames(pipe.getFlowRegimeProfile());
      oilWaterFlowRegime = oilWaterFlowRegimeNames(pipe.getOilWaterFlowRegimeProfile());
      waterWetting = pipe.getWaterWettingProfile();
      waterDropoutRisk = pipe.getWaterDropoutRiskProfile();
      entrainmentFraction = pipe.getEntrainmentFractionProfile();
      entrainedDropletDiameterM = pipe.getEntrainedDropletDiameterProfile();
      inclinedSectionGasCarryoverNumber = pipe.getInclinedSectionGasCarryoverNumberProfile();
      inclinedSectionLiquidFallbackPotential = pipe.getInclinedSectionLiquidFallbackPotentialProfile();
      severeSluggingNumber = pipe.getSevereSluggingNumberProfile();
      severeSlugPotential = pipe.getSevereSlugPotentialProfile();
    }

    /**
     * Convert pressure values from pascal to bar absolute.
     *
     * @param pressurePa pressure profile in pascal
     * @return pressure profile in bar absolute
     */
    private static double[] convertPressureToBara(double[] pressurePa) {
      double[] converted = new double[pressurePa.length];
      for (int i = 0; i < pressurePa.length; i++) {
        converted[i] = pressurePa[i] * 1.0e-5;
      }
      return converted;
    }

    /**
     * Convert temperature values from kelvin to degrees Celsius.
     *
     * @param temperatureK temperature profile in kelvin
     * @return temperature profile in degrees Celsius
     */
    private static double[] convertTemperatureToC(double[] temperatureK) {
      double[] converted = new double[temperatureK.length];
      for (int i = 0; i < temperatureK.length; i++) {
        converted[i] = temperatureK[i] - 273.15;
      }
      return converted;
    }

    /**
     * Convert flow-regime values to stable JSON names.
     *
     * @param regimes flow-regime profile
     * @return flow-regime names
     */
    private static String[] flowRegimeNames(FlowRegime[] regimes) {
      String[] names = new String[regimes.length];
      for (int i = 0; i < regimes.length; i++) {
        names[i] = regimes[i] == null ? null : regimes[i].name();
      }
      return names;
    }

    /**
     * Convert oil-water flow-regime values to stable JSON names.
     *
     * @param regimes oil-water flow-regime profile
     * @return oil-water flow-regime names
     */
    private static String[] oilWaterFlowRegimeNames(OilWaterFlowRegime[] regimes) {
      String[] names = new String[regimes.length];
      for (int i = 0; i < regimes.length; i++) {
        names[i] = regimes[i] == null ? null : regimes[i].name();
      }
      return names;
    }
  }
}
