/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms;

import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import java.util.Collection;

/**
 *
 * @author JG
 */
public class GraphStats {
    
    public static void printEdgeStats(Collection<SGraph> graphs) {
        Counter<String> labelCounter = new Counter<>();
        for (SGraph graph : graphs) {
            for (GraphEdge edge : graph.getGraph().edgeSet()) {
                labelCounter.add(edge.getLabel());
            }
        }
        labelCounter.printAllSorted();
    }
    
}
