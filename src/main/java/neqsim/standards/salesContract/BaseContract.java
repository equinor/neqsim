/*
 * BaseContract.java
 *
 * Created on 15. juni 2004, 21:43
 */
package neqsim.standards.salesContract;

import java.awt.BorderLayout;
import java.awt.Container;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import neqsim.standards.StandardInterface;
import neqsim.standards.gasQuality.Draft_ISO18453;
import neqsim.standards.gasQuality.GasChromotograpyhBase;
import neqsim.standards.gasQuality.Standard_ISO6976;
import neqsim.standards.gasQuality.BestPracticeHydrocarbonDewPoint;
import neqsim.standards.gasQuality.SulfurSpecificationMethod;
import neqsim.standards.gasQuality.UKspecifications_ICF_SI;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * BaseContract class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class BaseContract implements ContractInterface {
    private static final long serialVersionUID = 1000;

    private String[][] resultTable = new String[50][9];
    double waterDewPointTemperature = -12.0;
    double waterDewPointSpecPressure = 70.0;
    private String contractName = "";
    ContractSpecification[] spesifications = new ContractSpecification[50];
    private int specificationsNumber = 0;

    /**
     * Creates a new instance of BaseContract
     */
    public BaseContract() {}

    /**
     * <p>
     * Constructor for BaseContract.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public BaseContract(SystemInterface system) {
        StandardInterface standard = new Draft_ISO18453(system);
        spesifications[0] = new ContractSpecification("", "", "", "water dew point specification",
                standard, 0, 0, "degC", 0, 0, 0, "");
    }

    /**
     * <p>
     * Constructor for BaseContract.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param terminal a {@link java.lang.String} object
     * @param country a {@link java.lang.String} object
     */
    public BaseContract(SystemInterface system, String terminal, String country) {
        int numb = 0;
        this.setContractName(contractName);
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        try {
            dataSet =
                    database.getResultSet("SELECT * FROM gascontractspecifications WHERE TERMINAL='"
                            + terminal + "'" + " AND COUNTRY='" + country + "'");
            while (dataSet.next()) {
                numb++;
                StandardInterface method = getMethod(system, dataSet.getString("METHOD"));
                spesifications[numb - 1] = getSpecification(method, dataSet.getString("NAME"),
                        dataSet.getString("SPECIFICATION"), dataSet.getString("COUNTRY"),
                        dataSet.getString("TERMINAL"),
                        Double.parseDouble(dataSet.getString("MINVALUE")),
                        Double.parseDouble(dataSet.getString("MAXVALUE")),
                        dataSet.getString("UNIT"),
                        Double.parseDouble(dataSet.getString("ReferenceTmeasurement")),
                        Double.parseDouble(dataSet.getString("ReferenceTcombustion")),
                        Double.parseDouble(dataSet.getString("ReferencePbar")),
                        dataSet.getString("Comments"));
                System.out.println("specification added..." + numb);
            }
        } catch (Exception e) {
            System.out.println("error in comp");
            e.printStackTrace();
        } finally {
            specificationsNumber = numb;
            try {
                if (dataSet != null) {
                    dataSet.close();
                }
                if (database.getStatement() != null) {
                    database.getStatement().close();
                }
                if (database.getConnection() != null) {
                    database.getConnection().close();
                }
            } catch (Exception e) {
                System.out.println("error closing database.....");
                e.printStackTrace();
            }
        }
    }

    /**
     * <p>
     * getMethod.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param methodName a {@link java.lang.String} object
     * @return a {@link neqsim.standards.StandardInterface} object
     */
    public StandardInterface getMethod(SystemInterface system, String methodName) {
        if (methodName.equals("ISO18453")) {
            return new Draft_ISO18453(system);
        }
        if (methodName.equals("CO2")) {
            return new GasChromotograpyhBase(system, "CO2");
        }
        if (methodName.equals("H2S")) {
            return new GasChromotograpyhBase(system, "H2S");
        }
        if (methodName.equals("Total sulphur")) {
            return new GasChromotograpyhBase(system, "H2S");
        }
        if (methodName.equals("oxygen")) {
            return new GasChromotograpyhBase(system, "oxygen");
        }
        if (methodName.equals("ISO6976")) {
            return new Standard_ISO6976(system);
        }
        if (methodName.equals("SulfurSpecificationMethod")) {
            return new SulfurSpecificationMethod(system);
        }
        if (methodName.equals("BestPracticeHydrocarbonDewPoint")) {
            return new BestPracticeHydrocarbonDewPoint(system);
        }
        if (methodName.equals("UKspecifications")) {
            return new UKspecifications_ICF_SI(system);
        }
        return null;
    }

    /**
     * <p>
     * getSpecification.
     * </p>
     *
     * @param method a {@link neqsim.standards.StandardInterface} object
     * @param specificationName a {@link java.lang.String} object
     * @param specificationName2 a {@link java.lang.String} object
     * @param country a {@link java.lang.String} object
     * @param terminal a {@link java.lang.String} object
     * @param minValue a double
     * @param maxValue a double
     * @param unit a {@link java.lang.String} object
     * @param referenceTemperature a double
     * @param referenceTemperatureComb a double
     * @param referencePressure a double
     * @param comments a {@link java.lang.String} object
     * @return a {@link neqsim.standards.salesContract.ContractSpecification} object
     */
    public ContractSpecification getSpecification(StandardInterface method,
            String specificationName, String specificationName2, String country, String terminal,
            double minValue, double maxValue, String unit, double referenceTemperature,
            double referenceTemperatureComb, double referencePressure, String comments) {
        return new ContractSpecification(specificationName, specificationName2, country, terminal,
                method, minValue, maxValue, unit, referenceTemperature, referenceTemperatureComb,
                referencePressure, comments);

    }

    /** {@inheritDoc} */
    @Override
    public void runCheck() {
        int j = 0;
        resultTable = new String[specificationsNumber][12];
        for (int i = 0; i < specificationsNumber; i++) {
            if (!(spesifications[i] == null)) {
                try {
                    spesifications[i].getStandard().calculate();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                spesifications[i].getStandard().setSalesContract(this);
                System.out.println("Type: " + spesifications[i].getDescription() + " Standard "
                        + spesifications[i].getStandard().getName() + " : "
                        + spesifications[i].getStandard().isOnSpec());
                getResultTable()[j][0] = spesifications[i].getDescription();
                getResultTable()[j][1] = Double.toString(spesifications[i].getStandard()
                        .getValue(spesifications[i].getName(), spesifications[i].getUnit()));
                getResultTable()[j][2] = spesifications[i].getCountry();
                getResultTable()[j][3] = spesifications[i].getTerminal();
                getResultTable()[j][4] = Double.toString(spesifications[i].getMinValue());
                getResultTable()[j][5] = Double.toString(spesifications[i].getMaxValue());
                getResultTable()[j][6] = spesifications[i].getUnit();
                getResultTable()[j][7] = spesifications[i].getStandard().getName();
                getResultTable()[j][8] =
                        Double.toString(spesifications[i].getReferenceTemperatureMeasurement());
                getResultTable()[j][9] =
                        Double.toString(spesifications[i].getReferenceTemperatureCombustion());
                getResultTable()[j][10] = Double.toString(spesifications[i].getReferencePressure());
                getResultTable()[j][11] = spesifications[i].getComments();
                j++;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void display() {
        JFrame dialog = new JFrame(
                "Specification check against sales specifications: " + getContractName());
        Container dialogContentPane = dialog.getContentPane();
        dialogContentPane.setLayout(new BorderLayout());

        String[] names = {"Specification", "Value", "Country", "Terminal", "Minimum", "Maximum",
                "Unit", "Method", "Reference temperature measurement",
                "Reference temperature of combustion", "Reference pressure", "Comments"};
        JTable Jtab = new JTable(getResultTable(), names);
        JScrollPane scrollpane = new JScrollPane(Jtab);
        dialogContentPane.add(scrollpane);
        dialog.pack();
        dialog.setVisible(true);
    }

    /** {@inheritDoc} */
    @Override
    public void setContract(String name) {
        waterDewPointTemperature = -12.0;
        waterDewPointSpecPressure = 70.0;
    }

    /**
     * {@inheritDoc}
     *
     * Getter for property waterDewPointTemperature.
     */
    @Override
    public double getWaterDewPointTemperature() {
        return waterDewPointTemperature;
    }

    /**
     * {@inheritDoc}
     *
     * Setter for property waterDewPointTemperature.
     */
    @Override
    public void setWaterDewPointTemperature(double waterDewPointTemperature) {
        this.waterDewPointTemperature = waterDewPointTemperature;
    }

    /**
     * {@inheritDoc}
     *
     * Getter for property waterDewPointSpecPressure.
     */
    @Override
    public double getWaterDewPointSpecPressure() {
        return waterDewPointSpecPressure;
    }

    /**
     * {@inheritDoc}
     *
     * Setter for property waterDewPointSpecPressure.
     */
    @Override
    public void setWaterDewPointSpecPressure(double waterDewPointSpecPressure) {
        this.waterDewPointSpecPressure = waterDewPointSpecPressure;
    }

    /** {@inheritDoc} */
    @Override
    public int getSpecificationsNumber() {
        return specificationsNumber;
    }

    /** {@inheritDoc} */
    @Override
    public void setSpecificationsNumber(int specificationsNumber) {
        this.specificationsNumber = specificationsNumber;
    }

    /** {@inheritDoc} */
    @Override
    public String[][] getResultTable() {
        return resultTable;
    }

    /** {@inheritDoc} */
    @Override
    public void setResultTable(String[][] resultTable) {
        this.resultTable = resultTable;
    }

    /** {@inheritDoc} */
    @Override
    public String getContractName() {
        return contractName;
    }

    /** {@inheritDoc} */
    @Override
    public void setContractName(String contractName) {
        this.contractName = contractName;
    }
}
