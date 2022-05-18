/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr;

import com.google.common.collect.Sets;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.OBJ;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.SUBJ;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.TupleIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author Jonas
 */
public class NoPassiveSignatureBuilder extends AMRSignatureBuilder {

    @Override
    protected Collection<Map<GraphEdge, String>> getSourceAssignments(Collection<GraphEdge> blobEdges, SGraph graph) {
        lexiconSourceRemappingsMulti = Collections.EMPTY_LIST;
        lexiconSourceRemappingsOne= Collections.EMPTY_LIST;
        return super.getSourceAssignments(blobEdges, graph);
    }

    @Override
    protected Collection<Map<GraphNode, String>> getBlobTargets(SGraph graph, GraphNode node) {
        Collection<Map<GraphNode, String>> ret = new HashSet<>();
        for (Map<GraphEdge, String> map : getSourceAssignments(blobUtils.getBlobEdges(graph, node), graph)) {
            Map<GraphNode, String> retHere = new HashMap<>();
            for (GraphEdge edge : map.keySet()) {
                retHere.put(GeneralBlobUtils.otherNode(node, edge), map.get(edge));
            }
            ret.add(retHere);
        }
        return ret;
    }
    
    @Override
    protected Collection<Map<GraphNode, String>> getTargets(SGraph graph, GraphNode node) {
        Collection<Map<GraphNode, String>> blob = getBlobTargets(graph, node);
        if (blobUtils.isConjunctionNode(graph, node)) {
            Collection<Map<GraphNode, String>> conj = getConjunctionTargets(graph, node);
            Collection<Map<GraphNode, String>> ret = new HashSet<>();
            for (Map<GraphNode, String> blobMap : blob) {
                Map<GraphNode, String> filteredBlobMap = new HashMap<>();
                for (GraphNode n : blobMap.keySet()) {
                    GraphEdge blobEdge = graph.getGraph().getEdge(node, n);
                    if (blobEdge == null || !blobUtils.isConjEdgeLabel(blobEdge.getLabel())) {
                        filteredBlobMap.put(n, blobMap.get(n));
                    }
                }
                blobMap = filteredBlobMap;
                for (Map<GraphNode, String> conjMap : conj) {
                    Map<GraphNode, String> retHere = new HashMap<>(blobMap);
                    boolean hitForbidden = false;
                    for (GraphNode n : conjMap.keySet()) {
                        String s = conjMap.get(n);
                        if (blobMap.values().contains(s)) {
                            hitForbidden = true;
                            break;
                        } else {
                            retHere.put(n, s);//will override blob-targets with conjunction targets. I think this is the wanted behaviour for now -- JG
                        }
                    }
                    if (!hitForbidden) {
                        ret.add(retHere);
                    }
                }
            }
            return ret;
        } else if (blobUtils.isRaisingNode(graph, node)) {
            GraphEdge argEdge = blobUtils.getArgEdges(node, graph).iterator().next();//know we have exactly one if node is raising node
            Collection<Map<GraphNode, String>> ret = new HashSet<>();
            for (Map<GraphNode, String> blobMap : blob) {
                    //HIER CODE gelöscht! --ml
                    ret.add(blobMap);
            }
            return ret;
        } else {
            return blob;
        }
    }
    
    
        private Collection<Map<GraphNode, String>> getConjunctionTargets(SGraph graph, GraphNode coordNode) {
        if (blobUtils.isConjunctionNode(graph, coordNode)) {
            Collection<Map<GraphNode, String>> ret = new HashSet<>();
            Set<GraphNode> jointTargets = new HashSet();
            jointTargets.addAll(graph.getGraph().vertexSet());//add all first, remove wrong nodes later
            List<GraphNode> opTargets = new ArrayList<>();
            for (GraphEdge edge : graph.getGraph().outgoingEdgesOf(coordNode)) {
                if (blobUtils.isConjEdgeLabel(edge.getLabel())) {
                    GraphNode other = GeneralBlobUtils.otherNode(coordNode, edge);
                    opTargets.add(other);
                    Collection<Map<GraphNode, String>> otherTargets = getTargets(graph, other);
                    Set<GraphNode> targetsHere = new HashSet<>();
                    for (Map<GraphNode, String> otMap : otherTargets) {
                        targetsHere.addAll(otMap.keySet());
                    }
                    jointTargets.removeIf(lambdaNode -> !targetsHere.contains(lambdaNode));
                }
            }
            Collection<Map<GraphNode, String>>[] iterable = opTargets.stream().map(opTgt -> getTargets(graph, opTgt)).collect(Collectors.toList())
                    .toArray(new Collection[0]);
            TupleIterator<Map<GraphNode, String>> tupleIt = new TupleIterator<>(iterable, new Map[iterable.length]);
            
            //get maxDomain (maybe unnecessary)
            Set<Set<GraphNode>> maxDomains = new HashSet<>();
            int maxDomainSize = 0;
            while (tupleIt.hasNext()) {
                Map<GraphNode, String>[] targets = tupleIt.next();
                Set<GraphNode> domainHere = new HashSet<>();
                for (GraphNode jt : jointTargets) {
                    boolean consensus = true;
                    String consensusString = targets[0].get(jt);
                    for (Map<GraphNode, String> n2s : targets) {
                        if (consensusString == null || !consensusString.equals(n2s.get(jt))) {
                            consensus = false;
                            break;
                        }
                    }
                    if (consensus) {
                        domainHere.add(jt);
                    }
                }
                if (domainHere.size() == maxDomainSize) {
                    maxDomains.add(domainHere);
                } else if (domainHere.size() > maxDomainSize) {
                    maxDomainSize = domainHere.size();
                    maxDomains = new HashSet();
                    maxDomains.add(domainHere);
                }
            }
            Set<GraphNode> maxDomain = graph.getGraph().vertexSet();
            for (Set<GraphNode> dom : maxDomains) {
                maxDomain = Sets.intersection(maxDomain, dom);
            }
            
            tupleIt = new TupleIterator<>(iterable, new Map[iterable.length]);
            while (tupleIt.hasNext()) {
                Map<GraphNode, String>[] targets = tupleIt.next();
                Set<GraphNode> domainHere = new HashSet<>();
                for (GraphNode jt : jointTargets) {
                    boolean consensus = true;
                    String consensusString = targets[0].get(jt);
                    for (Map<GraphNode, String> n2s : targets) {
                        if (consensusString == null || !consensusString.equals(n2s.get(jt))) {
                            consensus = false;
                            break;
                        }
                    }
                    if (consensus) {
                        domainHere.add(jt);
                    }
                }
                if (domainHere.containsAll(maxDomain)) {
                    Map<GraphNode, String> retHere = new HashMap<>();
                    for (GraphNode jt : maxDomain) {
                        retHere.put(jt, targets[0].get(jt));//which target doesnt matter, because of consensus
                    }
                    ret.add(retHere);
                }
            }
            
            return ret;
            
            
        } else {
            return new HashSet<>();
        }
    }
    
}
