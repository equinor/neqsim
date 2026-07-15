package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Rigorous inlet-guide-vane (IGV) chart family for a centrifugal {@link Compressor}.
 *
 * <p>
 * Where {@link InletGuideVaneModel} applies generic parametric corrections on top of a single fully-open chart, this
 * class holds a <em>vendor</em> performance map per IGV position (each a complete set of speed lines with flow, head
 * and polytropic efficiency) and interpolates them into a standard {@link CompressorChart} at any requested opening.
 * This mirrors how the expander charts in {@link TurboMachineryChartLibrary} carry IGV-position curve families.
 * </p>
 *
 * <p>
 * Positions are given as opening fractions in {@code [0, 1]} ({@code 1} = fully open).
 * {@link #getChartAtOpening(double)} linearly interpolates the flow, head and efficiency curves between the two
 * bracketing positions (clamping to the nearest position outside the supplied range) and regenerates the surge curve,
 * returning a ready-to-use {@code CompressorChart}. All supplied positions must share the same speed lines and array
 * shapes.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorChartIGV implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Head unit applied to every produced chart. */
  private String headUnit = "kJ/kg";

  /** Whether reference conditions have been supplied. */
  private boolean referenceConditionsSet = false;

  /** Reference molar mass. */
  private double refMW = 0.0;

  /** Reference temperature (K). */
  private double refTemperature = 0.0;

  /** Reference pressure (bara). */
  private double refPressure = 0.0;

  /** Reference compressibility. */
  private double refZ = 1.0;

  /** The vendor positions, kept sorted by opening. */
  private final List<Position> positions = new ArrayList<Position>();

  /** One vendor IGV position: a full set of speed curves at a given opening. */
  private static class Position implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double opening;
    private final double[] chartConditions;
    private final double[] speed;
    private final double[][] flow;
    private final double[][] head;
    private final double[][] polyEff;

    Position(double opening, double[] chartConditions, double[] speed, double[][] flow, double[][] head,
        double[][] polyEff) {
      this.opening = opening;
      this.chartConditions = chartConditions;
      this.speed = speed;
      this.flow = flow;
      this.head = head;
      this.polyEff = polyEff;
    }
  }

  /** Default constructor. */
  public CompressorChartIGV() {
  }

  /**
   * Set the head unit applied to every produced chart (e.g. {@code "kJ/kg"} or {@code "meter"}).
   *
   * @param headUnit the head unit
   */
  public void setHeadUnit(String headUnit) {
    this.headUnit = headUnit;
  }

  /**
   * Set the reference (normalisation) conditions applied to every produced chart.
   *
   * @param refMW the reference molar mass
   * @param refTemperature the reference temperature in K
   * @param refPressure the reference pressure in bara
   * @param refZ the reference compressibility
   */
  public void setReferenceConditions(double refMW, double refTemperature, double refPressure, double refZ) {
    this.refMW = refMW;
    this.refTemperature = refTemperature;
    this.refPressure = refPressure;
    this.refZ = refZ;
    this.referenceConditionsSet = true;
  }

  /**
   * Add a vendor IGV position (a full speed map at one opening). Positions may be added in any order.
   *
   * @param opening the IGV opening fraction in {@code [0, 1]} (1 = fully open)
   * @param chartConditions the chart normalisation conditions (as for {@link CompressorChart#setCurves})
   * @param speed the speed lines (rpm)
   * @param flow the volumetric-flow curve per speed line
   * @param head the head curve per speed line
   * @param polyEff the polytropic-efficiency curve per speed line
   */
  public void addPosition(double opening, double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] polyEff) {
    positions.add(new Position(opening, chartConditions, speed, flow, head, polyEff));
    Collections.sort(positions, new Comparator<Position>() {
      @Override
      public int compare(Position a, Position b) {
        return Double.compare(a.opening, b.opening);
      }
    });
  }

  /**
   * Get the number of supplied IGV positions.
   *
   * @return the number of positions
   */
  public int getNumberOfPositions() {
    return positions.size();
  }

  /**
   * Build a {@link CompressorChart} at the requested IGV opening by interpolating the vendor position curves.
   *
   * @param opening the IGV opening fraction in {@code [0, 1]} (1 = fully open)
   * @return a ready-to-use compressor chart with a generated surge curve
   */
  public CompressorChart getChartAtOpening(double opening) {
    if (positions.isEmpty()) {
      throw new RuntimeException("CompressorChartIGV: no IGV positions have been added.");
    }
    Position lo = positions.get(0);
    Position hi = positions.get(positions.size() - 1);
    if (opening <= lo.opening) {
      return build(lo.chartConditions, lo.speed, lo.flow, lo.head, lo.polyEff);
    }
    if (opening >= hi.opening) {
      return build(hi.chartConditions, hi.speed, hi.flow, hi.head, hi.polyEff);
    }
    Position a = lo;
    Position b = hi;
    for (int i = 0; i < positions.size() - 1; i++) {
      if (opening >= positions.get(i).opening && opening <= positions.get(i + 1).opening) {
        a = positions.get(i);
        b = positions.get(i + 1);
        break;
      }
    }
    if (!sameShape(a, b)) {
      // Cannot interpolate mismatched maps — use the nearest position.
      Position nearest = (Math.abs(opening - a.opening) <= Math.abs(opening - b.opening)) ? a : b;
      return build(nearest.chartConditions, nearest.speed, nearest.flow, nearest.head, nearest.polyEff);
    }
    double w = (opening - a.opening) / (b.opening - a.opening);
    double[][] flow = interpolate(a.flow, b.flow, w);
    double[][] head = interpolate(a.head, b.head, w);
    double[][] eff = interpolate(a.polyEff, b.polyEff, w);
    return build(a.chartConditions, a.speed, flow, head, eff);
  }

  /**
   * Whether two positions have matching array shapes so that they can be interpolated element-wise.
   *
   * @param a the first position
   * @param b the second position
   * @return {@code true} if the speed and curve arrays have identical shapes
   */
  private boolean sameShape(Position a, Position b) {
    if (a.speed.length != b.speed.length || a.flow.length != b.flow.length) {
      return false;
    }
    for (int i = 0; i < a.flow.length; i++) {
      if (a.flow[i].length != b.flow[i].length || a.head[i].length != b.head[i].length
          || a.polyEff[i].length != b.polyEff[i].length) {
        return false;
      }
    }
    return true;
  }

  /**
   * Linear element-wise interpolation between two curve arrays.
   *
   * @param a the array at weight 0
   * @param b the array at weight 1
   * @param w the interpolation weight in {@code [0, 1]}
   * @return the interpolated array
   */
  private double[][] interpolate(double[][] a, double[][] b, double w) {
    double[][] out = new double[a.length][];
    for (int i = 0; i < a.length; i++) {
      out[i] = new double[a[i].length];
      for (int j = 0; j < a[i].length; j++) {
        out[i][j] = a[i][j] * (1.0 - w) + b[i][j] * w;
      }
    }
    return out;
  }

  /**
   * Build a {@link CompressorChart} from interpolated curve arrays.
   *
   * @param chartConditions the chart normalisation conditions
   * @param speed the speed lines
   * @param flow the flow curves
   * @param head the head curves
   * @param polyEff the efficiency curves
   * @return the built chart with a generated surge curve
   */
  private CompressorChart build(double[] chartConditions, double[] speed, double[][] flow, double[][] head,
      double[][] polyEff) {
    CompressorChart chart = new CompressorChart();
    chart.setHeadUnit(headUnit);
    chart.setCurves(chartConditions, speed, flow, head, polyEff);
    if (referenceConditionsSet) {
      chart.setReferenceConditions(refMW, refTemperature, refPressure, refZ);
    }
    chart.setUseCompressorChart(true);
    chart.generateSurgeCurve();
    return chart;
  }
}
