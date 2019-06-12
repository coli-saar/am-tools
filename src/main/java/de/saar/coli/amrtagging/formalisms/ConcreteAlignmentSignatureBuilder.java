/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms;

import com.google.common.collect.Sets;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.OBJ;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.SUBJ;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import static de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Util;
import de.up.ling.tree.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jgrapht.DirectedGraph;

/**
 * see description at https://www.overleaf.com/project/5c13d002bb94d5092364e0bf
 * @author JG
 */
public class ConcreteAlignmentSignatureBuilder implements AMSignatureBuilder {
    
    private final Map<GraphNode, Map<GraphNode, Set<String>>> node2argument2sources;
    private final Map<GraphNode, Map<Pair<GraphNode, String>, Set<GraphNode>>> node2nestedArgNsource2blobTarget;
    private final Map<GraphNode, Set<GraphNode>> node2blobTargets;
    private final Map<GraphNode, Set<GraphNode>> node2arguments;
    protected final SGraph sGraph;
    protected final AMRBlobUtils blobUtils;
    protected final Map<String, Integer> node2firstAlignedIndex;
    
    public ConcreteAlignmentSignatureBuilder(SGraph sGraph, List<Alignment> alignments, AMRBlobUtils blobUtils) {
        DirectedGraph<GraphNode, GraphEdge> graph = sGraph.getGraph();
        this.sGraph = sGraph;
        this.blobUtils = blobUtils;
        node2firstAlignedIndex = new HashMap<>();
        for (Alignment al : alignments) {
            for (String nn : al.nodes) {
                node2firstAlignedIndex.put(nn, al.span.start);
            }
        }
        
        node2blobTargets = new HashMap<>();
        for (GraphNode node : graph.vertexSet()) {
            Set<GraphNode> blobTargets = new HashSet<>();
            node2blobTargets.put(node, blobTargets);
            graph.edgesOf(node).stream().filter(e -> blobUtils.isBlobEdge(node, e)).forEach(blobEdge -> {
                blobTargets.add(GeneralBlobUtils.otherNode(node, blobEdge));
            });
        }
        
        node2arguments = new HashMap<>();
        for (GraphNode node : graph.vertexSet()) {
            getArguments(node);//this fills node2arguments
        }
        
        node2nestedArgNsource2blobTarget = new HashMap();
        node2argument2sources = new HashMap();
        for (GraphNode node : graph.vertexSet()) {
            getArgument2Sources(node);//this fills node2argument2sources
            getNestedArgNsource2blobTarget(node);//this fills node2nestedArgNsource2blobTarget
        }
    }
    
    private Set<GraphNode> getArguments(GraphNode node) {
        if (node2arguments.containsKey(node)) {
            return node2arguments.get(node);
        } else {
            return node2arguments.put(node, getArguments(node, node, new HashSet<>()));
        }
    }
    
    private Set<GraphNode> getArguments(GraphNode node, GraphNode seed, Set<GraphNode> seen) {
        Set<GraphNode> args = new HashSet<>();
        for (GraphNode blobTarget : node2blobTargets.get(node)) {
            if (blobTarget.equals(seed)) {
                throw new IllegalArgumentException("Graph with argument loop found in ConcreteAlignmentSignatureBuilder. Aborting.");
            }
            if (!seen.contains(blobTarget)) {
                seen.add(blobTarget);
                args.add(blobTarget);
                args.addAll(getArguments(blobTarget, seed, seen));
            }
        }
        return args;
    }

    public AMRBlobUtils getBlobUtils() {
        return blobUtils;
    }
    
    
    
    private Map<GraphNode, Set<String>> getArgument2Sources(GraphNode node) {
        if (node2argument2sources.containsKey(node)) {
            return node2argument2sources.get(node);
        } else {
            Map<GraphNode, Set<String>> map = new HashMap<>();
            Collection<Map<GraphEdge, String>> e2s = getSourceAssignments(blobUtils.getBlobEdges(sGraph, node), sGraph);
            for (GraphNode arg : node2blobTargets.get(node)) {
                Set<String> directSources = new HashSet<>();
                for (GraphEdge e : Sets.union(sGraph.getGraph().getAllEdges(node, arg), sGraph.getGraph().getAllEdges(arg, node))) {
                    //we assumed blob-acyclic graphs, so e must be in the blob of node. (If e is in the blob of node, then node is a blob target of arg; but arg is a blob target of node, a cycle.)
                    for (Map<GraphEdge, String> runningE2s : e2s) {
                        directSources.add(runningE2s.get(e));
                    }
                }
                map.put(arg, directSources);
            }
            for (Pair<GraphNode, String> argNsource : getNestedArgNsource2blobTarget(node).keySet()) {
                GraphNode arg = argNsource.left;
                if (!node2blobTargets.get(node).contains(arg)) {
                    Set<GraphNode> blobTargets = getNestedArgNsource2blobTarget(node).get(argNsource);
                    boolean add = false;
                    if (blobTargets.size() == 1) {
                        add = isRaising(node, argNsource.right, argNsource.left, blobTargets.iterator().next());
                    } else if (blobTargets.size() > 1) {
                        add = isCoord(node, argNsource.right, arg, blobTargets);
                    }
                    if (add) {
                        if (map.containsKey(arg)) {
                            map.get(arg).add(argNsource.right);
                        } else {
                            Set<String> ret = new HashSet<>();
                            ret.add(argNsource.right);
                            map.put(arg, ret);
                        }
                    }
                }
            }
            node2argument2sources.put(node, map);
            return map;
        }
    }
    
