package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer.State;

/**
 * Checks prescribed-pressure water exchange against an independent high-precision linear ODE solution.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FetkovichAquiferPrecisionTest {
  /**
   * Checks finite exposures and explicit zero/infinite-support limits without changing the frozen tolerances.
   *
   * @throws Exception if the reference or diagnostic output cannot be read or written
   */
  @Test
  void agreesWithIndependentPrescribedPressureGrid() throws Exception {
    checkReference("grid", 776);
  }

  /**
   * Checks both sides of the old and new arithmetic boundaries, including signed-flow extrema.
   *
   * @throws Exception if the reference or diagnostic output cannot be read or written
   */
  @Test
  void remainsAccurateAcrossSmallExposureBoundaries() throws Exception {
    checkReference("boundaries", 668);
  }

  /**
   * Retains every native whole/subdivided result before asserting the complete reference matrix.
   *
   * @param name frozen reference matrix identifier
   * @param expectedRows required reference row count
   * @throws Exception if input or complete diagnostic output cannot be read or written
   */
  private void checkReference(String name, int expectedRows) throws Exception {
    Path directory = Paths.get("target", "aquifer-precision");
    Files.createDirectories(directory);
    InputStream input = getClass().getResourceAsStream("/neqsim/pvtsimulation/aquifer-precision/" + name + ".csv");
    assertNotNull(input);
    List<String> failures = new ArrayList<String>();
    int rows = 0;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        BufferedWriter writer = Files.newBufferedWriter(directory.resolve(name + ".csv"), StandardCharsets.UTF_8)) {
      reader.readLine();
      writer.write(
          "id,pa_bara,We_m3,min_We_m3,max_We_m3,refined_pa_bara,refined_We_m3,refined_min_We_m3,refined_max_We_m3\n");
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split(",");
        assertEquals(11, fields.length);
        double[] values = new double[10];
        for (int i = 0; i < values.length; i++) {
          values[i] = Double.parseDouble(fields[i + 1]);
        }
        FetkovichAquifer aquifer = new FetkovichAquifer(values[0], values[1], values[2], 300.0);
        State initial = aquifer.initialState();
        State whole = aquifer.advance(initial, values[3], values[4], values[5]);
        State refined = initial;
        double minimum = 0.0;
        double maximum = 0.0;
        for (int i = 0; i < 16; i++) {
          refined = aquifer.advance(refined, values[3] / 16, values[4] + (values[5] - values[4]) * i / 16,
              values[4] + (values[5] - values[4]) * (i + 1) / 16);
          minimum = Math.min(minimum, refined.getIntervalMinimumCumulativeInfluxM3());
          maximum = Math.max(maximum, refined.getIntervalMaximumCumulativeInfluxM3());
        }
        double[] actual = {whole.getPressureBara(), whole.getCumulativeInfluxM3(),
            whole.getIntervalMinimumCumulativeInfluxM3(), whole.getIntervalMaximumCumulativeInfluxM3(),
            refined.getPressureBara(), refined.getCumulativeInfluxM3(), minimum, maximum};
        writer.write(fields[0]);
        for (int i = 0; i < actual.length; i++) {
          double reference = values[6 + i % 4];
          double tolerance = i % 4 == 0 ? 1.0e-9 : 1.0e-8 + 2.0e-12 * Math.max(1.0, Math.abs(reference));
          if (!Double.isFinite(actual[i]) || Math.abs(actual[i] - reference) > tolerance) {
            failures.add(fields[0] + ":column" + i + ":actual=" + actual[i] + ":reference=" + reference);
          }
          writer.write("," + actual[i]);
        }
        writer.newLine();
        assertEquals(0.0, initial.getCumulativeInfluxM3(), 0.0);
        assertEquals(300.0, initial.getPressureBara(), 0.0);
        rows++;
      }
    }
    assertEquals(expectedRows, rows);
    assertEquals(new ArrayList<String>(), failures, "Complete failed numerical comparisons");
  }
}
