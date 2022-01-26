package de.saar.coli.amtools.decomposition.formalisms.decomposition_packages;

import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.NamedEntityRecognizer;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessingException;
import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.corpus.Instance;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.simple.Sentence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractorCLI.LITERAL_JOINER;

public class AMRDecompositionPackageLegacy extends DecompositionPackage {


    private final SGraph graph;
    private final List<String> sent;
    private final List<String> origSent;
    private final List<String> spanmap;
    private final List<Alignment> alignments;
    private final List<String> posTags;
    private final List<String> literals;
    private final NamedEntityRecognizer neRecognizer;
    private final String id;
    private final boolean useLexLabelReplacement;


    /**
     *
     * @param instance
     * @param edgeAttachmentHeuristic
     * @param preprocessedData if null, no POS tags will be added
     * @param neRecognizer if null, no NE tags will be added
     * @param useLexLabelReplacement
     */
    public AMRDecompositionPackageLegacy(Instance instance, EdgeAttachmentHeuristic edgeAttachmentHeuristic, PreprocessedData preprocessedData, NamedEntityRecognizer neRecognizer,
                                         boolean useLexLabelReplacement) {
        // TODO use "_" instead of "NULL" if no lex label
        super(null, edgeAttachmentHeuristic, false);
        graph = (SGraph)instance.getInputObjects().get("repgraph");
        sent = (List)instance.getInputObjects().get("repstring");
        origSent = (List)instance.getInputObjects().get("string");
        spanmap = (List)instance.getInputObjects().get("spanmap");
        id = ((List<String>)instance.getInputObjects().get("id")).get(0);

//            if (!alBr.ready()) {
//                break;
//            }
        Set<String> lexNodes = new HashSet<>();
        //List<String> als;
//            if (cli.joint) {
//                als =(List)inst.getInputObjects().get("repalignmentp");
//            } else {
        List<String> alStrings;
        alStrings =(List)instance.getInputObjects().get("repalignment");
//            }
        if (alStrings.size() == 1 && alStrings.get(0).equals("")) {
            //System.err.println("Repaired empty alignment!");
            alStrings = new ArrayList<>();
        }

        String[] alStringArray = alStrings.toArray(new String[0]);
        alignments = new ArrayList<>();
        for (String alString : alStringArray) {
            Alignment al = Alignment.read(alString, 0);
            lexNodes.addAll(al.lexNodes);
            alignments.add(al); // add to alignments
        }

        List<TaggedWord> origPosTags = (preprocessedData != null) ? preprocessedData.getPosTags(id) : null;

        posTags = new ArrayList<>();
        literals = new ArrayList<>();

        for (String spanString : spanmap) {
            Alignment.Span span = new Alignment.Span(spanString);
            List<String> origWords = new ArrayList<>();
            for (int l = span.start; l<span.end; l++) {
                origWords.add(origSent.get(l));
            }
            literals.add(origWords.stream().collect(Collectors.joining(LITERAL_JOINER)));
            if (origPosTags != null) {
                posTags.add(origPosTags.get(span.start).tag());
            } else {
                posTags.add(AmConllEntry.DEFAULT_NULL);
            }
        }

        this.neRecognizer = neRecognizer;
        this.useLexLabelReplacement = useLexLabelReplacement;
    }

    @Override
    public AmConllSentence makeBaseAmConllSentence() {

        // code copied and adapted from de.saar.coli.amrtagging.formalisms.amr.toolsToAMConll

        AmConllSentence amSent = new AmConllSentence();
        amSent.setAttr("git", AMToolsVersion.GIT_SHA);
        amSent.setId(id);
        amSent.setAttr("framework", "amr");
        amSent.setAttr("flavor", "2");

        List<Integer> origPositions = new ArrayList<>();
        List<String> ners = new ArrayList<>();
        List<String> expandedWords = new ArrayList<>();

        for (int positionInSentence = 0; positionInSentence < sent.size(); positionInSentence++) {
            String wordForm = literals.get(positionInSentence).replace(LITERAL_JOINER, "_");
            AmConllEntry e = new AmConllEntry(positionInSentence + 1, wordForm);
            e.setLexLabel("NULL"); // just a baseline initialization; content labels come below
            amSent.add(e);
            ners.add("O");  // initialize as no NE

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


        List<CoreLabel> nerTags = null;
        List<String> ourLemmas = new ArrayList<>(amSent.words());
        List<String> lemmas;


        Sentence stanfSent = new Sentence(expandedWords);
        lemmas = stanfSent.lemmas();
        if (neRecognizer != null) {
            try {
                nerTags = neRecognizer.tag(Util.makeCoreLabelsForTokens(expandedWords));
            } catch (PreprocessingException e) {
                throw new RuntimeException(e); // don't expect this to happen really, and don't see the need to bother with error handling. If it happens, this will do -- JG
            }
        }

        for (int j = 0; j < lemmas.size(); j++) {
            if (nerTags != null) {
                ners.set(origPositions.get(j), nerTags.get(j).ner());
            }
            ourLemmas.set(origPositions.get(j), lemmas.get(j));
        }


        amSent.addReplacementTokens(sent,false);
        amSent.addPos(posTags);
        amSent.addLemmas(ourLemmas);
        amSent.addNEs(ners);

        for (Alignment al : alignments) {
            if (!al.lexNodes.isEmpty()) {
                String lexLabel = graph.getNode(al.lexNodes.iterator().next()).getLabel();
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
        for (Alignment al : alignments) {
            // check if the root node is part of the alignment; if it is, this is the alignment that determines the lex node. (note that this won't work in the AM+ algebra)
            if (al.nodes.contains(rootNodeName)) {
                if (al.lexNodes.isEmpty()) {
                    //TODO maybe just return null, and update rest of code to allow graph fragments with no lex node (and document parent string).
                    throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" corresponds to alignment with no lex node");
                }
                return graph.getNode(al.lexNodes.iterator().next());
            }
        }
        //TODO maybe just return null, and update rest of code to allow graph fragments with no lex node (and document parent string).
        throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" did not correspond to an alignment, no lex node could be determined");
    }

    @Override
    public int getSentencePositionForGraphFragment(SGraph graphFragment) {
        String rootNodeName = graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        for (Alignment al : alignments) {
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


    @Override
    public Set<Set<String>> getMultinodeConstantNodeNames() {
        return alignments.stream().map(al -> al.nodes).filter(set -> set.size()>1).collect(Collectors.toSet());
    }
}
