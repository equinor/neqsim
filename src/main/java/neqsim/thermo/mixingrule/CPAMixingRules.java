/*
 * CPAMixing.java
 *
 * Created on 4. juni 2000, 12:38
 */

package neqsim.thermo.mixingrule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.phase.PhaseCPAInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * CPAMixingRules class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class CPAMixingRules implements Cloneable, ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CPAMixingRules.class);

  /** Name of mixing rule. */
  private String mixingRuleName = "CPA_Radoch";

  int[][] assosSchemeType = null; // 0- ER - 1 - CR1
  double[][] cpaBetaCross = null;
  double[][] cpaEpsCross = null;
  final int[] charge4C = {1, 1, -1, -1};
  final int[] charge1A = {-1};
  final int[] charge2A = {-1, -1};
  final int[] charge2B = {1, -1};

  /**
   * <p>
   * Constructor for CPAMixingRules.
   * </p>
   */
  public CPAMixingRules() {}

  /** {@inheritDoc} */
  @Override
  public CPAMixingRules clone() {
    CPAMixingRules clonedSystem = null;
    try {
      clonedSystem = (CPAMixingRules) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }

  public abstract class CPA_Radoch_base implements CPAMixingRulesInterface {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    double eps = 12000.76;
    double beta = 0.03;
    protected double[][] epsab =
        {{0, eps, eps, eps}, {eps, 0, eps, eps}, {eps, eps, 0, eps}, {eps, eps, eps, 0}};
    protected double[][] betamat = {{0, beta, beta, beta}, {beta, 0, beta, beta},
        {beta, beta, 0, beta}, {beta, beta, beta, 0}};

    public double calcXi(int siteNumber, int compnumb, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcXi(int[][][] assosScheme, int[][][][] assosScheme2, int siteNumber,
        int compnumb, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDelta(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltaNog(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadN(int derivativeComp, int siteNumber1, int siteNumber2, int compnumb1,
        int compnumb2, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadT(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadV(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadTdT(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return 1.0;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadTdV(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return 1.0;
    }
  }

  public class CPA_Radoch extends CPA_Radoch_base {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /** {@inheritDoc} */
    @Override
    public String getName() {
      return mixingRuleName;
    }

    public double getCrossAssociationEnergy(int compnumb1, int compnumb2, PhaseInterface phase,
        double temperature, double pressure, int numbcomp) {
      if (Math.abs(cpaEpsCross[compnumb1][compnumb2]) > 1e-10) {
        // double ec = (phase.getComponent(compnumb1).getAssociationEnergy() +
        // phase.getComponent(compnumb2).getAssociationEnergy()) / 2.0;

        // System.out.println("epscross " + ec + " .. " +
        // cpaEpsCross[compnumb1][compnumb2]);
        return cpaEpsCross[compnumb1][compnumb2];
      }
      return (phase.getComponent(compnumb1).getAssociationEnergy()
          + phase.getComponent(compnumb2).getAssociationEnergy()) / 2.0;
    }

    public double getCrossAssociationVolume(int compnumb1, int compnumb2, PhaseInterface phase,
        double temperature, double pressure, int numbcomp) {
      // System.out.println("ass vol " +
      // Math.sqrt(phase.getComponent(compnumb1).getAssociationVolume()*phase.getComponent(compnumb2).getAssociationVolume()));
      if (Math.abs(cpaBetaCross[compnumb1][compnumb2]) > 1e-10) {
        // System.out.println("betacorss here " + cpaBetaCross[compnumb1][compnumb2] +"
        // epscorss " + getCrossAssociationEnergy(siteNumber1, siteNumber2, compnumb1,
        // compnumb2, phase, temperature, pressure, numbcomp));
        return cpaBetaCross[compnumb1][compnumb2];
      }
      return Math.sqrt(phase.getComponent(compnumb1).getAssociationVolume()
          * phase.getComponent(compnumb2).getAssociationVolume());
    }

    /** {@inheritDoc} */
    @Override
    public double calcDelta(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      if (assosSchemeType[compnumb1][compnumb2] == 0) {
        double temp2 = 0;
        double temp1 = (Math.exp(
            getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature, pressure, numbcomp)
                / (R * phase.getTemperature()))
            - 1.0)
            * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
                + ((ComponentEosInterface) phase.getComponent(compnumb1)).getb())
            / 2.0 * getCrossAssociationVolume(compnumb1, compnumb1, phase, temperature, pressure,
                numbcomp)
            * ((PhaseCPAInterface) phase).getGcpa();
        if (compnumb1 == compnumb2) {
          temp2 = temp1;
        } else {
          temp2 = (Math.exp(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature,
              pressure, numbcomp) / (R * phase.getTemperature())) - 1.0)
              * (((ComponentEosInterface) phase.getComponent(compnumb2)).getb()
                  + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
              / 2.0 * getCrossAssociationVolume(compnumb2, compnumb2, phase, temperature, pressure,
                  numbcomp)
              * ((PhaseCPAInterface) phase).getGcpa();
        }
        return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
            siteNumber1, siteNumber2) * Math.sqrt(temp1 * temp2);
      }
      return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
          siteNumber1, siteNumber2)
          * (Math.exp(getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure,
              numbcomp) / (R * phase.getTemperature())) - 1.0)
          * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
              + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
          / 2.0
          * getCrossAssociationVolume(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
          * ((PhaseCPAInterface) phase).getGcpa();
    }

    public double calcDelta(int compnumb1, int compnumb2, PhaseInterface phase, double temperature,
        double pressure, int numbcomp) {
      if (assosSchemeType[compnumb1][compnumb2] == 0) {
        double temp2 = 0;
        double temp1 = (Math.exp(
            getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature, pressure, numbcomp)
                / (R * phase.getTemperature()))
            - 1.0)
            * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
                + ((ComponentEosInterface) phase.getComponent(compnumb1)).getb())
            / 2.0 * getCrossAssociationVolume(compnumb1, compnumb1, phase, temperature, pressure,
                numbcomp)
            * ((PhaseCPAInterface) phase).getGcpa();
        if (compnumb1 == compnumb2) {
          temp2 = temp1;
        } else {
          temp2 = (Math.exp(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature,
              pressure, numbcomp) / (R * phase.getTemperature())) - 1.0)
              * (((ComponentEosInterface) phase.getComponent(compnumb2)).getb()
                  + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
              / 2.0 * getCrossAssociationVolume(compnumb2, compnumb2, phase, temperature, pressure,
                  numbcomp)
              * ((PhaseCPAInterface) phase).getGcpa();
        }
        return Math.sqrt(temp1 * temp2);
      }
      return (Math.exp(
          getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
              / (R * phase.getTemperature()))
          - 1.0)
          * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
              + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
          / 2.0
          * getCrossAssociationVolume(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
          * ((PhaseCPAInterface) phase).getGcpa();
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltaNog(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      if (assosSchemeType[compnumb1][compnumb2] == 0) {
        double temp2 = 0;
        double temp1 = (Math.exp(
            getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature, pressure, numbcomp)
                / (R * phase.getTemperature()))
            - 1.0)
            * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
                + ((ComponentEosInterface) phase.getComponent(compnumb1)).getb())
            / 2.0 * getCrossAssociationVolume(compnumb1, compnumb1, phase, temperature, pressure,
                numbcomp);
        if (compnumb1 == compnumb2) {
          temp2 = temp1;
        } else {
          temp2 = (Math.exp(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature,
              pressure, numbcomp) / (R * phase.getTemperature())) - 1.0)
              * (((ComponentEosInterface) phase.getComponent(compnumb2)).getb()
                  + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
              / 2.0 * getCrossAssociationVolume(compnumb2, compnumb2, phase, temperature, pressure,
                  numbcomp);
        }
        return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
            siteNumber1, siteNumber2) * Math.sqrt(temp1 * temp2);
      }
      return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
          siteNumber1, siteNumber2)
          * (Math.exp(getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure,
              numbcomp) / (R * phase.getTemperature())) - 1.0)
          * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
              + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
          / 2.0
          * getCrossAssociationVolume(compnumb1, compnumb2, phase, temperature, pressure, numbcomp);
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadN(int derivativeComp, int siteNumber1, int siteNumber2, int compnumb1,
        int compnumb2, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
          siteNumber1, siteNumber2)
          * calcDelta(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
          * ((ComponentSrkCPA) phase.getComponent(derivativeComp)).calc_lngi(phase);
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadT(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      if (assosSchemeType[compnumb1][compnumb2] == 0) {
        double derivative1 =
            -getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature, pressure, numbcomp)
                / (R * phase.getTemperature() * phase.getTemperature())
                * (Math.exp(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature,
                    pressure, numbcomp) / (R * phase.getTemperature())))
                * (((ComponentEosInterface) phase.getComponent(compnumb2)).getb()
                    + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
                / 2.0 * getCrossAssociationVolume(compnumb2, compnumb2, phase, temperature,
                    pressure, numbcomp)
                * ((PhaseCPAInterface) phase).getGcpa();
        double temp1 = derivative1
            * (Math.exp(getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature,
                pressure, numbcomp) / (R * phase.getTemperature())) - 1.0)
            * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
                + ((ComponentEosInterface) phase.getComponent(compnumb1)).getb())
            / 2.0 * getCrossAssociationVolume(compnumb1, compnumb1, phase, temperature, pressure,
                numbcomp)
            * ((PhaseCPAInterface) phase).getGcpa();

        double derivative2 =
            -getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature, pressure, numbcomp)
                / (R * phase.getTemperature() * phase.getTemperature())
                * (Math.exp(getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature,
                    pressure, numbcomp) / (R * phase.getTemperature())))
                * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
                    + ((ComponentEosInterface) phase.getComponent(compnumb1)).getb())
                / 2.0 * getCrossAssociationVolume(compnumb1, compnumb1, phase, temperature,
                    pressure, numbcomp)
                * ((PhaseCPAInterface) phase).getGcpa();
        double temp2 = derivative2
            * (Math.exp(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature,
                pressure, numbcomp) / (R * phase.getTemperature())) - 1.0)
            * (((ComponentEosInterface) phase.getComponent(compnumb2)).getb()
                + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
            / 2.0 * getCrossAssociationVolume(compnumb2, compnumb2, phase, temperature, pressure,
                numbcomp)
            * ((PhaseCPAInterface) phase).getGcpa();
        return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
            siteNumber1, siteNumber2) * 0.5
            / calcDelta(siteNumber1, siteNumber2, compnumb1, compnumb2, phase, temperature,
                pressure, numbcomp)
            * (temp1 + temp2);
      }
      double derivative =
          -getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
              / (R * phase.getTemperature() * phase.getTemperature());
      return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
          siteNumber1, siteNumber2)
          * derivative
          * (Math.exp(getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure,
              numbcomp) / (R * phase.getTemperature())) - 1.0)
          * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
              + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
          / 2.0
          * getCrossAssociationVolume(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
          * ((PhaseCPAInterface) phase).getGcpa();
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadV(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
          siteNumber1, siteNumber2)
          * calcDelta(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
          * ((PhaseCPAInterface) phase).getGcpav();
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadTdV(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      if (assosSchemeType[compnumb1][compnumb2] == 0) {
        if (((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2, siteNumber1,
            siteNumber2) == 1) {
          double tempDelta =
              calcDelta(compnumb2, compnumb2, phase, temperature, pressure, numbcomp);
          // double temp2 = calcDelta(compnumb2, compnumb2, phase, temperature, pressure,
          // numbcomp);
          return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
              siteNumber1, siteNumber2) * 0.5 * Math.pow(tempDelta, -1.0) * 2.0
              * calcDeltadT(siteNumber1, siteNumber2, compnumb1, compnumb2, phase, temperature,
                  pressure, numbcomp)
              * tempDelta * ((PhaseCPAInterface) phase).getGcpav();
        } else {
          return 0.0;
        }
      }
      double derivative =
          -getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
              / (R * phase.getTemperature() * phase.getTemperature());
      return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
          siteNumber1, siteNumber2)
          * derivative
          * Math.exp(getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure,
              numbcomp) / (R * phase.getTemperature()))
          * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
              + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
          / 2.0
          * getCrossAssociationVolume(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
          * ((PhaseCPAInterface) phase).getGcpa() * ((PhaseCPAInterface) phase).getGcpav();
    }

    /** {@inheritDoc} */
    @Override
    public double calcDeltadTdT(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      if (assosSchemeType[compnumb1][compnumb2] == 0) {
        double deltaj = 0;
        double deltai = calcDelta(compnumb1, compnumb1, phase, temperature, pressure, numbcomp);
        if (compnumb1 == compnumb2) {
          deltaj = deltai;
        } else {
          deltaj = calcDelta(compnumb2, compnumb2, phase, temperature, pressure, numbcomp);
        }

        double dDeltaidT =
            -getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature, pressure, numbcomp)
                / (R * phase.getTemperature() * phase.getTemperature())
                * (Math.exp(getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature,
                    pressure, numbcomp) / (R * phase.getTemperature())))
                * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
                    + ((ComponentEosInterface) phase.getComponent(compnumb1)).getb())
                / 2.0 * getCrossAssociationVolume(compnumb1, compnumb1, phase, temperature,
                    pressure, numbcomp)
                * ((PhaseCPAInterface) phase).getGcpa();
        double dDeltajdT =
            -getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature, pressure, numbcomp)
                / (R * phase.getTemperature() * phase.getTemperature())
                * (Math.exp(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature,
                    pressure, numbcomp) / (R * phase.getTemperature())))
                * (((ComponentEosInterface) phase.getComponent(compnumb2)).getb()
                    + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
                / 2.0 * getCrossAssociationVolume(compnumb2, compnumb2, phase, temperature,
                    pressure, numbcomp)
                * ((PhaseCPAInterface) phase).getGcpa();

        double dDeltajdTdT = (2.0
            * getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature, pressure,
                numbcomp)
            / (R * phase.getTemperature() * phase.getTemperature() * phase.getTemperature())
            * (Math.exp(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature,
                pressure, numbcomp) / (R * phase.getTemperature())))
            + Math
                .pow(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature, pressure,
                    numbcomp) / (R * phase.getTemperature() * phase.getTemperature()), 2.0)
                * Math.exp(getCrossAssociationEnergy(compnumb2, compnumb2, phase, temperature,
                    pressure, numbcomp) / (R * phase.getTemperature())))
            * (((ComponentEosInterface) phase.getComponent(compnumb2)).getb()
                + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
            / 2.0 * getCrossAssociationVolume(compnumb2, compnumb2, phase, temperature, pressure,
                numbcomp)
            * ((PhaseCPAInterface) phase).getGcpa();
        double dDeltaidTdT = (2.0
            * getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature, pressure,
                numbcomp)
            / (R * phase.getTemperature() * phase.getTemperature() * phase.getTemperature())
            * (Math.exp(getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature,
                pressure, numbcomp) / (R * phase.getTemperature())))
            + Math
                .pow(getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature, pressure,
                    numbcomp) / (R * phase.getTemperature() * phase.getTemperature()), 2.0)
                * Math.exp(getCrossAssociationEnergy(compnumb1, compnumb1, phase, temperature,
                    pressure, numbcomp) / (R * phase.getTemperature())))
            * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
                + ((ComponentEosInterface) phase.getComponent(compnumb1)).getb())
            / 2.0 * getCrossAssociationVolume(compnumb1, compnumb1, phase, temperature, pressure,
                numbcomp)
            * ((PhaseCPAInterface) phase).getGcpa();

        double deltajjdeltaii = Math.pow(calcDelta(siteNumber1, siteNumber2, compnumb1, compnumb2,
            phase, temperature, pressure, numbcomp), 2.0);

        return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
            siteNumber1, siteNumber2)
            * (-1.0 / 4.0 * Math.pow(deltajjdeltaii, -3.0 / 2.0)
                * Math.pow(dDeltaidT * deltaj + dDeltajdT * deltai, 2.0)
                + 0.5 * Math.pow(deltajjdeltaii, -1.0 / 2.0)
                    * (dDeltaidTdT * deltaj + 2.0 * dDeltaidT * dDeltajdT + dDeltajdTdT * deltai));
      }

      double derivative1 =
          -getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
              / (R * phase.getTemperature() * phase.getTemperature());
      double derivative2 = 2.0
          * getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
          / (R * phase.getTemperature() * phase.getTemperature() * phase.getTemperature());

      return ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2,
          siteNumber1, siteNumber2)
          * derivative1 * derivative1
          * Math.exp(getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature, pressure,
              numbcomp) / (R * phase.getTemperature()))
          * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
              + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
          / 2.0
          * getCrossAssociationVolume(compnumb1, compnumb2, phase, temperature, pressure, numbcomp)
          * ((PhaseCPAInterface) phase).getGcpa()
          + ((PhaseCPAInterface) phase).getCrossAssosiationScheme(compnumb1, compnumb2, siteNumber1,
              siteNumber2)
              * derivative2
              * Math.exp(getCrossAssociationEnergy(compnumb1, compnumb2, phase, temperature,
                  pressure, numbcomp) / (R * phase.getTemperature()))
              * (((ComponentEosInterface) phase.getComponent(compnumb1)).getb()
                  + ((ComponentEosInterface) phase.getComponent(compnumb2)).getb())
              / 2.0 * getCrossAssociationVolume(compnumb1, compnumb2, phase, temperature, pressure,
                  numbcomp)
              * ((PhaseCPAInterface) phase).getGcpa();
    }
    // public double calcXi(int siteNumber, int compnumb, PhaseInterface phase,
    // double temperature, double pressure, int numbcomp) {
    // //System.out.println("scheme " +
    // phase.getComponent(compnumb).getAssociationScheme());

    // double Xi=0.0;
    // double temp=0.0, temp2=0.0;

    // try{
    // for(int i=0;i<phase.getNumberOfComponents();i++){
    // temp2=0.0;
    // assosScheme = setAssociationScheme(i,phase);

    // for(int j=0;j<phase.getComponent(i).getNumberOfAssociationSites();j++){
    // double delatSite = 0.0;
    // // if(assosScheme[siteNumber][j]==0 && compnumb==i && compnumb==i){}
    // if(compnumb==i){
    // delatSite = assosScheme[siteNumber][j]*calcDelta(siteNumber,j,
    // compnumb,i,phase,temperature,pressure,numbcomp);
    // } else{
    // assosScheme2= setCrossAssociationScheme(compnumb,i,phase);

    // // elloit rule
    // if(crossAccociationScheme==0){
    // double sum1 =1.0,sum2=1.0;
    // sum1 = assosScheme2[siteNumber][j]*calcDelta(siteNumber,j,
    // i,i,phase,temperature,pressure,numbcomp);
    // sum2 = assosScheme2[siteNumber][j]*calcDelta(siteNumber,j,
    // compnumb,compnumb,phase,temperature,pressure,numbcomp);
    // delatSite = Math.sqrt(sum1*sum2);
    // }
    // // CR-1
    // else if(crossAccociationScheme==1){
    // delatSite = assosScheme2[siteNumber][j]*calcDelta(siteNumber,j,
    // compnumb,i,phase,temperature,pressure,numbcomp);
    // } else{
    // System.out.println("invalid crossassociation scheme..");
    // }
    // }
    // temp2
    // +=((ComponentCPAInterface)phase.getComponent(i)).getXsite()[j]*delatSite;
    // }
    // temp +=phase.getComponent(i).getNumberOfMolesInPhase()*temp2;
    // }
    // Xi = 1.0/(1.0+1.0/phase.getTotalVolume()*temp);
    // } catch(Exception ex){
    // logger.error(ex.getMessage(), ex);
    // }
    // return Xi;
    // }
  }

  public class PCSAFTa_Radoch extends CPA_Radoch {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    public double getCrossAssociationEnergy(int siteNumber1, int siteNumber2, int compnumb1,
        int compnumb2, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      return (phase.getComponent(compnumb1).getAssociationEnergySAFT()
          + phase.getComponent(compnumb2).getAssociationEnergySAFT()) / 2.0;
    }

    public double getCrossAssociationVolume(int siteNumber1, int siteNumber2, int compnumb1,
        int compnumb2, PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      double extrwterm = Math.pow(Math
          .sqrt(((phase.getComponent(compnumb1).getSigmaSAFTi())
              * phase.getComponent(compnumb2).getSigmaSAFTi()))
          / (0.5 * ((phase.getComponent(compnumb1).getSigmaSAFTi())
              + phase.getComponent(compnumb2).getSigmaSAFTi())),
          3.0);
      // System.out.println("ass vol " +
      // Math.sqrt(phase.getComponent(compnumb1).getAssociationVolume()*phase.getComponent(compnumb2).getAssociationVolume()));
      return Math.sqrt(phase.getComponent(compnumb1).getAssociationVolumeSAFT()
          * phase.getComponent(compnumb2).getAssociationVolumeSAFT()) * extrwterm;
    }

    /** {@inheritDoc} */
    @Override
    public double calcDelta(int siteNumber1, int siteNumber2, int compnumb1, int compnumb2,
        PhaseInterface phase, double temperature, double pressure, int numbcomp) {
      // System.out.println("bsaft " +
      // Math.pow((((ComponentEosInterface)phase.getComponent(compnumb1)).getSigmaSAFTi()+((ComponentEosInterface)phase.getComponent(compnumb2)).getSigmaSAFTi())/2.0,3.0));
      // System.out.println("bcpa " +
      // (((ComponentEosInterface)phase.getComponent(compnumb1)).getb()+((ComponentEosInterface)phase.getComponent(compnumb2)).getb())/2.0);

      return (Math.exp(getCrossAssociationEnergy(siteNumber1, siteNumber2, compnumb1, compnumb2,
          phase, temperature, pressure, numbcomp) / (R * phase.getTemperature())) - 1.0)
          * Math.pow((phase.getComponent(compnumb1).getSigmaSAFTi()
              + phase.getComponent(compnumb2).getSigmaSAFTi()) / 2.0, 3.0)
          * 1.0e5 * ThermodynamicConstantsInterface.avagadroNumber
          * getCrossAssociationVolume(siteNumber1, siteNumber2, compnumb1, compnumb2, phase,
              temperature, pressure, numbcomp)
          * ((PhaseCPAInterface) phase).getGcpa();
    }
  }

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @param mr a int
   * @return a {@link neqsim.thermo.mixingrule.CPAMixingRulesInterface} object
   */
  public CPAMixingRulesInterface getMixingRule(int mr) {
    if (mr == 1) {
      mixingRuleName = "CPA_Radoch";
      return new CPA_Radoch();
    } else if (mr == 3) {
      mixingRuleName = "PCSAFTa_Radoch";
      return new PCSAFTa_Radoch();
    }
    throw new RuntimeException(
        new neqsim.util.exception.InvalidInputException(this, "getMixingRule", "mr"));
  }

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @param mr a int
   * @return a {@link neqsim.thermo.mixingrule.CPAMixingRulesInterface} object
   */
  public CPAMixingRulesInterface getMixingRule(MixingRuleTypeInterface mr) {
    if (!CPAMixingRuleType.class.isInstance(mr)) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "setMixingRule", "mr"));
    }
    CPAMixingRuleType cmr = (CPAMixingRuleType) mr;
    switch (cmr) {
      case CPA_RADOCH:
        mixingRuleName = "CPA_Radoch";
        return new CPA_Radoch();
      case PCSAFTA_RADOCH:
        mixingRuleName = "PCSAFTa_Radoch";
        return new PCSAFTa_Radoch();
      default:
        return new CPA_Radoch();
    }
  }

  /**
   * <p>
   * getMixingRule.
   * </p>
   *
   * @param mr a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.thermo.mixingrule.CPAMixingRulesInterface} object
   */
  public CPAMixingRulesInterface getMixingRule(int mr, PhaseInterface phase) {
    assosSchemeType = new int[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    cpaBetaCross = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
    cpaEpsCross = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];

    for (int k = 0; k < phase.getNumberOfComponents(); k++) {
      String component_name = phase.getComponent(k).getComponentName();
      java.sql.ResultSet dataSet = null;

      for (int l = k; l < phase.getNumberOfComponents(); l++) {
        if (k == l || phase.getComponent(l).getNumberOfAssociationSites() == 0
            || phase.getComponent(k).getNumberOfAssociationSites() == 0) {
        } else {
          try (neqsim.util.database.NeqSimDataBase database =
              new neqsim.util.database.NeqSimDataBase()) {
            // database = new util.database.NeqSimDataBase();
            if (NeqSimDataBase.createTemporaryTables()) {
              dataSet = database.getResultSet("SELECT * FROM intertemp WHERE (comp1='"
                  + component_name + "' AND comp2='" + phase.getComponent(l).getComponentName()
                  + "') OR (comp1='" + phase.getComponent(l).getComponentName() + "' AND comp2='"
                  + component_name + "')");
            } else {
              dataSet = database.getResultSet("SELECT * FROM inter WHERE (comp1='" + component_name
                  + "' AND comp2='" + phase.getComponent(l).getComponentName() + "') OR (comp1='"
                  + phase.getComponent(l).getComponentName() + "' AND comp2='" + component_name
                  + "')");
            }
            if (dataSet.next()) {
              assosSchemeType[k][l] =
                  Integer.parseInt(dataSet.getString("cpaAssosiationType").trim());
              assosSchemeType[l][k] = assosSchemeType[k][l];

              cpaBetaCross[k][l] = Double.parseDouble(dataSet.getString("cpaBetaCross").trim());
              cpaBetaCross[l][k] = cpaBetaCross[k][l];

              cpaEpsCross[k][l] = Double.parseDouble(dataSet.getString("cpaEpsCross").trim());
              cpaEpsCross[l][k] = cpaEpsCross[k][l];
            }
            // System.out.println("ass scheme " + assosSchemeType[l][k]);
            // System.out.println("cpaEpsCross[k][l] " + cpaEpsCross[k][l]);
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
          }
        }
      }
    }

    return getMixingRule(mr);
  }

  /**
   * <p>
   * resetMixingRule.
   * </p>
   *
   * @param i a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a {@link neqsim.thermo.mixingrule.CPAMixingRulesInterface} object
   */
  public CPAMixingRulesInterface resetMixingRule(int i, PhaseInterface phase) {
    return getMixingRule(i);
  }

  /**
   * <p>
   * setAssociationScheme.
   * </p>
   *
   * @param compnumb a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return an array of {@link int} objects
   */
  public int[][] setAssociationScheme(int compnumb, PhaseInterface phase) {
    if (phase.getComponent(compnumb).getAssociationScheme().equals("4C")) {
      return getInteractionMatrix(charge4C, charge4C);
    } else if (phase.getComponent(compnumb).getAssociationScheme().equals("2B")) {
      return getInteractionMatrix(charge2B, charge2B);
    } else if (phase.getComponent(compnumb).getAssociationScheme().equals("1A")) {
      return getInteractionMatrix(charge1A, charge1A);
    } else if (phase.getComponent(compnumb).getAssociationScheme().equals("2A")) {
      return getInteractionMatrix(charge2A, charge2A);
    } else {
      return new int[0][0];
    }
  }

  /**
   * <p>
   * setCrossAssociationScheme.
   * </p>
   *
   * @param compnumb a int
   * @param compnumb2 a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return an array of {@link int} objects
   */
  public int[][] setCrossAssociationScheme(int compnumb, int compnumb2, PhaseInterface phase) {
    int[] comp1Scheme = new int[0];
    int[] comp2Scheme = new int[0];
    if (phase.getComponent(compnumb).getOrginalNumberOfAssociationSites()
        * phase.getComponent(compnumb2).getOrginalNumberOfAssociationSites() > 0) {
      if (phase.getComponent(compnumb).getAssociationScheme().equals("4C")) {
        comp1Scheme = charge4C;
      }
      if (phase.getComponent(compnumb).getAssociationScheme().equals("2A")) {
        comp1Scheme = charge2A;
      }
      if (phase.getComponent(compnumb).getAssociationScheme().equals("2B")) {
        comp1Scheme = charge2B;
      }
      if (phase.getComponent(compnumb).getAssociationScheme().equals("1A")) {
        comp1Scheme = charge1A;
      }

      if (phase.getComponent(compnumb2).getAssociationScheme().equals("4C")) {
        comp2Scheme = charge4C;
      }
      if (phase.getComponent(compnumb2).getAssociationScheme().equals("2A")) {
        comp2Scheme = charge2A;
      }
      if (phase.getComponent(compnumb2).getAssociationScheme().equals("2B")) {
        comp2Scheme = charge2B;
      }
      if (phase.getComponent(compnumb2).getAssociationScheme().equals("1A")) {
        comp2Scheme = charge1A;
      }
    } else {
      return new int[0][0];
    }
    return getInteractionMatrix(comp1Scheme, comp2Scheme);
  }

  /**
   * <p>
   * getInteractionMatrix.
   * </p>
   *
   * @param comp1Scheme an array of {@link int} objects
   * @param comp2Scheme an array of {@link int} objects
   * @return an array of {@link int} objects
   */
  public int[][] getInteractionMatrix(int[] comp1Scheme, int[] comp2Scheme) {
    int[][] intMatrix = new int[comp1Scheme.length][comp2Scheme.length];
    for (int i = 0; i < comp1Scheme.length; i++) {
      for (int j = 0; j < comp2Scheme.length; j++) {
        if (comp1Scheme[i] * comp2Scheme[j] < 0) {
          intMatrix[i][j] = 1;
        } else {
          intMatrix[i][j] = 0;
        }
      }
    }
    return intMatrix;
  }
}
