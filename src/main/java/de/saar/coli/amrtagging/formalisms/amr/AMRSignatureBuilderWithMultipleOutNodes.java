package de.saar.coli.amrtagging.formalisms.amr;

import com.google.common.collect.Sets;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.up.ling.irtg.algebra.graph.*;
import de.up.ling.irtg.util.Util;
import de.up.ling.tree.ParseException;

import java.util.*;
import java.util.stream.Collectors;

import static de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP;
import static de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.OP_COREFMARKER;

public class AMRSignatureBuilderWithMultipleOutNodes extends AMRSignatureBuilder {

    @Override
    public Set<String> getConstantsForAlignment(Alignment al, SGraph graph, boolean addCoref) throws IllegalArgumentException, ParseException {

        InAndOutNodes inAndOutNodes = new InAndOutNodes(graph, al, blobUtils);

        if (inAndOutNodes.inNodes.size() > 1) {
            throw new IllegalArgumentException("Cannot create a constant for this alignment ("+al.toString()+"): More than one node with edges from outside.");
        }

        // we know now that there is only one inNode
        GraphNode root = inAndOutNodes.inNodes.iterator().next();

        //if there is no node with blob edge pointing out of alignment node cluster, we are done. Otherwise continue, focussing on that one node.
        if (inAndOutNodes.outNodes.isEmpty()) {
            SGraph constGraph = makeConstGraph(al.nodes, graph, root);
            return Collections.singleton(linearizeToAMConstant(constGraph, ApplyModifyGraphAlgebra.Type.EMPTY_TYPE.toString()));
        }

        if (inAndOutNodes.outNodes.size() == 1) {
            GraphNode outNode = inAndOutNodes.outNodes.iterator().next();//at this point, there is exactly one. This is the one node in the alignment with blob edges that leave the alignment. For their endpoints, we need to find sources.
            Set<String> ret = new HashSet<>();

            Collection<GraphEdge> blobEdges = blobUtils.getBlobEdges(graph, outNode);
            if (blobUtils.isConjunctionNode(graph, outNode)) {
                addConstantsForCoordNode(graph, outNode, al, root, blobEdges, addCoref, ret);
            } else if (blobUtils.isRaisingNode(graph, outNode)) {
                addConstantsForRaisingNode(graph, outNode, al, root, blobEdges, addCoref, ret);
            } else {
                addConstantsForNormalNode(graph, outNode, al, root, blobEdges, addCoref, ret);
            }
            if (addCoref) {
                ret.add(ApplyModifyGraphAlgebra.OP_COREF + al.span.start);
            }
            return ret;
        } else {
            Set<String> ret = new HashSet<>();
            addConstantsForMultipleOutNodes(graph, al, root, addCoref, ret);
            return ret;
        }
    }

    protected void addConstantsForMultipleOutNodes(SGraph graph, Alignment al, GraphNode root, boolean addCoref, Set<String> ret) {
        Set<Integer> corefIDs = new HashSet<>();
        if (addCoref) {
            corefIDs.add(al.span.start);
        }
        addConstantsForMultipleOutNodes(graph, al.nodes, root, corefIDs, ret);
    }