    private Map<Pair<GraphNode, String>, Set<GraphNode>> getNestedArgNsource2blobTarget(GraphNode node) {
        if (node2nestedArgNsource2blobTarget.containsKey(node)) {
            return node2nestedArgNsource2blobTarget.get(node);
        } else {
            Map<Pair<GraphNode, String>, Set<GraphNode>> map = new HashMap<>();
            //gather *all* blob targets t that assign a source s to a given argument arg
            for (GraphNode arg : getArguments(node)) {
                for (GraphNode t : node2blobTargets.get(node)) {
                    for (String s : (Set<String>)getArgument2Sources(t).getOrDefault(arg, Collections.EMPTY_SET)) {
                        Set<GraphNode> blobTargetsHere = map.get(new Pair(arg, s));
                        if (blobTargetsHere == null) {
                            blobTargetsHere = new HashSet<>();
                            map.put(new Pair(arg, s), blobTargetsHere);
                        }
                        blobTargetsHere.add(t);
                    }
                }
            }
            //now clean the map to only allow raising, coordination and control style reentrancies
            map.entrySet().removeIf(entry -> !isValidNestedArg(node, entry.getKey().left, entry.getKey().right, entry.getValue()));
            node2nestedArgNsource2blobTarget.put(node, map);
            return map;
        }
    }
    
    private boolean isValidNestedArg(GraphNode node, GraphNode nestedArg, String source, Set<GraphNode> blobTargets) {
        if (blobTargets.isEmpty()) {
            System.err.println("***WARNING*** empty set encountered in ConcreteAlignmentSignatureBuilder#isInvalidNestedArg");
            System.err.println("This should not cause any problems, but may be worth looking into.");
            return false;//this should never happen though, but just in case.
        }
        if (node2blobTargets.get(node).contains(nestedArg)) {
            return true;//this is control style reentrancy
        } else {
            if (blobTargets.size() > 1) {
                //this is the coordination case
                return isCoord(node, source, nestedArg, blobTargets);
            } else {
                //only the raising case remains, and blobTargets.size() is guaranteed to be 1.
                return isRaising(node, source, nestedArg, blobTargets.iterator().next());
            }
        }
    }
    
    //override this for more detailed control over when raising is allowed.
    protected boolean isRaising(GraphNode node, String s, GraphNode nestedArg, GraphNode blobTarget) {
        return !node.getLabel().equals("ART-ROOT") && s.equals(SUBJ);
    }
    
