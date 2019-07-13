package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.TokenRange;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MrpPreprocessedData implements PreprocessedData {
    private Map<String,ConlluSentence> companionData;

    public MrpPreprocessedData(File companionDataFilename) throws IOException {
        // load companion data
        List<ConlluSentence> sentences = ConlluSentence.read(new FileReader(companionDataFilename));
        companionData = new HashMap<>();
        for( ConlluSentence sent : sentences ) {
            companionData.put(sent.getId(), sent);
        }
    }

    @Override
    public List<TaggedWord> getPosTags(String instanceId) {
        ConlluSentence sent = companionData.get(instanceId);

        if( sent == null ) {
            return null;
        } else {
            List<TaggedWord> ret = new ArrayList<>();

            for( int i = 0; i < sent.size(); i++ ) {
                ConlluEntry entry = sent.get(i);
                TaggedWord tw = new TaggedWord(entry.getForm(), entry.getPos());
                ret.add(tw);
            }
            return ret;
        }
    }

    @Override
    public List<CoreLabel> getTokens(String instanceId) {
        ConlluSentence sent = companionData.get(instanceId);

        if( sent == null ) {
            return null;
        } else {
            List<CoreLabel> ret = new ArrayList<>();

            for( int i = 0; i < sent.size(); i++ ) {
                ConlluEntry entry = sent.get(i);
                TokenRange tr = entry.getTokenRange();

                CoreLabel cl = CoreLabel.wordFromString(entry.getForm());
                cl.setBeginPosition(tr.getFrom());
                cl.setEndPosition(tr.getTo());

                ret.add(cl);
            }
            return ret;
        }
    }

    @Override
    public void setTokens(String instanceId, List<String> tokens) {

    }

    @Override
    public void setUntokenizedSentence(String instanceId, String sentence) {

    }
}
