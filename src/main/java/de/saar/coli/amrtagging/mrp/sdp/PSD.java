/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.sdp;

import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConcreteAlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDConcreteSignatureBuilder;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.saar.coli.amrtagging.TokenRange;
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
public class PSD extends SDPs {
    
    /**
     * TODO: PSD lemmatization refinements:
     * fractions (7/8)
     * comparatives (strong, small)
     * compounds with hyphens: inflation-adjusted --> inflation_adjusted (but see 21556026)
     */
    
    /**
     * Takes the sentence with refined tokenization and the preprocessed MRP graph and returns
     * an s-graph, the tokens and the alignment bundled into an MRInstance
     * @param sentence
     * @param mrpgraph
     * @return 
     */
    @Override
    public MRInstance toMRInstance(ConlluSentence sentence, MRPGraph mrpgraph){
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
        
        sg = ConjHandler.handleConj(sg, new PSDBlobUtils());
        
        return new MRInstance(sentence.words(),sg, als);
    }
    
    /**
     * Evaluates an AM dependency tree, encoded as a AmConllSentence.
     * The graph should not be postprocessed already.
     * @param s
     * @return 
     */
    @Override
    public MRPGraph evaluate(AmConllSentence s) throws IllegalArgumentException {
         try {
            AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
            SGraph evaluatedGraph = amdep.evaluate(true);
            evaluatedGraph = ConjHandler.restoreConj(evaluatedGraph, new PSDBlobUtils());
            MRPGraph output = sGraphToMRP(evaluatedGraph, s);
            output.setFramework("psd");
            return output;
         } catch (ParseException | ParserException | AlignedAMDependencyTree.ConllParserException e){
             throw new IllegalArgumentException(e);
         }
    }
    
    /**
     * Returns the signature builder for this instance with formalism
     * specific behaviour and blob utils.
     * @param instance
     * @return 
     */
    public AMSignatureBuilder getSignatureBuilder (MRInstance instance){
        return new PSDConcreteSignatureBuilder(instance.getGraph(), instance.getAlignments(), new PSDBlobUtils());
    }

    @Override
    public AlignmentTrackingAutomaton getAlignmentTrackingAutomaton(MRInstance instance) throws ParseException {
        return ConcreteAlignmentTrackingAutomaton.create(instance,getSignatureBuilder(instance), false);
    }
    
}
