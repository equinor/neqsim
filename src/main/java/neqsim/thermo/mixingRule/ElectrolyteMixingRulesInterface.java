/*
 * ElectrolyteMixingRulesInterface.java
 *
 * Created on 26. februar 2001, 19:38
 */

package neqsim.thermo.mixingRule;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface ElectrolyteMixingRulesInterface extends Serializable {

    public void calcWij(PhaseInterface phase);

    public void setWijParameter(int i, int j, double value);

    public double getWijParameter(int i, int j);

    public void setWijT1Parameter(int i, int j, double value);

    public double gettWijT1Parameter(int i, int j);

    public void setWijT2Parameter(int i, int j, double value);

    public double gettWijT2Parameter(int i, int j);

    public double getWij(int i, int j, double temperature);

    public double getWijT(int i, int j, double temperature);

    public double getWijTT(int i, int j, double temperature);

    public double calcW(PhaseInterface phase, double temperature, double pressure, int numbcomp);

    public double calcWi(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

    public double calcWiT(int compNumb, PhaseInterface phase, double temperature, double pressure, int numbcomp);

    public double calcWT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

    public double calcWTT(PhaseInterface phase, double temperature, double pressure, int numbcomp);

    public double calcWij(int compNumbi, int compNumj, PhaseInterface phase, double temperature, double pressure,
            int numbcomp);

}