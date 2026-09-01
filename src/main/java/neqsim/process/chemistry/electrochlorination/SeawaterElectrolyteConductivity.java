package neqsim.process.chemistry.electrochlorination;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Seawater electrical conductivity and the ohmic contribution to the cell voltage of a seawater electrochlorination
 * (hypochlorite generator) cell.
 *
 * <p>
 * Electrochlorination packages are current-controlled: the transformer/rectifier holds a preset DC current and the cell
 * voltage floats with the resistivity of the seawater. Cold, slightly fresher water therefore raises the cell voltage
 * at constant hypochlorite output, which matters because both the vendor maximum cell voltage and the Ex (ATEX)
 * certification limit are fixed numbers. This class supplies the missing electrolyte property - electrical conductivity
 * - and converts it into a cell ohmic voltage so that design margin against those limits can be quantified.
 * </p>
 *
 * <p>
 * Conductivity uses the UNESCO/PSS-78 practical-salinity relation inverted for conductivity: the conductivity ratio R
 * is solved from the practical salinity S and the temperature/pressure polynomials of Perkin and Lewis (1980), and
 * scaled by the standard reference conductivity C(35, 15, 0) = 4.2914 S/m. Validity: S = 2 to 42, t = -2 to 35 C, p = 0
 * to 1000 bar.
 * </p>
 *
 * <p>
 * Standards / references: UNESCO Technical Papers in Marine Science 44 (1983); Perkin and Lewis, IEEE J. Ocean. Eng. 5
 * (1980) 9-16.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SeawaterElectrolyteConductivity implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Reference conductivity of standard seawater, C(35, 15, 0), in S/m. */
  public static final double REFERENCE_CONDUCTIVITY_S_PER_M = 4.2914;

  private double salinityPsu = 35.0;
  private double temperatureC = 15.0;
  private double pressureBara = 1.01325;

  private double conductivitySPerM = 0.0;
  private double resistivityOhmM = 0.0;
  private boolean evaluated = false;

  /** Creates a calculator at standard seawater conditions (S = 35, t = 15 C, atmospheric). */
  public SeawaterElectrolyteConductivity() {
  }

  /**
   * Sets the practical salinity.
   *
   * @param salinity practical salinity [PSU], valid 2 to 42
   * @return this calculator
   */
  public SeawaterElectrolyteConductivity setSalinityPsu(double salinity) {
    this.salinityPsu = salinity;
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the temperature.
   *
   * @param tC temperature [C], valid -2 to 35
   * @return this calculator
   */
  public SeawaterElectrolyteConductivity setTemperatureCelsius(double tC) {
    this.temperatureC = tC;
    this.evaluated = false;
    return this;
  }

  /**
   * Sets the absolute pressure.
   *
   * @param p pressure [bara]
   * @return this calculator
   */
  public SeawaterElectrolyteConductivity setPressureBara(double p) {
    this.pressureBara = p;
    this.evaluated = false;
    return this;
  }

  /**
   * Evaluates conductivity and resistivity at the specified state.
   *
   * @return this calculator
   */
  public SeawaterElectrolyteConductivity calculate() {
    double ratio = conductivityRatio(salinityPsu, temperatureC, pressureBara);
    conductivitySPerM = ratio * REFERENCE_CONDUCTIVITY_S_PER_M;
    resistivityOhmM = conductivitySPerM > 0.0 ? 1.0 / conductivitySPerM : Double.NaN;
    evaluated = true;
    return this;
  }

  /**
   * Ohmic voltage drop across an electrolyte gap carrying a given current.
   *
   * <p>
   * {@code V = I * rho * gap / area}. Use the total conducting area of the electrode pair and the electrode separation;
   * for a bipolar stack multiply by the number of elements in series.
   * </p>
   *
   * @param currentAmp cell current [A]
   * @param gapMetre electrode separation [m]
   * @param areaSquareMetre conducting electrode area [m2]
   * @param elementsInSeries number of bipolar elements electrically in series (>= 1)
   * @return ohmic voltage [V]
   */
  public double ohmicVoltage(double currentAmp, double gapMetre, double areaSquareMetre, int elementsInSeries) {
    if (!evaluated) {
      calculate();
    }
    if (areaSquareMetre <= 0.0 || elementsInSeries < 1) {
      return Double.NaN;
    }
    return currentAmp * resistivityOhmM * gapMetre / areaSquareMetre * elementsInSeries;
  }

  /**
   * Relative change in the ohmic voltage of a current-controlled cell when the seawater temperature changes from a
   * reference value to the current setting, all else equal.
   *
   * @param referenceTemperatureC reference seawater temperature [C]
   * @return ratio {@code rho(T) / rho(Tref)} [-]
   */
  public double ohmicVoltageRatioVersusTemperature(double referenceTemperatureC) {
    if (!evaluated) {
      calculate();
    }
    double refRatio = conductivityRatio(salinityPsu, referenceTemperatureC, pressureBara);
    double refConductivity = refRatio * REFERENCE_CONDUCTIVITY_S_PER_M;
    if (conductivitySPerM <= 0.0) {
      return Double.NaN;
    }
    return refConductivity / conductivitySPerM;
  }

  /**
   * Conductivity ratio {@code R = C(S,t,p) / C(35,15,0)} from the PSS-78 salinity algorithm, inverted numerically for R
   * at the given salinity.
   *
   * @param salinity practical salinity [PSU]
   * @param tC temperature [C]
   * @param pBara pressure [bara]
   * @return conductivity ratio [-]
   */
  private double conductivityRatio(double salinity, double tC, double pBara) {
    double lo = 0.0;
    double hi = 3.0;
    double r = 1.0;
    for (int i = 0; i < 120; i++) {
      r = 0.5 * (lo + hi);
      double s = practicalSalinity(r, tC, pBara);
      if (s < salinity) {
        lo = r;
      } else {
        hi = r;
      }
    }
    return r;
  }

  /**
   * PSS-78 practical salinity from the conductivity ratio, temperature and pressure.
   *
   * @param r conductivity ratio C(S,t,p)/C(35,15,0) [-]
   * @param tC temperature [C]
   * @param pBara absolute pressure [bara]
   * @return practical salinity [PSU]
   */
  private double practicalSalinity(double r, double tC, double pBara) {
    if (r <= 0.0) {
      return 0.0;
    }
    double pDbar = (pBara - 1.01325) * 10.0;
    double rp = 1.0 + (pDbar * (2.070e-5 + pDbar * (-6.370e-10 + pDbar * 3.989e-15)))
        / (1.0 + tC * (3.426e-2 + tC * 4.464e-4) + r * (4.215e-1 - 3.107e-3 * tC));
    double rt = 0.6766097 + tC * (2.00564e-2 + tC * (1.104259e-4 + tC * (-6.9698e-7 + tC * 1.0031e-9)));
    double bigR = r / (rp * rt);
    double sqrtR = Math.sqrt(bigR);
    double dt = tC - 15.0;
    double s = 0.0080 + sqrtR * (-0.1692 + sqrtR * (25.3851 + sqrtR * (14.0941 + sqrtR * (-7.0261 + sqrtR * 2.7081))));
    double ds = 0.0005
        + sqrtR * (-0.0056 + sqrtR * (-0.0066 + sqrtR * (-0.0375 + sqrtR * (0.0636 + sqrtR * (-0.0144)))));
    return s + ds * dt / (1.0 + 0.0162 * dt);
  }

  /**
   * Returns the electrical conductivity.
   *
   * @return conductivity [S/m]
   */
  public double getConductivitySPerM() {
    return conductivitySPerM;
  }

  /**
   * Returns the electrical resistivity.
   *
   * @return resistivity [ohm m]
   */
  public double getResistivityOhmM() {
    return resistivityOhmM;
  }

  /**
   * Returns true once {@link #calculate()} has been invoked.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns a structured map representation suitable for JSON serialisation.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("salinityPsu", salinityPsu);
    map.put("temperatureC", temperatureC);
    map.put("pressureBara", pressureBara);
    map.put("conductivitySPerM", conductivitySPerM);
    map.put("resistivityOhmM", resistivityOhmM);
    map.put("referenceConductivitySPerM", REFERENCE_CONDUCTIVITY_S_PER_M);
    map.put("standard", "UNESCO Technical Papers in Marine Science 44 (1983), PSS-78");
    return map;
  }

  /**
   * Returns a JSON representation of the evaluated state.
   *
   * @return JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
