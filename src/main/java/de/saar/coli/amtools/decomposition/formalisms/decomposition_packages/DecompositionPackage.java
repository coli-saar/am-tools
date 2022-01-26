package de.saar.coli.amtools.decomposition.formalisms.decomposition_packages;

import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;
import org.jetbrains.annotations.NotNull;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contains information on how to decompose a specific SGraph, and how to create an AmConllSentence for it.
 * @author Jonas Groschwitz
 */
public class DecompositionPackage {

    protected final AMRBlobUtils blobUtils;
    protected final boolean fasterModeForTesting;
    protected final MRInstance mrInstance;
    protected String framework = "unknown";

    public DecompositionPackage(MRInstance mrInstance, AMRBlobUtils blobUtils, boolean fasterModeForTesting) {
        this.mrInstance = mrInstance;
        this.blobUtils = blobUtils;
        this.fasterModeForTesting = fasterModeForTesting;
    }


    /**
     * Creates a base AM Conll sentence, without the AM dependency edges. Must contain
     * Lemma and POS tag for each word, and should for each word set the default
     * head to 0, edge label to IGNORE and aligned to true. Must also take care of the artificial root.
     * @return
     */
    public AmConllSentence makeBaseAmConllSentence() {
        AmConllSentence amSent = makeAmConllSentenceWithGeneralInformation();

        for (int positionInSentence = 0; positionInSentence < mrInstance.getSentence().size(); positionInSentence++) {
            addBasicAMConllEntryToSentence(amSent, positionInSentence);
        }

        if (!fasterModeForTesting) {
            addPosNeLemmaTagsUsingStanfordNLP(amSent);
        }

        // It actually matters that this comes after setting the lemmata: it not only sets the lexical labels
        // but also registers label replacements (see AmConllEntry#setLexLabel), for which the lemma information
        // is relevant.
        setLexicalLabels(amSent);

        return amSent;
    }


    protected void setLexicalLabels(AmConllSentence amSent) {
        for (Alignment al : mrInstance.getAlignments()) {
            if (!al.lexNodes.isEmpty()) {
                String lexLabel = mrInstance.getGraph().getNode(al.lexNodes.iterator().next()).getLabel();
                amSent.get(al.span.start).setLexLabel(lexLabel);  // both amSent.get and span.start are 0-based
            }
        }
    }

    protected void addPosNeLemmaTagsUsingStanfordNLP(AmConllSentence amSent) {
        Sentence stanfordSentence = new Sentence(amSent.words());

        amSent.addLemmas(stanfordSentence.lemmas());
        amSent.addPos(stanfordSentence.posTags());
        amSent.addNEs(stanfordSentence.nerTags());
    }


    protected void addBasicAMConllEntryToSentence(AmConllSentence amSent, int positionInSentence) {
        AmConllEntry e = addWordAndReturnCorrespondingAmConllEntry(positionInSentence);
        e.setLexLabel("_"); // just a baseline initialization; content labels come later
        amSent.add(e);
    }

    @NotNull
    protected AmConllEntry addWordAndReturnCorrespondingAmConllEntry(int positionInSentence) {
        String wordForm = mrInstance.getSentence().get(positionInSentence);
        // IDs in AmConllEntry are 1-based, so we add a '+ 1' here
        AmConllEntry e = new AmConllEntry(positionInSentence + 1, wordForm);
        e.setAligned(true); // this just means that the whole sentence is aligned. I.e. that the node (or lack of node) in this entry actually corresponds to the word position
        return e;
    }

    @NotNull
    protected AmConllSentence makeAmConllSentenceWithGeneralInformation() {
        AmConllSentence amSent = new AmConllSentence();
        amSent.setAttr("git", AMToolsVersion.GIT_SHA);
        amSent.setId((String)mrInstance.getExtra("id"));
        amSent.setAttr("framework", framework);
        return amSent;
    }

    /**
     * For a sub-s-graph, return the corresponding lexical node, that is, the node which should be delaxicalized in the
     * supertag, and whose label should fill the "lexical label" column in the amconll file.
     * @param graphFragment
     * @return
     */
    public GraphNode getLexNodeFromGraphFragment(SGraph graphFragment) {
        Alignment correspondingAlignment = getAlignmentForGraphFragment(graphFragment);
        if (correspondingAlignment.lexNodes.isEmpty()) {
            //TODO maybe just return null, and update rest of code to allow graph fragments with no lex node (and document parent string).
            throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" corresponds to alignment with no lex node");
        } else {
            return mrInstance.getGraph().getNode(correspondingAlignment.lexNodes.iterator().next());
        }
    }


    /**
     * For a sub-s-graph of the full graph, return the position (1-based!) of the word that the graph
     * fragment is aligned to. Note that this must work for atomic sub-s-graphs consisting of only one node
     * and its blob edges, but also for larger graphs if they occur during decomposition.
     * @param graphFragment
     * @return
     */
    public int getSentencePositionForGraphFragment(SGraph graphFragment) {
        Alignment correspondingAlignment = getAlignmentForGraphFragment(graphFragment);
        return correspondingAlignment.span.start+1;
    }

    protected Alignment getAlignmentForGraphFragment(SGraph graphFragment) {
        //TODO this assumes that the root node of the graph fragment is a labeled node of the graph fragment,
        // i.e. a node belonging to the alignment that spawned that graph fragment. This is always true in the
        // current implementation (written: January 2022), but might change in the future -- then, this function
        // needs to be adapted.
        String rootNodeName = graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        for (Alignment al : mrInstance.getAlignments()) {
            // check if the root node is part of the alignment; if it is, this is the alignment that determines the sentence position. (note that this won't work in the AM+ algebra)
            if (al.nodes.contains(rootNodeName)) {
                return al;// start is 0 based, but need 1-based here
            }
        }
        throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" did not correspond to an alignment!" +
                "This may be because one of its nodes is unaligned. Note that every node in the graph must be aligned to a word.");
    }

    /**
     * For a node name, create a graph-wide unique source name. The default implementation simply removes all underscores
     * from the nodename, since those are forbidden in source names of the AM algebra.
     * @param nodeName
     * @return
     */
    public String getTempSourceForNodeName(String nodeName)  {
        return "S"+nodeName.replaceAll("_", "");
    }

    /**
     * If there are multinode constants in the decomposition, this returns a set S such that for each multinode constant
     * C, S contains the set of all node names in C. The default implementation works off of the alignments given
     * in the MRInstance.
     * @return
     */
    public Set<Set<String>> getMultinodeConstantNodeNames() {
        return mrInstance.getAlignments().stream().map(al -> al.nodes).filter(set -> set.size()>1).collect(Collectors.toSet());
    }

    /**
     * Returns an AMRBlobUtils (don't be fooled by the name, that's the parent class that other classes like DMBlobUtils overwrite)
     * for the given graph. Can probably simply return the blob utils for the respective formalism.
     * @return
     */
    public AMRBlobUtils getBlobUtils() {
        return blobUtils;
    }

}
