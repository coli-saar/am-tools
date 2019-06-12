/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.ucca;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;

/**
 *
 * @author JG
 */
public class UCCABlobUtils extends AMRBlobUtils {
    
    
    
    @Override
    public boolean isOutbound(GraphEdge edge) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public String edge2Source(GraphEdge edge, SGraph graph){
        throw new UnsupportedOperationException();
    }
    
    @Override
    public boolean isConjEdgeLabel(String edgeLabel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConjunctionNode(SGraph graph, GraphNode node) {
        throw new UnsupportedOperationException();
    }
    
    
}