    //override this for more detailed control over when coordination is allowed.
    protected boolean isCoord(GraphNode node, String s, GraphNode nodestedArg, Set<GraphNode> blobTargets) {
        return blobTargets.size() > 1;
    }
    
    
    @Override
    public Signature makeDecompositionSignature(SGraph graph, int maxCoref) throws IllegalArgumentException, ParseException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Signature makeDecompositionSignatureWithAlignments(SGraph graph, List<Alignment> alignments, boolean coref) throws IllegalArgumentException, ParseException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Set<String> getConstantsForAlignment(Alignment al, SGraph graph, boolean coref) throws IllegalArgumentException, ParseException {
        //consolidate the nestedArgNsource2blobTarget maps of all nodes in the alignment into one map.
        
//        if (graph.getNode(al.lexNodes.iterator().next()).getLabel().equals("ART-ROOT")) {
//            System.err.println();
//        }
        
        Map<Pair<GraphNode, String>, Set<GraphNode>> fullNestedArgNSource2blobTarget = new HashMap<>();
        for (String nodeName : al.nodes) {
            GraphNode node = graph.getNode(nodeName);
            Map<Pair<GraphNode, String>, Set<GraphNode>> mapHere = getNestedArgNsource2blobTarget(node);
            for (Pair<GraphNode, String> nestedArgNSource : mapHere.keySet()) {
                Set<GraphNode> blobTargetsHere = fullNestedArgNSource2blobTarget.get(nestedArgNSource);
                if (blobTargetsHere == null) {
                    blobTargetsHere = new HashSet<>();
                    fullNestedArgNSource2blobTarget.put(nestedArgNSource, blobTargetsHere);
                }
                blobTargetsHere.addAll(mapHere.get(nestedArgNSource));
            }
        }
        Iterable<Map<GraphNode, Pair<Set<Pair<GraphNode, String>>, Map<String, String>>>> compatibleAnnots = getCompatibleAnnotations(fullNestedArgNSource2blobTarget);
        
        //collect all blob edges of alignment to external nodes. If there are multiple blob edges to the same node, keep only one. \\TODO the choice which edge to keep is somewhat arbitrary right now (just lexical order)
        Set<GraphEdge> allExternalBlobEdges = new HashSet<>();
        Map<GraphNode, GraphEdge> other2edge = new HashMap<>();
        for (String nodeName : al.nodes) {
            GraphNode node = graph.getNode(nodeName);
            for (GraphEdge edge : blobUtils.getBlobEdges(graph, node)) {
                GraphNode other = GeneralBlobUtils.otherNode(node, edge);
                if (!al.nodes.contains(other.getName())) {
                    if (other2edge.containsKey(other)) {
                        GraphEdge competingEdge = other2edge.get(other);
                        if (competingEdge.getLabel().compareTo(edge.getLabel()) > 0) {
                            //this ought to keep labels that come first in the alphabet 
                            allExternalBlobEdges.remove(competingEdge);
                            allExternalBlobEdges.add(edge);
                            other2edge.put(other, edge);
                        } //else keep the competingEdge
                    } else {
                        allExternalBlobEdges.add(edge);
                        other2edge.put(other, edge);
                    }
                }
            }
        }
//        System.err.println(allExternalBlobEdges);
        Collection<Map<GraphEdge, String>> sourceAssignments = getSourceAssignments(allExternalBlobEdges, graph);
//        System.err.println("source assignments: "+sourceAssignments.size());
//        System.err.println(compatibleAnnots);
//        System.err.println(sourceAssignments);
        
        Set<GraphNode> inNodes = new HashSet<>();//should contain the one node that becomes the root of the constant.
        String globalRootNN = graph.getNodeForSource("root");
        if (globalRootNN != null && al.nodes.contains(globalRootNN)) {
            inNodes.add(graph.getNode(globalRootNN));
        }
        for (String nn : al.nodes) {
            GraphNode node = graph.getNode(nn);
            for (GraphEdge e : graph.getGraph().edgesOf(node)) {
                if (!al.nodes.contains(e.getSource().getName()) || !al.nodes.contains(e.getTarget().getName())) {
                    if (!blobUtils.isBlobEdge(node, e)) {
                        inNodes.add(node);
                    }
                }
            }
        }
        if (inNodes.size() > 1) {
            throw new IllegalArgumentException("Cannot create a constant for this alignment ("+al.toString()+"): More than one node with edges from outside, but we can only have one root.");
        }
        GraphNode root;
        if (inNodes.isEmpty()) {
            //for now take arbirary root here, preferring lexicalized nodes for consistency. TODO: is this a problem? can we do better?
            if (!al.lexNodes.isEmpty()) {
                root = graph.getNode(al.lexNodes.iterator().next());
            } else {
                root = graph.getNode(al.nodes.iterator().next());
            }
        } else {
            root = inNodes.iterator().next();//this is unique
        }
        
        
        Set<String> ret = new HashSet<>();
        if (coref) {
            throw new IllegalArgumentException("Coref not supported yet in ConcreteAlignmentSignatureBuilder");
        }
        
//        //if there is no node with blob edge pointing out of alignment node cluster, we are done. Otherwise continue, focussing on that one node. EDIT: should happen automatically in the following for loops
//        if (allExternalBlobEdges.isEmpty()) {
//            SGraph constGraph = makeConstGraph(al.nodes, graph, root);
//            ret.add(constGraph.toIsiAmrStringWithSources()+GRAPH_TYPE_SEP+ApplyModifyGraphAlgebra.Type.EMPTY_TYPE.toString());
//            return ret;
//        }
        //otherwise, add all variants of source names and annotations
        for (Map<GraphNode,Pair<Set<Pair<GraphNode,String>>, Map<String, String>>> annot : compatibleAnnots) {
            sourceloop:
            for (Map<GraphEdge, String> sourceAssignment : sourceAssignments) {
                SGraph constGraph = makeConstGraph(al.nodes, graph, root);
                Map<GraphNode, String> blobTarget2source = new HashMap<>();
                for (GraphEdge edge : allExternalBlobEdges) {
                    GraphNode blobTarget = al.nodes.contains(edge.getSource().getName()) ? edge.getTarget() : edge.getSource();//get the node of edge that is not in al.
                    String src = sourceAssignment.get(edge);
                    blobTarget2source.put(blobTarget, src);
                    //add source to graph
                    constGraph.addSource(src, blobTarget.getName());
                }
                
                String typeString = "(";
                for (Map.Entry<GraphNode, String> blobTargetNsource : blobTarget2source.entrySet()) {
                    if (!typeString.endsWith("(")) {
                        typeString+=",";
                    }
                    typeString+=blobTargetNsource.getValue()+"(";
                    Pair<Set<Pair<GraphNode, String>>, Map<String, String>> argumentsNsourcesNtype = annot.get(blobTargetNsource.getKey());
                    if (argumentsNsourcesNtype != null) {
                        for (Pair<GraphNode, String> nestedArgNsource : argumentsNsourcesNtype.left) {
//                            if (node2blobTargets.get(blobTargetNsource.getKey()).contains(nestedArgNsource.left)) { // this was just wrong
                                String givenSource = nestedArgNsource.right;
                                String targetSource;
                                if (blobTarget2source.containsKey(nestedArgNsource.left)) {
                                    targetSource = blobTarget2source.get(nestedArgNsource.left);
                                } else {
                                    if (sourceAssignment.containsValue(givenSource)) {
                                        continue sourceloop;
                                    } else {
                                        targetSource = givenSource;
                                    }
                                }
                                if (!typeString.endsWith("(")) {
                                    typeString+=",";
                                }
                                typeString += givenSource+argumentsNsourcesNtype.right.get(givenSource);
                                if (!givenSource.equals(targetSource)) {
                                    typeString += "_UNIFY_"+targetSource;
                                }
//                            }
                        }
                    } //else use empty type
                    typeString += ")";
                }
                typeString += ")";
                ret.add(constGraph.toIsiAmrStringWithSources()+GRAPH_TYPE_SEP+typeString);
            }
        }
        
        String printLabel = al.lexNodes.isEmpty() ? "anon" : graph.getNode(al.lexNodes.iterator().next()).getLabel();
//        System.err.println("constants for "+printLabel+": "+ret.size());
        
        return ret;
    }

    
    //copied over from AMRSignatureBuilder, TODO: unify
    private SGraph makeConstGraph(Set<String> nodes, SGraph graph, GraphNode root) {
        SGraph constGraph = new SGraph();
        for (String nn : nodes) {
            GraphNode node = graph.getNode(nn);
            constGraph.addNode(nn, node.getLabel());
        }
        for (String nn : nodes) {
            GraphNode node = graph.getNode(nn);
            for (GraphEdge e : blobUtils.getBlobEdges(graph, node)) {
                GraphNode other = GeneralBlobUtils.otherNode(node, e);
                if (!nodes.contains(other.getName())) {
                    constGraph.addNode(other.getName(), null);
                }
                constGraph.addEdge(constGraph.getNode(e.getSource().getName()), constGraph.getNode(e.getTarget().getName()), e.getLabel());
            }
        }
        constGraph.addSource("root", root.getName());
        return constGraph;
    }
    
    
    private Iterable<Map<GraphNode, Pair<Set<Pair<GraphNode, String>>, Map<String, String>>>> getCompatibleAnnotations(Map<Pair<GraphNode, String>, Set<GraphNode>> nestedArgNSource2blobTarget) {
        Set<Map<GraphNode, Pair<Set<Pair<GraphNode, String>>, Map<String, String>>>> ret = new HashSet<>();
        
        Set<Map<Pair<GraphNode, String>, Set<GraphNode>>> stack = new HashSet<>();
        stack.add(new HashMap<>());
        
        for (Map.Entry<Pair<GraphNode, String>, Set<GraphNode>> entry : nestedArgNSource2blobTarget.entrySet()) {
            Set<Map<Pair<GraphNode, String>, Set<GraphNode>>> additions = new HashSet<>();
            for (Map<Pair<GraphNode, String>, Set<GraphNode>> fromStack : stack) {
                if (isCompatible(fromStack, entry)) {
                    Map<Pair<GraphNode, String>, Set<GraphNode>> newMap = new HashMap(fromStack);
                    newMap.put(entry.getKey(), entry.getValue());
                    additions.add(newMap);
                }
            }
            stack.addAll(additions);
        }
//        System.err.println("stack size: "+stack.size());
        //TODO possibly one may only want to keep maximal sets in the stack.
        
        //re-format the returned set
        for (Map<Pair<GraphNode, String>, Set<GraphNode>> fromStack : stack) {
//            System.err.println("from Stack: "+fromStack);
            Map<GraphNode, Pair<Set<Pair<GraphNode, String>>, Map<String, String>>> retMap = new HashMap<>();
            Set<Map<Pair<GraphNode, String>, String>> nestedArg2Annots = new HashSet<>();
            nestedArg2Annots.add(new HashMap<>());
            for (Map.Entry<Pair<GraphNode, String>, Set<GraphNode>> entry : fromStack.entrySet()) {
                
//                System.err.println("\tentry: "+entry);
                Set<String> annotsForEntry = getNestedAnnotations(entry, fromStack);
                Set<Map<Pair<GraphNode, String>, String>> newNestedArg2Annots = new HashSet<>();
                for (Map<Pair<GraphNode, String>, String> map : nestedArg2Annots) {
                    for (String annotHere : annotsForEntry) {
                        Map<Pair<GraphNode, String>, String> newMap = new HashMap<>(map);
                        newMap.put(entry.getKey(), annotHere);
                        newNestedArg2Annots.add(newMap);
                    }
                }
                nestedArg2Annots = newNestedArg2Annots;
//                System.err.println("\tannotsForEntry: "+annotsForEntry);
                for (GraphNode blobTarget : entry.getValue()) {
                    Pair<Set<Pair<GraphNode, String>>, Map<String, String>> annotsHere = retMap.get(blobTarget);
                    if (annotsHere == null) {
                        annotsHere = new Pair(new HashSet<>(), null);
                        retMap.put(blobTarget, annotsHere);
                    }
                    annotsHere.left.add(entry.getKey());
                }
            }
            //explore all different types for retMap
            for (Map<Pair<GraphNode, String>, String> map : nestedArg2Annots) {
                Map<GraphNode, Pair<Set<Pair<GraphNode, String>>, Map<String, String>>> retMapHere = new HashMap<>(retMap);
                for (Map.Entry<GraphNode, Pair<Set<Pair<GraphNode, String>>, Map<String, String>>> entry : retMapHere.entrySet()) {
                    Pair<Set<Pair<GraphNode, String>>, Map<String, String>> newPair;
                    Map<String, String> newMap = new HashMap<>();
                    for (Pair<GraphNode, String> nodeNsource : entry.getValue().left) {
                        newMap.put(nodeNsource.right, map.get(nodeNsource));
                    }
                    newPair = new Pair(entry.getValue().left, newMap);
                    entry.setValue(newPair);
                }
//                System.err.println("retMapHere: "+retMapHere);
                ret.add(retMapHere);
            }
            
            
            
            
        }
        if (ret.size() > 100) {
            System.err.println("ret size: "+ret.size());
        }
        return ret;
    }
    
