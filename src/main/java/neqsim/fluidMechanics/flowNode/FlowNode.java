/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package neqsim.fluidMechanics.flowNode;

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

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.InterphaseTransportCoefficientBaseClass;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.InterphaseTransportCoefficientInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.util.util.DoubleCloneable;

public abstract class FlowNode implements FlowNodeInterface, ThermodynamicConstantsInterface {

    private static final long serialVersionUID = 1000;

    protected double distanceToCenterOfNode = 0, lengthOfNode = 0, veticalPositionOfNode = 0;
    protected double[] hydraulicDiameter, reynoldsNumber;
    protected int[] flowDirection;
    protected double[] interphaseContactLength, wallContactLength, phaseFraction;
    public double[] molarFlowRate, massFlowRate, volumetricFlowRate;
    protected ThermodynamicOperations operations;
    protected String flowNodeType = null;
    protected FluidBoundaryInterface fluidBoundary = null;
    public SystemInterface bulkSystem;
    protected double inclination = 0;
    public DoubleCloneable[] velocityIn;
    public DoubleCloneable[] velocityOut;
    public double[] superficialVelocity;
    public double interphaseContactArea = 0;
    public double[] velocity;
    public GeometryDefinitionInterface pipe;
    protected InterphaseTransportCoefficientInterface interphaseTransportCoefficient;
    protected double[] wallFrictionFactor, interphaseFrictionFactor;
    protected ThermodynamicOperations phaseOps;

    public FlowNode() {
        this.bulkSystem = null;
        this.pipe = null;
        this.fluidBoundary = null;
        this.interphaseTransportCoefficient = null;
    }

    public FlowNode(SystemInterface system) {
        this.interphaseTransportCoefficient = new InterphaseTransportCoefficientBaseClass(this);
        this.velocityIn = new DoubleCloneable[2];
        this.velocityOut = new DoubleCloneable[2];
        this.velocity = new double[2];
        this.flowDirection = new int[2];
        this.wallFrictionFactor = new double[2];
        this.superficialVelocity = new double[2];
        phaseFraction = new double[2];
        hydraulicDiameter = new double[2];
        reynoldsNumber = new double[2];
        interphaseContactLength = new double[2];
        wallContactLength = new double[2];
        molarFlowRate = new double[2];
        massFlowRate = new double[2];
        volumetricFlowRate = new double[2];
        interphaseFrictionFactor = new double[2];
        velocity[0] = 0.0;

        this.bulkSystem = (SystemInterface) system.clone();
        if (this.bulkSystem.isChemicalSystem()) {
            this.bulkSystem.chemicalReactionInit();
        }
        operations = new ThermodynamicOperations(this.bulkSystem);
        // bulkSystem.getChemicalReactionOperations().setSystem(bulkSystem);
        for (int i = 0; i < 2; i++) {
            this.velocityOut[i] = new DoubleCloneable();
            this.velocityIn[i] = new DoubleCloneable();
            this.flowDirection[i] = 1;
        }
    }

