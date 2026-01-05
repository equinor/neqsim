package neqsim.process.processmodel;

/**
 * Exception thrown when there is an error reading a DEXPI XML file.
 */
public class DexpiXmlReaderException extends Exception {

    private static final long serialVersionUID = 1L;

    public DexpiXmlReaderException(String message) {
        super(message);
    }

    public DexpiXmlReaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
