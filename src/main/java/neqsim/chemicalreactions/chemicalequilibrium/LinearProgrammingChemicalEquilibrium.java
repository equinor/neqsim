/*
 * LinearProgrammingChemicalEquilibrium.java
 *
 * Created on 11. april 2001, 10:04
 */

package neqsim.chemicalreactions.chemicalequilibrium;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.LinearConstraintSet;
import org.apache.commons.math3.optim.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optim.linear.NoFeasibleSolutionException;
import org.apache.commons.math3.optim.linear.NonNegativeConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.apache.commons.math3.optim.linear.SimplexSolver;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * LinearProgrammingChemicalEquilibrium class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LinearProgrammingChemicalEquilibrium
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LinearProgrammingChemicalEquilibrium.class);

  double[] xEts = null;
  double[][] Amatrix;
  double[] chemRefPot;
  ComponentInterface[] components;
  double[] numbering;
  String[] elements;
  int changes = 0;
  int minChanges = 0;
  int maxChanges = 0;

  ChemicalReactionOperations operations;

  /**
   * <p>
   * Constructor for LinearProgrammingChemicalEquilibrium.
   * </p>
   *
   * @param chemRefPot an array of type double
   * @param components an array of {@link neqsim.thermo.component.ComponentInterface} objects
   * @param elements an array of {@link java.lang.String} objects
   * @param operations a {@link neqsim.chemicalreactions.ChemicalReactionOperations} object
   * @param phaseNum a int
   */
  public LinearProgrammingChemicalEquilibrium(double[] chemRefPot, ComponentInterface[] components,
      String[] elements, ChemicalReactionOperations operations, int phaseNum) {
    this.operations = operations;
    this.chemRefPot = chemRefPot;
    this.components = components;
    this.elements = elements;
    changes = elements.length;
    minChanges = elements.length;
    maxChanges = components.length;
    // System.out.println("max: " + maxChanges + " MIN: " +minChanges);
    for (int i = 0; i < components.length; i++) {
      components[i].setReferencePotential(chemRefPot[i]);
    }

    // Commented out by Neeraj
    // Arrays.sort(components,new ReferencePotComparator());
    // this.changePrimaryComponents();
    if (operations.calcChemRefPot(phaseNum) != null) {
      System.arraycopy(operations.calcChemRefPot(phaseNum), 0, this.chemRefPot, 0,
          this.chemRefPot.length);
      for (int i = 0; i < components.length; i++) {
        components[i].setReferencePotential(chemRefPot[i]);
        // System.out.println("sorting....." + components[i].getComponentNumber());
      }
    } else {
      int refPotIter = 0;
      int maxRefPotIter = 100;
      do {
        System.out.println("shifting primary components.....");
        this.changePrimaryComponents();
        refPotIter++;
      } while (operations.calcChemRefPot(phaseNum) == null && refPotIter < maxRefPotIter);
      // System.out.println("shifting components....." );
      System.arraycopy(operations.calcChemRefPot(phaseNum), 0, this.chemRefPot, 0,
          this.chemRefPot.length);
      for (int i = 0; i < components.length; i++) {
        components[i].setReferencePotential(chemRefPot[i]);
      }
      Arrays.sort(components, new ReferencePotComparator());
      for (int i = 0; i < components.length; i++) {
        chemRefPot[i] = components[i].getReferencePotential();
      }
    }

    this.Amatrix = calcA();
  }

  // Modified method by Procede
  /**
   * <p>
   * calcA.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] calcA() {
    int A_size = components.length - operations.getReactionList().getChemicalReactionList().size();
    if (elements.length < (components.length
        - operations.getReactionList().getChemicalReactionList().size())) {
      A_size = elements.length;
    }
    A_size = elements.length;
    double[][] A = new double[A_size + 1][components.length];
    double[][] Am = new double[A_size][components.length];

    for (int k = 0; k < A_size; k++) {
      for (int i = 0; i < components.length; i++) {
        for (int j = 0; j < components[i].getElements().getElementNames().length; j++) {
          if (components[i].getElements().getElementNames()[j].equals(elements[k])) {
            A[k][i] = components[i].getElements().getElementCoefs()[j];
            Am[k][i] = components[i].getElements().getElementCoefs()[j];
          }
        }
      }
    }

    for (int i = 0; i < components.length; i++) {
      A[A_size][i] = components[i].getIonicCharge();
    }
    /*
     * //Added By Neeraj Matrix A_matrix = new Matrix(A); A_matrix.print(10, 10); if
     * (A_matrix.rank() < (elements.length)) { Amatrix = Am; return Am; } else { Amatrix = A; return
     * A; }
     */
    return A;
  }

  /**
   * <p>
   * getA.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] getA() {
    return Amatrix;
  }

  /**
   * <p>
   * getRefPot.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getRefPot() {
    return chemRefPot;
  }

  /**
   * <p>
   * changePrimaryComponents.
   * </p>
   */
  public void changePrimaryComponents() {
    if (changes == maxChanges && minChanges >= 0) {
      changes = minChanges;
      minChanges--;
    }
    ComponentInterface tempComp;
    tempComp = components[minChanges - 1].clone();
    components[minChanges - 1] = components[changes].clone();
    components[changes] = tempComp;
    changes++;
    // chemRefPot = operations.calcChemRefPot();
    /*
     * this.Amatrix = calcA(); Matrix temp = ((Matrix) (new Matrix(Amatrix))).getMatrix(0,
     * elements.length-1, 0, elements.length-1); System.out.println("rank....." +temp.rank());
     */
  }

  // Method commented out by Neeraj
  /*
   * public double[] generateInitialEstimates(SystemInterface system, double[] bVector, double
   * inertMoles, int phaseNum){ Matrix solved; Matrix atemp = new
   * Matrix(Amatrix).getMatrix(0,Amatrix.length-1,0,Amatrix[0].length-1).copy(); Matrix mutemp = new
   * Matrix(chemRefPot,1).times(1.0/(R*system.getPhase(phaseNum).getTemperature())). copy(); Matrix
   * lagrangeTemp = atemp.transpose().solve(mutemp.transpose()).copy(); //bmatrix and Ans Added by
   * Neeraj //bmatrix = new Matrix(bVector,1); //int rank = atemp.rank();
   * //System.out.println("Rank of A "+rank); //Ans = atemp.solve(bmatrix.transpose());
   * //System.out.println("Ans"); //Ans.print(10,8); //Print statements added by Neeraj
   * System.out.println("lagranges: "); lagrangeTemp.print(10,2); System.out.println("refpot: ");
   * mutemp.print(10,2); System.out.println("A: "); atemp.print(10,2);
   *
   * Matrix rTemp = new Matrix(atemp.getRowDimension(),1); rTemp.set(0,0,inertMoles/bVector[0]);
   * for(int i=1;i<atemp.getRowDimension();i++){ rTemp.set(i,0, bVector[i]/bVector[0]); }
   *
   * // System.out.println("rMatTemp: "); // rTemp.print(10,5);
   *
   * Matrix zTemp = new Matrix(atemp.getRowDimension(),1); for(int
   * i=0;i<atemp.getRowDimension();i++){ //Neeraj -- Source of error //lagrange have very high +ve
   * values. So, their exp becomes infinity zTemp.set(i,0, Math.exp(lagrangeTemp.get(i,0))); }
   *
   * System.out.println("zMatTemp: "); //zTemp.print(10,5);
   *
   * Matrix phiMatTemp = new Matrix(atemp.getRowDimension(),1);
   * //atemp.transpose().solve(mutemp.transpose()); for(int i=0;i<atemp.getRowDimension();i++) {
   * phiMatTemp.set(i,0,zTemp.get(i,0)); } for(int i=1;i<atemp.getRowDimension();i++){
   * phiMatTemp.set(0,0,phiMatTemp.get(0,0)*Math.pow(phiMatTemp.get(i,0),rTemp.get (i,0))); }
   * //System.out.println("phiMatTemp: "); //phiMatTemp.print(10,10);
   *
   * Matrix betaTemp = atemp.copy(); for(int j=0;j<atemp.getColumnDimension();j++){
   * betaTemp.set(0,j,1.0 + rTemp.get(0,0)*atemp.get(0,j)); }
   *
   * for(int i=1;i<atemp.getRowDimension();i++) { for(int j=0;j<atemp.getColumnDimension();j++){
   * betaTemp.set(i,j,atemp.get(i,j)-rTemp.get(i,0)*atemp.get(0,j)); } }
   *
   * // System.out.println("betaTemp: "); // betaTemp.print(10,10);
   *
   * Matrix alphaTemp = betaTemp.copy(); for(int j=0;j<Amatrix[0].length;j++){
   * alphaTemp.set(0,j,atemp.get(0,j)); } // System.out.println("alphaTemp: ");
   * alphaTemp.print(10,10);
   *
   * do{ double[] fVal = new double[atemp.getRowDimension()]; double[][] dfVal = new
   * double[atemp.getRowDimension()][atemp.getRowDimension()];
   *
   * //creates f-vlas
   *
   * for(int i=0;i<atemp.getRowDimension();i++) { fVal[i]=0; for(int
   * j=0;j<atemp.getColumnDimension();j++){ double phiTemp = 1.0; for(int
   * k=0;k<atemp.getRowDimension();k++) { phiTemp =
   * phiTemp*Math.pow(phiMatTemp.get(k,0),alphaTemp.get(k,j)); } fVal[i] +=
   * betaTemp.get(i,j)*Math.exp(-chemRefPot[j]/(R*system.getPhase(phase).
   * getTemperature()))*phiTemp; } // System.out.println("fval: " + fVal[i]); } fVal[0] = fVal[0] -
   * 1.0;
   *
   * for(int i=0;i<atemp.getRowDimension();i++){ for(int j=0;j<atemp.getRowDimension();j++) {
   * for(int k=0;k<atemp.getColumnDimension();k++){ double phiTemp = 1.0; for(int
   * p=0;p<atemp.getRowDimension();p++) { phiTemp =
   * phiTemp*Math.pow(phiMatTemp.get(p,0),alphaTemp.get(p,k)); } dfVal[i][j] +=
   * betaTemp.get(i,k)*alphaTemp.get(j,k)*Math.exp(-chemRefPot[k]/(R*system.
   * getPhase(phase).getTemperature()))*phiTemp; } } }
   *
   * // System.out.println("solved: "); Matrix fMatrix = new Matrix(fVal,1); Matrix dfMatrix = new
   * Matrix(dfVal); solved = dfMatrix.solve(fMatrix.timesEquals(-1.0).transpose());
   *
   * //fMatrix.print(10,2); //dfMatrix.print(10,2); //System.out.println("solved: ");
   * //solved.print(10,6);
   *
   * for(int i=0;i<atemp.getRowDimension();i++) {
   * phiMatTemp.set(i,0,Math.exp(solved.get(i,0))*phiMatTemp.get(i,0)); }
   * //System.out.println("phiMatTemp: "); //phiMatTemp.print(10,10); }
   * while(Math.abs(solved.norm2())>1e-10);
   *
   * double temp=1.0; for(int i=1;i<atemp.getRowDimension();i++) { zTemp.set(i,0,
   * phiMatTemp.get(i,0)); temp = temp*Math.pow(zTemp.get(i,0),rTemp.get(i,0)); }
   * zTemp.set(0,0,phiMatTemp.get(0,0)/temp);
   *
   * xEts = new double[atemp.getColumnDimension()]; double sum=0; for(int
   * k=0;k<atemp.getColumnDimension();k++){ xEts[k] =
   * Math.exp(-chemRefPot[k]/(R*system.getPhase(0).getTemperature()));
   * //System.out.println("x check1: " + xEts[k]); for(int i=0;i<atemp.getRowDimension();i++) {
   * xEts[k] = xEts[k]*Math.pow(zTemp.get(i,0),atemp.get(i,k)); } sum += xEts[k];
   * //System.out.println("x check2: " + xEts[k]); } //System.out.println("sum: " + sum);
   *
   * double moles=0; for(int k=0;k<atemp.getColumnDimension();k++){ moles += xEts[k]*atemp.get(0,k);
   * } //Print added by Neeraj //System.out.println("mole tot " + moles); moles = 1.0/moles *
   * bVector[0];
   *
   * double[] nEts = new double[atemp.getColumnDimension()]; double totm=0.0; for(int
   * k=0;k<atemp.getColumnDimension();k++){ nEts[k] = xEts[k]*moles;
   * //system.getPhases()[1].getNumberOfMolesInPhase(); totm += nEts[k];
   * //System.out.println("N check: " + "  comp " + components[k].getComponentName() + "  " +
   * nEts[k]); } //System.out.println("tot moles : " + system.getPhase(1).getNumberOfMolesInPhase()
   * + "  tot " +totm);
   *
   * return nEts; }
   */

  /**
   * <p>
   * calcx.
   * </p>
   *
   * @param atemp a {@link Jama.Matrix} object
   * @param lagrangeTemp a {@link Jama.Matrix} object
   */
  public void calcx(Matrix atemp, Matrix lagrangeTemp) {
    /*
     * xEts = new double[atemp.getColumnDimension()]; for(int k=0;k<atemp.getColumnDimension();k++){
     * xEts[k] = Math.exp(-chemRefPot[k]/(R*system.getTemperature())); for(int
     * i=0;i<Amatrix.length;i++) { xEts[k] =
     * xEts[k]*Math.pow(Math.exp(lagrangeTemp.get(i,0)),atemp.get(i,k)); }
     * System.out.println("x check: " + xEts[k]); /* if(xEts[k]>200) {
     * this.changePrimaryComponents(); return this.generateInitialEstimates(system, bVector,
     * inertMoles); } } xEts = new double[Amatrix[0].length]; for(int i=0;i<Amatrix.length;i++) {
     * xEts[i] = 1.0; }
     */
  }
  // Method added by Neeraj
  /*
   * public double[] generateInitialEstimates(SystemInterface system, double[] bVector, double
   * inertMoles, int phaseNum){ int i,j; double[] n = new double[components.length]; Matrix atemp,
   * btemp; Matrix mutemp = new
   * Matrix(chemRefPot,1).times(1.0/(R*system.getPhase(phaseNum).getTemperature())). copy(); Matrix
   * ntemp; atemp = new Matrix(7,7); btemp = new Matrix(1,7); //for (i=0;i<4;i++) for (i=0;i<5;i++)
   * { for (j=0;j<7;j++) atemp.set(i,j,Amatrix[i][j]); btemp.set(0,i,bVector[i]); }
   * atemp.set(5,4,1); atemp.set(6,5,1); //atemp.set(4,4,1); //atemp.set(5,5,1); //atemp.set(6,1,1);
   * //atemp.print(5,1); //btemp.print(5,5); //mutemp.print(5,5); ntemp =
   * atemp.solve(btemp.transpose()); ntemp.print(5,5); for (i=0;i<7;i++) n[i] = ntemp.get(i,0); int
   * rank = atemp.rank(); return n; }
   */

  // Method updated to use Apache Commons Math 3 by Marlene 07.12.18
  /**
   * <p>
   * generateInitialEstimates.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param bVector an array of type double
   * @param inertMoles a double
   * @param phaseNum a int
   * @return an array of type double
   */
  public double[] generateInitialEstimates(SystemInterface system, double[] bVector,
      double inertMoles, int phaseNum) {
    int i;
    int j;
    double rhs = 0.0;
    Matrix mutemp = new Matrix(chemRefPot, 1)
        .times(1.0 / (R * system.getPhase(phaseNum).getTemperature())).copy();
    double[] v = new double[components.length + 1];
    for (i = 0; i < components.length; i++) {
      v[i + 1] = mutemp.get(0, i);
    }
    LinearObjectiveFunction f = new LinearObjectiveFunction(v, 0.0);
    List<LinearConstraint> cons = new ArrayList<LinearConstraint>();
    for (j = 0; j < bVector.length; j++) {
      // BUG FIX: Create a fresh array for each constraint to avoid v[0] contamination
      double[] constraintCoeffs = new double[components.length + 1];
      constraintCoeffs[0] = 0.0; // Explicitly set to 0 - this variable is not used
      for (i = 0; i < components.length; i++) {
        constraintCoeffs[i + 1] = Amatrix[j][i];
      }
      rhs = bVector[j];
      cons.add(new LinearConstraint(constraintCoeffs, Relationship.EQ, rhs));
    }

    NonNegativeConstraint nonneg = new NonNegativeConstraint(true);
    LinearConstraintSet consSet = new LinearConstraintSet(cons);
    SimplexSolver solver = new SimplexSolver();
    PointValuePair optimal = null;
    try {
      optimal = solver.optimize(new MaxIter(1000), f, consSet, GoalType.MINIMIZE, nonneg);
    } catch (NoFeasibleSolutionException ex) {
      logger.error("no feasible solution", ex);
      return null;
    } catch (Exception ex) {
      logger.error("linear optimization failed", ex);
      return null;
    }

    int compNumb = system.getPhase(phaseNum).getNumberOfComponents();
    double[] lp_solution = new double[compNumb];
    double[] temp = optimal.getPoint();

    for (i = 0; i < compNumb - (compNumb - components.length); i++) {
      lp_solution[i] = temp[i + 1];
    }

    return lp_solution;
  }
}