    private Set<String> getNestedAnnotations(Map.Entry<Pair<GraphNode, String>, Set<GraphNode>> entryFromStack, Map<Pair<GraphNode, String>, Set<GraphNode>> fromStack) {
        if (entryFromStack.getValue().isEmpty()) {
            return Collections.singleton("()");
        }
        
        Iterator<GraphNode> nodeIt = entryFromStack.getValue().iterator();
        Set<Map.Entry<Pair<GraphNode, String>, Set<GraphNode>>> intersection = getNestedArgNsource2blobTarget(nodeIt.next()).entrySet();
        while (nodeIt.hasNext()) {
            GraphNode node = nodeIt.next();
            intersection = Sets.intersection(intersection, getNestedArgNsource2blobTarget(node).entrySet());
        }
        intersection = intersection.stream().filter((Map.Entry<Pair<GraphNode, String>, Set<GraphNode>> entry) -> {
            return containsLeft(fromStack.keySet(), entry.getKey().left) && entry.getValue().contains(entryFromStack.getKey().left); //TODO actually, only the node of entry.getKey must be in fromStack. if source disagrees, rename
        }).collect(Collectors.toSet());
        //System.err.println(intersection);
        Set<String> ret = new HashSet<>();
        ret.add("(");
        for (Map.Entry<Pair<GraphNode, String>, Set<GraphNode>> entry : intersection) {
            Set<String> cur = new HashSet<>(ret);
            for (String type : ret) {
                if (type.endsWith(")")) {
                    type += ",";
                }
                type += entry.getKey().right+"()";
                String finalSource = getFirstMatchingRight(fromStack.keySet(), entry.getKey().left);
                if (!entry.getKey().right.equals(finalSource)) {
                    type += "_UNIFY_"+finalSource;
                }
                cur.add(type);
            }
            ret = cur;
        }
        ret = Util.appendToAll(ret, ")", false);
        return ret;
    }
    
