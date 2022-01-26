/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition.automata.component_analysis;

import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * TODO: could possibly be rewritten to extend GraphNode, which would simplify some code.
 * Edit: tried it, and leads to problems with the GraphNode#equals method,
 * which checks the class (and we would like a DAGNode and a GraphNode to be
 * equal if they have the same name). Changing that method seems potentially dangerous,
 * so I'll leave this as its own class for now.
 * @author JG
 */
public class DAGNode {
    
    
    private final SGraph graph;
    private final EdgeAttachmentHeuristic edgeAttachmentHeuristic;
    private final GraphNode node;
    
    public DAGNode(SGraph graph, GraphNode node, EdgeAttachmentHeuristic edgeAttachmentHeuristic) {
        this.graph = graph;
        this.edgeAttachmentHeuristic = edgeAttachmentHeuristic;
        this.node = node;
    }
    
    public Set<DAGNode> getChildren() {
        Set<DAGNode> ret = new HashSet<>();
        for (GraphEdge e : edgeAttachmentHeuristic.getBlobEdges(graph, node)) {
            ret.add(new DAGNode(graph, GeneralBlobUtils.otherNode(node, e), edgeAttachmentHeuristic));
        }
        return ret;
    } 
    
    @Override
    public String toString() {
        return node.getName()+"/"+node.getLabel().split("~")[0];
    }

    public GraphNode getNode() {
        return node;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.node);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DAGNode other = (DAGNode) obj;
        if (!Objects.equals(this.node, other.node)) {
            return false;
        }
        return true;
    }
    
    

    
    
}
