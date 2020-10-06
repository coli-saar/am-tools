package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SDPDecompositionPackage extends DecompositionPackage {


    private final AMRBlobUtils blobUtils;
    private final Graph sdpGraph;

    public SDPDecompositionPackage(Graph sdpGraph, AMRBlobUtils blobUtils) {
        this.blobUtils = blobUtils;
        this.sdpGraph = sdpGraph;
    }

    @Override
    public AmConllSentence makeBaseAmConllSentence() {
        AmConllSentence sent = new AmConllSentence();

        //add all words from the SDP graph, treating all as ignored for now
        for (Node word : sdpGraph.getNodes()) {
            if (word.id >= 1) {
                AmConllEntry amConllEntry = new AmConllEntry(word.id, word.form);
                amConllEntry.setAligned(true);
                amConllEntry.setHead(0);
                amConllEntry.setLemma(word.lemma);
                amConllEntry.setPos(word.pos);
                amConllEntry.setEdgeLabel(AmConllEntry.IGNORE);
                sent.add(amConllEntry);
            }
        }
        // add artificial root
        AmConllEntry artRoot = new AmConllEntry(sdpGraph.getNNodes(), SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setEdgeLabel(AmConllEntry.ROOT_SYM);
        artRoot.setHead(0);
        artRoot.setAligned(true);
        artRoot.setLemma(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setPos(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setLexLabel(AmConllEntry.LEMMA_PLACEHOLDER);
        sent.add(artRoot);

        //add NE tags
        List<String> forms = sdpGraph.getNodes().subList(1, sdpGraph.getNNodes()).stream().map(word -> word.form).collect(Collectors.toList());
        Sentence stanfAn = new Sentence(forms);
        List<String> neTags = new ArrayList<>(stanfAn.nerTags());
        neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        sent.addNEs(neTags);

        return sent;
    }

    @Override
    public GraphNode getLexNodeFromGraphFragment(SGraph graphFragment) {
        return graphFragment.getNode(graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME));
    }

    @Override
    public int getSentencePositionForGraphFragment(SGraph graphFragment) {
        String rootNodeName = graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        int id;
        if (rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
            id = sdpGraph.getNNodes();// the artificial root position is the last in the sentence, which is original size + 1
        } else {
            id = Integer.parseInt(rootNodeName.substring(2));// maps i_x to x
        }
        return id;
    }


    @Override
    public AMRBlobUtils getBlobUtils() {
        return blobUtils;
    }
}