    private boolean containsLeft(Set<Pair<GraphNode, String>> set, GraphNode obj) {
        for (Pair<GraphNode, String> p : set) {
            if (obj.equals(p.left)) {
                return true;
            }
        }
        return false;
    }
    
    private String getFirstMatchingRight(Set<Pair<GraphNode, String>> set, GraphNode obj) {
        for (Pair<GraphNode, String> p : set) {
            if (obj.equals(p.left)) {
                return p.right;
            }
        }
        return null;
    }
    
    //check whether adding the entry to the set will form a valid set of annotations.
    private static boolean isCompatible(Map<Pair<GraphNode, String>, Set<GraphNode>> set, Map.Entry<Pair<GraphNode, String>, Set<GraphNode>> entry) {
        //condition one: all Pair<GraphNode, String> must be distinct
        //condition two: no blob target may assign the same source to two different graph nodes.
        //condition three: no blob target may assign two different sources to the same graph node.
        for (Map.Entry<Pair<GraphNode, String>, Set<GraphNode>> inSet : set.entrySet()) {
            if (inSet.getKey().equals(entry.getKey())) {
                return false;//condition one
            }
            if (inSet.getKey().left.equals(entry.getKey().left) || inSet.getKey().right.equals(entry.getKey().right)) {
                if (!Sets.intersection(inSet.getValue(), entry.getValue()).isEmpty()) {
                    return false; // conditions two and three
                }
            }
        }
        return true;
    }
    
    
    
