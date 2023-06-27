package neqsim.processSimulation.mechanicalDesign.designStandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;
import neqsim.processSimulation.processEquipment.separator.SeparatorInterface;

/**
 * <p>
 * SeparatorDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparatorDesignStandard extends DesignStandard {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(SeparatorDesignStandard.class);

  /**
   * <p>
   * getFg.
   * </p>
   *
   * @return the Fg
   */
  public double getFg() {
    return Fg;
  }

  /**
   * <p>
   * setFg.
   * </p>
   *
   * @param Fg the Fg to set
   */
  public void setFg(double Fg) {
    this.Fg = Fg;
  }

  double gasLoadFactor = 0.11;
  private double Fg = 0.8;
  private double volumetricDesignFactor = 1.0;

  /**
   * <p>
   * Constructor for SeparatorDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
   */
  public SeparatorDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase();
    java.sql.ResultSet dataSet = null;
    try {
      try {
        dataSet = database.getResultSet(
            ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Separator' AND Company='"
                + standardName + "'"));
        while (dataSet.next()) {
          String specName = dataSet.getString("SPECIFICATION");
          if (specName.equals("GasLoadFactor")) {
            gasLoadFactor = (Double.parseDouble(dataSet.getString("MAXVALUE"))
                + Double.parseDouble(dataSet.getString("MINVALUE"))) / 2.0;
          }
          if (specName.equals("Fg")) {
            Fg = (Double.parseDouble(dataSet.getString("MAXVALUE"))
                + Double.parseDouble(dataSet.getString("MINVALUE"))) / 2.0;
          }
          if (specName.equals("VolumetricDesignFactor")) {
            volumetricDesignFactor = (Double.parseDouble(dataSet.getString("MAXVALUE"))
                + Double.parseDouble(dataSet.getString("MINVALUE"))) / 2.0;
          }
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
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
  }

  /**
   * <p>
   * Getter for the field <code>gasLoadFactor</code>.
   * </p>
   *
   * @return a double
   */
  public double getGasLoadFactor() {
    if (standardName.equals("StatoilTR")) {
      return gasLoadFactor;
    } else {
      return gasLoadFactor;
    }
  }

  /**
   * <p>
   * Getter for the field <code>volumetricDesignFactor</code>.
   * </p>
   *
   * @return a double
   */
  public double getVolumetricDesignFactor() {
    if (standardName.equals("StatoilTR")) {
      return volumetricDesignFactor;
    } else {
      return volumetricDesignFactor;
    }
  }

  /**
   * <p>
   * Setter for the field <code>volumetricDesignFactor</code>.
   * </p>
   *
   * @param volumetricDesignFactor the volumetricDesignFactor to set
   */
  public void setVolumetricDesignFactor(double volumetricDesignFactor) {
    this.volumetricDesignFactor = volumetricDesignFactor;
  }

  /**
   * <p>
   * getLiquidRetentionTime.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
   * @return a double
   */
  public double getLiquidRetentionTime(String name, MechanicalDesign equipmentInn) {
    double retTime = 90.0;
    double dens = ((SeparatorInterface) equipmentInn.getProcessEquipment()).getThermoSystem()
        .getPhase(1).getPhysicalProperties().getDensity() / 1000.0;

    // select correct residensetime from database
    // to be implmented
    if (name.equals("API12J")) {
      if (dens < 0.85) {
        retTime = 60.0;
      } else if (dens > 0.93) {
        retTime = 180.0;
      }
    }
    return retTime;
  }
}
