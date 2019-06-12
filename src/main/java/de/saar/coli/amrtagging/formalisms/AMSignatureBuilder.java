/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.tree.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 *
 * @author matthias
 */
public interface AMSignatureBuilder {
    
    
    public static final String SUBJ = "s";
    public static final String OBJ = "o";
    public static final String MOD = "mod";
    public static final String POSS = "poss";
    public static final String DOMAIN = "s";//"dom" for now, maybe use s instead -- EDIT: definitely do that (TODO) -- EDIT: did that (Matthias)
    
     /**
     * Creates a signature with all relevant constants (including source annotations)
     * for the decomposition automaton.
     * @param graph
     * @param maxCoref
     * @return
     * @throws de.up.ling.tree.ParseException
     */
    
    public Signature makeDecompositionSignature(SGraph graph, int maxCoref) throws IllegalArgumentException, ParseException; 
    
    
      /**
     * Create a signature with constants for all the given alignments.
     * @param graph
     * @param alignments
     * @param coref
     * @return
     * @throws de.up.ling.tree.ParseException
     */
    public Signature makeDecompositionSignatureWithAlignments(SGraph graph, List<Alignment> alignments, boolean coref) throws IllegalArgumentException, ParseException;
    
    
      /**
     * Runs heuristics to create constants that cover the nodes in Alignment al within the SGraph graph.
     * @param al
     * @param graph
     * @param coref
     * @return
     * @throws de.up.ling.tree.ParseException
     */
    public Set<String> getConstantsForAlignment(Alignment al, SGraph graph, boolean coref) throws IllegalArgumentException, ParseException;
    
    
    /**
     * Scores a constant. May want to change that in the future.
     * @param graph
     * @return 
     */
    
    public double scoreGraph(Pair<SGraph, ApplyModifyGraphAlgebra.Type> graph);
    
    /**
     * Returns all sources that are possible in this signature (maybe taking into account the graph for op_1, op_n)
     * @param graph
     * @return 
     */
    @Deprecated
    public Collection<String> getAllPossibleSources(SGraph graph);
    
    
}
