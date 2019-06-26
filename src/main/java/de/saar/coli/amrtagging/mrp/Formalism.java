/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp;

import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;

/**
 *
 * Specifies what needs to be implemented for a specific MRP formalism.
 * 
 * For training the procedure looks as follows:
 * refineTokenization -> preprocessMRP -> toMRInstance -> preprocess -> Decomposition of the graph
 * During evaluation, the procedure is kind of reversed:
 * evaluate -> postprocess -> postprocessMRP
 * @author matthias
 */
public interface Formalism {
    
    
    
    /**
     * Returns a graphbank specific refinement of tokenization, lemmas etc of the companion data.
     * @param sentence
     * @return 
     */
    public ConlluSentence refine(ConlluSentence sentence);
    
    
    /**
     * Performs an MRP specific preprocessing step, e.g. split named entity information 
     * which is expressed as properties into separate nodes
     * @param mrpgraph
     * @return 
     */
    public MRPGraph preprocess(MRPGraph mrpgraph);
    
    /**
     * Reverts the MRP specific preprocessing step, should be the inverse of preprocess.
     * @param mrpgraph
     * @return 
     */
    public MRPGraph postprocess(MRPGraph mrpgraph);
    
    /**
     * Takes the sentence with refined tokenization and the preprocessed MRP graph and returns
     * an s-graph, the tokens and the alignment bundled into an MRInstance
     * @param sentence
     * @param mrpgraph
     * @return 
     */
    public MRInstance toMRInstance(ConlluSentence sentence, MRPGraph mrpgraph);
    
    /**
     * Evaluates an AM dependency tree, encoded as a ConllSentence. 
     * The graph should not be postprocessed already.
     * @param amconll
     * @return 
     */
    public MRPGraph evaluate(ConllSentence amconll);
    
    /**
     * Returns the signature builder for this instance with formalism
     * specific behaviour and blob utils.
     * @param instance
     * @return 
     */
    public AMSignatureBuilder getSignatureBuilder (MRInstance instance);
    
}
