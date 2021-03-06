/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr;

import de.saar.basic.Pair;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.DOMAIN;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.MOD;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.OBJ;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.POSS;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.SUBJ;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.jgrapht.DirectedGraph;

/**
 *
 * @author Jonas
 */
public class AMRBlobUtils {

    
    //---------------------   handwritten heuristics   --------------------------
    
    //-------------------------------   attaching edges to blobs  ----------------------------

    //TODO: replace these with regular expressions, instead of prefixes
    public static final String[] OUTBOUND_EDGEPREFIXES = new String[]{"ARG", "op", "snt", "poss", "consist", "domain", "UNKOUT"};//part goes the other way than poss //consist is actually consist-of the other direction...
//    public static final String[] INBOUND_EDGEPREFIXES = new String[]{"mod", "manner", "source", "topic", "direction", "condition", "concession", "part", "frequency", "extent", "age", "duration", "location", "polarity", "time", "mode", "degree", "li", "wiki", "quant", "unit", "value", "scale", "name", "day", "month", "year", "calendar", "dayperiod", "year2", "century", "quarter", "season", "timezone", "weekday", "UNKIN"};
//    private static final Object2BooleanMap<String> INBOUND_STORE = new Object2BooleanOpenHashMap<>();
    private static final Object2BooleanMap<String> OUTBUND_STORE = new Object2BooleanOpenHashMap<>();
    
    
    
    
    public boolean isOutboundBase(GraphEdge edge) {
        synchronized (OUTBUND_STORE) {
            if (OUTBUND_STORE.containsKey(edge.getLabel())) {
                return OUTBUND_STORE.getBoolean(edge.getLabel());
            }
            for (String pref : OUTBOUND_EDGEPREFIXES) {
                if (edge.getLabel().matches(pref+"[0-9]*")) {
                    OUTBUND_STORE.put(edge.getLabel(), true);
                    return true;
                }
            }
            OUTBUND_STORE.put(edge.getLabel(), false);
            return false;
        }
    }
    
//    private boolean isInboundBase(String edgeLabel) {
//        synchronized (INBOUND_STORE) {
//            if (INBOUND_STORE.containsKey(edgeLabel)) {
//                return INBOUND_STORE.getBoolean(edgeLabel);
//            }
//            for (String pref : INBOUND_EDGEPREFIXES) {
//                if (edgeLabel.startsWith(pref)) {
//                    INBOUND_STORE.put(edgeLabel, true);
//                    return true;
//                }
//            }
//            INBOUND_STORE.put(edgeLabel, false);
//            return false;
//        }
//    }
    
    
    /**
     * Returns true if this edge belongs to the blob of its start node.
     * 
     * @param edge
     * @return 
     */
    public boolean isOutbound(GraphEdge edge) {
        return isOutboundBase(edge);//isStandalone(edgeLabel) || ;
    }
    
    /**
     * Returns true if this edge belongs to the blob of its end node.
     * 
     * @param edge
     * @return 
     */
    public boolean isInbound(GraphEdge edge) {
        return !isOutbound(edge);
    }

    /*
    public boolean isStandalone(String edgeLabel) {
        return false;
        //return !isInboundBase(edgeLabel) && !isOutboundBase(edgeLabel);
    }
    */
    
    
    
    /**
     * returns the default source name for the label of edge, in the context of graph (sometimes ARG edges get mapped to op sources, if they are of a conjunction node)
     * @param edge
     * @param graph
     * @return 
     */
    public String edge2Source(GraphEdge edge, SGraph graph) {
        switch (edge.getLabel()) {
            case "ARG0": return SUBJ;
            case "ARG1": 
                if (isConjunctionNode(graph, edge.getSource())) {
                    return "op1";
                } else {
                    return OBJ;
                }
            case "poss": case "part": return POSS;
            case "domain": return DOMAIN;
            default:
                if (edge.getLabel().startsWith("ARG")) {
                    if (isConjunctionNode(graph, edge.getSource())) {
                        return "op" + edge.getLabel().substring("ARG".length());
                    } else {
                        return OBJ + edge.getLabel().substring("ARG".length());
                    }
                    
                } else if (edge.getLabel().startsWith("op") || edge.getLabel().startsWith("snt")) {
                    return edge.getLabel();
                } else {
                    return MOD;
                }
        }
    }
    
    
    /**
     * A function that assigns a weight to each constant. Used for scoring 
     * source assignments according to heuristic preferences in the ACL 2018 
     * experiments.
     * 
     * @param g
     * @return 
     */
    public double scoreGraph(Pair<SGraph, ApplyModifyGraphAlgebra.Type> g){
        return scoreGraphPassiveSpecial(g.left);
    }
    
