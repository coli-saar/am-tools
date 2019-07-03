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
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.GraphvizUtils;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.MRPInputCodec;
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.utils.Fuser;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates amconll corpus from MRP data.
 * 
 * @author matthias
 */
public class CreateCorpus {
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus  or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/eds/40.mrp";
    
    @Parameter(names = {"--companion", "-c"}, description = "Path to companion data.")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";
    
    @Parameter(names = {"--train-companion", "-tc"}, description = "Path to companion data that doesn't contain the test set but the training set")//, required = true)
    private String full_companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/output/EDS/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "bla";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--timeout"}, description = "maximum runtime of the tree-automaton step per thread, in seconds. Default = 120 (=2 mins)")
    private int timeout = 120;
    
    @Parameter(names = {"--sort"}, description = "Sort sentences by length")
    private boolean sort=false;
        
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=true;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParserException{      
        CreateCorpus cli = new CreateCorpus();
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
        
       
        int counter = 0;
        int problems = 0;
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        Reader fr = new FileReader(cli.corpusPath);
        Reader sentReader = new FileReader(cli.companion);
        List<Pair<MRPGraph, ConlluSentence>> pairs = Fuser.fuse(fr, sentReader);
        
        if (cli.sort){
            pairs.sort((g1,g2) -> Integer.compare(g1.right.size(), g2.right.size()));
        }
        // EDS needs to invoke a POS tagger, here we collect training data for that.
        List<ConlluSentence> trainingDataForTagger = ConlluSentence.readFromFile(cli.full_companion);
        
        for (Pair<MRPGraph, ConlluSentence> pair : pairs){
            MRPGraph mrpGraph = pair.getLeft();
            ConlluSentence usentence = pair.getRight();
            if (counter % 10 == 0 && counter>0){
                System.err.println(counter);
                System.err.println("decomposable so far " + 100*(1.0 - (problems / (float) counter))+ "%");
            }
            if (counter % 1000 == 0 && counter>0){ //every now and then write intermediate results.
                cli.write(outCorpus, supertagDictionary);
            }
            counter ++;
            Formalism formalism;
            if (mrpGraph.getFramework().equals("dm")){
                formalism = new DM();
            } else if (mrpGraph.getFramework().equals("psd")){
                formalism = new PSD();
            } else if (mrpGraph.getFramework().equals("eds")){
                formalism = new EDS(trainingDataForTagger);
            } else {
                throw new IllegalArgumentException("Formalism/Framework "+mrpGraph.getFramework()+" not supported yet.");
            }

            MRPGraph preprocessed = formalism.preprocess(mrpGraph);
            usentence = formalism.refine(usentence);
            MRInstance instance;
            try {
                instance = formalism.toMRInstance(usentence, preprocessed);
            } catch (Exception e){
                System.err.println("Could not create MRInstance for "+mrpGraph.getId());
                problems++;
                e.printStackTrace();
                continue;
            }
            //System.out.println(GraphvizUtils.simpleAlignViz(instance, true));
            try {
                instance.checkEverythingAligned();
            } catch (Exception e){
                e.printStackTrace();
                problems++;
                continue;
            }
            System.out.println(mrpGraph.getId());
            AMSignatureBuilder sigBuilder = formalism.getSignatureBuilder(instance);
            try {
                AlignmentTrackingAutomaton auto;
                try {
                     auto = formalism.getAlignmentTrackingAutomaton(instance);
                } catch (Exception ex){
                    System.err.println("Ignoring:");
                    ex.printStackTrace();
                    problems++;
                    continue;
                }
                
                try {
                    auto.processAllRulesBottomUp(null, cli.timeout*1000);
                } catch (InterruptedException ex) {
                    System.err.println("Decomposition of graph "+mrpGraph.getId()+" interrupted after "+cli.timeout+" seconds. Will be excluded in output.");
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
                    formalism.refineDelex(sent);
                    outCorpus.add(sent);
                    //AMDependencyTree amdep = AMDependencyTree.fromSentence(sent);
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getDelexSupertag() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();

                    //SGraph alignedGraph = amdep.evaluate(true);
                } else {
                    problems ++;
                    if (cli.debug){
                        System.err.println("id "+mrpGraph.getId());
                        System.err.println(instance.getGraph());
                        System.err.println("not decomposable " + instance.getSentence());
                        for (Alignment al : instance.getAlignments()){
                            System.err.println(instance.getSentence().get(al.span.start));
                            System.err.println(sigBuilder.getConstantsForAlignment(al, instance.getGraph(), false));
                        }
                        System.err.println(GraphvizUtils.simpleAlignViz(instance, true));
                        System.err.println("=====end not decomposable=====");
                    } else {
                        System.err.println("not decomposable "+mrpGraph.getId());
                    }
                }
            } catch (Exception ex){
               System.err.println("Ignoring an exception:");
               System.err.println("id "+mrpGraph.getId());
               System.err.println(instance.getSentence());
               ex.printStackTrace();
               problems++;
               if (cli.debug){
                       for (Alignment al : instance.getAlignments()){
                           System.err.println(instance.getSentence().get(al.span.start));
                           try {
                               System.err.println(sigBuilder.getConstantsForAlignment(al, instance.getGraph(), false));
                           } catch (IllegalArgumentException ex2){
                               System.err.println("[]"); //print empty list
                           } catch (Exception e){
                                System.err.println(e.getMessage());
                            }

                       }
                       System.err.println(GraphvizUtils.simpleAlignViz(instance, true));
                       System.err.println("=====end not decomposable=====");
               }
            }
        }
        System.err.println("ok: "+(counter-problems));
        System.err.println("total: "+counter);
        System.err.println("i.e. " + 100*(1.0 - (problems / (float) counter))+ "%");
        cli.write(outCorpus,supertagDictionary);
        
    }
        private void write(ArrayList<ConllSentence> outCorpus, SupertagDictionary supertagDictionary) throws IOException{
            if (outPath != null && prefix != null){
                ConllSentence.writeToFile(outPath+"/"+prefix+".amconll", outCorpus);
                if (vocab == null){ //only write vocab if it wasn't restored.
                    supertagDictionary.writeToFile(outPath+"/"+prefix+"-supertags.txt");
                }
            }
        }
        
    
}
