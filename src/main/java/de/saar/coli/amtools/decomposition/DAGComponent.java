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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.collections.impl.factory.Sets;

/**
 *
 * @author JG
 */
public class DAGComponent {
    
    private final SGraph graph;
    private final GraphNode root;
    private final AMRBlobUtils blobUtils;
    
    private final Set<DAGNode> allNodes;
    private final Map<GraphNode, DAGNode> graphNode2DAGNode;
    private final Map<GraphNode, Set<DAGNode>> node2parents;
    private final Map<GraphNode, Set<DAGNode>> node2ancestors;
    
    public DAGComponent(SGraph graph, GraphNode dagRoot, AMRBlobUtils blobUtils) {
        
        this.graph = graph;
        this.root = dagRoot;
        this.blobUtils = blobUtils;
        
        allNodes = new HashSet<>();
        graphNode2DAGNode = new HashMap<>();
        addRecursive(new DAGNode(graph, root, blobUtils));
        
        node2parents = new HashMap<>();
        setParents();
        node2ancestors = new HashMap<>();
        for (DAGNode node : allNodes) {
            setAncestors(node, 0);
        }
    }
    
    
    private void addRecursive(DAGNode node) {
        if (!allNodes.contains(node)) {
            allNodes.add(node);
            graphNode2DAGNode.put(node.getNode(), node);
            for (DAGNode child : node.getChildren()) {
                addRecursive(child);
            }
        }
    }
    
    
    private void setParents() {
        //initialize empty sets as values
        for (DAGNode node : getAllNodes()) {
            node2parents.put(node.getNode(), new HashSet<>());
        }
        
        for (DAGNode parent : getAllNodes()) {
            for (DAGNode child : parent.getChildren()) {
                node2parents.get(child.getNode()).add(parent);
            }
        }
    }
    
    
    private void setAncestors(DAGNode node, int depth) {
        if (node2ancestors.containsKey(node.getNode())) {
            return;
        }
        if (depth > graph.getGraph().vertexSet().size()+3) {
            // the +3 is just to make sure we don't have an off-by-one error or smth
            System.err.println("Loop detected! Creating DAG failed!");
            System.err.println(node);
            System.err.println(graph);
            return;
        }
        Set<DAGNode> ancestors = new HashSet<>(node2parents.get(node.getNode())); 
        for (DAGNode parent : node2parents.get(node.getNode())) {
            setAncestors(parent, depth+1);//make sure parent ancestors are set before we continue
            ancestors.addAll(node2ancestors.get(parent.getNode()));
        }
        node2ancestors.put(node.getNode(), ancestors);
    }

    
    public GraphNode getRoot() {
        return root;
    }

    
    public Set<DAGNode> getAllNodes() {
        return allNodes;
    }
    
    
    
    
    public Set<GraphEdge> getEdgesTo(Set<GraphNode> nodes) {
        Set<GraphEdge> ret = new HashSet<>();
        for (DAGNode dn : getAllNodes()) {
            for (GraphEdge e : graph.getGraph().edgesOf(dn.getNode())) {
                if (nodes.contains(GeneralBlobUtils.otherNode(dn.getNode(), e))) {
                    ret.add(e);
                }
            }
        }
        return ret;
    }

    public Collection<GraphNode> getNodesWithEdgeTo(Set<GraphNode> nodeSet) {
        Set<GraphNode> ret = new HashSet<>();
        for (DAGNode node : getAllNodes()) {
            for (GraphEdge e : graph.getGraph().edgesOf(node.getNode())) {
                if (nodeSet.contains(e.getTarget()) || nodeSet.contains(e.getSource())) {
                    ret.add(node.getNode());
                    break;//no need to check further edges then
                }
            }
        }
        return ret;
    }

    /**
     * Returns the lowest common ancestor (inclusive, i.e.~treating a node
     * as an ancestor of itself) of the given nodes, or the single node
     * in nodeSet if it is a singleton.
     * @param nodeSet
     * @return 
     */
    public GraphNode getLowestCommonAncestor(Collection<GraphNode> nodeSet) {
        assert getAllAsGraphNodes().containsAll(nodeSet);
        assert !nodeSet.isEmpty();
        
        if (nodeSet.size() == 1) {
            return nodeSet.iterator().next();
        }
        
        
        Set<DAGNode> commonAncestors = getAllNodes();
        for (GraphNode node : nodeSet) {
            Set<DAGNode> ancestorsHereInclusive = Sets.union(node2ancestors.get(node), Collections.singleton(graphNode2DAGNode.get(node)));
            commonAncestors = Sets.intersect(commonAncestors, ancestorsHereInclusive);
        }
        
        assert !commonAncestors.isEmpty();        
        
        // TODO the following seems inefficient or maybe even wrong to me -- JG
        for (DAGNode ancestor : commonAncestors) {
            Set<DAGNode> commonAncestorsExcludingThisOne = new HashSet<>(commonAncestors);
            commonAncestorsExcludingThisOne.remove(ancestor);
            if (node2ancestors.get(ancestor.getNode()).containsAll(commonAncestorsExcludingThisOne)) {
                return ancestor.getNode();
            }
        }
        return null;
    }

    public Collection<GraphNode> getAllAsGraphNodes() {
        return getAllNodes().stream().map(DAGNode::getNode).collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return getAllNodes().toString();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 73 * hash + Objects.hashCode(this.root);
        hash = 73 * hash + Objects.hashCode(this.allNodes);
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
        final DAGComponent other = (DAGComponent) obj;
        if (!Objects.equals(this.root, other.root)) {
            return false;
        }
        if (!Objects.equals(this.allNodes, other.allNodes)) {
            return false;
        }
        return true;
    }
    
    
}
