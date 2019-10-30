/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 *
 * @author JG
 */
public class ConnectedComponent {
    
    private final Set<GraphNode> allNodes;
    
    /**
     * Creates the connected component of "graph", where the "removed" set of nodes
     * is removed, which contains the node "seed".
     * @param graph
     * @param seed
     * @param removed 
     */
    public ConnectedComponent(SGraph graph, GraphNode seed, Collection<? extends GraphNode> removed) {
        allNodes = new HashSet<>();
        addRecursive(seed, graph, removed);
    }
    
    /**
     * Creates a ConnectedComponent that consists of the given set of nodes.
     * Use at own risk, this does not check for connectivity etc.
     * @param nodes 
     */
    public ConnectedComponent(Set<GraphNode> nodes) {
        this.allNodes = nodes;
    }
    
    private void addRecursive(GraphNode node, SGraph graph, Collection<? extends GraphNode> removed) {
        allNodes.add(node);
        for (GraphEdge e : graph.getGraph().edgesOf(node)) {
            GraphNode other = GeneralBlobUtils.otherNode(node, e);
            if (!removed.contains(other) && !allNodes.contains(other)) {
                addRecursive(other, graph, removed);
            }
        }
    }

    public Set<GraphNode> getAllNodes() {
        return allNodes;
    }
    
    public int size() {
        return getAllNodes().size();
    }
    
    public static Collection<ConnectedComponent> getAllConnectedComponents(SGraph graph, Collection<? extends GraphNode> removed) {
        List<ConnectedComponent> ret = new ArrayList<>();
        Set<GraphNode> covered = new HashSet<>();
        for (GraphNode node : graph.getGraph().vertexSet()) {
            if (!covered.contains(node) && !removed.contains(node)) {
                ConnectedComponent comp = new ConnectedComponent(graph, node, removed);
                ret.add(comp);
                covered.addAll(comp.getAllNodes());
            }
        }
        return ret;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.allNodes);
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
        final ConnectedComponent other = (ConnectedComponent) obj;
        if (!Objects.equals(this.allNodes, other.allNodes)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (GraphNode node : getAllNodes()) {
            sj.add(node.getName()+"/"+node.getLabel().split("~")[0]);
        }
        return sj.toString();
    }
    
    
    
}
