package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import de.up.ling.irtg.util.Util;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StanfordNamedEntityRecognizer implements NamedEntityRecognizer {
    AbstractSequenceClassifier<CoreLabel> classifier;

    public StanfordNamedEntityRecognizer(File nerModelFile) throws IOException, ClassNotFoundException {
        classifier = CRFClassifier.getClassifier(nerModelFile);
    }

    @Override
    public List<CoreLabel> tag(List<CoreLabel> tokens) throws PreprocessingException {
        List<String> sent = Util.mapToList(tokens, CoreLabel::word);
        List<List<CoreLabel>> lcls = classifier.classify(sent.stream().collect(Collectors.joining(" ")).replaceAll("[<>]", ""));
        List<CoreLabel> lcl = new ArrayList<>();
        lcls.forEach(l -> lcl.addAll(l));


        // lcl and sent are slightly differently tokenized. we need to fix that.
        // NB The more systematic fix would be to simply use run on the same tokens always - AK
        if (lcl.size() < sent.size()) {
            System.err.println(lcl);
            System.err.println(sent);
        }
        String lastWord = "";
        CoreLabel lastLCL = null;
        for (int i = 0; i<sent.size(); i++) {
            String word = sent.get(i);
            String lclWord = lcl.get(i).word();
            if (!lclWord.equals(word)) {
                if (lastWord.endsWith(lclWord)) {
                    lcl.remove(i);
                } else if (lastLCL != null && lastLCL.word().endsWith(word)) {
                    lcl.add(i, lastLCL);
                }
            }
            lastWord = word;
            lastLCL = lcl.get(i);
        }

        if (lcl.size() == sent.size() + 1 && !sent.get(sent.size()-1).equals(lcl.get(lcl.size()-1).word())) {
            lcl.remove(lcl.size()-1);
        }

        assert lcl.size() == sent.size();

        // move NER tags from the AnswerAnnotation field to the NER field
        List<CoreLabel> ret = new ArrayList<>(sent.size());
        for( int i = 0; i < sent.size(); i++ ) {
            CoreLabel x = tokens.get(i);
            x.setNER(lcl.get(i).get(CoreAnnotations.AnswerAnnotation.class));
            ret.add(x);
        }

        return ret;
    }

    /*
    public static void main(String[] args) throws IOException, ClassNotFoundException, PreprocessingException {
        NamedEntityRecognizer rec = new StanfordNamedEntityRecognizer(new File("resources/english.conll.4class.distsim.crf.ser.gz"));

        List<CoreLabel> tags = rec.tag(UiucNamedEntityRecognizer.getTestTokens());
        System.err.println(Util.mapToList(tags, CoreLabel::ner));
    }

     */
}
