package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.up.ling.irtg.algebra.graph.*;
import de.up.ling.irtg.util.MutableInteger;

import java.util.ArrayList;
import java.util.List;

public class UnifyInEdges {

    // when true, prints info about moved edges in non-decomposable graphs
    private boolean verbose = false;

    public void setVerbose(boolean verbose_setting) {
        this.verbose = verbose_setting;
    }

    public boolean getVerbose() {
        return this.verbose;
    }

    AMRBlobUtils blobUtils;

    public UnifyInEdges(AMRBlobUtils blobUtils) {
        this.blobUtils = blobUtils;
        this.setVerbose(false);
    }

    public boolean runOnInstance(MRInstance mrInst, MutableInteger totalMovedEdges) {
        boolean changed = false;
        int totalMovedEdgesBefore = totalMovedEdges.getValue();  // only needed for verbose setting
        for (Alignment al : mrInst.getAlignments()) {
            unifyInEdgesForAlignment(al, mrInst.getGraph(), totalMovedEdges);
        }
        if (totalMovedEdges.getValue() > totalMovedEdgesBefore) {
            changed = true;
            if (this.getVerbose()) {
                System.out.println("Moved edges above belong to AMR id " + mrInst.getId() + "\n");
            }
        }
        return changed;
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
        if (inAndOutNodes.inNodes.size() > 1) {
            GraphNode unificationTarget = AMRSignatureBuilder.getClosestToRootOrLexicallyFirst(inAndOutNodes.inNodes, graph);
            for (GraphNode node : inAndOutNodes.inNodes) {
                if (node != unificationTarget) {
                    for (GraphEdge e : graph.getGraph().edgesOf(node)) {
                        if (!blobUtils.isBlobEdge(node, e) && !al.nodes.contains(BlobUtils.otherNode(node, e).getName())) {
                            graph.getGraph().removeEdge(e);
                            if (e.getSource() == node) {
                                if (this.getVerbose()) {
                                    System.out.println(al);
                                    System.out.println("reattaching " + e.getLabel() + " in-edge from " + e.getSource().getName()
                                            + "->" + e.getTarget().getName() + " to " + unificationTarget.getName() + "->" + e.getTarget().getName());
                                }
                                graph.addEdge(unificationTarget, e.getTarget(), e.getLabel());
                            } else {
                                if (this.getVerbose()) {
                                    System.out.println(al);
                                    System.out.println("reattaching " + e.getLabel() + " in-edge from " + e.getSource().getName()
                                            + "->" + e.getTarget().getName() + " to " + e.getSource().getName() + "->" + unificationTarget.getName());
                                }
                                graph.addEdge(e.getSource(), unificationTarget, e.getLabel());
                            }
                            totalMovedEdges.incValue();
                        }
                    }
                }
            }
        }
    }




}
