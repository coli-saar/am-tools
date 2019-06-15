/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
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
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import java.io.FileWriter;
import java.util.stream.Collectors;

/**
 * Command line interface for the DependencyExtractor class; call with --help to see options.
 * @author jonas
 */
public class DependencyExtractorCLI {
    @Parameter(names = {"--corpusPath", "-c"}, description = "Path to the input corpus") //required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/100.corpus";

    @Parameter(names = {"--outPath", "-o"}, description = "Prefix for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab;
    
    //@Parameter(names = {"--posPath", "-pos"}, description = "Path to the stanford POS tagger model file english-bidirectional-distsim.tagger", required = true)
    //private String posPath;
    
    @Parameter(names = {"--threads", "-t"}, description = "Number of threads over which the instances should be parallelized")
    private int numThreads = 1;
    
    @Parameter(names = {"--limit", "-li"}, description = "Number of minutes after which the process will be terminated")
    private long limit = 999999999;
    
    @Parameter(names = {"--coref", "-cr"}, description = "Set this flag to allow coref sources")
    private boolean coref = false;
    
//    @Parameter(names = {"--joint"}, description = "Set this flag to track alignments jointly (using the alignmentp values)")
//    private boolean joint = false;
//    
//    @Parameter(names = {"--doWrap"}, description = "Set this flag to make the JointAlignmentTrackingAutomaton spread (only does something in conjunction with --joint)")
//    private boolean doWrap = false;
//    
//    @Parameter(names = {"--spread"}, description = "How many words next to a span are allowed to be covered without alignment in")
//    private int maxJointSpread = 2;
    
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
    public static void main(String[] args) throws FileNotFoundException, IOException, CorpusReadingException, IllegalArgumentException, ParseException, InterruptedException {
        
        DependencyExtractorCLI cli = new DependencyExtractorCLI();
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
        
        //MaxentTagger tagger = new MaxentTagger(cli.posPath);
        
        MutableInteger nextInstanceID = new MutableInteger(0);
        ForkJoinPool forkJoinPool = new ForkJoinPool(cli.numThreads);
        MutableInteger success = new MutableInteger(0);
        
        SupertagDictionary dictionary = new SupertagDictionary();
        if (cli.vocab != null){
            try {
            dictionary.readFromFile(cli.vocab);
            } catch (ParserException ex){
                System.err.println("Supertag vocab file "+cli.vocab+" is not a valid SupertagDictionary file.");
                ex.printStackTrace();
            }
        }
        
        for (Instance inst : corpus) {
            final int i = nextInstanceID.incValue();//returns old value
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
            
            forkJoinPool.execute(() -> {
                try {
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
                    
                    TreeAutomaton auto;
//                    if (cli.joint) {
//                        auto = JointAlignmentTrackingAutomaton.create(graph, alStrings, sent, cli.doWrap, cli.maxJointSpread);
//                    } else {
                    MRInstance instance = new MRInstance(literals, graph, alignments);               
                    auto = AlignmentTrackingAutomaton.create(instance, new AMRSignatureBuilder(), cli.coref);
//                    }
                    auto.processAllRulesBottomUp(null);

                    Tree<String> vit = auto.viterbi();
                    if (vit != null) {
                        ConllSentence cs = ConllSentence.fromIndexedAMTerm(vit, instance, dictionary);
                        cs.addPos(posTags);
                        cs.addLemmas(lemmas);
                        cs.addNEs(nerTags);
                        cs.addReplacement(sent);
                        AMDependencyTree deptree = AMDependencyTree.fromSentence(cs);
                        //deptree.getTree().map(ent -> ent.getForm() + " "+ent.getEdgeLabel()).draw(); //only use with very very small corpus
                        if (!deptree.evaluate(false).equals(graph)){
                            System.err.println("Not equal "+deptree.evaluate(false) + " and "+graph);
                        }
                        synchronized (success) {
                            success.incValue();
                        }
                        synchronized (outCorpus){
                           outCorpus.add(cs);
                           ConllSentence.writeToFile(cli.outPath+"/corpus.amconll", outCorpus);
                           dictionary.writeToFile(cli.outPath+"/supertags.txt");
                        }
                    }
                    if ((i+1) % 500 == 0) {
                        synchronized (success) {
                                System.err.println("Successes: "+success.getValue()+"/"+i);
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    System.err.println(i);
                    System.err.println(graph.toIsiAmrStringWithSources());
                    System.err.println(ex.toString());
                } catch (Exception ex) {
                    System.err.println(i);
                    System.err.println(graph.toIsiAmrStringWithSources());
                    ex.printStackTrace();
                }
            });
            
        }
        
        
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(cli.limit, TimeUnit.MINUTES);
        
        ConllSentence.writeToFile(cli.outPath+"/corpus.amconll", outCorpus);
        dictionary.writeToFile(cli.outPath+"/supertags.txt");
        
        
    }
    
    
    
}
