package neqsim.process.equipment.flare;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * FlareStack: Combusts relief gas and computes heat release, emissions, radiation and tip
 * backpressure.
 *
 * <p>Radiation models:
 * <ul>
 * <li>POINT_SOURCE: q = chi_r * Q / (4 pi R^2)</li>
 * <li>CHAMBERLAIN: line-source style with emissive power, flame length, wind tilt, and atmospheric
 * attenuation.</li>
 * </ul>
 *
 * <p>Tip ΔP/backpressure:
 * <ul>
 * <li>ΔP_tip = K_tip * 0.5 * rho_exit * u_exit^2</li>
 * </ul>
 *
 * <p>NOTES:
 * <ul>
 * <li>Coefficients for Chamberlain are exposed so you can calibrate to your standard/vendor.</li>
 * <li>This is an engineering model; validate before use for regulatory work.</li>
 * </ul>
 */
public class FlareStack extends ProcessEquipmentBaseClass implements Serializable {
  private static final long serialVersionUID = 1L;

  // Connections
  private StreamInterface reliefInlet; // gas to be flared
  private StreamInterface airAssist; // optional
  private StreamInterface steamAssist; // optional

  // Tip/stack geometry
  private double tipDiameter_m = 0.3; // m
  private double tipElevation_m = 40.0; // m (centerline)

  // Ambient
  private double ambientTempK = 288.15;
  private double ambientPressBar = 1.01325;
  private double windSpeed10m = 2.0; // m/s (neutral stability)

  // Combustion parameters
  private double burningEfficiency = 0.985; // fraction of fuel oxidized
  private double radiantFraction = 0.18; // used in POINT_SOURCE
  private double so2Conversion = 1.0; // fraction S → SO2
  private double unburnedTHCFraction = 0.005; // mass fraction of C to THC
  private double coFraction = 0.003; // mass fraction of C to CO
  private double excessAirFrac = 0.0; // additional O2 in airAssist

  // Tip loss / backpressure model
  private double tipLossK = 1.0; // dimensionless K for tip
  private double tipBackpressureBar = 1.03; // computed each run()

  // Radiation model selection
  public enum RadiationModel {
    POINT_SOURCE,
    CHAMBERLAIN
  }

  private RadiationModel radiationModel = RadiationModel.POINT_SOURCE;

  // --- Chamberlain coefficients (tunable) ---
  // Emissive power (kW/m2): EP = epC * (Q_MW)^epA (typical epA ~ 0.12..0.2, epC ~ 140..220)
  private double ch_epC_kWm2 = 160.0;
  private double ch_epA = 0.12;

  // Flame length (m): L = lfC * (Q_MW)^lfA / (Dt_m)^lfB (typical lfA ~ 0.4..0.5, lfB ~ 0.2..0.3)
  private double ch_lfC = 12.0;
  private double ch_lfA = 0.45;
  private double ch_lfB = 0.20;

  // Tilt angle (rad): tan(theta) = kTilt * U / Vexit (kTilt ~ O(1))
  private double ch_kTilt = 1.3;

  // Atmospheric transmissivity: tau = exp(-ka * R) (ka ~ 0.005..0.02 1/m depending on humidity)
  private double ch_kAtten = 0.008;

  // Line integration segments for radiation
  private int ch_lineSegments = 30;

  // Results
  private double heatReleaseMW = 0.0;
  private Map<String, Double> emissionsKgPerHr = new HashMap<>();

  public FlareStack(String name) {
    super(name);
  }

  // --- Connections ---
  public void setReliefInlet(StreamInterface s) {
    this.reliefInlet = s;
  }

  public void setAirAssist(StreamInterface s) {
    this.airAssist = s;
  }

  public void setSteamAssist(StreamInterface s) {
    this.steamAssist = s;
  }

  // --- Config setters ---
  public void setTipDiameter(double m) {
    this.tipDiameter_m = m;
  }

  public void setTipElevation(double m) {
    this.tipElevation_m = m;
  }

  public void setBurningEfficiency(double eff) {
    this.burningEfficiency = clamp01(eff);
  }

  public void setRadiantFraction(double f) {
    this.radiantFraction = clamp01(f);
  }

  public void setSO2Conversion(double f) {
    this.so2Conversion = clamp01(f);
  }

  public void setUnburnedTHCFraction(double f) {
    this.unburnedTHCFraction = Math.max(0.0, f);
  }

  public void setCOFraction(double f) {
    this.coFraction = Math.max(0.0, f);
  }

  public void setExcessAirFrac(double f) {
    this.excessAirFrac = Math.max(0.0, f);
  }

  public void setAmbient(double tempK, double pressBar) {
    this.ambientTempK = tempK;
    this.ambientPressBar = pressBar;
  }

