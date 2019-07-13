/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.formalisms.amr.tools.RareWordsAnnotator;
import static de.saar.coli.amrtagging.formalisms.amr.tools.datascript.TestNER.matchesDatePattern;

import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.MrpPreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.StanfordPreprocessedData;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.CorpusWriter;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Util;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Makes the input data for the evaluation step, i.e.~ runs preprocessing on
 * the corpus sentences, without making use of the graphs.
 * @author Jonas
 */
public class MakeDevData {
    @Parameter(names = {"--corpusPath", "-c"}, description = "Path to the input corpus", required = true)
    private String corpusPath = null;

    @Parameter(names = {"--outPath", "-o"}, description = "Prefix for output files", required = true)
    private String outPath = null;

    @Parameter(names = {"--tagger-model"}, description = "Filename of Stanford POS tagger model english-bidirectional-distsim.tagger", required = false)
    private String stanfordTaggerFilename;

    @Parameter(names = {"--ner-model"}, description = "Filename of Stanford NER model english.conll.4class.distsim.crf.ser.gz", required = true)
    private String stanfordNerFilename;

    @Parameter(names = {"--companion"}, description = "Path to MRP companion data (will disable builtin tokenization and POS tagging", required = false)
    private String companionDataFile = null;

    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;

    /**
     * Makes the input data for the evaluation step, i.e.~ runs preprocessing on
     * the corpus sentences, without making use of the graphs.
     * First argument is the corpusPath to folder containing the corpus,
     * second argument is corpusPath to output folder, third is
     * corpusPath to stanford POS tagger model english-bidirectional-distsim.tagger and
     * fourth is corpusPath to stanford NER model english.conll.4class.distsim.crf.ser.gz
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws CorpusReadingException 
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ClassCastException, ClassNotFoundException, CorpusReadingException {
        MakeDevData mdd = new MakeDevData();
        JCommander commander = new JCommander(mdd);
        commander.setProgramName("MakeDevData");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (mdd.help) {
            commander.usage();
            return;
        }

        mdd.makeDevData();
    }

    public void makeDevData() throws IOException, CorpusReadingException, ClassNotFoundException {
        AbstractSequenceClassifier<CoreLabel> classifier = CRFClassifier.getClassifier(stanfordNerFilename);
        
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation("string", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("graph", new Interpretation(new GraphAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("id", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        String corpusFilename = corpusPath +"finalAlto.corpus";
        Corpus corpus = Corpus.readCorpus(new FileReader(corpusFilename), loaderIRTG);
        //BufferedReader graphBR = new BufferedReader(new FileReader(corpusPath+"raw.amr"));

        PreprocessedData preprocData = null;

        if( companionDataFile != null ) {
            preprocData = new MrpPreprocessedData(new File(companionDataFile));
        } else if( stanfordTaggerFilename != null ){
            preprocData = new StanfordPreprocessedData(stanfordTaggerFilename);
            ((StanfordPreprocessedData) preprocData).readTokenizedFromCorpus(corpus);
        } else {
            System.err.println("Either MRP companion data or the Stanford POS tagger is required.");
            System.exit(1);
        }
        
        FileWriter sentenceW = new FileWriter(outPath+"sentences.txt");
        FileWriter posW = new FileWriter(outPath+"pos.txt");
        FileWriter literalW = new FileWriter(outPath+"literal.txt");
        //FileWriter goldW = new FileWriter(corpusPath+"gold.txt");
        
        for (Instance inst : corpus) {
            List<String> ids = (List)inst.getInputObjects().get("id");
            String id = ids.get(0);
            
            List<String> sent = (List)inst.getInputObjects().get("string");
            List<List<CoreLabel>> lcls = classifier.classify(sent.stream().collect(Collectors.joining(" ")).replaceAll("[<>]", ""));
            List<CoreLabel> lcl = new ArrayList<>();
            lcls.forEach(l -> lcl.addAll(l));
            
            //lcl and sent are slightly differently tokenized. we need to fix that.
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
            
            List<TaggedWord> posTaggedWords = preprocData.getPosTags(id);
            List<String> posTags = Util.mapToList(posTaggedWords, tw -> tw.tag());
            
//            List<String> posTags = tagger.apply(sent.stream().map(word -> new Word(word)).collect(Collectors.toList()))
//                    .stream().map(tw -> tw.tag()).collect(Collectors.toList());
        
//            if (lcl.size() != sent.size()) {
//                System.err.println(lcl);
//                System.err.println(sent);
//            }
//            assert posTags.size() == sent.size();
            
            List<String> posOut = new ArrayList<>();
            List<String> sentOut = new ArrayList<>();
            List<String> literalOut = new ArrayList<>();
            
            int prevIndex = -1;
            String prevCat = "";
            for (int i = 0; i<sent.size(); i++) {
                CoreLabel cl = lcl.get(i);
                String ner = cl.get(CoreAnnotations.AnswerAnnotation.class);
                if (!ner.equals(TestNER.OTHER)) {
                    if (prevIndex == -1) {
                        //if we were searching before, now we start.
                        prevIndex = i;
                        prevCat = ner;
                    } else {
                        if (!prevCat.equals(ner)) {
                            //if category switched, save previous span and start new.
                            // **WARNING** duplicated code below
                            literalOut.add(sent.subList(prevIndex, i).stream()
                                    .collect(Collectors.joining("_")));
                            sentOut.add(RareWordsAnnotator.NAME_TOKEN.toLowerCase());
                            posOut.add(posTags.get(prevIndex));
                            prevIndex = i;
                            prevCat = ner;
                        }
                    }
                } else {
                    if (prevIndex != -1) {
                        //if we were working on a span before, save it and continue searching
                        // **WARNING** duplicated code above and below
                        literalOut.add(sent.subList(prevIndex, i).stream()
                                    .collect(Collectors.joining("_")));
                        sentOut.add(RareWordsAnnotator.NAME_TOKEN.toLowerCase());
                        posOut.add(posTags.get(prevIndex));
                        prevIndex = -1;
                    }
                    
                    //this now is the default case, where we don't have a named entity.
                    //first, keep literal and store POS
                    String origToken = sent.get(i);
                    posOut.add(posTags.get(i));
                    literalOut.add(origToken);
                    //now try and apply date and number rules, otherwise keep word in lowercase
                    int patternID = matchesDatePattern(origToken);
                    if (patternID >= 0) {
                        sentOut.add(RareWordsAnnotator.DATE_TOKEN.toLowerCase());
                    } else {
                        if (origToken.matches(RareWordsAnnotator.NUMBER_REGEX)) {
                            sentOut.add(RareWordsAnnotator.NUMBER_TOKEN.toLowerCase());
                        } else {
                            sentOut.add(origToken.toLowerCase());
                        }
                    }
                }
            }
            
            if (prevIndex != -1) {
                //if we were working on a span before, save it and continue searching
                // **WARNING** duplicated code above
                literalOut.add(sent.subList(prevIndex, sent.size()).stream()
                            .collect(Collectors.joining("_")));
                sentOut.add(RareWordsAnnotator.NAME_TOKEN.toLowerCase());
                posOut.add(posTags.get(prevIndex));
            }
            
            posW.write(posOut.stream().collect(Collectors.joining(" "))+"\n");
            sentenceW.write(sentOut.stream().collect(Collectors.joining(" "))+"\n");
            literalW.write(literalOut.stream().collect(Collectors.joining(" "))+"\n");
            
            inst.getInputObjects().put("pos", posOut);
            inst.getInputObjects().put("repstring", sentOut);
            inst.getInputObjects().put("literal", literalOut);
            
            //write gold graph in proper format (with blank line in between)
            //goldW.write(graphBR.readLine()+"\n\n");
            ((SGraph)inst.getInputObjects().get("graph")).setWriteAsAMR(true);
        }
        
        posW.close();
        sentenceW.close();
        literalW.close();
        //goldW.close();
        
        loaderIRTG.addInterpretation("pos", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("literal", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("repstring", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        
        new CorpusWriter(loaderIRTG, "evaluation input", "///###", new FileWriter(outPath+"evalInput.corpus"))
                .writeCorpus(corpus);
        
    }
    
}
