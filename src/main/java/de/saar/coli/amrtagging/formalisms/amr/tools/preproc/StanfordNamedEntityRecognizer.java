package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import de.up.ling.irtg.util.Util;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.simple.Sentence;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StanfordNamedEntityRecognizer implements NamedEntityRecognizer {
    private AbstractSequenceClassifier<CoreLabel> classifier = null;

    /**
     * Creates new StanfordNamedEntityRecognizer. If detailedTagset is true,
     * the model file is ignored and the CoreNLP "simple" API is used which uses
     * a much more finegrained tagset (and is slower).
     * @param nerModelFile
     * @param finegrained
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    public StanfordNamedEntityRecognizer(File nerModelFile, boolean finegrained) throws IOException, ClassNotFoundException {
        if (!finegrained){
            classifier = CRFClassifier.getClassifier(nerModelFile);
        }
        
    }


    @Override
    public List<CoreLabel> tag(List<CoreLabel> tokens) {
        List<String> sent = Util.mapToList(tokens, CoreLabel::word);
        
        List<CoreLabel> lcl;
        if (classifier != null){
            lcl = classifier.classifySentence(tokens);
        } else {
            lcl =  new Sentence(sent).asCoreLabels((x -> x.nerTags()));;
        }
         

        assert lcl.size() == sent.size();

        // move NER tags from the AnswerAnnotation field to the NER field
        List<CoreLabel> ret = new ArrayList<>(sent.size());
        for( int i = 0; i < sent.size(); i++ ) {
            CoreLabel x = tokens.get(i);
            if (classifier != null){
                x.setNER(lcl.get(i).get(CoreAnnotations.AnswerAnnotation.class));
            } else {
                x.setNER(lcl.get(i).ner());
            }
            
            ret.add(x);
        }

        return ret;
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException, PreprocessingException {
        NamedEntityRecognizer rec = new StanfordNamedEntityRecognizer(new File("resources/english.conll.4class.distsim.crf.ser.gz"), true);

        List<CoreLabel> tags = rec.tag(UiucNamedEntityRecognizer.getTestTokens());
        System.err.println(Util.mapToList(tags, CoreLabel::ner));
    }
}