  public void setWindSpeed10m(double u) {
    this.windSpeed10m = Math.max(0.0, u);
  }

  public void setTipLossK(double k) {
    this.tipLossK = Math.max(0.0, k);
  }

  public void setRadiationModel(RadiationModel m) {
    this.radiationModel = m;
  }

  // Chamberlain tuners
  public void setChamberlainEmissivePower(double epC_kWm2, double epA) {
    this.ch_epC_kWm2 = epC_kWm2;
    this.ch_epA = epA;
  }

  public void setChamberlainFlameLength(double lfC, double lfA, double lfB) {
    this.ch_lfC = lfC;
    this.ch_lfA = lfA;
    this.ch_lfB = lfB;
  }

  public void setChamberlainTilt(double kTilt) {
    this.ch_kTilt = kTilt;
  }

  public void setChamberlainAttenuation(double kAtten_1_per_m) {
    this.ch_kAtten = Math.max(0.0, kAtten_1_per_m);
  }

  public void setChamberlainSegments(int n) {
    this.ch_lineSegments = Math.max(5, n);
  }

  // --- Public results ---
  public double getHeatReleaseMW() {
    return heatReleaseMW;
  }

  public Map<String, Double> getEmissionsKgPerHr() {
    return emissionsKgPerHr;
  }

  public double getTipBackpressureBar() {
    return tipBackpressureBar;
  }

  @Override
  public void run(UUID id) {
    if (reliefInlet == null) {
      throw new RuntimeException("FlareStack: relief inlet not connected.");
    }

    // 1) Clone inlet for mixture properties at ambient (for LHV, elements)
    SystemInterface mix = reliefInlet.getThermoSystem().clone();
    mix.setTemperature(ambientTempK);
    mix.setPressure(ambientPressBar);
    mix.init(0);

    // Mass and molar rates
    double mdot_kg_s = safeRate(reliefInlet.getFlowRate("kg/sec"));

    // 2) Mixture LHV and stoichiometric O2
    CombustionMixProps props = CombustionMixProps.fromSystem(mix);
    double LHV_J_per_kg = props.LHV_J_per_kg;
    double O2_stoich_kmol_s = props.O2Stoich_kmol_per_kg * mdot_kg_s;

    // 3) Available O2 from air assist (optional)
    double O2_avail_kmol_s = 0.0;
    if (airAssist != null) {
      double kmol_s = safeRate(airAssist.getMolarRate() / 1000.0);
      double yO2 = getMoleFrac(airAssist.getThermoSystem(), "oxygen");
      O2_avail_kmol_s += kmol_s * yO2 * (1.0 + excessAirFrac);
    }

    // 4) O2 limitation & effective efficiency
    double extent =
        (O2_stoich_kmol_s > 1e-12) ? Math.min(1.0, O2_avail_kmol_s / O2_stoich_kmol_s) : 1.0;
    double etaEff = burningEfficiency * extent;

    // 5) Heat release
    double Qdot_W = etaEff * mdot_kg_s * LHV_J_per_kg;
    this.heatReleaseMW = Qdot_W * 1e-6;

    // 6) Emissions (simplified)
    EmissionResult er = EmissionResult.compute(mix, mdot_kg_s, etaEff, unburnedTHCFraction,
        coFraction, so2Conversion);
    this.emissionsKgPerHr = er.toMapKgPerHr();

    // 7) Tip ΔP/backpressure
    // Exit density & velocity from RELIEF STREAM (pre-flame), using its current T/P/comp
    double rho_exit = Math.max(0.1, reliefInlet.getThermoSystem().getDensity("kg/m3"));
    double area = Math.PI * tipDiameter_m * tipDiameter_m / 4.0;
    double u_exit = (rho_exit > 1e-9 && area > 1e-9) ? mdot_kg_s / (rho_exit * area) : 0.0;

    double deltaP_bar = tipLossK * 0.5 * rho_exit * u_exit * u_exit / 1.0e5; // Pa→bar
    this.tipBackpressureBar = ambientPressBar + deltaP_bar;

    setCalculationIdentifier(id);
  }

  // ========================= Radiation APIs =========================

  /**
   * Heat flux at ground point located downwind distance R (m) from the flare base. Uses the
   * currently selected radiation model.
   */
  public double heatFlux_W_m2(double groundRange_m) {
    switch (radiationModel) {
      case CHAMBERLAIN:
        return chamberlainHeatFlux(groundRange_m);
      case POINT_SOURCE:
      default:
        return pointSourceHeatFlux(groundRange_m);
    }
  }

