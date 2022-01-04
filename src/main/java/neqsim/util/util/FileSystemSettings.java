/*
 * FileSystemSettings.java
 *
 * Created on 3. november 2001, 17:55
 */

package neqsim.util.util;

/**
 * <p>FileSystemSettings interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FileSystemSettings {
    /** Constant <code>root="c:"</code> */
    String root = "c:";
    /** Constant <code>tempDir="root + /temp/"</code> */
    String tempDir = root + "/temp/";
    /** Constant <code>defaultFileTreeRoot="root + /Program Files/NeqSim/pythonScri"{trunked}</code> */
    String defaultFileTreeRoot = root + "/Program Files/NeqSim/pythonScript/";
    /** Constant <code>defaultDatabaseRootRoot="root + /java/NeqSim/util/database"</code> */
    String defaultDatabaseRootRoot = root + "/java/NeqSim/util/database";
    /** Constant <code>relativeFilePath="root"</code> */
    String relativeFilePath = root;
    /** Constant <code>fileExtension=".py"</code> */
    String fileExtension = ".py";
}
