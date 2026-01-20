package neqsim.blackoil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Converter from a compositional (EOS) NeqSim fluid to a Black-Oil PVT table + stream.
 *
 * @author esol
 */
public class BlackOilConverter {

  /**
   * Result class to hold the output of the conversion.
   */
  public static class Result {
    public BlackOilPVTTable pvt;
    public SystemBlackOil blackOilSystem;
    public double rho_o_sc;
    public double rho_g_sc;
    public double rho_w_sc;
    public double bubblePoint;
  }

  /**
   * <p>
   * convert.
   * </p>
   *
   * @param eosFluid a {@link neqsim.thermo.system.SystemInterface} object
   * @param Tref a double
   * @param pGrid an array of double objects
   * @param Pstd a double
   * @param Tstd a double
   * @return a {@link neqsim.blackoil.BlackOilConverter.Result} object
   */
  public static Result convert(SystemInterface eosFluid, double Tref, double[] pGrid, double Pstd,
      double Tstd) {
    Objects.requireNonNull(eosFluid, "eosFluid == null");
    if (pGrid == null || pGrid.length < 2) {
      throw new IllegalArgumentException("pGrid must have >= 2 points");
    }
    double[] P = Arrays.stream(pGrid).sorted().toArray();

    StdTotals stdTotals = computeStdTotalsFromWhole(eosFluid, Pstd, Tstd);

    List<BlackOilPVTTable.Record> recs = new ArrayList<>();
    double bubblePoint = Double.NaN;

    double rho_o_sc = Double.NaN;
    double rho_g_sc = Double.NaN;
    double rho_w_sc = Double.NaN;
    BlackOilPVTTable.Record lastRecWithGas = null;

    for (double p : P) {
      PerPressureProps props = evalAtPressure(eosFluid, Tref, p, Pstd, Tstd);
      if (!Double.isNaN(props.rho_o_sc)) {
        rho_o_sc = props.rho_o_sc;
      }
      if (!Double.isNaN(props.rho_g_sc)) {
        rho_g_sc = props.rho_g_sc;
      }
      if (!Double.isNaN(props.rho_w_sc)) {
        rho_w_sc = props.rho_w_sc;
      }
      recs.add(new BlackOilPVTTable.Record(p, props.Rs, props.Bo, props.mu_o, props.Bg, props.mu_g,
          props.Rv, props.Bw, props.mu_w));
      if (props.hasFreeGas) {
        lastRecWithGas = recs.get(recs.size() - 1);
      }
    }

    for (int i = P.length - 1; i >= 0; i--) {
      PerPressureProps props = evalAtPressure(eosFluid, Tref, P[i], Pstd, Tstd);
      if (props.hasFreeGas) {
        bubblePoint = P[i];
        break;
      }
    }
    if (Double.isNaN(bubblePoint)) {
      bubblePoint = P[0];
    }

    double rsAtPb = interpolateRsAt(recs, bubblePoint);
    for (int i = 0; i < recs.size(); i++) {
      BlackOilPVTTable.Record r = recs.get(i);
      if (r.p > bubblePoint) {
        recs.set(i, new BlackOilPVTTable.Record(r.p, rsAtPb, r.Bo, r.mu_o, r.Bg, r.mu_g, r.Rv, r.Bw,
            r.mu_w));
      }
    }

    if (lastRecWithGas != null) {
      double lastBg = lastRecWithGas.Bg;
      double lastMug = lastRecWithGas.mu_g;
      double lastRv = lastRecWithGas.Rv;
      for (int i = 0; i < recs.size(); i++) {
        BlackOilPVTTable.Record r = recs.get(i);
        if (r.p > bubblePoint) {
          recs.set(i, new BlackOilPVTTable.Record(r.p, r.Rs, r.Bo, r.mu_o, lastBg, lastMug, lastRv,
              r.Bw, r.mu_w));
        }
      }
    }

    if (Double.isNaN(rho_o_sc) || Double.isNaN(rho_g_sc) || Double.isNaN(rho_w_sc)) {
      if (Double.isNaN(rho_o_sc) && stdTotals.rho_o_sc > 0) {
        rho_o_sc = stdTotals.rho_o_sc;
      }
      if (Double.isNaN(rho_g_sc) && stdTotals.rho_g_sc > 0) {
        rho_g_sc = stdTotals.rho_g_sc;
      }
      if (Double.isNaN(rho_w_sc) && stdTotals.rho_w_sc > 0) {
        rho_w_sc = stdTotals.rho_w_sc;
      }
    }

    BlackOilPVTTable pvt = new BlackOilPVTTable(recs, bubblePoint);
    SystemBlackOil sys = new SystemBlackOil(pvt, rho_o_sc, rho_g_sc, rho_w_sc);
    sys.setStdTotals(stdTotals.O_std, stdTotals.G_std, stdTotals.W_std);
    sys.setPressure(eosFluid.getPressure());
    sys.setTemperature(eosFluid.getTemperature());

    Result out = new Result();
    out.pvt = pvt;
    out.blackOilSystem = sys;
    out.rho_o_sc = rho_o_sc;
    out.rho_g_sc = rho_g_sc;
    out.rho_w_sc = rho_w_sc;
    out.bubblePoint = bubblePoint;
    return out;
  }