  /** Simple point-source radiation from flame centroid at height tipElevation. */
  public double pointSourceHeatFlux(double groundRange_m) {
    double Qdot = heatReleaseMW * 1e6;
    double R = Math.sqrt(groundRange_m * groundRange_m + tipElevation_m * tipElevation_m);
    return radiantFraction * Qdot / (4.0 * Math.PI * R * R);
  }

  /** Chamberlain-style line source with tilt, emissive power and attenuation. */
  public double chamberlainHeatFlux(double groundRange_m) {
    if (heatReleaseMW <= 1e-9) {
      return 0.0;
    }

    // Emissive power (W/m2)
    double EP = (ch_epC_kWm2 * 1e3) * Math.pow(Math.max(1e-3, heatReleaseMW), ch_epA);

    // Exit velocity for tilt
    double mdot_kg_s = safeRate(reliefInlet.getFlowRate("kg/sec"));
    double rho_exit = Math.max(0.1, reliefInlet.getThermoSystem().getDensity("kg/m3"));
    double area = Math.PI * tipDiameter_m * tipDiameter_m / 4.0;
    double Vexit = (rho_exit > 1e-9 && area > 1e-9) ? mdot_kg_s / (rho_exit * area) : 0.0;

    // Flame length (m)
    double Lf = ch_lfC * Math.pow(Math.max(1e-3, heatReleaseMW), ch_lfA)
        / Math.pow(Math.max(1e-6, tipDiameter_m), ch_lfB);

    // Tilt angle (rad), limit to < ~65 deg to avoid singular geometries
    double theta = Math.atan(ch_kTilt * windSpeed10m / Math.max(0.5, Vexit));
    theta = Math.min(theta, Math.toRadians(65.0));

    // Discretize line from tip along tilt; assume uniform EP
    int N = ch_lineSegments;
    double ds = Lf / N;
    double q_sum = 0.0;

    // Flame start point at tip elevation
    double x0 = 0.0; // horizontal at base
    double z0 = tipElevation_m;

    for (int i = 0; i < N; i++) {
      double s_mid = (i + 0.5) * ds;
      // Segment midpoint
      double x = x0 + s_mid * Math.sin(theta);
      double z = z0 + s_mid * Math.cos(theta);

      // Distance from ground receptor located on centerline downwind at (R, 0)
      double dx = groundRange_m - x;
      double dz = z; // ground z=0
      double R = Math.sqrt(dx * dx + dz * dz);

      // View factor for an elemental patch ~ ds / (4πR^2) with cosine to receptor assumed ~ dz/R
      double view = ds / (4.0 * Math.PI * R * R);
      double cosTerm = dz / R; // crude projection toward ground
      if (cosTerm < 0) {
        cosTerm = 0;
      }

      // Atmospheric attenuation
      double tau = Math.exp(-ch_kAtten * R);

      q_sum += EP * view * cosTerm * tau;
    }
    return q_sum;
  }

  // ========================= Helpers & Sub-models =========================

