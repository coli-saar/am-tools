package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.simple.Sentence;

import java.util.*;

public class UCCADecompositionPackage extends DecompositionPackage{
    //private final static UCCABlobUtils blobUtils = new UCCABlobUtils();
    //private final AMRBlobUtils blobUtils;


    private final SGraph sgraph;
    private final MRInstance inst;
    private final  List<CoreLabel> tokens;
    private final List<String> mappedPosTags;
    private final List<String> mappedLemmas;
    //private final List<String> mappedNeTags;
    public static ArrayList<String> wordIds;
    private final AMRBlobUtils blobUtils;


    public UCCADecompositionPackage(Object[] UCCADecompositionPackageBundle, AMRBlobUtils blobUtils) {

        this.blobUtils = blobUtils;
        this.sgraph = (SGraph) UCCADecompositionPackageBundle[0];
        this.inst = (MRInstance) UCCADecompositionPackageBundle[1];
        this.tokens = (List<CoreLabel>) UCCADecompositionPackageBundle[2];
        this.mappedPosTags = (List<String>) UCCADecompositionPackageBundle[3];
        this.mappedLemmas = (List<String>) UCCADecompositionPackageBundle[4];
        //this.mappedNeTags = mappedNeTags;


    }



    @Override
    public AmConllSentence makeBaseAmConllSentence() {
        AmConllSentence sent = new AmConllSentence();
        //for the word ids, we want all ids that correspond to lexical nodes
        //ArrayList<String> wordIds = new ArrayList<>();

        List<Alignment> alignments = inst.getAlignments();

        for (Alignment al: alignments){
            for (String lexNode: al.lexNodes){
                AmConllEntry amConllEntry = new AmConllEntry(Integer.parseInt(lexNode), sgraph.getSourceLabel(lexNode));
                amConllEntry.setAligned(true);
                amConllEntry.setHead(0);
                amConllEntry.setEdgeLabel(AmConllEntry.IGNORE);
                sent.add(amConllEntry);
                wordIds.add(lexNode);
            }

        }

        sent.addLemmas(mappedLemmas);
        sent.addPos(mappedPosTags);
        //sent.addNEs(mappedNeTags);




        //artificial root. Is it necessary? UCCA graphs are already rooted.
        AmConllEntry artRoot = new AmConllEntry(sgraph.nodeCount(), SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setEdgeLabel(AmConllEntry.ROOT_SYM);
        artRoot.setHead(0);
        artRoot.setAligned(true);
        artRoot.setLemma(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setPos(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setLexLabel(AmConllEntry.LEMMA_PLACEHOLDER);
        sent.add(artRoot);

        Sentence stanfAn = new Sentence(inst.getSentence());
        List<String> neTags = new ArrayList<>(stanfAn.nerTags());
        neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        sent.addNEs(neTags);

        return sent;
    }

    @Override
    public GraphNode getLexNodeFromGraphFragment(SGraph graphFragment) {
        ArrayList<String> fragmentNodes = (ArrayList<String>) graphFragment.getAllNodeNames();
        boolean hasLexNodes = fragmentNodes.retainAll(wordIds);
        assert(fragmentNodes.size() == 1);
        return sgraph.getNode(fragmentNodes.get(0));

    }

    @Override
    public int getSentencePositionForGraphFragment(SGraph graphFragment) {
        System.out.println(tokens);

        int lexNode = Integer.parseInt(getLexNodeFromGraphFragment(sgraph).toString());
        return lexNode + 1;

        //because 1-based, using the node labels should be okay given that any contraction
        // must have occurred non-terminal edges
    }


    @Override
    public AMRBlobUtils getBlobUtils() {
        return blobUtils;
    }
}
