/*
 * FluidBoundarySolver.java
 *
 * Created on 8. august 2001, 14:51
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySolver;

import Jama.Matrix;
import neqsim.MathLib.generalMath.TDMAsolve;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.FluidBoundarySystemInterface;

/**
 * <p>
 * FluidBoundarySolver class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class FluidBoundarySolver implements FluidBoundarySolverInterface {
  FluidBoundarySystemInterface boundary;
  double[][] xNew;
  protected Matrix[] solMatrix;
  protected Matrix[] diffMatrix;
  protected double[] a;
  protected double[] b;
  protected double[] c;
  protected double[] r;
  boolean reactive = false;

  /**
   * <p>
   * Constructor for FluidBoundarySolver.
   * </p>
   */
  public FluidBoundarySolver() {}

  /**
   * <p>
   * Constructor for FluidBoundarySolver.
   * </p>
   *
   * @param boundary a {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.FluidBoundarySystemInterface} object
   */
  public FluidBoundarySolver(FluidBoundarySystemInterface boundary) {
    this.boundary = boundary;
    this.initProfiles();
    a = new double[boundary.getNumberOfNodes()];
    b = new double[boundary.getNumberOfNodes()];
    c = new double[boundary.getNumberOfNodes()];
    r = new double[boundary.getNumberOfNodes()];

    solMatrix =
        new Matrix[boundary.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    diffMatrix =
        new Matrix[boundary.getNode(0).getBulkSystem().getPhases()[0].getNumberOfComponents()];
    for (int k = 0; k < boundary.getNode(0).getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); k++) {
      diffMatrix[k] = new Matrix(a, 1).transpose();
      solMatrix[k] = new Matrix(a, 1).transpose();
    }
  }

  /**
   * <p>
   * Constructor for FluidBoundarySolver.
   * </p>
   *
   * @param boundary a {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.FluidBoundarySystemInterface} * object
   * @param reactive a boolean
   */
  public FluidBoundarySolver(FluidBoundarySystemInterface boundary, boolean reactive) {
    this(boundary);
    this.reactive = reactive;
  }

  /**
   * <p>
   * initProfiles.
   * </p>
   */
  public void initProfiles() {
    Matrix reacRates = new Matrix(1, 1);
    for (int i = 0; i < boundary.getNumberOfNodes() - 1; i++) {
      boundary.getNode(i).getBulkSystem().init(3);
      boundary.getNode(i).getBulkSystem().getPhases()[1].initPhysicalProperties();
      boundary.getNode(i).getBulkSystem().getPhases()[1].getPhysicalProperties()
          .calcEffectiveDiffusionCoefficients();
      // if(reactive) reacRates =
      // boundary.getNode(i).getBulkSystem().getChemicalReactionOperations().calcReacRates(1);

      for (int j = 0; j < boundary.getNode(0).getBulkSystem().getPhases()[0]
          .getNumberOfComponents(); j++) {
        double xbulk =
            boundary.getFluidBoundary().getBulkSystem().getPhases()[1].getComponents()[j].getx();
        double xinterphase =
            boundary.getFluidBoundary().getInterphaseSystem().getPhases()[1].getComponents()[j]
                .getx();
        double dx = xinterphase - xbulk;
        double last = boundary.getNode(i).getBulkSystem().getPhases()[1].getComponents()[j].getx();
        if (reactive) {
          boundary.getNode(i + 1).getBulkSystem().getPhases()[1].getComponents()[j].setx(last - dx
              - reacRates.get(j, 0)
                  / boundary.getNode(i).getBulkSystem().getPhases()[1].getPhysicalProperties()
                      .getEffectiveDiffusionCoefficient(j)
                  * Math.pow(boundary.getNodeLength(), 2.0));
        } else {
          boundary.getNode(i + 1).getBulkSystem().getPhases()[1].getComponents()[j]
              .setx(xinterphase - dx * ((double) (i + 1) / boundary.getNumberOfNodes()));
        }
        System.out.println("x comp " + reactive + "  "
            + boundary.getNode(i).getBulkSystem().getPhases()[1].getComponents()[j].getx());
      }
    }
  }

  /**
   * <p>
   * initMatrix.
   * </p>
   */
  public void initMatrix() {
    for (int j = 0; j < boundary.getNode(0).getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); j++) {
      // pipe.getNode(i).init();
      for (int i = 0; i < boundary.getNumberOfNodes(); i++) {
        solMatrix[j].set(i, 0,
            boundary.getNode(i).getBulkSystem().getPhases()[1].getComponents()[j].getx());
      }
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
    for (int j = 0; j < boundary.getNumberOfNodes(); j++) {
      for (int p = 0; p < boundary.getNode(0).getBulkSystem().getPhases()[0]
          .getNumberOfComponents(); p++) {
        boundary.getNode(j).getBulkSystem().getPhases()[1].getComponents()[p]
            .setx(solMatrix[p].get(j, 0));
      }
      boundary.getNode(j).getBulkSystem().getPhases()[0].normalize();
      boundary.getNode(j).getBulkSystem().init(3);
    }
  }

  /**
   * <p>
   * setComponentConservationMatrix.
   * </p>
   *
   * @param componentNumber a int
   */
  public void setComponentConservationMatrix(int componentNumber) {
    for (int i = 0; i < boundary.getNumberOfNodes(); i++) {
      boundary.getNode(i).getBulkSystem().getPhases()[1].initPhysicalProperties();
      boundary.getNode(i).getBulkSystem().getPhases()[1].getPhysicalProperties()
          .calcEffectiveDiffusionCoefficients();
      // if(reactive)
      // boundary.getNode(i).getBulkSystem().getChemicalReactionOperations().calcReacRates(1);
    }

    a[0] = 0.0;
    c[0] = 0.0;
    b[0] = 1.0; // boundary.getNode(0).getBulkSystem().getPhases()[1].getComponents()[componentNumber].getx();
    r[0] =
        boundary.getNode(0).getBulkSystem().getPhases()[1].getComponents()[componentNumber].getx();
    System.out.println("b0 :" + b[0]);
    // setter ligningen paa rett form
    a[0] = -a[0];
    c[0] = -c[0];

    for (int i = 1; i < boundary.getNumberOfNodes() - 1; i++) {
      double Dw = (boundary.getNode(i - 1).getBulkSystem().getPhases()[1].getPhysicalProperties()
          .getEffectiveDiffusionCoefficient(componentNumber)
          + boundary.getNode(i).getBulkSystem().getPhases()[1].getPhysicalProperties()
              .getEffectiveDiffusionCoefficient(componentNumber))
          / 2.0;
      double De = (boundary.getNode(i).getBulkSystem().getPhases()[1].getPhysicalProperties()
          .getEffectiveDiffusionCoefficient(componentNumber)
          + boundary.getNode(i + 1).getBulkSystem().getPhases()[1].getPhysicalProperties()
              .getEffectiveDiffusionCoefficient(componentNumber))
          / 2.0;

      a[i] = Dw / boundary.getNodeLength();
      c[i] = De / boundary.getNodeLength();
      b[i] = a[i] + c[i];
      r[i] = 0.0;

      // setter ligningen paa rett form
      a[i] = -a[i];
      c[i] = -c[i];
    }

    int i = boundary.getNumberOfNodes() - 1;
    a[i] = 0.0;
    c[i] = 0.0;
    b[i] = 1.0; // boundary.getNode(i).getBulkSystem().getPhases()[1].getComponents()[componentNumber].getx();
    r[i] =
        boundary.getNode(i).getBulkSystem().getPhases()[1].getComponents()[componentNumber].getx();
    System.out.println("bn :" + b[i]);
    // setter ligningen paa rett form
    a[i] = -a[i];
    c[i] = -c[i];
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarFlux(int componentNumber) {
    double temp = 1.0;
    if (reactive) {
      temp = 2.1;
    }
    return temp
        * boundary.getNode(0).getBulkSystem().getPhases()[1].getPhysicalProperties()
            .getEffectiveDiffusionCoefficient(componentNumber)
        * (boundary.getNode(0).getBulkSystem().getPhases()[1].getComponents()[componentNumber]
            .getx()
            - boundary.getNode(1).getBulkSystem().getPhases()[1].getComponents()[componentNumber]
                .getx())
        / boundary.getNodeLength();
  }

  /** {@inheritDoc} */
  @Override
  public void solve() {
    // double d[];

    // double maxDiffOld = 0;
    double diff = 0;
    xNew = new double[boundary.getNode(0).getBulkSystem().getPhases()[0]
        .getNumberOfComponents()][boundary.getNumberOfNodes()];

    initProfiles();
    initMatrix();
    initComposition(1);
    System.out
        .println(" vol " + boundary.getNode(2).getBulkSystem().getPhases()[0].getMolarVolume());

    int iter = 0;
    int iterTop = 0;
    double maxDiff = 0;
    do {
      // maxDiffOld = maxDiff;
      maxDiff = 0;
      iterTop++;
      iter = 0;

      do {
        iter++;
        for (int p = 0; p < boundary.getNode(0).getBulkSystem().getPhases()[1]
            .getNumberOfComponents(); p++) {
          setComponentConservationMatrix(p);
          Matrix solOld = solMatrix[p].copy();
          solOld.print(20, 20);
          xNew[p] = TDMAsolve.solve(a, b, c, r);
          solMatrix[p] = new Matrix(xNew[p], 1).transpose();
          solMatrix[p].print(20, 20);
          diffMatrix[p] = solMatrix[p].minus(solOld);
          diff = Math.abs(diffMatrix[p].norm1() / (solMatrix[p].norm1()));
          if (diff > maxDiff) {
            maxDiff = diff;
          }
        }
        // initComposition(iter);
        // solMatrix.print(10,10);
      } while (diff > 1e-15 && iter < 100);

      System.out.println("maxDiff " + maxDiff);
    } while (Math.abs(maxDiff) > 1e-10 && iterTop < 10);
    // diffMatrix.norm2()/sol2Matrix.norm2())>0.1);
  }
}
