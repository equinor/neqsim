package neqsim.process.fielddevelopment.hybrid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import neqsim.process.fielddevelopment.integrated.ReservoirDrive;

/**
 * Tabulated reservoir-state proxy from one reservoir simulator run: reservoir pressure, water cut, gas-oil ratio,
 * productivity index and optional wellstream composition as functions of one cumulative volume.
 *
 * <p>
 * The table is built by post-processing Eclipse or OPM Flow summary results for a well or a drainage unit. Values are
 * interpolated linearly in the abscissa and held constant outside the table; {@link #isOutsideValidity()} then tells
 * that a new simulator run is needed. The underlying assumption is that the reservoir state seen by the well is a
 * function of the chosen cumulative volume only. That holds exactly for the simulator run the table came from and is an
 * approximation for any other production strategy; use {@link ScenarioReservoirState} to interpolate between several
 * runs.
 * </p>
 *
 * <p>
 * The class also implements {@link ReservoirDrive}, so a simulator-derived pressure table can drive the existing
 * {@code IntegratedProductionModel}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ReservoirStateProxy extends AbstractReservoirState implements ReservoirDrive {
  private static final long serialVersionUID = 1000L;
  private final CumulativeQuantity abscissaQuantity;
  private final double[] abscissa;
  private final double[] pressure;
  private double[] waterCut;
  private double[] gasOilRatio;
  private double[] productivityIndex;
  private String[] componentNames;
  private double[][] composition;
  private double[] hydrocarbonMolesPerSm3;
  private final Map<CumulativeQuantity, double[]> quantityColumns = new EnumMap<CumulativeQuantity, double[]>(
      CumulativeQuantity.class);

  /**
   * Creates a proxy with cumulative oil produced as abscissa.
   *
   * @param name name of the well or drainage unit
   * @param cumulativeOilSm3 cumulative oil produced in Sm3, strictly increasing, at least two values
   * @param pressureBara reservoir pressure in bara for each abscissa value
   */
  public ReservoirStateProxy(String name, double[] cumulativeOilSm3, double[] pressureBara) {
    this(name, CumulativeQuantity.OIL_PRODUCED, cumulativeOilSm3, pressureBara);
  }

  /**
   * Creates a proxy with a selected abscissa.
   *
   * @param name name of the well or drainage unit
   * @param abscissaQuantity cumulative volume used as abscissa
   * @param abscissaSm3 abscissa values in Sm3, strictly increasing, at least two values
   * @param pressureBara reservoir pressure in bara for each abscissa value
   * @throws IllegalArgumentException if the arrays are inconsistent or a pressure is not positive
   */
  public ReservoirStateProxy(String name, CumulativeQuantity abscissaQuantity, double[] abscissaSm3,
      double[] pressureBara) {
    super(name);
    if (abscissaQuantity == null || abscissaSm3 == null || pressureBara == null) {
      throw new IllegalArgumentException("Abscissa quantity, abscissa and pressure must not be null");
    }
    if (abscissaSm3.length < 2 || abscissaSm3.length != pressureBara.length) {
      throw new IllegalArgumentException("Abscissa and pressure need at least two values of equal count");
    }
    for (int i = 0; i < abscissaSm3.length; i++) {
      if (i > 0 && !(abscissaSm3[i] > abscissaSm3[i - 1])) {
        throw new IllegalArgumentException("Abscissa must be strictly increasing at index " + i);
      }
      if (!(pressureBara[i] > 0.0)) {
        throw new IllegalArgumentException("Pressure must be positive at index " + i);
      }
    }
    this.abscissaQuantity = abscissaQuantity;
    this.abscissa = abscissaSm3.clone();
    this.pressure = pressureBara.clone();
  }

  /**
   * Reads a proxy from a comma-separated file.
   *
   * <p>
   * The first non-comment line is a header. Recognised columns (case-insensitive): {@code cumulative} (required),
   * {@code pressure} (required), {@code waterCut}, {@code gor}, {@code pi}, {@code molesPerSm3}, a cumulative quantity
   * name such as {@code WATER_INJECTED}, and {@code z:<component>} for composition columns. Lines starting with
   * {@code #} are ignored.
   * </p>
   *
   * @param name name of the well or drainage unit
   * @param abscissaQuantity cumulative volume of the {@code cumulative} column
   * @param file file to read
   * @return the proxy
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if a required column is missing or a value is not a number
   */
  public static ReservoirStateProxy fromCsv(String name, CumulativeQuantity abscissaQuantity, Path file)
      throws IOException {
    List<String> lines = new ArrayList<String>();
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
        lines.add(trimmed);
      }
    }
    if (lines.size() < 3) {
      throw new IllegalArgumentException("Proxy file needs a header and at least two rows: " + file);
    }
    String[] header = lines.get(0).split(",");
    int rows = lines.size() - 1;
    double[][] columns = new double[header.length][rows];
    for (int r = 0; r < rows; r++) {
      String[] cells = lines.get(r + 1).split(",");
      if (cells.length != header.length) {
        throw new IllegalArgumentException(
            "Row " + (r + 2) + " has " + cells.length + " cells, expected " + header.length);
      }
      for (int c = 0; c < header.length; c++) {
        columns[c][r] = Double.parseDouble(cells[c].trim());
      }
    }
    int cumulativeIndex = -1;
    int pressureIndex = -1;
    for (int c = 0; c < header.length; c++) {
      String key = header[c].trim().toLowerCase(Locale.ROOT);
      if ("cumulative".equals(key)) {
        cumulativeIndex = c;
      } else if ("pressure".equals(key)) {
        pressureIndex = c;
      }
    }
    if (cumulativeIndex < 0 || pressureIndex < 0) {
      throw new IllegalArgumentException("Proxy file needs the columns cumulative and pressure: " + file);
    }
    ReservoirStateProxy proxy = new ReservoirStateProxy(name, abscissaQuantity, columns[cumulativeIndex],
        columns[pressureIndex]);
    List<String> components = new ArrayList<String>();
    List<double[]> fractions = new ArrayList<double[]>();
    double[] moles = null;
    for (int c = 0; c < header.length; c++) {
      String raw = header[c].trim();
      String key = raw.toLowerCase(Locale.ROOT);
      if ("watercut".equals(key)) {
        proxy.setWaterCut(columns[c]);
      } else if ("gor".equals(key)) {
        proxy.setGasOilRatio(columns[c]);
      } else if ("pi".equals(key)) {
        proxy.setProductivityIndex(columns[c]);
      } else if ("molespersm3".equals(key)) {
        moles = columns[c];
      } else if (key.startsWith("z:")) {
        components.add(raw.substring(2));
        fractions.add(columns[c]);
      } else if (c != cumulativeIndex && c != pressureIndex) {
        proxy.setQuantityColumn(CumulativeQuantity.valueOf(raw.toUpperCase(Locale.ROOT)), columns[c]);
      }
    }
    if (!components.isEmpty()) {
      double[][] z = new double[rows][components.size()];
      for (int r = 0; r < rows; r++) {
        for (int k = 0; k < components.size(); k++) {
          z[r][k] = fractions.get(k)[r];
        }
      }
      proxy.setComposition(components.toArray(new String[0]), z, moles);
    }
    return proxy;
  }

  /**
   * Sets the water cut column.
   *
   * @param values water fraction of the liquid for each abscissa value, each in [0, 1)
   * @return this proxy
   * @throws IllegalArgumentException if the length differs from the abscissa or a value is outside [0, 1)
   */
  public ReservoirStateProxy setWaterCut(double[] values) {
    checkColumn("water cut", values);
    for (double v : values) {
      if (!(v >= 0.0 && v < 1.0)) {
        throw new IllegalArgumentException("Water cut must be in [0, 1): " + v);
      }
    }
    this.waterCut = values.clone();
    return this;
  }

  /**
   * Sets the gas-oil ratio column.
   *
   * @param values producing gas-oil ratio in Sm3/Sm3 for each abscissa value
   * @return this proxy
   * @throws IllegalArgumentException if the length differs from the abscissa or a value is negative
   */
  public ReservoirStateProxy setGasOilRatio(double[] values) {
    checkColumn("gas-oil ratio", values);
    for (double v : values) {
      if (!(v >= 0.0)) {
        throw new IllegalArgumentException("Gas-oil ratio must not be negative: " + v);
      }
    }
    this.gasOilRatio = values.clone();
    return this;
  }

  /**
   * Sets the productivity index column.
   *
   * @param values oil productivity index in Sm3/(day bar) for each abscissa value
   * @return this proxy
   * @throws IllegalArgumentException if the length differs from the abscissa or a value is negative
   */
  public ReservoirStateProxy setProductivityIndex(double[] values) {
    checkColumn("productivity index", values);
    for (double v : values) {
      if (!(v >= 0.0)) {
        throw new IllegalArgumentException("Productivity index must not be negative: " + v);
      }
    }
    this.productivityIndex = values.clone();
    return this;
  }

  /**
   * Sets the hydrocarbon wellstream composition as a function of the abscissa.
   *
   * @param names component names
   * @param moleFractions mole fractions, one row per abscissa value and one column per component
   * @param molesPerSm3 mol hydrocarbon wellstream per Sm3 stock-tank oil for each abscissa value, or null
   * @return this proxy
   * @throws IllegalArgumentException if the dimensions are inconsistent
   */
  public ReservoirStateProxy setComposition(String[] names, double[][] moleFractions, double[] molesPerSm3) {
    if (names == null || moleFractions == null || moleFractions.length != abscissa.length) {
      throw new IllegalArgumentException("Composition needs one row per abscissa value");
    }
    double[][] copy = new double[moleFractions.length][];
    for (int r = 0; r < moleFractions.length; r++) {
      if (moleFractions[r] == null || moleFractions[r].length != names.length) {
        throw new IllegalArgumentException("Composition row " + r + " needs " + names.length + " values");
      }
      copy[r] = moleFractions[r].clone();
    }
    if (molesPerSm3 != null) {
      checkColumn("moles per Sm3", molesPerSm3);
    }
    this.componentNames = names.clone();
    this.composition = copy;
    this.hydrocarbonMolesPerSm3 = molesPerSm3 == null ? null : molesPerSm3.clone();
    return this;
  }

  /**
   * Records how another cumulative volume developed along the simulator run, for example the cumulative water injected
   * at each cumulative oil value. {@link ScenarioReservoirState} uses it to place the run among scenarios.
   *
   * @param quantity cumulative quantity
   * @param valuesSm3 cumulative volume in Sm3 for each abscissa value
   * @return this proxy
   * @throws IllegalArgumentException if the length differs from the abscissa
   */
  public ReservoirStateProxy setQuantityColumn(CumulativeQuantity quantity, double[] valuesSm3) {
    checkColumn(quantity.name(), valuesSm3);
    quantityColumns.put(quantity, valuesSm3.clone());
    return this;
  }

  /**
   * Checks the length of a column.
   *
   * @param label column label for the error message
   * @param values column values
   * @throws IllegalArgumentException if the column is null or has the wrong length
   */
  private void checkColumn(String label, double[] values) {
    if (values == null || values.length != abscissa.length) {
      throw new IllegalArgumentException("Column " + label + " needs " + abscissa.length + " values");
    }
  }

  /**
   * Interpolates a column linearly and holds the end values outside the table.
   *
   * @param column column values
   * @param x abscissa value in Sm3
   * @return interpolated value
   */
  private double interpolate(double[] column, double x) {
    int n = abscissa.length;
    if (!(x > abscissa[0])) {
      return column[0];
    }
    if (x >= abscissa[n - 1]) {
      return column[n - 1];
    }
    int lo = 0;
    int hi = n - 1;
    while (hi - lo > 1) {
      int mid = (lo + hi) >>> 1;
      if (abscissa[mid] <= x) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    double fraction = (x - abscissa[lo]) / (abscissa[hi] - abscissa[lo]);
    return column[lo] + fraction * (column[hi] - column[lo]);
  }

  /**
   * Gets the cumulative volume used as abscissa.
   *
   * @return abscissa quantity
   */
  public CumulativeQuantity getAbscissaQuantity() {
    return abscissaQuantity;
  }

  /**
   * Gets the largest abscissa value covered by the simulator run.
   *
   * @return last abscissa value in Sm3
   */
  public double getMaximumAbscissa() {
    return abscissa[abscissa.length - 1];
  }

  /**
   * Interpolates the reservoir pressure.
   *
   * @param x abscissa value in Sm3
   * @return reservoir pressure in bara
   */
  public double pressureAt(double x) {
    return interpolate(pressure, x);
  }

  /**
   * Interpolates the water cut.
   *
   * @param x abscissa value in Sm3
   * @return water fraction of the liquid, 0 without a water cut column
   */
  public double waterCutAt(double x) {
    return waterCut == null ? 0.0 : interpolate(waterCut, x);
  }

  /**
   * Interpolates the gas-oil ratio.
   *
   * @param x abscissa value in Sm3
   * @return gas-oil ratio in Sm3/Sm3, NaN without a gas-oil ratio column
   */
  public double gasOilRatioAt(double x) {
    return gasOilRatio == null ? Double.NaN : interpolate(gasOilRatio, x);
  }

  /**
   * Interpolates the productivity index.
   *
   * @param x abscissa value in Sm3
   * @return productivity index in Sm3/(day bar), NaN without a productivity index column
   */
  public double productivityIndexAt(double x) {
    return productivityIndex == null ? Double.NaN : interpolate(productivityIndex, x);
  }

  /**
   * Interpolates the composition.
   *
   * @param x abscissa value in Sm3
   * @return mole fractions normalised to one, or null without a composition
   */
  public double[] compositionAt(double x) {
    if (composition == null) {
      return null;
    }
    int n = componentNames.length;
    double[] z = new double[n];
    double[] column = new double[abscissa.length];
    double sum = 0.0;
    for (int k = 0; k < n; k++) {
      for (int r = 0; r < abscissa.length; r++) {
        column[r] = composition[r][k];
      }
      z[k] = interpolate(column, x);
      sum += z[k];
    }
    if (sum > 0.0) {
      for (int k = 0; k < n; k++) {
        z[k] /= sum;
      }
    }
    return z;
  }

  /**
   * Interpolates the hydrocarbon moles per stock-tank oil volume.
   *
   * @param x abscissa value in Sm3
   * @return mol per Sm3 oil, NaN without such a column
   */
  public double hydrocarbonMolesPerSm3At(double x) {
    return hydrocarbonMolesPerSm3 == null ? Double.NaN : interpolate(hydrocarbonMolesPerSm3, x);
  }

  /**
   * Tells whether a cumulative quantity column was recorded.
   *
   * @param quantity cumulative quantity
   * @return true if {@link #quantityAt(CumulativeQuantity, double)} has data
   */
  public boolean hasQuantityColumn(CumulativeQuantity quantity) {
    return quantityColumns.containsKey(quantity);
  }

  /**
   * Interpolates a recorded cumulative quantity along the simulator run.
   *
   * @param quantity cumulative quantity
   * @param x abscissa value in Sm3
   * @return cumulative volume in Sm3
   * @throws IllegalArgumentException if the quantity was not recorded
   */
  public double quantityAt(CumulativeQuantity quantity, double x) {
    double[] column = quantityColumns.get(quantity);
    if (column == null) {
      throw new IllegalArgumentException("Proxy " + getName() + " has no column " + quantity);
    }
    return interpolate(column, x);
  }

  /**
   * Tells whether an abscissa value lies beyond the simulator run.
   *
   * @param x abscissa value in Sm3
   * @return true if the value is above the last tabulated abscissa
   */
  public boolean isOutsideTable(double x) {
    return x > abscissa[abscissa.length - 1] * (1.0 + 1.0e-9);
  }

  /**
   * Gets the current abscissa value.
   *
   * @return current cumulative volume of the abscissa quantity in Sm3
   */
  public double getAbscissaValue() {
    return getCumulative(abscissaQuantity);
  }

  /** {@inheritDoc} */
  @Override
  public double getReservoirPressure() {
    return pressureAt(getAbscissaValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getWaterCut() {
    return waterCutAt(getAbscissaValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getGasOilRatio() {
    return gasOilRatioAt(getAbscissaValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getProductivityIndex() {
    return productivityIndexAt(getAbscissaValue());
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasComposition() {
    return composition != null;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getComponentNames() {
    return componentNames == null ? null : componentNames.clone();
  }

  /** {@inheritDoc} */
  @Override
  public double[] getComposition() {
    return compositionAt(getAbscissaValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getHydrocarbonMolesPerSm3() {
    return hydrocarbonMolesPerSm3At(getAbscissaValue());
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOutsideValidity() {
    return isOutsideTable(getAbscissaValue());
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * The volume is added to the abscissa quantity of the table.
   * </p>
   */
  @Override
  public void produce(double producedVolumeSm3, double dtDays) {
    setCumulative(abscissaQuantity, getCumulative(abscissaQuantity) + producedVolumeSm3);
  }

  /** {@inheritDoc} */
  @Override
  public double getCumulativeProduction() {
    return getAbscissaValue();
  }

  /** {@inheritDoc} */
  @Override
  public double getInPlaceVolume() {
    return getMaximumAbscissa();
  }
}
