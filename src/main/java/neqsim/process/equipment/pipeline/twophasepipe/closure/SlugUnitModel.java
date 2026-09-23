package neqsim.process.equipment.pipeline.twophasepipe.closure;

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
 * unit equals the flux leaving it and the unit-averaged gradient is exactly wall friction plus gravity: the pressure
 * spent accelerating film liquid at the front is recovered at the tail. That front term is reported separately for
 * diagnosis only. When slugs cannot be sustained (slug fraction at or below zero) the result is flagged
 * {@link Result#stratified}; when the film vanishes the unit degenerates to the slug body.
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
    /** Unit-averaged wall friction gradient, Pa/m. */
    public double frictionGradient;
    /** Pressure drop of the front mixing zone per unit length, Pa/m; recovered at the tail, diagnostic only. */
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
    double slugLiquidVelocity = (vm - bubbleVelocity * (1.0 - slugHoldup)) / slugHoldup;

    result.slugHoldup = slugHoldup;
    result.translationalVelocity = translational;
    result.slugLiquidVelocity = slugLiquidVelocity;

    double low = MINIMUM_FILM_HOLDUP;
    double high = slugHoldup * (1.0 - 1.0e-9);
    double residualLow = filmResidual(low, result, bubbleVelocity, vsG, rhoG, rhoL, muG, muL, diameter, roughness,
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
      double value = filmResidual(trial, result, bubbleVelocity, vsG, rhoG, rhoL, muG, muL, diameter, roughness,
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
      double value = filmResidual(middle, result, bubbleVelocity, vsG, rhoG, rhoL, muG, muL, diameter, roughness,
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
    result.frictionGradient = slugFraction * slugWallGradient + (1.0 - slugFraction) * filmWallForce / area;

    double unitLength = result.slugLength / slugFraction;
    result.accelerationGradient = slugFraction < 1.0
        ? rhoL * filmHoldup * (translational - filmLiquidVelocity) * (slugLiquidVelocity - filmLiquidVelocity)
            / unitLength
        : 0.0;
    double filmDensity = filmHoldup * rhoL + (1.0 - filmHoldup) * rhoG;
    result.gravityGradient = (slugFraction * slugDensity + (1.0 - slugFraction) * filmDensity) * GRAVITY * sinTheta;
    result.valid = true;
    return result;
  }

  /**
   * Gas minus film-liquid pressure-gradient requirement in the film zone at a trial film holdup.
   *
   * @param filmHoldup trial film holdup
   * @param unit partially filled result holding slug-body state and translational velocity
   * @param bubbleVelocity dispersed-bubble velocity in the slug body, m/s
   * @param vsG superficial gas velocity, m/s
   * @param rhoG gas density, kg/m3
   * @param rhoL liquid density, kg/m3
   * @param muG gas viscosity, Pa.s
   * @param muL liquid viscosity, Pa.s
   * @param diameter pipe diameter, m
   * @param roughness wall roughness, m
   * @param inclination inclination, radians
   * @return momentum residual, Pa/m
   */
  private double filmResidual(double filmHoldup, Result unit, double bubbleVelocity, double vsG, double rhoG,
      double rhoL, double muG, double muL, double diameter, double roughness, double inclination) {
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
