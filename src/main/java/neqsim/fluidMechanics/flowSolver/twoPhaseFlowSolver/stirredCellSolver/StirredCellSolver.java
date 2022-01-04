/*
 * steadstateOnePhasePipeFlowSolver.java
 *
 * Created on 17. januar 2001, 21:10
 */

package neqsim.fluidMechanics.flowSolver.twoPhaseFlowSolver.stirredCellSolver;

import Jama.Matrix;
import neqsim.fluidMechanics.flowSolver.twoPhaseFlowSolver.twoPhasePipeFlowSolver.TwoPhaseFixedStaggeredGridSolver;
import neqsim.fluidMechanics.flowSolver.twoPhaseFlowSolver.twoPhasePipeFlowSolver.TwoPhasePipeFlowSolver;
import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * StirredCellSolver class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class StirredCellSolver extends TwoPhasePipeFlowSolver
        implements neqsim.thermo.ThermodynamicConstantsInterface {
    private static final long serialVersionUID = 1000;
    Matrix diffMatrix;
    double dn[][];
    int iter = 0;
    Matrix[] diff4Matrix;
    double xNew[][][];
    protected double oldMass[][];
    protected double oldComp[][];
    protected double oldDensity[][];
    protected double oldVelocity[][];
    protected double oldComposition[][][];
    protected double oldInternalEnergy[][];
    protected double oldImpuls[][];
    protected double oldEnergy[][];

    /**
     * <p>
     * Constructor for StirredCellSolver.
     * </p>
     */
    public StirredCellSolver() {}

    /**
     * <p>
     * Constructor for StirredCellSolver.
     * </p>
     *
     * @param pipe a {@link neqsim.fluidMechanics.flowSystem.FlowSystemInterface} object
     * @param length a double
     * @param nodes a int
     */
    public StirredCellSolver(FlowSystemInterface pipe, double length, int nodes) {
        super(pipe, length, nodes);
    }

    /**
     * <p>
     * Constructor for StirredCellSolver.
     * </p>
     *
     * @param pipe a {@link neqsim.fluidMechanics.flowSystem.FlowSystemInterface} object
     * @param length a double
     * @param nodes a int
     * @param dynamic a boolean
     */
    public StirredCellSolver(FlowSystemInterface pipe, double length, int nodes, boolean dynamic) {
        super(pipe, length, nodes);
        this.dynamic = dynamic;
        oldMass = new double[2][nodes];
        oldComp = new double[2][nodes];
        oldImpuls = new double[2][nodes];
        diff4Matrix =
                new Matrix[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
        oldEnergy = new double[2][nodes];
        oldVelocity = new double[2][nodes];
        oldDensity = new double[2][nodes];
        oldInternalEnergy = new double[2][nodes];
        oldComposition = new double[2][pipe.getNode(0).getBulkSystem().getPhases()[0]
                .getNumberOfComponents()][nodes];
        numberOfVelocityNodes = nodes;
    }

    /** {@inheritDoc} */
    @Override
    public TwoPhaseFixedStaggeredGridSolver clone() {
        TwoPhaseFixedStaggeredGridSolver clonedSystem = null;
        try {
            clonedSystem = (TwoPhaseFixedStaggeredGridSolver) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedSystem;
    }

    /**
     * <p>
     * initProfiles.
     * </p>
     */
    public void initProfiles() {
        SystemInterface tempSyst = (SystemInterface) pipe.getNode(0).getBulkSystem().clone();
        ThermodynamicOperations testOps = new ThermodynamicOperations(tempSyst);
        testOps.TPflash();
        testOps.displayResult();

        double[][][] molDiff =
                new double[numberOfNodes][2][pipe.getNode(0).getBulkSystem().getPhases()[0]
                        .getNumberOfComponents()];
        pipe.getNode(0).init();
        pipe.getNode(0).calcFluxes();

        for (int i = 1; i < numberOfNodes - 1; i++) {
            pipe.getNode(i).init();
            pipe.getNode(i).calcFluxes();

            for (int componentNumber =
                    0; componentNumber < pipe.getNode(0).getBulkSystem().getPhases()[0]
                            .getNumberOfComponents(); componentNumber++) {
                double liquidMolarRate =
                        pipe.getNode(i).getFluidBoundary().getInterphaseMolarFlux(componentNumber)
                                * pipe.getNode(i).getInterphaseContactLength(0)
                                * pipe.getNode(i).getGeometry().getNodeLength();
                double gasMolarRate =
                        -pipe.getNode(i).getFluidBoundary().getInterphaseMolarFlux(componentNumber)
                                * pipe.getNode(i).getInterphaseContactLength(0)
                                * pipe.getNode(i).getGeometry().getNodeLength();
                double liquidHeatRate = pipe.getNode(i).getFluidBoundary().getInterphaseHeatFlux(1)
                        * pipe.getNode(i).getInterphaseContactLength(0)
                        * pipe.getNode(i).getGeometry().getNodeLength();
                double gasHeatRate = -pipe.getNode(i).getFluidBoundary().getInterphaseHeatFlux(0)
                        * pipe.getNode(i).getInterphaseContactLength(0)
                        * pipe.getNode(i).getGeometry().getNodeLength();

                double liquid_dT =
                        -liquidHeatRate / pipe.getNode(i).getBulkSystem().getPhase(1).getCp();
                double gas_dT = gasHeatRate / pipe.getNode(i).getBulkSystem().getPhase(0).getCp();

                molDiff[i][0][componentNumber] = molDiff[i - 1][0][componentNumber] + gasMolarRate;
                molDiff[i][1][componentNumber] =
                        molDiff[i - 1][1][componentNumber] + liquidMolarRate;

                pipe.getNode(i + 1).getBulkSystem().getPhases()[0].addMoles(componentNumber,
                        molDiff[i][0][componentNumber]);
                pipe.getNode(i + 1).getBulkSystem().getPhases()[1].addMoles(componentNumber,
                        molDiff[i][1][componentNumber]);

                pipe.getNode(i + 1).getBulkSystem().getPhase(0).setTemperature(
                        pipe.getNode(i).getBulkSystem().getPhase(0).getTemperature() + gas_dT);
                pipe.getNode(i + 1).getBulkSystem().getPhase(1).setTemperature(
                        pipe.getNode(i).getBulkSystem().getPhase(1).getTemperature() + liquid_dT);
            }

            pipe.getNode(i + 1).getBulkSystem().initBeta();
            pipe.getNode(i + 1).getBulkSystem().init_x_y();
            pipe.getNode(i + 1).initFlowCalc();

            System.out.println("x "
                    + pipe.getNode(i - 1).getBulkSystem().getPhases()[1].getComponents()[0].getx());
            System.out.println("x "
                    + pipe.getNode(i - 1).getBulkSystem().getPhases()[1].getComponents()[1].getx());
            System.out.println("y "
                    + pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getComponents()[0].getx());
            System.out.println("y "
                    + pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getComponents()[1].getx());
        }
        pipe.getNode(numberOfNodes - 1).init();
        pipe.getNode(numberOfNodes - 1).calcFluxes();
        this.initNodes();
        System.out.println("finisched initializing....");
    }

    /**
     * <p>
     * initMatrix.
     * </p>
     */
    public void initMatrix() {
        for (int i = 0; i < numberOfNodes; i++) {
            pipe.getNode(i).init();
            double enthalpy0 = pipe.getNode(i).getBulkSystem().getPhases()[0].getEnthalpy()
                    / pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
                    / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();
            double enthalpy1 = pipe.getNode(i).getBulkSystem().getPhases()[1].getEnthalpy()
                    / pipe.getNode(i).getBulkSystem().getPhases()[1].getNumberOfMolesInPhase()
                    / pipe.getNode(i).getBulkSystem().getPhases()[1].getMolarMass();

            solMatrix[0].set(i, 0, pipe.getNode(i).getVelocityIn(0).doubleValue());
            solMatrix[1].set(i, 0, pipe.getNode(i).getVelocityIn(1).doubleValue());

            sol3Matrix[0].set(i, 0, enthalpy0);
            sol3Matrix[1].set(i, 0, enthalpy1);

            solPhaseConsMatrix[0].set(i, 0, pipe.getNode(i).getBulkSystem().getPhases()[0]
                    .getPhysicalProperties().getDensity());
            solPhaseConsMatrix[1].set(i, 0, pipe.getNode(i).getPhaseFraction(1));

            for (int phase = 0; phase < 2; phase++) {
                for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhases()[0]
                        .getNumberOfComponents(); j++) {
                    solMolFracMatrix[phase][j].set(i, 0,
                            pipe.getNode(i).getBulkSystem().getPhases()[phase].getComponents()[j]
                                    .getx()
                                    * pipe.getNode(i).getBulkSystem().getPhases()[phase]
                                            .getComponents()[j].getMolarMass()
                                    / pipe.getNode(i).getBulkSystem().getPhases()[phase]
                                            .getMolarMass());
                }
            }
        }
    }

    /**
     * <p>
     * initPressure.
     * </p>
     *
     * @param phase a int
     */
    public void initPressure(int phase) {
        for (int i = 0; i < numberOfNodes; i++) {
            pipe.getNode(i).init();
            pipe.getNode(i).getBulkSystem().setPressure(
                    0.8 * pipe.getNode(i).getBulkSystem().getPhases()[phase].getdPdrho()
                            * diffMatrix.get(i, 0) * 1e-5
                            + pipe.getNode(i).getBulkSystem().getPressure());
            pipe.getNode(i).init();
        }
    }

    /**
     * <p>
     * initVelocity.
     * </p>
     *
     * @param phase a int
     */
    public void initVelocity(int phase) {
        for (int i = 0; i < numberOfNodes; i++) {
            pipe.getNode(i).setVelocityIn(phase,
                    pipe.getNode(i).getVelocityIn(phase).doubleValue()
                            + 0.8 * (solMatrix[phase].get(i, 0)
                                    - pipe.getNode(i).getVelocityIn(phase).doubleValue()));
        }

        for (int i = 0; i < numberOfNodes; i++) {
            double meanVelocity = pipe.getNode(i).getVelocityIn(phase).doubleValue();
            pipe.getNode(i).setVelocity(phase, meanVelocity);
            pipe.getNode(i).init();
        }
    }

    /**
     * <p>
     * initTemperature.
     * </p>
     *
     * @param phase a int
     */
    public void initTemperature(int phase) {
        for (int i = 0; i < numberOfNodes; i++) {
            pipe.getNode(i).init();
            pipe.getNode(i).getBulkSystem()
                    .setTemperature(pipe.getNode(i).getBulkSystem().getTemperature(phase)
                            + 0.8 * diffMatrix.get(i, 0)
                                    / (pipe.getNode(i).getBulkSystem().getPhases()[phase].getCp()
                                            / pipe.getNode(i).getBulkSystem().getPhases()[phase]
                                                    .getNumberOfMolesInPhase()
                                            / pipe.getNode(i).getBulkSystem().getPhases()[phase]
                                                    .getMolarMass()),
                            phase);
            pipe.getNode(i).init();
        }
    }

    /**
     * <p>
     * initPhaseFraction.
     * </p>
     *
     * @param phase a int
     */
    public void initPhaseFraction(int phase) {
        for (int i = 0; i < numberOfNodes; i++) {
            pipe.getNode(i).setPhaseFraction(phase,
                    pipe.getNode(i).getPhaseFraction(phase) + 0.8 * diffMatrix.get(i, 0));
            pipe.getNode(i).setPhaseFraction(0, 1.0 - pipe.getNode(i).getPhaseFraction(phase));
            pipe.getNode(i).init();
        }
    }

    /**
     * <p>
     * initComposition.
     * </p>
     *
     * @param phase a int
     * @param comp a int
     */
    public void initComposition(int phase, int comp) {
        for (int j = 0; j < numberOfNodes; j++) {
            if ((pipe.getNode(j).getBulkSystem().getPhases()[phase].getComponents()[comp].getx()
                    + diffMatrix.get(j, 0)
                            * pipe.getNode(j).getBulkSystem().getPhases()[phase].getMolarMass()
                            / pipe.getNode(j).getBulkSystem().getPhases()[phase]
                                    .getComponents()[comp].getMolarMass()) > 1.0) {
                pipe.getNode(j).getBulkSystem().getPhases()[phase].getComponents()[comp]
                        .setx(1.0 - 1e-30);
            } else if (pipe.getNode(j).getBulkSystem().getPhases()[phase].getComponents()[comp]
                    .getx()
                    + diffMatrix.get(j, 0)
                            * pipe.getNode(j).getBulkSystem().getPhases()[phase].getMolarMass()
                            / pipe.getNode(j).getBulkSystem().getPhases()[phase]
                                    .getComponents()[comp].getMolarMass() < 0.0) {
                pipe.getNode(j).getBulkSystem().getPhases()[phase].getComponents()[comp]
                        .setx(1e-30);
            } else {
                pipe.getNode(j).getBulkSystem().getPhases()[phase].getComponents()[comp].setx(
                        pipe.getNode(j).getBulkSystem().getPhases()[phase].getComponents()[comp]
                                .getx()
                                + diffMatrix.get(j, 0)
                                        * pipe.getNode(j).getBulkSystem().getPhases()[phase]
                                                .getMolarMass()
                                        / pipe.getNode(j).getBulkSystem().getPhases()[phase]
                                                .getComponents()[comp].getMolarMass());// pipe.getNode(j).getBulkSystem().getPhases()[0].getComponents()[p].getx()
                                                                                       // +
                                                                                       // 0.5*diff4Matrix[p].get(j,0));
            }

            double xSum = 0.0;
            for (int i = 0; i < pipe.getNode(j).getBulkSystem().getPhases()[phase]
                    .getNumberOfComponents() - 1; i++) {
                xSum += pipe.getNode(j).getBulkSystem().getPhases()[phase].getComponents()[i]
                        .getx();
            }

            pipe.getNode(j).getBulkSystem().getPhases()[phase]
                    .getComponents()[pipe.getNode(j).getBulkSystem().getPhases()[phase]
                            .getNumberOfComponents() - 1].setx(1.0 - xSum);
            pipe.getNode(j).init();
        }
    }

    /**
     * <p>
     * initFinalResults.
     * </p>
     *
     * @param phase a int
     */
    public void initFinalResults(int phase) {
        for (int i = 0; i < numberOfNodes; i++) {
            oldVelocity[phase][i] = pipe.getNode(i).getVelocityIn().doubleValue();
            oldDensity[phase][i] = pipe.getNode(i).getBulkSystem().getPhases()[0]
                    .getPhysicalProperties().getDensity();
            oldInternalEnergy[phase][i] =
                    pipe.getNode(i).getBulkSystem().getPhases()[0].getEnthalpy()
                            / pipe.getNode(i).getBulkSystem().getPhases()[0]
                                    .getNumberOfMolesInPhase()
                            / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();

            for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhases()[0]
                    .getNumberOfComponents(); j++) {
                oldComposition[phase][j][i] = xNew[phase][j][i];
            }
        }
    }

    /**
     * <p>
     * calcFluxes.
     * </p>
     */
    public void calcFluxes() {
        for (int i = 0; i < numberOfNodes; i++) {
            pipe.getNode(i).calcFluxes();
        }
    }

    /**
     * <p>
     * initNodes.
     * </p>
     */
    public void initNodes() {
        for (int i = 0; i < numberOfNodes; i++) {
            pipe.getNode(i).init();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void solveTDMA() {
        initProfiles();
    }
}
