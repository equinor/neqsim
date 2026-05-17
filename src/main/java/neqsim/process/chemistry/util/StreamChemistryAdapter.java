package neqsim.process.chemistry.util;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Adapter that extracts chemistry-relevant scalars from a NeqSim {@link StreamInterface} or
 * {@link SystemInterface}.
 *
 * <p>
 * The chemistry models in {@code neqsim.process.chemistry} historically took only scalar inputs (Ca
 * mg/L, H2S ppm, etc.). This adapter is the single bridge used by every performance and
 * compatibility model to pull those scalars from a real flowsheet stream so the manual
 * transcription step is eliminated.
 * </p>
 *
 * <p>
 * The adapter is robust: if a phase or component is missing the corresponding getter returns
 * {@code 0.0} (or {@code Double.NaN} for pH which is undefined without an aqueous phase). All
 * concentrations are reported in conventional oilfield units (mg/L, ppm, bara, Celsius).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class StreamChemistryAdapter implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Source stream (may be null if built from a SystemInterface directly). */
  private final transient StreamInterface stream;

  /** Source thermo system. */
  private final transient SystemInterface system;

  /**
   * Build an adapter from a NeqSim stream.
   *
   * @param stream a flowsheet stream that has been run
   */
  public StreamChemistryAdapter(StreamInterface stream) {
    this.stream = stream;
    this.system = stream == null ? null : stream.getFluid();
  }

  /**
   * Build an adapter from a thermo system directly.
   *
   * @param system a thermo system
   */
  public StreamChemistryAdapter(SystemInterface system) {
    this.stream = null;
    this.system = system;
  }

  /**
   * Returns the operating temperature in Celsius.
   *
   * @return temperature in Celsius
   */
  public double getTemperatureCelsius() {
    if (system == null) {
      return Double.NaN;
    }
    return system.getTemperature() - 273.15;
  }

  /**
   * Returns the operating pressure in bara.
   *
   * @return pressure in bara
   */
  public double getPressureBara() {
    if (system == null) {
      return Double.NaN;
    }
    return system.getPressure();
  }

  /**
   * Returns the partial pressure of a gas-phase component in bara.
   *
   * @param component component name (e.g. "CO2", "H2S")
   * @return partial pressure in bara, or 0 if no gas phase or component not present
   */
  public double getPartialPressureBara(String component) {
    if (system == null || !system.hasPhaseType("gas")) {
      return 0.0;
    }
    PhaseInterface gas = system.getPhase("gas");
    if (gas.hasComponent(component)) {
      return gas.getComponent(component).getx() * gas.getPressure();
    }
    return 0.0;
  }

  /**
   * Returns the molality of a component in the aqueous phase as mg/L of cation/anion.
   *
   * <p>
   * Approximates concentration assuming aqueous-phase density of 1000 kg/m3. For most
   * brine/produced-water concentrations this is within a few percent.
   * </p>
   *
   * @param component component name (e.g. "Ca++", "Na+", "Cl-", "HCO3-")
   * @param molarMassGmol molar mass of the species in g/mol
   * @return concentration in mg/L (0 if no aqueous phase or component absent)
   */
  public double getAqueousConcentrationMgL(String component, double molarMassGmol) {
    if (system == null || !system.hasPhaseType("aqueous")) {
      return 0.0;
    }
    PhaseInterface aq = system.getPhase("aqueous");
    if (!aq.hasComponent(component)) {
      return 0.0;
    }
    double moles = aq.getComponent(component).getNumberOfMolesInPhase();
    double waterMassKg = 0.0;
    if (aq.hasComponent("water")) {
      waterMassKg = aq.getComponent("water").getNumberOfMolesInPhase() * 18.015e-3;
    }
    if (waterMassKg <= 0.0) {
      return 0.0;
    }
    double waterVolumeL = waterMassKg; // 1 L water = 1 kg
    return moles * molarMassGmol * 1000.0 / waterVolumeL;
  }

  /**
   * Returns the calcium concentration of the aqueous phase in mg/L.
   *
   * @return Ca++ concentration in mg/L
   */
  public double getCalciumMgL() {
    return getAqueousConcentrationMgL("Ca++", 40.078);
  }

  /**
   * Returns the iron(II) concentration of the aqueous phase in mg/L.
   *
   * @return Fe++ concentration in mg/L
   */
  public double getIronMgL() {
    return getAqueousConcentrationMgL("Fe++", 55.845);
  }

  /**
   * Returns the bicarbonate concentration of the aqueous phase in mg/L.
   *
   * @return HCO3- concentration in mg/L
   */
  public double getBicarbonateMgL() {
    return getAqueousConcentrationMgL("HCO3-", 61.016);
  }

  /**
   * Returns the chloride concentration of the aqueous phase in mg/L.
   *
   * @return Cl- concentration in mg/L
   */
  public double getChlorideMgL() {
    return getAqueousConcentrationMgL("Cl-", 35.453);
  }

  /**
   * Returns the sodium concentration of the aqueous phase in mg/L.
   *
   * @return Na+ concentration in mg/L
   */
  public double getSodiumMgL() {
    return getAqueousConcentrationMgL("Na+", 22.99);
  }

  /**
   * Returns the sulphate concentration of the aqueous phase in mg/L.
   *
   * @return SO4-- concentration in mg/L
   */
  public double getSulphateMgL() {
    return getAqueousConcentrationMgL("SO4--", 96.06);
  }

  /**
   * Returns the barium concentration of the aqueous phase in mg/L.
   *
   * @return Ba++ concentration in mg/L
   */
  public double getBariumMgL() {
    return getAqueousConcentrationMgL("Ba++", 137.33);
  }

  /**
   * Returns the H2S concentration in the gas phase in ppm (mole/mole).
   *
   * @return H2S in ppm or 0 if no gas phase
   */
  public double getH2SInGasPpm() {
    if (system == null || !system.hasPhaseType("gas")) {
      return 0.0;
    }
    PhaseInterface gas = system.getPhase("gas");
    if (gas.hasComponent("H2S")) {
      return gas.getComponent("H2S").getx() * 1.0e6;
    }
    return 0.0;
  }

  /**
   * Returns the gas flow rate in Sm3/d.
   *
   * @return gas flow in Sm3/d, 0 if no stream or no gas
   */
  public double getGasFlowSm3PerDay() {
    if (stream == null) {
      return 0.0;
    }
    try {
      return stream.getFluid().getFlowRate("Sm3/sec") * 86400.0;
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Returns the total dissolved solids (TDS) of the aqueous phase as a rough sum of major ions.
   *
   * @return TDS in mg/L
   */
  public double getTdsMgL() {
    return getSodiumMgL() + getCalciumMgL() + getChlorideMgL() + getSulphateMgL()
        + getBicarbonateMgL() + getBariumMgL() + getIronMgL();
  }

  /**
   * Returns the wall shear stress estimate for a given pipe diameter and velocity, using a simple
   * Darcy-Weisbach approximation (f = 0.02). Returns 0 if no aqueous phase.
   *
   * @param pipeDiameterM pipe internal diameter in m
   * @param velocityMs flow velocity in m/s
   * @return wall shear stress in Pa
   */
  public double estimateWallShearStressPa(double pipeDiameterM, double velocityMs) {
    if (system == null) {
      return 0.0;
    }
    double rho = system.hasPhaseType("aqueous") ? system.getPhase("aqueous").getDensity() : 1000.0;
    double f = 0.02;
    return 0.125 * f * rho * velocityMs * velocityMs;
  }

  /**
   * Returns a snapshot map of all extracted chemistry scalars for diagnostics.
   *
   * @return ordered map of extracted values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("temperatureC", getTemperatureCelsius());
    map.put("pressureBara", getPressureBara());
    map.put("calciumMgL", getCalciumMgL());
    map.put("ironMgL", getIronMgL());
    map.put("bicarbonateMgL", getBicarbonateMgL());
    map.put("chlorideMgL", getChlorideMgL());
    map.put("sodiumMgL", getSodiumMgL());
    map.put("sulphateMgL", getSulphateMgL());
    map.put("bariumMgL", getBariumMgL());
    map.put("tdsMgL", getTdsMgL());
    map.put("co2PartialPressureBar", getPartialPressureBara("CO2"));
    map.put("h2sPartialPressureBar", getPartialPressureBara("H2S"));
    map.put("h2sInGasPpm", getH2SInGasPpm());
    map.put("gasFlowSm3PerDay", getGasFlowSm3PerDay());
    return map;
  }
}
