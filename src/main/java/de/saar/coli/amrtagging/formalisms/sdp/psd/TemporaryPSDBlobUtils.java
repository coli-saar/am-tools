/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd;

import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;

/**
 *
 * @author matthias
 */
public class TemporaryPSDBlobUtils extends AMRBlobUtils {

    @Override
    public boolean isConjEdgeLabel(String edgeLabel) {
        return edgeLabel != null && edgeLabel.endsWith(".member");
    }

    @Override
    public boolean isConjunctionNode(SGraph graph, GraphNode node) {
        return graph.getGraph().outDegreeOf(node) > 0 && graph.getGraph().outgoingEdgesOf(node).stream().allMatch(e -> isConjEdgeLabel(e.getLabel()));
    }
    
    
    
    
}
