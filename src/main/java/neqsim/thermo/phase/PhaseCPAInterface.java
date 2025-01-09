package neqsim.thermo.phase;

import neqsim.thermo.mixingrule.CPAMixingRulesInterface;

/**
 * <p>
 * PhaseCPAInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface PhaseCPAInterface extends PhaseEosInterface {
  /**
   * <p>
   * Getter for property hcpatot.
   * </p>
   *
   * @return a double
   */
  double getHcpatot();

  /**
   * <p>
   * getCrossAssosiationScheme.
   * </p>
   *
   * @param comp1 a int
   * @param comp2 a int
   * @param site1 a int
   * @param site2 a int
   * @return a int
   */
  int getCrossAssosiationScheme(int comp1, int comp2, int site1, int site2);

  /**
   * <p>
   * getGcpa.
   * </p>
   *
   * @return a double
   */
  public double getGcpa();

  /**
   * <p>
   * getGcpav.
   * </p>
   *
   * @return a double
   */
  public double getGcpav();

  /**
   * <p>
   * getTotalNumberOfAccociationSites.
   * </p>
   *
   * @return a int
   */
  public int getTotalNumberOfAccociationSites();

  /**
   * <p>
   * setTotalNumberOfAccociationSites.
   * </p>
   *
   * @param totalNumberOfAccociationSites a int
   */
  public void setTotalNumberOfAccociationSites(int totalNumberOfAccociationSites);

  /**
   * <p>
   * getCpamix.
   * </p>
   *
   * @return a {@link neqsim.thermo.mixingrule.CPAMixingRulesInterface} object
   */
  public CPAMixingRulesInterface getCpamix();
}
