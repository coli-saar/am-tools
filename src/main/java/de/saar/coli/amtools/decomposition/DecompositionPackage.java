package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.SGraph;

/**
 * Contains information on how to decompose a specific SGraph, and how to create an AmConllSentence for it.
 * @author Jonas Groschwitz
 */
public abstract class DecompositionPackage {

    /**
     * Creates a base AM Conll sentence, without the AM dependency edges. Must contain
     * Lemma and POS tag for each word, and should for each word set the default
     * head to 0, edge label to IGNORE and aligned to true. Must also take care of the artificial root.
     * @return
     */
    public abstract AmConllSentence makeBaseAmConllSentence();

    /**
     * For a sub-s-graph, return the corresponding value to be filled into the lexical label column
     * of the AmConll file. In particular, for a formalism where the node labels are not just the words of
     * the sentence, use the label of the lexical node.
     * @param graphFragment
     * @return
     */
    public abstract String getLexLabelFromGraphFragment(SGraph graphFragment);

    /**
     * For a sub-s-graph of the full graph, return the position (1-based!) of the word that the graph
     * fragment is aligned to. Note that this must work for atomic sub-s-graphs consisting of only one node
     * and its blob edges, but also for larger graphs if they occur during decomposition.
     * @param graphFragment
     * @return
     */
    public abstract int getSentencePositionForGraphFragment(SGraph graphFragment);

    /**
     * For a node name, create a graph-wide unique source name. The default implementation simply removes all underscores
     * from the nodename, since those are forbidden in source names of the AM algebra.
     * @param nodeName
     * @return
     */
    public String getTempSourceForNodeName(String nodeName)  {
        return nodeName.replaceAll("_", "");
    }

    /**
     * Returns an AMRBlobUtils (don't be fooled by the name, that's the parent class that other classes like DMBlobUtils overwrite)
     * for the given graph. Can probably simply return the blob utils for the respective formalism.
     * @return
     */
    public abstract AMRBlobUtils getBlobUtils();

}
