package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.Serializable;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Entrainment and deposition model for droplet exchange between phases.
 *
 * <p>
 * Models the exchange of liquid droplets between the liquid film and the gas core in annular and
 * mist flow regimes. Uses correlations for:
 * </p>
 * <ul>
 * <li>Atomization rate from the liquid film</li>
 * <li>Deposition rate onto the liquid film</li>
 * <li>Droplet size distribution</li>
 * <li>Critical conditions for onset of entrainment</li>
 * </ul>
 *
 * <h2>Correlations</h2>
 * <ul>
 * <li><b>Entrainment Rate:</b> Ishii-Mishima (1989), Pan-Hanratty (2002)</li>
 * <li><b>Deposition Rate:</b> McCoy-Hanratty (1977), Particle relaxation model</li>
 * <li><b>Droplet Size:</b> Tatterson correlation, Weber number based</li>
 * <li><b>Onset:</b> Ishii-Grolmes criterion for critical film Reynolds number</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class EntrainmentDeposition implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Model for entrainment rate calculation. */
  public enum EntrainmentModel {
    /** Ishii-Mishima correlation. */
    ISHII_MISHIMA,
    /** Pan-Hanratty correlation. */
    PAN_HANRATTY,
    /** Oliemans-Pots-Trupe correlation. */
    OLIEMANS
  }

  /** Model for deposition rate calculation. */
  public enum DepositionModel {
    /** McCoy-Hanratty diffusion model. */
    MCCOY_HANRATTY,
    /** Particle relaxation time model. */
    RELAXATION,
    /** Cousins model. */
    COUSINS
  }

  /** Current entrainment model. */
  private EntrainmentModel entrainmentModel = EntrainmentModel.ISHII_MISHIMA;

  /** Current deposition model. */
  private DepositionModel depositionModel = DepositionModel.MCCOY_HANRATTY;

  /** Critical Weber number for entrainment onset. */
  private double criticalWeber = 13.0;

  /** Critical film Reynolds number for entrainment. */
  private double criticalReFilm = 160.0;

  /**
   * Result container for entrainment/deposition calculations.
   */
  public static class EntrainmentResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Entrainment rate (kg/m²/s). */
    public double entrainmentRate;

    /** Deposition rate (kg/m²/s). */
    public double depositionRate;

    /** Net mass transfer rate, positive when entrainment &gt; deposition (kg/m²/s). */
    public double netTransferRate;

    /** Entrainment fraction (fraction of liquid entrained). */
    public double entrainmentFraction;

    /** Sauter mean diameter of droplets (m). */
    public double dropletDiameter;

    /** Droplet concentration in gas core (kg/m³). */
    public double dropletConcentration;

    /** Film Reynolds number. */
    public double filmReynoldsNumber;

    /** Whether entrainment is occurring. */
    public boolean isEntraining;
  }

  /**
   * Default constructor.
   */
  public EntrainmentDeposition() {}

  /**
   * Constructor with model specification.
   *
   * @param entrainmentModel Entrainment model to use
   * @param depositionModel Deposition model to use
   */
  public EntrainmentDeposition(EntrainmentModel entrainmentModel, DepositionModel depositionModel) {
    this.entrainmentModel = entrainmentModel;
    this.depositionModel = depositionModel;
  }

  /**
   * Calculate entrainment and deposition rates.
   *
   * @param flowRegime Current flow regime
   * @param gasVelocity Superficial gas velocity (m/s)
   * @param liquidVelocity Superficial liquid velocity (m/s)
   * @param gasDensity Gas density (kg/m³)
   * @param liquidDensity Liquid density (kg/m³)
   * @param gasViscosity Gas dynamic viscosity (Pa·s)
   * @param liquidViscosity Liquid dynamic viscosity (Pa·s)
   * @param surfaceTension Surface tension (N/m)
   * @param diameter Pipe diameter (m)
   * @param liquidHoldup Liquid holdup fraction
   * @return EntrainmentResult with rates and droplet properties
   */
  public EntrainmentResult calculate(FlowRegime flowRegime, double gasVelocity,
      double liquidVelocity, double gasDensity, double liquidDensity, double gasViscosity,
      double liquidViscosity, double surfaceTension, double diameter, double liquidHoldup) {

    EntrainmentResult result = new EntrainmentResult();

    // Only calculate for annular/mist regimes
    if (flowRegime != FlowRegime.ANNULAR && flowRegime != FlowRegime.MIST) {
      result.entrainmentRate = 0.0;
      result.depositionRate = 0.0;
      result.netTransferRate = 0.0;
      result.entrainmentFraction = 0.0;
      result.isEntraining = false;
      return result;
    }

    // Calculate film thickness from holdup (approximate for annular flow)
    double filmThickness = estimateFilmThickness(diameter, liquidHoldup);

    // Film Reynolds number
    double filmVelocity = liquidVelocity / liquidHoldup;
    double reFilm = liquidDensity * filmVelocity * filmThickness / liquidViscosity;
    result.filmReynoldsNumber = reFilm;

    // Check if entrainment is occurring
    result.isEntraining = isEntrainmentActive(gasVelocity, gasDensity, liquidDensity,
        surfaceTension, diameter, reFilm);

    if (result.isEntraining) {
      // Calculate entrainment rate
      result.entrainmentRate = calculateEntrainmentRate(gasVelocity, liquidVelocity, gasDensity,
          liquidDensity, gasViscosity, liquidViscosity, surfaceTension, diameter, filmThickness);

      // Calculate entrainment fraction
      result.entrainmentFraction = calculateEntrainmentFraction(gasVelocity, gasDensity,
          liquidDensity, liquidViscosity, surfaceTension, diameter, reFilm);
    } else {
      result.entrainmentRate = 0.0;
      result.entrainmentFraction = 0.0;
    }

    // Calculate droplet diameter
    result.dropletDiameter = calculateDropletDiameter(gasVelocity, gasDensity, surfaceTension);

    // Estimate droplet concentration
    result.dropletConcentration = estimateDropletConcentration(liquidVelocity, liquidDensity,
        liquidHoldup, result.entrainmentFraction, gasVelocity, diameter);

    // Calculate deposition rate
    result.depositionRate = calculateDepositionRate(result.dropletConcentration,
        result.dropletDiameter, gasDensity, gasViscosity, liquidDensity, gasVelocity, diameter);

    // Net transfer rate
    result.netTransferRate = result.entrainmentRate - result.depositionRate;

    return result;
  }

  /**
   * Check if entrainment is active using Ishii-Grolmes criterion.
   *
   * @param gasVelocity gas velocity [m/s]
   * @param gasDensity gas density [kg/m³]
   * @param liquidDensity liquid density [kg/m³]
   * @param surfaceTension surface tension [N/m]
   * @param diameter pipe diameter [m]
   * @param reFilm film Reynolds number
   * @return true if entrainment is active
   */
  private boolean isEntrainmentActive(double gasVelocity, double gasDensity, double liquidDensity,
      double surfaceTension, double diameter, double reFilm) {

    // Weber number criterion
    double we = gasDensity * gasVelocity * gasVelocity * diameter / surfaceTension;

    // Film Reynolds number criterion
    return we > criticalWeber && reFilm > criticalReFilm;
  }

  /**
   * Calculate entrainment rate using selected model.
   *
   * @param gasVelocity gas velocity [m/s]
   * @param liquidVelocity liquid velocity [m/s]
   * @param gasDensity gas density [kg/m³]
   * @param liquidDensity liquid density [kg/m³]
   * @param gasViscosity gas viscosity [Pa·s]
   * @param liquidViscosity liquid viscosity [Pa·s]
   * @param surfaceTension surface tension [N/m]
   * @param diameter pipe diameter [m]
   * @param filmThickness film thickness [m]
   * @return entrainment rate [kg/(m²·s)]
   */
  private double calculateEntrainmentRate(double gasVelocity, double liquidVelocity,
      double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity,
      double surfaceTension, double diameter, double filmThickness) {

    switch (entrainmentModel) {
      case ISHII_MISHIMA:
        return entrainmentIshiiMishima(gasVelocity, gasDensity, liquidDensity, liquidViscosity,
            surfaceTension, diameter);

      case PAN_HANRATTY:
        return entrainmentPanHanratty(gasVelocity, liquidVelocity, gasDensity, liquidDensity,
            gasViscosity, liquidViscosity, surfaceTension, diameter);

      case OLIEMANS:
        return entrainmentOliemans(gasVelocity, gasDensity, liquidDensity, liquidViscosity,
            surfaceTension, diameter);

      default:
        return entrainmentIshiiMishima(gasVelocity, gasDensity, liquidDensity, liquidViscosity,
            surfaceTension, diameter);
    }
  }

  /**
   * Ishii-Mishima entrainment rate correlation.
   *
   * @param gasVelocity the velocity of the gas phase
   * @param gasDensity the density of the gas phase
   * @param liquidDensity the density of the liquid phase
   * @param liquidViscosity the dynamic viscosity of the liquid phase
   * @param surfaceTension the surface tension between phases
   * @param diameter the pipe diameter
   * @return the entrainment rate according to Ishii-Mishima correlation
   */
  private double entrainmentIshiiMishima(double gasVelocity, double gasDensity,
      double liquidDensity, double liquidViscosity, double surfaceTension, double diameter) {

    // Dimensionless groups
    double densityRatio = gasDensity / liquidDensity;
    double re = gasDensity * gasVelocity * diameter / (liquidViscosity * Math.sqrt(densityRatio));
    double we = gasDensity * gasVelocity * gasVelocity * diameter / surfaceTension;

    // Ishii-Mishima correlation
    double entrainmentRate = 0.0;
    if (we > criticalWeber) {
      double coeffA = 6.6e-7;
      entrainmentRate = coeffA * gasDensity * gasVelocity * Math.pow(re, 0.25) * Math.pow(we, 0.25);
    }

    return Math.max(0.0, entrainmentRate);
  }

  /**
   * Pan-Hanratty entrainment rate correlation.
   *
   * @param gasVelocity the velocity of the gas phase
   * @param liquidVelocity the velocity of the liquid phase
   * @param gasDensity the density of the gas phase
   * @param liquidDensity the density of the liquid phase
   * @param gasViscosity the dynamic viscosity of the gas phase
   * @param liquidViscosity the dynamic viscosity of the liquid phase
   * @param surfaceTension the surface tension between phases
   * @param diameter the pipe diameter
   * @return the entrainment rate according to Pan-Hanratty correlation
   */
  private double entrainmentPanHanratty(double gasVelocity, double liquidVelocity,
      double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity,
      double surfaceTension, double diameter) {

    double reG = gasDensity * gasVelocity * diameter / gasViscosity;
    double weG = gasDensity * gasVelocity * gasVelocity * diameter / surfaceTension;

    // Characteristic velocity
    double uStar = gasVelocity * Math.sqrt(0.005); // Approximate friction velocity

    // Pan-Hanratty form
    double coeffK = 3.0e-6;
    double entrainmentRate =
        coeffK * liquidDensity * uStar * Math.pow(reG, 0.25) * Math.pow(weG / criticalWeber, 0.5);

    return Math.max(0.0, entrainmentRate);
  }

  /**
   * Oliemans entrainment rate correlation.
   *
   * @param gasVelocity gas velocity (m/s)
   * @param gasDensity gas density (kg/m³)
   * @param liquidDensity liquid density (kg/m³)
   * @param liquidViscosity liquid viscosity (Pa·s)
   * @param surfaceTension surface tension (N/m)
   * @param diameter pipe diameter (m)
   * @return entrainment rate (kg/m²/s)
   */
  private double entrainmentOliemans(double gasVelocity, double gasDensity, double liquidDensity,
      double liquidViscosity, double surfaceTension, double diameter) {

    // Similar structure to Ishii-Mishima with different coefficients
    double we = gasDensity * gasVelocity * gasVelocity * diameter / surfaceTension;

    double entrainmentRate = 0.0;
    if (we > criticalWeber) {
      double coeffB = 1.0e-6;
      entrainmentRate = coeffB * gasDensity * gasVelocity * Math.pow(we - criticalWeber, 0.5);
    }

    return Math.max(0.0, entrainmentRate);
  }

  /**
   * Calculate equilibrium entrainment fraction.
   */
  private double calculateEntrainmentFraction(double gasVelocity, double gasDensity,
      double liquidDensity, double liquidViscosity, double surfaceTension, double diameter,
      double reFilm) {

    // Ishii-Mishima equilibrium entrainment fraction
    double viscosityNumber = liquidViscosity / Math.sqrt(liquidDensity * surfaceTension * diameter);

    // Modified Weber number
    double weCrit = criticalWeber;
    double we = gasDensity * gasVelocity * gasVelocity * diameter / surfaceTension;

    if (we <= weCrit) {
      return 0.0;
    }

    // Entrainment fraction correlation
    double eInf = Math.tanh(7.25e-7 * Math.pow(we, 1.25) * Math.pow(reFilm, 0.25));

    return Math.min(0.99, Math.max(0.0, eInf));
  }

  /**
   * Calculate deposition rate using selected model.
   */
  private double calculateDepositionRate(double dropletConcentration, double dropletDiameter,
      double gasDensity, double gasViscosity, double liquidDensity, double gasVelocity,
      double diameter) {

    switch (depositionModel) {
      case MCCOY_HANRATTY:
        return depositionMcCoyHanratty(dropletConcentration, dropletDiameter, gasDensity,
            gasViscosity, gasVelocity, diameter);

      case RELAXATION:
        return depositionRelaxation(dropletConcentration, dropletDiameter, gasDensity, gasViscosity,
            liquidDensity, gasVelocity, diameter);

      case COUSINS:
        return depositionCousins(dropletConcentration, dropletDiameter, gasVelocity, gasDensity,
            gasViscosity, diameter);

      default:
        return depositionMcCoyHanratty(dropletConcentration, dropletDiameter, gasDensity,
            gasViscosity, gasVelocity, diameter);
    }
  }

  /**
   * McCoy-Hanratty deposition rate correlation.
   */
  private double depositionMcCoyHanratty(double dropletConcentration, double dropletDiameter,
      double gasDensity, double gasViscosity, double gasVelocity, double diameter) {

    // Friction velocity
    double reG = gasDensity * gasVelocity * diameter / gasViscosity;
    double fG = 0.046 * Math.pow(reG, -0.2); // Blasius friction factor
    double uStar = gasVelocity * Math.sqrt(fG / 2.0);

    // Dimensionless droplet relaxation time
    double tauPlus = dropletConcentration * dropletDiameter * dropletDiameter * uStar * uStar
        / (18.0 * gasViscosity * gasViscosity / gasDensity);

    // Deposition velocity (McCoy-Hanratty)
    double kd;
    if (tauPlus < 0.2) {
      // Diffusion regime
      kd = 0.0003 * uStar;
    } else if (tauPlus < 20.0) {
      // Intermediate regime
      kd = 0.0003 * uStar * Math.pow(tauPlus, 2);
    } else {
      // Inertia regime
      kd = 0.1 * uStar;
    }

    // Deposition rate (kg/m²/s)
    return kd * dropletConcentration;
  }

  /**
   * Particle relaxation time deposition model.
   */
  private double depositionRelaxation(double dropletConcentration, double dropletDiameter,
      double gasDensity, double gasViscosity, double liquidDensity, double gasVelocity,
      double diameter) {

    // Particle relaxation time
    double tauP = liquidDensity * dropletDiameter * dropletDiameter / (18.0 * gasViscosity);

    // Turbulent timescale
    double reG = gasDensity * gasVelocity * diameter / gasViscosity;
    double lTurb = 0.1 * diameter; // Turbulent length scale
    double uTurb = 0.05 * gasVelocity; // Turbulent velocity scale
    double tauT = lTurb / uTurb;

    // Stokes number
    double st = tauP / tauT;

    // Deposition velocity based on Stokes number
    double kd;
    if (st < 0.1) {
      kd = uTurb * st;
    } else {
      kd = uTurb * Math.min(1.0, Math.sqrt(st));
    }

    return kd * dropletConcentration;
  }

  /**
   * Cousins deposition model.
   */
  private double depositionCousins(double dropletConcentration, double dropletDiameter,
      double gasVelocity, double gasDensity, double gasViscosity, double diameter) {

    // Simple diffusion-based model
    double reG = gasDensity * gasVelocity * diameter / gasViscosity;
    double sc = gasViscosity / (gasDensity * 1e-5); // Schmidt number, assume diffusivity ~1e-5

    // Mass transfer coefficient
    double sh = 0.023 * Math.pow(reG, 0.8) * Math.pow(sc, 0.33);
    double kd = sh * 1e-5 / diameter;

    return kd * dropletConcentration;
  }

  /**
   * Calculate Sauter mean diameter of droplets.
   *
   * @param gasVelocity Gas phase velocity (m/s)
   * @param gasDensity Gas phase density (kg/m³)
   * @param surfaceTension Surface tension (N/m)
   * @return Sauter mean diameter (m)
   */
  private double calculateDropletDiameter(double gasVelocity, double gasDensity,
      double surfaceTension) {

    // Weber number based correlation (Tatterson)
    double weCrit = 13.0;

    // Maximum stable droplet diameter
    double dMax = weCrit * surfaceTension / (gasDensity * gasVelocity * gasVelocity);

    // Sauter mean diameter is typically 0.5-0.7 of max
    double d32 = 0.6 * dMax;

    // Limit to reasonable range
    return Math.max(1e-6, Math.min(1e-3, d32));
  }

  /**
   * Estimate film thickness from holdup.
   *
   * @param diameter pipe diameter [m]
   * @param liquidHoldup liquid holdup fraction
   * @return film thickness [m]
   */
  private double estimateFilmThickness(double diameter, double liquidHoldup) {
    // For annular flow, approximate film thickness
    // Assuming uniform film: A_film = pi * D * delta = alpha_L * pi * D^2 / 4
    // delta = alpha_L * D / 4

    double filmThickness = liquidHoldup * diameter / 4.0;

    // Limit to reasonable values
    return Math.max(1e-6, Math.min(diameter / 4.0, filmThickness));
  }

  /**
   * Estimate droplet concentration in gas core.
   */
  /**
   * Estimate droplet concentration in gas core.
   *
   * @param liquidVelocity liquid velocity [m/s]
   * @param liquidDensity liquid density [kg/m³]
   * @param liquidHoldup liquid holdup fraction
   * @param entrainmentFraction entrainment fraction
   * @param gasVelocity gas velocity [m/s]
   * @param diameter pipe diameter [m]
   * @return droplet concentration [kg/m³]
   */
  private double estimateDropletConcentration(double liquidVelocity, double liquidDensity,
      double liquidHoldup, double entrainmentFraction, double gasVelocity, double diameter) {

    // Mass flow rate of entrained liquid
    double liquidMassFlux = liquidDensity * liquidVelocity;
    double entrainedMassFlux = liquidMassFlux * entrainmentFraction;

    // Droplet concentration (mass per unit volume in gas core)
    double gasHoldup = 1.0 - liquidHoldup + liquidHoldup * entrainmentFraction;
    double dropletConc = entrainedMassFlux / gasVelocity;

    return Math.max(0.0, dropletConc);
  }

  // Getters and setters

  /**
   * Get current entrainment model.
   *
   * @return Entrainment model
   */
  public EntrainmentModel getEntrainmentModel() {
    return entrainmentModel;
  }

  /**
   * Set entrainment model.
   *
   * @param model Entrainment model to use
   */
  public void setEntrainmentModel(EntrainmentModel model) {
    this.entrainmentModel = model;
  }

  /**
   * Get current deposition model.
   *
   * @return Deposition model
   */
  public DepositionModel getDepositionModel() {
    return depositionModel;
  }

  /**
   * Set deposition model.
   *
   * @param model Deposition model to use
   */
  public void setDepositionModel(DepositionModel model) {
    this.depositionModel = model;
  }

  /**
   * Get critical Weber number.
   *
   * @return Critical Weber number
   */
  public double getCriticalWeber() {
    return criticalWeber;
  }

  /**
   * Set critical Weber number.
   *
   * @param criticalWeber Critical Weber number for entrainment onset
   */
  public void setCriticalWeber(double criticalWeber) {
    this.criticalWeber = criticalWeber;
  }

  /**
   * Get critical film Reynolds number.
   *
   * @return Critical film Reynolds number
   */
  public double getCriticalReFilm() {
    return criticalReFilm;
  }

  /**
   * Set critical film Reynolds number.
   *
   * @param criticalReFilm Critical film Reynolds number
   */
  public void setCriticalReFilm(double criticalReFilm) {
    this.criticalReFilm = criticalReFilm;
  }
}
