/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */
package neqsim.processSimulation.processEquipment.separator;

import neqsim.processSimulation.mechanicalDesign.separator.GasScrubberMechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class GasScrubberSimple extends Separator implements ProcessEquipmentInterface, SeparatorInterface {

    private static final long serialVersionUID = 1000;

    SystemInterface gasSystem, waterSystem, liquidSystem, thermoSystemCloned;
    Stream inletStream;
    Stream gasOutStream;
    Stream liquidOutStream;
    String name = new String();

    /**
     * Creates new Separator
     */
    public GasScrubberSimple() {
        super();
        mechanicalDesign = new GasScrubberMechanicalDesign(this);
        this.setOrientation("vertical");
    }

    public GasScrubberSimple(Stream inletStream) {
        this();
        this.setInletStream(inletStream);
    }

    public GasScrubberSimple(String name, Stream inletStream) {
        this();
        this.name = name;
        this.setInletStream(inletStream);
    }

    @Override
	public void setName(String name) {
        this.name = name;
    }

    public void setInletStream(Stream inletStream) {
        this.inletStream = inletStream;

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        gasSystem = thermoSystem.phaseToSystem(0);
        gasOutStream = new Stream(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        liquidSystem = thermoSystem.phaseToSystem(1);
        liquidOutStream = new Stream(liquidSystem);
    }

    @Override
	public Stream getLiquidOutStream() {
        return liquidOutStream;
    }

    @Override
	public Stream getGasOutStream() {
        return gasOutStream;
    }

    @Override
	public Stream getGas() {
        return getGasOutStream();
    }

    @Override
	public Stream getLiquid() {
        return getLiquidOutStream();
    }

    @Override
	public void run() {

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoOps.TPflash();
        if (separatorSection.size() > 0) {
            calcLiquidCarryoverFraction();
            thermoSystem.addLiquidToGas(getLiquidCarryoverFraction());
        }
        gasSystem = thermoSystem.phaseToSystem(0);
        gasSystem.setNumberOfPhases(1);
        gasOutStream.setThermoSystem(gasSystem);

        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        if (separatorSection.size() > 0) {
            thermoSystem.addGasToLiquid(getGasCarryunderFraction());
            liquidSystem = thermoSystem.phaseToSystem(1);
        }
        liquidSystem.setNumberOfPhases(1);
        liquidOutStream.setThermoSystem(liquidSystem);
    }

    @Override
	public String getName() {
        return name;
    }

    public double calcLiquidCarryoverFraction() {
        double Ktot = 1.0;

        for (int i = 0; i < separatorSection.size(); i++) {
            Ktot *= (1.0 - separatorSection.get(i).getEfficiency());
        }
        System.out.println("Ktot " + (1.0 - Ktot));
        double area = getInternalDiameter() * getInternalDiameter() / 4.0 * 3.14;
        double gasVel = thermoSystem.getTotalNumberOfMoles() * thermoSystem.getMolarVolume() / 1e5 / area;
        setLiquidCarryoverFraction(Ktot);
        return gasVel;
    }

    public void runTransient() {
    }
}
