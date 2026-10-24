package neqsim.process.equipment.reservoir;

import java.io.Serializable;
import neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.Viscosity;
import neqsim.thermo.util.steam.Iapws_if97;

/**
 * Stateless radial-Darcy water-injectivity law with explicit PVT evidence.
 *
 * <p>
 * The model evaluates {@code q = 2 pi k h (p_bh - p_r) / (mu B (ln(r_e / r_w) + S))} and reports the rate at standard
 * conditions in Sm3/day. It contains no fracture, relative-permeability, crossflow, transient-reservoir or calibration
 * model. Those constraints remain with the owning well and reservoir workflow.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class RadialDarcyWaterInjectivity implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Millidarcy to square metre conversion retained from the established injection screen. */
  private static final double MILLIDARCY_TO_SQUARE_METRE = 9.869e-16;
  /** Seconds per day. */
  private static final double SECONDS_PER_DAY = 86400.0;
  /** Pascals per bar. */
  private static final double PASCALS_PER_BAR = 1.0e5;
  /** Standard temperature used for pure-water standard volume (K). */
  private static final double STANDARD_TEMPERATURE_K = 288.15;
  /** Standard pressure used for pure-water standard volume (bara). */
  private static final double STANDARD_PRESSURE_BARA = 1.01325;
  /** Lower temperature of the existing water-viscosity fit (K). */
  private static final double IAPWS_MIN_TEMPERATURE_K = 275.15;
  /** Upper temperature of the existing water-viscosity fit (K). */
  private static final double IAPWS_MAX_TEMPERATURE_K = 573.15;
  /** Lower pressure of the existing water-viscosity fit (bara). */
  private static final double IAPWS_MIN_PRESSURE_BARA = 1.0;
  /** Upper pressure of the existing water-viscosity fit (bara). */
  private static final double IAPWS_MAX_PRESSURE_BARA = 700.0;
  /** Critical pressure of ordinary water (MPa). */
  private static final double WATER_CRITICAL_PRESSURE_MPA = 22.064;

  /** Water-property basis. */
  public enum PvtMode {
    /** Caller-provided constant viscosity and formation-volume factor. */
    CONFIGURED_CONSTANT,
    /** IF97 specific volume and NeqSim's IAPWS-95-fitted viscosity correlation. */
    IAPWS_IF97_PURE_WATER
  }

  private final double permeabilityMd;
  private final double thicknessM;
  private final double skinFactor;
  private final double drainageRadiusM;
  private final double wellboreRadiusM;
  private final double temperatureK;
  private final double configuredViscosityCp;
  private final double configuredFormationVolumeFactor;
  private final PvtMode pvtMode;

  /**
   * Creates a configured-constant radial-Darcy water law.
   *
   * @param permeabilityMd formation permeability (mD)
   * @param thicknessM completed thickness (m)
   * @param skinFactor dimensionless skin factor
   * @param drainageRadiusM drainage radius (m)
   * @param wellboreRadiusM wellbore radius (m)
   * @param viscosityCp water viscosity at the reservoir boundary (cP)
   * @param formationVolumeFactor reservoir m3/Sm3
   * @return configured water-injectivity law
   */
  public static RadialDarcyWaterInjectivity configured(double permeabilityMd, double thicknessM, double skinFactor,
      double drainageRadiusM, double wellboreRadiusM, double viscosityCp, double formationVolumeFactor) {
    return new RadialDarcyWaterInjectivity(permeabilityMd, thicknessM, skinFactor, drainageRadiusM, wellboreRadiusM,
        Double.NaN, viscosityCp, formationVolumeFactor, PvtMode.CONFIGURED_CONSTANT);
  }

  /**
   * Creates an IAPWS pure-water radial-Darcy law.
   *
   * @param permeabilityMd formation permeability (mD)
   * @param thicknessM completed thickness (m)
   * @param skinFactor dimensionless skin factor
   * @param drainageRadiusM drainage radius (m)
   * @param wellboreRadiusM wellbore radius (m)
   * @param temperatureK reservoir temperature (K)
   * @return pure-water injectivity law
   */
  public static RadialDarcyWaterInjectivity iapwsPureWater(double permeabilityMd, double thicknessM, double skinFactor,
      double drainageRadiusM, double wellboreRadiusM, double temperatureK) {
    return new RadialDarcyWaterInjectivity(permeabilityMd, thicknessM, skinFactor, drainageRadiusM, wellboreRadiusM,
        temperatureK, Double.NaN, Double.NaN, PvtMode.IAPWS_IF97_PURE_WATER);
  }

  /**
   * Creates an immutable radial-Darcy law.
   *
   * @param permeabilityMd formation permeability (mD)
   * @param thicknessM completed thickness (m)
   * @param skinFactor dimensionless skin factor
   * @param drainageRadiusM drainage radius (m)
   * @param wellboreRadiusM wellbore radius (m)
   * @param temperatureK reservoir temperature (K), when required
   * @param configuredViscosityCp configured viscosity (cP), when required
   * @param configuredFormationVolumeFactor configured reservoir m3/Sm3, when required
   * @param pvtMode water-property basis
   */
  private RadialDarcyWaterInjectivity(double permeabilityMd, double thicknessM, double skinFactor,
      double drainageRadiusM, double wellboreRadiusM, double temperatureK, double configuredViscosityCp,
      double configuredFormationVolumeFactor, PvtMode pvtMode) {
    if (!Double.isFinite(permeabilityMd) || permeabilityMd < 0.0 || !Double.isFinite(thicknessM) || thicknessM <= 0.0
        || !Double.isFinite(skinFactor) || !Double.isFinite(drainageRadiusM) || !Double.isFinite(wellboreRadiusM)
        || wellboreRadiusM <= 0.0 || drainageRadiusM <= wellboreRadiusM
        || Math.log(drainageRadiusM / wellboreRadiusM) + skinFactor <= 0.0 || pvtMode == null) {
      throw new IllegalArgumentException("Radial-Darcy water injectivity requires physical geometry, "
          + "non-negative permeability and a positive radial-flow denominator");
    }
    if (pvtMode == PvtMode.CONFIGURED_CONSTANT
        && (!Double.isFinite(configuredViscosityCp) || configuredViscosityCp <= 0.0
            || !Double.isFinite(configuredFormationVolumeFactor) || configuredFormationVolumeFactor <= 0.0)) {
      throw new IllegalArgumentException("Configured water PVT requires positive finite viscosity and volume factor");
    }
    if (pvtMode == PvtMode.IAPWS_IF97_PURE_WATER && (!Double.isFinite(temperatureK)
        || temperatureK < IAPWS_MIN_TEMPERATURE_K || temperatureK > IAPWS_MAX_TEMPERATURE_K)) {
      throw new IllegalArgumentException("IAPWS pure-water injectivity requires 275.15-573.15 K");
    }
    this.permeabilityMd = permeabilityMd;
    this.thicknessM = thicknessM;
    this.skinFactor = skinFactor;
    this.drainageRadiusM = drainageRadiusM;
    this.wellboreRadiusM = wellboreRadiusM;
    this.temperatureK = temperatureK;
    this.configuredViscosityCp = configuredViscosityCp;
    this.configuredFormationVolumeFactor = configuredFormationVolumeFactor;
    this.pvtMode = pvtMode;
  }

  /**
   * Evaluates rate and property evidence at one reservoir/BHP boundary.
   *
   * @param reservoirPressureBara reservoir pressure (bara)
   * @param bottomholePressureBara bottom-hole injection pressure (bara)
   * @return immutable evaluation evidence
   */
  public Result evaluate(double reservoirPressureBara, double bottomholePressureBara) {
    if (!Double.isFinite(reservoirPressureBara) || reservoirPressureBara <= 0.0
        || !Double.isFinite(bottomholePressureBara) || bottomholePressureBara <= 0.0) {
      throw new IllegalArgumentException("Radial-Darcy injection pressures must be positive and finite");
    }
    double viscosityCp = configuredViscosityCp;
    double formationVolumeFactor = configuredFormationVolumeFactor;
    double standardDensityKgPerSm3 = Double.NaN;
    if (pvtMode == PvtMode.IAPWS_IF97_PURE_WATER) {
      validateIapwsState(reservoirPressureBara);
      double reservoirSpecificVolume = Iapws_if97.v_pt(reservoirPressureBara / 10.0, temperatureK);
      double standardSpecificVolume = Iapws_if97.v_pt(STANDARD_PRESSURE_BARA / 10.0, STANDARD_TEMPERATURE_K);
      viscosityCp = Viscosity.calcWaterViscosity(temperatureK, reservoirPressureBara);
      formationVolumeFactor = reservoirSpecificVolume / standardSpecificVolume;
      standardDensityKgPerSm3 = 1.0 / standardSpecificVolume;
    }
    if (!Double.isFinite(viscosityCp) || viscosityCp <= 0.0 || !Double.isFinite(formationVolumeFactor)
        || formationVolumeFactor <= 0.0) {
      throw new IllegalArgumentException("Water PVT evaluation returned nonphysical properties");
    }
    double denominator = viscosityCp * 1.0e-3 * formationVolumeFactor
        * (Math.log(drainageRadiusM / wellboreRadiusM) + skinFactor);
    double injectivitySm3PerDayPerBar = 2.0 * Math.PI * permeabilityMd * MILLIDARCY_TO_SQUARE_METRE * thicknessM
        / denominator * SECONDS_PER_DAY * PASCALS_PER_BAR;
    double standardVolumeRateSm3PerDay = injectivitySm3PerDayPerBar
        * Math.max(bottomholePressureBara - reservoirPressureBara, 0.0);
    if (!Double.isFinite(injectivitySm3PerDayPerBar) || injectivitySm3PerDayPerBar < 0.0
        || !Double.isFinite(standardVolumeRateSm3PerDay) || standardVolumeRateSm3PerDay < 0.0) {
      throw new IllegalArgumentException("Radial-Darcy injection evaluation returned a nonphysical rate");
    }
    return new Result(pvtMode, temperatureK, reservoirPressureBara, bottomholePressureBara, viscosityCp,
        formationVolumeFactor, standardDensityKgPerSm3, injectivitySm3PerDayPerBar, standardVolumeRateSm3PerDay);
  }

  /**
   * Gets the selected PVT basis.
   *
   * @return PVT basis
   */
  public PvtMode getPvtMode() {
    return pvtMode;
  }

  /**
   * Validates the declared IF97 compressed-liquid state.
   *
   * @param pressureBara reservoir pressure (bara)
   */
  private void validateIapwsState(double pressureBara) {
    if (pressureBara < IAPWS_MIN_PRESSURE_BARA || pressureBara > IAPWS_MAX_PRESSURE_BARA) {
      throw new IllegalArgumentException("IAPWS pure-water injectivity requires 1-700 bara");
    }
    double pressureMPa = pressureBara / 10.0;
    if (pressureMPa <= WATER_CRITICAL_PRESSURE_MPA) {
      double saturationTemperature = Iapws_if97.T4_p(pressureMPa);
      if (!Double.isFinite(saturationTemperature) || temperatureK > saturationTemperature) {
        throw new IllegalArgumentException("IAPWS pure-water injectivity requires a Region-1 liquid state");
      }
    }
  }

  /**
   * Immutable pressure-driven injectivity evidence.
   *
   * @author ESOL
   * @version 1.0
   */
  public static final class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;
    private final PvtMode pvtMode;
    private final double temperatureK;
    private final double reservoirPressureBara;
    private final double bottomholePressureBara;
    private final double viscosityCp;
    private final double formationVolumeFactor;
    private final double standardDensityKgPerSm3;
    private final double injectivitySm3PerDayPerBar;
    private final double standardVolumeRateSm3PerDay;

    /**
     * Creates immutable evaluation evidence.
     *
     * @param pvtMode property basis
     * @param temperatureK property-evaluation temperature (K), or NaN for configured constants
     * @param reservoirPressureBara reservoir pressure (bara)
     * @param bottomholePressureBara bottom-hole pressure (bara)
     * @param viscosityCp viscosity (cP)
     * @param formationVolumeFactor reservoir m3/Sm3
     * @param standardDensityKgPerSm3 standard density (kg/Sm3), or NaN for configured volume-only evidence
     * @param injectivitySm3PerDayPerBar standard-volume injectivity index
     * @param standardVolumeRateSm3PerDay standard-volume rate
     */
    private Result(PvtMode pvtMode, double temperatureK, double reservoirPressureBara, double bottomholePressureBara,
        double viscosityCp, double formationVolumeFactor, double standardDensityKgPerSm3,
        double injectivitySm3PerDayPerBar, double standardVolumeRateSm3PerDay) {
      this.pvtMode = pvtMode;
      this.temperatureK = temperatureK;
      this.reservoirPressureBara = reservoirPressureBara;
      this.bottomholePressureBara = bottomholePressureBara;
      this.viscosityCp = viscosityCp;
      this.formationVolumeFactor = formationVolumeFactor;
      this.standardDensityKgPerSm3 = standardDensityKgPerSm3;
      this.injectivitySm3PerDayPerBar = injectivitySm3PerDayPerBar;
      this.standardVolumeRateSm3PerDay = standardVolumeRateSm3PerDay;
    }

    /** @return property basis */
    public PvtMode getPvtMode() {
      return pvtMode;
    }

    /** @return property-evaluation temperature (K), or NaN for configured constants */
    public double getTemperatureK() {
      return temperatureK;
    }

    /** @return reservoir pressure (bara) */
    public double getReservoirPressureBara() {
      return reservoirPressureBara;
    }

    /** @return bottom-hole pressure (bara) */
    public double getBottomholePressureBara() {
      return bottomholePressureBara;
    }

    /** @return viscosity (cP) */
    public double getViscosityCp() {
      return viscosityCp;
    }

    /** @return formation-volume factor (reservoir m3/Sm3) */
    public double getFormationVolumeFactor() {
      return formationVolumeFactor;
    }

    /** @return standard density (kg/Sm3), or NaN for configured volume-only evidence */
    public double getStandardDensityKgPerSm3() {
      return standardDensityKgPerSm3;
    }

    /** @return standard-volume injectivity index (Sm3/day/bar) */
    public double getInjectivitySm3PerDayPerBar() {
      return injectivitySm3PerDayPerBar;
    }

    /** @return standard-volume rate (Sm3/day) */
    public double getStandardVolumeRateSm3PerDay() {
      return standardVolumeRateSm3PerDay;
    }
  }
}
