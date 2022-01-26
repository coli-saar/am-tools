package de.saar.coli.amtools.decomposition.formalisms;

import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class EdgeAttachmentHeuristic {

    // need to implement this function, the rest are convenience functions fully derived from this
    public abstract boolean isOutbound(GraphEdge edge);


    /**
     * returns true if and only if the edge is in the blob of the node.
     * @param node
     * @param edge
     * @return
     */
    public boolean isBlobEdge(GraphNode node, GraphEdge edge) {
        return (edge.getSource().equals(node)) && isOutbound(edge)  || (edge.getTarget().equals(node) && !isOutbound(edge) );
    }


    /**
     * returns all edges in the blob of node, in graph.
     * @param graph
     * @param node
     * @return
     */
    public Collection<GraphEdge> getBlobEdges(SGraph graph, GraphNode node) {
        List<GraphEdge> ret = new ArrayList<>();
        for (GraphEdge edge : graph.getGraph().edgesOf(node)) {
            if (isBlobEdge(node, edge)) {
                ret.add(edge);
            }
        }
        return ret;
    }

}
