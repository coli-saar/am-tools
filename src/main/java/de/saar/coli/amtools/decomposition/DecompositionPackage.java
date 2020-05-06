package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.SGraph;

public abstract class DecompositionPackage {

    public abstract AmConllSentence makeBaseAmConllSentence();

    public abstract String getLexLabelFromGraphFragment(SGraph graphFragment);

    public abstract int getSentencePositionForGraphFragment(SGraph graphFragment, AmConllSentence amConllSentence);

    public abstract String getTempSourceForNodeName(String nodeName);

    public abstract AMRBlobUtils getBlobUtils();

}
