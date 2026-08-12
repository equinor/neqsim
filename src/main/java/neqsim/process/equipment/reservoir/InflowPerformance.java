package neqsim.process.equipment.reservoir;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * InflowPerformance class - liquid inflow performance relationships (IPR) for oil wells.
 *
 * <p>
 * The IPR models already available through {@link WellFlow} are all gas forms: they work in squared pressures and
 * MSm3/day. An oil well needs the liquid forms, in which the rate is proportional to the pressure drawdown itself
 * rather than to the difference of its square, and in which the rate is a stock-tank liquid volume in Sm3/day. This
 * class supplies those, as a small self-contained calculation object that can be evaluated from either end - rate from
 * pressure, or pressure from rate - and attached to a {@link WellFlow} through
 * {@link WellFlow#setInflowPerformance(InflowPerformance)}.
 * </p>
 *
 * <h2>Models</h2>
 * <table>
 * <caption>Liquid inflow performance relationships implemented by this class</caption>
 * <tr>
 * <th>Model</th>
 * <th>Relationship</th>
 * <th>Use when</th>
 * </tr>
 * <tr>
 * <td>LINEAR</td>
 * <td>q = J (Pr - Pwf)</td>
 * <td>the whole drainage area stays above the bubble point</td>
 * </tr>
 * <tr>
 * <td>VOGEL</td>
 * <td>q / qmax = 1 - 0.2 (Pwf/Pr) - 0.8 (Pwf/Pr)&sup2;, qmax = J Pr / 1.8</td>
 * <td>the reservoir is at or below the bubble point</td>
 * </tr>
 * <tr>
 * <td>COMPOSITE</td>
 * <td>linear above the bubble point, Vogel below it</td>
 * <td>an undersaturated reservoir produced at a drawdown that takes the sandface into two-phase flow - the common case,
 * and the one a linear model flatters</td>
 * </tr>
 * <tr>
 * <td>JOSHI_HORIZONTAL</td>
 * <td>composite, with J computed from rock and geometry</td>
 * <td>a horizontal drain whose productivity should follow from permeability, net pay and drain length rather than being
 * assumed</td>
 * </tr>
 * </table>
 *
 * <h2>Units</h2>
 * <p>
 * Rates are stock-tank liquid Sm3/day, pressures are bara and the productivity index is Sm3/(day&middot;bar). The Darcy
 * productivity helpers additionally take permeability in mD, lengths in m and viscosity in cP.
 * </p>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // An undersaturated reservoir 3.7 bar above its bubble point.
 * InflowPerformance ipr = InflowPerformance.composite(200.0, 70.68, 66.94);
 *
 * double pwf = ipr.bottomHolePressure(2500.0); // bara required for 2500 Sm3/day
 * double rate = ipr.rate(55.0); // Sm3/day at 55 bara
 * double aof = ipr.absoluteOpenFlow(); // Sm3/day at zero bottom-hole pressure
 * }</pre>
 *
 * <p>
 * A horizontal drain whose productivity index is derived rather than assumed:
 * </p>
 *
 * <pre>{@code
 * InflowPerformance horizontal = InflowPerformance.joshiHorizontal(2000.0, // permeability, mD
 *     45.0, // net pay, m
 *     1500.0, // drain length, m
 *     800.0, // drainage radius, m
 *     0.108, // wellbore radius, m
 *     3.0, // oil viscosity, cP
 *     1.1224, // formation volume factor
 *     0.3, // kv / kh
 *     2.0, // skin
 *     70.68, // reservoir pressure, bara
 *     66.94); // bubble point pressure, bara
 * }</pre>
 *
 * @author asmund
 * @version $Id: $Id
 * @see WellFlow
 * @see TubingPerformance
 */
