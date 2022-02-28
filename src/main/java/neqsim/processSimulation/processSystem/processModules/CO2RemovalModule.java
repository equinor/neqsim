package neqsim.processSimulation.processSystem.processModules;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.processSimulation.processSystem.ProcessModuleBaseClass;

/**
 * <p>
 * CO2RemovalModule class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CO2RemovalModule extends ProcessModuleBaseClass {
    private static final long serialVersionUID = 1000;

    protected StreamInterface streamToAbsorber = null, streamFromAbsorber = null,
            gasFromCO2Stripper = null;

    protected Separator inletSeparator = null;

    public CO2RemovalModule(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void addInputStream(String streamName, StreamInterface stream) {
        if (streamName.equals("streamToAbsorber")) {
            this.streamToAbsorber = stream;
        }
    }

    /** {@inheritDoc} */
    @Override
    public StreamInterface getOutputStream(String streamName) {
        if (!isInitializedStreams) {
            initializeStreams();
        }
        if (streamName.equals("streamFromAbsorber")) {
            return this.streamFromAbsorber;
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (!isInitializedModule) {
            initializeModule();
        }
        getOperations().run();

        streamFromAbsorber = (Stream) inletSeparator.getGasOutStream().clone();
        streamFromAbsorber.getThermoSystem().addComponent("CO2",
                -streamFromAbsorber.getThermoSystem().getPhase(0).getComponent("CO2")
                        .getNumberOfMolesInPhase() * 0.99);
        streamFromAbsorber.getThermoSystem().addComponent("MEG",
                -streamFromAbsorber.getThermoSystem().getPhase(0).getComponent("MEG")
                        .getNumberOfMolesInPhase() * 0.99);
        streamFromAbsorber.getThermoSystem().init(1);
    }

    /** {@inheritDoc} */
    @Override
    public void initializeStreams() {
        isInitializedStreams = true;
        try {
            this.streamFromAbsorber = (Stream) this.streamToAbsorber.clone();
            this.streamFromAbsorber.setName("Stream from ABsorber");

            this.gasFromCO2Stripper = (Stream) this.streamToAbsorber.clone();
            this.gasFromCO2Stripper.setName("Gas stream from Stripper");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void initializeModule() {
        isInitializedModule = true;
        inletSeparator = new Separator("inletSeparator", streamToAbsorber);

        getOperations().add(inletSeparator);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt) {
        getOperations().runTransient(dt);
    }

    /** {@inheritDoc} */
    @Override
    public void calcDesign() {
        // design is done here
    }

    /** {@inheritDoc} */
    @Override
    public void setDesign() {
        // set design is done here
    }
}
