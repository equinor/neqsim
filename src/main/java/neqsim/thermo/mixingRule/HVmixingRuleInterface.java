/*
 * HVmixingRuleInterface.java
 *
 * Created on 5. mai 2001, 17:48
 */

package neqsim.thermo.mixingRule;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface HVmixingRuleInterface extends EosMixingRulesInterface {
    public void setHVDijParameter(int i, int j, double value);

    public void setHVDijTParameter(int i, int j, double value);

    public double getHVDijParameter(int i, int j);

    public double getHVDijTParameter(int i, int j);

    public double getKijWongSandler(int i, int j);

    public void setKijWongSandler(int i, int j, double value);

    public void setHValphaParameter(int i, int j, double value);

    public double getHValphaParameter(int i, int j);
}