  private static class StdTotals {
    double O_std;
    double G_std;
    double W_std;
    double rho_o_sc;
    double rho_g_sc;
    double rho_w_sc;
  }

  private static StdTotals computeStdTotalsFromWhole(SystemInterface fluid, double Pstd,
      double Tstd) {
    try {
      SystemInterface f = fluid.clone();
      f.setPressure(Pstd);
      f.setTemperature(Tstd);
      ThermodynamicOperations ops = new ThermodynamicOperations(f);
      ops.TPflash();

      StdTotals s = new StdTotals();
      PhaseInterface oil = findOilPhase(f);
      PhaseInterface gas = findGasPhase(f);
      PhaseInterface wat = findWaterPhase(f);

      if (oil != null) {
        double V = phaseVolume(oil);
        s.O_std = V;
        s.rho_o_sc = oil.getDensity();
      }
      if (gas != null) {
        double V = phaseVolume(gas);
        s.G_std = V;
        s.rho_g_sc = gas.getDensity();
      }
      if (wat != null) {
        double V = phaseVolume(wat);
        s.W_std = V;
        s.rho_w_sc = wat.getDensity();
      }
      return s;
    } catch (Exception e) {
      throw new RuntimeException("Failed stock flash of whole fluid: " + e.getMessage(), e);
    }
  }

  private static class PerPressureProps {
    double Rs = 0.0;
    double Bo = 1.0;
    double mu_o = Double.NaN;
    double Bg = Double.NaN;
    double mu_g = Double.NaN;
    double Rv = 0.0;
    double Bw = Double.NaN;
    double mu_w = Double.NaN;
    boolean hasFreeGas = false;
    double rho_o_sc = Double.NaN;
    double rho_g_sc = Double.NaN;
    double rho_w_sc = Double.NaN;
  }

