/*
 * BaseContract.java
 *
 * Created on 15. juni 2004, 21:43
 */

package neqsim.standards.salescontract;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.standards.StandardInterface;
import neqsim.standards.gasquality.BestPracticeHydrocarbonDewPoint;
import neqsim.standards.gasquality.Draft_ISO18453;
import neqsim.standards.gasquality.GasChromotograpyhBase;
import neqsim.standards.gasquality.Standard_ISO6974;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.gasquality.SulfurSpecificationMethod;
import neqsim.standards.gasquality.UKspecifications_ICF_SI;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * BaseContract class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class BaseContract implements ContractInterface {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BaseContract.class);

  private String[][] resultTable = new String[50][9];
  double waterDewPointTemperature = -12.0;
  double waterDewPointSpecPressure = 70.0;
  private String contractName = "";
  ArrayList<ContractSpecification> spesifications = new ArrayList<ContractSpecification>();
  private int specificationsNumber = 0;

  /**
   * <p>
   * Constructor for BaseContract.
   * </p>
   */
  public BaseContract() {}

  /**
   * <p>
   * Constructor for BaseContract.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public BaseContract(SystemInterface system) {
    StandardInterface standard = new Draft_ISO18453(system);
    spesifications.add(new ContractSpecification("", "", "", "water dew point specification",
        standard, 0, 0, "degC", 0, 0, 0, ""));
  }

  /**
   * <p>
   * Constructor for BaseContract.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param terminal a {@link java.lang.String} object
   * @param country a {@link java.lang.String} object
   */
  public BaseContract(SystemInterface system, String terminal, String country) {
    // int numb = 0;
    this.setContractName(contractName);
    try (
        neqsim.util.database.NeqSimContractDataBase database =
            new neqsim.util.database.NeqSimContractDataBase();
        java.sql.ResultSet dataSet =
            database.getResultSet("SELECT * FROM gascontractspecifications WHERE TERMINAL='"
                + terminal + "'" + " AND COUNTRY='" + country + "'")) {
      while (dataSet.next()) {
        // numb++;
        StandardInterface method = getMethod(system, dataSet.getString("METHOD"));
        double referencePressure = Double.parseDouble(dataSet.getString("ReferencePbar"));
        method.setReferencePressure(referencePressure);
        spesifications.add(getSpecification(method, dataSet.getString("NAME"),
            dataSet.getString("SPECIFICATION"), dataSet.getString("COUNTRY"),
            dataSet.getString("TERMINAL"), Double.parseDouble(dataSet.getString("MINVALUE")),
            Double.parseDouble(dataSet.getString("MAXVALUE")), dataSet.getString("UNIT"),
            Double.parseDouble(dataSet.getString("ReferenceTdegC")),
            Double.parseDouble(dataSet.getString("ReferenceTdegC")), referencePressure, "")); // dataSet.getString("Comments"));
        // System.out.println(dataSet.getString("Comments"));
        // System.out.println("specification added..." + numb);
      }
    } catch (Exception ex) {
      logger.error("error in comp");
    } finally {
      specificationsNumber = spesifications.size();
    }
  }

  /**
   * <p>
   * getMethod.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param methodName a {@link java.lang.String} object
   * @return a {@link neqsim.standards.StandardInterface} object
   */
  public StandardInterface getMethod(SystemInterface system, String methodName) {
    if (methodName.equals("ISO18453")) {
      Draft_ISO18453 standard = new Draft_ISO18453(system);
      standard.setReferencePressure(specificationsNumber);
      return new Draft_ISO18453(system);
    }
    if (methodName.equals("ISO6974")) {
      return new Standard_ISO6974(system);
    }
    if (methodName.equals("Total sulphur")) {
      return new GasChromotograpyhBase(system);
    }
    if (methodName.equals("oxygen")) {
      return new Standard_ISO6974(system);
    }
    if (methodName.equals("ISO6976")) {
      return new Standard_ISO6976(system);
    }
    if (methodName.equals("SulfurSpecificationMethod")) {
      return new SulfurSpecificationMethod(system);
    }
    if (methodName.equals("BestPracticeHydrocarbonDewPoint")) {
      return new BestPracticeHydrocarbonDewPoint(system);
    }
    if (methodName.equals("UKspecifications")) {
      return new UKspecifications_ICF_SI(system);
    }
    return null;
  }

  /**
   * <p>
   * getSpecification.
   * </p>
   *
   * @param method a {@link neqsim.standards.StandardInterface} object
   * @param specificationName a {@link java.lang.String} object
   * @param specificationName2 a {@link java.lang.String} object
   * @param country a {@link java.lang.String} object
   * @param terminal a {@link java.lang.String} object
   * @param minValue a double
   * @param maxValue a double
   * @param unit a {@link java.lang.String} object
   * @param referenceTemperature a double
   * @param referenceTemperatureComb a double
   * @param referencePressure a double
   * @param comments a {@link java.lang.String} object
   * @return a {@link neqsim.standards.salescontract.ContractSpecification} object
   */
  public ContractSpecification getSpecification(StandardInterface method, String specificationName,
      String specificationName2, String country, String terminal, double minValue, double maxValue,
      String unit, double referenceTemperature, double referenceTemperatureComb,
      double referencePressure, String comments) {
    return new ContractSpecification(specificationName, specificationName2, country, terminal,
        method, minValue, maxValue, unit, referenceTemperature, referenceTemperatureComb,
        referencePressure, comments);
  }

  /** {@inheritDoc} */
  @Override
  public void runCheck() {
    int j = 0;
    resultTable = new String[specificationsNumber][12];
    for (ContractSpecification spesification : spesifications)
      if (!(spesification == null)) {
        try {
          spesification.getStandard().calculate();
        } catch (Exception ex) {
          logger.error(ex.getMessage(), ex);
        }
        spesification.getStandard().setSalesContract(this);
        System.out.println("Type: " + spesification.getSpecification() + " Standard "
            + spesification.getStandard().getName() + " : "
            + spesification.getStandard().isOnSpec());
        getResultTable()[j][0] = spesification.getSpecification();
        getResultTable()[j][1] = Double.toString(spesification.getStandard()
            .getValue(spesification.getSpecification(), spesification.getUnit()));
        getResultTable()[j][2] = spesification.getCountry();
        getResultTable()[j][3] = spesification.getTerminal();
        getResultTable()[j][4] = Double.toString(spesification.getMinValue());
        getResultTable()[j][5] = Double.toString(spesification.getMaxValue());
        getResultTable()[j][6] = spesification.getUnit();
        getResultTable()[j][7] = spesification.getStandard().getName();
        getResultTable()[j][8] =
            Double.toString(spesification.getReferenceTemperatureMeasurement());
        getResultTable()[j][9] = Double.toString(spesification.getReferenceTemperatureCombustion());
        getResultTable()[j][10] = Double.toString(spesification.getReferencePressure());
        getResultTable()[j][11] = spesification.getComments();
        j++;
      }
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void display() {
    JFrame dialog =
        new JFrame("Specification check against sales specifications: " + getContractName());
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] names = {"Specification", "Value", "Country", "Terminal", "Minimum", "Maximum", "Unit",
        "Method", "Reference temperature measurement", "Reference temperature of combustion",
        "Reference pressure", "Comments"};
    JTable Jtab = new JTable(getResultTable(), names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public void setContract(String name) {
    waterDewPointTemperature = -12.0;
    waterDewPointSpecPressure = 70.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getWaterDewPointTemperature() {
    return waterDewPointTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setWaterDewPointTemperature(double waterDewPointTemperature) {
    this.waterDewPointTemperature = waterDewPointTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public double getWaterDewPointSpecPressure() {
    return waterDewPointSpecPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setWaterDewPointSpecPressure(double waterDewPointSpecPressure) {
    this.waterDewPointSpecPressure = waterDewPointSpecPressure;
  }

  /** {@inheritDoc} */
  @Override
  public int getSpecificationsNumber() {
    return specificationsNumber;
  }

  /** {@inheritDoc} */
  @Override
  public void setSpecificationsNumber(int specificationsNumber) {
    this.specificationsNumber = specificationsNumber;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] getResultTable() {
    return resultTable;
  }

  /** {@inheritDoc} */
  @Override
  public void setResultTable(String[][] resultTable) {
    this.resultTable = resultTable;
  }

  /** {@inheritDoc} */
  @Override
  public String getContractName() {
    return contractName;
  }

  /** {@inheritDoc} */
  @Override
  public void setContractName(String contractName) {
    this.contractName = contractName;
  }
}
