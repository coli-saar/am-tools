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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final Map<GraphNode, Set<DAGNode>> node2parents;
    private final Map<GraphNode, Set<DAGNode>> node2ancestors;
    
    public DAGComponent(SGraph graph, GraphNode dagRoot, AMRBlobUtils blobUtils) {
        
        this.graph = graph;
        this.root = dagRoot;
        this.blobUtils = blobUtils;
        
        allNodes = new HashSet<>();
        addRecursive(new DAGNode(graph, root, blobUtils));
        
        node2parents = new HashMap<>();
        setParents();
        node2ancestors = new HashMap<>();
        for (DAGNode node : allNodes) {
            setAncestors(node);
        }
    }
    
    
    private void addRecursive(DAGNode node) {
        if (!allNodes.contains(node)) {
            allNodes.add(node);
            for (DAGNode child : node.getChildren()) {
                addRecursive(child);
            }
        }
    }
    
    
    private void setParents() {
        //initialize empty sets as values
        for (DAGNode node : getAllNodes()) {
            node2parents.put(node, new HashSet<>());
        }
        
        for (DAGNode parent : getAllNodes()) {
            for (DAGNode child : parent.getChildren()) {
                node2parents.get(child).add(parent);
            }
        }
    }
    
    
    private void setAncestors(DAGNode node) {
        if (node2ancestors.containsKey(node)) {
            return;
        }
        Set<DAGNode> ancestors = new HashSet<>(node2parents.get(node)); 
        for (DAGNode parent : node2parents.get(node)) {
            setAncestors(parent);//make sure parent ancestors are set before we continue
            ancestors.addAll(node2ancestors.get(parent));
        }
        node2ancestors.put(node, ancestors);
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
            for (GraphEdge e : graph.getGraph().edgesOf(dn)) {
                if (nodes.contains(GeneralBlobUtils.otherNode(dn, e))) {
                    ret.add(e);
                }
            }
        }
        return ret;
    }

    public Collection<GraphNode> getNodesWithEdgeTo(Set<GraphNode> nodeSet) {
        Set<GraphNode> ret = new HashSet<>();
        for (GraphNode node : getAllNodes()) {
            for (GraphEdge e : graph.getGraph().edgesOf(node)) {
                if (nodeSet.contains(e.getTarget()) || nodeSet.contains(e.getSource())) {
                    ret.add(node);
                    break;//no need to check further edges then
                }
            }
        }
        return ret;
    }

    public GraphNode getLowestCommonAncestor(Collection<GraphNode> nodeSet) {
        assert getAllNodes().containsAll(nodeSet);//this contains check works because the equality check of DAGNode is the same as the one for GraphNode (checking node name)
        
        Set<DAGNode> commonAncestors = getAllNodes();
        for (GraphNode node : nodeSet) {
            commonAncestors = Sets.intersect(commonAncestors, node2ancestors.get(node));
        }
        
        assert !commonAncestors.isEmpty();        
        
        
        
    }
    
    
}
