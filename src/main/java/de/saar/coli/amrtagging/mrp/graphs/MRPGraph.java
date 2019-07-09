/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.graphs;



/**
 *
 * @author matthias
 */
import com.owlike.genson.Genson;
import de.saar.coli.amrtagging.TokenRange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.DirectedGraph;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;

public class MRPGraph {
    
    private String id;
    private int flavor;
    private String framework;
    private String version;
    private String time;
    private String input;
    private Set<Integer> tops;
    private List<MRPNode> nodes;
    private Set<MRPEdge> edges;
    
    /**
     * Should be called after being read from JSON.
     */
    public void sanitize(){
        
        if (tops == null){
            tops = new HashSet<>();
        }
        if (nodes == null){
            nodes = new ArrayList<>();
        }
        if (edges == null){
            edges = new HashSet<>();
        }
        
        for (MRPEdge e : edges){
            if (e.normal != null){ //normalize = reverse edge direction
                int source = e.source;
                int target = e.target;
                String lbl = e.normal;
                e.label = lbl;
                e.source = target;
                e.target = source;
                e.normal = null;
            }
        }
        
    }
    
    public Set<TokenRange> tokenRanges(){
        HashSet<TokenRange> r = new HashSet<>();
        for (MRPNode node : nodes){
            if (node.getAnchors() != null){
                for (MRPAnchor a : node.getAnchors()){
                    r.add(new TokenRange(a.from,a.to));
                }
            }  
        }
        return r;
    }
    
    public Set<MRPNode> getNodesForAnchor(MRPAnchor a){
        Set<MRPNode> found = new HashSet<>();
        for (MRPNode n : nodes){
            if (n.getAnchors().contains(a)){
                found.add(n);
            }
        }
        return found;
    }
    
    /**
     * Returns the next available id.
     * @return 
     */
    public int obtainAvailableId(){
        Optional<Integer> max = nodes.stream().map(node -> node.getId()).max(Integer::compare);
        if (max.isPresent()){
            return max.get()+1;
        }
        return 0;
    }
    
    public DirectedGraph<MRPNode,MRPEdge> asDirectedGraph(){
        DirectedGraph<MRPNode,MRPEdge> jg = new DirectedAcyclicGraph(DefaultEdge.class);
        for (MRPNode n : getNodes()){
            jg.addVertex(n);
        }
        Map<Integer,MRPNode> node2Id = id2Node();
        
        for (MRPEdge e: getEdges()){
            jg.addEdge(node2Id.get(e.source), node2Id.get(e.target), e);
        }
        return jg;
    }
    
    public MRPGraph deepCopy(){
        Genson genson = new Genson();
        MRPGraph r = genson.deserialize(genson.serialize(this), MRPGraph.class);
        r.sanitize();
        return r;
    }
    
    /**
     * Returns a map mapping IDs of nodes to MRPNodes.
     * @return 
     */
    public Map<Integer,MRPNode> id2Node(){
        return nodes.stream().collect(
                Collectors.toMap(node -> node.getId(), node -> node));
    }
    
    public MRPNode getNode(int id){
        return id2Node().get(id);
        //when computation time is an issue, we can speed this up for flavour 0 graphs.
    }
    
    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the flavor
     */
    public int getFlavor() {
        return flavor;
    }

    /**
     * @param flavor the flavor to set
     */
    public void setFlavor(int flavor) {
        this.flavor = flavor;
    }

    /**
     * @return the framework
     */
    public String getFramework() {
        return framework;
    }

    /**
     * @param framework the framework to set
     */
    public void setFramework(String framework) {
        this.framework = framework;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return the time
     */
    public String getTime() {
        return time;
    }

    /**
     * @param time the time to set
     */
    public void setTime(String time) {
        this.time = time;
    }

    /**
     * @return the input
     */
    public String getInput() {
        return input;
    }

    /**
     * @param input the input to set
     */
    public void setInput(String input) {
        this.input = input;
    }

    /**
     * @return the tops
     */
    public Set<Integer> getTops() {
        return tops;
    }

    /**
     * @param tops the tops to set
     */
    public void setTops(Set<Integer> tops) {
        this.tops = tops;
    }

    /**
     * @return the nodes
     */
    public List<MRPNode> getNodes() {
        return nodes;
    }

    /**
     * @param nodes the nodes to set
     */
    public void setNodes(List<MRPNode> nodes) {
        this.nodes = nodes;
    }

    /**
     * @return the edges
     */
    public Set<MRPEdge> getEdges() {
        return edges;
    }

    /**
     * @param edges the edges to set
     */
    public void setEdges(Set<MRPEdge> edges) {
        this.edges = edges;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.id);
        hash = 67 * hash + Objects.hashCode(this.flavor);
        hash = 67 * hash + Objects.hashCode(this.framework);
        hash = 67 * hash + Objects.hashCode(this.version);
        hash = 67 * hash + Objects.hashCode(this.time);
        hash = 67 * hash + Objects.hashCode(this.input);
        hash = 67 * hash + Objects.hashCode(this.tops);
        hash = 67 * hash + Objects.hashCode(this.nodes);
        hash = 67 * hash + Objects.hashCode(this.edges);
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
        final MRPGraph other = (MRPGraph) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (!Objects.equals(this.flavor, other.flavor)) {
            return false;
        }
        if (!Objects.equals(this.framework, other.framework)) {
            return false;
        }
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        if (!Objects.equals(this.input, other.input)) {
            return false;
        }
        if (!Objects.equals(this.tops, other.tops)) {
            return false;
        }
        if (!Objects.equals(this.edges, other.edges)) {
            return false;
        }
        /**
         * TODO: if "nodes" is a set rather than a list, a strange bug seems to arise: Objects.equals(this.nodes, other.nodes) is not necessarily the same
         * as Objects.equals(other.nodes, this.nodes)
         */
        if (!Objects.equals(this.nodes, other.nodes)) {
            return false;
        }

        return true;
    }
    
    /**
     * Returns the set of adjacent edges.
     * @param id
     * @return 
     */
    public Set<MRPEdge> edgesOf(int id){
        return edges.stream().filter(edg -> edg.source == id || edg.target == id).collect(Collectors.toSet());
    }
    
    /**
     * Returns the outgoing edges of a specific node.
     * @param id
     * @return 
     */
    public Set<MRPEdge> outgoingEdges(int id){
        return edges.stream().filter(edg -> edg.source == id).collect(Collectors.toSet());
    }
    
    /**
     * Returns the incoming edges of a specific node.
     * @param id
     * @return 
     */
    public Set<MRPEdge> incomingEdges(int id){
        return edges.stream().filter(edg -> edg.target == id).collect(Collectors.toSet());
    }
    
    
    
}
