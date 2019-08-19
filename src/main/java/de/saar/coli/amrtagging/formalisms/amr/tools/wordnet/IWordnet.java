/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.wordnet;

import java.util.Set;

/**
 *
 * @author koller
 */
public interface IWordnet {
    /**
     * Returns the set of all noun hypernyms of the given word.
     * 
     * @param word
     * @return 
     */
    public Set<String> getAllNounHypernyms(String word);
    
    /**
     * Returns a set of node-label candidates for the given inflected word.
     * Candidates are lemmas, e.g. the uninflected words in Wordnet synsets.
     * These candidates will be compared against node labels in the AMR graph,
     * and if a candidate matches a node label exactly, that node becomes
     * a good candidate for being aligned with the token in the sentence.<p>
     * 
     * The method also builds up a data structure which will support
     * subsequent queries to the quality of a candidate, using
     * {@link #scoreRel(java.lang.String, java.lang.String) }.
     * 
     * @param word
     * @return 
     */
    public Set<String> getWNCandidates(String word);
    
    /**
     * Returns the score of the path from a word (as occurring in the sentence)
     * to a related lemma. If no relation is found, -1000 is returned. (large
     * enough to make score really bad, but safely away from overflow). Returns
     * a negative value, lower is worse. Current range is from 0 to -1.4
     *
     * @param word
     * @param lemma2check
     * @return
     */
    public double scoreRel(String word, String lemma2check);
    
    /**
     * Looks up the given word form as a verb. If it is found,
     * return its first lemma. Otherwise returns null.
     * 
     * @param word
     * @return 
     */
    public String findVerbStem(String word);
    
    /**
     * Looks up the given word form as a verb that is
     * derivationally related to the given word form.
     * If it is found, return the first found lemma. 
     * Otherwise return null.
     * 
     * @param word
     * @return 
     */
    public String findRelatedVerbStem(String word);
    
    /**
     * Looks up the given word form as a noun. If it is found,
     * returns its first lemma. Otherwise, returns null.
     * 
     * @param word
     * @return 
     */
    public String findNounStem(String word);
}
