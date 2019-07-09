/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.utils;

import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.TokenRange;
import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.Util;
import static de.saar.coli.amrtagging.Util.fixPunct;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.mrp.graphs.MRPEdge;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPAnchor;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 *
 * @author matthias
 */
public class MRPUtils {
    
    public static final String ART_ROOT = SGraphConverter.ARTIFICAL_ROOT_LABEL;
    public static final String ROOT_EDGE_LABEL = SGraphConverter.ROOT_EDGE_LABEL;
    public static final String NODE_PREFIX = "n";
    public static final String ANCHOR_PREFIX = "a";
    public static final String LNK_PATTERN ="<([0-9]+):([0-9]+)>";
    
    public static final String SEPARATOR ="__"; //separator between node label and features
    public static final String EQUALS = "="; //separator between feature name and value
    
    

    
    public static MRPGraph getDummy(String framework, String id, String sentence, String time, String version) throws IllegalArgumentException{
        MRPGraph g = new MRPGraph();
        g.setFramework(framework);
        if (framework.equals("amr")){
            g.setFlavor(2);
        } else if (framework.equals("dm") || framework.equals("psd")){
            g.setFlavor(0);
        } else if (framework.equals("eds") || framework.equals("ucca")){
            g.setFlavor(1);
        } else {
            throw new IllegalArgumentException("Unknown framework \""+framework+"\"");
        }
        g.setId(id);
        g.setInput(sentence);
        g.setTime(time);
        g.setVersion(version);
        g.sanitize();
        return g;
    }
    
    
    public static final String mrpIdToSGraphId(int id){
        return NODE_PREFIX+id;
    }
    
    
    /**
     * Converts back to MRPGraph, no anchoring is used.
     * @param sg
     * @param propertyNames a list of regular expressions that match on edge labels which are actually properties (and the node label at the target is the value)
     * @param flavor
     * @param framework
     * @param graphId
     * @param raw
     * @param version
     * @param time
     * @return 
     */
    public static MRPGraph fromSGraph(SGraph sg, List<Pattern> propertyNames, int flavor, String framework, String graphId, String raw, String version, String time){
        MRPGraph output = new MRPGraph();
        output.sanitize();
        output.setId(graphId);
        output.setFramework(framework);
        output.setFlavor(flavor);
        output.setInput(raw);
        output.setVersion(version);
        output.setTime(time);
        
        Map<Integer,String> id2node = new HashMap<>();
        Map<String,Integer> node2id = new HashMap<>();
        
        int index = 0;
        
        for (String node: sg.getAllNodeNames()){
            node2id.put(node, index);
            id2node.put(index, node);
            GraphNode gN = sg.getNode(node);
            if (sg.getGraph().incomingEdgesOf(gN).size() == 1){
                //might be a property edge
                GraphEdge e = sg.getGraph().incomingEdgesOf(gN).iterator().next();
                boolean isPropertyEdge = false;
                for (Pattern propertyName : propertyNames){
                    Matcher m = propertyName.matcher(e.getLabel());
                    if (m.matches() && sg.getGraph().edgesOf(e.getTarget()).size() == 1){ //if the node as more than one edge, it cannot be a property
                        isPropertyEdge = true;
                        break;
                    }
                }
                if (! isPropertyEdge) {
                    output.getNodes().add(new MRPNode(index,gN.getLabel(),new ArrayList<>(),new ArrayList<>(),null));
                    index++;
                }
            } else {
                output.getNodes().add(new MRPNode(index,gN.getLabel(),new ArrayList<>(),new ArrayList<>(),null));
                index++;
            }
            
        }

        //add top node (ART-ROOT, if necessary, will be done later)
        Set<Integer> tops = new HashSet<>();
        String rootName = sg.getNodeForSource("root");
        tops.add(node2id.get(rootName));
        output.setTops(tops);

        //add edges
        for (GraphEdge e : sg.getGraph().edgeSet() ){
            boolean isPropertyEdge = false;
            for (Pattern propertyName : propertyNames){
                Matcher m = propertyName.matcher(e.getLabel());
                if (m.matches() && sg.getGraph().edgesOf(e.getTarget()).size() == 1){
                    isPropertyEdge = true;
                    output.getNode(node2id.get(e.getSource().getName())).getProperties().add(e.getLabel());
                    output.getNode(node2id.get(e.getSource().getName())).getValues().add(e.getTarget().getLabel());
                }
            }
            

            if (! isPropertyEdge) {
                output.getEdges().add(new MRPEdge(node2id.get(e.getSource().getName()), node2id.get(e.getTarget().getName()),e.getLabel()));
            }
        }
        
        
        return output;
    }
    
    
     /**
     * Converts back to MRPGraph
     * @param sg
     * @param restoreProperties whether properties and values are encoded as nodes being attached to the node they are a property of.
     * @param flavor
     * @param framework
     * @param graphId
     * @param raw
     * @param version
     * @param time
     * @return 
     */
    public static MRPGraph fromAnchoredSGraph(AnchoredSGraph sg, boolean restoreProperties, int flavor, String framework, String graphId, String raw, String version, String time){
        MRPGraph output = new MRPGraph();
        output.sanitize();
        output.setId(graphId);
        output.setFramework(framework);
        output.setFlavor(flavor);
        output.setInput(raw);
        output.setVersion(version);
        output.setTime(time);
        
        Map<Integer,String> id2node = new HashMap<>();
        Map<String,Integer> node2id = new HashMap<>();
        Map<String, TokenRange> node2range = new HashMap<>();
        List<TokenRange> rangesSorted = new ArrayList<>(sg.getAllSpans());
        rangesSorted.sort((TokenRange r1, TokenRange r2) -> Integer.compare(r1.getFrom(), r2.getFrom()));
        
        // give nodes id based on their position in the string
        int index = 0;
        for (TokenRange r : rangesSorted){
            Set<String> aligned = sg.findAligned(r);
            for (String nodeName : aligned){
                id2node.put(index, nodeName);
                node2id.put(nodeName, index);
                node2range.put(nodeName, r);
                index++;
            }
        }
        //Now give unaligned nodes ids:
        for (String node : sg.getAllNodeNames()){
            if (node2id.containsKey(node) || AnchoredSGraph.isLnkNode(sg.getNode(node))) continue;
            node2id.put(node, index);
            id2node.put(index, node);
            index++;
        }
        
        for (int id : id2node.keySet().stream().sorted().collect(Collectors.toList())){
            GraphNode gN = sg.getNode(id2node.get(id));
            List<MRPAnchor> anchors = new ArrayList<>();
            Optional<GraphNode> lnkNode = sg.lnkDaughter(gN.getName());
            if (lnkNode.isPresent() && AnchoredSGraph.isLnkNode(lnkNode.get())){
                anchors.add(MRPAnchor.fromTokenRange(AnchoredSGraph.getRangeOfLnkNode(sg.getNode(lnkNode.get().getName()))));
            }
            output.getNodes().add(new MRPNode(id,gN.getLabel(),new ArrayList<>(),new ArrayList<>(),anchors));
        }
        
        if (restoreProperties){
           //add properties
            for (GraphNode n : sg.getUnachored()){
                Set<GraphNode> parents = sg.getGraph().incomingEdgesOf(n).stream().map(e -> e.getSource()).collect(Collectors.toSet());
                Set<String> incomingLabels = sg.getGraph().incomingEdgesOf(n).stream().map(e -> e.getLabel()).collect(Collectors.toSet());
                if (parents.size() != 1){
                    System.err.println("[AncoredSGraph -> MRPGraph] WARNING: unaligned node "+n.getName()+" should have exactly one parent but has "+parents.size());
                } else {
                    GraphNode parent = parents.stream().findAny().get();
                    String propertyName = incomingLabels.stream().findAny().get(); //there must be exactly one edge label in this set
                    output.getNode(node2id.get(parent.getName())).getProperties().add(propertyName);
                    output.getNode(node2id.get(parent.getName())).getValues().add(n.getLabel());
                }
            }
        }


        //add top node (ART-ROOT will be done later)
        Set<Integer> tops = new HashSet<>();
        String rootName = sg.getNodeForSource("root");
        tops.add(node2id.get(rootName));
        output.setTops(tops);

        //add edges
        for (GraphEdge e : sg.getGraph().edgeSet() ){
            if (! AnchoredSGraph.isLnkEdge(e)){
                output.getEdges().add(new MRPEdge(node2id.get(e.getSource().getName()), node2id.get(e.getTarget().getName()),e.getLabel()));
            }
        }
        
        
        return output;
    }

    
     /**
     * Converts an MRPGraph with single top node to an SGraph, 
     * making the anchoring explicit in nodes with labels of the form <from:to>. These nodes have an incoming "lnk" edge.
     * Node ids in MRPGraphs are numbers and are changed to Strings with a preceding NODE_PREFIX (="n").
     * @param mrpGraph
     * @return 
     */
    public static AnchoredSGraph toSGraphWithAnchoring(MRPGraph mrpGraph){
        AnchoredSGraph g = new AnchoredSGraph();
        for (MRPNode n : mrpGraph.getNodes()){
            if (n.getProperties() != null && !n.getProperties().isEmpty()){
                throw new IllegalArgumentException("MRP graph cannot be converted to s-graph, the MRP graph still contains node properties");
            }
        }
        
        for (MRPNode n : mrpGraph.getNodes()){
            g.addNode(mrpIdToSGraphId(n.getId()), n.getLabel());
            if (n.getAnchors() != null && !n.getAnchors().isEmpty()){
                int numAnchor = 0;
                for (MRPAnchor a : n.getAnchors()){ //convert anchors to lnk edges.
                    String nodeName = ANCHOR_PREFIX + mrpIdToSGraphId(n.getId());
                    g.addNode(nodeName, "<"+a.from+":"+a.to+">");
                    g.addEdge(g.getNode(mrpIdToSGraphId(n.getId())), g.getNode(nodeName), "lnk");
                    numAnchor++;
                }
            }
        }
        if (mrpGraph.getTops().size() != 1){
            throw new IllegalArgumentException("Graph "+mrpGraph.getId()+" of framework "+mrpGraph.getFramework()+" doesn't have single top node");
        }
        int top = mrpGraph.getTops().stream().findFirst().get();
        g.addSource("root", mrpIdToSGraphId(top));
        
        for (MRPEdge e : mrpGraph.getEdges()){
            g.addEdge(g.getNode(mrpIdToSGraphId(e.source)), g.getNode(mrpIdToSGraphId(e.target)), e.label);
        }

        
        return g;
    }
    
    
    /**
     * Converts an MRPGraph with single top node to an SGraph, not taking any anchoring into account.
     * Node ids in MRPGraphs are numbers and are changed to Strings with a preceding NODE_PREFIX (="n").
     * @param mrpGraph
     * @return 
     */
    public static SGraph toSGraphWoAnchoring(MRPGraph mrpGraph){
        SGraph g = new SGraph();
        for (MRPNode n : mrpGraph.getNodes()){
            if (!n.getProperties().isEmpty()){
                throw new IllegalArgumentException("MRP graph cannot be converted to s-graph, the MRP graph still contains node properties");
            }
        }
        
        for (MRPNode n : mrpGraph.getNodes()){
            g.addNode(mrpIdToSGraphId(n.getId()), n.getLabel());
        }
        if (mrpGraph.getTops().size() != 1){
            throw new IllegalArgumentException("Graph "+mrpGraph.getId()+" of framework "+mrpGraph.getFramework()+" doesn't have single top node");
        }
        int top = mrpGraph.getTops().stream().findFirst().get();
        g.addSource("root", mrpIdToSGraphId(top));
        
        for (MRPEdge e : mrpGraph.getEdges()){
            g.addEdge(g.getNode(mrpIdToSGraphId(e.source)), g.getNode(mrpIdToSGraphId(e.target)), e.label);
        }

        
        return g;
    }
    
