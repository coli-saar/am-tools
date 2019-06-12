/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jgrapht.graph.DirectedMultigraph;

/**
 * A class for pre- and postprocessing that transforms conjunctions in PSD to a form that ressembles AMR (and back).
 * @author matthias
 */
public class ConjHandler {
    
    public static final HashSet<String> CONJ_EDGES = new HashSet<>(Arrays.asList("CONJ.member"));
    
    public static SGraph restoreConj(SGraph g, PSDBlobUtils blobUtils){
        SGraph before = g;
        before.setEqualsMeansIsomorphy(true);
        SGraph after = restoreConjIteration(before, blobUtils);
        while (before.getGraph().edgeSet().size() < after.getGraph().edgeSet().size()
                && before.getGraph().edgeSet().size() < 10000) {
             // the <10000 check is just so the loop is guaranteed to finish
            before = after;
            before.setEqualsMeansIsomorphy(true);
            after = restoreConjIteration(after, blobUtils);
        }
        return after;
    }
    
    public static SGraph restoreConjIteration(SGraph g, PSDBlobUtils blobUtils){
        SGraph output = g.merge(new SGraph()); //make copy of g
        DirectedMultigraph<GraphNode,GraphEdge> graph = output.getGraph();
        
        for (GraphNode conjunctionNode : graph.vertexSet()){
            if (blobUtils.isConjunctionNode(g, conjunctionNode)){
                for (GraphEdge e : g.getGraph().edgesOf(conjunctionNode)){
                    if (!blobUtils.isBlobEdge(conjunctionNode, e) 
                            && ! blobUtils.isConjEdgeLabel(e.getLabel())) { // don't redistribute conj labels
                        for (GraphNode child : getConjChildren(g.getGraph(), conjunctionNode, blobUtils)){
                            GraphNode predicate = GeneralBlobUtils.otherNode(conjunctionNode, e);
                            GraphEdge newEdge;
                            if (!conjunctionNode.equals(e.getSource())) {
                                newEdge = new GraphEdge(predicate,child,e.getLabel());
                                graph.addEdge(predicate, child, newEdge);
                            } else {
                                newEdge = new GraphEdge(child,predicate,e.getLabel());
                                graph.addEdge(child,predicate, newEdge);
                            }
                            
                        }
                        graph.removeEdge(e);
                    }
                }
            }
        }
        
        return output;
    }
    
    public static SGraph handleConj(SGraph g, PSDBlobUtils blobUtils) throws IllegalArgumentException{
        SGraph before = g;
        before.setEqualsMeansIsomorphy(true);
        SGraph after = handleConjIteration(before, blobUtils);
        while (before.getGraph().edgeSet().size() > after.getGraph().edgeSet().size()) {
            before = after;
            before.setEqualsMeansIsomorphy(true);
            after = handleConjIteration(after, blobUtils);
        }
        return after;
    }
    
    public static SGraph handleConjIteration(SGraph g, PSDBlobUtils blobUtils) throws IllegalArgumentException{
        SGraph output = g.merge(new SGraph()); //make copy of g
        DirectedMultigraph<GraphNode,GraphEdge> graph = output.getGraph();
        for (GraphNode conjunctionNode : graph.vertexSet()){
            if (blobUtils.isConjunctionNode(output, conjunctionNode)){ //found a conjunction conjunctionNode
                Map<Pair<GraphNode, String>, HashSet<GraphEdge>> matchingEdges = new HashMap<>();
                boolean firstTarget = true;
                for (GraphNode target : getConjChildren(graph ,conjunctionNode, blobUtils)){ //go over the conjoined nodes 
                    Map<Pair<GraphNode, String>, GraphEdge> edgesHere = new HashMap<>();
                    for (GraphEdge e : graph.edgesOf(target)){
                        if (!blobUtils.isBlobEdge(target, e)) {
                            GraphNode other = GeneralBlobUtils.otherNode(target, e);
                            if (!other.equals(conjunctionNode)){
                                edgesHere.put(new Pair(other, e.getLabel()), e);
                            }
                        }
                    }
                    if (firstTarget) {
                        for (Entry<Pair<GraphNode, String>, GraphEdge> entry : edgesHere.entrySet()) {
                            // add all entries to matchingEdges, but replace the value with a set that contains (just) the value.
                            HashSet<GraphEdge> set = new HashSet<>();
                            set.add(entry.getValue());
                            matchingEdges.put(entry.getKey(), set);
                        }
                        firstTarget = false;
                    } else {
                        for (Pair<GraphNode, String> key : new HashSet<>(matchingEdges.keySet())) {
                            // for all node + edge label pairs (n,l) where we so far have have an edge from n with label l to each visited target
                            if (edgesHere.containsKey(key)) {
                                // if we have such an edge to this target too, keep the key and add the current edge to the set of matching edges, to keep track
                                matchingEdges.get(key).add(edgesHere.get(key));
                            } else {
                                // then not all children have such an edge, and we leave such edges untouched in the graph (i.e. we remove the key)
                                matchingEdges.remove(key);
                            }
                        }
                    }
                }
                for (Pair<GraphNode, String> key : matchingEdges.keySet()) {
                    GraphNode predicate = key.left;
                    String edgeLabel = key.right;
                    Set<GraphEdge> edges = matchingEdges.get(key);
                    GraphEdge exampleEdge = edges.iterator().next();
                    GraphEdge newEdge;
                    if (predicate.equals(exampleEdge.getSource())) {
                        newEdge = new GraphEdge(predicate,conjunctionNode,edgeLabel);
                    } else {
                        newEdge = new GraphEdge(conjunctionNode,predicate,edgeLabel);
                    }
                    graph.addEdge(key.left, conjunctionNode, newEdge);
                    graph.removeAllEdges(edges);
                }
            }
        }
        return output;
    }
    
    
    private static Set<GraphNode> getConjChildren(DirectedMultigraph<GraphNode,GraphEdge> g,GraphNode conj, PSDBlobUtils blobUtils){
        HashSet<GraphNode> ret = new HashSet<>();
        for (GraphEdge e : g.edgesOf(conj)){
            if (e.getSource().equals(conj) && blobUtils.isConjEdgeLabel(e.getLabel())){
                ret.add(e.getTarget());
            }
        }
        return ret;
    }
    
    
    
}
