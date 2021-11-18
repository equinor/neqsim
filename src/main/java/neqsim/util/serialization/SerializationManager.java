/*
 * SerializationManager.java
 *
 * Created on 27. desember 2002, 00:10
 */

package neqsim.util.serialization;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 *
 * @author ESOL
 */
public class SerializationManager {
    private static final long serialVersionUID = 1000;

    /** Creates a new instance of SerializationManager */
    public SerializationManager() {}

    public static void save(Object obj, String name) {
        FileOutputStream fout = null;
        ObjectOutputStream out = null;
        try {
            fout = new FileOutputStream(name);
            out = new ObjectOutputStream(fout);
            out.writeObject(obj);
            out.close();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public static Object open(String name) {
        FileInputStream fin = null;
        ObjectInputStream in = null;
        try {
            fin = new FileInputStream(name);
            in = new ObjectInputStream(fin);
            return in.readObject();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        return null;
    }
}