    public FlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
        this(system);
        this.pipe = (GeometryDefinitionInterface) pipe.clone();
    }

    public FlowNode(SystemInterface system, GeometryDefinitionInterface pipe, double lengthOfNode,
            double distanceToCenterOfNode) {
        this(system, pipe);
        this.lengthOfNode = lengthOfNode;
        this.distanceToCenterOfNode = distanceToCenterOfNode;
    }

    @Override
    public void setFrictionFactorType(int type) {
        if (type == 0) {
            interphaseTransportCoefficient = new InterphaseTransportCoefficientBaseClass(this);
        }
        if (type == 1) {
            interphaseTransportCoefficient = new InterphaseTransportCoefficientBaseClass(this);
        } else {
            System.out.println("error chhosing friction type");
        }
    }

    // public void initFlowCalc(){
    // }

    @Override
    public void setGeometryDefinitionInterface(GeometryDefinitionInterface pipe) {
        this.pipe = (GeometryDefinitionInterface) pipe.clone();
    }

    @Override
    public void setDistanceToCenterOfNode(double distanceToCenterOfNode) {
        this.distanceToCenterOfNode = distanceToCenterOfNode;
    }

    @Override
    public InterphaseTransportCoefficientInterface getInterphaseTransportCoefficient() {
        return interphaseTransportCoefficient;
    }

    @Override
    public double getDistanceToCenterOfNode() {
        return this.distanceToCenterOfNode;
    }

    @Override
    public double getVerticalPositionOfNode() {
        return this.veticalPositionOfNode;
    }

    @Override
    public void setVerticalPositionOfNode(double veticalPositionOfNode) {
        this.veticalPositionOfNode = veticalPositionOfNode;
    }

    @Override
    public double getSuperficialVelocity(int i) {
        return this.superficialVelocity[i];
    }

    @Override
    public String getFlowNodeType() {
        return this.flowNodeType;
    }

    @Override
    public void setLengthOfNode(double lengthOfNode) {
        this.lengthOfNode = lengthOfNode;
        getGeometry().setNodeLength(lengthOfNode);
    }

    @Override
    public double getLengthOfNode() {
        return lengthOfNode;
    }

    @Override
    public Object clone() {
        FlowNode clonedSystem = null;
        try {
            clonedSystem = (FlowNode) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        clonedSystem.bulkSystem = (SystemInterface) bulkSystem.clone();
        clonedSystem.pipe = (GeometryDefinitionInterface) pipe.clone();
        clonedSystem.velocity = new double[2];
        clonedSystem.volumetricFlowRate = new double[2];
        clonedSystem.reynoldsNumber = new double[2];
        clonedSystem.wallFrictionFactor = new double[2];
        clonedSystem.hydraulicDiameter = new double[2];
        clonedSystem.reynoldsNumber = new double[2];
        clonedSystem.interphaseContactLength = new double[2];
        clonedSystem.wallContactLength = new double[2];
        clonedSystem.flowDirection = new int[2];
        clonedSystem.phaseFraction = new double[2];
        clonedSystem.molarFlowRate = new double[2];
        clonedSystem.massFlowRate = new double[2];
        clonedSystem.interphaseFrictionFactor = new double[2];

        clonedSystem.velocityIn = velocityIn.clone();
        clonedSystem.velocityOut = velocityOut.clone();

        for (int i = 0; i < 2; i++) {
            clonedSystem.velocityIn[i] = (DoubleCloneable) velocityIn[i].clone();
            clonedSystem.velocityOut[i] = (DoubleCloneable) velocityOut[i].clone();
        }
        System.arraycopy(this.flowDirection, 0, clonedSystem.flowDirection, 0, this.flowDirection.length);
        System.arraycopy(this.wallContactLength, 0, clonedSystem.wallContactLength, 0, this.wallContactLength.length);
        System.arraycopy(this.interphaseFrictionFactor, 0, clonedSystem.interphaseFrictionFactor, 0,
                this.interphaseFrictionFactor.length);
        System.arraycopy(this.molarFlowRate, 0, clonedSystem.molarFlowRate, 0, this.molarFlowRate.length);
        System.arraycopy(this.massFlowRate, 0, clonedSystem.massFlowRate, 0, this.massFlowRate.length);
        System.arraycopy(this.volumetricFlowRate, 0, clonedSystem.volumetricFlowRate, 0,
                this.volumetricFlowRate.length);
        System.arraycopy(this.phaseFraction, 0, clonedSystem.phaseFraction, 0, this.phaseFraction.length);
        System.arraycopy(this.velocity, 0, clonedSystem.velocity, 0, this.velocity.length);
        System.arraycopy(this.hydraulicDiameter, 0, clonedSystem.hydraulicDiameter, 0, this.hydraulicDiameter.length);
        System.arraycopy(this.reynoldsNumber, 0, clonedSystem.reynoldsNumber, 0, this.reynoldsNumber.length);
        System.arraycopy(this.wallFrictionFactor, 0, clonedSystem.wallFrictionFactor, 0,
                this.wallFrictionFactor.length);

        return clonedSystem;
    }

    @Override
    public void init() {
        bulkSystem.init(3);
        bulkSystem.initPhysicalProperties();
    }

    @Override
    public void initBulkSystem() {
        bulkSystem.init(3);
        bulkSystem.initPhysicalProperties();
    }

    @Override
    public SystemInterface getBulkSystem() {
        return bulkSystem;
    }

    @Override
    public DoubleCloneable getVelocityOut(int i) {
        return velocityOut[i];
    }

    @Override
    public DoubleCloneable getVelocityIn(int i) {
        return velocityIn[i];
    }

    @Override
    public DoubleCloneable getVelocityOut() {
        return velocityOut[0];
    }

    @Override
    public DoubleCloneable getVelocityIn() {
        return velocityIn[0];
    }

    @Override
    public void setVelocity(double vel) {
        velocity[0] = vel;
    }

    @Override
    public void setVelocity(int phase, double vel) {
        velocity[phase] = vel;
    }

    @Override
    public void setVelocityIn(double vel) {
        velocityIn[0].set(vel);
    }

    @Override
    public void setVelocityOut(double vel) {
        velocityOut[0].set(vel);
    }

    @Override
    public void setVelocityOut(int phase, double vel) {
        velocityOut[phase].set(vel);
    }

    @Override
    public void setVelocityIn(int phase, double vel) {
        velocityIn[phase].set(vel);
    }

    @Override
    public void setVelocityOut(DoubleCloneable vel) {
        velocityOut[0] = vel;
    }

    @Override
    public void setVelocityIn(DoubleCloneable vel) {
        velocityIn[0] = vel;
    }

    @Override
    public void setVelocityIn(int phase, DoubleCloneable vel) {
        velocityIn[phase] = vel;
    }

    @Override
    public void setVelocityOut(int phase, DoubleCloneable vel) {
        velocityOut[phase] = vel;
    }

    @Override
    public double getVelocity() {
        return velocity[0];
    }

    @Override
    public double getVelocity(int phase) {
        return velocity[phase];
    }

    @Override
    public double getWallFrictionFactor() {
        return wallFrictionFactor[0];
    }

    @Override
    public double getWallFrictionFactor(int phase) {
        return wallFrictionFactor[phase];
    }

    @Override
    public double getInterPhaseFrictionFactor() {
        return interphaseFrictionFactor[0];
    }

    @Override
    public double getReynoldsNumber() {
        return reynoldsNumber[0];
    }

    @Override
    public double getHydraulicDiameter(int i) {
        return hydraulicDiameter[i];
    }

    @Override
    public double getReynoldsNumber(int i) {
        return reynoldsNumber[i];
    }

    @Override
    public SystemInterface getInterphaseSystem() {
        return fluidBoundary.getInterphaseSystem();
    }

    @Override
    public FluidBoundaryInterface getFluidBoundary() {
        return fluidBoundary;
    }

    @Override
    public GeometryDefinitionInterface getGeometry() {
        return pipe;
    }

    @Override
    public void setInterphaseSystem(SystemInterface interphaseSystem) {
        fluidBoundary.setInterphaseSystem((SystemInterface) interphaseSystem.clone());
    }

    @Override
    public void setInterphaseModelType(int i) {
        if (i == 0) {
            // System.out.println("set equilibrium");
            this.fluidBoundary = new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.equilibriumFluidBoundary.EquilibriumFluidBoundary(
                    this);
        } else {
            // System.out.println("set non equilibrium");
            if (bulkSystem.isChemicalSystem()) {
                this.fluidBoundary = new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.ReactiveKrishnaStandartFilmModel(
                        this);
            } else {
                this.fluidBoundary = new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.KrishnaStandartFilmModel(
                        this);
            }
        }
    }

    @Override
    public void setBulkSystem(SystemInterface bulkSystem) {
        this.bulkSystem = (SystemInterface) bulkSystem.clone();
        phaseOps = new ThermodynamicOperations(this.getBulkSystem());
        phaseOps.TPflash();
        init();
    }

    @Override
    public FlowNodeInterface getNextNode() {
        return (FlowNodeInterface) this.clone();
    }

    @Override
    public double getVolumetricFlow() {
        return volumetricFlowRate[0];
    }

    @Override
    public void calcFluxes() {
    }

    @Override
    public void setFluxes(double dn[]) {
    }

    @Override
    public double calcSherwoodNumber(double schmidtNumber, int phase) {
        return 0;
    }

    @Override
    public double calcNusseltNumber(double prandtlNumber, int phase) {
        return 0;
    }

    @Override
    public double getPrandtlNumber(int phase) {
        return getBulkSystem().getPhases()[phase].getCp()
                * getBulkSystem().getPhases()[phase].getPhysicalProperties().getViscosity()
                / getBulkSystem().getPhases()[phase].getPhysicalProperties().getConductivity();
    }

    @Override
    public double getSchmidtNumber(int phase, int component1, int component2) {
        return getBulkSystem().getPhase(phase).getPhysicalProperties().getDiffusionCoeffisient(component1, component2)
                / getBulkSystem().getPhase(phase).getPhysicalProperties().getKinematicViscosity();
    }

    @Override
    public double getEffectiveSchmidtNumber(int phase, int component) {
        getBulkSystem().getPhase(phase).getPhysicalProperties().calcEffectiveDiffusionCoefficients();
        return getBulkSystem().getPhase(phase).getPhysicalProperties().getKinematicViscosity()
                / getBulkSystem().getPhase(phase).getPhysicalProperties().getEffectiveDiffusionCoefficient(component);
    }

    @Override
    public double calcStantonNumber(double schmidtNumber, int phase) {
        return 0;
    }

    @Override
    public double getArea(int i) {
        return pipe.getArea() * phaseFraction[i];
    }

    @Override
    public void updateMolarFlow() {
    }

    @Override
    public double getPhaseFraction(int phase) {
        return phaseFraction[phase];
    }

    @Override
    public double getInterphaseContactArea() {
        return interphaseContactArea;
    }

    @Override
    public void setPhaseFraction(int phase, double frac) {
        phaseFraction[phase] = frac;
    }

    @Override
    public double getWallContactLength(int phase) {
        return wallContactLength[phase];
    }

    @Override
    public double getInterphaseContactLength(int phase) {
        return interphaseContactLength[phase];
    }

    @Override
    public double getMassFlowRate(int phase) {
        return massFlowRate[phase];
    }

    @Override
    public void increaseMolarRate(double moles) {
    }

    @Override
    public double calcTotalHeatTransferCoefficient(int phase) {
        double prandtlNumber = getBulkSystem().getPhases()[phase].getCp()
                / getBulkSystem().getPhases()[phase].getNumberOfMolesInPhase()
                * getBulkSystem().getPhases()[phase].getPhysicalProperties().getViscosity()
                / getBulkSystem().getPhases()[phase].getPhysicalProperties().getConductivity();
        double temp = 1.0
                / (1.0 / interphaseTransportCoefficient.calcWallHeatTransferCoefficient(phase, prandtlNumber, this)
                        + 1.0 / pipe.getWallHeatTransferCoefficient()
                        + 1.0 / pipe.getSurroundingEnvironment().getHeatTransferCoefficient());
        return temp;
    }

    @Override
    public void setEnhancementType(int type) {
    }

    @Override
    public void display() {
        display("");
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
        String[] names = { "", "Phase 1", "Phase 2", "Phase 3", "Unit" };
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
    public void update() {
    }

    /**
     * Getter for property operations.
     * 
     * @return Value of property operations.
     */
    @Override
    public neqsim.thermodynamicOperations.ThermodynamicOperations getOperations() {
        return operations;
    }

    /**
     * Setter for property operations.
     * 
     * @param operations New value of property operations.
     */
    public void setOperations(neqsim.thermodynamicOperations.ThermodynamicOperations operations) {
        this.operations = operations;
    }

    @Override
    public double getMolarMassTransferRate(int componentNumber) {
        return getFluidBoundary().getInterphaseMolarFlux(componentNumber) * interphaseContactArea;
    }

    /**
     * Getter for property flowDirection.
     * 
     * @return Value of property flowDirection.
     */
    @Override
    public int getFlowDirection(int i) {
        return this.flowDirection[i];
    }

    /**
     * Setter for property flowDirection.
     * 
     * @param flowDirection New value of property flowDirection.
     */
    @Override
    public void setFlowDirection(int flowDirection, int i) {
        this.flowDirection[i] = flowDirection;
    }

    public String[][] createTable(String name) {
        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");

        String[][] table = new String[bulkSystem.getPhases()[0].getNumberOfComponents() * 10][5];
        table[0][0] = "";
        table[0][1] = "";
        table[0][2] = "";
        table[0][3] = "";
        StringBuffer buf = new StringBuffer();
        FieldPosition test = new FieldPosition(0);
        for (int i = 0; i < bulkSystem.getNumberOfPhases(); i++) {
            for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 1][0] = bulkSystem.getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 1][i + 1] = nf
                        .format(bulkSystem.getPhase(bulkSystem.getPhaseIndex(i)).getComponents()[j].getx(), buf, test)
                        .toString();
                table[j + 1][4] = "[-] bulk";
            }

            for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + bulkSystem.getPhases()[0].getNumberOfComponents()
                        + 2][0] = getInterphaseSystem().getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] = nf.format(
                        getInterphaseSystem().getPhase(getInterphaseSystem().getPhaseIndex(i)).getComponents()[j]
                                .getx(),
                        buf, test).toString();
                table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][4] = "[-] interface";
            }

            for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][0] = bulkSystem.getPhases()[0]
                        .getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] = nf
                        .format(getFluidBoundary().getInterphaseMolarFlux(j), buf, test).toString();
                table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[mol/sec*m^2]";
            }
            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 5][0] = "Reynolds Number";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] = nf
                    .format(reynoldsNumber[i], buf, test).toString();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 5][4] = "[-]";

            // Double.longValue(system.getPhase(phaseIndex[i]).getBeta());

            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 6][0] = "Velocity";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 6][i + 1] = nf.format(velocity[i], buf, test)
                    .toString();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 6][4] = "[m/sec]";

            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Gas Heat Flux";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] = nf
                    .format(getFluidBoundary().getInterphaseHeatFlux(0), buf, test).toString();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 7][4] = "[J/sec*m^2]";

            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Pressure";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] = Double
                    .toString(bulkSystem.getPhase(bulkSystem.getPhaseIndex(i)).getPressure());
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 8][4] = "[bar]";

            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 9][0] = "Bulk Temperature";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 9][i + 1] = Double
                    .toString(bulkSystem.getPhase(bulkSystem.getPhaseIndex(i)).getTemperature());
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 9][4] = "[K]";

            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Interface Temperature";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] = Double
                    .toString(getInterphaseSystem().getPhase(bulkSystem.getPhaseIndex(i)).getTemperature());
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[K]";

            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Interface Area";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] = nf
                    .format(getInterphaseContactArea());
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[m^2]";

            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 12][0] = "Inner wall temperature";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 12][i + 1] = Double
                    .toString(pipe.getInnerWallTemperature());
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 12][4] = "K";

            buf = new StringBuffer();
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 13][0] = "Node";
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
            table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "-";
        }
        return table;
    }

    @Override
    public void write(String name, String filename, boolean newfile) {
        String[][] table = createTable(name);
        neqsim.dataPresentation.fileHandeling.createTextFile.TextFile file = new neqsim.dataPresentation.fileHandeling.createTextFile.TextFile();
        if (newfile) {
            file.newFile(filename);
        }
        file.setOutputFileName(filename);
        file.setValues(table);
        file.createFile();
        getBulkSystem().write(("thermo for " + name), filename, false);
        getFluidBoundary().write(("boundary for " + name), filename, false);
    }
}
