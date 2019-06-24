/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.utils;

import de.saar.coli.amrtagging.mrp.graphs.MRPEdge;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPAnchor;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 *
 * @author matthias
 */
public class MRPUtils {
    
    public static final String ART_ROOT = "ART-ROOT";
    public static final String ROOT_EDGE_LABEL = "art-snt";
    
    
    /**
     * Returns alignment between graph and sentence assuming that every TokenRange in the graph
     * comes from some TokenRange in the string. Assumes a single MRPAnchor for every node.
     * @param sent
     * @param mrpGraph
     * @return 
     */
//    public static TODO extractPerfectAlignment(ConlluSentence sent, MRPGraph mrpGraph){
//        
//    }
    
    /**
     * Converts an MRPGraph with single top node to an SGraph, not taking any anchoring into account.
     * @param mrpGraph
     * @return 
     */
    public static SGraph toSGraphWoAnchoring(MRPGraph mrpGraph){
        SGraph g = new SGraph();
        for (MRPNode n : mrpGraph.getNodes()){
            g.addNode("n"+n.getId(), n.getLabel());
        }
        if (mrpGraph.getTops().size() != 1){
            throw new IllegalArgumentException("Graph "+mrpGraph.getId()+" of framework "+mrpGraph.getFramework()+" doesn't have single top node");
        }
        int top = mrpGraph.getTops().stream().findFirst().get();
        g.addSource("root", "n"+top);
        
        for (MRPNode n : mrpGraph.getNodes()){
            if (!n.getProperties().isEmpty()){
                throw new IllegalArgumentException("MRP graph cannot be converted to s-graph, the MRP graph still contains node properties");
            }
        }
        
        return g;
    }
    
    /**
     * Adds an additional token (ART-ROOT) that unifies all top nodes and makes the graph connected.
     * @param sent
     * @param mrpGraph 
     */
    public static void addArtificalRoot(ConlluSentence sent, MRPGraph mrpGraph){
        // How does it work?
        // we add an additional node to the graph
        // we go over all connected components and draw an edge to the top node
        // if there is no top node, we read of the head of the span the component comprises from the companion data
        // and draw the edge into the corresponding node
        
        // Create ART-ROOT conllu entry
        ConlluEntry artRoot = new ConlluEntry(sent.size()+1,ART_ROOT);
        artRoot.setLemma(ART_ROOT);
        artRoot.setPos(ART_ROOT);
        artRoot.setUPos(ART_ROOT);
        artRoot.setHead(ConlluEntry.NOID);
        TokenRange lastTokenRange = (TokenRange) sent.get(sent.size()-1).getTokenRange();
        int start = lastTokenRange.getFrom()+1;
        int end = start + ART_ROOT.length();
        artRoot.setTokenRange(new TokenRange(start, end));
        
        sent.add(artRoot);
        //mrpgraph.getTops()
        //Identify unconnected components in MRP graph
        MRPGraph copy = mrpGraph.deepCopy();
  
        ConnectivityInspector<MRPNode, MRPEdge> inspector =  new ConnectivityInspector(copy.asDirectedGraph());
        //Sentence sent = null;
        int sntCounter = 1;
        Set<Integer> tops = copy.getTops();
        int artRootId = mrpGraph.obtainAvailableId();
        List<MRPAnchor> rootAnchors = new ArrayList<>();
        rootAnchors.add(new MRPAnchor(start, end));
        MRPNode artRootNode = new MRPNode(artRootId,ART_ROOT,new ArrayList<>(),new ArrayList<>(),rootAnchors);
        mrpGraph.getNodes().add(artRootNode);
        
        mrpGraph.setInput(mrpGraph.getInput()+" "+ART_ROOT);
        Set<Integer> newTop = new HashSet<>();
        newTop.add(artRootId);
        mrpGraph.setTops(newTop);
        for (Set<MRPNode> connectedSet : inspector.connectedSets()){
                boolean foundTop = false;
              
                for (MRPNode n : connectedSet){
                    if (tops.contains(n.getId())) { //n is a top node
                        mrpGraph.getEdges().add(new MRPEdge(artRootId,n.getId(),ROOT_EDGE_LABEL+sntCounter));
                        //sg.addEdge(sg.getNode(ARTIFICAL_ROOT_LABEL), sg.getNode(PREFIX+String.valueOf(n.id)), ROOT_EDGE_LABEL+String.valueOf(sntCounter));
                        foundTop = true;
                     }
                }
                // if the component didn't have top node find the head of the span (companion data) of this component
                // (assumes this component of the graph forms a contiguous span)
                // and make it the root of this subgraph
                if (! foundTop){
                    //throw new IllegalArgumentException("This graph is connected please comment in the corenlp stuff.");
                    MRPAnchor firstPos = minAnchorStart(connectedSet);
                    MRPAnchor lastPos = maxAnchorEnd(connectedSet);
                    
                    int head = sent.headOfSpan(sent.getCorrespondingIndex(TokenRange.fromAnchor(firstPos)),
                                sent.getCorrespondingIndex(TokenRange.fromAnchor(lastPos)));
                    TokenRange headRange = sent.get(head).getTokenRange();
                    
                    if (copy.tokenRanges().contains(headRange)){
                        Set<MRPNode> topNodes = mrpGraph.getNodesForAnchor(MRPAnchor.fromTokenRange(headRange));
                        if (topNodes.size() != 1){
                            System.err.println("WARNING: Expected to find one node with anchor "+headRange+ " but found "+topNodes.size()+" in "+mrpGraph.getId());
                        }
                        MRPNode topNode = topNodes.stream().findAny().get();
                        mrpGraph.getEdges().add(new MRPEdge(artRootId,topNode.getId(),ROOT_EDGE_LABEL+sntCounter));
                    } else {
                       System.err.println("WARNING: TokenRange of head ("+headRange.toString()+") not present in graph "+mrpGraph.getId()+" -- take arbitrary root for component");
                       MRPNode someNode = connectedSet.stream().findAny().get();
                       mrpGraph.getEdges().add(new MRPEdge(artRootId, someNode.getId(), ROOT_EDGE_LABEL+sntCounter));
                    }
                }
                sntCounter++;
        }
    }
    
