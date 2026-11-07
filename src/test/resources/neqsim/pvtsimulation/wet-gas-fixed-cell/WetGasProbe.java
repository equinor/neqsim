import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Baseline wet-gas family-change diagnostic, using only the unchanged-family API.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class WetGasProbe {
  /**
   * Executes every declared scalar baseline path without discarding rejections.
   *
   * @param args output JSON filename
   * @throws Exception if output cannot be written
   */
  public static void main(String[] args) throws Exception {
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    for (double temperature : new double[] {440, 450, 460}) {
      for (double water : new double[] {.05, .08, .1}) {
        SystemInterface input = new SystemSrkEos(temperature, 200);
        input.addComponent("methane", 1 - water);
        input.addComponent("water", water);
        input.setMixingRule("classic");
        input.setMultiPhaseCheck(true);
        input.useVolumeCorrection(false);
        input.init(0);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("temperatureK", temperature);
        row.put("waterMoleFraction", water);
        try {
          ConstantVolumeDepletion.FixedCellResult result = new ConstantVolumeDepletion(input)
              .runFixedCellDepletion(temperature, new double[] {200, 150, 100, 70, 50, 30, 20, 10, 5, 2});
          row.put("status", "completed");
          row.put("finalMoles", result.getSnapshots()[9].getRetainedFluid().getTotalNumberOfMoles());
        } catch (Exception ex) {
          row.put("status", "rejected");
          row.put("error", ex.toString());
        }
        rows.add(row);
      }
    }
    Files.write(Paths.get(args[0]),
        new GsonBuilder().setPrettyPrinting().create().toJson(rows).getBytes(StandardCharsets.UTF_8));
  }
}
