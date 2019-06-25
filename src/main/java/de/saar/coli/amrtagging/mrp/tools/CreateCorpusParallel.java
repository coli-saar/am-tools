/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.utils.Fuser;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 *  Create training data (amconll) in parallel.
 * @author matthias
 */
public class CreateCorpusParallel {
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus  or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/dm/40.mrp";
    
    @Parameter(names = {"--companion", "-c"}, description = "Path to companion data")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/output/DM/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "generalized";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--hours"}, description = "maximum runtime in hours (afterwards, all incomplete decompositions are canceled and the program finishes up orderly.")
    private int hours = 24;
    
    @Parameter(names = {"--threadTimeout"}, description = "maximum runtime of the tree-automaton step per thread, in seconds. Default = 600 (=10mins)")
    private int threadTimeout = 600;
    
    @Parameter(names = {"--threads", "-t"}, description = "number of threads")
    private int threads = 1;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=false;
    
   @Parameter(names = {"--sort"}, description = "Sort sentences by length")
    private boolean sort=true;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException, InterruptedException{      
        CreateCorpusParallel cli = new CreateCorpusParallel();
        JCommander commander = new JCommander(cli);

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
        
        
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        
        MutableInteger nextInstanceID = new MutableInteger(0);
        MutableInteger problems = new MutableInteger(0);
        MutableInteger interruptedGraphs = new MutableInteger(0);
        ForkJoinPool forkJoinPool = new ForkJoinPool(cli.threads);
        
        Reader fr = new FileReader(cli.corpusPath);
        Reader sentReader = new FileReader(cli.companion);
        List<Pair<MRPGraph, ConlluSentence>> pairs = Fuser.fuse(fr, sentReader);
        
        if (cli.sort){
            pairs.sort((g1,g2) -> Integer.compare(g1.right.size(), g2.right.size()));
        }
        
        for (Pair<MRPGraph, ConlluSentence> pair : pairs){
            final int i = nextInstanceID.incValue();
            final MRPGraph graphi = pair.getLeft();
            final String id = graphi.getId();
            final ConlluSentence usentence = pair.getRight();
            forkJoinPool.execute(() -> {
                try {
                     Formalism formalism;
                    if (graphi.getFramework().equals("dm")){
                        formalism = new DM();
                    } else if (graphi.getFramework().equals("psd")){
                        formalism = new PSD();
                    } else {
                        throw new IllegalArgumentException("Formalism/Framework "+graphi.getFramework()+" not supported yet.");
                    }
                final MRPGraph preprocessed = formalism.preprocess(graphi);
                MRInstance instance = formalism.toMRInstance(usentence, preprocessed);
                AMSignatureBuilder sigBuilder = formalism.getSignatureBuilder(instance);
                AlignmentTrackingAutomaton auto = AlignmentTrackingAutomaton.create(instance,sigBuilder, false);
                 try {
                        auto.processAllRulesBottomUp(null, cli.threadTimeout*1000);
                } catch (InterruptedException ex) {
                    System.err.println("Decomposition of graph "+i+" interrupted after "+cli.threadTimeout+" seconds. Will be excluded in output.");
                    interruptedGraphs.incValue();
                }
                 Tree<String> t = auto.viterbi();
                if (t != null){
                    //SGraphDrawer.draw(inst.getGraph(), "");
                    ConllSentence sent = ConllSentence.fromIndexedAMTerm(t, instance, supertagDictionary);
                    sent.addRanges(usentence.ranges());
                    sent.setAttr("id", preprocessed.getId());
                    sent.setAttr("framework", preprocessed.getFramework());
                    sent.setAttr("raw",preprocessed.getInput());
                    sent.setAttr("version", preprocessed.getVersion());
                    sent.setAttr("time", preprocessed.getTime());

                    List<String> posTags = usentence.pos();
                    sent.addPos(posTags);


                    //TODO: NER

                    List<String> lemmata = usentence.lemmas();
                    sent.addLemmas(lemmata);

                    synchronized (outCorpus) {
                        outCorpus.add(sent);
                        if (i % 100 == 0){
                            System.err.println(i);
                        }
                        if (i % 10 == 0){
                            synchronized(supertagDictionary){
                                cli.write(outCorpus, supertagDictionary);
                            }
                        }
                    }
                       
                   } else {
                        System.err.println("Problem with "+id);
                        problems.incValue();
                    }
                } catch (Exception ex){
                    problems.incValue();
                    System.err.println("Ignoring an exception in graph "+id+":");
                    ex.printStackTrace();
                }
            });
//            watch.record();
//            System.err.println(watch.getMillisecondsBefore(1));
        }
        
        
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(cli.hours, TimeUnit.HOURS);
        
        int counter = nextInstanceID.getValue();
        System.err.println("ok: "+(counter-problems.getValue()));
        System.err.println("interrupted: "+interruptedGraphs.getValue());
        System.err.println("total: "+counter);
        System.err.println("i.e. " + 100*(1.0 - (problems.getValue() / (float) counter))+ "%");
        cli.write(outCorpus,supertagDictionary);
        
    }
    
    private void write(ArrayList<ConllSentence> outCorpus, SupertagDictionary supertagDictionary) throws IOException{
        if (outPath != null && prefix != null){
            new File(outPath).mkdirs();
            ConllSentence.writeToFile(outPath+"/"+prefix+".amconll", outCorpus);
            if (vocab == null){ //only write vocab if it wasn't restored.
                supertagDictionary.writeToFile(outPath+"/"+prefix+"-supertags.txt");
            }
        }
    }
        
}
    

    
