package neqsim.process.mechanicaldesign.piping;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

/**
 * Three-dimensional pipe stress model of one piping system per ASME B31.3 (Process Piping).
 *
 * <p>
 * The pipe is modelled as 3D Euler-Bernoulli beam elements (six degrees of freedom per node) in a global frame with z
 * up. At every change of direction the model inserts a long-radius bend: the two short tangent elements next to the
 * corner get the B31.3 bend flexibility factor k = 1.65/h, and the bend nodes get the stress intensification factor i =
 * 0.9/h<sup>2/3</sup> with h = T R / r<sup>2</sup> (applied to the resultant moment, conservative for the out-of-plane
 * part). Nodes joining three or more elements are treated as welding tees (h = 3.1 T / r).
 * </p>
 *
 * <p>
 * Restraints: anchors (rigid or with given nozzle stiffness), one-way rest supports (+z only, can lift off) with
 * Coulomb friction, guides (rest support plus a bilateral horizontal stop across the pipe) and axial line stops. The
 * operating case is solved by iterating the support contact and stick/slip states until they no longer change.
 * </p>
 *
 * <p>
 * Load cases (B31.3 para. 319 and 320, liberal allowable of Eq. 1b):
 * </p>
 * <ul>
 * <li>OPE: weight + thermal expansion, nonlinear supports with friction;</li>
 * <li>SUS: weight + pressure with the support contact found in OPE (sustained longitudinal stress S<sub>L</sub> &lt;=
 * S<sub>h</sub>, corroded wall);</li>
 * <li>EXP: OPE minus SUS (displacement stress range S<sub>E</sub> &lt;= S<sub>A</sub> = f (1.25 (S<sub>c</sub> +
 * S<sub>h</sub>) - S<sub>L</sub>), nominal wall);</li>
 * <li>OCC: horizontal acceleration (seismic) and wind in x and y, combined with SUS (S<sub>L</sub> + S<sub>occ</sub>
 * &lt;= 1.33 S<sub>h</sub>).</li>
 * </ul>
 *
 * <p>
 * Results include displacements, code stresses with SIFs at every element end, the internal-pressure wall thickness
 * check (B31.3 para. 304.1.2), support loads and states, and anchor (nozzle) loads checked against vendor allowables
 * when given. Units are SI: metres, newtons, pascals, degrees Celsius.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class PipeStressModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Gravitational acceleration in m/s2. */
  private static final double G = 9.81;

  /** Penalty stiffness for rigid translational restraints in N/m. */
  private static final double K_TRANS = 1.0e12;

  /** Penalty stiffness for rigid rotational restraints in N*m/rad. */
  private static final double K_ROT = 1.0e12;

  /** Minimum change of direction treated as a bend in radians (5 degrees). */
  private static final double BEND_ANGLE_MIN = Math.toRadians(5.0);

  /** Displacement below which a sliding support is considered stuck in metres. */
  private static final double SLIP_TOL = 1.0e-5;

  /** Maximum iterations of the contact (lift-off) loop per friction update. */
  private static final int MAX_ITER = 60;

  /** Maximum friction (outer) iterations. */
  private static final int MAX_OUTER = 80;

  /** Tension on a rest support that releases it, in N. */
  private static final double LIFT_TOL = 1.0;

  /** Penetration below a lifted support that closes it again, in m. */
  private static final double GAP_TOL = 1.0e-6;

  /** Restraint types. */
  public enum RestraintType {
    /** All six degrees of freedom fixed (or springs with the given stiffness). */
    ANCHOR,
    /** One-way vertical support (+z), can lift off, Coulomb friction in the horizontal plane. */
    REST,
    /** Rest support plus a bilateral horizontal stop across the pipe axis. */
    GUIDE,
    /** Bilateral stop along the horizontal pipe axis. */
    LINE_STOP
  }

  private final String name;
  private final List<String> nodeIds = new ArrayList<String>();
  private final List<double[]> nodeXyz = new ArrayList<double[]>();
  private final List<int[]> userElements = new ArrayList<int[]>();
  private final List<Restraint> restraints = new ArrayList<Restraint>();

  private double outerDiameter = 0.2191;
  private double wallThickness = 0.00818;
  private double corrosionAllowance = 0.0;
  private double elasticModulus = 203.0e9;
  private double poissonRatio = 0.3;
  private double steelDensity = 7850.0;
  private double thermalExpansion = 12.0e-6;
  private double allowableHot = 138.0e6;
  private double allowableCold = 138.0e6;
  private double stressRangeFactor = 1.0;
  private double fluidDensity = 1000.0;
  private double insulationWeight = 0.0;
  private double designPressure = 0.0;
  private double installTemperature = 20.0;
  private double operatingTemperature = 20.0;
  private double bendRadiusFactor = 1.5;
  private double seismicAcceleration = 0.0;
  private double windPressure = 0.0;
  private double windDragCoefficient = 0.7;

  private Results results;

  /**
   * Restraint at a node.
   *
   * @author esol
   * @version 1.0
   */
  public static class Restraint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final int node;
    private final RestraintType type;
    private double friction;
    private double translationalStiffness = K_TRANS;
    private double rotationalStiffness = K_ROT;
    private double allowableForce = Double.NaN;
    private double allowableMoment = Double.NaN;

    /**
     * Create a restraint.
     *
     * @param id restraint id
     * @param node node index
     * @param type restraint type
     */
    Restraint(String id, int node, RestraintType type) {
      this.id = id;
      this.node = node;
      this.type = type;
    }

    /**
     * Restraint id.
     *
     * @return id
     */
    public String getId() {
      return id;
    }

    /**
     * Restraint type.
     *
     * @return type
     */
    public RestraintType getType() {
      return type;
    }
  }

  /**
   * Element used by the solver (after bend insertion).
   *
   * @author esol
   * @version 1.0
   */
  private static final class Element implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int i;
    private final int j;
    private double flexibility = 1.0;

    /**
     * Create an element.
     *
     * @param i start node
     * @param j end node
     */
    Element(int i, int j) {
      this.i = i;
      this.j = j;
    }
  }

  /**
   * Contact state of a rest-type support during the nonlinear solve.
   *
   * @author esol
   * @version 1.0
   */
  private static final class SupportState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private boolean active = true;
    private boolean sliding = false;
    private double[] friction = new double[2];
  }

  /**
   * Create an empty model.
   *
   * @param name model name (for example the line number)
   */
  public PipeStressModel(String name) {
    this.name = name;
  }

  /**
   * Model name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Set pipe size.
   *
   * @param outerDiameterM outer diameter in m
   * @param wallThicknessM nominal wall thickness in m
   * @param corrosionAllowanceM corrosion allowance in m (removed for sustained and occasional stresses)
   */
  public void setPipe(double outerDiameterM, double wallThicknessM, double corrosionAllowanceM) {
    if (!(outerDiameterM > 0) || !(wallThicknessM > 0) || wallThicknessM - corrosionAllowanceM <= 0
        || 2 * wallThicknessM >= outerDiameterM) {
      throw new IllegalArgumentException("Pipe needs OD > 2 t > 0 and t greater than the corrosion allowance");
    }
    this.outerDiameter = outerDiameterM;
    this.wallThickness = wallThicknessM;
    this.corrosionAllowance = corrosionAllowanceM;
  }

  /**
   * Set material properties.
   *
   * @param elasticModulusPa elastic modulus in Pa
   * @param poisson Poisson ratio
   * @param densityKgM3 pipe density in kg/m3
   * @param alphaPerK mean thermal expansion coefficient from installation to operating temperature in 1/K
   */
  public void setMaterial(double elasticModulusPa, double poisson, double densityKgM3, double alphaPerK) {
    this.elasticModulus = elasticModulusPa;
    this.poissonRatio = poisson;
    this.steelDensity = densityKgM3;
    this.thermalExpansion = alphaPerK;
  }

  /**
   * Set B31.3 allowable stresses.
   *
   * @param hotPa basic allowable stress at the operating (hot) temperature S_h in Pa
   * @param coldPa basic allowable stress at the minimum (cold) temperature S_c in Pa
   * @param rangeFactor stress range factor f (1.0 for up to 7000 cycles)
   */
  public void setAllowables(double hotPa, double coldPa, double rangeFactor) {
    this.allowableHot = hotPa;
    this.allowableCold = coldPa;
    this.stressRangeFactor = rangeFactor;
  }

  /**
   * Set contents and insulation.
   *
   * @param fluidDensityKgM3 density of the contents in kg/m3
   * @param insulationWeightNPerM insulation and cladding weight in N/m
   */
  public void setContents(double fluidDensityKgM3, double insulationWeightNPerM) {
    this.fluidDensity = fluidDensityKgM3;
    this.insulationWeight = insulationWeightNPerM;
  }

  /**
   * Set design pressure and temperatures.
   *
   * @param designPressurePa internal design pressure (gauge) in Pa
   * @param installTemperatureC installation temperature in C
   * @param operatingTemperatureC operating (design) temperature in C
   */
  public void setConditions(double designPressurePa, double installTemperatureC, double operatingTemperatureC) {
    this.designPressure = designPressurePa;
    this.installTemperature = installTemperatureC;
    this.operatingTemperature = operatingTemperatureC;
  }

  /**
   * Set the bend radius as a multiple of the outer diameter (1.5 for long-radius elbows).
   *
   * @param factor bend radius factor
   */
  public void setBendRadiusFactor(double factor) {
    this.bendRadiusFactor = factor;
  }

  /**
   * Set occasional loads.
   *
   * @param horizontalAccelerationG horizontal seismic acceleration as a fraction of g
   * @param windPressurePa wind dynamic pressure in Pa (0 for none)
   * @param dragCoefficient drag coefficient for wind on the pipe
   */
  public void setOccasionalLoads(double horizontalAccelerationG, double windPressurePa, double dragCoefficient) {
    this.seismicAcceleration = horizontalAccelerationG;
    this.windPressure = windPressurePa;
    this.windDragCoefficient = dragCoefficient;
  }

  /**
   * Add a node.
   *
   * @param id node id
   * @param x x in m
   * @param y y in m
   * @param z z (up) in m
   * @return node index
   */
  public int addNode(String id, double x, double y, double z) {
    if (nodeIds.contains(id)) {
      throw new IllegalArgumentException("Duplicate node id " + id);
    }
    nodeIds.add(id);
    nodeXyz.add(new double[] {x, y, z});
    return nodeIds.size() - 1;
  }

  /**
   * Add a straight pipe element between two nodes.
   *
   * @param fromNode start node id
   * @param toNode end node id
   */
  public void addElement(String fromNode, String toNode) {
    int i = index(fromNode);
    int j = index(toNode);
    if (dist(nodeXyz.get(i), nodeXyz.get(j)) < 1.0e-6) {
      throw new IllegalArgumentException("Element " + fromNode + "-" + toNode + " has zero length");
    }
    userElements.add(new int[] {i, j});
  }

  /**
   * Add a restraint.
   *
   * @param id restraint id
   * @param node node id
   * @param type restraint type
   * @param frictionCoefficient Coulomb friction coefficient (REST and GUIDE; 0 for none)
   * @return the restraint, for optional stiffness and allowables
   */
  public Restraint addRestraint(String id, String node, RestraintType type, double frictionCoefficient) {
    Restraint r = new Restraint(id, index(node), type);
    r.friction = frictionCoefficient;
    restraints.add(r);
    return r;
  }

  /**
   * Set nozzle flexibility of an anchor (for example from WRC 297 or vendor data).
   *
   * @param restraint anchor restraint
   * @param translationalNPerM translational stiffness in N/m
   * @param rotationalNmPerRad rotational stiffness in N*m/rad
   */
  public void setAnchorStiffness(Restraint restraint, double translationalNPerM, double rotationalNmPerRad) {
    restraint.translationalStiffness = translationalNPerM;
    restraint.rotationalStiffness = rotationalNmPerRad;
  }

  /**
   * Set allowable nozzle loads for an anchor (vendor or standard allowables, resultant values).
   *
   * @param restraint anchor restraint
   * @param resultantForceN allowable resultant force in N
   * @param resultantMomentNm allowable resultant moment in N*m
   */
  public void setNozzleAllowables(Restraint restraint, double resultantForceN, double resultantMomentNm) {
    restraint.allowableForce = resultantForceN;
    restraint.allowableMoment = resultantMomentNm;
  }

  /**
   * Node index for an id.
   *
   * @param id node id
   * @return index
   */
  private int index(String id) {
    int k = nodeIds.indexOf(id);
    if (k < 0) {
      throw new IllegalArgumentException("Unknown node " + id);
    }
    return k;
  }

  /**
   * Distance between two points.
   *
   * @param a first point
   * @param b second point
   * @return distance
   */
  private static double dist(double[] a, double[] b) {
    double dx = b[0] - a[0];
    double dy = b[1] - a[1];
    double dz = b[2] - a[2];
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  /**
   * Unit vector from a to b.
   *
   * @param a start
   * @param b end
   * @return unit vector
   */
  private static double[] unit(double[] a, double[] b) {
    double l = dist(a, b);
    return new double[] {(b[0] - a[0]) / l, (b[1] - a[1]) / l, (b[2] - a[2]) / l};
  }

  /**
   * Mean radius of the pipe wall.
   *
   * @return mean radius in m
   */
  private double meanRadius() {
    return (outerDiameter - wallThickness) / 2.0;
  }

  /**
   * Bend flexibility characteristic h = T R / r^2.
   *
   * @return h
   */
  public double bendCharacteristic() {
    double r = meanRadius();
    return wallThickness * bendRadiusFactor * outerDiameter / (r * r);
  }

  /**
   * B31.3 bend flexibility factor k = 1.65 / h (not less than 1).
   *
   * @return k
   */
  public double bendFlexibilityFactor() {
    return Math.max(1.0, 1.65 / bendCharacteristic());
  }

  /**
   * B31.3 in-plane bend SIF 0.9 / h^(2/3) (not less than 1).
   *
   * @return SIF
   */
  public double bendSif() {
    return Math.max(1.0, 0.9 / Math.pow(bendCharacteristic(), 2.0 / 3.0));
  }

  /**
   * B31.3 welding-tee SIF 0.9 / h^(2/3) with h = 3.1 T / r (not less than 1).
   *
   * @return SIF
   */
  public double teeSif() {
    double h = 3.1 * wallThickness / meanRadius();
    return Math.max(1.0, 0.9 / Math.pow(h, 2.0 / 3.0));
  }

  /**
   * Build solver geometry: split elements at bends and assign flexibility and SIFs.
   *
   * @param xyz output node coordinates (filled)
   * @param ids output node ids (filled)
   * @param sif output SIF per node (filled)
   * @return solver elements
   */
  private List<Element> buildGeometry(List<double[]> xyz, List<String> ids, List<Double> sif) {
    for (int n = 0; n < nodeXyz.size(); n++) {
      xyz.add(nodeXyz.get(n).clone());
      ids.add(nodeIds.get(n));
      sif.add(1.0);
    }
    int nodeCount = xyz.size();
    List<List<Integer>> touching = new ArrayList<List<Integer>>();
    for (int n = 0; n < nodeCount; n++) {
      touching.add(new ArrayList<Integer>());
    }
    for (int s = 0; s < userElements.size(); s++) {
      touching.get(userElements.get(s)[0]).add(s);
      touching.get(userElements.get(s)[1]).add(s);
    }
    int[][] split = new int[userElements.size()][2];
    for (int[] row : split) {
      row[0] = -1;
      row[1] = -1;
    }
    for (int n = 0; n < nodeCount; n++) {
      List<Integer> t = touching.get(n);
      if (t.size() >= 3) {
        sif.set(n, Math.max(sif.get(n), teeSif()));
        continue;
      }
      if (t.size() != 2) {
        continue;
      }
      int[] sides = {t.get(0), t.get(1)};
      int[] others = new int[2];
      double[][] dirs = new double[2][];
      for (int k = 0; k < 2; k++) {
        int[] seg = userElements.get(sides[k]);
        others[k] = seg[0] == n ? seg[1] : seg[0];
        dirs[k] = unit(xyz.get(n), xyz.get(others[k]));
      }
      double cos = dirs[0][0] * dirs[1][0] + dirs[0][1] * dirs[1][1] + dirs[0][2] * dirs[1][2];
      double deflection = Math.PI - Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
      if (deflection < BEND_ANGLE_MIN) {
        continue;
      }
      double tangent = bendRadiusFactor * outerDiameter * Math.tan(deflection / 2.0);
      sif.set(n, Math.max(sif.get(n), bendSif()));
      for (int k = 0; k < 2; k++) {
        double lt = Math.min(tangent, 0.45 * dist(xyz.get(n), xyz.get(others[k])));
        double[] p = {xyz.get(n)[0] + dirs[k][0] * lt, xyz.get(n)[1] + dirs[k][1] * lt,
            xyz.get(n)[2] + dirs[k][2] * lt};
        xyz.add(p);
        ids.add(ids.get(n) + (k == 0 ? "_bendA" : "_bendB"));
        sif.add(bendSif());
        split[sides[k]][userElements.get(sides[k])[0] == n ? 0 : 1] = xyz.size() - 1;
      }
    }
    double kFlex = bendFlexibilityFactor();
    List<Element> out = new ArrayList<Element>();
    for (int s = 0; s < userElements.size(); s++) {
      int a = userElements.get(s)[0];
      final int b = userElements.get(s)[1];
      List<Integer> chain = new ArrayList<Integer>();
      chain.add(a);
      if (split[s][0] >= 0) {
        chain.add(split[s][0]);
      }
      if (split[s][1] >= 0) {
        chain.add(split[s][1]);
      }
      chain.add(b);
      for (int c = 0; c < chain.size() - 1; c++) {
        Element el = new Element(chain.get(c), chain.get(c + 1));
        boolean firstBend = split[s][0] >= 0 && c == 0;
        boolean lastBend = split[s][1] >= 0 && c == chain.size() - 2;
        if (firstBend || lastBend) {
          el.flexibility = kFlex;
        }
        out.add(el);
      }
    }
    return out;
  }

  /**
   * Section properties for a wall thickness.
   *
   * @param t wall thickness in m
   * @return {area, I, J, Z}
   */
  private double[] section(double t) {
    double di = outerDiameter - 2.0 * t;
    double area = Math.PI / 4.0 * (outerDiameter * outerDiameter - di * di);
    double inertia = Math.PI / 64.0 * (Math.pow(outerDiameter, 4) - Math.pow(di, 4));
    return new double[] {area, inertia, 2.0 * inertia, 2.0 * inertia / outerDiameter};
  }

  /**
   * Rotation matrix with rows = local x, y, z axes in global coordinates.
   *
   * @param a start point
   * @param b end point
   * @return 3x3 rotation matrix
   */
  private static double[][] rotation(double[] a, double[] b) {
    double[] ex = unit(a, b);
    double[] ref = Math.abs(ex[2]) > 0.95 ? new double[] {1.0, 0.0, 0.0} : new double[] {0.0, 0.0, 1.0};
    double[] ez = {ex[1] * ref[2] - ex[2] * ref[1], ex[2] * ref[0] - ex[0] * ref[2], ex[0] * ref[1] - ex[1] * ref[0]};
    double n = Math.sqrt(ez[0] * ez[0] + ez[1] * ez[1] + ez[2] * ez[2]);
    ez = new double[] {ez[0] / n, ez[1] / n, ez[2] / n};
    double[] ey = {ez[1] * ex[2] - ez[2] * ex[1], ez[2] * ex[0] - ez[0] * ex[2], ez[0] * ex[1] - ez[1] * ex[0]};
    return new double[][] {ex, ey, ez};
  }

  /**
   * Local 12x12 stiffness of a 3D beam.
   *
   * @param len length in m
   * @param flex bending flexibility factor (bending stiffness divided by this)
   * @return stiffness matrix
   */
  private double[][] localStiffness(double len, double flex) {
    double[] s = section(wallThickness);
    double ea = elasticModulus * s[0];
    final double ei = elasticModulus * s[1] / flex;
    final double gj = elasticModulus / (2.0 * (1.0 + poissonRatio)) * s[2];
    double l2 = len * len;
    final double l3 = l2 * len;
    double[][] k = new double[12][12];
    k[0][0] = ea / len;
    k[0][6] = -ea / len;
    k[6][6] = ea / len;
    k[3][3] = gj / len;
    k[3][9] = -gj / len;
    k[9][9] = gj / len;
    k[1][1] = 12 * ei / l3;
    k[1][5] = 6 * ei / l2;
    k[1][7] = -12 * ei / l3;
    k[1][11] = 6 * ei / l2;
    k[5][5] = 4 * ei / len;
    k[5][7] = -6 * ei / l2;
    k[5][11] = 2 * ei / len;
    k[7][7] = 12 * ei / l3;
    k[7][11] = -6 * ei / l2;
    k[11][11] = 4 * ei / len;
    k[2][2] = 12 * ei / l3;
    k[2][4] = -6 * ei / l2;
    k[2][8] = -12 * ei / l3;
    k[2][10] = -6 * ei / l2;
    k[4][4] = 4 * ei / len;
    k[4][8] = 6 * ei / l2;
    k[4][10] = 2 * ei / len;
    k[8][8] = 12 * ei / l3;
    k[8][10] = 6 * ei / l2;
    k[10][10] = 4 * ei / len;
    for (int r = 0; r < 12; r++) {
      for (int c = 0; c < r; c++) {
        k[r][c] = k[c][r];
      }
    }
    return k;
  }

  /**
   * Local equivalent nodal loads for a uniform global line load and a thermal strain.
   *
   * @param rot rotation matrix
   * @param len length in m
   * @param q global line load in N/m
   * @param thermalStrain free thermal strain
   * @return local load vector (12)
   */
  private double[] localLoads(double[][] rot, double len, double[] q, double thermalStrain) {
    double qx = rot[0][0] * q[0] + rot[0][1] * q[1] + rot[0][2] * q[2];
    double qy = rot[1][0] * q[0] + rot[1][1] * q[1] + rot[1][2] * q[2];
    final double qz = rot[2][0] * q[0] + rot[2][1] * q[1] + rot[2][2] * q[2];
    double[] f = new double[12];
    f[0] = qx * len / 2.0;
    f[6] = qx * len / 2.0;
    f[1] = qy * len / 2.0;
    f[5] = qy * len * len / 12.0;
    f[7] = qy * len / 2.0;
    f[11] = -qy * len * len / 12.0;
    f[2] = qz * len / 2.0;
    f[4] = -qz * len * len / 12.0;
    f[8] = qz * len / 2.0;
    f[10] = qz * len * len / 12.0;
    double axial = elasticModulus * section(wallThickness)[0] * thermalStrain;
    f[0] -= axial;
    f[6] += axial;
    return f;
  }

  /**
   * Transform a 12-vector between global and local axes.
   *
   * @param rot rotation matrix
   * @param v vector
   * @param toLocal true for global to local, false for local to global
   * @return transformed vector
   */
  private static double[] transform(double[][] rot, double[] v, boolean toLocal) {
    double[] out = new double[12];
    for (int b = 0; b < 4; b++) {
      for (int r = 0; r < 3; r++) {
        double s = 0.0;
        for (int c = 0; c < 3; c++) {
          s += (toLocal ? rot[r][c] : rot[c][r]) * v[3 * b + c];
        }
        out[3 * b + r] = s;
      }
    }
    return out;
  }

  /**
   * Global element stiffness T^T k T.
   *
   * @param rot rotation matrix
   * @param k local stiffness
   * @return global 12x12 stiffness
   */
  private static double[][] globalStiffness(double[][] rot, double[][] k) {
    double[][] t = new double[12][12];
    for (int b = 0; b < 4; b++) {
      for (int r = 0; r < 3; r++) {
        for (int c = 0; c < 3; c++) {
          t[3 * b + r][3 * b + c] = rot[r][c];
        }
      }
    }
    double[][] kt = new double[12][12];
    for (int r = 0; r < 12; r++) {
      for (int c = 0; c < 12; c++) {
        double s = 0.0;
        for (int m = 0; m < 12; m++) {
          s += k[r][m] * t[m][c];
        }
        kt[r][c] = s;
      }
    }
    double[][] g = new double[12][12];
    for (int r = 0; r < 12; r++) {
      for (int c = 0; c < 12; c++) {
        double s = 0.0;
        for (int m = 0; m < 12; m++) {
          s += t[m][r] * kt[m][c];
        }
        g[r][c] = s;
      }
    }
    return g;
  }

  /**
   * Weight per metre of pipe, contents and insulation.
   *
   * @return line weight in N/m
   */
  public double lineWeight() {
    double di = outerDiameter - 2.0 * wallThickness;
    return G * (section(wallThickness)[0] * steelDensity + Math.PI / 4.0 * di * di * fluidDensity) + insulationWeight;
  }

  /**
   * Horizontal axes along and across the pipe at a node (from the first attached non-vertical element).
   *
   * @param node node index
   * @param xyz node coordinates
   * @param els elements
   * @return {axial, lateral} horizontal unit vectors (2D)
   */
  private static double[][] horizontalAxes(int node, List<double[]> xyz, List<Element> els) {
    for (Element e : els) {
      if (e.i == node || e.j == node) {
        double[] u = unit(xyz.get(e.i), xyz.get(e.j));
        double h = Math.hypot(u[0], u[1]);
        if (h > 1.0e-6) {
          return new double[][] {{u[0] / h, u[1] / h}, {-u[1] / h, u[0] / h}};
        }
      }
    }
    return new double[][] {{1.0, 0.0}, {0.0, 1.0}};
  }

  /**
   * One linear solve.
   *
   * @param xyz node coordinates
   * @param els elements
   * @param q global line load in N/m
   * @param thermalStrain free thermal strain
   * @param states support states (null = all rest supports active and stuck)
   * @param applyFriction whether to apply sliding friction forces
   * @return global displacement vector
   */
  private double[] solveLinear(List<double[]> xyz, List<Element> els, double[] q, double thermalStrain,
      Map<Restraint, SupportState> states, boolean applyFriction) {
    int ndof = 6 * xyz.size();
    DMatrixRMaj kg = new DMatrixRMaj(ndof, ndof);
    DMatrixRMaj f = new DMatrixRMaj(ndof, 1);
    for (Element e : els) {
      double[][] rot = rotation(xyz.get(e.i), xyz.get(e.j));
      double len = dist(xyz.get(e.i), xyz.get(e.j));
      double[][] k = globalStiffness(rot, localStiffness(len, e.flexibility));
      double[] fg = transform(rot, localLoads(rot, len, q, thermalStrain), false);
      int[] map = dofMap(e);
      for (int r = 0; r < 12; r++) {
        f.add(map[r], 0, fg[r]);
        for (int c = 0; c < 12; c++) {
          kg.add(map[r], map[c], k[r][c]);
        }
      }
    }
    for (Restraint r : restraints) {
      final int b = 6 * r.node;
      if (r.type == RestraintType.ANCHOR) {
        for (int d = 0; d < 3; d++) {
          kg.add(b + d, b + d, r.translationalStiffness);
          kg.add(b + 3 + d, b + 3 + d, r.rotationalStiffness);
        }
        continue;
      }
      double[][] axes = horizontalAxes(r.node, xyz, els);
      if (r.type == RestraintType.LINE_STOP) {
        addDirectional(kg, b, axes[0], K_TRANS);
        continue;
      }
      SupportState st = states == null ? null : states.get(r);
      boolean active = st == null || st.active;
      if (r.type == RestraintType.GUIDE) {
        addDirectional(kg, b, axes[1], K_TRANS);
      }
      if (!active) {
        continue;
      }
      kg.add(b + 2, b + 2, K_TRANS);
      boolean stuck = r.friction > 0 && (st == null || !st.sliding || !applyFriction);
      if (stuck) {
        if (r.type == RestraintType.GUIDE) {
          addDirectional(kg, b, axes[0], K_TRANS);
        } else {
          kg.add(b, b, K_TRANS);
          kg.add(b + 1, b + 1, K_TRANS);
        }
      } else if (applyFriction && st != null && st.sliding) {
        f.add(b, 0, st.friction[0]);
        f.add(b + 1, 0, st.friction[1]);
      }
    }
    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.lu(ndof);
    if (!solver.setA(kg)) {
      throw new IllegalStateException("Pipe model " + name + " is unstable: add anchors or restraints");
    }
    DMatrixRMaj u = new DMatrixRMaj(ndof, 1);
    solver.solve(f, u);
    double[] out = u.getData().clone();
    for (double v : out) {
      if (!Double.isFinite(v) || Math.abs(v) > 100.0) {
        throw new IllegalStateException("Pipe model " + name + " is unstable: add anchors or restraints");
      }
    }
    return out;
  }

  /**
   * Add a penalty spring along a horizontal direction.
   *
   * @param kg global stiffness
   * @param b first dof of the node
   * @param dir horizontal unit vector (2D)
   * @param k stiffness
   */
  private static void addDirectional(DMatrixRMaj kg, int b, double[] dir, double k) {
    kg.add(b, b, k * dir[0] * dir[0]);
    kg.add(b, b + 1, k * dir[0] * dir[1]);
    kg.add(b + 1, b, k * dir[0] * dir[1]);
    kg.add(b + 1, b + 1, k * dir[1] * dir[1]);
  }

  /**
   * Global dof indices of an element.
   *
   * @param e element
   * @return 12 dof indices
   */
  private static int[] dofMap(Element e) {
    int[] m = new int[12];
    for (int d = 0; d < 6; d++) {
      m[d] = 6 * e.i + d;
      m[6 + d] = 6 * e.j + d;
    }
    return m;
  }

  /**
   * Run all load cases and code checks.
   *
   * @return results
   */
  public Results run() {
    if (userElements.isEmpty()) {
      throw new IllegalStateException("Pipe model " + name + " has no elements");
    }
    boolean anchored = false;
    for (Restraint r : restraints) {
      anchored |= r.type == RestraintType.ANCHOR;
    }
    if (!anchored) {
      throw new IllegalStateException("Pipe model " + name + " needs at least one anchor");
    }
    List<double[]> xyz = new ArrayList<double[]>();
    List<String> ids = new ArrayList<String>();
    List<Double> sif = new ArrayList<Double>();
    List<Element> els = buildGeometry(xyz, ids, sif);
    double w = lineWeight();
    double[] weight = {0.0, 0.0, -w};
    double strain = thermalExpansion * (operatingTemperature - installTemperature);

    Map<Restraint, SupportState> states = new LinkedHashMap<Restraint, SupportState>();
    for (Restraint r : restraints) {
      if (r.type == RestraintType.REST || r.type == RestraintType.GUIDE) {
        states.put(r, new SupportState());
      }
    }
    double[] uOpe = solveLinear(xyz, els, weight, strain, states, true);
    boolean converged = false;
    int iter = 1;
    for (int outer = 0; outer < MAX_OUTER && !converged; outer++) {
      int inner = 0;
      while (inner < MAX_ITER && updateContact(uOpe, states)) {
        uOpe = solveLinear(xyz, els, weight, strain, states, true);
        inner++;
        iter++;
      }
      boolean frictionChanged = updateFriction(uOpe, states, xyz, els, outer >= MAX_OUTER / 2);
      if (!frictionChanged && !updateContact(uOpe, states)) {
        converged = inner < MAX_ITER;
        break;
      }
      uOpe = solveLinear(xyz, els, weight, strain, states, true);
      iter++;
    }
    Map<Restraint, SupportState> susStates = new LinkedHashMap<Restraint, SupportState>();
    for (Map.Entry<Restraint, SupportState> e : states.entrySet()) {
      SupportState s = new SupportState();
      s.active = e.getValue().active;
      susStates.put(e.getKey(), s);
    }
    final double[] uSus = solveLinear(xyz, els, weight, 0.0, susStates, false);
    double wind = windPressure * windDragCoefficient * outerDiameter;
    double[] qx = {seismicAcceleration * w + wind, 0.0, 0.0};
    double[] qy = {0.0, seismicAcceleration * w + wind, 0.0};
    final double[] uOccX = solveLinear(xyz, els, qx, 0.0, susStates, false);
    final double[] uOccY = solveLinear(xyz, els, qy, 0.0, susStates, false);

    Results res = new Results();
    res.name = name;
    res.converged = converged;
    res.iterations = iter;
    res.lineWeightNPerM = w;
    res.bendFlexibilityFactor = bendFlexibilityFactor();
    res.bendSif = bendSif();
    res.thermalStrain = strain;
    res.requiredWallThicknessM = designPressure * outerDiameter / (2.0 * (allowableHot + designPressure * 0.4))
        + corrosionAllowance;
    res.pressureDesignStatus = wallThickness >= res.requiredWallThicknessM ? "pass" : "fail";
    double[][] cases = {uOpe, uSus, uOccX, uOccY};
    double[][] loads = {weight, weight, qx, qy};
    double[] strains = {strain, 0.0, 0.0, 0.0};
    fillStresses(res, xyz, ids, sif, els, cases, loads, strains);
    fillRestraints(res, xyz, els, states, susStates, cases);
    for (int n = 0; n < ids.size(); n++) {
      res.displacementsOpeM.put(ids.get(n), new double[] {uOpe[6 * n], uOpe[6 * n + 1], uOpe[6 * n + 2]});
      res.displacementsSusM.put(ids.get(n), new double[] {uSus[6 * n], uSus[6 * n + 1], uSus[6 * n + 2]});
    }
    res.finish();
    this.results = res;
    return res;
  }

  /**
   * Active-set contact update: release the most tensile support or close the most penetrated gap, one per call.
   *
   * <p>
   * Changing one support at a time avoids the chatter that occurs when several neighbouring supports switch together. A
   * small hysteresis (1 N tension, 1 micrometre penetration) stops round-off from toggling a support.
   * </p>
   *
   * @param u displacements
   * @param states support states
   * @return true when a support changed state (re-solve needed)
   */
  private boolean updateContact(double[] u, Map<Restraint, SupportState> states) {
    SupportState release = null;
    double worstTension = -LIFT_TOL;
    SupportState close = null;
    double worstGap = -GAP_TOL;
    for (Map.Entry<Restraint, SupportState> e : states.entrySet()) {
      SupportState s = e.getValue();
      double uz = u[6 * e.getKey().node + 2];
      if (s.active) {
        double normal = -K_TRANS * uz;
        if (normal < worstTension) {
          worstTension = normal;
          release = s;
        }
      } else if (uz < worstGap) {
        worstGap = uz;
        close = s;
      }
    }
    if (release != null) {
      release.active = false;
      release.sliding = false;
      return true;
    }
    if (close != null) {
      close.active = true;
      close.sliding = false;
      return true;
    }
    return false;
  }

  /**
   * Update stick/slip state and friction forces of supports in contact.
   *
   * <p>
   * A stuck support starts to slide when its horizontal reaction exceeds mu N; a sliding support carries mu N opposite
   * to its movement (relaxed 50 % towards the new value) and sticks again when it no longer moves. After half the outer
   * iterations the stick/slip pattern is frozen and only the friction forces are updated, which guarantees termination.
   * </p>
   *
   * @param u displacements
   * @param states support states
   * @param xyz node coordinates
   * @param els elements
   * @param freeze true to keep the stick/slip pattern
   * @return true when friction states or forces changed
   */
  private boolean updateFriction(double[] u, Map<Restraint, SupportState> states, List<double[]> xyz, List<Element> els,
      boolean freeze) {
    boolean changed = false;
    for (Map.Entry<Restraint, SupportState> e : states.entrySet()) {
      Restraint r = e.getKey();
      SupportState s = e.getValue();
      if (!s.active || r.friction <= 0.0) {
        continue;
      }
      double normal = Math.max(0.0, -K_TRANS * u[6 * r.node + 2]);
      double limit = r.friction * normal;
      double[] slip = lateralSlip(r, u, horizontalAxes(r.node, xyz, els));
      double mag = Math.hypot(slip[0], slip[1]);
      if (!s.sliding) {
        if (!freeze && K_TRANS * mag > limit * (1.0 + 1.0e-6) && mag > 0.0) {
          s.sliding = true;
          s.friction = new double[] {-limit * slip[0] / mag, -limit * slip[1] / mag};
          changed = true;
        }
        continue;
      }
      if (mag < SLIP_TOL) {
        if (!freeze) {
          s.sliding = false;
          changed = true;
        }
        continue;
      }
      double[] target = {-limit * slip[0] / mag, -limit * slip[1] / mag};
      double[] relaxed = {0.5 * (s.friction[0] + target[0]), 0.5 * (s.friction[1] + target[1])};
      if (Math.hypot(target[0] - s.friction[0], target[1] - s.friction[1]) > 0.005 * Math.max(limit, 1.0)) {
        changed = true;
        s.friction = relaxed;
      } else {
        s.friction = target;
      }
    }
    return changed;
  }

  /**
   * Horizontal movement of a support node in its friction directions (axial only for guides).
   *
   * @param r restraint
   * @param u displacements
   * @param axes horizontal axes
   * @return movement (2)
   */
  private static double[] lateralSlip(Restraint r, double[] u, double[][] axes) {
    final int b = 6 * r.node;
    if (r.type == RestraintType.GUIDE) {
      double a = u[b] * axes[0][0] + u[b + 1] * axes[0][1];
      return new double[] {a * axes[0][0], a * axes[0][1]};
    }
    return new double[] {u[b], u[b + 1]};
  }

  /**
   * Element end forces in local axes for a displacement vector.
   *
   * @param e element
   * @param xyz coordinates
   * @param u displacements
   * @param q line load
   * @param strain thermal strain
   * @return local end forces (12): forces on the element at nodes i and j
   */
  private double[] endForces(Element e, List<double[]> xyz, double[] u, double[] q, double strain) {
    double[][] rot = rotation(xyz.get(e.i), xyz.get(e.j));
    double len = dist(xyz.get(e.i), xyz.get(e.j));
    double[] ue = new double[12];
    int[] map = dofMap(e);
    for (int d = 0; d < 12; d++) {
      ue[d] = u[map[d]];
    }
    double[] ul = transform(rot, ue, true);
    double[][] k = localStiffness(len, e.flexibility);
    double[] fl = localLoads(rot, len, q, strain);
    double[] out = new double[12];
    for (int r = 0; r < 12; r++) {
      double s = -fl[r];
      for (int c = 0; c < 12; c++) {
        s += k[r][c] * ul[c];
      }
      out[r] = s;
    }
    return out;
  }

  /**
   * Code stresses at both ends of every element.
   *
   * @param res results
   * @param xyz coordinates
   * @param ids node ids
   * @param sif SIF per node
   * @param els elements
   * @param cases displacements {OPE, SUS, OCC x, OCC y}
   * @param loads line loads per case
   * @param strains thermal strain per case
   */
  private void fillStresses(Results res, List<double[]> xyz, List<String> ids, List<Double> sif, List<Element> els,
      double[][] cases, double[][] loads, double[] strains) {
    double[] nom = section(wallThickness);
    double[] cor = section(wallThickness - corrosionAllowance);
    double di = outerDiameter - 2.0 * wallThickness;
    double slp = designPressure * di * di / (outerDiameter * outerDiameter - di * di);
    for (Element e : els) {
      double[][] f = new double[4][];
      for (int c = 0; c < 4; c++) {
        f[c] = endForces(e, xyz, cases[c], loads[c], strains[c]);
      }
      for (int end = 0; end < 2; end++) {
        int o = 6 * end;
        int node = end == 0 ? e.i : e.j;
        double i = sif.get(node);
        final double sl = longitudinal(f[1], o, i, cor, slp);
        double mt = Math.abs(f[0][o + 3] - f[1][o + 3]);
        double mb = Math.hypot(f[0][o + 4] - f[1][o + 4], f[0][o + 5] - f[1][o + 5]);
        final double sb = i * mb / nom[3];
        final double st = mt / (2.0 * nom[3]);
        final double occ = Math.max(longitudinal(f[2], o, i, cor, 0.0), longitudinal(f[3], o, i, cor, 0.0));
        StressPoint p = new StressPoint();
        p.node = ids.get(node);
        p.x = xyz.get(node)[0];
        p.y = xyz.get(node)[1];
        p.z = xyz.get(node)[2];
        p.sif = i;
        p.sustainedPa = sl;
        p.sustainedAllowablePa = allowableHot;
        p.expansionPa = Math.sqrt(sb * sb + 4.0 * st * st);
        p.expansionAllowablePa = Math.max(0.0, stressRangeFactor * (1.25 * (allowableCold + allowableHot) - sl));
        p.occasionalPa = sl + occ;
        p.occasionalAllowablePa = 1.33 * allowableHot;
        p.operatingAxialForceN = f[0][o];
        p.operatingMomentNm = Math.hypot(f[0][o + 4], f[0][o + 5]);
        res.stresses.add(p);
      }
    }
  }

  /**
   * Longitudinal stress from axial force, pressure, bending and torsion.
   *
   * @param f local end forces
   * @param o offset (0 or 6)
   * @param i SIF
   * @param sec section properties
   * @param pressureStress longitudinal pressure stress in Pa
   * @return stress in Pa
   */
  private static double longitudinal(double[] f, int o, double i, double[] sec, double pressureStress) {
    double sa = Math.abs(f[o]) / sec[0] + pressureStress;
    double sb = i * Math.hypot(f[o + 4], f[o + 5]) / sec[3];
    double st = Math.abs(f[o + 3]) / (2.0 * sec[3]);
    return Math.sqrt((sa + sb) * (sa + sb) + 4.0 * st * st);
  }

  /**
   * Restraint loads per case and nozzle checks.
   *
   * @param res results
   * @param xyz coordinates
   * @param els elements
   * @param opeStates OPE states
   * @param susStates SUS states
   * @param cases displacements {OPE, SUS, OCC x, OCC y}
   */
  private void fillRestraints(Results res, List<double[]> xyz, List<Element> els,
      Map<Restraint, SupportState> opeStates, Map<Restraint, SupportState> susStates, double[][] cases) {
    for (Restraint r : restraints) {
      RestraintLoad rl = new RestraintLoad();
      rl.id = r.getId();
      rl.node = nodeIds.get(r.node);
      rl.type = r.type.name();
      rl.ope = reaction(r, cases[0], opeStates.get(r), xyz, els, true);
      rl.sus = reaction(r, cases[1], susStates.get(r), xyz, els, false);
      double[] ox = reaction(r, cases[2], susStates.get(r), xyz, els, false);
      double[] oy = reaction(r, cases[3], susStates.get(r), xyz, els, false);
      rl.occ = norm3(ox, 0) >= norm3(oy, 0) ? ox : oy;
      SupportState s = opeStates.get(r);
      rl.state = s == null ? "fixed" : !s.active ? "lifted_off" : s.sliding ? "sliding" : "in_contact";
      if (r.type == RestraintType.ANCHOR) {
        rl.resultantForceN = Math.max(norm3(rl.ope, 0), norm3(rl.sus, 0));
        rl.resultantMomentNm = Math.max(norm3(rl.ope, 3), norm3(rl.sus, 3));
        if (Double.isNaN(r.allowableForce)) {
          rl.nozzleStatus = "no_allowable";
        } else {
          rl.allowableForceN = r.allowableForce;
          rl.allowableMomentNm = r.allowableMoment;
          rl.nozzleStatus = rl.resultantForceN <= r.allowableForce && rl.resultantMomentNm <= r.allowableMoment ? "pass"
              : "fail";
        }
      }
      res.restraints.add(rl);
    }
  }

  /**
   * Length of a 3-vector inside a 6-vector.
   *
   * @param v vector
   * @param o offset
   * @return length
   */
  private static double norm3(double[] v, int o) {
    return Math.sqrt(v[o] * v[o] + v[o + 1] * v[o + 1] + v[o + 2] * v[o + 2]);
  }

  /**
   * Reaction of a restraint on the pipe: {Fx, Fy, Fz, Mx, My, Mz} in global axes.
   *
   * @param r restraint
   * @param u displacements
   * @param s support state (null for anchors and stops)
   * @param xyz coordinates
   * @param els elements
   * @param withFriction whether sliding friction is applied in this case
   * @return reaction (6)
   */
  private double[] reaction(Restraint r, double[] u, SupportState s, List<double[]> xyz, List<Element> els,
      boolean withFriction) {
    final int b = 6 * r.node;
    double[] out = new double[6];
    if (r.type == RestraintType.ANCHOR) {
      for (int d = 0; d < 3; d++) {
        out[d] = -r.translationalStiffness * u[b + d];
        out[3 + d] = -r.rotationalStiffness * u[b + 3 + d];
      }
      return out;
    }
    double[][] axes = horizontalAxes(r.node, xyz, els);
    if (r.type == RestraintType.LINE_STOP) {
      double a = -K_TRANS * (u[b] * axes[0][0] + u[b + 1] * axes[0][1]);
      out[0] = a * axes[0][0];
      out[1] = a * axes[0][1];
      return out;
    }
    if (r.type == RestraintType.GUIDE) {
      double lat = -K_TRANS * (u[b] * axes[1][0] + u[b + 1] * axes[1][1]);
      out[0] += lat * axes[1][0];
      out[1] += lat * axes[1][1];
    }
    if (s != null && !s.active) {
      return out;
    }
    out[2] = -K_TRANS * u[b + 2];
    if (r.friction > 0) {
      if (s != null && s.sliding && withFriction) {
        out[0] += s.friction[0];
        out[1] += s.friction[1];
      } else {
        double[] slip = lateralSlip(r, u, axes);
        out[0] -= K_TRANS * slip[0];
        out[1] -= K_TRANS * slip[1];
      }
    }
    return out;
  }

  /**
   * Results of the last run (null before {@link #run()}).
   *
   * @return results
   */
  public Results getResults() {
    return results;
  }

  /**
   * Code stress at one element end.
   *
   * @author esol
   * @version 1.0
   */
  public static class StressPoint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private String node;
    private double x;
    private double y;
    private double z;
    private double sif;
    private double sustainedPa;
    private double sustainedAllowablePa;
    private double expansionPa;
    private double expansionAllowablePa;
    private double occasionalPa;
    private double occasionalAllowablePa;
    private double operatingAxialForceN;
    private double operatingMomentNm;

    /**
     * Node id.
     *
     * @return node id
     */
    public String getNode() {
      return node;
    }

    /**
     * Position {x, y, z} of the point.
     *
     * @return position in m
     */
    public double[] getPosition() {
      return new double[] {x, y, z};
    }

    /**
     * Sustained stress ratio S_L / S_h.
     *
     * @return ratio
     */
    public double getSustainedRatio() {
      return sustainedPa / sustainedAllowablePa;
    }

    /**
     * Expansion stress ratio S_E / S_A.
     *
     * @return ratio
     */
    public double getExpansionRatio() {
      return expansionAllowablePa > 0 ? expansionPa / expansionAllowablePa : Double.POSITIVE_INFINITY;
    }

    /**
     * Occasional stress ratio (S_L + S_occ) / 1.33 S_h.
     *
     * @return ratio
     */
    public double getOccasionalRatio() {
      return occasionalPa / occasionalAllowablePa;
    }

    /**
     * Expansion stress range S_E.
     *
     * @return stress in Pa
     */
    public double getExpansionPa() {
      return expansionPa;
    }

    /**
     * Sustained stress S_L.
     *
     * @return stress in Pa
     */
    public double getSustainedPa() {
      return sustainedPa;
    }

    /**
     * Stress intensification factor used at this point.
     *
     * @return SIF
     */
    public double getSif() {
      return sif;
    }

    /**
     * Operating bending moment at this point.
     *
     * @return moment in N*m
     */
    public double getOperatingMomentNm() {
      return operatingMomentNm;
    }

    /**
     * Operating local axial end force (element end convention).
     *
     * @return force in N
     */
    public double getOperatingAxialForceN() {
      return operatingAxialForceN;
    }
  }

  /**
   * Loads on one restraint.
   *
   * @author esol
   * @version 1.0
   */
  public static class RestraintLoad implements Serializable {
    private static final long serialVersionUID = 1000L;
    private String id;
    private String node;
    private String type;
    private String state;
    private double[] ope;
    private double[] sus;
    private double[] occ;
    private double resultantForceN = Double.NaN;
    private double resultantMomentNm = Double.NaN;
    private double allowableForceN = Double.NaN;
    private double allowableMomentNm = Double.NaN;
    private String nozzleStatus;

    /**
     * Restraint id.
     *
     * @return id
     */
    public String getId() {
      return id;
    }

    /**
     * Node id of the restraint.
     *
     * @return node id
     */
    public String getNode() {
      return node;
    }

    /**
     * Restraint type name.
     *
     * @return type
     */
    public String getType() {
      return type;
    }

    /**
     * Allowable resultant nozzle force (NaN when not given).
     *
     * @return force in N
     */
    public double getAllowableForceN() {
      return allowableForceN;
    }

    /**
     * Allowable resultant nozzle moment (NaN when not given).
     *
     * @return moment in N*m
     */
    public double getAllowableMomentNm() {
      return allowableMomentNm;
    }

    /**
     * Operating reaction on the pipe {Fx, Fy, Fz, Mx, My, Mz} (N, N*m).
     *
     * @return reaction
     */
    public double[] getOperating() {
      return ope.clone();
    }

    /**
     * Sustained reaction on the pipe {Fx, Fy, Fz, Mx, My, Mz} (N, N*m).
     *
     * @return reaction
     */
    public double[] getSustained() {
      return sus.clone();
    }

    /**
     * Largest occasional reaction on the pipe {Fx, Fy, Fz, Mx, My, Mz} (N, N*m).
     *
     * @return reaction
     */
    public double[] getOccasional() {
      return occ.clone();
    }

    /**
     * Support state in the operating case: fixed, in_contact, sliding or lifted_off.
     *
     * @return state
     */
    public String getState() {
      return state;
    }

    /**
     * Nozzle check status for anchors: pass, fail or no_allowable (null for supports).
     *
     * @return status
     */
    public String getNozzleStatus() {
      return nozzleStatus;
    }

    /**
     * Largest resultant anchor force of OPE and SUS (NaN for supports).
     *
     * @return force in N
     */
    public double getResultantForceN() {
      return resultantForceN;
    }

    /**
     * Largest resultant anchor moment of OPE and SUS (NaN for supports).
     *
     * @return moment in N*m
     */
    public double getResultantMomentNm() {
      return resultantMomentNm;
    }
  }

  /**
   * All results of a run.
   *
   * @author esol
   * @version 1.0
   */
  public static class Results implements Serializable {
    private static final long serialVersionUID = 1000L;
    private String name;
    private final String standard;
    private boolean converged;
    private int iterations;
    private double lineWeightNPerM;
    private double bendFlexibilityFactor;
    private double bendSif;
    private double thermalStrain;
    private double requiredWallThicknessM;
    private String pressureDesignStatus;
    private double maxSustainedRatio;
    private double maxExpansionRatio;
    private double maxOccasionalRatio;
    private String maxSustainedNode;
    private String maxExpansionNode;
    private String status;
    private final List<String> liftOff = new ArrayList<String>();
    private final List<String> sliding = new ArrayList<String>();
    private final List<StressPoint> stresses = new ArrayList<StressPoint>();
    private final List<RestraintLoad> restraints = new ArrayList<RestraintLoad>();
    private final Map<String, double[]> displacementsOpeM = new LinkedHashMap<String, double[]>();
    private final Map<String, double[]> displacementsSusM = new LinkedHashMap<String, double[]>();
    private final List<String> limitations = new ArrayList<String>();

    /**
     * Creates an empty result set for the ASME B31.3 basis.
     */
    Results() {
      standard = "ASME B31.3 para. 304.1.2, 319 and 320 (liberal Eq. 1b)";
    }

    /** Summarise ratios and states. */
    void finish() {
      for (StressPoint p : stresses) {
        if (p.getSustainedRatio() > maxSustainedRatio) {
          maxSustainedRatio = p.getSustainedRatio();
          maxSustainedNode = p.node;
        }
        if (p.getExpansionRatio() > maxExpansionRatio) {
          maxExpansionRatio = p.getExpansionRatio();
          maxExpansionNode = p.node;
        }
        maxOccasionalRatio = Math.max(maxOccasionalRatio, p.getOccasionalRatio());
      }
      boolean nozzleFail = false;
      for (RestraintLoad r : restraints) {
        if ("lifted_off".equals(r.state)) {
          liftOff.add(r.id);
        } else if ("sliding".equals(r.state)) {
          sliding.add(r.id);
        }
        nozzleFail |= "fail".equals(r.nozzleStatus);
      }
      boolean ok = converged && maxSustainedRatio <= 1.0 && maxExpansionRatio <= 1.0 && maxOccasionalRatio <= 1.0
          && "pass".equals(pressureDesignStatus) && !nozzleFail;
      status = ok ? "pass" : "fail";
      limitations.add("Linear-elastic beam model; bends modelled by the B31.3 flexibility factor on tangent elements "
          + "and the in-plane SIF applied to the resultant moment.");
      limitations.add("Mean thermal expansion coefficient and ambient elastic modulus; no creep or fatigue spectrum.");
      limitations.add("Occasional loads are static equivalents (no response-spectrum or dynamic analysis).");
      limitations.add("Nozzle loads are checked only against vendor or standard allowables supplied to the model.");
    }

    /**
     * Model name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Code basis.
     *
     * @return standard text
     */
    public String getStandard() {
      return standard;
    }

    /**
     * Overall status: pass when all code checks pass and the support iteration converged.
     *
     * @return pass or fail
     */
    public String getStatus() {
      return status;
    }

    /**
     * Whether the nonlinear support iteration converged.
     *
     * @return converged
     */
    public boolean isConverged() {
      return converged;
    }

    /**
     * Number of operating-case iterations.
     *
     * @return iterations
     */
    public int getIterations() {
      return iterations;
    }

    /**
     * Line weight used (pipe, contents, insulation).
     *
     * @return weight in N/m
     */
    public double getLineWeightNPerM() {
      return lineWeightNPerM;
    }

    /**
     * Free thermal strain alpha * (T_op - T_install).
     *
     * @return strain
     */
    public double getThermalStrain() {
      return thermalStrain;
    }

    /**
     * Largest sustained stress ratio.
     *
     * @return ratio
     */
    public double getMaxSustainedRatio() {
      return maxSustainedRatio;
    }

    /**
     * Node with the largest sustained stress ratio.
     *
     * @return node id
     */
    public String getMaxSustainedNode() {
      return maxSustainedNode;
    }

    /**
     * Largest expansion stress ratio.
     *
     * @return ratio
     */
    public double getMaxExpansionRatio() {
      return maxExpansionRatio;
    }

    /**
     * Node with the largest expansion stress ratio.
     *
     * @return node id
     */
    public String getMaxExpansionNode() {
      return maxExpansionNode;
    }

    /**
     * Largest occasional stress ratio.
     *
     * @return ratio
     */
    public double getMaxOccasionalRatio() {
      return maxOccasionalRatio;
    }

    /**
     * Pressure design status (B31.3 304.1.2 with E = W = 1, Y = 0.4).
     *
     * @return pass or fail
     */
    public String getPressureDesignStatus() {
      return pressureDesignStatus;
    }

    /**
     * Required wall thickness for pressure including corrosion allowance.
     *
     * @return thickness in m
     */
    public double getRequiredWallThicknessM() {
      return requiredWallThicknessM;
    }

    /**
     * Supports that lift off in the operating case.
     *
     * @return support ids
     */
    public List<String> getLiftOff() {
      return new ArrayList<String>(liftOff);
    }

    /**
     * Supports that slide in the operating case.
     *
     * @return support ids
     */
    public List<String> getSliding() {
      return new ArrayList<String>(sliding);
    }

    /**
     * Code stresses at every element end.
     *
     * @return stress points
     */
    public List<StressPoint> getStresses() {
      return new ArrayList<StressPoint>(stresses);
    }

    /**
     * Restraint loads.
     *
     * @return loads
     */
    public List<RestraintLoad> getRestraints() {
      return new ArrayList<RestraintLoad>(restraints);
    }

    /**
     * Restraint load by id.
     *
     * @param id restraint id
     * @return load, or null
     */
    public RestraintLoad getRestraint(String id) {
      for (RestraintLoad r : restraints) {
        if (r.id.equals(id)) {
          return r;
        }
      }
      return null;
    }

    /**
     * Operating displacement {dx, dy, dz} of a node.
     *
     * @param node node id
     * @return displacement in m
     */
    public double[] getOperatingDisplacement(String node) {
      return displacementsOpeM.get(node).clone();
    }

    /**
     * Sustained displacement {dx, dy, dz} of a node.
     *
     * @param node node id
     * @return displacement in m
     */
    public double[] getSustainedDisplacement(String node) {
      return displacementsSusM.get(node).clone();
    }

    /**
     * Bend stress intensification factor used.
     *
     * @return SIF
     */
    public double getBendSif() {
      return bendSif;
    }

    /**
     * Bend flexibility factor used.
     *
     * @return k
     */
    public double getBendFlexibilityFactor() {
      return bendFlexibilityFactor;
    }

    /**
     * Model limitations.
     *
     * @return limitation texts
     */
    public List<String> getLimitations() {
      return new ArrayList<String>(limitations);
    }

    /**
     * Results as JSON.
     *
     * @return JSON text
     */
    public String toJson() {
      return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(this);
    }
  }
}
