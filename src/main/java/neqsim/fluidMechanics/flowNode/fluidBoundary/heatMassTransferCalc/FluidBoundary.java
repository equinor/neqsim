package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc;

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
import Jama.Matrix;
import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor.EnhancementFactor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Abstract FluidBoundary class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class FluidBoundary implements FluidBoundaryInterface, java.io.Serializable {
    private static final long serialVersionUID = 1000;

    protected FlowNodeInterface flowNode;
    protected SystemInterface bulkSystem;
    protected SystemInterface interphaseSystem;
    protected ThermodynamicOperations interphaseOps;
    protected ThermodynamicOperations bulkSystemOps;
    public double[] interphaseHeatFlux = new double[2];
    public boolean massTransferCalc = true, heatTransferCalc = false;
    public boolean[] thermodynamicCorrections, finiteFluxCorrection;
    // protected double[] dn;
    protected boolean numeric = false;
    protected Matrix jFlux, nFlux;
    protected EnhancementFactor enhancementFactor;
    // protected double[][][] massTransferCoefficient;
    protected double totalFlux = 0;
    protected Matrix[] nonIdealCorrections;
    protected Matrix[] fluxTypeCorrectionMatrix;
    protected Matrix[] rateCorrectionMatrix;
    protected Matrix[] fluxTypeCorrectionMatrixV;
    protected Matrix[] totalMassTransferCoefficientMatrix;
    protected Matrix[] massTransferCoefficientMatrix;
    protected double[][][] binarySchmidtNumber;
    public double[][][] binaryMassTransferCoefficient;
    public double[] heatTransferCoefficient, heatTransferCorrection;
    protected double[] prandtlNumber;
    protected int solverType = 0;

    /**
     * <p>
     * Constructor for FluidBoundary.
     * </p>
     */
    public FluidBoundary() {}

    /**
     * <p>
     * Constructor for FluidBoundary.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public FluidBoundary(SystemInterface system) {
        this.bulkSystem = system;
        bulkSystemOps = new ThermodynamicOperations(bulkSystem);
        nonIdealCorrections = new Matrix[2];
        fluxTypeCorrectionMatrix = new Matrix[2];
        fluxTypeCorrectionMatrixV = new Matrix[2];
        rateCorrectionMatrix = new Matrix[2];
        totalMassTransferCoefficientMatrix = new Matrix[2];
        massTransferCoefficientMatrix = new Matrix[2];
        jFlux = new Matrix(system.getPhases()[0].getNumberOfComponents() - 1, 1);
        nFlux = new Matrix(system.getPhases()[0].getNumberOfComponents(), 1);
        nFlux.set(0, 0, 0.0);
        nFlux.set(1, 0, 0.0);
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
     * <p>
     * initInterphaseSystem.
     * </p>
     */
    public void initInterphaseSystem() {
        interphaseSystem = bulkSystem.clone();
        interphaseSystem.setNumberOfPhases(2);

        // interphaseSystem.addComponent("methane", 100.0,0);
        interphaseSystem.initBeta();
        interphaseSystem.setTemperature(
                (bulkSystem.getPhase(0).getTemperature() + bulkSystem.getPhase(1).getTemperature())
                        / 2.0);
        // interphaseSystem.init(0);
        // interphaseSystem.setBeta(bulkSystem.getBeta());
        // interphaseSystem.init_x_y();
        interphaseSystem.calc_x_y();
        interphaseSystem.init(3);
        ThermodynamicOperations interphaseOps = new ThermodynamicOperations(interphaseSystem);
        // interphaseOps.setSystem(interphaseSystem);
        interphaseOps.TPflash();
        // if(interphaseSystem.getNumberOfPhases()<2)
        // interphaseOps.dewPointTemperatureFlash();
        // interphaseSystem.display();
    }

    /**
     * <p>
     * Constructor for FluidBoundary.
     * </p>
     *
     * @param flowNode a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public FluidBoundary(FlowNodeInterface flowNode) {
        this(flowNode.getBulkSystem());
        this.flowNode = flowNode;
    }

    /** {@inheritDoc} */
    @Override
    public FluidBoundary clone() {
        FluidBoundary clonedSystem = null;

        try {
            clonedSystem = (FluidBoundary) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        clonedSystem.interphaseSystem = interphaseSystem.clone();
        clonedSystem.nFlux = (Matrix) nFlux.clone();

        return clonedSystem;
    }

    /**
     * Specifies if both mass and heat transfer should be solved for. Also specifies if we are going
     * to solve using multicomponent mass transfer models.
     *
     * @param type a int
     */
    public void setSolverType(int type) {
        solverType = type;
    }

    /**
     * <p>
     * initMassTransferCalc.
     * </p>
     */
    public void initMassTransferCalc() {}

    /**
     * <p>
     * initHeatTransferCalc.
     * </p>
     */
    public void initHeatTransferCalc() {}

    /**
     * <p>
     * init.
     * </p>
     */
    public void init() {
        // if(this.bulkSystem.isChemicalSystem()) this.bulkSystem.initNumeric();
        // else this.bulkSystem.init(3);
        // if(this.interphaseSystem.isChemicalSystem())
        // this.interphaseSystem.initNumeric();
        // else this.interphaseSystem.init(3);
        // this.interphaseSystem.initNumeric();
        this.interphaseSystem.init(3);
    }

    /** {@inheritDoc} */
    @Override
    public SystemInterface getBulkSystem() {
        return bulkSystem;
    }

    /**
     * <p>
     * Setter for the field <code>bulkSystem</code>.
     * </p>
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
     * <p>
     * getInterphaseOpertions.
     * </p>
     *
     * @return a {@link neqsim.thermodynamicOperations.ThermodynamicOperations} object
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
     * <p>
     * calcFluxTypeCorrectionMatrix.
     * </p>
     *
     * @param phase a int
     * @param k a int
     */
    public void calcFluxTypeCorrectionMatrix(int phase, int k) {
        fluxTypeCorrectionMatrixV[phase] =
                new Matrix(bulkSystem.getPhases()[phase].getNumberOfComponents(), 1);
        fluxTypeCorrectionMatrix[phase] =
                new Matrix(bulkSystem.getPhases()[phase].getNumberOfComponents() - 1, 1);
        double temp = 0, sum = 0;

        for (int i = 0; i < bulkSystem.getPhases()[phase].getNumberOfComponents() - 1; i++) {
            temp = (i == k) ? 1.0 : 0.0;
            fluxTypeCorrectionMatrixV[phase].set(i, 0, temp);
            sum += fluxTypeCorrectionMatrixV[phase].get(i, 0)
                    * bulkSystem.getPhases()[phase].getComponents()[i].getx();
        }

        sum += fluxTypeCorrectionMatrixV[phase]
                .get(bulkSystem.getPhases()[phase].getNumberOfComponents() - 1, 0)
                * bulkSystem.getPhases()[phase]
                        .getComponents()[bulkSystem.getPhases()[phase].getNumberOfComponents() - 1]
                                .getx();

        for (int i = 0; i < bulkSystem.getPhases()[phase].getNumberOfComponents() - 1; i++) {
            fluxTypeCorrectionMatrix[phase].set(i, 0,
                    (fluxTypeCorrectionMatrixV[phase].get(i, 0) - fluxTypeCorrectionMatrixV[phase]
                            .get(bulkSystem.getPhases()[phase].getNumberOfComponents() - 1, 0))
                            / sum);
        }
    }

    /**
     * <p>
     * calcNonIdealCorrections.
     * </p>
     *
     * @param phase a int
     */
    public void calcNonIdealCorrections(int phase) {
        nonIdealCorrections[phase] =
                new Matrix(bulkSystem.getPhases()[phase].getNumberOfComponents() - 1,
                        bulkSystem.getPhases()[phase].getNumberOfComponents() - 1);
        double temp = 0;
        for (int i = 0; i < bulkSystem.getPhases()[phase].getNumberOfComponents() - 1; i++) {
            for (int j = 0; j < bulkSystem.getPhases()[phase].getNumberOfComponents() - 1; j++) {
                temp = (i == j) ? 1.0 : 0.0;
                nonIdealCorrections[phase].set(i, j,
                        temp + bulkSystem.getPhases()[phase].getComponents()[i].getx()
                                * bulkSystem.getPhases()[phase].getComponents()[i].getdfugdn(j)
                                * bulkSystem.getPhases()[phase].getNumberOfMolesInPhase()); // her
                                                                                            // maa
                                                                                            // det
                                                                                            // fylles
                                                                                            // inn
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
    public void massTransSolve() {}

    /** {@inheritDoc} */
    @Override
    public void heatTransSolve() {}

    /** {@inheritDoc} */
    @Override
    public Matrix[] getMassTransferCoefficientMatrix() {
        return massTransferCoefficientMatrix;
    }

    /** {@inheritDoc} */
    @Override
    public double getBinaryMassTransferCoefficient(int phase, int i, int j) {
        return binaryMassTransferCoefficient[phase][i][j];
    }

    /** {@inheritDoc} */
    @Override
    public double getEffectiveMassTransferCoefficient(int phase, int i) {
        double temp = 0.0;
        for (int j = 0; j < bulkSystem.getPhase(phase).getNumberOfComponents(); j++) {
            try {
                temp += bulkSystem.getPhase(phase).getComponent(j).getx()
                        * binaryMassTransferCoefficient[phase][i][j];
            } catch (Exception e) {
                e.printStackTrace();
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
    public void setEnhancementType(int type) {}

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
     * <p>
     * createTable.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @return an array of {@link java.lang.String} objects
     */
    public String[][] createTable(String name) {
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");

        String[][] table = new String[bulkSystem.getPhases()[0].getNumberOfComponents() * 10][5];
        // String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
        table[0][0] = "";
        table[0][1] = "";
        table[0][2] = "";
        table[0][3] = "";
        StringBuffer buf = new StringBuffer();
        FieldPosition test = new FieldPosition(0);
        for (int i = 0; i < bulkSystem.getNumberOfPhases(); i++) {
            for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 1][0] = "eff. mass trans coef. "
                        + bulkSystem.getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 1][i + 1] =
                        nf.format(getEffectiveMassTransferCoefficient(i, j), buf, test).toString();
                table[j + 1][4] = "[-] bulkcoef";
            }
            if (getBulkSystem().isChemicalSystem()) {
                getEnhancementFactor().calcEnhancementVec(i);
                for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
                    table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][0] =
                            "enhancement " + getInterphaseSystem().getPhases()[0].getComponents()[j]
                                    .getName();
                    buf = new StringBuffer();
                    table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] =
                            nf.format(getEnhancementFactor().getEnhancementVec(j), buf, test)
                                    .toString();
                    table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][4] =
                            "[-] interfacecoef";
                }
            }
            getBulkSystem().getPhase(i).getPhysicalProperties()
                    .calcEffectiveDiffusionCoefficients();
            for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][0] =
                        "schmidt " + bulkSystem.getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] =
                        nf.format(getBulkSystem().getPhase(i).getPhysicalProperties()
                                .getEffectiveSchmidtNumber(j), buf, test).toString();
                table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][4] =
                        "[-] fluidboundarycoef";
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
        Jtab.setFont(new Font("Serif", Font.PLAIN,
                dialog.getHeight() / table.length - dialog.getHeight() / table.length / 10));
        // dialog.pack();
        dialog.setVisible(true);
    }

    /** {@inheritDoc} */
    @Override
    public void write(String name, String filename, boolean newfile) {
        String[][] table = createTable(name);
        neqsim.dataPresentation.fileHandeling.createTextFile.TextFile file =
                new neqsim.dataPresentation.fileHandeling.createTextFile.TextFile();
        if (newfile) {
            file.newFile(filename);
        }
        file.setOutputFileName(filename);
        file.setValues(table);
        file.createFile();
        getBulkSystem().write(name, filename, false);
    }
}
