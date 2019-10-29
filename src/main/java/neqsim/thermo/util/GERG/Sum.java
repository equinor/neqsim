package neqsim.thermo.util.GERG;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author esol
 */
public class Sum {
    
     public static double sum(double[] values, int a) {
   double result = 0;
   for (double value:values)
     result += value;
   return result;
 }

  public static double sum(double[] values1, double[] values2) {
   double result = 0;
   int k = values1.length;
   for (int i=0;i<k;i++)
     result += values1[i]*values2[i];
   return result;
 }
}
