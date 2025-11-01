package neqsim.process.mechanicaldesign.data;

import java.sql.ResultSet;
import java.util.Locale;
import java.util.Optional;
import neqsim.process.mechanicaldesign.DesignLimitData;
import neqsim.util.database.NeqSimProcessDesignDataBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reads mechanical design limits from the NeqSim process design database.
 */
public class DatabaseMechanicalDesignDataSource implements MechanicalDesignDataSource {
  private static final Logger logger = LogManager.getLogger(DatabaseMechanicalDesignDataSource.class);

  private static final String QUERY_TEMPLATE = "SELECT SPECIFICATION, MAXVALUE, MINVALUE FROM "
      + "TechnicalRequirements_Process WHERE EQUIPMENTTYPE='%s' AND Company='%s'";

  @Override
  public Optional<DesignLimitData> getDesignLimits(String equipmentTypeName,
      String companyIdentifier) {
    if (equipmentTypeName == null || equipmentTypeName.isEmpty() || companyIdentifier == null
        || companyIdentifier.isEmpty()) {
      return Optional.empty();
    }

    String query = String.format(Locale.ROOT, QUERY_TEMPLATE, equipmentTypeName, companyIdentifier);
    DesignLimitData.Builder builder = DesignLimitData.builder();
    boolean found = false;

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet dataSet = database.getResultSet(query)) {
      while (dataSet.next()) {
        String specification = dataSet.getString("SPECIFICATION");
        double maxValue = parseDouble(dataSet.getString("MAXVALUE"));
        double minValue = parseDouble(dataSet.getString("MINVALUE"));
        double representative = Double.isNaN(maxValue) ? minValue
            : Double.isNaN(minValue) ? maxValue : (maxValue + minValue) / 2.0;
        switch (specification) {
          case "MaxPressure":
            builder.maxPressure(representative);
            found = true;
            break;
          case "MinPressure":
            builder.minPressure(representative);
            found = true;
            break;
          case "MaxTemperature":
            builder.maxTemperature(representative);
            found = true;
            break;
          case "MinTemperature":
            builder.minTemperature(representative);
            found = true;
            break;
          case "CorrosionAllowance":
            builder.corrosionAllowance(representative);
            found = true;
            break;
          case "JointEfficiency":
            builder.jointEfficiency(representative);
            found = true;
            break;
          default:
            break;
        }
      }
    } catch (Exception ex) {
      logger.error("Unable to read design limits from database", ex);
      return Optional.empty();
    }

    return found ? Optional.of(builder.build()) : Optional.empty();
  }

  private double parseDouble(String value) {
    if (value == null) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ex) {
      return Double.NaN;
    }
  }
}
