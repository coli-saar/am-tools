/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.sdp;

import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author matthias
 */
public class DM extends SDPs {


    @Override
    public MRInstance toMRInstance(ConlluSentence sentence, MRPGraph mrpgraph) {
        MRPUtils.addArtificalRoot(sentence, mrpgraph);
        SGraph sg = MRPUtils.toSGraphWoAnchoring(mrpgraph);
        List<Alignment> als = new ArrayList<>();
        
        for (MRPNode n : mrpgraph.getNodes()){
            Set<String> nodes = new HashSet<>();
            nodes.add(MRPUtils.mrpIdToSGraphId(n.getId()));
            int start = sentence.getExactIndex(TokenRange.fromAnchor(n.getAnchors().get(0)));
            Alignment al = new Alignment(nodes, new Alignment.Span(start, start+1),nodes,0);
            als.add(al);
        }
        return new MRInstance(sentence.words(), sg, als);
    }

    @Override
    public MRPGraph evaluate(AmConllSentence s) {
        try {
            AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
            SGraph evaluatedGraph = amdep.evaluate(true);
            MRPGraph output = sGraphToMRP(evaluatedGraph, s);
            output.setFramework("dm");
            return output;
         } catch (ParseException | ParserException | AlignedAMDependencyTree.ConllParserException e){
             throw new IllegalArgumentException(e);
         }
    }


    public AMSignatureBuilder getSignatureBuilder(MRInstance instance) {
        AMRSignatureBuilder sigBuilder = new AMRSignatureBuilder();
        sigBuilder.blobUtils = new DMBlobUtils();
        return sigBuilder;
    }

    @Override
    public AlignmentTrackingAutomaton getAlignmentTrackingAutomaton(MRInstance instance) throws ParseException {
        return AlignmentTrackingAutomaton.create(instance,getSignatureBuilder(instance), false);
    }
    
}
