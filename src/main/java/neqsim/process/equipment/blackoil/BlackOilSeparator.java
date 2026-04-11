package neqsim.process.equipment.blackoil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import neqsim.blackoil.BlackOilFlashResult;
import neqsim.blackoil.SystemBlackOil;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

/**
 * Equilibrium Black-Oil separator at given (P_out, T_out).
 *
 * <p>
 * Extends {@link ProcessEquipmentBaseClass} so it can be added to a
 * {@link neqsim.process.processmodel.ProcessSystem} and participate in sequential flowsheet
 * execution alongside compositional equipment.
 * </p>
 *
 * <p>
 * Inlet and outlet fluids are represented as {@link SystemBlackOil} objects (not
 * {@link neqsim.thermo.system.SystemInterface}). Access them via {@link #getBlackOilInlet()},
 * {@link #getOilOut()}, {@link #getGasOut()}, {@link #getWaterOut()}.
 * </p>
 *
 * @author esol
 * @version 2.0
 */
public class BlackOilSeparator extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  private SystemBlackOil inlet;
  private double outletPressure;
  private double outletTemperature;
  private SystemBlackOil oilOut;
  private SystemBlackOil gasOut;
  private SystemBlackOil waterOut;
  private BlackOilFlashResult lastResult;

  /**
   * Constructs a BlackOilSeparator.
   *
   * @param name equipment name used in the process system
   * @param inlet the inlet Black-Oil fluid
   * @param outletPressure separator outlet pressure in bar(a)
   * @param outletTemperature separator outlet temperature in Kelvin
   */
  public BlackOilSeparator(String name, SystemBlackOil inlet, double outletPressure,
      double outletTemperature) {
    super(name);
    this.inlet = inlet;
    this.outletPressure = outletPressure;
    this.outletTemperature = outletTemperature;
  }

  /**
   * Sets the inlet Black-Oil fluid.
   *
   * @param inlet a {@link SystemBlackOil} object
   */
  public void setInlet(SystemBlackOil inlet) {
    this.inlet = inlet;
  }

  /**
   * Returns the inlet Black-Oil fluid.
   *
   * @return a {@link SystemBlackOil} object
   */
  public SystemBlackOil getBlackOilInlet() {
    return inlet;
  }

  /**
   * Returns the oil outlet stream after separation.
   *
   * @return a {@link SystemBlackOil} object, or null if not yet run
   */
  public SystemBlackOil getOilOut() {
    return oilOut;
  }

  /**
   * Returns the gas outlet stream after separation.
   *
   * @return a {@link SystemBlackOil} object, or null if not yet run
   */
  public SystemBlackOil getGasOut() {
    return gasOut;
  }

  /**
   * Returns the water outlet stream after separation.
   *
   * @return a {@link SystemBlackOil} object, or null if not yet run
   */
  public SystemBlackOil getWaterOut() {
    return waterOut;
  }

  /**
   * Returns the last flash result from the most recent run.
   *
   * @return a {@link BlackOilFlashResult} object, or null if not yet run
   */
  public BlackOilFlashResult getLastFlashResult() {
    return lastResult;
  }

  /**
   * Returns the separator outlet pressure.
   *
   * @return outlet pressure in bar(a)
   */
  public double getOutletPressure() {
    return outletPressure;
  }

  /**
   * Sets the separator outlet pressure.
   *
   * @param outletPressure outlet pressure in bar(a)
   */
  public void setOutletPressure(double outletPressure) {
    this.outletPressure = outletPressure;
  }

  /**
   * Returns the separator outlet temperature.
   *
   * @return outlet temperature in Kelvin
   */
  public double getOutletTemperature() {
    return outletTemperature;
  }

  /**
   * Sets the separator outlet temperature.
   *
   * @param outletTemperature outlet temperature in Kelvin
   */
  public void setOutletTemperature(double outletTemperature) {
    this.outletTemperature = outletTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    inlet.setPressure(outletPressure);
    inlet.setTemperature(outletTemperature);
    BlackOilFlashResult r = inlet.flash();
    this.lastResult = r;

    oilOut = inlet.copyShallow();
    gasOut = inlet.copyShallow();
    waterOut = inlet.copyShallow();

    double oilOilStd = r.O_std;
    double oilGasStd = r.Rs * r.O_std;
    oilOut.setStdTotals(oilOilStd, oilGasStd, 0.0);
    oilOut.setPressure(outletPressure);
    oilOut.setTemperature(outletTemperature);

    double gasGasStd = r.Gf_std;
    double gasOilStd = r.Rv * r.Gf_std;
    gasOut.setStdTotals(gasOilStd, gasGasStd, 0.0);
    gasOut.setPressure(outletPressure);
    gasOut.setTemperature(outletTemperature);

    waterOut.setStdTotals(0.0, 0.0, r.W_std);
    waterOut.setPressure(outletPressure);
    waterOut.setTemperature(outletTemperature);
  }

  /**
   * Legacy run method for backward compatibility (no UUID tracking).
   */
  public void runSeparation() {
    run(UUID.randomUUID());
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    JsonObject json = new JsonObject();
    json.addProperty("name", getName());
    json.addProperty("type", "BlackOilSeparator");
    json.addProperty("outletPressure_bar", outletPressure);
    json.addProperty("outletTemperature_K", outletTemperature);

    if (inlet != null) {
      JsonObject inletJson = new JsonObject();
      inletJson.addProperty("oilStd_Sm3", inlet.getOilStdTotal());
      inletJson.addProperty("gasStd_Sm3", inlet.getGasStdTotal());
      inletJson.addProperty("waterStd_Sm3", inlet.getWaterStd());
      json.add("inlet", inletJson);
    }

    if (lastResult != null) {
      JsonObject resultJson = new JsonObject();
      resultJson.addProperty("Rs_Sm3_Sm3", lastResult.Rs);
      resultJson.addProperty("Rv_Sm3_Sm3", lastResult.Rv);
      resultJson.addProperty("Bo_rm3_Sm3", lastResult.Bo);
      resultJson.addProperty("Bg_rm3_Sm3", lastResult.Bg);
      resultJson.addProperty("Bw_rm3_Sm3", lastResult.Bw);
      resultJson.addProperty("oilDensity_kg_m3", lastResult.rho_o);
      resultJson.addProperty("gasDensity_kg_m3", lastResult.rho_g);
      resultJson.addProperty("waterDensity_kg_m3", lastResult.rho_w);
      resultJson.addProperty("oilViscosity_Pas", lastResult.mu_o);
      resultJson.addProperty("gasViscosity_Pas", lastResult.mu_g);
      resultJson.addProperty("waterViscosity_Pas", lastResult.mu_w);
      resultJson.addProperty("oilVolume_m3", lastResult.V_o);
      resultJson.addProperty("gasVolume_m3", lastResult.V_g);
      resultJson.addProperty("waterVolume_m3", lastResult.V_w);
      json.add("flashResult", resultJson);
    }

    if (oilOut != null) {
      JsonObject oilJson = new JsonObject();
      oilJson.addProperty("oilStd_Sm3", oilOut.getOilStdTotal());
      oilJson.addProperty("gasStd_Sm3", oilOut.getGasStdTotal());
      json.add("oilOutlet", oilJson);
    }
    if (gasOut != null) {
      JsonObject gasJson = new JsonObject();
      gasJson.addProperty("oilStd_Sm3", gasOut.getOilStdTotal());
      gasJson.addProperty("gasStd_Sm3", gasOut.getGasStdTotal());
      json.add("gasOutlet", gasJson);
    }
    if (waterOut != null) {
      JsonObject waterJson = new JsonObject();
      waterJson.addProperty("waterStd_Sm3", waterOut.getWaterStd());
      json.add("waterOutlet", waterJson);
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(json);
  }

  /**
   * Returns key results as a string map for reporting.
   *
   * @return map of result name to value string
   */
  public Map<String, String> getResultsMap() {
    Map<String, String> results = new LinkedHashMap<String, String>();
    results.put("name", getName());
    results.put("outlet pressure [bar]", Double.toString(outletPressure));
    results.put("outlet temperature [K]", Double.toString(outletTemperature));
    if (lastResult != null) {
      results.put("Bo [rm3/Sm3]", Double.toString(lastResult.Bo));
      results.put("Bg [rm3/Sm3]", Double.toString(lastResult.Bg));
      results.put("Rs [Sm3/Sm3]", Double.toString(lastResult.Rs));
      results.put("oil density [kg/m3]", Double.toString(lastResult.rho_o));
      results.put("gas density [kg/m3]", Double.toString(lastResult.rho_g));
      results.put("oil volume [m3]", Double.toString(lastResult.V_o));
      results.put("gas volume [m3]", Double.toString(lastResult.V_g));
      results.put("water volume [m3]", Double.toString(lastResult.V_w));
    }
    return results;
  }
}
