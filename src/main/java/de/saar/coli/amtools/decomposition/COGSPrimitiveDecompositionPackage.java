package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;


/**
 * Decomposition Package for COGS Primitives<br>
 *
 * based on <code>COGSDecompositionPackage</code>
 * We need this extra packages for the primitives: can have open sources, ...
 * TODO can't we use the normal package directly?
 *
 * @author pia (weissenh)
 */
public class COGSPrimitiveDecompositionPackage extends COGSDecompositionPackage {

    public COGSPrimitiveDecompositionPackage(MRInstance mrInstance, AMRBlobUtils blobUtils, boolean useLexLabelReplacement) {
        super(mrInstance, blobUtils, useLexLabelReplacement);
        assert(mrInstance.getSentence().size()==1); // primitives: one word only
    }

//    @Override  // do same as in cogsdecom? no lex node for some?
//    public GraphNode getLexNodeFromGraphFragment(SGraph graphFragment) {
//        // assume the lexical node is always the root node of the input graphFragment (same as what SDP does)
//        // todo sanity checks?
//        return graphFragment.getNode(graphFragment.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME));
//    }

//    @Override  // do same as in cogsdecompositionpackage? (no alignment for non-root nodes: the ones with sources)
//    public int getSentencePositionForGraphFragment(SGraph graphFragment) {
//        // todo sanity checks?
//        // todo should the 'open' nodes with no label and just source also be aligned?
//        return 1;  // everything is aligned to the only input token (1-indexed)
//    }

}