    public static String addArtificialRootToSent(String sent){
        return sent+" "+ART_ROOT;
    }
    
    public static void addArtificialRootToSent(ConlluSentence sent){
        ConlluEntry artRoot = new ConlluEntry(sent.size()+1,ART_ROOT);
        artRoot.setLemma(ART_ROOT);
        artRoot.setPos(ART_ROOT);
        artRoot.setUPos(ART_ROOT);
        artRoot.setHead(ConlluEntry.NOID);
        TokenRange lastTokenRange = (TokenRange) sent.get(sent.size()-1).getTokenRange();
        int start = lastTokenRange.getFrom()+1+1;
        int end = start + ART_ROOT.length();
        artRoot.setTokenRange(new TokenRange(start, end));
        
        sent.add(artRoot);
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
        
        MRPUtils.addArtificialRootToSent(sent);

        //mrpgraph.getTops()
        //Identify unconnected components in MRP graph
        MRPGraph copy = mrpGraph.deepCopy();
  
        ConnectivityInspector<MRPNode, MRPEdge> inspector =  new ConnectivityInspector(copy.asDirectedGraph());
        //Sentence sent = null;
        int sntCounter = 1;
        Set<Integer> tops = copy.getTops();
        int artRootId = mrpGraph.obtainAvailableId();
        List<MRPAnchor> rootAnchors = new ArrayList<>();
        rootAnchors.add(MRPAnchor.fromTokenRange(sent.get(sent.size()-1).getTokenRange())); //last entry is the artificial root.
        MRPNode artRootNode = new MRPNode(artRootId,ART_ROOT,new ArrayList<>(),new ArrayList<>(),rootAnchors);
        mrpGraph.getNodes().add(artRootNode);
        
        mrpGraph.setInput(addArtificialRootToSent(mrpGraph.getInput()));
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
                            System.err.println("[ADD ART-ROOT] WARNING: Expected to find one node with anchor "+headRange+ " but found "+topNodes.size()+" in "+mrpGraph.getId());
                        }
                        MRPNode topNode = topNodes.stream().findAny().get();
                        mrpGraph.getEdges().add(new MRPEdge(artRootId,topNode.getId(),ROOT_EDGE_LABEL+sntCounter));
                    } else {
                       System.err.println("[ADD ART-ROOT] WARNING: TokenRange of head ("+headRange.toString()+") not present in graph "+mrpGraph.getId()+" -- take arbitrary root for component");
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
        MRPGraph copy = mrpGraph.deepCopy();
        if (mrpGraph.getTops().size() != 1){
            return copy; //no artificial root
        }
        
        int rootId = copy.getTops().stream().findFirst().get();
        
        if (!copy.getNode(rootId).getLabel().equals(ART_ROOT)){
            return copy; //no artificial root
        }
        
        copy.setTops(new HashSet<>());
        
        for (MRPEdge outg : copy.outgoingEdges(rootId)){
            if (outg.label.contains(ROOT_EDGE_LABEL)){
                copy.getTops().add(outg.target); //add a new top node.
            }
        }
        //remove all edges attached to ART-ROOT:
        for (MRPEdge e : copy.edgesOf(rootId)){
            copy.getEdges().remove(e);
        }
        //finally, remove ART-ROOT node:
        copy.getNodes().remove(copy.getNode(rootId));
        copy.setInput(copy.getInput().substring(0, copy.getInput().length() - ART_ROOT.length() -1 )); //-1 for space before ART_ROOT
        
        //If the parser didn't produce perfect results, some anchors might refer to the artificial root:
        int maxAnchor = copy.getInput().length();
        for (MRPNode n : copy.getNodes()){
            for (MRPAnchor a: n.getAnchors()){
                if (a.from > maxAnchor){
                    a.from = maxAnchor;
                }
                if (a.to > maxAnchor){
                    a.to = maxAnchor;
                }
            }
        }
        
        return copy;
        
    }
    
    
    /**
     * Encodes the properties (and their values) of nodes into the label.
     * @param g 
     */
    public static void encodePropertiesInLabels(MRPGraph g){
        for (MRPNode n : g.getNodes()){
            if (n.getProperties() == null) continue;
            for (int i = 0; i < n.getProperties().size();i++){
                n.setLabel(fixPunct(n.getLabel())+SEPARATOR+n.getProperties().get(i)+EQUALS+n.getValues().get(i));
            }
            n.setProperties(new ArrayList<>());
            n.setValues(new ArrayList<>());
        }
    }
    
    /**
     * Reverses the encoding of properties into labels.
     * @param g 
     */
    
    public static void decodePropertiesInLabels(MRPGraph g){
        for (MRPNode n : g.getNodes()){
            String[] parts = n.getLabel().split(MRPUtils.SEPARATOR);
            n.setLabel(Util.unfixPunct(parts[0])); //original label
            for (int i = 1; i < parts.length;i++){
                String[] keyVal = parts[i].split(MRPUtils.EQUALS);
                if (n.getProperties() == null){
                    n.setProperties(new ArrayList<>());
                }
                n.getProperties().add(keyVal[0]);
                if (n.getValues() == null){
                    n.setValues(new ArrayList<>());
                }
                n.getValues().add(keyVal[1]);
            }
        }
    }
    
}
