package de.saar.coli.amtools.decomposition.formalisms.decomposition_packages;

import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractorCLI.LITERAL_JOINER;

public class AMRDecompositionPackage extends DecompositionPackage {


    private final AMRBlobUtils blobUtils;
    private final MRInstance instance;

    private final List<String> literals;
    private final boolean useLexLabelReplacement;
    private final boolean useStanfordTagger;


    /**
     *
     * @param instance
     * @param blobUtils
     * @param useLexLabelReplacement
     */
    public AMRDecompositionPackage(MRInstance instance, AMRBlobUtils blobUtils, boolean useLexLabelReplacement,
                                   boolean useStanfordTagger) {
        // TODO use "_" instead of "NULL" if no lex label
        this.blobUtils = blobUtils;
        this.instance = instance;

        Set<String> lexNodes = new HashSet<>();
        for (Alignment al : instance.getAlignments()) {
            lexNodes.addAll(al.lexNodes);
        }


        literals = new ArrayList<>();

        List<String> spanmap = (List<String>)instance.getExtra("spanmap");
        List<String> origSent = (List<String>)instance.getExtra("origSent");
        for (String spanString : spanmap) {
            Alignment.Span span = new Alignment.Span(spanString);
            List<String> origWords = new ArrayList<>();
            for (int l = span.start; l<span.end; l++) {
                origWords.add(origSent.get(l));
            }
            literals.add(String.join(LITERAL_JOINER, origWords));

        }

        this.useLexLabelReplacement = useLexLabelReplacement;
        this.useStanfordTagger = useStanfordTagger;
    }

    @Override
    public AmConllSentence makeBaseAmConllSentence() {

        // code copied and adapted from de.saar.coli.amrtagging.formalisms.amr.toolsToAMConll

        AmConllSentence amSent = new AmConllSentence();
        amSent.setAttr("git", AMToolsVersion.GIT_SHA);
        amSent.setId((String)instance.getExtra("id"));
        amSent.setAttr("framework", "amr");
        amSent.setAttr("flavor", "2");

        List<Integer> origPositions = new ArrayList<>();
        List<String> expandedWords = new ArrayList<>();

        for (int positionInSentence = 0; positionInSentence < instance.getSentence().size(); positionInSentence++) {
            String wordForm = literals.get(positionInSentence).replace(LITERAL_JOINER, "_");
            AmConllEntry e = new AmConllEntry(positionInSentence + 1, wordForm);
            e.setLexLabel("NULL"); // just a baseline initialization; content labels come below
            amSent.add(e);

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

        // At this point, expandedWords is a list of tokens. Potentially, |expandedWords| > |sentences(i)|,
        // because tokens may have been split at underscores. Thus origPositions maps each position in
        // expandedWords to the position in the original sentence from which it came.


        if (useStanfordTagger) {
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



        amSent.addReplacement(instance.getSentence(),false);

        for (Alignment al : instance.getAlignments()) {
            if (!al.lexNodes.isEmpty()) {
                String lexLabel = instance.getGraph().getNode(al.lexNodes.iterator().next()).getLabel();
                if (useLexLabelReplacement) {
                    amSent.get(al.span.start).setLexLabel(lexLabel);  // both amSent.get and span.start are 0-based
                } else {
                    amSent.get(al.span.start).setLexLabelWithoutReplacing(lexLabel);
                }
            }
        }

        return amSent;
    }

    @Override
    public GraphNode getLexNodeFromGraphFragment(SGraph graphFragment) {
        String rootNodeName = graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        for (Alignment al : instance.getAlignments()) {
            // check if the root node is part of the alignment; if it is, this is the alignment that determines the lex node. (note that this won't work in the AM+ algebra)
            if (al.nodes.contains(rootNodeName)) {
                if (al.lexNodes.isEmpty()) {
                    //TODO maybe just return null, and update rest of code to allow graph fragments with no lex node (and document parent string).
                    throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" corresponds to alignment with no lex node");
                }
                return instance.getGraph().getNode(al.lexNodes.iterator().next());
            }
        }
        //TODO maybe just return null, and update rest of code to allow graph fragments with no lex node (and document parent string).
        throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" did not correspond to an alignment, no lex node could be determined");
    }

    @Override
    public int getSentencePositionForGraphFragment(SGraph graphFragment) {
        String rootNodeName = graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        for (Alignment al : instance.getAlignments()) {
            // check if the root node is part of the alignment; if it is, this is the alignment that determines the sentence position. (note that this won't work in the AM+ algebra)
            if (al.nodes.contains(rootNodeName)) {
                return al.span.start+1;// start is 0 based, but need 1-based here
            }
        }
        throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" did not correspond to an alignment, no sentence position could be determined");
    }


    @Override
    public AMRBlobUtils getBlobUtils() {
        return blobUtils;
    }


    @Override
    public Set<Set<String>> getMultinodeConstantNodeNames() {
        return instance.getAlignments().stream().map(al -> al.nodes).filter(set -> set.size()>1).collect(Collectors.toSet());
    }
}
