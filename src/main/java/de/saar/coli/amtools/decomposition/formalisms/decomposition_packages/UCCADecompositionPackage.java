package de.saar.coli.amtools.decomposition.formalisms.decomposition_packages;

import de.saar.coli.amrtagging.*;
import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.simple.Sentence;

import java.util.*;

public class UCCADecompositionPackage extends DecompositionPackage {

    public static final String[] orderedLexicalEdgeLabels = new String[]{"L", "P", "S", "N", "C", "A", "G", "R", "T", "D","U", "F", "H"};

    private final SGraph sgraph;
    private final List<CoreLabel> tokens;
    private final List<String> mappedPosTags;
    private final List<String> mappedLemmas;


    public UCCADecompositionPackage(Object[] UCCADecompositionPackageBundle, EdgeAttachmentHeuristic edgeAttachmentHeuristic) {
        super((MRInstance) UCCADecompositionPackageBundle[1], edgeAttachmentHeuristic, false);
        this.sgraph = (SGraph) UCCADecompositionPackageBundle[0];
        this.tokens = (List<CoreLabel>) UCCADecompositionPackageBundle[2];
        this.mappedPosTags = (List<String>) UCCADecompositionPackageBundle[3];
        this.mappedLemmas = (List<String>) UCCADecompositionPackageBundle[4];
        //this.mappedNeTags = mappedNeTags;


    }


    @Override
    public AmConllSentence makeBaseAmConllSentence() {
        AmConllSentence sent = new AmConllSentence();
        List<Alignment> alignments = mrInstance.getAlignments();
        ArrayList<Integer> lexNodes = new ArrayList<>();

        for (Alignment al:alignments){
            for (String lexNode: al.lexNodes){
                lexNodes.add(Integer.parseInt(lexNode.split("_")[1]));
            }
        }

        Collections.sort(lexNodes);




        for (int lexNode : lexNodes) {
            AmConllEntry amConllEntry = new AmConllEntry(lexNode + 1, sgraph.getNode("n_" + String.valueOf(lexNode)).toString());
            amConllEntry.setAligned(true);
            amConllEntry.setHead(0);
            amConllEntry.setEdgeLabel(AmConllEntry.IGNORE);
            sent.add(amConllEntry);
        }






        SizeFixerUCCA sizeFixer = new SizeFixerUCCA(mappedPosTags, tokens, mappedLemmas, sent.words());
        Sentence stanfAn = new Sentence(mrInstance.getSentence());
        List<String> neTags = new ArrayList<>(stanfAn.nerTags());

        List<List<String>> adjustedLemmasPosNe= sizeFixer.adjust(mappedPosTags, mappedLemmas, neTags);


        sent.addLemmas(adjustedLemmasPosNe.get(0));
        sent.addPos(adjustedLemmasPosNe.get(1));


        //artificial root. Is it necessary? UCCA graphs are already rooted.
        AmConllEntry artRoot = new AmConllEntry(lexNodes.size() +1, SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setEdgeLabel(AmConllEntry.ROOT_SYM);
        artRoot.setHead(0);
        artRoot.setAligned(true);
        artRoot.setLemma(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setPos(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setLexLabel(AmConllEntry.LEMMA_PLACEHOLDER);
        sent.add(artRoot);

        List<String> refinedNeTags = adjustedLemmasPosNe.get(2);
        refinedNeTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        sent.addNEs(refinedNeTags);

        return sent;
    }

    @Override
    public GraphNode getLexNodeFromGraphFragment(SGraph graphFragment) {
        ArrayList<GraphNode> nodes = new ArrayList<GraphNode>(graphFragment.getGraph().vertexSet());

        //look at the edgelabels in hierarchical order (in UCCA some nodes are more likely to be lexical than others)
        if (nodes.size() == 1){
            return nodes.get(0);
        }

        else {
            for (String l : orderedLexicalEdgeLabels) {
                for (GraphNode n : nodes) {
                    for (GraphEdge e : graphFragment.getGraph().incomingEdgesOf(n)) {
                        //discard our manipulations for old_raising and new_raising if there are any
                        String edgeLabel = e.getLabel().split("-|_")[0];


                        //use contains for the case of collapsed edges
                        if (edgeLabel.equals(l) | edgeLabel.contains(l)) {

                            return n;
                        }

                    }
                }
            }
        }

        return null;
    }

    @Override
    public int getSentencePositionForGraphFragment(SGraph graphFragment) {

        String lexNode = getLexNodeFromGraphFragment(graphFragment).getName();

        //1-based
        int sentencePosition = Integer.parseInt(lexNode.split("_")[1]) + 1;
        return sentencePosition;


        //because 1-based, using the node labels should be okay given that any contraction
        // must have occurred non-terminal edges
    }


    @Override
    public EdgeAttachmentHeuristic getEdgeAttachmentHeuristic() {
        return edgeAttachmentHeuristic;
    }
}
