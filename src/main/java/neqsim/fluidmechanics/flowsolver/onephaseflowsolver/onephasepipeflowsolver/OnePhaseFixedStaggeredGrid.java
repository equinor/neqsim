/*
 * OnePhaseFixedStaggeredGrid.java
 *
 * Created on 17. januar 2001, 21:10
 */

package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.mathlib.generalmath.TDMAsolve;

/**
 * <p>
 * OnePhaseFixedStaggeredGrid class.
 * </p>
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

  /**
   * <p>
   * Constructor for OnePhaseFixedStaggeredGrid.
   * </p>
   */
  public OnePhaseFixedStaggeredGrid() {}

  /**
   * <p>
   * Constructor for OnePhaseFixedStaggeredGrid.
   * </p>
   *
   * @param pipe a
   *        {@link neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem}
   *        object
   * @param length a double
   * @param nodes a int
   * @param dynamic a boolean
   */
  public OnePhaseFixedStaggeredGrid(
      neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem pipe,
      double length, int nodes, boolean dynamic) {
    super(pipe, length, nodes);
    this.dynamic = dynamic;
    oldMass = new double[nodes];
    oldComp = new double[nodes];
    oldImpuls = new double[nodes];
    diff4Matrix =
        new Matrix[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    oldEnergy = new double[nodes];
    oldVelocity = new double[nodes];
    oldDensity = new double[nodes];
    oldInternalEnergy = new double[nodes];
    oldComposition =
        new double[pipe.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()][nodes];
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
   * <p>
   * initProfiles.
   * </p>
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
                    / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass()
                    * pipe.getNode(i + 1).getVelocity()
                    * pipe.getNode(i + 1).getGeometry().getDiameter()
                    * pipe.getNode(i + 1).getBulkSystem().getPhases()[0].getDensity())
                + pipe.getNode(i + 1).getBulkSystem().getPhases()[0].getJouleThomsonCoefficient()
                    * dpdx)
                * (pipe.getNode(i + 1).getGeometry().getNodeLength()
                    + pipe.getNode(i).getGeometry().getNodeLength())
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
                * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
                * pipe.getNode(i).getVelocity() * pipe.getNode(i).getVelocity()
                / pipe.getNode(i).getGeometry().getDiameter() / 2.0
                * (pipe.getNode(i).getGeometry().getNodeLength()) / 1e5
                - gravity * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
                    * (pipe.getNode(i + 1).getVerticalPositionOfNode()
                        - pipe.getNode(i).getVerticalPositionOfNode())
                    / 1e5
                + pipe.getNode(i).getBulkSystem().getPressure());
        // if(pipe.getNode(i+1).getBulkSystem().getPressure()<10.5)
        // pipe.getNode(i+1).getBulkSystem().setPressure(1.0);
        err += (oldPres - pipe.getNode(i + 1).getBulkSystem().getPressure());
        pipe.getNode(i + 1).initFlowCalc();
        pipe.getNode(i + 1).init();
        dpdx = (pipe.getNode(i + 1).getBulkSystem().getPressure()
            - pipe.getNode(i).getBulkSystem().getPressure())
            / ((pipe.getNode(i + 1).getGeometry().getNodeLength()
                + pipe.getNode(i).getGeometry().getNodeLength()) * 0.5);
        /*
         * System.out.println("pres : " + pipe.getNode(i + 1).getBulkSystem().getPressure());
         * System.out .println("temp : " + pipe.getNode(i + 1).getBulkSystem().getTemperature());
         * System.out.println("velocity : " + pipe.getNode(i + 1).getVelocity());
         * System.out.println("dpdx : " + dpdx); System.out .println("JT coeff : " + pipe.getNode(i
         * + 1).getBulkSystem().getPhases()[0] .getJouleThomsonCoefficient());
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
   * <p>
   * initMatrix.
   * </p>
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
      for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhases()[0]
          .getNumberOfComponents(); j++) {
        sol4Matrix[j].set(i, 0,
            pipe.getNode(i).getBulkSystem().getPhases()[0].getComponent(j).getx()
                * pipe.getNode(i).getBulkSystem().getPhases()[0].getComponent(j).getMolarMass()
                / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass());
      }
    }
  }

  /**
   * <p>
   * initPressure.
   * </p>
   *
   * @param iteration a int
   */
  public void initPressure(int iteration) {
    for (int i = 0; i < numberOfNodes; i++) {
      // if(dynamic) System.out.println(" old pressure " +
      // pipe.getNode(i).getBulkSystem().getPressure());

      pipe.getNode(i).getBulkSystem().setPressure(
          pipe.getNode(i).getBulkSystem().getPhases()[0].getdPdrho() * diffMatrix.get(i, 0) * 1e-5
              + pipe.getNode(i).getBulkSystem().getPressure());
      pipe.getNode(i).init();
      // if(dynamic) System.out.println("i " + i +" diff 0 " +(diffMatrix.get(i,
      // 0) )
      // + " new pressure " + pipe.getNode(i).getBulkSystem().getPressure());
    }
  }

  /**
   * <p>
   * initVelocity.
   * </p>
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
        meanVelocity = (pipe.getNode(i).getVelocityIn().doubleValue()
            + pipe.getNode(i).getVelocityOut().doubleValue()) / 2.0;
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
   * <p>
   * initTemperature.
   * </p>
   *
   * @param iteration a int
   */
  public void initTemperature(int iteration) {
    for (int i = 0; i < numberOfNodes; i++) {
      pipe.getNode(i).init();
      pipe.getNode(i).getBulkSystem()
          .setTemperature(pipe.getNode(i).getBulkSystem().getTemperature()
              + iteration * 1.0 / (10.0 + iteration) * diffMatrix.get(i, 0)
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
   * <p>
   * initComposition.
   * </p>
   *
   * @param iter a int
   */
  public void initComposition(int iter) {
    for (int j = 1; j < numberOfNodes; j++) {
      for (int p = 0; p < pipe.getNode(0).getBulkSystem().getPhases()[0]
          .getNumberOfComponents(); p++) {
        pipe.getNode(j).getBulkSystem().getPhases()[0].getComponent(p)
            .setx(sol4Matrix[p].get(j, 0)
                * pipe.getNode(j).getBulkSystem().getPhases()[0].getMolarMass()
                / pipe.getNode(j).getBulkSystem().getPhases()[0].getComponent(p).getMolarMass());
        // pipe.getNode(j).getBulkSystem().getPhases()[0].getComponent(p).getx() +
        // 0.5*diff4Matrix[p].get(j,0));
      }

      pipe.getNode(j).getBulkSystem().getPhases()[0].normalize();
      pipe.getNode(j).init();
    }
  }

  /**
   * <p>
   * setMassConservationMatrixTDMA.
   * </p>
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
      double Ae = pipe.getNode(1).getGeometry().getArea();
      double Aw = pipe.getNode(0).getGeometry().getArea();
      double Fw = pipe.getNode(0).getVelocityIn().doubleValue() * Aw;
      double Fe = oldVelocity[1] * Ae;
      // System.out.println("new- old : " +
      // (pipe.getNode(0).getVelocityIn().doubleValue() - oldVelocity[0]));
      oldMass[0] = 1.0 / timeStep * pipe.getNode(0).getGeometry().getArea()
          * pipe.getNode(0).getGeometry().getNodeLength();

      a[0] = Math.max(Fw, 0);
      c[0] = Math.max(-Fe, 0);
      b[0] = a[0] + c[0] + (Fe - Fw) + oldMass[0];
      r[0] = oldMass[0] * oldDensity[0];

      // setter ligningen paa rett form
      // a[0] = - a[0];
      // c[0] = -c[0];
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
    double Ae = pipe.getNode(i).getGeometry().getArea();
    double Aw = pipe.getNode(i - 1).getGeometry().getArea();

    double Fe = pipe.getNode(i).getVelocity() * Ae;
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

  /**
   * <p>
   * setImpulsMatrixTDMA.
   * </p>
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
      double meanVelocity =
          (pipe.getNode(i - 1).getVelocity() + pipe.getNode(i).getVelocity()) / 2.0;
      double vertposchange = pipe.getNode(i).getVerticalPositionOfNode()
          - pipe.getNode(i - 1).getVerticalPositionOfNode();
      double nodeLength = pipe.getNode(i - 1).getGeometry().getNodeLength();

      SU = -Amean
          * (pipe.getNode(i).getBulkSystem().getPressure()
              - pipe.getNode(i - 1).getBulkSystem().getPressure())
          * 1e5 - Amean * gravity * meanDensity * vertposchange
          + Amean * nodeLength * meanDensity * meanFrik / meanDiameter * meanVelocity
              * Math.abs(meanVelocity) / 2.0;
      SP = -Amean * nodeLength * meanDensity * meanFrik / meanDiameter * meanVelocity;
      Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()
          * (pipe.getNode(i - 1).getVelocityIn().doubleValue()
              + pipe.getNode(i - 1).getVelocityOut().doubleValue())
          / 2.0;
      Fe = Ae * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
          * (pipe.getNode(i).getVelocityIn().doubleValue()
              + pipe.getNode(i).getVelocityOut().doubleValue())
          / 2.0;

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

    SU = -Amean
        * (pipe.getNode(i).getBulkSystem().getPressure()
            - pipe.getNode(i - 1).getBulkSystem().getPressure())
        * 1e5 - Amean * gravity * meanDensity * vertposchange
        + Amean * nodeLength * meanDensity * meanFrik / meanDiameter * meanVelocity
            * Math.abs(meanVelocity) / 2.0;
    SP = -Amean * nodeLength * meanDensity * meanFrik / meanDiameter * meanVelocity;
    Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()
        * (pipe.getNode(i - 1).getVelocityIn().doubleValue()
            + pipe.getNode(i - 1).getVelocityOut().doubleValue())
        / 2.0;
    Fe = Ae * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
        * (pipe.getNode(i).getVelocityIn().doubleValue()
            + pipe.getNode(i).getVelocityOut().doubleValue())
        / 2.0;

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
   * <p>
   * setEnergyMatrixTDMA.
   * </p>
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
          / (pipe.getNode(i).getGeometry().getNodeLength()
              + pipe.getNode(i + 1).getGeometry().getNodeLength());
      double fw = pipe.getNode(i - 1).getGeometry().getNodeLength()
          / (pipe.getNode(i).getGeometry().getNodeLength()
              + pipe.getNode(i - 1).getGeometry().getNodeLength());
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();
      double vertposchange = (1 - fe)
          * (pipe.getNode(i + 1).getVerticalPositionOfNode()
              - pipe.getNode(i).getVerticalPositionOfNode())
          + (1 - fw) * (pipe.getNode(i).getVerticalPositionOfNode()
              - pipe.getNode(i - 1).getVerticalPositionOfNode());

      SU = -pipe.getNode(i).getGeometry().getArea() * gravity
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
          * pipe.getNode(i).getVelocity() * vertposchange
          + pipe.getNode(i).getGeometry().getArea() * 4.0
              * pipe.getNode(i).calcTotalHeatTransferCoefficient(0)
              * (pipe.getNode(i).getGeometry().getSurroundingEnvironment().getTemperature()
                  - pipe.getNode(i).getBulkSystem().getTemperature())
              / (pipe.getNode(i).getGeometry().getDiameter())
              * pipe.getNode(i).getGeometry().getNodeLength();
      double SP = 0;
      double Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()
          * pipe.getNode(i).getVelocityIn().doubleValue();
      double Fe = Ae * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
          * pipe.getNode(i).getVelocityOut().doubleValue();

      if (dynamic) {
        oldEnergy[i] =
            1.0 / timeStep * oldDensity[i] * pipe.getNode(i).getGeometry().getNodeLength()
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
        / (pipe.getNode(i).getGeometry().getNodeLength()
            + pipe.getNode(i - 1).getGeometry().getNodeLength());
    double Ae = pipe.getNode(i).getGeometry().getArea();
    // 1.0/((1.0-fe)/pipe.getNode(i).getGeometry().getArea() +
    // fe/pipe.getNode(i+1).getGeometry().getArea());

    double Aw = pipe.getNode(i - 1).getGeometry().getArea();
    // 1.0/((1.0-fw)/pipe.getNode(i).getGeometry().getArea() +
    // fw/pipe.getNode(i-1).getGeometry().getArea());

    double vertposchange = (1 - fw) * (pipe.getNode(i).getVerticalPositionOfNode()
        - pipe.getNode(i - 1).getVerticalPositionOfNode());

    SU = -pipe.getNode(i).getGeometry().getArea() * gravity
        * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
        * pipe.getNode(i).getVelocity() * vertposchange
        + pipe.getNode(i).getGeometry().getArea() * 4.0
            * pipe.getNode(i).calcTotalHeatTransferCoefficient(0)
            * (pipe.getNode(i).getGeometry().getSurroundingEnvironment().getTemperature()
                - pipe.getNode(i).getBulkSystem().getTemperature())
            / (pipe.getNode(i).getGeometry().getDiameter())
            * pipe.getNode(i).getGeometry().getNodeLength();
    double SP = 0;
    // -pipe.getNode(i).getGeometry().getArea() *
    // 4.0*12.0 /
    // (pipe.getNode(i).getGeometry().getDiameter())*pipe.getNode(i).getGeometry().getNodeLength();

    double Fw = Aw * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity()
        * pipe.getNode(i).getVelocityIn().doubleValue();
    double Fe = Ae * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity()
        * pipe.getNode(i).getVelocity();

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
   * <p>
   * setComponentConservationMatrix.
   * </p>
   *
   * @param componentNumber a int
   */
  public void setComponentConservationMatrix(int componentNumber) {
    double SU = 0;
    a[0] = 0;
    b[0] = 1.0;
    c[0] = 0;
    SU = pipe.getNode(0).getBulkSystem().getPhases()[0].getComponents()[componentNumber].getx()
        * pipe.getNode(0).getBulkSystem().getPhases()[0].getComponents()[componentNumber]
            .getMolarMass()
        / pipe.getNode(0).getBulkSystem().getPhases()[0].getMolarMass();
    r[0] = SU;

    for (int i = 1; i < numberOfNodes - 1; i++) {
      double Ae = pipe.getNode(i).getGeometry().getArea();
      double Aw = pipe.getNode(i - 1).getGeometry().getArea();

      double Fe = pipe.getNode(i).getVelocityOut().doubleValue()
          * pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity() * Ae;
      double Fw = pipe.getNode(i).getVelocityIn().doubleValue()
          * pipe.getNode(i - 1).getBulkSystem().getPhases()[0].getDensity() * Aw;
      // System.out.println("vel: " +
      // pipe.getNode(i).getVelocityOut(phase).doubleValue() + " fe " + Fe);
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

      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = numberOfNodes - 1;
    // double fw =
    // pipe.getNode(i-1).getGeometry().getNodeLength() /
    // (pipe.getNode(i).getGeometry().getNodeLength()+pipe.getNode(i-1).getGeometry().getNodeLength());
    double Ae = pipe.getNode(i).getGeometry().getArea();
    double Aw = pipe.getNode(i - 1).getGeometry().getArea();

    double Fe = pipe.getNode(i).getVelocity()
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
    // setter ligningen paa rett form
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /**
   * <p>
   * initFinalResults.
   * </p>
   */
  public void initFinalResults() {
    for (int i = 0; i < numberOfNodes; i++) {
      oldVelocity[i] = pipe.getNode(i).getVelocityIn().doubleValue();
      oldDensity[i] = pipe.getNode(i).getBulkSystem().getPhases()[0].getDensity();
      oldInternalEnergy[i] = pipe.getNode(i).getBulkSystem().getPhases()[0].getEnthalpy()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()
          / pipe.getNode(i).getBulkSystem().getPhases()[0].getMolarMass();

      for (int j = 0; j < pipe.getNode(i).getBulkSystem().getPhases()[0]
          .getNumberOfComponents(); j++) {
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
    double[] d;
    int iter = 0;
    int iterTop = 0;
    double maxDiff = 1.0;
    double diff = 0;
    xNew = new double[pipe.getNode(0).getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][numberOfNodes];
    if (!dynamic) {
      initProfiles();
    }
    initMatrix();

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
          for (int p = 0; p < pipe.getNode(0).getBulkSystem().getPhases()[0]
              .getNumberOfComponents(); p++) {
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

      // System.out.println("maxDiff " + maxDiff);
    } while (Math.abs(maxDiff) > 1e-10 && iterTop < 100); // diffMatrix.norm2()/sol2Matrix.norm2())>0.1);

    initFinalResults();
  }
}
