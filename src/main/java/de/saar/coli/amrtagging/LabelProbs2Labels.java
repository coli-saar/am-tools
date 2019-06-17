/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.saar.basic.Pair;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Extracts the highest-scoring labels from a file with label probabilities (e.g.~produced by neural tagger).
 * 
 * @author Jonas
 */
public class LabelProbs2Labels {
    
    /**
     * First argument is the folder path, second argument is the prefix to the probs file
     * (e.g.~label for labelProbs.txt), third argument should be true iff null tokens are allowed.
     * @param args
     * @throws IOException 
     */
    public static void main(String[] args) throws IOException {
        String type = args[1];
        
        Reader r = new FileReader(args[0]+args[1]+"Probs.txt");        
        List<List<List<AnnotatedSupertag>>> labelProbs = Util.readSupertagProbs(r, true);
        
        boolean useNull = Boolean.parseBoolean(args[2]);
        
        FileWriter w = new FileWriter(args[0]+args[1]+"s.txt");
        
        for (List<List<AnnotatedSupertag>> sent : labelProbs) {
            StringJoiner sj = new StringJoiner(" ");
            for (List<AnnotatedSupertag> word : sent) {
                if (!word.isEmpty()) {
                    List<AnnotatedSupertag> sorted = new ArrayList<>(word);
                    sorted.sort((AnnotatedSupertag o1, AnnotatedSupertag o2) -> -Double.compare(o1.probability, o2.probability));
                    String label = sorted.get(0).graph;
                    if (!useNull && label.equals("NULL") && sorted.size() > 1) {
                        label = sorted.get(1).graph;
                    }
                    sj.add(label);
                } else {
                    System.err.println("***WARNING*** empty label list for a word!");
                }
            }
            w.write(sj.toString()+"\n");
        }
        w.close();
    }
    
}
