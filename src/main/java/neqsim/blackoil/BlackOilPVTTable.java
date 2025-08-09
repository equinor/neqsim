package neqsim.blackoil;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Black-Oil PVT table with linear interpolation in pressure.
 * Units (recommended):
 *   P: bar (or Pa, but be consistent across inputs!)
 *   Rs: Sm3 gas / Sm3 oil
 *   Rv: Sm3 oil / Sm3 gas (often 0.0)
 *   Bo, Bg, Bw: reservoir m3 per standard m3 (rm3 / Sm3)
 *   mu_*: Pa·s
 */
public class BlackOilPVTTable {
  public static final class Record {
    public final double p, Rs, Bo, mu_o, Bg, mu_g, Rv, Bw, mu_w;
    public Record(double p, double Rs, double Bo, double mu_o,
                  double Bg, double mu_g, double Rv, double Bw, double mu_w) {
      this.p = p; this.Rs = Rs; this.Bo = Bo; this.mu_o = mu_o;
      this.Bg = Bg; this.mu_g = mu_g; this.Rv = Rv; this.Bw = Bw; this.mu_w = mu_w;
    }
  }

  private final List<Record> recs = new ArrayList<>();
  private final double bubblePointP;

  public BlackOilPVTTable(List<Record> records, double bubblePointP) {
    this.recs.addAll(records);
    this.recs.sort(Comparator.comparingDouble(r -> r.p));
    if (this.recs.isEmpty()) throw new IllegalArgumentException("Empty PVT table");
    this.bubblePointP = bubblePointP;
  }

  public double getBubblePointP() { return bubblePointP; }

  private double lin(double p, ToDoubleFunction<Record> f) {
    if (p <= recs.get(0).p) return f.applyAsDouble(recs.get(0));
    if (p >= recs.get(recs.size() - 1).p) return f.applyAsDouble(recs.get(recs.size() - 1));
    for (int i = 0; i < recs.size()-1; i++) {
      Record a = recs.get(i), b = recs.get(i+1);
      if (p >= a.p && p <= b.p) {
        double t = (p - a.p) / (b.p - a.p);
        return f.applyAsDouble(a)*(1.0 - t) + f.applyAsDouble(b)*t;
      }
    }
    return f.applyAsDouble(recs.get(recs.size() - 1));
  }

  public double Rs(double p)   { return lin(p, r -> r.Rs); }
  public double Bo(double p)   { return lin(p, r -> r.Bo); }
  public double mu_o(double p) { return lin(p, r -> r.mu_o); }
  public double Bg(double p)   { return lin(p, r -> r.Bg); }
  public double mu_g(double p) { return lin(p, r -> r.mu_g); }
  public double Rv(double p)   { return lin(p, r -> r.Rv); }
  public double Bw(double p)   { return lin(p, r -> r.Bw); }
  public double mu_w(double p) { return lin(p, r -> r.mu_w); }

  /** Above Pb, keep Rs constant at Rs(Pb) (simple Black-Oil rule). */
  public double RsEffective(double p) {
    return (p > bubblePointP) ? Rs(bubblePointP) : Rs(p);
  }
}
