package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.cogs.COGSBlobUtils;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;

import java.util.List;

/**
 * Contains information on how to decompose a specific COGS SGraph, and how to create an AmConllSentence for it.<br>
 *
 * After the COGS logical forms have been converted into a <code>SGraph</code>
 * (plus alignments: all bundled in a <code>MRInstance</code>)
 * using the <code>LogicalFormConverter</code>, we can use this package to decompose the graph together with the
 * <code>COGSBlobUtils</code> (a subclass of <code>AMRBlobUtils</code>).<br>
 * References: The COGS Paper: <a href="https://www.aclweb.org/anthology/2020.emnlp-main.731/">Kim and Linzen, 2020</a>
 *
 * @author pia (weissenh)
 */
public class COGSDecompositionPackage extends DecompositionPackage {

    private final AMRBlobUtils blobUtils;  ///< actually COGSBlobUtils when initialized (AMRBlobUtils is the superclass)
    private final MRInstance mrInstance;  ///< contains tokens of the sentence, an sGraph and a list of alignments
    private final boolean useLexLabelReplacement = false;  ///< if true, lex label can be $LEMMA$ or $WORD$
    // for now we don't use this replacement for simplicity reasons

    public COGSDecompositionPackage(MRInstance mrInstance, AMRBlobUtils blobUtils) {
        this.blobUtils = blobUtils;
        this.mrInstance = mrInstance;
        assert(mrInstance.getSentence().size()>0);
    }

    // todo do I have to add an artificial root?
    @Override
    public AmConllSentence makeBaseAmConllSentence() {
        List<String> sentence = mrInstance.getSentence();
        int sentsize = sentence.size();
        SGraph graph = mrInstance.getGraph();
        List<Alignment> alignments = mrInstance.getAlignments();

        AmConllSentence sent = new AmConllSentence();
        //  * add meta data
        sent.setAttr("git", AMToolsVersion.GIT_SHA);
        // sent.setId(id);
        // sent.setAttr("framework", "cogs");
        // todo maybe logical form as comments?
        // sent.setAttr("gen_type_required", "in_distribution");  // todo: magic strings: put them in constants mb?

        // for all words in the sentence: add amconll entry default for now (aligned, ignored)
        // todo add lemma based on alignment maybe?
        for (int i = 0; i < sentsize; i++) {
            int am_id = i + 1;  // 1-based index in amconll entries! https://github.com/coli-saar/am-parser/wiki/AM-CoNLL-file-format
            String word = sentence.get(i);
            AmConllEntry amConllEntry = new AmConllEntry(am_id, word);

            // - set attributes
            // -- aligned but ignored in am dep tree for now
            amConllEntry.setAligned(true);
            amConllEntry.setHead(0);
            amConllEntry.setEdgeLabel(AmConllEntry.IGNORE);
            // -- lemma, pos, ne  default null for now
            amConllEntry.setLemma(AmConllEntry.DEFAULT_NULL);  // todo add lemma based on alignment maybe?
            amConllEntry.setPos(AmConllEntry.DEFAULT_NULL);
            amConllEntry.setNe(AmConllEntry.DEFAULT_NULL);  // todo _ or O ? could I be more informative? proper name?
            // add them to the AMConLLSentence
            sent.add(amConllEntry);
        }

        // we need to add the lexical labels (column 8 in the AMCoNLL format) for the supertags
        // code below based on AMRDecompositionPackage
        for (Alignment al : alignments) {
            if (!al.lexNodes.isEmpty()) {
                String lexLabel = graph.getNode(al.lexNodes.iterator().next()).getLabel();
                if (useLexLabelReplacement) {
                    sent.get(al.span.start).setLexLabel(lexLabel);  // both amSent.get and span.start are 0-based
                } else {
                    sent.get(al.span.start).setLexLabelWithoutReplacing(lexLabel);
                }
            }
        }
        // add artificial root
        /*
        AmConllEntry artRoot = new AmConllEntry(..get the special root node in graph.., SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setEdgeLabel(AmConllEntry.ROOT_SYM);
        artRoot.setHead(0);
        artRoot.setAligned(true);
        artRoot.setLemma(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setPos(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setLexLabel(AmConllEntry.LEMMA_PLACEHOLDER);
        sent.add(artRoot);
        */

        return sent;
    }

    /**
     * For a sub-s-graph, return the corresponding lexical node, that is, the node which should be delexicalized in the
     * supertag, and whose label should fill the "lexical label" column in the amconll file.
     *
     * @param graphFragment from which we would like to get the lexical node
     * @return a node from graphFragment that should be delexicalized
     */
    @Override
    public GraphNode getLexNodeFromGraphFragment(SGraph graphFragment) {
        // todo should we do the oneliner sdp or do I have to do the long amr way with the alignments?
        // SDP does:
        // return graphFragment.getNode(graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME));
        // AMR does:
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

    /**
     * For a sub-s-graph of the full graph, return the position (1-based!) of the word that the graph
     * fragment is aligned to. Note that this must work for atomic sub-s-graphs consisting of only one node
     * and its blob edges, but also for larger graphs if they occur during decomposition.
     *
     * @param graphFragment for which we would like to know the aligned word
     * @return position (1-based!) of the word that graphFragment is aligned to
     */
    @Override
    public int getSentencePositionForGraphFragment(SGraph graphFragment) {
        // AMR copied:
        String rootNodeName = graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        for (Alignment al : mrInstance.getAlignments()) {
            // check if the root node is part of the alignment; if it is, this is the alignment that determines the sentence position. (note that this won't work in the AM+ algebra)
            if (al.nodes.contains(rootNodeName)) {
                return al.span.start+1;// start is 0 based, but need 1-based here
            }
        }
        throw new IllegalArgumentException("Graph fragment "+graphFragment.toIsiAmrStringWithSources()+" did not correspond to an alignment, no sentence position could be determined");
    }

    /**
     * Returns an AMRBlobUtils (don't be fooled by the name, that's the parent class that other classes like
     * COGSlobUtils overwrite) for the given graph.
     * Can probably simply return the blob utils for the respective formalism.
     *
     * @return AMRBlobUtils (this is a superclass of COGSBlobUtils)
     */
    @Override
    public AMRBlobUtils getBlobUtils() {
        return blobUtils;
    }
}
