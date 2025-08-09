package neqsim.blackoil.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import neqsim.blackoil.BlackOilPVTTable;
import neqsim.blackoil.SystemBlackOil;

/** Minimal ECLIPSE deck importer to build a Black-Oil fluid. */
public class EclipseBlackOilImporter {

  public enum Units { METRIC, FIELD, LAB }

  public static class Result {
    public BlackOilPVTTable pvt;
    public SystemBlackOil system;
    public double rho_o_sc, rho_w_sc, rho_g_sc;
    public double bubblePoint;
    public List<String> log = new ArrayList<>();
  }

  public static Result fromFile(Path deckPath) throws IOException { try (BufferedReader br = Files.newBufferedReader(deckPath)) { return parse(br); } }
  public static Result fromReader(Reader reader) throws IOException { return parse(new BufferedReader(reader)); }

  private static class PVTOCurve { double Rs; final List<Row> rows = new ArrayList<>(); static class Row { double P, Bo, mu; } }
  private static class PVTGCurve { double Rv; final List<Row> rows = new ArrayList<>(); static class Row { double P, Bg, mu; } }
  private static class PVTWRow { double P, Bw, mu; }

  private static Result parse(BufferedReader br) throws IOException {
    Objects.requireNonNull(br, "reader == null");
    Units units = Units.METRIC;
    List<PVTOCurve> pvto = new ArrayList<>();
    List<PVTGCurve> pvtg = new ArrayList<>();
    List<PVTWRow>  pvtw = new ArrayList<>();
    double rho_o_sc = Double.NaN, rho_w_sc = Double.NaN, rho_g_sc = Double.NaN;

    String line;
    while ((line = br.readLine()) != null) {
      line = stripComments(line);
      if (line.isBlank()) continue;
      String u = line.toUpperCase(Locale.ROOT).trim();
      if (u.startsWith("UNITS")) {
        if (u.contains("FIELD")) units = Units.FIELD; else if (u.contains("LAB")) units = Units.LAB; else units = Units.METRIC;
      } else if (u.startsWith("DENSITY")) {
        var vals = new ArrayList<Double>();
        vals.addAll(numbersFromLine(u.substring("DENSITY".length())));
        while (!u.contains("/")) { String l2 = br.readLine(); if (l2 == null) break; u = stripComments(l2).toUpperCase(Locale.ROOT); vals.addAll(numbersFromLine(u)); }
        if (vals.size() >= 3) {
          rho_o_sc = vals.get(0); rho_w_sc = vals.get(1); rho_g_sc = vals.get(2);
          if (units == Units.FIELD) { double f = 16.01846337; rho_o_sc*=f; rho_w_sc*=f; rho_g_sc*=f; }
        }
      } else if (u.startsWith("PVTO")) {
        while (true) {
          String h = br.readLine(); if (h == null) break; h = stripComments(h);
          if (h.isBlank()) continue; if (h.trim().startsWith("/")) break;
          boolean headerEnds = h.contains("/");
          String hClean = h.split("/", 2)[0];
          var hdr = numbersFromLine(hClean);
          if (hdr.size() < 4) { String h2 = br.readLine(); if (h2 == null) break; h2 = stripComments(h2); String hc = hClean + " " + h2.split("/", 2)[0]; hdr = numbersFromLine(hc); }
          if (hdr.size() < 4) throw new IOException("PVTO header needs at least 4 numbers: Rs Pb Bo mu");
          PVTOCurve c = new PVTOCurve(); c.Rs = hdr.get(0); double Pb = hdr.get(1);
          PVTOCurve.Row r0 = new PVTOCurve.Row(); r0.P = Pb; r0.Bo = hdr.get(2); r0.mu = hdr.get(3); c.rows.add(r0);
          if (!headerEnds) {
            while (true) { String l2 = br.readLine(); if (l2 == null) break; String lc = stripComments(l2);
              if (lc.isBlank()) continue; boolean end = lc.contains("/"); String data = lc.split("/", 2)[0]; var row = numbersFromLine(data);
              if (row.size() >= 3) { PVTOCurve.Row rr = new PVTOCurve.Row(); rr.P = row.get(0); rr.Bo = row.get(1); rr.mu = row.get(2); c.rows.add(rr); }
              if (end) break; }
          }
          pvto.add(c);
        }
      } else if (u.startsWith("PVTG")) {
        while (true) {
          String h = br.readLine(); if (h == null) break; h = stripComments(h);
          if (h.isBlank()) continue; if (h.trim().startsWith("/")) break;
          boolean headerEnds = h.contains("/");
          String hClean = h.split("/", 2)[0];
          var hdr = numbersFromLine(hClean);
          if (hdr.size() < 4) { String h2 = br.readLine(); if (h2 == null) break; h2 = stripComments(h2); String hc = hClean + " " + h2.split("/", 2)[0]; hdr = numbersFromLine(hc); }
          if (hdr.size() < 4) throw new IOException("PVTG header needs at least 4 numbers: Rv Pd Bg mu");
          PVTGCurve c = new PVTGCurve(); c.Rv = hdr.get(0); double Pd = hdr.get(1);
          PVTGCurve.Row r0 = new PVTGCurve.Row(); r0.P = Pd; r0.Bg = hdr.get(2); r0.mu = hdr.get(3); c.rows.add(r0);
          if (!headerEnds) {
            while (true) { String l2 = br.readLine(); if (l2 == null) break; String lc = stripComments(l2);
              if (lc.isBlank()) continue; boolean end = lc.contains("/"); String data = lc.split("/", 2)[0]; var row = numbersFromLine(data);
              if (row.size() >= 3) { PVTGCurve.Row rr = new PVTGCurve.Row(); rr.P = row.get(0); rr.Bg = row.get(1); rr.mu = row.get(2); c.rows.add(rr); }
              if (end) break; }
          }
          pvtg.add(c);
        }
      } else if (u.startsWith("PVTW")) {
        while (true) {
          String l2 = br.readLine(); if (l2 == null) break; String lc = stripComments(l2);
          if (lc.isBlank()) continue; if (lc.trim().startsWith("/")) break;
          boolean end = lc.contains("/"); String data = lc.split("/", 2)[0]; var row = numbersFromLine(data);
          if (row.size() >= 3) { PVTWRow r = new PVTWRow(); r.P = row.get(0); r.Bw = row.get(1); r.mu = row.get(2); pvtw.add(r); }
          if (end) break;
        }
      }
    }

    if (units == Units.FIELD) {
      for (PVTOCurve c : pvto) { c.Rs = c.Rs * (0.028316846592 / 0.158987294928); for (PVTOCurve.Row r : c.rows) { r.P *= 0.06894757293168; r.mu *= 1e-3; } }
      for (PVTGCurve c : pvtg) { c.Rv = c.Rv * (0.158987294928 / 0.028316846592);
        for (PVTGCurve.Row r : c.rows) { r.P *= 0.06894757293168; r.Bg *= (0.158987294928 / 0.028316846592); r.mu *= 1e-3; } }
      for (PVTWRow r : pvtw) { r.P *= 0.06894757293168; r.mu *= 1e-3; }
    } else {
      for (PVTOCurve c : pvto) for (PVTOCurve.Row r : c.rows) r.mu *= 1e-3;
      for (PVTGCurve c : pvtg) for (PVTGCurve.Row r : c.rows) r.mu *= 1e-3;
      for (PVTWRow r : pvtw) r.mu *= 1e-3;
    }

    if (pvto.isEmpty()) throw new IOException("PVTO not found in deck");
    if (Double.isNaN(rho_o_sc)) rho_o_sc = 800.0; if (Double.isNaN(rho_w_sc)) rho_w_sc = 1000.0; if (Double.isNaN(rho_g_sc)) rho_g_sc = 1.2;
    PVTOCurve base = Collections.max(pvto, Comparator.comparingDouble(c -> c.Rs));

    class SatPoint { double P, Rs, Bo, mu; SatPoint(double P,double Rs,double Bo,double mu){this.P=P;this.Rs=Rs;this.Bo=Bo;this.mu=mu;} }
    var sat = new ArrayList<SatPoint>();
    for (PVTOCurve c : pvto) { if (c.rows.isEmpty()) continue; var r0 = c.rows.get(0); sat.add(new SatPoint(r0.P, c.Rs, r0.Bo, r0.mu)); }
    sat.sort(Comparator.comparingDouble(sp -> sp.P));
    double Pb = base.rows.get(0).P;

    var Pg = new ArrayList<Double>(); var RsOfP = new ArrayList<Double>(); var BoOfP = new ArrayList<Double>(); var muoOfP = new ArrayList<Double>();
    for (SatPoint sp : sat) if (sp.P <= Pb) { Pg.add(sp.P); RsOfP.add(sp.Rs); BoOfP.add(sp.Bo); muoOfP.add(sp.mu); }
    if (Pg.stream().noneMatch(p -> Math.abs(p - Pb) < 1e-9)) { Pg.add(Pb); RsOfP.add(base.Rs); BoOfP.add(base.rows.get(0).Bo); muoOfP.add(base.rows.get(0).mu); }
    for (var r : base.rows) if (r.P >= Pb + 1e-9) { Pg.add(r.P); RsOfP.add(base.Rs); BoOfP.add(r.Bo); muoOfP.add(r.mu); }
    sortParallel(Pg, RsOfP, BoOfP, muoOfP);

    var BgOfP = new ArrayList<Double>(); var mugOfP = new ArrayList<Double>();
    PVTGCurve gas0 = findRvZero(pvtg);
    for (double p : Pg) { if (gas0 != null) { double[] bmu = interpBgMu(gas0, p); BgOfP.add(bmu[0]); mugOfP.add(bmu[1]); } else { BgOfP.add(0.0050); mugOfP.add(1.0e-5); } }

    var BwOfP = new ArrayList<Double>(); var muwOfP = new ArrayList<Double>();
    for (double p : Pg) { if (!pvtw.isEmpty()) { double[] bwmu = interpBwMu(pvtw, p); BwOfP.add(bwmu[0]); muwOfP.add(bwmu[1]); } else { BwOfP.add(1.0); muwOfP.add(0.5e-3); } }

    var recs = new java.util.ArrayList<BlackOilPVTTable.Record>();
    for (int i = 0; i < Pg.size(); i++) recs.add(new BlackOilPVTTable.Record(Pg.get(i), RsOfP.get(i), BoOfP.get(i), muoOfP.get(i), BgOfP.get(i), mugOfP.get(i), 0.0, BwOfP.get(i), muwOfP.get(i)));

    BlackOilPVTTable table = new BlackOilPVTTable(recs, Pb);
    SystemBlackOil sys = new SystemBlackOil(table, rho_o_sc, rho_g_sc, rho_w_sc);
    sys.setStdTotals(1.0, table.Rs(Pb) * 1.0, 0.0);
    sys.setPressure(Pb); sys.setTemperature(350.0);

    Result out = new Result(); out.pvt = table; out.system = sys;
    out.rho_o_sc = rho_o_sc; out.rho_w_sc = rho_w_sc; out.rho_g_sc = rho_g_sc; out.bubblePoint = Pb;
    out.log.add("Units detected: " + units); out.log.add("PVTO blocks: " + pvto.size()); out.log.add("PVTG blocks: " + pvtg.size()); out.log.add("PVTW rows : " + pvtw.size());
    return out;
  }