  private static double getMoleFrac(SystemInterface s, String comp) {
    try {
      return s.getPhase(0).getComponent(comp).getz();
    } catch (Exception e) {
      return 0.0;
    }
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  private static double safeRate(double v) {
    return Double.isFinite(v) ? Math.max(0.0, v) : 0.0;
  }

  // --- Mixture props, emissions (same style as earlier draft) ---
  static class CombustionMixProps {
    double LHV_J_per_kg;
    double O2Stoich_kmol_per_kg;

    static CombustionMixProps fromSystem(SystemInterface s) {
      CombustionMixProps p = new CombustionMixProps();
      double MWmix = s.getMolarMass(); // kg/kmol
      double LHV_J_per_kmol = 0.0;
      double O2_kmol_per_kmolFuel = 0.0;

      int nc = s.getPhase(0).getNumberOfComponents();
      for (int i = 0; i < nc; i++) {
        var comp = s.getPhase(0).getComponent(i);
        String name = comp.getComponentName().toLowerCase();
        double zi = comp.getz();
        SpeciesData sd = SpeciesData.of(name);
        LHV_J_per_kmol += zi * sd.LHV_J_per_kmol;
        O2_kmol_per_kmolFuel += zi * sd.O2_stoich_per_kmolFuel;
      }
      p.LHV_J_per_kg = LHV_J_per_kmol / MWmix;
      p.O2Stoich_kmol_per_kg = O2_kmol_per_kmolFuel / MWmix;
      return p;
    }
  }

  static class EmissionResult {
    double co2_kg_s, h2o_kg_s, so2_kg_s, nox_kg_s, co_kg_s, thc_kg_s;

    Map<String, Double> toMapKgPerHr() {
      Map<String, Double> m = new HashMap<>();
      m.put("CO2_kg_h", co2_kg_s * 3600.0);
      m.put("H2O_kg_h", h2o_kg_s * 3600.0);
      m.put("SO2_kg_h", so2_kg_s * 3600.0);
      m.put("NOx_kg_h", nox_kg_s * 3600.0);
      m.put("CO_kg_h", co_kg_s * 3600.0);
      m.put("THC_kg_h", thc_kg_s * 3600.0);
      return m;
    }

    static EmissionResult compute(SystemInterface s, double mdot_kg_s, double etaEff,
        double fracTHC, double fracCO, double fracSO2) {
      EmissionResult r = new EmissionResult();
      double MWmix = s.getMolarMass();
      double kmolFuel_s = (MWmix > 1e-12) ? mdot_kg_s / MWmix : 0.0;

      double C_kmol = 0.0, H_kmol = 0.0, S_kmol = 0.0;
      int nc = s.getPhase(0).getNumberOfComponents();
      for (int i = 0; i < nc; i++) {
        var c = s.getPhase(0).getComponent(i);
        double z = c.getz() * kmolFuel_s;
        ElementStoich es = ElementStoich.of(c.getComponentName().toLowerCase());
        C_kmol += z * es.C;
        H_kmol += z * es.H;
        S_kmol += z * es.S;
      }

      double C_to_CO2 = etaEff * (1.0 - fracCO - fracTHC) * C_kmol;
      double C_to_CO = etaEff * fracCO * C_kmol;
      double C_to_THC = (1.0 - etaEff) * C_kmol + etaEff * fracTHC * C_kmol;

      double H_to_H2O = etaEff * H_kmol / 2.0;
      double S_to_SO2 = etaEff * fracSO2 * S_kmol;

      r.co2_kg_s = C_to_CO2 * 44.0095;
      r.co_kg_s = C_to_CO * 28.010;
      r.thc_kg_s = C_to_THC * 16.043; // CH4 eqv
      r.h2o_kg_s = H_to_H2O * 18.015;
      r.so2_kg_s = S_to_SO2 * 64.066;

      // Simple NOx EF (g/MJ)
      double EF_NOx_g_per_MJ = 0.3;
      double LHV_J_per_kg = SpeciesData.mixLHV_J_per_kg(s);
      r.nox_kg_s = etaEff * mdot_kg_s * (LHV_J_per_kg * 1e-6) * (EF_NOx_g_per_MJ / 1e6);
      return r;
    }
  }

  static class SpeciesData {
    final double LHV_J_per_kmol;
    final double O2_stoich_per_kmolFuel;

    private SpeciesData(double LHV_J_per_kmol, double O2) {
      this.LHV_J_per_kmol = LHV_J_per_kmol;
      this.O2_stoich_per_kmolFuel = O2;
    }

    static SpeciesData of(String name) {
      switch (name) {
        case "methane":
          return new SpeciesData(802.3e6, 2.0);
        case "ethane":
          return new SpeciesData(1429e6, 3.5);
        case "propane":
          return new SpeciesData(2043e6, 5.0);
        case "n-butane":
        case "butane":
          return new SpeciesData(2658e6, 6.5);
        case "n-pentane":
        case "pentane":
          return new SpeciesData(3273e6, 8.0);
        case "hydrogen":
          return new SpeciesData(242e6, 0.5);
        case "carbon monoxide":
        case "co":
          return new SpeciesData(283e6, 0.5);
        case "h2s":
          return new SpeciesData(518e6, 1.5);
        default:
          return new SpeciesData(1200e6, 3.0); // generic CH1.8 proxy
      }
    }

    static double mixLHV_J_per_kg(SystemInterface s) {
      CombustionMixProps p = CombustionMixProps.fromSystem(s);
      return p.LHV_J_per_kg;
    }
  }

  static class ElementStoich {
    final double C, H, S;

    private ElementStoich(double C, double H, double S) {
      this.C = C;
      this.H = H;
      this.S = S;
    }

    static ElementStoich of(String name) {
      switch (name) {
        case "methane":
          return new ElementStoich(1, 4, 0);
        case "ethane":
          return new ElementStoich(2, 6, 0);
        case "propane":
          return new ElementStoich(3, 8, 0);
        case "n-butane":
        case "butane":
          return new ElementStoich(4, 10, 0);
        case "n-pentane":
        case "pentane":
          return new ElementStoich(5, 12, 0);
        case "hydrogen":
          return new ElementStoich(0, 2, 0);
        case "carbon monoxide":
        case "co":
          return new ElementStoich(1, 0, 0);
        case "h2s":
          return new ElementStoich(0, 2, 1);
        default:
          return new ElementStoich(1, 1.8, 0);
      }
    }
  }
}

