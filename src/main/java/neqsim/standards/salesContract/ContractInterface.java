/*
 * ContractInterface.java
 *
 * Created on 15. juni 2004, 21:43
 */
package neqsim.standards.salesContract;

/**
 * <p>
 * ContractInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface ContractInterface {
    /**
     * <p>
     * setContract.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setContract(String name);

    /**
     * <p>
     * getWaterDewPointTemperature.
     * </p>
     *
     * @return a double
     */
    public double getWaterDewPointTemperature();

    /**
     * <p>
     * setWaterDewPointTemperature.
     * </p>
     *
     * @param waterDewPointTemperature a double
     */
    public void setWaterDewPointTemperature(double waterDewPointTemperature);

    /**
     * <p>
     * getWaterDewPointSpecPressure.
     * </p>
     *
     * @return a double
     */
    public double getWaterDewPointSpecPressure();

    /**
     * <p>
     * setWaterDewPointSpecPressure.
     * </p>
     *
     * @param waterDewPointSpecPressure a double
     */
    public void setWaterDewPointSpecPressure(double waterDewPointSpecPressure);

    /**
     * <p>
     * runCheck.
     * </p>
     */
    public void runCheck();

    /**
     * <p>
     * getResultTable.
     * </p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[][] getResultTable();

    /**
     * <p>
     * setResultTable.
     * </p>
     *
     * @param resultTable an array of {@link java.lang.String} objects
     */
    public void setResultTable(String[][] resultTable);

    /**
     * <p>
     * getSpecificationsNumber.
     * </p>
     *
     * @return a int
     */
    public int getSpecificationsNumber();

    /**
     * <p>
     * setSpecificationsNumber.
     * </p>
     *
     * @param specificationsNumber a int
     */
    public void setSpecificationsNumber(int specificationsNumber);

    /**
     * <p>
     * display.
     * </p>
     */
    public void display();

    /**
     * <p>
     * getContractName.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getContractName();

    /**
     * <p>
     * setContractName.
     * </p>
     *
     * @param contractName a {@link java.lang.String} object
     */
    public void setContractName(String contractName);
}
