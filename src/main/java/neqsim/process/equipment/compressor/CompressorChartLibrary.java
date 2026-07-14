package neqsim.process.equipment.compressor;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;

/**
 * A named library ("bundle" / database) of compressor performance charts.
 *
 * <p>
 * The library lets a compressor hold several performance maps at once - for example the vendor expected curve, the
 * as-tested curve, and a field-fitted curve - each stored under a unique name together with descriptive
 * {@link CompressorChartMetadata}. A chart can then be selected professionally by name, and the whole library can be
 * serialized to and from JSON so that a shared database of vendor curves can be maintained and reused.
 * </p>
 *
 * <p>
 * Typical use on a {@link Compressor}:
 * </p>
 *
 * <pre>
 * compressor.getChartLibrary().add("BCL405B-export-design", expectedChart, metadata);
 * compressor.getChartLibrary().add("BCL405B-export-tested", asTestedChart);
 * compressor.selectChart("BCL405B-export-tested"); // switch the active chart
 * </pre>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class CompressorChartLibrary implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CompressorChartLibrary.class);

  private String libraryName = "compressor chart library";
  private final Map<String, CompressorChartInterface> charts = new LinkedHashMap<String, CompressorChartInterface>();
  private final Map<String, CompressorChartMetadata> metadata = new LinkedHashMap<String, CompressorChartMetadata>();
  private String selectedName = null;

  /**
   * Default constructor.
   */
  public CompressorChartLibrary() {
  }

  /**
   * Constructor with a library name.
   *
   * @param libraryName a descriptive name for the library
   */
  public CompressorChartLibrary(String libraryName) {
    this.libraryName = libraryName;
  }

  /**
   * Get the library name.
   *
   * @return the library name
   */
  public String getLibraryName() {
    return libraryName;
  }

  /**
   * Set the library name.
   *
   * @param libraryName the library name
   */
  public void setLibraryName(String libraryName) {
    this.libraryName = libraryName;
  }

  /**
   * Add a chart to the library under a unique name (without metadata).
   *
   * @param name the unique chart name (must be non-null and non-empty)
   * @param chart the compressor chart to store
   * @return this library for chaining
   */
  public CompressorChartLibrary add(String name, CompressorChartInterface chart) {
    return add(name, chart, null);
  }

  /**
   * Add a chart to the library under a unique name, with descriptive metadata.
   *
   * @param name the unique chart name (must be non-null and non-empty)
   * @param chart the compressor chart to store (must be non-null)
   * @param chartMetadata descriptive metadata (may be null)
   * @return this library for chaining
   * @throws IllegalArgumentException if name is null/empty or chart is null
   */
  public CompressorChartLibrary add(String name, CompressorChartInterface chart,
      CompressorChartMetadata chartMetadata) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("chart name must not be null or empty");
    }
    if (chart == null) {
      throw new IllegalArgumentException("chart must not be null");
    }
    charts.put(name, chart);
    if (chartMetadata != null) {
      metadata.put(name, chartMetadata);
    }
    if (selectedName == null) {
      selectedName = name;
    }
    return this;
  }

  /**
   * Get a chart by name.
   *
   * @param name the chart name
   * @return the chart, or null if not present
   */
  public CompressorChartInterface get(String name) {
    return charts.get(name);
  }

  /**
   * Get the metadata for a chart.
   *
   * @param name the chart name
   * @return the metadata, or null if none stored
   */
  public CompressorChartMetadata getMetadata(String name) {
    return metadata.get(name);
  }

  /**
   * Check whether a chart with the given name is present.
   *
   * @param name the chart name
   * @return true if present
   */
  public boolean contains(String name) {
    return charts.containsKey(name);
  }

  /**
   * Remove a chart from the library.
   *
   * @param name the chart name
   * @return the removed chart, or null if not present
   */
  public CompressorChartInterface remove(String name) {
    metadata.remove(name);
    CompressorChartInterface removed = charts.remove(name);
    if (name != null && name.equals(selectedName)) {
      selectedName = charts.isEmpty() ? null : charts.keySet().iterator().next();
    }
    return removed;
  }

  /**
   * Get the number of charts in the library.
   *
   * @return the chart count
   */
  public int size() {
    return charts.size();
  }

  /**
   * Get the names of all charts in insertion order.
   *
   * @return a list of chart names
   */
  public List<String> getNames() {
    return new ArrayList<String>(charts.keySet());
  }

  /**
   * Select a chart by name and mark it as the active chart.
   *
   * @param name the chart name
   * @return the selected chart
   * @throws IllegalArgumentException if no chart with that name exists
   */
  public CompressorChartInterface select(String name) {
    if (!charts.containsKey(name)) {
      throw new IllegalArgumentException("no chart named '" + name + "' in library; available: " + getNames());
    }
    selectedName = name;
    return charts.get(name);
  }

  /**
   * Get the currently selected chart.
   *
   * @return the selected chart, or null if the library is empty
   */
  public CompressorChartInterface getSelected() {
    return selectedName == null ? null : charts.get(selectedName);
  }

  /**
   * Get the name of the currently selected chart.
   *
   * @return the selected chart name, or null if the library is empty
   */
  public String getSelectedName() {
    return selectedName;
  }

  /**
   * Produce a compact JSON catalog of the library (names + metadata + selected flag) for browsing/selection UIs. The
   * curve data itself is not included; use {@link #toJson()} for a full round-trippable serialization.
   *
   * @return a JSON string describing the available charts
   */
  public String describe() {
    List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
    for (String name : charts.keySet()) {
      Map<String, Object> e = new LinkedHashMap<String, Object>();
      e.put("name", name);
      e.put("selected", name.equals(selectedName));
      e.put("metadata", metadata.get(name));
      entries.add(e);
    }
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("libraryName", libraryName);
    root.put("selectedName", selectedName);
    root.put("count", charts.size());
    root.put("charts", entries);
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(root);
  }

  /** Data-transfer object for a single chart entry (JSON round-trip). */
  private static class ChartEntryDto {
    String name;
    CompressorChartMetadata metadata;
    String headUnit;
    double[] chartConditions;
    double[] speeds;
    double[][] flows;
    double[][] heads;
    double[][] polytropicEfficiencies;
    double[] surgeFlow;
    double[] surgeHead;
  }

  /** Data-transfer object for the whole library (JSON round-trip). */
  private static class LibraryDto {
    String libraryName;
    String selectedName;
    List<ChartEntryDto> charts = new ArrayList<ChartEntryDto>();
  }

  /**
   * Serialize the full library (all curves + metadata) to JSON, so it can be stored in a file or database and reloaded
   * with {@link #fromJson(String)}.
   *
   * @return a JSON string
   */
  public String toJson() {
    LibraryDto dto = new LibraryDto();
    dto.libraryName = libraryName;
    dto.selectedName = selectedName;
    for (Map.Entry<String, CompressorChartInterface> entry : charts.entrySet()) {
      CompressorChartInterface chart = entry.getValue();
      ChartEntryDto c = new ChartEntryDto();
      c.name = entry.getKey();
      c.metadata = metadata.get(entry.getKey());
      c.headUnit = chart.getHeadUnit();
      c.chartConditions = chart.getChartConditions();
      c.speeds = chart.getSpeeds();
      c.flows = chart.getFlows();
      c.heads = chart.getHeads();
      c.polytropicEfficiencies = chart.getPolytropicEfficiencies();
      SafeSplineSurgeCurve surge = chart.getSurgeCurve();
      if (surge != null) {
        c.surgeFlow = surge.getSortedFlow();
        c.surgeHead = surge.getSortedHead();
      }
      dto.charts.add(c);
    }
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(dto);
  }

  /**
   * Rebuild a library from a JSON string produced by {@link #toJson()}.
   *
   * @param json the JSON string
   * @return the reconstructed library
   */
  public static CompressorChartLibrary fromJson(String json) {
    LibraryDto dto = new GsonBuilder().create().fromJson(json, LibraryDto.class);
    CompressorChartLibrary library = new CompressorChartLibrary();
    if (dto == null) {
      return library;
    }
    if (dto.libraryName != null) {
      library.libraryName = dto.libraryName;
    }
    if (dto.charts != null) {
      for (ChartEntryDto c : dto.charts) {
        CompressorChart chart = new CompressorChart();
        if (c.speeds != null && c.flows != null && c.heads != null && c.polytropicEfficiencies != null) {
          double[] cond = c.chartConditions != null ? c.chartConditions : new double[] { 0.0, 0.0, 0.0, 0.0 };
          chart.setCurves(cond, c.speeds, c.flows, c.heads, c.polytropicEfficiencies);
          if (c.chartConditions != null && c.chartConditions.length >= 4) {
            chart.setReferenceConditions(c.chartConditions[0], c.chartConditions[1], c.chartConditions[2],
                c.chartConditions[3]);
          }
        }
        if (c.headUnit != null) {
          chart.setHeadUnit(c.headUnit);
        }
        if (c.surgeFlow != null && c.surgeHead != null && c.surgeFlow.length > 0) {
          chart.setSurgeCurve(new SafeSplineSurgeCurve(c.surgeFlow, c.surgeHead));
        }
        chart.setUseCompressorChart(true);
        library.add(c.name, chart, c.metadata);
      }
    }
    if (dto.selectedName != null && library.contains(dto.selectedName)) {
      library.selectedName = dto.selectedName;
    }
    return library;
  }

  /**
   * Save the library to a JSON file.
   *
   * @param path the file path
   * @throws IOException if the file cannot be written
   */
  public void saveToFile(String path) throws IOException {
    Files.write(Paths.get(path), toJson().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Load a library from a JSON file previously written by {@link #saveToFile(String)}.
   *
   * @param path the file path
   * @return the loaded library
   * @throws IOException if the file cannot be read
   */
  public static CompressorChartLibrary loadFromFile(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    return fromJson(new String(bytes, StandardCharsets.UTF_8));
  }
}
