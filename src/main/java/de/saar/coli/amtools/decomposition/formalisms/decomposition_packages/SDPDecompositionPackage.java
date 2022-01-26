package de.saar.coli.amtools.decomposition.formalisms.decomposition_packages;

import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SDPDecompositionPackage extends DecompositionPackage {



    public SDPDecompositionPackage(MRInstance mrInstance, EdgeAttachmentHeuristic edgeAttachmentHeuristic, boolean fasterModeForTesting) {
        super(mrInstance, edgeAttachmentHeuristic, fasterModeForTesting);
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
        if (!this.fasterModeForTesting) {
            List<String> neTags = makeStanfordNETags(words);
            sent.addNEs(neTags);
        }

        return sent;
    }

    @NotNull
    private List<String> makeStanfordNETags(List<String> words) {
        Sentence stanfAn = new Sentence(words.subList(0, words.size()-1));
        List<String> neTags = new ArrayList<>(stanfAn.nerTags());
        neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        return neTags;
    }


    @Override
    public EdgeAttachmentHeuristic getEdgeAttachmentHeuristic() {
        return edgeAttachmentHeuristic;
    }
}