    protected void addConstantsForMultipleOutNodes(SGraph graph, Set<String> allNodeNames, GraphNode root, Set<Integer> corefIDs, Set<String> ret) {
        //iterate over all source assignments
        Set<GraphEdge> allOutEdges = new HashSet<>();
        Set<GraphNode> allNodes = allNodeNames.stream().map(graph::getNode).collect(Collectors.toSet());
        for (GraphNode node : allNodes) {
            for (GraphEdge edge : blobUtils.getBlobEdges(graph, node)) {
                if (!allNodeNames.contains(BlobUtils.otherNode(node, edge))) {
                    allOutEdges.add(edge);
                }
            }
        }
        for (Map<GraphNode, String> blobTargets : getBlobTargets(graph, allNodes)) {
            //start with constant graph
            SGraph constGraph = makeConstGraph(allNodeNames, graph, root);

            //find source annotations, i.e. build the constant's type. There are several possibilities, so we build a set of possible types.
            Set<String> typeStrings = new HashSet<>();
            typeStrings.add("(");
            for (GraphEdge edge : allOutEdges) {

                GraphNode other = getNodeOutsideOfAlignment(edge, allNodes);

                String src = blobTargets.get(other);

                //add source to graph
                constGraph.addSource(src, other.getName());

                //add source to type
                typeStrings = Util.appendToAll(typeStrings, ",", false, s -> s.endsWith(")"));
                typeStrings = Util.appendToAll(typeStrings, src+"(", false);

                //intersection of other's and node's targets
                Collection<Map<GraphNode, String>> recTargetsSet = getTargets(graph, other);
                Set<String> newTypeStrings = new HashSet();
                for (Map<GraphNode, String> recTargets : recTargetsSet) {
                    Set<GraphNode> intersect = Sets.intersection(blobTargets.keySet(), recTargets.keySet());
                    Set<String> newTypeStringsHere = new HashSet(typeStrings);
                    for (GraphNode recNode : intersect) {
                        Set<String> localTypes = Util.appendToAll(newTypeStringsHere, ",", false, s -> !s.endsWith("("));
                        localTypes = Util.appendToAll(localTypes, recTargets.get(recNode)+"()_UNIFY_"+blobTargets.get(recNode), false);
                        newTypeStringsHere.addAll(localTypes);
                    }
                    newTypeStrings.addAll(newTypeStringsHere);
                }
                typeStrings.addAll(newTypeStrings);
                //close bracket
                typeStrings = Util.appendToAll(typeStrings, ")", false);
            }
            //finish type and graph strings, add to returned set
            typeStrings = Util.appendToAll(typeStrings, ")", false);
            for (String typeString : typeStrings) {
                //typeString = new ApplyModifyGraphAlgebra.Type(typeString).closure().toString();
                ret.add(linearizeToAMConstant(constGraph, typeString));
                //coref: index used is the one from alignment!
                for (int corefID : corefIDs) {
                    // small note: this does not has as robust error messaging as the rest, because it's too complicated,
                    // and not in use right now anyway -- JG
                    String graphString = constGraph.toIsiAmrStringWithSources();
                    graphString = graphString.replaceFirst("<root>", "<root, COREF"+corefID+">");
                    ret.add(OP_COREFMARKER+corefID+"_"+graphString+GRAPH_TYPE_SEP+typeString);
                }
            }

        }
    }

    private Collection<Map<GraphNode, String>> getBlobTargets(SGraph graph, Set<GraphNode> nodes) {
        Set<GraphEdge> allOutEdges = new HashSet<>();
        for (GraphNode node : nodes) {
            for (GraphEdge edge : blobUtils.getBlobEdges(graph, node)) {
                if (!nodes.contains(BlobUtils.otherNode(node, edge))) {
                    allOutEdges.add(edge);
                }
            }
        }
        Collection<Map<GraphNode, String>> ret = new HashSet<>();
        for (Map<GraphEdge, String> map : getSourceAssignments(allOutEdges, graph)) {
            Map<GraphNode, String> retHere = new HashMap<>();
            for (GraphEdge edge : map.keySet()) {
                if (nodes.contains(edge.getSource())) {
                    retHere.put(edge.getTarget(), map.get(edge));
                } else {
                    retHere.put(edge.getSource(), map.get(edge));
                }
            }
            ret.add(retHere);
        }
        return ret;
    }

    private GraphNode getNodeOutsideOfAlignment(GraphEdge edge, Set<GraphNode> nodes) {
        if (nodes.contains(edge.getSource())) {
            return edge.getTarget();
        } else {
            return edge.getSource();
        }
    }
}
