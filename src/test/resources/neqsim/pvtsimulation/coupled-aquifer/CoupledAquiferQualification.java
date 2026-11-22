import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.pvtsimulation.util.CalibratedPvtModel;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer;

/**
 * Independent coupled finite-EOS qualification driver.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class CoupledAquiferQualification {
  /** Prevents construction. */
  private CoupledAquiferQualification() {
  }

  /**
   * Runs a frozen matrix and retains every endpoint before assessing acceptance.
   * 
   * @param args reference JSON followed by output JSON
   * @throws Exception if computation or file access fails
   */
  public static void main(String[] args) throws Exception {
    Gson gson = new Gson();
    JsonObject ref;
    try (FileReader reader = new FileReader(args[0])) {
      ref = gson.fromJson(reader, JsonObject.class);
    }
    double n0 = ref.get("n0").getAsDouble();
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    for (JsonElement element : ref.getAsJsonArray("cases")) {
      JsonObject test = element.getAsJsonObject();
      double c = test.get("capacityRatio").getAsDouble();
      double j = test.get("productivityRatio").getAsDouble();
      double f = test.get("producedFraction").getAsDouble();
      for (int count : new int[] {8, 16, 32, 64}) {
        SystemInterface sample = new SystemSrkEos(400, 200);
        sample.addComponent("methane", 1);
        sample.setMixingRule("classic");
        sample.useVolumeCorrection(false);
        new ThermodynamicOperations(sample).TPflash();
        sample.init(1);
        CalibratedPvtModel model = new CalibratedPvtModel(sample, sample, "synthetic pure methane coupled aquifer",
            "fixed original SRK", "identical SRK", "no laboratory or reservoir tuning; independent explicit SRK ODE",
            new String[] {"synthetic"});
        Step[] steps = new Step[count];
        for (int i = 0; i < count; i++) {
          steps[i] = new Step(10000.0 / count, new double[] {n0 * f / count}, new double[1], Double.NaN, Double.NaN,
              false);
        }
        CompositionalReservoirHistory history = new CompositionalReservoirHistory(model,
            "constant measured-component synthetic schedule; mol per interval", new String[] {"methane"}, 400, 200,
            new double[] {1, 0, 0}, steps);
        FetkovichAquifer aq = new FetkovichAquifer(c < 0 ? Double.POSITIVE_INFINITY : 1e8,
            c < 0 ? .0001 : c * 1000 / 200 / 1e8, j * 1000 / 200 / 10000, 200);
        byte[] checkpoint = org.apache.commons.lang3.SerializationUtils.serialize(model);
        long start = System.nanoTime();
        Result r = history.replay(1000, aq);
        double seconds = (System.nanoTime() - start) / 1e9;
        if (!java.util.Arrays.equals(checkpoint, org.apache.commons.lang3.SerializationUtils.serialize(model))) {
          throw new IllegalStateException("Original/tuned checkpoint changed");
        }
        List<double[]> rows = new ArrayList<double[]>();
        double pe = 0, we = 0, balance = 0, storage = 0;
        double initial = r.getComponentMoles()[0][0];
        for (int i = 1; i <= count; i++) {
          double p = r.getPressuresBara()[i], w = r.getAquiferInfluxM3()[i];
          double rp = test.getAsJsonArray("rows").get(i * 64 / count - 1).getAsJsonArray().get(0).getAsDouble();
          double rw = test.getAsJsonArray("rows").get(i * 64 / count - 1).getAsJsonArray().get(1).getAsDouble();
          pe = Math.max(pe, Math.abs(p - rp));
          we = Math.max(we, Math.abs(w - rw));
          balance = Math.max(balance, Math.abs(r.getComponentMoles()[i][0] - (initial - i * n0 * f / count)));
          if (c > 0)
            storage = Math.max(storage, Math.abs(r.getAquiferPressuresBara()[i] - (200 - w / (c * 5))));
          rows.add(
              new double[] {r.getTimesSeconds()[i], p, w, r.getAquiferPressuresBara()[i], r.getComponentMoles()[i][0]});
        }
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("capacityRatio", c);
        report.put("productivityRatio", j);
        report.put("fraction", f);
        report.put("intervals", count);
        report.put("maxPressureErrorBara", pe);
        report.put("maxWaterErrorM3", we);
        report.put("componentBalanceErrorMol", balance);
        report.put("storagePressureErrorBara", storage);
        SystemInterface finalFluid = r.getFinalFluid();
        finalFluid.init(1);
        report.put("finalPoreClosureM3", Math.abs(finalFluid.getVolume("m3") + r.getAquiferInfluxM3()[count] - 1000));
        report.put("seconds", seconds);
        report.put("rows", rows);
        results.add(report);
      }
    }
    try (FileWriter writer = new FileWriter(args[1])) {
      gson.toJson(results, writer);
    }
  }
}
