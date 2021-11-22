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

    public FluidBoundary() {}

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

    public void initInterphaseSystem() {
        interphaseSystem = (SystemInterface) bulkSystem.clone();
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

    public FluidBoundary(FlowNodeInterface flowNode) {
        this(flowNode.getBulkSystem());
        this.flowNode = flowNode;
    }

    @Override
    public Object clone() {
        FluidBoundary clonedSystem = null;

        try {
            clonedSystem = (FluidBoundary) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        clonedSystem.interphaseSystem = (SystemInterface) interphaseSystem.clone();
        clonedSystem.nFlux = (Matrix) nFlux.clone();

        return clonedSystem;
    }

    /**
     * Specifies if both mass and heat transfer should be solved for. Also specifies if we are going
     * to solve using multicomponent mass transfer models.
     * 
     * @param type
     */
    public void setSolverType(int type) {
        solverType = type;
    }

    public void initMassTransferCalc() {}

    public void initHeatTransferCalc() {}

    public void init() {
        // if(this.bulkSystem.isChemicalSystem()) this.bulkSystem.initNumeric();
        // else this.bulkSystem.init(3);
        // if(this.interphaseSystem.isChemicalSystem())
        // this.interphaseSystem.initNumeric();
        // else this.interphaseSystem.init(3);
        // this.interphaseSystem.initNumeric();
        this.interphaseSystem.init(3);
    }

    @Override
    public SystemInterface getBulkSystem() {
        return bulkSystem;
    }

    public void setBulkSystem(SystemInterface bulkSystem) {
        this.bulkSystem = bulkSystem;
    }

    @Override
    public SystemInterface getInterphaseSystem() {
        return interphaseSystem;
    }

    @Override
    public void setInterphaseSystem(SystemInterface interphaseSystem) {
        this.interphaseSystem = interphaseSystem;
    }

    public ThermodynamicOperations getInterphaseOpertions() {
        return interphaseOps;
    }

    @Override
    public ThermodynamicOperations getBulkSystemOpertions() {
        return bulkSystemOps;
    }

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

    @Override
    public double getInterphaseMolarFlux(int component) {
        return nFlux.get(component, 0);
    }

    @Override
    public double getInterphaseHeatFlux(int phase) {
        return interphaseHeatFlux[phase];
    }

    @Override
    public void massTransSolve() {}

    @Override
    public void heatTransSolve() {}

    @Override
    public Matrix[] getMassTransferCoefficientMatrix() {
        return massTransferCoefficientMatrix;
    }

    @Override
    public double getBinaryMassTransferCoefficient(int phase, int i, int j) {
        return binaryMassTransferCoefficient[phase][i][j];
    }

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

    @Override
    public EnhancementFactor getEnhancementFactor() {
        return enhancementFactor;
    }

    @Override
    public void setEnhancementType(int type) {}

    /**
     * Getter for property heatTransferCalc.
     * 
     * @return Value of property heatTransferCalc.
     */
    @Override
    public boolean isHeatTransferCalc() {
        return heatTransferCalc;
    }

    /**
     * Setter for property heatTransferCalc.
     * 
     * @param heatTransferCalc New value of property heatTransferCalc.
     */
    @Override
    public void setHeatTransferCalc(boolean heatTransferCalc) {
        this.heatTransferCalc = heatTransferCalc;
    }

    @Override
    public void setMassTransferCalc(boolean massTransferCalc) {
        this.massTransferCalc = massTransferCalc;
    }

    /**
     * Getter for property thermodynamicCorrections.
     * 
     * @return Value of property thermodynamicCorrections.
     */
    @Override
    public boolean useThermodynamicCorrections(int phase) {
        return thermodynamicCorrections[phase];
    }

    /**
     * Setter for property thermodynamicCorrections.
     * 
     * @param thermodynamicCorrections New value of property thermodynamicCorrections.
     */
    @Override
    public void useThermodynamicCorrections(boolean thermodynamicCorrections) {
        this.thermodynamicCorrections[0] = thermodynamicCorrections;
        this.thermodynamicCorrections[1] = thermodynamicCorrections;
    }

    @Override
    public void useThermodynamicCorrections(boolean thermodynamicCorrections, int phase) {
        this.thermodynamicCorrections[phase] = thermodynamicCorrections;
    }

    /**
     * Getter for property finiteFluxCorrection.
     * 
     * @return Value of property finiteFluxCorrection.
     */
    @Override
    public boolean useFiniteFluxCorrection(int phase) {
        return finiteFluxCorrection[phase];
    }

    /**
     * Setter for property finiteFluxCorrection.
     * 
     * @param finiteFluxCorrection New value of property finiteFluxCorrection.
     */
    @Override
    public void useFiniteFluxCorrection(boolean finiteFluxCorrection) {
        this.finiteFluxCorrection[0] = finiteFluxCorrection;
        this.finiteFluxCorrection[1] = finiteFluxCorrection;
    }

    @Override
    public void useFiniteFluxCorrection(boolean finiteFluxCorrection, int phase) {
        this.finiteFluxCorrection[phase] = finiteFluxCorrection;
    }

    public String[][] createTable(String name) {
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");

        String[][] table = new String[bulkSystem.getPhases()[0].getNumberOfComponents() * 10][5];
        String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
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
