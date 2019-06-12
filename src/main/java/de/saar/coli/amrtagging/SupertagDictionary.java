/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Hashtable; //synchronized collection

/**
 * Class that helps make consistent strings for supertags. TODO: check if there are problems with synchronization.
 * @author matthias
 */
public class SupertagDictionary {
     private Hashtable<SGraph, String> cache;
     public Hashtable<SGraph, Integer> counts = new Hashtable<>();
   
   
   public SupertagDictionary(){
     cache = new Hashtable<>();
   }
   

   
   public synchronized boolean contains(SGraph dec) throws Exception {
     return cache.containsKey(dec);
   }
   
   /**
   * Returns a string representation of the given graph,type pair and updates the dictionary. If an isomorphic graph gets passed to this method later, the same string will be returned.
     * @param dec
     * @return 
   */
   public synchronized String getRepr(SGraph dec) {
     if (cache.containsKey(dec)) {
       counts.put(dec, counts.get(dec)+1);
       return cache.get(dec);
     }
     String r = dec.toIsiAmrStringWithSources();
     cache.put(dec,r);
     counts.put(dec,1);
     return r;
   }
   
   /**
    * Writes the dictionary to a file with given filename for later reuse.
    * @param filename
    * @throws FileNotFoundException 
    */
   public synchronized void writeToFile(String filename) throws FileNotFoundException{
     PrintWriter pw = new PrintWriter(filename);
     for (SGraph sg : cache.keySet()){
       pw.println(cache.get(sg));
     }
     pw.close();
   }
   
   /**
    * Re-creates the state of the dictionary from a given file.
    * @param filename
    * @throws FileNotFoundException
    * @throws IOException
    * @throws de.up.ling.irtg.algebra.ParserException 
    */
   public void readFromFile(String filename) throws FileNotFoundException,IOException, de.up.ling.irtg.algebra.ParserException{
     FileInputStream fis = new FileInputStream(filename);
     BufferedReader br = new BufferedReader(new InputStreamReader(fis));
     GraphAlgebra ga = new GraphAlgebra();
     while (true){
       String line = br.readLine();
       if (line == null) break;
       SGraph sg = ga.parseString(line);
       cache.put(sg,line);
       counts.put(sg,0);
     }
     br.close();
   }
  
  
 
}
