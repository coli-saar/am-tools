/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.sdp;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDConcreteSignatureBuilder;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.saar.coli.amrtagging.mrp.sdp.SDPs;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.saar.coli.amrtagging.TokenRange;
import de.saar.coli.amrtagging.mrp.graphs.MRPAnchor;
import de.saar.coli.amrtagging.mrp.graphs.MRPEdge;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.tree.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author matthias
 */
public class PSD extends SDPs {
    
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
        
        ConjHandler.handleConj(sg, new PSDBlobUtils());
        
        return new MRInstance(sentence.words(),sg, als);
    }
    
    /**
     * Evaluates an AM dependency tree, encoded as a ConllSentence. 
     * The graph should not be postprocessed already.
     * @param s
     * @return 
     */
    @Override
    public MRPGraph evaluate(ConllSentence s) throws IllegalArgumentException {
         try {
            AMDependencyTree amdep = AMDependencyTree.fromSentence(s);
            SGraph evaluatedGraph = amdep.evaluate(true);
            evaluatedGraph = ConjHandler.restoreConj(evaluatedGraph, new PSDBlobUtils());
            MRPGraph output = sGraphToMRP(evaluatedGraph, s);
            output.setFramework("psd");
            return output;
         } catch (ParseException | ParserException | AMDependencyTree.ConllParserException e){
             throw new IllegalArgumentException(e);
         }
    }
    
    /**
     * Returns the signature builder for this instance with formalism
     * specific behaviour and blob utils.
     * @param instance
     * @return 
     */
    @Override
    public AMSignatureBuilder getSignatureBuilder (MRInstance instance){
        return new PSDConcreteSignatureBuilder(instance.getGraph(), instance.getAlignments(), new PSDBlobUtils());
    }
    
}
