package de.saar.coli.amtools.decomposition.formalisms.decomposition_packages;

import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractorCLI.LITERAL_JOINER;

public class AMRDecompositionPackage extends DecompositionPackage {



    private final List<String> literals;


    /**
     *
     * @param mrInstance
     * @param edgeAttachmentHeuristic
     * @param fasterModeForTesting
     */
    public AMRDecompositionPackage(MRInstance mrInstance, EdgeAttachmentHeuristic edgeAttachmentHeuristic, boolean fasterModeForTesting) {
        super(mrInstance, edgeAttachmentHeuristic, fasterModeForTesting);

        this.framework = "amr";

        this.literals = new ArrayList<>();

        List<String> spanmap = (List<String>)mrInstance.getExtra("spanmap");
        List<String> origSent = (List<String>)mrInstance.getExtra("origSent");
        for (String spanString : spanmap) {
            Alignment.Span span = new Alignment.Span(spanString);
            List<String> origWords = new ArrayList<>();
            for (int l = span.start; l<span.end; l++) {
                origWords.add(origSent.get(l));
            }
            this.literals.add(String.join(LITERAL_JOINER, origWords));

        }
    }

    @Override
    public AmConllSentence makeBaseAmConllSentence() {

        AmConllSentence amSent = makeStringOnlyAmConllSentence();

        registerReplacementLabels(amSent);

        setLexicalLabels(amSent);

        return amSent;
    }

    @Override
    public AmConllSentence makeStringOnlyAmConllSentence() {
        // code copied and adapted from de.saar.coli.amrtagging.formalisms.amr.toolsToAMConll

        AmConllSentence amSent = makeAmConllSentenceWithGeneralInformation();

        List<Integer> origPositions = new ArrayList<>();
        List<String> expandedWords = new ArrayList<>();

        for (int positionInSentence = 0; positionInSentence < mrInstance.getSentence().size(); positionInSentence++) {
            addBasicAMConllEntryToSentence(amSent, positionInSentence);

            // This is specific to our AMR preprocessing, which condenses multiple tokens into one (like a multi-token named entity).
            // Here we reconstruct the original token sequence
            registerOriginalPositionAndExpandedWords(origPositions, expandedWords, positionInSentence);

        }

        // At this point, expandedWords is a list of tokens. Potentially, |expandedWords| > |sentences(i)|,
        // because tokens may have been split at underscores. Thus origPositions maps each position in
        // expandedWords to the position in the original sentence from which it came.


        if (!fasterModeForTesting) {
            addPosNeLemmaTagsUsingStanfordNLP(amSent, origPositions, expandedWords);
        }

        return amSent;
    }

    private void addPosNeLemmaTagsUsingStanfordNLP(AmConllSentence amSent, List<Integer> origPositions, List<String> expandedWords) {
        Sentence stanfSent = new Sentence(expandedWords);
        List<String> lemmas = stanfSent.lemmas();
        List<String> posTags = stanfSent.posTags();
        List<String> neTags = stanfSent.nerTags();

        List<String> ourLemmas = new ArrayList<>(amSent.words());
        List<String> ourPosTags = new ArrayList<>(amSent.words());
        List<String> ourNeTags = new ArrayList<>(amSent.words());


        for (int j = 0; j < lemmas.size(); j++) {
            ourLemmas.set(origPositions.get(j), lemmas.get(j));
            ourPosTags.set(origPositions.get(j), posTags.get(j));
            ourNeTags.set(origPositions.get(j), neTags.get(j));
        }

        amSent.addLemmas(ourLemmas);
        amSent.addPos(ourPosTags);
        amSent.addNEs(ourNeTags);
    }


    // TODO this may only be here for legacy reasons -- maybe remove? --JG
    private void registerReplacementLabels(AmConllSentence amSent) {
        amSent.addReplacementTokens(mrInstance.getSentence(),false);
    }

    private void registerOriginalPositionAndExpandedWords(List<Integer> origPositions, List<String> expandedWords, int positionInSentence) {
        String[] splits = literals.get(positionInSentence).split(LITERAL_JOINER);

        if (splits.length == 0){
            // we have to add a token otherwise things break.
            expandedWords.add(literals.get(positionInSentence));
            origPositions.add(positionInSentence);
        } else {
            for (String w : splits) {
                if (w.length() > 0) {
                    expandedWords.add(w);
                    origPositions.add(positionInSentence);
                }
            }
        }
    }

    @Override
    protected void addBasicAMConllEntryToSentence(AmConllSentence amSent, int positionInSentence) {
        String wordForm = literals.get(positionInSentence).replace(LITERAL_JOINER, "_");
        AmConllEntry e = new AmConllEntry(positionInSentence + 1, wordForm);
        e.setLexLabel("_"); // just a baseline initialization; content labels come below
        e.setAligned(true); // this just means that the whole sentence is aligned. I.e. that the node (or lack of node) in this entry actually corresponds to the word position
        amSent.add(e);
    }


    @Override
    public GraphNode getLexNodeFromGraphFragment(SGraph graphFragment) {
        String rootNodeName = graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        for (Alignment al : mrInstance.getAlignments()) {
            // check if the root node is part of the alignment; if it is, this is the alignment that determines the lex node. (note that this won't work in the AM+ algebra)
            if (al.nodes.contains(rootNodeName)) {
                if (al.lexNodes.isEmpty()) {
                    //TODO maybe just return null, and update rest of code to allow graph fragments with no lex node (and document parent string).
                    throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" corresponds to alignment with no lex node");
                }
                return mrInstance.getGraph().getNode(al.lexNodes.iterator().next());
            }
        }
        //TODO maybe just return null, and update rest of code to allow graph fragments with no lex node (and document parent string).
        throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" did not correspond to an alignment, no lex node could be determined");
    }

    @Override
    public int getSentencePositionForGraphFragment(SGraph graphFragment) {
        String rootNodeName = graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        for (Alignment al : mrInstance.getAlignments()) {
            // check if the root node is part of the alignment; if it is, this is the alignment that determines the sentence position. (note that this won't work in the AM+ algebra)
            if (al.nodes.contains(rootNodeName)) {
                return al.span.start+1;// start is 0 based, but need 1-based here
            }
        }
        throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" did not correspond to an alignment, no sentence position could be determined");
    }


    @Override
    public EdgeAttachmentHeuristic getEdgeAttachmentHeuristic() {
        return edgeAttachmentHeuristic;
    }

}
