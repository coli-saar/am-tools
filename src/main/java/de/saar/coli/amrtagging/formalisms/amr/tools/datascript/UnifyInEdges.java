package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

import de.saar.basic.Agenda;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.up.ling.irtg.algebra.graph.*;
import de.up.ling.irtg.util.MutableInteger;

import java.util.HashSet;
import java.util.Set;

public class UnifyInEdges {

    // when true, prints info about moved edges in non-decomposable graphs
    boolean VERBOSE = false;

    AMRBlobUtils blobUtils;

    public UnifyInEdges(AMRBlobUtils blobUtils) {
        this.blobUtils = blobUtils;
    }

    public void unifyInEdges(MRInstance mrInst, MutableInteger totalMovedEdges) {
        for (Alignment al : mrInst.getAlignments()) {
            unifyInEdgesForAlignment(al, mrInst.getGraph(), totalMovedEdges);
        }
    }

    public void unifyInEdges(MRInstance mrInst, MutableInteger totalMovedEdges, boolean verbose) {
        VERBOSE = verbose;
        int totalMovedEdgesBefore = totalMovedEdges.getValue();
        for (Alignment al : mrInst.getAlignments()) {
            unifyInEdgesForAlignment(al, mrInst.getGraph(), totalMovedEdges);
        }
        if (totalMovedEdges.getValue() > totalMovedEdgesBefore && VERBOSE) {
            System.out.println("Moved edges above belong to AMR id " + mrInst.getId());
        }
    }

    private void unifyInEdgesForAlignment(Alignment al, SGraph graph, MutableInteger totalMovedEdges) {
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

        //boolean changedGraph = false;
        if (inAndOutNodes.inNodes.size() > 1) {
            GraphNode unificationTarget = AMRSignatureBuilder.getClosestToRootOrLexicallyFirst(inAndOutNodes.inNodes, graph);
            for (GraphNode node : inAndOutNodes.inNodes) {
                if (node != unificationTarget) {
                    for (GraphEdge e : graph.getGraph().edgesOf(node)) {
                        if (!blobUtils.isBlobEdge(node, e) && !al.nodes.contains(BlobUtils.otherNode(node, e).getName())) {
                            graph.getGraph().removeEdge(e);
                            if (e.getSource() == node) {
                                if (VERBOSE) {
                                    System.out.println(al);
                                    System.out.println("reattaching " + e.getLabel() + " in-edge from " + e.getSource().getName()
                                            + "->" + e.getTarget().getName() + " to " + unificationTarget.getName() + "->" + e.getTarget().getName());
                                }
                                graph.addEdge(unificationTarget, e.getTarget(), e.getLabel());
                            } else {
                                if (VERBOSE) {
                                    System.out.println(al);
                                    System.out.println("reattaching " + e.getLabel() + " in-edge from " + e.getSource().getName()
                                            + "->" + e.getTarget().getName() + " to " + e.getSource().getName() + "->" + unificationTarget.getName());
                                }
                                graph.addEdge(e.getSource(), unificationTarget, e.getLabel());
                            }
                            totalMovedEdges.incValue();
                            //changedGraph = true;
                        }
                    }
                }
            }
        }
        //return changedGraph;
    }




}
