/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Technically, a normal SGraph but some nodes contain TokenRanges and point to a string.
 * This is class is there to avoid confusion whether an SGraph is anchored or not.
 * @author matthias
 */
public class AnchoredSGraph extends SGraph {
    
    public static final Pattern LNK = Pattern.compile("<([0-9]+):([0-9]+)>");
    public static final String LNK_LABEL = "lnk";
    
    
    
    public AnchoredSGraph copy(){
        return fromSGraph(this);
    }
    /**
     * Blindly converts SGraph to AnchoredSGraph. Use only if you know what you're doing!
     * @param sg
     * @return 
     */
    public static AnchoredSGraph fromSGraph(SGraph sg){
        AnchoredSGraph asg = new AnchoredSGraph();
        for (String nodeName : sg.getAllNodeNames()){
            asg.addNode(nodeName, sg.getNode(nodeName).getLabel());
        }
        for (GraphEdge e : sg.getGraph().edgeSet()){
            asg.addEdge(asg.getNode(e.getSource().getName()), asg.getNode(e.getTarget().getName()), e.getLabel());
        }
        for (String sourceName : sg.getAllSources()){
            String sourceNode = sg.getNodeForSource(sourceName);
            asg.addSource(sourceName, sourceNode);
        }
        return asg;
    }
    
    public static boolean isLnkNode(GraphNode n){
        Matcher m = AnchoredSGraph.LNK.matcher(n.getLabel());
        return m.matches();
    }
    
    public static boolean isLnkEdge(GraphEdge e){
        return e.getLabel().equals(LNK_LABEL);
    }
    
    /**
     * Finds those nodes that have an outgoing lnk edge with TokenRange r.
     * @param r
     * @return 
     */
    public Set<String> findAligned(TokenRange r){
        Set<String> nodes = new HashSet<>();
        for (GraphNode n : getGraph().vertexSet()){
            if (AnchoredSGraph.isLnkNode(n) && getRangeOfLnkNode(n).equals(r)){
                GraphNode parent = lnkParent(n);
                nodes.add(parent.getName());
            }
        }
        return nodes;
    }
    
    public static TokenRange getRangeOfLnkNode(GraphNode n){
        Matcher m = AnchoredSGraph.LNK.matcher(n.getLabel());
        if (m.matches()){
            return new TokenRange(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)));
        }
        throw new IllegalArgumentException("GraphNode "+n.getName()+" with label "+n.getLabel()+" is not a lnk node");
    }
    
     /**
     * Returns all spans found in the graph.
     * @return 
     */
    public  Set<TokenRange> getAllSpans(){
        HashSet<TokenRange> spans =  new HashSet<>();
        for (GraphNode n : getGraph().vertexSet()){
            if (AnchoredSGraph.isLnkNode(n)){
                spans.add(AnchoredSGraph.getRangeOfLnkNode(n));
            }
        }
        return spans;
    }
    
    /**
     * Returns the lnk daughter of the given node or throws an exception.
     * @param node
     * @return
     * @throws IllegalArgumentException 
     */
    public Optional<GraphNode> lnkDaughter(String node) throws IllegalArgumentException{
        Optional<GraphEdge> lnkNode = getGraph().outgoingEdgesOf(getNode(node)).stream().filter(edge -> isLnkEdge(edge)).findFirst();
        if (lnkNode.isPresent()){
            return Optional.of(lnkNode.get().getTarget());
        }
        return Optional.empty();
    }
    
    /**
     * Assumes that n is a link node and returns its (single) parent.
     * @param n
     * @return 
     */
    public GraphNode lnkParent(GraphNode n) throws IllegalArgumentException{
        if (! AnchoredSGraph.isLnkNode(n)){
            throw new IllegalArgumentException(n.getName()+" is not a lnk node");
        }
        Set<GraphEdge> edges = getGraph().incomingEdgesOf(n);
        if (edges.size() != 1){
            throw new IllegalArgumentException(n.getName()+" doesn't have a single parent but "+edges.size());
        } else {
            return edges.stream().findFirst().get().getSource();
        }
        
    }
    
     /**
     * Returns the set of minimal spans, i.e. spans that have no subspans.
     * @return 
     */
    public Set<TokenRange> getMinimalSpans(){
        Set<TokenRange> spans = getAllSpans();
        Set<TokenRange> minimalSpans = spans.stream().filter(span -> spans.stream().allMatch(span2 -> (span.equals(span2)  || ! TokenRange.isSubSpan(span2,span)))).collect(Collectors.toSet());
        return minimalSpans;
    }
    
    /**
     * Returns the set of complex spans, i.e. spans that have subspans.
     * @return 
     */
    public Set<TokenRange> getComplexSpans(){
        Set<TokenRange> spans = getAllSpans();
        spans.removeAll(getMinimalSpans());
        return spans;
    }
    
     /**
     * Strips lnk nodes, useful for evaluating with Smatch.
     * @return  
     */
    public SGraph stripLnks(){
        SGraph copy = this.merge(new SGraph());
        for (String node : getAllNodeNames()){
            if (isLnkNode(copy.getNode(node))  ){
                copy.removeNode(node);
            }
        }
        return copy;
    }
    
    /**
     * Returns the set of those nodes that don't have an outgoing lnk edge (but are not lnk nodes themselves).
     * Attributes (MRP sense) are be unachored.
     * @return 
     */
    public Set<GraphNode> getUnachored(){
        Set<GraphNode> nodes = new HashSet<>();
        
        for (GraphNode n : getGraph().vertexSet()){
            if (!AnchoredSGraph.isLnkNode(n)){
                if (! lnkDaughter(n.getName()).isPresent()){
                    nodes.add(n);
                }
            }
        }
        
        return nodes;
    }
    
    public void setLnk(GraphNode node, TokenRange r){
        node.setLabel("<"+r.toString()+">");
    }
    
    
    
}
