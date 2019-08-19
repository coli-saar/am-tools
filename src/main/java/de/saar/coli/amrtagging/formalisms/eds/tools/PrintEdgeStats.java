/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds.tools;

import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.formalisms.GraphStats;
import de.saar.coli.amrtagging.formalisms.amr.tools.ReadRawCorpus;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes a raw ISI-AMR style corpus (which is used for EDS) and counts all edges in it.
 * Works for AMR and EDS.
 * @author JG
 */
public class PrintEdgeStats {
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
                
        List<AnchoredSGraph> allGraphs = ReadRawCorpus.readGraphs(args[0]);
        List<SGraph> allGs = new ArrayList<>();
        for (AnchoredSGraph sg : allGraphs){
            allGs.add(sg);
        }
        GraphStats.printEdgeStats(allGs);
    }
    
}
