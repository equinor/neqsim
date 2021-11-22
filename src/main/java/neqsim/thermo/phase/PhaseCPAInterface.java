/*
 * PhaseEosInterface.java
 *
 * Created on 5. juni 2000, 19:20
 */
package neqsim.thermo.phase;

import neqsim.thermo.mixingRule.CPAMixingInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface PhaseCPAInterface extends PhaseEosInterface {
    double getHcpatot();

    int getCrossAssosiationScheme(int comp1, int comp2, int site1, int site2);

    public double getGcpa();

    public double getGcpav();

    public int getTotalNumberOfAccociationSites();

    public void setTotalNumberOfAccociationSites(int totalNumberOfAccociationSites);

    public CPAMixingInterface getCpamix();
}
