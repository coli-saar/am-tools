/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.wordnet;

import java.util.Collections;
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

    public static class DummyIWordnet implements IWordnet {
        /**
         * A dummy implementation of IWordnet, that simply always returns the input word (and in scoreRel only
         * returns 0 if the two words are the same, and -1000 -- the score for no match -- in all other cases).
         * In other words, this assumes that there are no hypernyms, related words, stems etc. It's all just
         * the original word.
         * @param word
         * @return
         */

        @Override
        public Set<String> getAllNounHypernyms(String word) {
            return Collections.singleton(word);
        }

        @Override
        public Set<String> getWNCandidates(String word) {
            return Collections.singleton(word);
        }

        @Override
        public double scoreRel(String word, String lemma2check) {
            if (word.equals(lemma2check)) {
                return 0;
            } else {
                return -1000;
            }
        }

        @Override
        public String findVerbStem(String word) {
            return word;
        }

        @Override
        public String findRelatedVerbStem(String word) {
            return word;
        }

        @Override
        public String findNounStem(String word) {
            return word;
        }
    }

}
