import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Standalone original-family protocol control for all frozen off-grid paths.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class OffGridMasterProbe {
  /** Prevents construction. */
  private OffGridMasterProbe() {
  }

  /**
   * Retains every baseline outcome without changing the supplied input inventory.
   *
   * @param args independent reference JSON and diagnostic output JSON paths
   * @throws Exception if reference/output files cannot be accessed
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Reference and output filenames required");
    }
    JsonArray cases;
    try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(Paths.get(args[0])),
        StandardCharsets.UTF_8)) {
      cases = new Gson().fromJson(reader, JsonArray.class);
    }
    List<Map<String, Object>> outcomes = new ArrayList<Map<String, Object>>();
    for (int index = 0; index < cases.size(); index++) {
      JsonObject reference = cases.get(index).getAsJsonObject();
      double temperature = reference.get("temperatureK").getAsDouble();
      double water = reference.get("waterMoleFraction").getAsDouble();
      double scale = reference.get("sampleMoles").getAsDouble();
      JsonArray rows = reference.getAsJsonArray("rows");
      double[] pressures = new double[rows.size()];
      for (int row = 0; row < pressures.length; row++) {
        pressures[row] = rows.get(row).getAsJsonObject().get("pressureBara").getAsDouble();
      }
      SystemInterface sample = new SystemSrkEos(temperature, pressures[0]);
      sample.addComponent("methane", (1 - water) * scale);
      sample.addComponent("water", water * scale);
      sample.setMixingRule("classic");
      sample.setMultiPhaseCheck(true);
      sample.useVolumeCorrection(false);
      sample.init(0);
      byte[] before = SerializationUtils.serialize(sample);
      Map<String, Object> outcome = new LinkedHashMap<String, Object>();
      outcome.put("case", index);
      outcome.put("temperatureK", temperature);
      outcome.put("waterMoleFraction", water);
      outcome.put("sampleMoles", scale);
      outcome.put("pressuresBara", pressures);
      outcomes.add(outcome);
      try {
        ConstantVolumeDepletion.FixedCellResult result = new ConstantVolumeDepletion(sample)
            .runFixedCellDepletion(temperature, pressures);
        outcome.put("status", "completed");
        outcome.put("completedRows", result.getSnapshots().length);
      } catch (RuntimeException ex) {
        outcome.put("status", "rejected");
        outcome.put("error", ex.toString());
      }
      boolean unchanged = Arrays.equals(before, SerializationUtils.serialize(sample));
      outcome.put("callerUnchanged", unchanged);
      if (!unchanged) {
        throw new AssertionError("Baseline changed supplied sample at case " + index);
      }
    }
    Files.write(Paths.get(args[1]), new Gson().toJson(outcomes).getBytes(StandardCharsets.UTF_8));
  }
}