    private double scoreGraphPassiveSpecial(SGraph graph) {
        double ret = 1.0;
        for (String s : graph.getAllSources()) {
            if (s.matches(OBJ+"[0-9]+")) {
                double oNr = Integer.parseInt(s.substring(OBJ.length()));
                ret /= oNr;
            }
            if (s.equals(SUBJ)) {
                GraphNode n = graph.getNode(graph.getNodeForSource(s));
                Set<GraphEdge> edges = graph.getGraph().edgesOf(n);
                if (!edges.isEmpty()) {
                    if (edges.size() > 1) {
                        System.err.println("***WARNING*** more than one edge at node "+n.getName()+". Using arbitrary of the edges to score graph.");
                        System.err.println(edges);
                    }
                    GraphEdge e = edges.iterator().next();
                    if (e.getLabel().equals("ARG0")) {
                        ret *= 2.0;
                    } else if (e.getLabel().equals("ARG1")) {
                        ret *= 1.5;
                    }
                } else {
                    System.err.println("***WARNING*** no edges at node "+n);
                }
            }
        }
        return ret;
    }
    
    //--------------------------------------------    specific phenomena   ----------------------------
    
    public final Set<String> CONJUNCTION_NODE_LABELS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList("and", "or", "contrast-01", "either", "neither")));
    
    
    /**
     * Regular expression determining which sources can be used for conjunction.
     * @return 
     */
    public String coordSourceRegex(){
        return "op[0-9]+(-prop)?";
    }
    
    /**
     * Regular expression determining which edge labels are associated to conjunction.
     * @return 
     */
    public String getCoordRegex(){
        return "op[0-9]+(-prop)?";
    }
    
    public boolean isConjunctionNode(SGraph graph, GraphNode node) {
        if (!CONJUNCTION_NODE_LABELS.contains(node.getLabel())) {
            return false;
        } else {
            int conjEdgeCount = 0;
            for (GraphEdge edge : getBlobEdges(graph, node)) {
                if (edge.getLabel().equals("ARG0")) {
                    return false;//then different use of contrast-01
                }
                if (isConjEdgeLabel(edge.getLabel())) {
                    conjEdgeCount++;
                }
            }
            return conjEdgeCount >= 2;
        }
    }
    
    public boolean isConjEdgeLabel(String edgeLabel) {
        return edgeLabel.matches(getCoordRegex()) || edgeLabel.startsWith("ARG[0-9]+") || edgeLabel.startsWith("snt[0-9]+");
    }
    
    public boolean isRaisingNode(SGraph graph, GraphNode node) {
        Set<GraphEdge> argEdges = getArgEdges(node, graph);
        if (argEdges.size() == 1 &&
                (argEdges.iterator().next().getLabel().equals("ARG1") || argEdges.iterator().next().getLabel().equals("ARG2"))) {
            GraphNode other = GeneralBlobUtils.otherNode(node, argEdges.iterator().next());
            if (!getArgEdges(other, graph).isEmpty()) {
                return true;
            }
        }
        return false;
    }
    
    public Set<GraphEdge> getArgEdges(GraphNode n, SGraph graph) {
        Set<GraphEdge> argEdges = new HashSet<>();
        for (GraphEdge e : getBlobEdges(graph, n)) {
            if (e.getLabel().startsWith("ARG")) {
                argEdges.add(e);
            }
        }
        return argEdges;
    }
    
    
    
    
    
    //---------------------   general   --------------------------------------
    
    

    /**
     * returns true if and only if the edge is in the blob of the node.
     * @param node
     * @param edge
     * @return 
     */
    public boolean isBlobEdge(GraphNode node, GraphEdge edge) {
        return (edge.getSource().equals(node)) && isOutbound(edge)  || (edge.getTarget().equals(node) && isInbound(edge) );
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
    
    
    
    
    
    
    
    
    
    
    
    
    // ----------------   unused or deprecated   ------------------------
    
    @Deprecated
    public Set<GraphNode> getNestedConjunctionNodes(SGraph graph, GraphNode node) {
        return getNestedConjunctionNodes(graph, node, false);
    }
    
    @Deprecated
    private Set<GraphNode> getNestedConjunctionNodes(SGraph graph, GraphNode node, boolean checkConj) {
        Set<GraphNode> ret = new HashSet<>();
        if (!checkConj || isConjunctionNode(graph, node)) {
            ret.add(node);
            for (GraphEdge edge : graph.getGraph().outgoingEdgesOf(node)) {
                if (isConjEdgeLabel(edge.getLabel())) {
                    ret.addAll(getNestedConjunctionNodes(graph, edge.getTarget(), true));
                }
            }
        }
        return ret;
    }
    
    /**
     * gives list of reentrancies. Left of pair is always shorter (or equal length).
     * @param min
     * @param max
     * @param sgraph
     * @return 
     */
    @Deprecated
     Set<Pair<List<GraphEdge>, List<GraphEdge>>> getReentrancies(int min, int max, SGraph sgraph) {
        DirectedGraph<GraphNode, GraphEdge> graph = sgraph.getGraph();
        Set<Pair<List<GraphEdge>, List<GraphEdge>>> ret = new HashSet<>();
        for (GraphNode node : graph.vertexSet()) {
            Map<GraphNode, List<GraphEdge>> seen2Path = new HashMap<>();
            List<GraphNode> agenda = new ArrayList<>();
            List<GraphEdge> seed = new ArrayList<>();
            seen2Path.put(node, seed);
            agenda.add(node);
            while (!agenda.isEmpty()) {
                GraphNode a = agenda.get(0);
                agenda.remove(0);
                for (GraphEdge edge : graph.edgesOf(a)) {
                    if (isBlobEdge(a, edge)) {
                        GraphNode other = GeneralBlobUtils.otherNode(a, edge);
                        List<GraphEdge> newPath = new ArrayList(seen2Path.get(a));
                        newPath.add(edge);
                        if (seen2Path.containsKey(other)) {
                            List<GraphEdge> prevPath = seen2Path.get(other);
                            if ((prevPath.size()<2 || !prevPath.get(1).equals(newPath.get(1)))
                                    && newPath.size() >= min) {
                            //if (prevPath.size()<2) {
                                ret.add(new Pair(prevPath, newPath));
                            }
                        } else {
                            if (newPath.size()<=max) {
                                agenda.add(other);
                            }
                            seen2Path.put(other, newPath);
                        }
                    }
                }
            }
        }
        return ret;
    }
    
    /**
     * gives list of multimods (which is a word I just came up with, meaning that
     * a node is part of an undirected cycle with the relevant adjacent edges
     * not part of the node's blob).
     * This function is marked as deprecated since it is no longer used in
     * ongoing research. However, it is still in the code and working when
     * e.g. reproducing the IWCS 2017 results of Groschwitz et. al
     * @param sgraph
     * @return 
     */
    @Deprecated
    public Set<GraphNode> getMultimods(SGraph sgraph) {
        return getMultimods(sgraph, Collections.EMPTY_SET);
    }
    
    
    /**
     * gives list of multimods (which is a word I just came up with, meaning that
     * a node is part of an undirected cycle with the relevant adjacent edges
     * not part of the node's blob).
     * @param excluded
     * @param sgraph
     * @return 
     */
    @Deprecated
    public Set<GraphNode> getMultimods(SGraph sgraph, Set<Set<GraphEdge>> excluded) {
        DirectedGraph<GraphNode, GraphEdge> graph = sgraph.getGraph();
        Set<GraphNode> ret = new HashSet<>();
        for (GraphNode node : graph.vertexSet()) {
            Map<GraphEdge, Set<GraphNode>> seenPrev = new HashMap<>();
            edgeLoop:
            for (GraphEdge edge : graph.edgesOf(node)) {
                Set<GraphNode> seen = new HashSet();
                seen.add(node);//so we don't go back over this node
                List<GraphNode> agenda = new ArrayList<>();
                agenda.add(GeneralBlobUtils.otherNode(node, edge));
                while (!agenda.isEmpty()) {
                    GraphNode current = agenda.get(0);
                    agenda.remove(0);
                    for (GraphEdge re : graph.edgesOf(current)) {
                        GraphNode other = GeneralBlobUtils.otherNode(current, re);
                        if (!seen.contains(other)) {
                            seen.add(other);//put this here to avoid the case of other == node, and to avoid multiple checking. Edit: ??
                            agenda.add(other);//put this here to avoid the case of other == node, and to avoid multiple checking. Edit: ??
                            
                            for (Entry<GraphEdge, Set<GraphNode>> edgeAndSet : seenPrev.entrySet()) {
                                if (edgeAndSet.getValue().contains(other)) {
                                    boolean ex = false;
                                    for (Set<GraphEdge> set : excluded) {
                                        if (set.contains(edgeAndSet.getKey()) && set.contains(edge)) {
                                            ex = true;
                                            break;
                                        }
                                    }
                                    if (!ex && (!isBlobEdge(node, edge) || !isBlobEdge(node, edgeAndSet.getKey()))) {
                                        ret.add(node);
                                        break edgeLoop;
                                    }
                                }
                            }
                        }
                    }
                }
                seenPrev.put(edge, seen);
            }
        }
        return ret;
    }
    
    /**
     * returns null if no blob edge of node goes to other.
     * @param node
     * @param other
     * @param graph
     * @return 
     */
    @Deprecated
     GraphEdge getBlobEdgeTo(GraphNode node, GraphNode other, DirectedGraph<GraphNode, GraphEdge> graph) {
        for (GraphEdge e : graph.getAllEdges(node, other)) {
            if (isBlobEdge(node, e)) {
                return e;
            }
        }
        for (GraphEdge e : graph.getAllEdges(other, node)) {
            if (isBlobEdge(node, e)) {
                return e;
            }
        }
        return null;
    }
    
    @Deprecated
    public Set<GraphNode> getConjunctionTargets(SGraph graph, GraphNode node) {
        Set<GraphNode> ret = new HashSet<>();
        if (isConjunctionNode(graph, node)) {
            for (GraphEdge edge : graph.getGraph().outgoingEdgesOf(node)) {
                if (edge.getLabel().startsWith("op") || edge.getLabel().startsWith("ARG")) {
                    Set<GraphNode> rec = getConjunctionTargets(graph, edge.getTarget());
                    if (rec.isEmpty()) {
                        ret.add(edge.getTarget());
                    } else {
                        ret.addAll(rec);
                    }
                }
            }
        }
        return ret;
    }
    
    
    /**
     * A target here is a node that is on the other end of an edge of the input
     * node's blob, the resulting map maps the target to this blob edge's label.
     * If a blob edge 
     * @param graph
     * @param node the input node of which to get the targets.
     * @return 
     */
    @Deprecated
    public Map<GraphNode, String> getTargets(SGraph graph, GraphNode node) {
        Map<GraphNode, String> ret = getPrimaryTargets(graph, node);
        Map<GraphNode, String> sec = getSecondaryTargets(graph, node);
        for (GraphNode n : sec.keySet()) {
            ret.put(n, sec.get(n));//will override primary targets with secondary targets. I think this is the wanted behaviour for now -- JG
        }
        return ret;
    }
    
    @Deprecated
    public Map<GraphNode, String> getSecondaryTargets(SGraph graph, GraphNode node) {
        if (isConjunctionNode(graph, node)) {
            Map<GraphNode, String> ret = new HashMap<>();
            Map<GraphNode, Counter<String>> target2edge2Count = new HashMap<>();
            Map<GraphNode, Map<String, String>> target2edge2minRealizer = new HashMap<>();
            Set<String> allLabels = new HashSet<>();
            Collection<String> forbidden = getPrimaryTargets(graph, node).values();
            //add all nodes first, we will remove the non-targets below
            for (GraphNode temp : graph.getGraph().vertexSet()) {
                target2edge2Count.put(temp, new Counter<>());
                target2edge2minRealizer.put(temp, new HashMap<>());
            }
            for (GraphEdge edge : graph.getGraph().outgoingEdgesOf(node)) {
                if (isConjEdgeLabel(edge.getLabel())) {
                    GraphNode other = GeneralBlobUtils.otherNode(node, edge);
                    Map<GraphNode, String> otherTargets = getTargets(graph, other);
                    
                    //remove those that are not targets here
                    Set<GraphNode> remove = new HashSet(target2edge2Count.keySet());
                    remove.removeAll(otherTargets.keySet());
                    remove.forEach((rem) -> {target2edge2Count.remove(rem);});
                    remove.forEach((rem) -> {target2edge2minRealizer.remove(rem);});
                    
                    for (GraphNode target : target2edge2Count.keySet()) {
                        String recLabel = otherTargets.get(target);
                        Counter label2Count = target2edge2Count.get(target);
                        label2Count.add(recLabel);
                        Map<String, String> edge2minRealizer = target2edge2minRealizer.get(target);
                        if (!edge2minRealizer.containsKey(recLabel) || edge.getLabel().compareTo(edge2minRealizer.get(recLabel)) < 0) {
                            edge2minRealizer.put(recLabel, edge.getLabel());
                        }
                        
                    }
                }
            }
            for (Counter<String> counter : target2edge2Count.values()) {
                allLabels.addAll(counter.getAllSeen());
            }
            allLabels.removeAll(forbidden);
            List<String> sortedLabels = allLabels.stream().sorted().collect(Collectors.toList());
            while (!target2edge2Count.isEmpty()) {
                if (sortedLabels.isEmpty()) {
                    System.err.println("WARNING not enough labels when finding targets: "+graph.toIsiAmrString());
                    break;
                }
                String label = sortedLabels.get(0);
                sortedLabels.remove(0);
                List<GraphNode> candidates = target2edge2Count.keySet().stream().filter(
                        (GraphNode t) -> target2edge2Count.get(t).get(label) > 0).collect(Collectors.toList());
                if (candidates.isEmpty()) {
                    System.err.println("WARNING could not distribute labels properly when finding targets: "+graph.toIsiAmrString());
                    break;
                }
                for (Counter<String> counter : target2edge2Count.values()) {
                    counter.reset(label);
                }
                Counter<String> totalCounts = new Counter<>();
                for (Counter<String> temp : target2edge2Count.values()) {
                    for (String l : temp.getAllSeen()) {
                        totalCounts.add(l, temp.get(l));
                    }
                }
                candidates.sort(new Comparator<GraphNode>() {
                    @Override
                    public int compare(GraphNode o1, GraphNode o2) {
                        //return negative if o1 should win
                        try {
                            int min1 = totalCounts.getAllSeen().stream()
                                    .mapToInt(l -> totalCounts.get(l)-target2edge2Count.get(o1).get(l)).min().getAsInt();
                            int min2 = totalCounts.getAllSeen().stream()
                                    .mapToInt(l -> totalCounts.get(l)-target2edge2Count.get(o2).get(l)).min().getAsInt();
                            if (min1 != min2) {
                                return min2-min1;
                            } else {
                                return target2edge2minRealizer.get(o1).get(label).compareTo(target2edge2minRealizer.get(o2).get(label));
                            }
                        } catch (java.util.NoSuchElementException ex) {
                            System.err.println("WARNING no counts left when finding targets: "+graph.toIsiAmrString());
                            return 0;
                        }
                    }
                });
                GraphNode winner = candidates.get(0);
                ret.put(winner, label);
                target2edge2Count.remove(winner);
            }
            return ret;
        } else {
            return new HashMap<>();
        }
    }
    
    @Deprecated
    public Map<GraphNode, String> getPrimaryTargets(SGraph graph, GraphNode node) {
        Map<GraphNode, String> ret = new HashMap<>();
        boolean isConj = isConjunctionNode(graph, node);
        for (GraphEdge edge : getBlobEdges(graph, node)) {
            if (!isConj || !(isConjEdgeLabel(edge.getLabel()))) {
                ret.put(GeneralBlobUtils.otherNode(node, edge), edge.getLabel());
            }
        }
        return ret;
    }
    
}
