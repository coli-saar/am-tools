package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

import edu.illinois.cs.cogcomp.annotation.AnnotatorException;
import edu.illinois.cs.cogcomp.annotation.TextAnnotationBuilder;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.resources.ResourceConfigurator;
import edu.illinois.cs.cogcomp.ner.NERAnnotator;
import edu.illinois.cs.cogcomp.nlp.tokenizer.StatefulTokenizer;
import edu.illinois.cs.cogcomp.nlp.utility.TokenizerTextAnnotationBuilder;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UiucNamedEntityRecognizer {
    public static void main(String[] args) throws IOException {
//        String text1 = "Good afternoon, gentlemen. I am a HAL-9000 "
//                + "computer. I was born in Urbana, Il. in 1992";
//
//        String corpus = "2001_ODYSSEY";
//        String textId = "001";
//
//        ResourceConfigurator.ENDPOINT.value = "http://macniece.seas.upenn.edu:4008";
////        edu.illinois.cs.cogcomp.tokenizer.
//        // Create a TextAnnotation using the LBJ sentence splitter
//        // and tokenizers.
//        TextAnnotationBuilder tab = new TokenizerTextAnnotationBuilder(null); //new WhiteSpaceTok);
//        // don't split on hyphens, as NER models are trained this way
//        boolean splitOnHyphens = false;
//        tab = new TokenizerTextAnnotationBuilder(new StatefulTokenizer(splitOnHyphens, false));
//
//        TextAnnotation ta = tab.createTextAnnotation(corpus, textId, text1);
//
//        NERAnnotator co = new NERAnnotator(ViewNames.NER_CONLL);
//        try {
//            co.getView(ta);
//        } catch (AnnotatorException e) {
//            e.printStackTrace();
//        }
//
//        System.out.println(ta.getView(ViewNames.NER_CONLL));

        /*
        String text1 = "Good afternoon, gentlemen. I am a HAL-9000 computer. I was born in Urbana, Il. in 1992";
        String text1 = "This is John Doe, born in Washington DC, USA on the 4th of July 2000. " +
                "His mother is called Jana Doe. " +
                "His father is German but speaks French. " +
                "Joe works at Google. An apple costs 2.5 $. " +
                "He cannot live without his Playstation and his iPhone. " +
                "He is 100% sure he said 1st April 2019.";
        */

        String corpusId = "test_text";
        String textId = "001";

        // had problems connecting to smaug.cs.illinois.edu/192.17.58.151:8080 to download gazeteers
        // see https://github.com/CogComp/cogcomp-nlp/issues/714
        ResourceConfigurator.ENDPOINT.value = "http://macniece.seas.upenn.edu:4008";

        /*// Create a TextAnnotation using the LBJ sentence splitter and tokenizers.
        TextAnnotationBuilder tab;
        // don't split on hyphens, as NER models are trained this way
        boolean splitOnHyphens = false;
        tab = new TokenizerTextAnnotationBuilder(new StatefulTokenizer(splitOnHyphens, false));

        TextAnnotation ta = tab.createTextAnnotation(corpusId, textId, text1);*/

        // new: TextAnnotation built frome xisting tokenization
        List<String> sent = Arrays.asList("John","Doe","is","there","at","Google","at","9","April","2019","10","a.m.");
        String text1 = String.join(" ", sent);

        List<IntPair> characterOffsets = new ArrayList<>();
        int sentlengthsofar = 0;
        for (String s : sent) {
            int toksize = s.length();
            characterOffsets.add(new IntPair(sentlengthsofar, sentlengthsofar + toksize));  // off by one? space?
            sentlengthsofar += toksize;
        }
        String[] tokens = sent.toArray(new String[0]); // ??
        int[] sentenceEndPositions = new int[]{sent.size()};

        TextAnnotation ta = new TextAnnotation(
                corpusId,
                textId,
                text1,
                characterOffsets.toArray(new IntPair[0]),
                tokens,
                sentenceEndPositions);

        NERAnnotator co = new NERAnnotator(ViewNames.NER_ONTONOTES);
        // NERAnnotator co = new NERAnnotator(ViewNames.NER_CONLL);
        try {
            co.getView(ta);
        } catch (AnnotatorException e) {
            e.printStackTrace();
        }
        System.out.println("Text is " + text1);
        System.out.println(ta.getView(ViewNames.NER_ONTONOTES));
        // System.out.println(ta.getView(ViewNames.NER_CONLL));
    }

    private static List<CoreLabel> getTestTokens() {
        return null;
    }
}
