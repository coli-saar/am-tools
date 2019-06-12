/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.dm.tools;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.GraphStats;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

/**
 * Counts all edge labels in a .sdp file for DM. Also works for PDS and PAS.
 * @author JG
 */
public class PrintEdgeStats {
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        GraphReader2015 gr = new GraphReader2015(args[0]);
        Graph sdpGraph;
        
        List<SGraph> allGraphs = new ArrayList<>();
        while ((sdpGraph = gr.readGraph()) != null){
            MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
            allGraphs.add(inst.getGraph());
        }
        
        GraphStats.printEdgeStats(allGraphs);
    }
    
}