public class InflowPerformance implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Radial Darcy productivity constant giving Sm3/(day&middot;bar) from mD, m and cP. Equivalent to the familiar
   * field-unit constant 0.00708 with the net pay in feet.
   */
  public static final double DARCY_PI_CONSTANT = 0.053577;

  /** Liquid inflow performance relationship types. */
  public enum Model {
    /** Straight-line inflow, valid above the bubble point only. */
    LINEAR,
    /** Vogel's saturated-oil curve. */
    VOGEL,
    /** Linear above the bubble point, Vogel below it. */
    COMPOSITE,
    /** Composite inflow with a Joshi horizontal-well productivity index. */
    JOSHI_HORIZONTAL
  }

  /** Selected inflow relationship. */
  private Model model = Model.COMPOSITE;
  /** Productivity index, Sm3/(day.bar). */
  private double productivityIndex = 0.0;
  /** Average reservoir pressure, bara. */
  private double reservoirPressure = 0.0;
  /** Bubble point pressure, bara. */
  private double bubblePointPressure = 0.0;
  /** Inputs used when the productivity index came from the Joshi solution, may be null. */
  private double[] joshiInputs = null;

  /**
   * Constructor for InflowPerformance.
   *
   * @param model inflow relationship to use, must not be null
   * @param productivityIndex productivity index in Sm3/(day.bar), must be greater than zero
   * @param reservoirPressure average reservoir pressure in bara, must be greater than zero
   * @param bubblePointPressure bubble point pressure in bara, zero or greater; ignored by {@link Model#LINEAR} and
   * {@link Model#VOGEL}
   * @throws IllegalArgumentException if the productivity index or the reservoir pressure is not positive
   */
  public InflowPerformance(Model model, double productivityIndex, double reservoirPressure,
      double bubblePointPressure) {
    if (productivityIndex <= 0.0) {
      throw new IllegalArgumentException("productivity index must be greater than zero, got " + productivityIndex);
    }
    if (reservoirPressure <= 0.0) {
      throw new IllegalArgumentException("reservoir pressure must be greater than zero, got " + reservoirPressure);
    }
    this.model = model;
    this.productivityIndex = productivityIndex;
    this.reservoirPressure = reservoirPressure;
    this.bubblePointPressure = Math.max(bubblePointPressure, 0.0);
  }

  /**
   * Create a straight-line inflow relationship.
   *
   * @param productivityIndex productivity index in Sm3/(day.bar), greater than zero
   * @param reservoirPressure average reservoir pressure in bara, greater than zero
   * @return a linear inflow performance object
   */
  public static InflowPerformance linear(double productivityIndex, double reservoirPressure) {
    return new InflowPerformance(Model.LINEAR, productivityIndex, reservoirPressure, 0.0);
  }

  /**
   * Create a Vogel saturated-oil inflow relationship.
   *
   * @param productivityIndex productivity index in Sm3/(day.bar) at zero drawdown, greater than zero
   * @param reservoirPressure average reservoir pressure in bara, greater than zero
   * @return a Vogel inflow performance object
   */
  public static InflowPerformance vogel(double productivityIndex, double reservoirPressure) {
    return new InflowPerformance(Model.VOGEL, productivityIndex, reservoirPressure, reservoirPressure);
  }

  /**
   * Create a composite inflow relationship: linear above the bubble point, Vogel below it.
   *
   * @param productivityIndex productivity index in Sm3/(day.bar), greater than zero
   * @param reservoirPressure average reservoir pressure in bara, greater than zero
   * @param bubblePointPressure bubble point pressure in bara, zero or greater
   * @return a composite inflow performance object
   */
  public static InflowPerformance composite(double productivityIndex, double reservoirPressure,
      double bubblePointPressure) {
    return new InflowPerformance(Model.COMPOSITE, productivityIndex, reservoirPressure, bubblePointPressure);
  }

  /**
   * Create a composite inflow relationship whose productivity index comes from Joshi's horizontal well solution rather
   * than being assumed.
   *
   * @param permeabilityMilliDarcy horizontal permeability in mD, greater than zero
   * @param netPayMetre net pay thickness in m, greater than zero
   * @param drainLengthMetre horizontal drain length in m, greater than zero
   * @param drainageRadiusMetre drainage radius in m, greater than zero
   * @param wellboreRadiusMetre wellbore radius in m, greater than zero
   * @param viscosityCentiPoise oil viscosity at reservoir conditions in cP, greater than zero
   * @param formationVolumeFactor oil formation volume factor in rm3/Sm3, greater than zero
   * @param kvOverKh ratio of vertical to horizontal permeability, greater than zero and normally between 0.01 and 1
   * @param skin completion skin, dimensionless, zero or greater for a damaged completion
   * @param reservoirPressure average reservoir pressure in bara, greater than zero
   * @param bubblePointPressure bubble point pressure in bara, zero or greater
   * @return a composite inflow performance object carrying the derived productivity index
   */
  public static InflowPerformance joshiHorizontal(double permeabilityMilliDarcy, double netPayMetre,
      double drainLengthMetre, double drainageRadiusMetre, double wellboreRadiusMetre, double viscosityCentiPoise,
      double formationVolumeFactor, double kvOverKh, double skin, double reservoirPressure,
      double bubblePointPressure) {
    double index = joshiProductivityIndex(permeabilityMilliDarcy, netPayMetre, drainLengthMetre, drainageRadiusMetre,
        wellboreRadiusMetre, viscosityCentiPoise, formationVolumeFactor, kvOverKh, skin);
    InflowPerformance inflow = new InflowPerformance(Model.JOSHI_HORIZONTAL, index, reservoirPressure,
        bubblePointPressure);
    inflow.joshiInputs = new double[] { permeabilityMilliDarcy, netPayMetre, drainLengthMetre, drainageRadiusMetre,
        wellboreRadiusMetre, viscosityCentiPoise, formationVolumeFactor, kvOverKh, skin };
    return inflow;
  }

  /**
   * Joshi's productivity index for a horizontal well in a bounded drainage ellipse.
   *
   * <p>
   * J = 2 &pi; k h / (&mu; B [ln((a + sqrt(a&sup2; - (L/2)&sup2;)) / (L/2)) + (&beta; h / L) ln(h / (2 rw)) + S]), with
   * a the half major axis of the drainage ellipse and &beta; = sqrt(kh/kv) the anisotropy. For a thin reservoir column
   * the vertical permeability term dominates the denominator, so kv/kh is the assumption to challenge before the drain
   * length.
   * </p>
   *
   * @param permeabilityMilliDarcy horizontal permeability in mD, greater than zero
   * @param netPayMetre net pay thickness in m, greater than zero
   * @param drainLengthMetre horizontal drain length in m, greater than zero
   * @param drainageRadiusMetre drainage radius in m, greater than zero
   * @param wellboreRadiusMetre wellbore radius in m, greater than zero
   * @param viscosityCentiPoise oil viscosity in cP, greater than zero
   * @param formationVolumeFactor oil formation volume factor in rm3/Sm3, greater than zero
   * @param kvOverKh ratio of vertical to horizontal permeability, greater than zero
   * @param skin completion skin, dimensionless
   * @return productivity index in Sm3/(day.bar)
   * @throws IllegalArgumentException if any dimension, property or permeability is not positive
   */
  public static double joshiProductivityIndex(double permeabilityMilliDarcy, double netPayMetre,
      double drainLengthMetre, double drainageRadiusMetre, double wellboreRadiusMetre, double viscosityCentiPoise,
      double formationVolumeFactor, double kvOverKh, double skin) {
    if (permeabilityMilliDarcy <= 0.0 || netPayMetre <= 0.0 || drainLengthMetre <= 0.0 || drainageRadiusMetre <= 0.0
        || wellboreRadiusMetre <= 0.0 || viscosityCentiPoise <= 0.0 || formationVolumeFactor <= 0.0
        || kvOverKh <= 0.0) {
      throw new IllegalArgumentException(
          "Joshi productivity index requires positive dimensions, properties and permeability");
    }
    double halfLength = drainLengthMetre / 2.0;
    double ratio = Math.pow(2.0 * drainageRadiusMetre / drainLengthMetre, 4.0);
    double majorAxis = halfLength * Math.sqrt(0.5 + Math.sqrt(0.25 + ratio));
    double anisotropy = Math.sqrt(1.0 / kvOverKh);
    double ellipseTerm = Math
        .log((majorAxis + Math.sqrt(Math.max(majorAxis * majorAxis - halfLength * halfLength, 0.0))) / halfLength);
    double verticalTerm = anisotropy * netPayMetre / drainLengthMetre
        * Math.log(netPayMetre / (2.0 * wellboreRadiusMetre));
    double denominator = ellipseTerm + verticalTerm + skin;
    return DARCY_PI_CONSTANT * permeabilityMilliDarcy * netPayMetre
        / (viscosityCentiPoise * formationVolumeFactor * denominator);
  }

  /**
   * Steady-state radial Darcy productivity index for a vertical well.
   *
   * <p>
   * Useful as a sanity check on an assumed productivity index: if the assumed value and this one differ by more than a
   * factor of two, one of them is describing a different well.
   * </p>
   *
   * @param permeabilityMilliDarcy permeability in mD, greater than zero
   * @param netPayMetre net pay thickness in m, greater than zero
   * @param drainageRadiusMetre drainage radius in m, greater than the wellbore radius
   * @param wellboreRadiusMetre wellbore radius in m, greater than zero
   * @param viscosityCentiPoise oil viscosity in cP, greater than zero
   * @param formationVolumeFactor oil formation volume factor in rm3/Sm3, greater than zero
   * @param skin completion skin, dimensionless
   * @return productivity index in Sm3/(day.bar)
   * @throws IllegalArgumentException if any dimension or property is not positive, or if the drainage radius is not
   * larger than the wellbore radius
   */
  public static double radialProductivityIndex(double permeabilityMilliDarcy, double netPayMetre,
      double drainageRadiusMetre, double wellboreRadiusMetre, double viscosityCentiPoise, double formationVolumeFactor,
      double skin) {
    if (permeabilityMilliDarcy <= 0.0 || netPayMetre <= 0.0 || wellboreRadiusMetre <= 0.0 || viscosityCentiPoise <= 0.0
        || formationVolumeFactor <= 0.0) {
      throw new IllegalArgumentException("radial productivity index requires positive dimensions and properties");
    }
    if (drainageRadiusMetre <= wellboreRadiusMetre) {
      throw new IllegalArgumentException("drainage radius must be larger than the wellbore radius");
    }
    double denominator = Math.log(drainageRadiusMetre / wellboreRadiusMetre) - 0.75 + skin;
    return DARCY_PI_CONSTANT * permeabilityMilliDarcy * netPayMetre
        / (viscosityCentiPoise * formationVolumeFactor * denominator);
  }

  /**
   * Stock-tank liquid rate delivered at a bottom-hole flowing pressure.
   *
   * @param bottomHolePressure bottom-hole flowing pressure in bara, zero or greater
   * @return liquid rate in Sm3/day, never negative
   */
  public double rate(double bottomHolePressure) {
    double pwf = Math.max(bottomHolePressure, 0.0);
    if (pwf >= reservoirPressure) {
      return 0.0;
    }
    switch (model) {
    case LINEAR:
      return productivityIndex * (reservoirPressure - pwf);
    case VOGEL:
      return vogelRate(pwf, reservoirPressure);
    case COMPOSITE:
    case JOSHI_HORIZONTAL:
    default:
      if (reservoirPressure <= bubblePointPressure) {
        return vogelRate(pwf, reservoirPressure);
      }
      if (pwf >= bubblePointPressure) {
        return productivityIndex * (reservoirPressure - pwf);
      }
      return rateAtBubblePoint() + vogelSpan() * vogelShape(pwf / bubblePointPressure);
    }
  }

  /**
   * Bottom-hole flowing pressure required to deliver a stock-tank liquid rate.
   *
   * @param liquidRate liquid rate in Sm3/day, zero or greater
   * @return bottom-hole flowing pressure in bara; zero when the rate is at or above the absolute open flow
   */
  public double bottomHolePressure(double liquidRate) {
    double rate = Math.max(liquidRate, 0.0);
    switch (model) {
    case LINEAR:
      return Math.max(reservoirPressure - rate / productivityIndex, 0.0);
    case VOGEL:
      return invertVogel(rate, reservoirPressure, vogelQmax(reservoirPressure));
    case COMPOSITE:
    case JOSHI_HORIZONTAL:
    default:
      if (reservoirPressure <= bubblePointPressure) {
        return invertVogel(rate, reservoirPressure, vogelQmax(reservoirPressure));
      }
      if (rate <= rateAtBubblePoint()) {
        return reservoirPressure - rate / productivityIndex;
      }
      return invertVogel(rate - rateAtBubblePoint(), bubblePointPressure, vogelSpan());
    }
  }

  /**
   * Absolute open flow: the rate the well would deliver at zero bottom-hole pressure.
   *
   * @return absolute open flow potential in Sm3/day
   */
  public double absoluteOpenFlow() {
    return rate(0.0);
  }

  /**
   * The inflow performance curve, from the reservoir pressure down to zero.
   *
   * @param points number of points on the curve, at least two
   * @return a list of two-element arrays holding bottom-hole pressure in bara and rate in Sm3/day
   * @throws IllegalArgumentException if fewer than two points are requested
   */
  public List<double[]> curve(int points) {
    if (points < 2) {
      throw new IllegalArgumentException("an inflow curve needs at least two points");
    }
    List<double[]> rows = new ArrayList<double[]>(points);
    for (int index = points - 1; index >= 0; index--) {
      double pwf = reservoirPressure * index / (points - 1.0);
      rows.add(new double[] { pwf, rate(pwf) });
    }
    return rows;
  }

  /**
   * Vogel dimensionless shape function.
   *
   * @param ratio bottom-hole pressure divided by the reference pressure, zero to one
   * @return the dimensionless rate fraction, zero to one
   */
  private double vogelShape(double ratio) {
    double bounded = Math.min(Math.max(ratio, 0.0), 1.0);
    return 1.0 - 0.2 * bounded - 0.8 * bounded * bounded;
  }

  /**
   * Maximum Vogel rate at a reference pressure.
   *
   * @param referencePressure reference pressure in bara
   * @return maximum rate in Sm3/day
   */
  private double vogelQmax(double referencePressure) {
    return productivityIndex * referencePressure / 1.8;
  }

  /**
   * Vogel rate at a bottom-hole pressure.
   *
   * @param bottomHolePressure bottom-hole flowing pressure in bara
   * @param referencePressure reference pressure in bara
   * @return liquid rate in Sm3/day
   */
  private double vogelRate(double bottomHolePressure, double referencePressure) {
    return vogelQmax(referencePressure) * vogelShape(bottomHolePressure / referencePressure);
  }

  /**
   * Rate at which the sandface reaches the bubble point.
   *
   * @return liquid rate in Sm3/day, zero when the reservoir is already saturated
   */
  private double rateAtBubblePoint() {
    return Math.max(productivityIndex * (reservoirPressure - bubblePointPressure), 0.0);
  }

  /**
   * Additional rate available between the bubble point and zero bottom-hole pressure.
   *
   * @return rate span in Sm3/day
   */
  private double vogelSpan() {
    return vogelQmax(bubblePointPressure);
  }

  /**
   * Invert the Vogel shape function for a bottom-hole pressure.
   *
   * @param rate rate above the reference point in Sm3/day
   * @param referencePressure reference pressure in bara
   * @param span rate span covered by the Vogel branch in Sm3/day
   * @return bottom-hole flowing pressure in bara, zero when the rate exceeds the span
   */
  private double invertVogel(double rate, double referencePressure, double span) {
    if (span <= 0.0 || rate >= span) {
      return 0.0;
    }
    double constant = 1.0 - rate / span;
    double root = (-0.2 + Math.sqrt(0.04 + 3.2 * constant)) / 1.6;
    return Math.max(root, 0.0) * referencePressure;
  }

  /**
   * Get the inflow relationship in use.
   *
   * @return the selected model
   */
  public Model getModel() {
    return model;
  }

  /**
   * Get the productivity index.
   *
   * @return productivity index in Sm3/(day.bar)
   */
  public double getProductivityIndex() {
    return productivityIndex;
  }

  /**
   * Get the average reservoir pressure.
   *
   * @return reservoir pressure in bara
   */
  public double getReservoirPressure() {
    return reservoirPressure;
  }

  /**
   * Set the average reservoir pressure, so one object can be re-used as the reservoir depletes.
   *
   * @param reservoirPressure reservoir pressure in bara, greater than zero
   * @throws IllegalArgumentException if the pressure is not positive
   */
  public void setReservoirPressure(double reservoirPressure) {
    if (reservoirPressure <= 0.0) {
      throw new IllegalArgumentException("reservoir pressure must be greater than zero, got " + reservoirPressure);
    }
    this.reservoirPressure = reservoirPressure;
  }

  /**
   * Get the bubble point pressure.
   *
   * @return bubble point pressure in bara
   */
  public double getBubblePointPressure() {
    return bubblePointPressure;
  }

  /**
   * Set the bubble point pressure.
   *
   * @param bubblePointPressure bubble point pressure in bara, zero or greater
   */
  public void setBubblePointPressure(double bubblePointPressure) {
    this.bubblePointPressure = Math.max(bubblePointPressure, 0.0);
  }

  /**
   * Whether free gas is present at the sandface at a given bottom-hole pressure.
   *
   * @param bottomHolePressure bottom-hole flowing pressure in bara
   * @return true when the bottom-hole pressure is below the bubble point
   */
  public boolean hasFreeGasAtSandface(double bottomHolePressure) {
    return bottomHolePressure < bubblePointPressure;
  }

  /**
   * Get the Joshi inputs used to derive the productivity index, when there were any.
   *
   * @return a copy of the input array, or null when the productivity index was supplied directly
   */
  public double[] getJoshiInputs() {
    return joshiInputs == null ? null : joshiInputs.clone();
  }
}
