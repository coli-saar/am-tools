/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.aligner;

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
     * I don't quite understand what this method does. I think it
     * tries to find lemmas in Wordnet that are semantically related
     * to the given word form and stores them in a private dictionary.
     * It also computes a score for the Wordnet path from the word
     * to each of these lemmas, for later access with {@link #scoreRel(java.lang.String, java.lang.String) }.
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
