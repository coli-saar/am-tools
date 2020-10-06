/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;

import java.util.List;

/**
 * Data which has been low-level preprocessed (tokenization,
 * POS tagging, lemmatization).
 * Implementations of this class may choose to either compute
 * the tokens and POS tags themselves, or look them up in a file.<p>
 * 
 * This class assumes that every instance in the corpus has an ID,
 * and will return lists of tokens or tags for a given instance ID.
 * 
 * 
 * @author koller
 */
public interface PreprocessedData {
    /**
     * Returns the POS tags of the sentence, one per token.
     * 
     * @param instanceId
     * @return
     */
    public List<TaggedWord> getPosTags(String instanceId);
    
    /**
     * Returns the tokens of the sentence.<p>
     *
     * Each token is a {@link CoreLabel} object containing
     * at least the following information: (a) the original
     * word (access with {@link CoreLabel#word()}; (b)
     * the character-based begin and end position in the
     * original string (access with {@link CoreLabel#beginPosition()}
     * and {@link CoreLabel#endPosition()}).
     * 
     * @param instanceId
     * @return 
     */
    public List<CoreLabel> getTokens(String instanceId);

    /**
     * Returns the lemmas of the sentence.
     *
     * @param instanceId
     * @return
     */
    public List<String> getLemmas(String instanceId);

    /**
     * Sets the tokens for the sentence. Use this method if
     * a tokenization was already available, e.g. from a previous
     * preprocessing step.
     * 
     * @param instanceId
     * @param tokens 
     */
    public void setTokens(String instanceId, List<String> tokens);
    
    /**
     * Sets the untokenized sentence. If {@link #setTokens(java.lang.String, java.util.List) }
     * is called for the same instanceId before or after a call to this
     * method, the more explicit information passed to setTokens
     * takes priority, and the call to this method will have no effect.
     * 
     * @param instanceId
     * @param sentence 
     */
    public void setUntokenizedSentence(String instanceId, String sentence);

}
