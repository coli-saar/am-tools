/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms;

import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;

/**
 *
 * @author matthias
 */
public class GeneralBlobUtils {
    
    
        /**
     * Only applicable if edge is incident to node. If so, returns the other node incident to edge.
     * @param node
     * @param edge
     * @return 
     */
    public static GraphNode otherNode(GraphNode node, GraphEdge edge) {
        return (edge.getSource().equals(node)) ? edge.getTarget() : edge.getSource();
    }
    
}
