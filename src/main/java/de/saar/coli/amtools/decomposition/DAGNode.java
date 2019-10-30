/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.HashSet;
import java.util.Set;

/**
 * TODO: could possibly be rewritten to extend GraphNode, which would simplify some code.
 * @author JG
 */
public class DAGNode extends GraphNode {
    
    
    private final SGraph graph;
    private final AMRBlobUtils blobUtils;
    
    public DAGNode(SGraph graph, GraphNode node, AMRBlobUtils blobUtils) {
        super(node.getName(), node.getLabel());
        this.graph = graph;
        this.blobUtils = blobUtils;
    }
    
    public Set<DAGNode> getChildren() {
        Set<DAGNode> ret = new HashSet<>();
        for (GraphEdge e : blobUtils.getBlobEdges(graph, this)) {
            ret.add(new DAGNode(graph, GeneralBlobUtils.otherNode(this, e), blobUtils));
        }
        return ret;
    } 
    
    @Override
    public String toString() {
        return getName()+"/"+getLabel().split("~")[0];
    }

    
    
}
