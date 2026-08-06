/*
 * OnePhaseFixedStaggeredGrid.java
 *
 * Created on 17. januar 2001, 21:10
 */

package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.mathlib.generalmath.BandedLinearSystemSolver;
import neqsim.mathlib.generalmath.TDMAsolve;
import neqsim.fluidmechanics.flowsolver.SpeciesAdvectionScheme;
import neqsim.fluidmechanics.flowsolver.AxialDispersionModel;
import neqsim.fluidmechanics.flowsolver.NoAxialDispersion;

/**
 * OnePhaseFixedStaggeredGrid class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class OnePhaseFixedStaggeredGrid extends OnePhasePipeFlowSolver
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OnePhaseFixedStaggeredGrid.class);

  private static final int MAXIMUM_NONLINEAR_ITERATIONS = 100;
  private static final double NONLINEAR_RESIDUAL_TOLERANCE = 1.0e-10;
  private static final double DENSITY_RELATIVE_TOLERANCE = 1.0e-8;
  private static final double MASS_BALANCE_RELATIVE_TOLERANCE = 1.0e-8;
  private static final int MAXIMUM_COUPLED_ITERATIONS = 12;
  private static final double FINITE_DIFFERENCE_RELATIVE_STEP = 1.0e-7;
  private static final int COUPLED_HALF_BANDWIDTH = 2;
  private static final int COUPLED_JACOBIAN_COLORS = 2 * COUPLED_HALF_BANDWIDTH + 1;
  /** Maximum state dimension for the failure-only dense Jacobian diagnostic. */
  private static final int MAXIMUM_DENSE_JACOBIAN_DIAGNOSTIC_SIZE = 256;
  private static final int MAXIMUM_SPECIES_COUPLING_ITERATIONS = 100;
  private static final double SPECIES_COUPLING_TOLERANCE = 1.0e-10;
  private static final double THERMODYNAMIC_COMPOSITION_TOLERANCE = 1.0e-10;

  Matrix diffMatrix;
  int iter = 0;
  Matrix[] diff4Matrix;
  double[][] xNew;
  protected double[] oldMass;
  protected double[] oldComp;
  protected double[] oldDensity;
  protected double[] oldVelocity;
  protected double[][] oldComposition;
  protected double[] oldInternalEnergy;
  protected double[] oldImpuls;
  protected double[] oldEnergy;
  private OnePhaseFlowConvergenceReport lastConvergenceReport = OnePhaseFlowConvergenceReport.notRun();
  private OnePhaseSpeciesConservationReport lastSpeciesConservationReport = OnePhaseSpeciesConservationReport.notRun();
  private boolean failOnNonConvergence;
  private boolean conservativeSpeciesTransportEnabled;
  private SpeciesAdvectionScheme speciesAdvectionScheme = SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT;
  private AxialDispersionModel axialDispersionModel = NoAxialDispersion.INSTANCE;
  private double[] coupledMassEquationScale;
  private double[] coupledMomentumEquationScale;

  /**
   * Constructor for OnePhaseFixedStaggeredGrid.
   */
  public OnePhaseFixedStaggeredGrid() {
  }

  /**
   * Constructor for OnePhaseFixedStaggeredGrid.
   *
   * @param pipe a {@link neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem} object
   * @param length a double
   * @param nodes a int
   * @param dynamic a boolean
   */
  public OnePhaseFixedStaggeredGrid(
      neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem pipe, double length, int nodes,
      boolean dynamic) {
    super(pipe, length, nodes);
    this.dynamic = dynamic;
    oldMass = new double[nodes];
    oldComp = new double[nodes];
    oldImpuls = new double[nodes];
    diff4Matrix = new Matrix[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    oldEnergy = new double[nodes];
    oldVelocity = new double[nodes];
    oldDensity = new double[nodes];
    oldInternalEnergy = new double[nodes];
    oldComposition = new double[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()][nodes];
    numberOfVelocityNodes = nodes;
  }

  /** {@inheritDoc} */
  @Override
  public OnePhaseFixedStaggeredGrid clone() {
    OnePhaseFixedStaggeredGrid clonedSystem = null;
    try {
      clonedSystem = (OnePhaseFixedStaggeredGrid) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return clonedSystem;
  }

  /**
   * Get diagnostics from the most recent solve.
   *
   * @return immutable convergence and total-mass report
   */
  public OnePhaseFlowConvergenceReport getLastConvergenceReport() {
    return lastConvergenceReport == null ? OnePhaseFlowConvergenceReport.notRun() : lastConvergenceReport;
  }

  /**
   * Get conservative component-inventory diagnostics from the most recent solve.
   *
   * @return immutable species-conservation report
   */
  public OnePhaseSpeciesConservationReport getLastSpeciesConservationReport() {
    return lastSpeciesConservationReport == null ? OnePhaseSpeciesConservationReport.notRun()
        : lastSpeciesConservationReport;
  }

  /**
   * Enable conservative n-1 species transport on the validated isothermal solver-type-1 path.
   *
   * @param enabled true to couple positive-flow component inventories to hydraulics and EOS
   */
  public void setConservativeSpeciesTransportEnabled(boolean enabled) {
    conservativeSpeciesTransportEnabled = enabled;
  }

  /**
   * Check whether conservative species transport is enabled.
   *
   * @return true when the opt-in conservative component path is active
   */
  public boolean isConservativeSpeciesTransportEnabled() {
    return conservativeSpeciesTransportEnabled;
  }

  /**
   * Select the conservative component-inventory advection scheme.
   *
   * @param scheme non-null typed species scheme
   * @throws IllegalArgumentException if {@code scheme} is null
   */
  public void setSpeciesAdvectionScheme(SpeciesAdvectionScheme scheme) {
    if (scheme == null) {
      throw new IllegalArgumentException("Conservative species advection scheme cannot be null.");
    }
    speciesAdvectionScheme = scheme;
  }

  /** @return selected conservative component-inventory advection scheme */
  public SpeciesAdvectionScheme getSpeciesAdvectionScheme() {
    return speciesAdvectionScheme;
  }

  /**
   * Select the physical axial-dispersion model used by conservative component transport.
   *
   * @param model non-null model; use {@link NoAxialDispersion} for pure advection
   * @throws IllegalArgumentException if {@code model} is null
   */
  public void setAxialDispersionModel(AxialDispersionModel model) {
    if (model == null) {
      throw new IllegalArgumentException(
          "Physical axial-dispersion model cannot be null; use NoAxialDispersion for pure advection.");
    }
    axialDispersionModel = model;
  }

  /** @return selected non-null physical axial-dispersion model */
  public AxialDispersionModel getAxialDispersionModel() {
    return axialDispersionModel;
  }

  /**
   * Configure whether a transient solve throws when the convergence report fails.
   *
   * @param failOnNonConvergence true to throw after recording a failed report; false to log a warning and return
   */
  public void setFailOnNonConvergence(boolean failOnNonConvergence) {
    this.failOnNonConvergence = failOnNonConvergence;
  }

  /**
   * Check whether failed transient convergence throws.
   *
   * @return true when strict fail-loud mode is enabled
   */
  public boolean isFailOnNonConvergence() {
    return failOnNonConvergence;
  }

  /**
   * initProfiles.
   */
  public void initProfiles() {
    double err = 0;

    double oldPres = 0;
    double dpdx = 0;
    do {
      // pipe.getNode(0).setVelocityIn(pipe.getNode(0).getVelocity());
      err = 0;
      pipe.getNode(0).initFlowCalc();
      pipe.getNode(0).init();
      pipe.getNode(0).setVelocityIn(pipe.getNode(0).getVelocity());
      for (int i = 0; i < numberOfNodes - 1; i++) {
        // setting temperatures
        pipe.getNode(i).init();
        pipe.getNode(i + 1).getBulkSystem()
            .setTemperature((4.0 * pipe.getNode(i).calcTotalHeatTransferCoefficient(0)
                * (pipe.getNode(i).getGeometry().getSurroundingEnvironment().getTemperature()
                    - pipe.getNode(i).getBulkSystem().getPhases()[0].getTemperature())
                / (pipe.getNode(i).getBulkSystem().getPhases()[0].getCp()
                    / pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
                    / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass() * pipe.getNode(i + 1).getVelocity()
                    * pipe.getNode(i + 1).getGeometry().getDiameter()
                    * pipe.getNode(i + 1).getBulkSystem().getPhases()[0].getDensity())
                + pipe.getNode(i + 1).getBulkSystem().getPhases()[0].getJouleThomsonCoefficient() * dpdx)
                * (pipe.getNode(i + 1).getGeometry().getNodeLength() + pipe.getNode(i).getGeometry().getNodeLength())
                * 0.5 + pipe.getNode(i).getBulkSystem().getTemperature());
        if (pipe.getNode(i + 1).getBulkSystem().getTemperature() < 10.5) {
          pipe.getNode(i + 1).getBulkSystem().setTemperature(10.5);
        }
        pipe.getNode(i + 1).initFlowCalc();
        pipe.getNode(i + 1).init();

        // System.out.println("velocity " + pipe.getNode(i).getVelocity());
        // setting pressures
        // System.out
        // .println("presbef : " + pipe.getNode(i + 1).getBulkSystem().getPressure());
        oldPres = pipe.getNode(i + 1).getBulkSystem().getPressure();
        pipe.getNode(i + 1).getBulkSystem()
            .setPressure(-pipe.getNode(i).getWallFrictionFactor()
                * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * pipe.getNode(i).getVelocity()
                * pipe.getNode(i).getVelocity() / pipe.getNode(i).getGeometry().getDiameter() / 2.0
                * (pipe.getNode(i).getGeometry().getNodeLength()) / 1e5
                - gravity * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
                    * (pipe.getNode(i + 1).getVerticalPositionOfNode() - pipe.getNode(i).getVerticalPositionOfNode())
                    / 1e5
                + pipe.getNode(i).getBulkSystem().getPressure());
        // if(pipe.getNode(i+1).getBulkSystem().getPressure()<10.5)
        // pipe.getNode(i+1).getBulkSystem().setPressure(1.0);
        err += (oldPres - pipe.getNode(i + 1).getBulkSystem().getPressure());
        pipe.getNode(i + 1).initFlowCalc();
        pipe.getNode(i + 1).init();
        dpdx = (pipe.getNode(i + 1).getBulkSystem().getPressure() - pipe.getNode(i).getBulkSystem().getPressure())
            / ((pipe.getNode(i + 1).getGeometry().getNodeLength() + pipe.getNode(i).getGeometry().getNodeLength())
                * 0.5);
        /*
         * System.out.println("pres : " + pipe.getNode(i + 1).getBulkSystem().getPressure()); System.out
         * .println("temp : " + pipe.getNode(i + 1).getBulkSystem().getTemperature()); System.out.println("velocity : "
         * + pipe.getNode(i + 1).getVelocity()); System.out.println("dpdx : " + dpdx); System.out .println("JT coeff : "
         * + pipe.getNode(i + 1).getBulkSystem().getPhases()[0] .getJouleThomsonCoefficient());
         */
        // setting velocities
        pipe.getNode(i + 1).setVelocityIn(pipe.getNode(i + 1).getVelocity());
        pipe.getNode(i + 1).setVelocity((pipe.getNode(i + 1).getVelocityIn().doubleValue()));
        pipe.getNode(i + 1).init();
      }
      // System.out.println("err: " + err);
    } while (Math.abs(err) > 1);
    initMatrix();
  }

  /**
   * initMatrix.
   */
  public void initMatrix() {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).init();
      double enthalpy = pipe.getNode(i).getBulkSystem().getPhases()[0].getEnthalpy()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();
      solMatrix.set(i, 0, pipe.getNode(i).getVelocityIn().doubleValue());
      sol3Matrix.set(i, 0, enthalpy);
      sol2Matrix.set(i, 0, pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity());
      for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfComponents(); j++) {
        sol4Matrix[j].set(i, 0,
            pipe.getNode(i).getBulkSystem().getPhases()[0].getComponent(j).getx()
                * pipe.getNode(i).getBulkSystem().getPhases()[0].getComponent(j).getMolarMass()
                / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass());
      }
    }
  }

  /**
   * initPressure.
   *
   * @param iteration a int
   */
  public void initPressure(int iteration) {
    for (int i = 0; i < numberOfNodes; i++) {
      // if(dynamic) System.out.println(" old pressure " +
      // pipe.getNode(i).getBulkSystem().getPressure());

      pipe.getNode(i).getBulkSystem()
          .setPressure(pipe.getNode(i).getBulkSystem().getPhases()[0].getdPdrho() * diffMatrix.get(i, 0) * 1e-5
              + pipe.getNode(i).getBulkSystem().getPressure());
      pipe.getNode(i).init();
      // if(dynamic) System.out.println("i " + i +" diff 0 " +(diffMatrix.get(i,
      // 0) )
      // + " new pressure " + pipe.getNode(i).getBulkSystem().getPressure());
    }
  }

  /**
   * initVelocity.
   *
   * @param iteration a int
   */
  public void initVelocity(int iteration) {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).setVelocityIn(pipe.getNode(i).getVelocityIn().doubleValue()
          + (solMatrix.get(i, 0) - pipe.getNode(i).getVelocityIn().doubleValue()));
      // if(dynamic) System.out.println("i " + i +" diffvel 0 " +(solMatrix.get(i,
      // 0)
      // - pipe.getNode(i).getVelocityIn().doubleValue()));
    }

    for (int i = 0; i < numberOfNodes; i++) {
      double meanVelocity = 0.0;
      if (i == numberOfNodes - 1) {
        meanVelocity = pipe.getNode(i).getVelocityIn().doubleValue();
      } else {
        meanVelocity = (pipe.getNode(i).getVelocityIn().doubleValue() + pipe.getNode(i).getVelocityOut().doubleValue())
            / 2.0;
      }
      pipe.getNode(i).setVelocity(meanVelocity);
      pipe.getNode(i).init();
    }

    // if(dynamic){
    // pipe.getNode(numberOfNodes-1).setVelocity(0.001);
    // pipe.getNode(numberOfNodes-1).init();
    // }
  }

  /**
   * initTemperature.
   *
   * @param iteration a int
   */
  public void initTemperature(int iteration) {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).init();
      pipe.getNode(i).getBulkSystem().setTemperature(
          pipe.getNode(i).getBulkSystem().getTemperature() + iteration * 1.0 / (10.0 + iteration) * diffMatrix.get(i, 0)
              / (pipe.getNode(i).getBulkSystem().getPhases()[0].getCp()
                  / pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
                  / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass()));
      pipe.getNode(i).init();

      // System.out.println("cp: " +
      // (pipe.getNode(i).getBulkSystem().getPhases()[0].getCp() /
      // pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase() /
      // pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass()));
    }
  }

  /**
   * initComposition.
   *
   * @param iter a int
   */
  public void initComposition(int iter) {
    for (int j = 1; j < numberOfNodes; j++) {
      for (int p = 0; p < pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents(); p++) {
        pipe.getNode(j).getBulkSystem().getPhases()[0].getComponent(p)
            .setx(sol4Matrix[p].get(j, 0) * pipe.getNode(j).getBulkSystem().getPhases()[0].getMolarMass()
                / pipe.getNode(j).getBulkSystem().getPhases()[0].getComponent(p).getMolarMass());
        // pipe.getNode(j).getBulkSystem().getPhases()[0].getComponent(p).getx() +
        // 0.5*diff4Matrix[p].get(j,0));
      }

      pipe.getNode(j).getBulkSystem().getPhases()[0].normalize();
      pipe.getNode(j).init();
    }
  }

  /**
   * setMassConservationMatrixTDMA.
   */
  public void setMassConservationMatrixTDMA() {
    if (!dynamic) {
      double SU = 0;
      a[0] = 0;
      b[0] = 1.0;
      c[0] = 0;
      SU = pipe.getNode(0).getBulkSystem().getPhases()[0].getDensity();
      r[0] = SU;
    } else {
      // Node zero is the prescribed upstream boundary, not an accumulating control volume. The
      // first physical control volume is node one and uses this density in its west-face flux.
      a[0] = 0.0;
      b[0] = 1.0;
      c[0] = 0.0;
      r[0] = pipe.getNode(0).getBulkSystem().getPhases()[0].getDensity();
    }

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();
      double Fe = pipe.getNode(i).getVelocityOut().doubleValue() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn().doubleValue() * Aw;

      if (dynamic) {
        oldMass[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
            * pipe.getNode(i).getGeometry().getNodeLength();
      } else {
        oldMass[i] = 0.0;
      }
      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) + oldMass[i];
      r[i] = oldMass[i] * oldDensity[i];

      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;
    if (dynamic && solverType == 1) {
      // The last node prescribes outlet pressure and is not an accumulating control volume.
      a[i] = 0.0;
      b[i] = 1.0;
      c[i] = 0.0;
      r[i] = pipe.getNode(i).getBulkSystem().getPhase(0).getDensity();
    } else {
      double ae = pipe.getNode(i).getGeometry().getArea();
      double aw = pipe.getNode(i - 1).getGeometry().getArea();
      double fe = pipe.getNode(i).getVelocity() * ae;
      double fw = pipe.getNode(i).getVelocityIn().doubleValue() * aw;
      oldMass[i] = dynamic
          ? 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea() * pipe.getNode(i).getGeometry().getNodeLength()
          : 0.0;
      a[i] = -Math.max(fw, 0.0);
      c[i] = -Math.max(-fe, 0.0);
      b[i] = -a[i] - c[i] + fe - fw + oldMass[i];
      r[i] = oldMass[i] * oldDensity[i];
    }
  }

  /**
   * setImpulsMatrixTDMA.
   */
  public void setImpulsMatrixTDMA() {
    double SU = 0.0;
    double SP = 0.0;
    double Fw = 0.0;

    // pipe.getNode(0).initFlowCalc();
    // pipe.getNode(0).init();
    // pipe.getNode(0).setVelocityIn(pipe.getNode(0).getVelocity());

    double Fe = 0.0;
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;

    r[0] = pipe.getNode(0).getVelocityIn().doubleValue();

    a[1] = 0;
    b[1] = 1.0;
    c[1] = 0;

    r[1] = pipe.getNode(0).getVelocityIn().doubleValue();

    for (int i = 2; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();
      double Amean = pipe.getNode(i - 1).getGeometry().getArea();
      double meanDiameter = pipe.getNode(i - 1).getGeometry().getDiameter();
      double meanFrik = pipe.getNode(i - 1).getWallFrictionFactor();
      double meanDensity = (pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
          + pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()) / 2.0;
      double oldMeanDensity = (oldDensity[i - 1] + oldDensity[i]) / 2.0;
      double meanVelocity = (pipe.getNode(i - 1).getVelocity() + pipe.getNode(i).getVelocity()) / 2.0;
      double vertposchange = pipe.getNode(i).getVerticalPositionOfNode()
          - pipe.getNode(i - 1).getVerticalPositionOfNode();
      double nodeLength = pipe.getNode(i - 1).getGeometry().getNodeLength();

      SU = -Amean * (pipe.getNode(i).getBulkSystem().getPressure() - pipe.getNode(i - 1).getBulkSystem().getPressure())
          * 1e5 - Amean * gravity * meanDensity * vertposchange
          + Amean * nodeLength * meanDensity * meanFrik / meanDiameter * meanVelocity * Math.abs(meanVelocity) / 2.0;
      SP = -Amean * nodeLength * meanDensity * meanFrik / meanDiameter * meanVelocity;
      Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()
          * (pipe.getNode(i - 1).getVelocityIn().doubleValue() + pipe.getNode(i - 1).getVelocityOut().doubleValue())
          / 2.0;
      Fe = Ae * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
          * (pipe.getNode(i).getVelocityIn().doubleValue() + pipe.getNode(i).getVelocityOut().doubleValue()) / 2.0;

      if (dynamic) {
        oldImpuls[i] = 1.0 / timeStep * oldMeanDensity * nodeLength * Amean;
      } else {
        oldImpuls[i] = 0.0;
      }
      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0); // - Fe/2.0;

      b[i] = a[i] + c[i] + (Fe - Fw) - SP + oldImpuls[i];
      // System.out.println("Fe-Fw: " +(Fe - Fw) + " Fe: " + Fe);
      r[i] = SU + oldImpuls[i] * oldVelocity[i];
      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;
    double Ae = pipe.getNode(i).getGeometry().getArea();
    double Aw = pipe.getNode(i - 1).getGeometry().getArea();
    double Amean = pipe.getNode(i - 1).getGeometry().getArea();
    double meanDiameter = pipe.getNode(i - 1).getGeometry().getDiameter();
    double meanFrik = pipe.getNode(i - 1).getWallFrictionFactor();
    double meanDensity = pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity();
    double oldMeanDensity = oldDensity[i - 1];
    double meanVelocity = pipe.getNode(i - 1).getVelocity();
    double vertposchange = pipe.getNode(i).getVerticalPositionOfNode()
        - pipe.getNode(i - 1).getVerticalPositionOfNode();
    double nodeLength = pipe.getNode(i - 1).getGeometry().getNodeLength();

    SU = -Amean * (pipe.getNode(i).getBulkSystem().getPressure() - pipe.getNode(i - 1).getBulkSystem().getPressure())
        * 1e5 - Amean * gravity * meanDensity * vertposchange
        + Amean * nodeLength * meanDensity * meanFrik / meanDiameter * meanVelocity * Math.abs(meanVelocity) / 2.0;
    SP = -Amean * nodeLength * meanDensity * meanFrik / meanDiameter * meanVelocity;
    Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()
        * (pipe.getNode(i - 1).getVelocityIn().doubleValue() + pipe.getNode(i - 1).getVelocityOut().doubleValue())
        / 2.0;
    Fe = Ae * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
        * (pipe.getNode(i).getVelocityIn().doubleValue() + pipe.getNode(i).getVelocityOut().doubleValue()) / 2.0;

    if (dynamic) {
      oldImpuls[i] = 1.0 / timeStep * oldMeanDensity * nodeLength * Amean;
    } else {
      oldImpuls[i] = 0.0;
    }
    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    // if(dynamic){c[i] = - Fe/2.0; a[i] = Fw/2.0; }
    b[i] = a[i] + c[i] + (Fe - Fw) - SP + oldImpuls[i];
    r[i] = SU + oldImpuls[i] * oldVelocity[i];

    // setter ligningen paa rett form
    a[numberOfNodes - 1] = -a[numberOfNodes - 1];
    c[numberOfNodes - 1] = -c[numberOfNodes - 1];
  }

  /**
   * setEnergyMatrixTDMA.
   */
  public void setEnergyMatrixTDMA() {
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    double SU = pipe.getNode(0).getBulkSystem().getPhases()[0].getEnthalpy()
        / pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
        / pipe.getNode(0).getBulkSystem().getPhases()[0].getMolarMass();
    r[0] = SU;

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double fe = pipe.getNode(i + 1).getGeometry().getNodeLength()
          / (pipe.getNode(i).getGeometry().getNodeLength() + pipe.getNode(i + 1).getGeometry().getNodeLength());
      double fw = pipe.getNode(i - 1).getGeometry().getNodeLength()
          / (pipe.getNode(i).getGeometry().getNodeLength() + pipe.getNode(i - 1).getGeometry().getNodeLength());
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();
      double vertposchange = (1 - fe)
          * (pipe.getNode(i + 1).getVerticalPositionOfNode() - pipe.getNode(i).getVerticalPositionOfNode())
          + (1 - fw) * (pipe.getNode(i).getVerticalPositionOfNode() - pipe.getNode(i - 1).getVerticalPositionOfNode());

      SU = -pipe.getNode(i).getGeometry().getArea() * gravity
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * pipe.getNode(i).getVelocity() * vertposchange
          + pipe.getNode(i).getGeometry().getArea() * 4.0 * pipe.getNode(i).calcTotalHeatTransferCoefficient(0)
              * (pipe.getNode(i).getGeometry().getSurroundingEnvironment().getTemperature()
                  - pipe.getNode(i).getBulkSystem().getTemperature())
              / (pipe.getNode(i).getGeometry().getDiameter()) * pipe.getNode(i).getGeometry().getNodeLength();
      double SP = 0;
      double Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()
          * pipe.getNode(i).getVelocityIn().doubleValue();
      double Fe = Ae * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
          * pipe.getNode(i).getVelocityOut().doubleValue();

      if (dynamic) {
        oldEnergy[i] = 1.0 / timeStep * oldDensity[i] * pipe.getNode(i).getGeometry().getNodeLength()
            * pipe.getNode(i).getGeometry().getArea();
      } else {
        oldEnergy[i] = 0.0;
      }
      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) - SP + oldEnergy[i];
      r[i] = SU + oldEnergy[i] * oldInternalEnergy[i];

      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;

    double fw = pipe.getNode(i - 1).getGeometry().getNodeLength()
        / (pipe.getNode(i).getGeometry().getNodeLength() + pipe.getNode(i - 1).getGeometry().getNodeLength());
    double Ae = pipe.getNode(i).getGeometry().getArea();
    // 1.0/((1.0-fe)/pipe.getNode(i).getGeometry().getArea() +
    // fe/pipe.getNode(i+1).getGeometry().getArea());

    double Aw = pipe.getNode(i - 1).getGeometry().getArea();
    // 1.0/((1.0-fw)/pipe.getNode(i).getGeometry().getArea() +
    // fw/pipe.getNode(i-1).getGeometry().getArea());

    double vertposchange = (1 - fw)
        * (pipe.getNode(i).getVerticalPositionOfNode() - pipe.getNode(i - 1).getVerticalPositionOfNode());

    SU = -pipe.getNode(i).getGeometry().getArea() * gravity
        * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * pipe.getNode(i).getVelocity() * vertposchange
        + pipe.getNode(i).getGeometry().getArea() * 4.0 * pipe.getNode(i).calcTotalHeatTransferCoefficient(0)
            * (pipe.getNode(i).getGeometry().getSurroundingEnvironment().getTemperature()
                - pipe.getNode(i).getBulkSystem().getTemperature())
            / (pipe.getNode(i).getGeometry().getDiameter()) * pipe.getNode(i).getGeometry().getNodeLength();
    double SP = 0;
    // -pipe.getNode(i).getGeometry().getArea() *
    // 4.0*12.0 /
    // (pipe.getNode(i).getGeometry().getDiameter())*pipe.getNode(i).getGeometry().getNodeLength();

    double Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()
        * pipe.getNode(i).getVelocityIn().doubleValue();
    double Fe = Ae * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * pipe.getNode(i).getVelocity();

    if (dynamic) {
      oldEnergy[i] = 1.0 / timeStep * oldDensity[i] * pipe.getNode(i).getGeometry().getNodeLength()
          * pipe.getNode(i).getGeometry().getArea();
    } else {
      oldEnergy[i] = 0.0;
    }
    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    b[i] = a[i] + c[i] + (Fe - Fw) - SP + oldEnergy[i];
    r[i] = SU + oldEnergy[i] * oldInternalEnergy[i];
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * setComponentConservationMatrix.
   *
   * @param componentNumber a int
   */
  public void setComponentConservationMatrix(int componentNumber) {
    neqsim.fluidmechanics.flowsolver.AdvectionScheme scheme = pipe.getAdvectionScheme();

    if (scheme == neqsim.fluidmechanics.flowsolver.AdvectionScheme.FIRST_ORDER_UPWIND) {
      setComponentConservationMatrixFirstOrderUpwind(componentNumber);
    } else if (scheme.usesTVD()) {
      setComponentConservationMatrixTVD(componentNumber, scheme);
    } else if (scheme == neqsim.fluidmechanics.flowsolver.AdvectionScheme.SECOND_ORDER_UPWIND) {
      setComponentConservationMatrixSecondOrderUpwind(componentNumber);
    } else if (scheme == neqsim.fluidmechanics.flowsolver.AdvectionScheme.QUICK) {
      setComponentConservationMatrixQUICK(componentNumber);
    } else {
      // Default to first-order upwind
      setComponentConservationMatrixFirstOrderUpwind(componentNumber);
    }
  }

  /**
   * First-order upwind scheme for component conservation (original implementation).
   *
   * <p>
   * This scheme is unconditionally stable but has high numerical dispersion: D_num = (v × Δx / 2) × (1 - CFL)
   * </p>
   *
   * @param componentNumber the component index
   */
  private void setComponentConservationMatrixFirstOrderUpwind(int componentNumber) {
    double SU = 0;
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    SU = pipe.getNode(0).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getx()
        * pipe.getNode(0).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getMolarMass()
        / pipe.getNode(0).getBulkSystem().getPhases()[0].getMolarMass();
    r[0] = SU;

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();

      double Fe = pipe.getNode(i).getVelocityOut().doubleValue()
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
          * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;
      if (dynamic) {
        oldComp[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
            * pipe.getNode(i).getGeometry().getNodeLength()
            * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
      } else {
        oldComp[i] = 0.0;
      }
      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[i];
      r[i] = oldComp[i] * oldComposition[componentNumber][i];

      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;
    double Ae = pipe.getNode(i).getGeometry().getArea();
    double Aw = pipe.getNode(i - 1).getGeometry().getArea();

    double Fe = pipe.getNode(i).getVelocity() * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
    double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
        * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;

    if (dynamic) {
      oldComp[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
          * pipe.getNode(i).getGeometry().getNodeLength() * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
    } else {
      oldComp[i] = 0.0;
    }
    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[i];
    r[i] = oldComp[i] * oldComposition[componentNumber][i];
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * TVD (Total Variation Diminishing) scheme with flux limiters.
   *
   * <p>
   * Achieves second-order accuracy in smooth regions while preventing oscillations near discontinuities. The flux
   * limiter blends between first-order upwind and a higher-order scheme based on the local solution gradient.
   * </p>
   *
   * @param componentNumber the component index
   * @param scheme the TVD scheme variant (determines limiter function)
   */
  private void setComponentConservationMatrixTVD(int componentNumber,
      neqsim.fluidmechanics.flowsolver.AdvectionScheme scheme) {
    // Get composition values for gradient calculation
    double[] phi = new double[numberOfNodes];
    for (int i = 0; i < numberOfNodes; i++) {
      phi[i] = pipe.getNode(i).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getx()
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getMolarMass()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();
    }

    // Inlet boundary condition
    double SU = phi[0];
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    r[0] = SU;

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();

      double Fe = pipe.getNode(i).getVelocityOut().doubleValue()
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
          * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;

      if (dynamic) {
        oldComp[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
            * pipe.getNode(i).getGeometry().getNodeLength()
            * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
      } else {
        oldComp[i] = 0.0;
      }

      // Calculate gradient ratios for flux limiting
      double rW = 1.0; // Default for boundaries
      double rE = 1.0;

      if (i > 1 && Fw > 0) {
        // West face, positive flow (from upstream)
        rW = neqsim.fluidmechanics.flowsolver.FluxLimiter.gradientRatio(phi[i - 2], phi[i - 1], phi[i]);
      }
      if (i < numberOfNodes - 2 && Fe > 0) {
        // East face, positive flow
        rE = neqsim.fluidmechanics.flowsolver.FluxLimiter.gradientRatio(phi[i - 1], phi[i], phi[i + 1]);
      }

      // Get limiter values
      double psiW = neqsim.fluidmechanics.flowsolver.FluxLimiter.getLimiterValue(scheme, rW);
      double psiE = neqsim.fluidmechanics.flowsolver.FluxLimiter.getLimiterValue(scheme, rE);

      // Higher-order flux correction (anti-diffusion)
      // The TVD correction reduces numerical diffusion by adding anti-diffusive flux
      // modulated by the limiter function
      double higherOrderCorrectionW = 0.5 * psiW * Math.abs(Fw) * (1.0 - Math.abs(Fw) / (Math.abs(Fw) + 1e-10));
      double higherOrderCorrectionE = 0.5 * psiE * Math.abs(Fe) * (1.0 - Math.abs(Fe) / (Math.abs(Fe) + 1e-10));

      // Modified coefficients with TVD correction
      double aBase = Math.max(Fw, 0);
      double cBase = Math.max(-Fe, 0);

      // Add higher-order correction to RHS (deferred correction approach)
      double fluxCorrection = 0.0;
      if (i > 1 && Fw > 0) {
        fluxCorrection += higherOrderCorrectionW * (phi[i - 1] - phi[i - 2]);
      }
      if (i < numberOfNodes - 2 && Fe > 0) {
        fluxCorrection -= higherOrderCorrectionE * (phi[i + 1] - phi[i]);
      }

      a[i] = aBase;
      c[i] = cBase;
      b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[i];
      r[i] = oldComp[i] * oldComposition[componentNumber][i] + fluxCorrection;

      a[i] = -a[i];
      c[i] = -c[i];
    }

    // Outlet boundary
    int i = numberOfNodes - 1;
    double Ae = pipe.getNode(i).getGeometry().getArea();
    double Aw = pipe.getNode(i - 1).getGeometry().getArea();

    double Fe = pipe.getNode(i).getVelocity() * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
    double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
        * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;

    if (dynamic) {
      oldComp[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
          * pipe.getNode(i).getGeometry().getNodeLength() * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
    } else {
      oldComp[i] = 0.0;
    }
    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[i];
    r[i] = oldComp[i] * oldComposition[componentNumber][i];
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * Second-order upwind (Linear Upwind Differencing) scheme.
   *
   * <p>
   * Uses two upstream points for higher accuracy. Much less dispersive than first-order upwind but may oscillate near
   * discontinuities.
   * </p>
   *
   * @param componentNumber the component index
   */
  private void setComponentConservationMatrixSecondOrderUpwind(int componentNumber) {
    // Get composition values for gradient calculation
    double[] phi = new double[numberOfNodes];
    for (int i = 0; i < numberOfNodes; i++) {
      phi[i] = pipe.getNode(i).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getx()
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getMolarMass()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();
    }

    // Inlet boundary condition
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    r[0] = phi[0];

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();

      double Fe = pipe.getNode(i).getVelocityOut().doubleValue()
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
          * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;

      if (dynamic) {
        oldComp[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
            * pipe.getNode(i).getGeometry().getNodeLength()
            * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
      } else {
        oldComp[i] = 0.0;
      }

      // Second-order upwind: use two upstream points
      // For positive flow at west face: phi_w = 1.5*phi_{i-1} - 0.5*phi_{i-2}
      double extrapolationW = 0.0;
      double extrapolationE = 0.0;

      if (i > 1 && Fw > 0) {
        // Second-order extrapolation from upstream
        extrapolationW = 0.5 * Fw * (phi[i - 1] - phi[i - 2]);
      }
      if (i < numberOfNodes - 2 && Fe > 0) {
        extrapolationE = 0.5 * Fe * (phi[i] - phi[i - 1]);
      }

      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[i];

      // Add second-order correction to RHS
      r[i] = oldComp[i] * oldComposition[componentNumber][i] + extrapolationW - extrapolationE;

      a[i] = -a[i];
      c[i] = -c[i];
    }

    // Outlet boundary (first-order)
    int i = numberOfNodes - 1;
    double Ae = pipe.getNode(i).getGeometry().getArea();
    double Aw = pipe.getNode(i - 1).getGeometry().getArea();

    double Fe = pipe.getNode(i).getVelocity() * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
    double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
        * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;

    if (dynamic) {
      oldComp[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
          * pipe.getNode(i).getGeometry().getNodeLength() * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
    } else {
      oldComp[i] = 0.0;
    }
    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[i];
    r[i] = oldComp[i] * oldComposition[componentNumber][i];
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * QUICK scheme (Quadratic Upstream Interpolation for Convective Kinematics).
   *
   * <p>
   * Third-order accurate on uniform grids. Uses quadratic interpolation with upstream bias. Very low numerical
   * dispersion but may produce oscillations.
   * </p>
   *
   * @param componentNumber the component index
   */
  private void setComponentConservationMatrixQUICK(int componentNumber) {
    // Get composition values
    double[] phi = new double[numberOfNodes];
    for (int i = 0; i < numberOfNodes; i++) {
      phi[i] = pipe.getNode(i).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getx()
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getMolarMass()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();
    }

    // Inlet boundary condition
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    r[0] = phi[0];

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();

      double Fe = pipe.getNode(i).getVelocityOut().doubleValue()
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
          * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;

      if (dynamic) {
        oldComp[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
            * pipe.getNode(i).getGeometry().getNodeLength()
            * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
      } else {
        oldComp[i] = 0.0;
      }

      // QUICK: phi_face = phi_U + (3*phi_D + 6*phi_C - phi_UU)/8
      // For positive flow at west face: U=i-2, C=i-1, D=i
      double quickCorrectionW = 0.0;
      double quickCorrectionE = 0.0;

      if (i > 1 && Fw > 0) {
        // QUICK interpolation at west face
        double phiQuickW = phi[i - 1] + (3.0 * phi[i] + 6.0 * phi[i - 1] - phi[i - 2]) / 8.0 - phi[i - 1];
        quickCorrectionW = Fw * phiQuickW;
      }
      if (i < numberOfNodes - 2 && Fe > 0) {
        // QUICK interpolation at east face
        double phiQuickE = phi[i] + (3.0 * phi[i + 1] + 6.0 * phi[i] - phi[i - 1]) / 8.0 - phi[i];
        quickCorrectionE = Fe * phiQuickE;
      }

      a[i] = Math.max(Fw, 0);
      c[i] = Math.max(-Fe, 0);
      b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[i];

      // Add QUICK correction to RHS (deferred correction)
      r[i] = oldComp[i] * oldComposition[componentNumber][i] + quickCorrectionW - quickCorrectionE;

      a[i] = -a[i];
      c[i] = -c[i];
    }

    // Outlet boundary (first-order)
    int i = numberOfNodes - 1;
    double Ae = pipe.getNode(i).getGeometry().getArea();
    double Aw = pipe.getNode(i - 1).getGeometry().getArea();

    double Fe = pipe.getNode(i).getVelocity() * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
    double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
        * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;

    if (dynamic) {
      oldComp[i] = 1.0 / timeStep * pipe.getNode(i).getGeometry().getArea()
          * pipe.getNode(i).getGeometry().getNodeLength() * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
    } else {
      oldComp[i] = 0.0;
    }
    a[i] = Math.max(Fw, 0);
    c[i] = Math.max(-Fe, 0);
    b[i] = a[i] + c[i] + (Fe - Fw) + oldComp[i];
    r[i] = oldComp[i] * oldComposition[componentNumber][i];
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * initFinalResults.
   */
  public void initFinalResults() {
    for (int i = 0; i < numberOfNodes; i++) {
      oldVelocity[i] = pipe.getNode(i).getVelocityIn().doubleValue();
      oldDensity[i] = pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
      oldInternalEnergy[i] = pipe.getNode(i).getBulkSystem().getPhases()[0].getEnthalpy()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();

      for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfComponents(); j++) {
        oldComposition[j][i] = sol4Matrix[j].get(i, 0);
        // pipe.getNode(i).getBulkSystem().getPhases()[0].getComponent(j).getx() *
        // pipe.getNode(i).getBulkSystem().getPhases()[0].getComponent(j).getMolarMass() /
        // pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void solveTDMA() {
    lastSpeciesConservationReport = OnePhaseSpeciesConservationReport.notRun();
    double[] d;
    int iter = 0;
    int iterTop = 0;
    double maxDiff = 1.0;
    double densityResidual = Double.NaN;
    double diff = 0;
    double[] nonlinearUpdateHistory = new double[MAXIMUM_NONLINEAR_ITERATIONS];
    double[] densityResidualHistory = new double[MAXIMUM_NONLINEAR_ITERATIONS];
    double initialFiniteVolumeMass = calculateInitialFiniteVolumeMass();
    xNew = new double[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()][numberOfNodes];
    if (!dynamic) {
      initProfiles();
    }
    initMatrix();

    if (solverType == 1 && (dynamic || conservativeSpeciesTransportEnabled)) {
      if (dynamic && conservativeSpeciesTransportEnabled) {
        solveCoupledHydraulicEosSpecies(initialFiniteVolumeMass);
      } else {
        solveCoupledHydraulicEos(initialFiniteVolumeMass);
      }
      initFinalResults();
      return;
    }

    do {
      maxDiff = 0;
      iterTop++;

      iter = 0;
      if (this.solverType >= 0) {
        do {
          maxDiff = 0;
          do {
            iter++;
            setImpulsMatrixTDMA();
            Matrix solOld = solMatrix.copy();
            d = TDMAsolve.solve(a, b, c, r);
            solMatrix = new Matrix(d, 1).transpose();
            diffMatrix = solMatrix.minus(solOld);
            // if(dynamic) solMatrix.print(10,10);

            // System.out.println("diff1: ");
            // diffMatrix.print(10,10);
            // System.out.println("diff1: "+
            // diffMatrix.norm1()/solMatrix.norm1());
            initVelocity(iter);

            diff = Math.abs(diffMatrix.norm1() / solMatrix.norm1());
            if (diff > maxDiff) {
              maxDiff = diff;
            }
          } while (diff > 1e-15 && iter < 10);

          if (solverType > 0) {
            iter = 0;
            do {
              iter++;
              // System.out.println("iter: " +iter);
              setMassConservationMatrixTDMA();
              d = TDMAsolve.solve(a, b, c, r);
              Matrix sol2Old = sol2Matrix.copy();
              // sol2Matrix.print(10,10);
              sol2Matrix = new Matrix(d, 1).transpose();
              diffMatrix = sol2Matrix.minus(sol2Old);
              // System.out.println("diff2:
              // "+diffMatrix.norm1()/sol2Matrix.norm1());
              initPressure(iter);
              diff = Math.abs(diffMatrix.norm1() / sol2Matrix.norm1());
              if (diff > maxDiff) {
                maxDiff = diff;
              }
            } while (diff > 1e-15 && iter < 10);
          }
          // System.out.println("max diff " + maxDiff);
        } while (Math.abs(maxDiff) > 1e-10);
      }

      if (this.solverType >= 10) {
        iter = 0;
        do {
          iter++;
          Matrix sol3Old = sol3Matrix.copy();
          setEnergyMatrixTDMA();
          d = TDMAsolve.solve(a, b, c, r);
          sol3Matrix = new Matrix(d, 1).transpose();
          diffMatrix = sol3Matrix.minus(sol3Old);
          // System.out.println("diff3: " + diffMatrix.norm1() / sol3Matrix.norm1());
          initTemperature(iter);

          diff = Math.abs(diffMatrix.norm1() / sol3Matrix.norm1());
          if (diff > maxDiff) {
            maxDiff = diff;
          }
        } while (diff > 1e-15 && iter < 10);
      }

      if (this.solverType >= 20) {
        iter = 0;
        do {
          iter++;
          for (int p = 0; p < pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents(); p++) {
            setComponentConservationMatrix(p);
            Matrix sol4Old = sol4Matrix[p].copy();
            xNew[p] = TDMAsolve.solve(a, b, c, r);
            sol4Matrix[p] = new Matrix(xNew[p], 1).transpose();
            diff4Matrix[p] = sol4Matrix[p].minus(sol4Old);
            diff = Math.abs(diff4Matrix[p].norm1() / (sol4Matrix[p].norm1()));
            if (diff > maxDiff) {
              maxDiff = diff;
            }
          }
          initComposition(iter);
          // solMatrix.print(10,10);
        } while (diff > 1e-15 && iter < 10);
      }

      densityResidual = calculateMaximumRelativeDensityResidual();
      nonlinearUpdateHistory[iterTop - 1] = Math.abs(maxDiff);
      densityResidualHistory[iterTop - 1] = densityResidual;
      // System.out.println("maxDiff " + maxDiff);
    } while (!hasConverged(maxDiff, densityResidual) && iterTop < MAXIMUM_NONLINEAR_ITERATIONS); // diffMatrix.norm2()/sol2Matrix.norm2())>0.1);

    if (!dynamic && solverType == 1) {
      OnePhaseFixedStaggeredGrid refinement = new OnePhaseFixedStaggeredGrid(pipe, pipe.getSystemLength(),
          numberOfNodes, false);
      refinement.setSolverType(solverType);
      refinement.initMatrix();
      refinement.solveCoupledHydraulicEos(Double.NaN);
      lastConvergenceReport = refinement.getLastConvergenceReport();
      initFinalResults();
      return;
    }

    lastConvergenceReport = createConvergenceReport(iterTop, maxDiff, densityResidual, initialFiniteVolumeMass,
        Arrays.copyOf(nonlinearUpdateHistory, iterTop), Arrays.copyOf(densityResidualHistory, iterTop), false, null,
        false);

    if (dynamic && solverType > 0 && !lastConvergenceReport.isConverged()) {
      if (failOnNonConvergence) {
        throw new IllegalStateException(lastConvergenceReport.getMessage());
      }
      logger.warn("{}", lastConvergenceReport.getMessage());
    }

    initFinalResults();
  }

  private void solveCoupledHydraulicEosSpecies(double initialFiniteVolumeMass) {
    double maximumCompositionChange = Double.POSITIVE_INFINITY;
    double densityResidual = Double.POSITIVE_INFINITY;
    double[] compositionChangeHistory = new double[MAXIMUM_SPECIES_COUPLING_ITERATIONS];
    double[] speciesDensityHistory = new double[MAXIMUM_SPECIES_COUPLING_ITERATIONS];
    int couplingIteration = 0;

    while (couplingIteration < MAXIMUM_SPECIES_COUPLING_ITERATIONS
        && (maximumCompositionChange > SPECIES_COUPLING_TOLERANCE || densityResidual > DENSITY_RELATIVE_TOLERANCE)) {
      solveCoupledHydraulicEos(initialFiniteVolumeMass);
      if (!lastConvergenceReport.isConverged()) {
        throw new IllegalStateException(lastConvergenceReport.getMessage());
      }
      OnePhaseSpeciesConservationReport candidate = solveConservativeSpeciesStep();
      if (!candidate.isConverged()) {
        lastSpeciesConservationReport = candidate;
        handleSpeciesFailure(candidate);
        return;
      }
      maximumCompositionChange = maximumConservativeCompositionChange(candidate);
      synchronizeThermodynamicComposition(candidate);
      densityResidual = calculateMaximumRelativeDensityResidual();
      compositionChangeHistory[couplingIteration] = maximumCompositionChange;
      speciesDensityHistory[couplingIteration] = densityResidual;
      lastSpeciesConservationReport = candidate;
      couplingIteration++;
    }

    lastSpeciesConservationReport = lastSpeciesConservationReport.withCouplingDiagnostics(couplingIteration,
        Arrays.copyOf(compositionChangeHistory, couplingIteration),
        Arrays.copyOf(speciesDensityHistory, couplingIteration));
    double thermodynamicSyncError = calculateMaximumThermodynamicMassFractionError(lastSpeciesConservationReport);
    lastSpeciesConservationReport = lastSpeciesConservationReport.withThermodynamicSync(thermodynamicSyncError,
        THERMODYNAMIC_COMPOSITION_TOLERANCE);
    OnePhaseFlowConvergenceReport hydraulicReport = lastConvergenceReport;
    double[] densityHistory = hydraulicReport.getDensityResidualHistory();
    if (densityHistory.length > 0) {
      densityHistory[densityHistory.length - 1] = densityResidual;
    }
    lastConvergenceReport = createConvergenceReport(hydraulicReport.getNonlinearIterations(),
        hydraulicReport.getMaximumRelativeNonlinearUpdate(), densityResidual, initialFiniteVolumeMass,
        hydraulicReport.getNonlinearUpdateHistory(), densityHistory,
        hydraulicReport.getMaximumScaledMassEquationResidual(),
        hydraulicReport.getMaximumScaledMomentumEquationResidual(),
        hydraulicReport.getScaledMassEquationResidualHistory(),
        hydraulicReport.getScaledMomentumEquationResidualHistory(), false, null, true);

    if (maximumCompositionChange > SPECIES_COUPLING_TOLERANCE || densityResidual > DENSITY_RELATIVE_TOLERANCE) {
      lastSpeciesConservationReport = lastSpeciesConservationReport.withReason(
          OnePhaseSpeciesConservationReport.ConservationReason.COUPLING_NOT_CONVERGED,
          "Hydraulic/species fixed point did not converge after " + couplingIteration
              + " iterations: maximum mass-fraction change=" + maximumCompositionChange + " (tolerance "
              + SPECIES_COUPLING_TOLERANCE + "), EOS/FV density=" + densityResidual + " (tolerance "
              + DENSITY_RELATIVE_TOLERANCE + ").");
    }

    if (!lastSpeciesConservationReport.isConverged()) {
      handleSpeciesFailure(lastSpeciesConservationReport);
    }
    if (!lastConvergenceReport.isConverged()) {
      throw new IllegalStateException(lastConvergenceReport.getMessage());
    }
  }

  private OnePhaseSpeciesConservationReport solveConservativeSpeciesStep() {
    int components = pipe.getNode(0).getBulkSystem().getPhase(0).getNumberOfComponents();
    int cells = numberOfNodes - 2;
    String[] componentNames = new String[components];
    double[][] previousMassFraction = new double[components][cells];
    double[] inletMassFraction = new double[components];
    double[] previousCellMassKg = new double[cells];
    double[] finalCellMassKg = new double[cells];
    double[] faceMassFlowKgPerSecond = new double[cells + 1];
    double[] cellLengthM = new double[cells];

    for (int component = 0; component < components; component++) {
      componentNames[component] = pipe.getNode(0).getBulkSystem().getPhase(0).getComponent(component).getName();
      inletMassFraction[component] = componentMassFraction(0, component);
      for (int cell = 0; cell < cells; cell++) {
        previousMassFraction[component][cell] = oldComposition[component][cell + 1];
      }
    }
    for (int cell = 0; cell < cells; cell++) {
      int node = cell + 1;
      double volume = getControlVolume(node);
      previousCellMassKg[cell] = oldDensity[node] * volume;
      finalCellMassKg[cell] = sol2Matrix.get(node, 0) * volume;
      cellLengthM[cell] = pipe.getNode(node).getGeometry().getNodeLength();
    }
    faceMassFlowKgPerSecond[0] = pipe.getNode(1).getVelocityIn().doubleValue() * pipe.getNode(0).getGeometry().getArea()
        * sol2Matrix.get(0, 0);
    for (int face = 1; face < cells; face++) {
      int upstreamNode = face;
      faceMassFlowKgPerSecond[face] = pipe.getNode(upstreamNode).getVelocityOut().doubleValue()
          * pipe.getNode(upstreamNode).getGeometry().getArea() * sol2Matrix.get(upstreamNode, 0);
    }
    int outletCellNode = numberOfNodes - 2;
    faceMassFlowKgPerSecond[cells] = pipe.getNode(outletCellNode).getVelocityOut().doubleValue()
        * pipe.getNode(outletCellNode).getGeometry().getArea() * sol2Matrix.get(outletCellNode, 0);

    return ConservativeSpeciesTransport.solve(componentNames, previousMassFraction, inletMassFraction,
        previousCellMassKg, finalCellMassKg, faceMassFlowKgPerSecond, timeStep, speciesAdvectionScheme, cellLengthM,
        axialDispersionModel);
  }

  private double maximumConservativeCompositionChange(OnePhaseSpeciesConservationReport report) {
    double[][] massFraction = report.getMassFractionProfile();
    double maximum = 0.0;
    for (int component = 0; component < massFraction.length; component++) {
      for (int cell = 0; cell < massFraction[component].length; cell++) {
        maximum = Math.max(maximum,
            Math.abs(massFraction[component][cell] - componentMassFraction(cell + 1, component)));
      }
    }
    return maximum;
  }

  private void synchronizeThermodynamicComposition(OnePhaseSpeciesConservationReport report) {
    double[][] massFraction = report.getMassFractionProfile();
    for (int cell = 0; cell < massFraction[0].length; cell++) {
      int node = cell + 1;
      double[] moleFraction = massToMoleFractions(node, massFraction, cell);
      pipe.getNode(node).getBulkSystem().setMolarComposition(moleFraction);
      pipe.getNode(node).getBulkSystem().init(0);
      pipe.getNode(node).getBulkSystem().init(1);
      pipe.getNode(node).init();
      for (int component = 0; component < massFraction.length; component++) {
        sol4Matrix[component].set(node, 0, massFraction[component][cell]);
      }
    }

    int outletNode = numberOfNodes - 1;
    double[] outletMoleFraction = massToMoleFractions(numberOfNodes - 2, massFraction, massFraction[0].length - 1);
    pipe.getNode(outletNode).getBulkSystem().setMolarComposition(outletMoleFraction);
    pipe.getNode(outletNode).getBulkSystem().init(0);
    pipe.getNode(outletNode).getBulkSystem().init(1);
    pipe.getNode(outletNode).init();
    for (int component = 0; component < massFraction.length; component++) {
      sol4Matrix[component].set(outletNode, 0, massFraction[component][massFraction[component].length - 1]);
    }
  }

  private double[] massToMoleFractions(int node, double[][] massFraction, int cell) {
    int components = massFraction.length;
    double[] moleFraction = new double[components];
    double molarDenominator = 0.0;
    for (int component = 0; component < components; component++) {
      molarDenominator += massFraction[component][cell]
          / pipe.getNode(node).getBulkSystem().getPhase(0).getComponent(component).getMolarMass();
    }
    double independentSum = 0.0;
    for (int component = 0; component < components - 1; component++) {
      moleFraction[component] = massFraction[component][cell]
          / pipe.getNode(node).getBulkSystem().getPhase(0).getComponent(component).getMolarMass() / molarDenominator;
      independentSum += moleFraction[component];
    }
    moleFraction[components - 1] = 1.0 - independentSum;
    return moleFraction;
  }

  private double calculateMaximumThermodynamicMassFractionError(OnePhaseSpeciesConservationReport report) {
    double[][] massFraction = report.getMassFractionProfile();
    double maximum = 0.0;
    for (int component = 0; component < massFraction.length; component++) {
      for (int cell = 0; cell < massFraction[component].length; cell++) {
        maximum = Math.max(maximum,
            Math.abs(massFraction[component][cell] - componentMassFraction(cell + 1, component)));
      }
    }
    return maximum;
  }

  private double componentMassFraction(int node, int component) {
    return pipe.getNode(node).getBulkSystem().getPhase(0).getComponent(component).getx()
        * pipe.getNode(node).getBulkSystem().getPhase(0).getComponent(component).getMolarMass()
        / pipe.getNode(node).getBulkSystem().getPhase(0).getMolarMass();
  }

  private void handleSpeciesFailure(OnePhaseSpeciesConservationReport report) {
    throw new IllegalStateException(report.getMessage());
  }

  private void handleHydraulicFailure(OnePhaseFlowConvergenceReport report) {
    if (failOnNonConvergence) {
      throw new IllegalStateException(report.getMessage());
    }
    logger.warn("{}", report.getMessage());
  }

  private void solveCoupledHydraulicEos(double initialFiniteVolumeMass) {
    ensureSupportedCoupledFlowDirection();
    double[] state = getCoupledState();
    initializeCoupledEquationScales(state);
    double[] residualValues = calculateCoupledResidual(state);
    double residual = maximumAbsolute(residualValues);
    double[] nonlinearHistory = new double[MAXIMUM_COUPLED_ITERATIONS + 1];
    double[] densityHistory = new double[MAXIMUM_COUPLED_ITERATIONS + 1];
    double[] massEquationHistory = new double[MAXIMUM_COUPLED_ITERATIONS + 1];
    double[] momentumEquationHistory = new double[MAXIMUM_COUPLED_ITERATIONS + 1];
    int iteration = 0;
    boolean lineSearchFailed = false;
    String numericalFailureDetail = null;

    nonlinearHistory[0] = residual;
    densityHistory[0] = calculateMaximumRelativeDensityResidual();
    massEquationHistory[0] = maximumAbsoluteByEquationFamily(residualValues, 0);
    momentumEquationHistory[0] = maximumAbsoluteByEquationFamily(residualValues, 1);
    while (residual > NONLINEAR_RESIDUAL_TOLERANCE && iteration < MAXIMUM_COUPLED_ITERATIONS) {
      double[] update;
      double[][] jacobian;
      try {
        double[] values = iteration == 0 ? residualValues : calculateCoupledResidual(state);
        residualValues = values;
        residual = maximumAbsolute(values);
        nonlinearHistory[iteration] = residual;
        massEquationHistory[iteration] = maximumAbsoluteByEquationFamily(values, 0);
        momentumEquationHistory[iteration] = maximumAbsoluteByEquationFamily(values, 1);
        jacobian = calculateCoupledBandedJacobian(state);
        double[] rightHandSide = new double[values.length];
        for (int row = 0; row < values.length; row++) {
          rightHandSide[row] = -values[row];
        }
        update = BandedLinearSystemSolver.solve(jacobian, COUPLED_HALF_BANDWIDTH, COUPLED_HALF_BANDWIDTH,
            rightHandSide);
      } catch (RuntimeException exception) {
        applyCoupledState(state);
        numericalFailureDetail = exception.getClass().getSimpleName()
            + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
        break;
      }
      double step = 1.0;
      boolean accepted = false;
      while (step >= 1.0 / 65536.0) {
        double[] candidate = state.clone();
        for (int variable = 0; variable < candidate.length; variable++) {
          candidate[variable] += step * update[variable];
        }
        double[] candidateValues = safeCoupledResidualValues(candidate, state);
        double candidateResidual = candidateValues == null ? Double.POSITIVE_INFINITY
            : maximumAbsolute(candidateValues);
        if (Double.isFinite(candidateResidual) && candidateResidual < residual) {
          state = candidate;
          residualValues = candidateValues;
          residual = candidateResidual;
          accepted = true;
          break;
        }
        step *= 0.5;
      }
      iteration++;
      nonlinearHistory[iteration] = residual;
      massEquationHistory[iteration] = maximumAbsoluteByEquationFamily(residualValues, 0);
      momentumEquationHistory[iteration] = maximumAbsoluteByEquationFamily(residualValues, 1);
      applyCoupledState(state);
      densityHistory[iteration] = calculateMaximumRelativeDensityResidual();
      if (!accepted) {
        lineSearchFailed = true;
        numericalFailureDetail = calculateJacobianDirectionalDerivativeDetail(state, update, jacobian);
        break;
      }
    }

    applyCoupledState(state);
    double densityResidual = calculateMaximumRelativeDensityResidual();
    lastConvergenceReport = createConvergenceReport(iteration, residual, densityResidual, initialFiniteVolumeMass,
        Arrays.copyOf(nonlinearHistory, iteration + 1), Arrays.copyOf(densityHistory, iteration + 1),
        massEquationHistory[iteration], momentumEquationHistory[iteration],
        Arrays.copyOf(massEquationHistory, iteration + 1), Arrays.copyOf(momentumEquationHistory, iteration + 1),
        lineSearchFailed, numericalFailureDetail, true);
    if (!lastConvergenceReport.isConverged()) {
      if (conservativeSpeciesTransportEnabled || (dynamic && failOnNonConvergence)) {
        throw new IllegalStateException(lastConvergenceReport.getMessage());
      }
      if (dynamic) {
        logger.warn("{}", lastConvergenceReport.getMessage());
      }
    }
  }

  private void ensureSupportedCoupledFlowDirection() {
    for (int node = 1; node < numberOfNodes - 1; node++) {
      if (!Double.isFinite(pipe.getNode(node).getVelocityIn().doubleValue())
          || !Double.isFinite(pipe.getNode(node).getVelocityOut().doubleValue())
          || pipe.getNode(node).getVelocityIn().doubleValue() <= 0.0
          || pipe.getNode(node).getVelocityOut().doubleValue() <= 0.0) {
        throw new IllegalStateException(
            "The coupled one-phase hydraulic/EOS solver currently supports positive flow only; "
                + "reversed-flow boundary equations are not yet validated.");
      }
    }
  }

  private double[] safeCoupledResidualValues(double[] candidate, double[] rollbackState) {
    for (int node = 1; node < numberOfNodes - 1; node++) {
      int pressureVariable = 2 * (node - 1);
      if (!Double.isFinite(candidate[pressureVariable]) || candidate[pressureVariable] <= 0.0) {
        return null;
      }
    }
    for (int velocityVariable = 1; velocityVariable < candidate.length; velocityVariable += 2) {
      if (!Double.isFinite(candidate[velocityVariable]) || candidate[velocityVariable] <= 0.0) {
        return null;
      }
    }
    try {
      return calculateCoupledResidual(candidate);
    } catch (RuntimeException exception) {
      applyCoupledState(rollbackState);
      return null;
    }
  }

  private double[][] calculateCoupledBandedJacobian(double[] state) {
    try {
      int size = state.length;
      double[][] jacobian = new double[size][2 * COUPLED_HALF_BANDWIDTH + 1];
      double[] steps = new double[size];
      for (int variable = 0; variable < size; variable++) {
        steps[variable] = FINITE_DIFFERENCE_RELATIVE_STEP * Math.max(Math.abs(state[variable]), 1.0);
      }

      for (int color = 0; color < COUPLED_JACOBIAN_COLORS; color++) {
        double[] lower = state.clone();
        double[] upper = state.clone();
        for (int variable = color; variable < size; variable += COUPLED_JACOBIAN_COLORS) {
          lower[variable] -= steps[variable];
          upper[variable] += steps[variable];
        }
        double[] lowerResidual = calculateCoupledResidual(lower);
        double[] upperResidual = calculateCoupledResidual(upper);
        for (int row = 0; row < size; row++) {
          int firstColumn = Math.max(0, row - COUPLED_HALF_BANDWIDTH);
          int lastColumn = Math.min(size - 1, row + COUPLED_HALF_BANDWIDTH);
          for (int column = firstColumn; column <= lastColumn; column++) {
            if (column % COUPLED_JACOBIAN_COLORS == color) {
              setCoupledJacobianEntry(jacobian, row, column,
                  (upperResidual[row] - lowerResidual[row]) / (2.0 * steps[column]));
            }
          }
        }
      }

      applyCoupledState(state);
      setMassConservationMatrixTDMA();
      for (int node = 1; node < numberOfNodes - 1; node++) {
        int massRow = 2 * (node - 1);
        if (node > 1) {
          setCoupledJacobianEntry(jacobian, massRow, massRow - 2, a[node]
              / pipe.getNode(node - 1).getBulkSystem().getPhase(0).getdPdrho() / coupledMassEquationScale[node]);
        }
        setCoupledJacobianEntry(jacobian, massRow, massRow,
            b[node] / pipe.getNode(node).getBulkSystem().getPhase(0).getdPdrho() / coupledMassEquationScale[node]);
      }
      return jacobian;
    } finally {
      applyCoupledState(state);
    }
  }

  private void setCoupledJacobianEntry(double[][] jacobian, int row, int column, double value) {
    jacobian[row][column - row + COUPLED_HALF_BANDWIDTH] = value;
  }

  private String calculateJacobianDirectionalDerivativeDetail(double[] state, double[] direction, double[][] jacobian) {
    double maximumRelativeDirection = 0.0;
    for (int variable = 0; variable < state.length; variable++) {
      double scale = Math.max(Math.abs(state[variable]), 1.0);
      maximumRelativeDirection = Math.max(maximumRelativeDirection, Math.abs(direction[variable]) / scale);
    }
    if (!Double.isFinite(maximumRelativeDirection) || maximumRelativeDirection == 0.0) {
      return "Independent Jacobian directional-derivative check unavailable because the Newton direction is "
          + "zero or non-finite";
    }

    double[] normalizedPerturbations = { 1.0e-5, 1.0e-6, 1.0e-7 };
    double[] massRelativeErrors = new double[normalizedPerturbations.length];
    double[] momentumRelativeErrors = new double[normalizedPerturbations.length];
    double[] jacobianDirection = multiplyCoupledBandedJacobian(jacobian, direction);
    try {
      for (int perturbation = 0; perturbation < normalizedPerturbations.length; perturbation++) {
        double perturbationScale = normalizedPerturbations[perturbation] / maximumRelativeDirection;
        double[] lower = state.clone();
        double[] upper = state.clone();
        for (int variable = 0; variable < state.length; variable++) {
          lower[variable] -= perturbationScale * direction[variable];
          upper[variable] += perturbationScale * direction[variable];
        }

        double[] lowerResidual = safeCoupledResidualValues(lower, state);
        double[] upperResidual = safeCoupledResidualValues(upper, state);
        if (lowerResidual == null || upperResidual == null) {
          return "Independent Jacobian directional-derivative check unavailable because central perturbation "
              + normalizedPerturbations[perturbation] + " left the supported positive-pressure/positive-flow state";
        }

        double[] maximumDifference = new double[2];
        double[] maximumReference = new double[2];
        for (int row = 0; row < state.length; row++) {
          int family = row % 2;
          double finiteDifference = (upperResidual[row] - lowerResidual[row]) / (2.0 * perturbationScale);
          maximumDifference[family] = Math.max(maximumDifference[family],
              Math.abs(jacobianDirection[row] - finiteDifference));
          maximumReference[family] = Math.max(maximumReference[family],
              Math.max(Math.abs(jacobianDirection[row]), Math.abs(finiteDifference)));
        }
        massRelativeErrors[perturbation] = maximumDifference[0] / Math.max(maximumReference[0], 1.0e-14);
        momentumRelativeErrors[perturbation] = maximumDifference[1] / Math.max(maximumReference[1], 1.0e-14);
      }
      return "Independent Newton-direction Jacobian relative infinity-norm errors: continuity[1.0E-5="
          + massRelativeErrors[0] + ", 1.0E-6=" + massRelativeErrors[1] + ", 1.0E-7=" + massRelativeErrors[2]
          + "], momentum[1.0E-5=" + momentumRelativeErrors[0] + ", 1.0E-6=" + momentumRelativeErrors[1] + ", 1.0E-7="
          + momentumRelativeErrors[2] + "]; " + calculateDenseJacobianStructureDetail(state, jacobian);
    } catch (RuntimeException exception) {
      return "Independent Jacobian directional-derivative check failed with " + exception.getClass().getSimpleName()
          + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
    } finally {
      applyCoupledState(state);
    }
  }

  private double[] multiplyCoupledBandedJacobian(double[][] jacobian, double[] vector) {
    double[] product = new double[vector.length];
    for (int row = 0; row < vector.length; row++) {
      int firstColumn = Math.max(0, row - COUPLED_HALF_BANDWIDTH);
      int lastColumn = Math.min(vector.length - 1, row + COUPLED_HALF_BANDWIDTH);
      for (int column = firstColumn; column <= lastColumn; column++) {
        product[row] += jacobian[row][column - row + COUPLED_HALF_BANDWIDTH] * vector[column];
      }
    }
    return product;
  }

  private String calculateDenseJacobianStructureDetail(double[] state, double[][] bandedJacobian) {
    if (state.length > MAXIMUM_DENSE_JACOBIAN_DIAGNOSTIC_SIZE) {
      return "dense/uncolored Jacobian check skipped because state dimension " + state.length + " exceeds "
          + MAXIMUM_DENSE_JACOBIAN_DIAGNOSTIC_SIZE;
    }
    try {
      double[] firstResidual = calculateCoupledResidual(state);
      double[][] firstNodeState = captureCoupledNodeDiagnosticState();
      double[] repeatedResidual = calculateCoupledResidual(state);
      double[][] repeatedNodeState = captureCoupledNodeDiagnosticState();
      double[] repeatedDifference = new double[2];
      double[] repeatedReference = new double[2];
      for (int row = 0; row < state.length; row++) {
        int family = row % 2;
        repeatedDifference[family] = Math.max(repeatedDifference[family],
            Math.abs(repeatedResidual[row] - firstResidual[row]));
        repeatedReference[family] = Math.max(repeatedReference[family],
            Math.max(Math.abs(repeatedResidual[row]), Math.abs(firstResidual[row])));
      }

      double[][] denseJacobian = calculateCoupledDenseFiniteDifferenceJacobian(state);
      double[] inBandDifference = new double[2];
      double[] denseReference = new double[2];
      double[] offBandMaximum = new double[2];
      for (int row = 0; row < state.length; row++) {
        int family = row % 2;
        for (int column = 0; column < state.length; column++) {
          double denseValue = denseJacobian[row][column];
          denseReference[family] = Math.max(denseReference[family], Math.abs(denseValue));
          if (Math.abs(column - row) <= COUPLED_HALF_BANDWIDTH) {
            double bandedValue = bandedJacobian[row][column - row + COUPLED_HALF_BANDWIDTH];
            inBandDifference[family] = Math.max(inBandDifference[family], Math.abs(bandedValue - denseValue));
          } else {
            offBandMaximum[family] = Math.max(offBandMaximum[family], Math.abs(denseValue));
          }
        }
      }

      return "dense/uncolored check at relative step " + FINITE_DIFFERENCE_RELATIVE_STEP
          + ": repeated-residual relative errors continuity="
          + repeatedDifference[0] / Math.max(repeatedReference[0], 1.0e-14) + ", momentum="
          + repeatedDifference[1] / Math.max(repeatedReference[1], 1.0e-14)
          + "; colored-versus-dense in-band relative errors continuity="
          + inBandDifference[0] / Math.max(denseReference[0], 1.0e-14) + ", momentum="
          + inBandDifference[1] / Math.max(denseReference[1], 1.0e-14)
          + "; maximum off-band relative magnitudes continuity="
          + offBandMaximum[0] / Math.max(denseReference[0], 1.0e-14) + ", momentum="
          + offBandMaximum[1] / Math.max(denseReference[1], 1.0e-14) + "; repeated node-state relative drifts "
          + calculateRepeatedNodeStateDetail(firstNodeState, repeatedNodeState);
    } catch (RuntimeException exception) {
      return "dense/uncolored Jacobian check failed with " + exception.getClass().getSimpleName()
          + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
    } finally {
      applyCoupledState(state);
    }
  }

  private double[][] captureCoupledNodeDiagnosticState() {
    int numberOfComponents = pipe.getNode(0).getBulkSystem().getPhase(0).getNumberOfComponents();
    double[][] nodeState = new double[numberOfNodes][8 + numberOfComponents];
    for (int node = 0; node < numberOfNodes; node++) {
      nodeState[node][0] = pipe.getNode(node).getBulkSystem().getPhase(0).getNumberOfMolesInPhase();
      nodeState[node][1] = pipe.getNode(node).getBulkSystem().getPhase(0).getDensity();
      nodeState[node][2] = pipe.getNode(node).getVelocityIn().doubleValue();
      nodeState[node][3] = pipe.getNode(node).getVelocity();
      nodeState[node][4] = pipe.getNode(node).getMassFlowRate(0);
      nodeState[node][5] = pipe.getNode(node).getVolumetricFlow();
      nodeState[node][6] = pipe.getNode(node).getReynoldsNumber();
      nodeState[node][7] = pipe.getNode(node).getWallFrictionFactor();
      for (int component = 0; component < numberOfComponents; component++) {
        nodeState[node][8 + component] = pipe.getNode(node).getBulkSystem().getPhase(0).getComponent(component)
            .getNumberOfMolesInPhase();
      }
    }
    return nodeState;
  }

  private String calculateRepeatedNodeStateDetail(double[][] first, double[][] repeated) {
    String[] labels = { "phaseMoles", "density", "velocityIn", "meanVelocity", "massFlow", "volumetricFlow", "Reynolds",
        "frictionFactor", "componentMoles" };
    double[] maximumRelativeDrift = new double[labels.length];
    int[] maximumDriftNode = new int[labels.length];
    for (int node = 0; node < first.length; node++) {
      for (int field = 0; field < first[node].length; field++) {
        int category = Math.min(field, labels.length - 1);
        double reference = Math.max(Math.max(Math.abs(first[node][field]), Math.abs(repeated[node][field])), 1.0e-14);
        double relativeDrift = Math.abs(repeated[node][field] - first[node][field]) / reference;
        if (!Double.isFinite(relativeDrift)) {
          relativeDrift = Double.POSITIVE_INFINITY;
        }
        if (relativeDrift > maximumRelativeDrift[category]) {
          maximumRelativeDrift[category] = relativeDrift;
          maximumDriftNode[category] = node;
        }
      }
    }

    StringBuilder detail = new StringBuilder();
    for (int category = 0; category < labels.length; category++) {
      if (category > 0) {
        detail.append(", ");
      }
      detail.append(labels[category]).append('=').append(maximumRelativeDrift[category]).append("@node")
          .append(maximumDriftNode[category]);
    }
    return detail.toString();
  }

  private double[][] calculateCoupledDenseFiniteDifferenceJacobian(double[] state) {
    double[][] denseJacobian = new double[state.length][state.length];
    try {
      for (int column = 0; column < state.length; column++) {
        double step = FINITE_DIFFERENCE_RELATIVE_STEP * Math.max(Math.abs(state[column]), 1.0);
        double[] lower = state.clone();
        double[] upper = state.clone();
        lower[column] -= step;
        upper[column] += step;
        double[] lowerResidual = calculateCoupledResidual(lower);
        double[] upperResidual = calculateCoupledResidual(upper);
        for (int row = 0; row < state.length; row++) {
          denseJacobian[row][column] = (upperResidual[row] - lowerResidual[row]) / (2.0 * step);
        }
      }
      return denseJacobian;
    } finally {
      applyCoupledState(state);
    }
  }

  private double[] calculateCoupledResidual(double[] state) {
    applyCoupledState(state);
    int physicalCells = numberOfNodes - 2;
    double[] values = new double[2 * physicalCells];
    setMassConservationMatrixTDMA();
    for (int node = 1; node < numberOfNodes - 1; node++) {
      double value = a[node] * sol2Matrix.get(node - 1, 0) + b[node] * sol2Matrix.get(node, 0) - r[node];
      value += c[node] * sol2Matrix.get(node + 1, 0);
      values[2 * (node - 1)] = value / coupledMassEquationScale[node];
    }
    setImpulsMatrixTDMA();
    for (int node = 2; node < numberOfNodes; node++) {
      double value = a[node] * solMatrix.get(node - 1, 0) + b[node] * solMatrix.get(node, 0) - r[node];
      if (node < numberOfNodes - 1) {
        value += c[node] * solMatrix.get(node + 1, 0);
      }
      values[2 * (node - 2) + 1] = value / coupledMomentumEquationScale[node];
    }
    return values;
  }

  private double[] getCoupledState() {
    double[] state = new double[2 * (numberOfNodes - 2)];
    for (int node = 1; node < numberOfNodes - 1; node++) {
      int cell = node - 1;
      state[2 * cell] = pipe.getNode(node).getBulkSystem().getPressure();
      state[2 * cell + 1] = pipe.getNode(node + 1).getVelocityIn().doubleValue();
    }
    return state;
  }

  private void initializeCoupledEquationScales(double[] state) {
    applyCoupledState(state);
    coupledMassEquationScale = new double[numberOfNodes];
    coupledMomentumEquationScale = new double[numberOfNodes];
    setMassConservationMatrixTDMA();
    for (int node = 1; node < numberOfNodes - 1; node++) {
      double termScale = Math.abs(r[node]) + Math.abs(a[node] * sol2Matrix.get(node - 1, 0))
          + Math.abs(b[node] * sol2Matrix.get(node, 0));
      termScale += Math.abs(c[node] * sol2Matrix.get(node + 1, 0));
      coupledMassEquationScale[node] = Math.max(termScale, 1.0);
    }
    setImpulsMatrixTDMA();
    for (int node = 2; node < numberOfNodes; node++) {
      double termScale = Math.abs(r[node]) + Math.abs(a[node] * solMatrix.get(node - 1, 0))
          + Math.abs(b[node] * solMatrix.get(node, 0));
      if (node < numberOfNodes - 1) {
        termScale += Math.abs(c[node] * solMatrix.get(node + 1, 0));
      }
      coupledMomentumEquationScale[node] = Math.max(termScale, 1.0);
    }
  }

  private void applyCoupledState(double[] state) {
    for (int node = 1; node < numberOfNodes - 1; node++) {
      pipe.getNode(node).getBulkSystem().setPressure(state[2 * (node - 1)]);
      pipe.getNode(node).init();
    }
    for (int node = 2; node < numberOfNodes; node++) {
      pipe.getNode(node).setVelocityIn(state[2 * (node - 2) + 1]);
    }
    for (int node = 0; node < numberOfNodes; node++) {
      solMatrix.set(node, 0, pipe.getNode(node).getVelocityIn().doubleValue());
    }
    initVelocity(1);
    for (int node = 0; node < numberOfNodes; node++) {
      solMatrix.set(node, 0, pipe.getNode(node).getVelocityIn().doubleValue());
      sol2Matrix.set(node, 0, pipe.getNode(node).getBulkSystem().getPhase(0).getDensity());
    }
  }

  private double maximumAbsolute(double[] values) {
    double maximum = 0.0;
    for (double value : values) {
      maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum;
  }

  private double maximumAbsoluteByEquationFamily(double[] values, int parity) {
    double maximum = 0.0;
    for (int row = parity; row < values.length; row += 2) {
      maximum = Math.max(maximum, Math.abs(values[row]));
    }
    return maximum;
  }

  private boolean hasConverged(double nonlinearUpdate, double densityResidual) {
    if (!Double.isFinite(nonlinearUpdate) || !Double.isFinite(densityResidual)) {
      return false;
    }
    if (Math.abs(nonlinearUpdate) > NONLINEAR_RESIDUAL_TOLERANCE) {
      return false;
    }
    return !dynamic || solverType <= 0 || densityResidual <= DENSITY_RELATIVE_TOLERANCE;
  }

  private double calculateInitialFiniteVolumeMass() {
    if (!dynamic || oldDensity == null) {
      return Double.NaN;
    }
    double mass = 0.0;
    for (int i = 1; i < getAccumulatingNodeLimit(); i++) {
      mass += oldDensity[i] * getControlVolume(i);
    }
    return mass;
  }

  private double calculateFiniteVolumeMass() {
    double mass = 0.0;
    for (int i = 1; i < getAccumulatingNodeLimit(); i++) {
      mass += sol2Matrix.get(i, 0) * getControlVolume(i);
    }
    return mass;
  }

  private double calculateThermodynamicMass() {
    double mass = 0.0;
    for (int i = 1; i < getAccumulatingNodeLimit(); i++) {
      mass += pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * getControlVolume(i);
    }
    return mass;
  }

  private double getControlVolume(int node) {
    return pipe.getNode(node).getGeometry().getArea() * pipe.getNode(node).getGeometry().getNodeLength();
  }

  private int getAccumulatingNodeLimit() {
    return solverType == 1 ? numberOfNodes - 1 : numberOfNodes;
  }

  private double calculateMaximumRelativeDensityResidual() {
    if (solverType <= 0) {
      return 0.0;
    }
    double maximum = 0.0;
    for (int i = 1; i < getAccumulatingNodeLimit(); i++) {
      double finiteVolumeDensity = sol2Matrix.get(i, 0);
      double thermodynamicDensity = pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
      double scale = Math.max(Math.max(Math.abs(finiteVolumeDensity), Math.abs(thermodynamicDensity)), 1.0e-30);
      maximum = Math.max(maximum, Math.abs(finiteVolumeDensity - thermodynamicDensity) / scale);
    }
    return maximum;
  }

  private double calculateInletBoundaryMass() {
    if (!dynamic) {
      return Double.NaN;
    }

    double inletVelocity = pipe.getNode(1).getVelocityIn().doubleValue();
    if (inletVelocity < 0.0) {
      return Double.NaN;
    }

    double inletMassFlow = inletVelocity * pipe.getNode(0).getGeometry().getArea() * sol2Matrix.get(0, 0);
    return timeStep * inletMassFlow;
  }

  private double calculateOutletBoundaryMass() {
    if (!dynamic) {
      return Double.NaN;
    }

    double outletVelocity = solverType == 1 ? pipe.getNode(numberOfNodes - 1).getVelocityIn().doubleValue()
        : pipe.getNode(numberOfNodes - 1).getVelocity();
    if (outletVelocity < 0.0) {
      return Double.NaN;
    }

    // For solver type 1, node N - 2 owns the east-face area and density in the last
    // authoritative finite-volume mass row. The boundary node only prescribes pressure.
    int densityNode = solverType == 1 ? numberOfNodes - 2 : numberOfNodes - 1;
    double outletMassFlow = outletVelocity * pipe.getNode(densityNode).getGeometry().getArea()
        * sol2Matrix.get(densityNode, 0);
    return timeStep * outletMassFlow;
  }

  private OnePhaseFlowConvergenceReport createConvergenceReport(int nonlinearIterations, double nonlinearUpdate,
      double densityResidual, double initialFiniteVolumeMass, double[] nonlinearHistory, double[] densityHistory,
      boolean lineSearchFailed, String numericalFailureDetail, boolean nonlinearMetricEquationResidual) {
    return createConvergenceReport(nonlinearIterations, nonlinearUpdate, densityResidual, initialFiniteVolumeMass,
        nonlinearHistory, densityHistory, Double.NaN, Double.NaN, new double[0], new double[0], lineSearchFailed,
        numericalFailureDetail, nonlinearMetricEquationResidual);
  }

  private OnePhaseFlowConvergenceReport createConvergenceReport(int nonlinearIterations, double nonlinearUpdate,
      double densityResidual, double initialFiniteVolumeMass, double[] nonlinearHistory, double[] densityHistory,
      double maximumScaledMassEquationResidual, double maximumScaledMomentumEquationResidual,
      double[] scaledMassEquationResidualHistory, double[] scaledMomentumEquationResidualHistory,
      boolean lineSearchFailed, String numericalFailureDetail, boolean nonlinearMetricEquationResidual) {
    double finalFiniteVolumeMass = dynamic ? calculateFiniteVolumeMass() : Double.NaN;
    double finalThermodynamicMass = dynamic ? calculateThermodynamicMass() : Double.NaN;
    double inletBoundaryMass = calculateInletBoundaryMass();
    double outletBoundaryMass = calculateOutletBoundaryMass();
    double netBoundaryMass = inletBoundaryMass - outletBoundaryMass;
    double finiteVolumeMassResidual = dynamic ? finalFiniteVolumeMass - initialFiniteVolumeMass - netBoundaryMass
        : Double.NaN;
    double thermodynamicMassResidual = dynamic ? finalThermodynamicMass - initialFiniteVolumeMass - netBoundaryMass
        : Double.NaN;
    double massScale = dynamic ? Math.max(Math.max(Math.abs(initialFiniteVolumeMass), Math.abs(netBoundaryMass)), 1.0)
        : Double.NaN;
    double relativeFiniteVolumeMassResidual = dynamic ? Math.abs(finiteVolumeMassResidual) / massScale : Double.NaN;
    double relativeThermodynamicMassResidual = dynamic ? Math.abs(thermodynamicMassResidual) / massScale : Double.NaN;

    OnePhaseFlowConvergenceReport.ConvergenceReason reason;
    if (lineSearchFailed) {
      reason = OnePhaseFlowConvergenceReport.ConvergenceReason.LINE_SEARCH_FAILED;
    } else if (numericalFailureDetail != null) {
      reason = OnePhaseFlowConvergenceReport.ConvergenceReason.NUMERICAL_FAILURE;
    } else if (!diagnosticsAreFinite(nonlinearUpdate, densityResidual, relativeFiniteVolumeMassResidual,
        relativeThermodynamicMassResidual)) {
      reason = OnePhaseFlowConvergenceReport.ConvergenceReason.NON_FINITE_RESIDUAL;
    } else if (dynamic && solverType > 0 && densityResidual > DENSITY_RELATIVE_TOLERANCE) {
      reason = OnePhaseFlowConvergenceReport.ConvergenceReason.DENSITY_INCONSISTENT;
    } else if (dynamic && solverType > 0 && (relativeFiniteVolumeMassResidual > MASS_BALANCE_RELATIVE_TOLERANCE
        || relativeThermodynamicMassResidual > MASS_BALANCE_RELATIVE_TOLERANCE)) {
      reason = OnePhaseFlowConvergenceReport.ConvergenceReason.MASS_BALANCE_FAILED;
    } else if (!hasConverged(nonlinearUpdate, densityResidual)) {
      reason = OnePhaseFlowConvergenceReport.ConvergenceReason.MAX_ITERATIONS_REACHED;
    } else {
      reason = OnePhaseFlowConvergenceReport.ConvergenceReason.CONVERGED;
    }

    String nonlinearMetricLabel = nonlinearMetricEquationResidual ? "scaled equation residual="
        : "relative nonlinear update=";
    String message = "One-phase pipe solve " + reason + " after " + nonlinearIterations + " nonlinear iterations: "
        + nonlinearMetricLabel + Math.abs(nonlinearUpdate) + " (tolerance " + NONLINEAR_RESIDUAL_TOLERANCE
        + "), EOS/FV density=" + densityResidual + " (tolerance " + DENSITY_RELATIVE_TOLERANCE + "), FV mass residual="
        + finiteVolumeMassResidual + " kg, EOS mass residual=" + thermodynamicMassResidual + " kg (relative tolerance "
        + MASS_BALANCE_RELATIVE_TOLERANCE + ").";
    if (nonlinearMetricEquationResidual) {
      message += " Final scaled continuity residual=" + maximumScaledMassEquationResidual + ", momentum residual="
          + maximumScaledMomentumEquationResidual + ".";
    }
    if (numericalFailureDetail != null) {
      String detailLabel = lineSearchFailed ? " Line-search diagnostic: " : " Numerical failure: ";
      message += detailLabel + numericalFailureDetail + ".";
    }

    return new OnePhaseFlowConvergenceReport(reason, dynamic, solverType, nonlinearIterations,
        NONLINEAR_RESIDUAL_TOLERANCE, DENSITY_RELATIVE_TOLERANCE, MASS_BALANCE_RELATIVE_TOLERANCE,
        Math.abs(nonlinearUpdate), densityResidual, initialFiniteVolumeMass, finalFiniteVolumeMass,
        finalThermodynamicMass, inletBoundaryMass, outletBoundaryMass, netBoundaryMass, finiteVolumeMassResidual,
        thermodynamicMassResidual, relativeFiniteVolumeMassResidual, relativeThermodynamicMassResidual,
        nonlinearHistory, densityHistory, maximumScaledMassEquationResidual, maximumScaledMomentumEquationResidual,
        scaledMassEquationResidualHistory, scaledMomentumEquationResidualHistory, message,
        nonlinearMetricEquationResidual);
  }

  private boolean diagnosticsAreFinite(double nonlinearUpdate, double densityResidual,
      double relativeFiniteVolumeMassResidual, double relativeThermodynamicMassResidual) {
    if (!Double.isFinite(nonlinearUpdate) || !Double.isFinite(densityResidual)) {
      return false;
    }
    if (!dynamic) {
      return true;
    }
    return Double.isFinite(relativeFiniteVolumeMassResidual) && Double.isFinite(relativeThermodynamicMassResidual);
  }
}
