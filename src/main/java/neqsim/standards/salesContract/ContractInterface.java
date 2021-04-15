/*
 * ContractInterface.java
 *
 * Created on 15. juni 2004, 21:43
 */

package neqsim.standards.salesContract;

/**
 *
 * @author ESOL
 */
public interface ContractInterface {
    public void setContract(String name);

    public double getWaterDewPointTemperature();

    public void setWaterDewPointTemperature(double waterDewPointTemperature);

    public double getWaterDewPointSpecPressure();

    public void setWaterDewPointSpecPressure(double waterDewPointSpecPressure);

    public void runCheck();

    public String[][] getResultTable();

    public void setResultTable(String[][] resultTable);

    public int getSpecificationsNumber();

    public void setSpecificationsNumber(int specificationsNumber);

    public void display();

    public String getContractName();

    public void setContractName(String contractName);
}
