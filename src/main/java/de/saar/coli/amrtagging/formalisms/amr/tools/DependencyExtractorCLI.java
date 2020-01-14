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
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.MrpPreprocessedData;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.StanfordPreprocessedData;
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
import edu.stanford.nlp.ling.TaggedWord;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command line interface for the DependencyExtractor class; call with --help to see options.
 * @author jonas
 */
public class DependencyExtractorCLI {
    
    public static final String LITERAL_JOINER = "_";
    
    @Parameter(names = {"--corpusPath", "-c"}, description = "Path to the input corpus") //required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/100.corpus";

    @Parameter(names = {"--outPath", "-o"}, description = "Prefix for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/";
    
    @Parameter(names = {"--vocabPath", "-v"}, description = "Prefix for vocab files from a previous run that should be used here (e.g. to training vocab when doing dev/test files)")
    private String vocabPath = null;
    
    @Parameter(names = {"--posPath", "-pos"}, description = "Path to the stanford POS tagger model file english-bidirectional-distsim.tagger", required = false)
    private String posPath;
    
    @Parameter(names = {"--threads", "-t"}, description = "Number of threads over which the instances should be parallelized")
    private int numThreads = 1;
    
    @Parameter(names = {"--limit", "-li"}, description = "Number of minutes after which the process will be terminated")
    private long limit = 999999999;
    
    @Parameter(names = {"--coref", "-cr"}, description = "Set this flag to allow coref sources")
    private boolean coref = false;

    @Parameter(names = {"--companion"}, description = "Path to MRP companion data (will disable builtin tokenization and POS tagging", required = false)
    private String companionDataFile = null;

    
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
        loaderIRTG.addInterpretation("id", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));

//        if (cli.joint) {
//            loaderIRTG.addInterpretation("repalignmentp", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
//        } else {
        loaderIRTG.addInterpretation("repalignment", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
//        }
        
        Corpus corpus = Corpus.readCorpusWithStrictFormatting(new FileReader(cli.corpusPath), loaderIRTG);
        
//        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        
        PreprocessedData _preprocData = null;

        if( cli.companionDataFile != null ) {
            _preprocData = new MrpPreprocessedData(new File(cli.companionDataFile));
        } else if( cli.posPath != null ){
            _preprocData = new StanfordPreprocessedData(cli.posPath);
            ((StanfordPreprocessedData) _preprocData).readTokenizedFromCorpus(corpus);
        } else {
            System.err.println("You must specify either the MRP companion data or the Stanford POS tagging model.");
            System.exit(1);
        }

        final PreprocessedData preprocData = _preprocData; // so it can be used from the lambda expr below

        DependencyExtractor extr = (cli.vocabPath == null) ? new DependencyExtractor(cli.outPath) 
                : new DependencyExtractor(cli.outPath, cli.vocabPath);
        FileWriter posWriter = new FileWriter(cli.outPath+"pos.txt");
        Set<String> allPosTags = new HashSet<>();
        FileWriter literalWriter = new FileWriter(cli.outPath+"literal.txt");
        FileWriter idWriter = new FileWriter(cli.outPath+"graphIDs.txt");
        
        MutableInteger nextInstanceID = new MutableInteger(0);
        ForkJoinPool forkJoinPool = new ForkJoinPool(cli.numThreads);
        MutableInteger success = new MutableInteger(0);
        
        for (Instance inst : corpus) {
            final int i = nextInstanceID.incValue();//returns old value
            SGraph graph = (SGraph)inst.getInputObjects().get("repgraph");
            List<String> sent = (List)inst.getInputObjects().get("repstring");
            List<String> origSent = (List)inst.getInputObjects().get("string");
            List<String> spanmap = (List)inst.getInputObjects().get("spanmap");
            List<String> ids = (List)inst.getInputObjects().get("id");
            String id = ids.get(0);

//            if (!alBr.ready()) {
//                break;
//            }
            Set<String> lexNodes = new HashSet<>();
            //List<String> als;
//            if (cli.joint) {
//                als =(List)inst.getInputObjects().get("repalignmentp");
//            } else {
            List<String> alStrings;
            alStrings =(List)inst.getInputObjects().get("repalignment");
//            }
            if (alStrings.size() == 1 && alStrings.get(0).equals("")) {
                //System.err.println("Repaired empty alignment!");
                alStrings = new ArrayList<>();
            }

            String[] alStringArray = alStrings.toArray(new String[0]);
            List<Alignment> als;
            als = new ArrayList<>();
            for (String alString : alStringArray) {
                Alignment al = Alignment.read(alString, 0);
                lexNodes.addAll(al.lexNodes);
                als.add(al); // add to alignments
            }

            
            // Keep the alignments as Alignments and make a meaning rep instance
            MRInstance stringGraphAlignment = new MRInstance(sent, graph, als);
                       
            forkJoinPool.execute(() -> {
                try {
                    TreeAutomaton auto;
//                    if (cli.joint) {
//                        auto = JointAlignmentTrackingAutomaton.create(graph, alStrings, sent, cli.doWrap, cli.maxJointSpread);
//                    } else {
                    //auto = AlignmentTrackingAutomaton.create(graph, alStrings, sent.size(), cli.coref, (g -> AMSignatureBuilder.scoreGraphPassiveSpecial(g)));
//                    }

                    // this automaton isn't really being given graph types for the scorer. Is this a problem?
                    AMRSignatureBuilder sigBuilder = new AMRSignatureBuilder();
                    auto = AlignmentTrackingAutomaton.create(stringGraphAlignment, 
                            sigBuilder, cli.coref, (graphTypePair -> AMRSignatureBuilder.scoreGraphPassiveSpecial(graphTypePair)));
                    auto.processAllRulesBottomUp(null);

                    Tree<String> vit = auto.viterbi();
                    //System.err.println(vit);
                    if (vit != null) {
                        synchronized (success) {
                            success.incValue();
                        }
                        
                        // make POS and literal output, from original sentence, using span map
                        List<TaggedWord> origPosTags = preprocData.getPosTags(id);
                        origPosTags.stream().forEach(t -> allPosTags.add(t.tag()));
                        
                        List<String> posTags = new ArrayList<>();
                        List<String> literals = new ArrayList<>();
                        
                        for (String spanString : spanmap) {
                            Span span = new Span(spanString);
                            List<String> origWords = new ArrayList<>();
                            for (int l = span.start; l<span.end; l++) {
                                origWords.add(origSent.get(l));
                            }
                            literals.add(origWords.stream().collect(Collectors.joining(LITERAL_JOINER)));
                            posTags.add(origPosTags.get(span.start).tag());
                        }
                        
                        synchronized (extr) {
                            //TODO maybe preserve order of corpus? -- Edit: not necessary, working around that elsewhere
                            List<String> constraints = extr.tree2constraints(auto.viterbi(), lexNodes);
                            extr.writeTrainingdataFromConstraints(constraints, sent);
                            posWriter.write(posTags.stream().collect(Collectors.joining(" "))+"\n");
                            literalWriter.write(literals.stream().collect(Collectors.joining(" "))+"\n");
                            idWriter.write(id+"\n");
                        }
                    }
                    if ((i+1) % 500 == 0) {
                        synchronized (success) {
                            System.err.println("\nSuccesses: "+success.getValue()+"/"+i+"\n");
                        }
                    }
                    if (i+1 == corpus.getNumberOfInstances()) {
                        synchronized (success) {
                            System.err.println("\n**Total successful decompositions**: "+success.getValue()+"/"+corpus.getNumberOfInstances() +"\n");
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
        
        posWriter.close();
        literalWriter.close();
        idWriter.close();
        FileWriter posVocabW = new FileWriter(cli.outPath+"vocabPos.txt");
        for (String tag : allPosTags) {
            posVocabW.write(tag+"\n");
        }
        posVocabW.close();
        extr.writeVocab();
        extr.close();
        
    }
}
