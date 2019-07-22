package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import com.google.common.collect.ImmutableMap;
import de.up.ling.irtg.util.Util;
import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.resources.ResourceConfigurator;
import edu.illinois.cs.cogcomp.ner.NERAnnotator;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class UiucNamedEntityRecognizer implements NamedEntityRecognizer {
    static {
        // had problems connecting to smaug.cs.illinois.edu/192.17.58.151:8080 to download gazeteers
        // see https://github.com/CogComp/cogcomp-nlp/issues/714
        ResourceConfigurator.ENDPOINT.value = "http://macniece.seas.upenn.edu:4008";
    }

    private static Map<String,String> uiucTagToStanfordTag = ImmutableMap.of("PER", PERSON, "ORG", ORGANIZATION, "LOC", LOCATION, "MISC", MISCELLANEOUS);

    private NERAnnotator co = null;
    private String tagset;

    public UiucNamedEntityRecognizer(String tagset) {
        try {
            co = new NERAnnotator(tagset);
            this.tagset = tagset;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public UiucNamedEntityRecognizer() {
        this(ViewNames.NER_CONLL); // 4-label tagset: PER / ORG / LOC / MISC
    }

    @Override
    public synchronized List<CoreLabel> tag(List<CoreLabel> tokens) throws PreprocessingException {
        List<String> words = Util.mapToList(tokens, CoreLabel::word);
        String text1 = String.join(" ", words);
        List<IntPair> characterOffsets = Util.mapToList(tokens, cl -> new IntPair(cl.beginPosition(), cl.endPosition()));
        int[] sentenceEndPositions = new int[] { tokens.size() };

        TextAnnotation ta = new TextAnnotation(
                "dummy_corpus_id",
                "dummy_text_id",
                text1,
                characterOffsets.toArray(new IntPair[0]),
                words.toArray(new String[0]),
                sentenceEndPositions);

        try {
            co.getView(ta);
        } catch (AnnotatorException e) {
            throw new PreprocessingException(e);
        }

        SpanLabelView view = (SpanLabelView) ta.getView(tagset);

        // extract NER labels from view
        for( int i = 0; i < tokens.size(); i++ ) {
            String neLabel = view.getLabel(i);

            if( "".equals(neLabel)) {
                tokens.get(i).setNER(NER_NULL);
            } else {
                String decodedLabel = uiucTagToStanfordTag.get(neLabel);

                if( decodedLabel == null ) {
                    throw new PreprocessingException("Unknown UIUC NER tag: " + neLabel);
                }

                tokens.get(i).setNER(decodedLabel);
            }
        }

        return tokens;
    }

    /*
    public static void main(String[] args) throws IOException, PreprocessingException {
        List<CoreLabel> tokens = getTestTokens();
        UiucNamedEntityRecognizer rec = new UiucNamedEntityRecognizer();
        List<CoreLabel> tagged = rec.tag(tokens);

        System.err.println(tagged);
        System.err.println(Util.mapToList(tagged, CoreLabel::ner));

    }

    static List<CoreLabel> getTestTokens() {
        List<CoreLabel> ret = new ArrayList<>();
        List<String> sent = Arrays.asList("John","Doe","is","there","at","Google","at","9","April","2019","10","a.m.");
        String text1 = String.join(" ", sent);

        List<IntPair> characterOffsets = new ArrayList<>();
        int sentlengthsofar = 0;
        for (String s : sent) {
            int toksize = s.length();
            characterOffsets.add(new IntPair(sentlengthsofar, sentlengthsofar + toksize));

            CoreLabel lab = CoreLabel.wordFromString(s);
            lab.setBeginPosition(sentlengthsofar);
            lab.setEndPosition(sentlengthsofar+toksize);
            ret.add(lab);

            sentlengthsofar += toksize+1;
        }

        return ret;
    }

     */
}
