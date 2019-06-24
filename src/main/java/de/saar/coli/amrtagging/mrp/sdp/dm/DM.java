/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.sdp.dm;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.utils.ConlluSentence;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.sdp.SDPs;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.List;

/**
 *
 * @author matthias
 */
public class DM extends SDPs {

    @Override
    public ConlluSentence refineTokenization(ConlluSentence sentence) {
        return sentence;
    }

    @Override
    public MRInstance toMRInstance(ConlluSentence sentence, MRPGraph mrpgraph) {
        List<String> sent = sentence.words();
        throw new UnsupportedOperationException("Not supported yet.");
        //MRInstance instance = new MRInstanceList(sent, SGraph sg, List<Alignment> alignments);
        //return instance;
    }

    @Override
    public MRPGraph evaluate(ConllSentence amconll) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MRPGraph postprocess(MRPGraph mrpgraph) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public AMSignatureBuilder getSignatureBuilder(MRInstance instance) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
