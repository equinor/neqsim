package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Inline spreadsheet / calculator block for embedding custom calculations directly in a flowsheet.
 * Similar to UniSim's spreadsheet, this block lets users define named import cells that pull values
 * from process streams or equipment, named formula cells that perform arithmetic on those values,
 * and export cells that push computed results back into the process.
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * SpreadsheetBlock sheet = new SpreadsheetBlock("Energy Calc");
 * sheet.addStreamImportCell("T_in", feed, s -&gt; s.getTemperature("C"));
 * sheet.addStreamImportCell("T_out", product, s -&gt; s.getTemperature("C"));
 * sheet.addStreamImportCell("mdot", feed, s -&gt; s.getFlowRate("kg/hr"));
 * sheet.addFormulaCell("deltaT", cells -&gt; cells.get("T_out") - cells.get("T_in"));
 * sheet.addFormulaCell("duty_kW", cells -&gt; cells.get("mdot") * 4.18 * cells.get("deltaT") / 3600.0);
 * sheet.addExportCell("duty_kW", cooler, (eq, val) -&gt; ((Cooler) eq).setEnergyInput(val));
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class SpreadsheetBlock extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SpreadsheetBlock.class);

  /** Ordered map of cell names to their definitions. */
  private final LinkedHashMap<String, CellDefinition> cells = new LinkedHashMap<>();

  /** Current computed values for each cell. */
  private final LinkedHashMap<String, Double> cellValues = new LinkedHashMap<>();

  /** Export targets that push computed values back into the process. */
  private final List<ExportTarget> exportTargets = new ArrayList<>();

  /**
   * Constructor for SpreadsheetBlock.
   *
   * @param name a {@link java.lang.String} name for this spreadsheet block
   */
  public SpreadsheetBlock(String name) {
    super(name);
  }

  /**
   * Add an import cell that reads a value from a stream each time the block runs.
   *
   * @param cellName unique name for this cell (e.g. "T_inlet")
   * @param stream the source stream to read from
   * @param reader function that extracts a double value from the stream
   */
  public void addStreamImportCell(String cellName, StreamInterface stream,
      Function<StreamInterface, Double> reader) {
    if (cellName == null || cellName.trim().isEmpty()) {
      throw new IllegalArgumentException("Cell name cannot be null or empty");
    }
    cells.put(cellName, new ImportCellFromStream(stream, reader));
  }

  /**
   * Add an import cell that reads a value from any process equipment each time the block runs.
   *
   * @param cellName unique name for this cell
   * @param equipment the source equipment to read from
   * @param reader function that extracts a double value from the equipment
   */
  public void addImportCell(String cellName, ProcessEquipmentInterface equipment,
      Function<ProcessEquipmentInterface, Double> reader) {
    if (cellName == null || cellName.trim().isEmpty()) {
      throw new IllegalArgumentException("Cell name cannot be null or empty");
    }
    cells.put(cellName, new ImportCellFromEquipment(equipment, reader));
  }

  /**
   * Add a constant cell with a fixed value.
   *
   * @param cellName unique name for this cell
   * @param value the constant value
   */
  public void addConstantCell(String cellName, double value) {
    if (cellName == null || cellName.trim().isEmpty()) {
      throw new IllegalArgumentException("Cell name cannot be null or empty");
    }
    cells.put(cellName, new ConstantCell(value));
  }

  /**
   * Add a formula cell that computes a value from the current cell values. Formula cells can
   * reference any previously defined cell (import, constant, or earlier formula cells).
   *
   * @param cellName unique name for this cell
   * @param formula function that takes the map of current cell values and returns the computed value
   */
  public void addFormulaCell(String cellName, Function<Map<String, Double>, Double> formula) {
    if (cellName == null || cellName.trim().isEmpty()) {
      throw new IllegalArgumentException("Cell name cannot be null or empty");
    }
    cells.put(cellName, new FormulaCell(formula));
  }

  /**
   * Add an export target that pushes a computed cell value back into process equipment after
   * calculation.
   *
   * @param cellName the cell whose value to export
   * @param target the equipment to receive the value
   * @param writer biconsumer that applies the value to the equipment
   */
  public void addExportCell(String cellName, ProcessEquipmentInterface target,
      ExportWriter writer) {
    exportTargets.add(new ExportTarget(cellName, target, writer));
  }

  /**
   * Get the current computed value of a cell.
   *
   * @param cellName the cell name
   * @return the current value, or {@code Double.NaN} if not yet computed
   */
  public double getCellValue(String cellName) {
    Double val = cellValues.get(cellName);
    return val != null ? val : Double.NaN;
  }

  /**
   * Get all current cell values as an unmodifiable map.
   *
   * @return map of cell name to computed value
   */
  public Map<String, Double> getAllCellValues() {
    return Collections.unmodifiableMap(cellValues);
  }

  /**
   * Get the names of all defined cells in order.
   *
   * @return list of cell names
   */
  public List<String> getCellNames() {
    return new ArrayList<>(cells.keySet());
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    // Phase 1: evaluate all cells in definition order
    for (Map.Entry<String, CellDefinition> entry : cells.entrySet()) {
      String cellName = entry.getKey();
      CellDefinition def = entry.getValue();
      try {
        double value = def.evaluate(cellValues);
        cellValues.put(cellName, value);
      } catch (Exception ex) {
        logger.error("Error evaluating cell '" + cellName + "'", ex);
        cellValues.put(cellName, Double.NaN);
      }
    }

    // Phase 2: push export values to target equipment
    for (ExportTarget export : exportTargets) {
      Double value = cellValues.get(export.cellName);
      if (value == null || Double.isNaN(value)) {
        logger.warn("Skipping export of cell '" + export.cellName + "': value is NaN or missing");
        continue;
      }
      try {
        export.writer.apply(export.target, value);
      } catch (Exception ex) {
        logger.error("Error exporting cell '" + export.cellName + "'", ex);
      }
    }

    setCalculationIdentifier(id);
  }

  /**
   * Functional interface for writing a computed value to process equipment.
   */
  @FunctionalInterface
  public interface ExportWriter extends java.io.Serializable {
    /**
     * Apply the computed value to the target equipment.
     *
     * @param equipment the target equipment
     * @param value the computed cell value
     */
    void apply(ProcessEquipmentInterface equipment, double value);
  }

  // --- Internal cell definition hierarchy ---

  /**
   * Base interface for cell definitions.
   */
  private interface CellDefinition extends java.io.Serializable {
    /**
     * Evaluate this cell given the current state of all previously computed cells.
     *
     * @param currentValues map of cell name to value for already-computed cells
     * @return the evaluated value
     */
    double evaluate(Map<String, Double> currentValues);
  }

  /**
   * Cell that reads from a stream.
   */
  private static class ImportCellFromStream implements CellDefinition {
    private static final long serialVersionUID = 1L;
    private final StreamInterface stream;
    private final Function<StreamInterface, Double> reader;

    ImportCellFromStream(StreamInterface stream, Function<StreamInterface, Double> reader) {
      this.stream = stream;
      this.reader = reader;
    }

    @Override
    public double evaluate(Map<String, Double> currentValues) {
      return reader.apply(stream);
    }
  }

  /**
   * Cell that reads from process equipment.
   */
  private static class ImportCellFromEquipment implements CellDefinition {
    private static final long serialVersionUID = 1L;
    private final ProcessEquipmentInterface equipment;
    private final Function<ProcessEquipmentInterface, Double> reader;

    ImportCellFromEquipment(ProcessEquipmentInterface equipment,
        Function<ProcessEquipmentInterface, Double> reader) {
      this.equipment = equipment;
      this.reader = reader;
    }

    @Override
    public double evaluate(Map<String, Double> currentValues) {
      return reader.apply(equipment);
    }
  }

  /**
   * Cell with a constant value.
   */
  private static class ConstantCell implements CellDefinition {
    private static final long serialVersionUID = 1L;
    private final double value;

    ConstantCell(double value) {
      this.value = value;
    }

    @Override
    public double evaluate(Map<String, Double> currentValues) {
      return value;
    }
  }

  /**
   * Cell computed by a formula referencing other cells.
   */
  private static class FormulaCell implements CellDefinition {
    private static final long serialVersionUID = 1L;
    private final Function<Map<String, Double>, Double> formula;

    FormulaCell(Function<Map<String, Double>, Double> formula) {
      this.formula = formula;
    }

    @Override
    public double evaluate(Map<String, Double> currentValues) {
      return formula.apply(Collections.unmodifiableMap(currentValues));
    }
  }

  /**
   * Internal record for export targets.
   */
  private static class ExportTarget implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    final String cellName;
    final ProcessEquipmentInterface target;
    final ExportWriter writer;

    ExportTarget(String cellName, ProcessEquipmentInterface target, ExportWriter writer) {
      this.cellName = cellName;
      this.target = target;
      this.writer = writer;
    }
  }
}
