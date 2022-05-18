package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

import de.saar.basic.Agenda;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.up.ling.irtg.algebra.graph.*;

import java.util.HashSet;
import java.util.Set;

public class UnifyInEdges {

    AMRBlobUtils blobUtils;

    public UnifyInEdges(AMRBlobUtils blobUtils) {
        this.blobUtils = blobUtils;
    }

    public void unifyInEdges(MRInstance mrInst) {
        System.out.println(mrInst.getGraph().toIsiAmrStringWithSources());
        for (Alignment al : mrInst.getAlignments()) {
            unifyInEdgesForAlignment(al, mrInst.getGraph());
        }
    }

    private void unifyInEdgesForAlignment(Alignment al, SGraph graph) {
        AMRSignatureBuilder.InAndOutNodes inAndOutNodes = new AMRSignatureBuilder.InAndOutNodes(graph, al, blobUtils);

//        if (outNodes.size() > 1) {
//            GraphNode unificationTarget = getClosestToRootOrLexicallyFirst(outNodes, graph);
//            for (GraphNode node : outNodes) {
//                if (!node.equals(unificationTarget)) {
//                    for (GraphEdge e : graph.getGraph().edgesOf(node)) {
//                        if (blobUtils.isBlobEdge(node, e) && !al.nodes.contains(BlobUtils.otherNode(node, e).getName())) {
//                            graph.getGraph().removeEdge(e);
//                            if (e.getSource().equals(node)) {
//                                System.out.println(al);
//                                System.out.println("reattaching " + e.getLabel() + " out-edge from " + e.getSource().getName()
//                                        + "->" + e.getTarget().getName() + " to " + unificationTarget.getName() + "->" + e.getTarget().getName());
//                                graph.addEdge(unificationTarget, e.getTarget(), e.getLabel());
//                            } else {
//                                System.out.println(al);
//                                System.out.println("reattaching " + e.getLabel() + " out-edge from " + e.getSource().getName()
//                                        + "->" + e.getTarget().getName() + " to " +  e.getSource().getName() + "->" + unificationTarget.getName());
//                                graph.addEdge(e.getSource(), unificationTarget, e.getLabel());
//                            }
//                        }
//                    }
//                }
//            }
//        }

        if (inAndOutNodes.inNodes.size() > 1) {
            GraphNode unificationTarget = getClosestToRootOrLexicallyFirst(inAndOutNodes.inNodes, graph);
            for (GraphNode node : inAndOutNodes.inNodes) {
                if (node != unificationTarget) {
                    for (GraphEdge e : graph.getGraph().edgesOf(node)) {
                        if (!blobUtils.isBlobEdge(node, e) && !al.nodes.contains(BlobUtils.otherNode(node, e).getName())) {
                            graph.getGraph().removeEdge(e);
                            if (e.getSource() == node) {
                                System.out.println(al);
                                System.out.println("reattaching " + e.getLabel() + " in-edge from " + e.getSource().getName()
                                        + "->" + e.getTarget().getName() + " to " + unificationTarget.getName() + "->" + e.getTarget().getName());
                                graph.addEdge(unificationTarget, e.getTarget(), e.getLabel());
                            } else {
                                System.out.println(al);
                                System.out.println("reattaching " + e.getLabel() + " in-edge from " + e.getSource().getName()
                                        + "->" + e.getTarget().getName() + " to " +  e.getSource().getName() + "->" + unificationTarget.getName());
                                graph.addEdge(e.getSource(), unificationTarget, e.getLabel());
                            }
                        }
                    }
                }
            }
        }
    }

    private GraphNode getClosestToRootOrLexicallyFirst(Set<GraphNode> nodes, SGraph graph) {
        GraphNode closest = null;
        int minDistance = Integer.MAX_VALUE;
        String bestNodeLabel = null;
        for (GraphNode node : nodes) {
            int distance = getDistanceToRoot(node, graph);
            if (distance < minDistance) {
                closest = node;
                minDistance = distance;
                bestNodeLabel = node.getLabel();
            } else if (distance == minDistance) {
                if (bestNodeLabel == null || node.getLabel().compareTo(bestNodeLabel) < 0) {
                    closest = node;
                    bestNodeLabel = node.getLabel();
                }
            }
        }
        return closest;
    }

    private int getDistanceToRoot(GraphNode node, SGraph graph) {
        int distance = 0;
        Set<GraphNode> seen = new HashSet<>();
        seen.add(node);
        Agenda<GraphNode> agenda = new Agenda<>();
        agenda.add(node);
        GraphNode root = graph.getNode(graph.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME));
        while (!seen.contains(root)) {
            distance++;
            Set<GraphNode> newThisRound = new HashSet<>();
            while (!agenda.isEmpty()) {
                GraphNode pulled = agenda.poll();
                for (GraphEdge e : graph.getGraph().edgesOf(pulled)) {
                    GraphNode other = BlobUtils.otherNode(pulled, e);
                    if (!seen.contains(other)) {
                        seen.add(other);
                        newThisRound.add(other);
                    }
                }
            }
            agenda.addAll(newThisRound);
        }
        return distance;
    }

}
