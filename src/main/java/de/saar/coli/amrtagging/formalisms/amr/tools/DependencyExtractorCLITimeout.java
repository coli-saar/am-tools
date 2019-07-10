/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.Alignment.Span;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import edu.stanford.nlp.simple.Sentence;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Command line interface for the DependencyExtractor class; call with --help to see options.
 * @author jonas
 */
public class DependencyExtractorCLITimeout {
    @Parameter(names = {"--corpusPath", "-c"}, description = "Path to the input corpus") //required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/100.corpus";

    @Parameter(names = {"--outPath", "-o"}, description = "Prefix for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab;

    @Parameter(names = {"--timeout"}, description = "Seconds for timeout for a single sentence")
    private int timeout = 120;
    
    private boolean coref = false;

    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;
    
    /**
     * Command line interface for the DependencyExtractor class; call with --help to see options.
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws CorpusReadingException
     * @throws IllegalArgumentException
     * @throws ParseException
     * @throws InterruptedException 
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, CorpusReadingException, IllegalArgumentException, ParseException, InterruptedException{
        
        DependencyExtractorCLITimeout cli = new DependencyExtractorCLITimeout();
        JCommander commander = new JCommander(cli);
        commander.setProgramName("constraint_extractor");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }
        
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation("repgraph", new Interpretation(new GraphAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("repstring", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("string", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("spanmap", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
//        if (cli.joint) {
//            loaderIRTG.addInterpretation("repalignmentp", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
//        } else {
        loaderIRTG.addInterpretation("repalignment", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
//        }
        
        Corpus corpus = Corpus.readCorpusWithStrictFormatting(new FileReader(cli.corpusPath), loaderIRTG);
        
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        
        
        
        SupertagDictionary dictionary = new SupertagDictionary();
        if (cli.vocab != null){
            try {
            dictionary.readFromFile(cli.vocab);
            } catch (ParserException ex){
                System.err.println("Supertag vocab file "+cli.vocab+" is not a valid SupertagDictionary file.");
                ex.printStackTrace();
            }
        }
        
        int i = 0;
        for (Instance inst : corpus) {
            i++;
            SGraph graph = (SGraph)inst.getInputObjects().get("repgraph");
            List<String> sent = (List)inst.getInputObjects().get("repstring");
            List<String> origSent = (List)inst.getInputObjects().get("string");
            List<String> spanmap = (List)inst.getInputObjects().get("spanmap");

            List<String> als;

            als =(List)inst.getInputObjects().get("repalignment");
            if (als.size() == 1 && als.get(0).equals("")) {
                //System.err.println("Repaired empty alignment!");
                als = new ArrayList<>();
            }
            String[] alStrings = als.toArray(new String[0]);
            ArrayList<Alignment> alignments = new ArrayList<>();
            for (String alString : alStrings) {
                Alignment al = Alignment.read(alString, 0);
                alignments.add(al);
            }
                    Sentence stanfAn = new Sentence(origSent);
                    List<String> origPosTags = stanfAn.posTags();
                    List<String> origLemmas = new ArrayList<>(stanfAn.lemmas());
                    List<String> origNE = new ArrayList<>(stanfAn.nerTags());
                    
                    List<String> posTags = new ArrayList<>();
                    List<String> lemmas = new ArrayList<>();
                    List<String> nerTags = new ArrayList<>();
                    List<String> literals = new ArrayList<>(); //contains the original words but has the same length as the list with replaced words, some may be glued together
                    //make POS and literal output, from original sentence, using span map
                    for (String spanString : spanmap) {
                        Span span = new Span(spanString);
                        List<String> origWords = new ArrayList<>();
                        for (int l = span.start; l<span.end; l++) {
                            origWords.add(origSent.get(l));
                        }
                        literals.add(origWords.stream().collect(Collectors.joining("_")));
                        posTags.add(origPosTags.get(span.start));
                        lemmas.add(origLemmas.get(span.start));
                        nerTags.add(origNE.get(span.start));
                   }
                    MRInstance instance = new MRInstance(literals, graph, alignments);   
                    
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<ConllSentence> future = executor.submit(new Task(instance, dictionary, posTags, lemmas, nerTags, sent));
                    try {
                        ConllSentence o = future.get(cli.timeout, TimeUnit.SECONDS);
                        if (o != null){
                            outCorpus.add(o);
                        }
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        System.err.println("Skipping this sentence "+instance.getSentence());
                    } catch (ExecutionException  e){
                        System.err.println("Ignoring this:");
                        e.printStackTrace();
                    }
                    executor.shutdownNow();
                    if (i % 10 == 0){
                        System.err.println(i);
                        ConllSentence.writeToFile(cli.outPath+"/corpus.amconll", outCorpus);
                        dictionary.writeToFile(cli.outPath+"/supertags.txt");
                    }
        }
        
        ConllSentence.writeToFile(cli.outPath+"/corpus.amconll", outCorpus);
        dictionary.writeToFile(cli.outPath+"/supertags.txt");
        
        
    }
    
    static class Task implements Callable<ConllSentence> {
        MRInstance inst;
        SupertagDictionary supertagDictionary;
        private final List<String> posTags;
        private final List<String> lemmas;
        private final List<String> nerTags;
        private final List<String> repl;
        public Task(MRInstance inst,SupertagDictionary dict, List<String> posTags, List<String> lemmas, List<String> nerTags, List<String> repl){
            this.inst = inst;
            supertagDictionary = dict;
            this.posTags = posTags;
            this.lemmas = lemmas;
            this.nerTags = nerTags;
            this.repl = repl;
        }
        @Override
        public ConllSentence call() throws Exception {
            try {
                TreeAutomaton auto;
                auto = AlignmentTrackingAutomaton.create(inst, new AMRSignatureBuilder(), false);
                auto.processAllRulesBottomUp(null);

                Tree<String> vit = auto.viterbi();
                if (vit != null) {
                     ConllSentence cs = ConllSentence.fromIndexedAMTerm(vit, inst, supertagDictionary);
                     cs.addPos(posTags);
                     cs.addLemmas(lemmas);
                     cs.addNEs(nerTags);
                     cs.addReplacement(this.repl);
                     return cs;
                }
                } catch (Exception ex) {
                    System.err.println("Ignoring exception:");
                    ex.printStackTrace();
                }
               return null;

        }
    }
    
    
    
}
