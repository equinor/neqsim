/*
 * Standard.java
 *
 * Created on 13. juni 2004, 23:56
 */

package neqsim.standards;

import java.awt.BorderLayout;
import java.awt.Container;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.standards.salescontract.BaseContract;
import neqsim.standards.salescontract.ContractInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.NamedBaseClass;

/**
 * <p>
 * Abstract Standard class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public abstract class Standard extends NamedBaseClass implements StandardInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected String standardDescription = "Base Description";
  protected ContractInterface salesContract = new BaseContract();
  protected String[][] resultTable = null;
  protected SystemInterface thermoSystem;
  protected ThermodynamicOperations thermoOps;
  private String referenceState = "real"; // "ideal"real
  private double referencePressure = 70.0;

  /**
   * Constructor for Standard.
   *
   * @param name name of standard
   * @param description description
   */
  public Standard(String name, String description) {
    super(name);
    standardDescription = description;
  }

  /**
   * Constructor for Standard.
   *
   * @param name name of standard
   * @param thermoSyst input fluid
   * @param description description of standard
   */
  public Standard(String name, String description, SystemInterface thermoSyst) {
    this(name, description);
    setThermoSystem(thermoSyst);
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void setThermoSystem(SystemInterface thermoSystem) {
    this.thermoSystem = thermoSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void setSalesContract(String name) {
    if (name.equals("baseContract")) {
      salesContract = new BaseContract();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setSalesContract(ContractInterface salesContract) {
    this.salesContract = salesContract;
  }

  /** {@inheritDoc} */
  @Override
  public ContractInterface getSalesContract() {
    return salesContract;
  }

  /** {@inheritDoc} */
  @Override
  public String getStandardDescription() {
    return standardDescription;
  }

  /**
   * Setter for property standardDescription.
   *
   * @param standardDescription New value of property standardDescription.
   */
  public void setStandardDescription(String standardDescription) {
    this.standardDescription = standardDescription;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] createTable(String name) {
    if (thermoSystem == null) {
      String[][] table = new String[0][6];
      return table;
    }
    thermoSystem.setNumberOfPhases(1);
    thermoSystem.createTable(name);

    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    int rows = thermoSystem.getPhases()[0].getNumberOfComponents() + 30;
    String[][] table = new String[rows][6];
    // String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    table[0][0] = ""; // getPhases()[0].getType(); //"";

    for (int i = 0; i < thermoSystem.getPhases()[0].getNumberOfComponents() + 30; i++) {
      for (int j = 0; j < 6; j++) {
        table[i][j] = "";
      }
    }
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      table[0][i + 1] = thermoSystem.getPhase(i).getType().toString();
    }

    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = thermoSystem.getPhases()[0].getComponentName(j);
        buf = new StringBuffer();
        table[j + 1][i + 1] =
            nf.format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getComponent(j).getx(),
                buf, test).toString();
        table[j + 1][4] = "[-]";
      }
    }

    resultTable = table;
    return table;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void display(String name) {
    JDialog dialog = new JDialog(new JFrame(), "Standard-Report");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());

    String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    String[][] table = createTable(name);
    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
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

  /**
   * <p>
   * Getter for the field <code>referenceState</code>.
   * </p>
   *
   * @return the referenceState
   */
  public String getReferenceState() {
    return referenceState;
  }

  /**
   * <p>
   * Setter for the field <code>referenceState</code>.
   * </p>
   *
   * @param referenceState the referenceState to set
   */
  public void setReferenceState(String referenceState) {
    this.referenceState = referenceState;
  }

  /** {@inheritDoc} */
  @Override
  public double getReferencePressure() {
    return referencePressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setReferencePressure(double referencePressure) {
    this.referencePressure = referencePressure;
  }
}
