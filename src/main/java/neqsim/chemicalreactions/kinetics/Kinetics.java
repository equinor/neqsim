/*
 * Kinetics.java
 *
 * Created on 3. august 2001, 23:05
 */

package neqsim.chemicalreactions.kinetics;

import java.util.Iterator;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * Kinetics class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Kinetics implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  protected ChemicalReactionOperations operations;
  double phiInfinite = 0.0;
  boolean isIrreversible;

  /**
   * <p>
   * Constructor for Kinetics.
   * </p>
   *
   * @param operations a {@link neqsim.chemicalreactions.ChemicalReactionOperations} object
   */
  public Kinetics(ChemicalReactionOperations operations) {
    this.operations = operations;
  }

  /**
   * <p>
   * calcKinetics.
   * </p>
   */
  public void calcKinetics() {}

  /**
   * <p>
   * calcReacMatrix.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param interPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param comp a int
   * @return a double
   */
  public double calcReacMatrix(PhaseInterface phase, PhaseInterface interPhase, int comp) {
    ChemicalReaction reaction;
    double reacCoef = 0.0;
    double irr = 0.0;
    double ktemp = 0.0;
    double exponent = 0.0;
    Iterator<ChemicalReaction> e =
        operations.getReactionList().getChemicalReactionList().iterator();
    phase.getPhysicalProperties().calcEffectiveDiffusionCoefficients();

    while (e.hasNext()) {
      reaction = e.next();
      ktemp = reaction.getRateFactor(interPhase);
      irr = 1.0 / reaction.getK(phase);
      // System.out.println("reaction heat " + reaction.getReactionHeat(phase));
      for (int j = 0; j < reaction.getNames().length; j++) {
        irr *= Math.pow(
            interPhase.getComponent(reaction.getNames()[j]).getx()
                * phase.getPhysicalProperties().getDensity()
                / phase.getComponent(reaction.getNames()[j]).getMolarMass(),
            -reaction.getStocCoefs()[j]);
        // System.out.println("reac names " + reaction.getNames()[j]);
        // System.out.println("stoc coefs " + reaction.getStocCoefs()[j]);
        if (phase.getComponents()[comp].getName().equals(reaction.getNames()[j])) {
          for (int k = 0; k < reaction.getNames().length; k++) {
            if (reaction.getStocCoefs()[k] * reaction.getStocCoefs()[j] > 0 && !(k == j)
                && !(phase.getComponent(reaction.getNames()[k]).getName().equals("water"))) {
              exponent = reaction.getStocCoefs()[k] / reaction.getStocCoefs()[j];
              double molConsAint = interPhase.getComponent(comp).getx()
                  * interPhase.getPhysicalProperties().getDensity()
                  / phase.getComponent(comp).getMolarMass();
              double molConsB = phase.getComponent(reaction.getNames()[k]).getx()
                  * phase.getPhysicalProperties().getDensity()
                  / phase.getComponent(reaction.getNames()[k]).getMolarMass();
              ktemp *= Math.pow(molConsB, exponent);
              phiInfinite = Math
                  .sqrt(phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(comp)
                      / phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(
                          phase.getComponent(reaction.getNames()[k]).getComponentNumber()))
                  + Math
                      .sqrt(phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(
                          phase.getComponent(reaction.getNames()[k]).getComponentNumber())
                          / phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(comp))
                      * molConsB / (exponent * molConsAint);
              // System.out.println("reac names " + reaction.getNames()[k]);
              // System.out.println("phi inf " + phiInfinite);
            }
          }
        }
      }
      reacCoef += ktemp;
      // System.out.println("irr " + irr);
      if (Math.abs(irr) < 1e-3) {
        isIrreversible = true;
      }
    }
    return reacCoef;
  }

  /**
   * <p>
   * Getter for the field <code>phiInfinite</code>.
   * </p>
   *
   * @return a double
   */
  public double getPhiInfinite() {
    return phiInfinite;
  }

  /**
   * <p>
   * getPseudoFirstOrderCoef.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param interPhase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param comp a int
   * @return a double
   */
  public double getPseudoFirstOrderCoef(PhaseInterface phase, PhaseInterface interPhase, int comp) {
    return calcReacMatrix(phase, interPhase, comp);
  }

  /**
   * Getter for property isIrreversible.
   *
   * @return Value of property isIrreversible.
   */
  public boolean isIrreversible() {
    return isIrreversible;
  }
}
