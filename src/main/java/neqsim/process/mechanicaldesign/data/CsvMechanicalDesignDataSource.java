package neqsim.process.mechanicaldesign.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import neqsim.process.mechanicaldesign.DesignLimitData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads mechanical design limits from a CSV file. The file is expected to contain the columns
 * {@code EQUIPMENTTYPE}, {@code COMPANY}, {@code MAXPRESSURE}, {@code MINPRESSURE},
 * {@code MAXTEMPERATURE}, {@code MINTEMPERATURE}, {@code CORROSIONALLOWANCE}, and
 * {@code JOINTEFFICIENCY}. Column order is flexible as long as the header matches.
 */
public class CsvMechanicalDesignDataSource implements MechanicalDesignDataSource {
  private static final Logger logger = LogManager.getLogger(CsvMechanicalDesignDataSource.class);

  private final Path csvPath;

  public CsvMechanicalDesignDataSource(Path csvPath) {
    this.csvPath = csvPath;
  }

  @Override
  public Optional<DesignLimitData> getDesignLimits(String equipmentTypeName,
      String companyIdentifier) {
    if (csvPath == null) {
      return Optional.empty();
    }

    if (!Files.isReadable(csvPath)) {
      logger.warn("Design limit CSV file {} is not readable", csvPath);
      return Optional.empty();
    }

    String normalizedEquipment = normalize(equipmentTypeName);
    String normalizedCompany = normalize(companyIdentifier);

    try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
      String header = reader.readLine();
      if (header == null) {
        return Optional.empty();
      }
      String[] columns = header.split(",");
      ColumnIndex index = ColumnIndex.from(columns);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] tokens = line.split(",");
        if (tokens.length < index.requiredLength()) {
          continue;
        }
        if (!normalize(tokens[index.equipmentTypeIndex]).equals(normalizedEquipment)) {
          continue;
        }
        if (!normalize(tokens[index.companyIndex]).equals(normalizedCompany)) {
          continue;
        }
        return Optional.of(parse(tokens, index));
      }
    } catch (IOException ex) {
      logger.error("Failed to read mechanical design CSV {}", csvPath, ex);
    }
    return Optional.empty();
  }

  private DesignLimitData parse(String[] tokens, ColumnIndex index) {
    DesignLimitData.Builder builder = DesignLimitData.builder();
    builder.maxPressure(parseDouble(tokens, index.maxPressureIndex));
    builder.minPressure(parseDouble(tokens, index.minPressureIndex));
    builder.maxTemperature(parseDouble(tokens, index.maxTemperatureIndex));
    builder.minTemperature(parseDouble(tokens, index.minTemperatureIndex));
    builder.corrosionAllowance(parseDouble(tokens, index.corrosionAllowanceIndex));
    builder.jointEfficiency(parseDouble(tokens, index.jointEfficiencyIndex));
    return builder.build();
  }

  private double parseDouble(String[] tokens, int index) {
    if (index < 0 || index >= tokens.length) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(tokens[index].trim());
    } catch (NumberFormatException ex) {
      return Double.NaN;
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static final class ColumnIndex {
    private final int equipmentTypeIndex;
    private final int companyIndex;
    private final int maxPressureIndex;
    private final int minPressureIndex;
    private final int maxTemperatureIndex;
    private final int minTemperatureIndex;
    private final int corrosionAllowanceIndex;
    private final int jointEfficiencyIndex;

    private ColumnIndex(int equipmentTypeIndex, int companyIndex, int maxPressureIndex,
        int minPressureIndex, int maxTemperatureIndex, int minTemperatureIndex,
        int corrosionAllowanceIndex, int jointEfficiencyIndex) {
      this.equipmentTypeIndex = equipmentTypeIndex;
      this.companyIndex = companyIndex;
      this.maxPressureIndex = maxPressureIndex;
      this.minPressureIndex = minPressureIndex;
      this.maxTemperatureIndex = maxTemperatureIndex;
      this.minTemperatureIndex = minTemperatureIndex;
      this.corrosionAllowanceIndex = corrosionAllowanceIndex;
      this.jointEfficiencyIndex = jointEfficiencyIndex;
    }

    static ColumnIndex from(String[] columns) {
      int equipment = indexOf(columns, "EQUIPMENTTYPE");
      int company = indexOf(columns, "COMPANY");
      int maxPressure = indexOf(columns, "MAXPRESSURE");
      int minPressure = indexOf(columns, "MINPRESSURE");
      int maxTemperature = indexOf(columns, "MAXTEMPERATURE");
      int minTemperature = indexOf(columns, "MINTEMPERATURE");
      int corrosionAllowance = indexOf(columns, "CORROSIONALLOWANCE");
      int jointEfficiency = indexOf(columns, "JOINTEFFICIENCY");
      return new ColumnIndex(equipment, company, maxPressure, minPressure, maxTemperature,
          minTemperature, corrosionAllowance, jointEfficiency);
    }

    int requiredLength() {
      return Math.max(Math.max(Math.max(Math.max(Math.max(Math.max(Math.max(equipmentTypeIndex,
          companyIndex), maxPressureIndex), minPressureIndex), maxTemperatureIndex),
          minTemperatureIndex), corrosionAllowanceIndex), jointEfficiencyIndex) + 1;
    }

    private static int indexOf(String[] columns, String name) {
      for (int i = 0; i < columns.length; i++) {
        if (name.equalsIgnoreCase(columns[i].trim())) {
          return i;
        }
      }
      return -1;
    }
  }
}
