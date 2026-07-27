package neqsim.thermo.util.benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Dataset;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Point;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Property;

/** Published H2-CO2 and H2-N2-CO2 phase-equilibrium benchmark datasets. */
public final class H2CO2PhaseEquilibriumData {
  private static final String RESOURCE =
      "/data/thermo/benchmark/zhang2026_h2_co2_phase_equilibrium.csv";
  private static final String DOI = "10.1063/5.0288386";
  private static final String CITATION =
      "Zhang et al. (2026), Measurement of phase equilibrium characteristics and "
          + "equation-of-state applicability for hydrogen-containing CO2 systems, "
          + "International Journal of Fluid Engineering 3, 013903.";
  private static final String LICENSE =
      "Values transcribed from Tables IV-V of the cited open article; cite the original source.";

  private H2CO2PhaseEquilibriumData() {}

  /**
   * Loads all published binary and ternary bubble- and dew-point data.
   *
   * <p>
   * The article does not report pointwise pressure uncertainty. The framework therefore records
   * uncertainty as unavailable rather than inventing a value.
   * </p>
   *
   * @return phase-equilibrium dataset containing 24 points
   * @throws IOException if the packaged resource cannot be read
   */
  public static Dataset load() throws IOException {
    InputStream input = H2CO2PhaseEquilibriumData.class.getResourceAsStream(RESOURCE);
    if (input == null) {
      throw new IOException("Missing benchmark resource " + RESOURCE);
    }
    List<Point> points = new ArrayList<Point>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      if (line == null) {
        throw new IOException("Empty benchmark resource " + RESOURCE);
      }
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
          continue;
        }
        String[] values = line.split(",", -1);
        if (values.length != 8) {
          throw new IOException("Expected 8 CSV columns but found " + values.length);
        }
        double temperatureK = Double.parseDouble(values[1]) + 273.15;
        double pressureBara = Double.parseDouble(values[3]) * 10.0;
        Map<String, Double> composition = new LinkedHashMap<String, Double>();
        composition.put("CO2", Double.parseDouble(values[4]));
        composition.put("hydrogen", Double.parseDouble(values[5]));
        double nitrogenFraction = Double.parseDouble(values[6]);
        if (nitrogenFraction > 0.0) {
          composition.put("nitrogen", nitrogenFraction);
        }
        Property property =
            "bubble".equals(values[2])
                ? Property.BUBBLE_POINT_PRESSURE
                : Property.DEW_POINT_PRESSURE;
        points.add(
            new Point(
                property,
                temperatureK,
                pressureBara,
                pressureBara,
                Double.NaN,
                "bara",
                composition));
      }
    }
    return new Dataset(
        "Zhang 2026 hydrogen-containing CO2 phase equilibrium",
        CITATION,
        DOI,
        LICENSE,
        points);
  }
}
