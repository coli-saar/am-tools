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
     public static final String[] OUTBOUND_EDGES = new String[]{"C", "P", "S", "A", "N", "H", "L", "R", "LR", "LA"};
    
    
    @Override
    public boolean isOutbound(GraphEdge edge) {
        //edge.getLabel()
        //throw new UnsupportedOperationException();
        //will this work??
        for (String regex : OUTBOUND_EDGES) {
            if (edge.getLabel().matches(regex)) {
                return true;
            }
        }
        return false;   
    }
    
    
    @Override
    public String edge2Source(GraphEdge edge, SGraph graph){
        if (edge.getLabel().startsWith("A")) {
           return "A" ;
         } else return "MOD";
             
    }
    
    @Override
    public boolean isConjEdgeLabel(String edgeLabel) {
        //throw new UnsupportedOperationException();
        return false;
        
    }

    @Override
    public boolean isConjunctionNode(SGraph graph, GraphNode node) {
        //throw new UnsupportedOperationException();
        return false;
    }
    
    
}