  private static PerPressureProps evalAtPressure(SystemInterface base, double Tref, double p,
      double Pstd, double Tstd) {
    try {
      SystemInterface f = base.clone();
      f.setTemperature(Tref);
      f.setPressure(p);
      ThermodynamicOperations ops = new ThermodynamicOperations(f);
      ops.TPflash();

      PerPressureProps out = new PerPressureProps();

      PhaseInterface oil = findOilPhase(f);
      PhaseInterface gas = findGasPhase(f);
      PhaseInterface wat = findWaterPhase(f);

      if (oil != null) {
        SystemInterface oilComp = phaseAsStandaloneSystem(base, oil, Tref, p);
        ThermodynamicOperations oilResOps = new ThermodynamicOperations(oilComp);
        oilResOps.TPflash();
        PhaseInterface oilRes = findOilPhase(oilComp);
        double V_res_liq = (oilRes != null) ? phaseVolume(oilRes) : totalVolume(oilComp);
        double mu_o = (oilRes != null) ? oilRes.getViscosity() : Double.NaN;

        SystemInterface oilStd = oilComp.clone();
        oilStd.setPressure(Pstd);
        oilStd.setTemperature(Tstd);
        ThermodynamicOperations oilStdOps = new ThermodynamicOperations(oilStd);
        oilStdOps.TPflash();
        PhaseInterface oilStdOil = findOilPhase(oilStd);
        PhaseInterface oilStdGas = findGasPhase(oilStd);

        double V_std_oil = (oilStdOil != null) ? phaseVolume(oilStdOil) : 0.0;
        double V_std_gas = (oilStdGas != null) ? phaseVolume(oilStdGas) : 0.0;

        out.mu_o = mu_o;
        out.Bo = (V_std_oil > 0.0) ? (V_res_liq / V_std_oil) : 1.0;
        out.Rs = (V_std_oil > 0.0) ? (V_std_gas / V_std_oil) : 0.0;

        if (oilStdOil != null) {
          out.rho_o_sc = oilStdOil.getDensity();
        }
        if (oilStdGas != null) {
          out.rho_g_sc = oilStdGas.getDensity();
        }
      }

      if (gas != null) {
        out.hasFreeGas = true;
        SystemInterface gasComp = phaseAsStandaloneSystem(base, gas, Tref, p);

        ThermodynamicOperations gasResOps = new ThermodynamicOperations(gasComp);
        gasResOps.TPflash();
        PhaseInterface gasRes = findGasPhase(gasComp);
        double V_res_gas = (gasRes != null) ? phaseVolume(gasRes) : totalVolume(gasComp);
        double mu_g = (gasRes != null) ? gasRes.getViscosity() : Double.NaN;

        SystemInterface gasStd = gasComp.clone();
        gasStd.setPressure(Pstd);
        gasStd.setTemperature(Tstd);
        ThermodynamicOperations gasStdOps = new ThermodynamicOperations(gasStd);
        gasStdOps.TPflash();
        PhaseInterface gasStdGas = findGasPhase(gasStd);
        PhaseInterface gasStdOil = findOilPhase(gasStd);
        double V_std_gas = (gasStdGas != null) ? phaseVolume(gasStdGas) : 0.0;
        double V_std_oil = (gasStdOil != null) ? phaseVolume(gasStdOil) : 0.0;

        out.mu_g = mu_g;
        out.Bg = (V_std_gas > 0.0) ? (V_res_gas / V_std_gas) : out.Bg;
        out.Rv = (V_std_gas > 0.0) ? (V_std_oil / V_std_gas) : 0.0;

        if (gasStdGas != null) {
          out.rho_g_sc = gasStdGas.getDensity();
        }
        if (gasStdOil != null && Double.isNaN(out.rho_o_sc)) {
          out.rho_o_sc = gasStdOil.getDensity();
        }
      }

      if (wat != null) {
        SystemInterface wRes = phaseAsStandaloneSystem(base, wat, Tref, p);
        ThermodynamicOperations wOps = new ThermodynamicOperations(wRes);
        wOps.TPflash();
        PhaseInterface wPhase = findWaterPhase(wRes);
        double rho_w_res = (wPhase != null) ? wPhase.getDensity() : Double.NaN;
        double mu_w = (wPhase != null) ? wPhase.getViscosity() : Double.NaN;

        SystemInterface wStd = wRes.clone();
        wStd.setPressure(Pstd);
        wStd.setTemperature(Tstd);
        ThermodynamicOperations wStdOps = new ThermodynamicOperations(wStd);
        wStdOps.TPflash();
        PhaseInterface wStdPhase = findWaterPhase(wStd);
        double rho_w_std = (wStdPhase != null) ? wStdPhase.getDensity() : Double.NaN;

        if (!Double.isNaN(rho_w_res) && !Double.isNaN(rho_w_std) && rho_w_res > 0) {
          out.Bw = rho_w_std / rho_w_res;
          out.mu_w = mu_w;
          out.rho_w_sc = rho_w_std;
        }
      }

      return out;
    } catch (Exception e) {
      throw new RuntimeException("evalAtPressure failed at P=" + p + ": " + e.getMessage(), e);
    }
  }

