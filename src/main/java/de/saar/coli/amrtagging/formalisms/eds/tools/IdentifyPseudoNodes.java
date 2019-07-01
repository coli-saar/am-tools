/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds.tools;

import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.GraphStats;
import de.saar.coli.amrtagging.formalisms.amr.tools.ReadRawCorpus;
import de.saar.coli.amrtagging.formalisms.eds.Aligner;
import de.saar.coli.amrtagging.formalisms.eds.EDSConverter;
import de.saar.coli.amrtagging.formalisms.eds.EDSUtils;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Takes a raw ISI-AMR style corpus (which is used for EDS) and checks if there are NODES that should actually be edges (always two outgoing edges, never any incoming ones).
 * @author matthias
 */
public class IdentifyPseudoNodes {
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        String path = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/meine_Daten/EDS-toy/train.amr.txt";
        List<AnchoredSGraph> allGraphs = ReadRawCorpus.readGraphs(path);
        HashMap<String,Integer> pseudoNodeOcc = new HashMap<String,Integer>();
        HashMap<String,Integer> nonPseudoNodeOcc = new HashMap<String,Integer>();
        for (SGraph g : allGraphs){
            for (GraphNode n : g.getGraph().vertexSet()){
                long odeg = g.getGraph().outgoingEdgesOf(n).stream().filter(e -> ! e.getLabel().equals(AnchoredSGraph.LNK_LABEL)).count();
                if (odeg == 2 && g.getGraph().inDegreeOf(n) == 0){
                    pseudoNodeOcc.put(n.getLabel(), 1+pseudoNodeOcc.getOrDefault(n.getLabel(), 0));
                } else {
                    nonPseudoNodeOcc.put(n.getLabel(), 1+nonPseudoNodeOcc.getOrDefault(n.getLabel(), 0));
                }
            }
       }
        
      for (String label : pseudoNodeOcc.keySet()){
          if (pseudoNodeOcc.get(label) > 100 && pseudoNodeOcc.get(label) > 2*nonPseudoNodeOcc.getOrDefault(label,0)){
              System.err.println(pseudoNodeOcc.get(label)/(nonPseudoNodeOcc.getOrDefault(label,0)+pseudoNodeOcc.get(label)+0.0)+"\t"+pseudoNodeOcc.get(label)+"\t"+label);
          }
          
      }
        
    }
    
}
