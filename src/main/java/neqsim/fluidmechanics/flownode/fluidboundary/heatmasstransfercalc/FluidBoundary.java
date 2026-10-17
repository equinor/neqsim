package neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ejml.data.DMatrixRMaj;
import Jama.Matrix;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.enhancementfactor.EnhancementFactor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Abstract FluidBoundary class.
 *
 * <p>
 * Coefficient and correction data use primitive arrays. Deprecated transient Jama views preserve the live legacy
 * coefficient API without owning separate numerical data. Interphase-flux working state uses EJML. Serialized instances
 * containing the former Jama coefficient, correction or flux fields are not compatible with this layout.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class FluidBoundary implements FluidBoundaryInterface, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FluidBoundary.class);

  protected FlowNodeInterface flowNode;
  protected SystemInterface bulkSystem;
  protected SystemInterface interphaseSystem;
  protected ThermodynamicOperations interphaseOps;
  protected ThermodynamicOperations bulkSystemOps;
  public double[] interphaseHeatFlux = new double[2];
  public boolean massTransferCalc = true;
  public boolean heatTransferCalc = false;
  public boolean[] thermodynamicCorrections;
  public boolean[] finiteFluxCorrection;
  // protected double[] dn;
  protected boolean numeric = false;
  protected DMatrixRMaj jFlux;
  protected DMatrixRMaj nFlux;
  protected EnhancementFactor enhancementFactor;
  // protected double[][][] massTransferCoefficient;
  protected double totalFlux = 0;
  protected double[][][] nonIdealCorrections;
  protected double[][] fluxTypeCorrectionMatrix;
  protected double[][][] rateCorrectionMatrix;
  protected double[][] fluxTypeCorrectionMatrixV;
  protected double[][][] totalMassTransferCoefficientMatrix;
  private double[][][] massTransferCoefficientArray;
  /**
   * Legacy views sharing the primitive coefficient arrays.
   *
   * @deprecated Use the array accessors instead of accessing these compatibility views.
   */
  @Deprecated
  protected transient Matrix[] massTransferCoefficientMatrix;
  protected double[][][] binarySchmidtNumber;
  public double[][][] binaryMassTransferCoefficient;
  public double[] heatTransferCoefficient;
  public double[] heatTransferCorrection;
  protected double[] prandtlNumber;
  protected int solverType = 0;

  /**
   * Constructor for FluidBoundary.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FluidBoundary(SystemInterface system) {
    this.bulkSystem = system;
    bulkSystemOps = new ThermodynamicOperations(bulkSystem);
    nonIdealCorrections = new double[2][][];
    fluxTypeCorrectionMatrix = new double[2][];
    fluxTypeCorrectionMatrixV = new double[2][];
    rateCorrectionMatrix = new double[2][][];
    totalMassTransferCoefficientMatrix = new double[2][][];
    massTransferCoefficientArray = new double[2][][];
    jFlux = new DMatrixRMaj(system.getPhases()[0].getNumberOfComponents() - 1, 1);
    nFlux = new DMatrixRMaj(system.getPhases()[0].getNumberOfComponents(), 1);
    heatTransferCoefficient = new double[2];
    heatTransferCorrection = new double[2];
    thermodynamicCorrections = new boolean[2];
    finiteFluxCorrection = new boolean[2];
    prandtlNumber = new double[2];
    initInterphaseSystem();
    // interphaseSystem.display();
    // interphaseOps.chemicalEquilibrium();
  }

  /**
   * initInterphaseSystem.
   */
  public void initInterphaseSystem() {
    interphaseSystem = bulkSystem.clone();
    interphaseSystem.setNumberOfPhases(2);

    // Phase-specific bulk inventories need overall fractions and initial K values before calc_x_y().
    interphaseSystem.init(0);
    interphaseSystem.initBeta();
    interphaseSystem
        .setTemperature((bulkSystem.getPhase(0).getTemperature() + bulkSystem.getPhase(1).getTemperature()) / 2.0);
    interphaseSystem.calc_x_y();
    interphaseSystem.init(3);

    ThermodynamicOperations interphaseOps = new ThermodynamicOperations(interphaseSystem);
    interphaseOps.TPflash();

    // If TPflash results in single phase, restore two-phase interface using bulk compositions
    if (interphaseSystem.getNumberOfPhases() < 2) {
      interphaseSystem.setNumberOfPhases(2);
      // Copy bulk compositions to interface - the driving force will come from concentration
      // differences between bulk and interface
      for (int i = 0; i < interphaseSystem.getNumberOfComponents(); i++) {
        double xi = bulkSystem.getPhase(1).getComponent(i).getx();
        double yi = bulkSystem.getPhase(0).getComponent(i).getx();
        interphaseSystem.getPhase(0).getComponent(i).setx(yi);
        interphaseSystem.getPhase(1).getComponent(i).setx(xi);
      }
      interphaseSystem.init(3);
    }
  }

  /**
   * Constructor for FluidBoundary.
   *
   * @param flowNode a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public FluidBoundary(FlowNodeInterface flowNode) {
    this(flowNode.getBulkSystem());
    this.flowNode = flowNode;
  }

  /** {@inheritDoc} */
  @Override
  public FluidBoundary clone() {
    ensureMassTransferCoefficientViews();
    synchronizeMassTransferCoefficientArray();
    FluidBoundary clonedSystem = null;

    try {
      clonedSystem = (FluidBoundary) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    clonedSystem.interphaseSystem = interphaseSystem.clone();
    clonedSystem.nFlux = nFlux.copy();

    return clonedSystem;
  }

  /**
   * Specifies if both mass and heat transfer should be solved for. Also specifies if we are going to solve using
   * multicomponent mass transfer models.
   *
   * @param type a int
   */
  public void setSolverType(int type) {
    solverType = type;
  }

  /**
   * initMassTransferCalc.
   */
  public void initMassTransferCalc() {
  }

  /**
   * initHeatTransferCalc.
   */
  public void initHeatTransferCalc() {
  }

  /**
   * init. Calls interphaseSystem.init(3)
   */
  public void init() {
    // if(this.bulkSystem.isChemicalSystem()) this.bulkSystem.initNumeric();
    // else this.bulkSystem.init(3);
    // if(this.interphaseSystem.isChemicalSystem())
    // this.interphaseSystem.initNumeric();
    // else this.interphaseSystem.init(3);
    // this.interphaseSystem.initNumeric();
    try {
      this.interphaseSystem.init(3);
    } catch (Exception e) {
      // If init(3) fails due to numerical issues, try to recover with init(1)
      try {
        this.interphaseSystem.init(1);
      } catch (Exception e2) {
        // If that also fails, just skip - fluxes will be zero
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getBulkSystem() {
    return bulkSystem;
  }

  /**
   * Setter for the field <code>bulkSystem</code>.
   *
   * @param bulkSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setBulkSystem(SystemInterface bulkSystem) {
    this.bulkSystem = bulkSystem;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getInterphaseSystem() {
    return interphaseSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void setInterphaseSystem(SystemInterface interphaseSystem) {
    this.interphaseSystem = interphaseSystem;
  }

  /**
   * getInterphaseOpertions.
   *
   * @return a {@link neqsim.thermodynamicoperations.ThermodynamicOperations} object
   */
  public ThermodynamicOperations getInterphaseOpertions() {
    return interphaseOps;
  }

  /** {@inheritDoc} */
  @Override
  public ThermodynamicOperations getBulkSystemOpertions() {
    return bulkSystemOps;
  }

  /**
   * calcFluxTypeCorrectionMatrix.
   *
   * @param phaseNum a int
   * @param k a int
   */
  public void calcFluxTypeCorrectionMatrix(int phaseNum, int k) {
    int componentCount = bulkSystem.getPhase(phaseNum).getNumberOfComponents();
    fluxTypeCorrectionMatrixV[phaseNum] = new double[componentCount];
    fluxTypeCorrectionMatrix[phaseNum] = new double[componentCount - 1];

    double sum = 0.0;
    for (int i = 0; i < componentCount - 1; i++) {
      fluxTypeCorrectionMatrixV[phaseNum][i] = i == k ? 1.0 : 0.0;
      sum += fluxTypeCorrectionMatrixV[phaseNum][i] * bulkSystem.getPhase(phaseNum).getComponent(i).getx();
    }

    for (int i = 0; i < componentCount - 1; i++) {
      fluxTypeCorrectionMatrix[phaseNum][i] = (fluxTypeCorrectionMatrixV[phaseNum][i]
          - fluxTypeCorrectionMatrixV[phaseNum][componentCount - 1]) / sum;
    }
  }

  /**
   * calcNonIdealCorrections.
   *
   * @param phaseNum a int
   */
  public void calcNonIdealCorrections(int phaseNum) {
    nonIdealCorrections[phaseNum] = new double[bulkSystem.getPhase(phaseNum).getNumberOfComponents()
        - 1][bulkSystem.getPhase(phaseNum).getNumberOfComponents() - 1];
    double temp = 0;
    for (int i = 0; i < bulkSystem.getPhase(phaseNum).getNumberOfComponents() - 1; i++) {
      for (int j = 0; j < bulkSystem.getPhase(phaseNum).getNumberOfComponents() - 1; j++) {
        temp = (i == j) ? 1.0 : 0.0;
        nonIdealCorrections[phaseNum][i][j] = temp + bulkSystem.getPhase(phaseNum).getComponent(i).getx()
            * bulkSystem.getPhase(phaseNum).getComponent(i).getdfugdn(j)
            * bulkSystem.getPhase(phaseNum).getNumberOfMolesInPhase();
        // her må det fylles inn
      }
    }
    // System.out.println("non-id");
    // nonIdealCorrections[phase].print(10,10);
  }

  /** {@inheritDoc} */
  @Override
  public double getInterphaseMolarFlux(int component) {
    return nFlux.get(component, 0);
  }

  /** {@inheritDoc} */
  @Override
  public double getInterphaseHeatFlux(int phase) {
    return interphaseHeatFlux[phase];
  }

  /** {@inheritDoc} */
  @Override
  public void massTransSolve() {
  }

  /** {@inheritDoc} */
  @Override
  public void heatTransSolve() {
  }

  /** {@inheritDoc} */
  @Override
  public double[][][] getMassTransferCoefficientArray() {
    synchronizeMassTransferCoefficientArray();
    double[][][] snapshot = new double[massTransferCoefficientArray.length][][];
    for (int phase = 0; phase < snapshot.length; phase++) {
      double[][] coefficients = massTransferCoefficientArray[phase];
      if (coefficients != null) {
        snapshot[phase] = new double[coefficients.length][];
        for (int row = 0; row < coefficients.length; row++) {
          snapshot[phase][row] = coefficients[row].clone();
        }
      }
    }
    return snapshot;
  }

  /**
   * Returns the live coefficient arrays for internal calculations.
   *
   * @param phase phase index
   * @return coefficient data indexed by row and column
   */
  protected final double[][] getMassTransferCoefficientArray(int phase) {
    synchronizeMassTransferCoefficientArray();
    return massTransferCoefficientArray[phase];
  }

  /**
   * Replaces a phase's canonical coefficient data and refreshes any legacy view.
   *
   * @param phase phase index
   * @param coefficients coefficient data indexed by row and column
   */
  protected final void setMassTransferCoefficientArray(int phase, double[][] coefficients) {
    massTransferCoefficientArray[phase] = coefficients;
    if (massTransferCoefficientMatrix != null) {
      massTransferCoefficientMatrix[phase] = coefficients == null ? null : new Matrix(coefficients);
    }
  }

  /**
   * Adopts replacements made through the legacy live Matrix array without copying numerical data.
   *
   * <p>
   * This compatibility adapter should be removed with {@link #massTransferCoefficientMatrix} and
   * {@link #getMassTransferCoefficientMatrix()}.
   */
  private void synchronizeMassTransferCoefficientArray() {
    if (massTransferCoefficientMatrix != null) {
      for (int phase = 0; phase < massTransferCoefficientArray.length; phase++) {
        Matrix view = massTransferCoefficientMatrix[phase];
        massTransferCoefficientArray[phase] = view == null ? null : view.getArray();
      }
    }
  }

  /**
   * Recreates transient views for the deprecated Matrix compatibility API after deserialization.
   *
   * <p>
   * This compatibility adapter should be removed with {@link #massTransferCoefficientMatrix} and
   * {@link #getMassTransferCoefficientMatrix()}.
   */
  private void ensureMassTransferCoefficientViews() {
    if (massTransferCoefficientMatrix == null) {
      massTransferCoefficientMatrix = new Matrix[massTransferCoefficientArray.length];
      for (int phase = 0; phase < massTransferCoefficientArray.length; phase++) {
        double[][] coefficients = massTransferCoefficientArray[phase];
        massTransferCoefficientMatrix[phase] = coefficients == null ? null : new Matrix(coefficients);
      }
    }
  }

  /**
   * Serializes canonical arrays, including pending replacements through deprecated views.
   *
   * @param output serialization stream
   * @throws java.io.IOException if coefficient state cannot be written
   */
  private void writeObject(java.io.ObjectOutputStream output) throws java.io.IOException {
    synchronizeMassTransferCoefficientArray();
    output.defaultWriteObject();
  }

  /**
   * {@inheritDoc}
   *
   * @deprecated Use {@link #getMassTransferCoefficientArray()} for array-based access.
   */
  @Deprecated
  @Override
  public Matrix[] getMassTransferCoefficientMatrix() {
    ensureMassTransferCoefficientViews();
    return massTransferCoefficientMatrix;
  }

  /** {@inheritDoc} */
  @Override
  public double getBinaryMassTransferCoefficient(int phaseNum, int i, int j) {
    return binaryMassTransferCoefficient[phaseNum][i][j];
  }

  /** {@inheritDoc} */
  @Override
  public double getEffectiveMassTransferCoefficient(int phaseNum, int i) {
    double temp = 0.0;
    for (int j = 0; j < bulkSystem.getPhase(phaseNum).getNumberOfComponents(); j++) {
      try {
        temp += bulkSystem.getPhase(phaseNum).getComponent(j).getx() * binaryMassTransferCoefficient[phaseNum][i][j];
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    return temp;
  }

  /** {@inheritDoc} */
  @Override
  public EnhancementFactor getEnhancementFactor() {
    return enhancementFactor;
  }

  /** {@inheritDoc} */
  @Override
  public void setEnhancementType(int type) {
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHeatTransferCalc() {
    return heatTransferCalc;
  }

  /** {@inheritDoc} */

  @Override
  public void setHeatTransferCalc(boolean heatTransferCalc) {
    this.heatTransferCalc = heatTransferCalc;
  }

  /** {@inheritDoc} */
  @Override
  public void setMassTransferCalc(boolean massTransferCalc) {
    this.massTransferCalc = massTransferCalc;
  }

  /** {@inheritDoc} */

  @Override
  public boolean useThermodynamicCorrections(int phase) {
    return thermodynamicCorrections[phase];
  }

  /** {@inheritDoc} */
  @Override
  public void useThermodynamicCorrections(boolean thermodynamicCorrections) {
    this.thermodynamicCorrections[0] = thermodynamicCorrections;
    this.thermodynamicCorrections[1] = thermodynamicCorrections;
  }

  /** {@inheritDoc} */
  @Override
  public void useThermodynamicCorrections(boolean thermodynamicCorrections, int phase) {
    this.thermodynamicCorrections[phase] = thermodynamicCorrections;
  }

  /** {@inheritDoc} */
  @Override
  public boolean useFiniteFluxCorrection(int phase) {
    return finiteFluxCorrection[phase];
  }

  /** {@inheritDoc} */
  @Override
  public void useFiniteFluxCorrection(boolean finiteFluxCorrection) {
    this.finiteFluxCorrection[0] = finiteFluxCorrection;
    this.finiteFluxCorrection[1] = finiteFluxCorrection;
  }

  /** {@inheritDoc} */
  @Override
  public void useFiniteFluxCorrection(boolean finiteFluxCorrection, int phase) {
    this.finiteFluxCorrection[phase] = finiteFluxCorrection;
  }

  /**
   * createTable.
   *
   * @param name a {@link java.lang.String} object
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] createTable(String name) {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    int rows = 0;
    if (bulkSystem == null) {
      String[][] table = new String[0][5];
      return table;
    }

    rows = bulkSystem.getPhases()[0].getNumberOfComponents() * 10;
    String[][] table = new String[rows][5];

    // String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    table[0][0] = "";
    table[0][1] = "";
    table[0][2] = "";
    table[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);
    for (int i = 0; i < bulkSystem.getNumberOfPhases(); i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = "eff. mass trans coef. " + bulkSystem.getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 1][i + 1] = nf.format(getEffectiveMassTransferCoefficient(i, j), buf, test).toString();
        table[j + 1][4] = "[-] bulkcoef";
      }
      if (getBulkSystem().isChemicalSystem()) {
        getEnhancementFactor().calcEnhancementVec(i);
        for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
          table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][0] = "enhancement "
              + getInterphaseSystem().getPhases()[0].getComponent(j).getName();
          buf = new StringBuffer();
          table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] = nf
              .format(getEnhancementFactor().getEnhancementVec(j), buf, test).toString();
          table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][4] = "[-] interfacecoef";
        }
      }
      getBulkSystem().getPhase(i).getPhysicalProperties().calcEffectiveDiffusionCoefficients();
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "schmidt "
            + bulkSystem.getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] = nf
            .format(getBulkSystem().getPhase(i).getPhysicalProperties().getEffectiveSchmidtNumber(j), buf, test)
            .toString();
        table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-] fluidboundarycoef";
      }

      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Node";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] = name;
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "-";
    }
    return table;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void display(String name) {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    JDialog dialog = new JDialog(new JFrame(), "Node-Report");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());
    Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
    dialog.setSize(screenDimension.width / 2, screenDimension.height / 2); // pack();
    String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    String[][] table = createTable(name);
    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    Jtab.setRowHeight(dialog.getHeight() / table.length);
    Jtab.setFont(
        new Font("Serif", Font.PLAIN, dialog.getHeight() / table.length - dialog.getHeight() / table.length / 10));
    // dialog.pack();
    dialog.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public void write(String name, String filename, boolean newfile) {
    String[][] table = createTable(name);
    neqsim.datapresentation.filehandling.TextFile file = new neqsim.datapresentation.filehandling.TextFile();
    if (newfile) {
      file.newFile(filename);
    }
    file.setOutputFileName(filename);
    file.setValues(table);
    file.createFile();
    getBulkSystem().write(name, filename, false);
  }
}