  private static double interpolateRsAt(List<BlackOilPVTTable.Record> recs, double p) {
    if (recs.isEmpty()) {
      return 0.0;
    }
    if (p <= recs.get(0).p) {
      return recs.get(0).Rs;
    }
    if (p >= recs.get(recs.size() - 1).p) {
      return recs.get(recs.size() - 1).Rs;
    }
    for (int i = 0; i < recs.size() - 1; i++) {
      BlackOilPVTTable.Record a = recs.get(i);
      BlackOilPVTTable.Record b = recs.get(i + 1);
      if (p >= a.p && p <= b.p) {
        double t = (p - a.p) / (b.p - a.p);
        return a.Rs * (1.0 - t) + b.Rs * t;
      }
    }
    return recs.get(recs.size() - 1).Rs;
  }

  private static PhaseInterface findOilPhase(SystemInterface s) {
    for (int i = 0; i < s.getNumberOfPhases(); i++) {
      PhaseInterface p = s.getPhase(i);
      String type = safeTypeName(p);
      if (type.contains("liquid") && hydrocarbonFraction(p) > 1e-6 && !isMostlyWater(p)) {
        return p;
      }
    }
    return null;
  }

  private static PhaseInterface findGasPhase(SystemInterface s) {
    for (int i = 0; i < s.getNumberOfPhases(); i++) {
      PhaseInterface p = s.getPhase(i);
      String type = safeTypeName(p);
      if (type.contains("gas") && hydrocarbonFraction(p) > 1e-6) {
        return p;
      }
    }
    return null;
  }

  private static PhaseInterface findWaterPhase(SystemInterface s) {
    for (int i = 0; i < s.getNumberOfPhases(); i++) {
      PhaseInterface p = s.getPhase(i);
      if (isMostlyWater(p)) {
        return p;
      }
    }
    return null;
  }

  private static String safeTypeName(PhaseInterface p) {
    try {
      return p.getPhaseTypeName().toLowerCase();
    } catch (Throwable t) {
      return "";
    }
  }

  private static boolean isMostlyWater(PhaseInterface p) {
    try {
      if (p.getComponent("water") != null) {
        double xw = p.getComponent("water").getx();
        if (xw > 0.8) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }
    return false;
  }

  private static double hydrocarbonFraction(PhaseInterface p) {
    try {
      double xw = (p.getComponent("water") != null) ? p.getComponent("water").getx() : 0.0;
      return Math.max(0.0, 1.0 - xw);
    } catch (Throwable t) {
      return 1.0;
    }
  }

  private static double totalVolume(SystemInterface s) {
    return s.getVolume();
  }

  private static double phaseVolume(PhaseInterface p) {
    try {
      double V = p.getVolume();
      if (V > 0.0 && Double.isFinite(V)) {
        return V;
      }
    } catch (Throwable ignored) {
    }
    double mass = p.getMass();
    double rho = p.getDensity();
    return (rho > 0) ? (mass / rho) : Double.NaN;
  }

  private static SystemInterface phaseAsStandaloneSystem(SystemInterface base, PhaseInterface phase,
      double T, double P) throws Exception {
    SystemInterface sys = base.clone();
    sys.setTemperature(T);
    sys.setPressure(P);
    int nc = sys.getPhase(0).getNumberOfComponents();
    double[] z = new double[nc];
    for (int i = 0; i < nc; i++) {
      String name = sys.getPhase(0).getComponent(i).getComponentName();
      double xi = 0.0;
      try {
        if (phase.getComponent(name) != null) {
          xi = phase.getComponent(name).getx();
        }
      } catch (Throwable ignored) {
      }
      z[i] = Math.max(0.0, xi);
    }
    double sum = Arrays.stream(z).sum();
    if (sum <= 0) {
      throw new IllegalArgumentException("Phase composition empty when building standalone system");
    }
    for (int i = 0; i < z.length; i++) {
      z[i] /= sum;
    }
    sys.setMolarComposition(z);
    try {
      sys.setTotalNumberOfMoles(1.0);
    } catch (Throwable ignored) {
    }
    return sys;
  }
}
