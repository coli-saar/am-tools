/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import de.saar.coli.amrtagging.Util;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.Instance;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author koller
 */
public class StanfordPreprocessedData implements PreprocessedData {

    private Map<String, List<CoreLabel>> tokensForId;
    private Map<String, String> untokenizedSentenceForId;

    private MaxentTagger tagger;
    private TokenizerFactory<CoreLabel> tokenizerFactory;

    public StanfordPreprocessedData(String taggerModelPath) {
        if (taggerModelPath == null) {
            tagger = null; // disable POS tagging
        } else {
            tagger = new MaxentTagger(taggerModelPath);
        }

        tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
        tokensForId = new HashMap<>();
        untokenizedSentenceForId = new HashMap<>();
    }

    @Override
    public List<TaggedWord> getPosTags(String instanceId) {
        if (tagger == null) {
            // no tagger available
            return null;
        } else {
            List<CoreLabel> sentence = getTokens(instanceId);
            List<TaggedWord> ret = tagger.apply(sentence);
//            System.err.println("postags: " + ret);
            return ret;
        }
    }

    @Override
    public List<CoreLabel> getTokens(String instanceId) {
        List<CoreLabel> ret = tokensForId.get(instanceId);

        if (ret == null) {
            String untok = untokenizedSentenceForId.get(instanceId);

            if (untok == null) {
                return null;
            } else {
                Tokenizer<CoreLabel> tok = tokenizerFactory.getTokenizer(new StringReader(untok));
                ret = tok.tokenize();
            }
        }

        return ret;
    }

    @Override
    public List<String> getLemmas(String instanceId) {
        throw new UnsupportedOperationException("Lemmatization is not yet supported in StanfordPreprocessedData.");
    }

    @Override
    public void setTokens(String instanceId, List<String> tokens) {
        List<CoreLabel> labeled = Util.makeCoreLabelsForTokens(tokens);
        tokensForId.put(instanceId, labeled);
    }

    @Override
    public void setUntokenizedSentence(String instanceId, String sentence) {
        untokenizedSentenceForId.put(instanceId, sentence);
    }

    public void readTokenizedFromCorpus(Corpus corpus) {
        for (Instance inst : corpus) {
            List<String> origSent = (List) inst.getInputObjects().get("string");
            List<String> ids = (List) inst.getInputObjects().get("id");
            String id = ids.get(0);

            setTokens(id, origSent);
        }
    }
}
