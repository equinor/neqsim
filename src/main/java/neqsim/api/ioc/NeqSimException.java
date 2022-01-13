/*
 * To change this license header, choose License Headers in Project Properties. To change this
 * template file, choose Tools | Templates and open the template in the editor.
 */
package neqsim.api.ioc;

/**
 *
 * @author jo.lyshoel
 */
public class NeqSimException extends Exception {

    public NeqSimException(String message) {
        super(message);
    }

    public NeqSimException(String message, Throwable cause) {
        super(message, cause);
    }

}
