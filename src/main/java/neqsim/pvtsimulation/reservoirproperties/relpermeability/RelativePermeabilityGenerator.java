package neqsim.pvtsimulation.reservoirproperties.relpermeability;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Relative permeability table generator supporting Corey and LET models.
 *
 * <p>
 * Generates relative permeability tables for oil-water (SWOF) and gas-oil (SGOF) systems using
 * industry-standard Corey power-law and LET three-parameter models. Output can be formatted as
 * Eclipse-compatible keywords.
 *
 * <p>
 * <b>Corey Model (1954):</b>
 *
 * $$ K_{rw} = K_{rw,max} \cdot S_{wn}^{n_w}, \quad K_{row} = K_{ro,max} \cdot (1 - S_{wn})^{n_o} $$
 *
 * <p>
 * <b>LET Model (Lomeland, Ebeltoft, Thomas 2005):</b>
 *
 * $$ K_r = K_{r,max} \cdot \frac{S_n^L}{S_n^L + E \cdot (1 - S_n)^T} $$
 *
 * <p>
 * <b>Usage Example:</b>
 *
 * <pre>
 * // Water-Oil Corey curves
 * RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
 * gen.setTableType(RelPermTableType.SWOF);
 * gen.setModelFamily(RelPermModelFamily.COREY);
 * gen.setSwc(0.15);
 * gen.setSorw(0.20);
 * gen.setKroMax(1.0);
 * gen.setKrwMax(0.25);
 * gen.setNo(2.5);
 * gen.setNw(1.5);
 * gen.setRows(25);
 * Map&lt;String, double[]&gt; table = gen.generate();
 *
 * // Export to Eclipse format
 * String eclipse = gen.toEclipseKeyword();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class RelativePermeabilityGenerator {

  /** Number of rows in the output table. */
  private int rows = 20;

  /** Table type (SWOF, SGOF, SOF3, SLGOF). */
  private RelPermTableType tableType = RelPermTableType.SWOF;

  /** Model family (COREY or LET). */
  private RelPermModelFamily modelFamily = RelPermModelFamily.COREY;

  // ==================== SATURATION ENDPOINTS ====================

  /** Connate (irreducible) water saturation. */
  private double swc = 0.0;

  /** Critical water saturation (onset of water flow). Defaults to swc if not set. */
  private double swcr = -1.0;

  /** Residual oil saturation to water. */
  private double sorw = 0.0;

  /** Residual oil saturation to gas. */
  private double sorg = 0.0;

  /** Critical gas saturation. */
  private double sgcr = 0.0;

  // ==================== ENDPOINT RELATIVE PERMEABILITIES ====================

  /** Maximum oil relative permeability (at Swc). */
  private double kroMax = 1.0;

  /** Maximum water relative permeability (at 1-Sorw). */
  private double krwMax = 1.0;

  /** Maximum gas relative permeability (at 1-Swc-Sorg). */
  private double krgMax = 1.0;

  // ==================== COREY EXPONENTS ====================

  /** Oil Corey exponent (for water-oil system). */
  private double no = 2.0;

  /** Water Corey exponent. */
  private double nw = 2.0;

  /** Gas Corey exponent. */
  private double ng = 2.0;

  /** Oil Corey exponent (for gas-oil system). */
  private double nog = 2.0;

  // ==================== LET PARAMETERS (WATER-OIL) ====================

  /** Oil L parameter (LET model, water-oil). */
  private double Lo = 2.0;

  /** Oil E parameter (LET model, water-oil). */
  private double Eo = 1.0;

  /** Oil T parameter (LET model, water-oil). */
  private double To = 2.0;

  /** Water L parameter (LET model). */
  private double Lw = 2.0;

  /** Water E parameter (LET model). */
  private double Ew = 1.0;

  /** Water T parameter (LET model). */
  private double Tw = 2.0;

  // ==================== LET PARAMETERS (GAS-OIL) ====================

  /** Gas L parameter (LET model). */
  private double Lg = 2.0;

  /** Gas E parameter (LET model). */
  private double Eg = 1.0;

  /** Gas T parameter (LET model). */
  private double Tg = 2.0;

  /** Oil L parameter (LET model, gas-oil system). */
  private double Log = 2.0;

  /** Oil E parameter (LET model, gas-oil system). */
  private double Eog = 1.0;

  /** Oil T parameter (LET model, gas-oil system). */
  private double Tog = 2.0;

  /**
   * Construct a new RelativePermeabilityGenerator with default parameters.
   */
  public RelativePermeabilityGenerator() {}

  /**
   * Generate the relative permeability table.
   *
   * <p>
   * Returns a map of column names to arrays of values. Column names depend on the table type:
   * <ul>
   * <li>SWOF: Sw, Krw, Krow, Pcow</li>
   * <li>SGOF: Sg, Krg, Krog, Pcog</li>
   * <li>SOF3: So, Krow, Krog</li>
   * <li>SLGOF: Sl, Krg, Krog, Pcog</li>
   * </ul>
   *
   * @return Map of column name to double arrays
   */
  public Map<String, double[]> generate() {
    switch (tableType) {
      case SWOF:
        return generateSWOF();
      case SGOF:
        return generateSGOF();
      case SOF3:
        return generateSOF3();
      case SLGOF:
        return generateSLGOF();
      default:
        throw new IllegalStateException("Unknown table type: " + tableType);
    }
  }

  /**
   * Generate the table and return it as a formatted Eclipse keyword string.
   *
   * @return Eclipse-format keyword string (e.g., SWOF table)
   */
  public String toEclipseKeyword() {
    Map<String, double[]> table = generate();
    StringBuilder sb = new StringBuilder();

    sb.append(tableType.name()).append('\n');

    String[] columns = table.keySet().toArray(new String[0]);
    sb.append("-- ");
    for (int c = 0; c < columns.length; c++) {
      if (c > 0) {
        sb.append("       ");
      }
      sb.append(columns[c]);
    }
    sb.append('\n');

    int nRows = table.get(columns[0]).length;
    for (int i = 0; i < nRows; i++) {
      sb.append("   ");
      for (int c = 0; c < columns.length; c++) {
        if (c > 0) {
          sb.append("  ");
        }
        sb.append(String.format("%.8f", table.get(columns[c])[i]));
      }
      sb.append('\n');
    }
    sb.append("/\n");

    return sb.toString();
  }

  // ==================== TABLE GENERATION METHODS ====================

  /**
   * Generate a SWOF (Water-Oil) table.
   *
   * @return map with columns Sw, Krw, Krow, Pcow
   */
  private Map<String, double[]> generateSWOF() {
    double effectiveSwcr = (swcr < 0) ? swc : swcr;
    double maxSw = 1.0 - sorw;

    double[] sw = linspace(swc, maxSw, rows);
    double[] krw = new double[rows];
    double[] krow = new double[rows];
    double[] pcow = new double[rows];

    for (int i = 0; i < rows; i++) {
      double swnKrw = normalizeWaterForKrw(sw[i], effectiveSwcr, sorw);
      double swnKro = normalizeWaterForKrow(sw[i], swc, sorw);

      if (modelFamily == RelPermModelFamily.LET) {
        krw[i] = krwMax * letCurve(swnKrw, Lw, Ew, Tw);
        krow[i] = kroMax * letCurve(1.0 - swnKro, Lo, Eo, To);
      } else {
        krw[i] = krwMax * coreyCurve(swnKrw, nw);
        krow[i] = kroMax * coreyCurve(1.0 - swnKro, no);
      }
      pcow[i] = 0.0;
    }

    // Enforce endpoint constraints
    krw[0] = 0.0;
    krow[0] = kroMax;
    krw[rows - 1] = krwMax;
    krow[rows - 1] = 0.0;

    Map<String, double[]> result = new LinkedHashMap<String, double[]>();
    result.put("Sw", sw);
    result.put("Krw", krw);
    result.put("Krow", krow);
    result.put("Pcow", pcow);
    return result;
  }

  /**
   * Generate a SGOF (Gas-Oil) table.
   *
   * @return map with columns Sg, Krg, Krog, Pcog
   */
  private Map<String, double[]> generateSGOF() {
    double maxSg = 1.0 - swc - sorg;

    double[] sg = linspace(0.0, maxSg, rows);
    double[] krg = new double[rows];
    double[] krog = new double[rows];
    double[] pcog = new double[rows];

    for (int i = 0; i < rows; i++) {
      double sgnKrg = normalizeGasForKrg(sg[i], sgcr, swc, sorg);
      double sgnKro = normalizeGasForKrog(sg[i], sgcr, swc, sorg);

      if (modelFamily == RelPermModelFamily.LET) {
        krg[i] = krgMax * letCurve(sgnKrg, Lg, Eg, Tg);
        krog[i] = kroMax * letCurve(1.0 - sgnKro, Log, Eog, Tog);
      } else {
        krg[i] = krgMax * coreyCurve(sgnKrg, ng);
        krog[i] = kroMax * coreyCurve(1.0 - sgnKro, nog);
      }
      pcog[i] = 0.0;
    }

    // Enforce endpoint constraints
    krg[0] = 0.0;
    krog[0] = kroMax;
    krg[rows - 1] = krgMax;
    krog[rows - 1] = 0.0;

    Map<String, double[]> result = new LinkedHashMap<String, double[]>();
    result.put("Sg", sg);
    result.put("Krg", krg);
    result.put("Krog", krog);
    result.put("Pcog", pcog);
    return result;
  }

  /**
   * Generate a SOF3 (three-phase oil) table.
   *
   * @return map with columns So, Krow, Krog
   */
  private Map<String, double[]> generateSOF3() {
    double minSo = Math.max(sorw, sorg);
    double maxSo = 1.0 - swc;

    double[] so = linspace(minSo, maxSo, rows);
    double[] krow = new double[rows];
    double[] krog = new double[rows];

    for (int i = 0; i < rows; i++) {
      // Krow(So): So normalized between Sorw and 1-Swc
      double sonW = (so[i] - sorw) / (1.0 - swc - sorw);
      sonW = clamp(sonW);
      // Krog(So): So normalized between Sorg and 1-Swc
      double sonG = (so[i] - sorg) / (1.0 - swc - sorg);
      sonG = clamp(sonG);

      if (modelFamily == RelPermModelFamily.LET) {
        krow[i] = kroMax * letCurve(sonW, Lo, Eo, To);
        krog[i] = kroMax * letCurve(sonG, Log, Eog, Tog);
      } else {
        krow[i] = kroMax * coreyCurve(sonW, no);
        krog[i] = kroMax * coreyCurve(sonG, nog);
      }
    }

    krow[0] = 0.0;
    krog[0] = 0.0;
    krow[rows - 1] = kroMax;
    krog[rows - 1] = kroMax;

    Map<String, double[]> result = new LinkedHashMap<String, double[]>();
    result.put("So", so);
    result.put("Krow", krow);
    result.put("Krog", krog);
    return result;
  }

  /**
   * Generate a SLGOF (Liquid-Gas) table.
   *
   * @return map with columns Sl, Krg, Krog, Pcog
   */
  private Map<String, double[]> generateSLGOF() {
    double minSl = swc + sorg;
    double maxSl = 1.0;

    double[] sl = linspace(minSl, maxSl, rows);
    double[] krg = new double[rows];
    double[] krog = new double[rows];
    double[] pcog = new double[rows];

    for (int i = 0; i < rows; i++) {
      double sg = 1.0 - sl[i];
      double sgnKrg = normalizeGasForKrg(sg, sgcr, swc, sorg);
      double sgnKro = normalizeGasForKrog(sg, sgcr, swc, sorg);

      if (modelFamily == RelPermModelFamily.LET) {
        krg[i] = krgMax * letCurve(sgnKrg, Lg, Eg, Tg);
        krog[i] = kroMax * letCurve(1.0 - sgnKro, Log, Eog, Tog);
      } else {
        krg[i] = krgMax * coreyCurve(sgnKrg, ng);
        krog[i] = kroMax * coreyCurve(1.0 - sgnKro, nog);
      }
      pcog[i] = 0.0;
    }

    // Endpoints: at Sl=minSl (max gas), at Sl=1 (no gas)
    krg[0] = krgMax;
    krog[0] = 0.0;
    krg[rows - 1] = 0.0;
    krog[rows - 1] = kroMax;

    Map<String, double[]> result = new LinkedHashMap<String, double[]>();
    result.put("Sl", sl);
    result.put("Krg", krg);
    result.put("Krog", krog);
    result.put("Pcog", pcog);
    return result;
  }

  // ==================== CORE MODEL FUNCTIONS ====================

  /**
   * Corey power-law relative permeability curve.
   *
   * <p>
   * Calculates the normalized relative permeability using the Corey (1954) model:
   *
   * $$ K_{r,norm} = S_n^n $$
   *
   * @param sn Normalized saturation in range [0, 1]
   * @param n Corey exponent (must be positive)
   * @return Normalized relative permeability [0, 1]
   */
  static double coreyCurve(double sn, double n) {
    double s = clamp(sn);
    return Math.pow(s, n);
  }

  /**
   * LET three-parameter relative permeability curve.
   *
   * <p>
   * Lomeland-Ebeltoft-Thomas (2005) model:
   *
   * $$ K_{r,norm} = \frac{S_n^L}{S_n^L + E \cdot (1 - S_n)^T} $$
   *
   * @param sn Normalized saturation in range [0, 1]
   * @param l L parameter (controls low-saturation curvature, must be positive)
   * @param e E parameter (controls mid-range position, must be positive)
   * @param t T parameter (controls high-saturation curvature, must be positive)
   * @return Normalized relative permeability [0, 1]
   */
  static double letCurve(double sn, double l, double e, double t) {
    double s = clamp(sn);
    if (s <= 0.0) {
      return 0.0;
    }
    if (s >= 1.0) {
      return 1.0;
    }
    double numerator = Math.pow(s, l);
    double denominator = numerator + e * Math.pow(1.0 - s, t);
    if (denominator <= 0.0) {
      return 0.0;
    }
    return numerator / denominator;
  }

  // ==================== SATURATION NORMALIZATION ====================

  /**
   * Normalize water saturation for water relative permeability.
   *
   * @param sw Water saturation
   * @param swcr Critical water saturation
   * @param sorw Residual oil saturation to water
   * @return Normalized water saturation [0, 1]
   */
  private static double normalizeWaterForKrw(double sw, double swcr, double sorw) {
    double denom = 1.0 - swcr - sorw;
    if (denom <= 0.0) {
      return 0.0;
    }
    return clamp((sw - swcr) / denom);
  }

  /**
   * Normalize water saturation for oil relative permeability in water-oil system.
   *
   * @param sw Water saturation
   * @param swc Connate water saturation
   * @param sorw Residual oil saturation to water
   * @return Normalized water saturation [0, 1]
   */
  private static double normalizeWaterForKrow(double sw, double swc, double sorw) {
    double denom = 1.0 - swc - sorw;
    if (denom <= 0.0) {
      return 0.0;
    }
    return clamp((sw - swc) / denom);
  }

  /**
   * Normalize gas saturation for gas relative permeability.
   *
   * @param sg Gas saturation
   * @param sgcr Critical gas saturation
   * @param swc Connate water saturation
   * @param sorg Residual oil saturation to gas
   * @return Normalized gas saturation [0, 1]
   */
  private static double normalizeGasForKrg(double sg, double sgcr, double swc, double sorg) {
    double denom = 1.0 - swc - sgcr - sorg;
    if (denom <= 0.0) {
      return 0.0;
    }
    return clamp((sg - sgcr) / denom);
  }

  /**
   * Normalize gas saturation for oil relative permeability in gas-oil system.
   *
   * @param sg Gas saturation
   * @param sgcr Critical gas saturation
   * @param swc Connate water saturation
   * @param sorg Residual oil saturation to gas
   * @return Normalized gas saturation [0, 1]
   */
  private static double normalizeGasForKrog(double sg, double sgcr, double swc, double sorg) {
    double denom = 1.0 - swc - sorg;
    if (denom <= 0.0) {
      return 0.0;
    }
    return clamp(sg / denom);
  }

  // ==================== UTILITY METHODS ====================

  /**
   * Clamp a value to [0, 1] range.
   *
   * @param val Value to clamp
   * @return Clamped value
   */
  private static double clamp(double val) {
    if (val < 0.0) {
      return 0.0;
    }
    if (val > 1.0) {
      return 1.0;
    }
    return val;
  }

  /**
   * Generate an array of evenly spaced values from start to end (inclusive).
   *
   * @param start First value
   * @param end Last value
   * @param n Number of points
   * @return Array of evenly spaced values
   */
  private static double[] linspace(double start, double end, int n) {
    if (n < 2) {
      return new double[] {start};
    }
    double[] result = new double[n];
    double step = (end - start) / (n - 1);
    for (int i = 0; i < n; i++) {
      result[i] = start + i * step;
    }
    result[n - 1] = end; // Ensure exact endpoint
    return result;
  }

  /**
   * Get number of table rows.
   *
   * @return Number of rows
   */
  public int getRows() {
    return rows;
  }

  /**
   * Set number of table rows.
   *
   * @param rows Number of rows (must be at least 3)
   */
  public void setRows(int rows) {
    if (rows < 3) {
      throw new IllegalArgumentException("Number of rows must be at least 3, got " + rows);
    }
    this.rows = rows;
  }

  /**
   * Get the table type.
   *
   * @return Table type
   */
  public RelPermTableType getTableType() {
    return tableType;
  }

  /**
   * Set the table type.
   *
   * @param tableType Table type (SWOF, SGOF, SOF3, SLGOF)
   */
  public void setTableType(RelPermTableType tableType) {
    this.tableType = tableType;
  }

  /**
   * Get the model family.
   *
   * @return Model family (COREY or LET)
   */
  public RelPermModelFamily getModelFamily() {
    return modelFamily;
  }

  /**
   * Set the model family.
   *
   * @param modelFamily Model family (COREY or LET)
   */
  public void setModelFamily(RelPermModelFamily modelFamily) {
    this.modelFamily = modelFamily;
  }

  /**
   * Get connate water saturation.
   *
   * @return Connate water saturation
   */
  public double getSwc() {
    return swc;
  }

  /**
   * Set connate (irreducible) water saturation.
   *
   * @param swc Connate water saturation [0, 1]
   */
  public void setSwc(double swc) {
    this.swc = swc;
  }

  /**
   * Get critical water saturation.
   *
   * @return Critical water saturation (-1 if using swc)
   */
  public double getSwcr() {
    return swcr;
  }

  /**
   * Set critical water saturation (onset of water flow). If not set, defaults to Swc.
   *
   * @param swcr Critical water saturation [0, 1]
   */
  public void setSwcr(double swcr) {
    this.swcr = swcr;
  }

  /**
   * Get residual oil saturation to water.
   *
   * @return Residual oil saturation to water
   */
  public double getSorw() {
    return sorw;
  }

  /**
   * Set residual oil saturation to water.
   *
   * @param sorw Residual oil saturation to water [0, 1]
   */
  public void setSorw(double sorw) {
    this.sorw = sorw;
  }

  /**
   * Get residual oil saturation to gas.
   *
   * @return Residual oil saturation to gas
   */
  public double getSorg() {
    return sorg;
  }

  /**
   * Set residual oil saturation to gas.
   *
   * @param sorg Residual oil saturation to gas [0, 1]
   */
  public void setSorg(double sorg) {
    this.sorg = sorg;
  }

  /**
   * Get critical gas saturation.
   *
   * @return Critical gas saturation
   */
  public double getSgcr() {
    return sgcr;
  }

  /**
   * Set critical gas saturation.
   *
   * @param sgcr Critical gas saturation [0, 1]
   */
  public void setSgcr(double sgcr) {
    this.sgcr = sgcr;
  }

  /**
   * Get maximum oil relative permeability.
   *
   * @return Maximum oil relative permeability
   */
  public double getKroMax() {
    return kroMax;
  }

  /**
   * Set maximum oil relative permeability.
   *
   * @param kroMax Maximum oil relative permeability [0, 1]
   */
  public void setKroMax(double kroMax) {
    this.kroMax = kroMax;
  }

  /**
   * Get maximum water relative permeability.
   *
   * @return Maximum water relative permeability
   */
  public double getKrwMax() {
    return krwMax;
  }

  /**
   * Set maximum water relative permeability.
   *
   * @param krwMax Maximum water relative permeability [0, 1]
   */
  public void setKrwMax(double krwMax) {
    this.krwMax = krwMax;
  }

  /**
   * Get maximum gas relative permeability.
   *
   * @return Maximum gas relative permeability
   */
  public double getKrgMax() {
    return krgMax;
  }

  /**
   * Set maximum gas relative permeability.
   *
   * @param krgMax Maximum gas relative permeability [0, 1]
   */
  public void setKrgMax(double krgMax) {
    this.krgMax = krgMax;
  }

  /**
   * Get oil Corey exponent (water-oil).
   *
   * @return Oil Corey exponent
   */
  public double getNo() {
    return no;
  }

  /**
   * Set oil Corey exponent (for water-oil system).
   *
   * @param no Oil Corey exponent (typically 1-6)
   */
  public void setNo(double no) {
    this.no = no;
  }

  /**
   * Get water Corey exponent.
   *
   * @return Water Corey exponent
   */
  public double getNw() {
    return nw;
  }

  /**
   * Set water Corey exponent.
   *
   * @param nw Water Corey exponent (typically 1-6)
   */
  public void setNw(double nw) {
    this.nw = nw;
  }

  /**
   * Get gas Corey exponent.
   *
   * @return Gas Corey exponent
   */
  public double getNg() {
    return ng;
  }

  /**
   * Set gas Corey exponent.
   *
   * @param ng Gas Corey exponent (typically 1-6)
   */
  public void setNg(double ng) {
    this.ng = ng;
  }

  /**
   * Get oil Corey exponent (gas-oil).
   *
   * @return Oil Corey exponent for gas-oil system
   */
  public double getNog() {
    return nog;
  }

  /**
   * Set oil Corey exponent (for gas-oil system).
   *
   * @param nog Oil Corey exponent for gas-oil system (typically 1-6)
   */
  public void setNog(double nog) {
    this.nog = nog;
  }

  /**
   * Get oil L parameter (water-oil LET).
   *
   * @return Oil L parameter
   */
  public double getLo() {
    return Lo;
  }

  /**
   * Set oil L parameter (LET model, water-oil system).
   *
   * @param lo Oil L parameter (positive)
   */
  public void setLo(double lo) {
    this.Lo = lo;
  }

  /**
   * Get oil E parameter (water-oil LET).
   *
   * @return Oil E parameter
   */
  public double getEo() {
    return Eo;
  }

  /**
   * Set oil E parameter (LET model, water-oil system).
   *
   * @param eo Oil E parameter (positive)
   */
  public void setEo(double eo) {
    this.Eo = eo;
  }

  /**
   * Get oil T parameter (water-oil LET).
   *
   * @return Oil T parameter
   */
  public double getTo() {
    return To;
  }

  /**
   * Set oil T parameter (LET model, water-oil system).
   *
   * @param to Oil T parameter (positive)
   */
  public void setTo(double to) {
    this.To = to;
  }

  /**
   * Get water L parameter (LET).
   *
   * @return Water L parameter
   */
  public double getLw() {
    return Lw;
  }

  /**
   * Set water L parameter (LET model).
   *
   * @param lw Water L parameter (positive)
   */
  public void setLw(double lw) {
    this.Lw = lw;
  }

  /**
   * Get water E parameter (LET).
   *
   * @return Water E parameter
   */
  public double getEw() {
    return Ew;
  }

  /**
   * Set water E parameter (LET model).
   *
   * @param ew Water E parameter (positive)
   */
  public void setEw(double ew) {
    this.Ew = ew;
  }

  /**
   * Get water T parameter (LET).
   *
   * @return Water T parameter
   */
  public double getTw() {
    return Tw;
  }

  /**
   * Set water T parameter (LET model).
   *
   * @param tw Water T parameter (positive)
   */
  public void setTw(double tw) {
    this.Tw = tw;
  }

  /**
   * Get gas L parameter (LET).
   *
   * @return Gas L parameter
   */
  public double getLg() {
    return Lg;
  }

  /**
   * Set gas L parameter (LET model).
   *
   * @param lg Gas L parameter (positive)
   */
  public void setLg(double lg) {
    this.Lg = lg;
  }

  /**
   * Get gas E parameter (LET).
   *
   * @return Gas E parameter
   */
  public double getEg() {
    return Eg;
  }

  /**
   * Set gas E parameter (LET model).
   *
   * @param eg Gas E parameter (positive)
   */
  public void setEg(double eg) {
    this.Eg = eg;
  }

  /**
   * Get gas T parameter (LET).
   *
   * @return Gas T parameter
   */
  public double getTg() {
    return Tg;
  }

  /**
   * Set gas T parameter (LET model).
   *
   * @param tg Gas T parameter (positive)
   */
  public void setTg(double tg) {
    this.Tg = tg;
  }

  /**
   * Get oil L parameter (gas-oil LET).
   *
   * @return Oil L parameter for gas-oil system
   */
  public double getLog() {
    return Log;
  }

  /**
   * Set oil L parameter (LET model, gas-oil system).
   *
   * @param log Oil L parameter for gas-oil (positive)
   */
  public void setLog(double log) {
    this.Log = log;
  }

  /**
   * Get oil E parameter (gas-oil LET).
   *
   * @return Oil E parameter for gas-oil system
   */
  public double getEog() {
    return Eog;
  }

  /**
   * Set oil E parameter (LET model, gas-oil system).
   *
   * @param eog Oil E parameter for gas-oil (positive)
   */
  public void setEog(double eog) {
    this.Eog = eog;
  }

  /**
   * Get oil T parameter (gas-oil LET).
   *
   * @return Oil T parameter for gas-oil system
   */
  public double getTog() {
    return Tog;
  }

  /**
   * Set oil T parameter (LET model, gas-oil system).
   *
   * @param tog Oil T parameter for gas-oil (positive)
   */
  public void setTog(double tog) {
    this.Tog = tog;
  }
}
