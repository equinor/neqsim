import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion.FixedCellResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Standalone unchanged-family runtime control, using only the pre-existing API.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FixedCellRuntimeProbe {
  /** Prevents construction. */
  private FixedCellRuntimeProbe() {
  }

  /**
   * Runs a fresh copied trial from the identical supplied sample.
   *
   * @param sample declared unmodified synthetic sample
   * @return sum of retained cell volumes, m3
   */
  private static double run(SystemInterface sample) {
    FixedCellResult result = new ConstantVolumeDepletion(sample).runFixedCellDepletion(350,
        new double[] {200, 100, 50});
    double totalVolume = 0;
    for (int row = 0; row < result.getSnapshots().length; row++) {
      totalVolume += result.getSnapshots()[row].getRetainedFluid().getCorrectedVolume();
    }
    return totalVolume;
  }

  /**
   * Writes warmed raw timings; compare alternating baseline/candidate forks in the same environment.
   *
   * @param args JSON output filename and optional positive warmup/trials-per-sample counts
   * @throws Exception if sample setup or output writing fails
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 1 && args.length != 3) {
      throw new IllegalArgumentException("Output filename and optional warmup/trial counts required");
    }
    int warmupTrials = args.length == 3 ? Integer.parseInt(args[1]) : 10;
    int trialsPerSample = args.length == 3 ? Integer.parseInt(args[2]) : 10;
    if (warmupTrials <= 0 || trialsPerSample <= 0) {
      throw new IllegalArgumentException("Positive warmup and sample trial counts required");
    }
    SystemInterface sample = new SystemSrkEos(350, 200);
    sample.addComponent("methane", .6);
    sample.addComponent("n-decane", .4);
    sample.setMixingRule("classic");
    sample.setMultiPhaseCheck(true);
    sample.useVolumeCorrection(false);
    sample.init(0);
    double checksum = 0;
    for (int warmup = 0; warmup < warmupTrials; warmup++) {
      checksum += run(sample);
    }
    double[] seconds = new double[8];
    for (int sampleIndex = 0; sampleIndex < seconds.length; sampleIndex++) {
      long start = System.nanoTime();
      for (int trial = 0; trial < trialsPerSample; trial++) {
        checksum += run(sample);
      }
      seconds[sampleIndex] = (System.nanoTime() - start) / 1e9;
    }
    if (!Double.isFinite(checksum) || checksum <= 0) {
      throw new IllegalStateException("Invalid unchanged-family runtime control");
    }
    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("warmupTrials", warmupTrials);
    metrics.put("trialsPerSample", trialsPerSample);
    metrics.put("seconds", seconds);
    metrics.put("checksumVolumeM3", checksum);
    metrics.put("javaVersion", System.getProperty("java.version"));
    Files.write(Paths.get(args[0]), new Gson().toJson(metrics).getBytes(StandardCharsets.UTF_8));
  }
}
