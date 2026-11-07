import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer;
import neqsim.pvtsimulation.util.CalibratedPvtModel;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble.Candidate;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble.Posterior;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Native finite-scenario qualification; reference predictions never enter the native forward model.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DiscretePosteriorQualification {
  /** Prevents construction. */
  private DiscretePosteriorQualification() {
  }

  /**
   * Represents undefined moments as JSON null without manufacturing a numerical value.
   *
   * @param values native moment vector
   * @return finite values or explicit nulls
   */
  private static Double[] finite(double[] values) {
    Double[] result = new Double[values.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = Double.isFinite(values[i]) ? values[i] : null;
    }
    return result;
  }

  /**
   * Represents undefined correlations as JSON null.
   *
   * @param values native correlation matrix
   * @return finite values or explicit nulls
   */
  private static Double[][] finite(double[][] values) {
    Double[][] result = new Double[values.length][];
    for (int i = 0; i < result.length; i++) {
      result[i] = finite(values[i]);
    }
    return result;
  }

  /**
   * Evaluates complete frozen hypotheses and writes all predictions and posterior evidence.
   *
   * @param args reference fixture path and output JSON path
   * @throws Exception if file access, replay or checkpoint checks fail
   */
  public static void main(String[] args) throws Exception {
    Gson gson = new Gson();
    JsonObject reference;
    try (FileReader reader = new FileReader(args[0])) {
      reference = gson.fromJson(reader, JsonObject.class);
    }
    double withdrawal = reference.getAsJsonObject("initialMolarDensities").get("SRK").getAsDouble() * 1000.0 * .04;
    List<Map<String, Object>> output = new ArrayList<Map<String, Object>>();
    for (JsonElement item : reference.getAsJsonArray("cases")) {
      JsonObject test = item.getAsJsonObject();
      JsonArray definitions = test.getAsJsonArray("candidates");
      Candidate[] candidates = new Candidate[definitions.size()];
      byte[][] checkpoints = new byte[candidates.length][];
      for (int i = 0; i < candidates.length; i++) {
        JsonObject definition = definitions.get(i).getAsJsonObject();
        SystemInterface fluid = "SRK".equals(definition.get("eos").getAsString())
            ? new SystemSrkEos(400.0, 200.0) : new SystemPrEos(400.0, 200.0);
        fluid.addComponent("methane", 1.0);
        fluid.setMixingRule("classic");
        fluid.useVolumeCorrection(false);
        new ThermodynamicOperations(fluid).TPflash();
        fluid.init(1);
        CalibratedPvtModel model = new CalibratedPvtModel(fluid, fluid, "synthetic discrete methane",
            "fixed original " + definition.get("eos").getAsString(), "identical frozen EOS",
            "explicit synthetic alternative; neither laboratory nor reservoir retuned", new String[] {"synthetic"});
        Step[] steps = new Step[4];
        for (int step = 0; step < steps.length; step++) {
          JsonElement observation = test.getAsJsonArray("observations").get(step);
          boolean forecast = observation.isJsonNull();
          steps[step] = new Step(2500.0,
              new double[] {withdrawal * definition.get("flowScale").getAsDouble()}, new double[1],
              forecast ? Double.NaN : observation.getAsDouble(),
              forecast ? Double.NaN : test.get("sigma").getAsDouble(), step == 2);
        }
        CompositionalReservoirHistory history = new CompositionalReservoirHistory(model,
            "prescribed methane withdrawals with explicit amount realization; no produced-phase prediction",
            new String[] {"methane"}, 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, steps);
        FetkovichAquifer aquifer = new FetkovichAquifer(100000.0, definition.get("capacity").getAsDouble() / 100000.0,
            definition.get("j").getAsDouble(), 200.0);
        candidates[i] = new Candidate(history, definition.get("volume").getAsDouble(), aquifer,
            definition.get("mass").getAsDouble(), "synthetic joint cell " + test.get("name").getAsString() + "/" + i);
        checkpoints[i] = SerializationUtils.serialize(model);
      }
      long start = System.nanoTime();
      Posterior posterior = ReservoirHistoryEnsemble.evaluate(candidates);
      double seconds = (System.nanoTime() - start) / 1.0e9;
      List<Object> rows = new ArrayList<Object>();
      List<Object> chi = new ArrayList<Object>();
      List<Object> holdout = new ArrayList<Object>();
      Result[] replays = posterior.getResults();
      for (int i = 0; i < replays.length; i++) {
        if (!Arrays.equals(checkpoints[i], SerializationUtils.serialize(candidates[i].getHistory().getModel()))) {
          throw new IllegalStateException("Frozen original/tuned EOS checkpoint changed");
        }
        Result result = replays[i];
        if (result == null) {
          rows.add(null);
          chi.add(null);
          holdout.add(null);
          continue;
        }
        List<double[]> trajectory = new ArrayList<double[]>();
        double[] pressure = result.getPressuresBara();
        double[] water = result.getAquiferInfluxM3();
        double[] aquiferPressure = result.getAquiferPressuresBara();
        double[] time = result.getTimesSeconds();
        double[][] amounts = result.getComponentMoles();
        for (int step = 0; step < pressure.length; step++) {
          trajectory.add(new double[] {time[step], pressure[step], water[step], aquiferPressure[step], amounts[step][0]});
        }
        rows.add(trajectory);
        chi.add(result.getCalibrationChiSquare());
        holdout.add(result.getHoldoutChiSquare());
      }
      List<double[]> parameterQuantiles = new ArrayList<double[]>();
      List<double[]> pressureQuantiles = new ArrayList<double[]>();
      long quantileStart = System.nanoTime();
      for (JsonElement probability : reference.getAsJsonArray("probabilities")) {
        parameterQuantiles.add(posterior.parameterQuantiles(probability.getAsDouble()));
        pressureQuantiles.add(posterior.pressureQuantiles(probability.getAsDouble()));
      }
      double quantileSeconds = (System.nanoTime() - quantileStart) / 1.0e9;
      Map<String, Object> report = new LinkedHashMap<String, Object>();
      report.put("name", test.get("name").getAsString());
      report.put("rows", rows);
      report.put("chiSquare", chi);
      report.put("holdoutChiSquare", holdout);
      report.put("prior", posterior.getPriorMasses());
      report.put("weights", posterior.getPosteriorMasses());
      report.put("means", finite(posterior.getPosteriorMeanParameters()));
      report.put("correlations", finite(posterior.getPosteriorCorrelations()));
      report.put("shifts", finite(posterior.getPriorStandardizedShifts()));
      report.put("parameterQuantiles", parameterQuantiles);
      report.put("pressureQuantiles", pressureQuantiles);
      report.put("effectiveCount", posterior.getEffectiveScenarioCount());
      report.put("logEvidence", posterior.getLogEvidenceWithoutGaussianConstant());
      report.put("rejectedMass", posterior.getRejectedPriorMassFraction());
      report.put("rejected", posterior.getRejectedCandidates());
      report.put("best", posterior.getBestLikelihoodIndex());
      report.put("seconds", seconds);
      report.put("quantileSeconds", quantileSeconds);
      output.add(report);
    }
    try (FileWriter writer = new FileWriter(args[1])) {
      gson.toJson(output, writer);
    }
  }
}
