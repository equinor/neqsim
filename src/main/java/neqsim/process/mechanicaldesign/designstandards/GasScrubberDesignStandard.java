package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * GasScrubberDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class GasScrubberDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GasScrubberDesignStandard.class);

  double gasLoadFactor = 0.11;
  double designFactorVolumeFlow = 1.1;
  double lengthGasInletToMeshpad = 550.0; // unit: mm
  double lengthGasInetToHHLL = 550.0; // unit: mm
  double lengthMeshPadToDemistingCyclone = 550.0; // unit: mm

  /**
   * <p>
   * Constructor for GasScrubberDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public GasScrubberDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      java.sql.ResultSet dataSet = null;
      try {
        dataSet = database.getResultSet(
            ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Gas scrubber' AND Company='"
                + standardName + "'"));
        while (dataSet.next()) {
          String specName = dataSet.getString("SPECIFICATION");
          if (specName.equals("GasLoadFactor")) {
            gasLoadFactor = Double.parseDouble(dataSet.getString("MAXVALUE"));
          } else if (specName.equals("FlowDesignFactor")) {
            designFactorVolumeFlow = Double.parseDouble(dataSet.getString("MAXVALUE"));
          } else if (specName.equals("LengthGasInetToHHLL")) {
            designFactorVolumeFlow = Double.parseDouble(dataSet.getString("MINVALUE"));
          } else if (specName.equals("LengthMeshPadToDemistingCyclone")) {
            designFactorVolumeFlow = Double.parseDouble(dataSet.getString("MINVALUE"));
          }
        }

        // gasLoadFactor = Double.parseDouble(dataSet.getString("gasloadfactor"));
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      } finally {
        try {
          if (dataSet != null) {
            dataSet.close();
          }
        } catch (Exception ex) {
          System.out.println("error closing database.....GasScrubberDesignStandard");
          logger.error(ex.getMessage(), ex);
        }
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      logger.error(e.getMessage());
    }
  }

  /**
   * <p>
   * Getter for the field <code>gasLoadFactor</code>.
   * </p>
   *
   * @return a double
   */
  public double getGasLoadFactor() {
    return gasLoadFactor;
  }

  /**
   * <p>
   * getVolumetricDesignFactor.
   * </p>
   *
   * @return a double
   */
  public double getVolumetricDesignFactor() {
    return designFactorVolumeFlow;
  }
}
