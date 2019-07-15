package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import de.up.ling.irtg.util.Util;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
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
        return lcl;
    }
}
