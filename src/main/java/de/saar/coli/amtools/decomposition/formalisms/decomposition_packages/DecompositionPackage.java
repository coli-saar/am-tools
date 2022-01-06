package de.saar.coli.amtools.decomposition.formalisms.decomposition_packages;

import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;

import java.util.Collections;
import java.util.Set;

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
     * For a sub-s-graph, return the corresponding lexical node, that is, the node which should be delaxicalized in the
     * supertag, and whose label should fill the "lexical label" column in the amconll file.
     * @param graphFragment
     * @return
     */
    public abstract GraphNode getLexNodeFromGraphFragment(SGraph graphFragment);

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
        return "S"+nodeName.replaceAll("_", "");
    }

    /**
     * If there are multinode constants in the decomposition, this returns a set S such that for each multinode constant
     * C, S contains the set of all node names in C. The default implementation assumes that there are no multinode constants
     * and returns the empty set.
     * @return
     */
    public Set<Set<String>> getMultinodeConstantNodeNames() {return Collections.emptySet(); }

    /**
     * Returns an AMRBlobUtils (don't be fooled by the name, that's the parent class that other classes like DMBlobUtils overwrite)
     * for the given graph. Can probably simply return the blob utils for the respective formalism.
     * @return
     */
    public abstract AMRBlobUtils getBlobUtils();

}
