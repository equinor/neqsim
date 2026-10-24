package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Steady slug-unit closure for gas-liquid intermittent flow.
 *
 * <p>
 * A slug unit is an aerated liquid slug followed by a film zone in which a liquid film runs under (or, in steep upward
 * flow, around) an elongated gas bubble. The unit travels at the translational velocity {@code vT}. Following Dukler
 * and Hubbard (1975) and the equilibrium-film simplification of Taitel and Barnea (1990), the film holdup is the root
 * of the combined gas-film momentum balance written with velocities fixed by mass conservation relative to the moving
 * slug front. The slug fraction then follows from the unit liquid balance.
 * </p>
 *
 * <p>
 * The pressure gradient charges mixture wall friction to the slug body only and per-phase wall friction to the film
 * zone, plus gravity. In the frame moving with the slug front the unit is periodic, so the momentum flux entering a
 * unit equals the flux leaving it and the unit-averaged gradient is wall friction plus gravity (Taitel and Barnea
 * 1990). Dukler and Hubbard (1975) add the acceleration of the film liquid picked up at the slug front; that term is
 * reported in {@link Result#accelerationGradient}. How much of it is counted in {@link Result#frictionGradient} is set
 * with {@link #setFrontMixingLossWeight(double)} or {@link #setFrontMixingLossIncluded(boolean)}; by default an
 * OLGA-calibrated weight is used that is zero on slopes steeper than about 2 degrees and below Froude number 2.3.
 * Benchmarking against OLGA 2025.1 on a 10 in gas-oil line showed why: on a horizontal section wall shear alone gives
 * 61 Pa/m against 86 in OLGA and the front term (14 Pa/m) closes part of the gap, but on 4 degree up-slopes wall shear
 * plus gravity (229 Pa/m) already matches OLGA (233 Pa/m) and the front term there (135 Pa/m) is pure excess.
 * </p>
 *
 * <p>
 * The unit holdup follows from the liquid balance as {@code hs + (vB/vt)(1 - hs) - vsG/vt}, independent of the film
 * balance, so the slip of the whole unit is set by the translational velocity {@code vt} and the bubble velocity
 * {@code vB}. A 32-case OLGA 2025.1 steady sweep (8 to 16 in gas-oil, gas-oil-water, gas-condensate and wet-gas lines,
 * 30 to 100 bara) showed the Bendiksen value {@code vt = 1.2 vm} overstates the slip above Froude number 3: OLGA's unit
 * gas velocity falls from 1.25 vm at Froude 2 to about {@code 1.12 - 0.25 vsL/vm} times vm above Froude 4.5. Both
 * velocities are therefore scaled towards no-slip by {@link #setHighFroudeSlipReductionEnabled(boolean)} (on by
 * default, horizontal sections only), which removed a 20 to 40 per cent holdup over-prediction and gives the thinner
 * film (about 0.11 to 0.19) and larger slug fraction OLGA carries. When slugs cannot be sustained (slug fraction at or
 * below zero) the result is flagged {@link Result#stratified}; when the film vanishes the unit degenerates to the slug
 * body.
 * </p>
 *
 * <p>
 * Closures: slug-body holdup of Gregory, Nicholson and Aziz (1978); translational velocity of Bendiksen (1984);
 * dispersed-bubble velocity in the slug body with a Harmathy rise term; stable slug length of 32 D horizontal and 16 D
 * vertical (Barnea and Brauner 1985); Haaland wall friction; a constant interfacial Fanning factor of 0.014 in the film
 * zone (Taitel and Barnea 1990).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class SlugUnitModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  private static final double GRAVITY = 9.81;
  private static final double HORIZONTAL_SLUG_LENGTH_DIAMETERS = 32.0;
  private static final double VERTICAL_SLUG_LENGTH_DIAMETERS = 16.0;
  private static final double MINIMUM_FILM_HOLDUP = 1.0e-5;
  private static final int FILM_ROOT_SCAN_POINTS = 40;
  private static final double FILM_INTERFACIAL_DARCY_FRICTION = 4.0 * 0.014;
  private static final double STRATIFIED_GEOMETRY_LIMIT = Math.toRadians(45.0);
  private static final double ANNULAR_GEOMETRY_LIMIT = Math.toRadians(75.0);
  private static final double HIGH_FROUDE_START = 2.5;
  private static final double HIGH_FROUDE_END = 4.5;
  private static final double HIGH_FROUDE_UNIT_VELOCITY_RATIO = 1.12;
  private static final double HIGH_FROUDE_LIQUID_SLOPE = 0.25;
  private static final double FRONT_MIXING_SLOPE_LIMIT = 0.035;
  private static final double FRONT_MIXING_ONSET_FROUDE = 2.3;

  /** Reduce the unit slip towards no-slip at high mixture Froude number; default true. */
  private boolean highFroudeSlipReductionEnabled = true;

  /**
   * Enable or disable the high-Froude reduction of the unit gas-liquid velocity ratio.
   *
   * @param enabled true to scale the translational and bubble velocities towards no-slip as the mixture Froude number
   * rises (default); false for the plain Bendiksen coefficients
   */
  public void setHighFroudeSlipReductionEnabled(boolean enabled) {
    highFroudeSlipReductionEnabled = enabled;
  }

  /** @return true when the high-Froude slip reduction is applied */
  public boolean isHighFroudeSlipReductionEnabled() {
    return highFroudeSlipReductionEnabled;
  }

  /**
   * Front-mixing weight set explicitly; NaN selects the OLGA-calibrated horizontal weight of
   * {@link #automaticFrontMixingWeight(double, double)}.
   */
  private double frontMixingLossWeight = Double.NaN;

  /** Legacy serialized switch, retained so older readers preserve explicitly enabled front-mixing loss. */
  private boolean frontMixingLossIncluded;

  /**
   * Restore the explicit mixing-loss choice from models saved before weighted mixing losses were introduced.
   *
   * <p>
   * Legacy models retain their boolean choice and unscaled Bendiksen velocities. Newer streams retain fractional
   * weights, the automatic NaN selection and their configured high-Froude slip reduction.
   * </p>
   *
   * @param input serialized model input
   * @throws IOException if the model fields cannot be read
   * @throws ClassNotFoundException if a serialized field type is unavailable
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    ObjectInputStream.GetField fields = input.readFields();
    boolean legacyIncluded = fields.get("frontMixingLossIncluded", false);
    frontMixingLossWeight = fields.defaulted("frontMixingLossWeight") ? (legacyIncluded ? 1.0 : 0.0)
        : fields.get("frontMixingLossWeight", Double.NaN);
    frontMixingLossIncluded = frontMixingLossWeight == 1.0;
    highFroudeSlipReductionEnabled = fields.get("highFroudeSlipReductionEnabled", false);
  }

  /**
   * Choose whether the slug-front mixing loss is counted in {@link Result#frictionGradient}.
   *
   * @param included true to add the whole Dukler-Hubbard front acceleration term; false for the Taitel-Barnea
   * wall-shear-only unit gradient. Not calling this keeps the automatic horizontal weight.
   */
  public void setFrontMixingLossIncluded(boolean included) {
    setFrontMixingLossWeight(included ? 1.0 : 0.0);
  }

  /**
   * Set the share of the front-mixing loss counted in {@link Result#frictionGradient}.
   *
   * @param weight share between zero and one; NaN restores the automatic horizontal weight
   * @throws IllegalArgumentException if the weight is outside zero to one and not NaN
   */
  public void setFrontMixingLossWeight(double weight) {
    if (!Double.isNaN(weight) && (weight < 0.0 || weight > 1.0)) {
      throw new IllegalArgumentException("front mixing weight must be within [0, 1]: " + weight);
    }
    frontMixingLossWeight = weight;
    frontMixingLossIncluded = weight == 1.0;
  }

  /** @return true when the whole front-mixing loss is counted in the unit friction gradient */
  public boolean isFrontMixingLossIncluded() {
    return frontMixingLossIncluded;
  }

  /**
   * Horizontal front-mixing weight calibrated against OLGA 2025.1.
   *
   * <p>
   * Wall shear alone under-predicts the OLGA unit gradient on horizontal gas-oil lines at moderate Froude number (65
   * against 83 Pa/m at Froude 3.4) and the front term closes it, but on up-slopes wall shear plus gravity already
   * matches and the term is excess, so the weight is zero beyond about 2 degrees. It falls from 0.6 to 0.2 as the
   * Froude number rises from 4.5 to 7 because the unit approaches no-slip and the pickup loss weakens.
   * </p>
   *
   * @param froude mixture Froude number vm / sqrt(g D)
   * @param sinTheta sine of the pipe inclination
   * @return weight between zero and one
   */
  static double automaticFrontMixingWeight(double froude, double sinTheta) {
    double inclinationRamp = Math.max(0.0, 1.0 - Math.abs(sinTheta) / FRONT_MIXING_SLOPE_LIMIT);
    double onset = Math.max(0.0, Math.min(1.0, (froude - FRONT_MIXING_ONSET_FROUDE) / 0.7));
    double x = Math.max(0.0, Math.min(1.0, (froude - 4.5) / 2.5));
    return inclinationRamp * onset * (0.6 - 0.4 * x);
  }

  /** Slug-unit state for one set of superficial velocities. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    /** True when a slug unit with a positive slug fraction was found. */
    public boolean valid;
    /** True when slugs cannot be sustained and the film zone fills the unit. */
    public boolean stratified;
    /** Liquid holdup of the slug body. */
    public double slugHoldup;
    /** Liquid holdup of the equilibrium film. */
    public double filmHoldup;
    /** Slug length divided by unit length. */
    public double slugFraction;
    /** Unit-averaged liquid holdup. */
    public double unitHoldup;
    /** Translational velocity of the slug front, m/s. */
    public double translationalVelocity;
    /** Liquid velocity in the slug body, m/s. */
    public double slugLiquidVelocity;
    /** Film liquid velocity, m/s. */
    public double filmLiquidVelocity;
    /** Gas velocity in the film zone, m/s. */
    public double filmGasVelocity;
    /** Slug length, m. */
    public double slugLength;
    /** Unit-averaged pressure-loss gradient from friction, including the front-mixing loss when enabled, Pa/m. */
    public double frictionGradient;
    /** Unit-averaged wall-shear gradient only (slug body and film zone), Pa/m. */
    public double wallFrictionGradient;
    /** Pressure drop of the front mixing zone per unit length, Pa/m (Dukler-Hubbard acceleration term). */
    public double accelerationGradient;
    /** Unit-averaged hydrostatic gradient, Pa/m. */
    public double gravityGradient;

    /**
     * Unit-averaged pressure-loss gradient.
     *
     * @return friction plus gravity gradient, Pa/m
     */
    public double totalGradient() {
      return frictionGradient + gravityGradient;
    }
  }

  /**
   * Solve the slug unit.
   *
   * @param vsG superficial gas velocity, m/s, positive
   * @param vsL superficial liquid velocity, m/s, positive
   * @param rhoG gas density, kg/m3
   * @param rhoL liquid density, kg/m3
   * @param muG gas viscosity, Pa.s
   * @param muL liquid viscosity, Pa.s
   * @param sigma surface tension, N/m
   * @param diameter pipe inner diameter, m
   * @param roughness absolute wall roughness, m
   * @param inclination pipe inclination, radians, positive upward
   * @return slug-unit state; {@link Result#valid} is false when no unit exists
   */
  public Result solve(double vsG, double vsL, double rhoG, double rhoL, double muG, double muL, double sigma,
      double diameter, double roughness, double inclination) {
    Result result = new Result();
    if (!(vsG > 0.0) || !(vsL > 0.0) || !(rhoL > rhoG) || !(rhoG > 0.0) || !(diameter > 0.0) || !(muL > 0.0)
        || !(muG > 0.0)) {
      return result;
    }
    double vm = vsG + vsL;
    double sinTheta = Math.sin(inclination);
    double cosTheta = Math.cos(inclination);
    double gD = Math.sqrt(GRAVITY * diameter);

    double slugHoldup = 1.0 / (1.0 + Math.pow(vm / 8.66, 1.39));
    double froude = vm / gD;
    double c0;
    double drift;
    if (froude < 3.5) {
      c0 = 1.05 + 0.15 * sinTheta * sinTheta;
      drift = gD * (0.54 * cosTheta + 0.35 * sinTheta);
    } else {
      c0 = 1.2;
      drift = 0.35 * gD * sinTheta;
    }
    double translational = c0 * vm + drift;
    double riseVelocity = sigma > 0.0
        ? 1.53 * Math.pow(GRAVITY * sigma * (rhoL - rhoG) / (rhoL * rhoL), 0.25) * Math.sqrt(slugHoldup) * sinTheta
        : 0.0;
    double bubbleVelocity = 1.2 * vm + riseVelocity;
    double slipScale = highFroudeSlipScale(froude, vsL / vm, cosTheta);
    translational *= slipScale;
    bubbleVelocity *= slipScale;
    double slugLiquidVelocity = (vm - bubbleVelocity * (1.0 - slugHoldup)) / slugHoldup;

    result.slugHoldup = slugHoldup;
    result.translationalVelocity = translational;
    result.slugLiquidVelocity = slugLiquidVelocity;

    double low = MINIMUM_FILM_HOLDUP;
    double high = slugHoldup * (1.0 - 1.0e-9);
    double residualLow = filmResidual(low, result, bubbleVelocity, rhoG, rhoL, muG, muL, diameter, roughness,
        inclination);
    if (!Double.isFinite(residualLow)) {
      return result;
    }
    // The film balance can have three roots; the thin film is the physical one in gas-driven flow, so bracket the
    // first sign change scanning up from the thin end (Barnea and Taitel 1992).
    boolean bracketed = false;
    double ratio = Math.pow(high / low, 1.0 / FILM_ROOT_SCAN_POINTS);
    double previous = low;
    for (int point = 1; point <= FILM_ROOT_SCAN_POINTS; point++) {
      double trial = point == FILM_ROOT_SCAN_POINTS ? high : previous * ratio;
      double value = filmResidual(trial, result, bubbleVelocity, rhoG, rhoL, muG, muL, diameter, roughness,
          inclination);
      if (!Double.isFinite(value)) {
        return result;
      }
      if (Math.signum(value) != Math.signum(residualLow)) {
        low = previous;
        high = trial;
        bracketed = true;
        break;
      }
      previous = trial;
      residualLow = value;
    }
    if (!bracketed) {
      return result;
    }
    for (int iteration = 0; iteration < 100 && high - low > 1.0e-12; iteration++) {
      double middle = 0.5 * (low + high);
      double value = filmResidual(middle, result, bubbleVelocity, rhoG, rhoL, muG, muL, diameter, roughness,
          inclination);
      if (!Double.isFinite(value)) {
        return result;
      }
      if (Math.signum(value) == Math.signum(residualLow)) {
        low = middle;
        residualLow = value;
      } else {
        high = middle;
      }
    }
    double filmHoldup = 0.5 * (low + high);
    double filmLiquidVelocity = translational - (translational - slugLiquidVelocity) * slugHoldup / filmHoldup;
    double filmGasVelocity = translational - (translational - bubbleVelocity) * (1.0 - slugHoldup) / (1.0 - filmHoldup);
    double slugFraction = (vsL - filmLiquidVelocity * filmHoldup)
        / (slugLiquidVelocity * slugHoldup - filmLiquidVelocity * filmHoldup);

    result.filmHoldup = filmHoldup;
    result.filmLiquidVelocity = filmLiquidVelocity;
    result.filmGasVelocity = filmGasVelocity;
    if (!Double.isFinite(slugFraction) || slugFraction <= 0.0) {
      result.stratified = true;
      return result;
    }
    slugFraction = Math.min(1.0, slugFraction);
    result.slugFraction = slugFraction;
    result.unitHoldup = slugFraction * slugHoldup + (1.0 - slugFraction) * filmHoldup;

    double steepness = Math.abs(sinTheta);
    double slugLengthDiameters = HORIZONTAL_SLUG_LENGTH_DIAMETERS
        + (VERTICAL_SLUG_LENGTH_DIAMETERS - HORIZONTAL_SLUG_LENGTH_DIAMETERS) * steepness * steepness;
    result.slugLength = slugLengthDiameters * diameter;

    double area = Math.PI * diameter * diameter / 4.0;
    double slugDensity = slugHoldup * rhoL + (1.0 - slugHoldup) * rhoG;
    double slugViscosity = slugHoldup * muL + (1.0 - slugHoldup) * muG;
    double slugFriction = darcyFriction(slugDensity * vm * diameter / slugViscosity, roughness / diameter);
    double slugWallGradient = slugFriction * slugDensity * vm * Math.abs(vm) / (2.0 * diameter);

    FilmGeometry film = filmGeometry(filmHoldup, diameter, inclination);
    double filmWallForce = 0.0;
    if (film.liquidPerimeter > 0.0) {
      double fL = darcyFriction(rhoL * Math.abs(filmLiquidVelocity) * film.liquidHydraulicDiameter / muL,
          roughness / diameter);
      filmWallForce += fL * rhoL * filmLiquidVelocity * Math.abs(filmLiquidVelocity) / 8.0 * film.liquidPerimeter;
    }
    if (film.gasPerimeter > 0.0) {
      double fG = darcyFriction(rhoG * Math.abs(filmGasVelocity) * film.gasHydraulicDiameter / muG,
          roughness / diameter);
      filmWallForce += fG * rhoG * filmGasVelocity * Math.abs(filmGasVelocity) / 8.0 * film.gasPerimeter;
    }
    result.wallFrictionGradient = slugFraction * slugWallGradient + (1.0 - slugFraction) * filmWallForce / area;

    double unitLength = result.slugLength / slugFraction;
    result.accelerationGradient = slugFraction < 1.0
        ? rhoL * filmHoldup * (translational - filmLiquidVelocity) * (slugLiquidVelocity - filmLiquidVelocity)
            / unitLength
        : 0.0;
    double weight = Double.isNaN(frontMixingLossWeight) ? automaticFrontMixingWeight(froude, sinTheta)
        : frontMixingLossWeight;
    result.frictionGradient = result.wallFrictionGradient
        + (weight > 0.0 && Double.isFinite(result.accelerationGradient)
            ? weight * Math.max(0.0, result.accelerationGradient)
            : 0.0);
    double filmDensity = filmHoldup * rhoL + (1.0 - filmHoldup) * rhoG;
    result.gravityGradient = (slugFraction * slugDensity + (1.0 - slugFraction) * filmDensity) * GRAVITY * sinTheta;
    result.valid = true;
    return result;
  }

  /**
   * Scale factor on the translational and bubble velocities at high mixture Froude number.
   *
   * <p>
   * Bendiksen's distribution coefficient of 1.2 is a developed-turbulent-profile value for the elongated bubble. In a
   * horizontal or near-horizontal line the gas-liquid velocity ratio of the whole unit falls towards the no-slip limit
   * as the mixing rate rises. OLGA 2025.1 steady runs on 8 to 12 in gas-oil, gas-oil-water and gas-condensate lines
   * (mixture Froude number 2 to 7, liquid fraction 0.1 to 0.5) give a unit gas velocity of 1.25 vm near Froude 2, and
   * about 1.12 - 0.25 liquid fraction times vm above Froude 4.5, against 1.2 vm without this scaling. The scaling is
   * applied with cos(theta) squared so a vertical slug unit keeps the Nicklin value.
   * </p>
   *
   * @param froude mixture Froude number vm / sqrt(g D)
   * @param liquidFraction no-slip liquid fraction vsL / vm
   * @param cosTheta cosine of the pipe inclination
   * @return scale factor in (0, 1]
   */
  private double highFroudeSlipScale(double froude, double liquidFraction, double cosTheta) {
    if (!highFroudeSlipReductionEnabled) {
      return 1.0;
    }
    double limit = Math.max(1.0, HIGH_FROUDE_UNIT_VELOCITY_RATIO - HIGH_FROUDE_LIQUID_SLOPE * liquidFraction) / 1.2;
    double x = Math.max(0.0, Math.min(1.0, (froude - HIGH_FROUDE_START) / (HIGH_FROUDE_END - HIGH_FROUDE_START)));
    double ramp = x * x * (3.0 - 2.0 * x);
    return 1.0 - (1.0 - Math.min(1.0, limit)) * ramp * cosTheta * cosTheta;
  }

  /**
   * Gas minus film-liquid pressure-gradient requirement in the film zone at a trial film holdup.
   *
   * @param filmHoldup trial film holdup
   * @param unit partially filled result holding slug-body state and translational velocity
   * @param bubbleVelocity dispersed-bubble velocity in the slug body, m/s
   * @param rhoG gas density, kg/m3
   * @param rhoL liquid density, kg/m3
   * @param muG gas viscosity, Pa.s
   * @param muL liquid viscosity, Pa.s
   * @param diameter pipe diameter, m
   * @param roughness wall roughness, m
   * @param inclination inclination, radians
   * @return momentum residual, Pa/m
   */
  private double filmResidual(double filmHoldup, Result unit, double bubbleVelocity, double rhoG, double rhoL,
      double muG, double muL, double diameter, double roughness, double inclination) {
    double vt = unit.translationalVelocity;
    double vLF = vt - (vt - unit.slugLiquidVelocity) * unit.slugHoldup / filmHoldup;
    double vGF = vt - (vt - bubbleVelocity) * (1.0 - unit.slugHoldup) / (1.0 - filmHoldup);
    FilmGeometry film = filmGeometry(filmHoldup, diameter, inclination);
    double area = Math.PI * diameter * diameter / 4.0;
    double liquidArea = filmHoldup * area;
    double gasArea = (1.0 - filmHoldup) * area;

    double fL = darcyFriction(rhoL * Math.abs(vLF) * film.liquidHydraulicDiameter / muL, roughness / diameter);
    double fG = darcyFriction(rhoG * Math.abs(vGF) * film.gasHydraulicDiameter / muG, roughness / diameter);
    // The film behind a slug is not a developed wave field; Taitel and Barnea (1990) use a constant interfacial
    // Fanning factor of 0.014 (Darcy 0.056) there instead of a wave-enhancement correlation.
    double fI = Math.max(fG, FILM_INTERFACIAL_DARCY_FRICTION);
    double liquidShear = fL * rhoL * vLF * Math.abs(vLF) / 8.0;
    double gasShear = fG * rhoG * vGF * Math.abs(vGF) / 8.0;
    double relative = vGF - vLF;
    double interfaceShear = fI * rhoG * relative * Math.abs(relative) / 8.0;

    double gasRequirement = (gasShear * film.gasPerimeter + interfaceShear * film.interfaceWidth) / gasArea
        + rhoG * GRAVITY * Math.sin(inclination);
    double liquidRequirement = (liquidShear * film.liquidPerimeter - interfaceShear * film.interfaceWidth) / liquidArea
        + rhoL * GRAVITY * Math.sin(inclination);
    return gasRequirement - liquidRequirement;
  }

  /** Wetted perimeters and hydraulic diameters of the film zone. */
  private static final class FilmGeometry {
    double liquidPerimeter;
    double gasPerimeter;
    double interfaceWidth;
    double liquidHydraulicDiameter;
    double gasHydraulicDiameter;
  }

  /**
   * Film-zone geometry, blending a bottom layer (near-horizontal) into an annular film (steep upward flow).
   *
   * @param filmHoldup film holdup
   * @param diameter pipe diameter, m
   * @param inclination inclination, radians
   * @return film geometry
   */
  private static FilmGeometry filmGeometry(double filmHoldup, double diameter, double inclination) {
    double area = Math.PI * diameter * diameter / 4.0;
    double beta = centralAngle(filmHoldup);
    double layerLiquidPerimeter = diameter * beta / 2.0;
    double layerGasPerimeter = diameter * (Math.PI - beta / 2.0);
    double layerInterface = diameter * Math.sin(beta / 2.0);

    double coreDiameter = diameter * Math.sqrt(1.0 - filmHoldup);
    double annularLiquidPerimeter = Math.PI * diameter;
    double annularInterface = Math.PI * coreDiameter;

    double weight = (inclination - STRATIFIED_GEOMETRY_LIMIT) / (ANNULAR_GEOMETRY_LIMIT - STRATIFIED_GEOMETRY_LIMIT);
    weight = Math.max(0.0, Math.min(1.0, weight));

    FilmGeometry g = new FilmGeometry();
    g.liquidPerimeter = (1.0 - weight) * layerLiquidPerimeter + weight * annularLiquidPerimeter;
    g.gasPerimeter = (1.0 - weight) * layerGasPerimeter;
    g.interfaceWidth = (1.0 - weight) * layerInterface + weight * annularInterface;
    g.liquidHydraulicDiameter = 4.0 * filmHoldup * area / Math.max(1.0e-12, g.liquidPerimeter);
    g.gasHydraulicDiameter = 4.0 * (1.0 - filmHoldup) * area / Math.max(1.0e-12, g.gasPerimeter + g.interfaceWidth);
    return g;
  }

  /**
   * Central angle of a circular segment with the given area fraction.
   *
   * @param holdup segment area divided by bore area
   * @return central angle, radians
   */
  private static double centralAngle(double holdup) {
    if (holdup <= 0.0) {
      return 0.0;
    }
    if (holdup >= 1.0) {
      return 2.0 * Math.PI;
    }
    double target = 2.0 * Math.PI * holdup;
    double low = 0.0;
    double high = 2.0 * Math.PI;
    for (int iteration = 0; iteration < 60; iteration++) {
      double middle = 0.5 * (low + high);
      if (middle - Math.sin(middle) < target) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Darcy friction factor: laminar below Re 2300, Haaland above 4000, linear blend between.
   *
   * @param reynolds Reynolds number
   * @param relativeRoughness roughness divided by diameter
   * @return Darcy friction factor
   */
  private static double darcyFriction(double reynolds, double relativeRoughness) {
    if (!(reynolds > 0.0)) {
      return 0.0;
    }
    if (reynolds < 2300.0) {
      return 64.0 / reynolds;
    }
    double turbulent = haaland(Math.max(4000.0, reynolds), relativeRoughness);
    if (reynolds < 4000.0) {
      double laminar = 64.0 / 2300.0;
      return laminar + (turbulent - laminar) * (reynolds - 2300.0) / 1700.0;
    }
    return turbulent;
  }

  /**
   * Haaland explicit friction factor.
   *
   * @param reynolds Reynolds number, at least 4000
   * @param relativeRoughness roughness divided by diameter
   * @return Darcy friction factor
   */
  private static double haaland(double reynolds, double relativeRoughness) {
    double term = -1.8 * Math.log10(6.9 / reynolds + Math.pow(relativeRoughness / 3.7, 1.11));
    return 1.0 / (term * term);
  }
}