    @Override
    public double scoreGraph(Pair<SGraph, ApplyModifyGraphAlgebra.Type> graph) {
        return blobUtils.scoreGraph(graph);
    }

    @Override
    public Collection<String> getAllPossibleSources(SGraph graph) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    private static final boolean allowOriginalSources = true;
    
    
    
    private final Collection<Function<Map<GraphEdge, String>,Collection<Map<GraphEdge, String>>>> lexiconSourceRemappingsMulti;
    private final Collection<Function<Map<GraphEdge, String>,Collection<Map<GraphEdge, String>>>> lexiconSourceRemappingsOne;
    
     {
        // control which source renamings are allowed
        lexiconSourceRemappingsMulti = new ArrayList<>();
        lexiconSourceRemappingsOne = new ArrayList<>();
        //allowOriginalSources  = true;
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 2));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 3));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 4));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 5));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 6));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 7));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 8));
        lexiconSourceRemappingsMulti.add(map -> promoteObj(map, 9));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 1));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 2));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 3));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 4));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 5));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 6));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 7));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 8));
        lexiconSourceRemappingsOne.add(map -> passivize(map, 9));
//        lexiconSourceRemappingsOne.add(map -> Collections.singleton(promoteObjMax(map)));
    }
    
    private Comparator<GraphEdge> sourceNumberingOrder() {
        return (GraphEdge o1, GraphEdge o2) -> {
            if (o1.getLabel().equals(o2.getLabel())) {
                int index1 = node2firstAlignedIndex.getOrDefault(o1.getTarget().getName(), 0);
                int index2 = node2firstAlignedIndex.getOrDefault(o2.getTarget().getName(), 0);
                return Integer.compare(index1, index2);
            } else {
                return o1.getLabel().compareTo(o2.getLabel());
            }
        };
    }
     
    // this segment is copied over from AMRSignatureBuilder. TODO: should probably be part of blobUtils
    private Collection<Map<GraphEdge, String>> getSourceAssignments(Collection<GraphEdge> blobEdges, SGraph graph) {
        //first get default mapping (called seed here)
        Map<GraphEdge, String> seed = new HashMap<>();
        blobEdges.stream().sorted(sourceNumberingOrder()).forEach(e -> { //for all blobEdges sorted alphabetically. TODO: allow custom comparator, that may also take alignments into account
            String sourceName = blobUtils.edge2Source(e, graph);
            if (seed.containsValue(sourceName)){ //we may have the situation where the same edge label occures twice (in DM), so keep counting until we found a source name that is ok.
                int counter = 2;
                while (seed.containsValue(sourceName+counter)){
                    counter++;
                }
                seed.put(e,sourceName+counter);
            } else {
                seed.put(e,sourceName );
            }
        });
        
        //now apply all remappings that are allowed with multiplicity
        Set<Map<GraphEdge, String>> seedSet = new HashSet();
        seedSet.add(seed);
        Set<Map<GraphEdge, String>> multClosure = closureUnderMult(seedSet);
        
        //now apply remappings that are allowed only once
        Set<Map<GraphEdge, String>> ret = new HashSet();
        for (Map<GraphEdge, String> map : multClosure) {
            if (allowOriginalSources) {
                ret.add(map);
            }
            for (Function<Map<GraphEdge, String>,Collection<Map<GraphEdge, String>>> f : lexiconSourceRemappingsOne) {
                ret.addAll(f.apply(map));
            }
        }
        //again apply all remappings that are allowed with multiplicity
        ret = closureUnderMult(ret);
        
        //return all maps that don't have duplicates
        return ret.stream().filter(map -> !hasDuplicates(map)).collect(Collectors.toList());
    }
    
    
    
    /**
     * recursively applies all functions in lexiconSourceRemappingsMulti, keeping both original and new maps
     * @param seedSet
     * @return 
     */
    private Set<Map<GraphEdge, String>> closureUnderMult(Set<Map<GraphEdge, String>> seedSet) {
        Queue<Map<GraphEdge, String>> agenda = new LinkedList<>(seedSet);
        Set<Map<GraphEdge, String>> seen = new HashSet(seedSet);
        while (!agenda.isEmpty()) {
            Map<GraphEdge, String> map = agenda.poll();
            for (Function<Map<GraphEdge, String>,Collection<Map<GraphEdge, String>>> f : lexiconSourceRemappingsMulti) {
                Collection<Map<GraphEdge, String>> newMaps = f.apply(map);
                for (Map<GraphEdge, String> newMap : newMaps) {
                    if (!seen.contains(newMap)) {
                        agenda.add(newMap);
                    }
                }
                seen.addAll(newMaps);
            }
        }
        return seen;
    }
    
    /**
     * checks whether the map assigns the same source to multiple edges.
     * @param edge2sources
     * @return 
     */
    private boolean hasDuplicates(Map<GraphEdge, String> edge2sources) {
        return edge2sources.keySet().size() != new HashSet(edge2sources.values()).size();
    }
    
    private Collection<Map<GraphEdge, String>> passivize(Map<GraphEdge, String> map, int objNr) {
        String objSrc;
        List<Map<GraphEdge, String>> ret = new ArrayList<>();
        if (objNr == 1) {
            objSrc = OBJ;
        } else if (objNr>1) {
            objSrc = OBJ+objNr;
        } else {
            return Collections.EMPTY_LIST;
        }
        for (Map.Entry<GraphEdge, String> entryO : map.entrySet()) {
            if (entryO.getValue().equals(objSrc)) {
                boolean foundS = false;
                for (Map.Entry<GraphEdge, String> entryS : map.entrySet()) {
                    if (entryS.getValue().equals(SUBJ)) {
                        foundS = true;
                        Map<GraphEdge, String> newMap = new HashMap(map);
                        newMap.put(entryO.getKey(), SUBJ);
                        newMap.put(entryS.getKey(), objSrc);
                        ret.add(newMap);
                    }
                }
                if (!foundS) {
                    Map<GraphEdge, String> newMap = new HashMap(map);
                    newMap.put(entryO.getKey(), SUBJ);
                    ret.add(newMap);
                }
            }
        }
        return ret;
    }
    
    private Collection<Map<GraphEdge, String>> promoteObj(Map<GraphEdge, String> map, int objNr) {
        List<Map<GraphEdge, String>> ret = new ArrayList<>();
        String objSrc = OBJ+objNr;
        String smallerSrc;
        if (objNr == 2) {
            smallerSrc = OBJ;
        } else if (objNr>2) {
            smallerSrc = OBJ+(objNr-1);
        } else {
            return Collections.EMPTY_LIST;
        }
        for (Map.Entry<GraphEdge, String> entrySmaller : map.entrySet()) {
            if (entrySmaller.getValue().equals(smallerSrc)) {
                return Collections.EMPTY_LIST;
            }
        }
        for (Map.Entry<GraphEdge, String> entryO : map.entrySet()) {
            if (entryO.getValue().equals(objSrc)) {
                Map<GraphEdge, String> newMap = new HashMap(map);
                newMap.put(entryO.getKey(), smallerSrc);
                ret.add(newMap);
            }
        }
        return ret;
    }
    
    
    
    public static void main(String[] args) throws IllegalArgumentException, ParseException {
        SGraph graph;
        List<Alignment> als;
        for (int i = 4; i<=4; i++) {
            als = new ArrayList<>();
            switch (i){
                case 0:
                    //the boy sleeps
                    graph = new IsiAmrInputCodec().read("(s / sleep :ARG0 (b / boy))");
                    als.add(Alignment.read("s!||2-3"));
                    als.add(Alignment.read("b!||1-2"));
                    break;
                case 1:
                    //the boy wants to sleep
                    graph = new IsiAmrInputCodec().read("(w / want :ARG1 (s / sleep :ARG0 (b / boy)) :ARG0 b)");
                    als.add(Alignment.read("s!||4-5"));
                    als.add(Alignment.read("b!||1-2"));
                    als.add(Alignment.read("w!||2-3"));
                    break;
                case 2:
                    //the girl sings and dances
                    graph = new IsiAmrInputCodec().read("(a / and :op1 (s / sing :ARG0 (g / girl)) :op2 (d / dance :ARG0 g))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("s!||2-3"));
                    als.add(Alignment.read("d!||4-5"));
                    als.add(Alignment.read("g!||1-2"));
                    break;
                case 3:
                    //the lizzard wants and trains to sing beautifully
                    graph = new IsiAmrInputCodec().read("(a / and :op1 (w / want :ARG1"
                                + " (s / sing :ARG0 (l / lizard) :manner (b / beautiful)) :ARG0 l)"
                            + " :op2 (t / train :ARG1 s :ARG0 l))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("s!||7-8"));
                    als.add(Alignment.read("b!||8-9"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("w!||2-3"));
                    als.add(Alignment.read("t!||5-6"));
                    break;
                case 4:
                    //the lizzard wants and seems to dance (testing coordination of complex types)
                    graph = new IsiAmrInputCodec().read("(a / and :op1 (w / want :ARG1"
                                + " (d / dance :ARG0 (l / lizard)) :ARG0 l)"
                            + " :op2 (s / seem :ARG1 d))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("d!||6-7"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("w!||2-3"));
                    als.add(Alignment.read("s!||4-5"));
                    break;
                case 5:
                    //the lion commands and persuades the snake to dance (testing rename and coord of rename)
                    graph = new IsiAmrInputCodec().read("(a / and :op1 (c / command"
                                + " :ARG2 (d / dance :ARG0 (s / snake)) :ARG1 s :ARG0 (l / lion))"
                            + " :op2 (p / persuade :ARG2 d :ARG1 s :ARG0 l))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("d!||8-9"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("c!||2-3"));
                    als.add(Alignment.read("p!||4-7"));
                    als.add(Alignment.read("s!||6-7"));
                    break;
                case 6:
                    //the boy wants to sleep, bad alignments (testing multiple aligned nodes)
                    graph = new IsiAmrInputCodec().read("(w / want :ARG1 (s / sleep :TEST-of (b / boy)) :ARG0 b)");
                    als.add(Alignment.read("w|s!||4-5"));
                    als.add(Alignment.read("b!||1-2"));
                    break;
                case 7:
                    //the boy wants to seem to sleep, (testing nested control and raising)
                    graph = new IsiAmrInputCodec().read("(w / want :ARG1 (s / seem :ARG1 (s2 / sleep :ARG0 (b / boy))) :ARG0 b)");
                    als.add(Alignment.read("w!||2-3"));
                    als.add(Alignment.read("s!||4-5"));
                    als.add(Alignment.read("s2!||6-7"));
                    als.add(Alignment.read("b!||1-2"));
                    break;
                default:
                    return;
            }

            ConcreteAlignmentSignatureBuilder sb = new ConcreteAlignmentSignatureBuilder(graph, als, new AMRBlobUtils());
            System.err.println(sb.node2blobTargets);
            System.err.println(sb.node2arguments);
            System.err.println(sb.node2argument2sources);
            System.err.println(sb.node2nestedArgNsource2blobTarget);
            for (Alignment al : als) {
                System.err.println();
                System.err.println(al);
                System.err.println(sb.getConstantsForAlignment(al, graph, false));
            }
//            for (Map<Pair<GraphNode, String>, Set<GraphNode>> nestedArgNSource2blobTarget : sb.node2nestedArgNsource2blobTarget.values()) {
//                System.err.println(getCompatibleAnnotations(nestedArgNSource2blobTarget));
//            }
//            for (GraphNode node : graph.getGraph().vertexSet()) {
//                System.err.println(node.toString()+": "+sb.getSourceAssignments(sb.blobUtils.getBlobEdges(graph, node), graph));
//            }
            System.err.println();
        }
    }
}
