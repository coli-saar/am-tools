/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.NamedEntityRecognizer;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.StanfordNamedEntityRecognizer;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.UiucNamedEntityRecognizer;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.utils.Fuser;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static edu.illinois.cs.cogcomp.core.datastructures.ViewNames.NER_CONLL;

/**
 *  Create training data (amconll) in parallel.
 * @author matthias
 */
public class CreateCorpusParallel {
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus  or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/dm/40.mrp";
    
    @Parameter(names = {"--companion", "-c"}, description = "Path to companion data")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";
    
    @Parameter(names = {"--train-companion", "-tc"}, description = "Path to companion data that doesn't contain the test set but the training set")//, required = true)
    private String full_companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/output/DM/";

    @Parameter(names = {"--stanford-ner-model"}, description = "Filename of Stanford NER model english.conll.4class.distsim.crf.ser.gz")
    private String stanfordNerFilename = null;

    @Parameter(names = {"--uiuc-ner-model"}, description = "Use UIUC NER model")
    private boolean uiucNer = false;

    @Parameter(names = {"--uiuc-ner-tagset"}, description = "Tagset to use for UIUC NER tagger; options: NER_CONLL (default), NER_ONTONOTES")
    private String uiucNerTagset = NER_CONLL;

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

    private NamedEntityRecognizer makeNeRecognizer() throws IOException, ClassNotFoundException {
        NamedEntityRecognizer namedEntityRecognizer = null;
        if( stanfordNerFilename != null ) {
            namedEntityRecognizer = new StanfordNamedEntityRecognizer(new File(stanfordNerFilename));
        } else if( uiucNer ) {
            namedEntityRecognizer = new UiucNamedEntityRecognizer(uiucNerTagset);
        }
        return namedEntityRecognizer;
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException, InterruptedException, ClassNotFoundException {
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

        final NamedEntityRecognizer namedEntityRecognizer = cli.makeNeRecognizer();
        
        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
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
        // EDS needs to invoke a POS tagger, here we collect training data for that.
        List<ConlluSentence> trainingDataForTagger = ConlluSentence.readFromFile(cli.full_companion);
        
        if (cli.sort){
            pairs.sort((g1,g2) -> Integer.compare(g1.right.size(), g2.right.size()));
        }
        
        for (Pair<MRPGraph, ConlluSentence> pair : pairs){
            final int i = nextInstanceID.incValue();
            final MRPGraph graphi = pair.getLeft();
            final String id = graphi.getId();
            forkJoinPool.execute(() -> {
                try {
                     Formalism formalism;
                    if (graphi.getFramework().equals("dm")){
                        formalism = new DM();
                    } else if (graphi.getFramework().equals("psd")){
                        formalism = new PSD();
                    } else if (graphi.getFramework().equals("eds")){
                        formalism = new EDS(trainingDataForTagger);
                    } else {
                        throw new IllegalArgumentException("Formalism/Framework "+graphi.getFramework()+" not supported yet.");
                    }
                final MRPGraph preprocessed = formalism.preprocess(graphi);
                final ConlluSentence usentence = formalism.refine(pair.getRight());
                MRInstance instance = formalism.toMRInstance(usentence, preprocessed);
                try {
                        instance.checkEverythingAligned();
                } catch (Exception e){
                        e.printStackTrace();
                        problems.incValue();
                        return;
                }
                AMSignatureBuilder sigBuilder = formalism.getSignatureBuilder(instance);
                AlignmentTrackingAutomaton auto = AlignmentTrackingAutomaton.create(instance,sigBuilder, false);
                 try {
                        auto.processAllRulesBottomUp(null, cli.threadTimeout*1000);
                } catch (InterruptedException ex) {
                    System.err.println("Decomposition of graph "+graphi.getId()+" interrupted after "+cli.threadTimeout+" seconds. Will be excluded in output.");
                    interruptedGraphs.incValue();
                }
                 Tree<String> t = auto.viterbi();
                if (t != null){
                    //SGraphDrawer.draw(inst.getGraph(), "");
                    AmConllSentence sent = AmConllSentence.fromIndexedAMTerm(t, instance, supertagDictionary);
                    sent.addRanges(usentence.ranges());
                    sent.setAttr("git", AMToolsVersion.GIT_SHA);
                    sent.setAttr("id", preprocessed.getId());
                    sent.setAttr("framework", preprocessed.getFramework());
                    sent.setAttr("raw",preprocessed.getInput());
                    sent.setAttr("version", preprocessed.getVersion());
                    sent.setAttr("time", preprocessed.getTime());

                    List<String> posTags = usentence.pos();
                    sent.addPos(posTags);

                    if( namedEntityRecognizer != null ) {
                        synchronized (namedEntityRecognizer) { // not sure if NE recognizers are threadsafe
                            List<CoreLabel> tokens = Util.makeCoreLabelsForTokens(sent.words());
                            List<CoreLabel> nes = namedEntityRecognizer.tag(tokens);
                            List<String> sNes = de.up.ling.irtg.util.Util.mapToList(nes, CoreLabel::ner);
                            sent.addNEs(sNes);
                        }
                    }

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
        synchronized (outCorpus) {
            synchronized(supertagDictionary){
                 cli.write(outCorpus,supertagDictionary);
            }
        }
        
    }
    
    private void write(ArrayList<AmConllSentence> outCorpus, SupertagDictionary supertagDictionary) throws IOException{
        if (outPath != null && prefix != null){
            new File(outPath).mkdirs();
            AmConllSentence.writeToFile(outPath+"/"+prefix+".amconll", outCorpus);
            if (vocab == null){ //only write vocab if it wasn't restored.
                supertagDictionary.writeToFile(outPath+"/"+prefix+"-supertags.txt");
            }
        }
    }
        
}
    

    

