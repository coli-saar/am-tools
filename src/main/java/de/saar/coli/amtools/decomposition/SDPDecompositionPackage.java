package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
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
    private final MRInstance mrInstance;
    private final boolean noNamedEntityTags;

    public SDPDecompositionPackage(MRInstance instance, AMRBlobUtils blobUtils, boolean noNamedEntityTags) {
        this.blobUtils = blobUtils;
        this.mrInstance = instance;
        this.noNamedEntityTags = noNamedEntityTags;
    }

    @Override
    public AmConllSentence makeBaseAmConllSentence() {
        AmConllSentence sent = new AmConllSentence();

        //add all words from the SDP graph, treating all as ignored for now
        List<String> words = mrInstance.getSentence();
        List<String> lemmas = mrInstance.getLemmas();
        List<String> posTags = mrInstance.getPosTags();
        for (int i = 0; i<words.size(); i++) {
            // this also adds the artificial root, which is already contained in words
            AmConllEntry amConllEntry = new AmConllEntry(i+1, words.get(i));
            amConllEntry.setAligned(true);
            amConllEntry.setHead(0);
            amConllEntry.setLemma(lemmas.get(i));
            amConllEntry.setPos(posTags.get(i));
            amConllEntry.setEdgeLabel(AmConllEntry.IGNORE);
            if (words.get(i).equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
                amConllEntry.setEdgeLabel(AmConllEntry.ROOT_SYM);
                amConllEntry.setLexLabel(AmConllEntry.LEMMA_PLACEHOLDER);
            }
            sent.add(amConllEntry);
        }

        //add NE tags
        if (!this.noNamedEntityTags) {
            Sentence stanfAn = new Sentence(words);
            List<String> neTags = new ArrayList<>(stanfAn.nerTags());
            neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
            sent.addNEs(neTags);
        }

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
            id = mrInstance.getSentence().size();// since the sentence contains the artificial root in its last position
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
