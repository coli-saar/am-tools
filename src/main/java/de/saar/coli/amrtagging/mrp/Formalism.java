/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp;

import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.mrp.utils.ConlluSentence;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.up.ling.irtg.algebra.graph.SGraph;

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
     * Returns a graphbank specific refinement of the tokenization from the companion data.
     * @param sentence
     * @return 
     */
    public ConlluSentence refineTokenization(ConlluSentence sentence);
    
    
    /**
     * Performs an MRP specific preprocessing step, e.g. split named entity information 
     * which is expressed as properties into separate nodes
     * @param mrpgraph
     * @return 
     */
    public MRPGraph preprocessMRP(MRPGraph mrpgraph);
    
    /**
     * Reverts the MRP specific preprocessing step, should be the inverse of preprocess.
     * @param mrpgraph
     * @return 
     */
    public MRPGraph postprocessMRP(MRPGraph mrpgraph);
    
    
    /**
     * A preprocessing step with more contentful purpose, e.g. coordination in PSD.
     * @param inst
     * @return 
     */
    public MRInstance preprocess(MRInstance inst);
    
    
    /**
     * Inverse of preprocess on the MRInstance.
     * @param sgraph
     * @return 
     */
    public SGraph postprocess(SGraph sgraph);
    
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
