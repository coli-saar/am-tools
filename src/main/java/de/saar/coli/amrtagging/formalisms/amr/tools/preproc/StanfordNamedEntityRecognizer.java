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
    private AbstractSequenceClassifier<CoreLabel> classifier;

    public StanfordNamedEntityRecognizer(File nerModelFile) throws IOException, ClassNotFoundException {
        classifier = CRFClassifier.getClassifier(nerModelFile);
    }

    @Override
    public List<CoreLabel> tag(List<CoreLabel> tokens) {
        List<String> sent = Util.mapToList(tokens, CoreLabel::word);
        List<CoreLabel> lcl = classifier.classifySentence(tokens);

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

    public static void main(String[] args) throws IOException, ClassNotFoundException, PreprocessingException {
        NamedEntityRecognizer rec = new StanfordNamedEntityRecognizer(new File("resources/english.conll.4class.distsim.crf.ser.gz"));

        List<CoreLabel> tags = rec.tag(UiucNamedEntityRecognizer.getTestTokens());
        System.err.println(Util.mapToList(tags, CoreLabel::ner));
    }
}