    /**
     * Returns the position of the first character described by the anchoring of any node in the list.
     * @param nodes
     * @return 
     */
    private static MRPAnchor minAnchorStart(Collection<MRPNode> nodes){
        int m = Integer.MAX_VALUE;
        MRPAnchor anchor = null;
        for (MRPNode n : nodes){
            for (MRPAnchor a : n.getAnchors()){
                if (a.from <= m){
                    m = a.from;
                    anchor = a;
                }
            }
        }
        return anchor;
    }
    
    private static MRPAnchor maxAnchorEnd(Collection<MRPNode> nodes){
        int m = Integer.MIN_VALUE;
        MRPAnchor anchor = null;
        for (MRPNode n : nodes){
            for (MRPAnchor a : n.getAnchors()){
                if (a.to >= m){
                    m = a.to;
                    anchor = a;
                }
            }
        }
        return anchor;
    }
    

    
    /**
     * Removes the artificial root.
     * @param mrpGraph
     * @return 
     */
    public static MRPGraph removeArtificalRoot(MRPGraph mrpGraph){
        if (mrpGraph.getTops().size() != 1){
            throw new IllegalArgumentException("Can only remove artificial root if it's there.");
        }
        MRPGraph copy = mrpGraph.deepCopy();
        int rootId = copy.getTops().stream().findFirst().get();
        
        for (MRPEdge outg : copy.outgoingEdges(rootId)){
            copy.getEdges().remove(outg); //remove art-snt* edge.
            copy.getTops().add(outg.target); //add a new top node.
        }
        //finally, remove ART-ROOT node:
        copy.getNodes().remove(copy.getNode(rootId));
        copy.getTops().removeIf(topNode -> topNode == rootId);
        copy.setInput(copy.getInput().substring(0, copy.getInput().length() - ART_ROOT.length() -1 )); //-1 for space before ART_ROOT
        return copy;
        
    }
    
}