  private static String stripComments(String s) { if (s == null) return ""; int i = s.indexOf("--"); if (i >= 0) s = s.substring(0, i); return s.replace('\t', ' ').trim(); }
  private static java.util.List<Double> numbersFromLine(String s) {
    String clean = s.replace(',', ' ').replace('\t', ' ').replace("/", " ");
    String[] tok = clean.trim().split("\\s+"); var out = new java.util.ArrayList<Double>();
    for (String t : tok) { if (t.isEmpty()) continue; try { out.add(Double.parseDouble(t)); } catch (NumberFormatException ignored) {} }
    return out;
  }
  private static void sortParallel(List<Double> P, List<Double> Rs, List<Double> Bo, List<Double> mu) {
    var idx = new java.util.ArrayList<Integer>(); for (int i=0;i<P.size();i++) idx.add(i);
    idx.sort(Comparator.comparingDouble(P::get)); reorder(P, idx); reorder(Rs, idx); reorder(Bo, idx); reorder(mu, idx);
  }
  private static void reorder(List<Double> a, List<Integer> idx) { var c = new java.util.ArrayList<Double>(a); for (int i=0;i<idx.size();i++) a.set(i, c.get(idx.get(i))); }
  private static PVTGCurve findRvZero(List<PVTGCurve> pvtg) {
    if (pvtg.isEmpty()) return null; PVTGCurve best = null; double bestAbs = Double.POSITIVE_INFINITY;
    for (PVTGCurve c : pvtg) { double ab = Math.abs(c.Rv); if (ab < bestAbs) { bestAbs = ab; best = c; if (ab < 1e-9) break; } } return best;
  }
  private static double[] interpBgMu(PVTGCurve curve, double p) {
    var rows = curve.rows; if (rows.isEmpty()) return new double[]{0.005, 1e-5}; rows.sort(Comparator.comparingDouble(r -> r.P));
    if (p <= rows.get(0).P) return new double[]{rows.get(0).Bg, rows.get(0).mu};
    if (p >= rows.get(rows.size()-1).P) return new double[]{rows.get(rows.size()-1).Bg, rows.get(rows.size()-1).mu};
    for (int i=0;i<rows.size()-1;i++) { var a = rows.get(i); var b = rows.get(i+1);
      if (p >= a.P && p <= b.P) { double t = (p - a.P) / (b.P - a.P); double Bg = a.Bg*(1.0-t) + b.Bg*t; double mu = a.mu*(1.0-t) + b.mu*t; return new double[]{Bg, mu}; } }
    return new double[]{rows.get(rows.size()-1).Bg, rows.get(rows.size()-1).mu};
  }
  private static double[] interpBwMu(List<PVTWRow> rows, double p) {
    rows.sort(Comparator.comparingDouble(r -> r.P));
    if (p <= rows.get(0).P) return new double[]{rows.get(0).Bw, rows.get(0).mu};
    if (p >= rows.get(rows.size()-1).P) return new double[]{rows.get(rows.size()-1).Bw, rows.get(rows.size()-1).mu};
    for (int i=0;i<rows.size()-1;i++) { var a = rows.get(i); var b = rows.get(i+1);
      if (p >= a.P && p <= b.P) { double t = (p - a.P) / (b.P - a.P); double Bw = a.Bw*(1.0-t) + b.Bw*t; double mu = a.mu*(1.0-t) + b.mu*t; return new double[]{Bw, mu}; } }
    return new double[]{rows.get(rows.size()-1).Bw, rows.get(rows.size()-1).mu};
  }
}
