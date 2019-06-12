/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConcreteAlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDConcreteSignatureBuilder;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.simple.Sentence;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import se.liu.ida.nlp.sdp.toolkit.graph.*;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.mutable.MutableInt;

/**
 *  Create PSD training data.
 * @author jonas
 */
public class CreateCorpusParallel {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "experimentData/generalized_am/SDP/PSD/toy/en.psd.sdp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "experimentData/generalized_am/SDP/PSD/toy/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "generalized";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--hours"}, description = "maximum runtime in hours (afterwards, all incomplete decompositions are canceled and the program finishes up orderly.")
    private int hours = 24;
    
    @Parameter(names = {"--threadTimeout"}, description = "maximum runtime of the tree-automaton step per thread, in seconds. Default = 600 (=10mins)")
    private int threadTimeout = 600;
    
    @Parameter(names = {"--threads", "-t"}, description = "number of threads")
    private int threads = 50;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=false;
    
   @Parameter(names = {"--sort"}, description = "Sort sentences by length")
    private boolean sort=false;
    
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
        
        
        GraphReader2015 gr = new GraphReader2015(cli.corpusPath);
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        Scorer overall = new Scorer();
        
        SGraphConverter.READABLE_NODE_LABELS = false; //if false -> node names are senses, if true -> nodenames also contain word forms
        
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        
        MutableInteger nextInstanceID = new MutableInteger(0);
        MutableInteger problems = new MutableInteger(0);
        MutableInteger interruptedGraphs = new MutableInteger(0);
        ForkJoinPool forkJoinPool = new ForkJoinPool(cli.threads);
        
        List<Graph> graphs = new ArrayList<>();
        Graph aGraph;
        while ((aGraph = gr.readGraph()) != null){
            graphs.add(aGraph);
        }
        if (cli.sort){
            graphs.sort((g1,g2) -> Integer.compare(g1.getNNodes(), g2.getNNodes()));
        }
        PSDBlobUtils blobUtils = new PSDBlobUtils();
        for (Graph sdpGraph : graphs){
            
//            if (counter % 10 == 0 && counter>0){
//                System.err.println(counter);
//                System.err.println("decomposable so far " + 100*(1.0 - (problems / (float) counter))+ "%");
//                System.err.println("Overall reconstruction F-Score: "+overall.getF1() + " and exact match "+overall.getExactMatch());
//            }
//            if (counter % 1000 == 0 && counter>0){ //every now and then write intermediate results.
//                cli.write(outCorpus, supertagDictionary);
//            }
//            counter ++;
            
//            System.err.println("Starting instance "+counter + " ("+inst.getSentence()+") ... ");
//            CpuTimeStopwatch watch = new CpuTimeStopwatch();
//            watch.record();
    
            final int i = nextInstanceID.incValue();
            final Graph sdpGraphi = sdpGraph;
            forkJoinPool.execute(() -> {
                try {
                    MRInstance inst = SGraphConverter.toSGraph(sdpGraphi);
                    //System.out.println(inst.getSentence());
                    //System.out.println(inst.getAlignments());
                    //System.out.println(inst.getGraph());
    //                System.err.println(inst.getSentence());
    //                System.err.println(inst.getGraph());
                    MRInstance modified = new MRInstance(inst.getSentence(), ConjHandler.handleConj(inst.getGraph(), blobUtils), inst.getAlignments());
                    ConcreteAlignmentSignatureBuilder sigBuilder =
                        new PSDConcreteSignatureBuilder(modified.getGraph(), modified.getAlignments(), blobUtils);
    //                System.err.println(modified.getSentence());
    //                System.err.println(modified.getGraph());
                    AlignmentTrackingAutomaton auto = ConcreteAlignmentTrackingAutomaton.create(modified,sigBuilder, false);
    //                System.err.println("Signature size: "+auto.getSignature().getMaxSymbolId());
    //                System.err.println("Signature size decomp: "+auto.getDecomp().getSignature().getMaxSymbolId());
//                    auto.processAllRulesBottomUp(null);
                    try {
                        auto.processAllRulesBottomUp(null, cli.threadTimeout*1000);
                    } catch (InterruptedException ex) {
                        System.err.println("Decomposition of graph "+i+" interrupted after "+cli.threadTimeout+" seconds. Will be excluded in output.");
                        interruptedGraphs.incValue();
                    }
                    Tree<String> t = auto.viterbi();

                    if (t != null){
                        //SGraphDrawer.draw(inst.getGraph(), "");

                        
                        ConllSentence sent = ConllSentence.fromIndexedAMTerm(t, modified, supertagDictionary);
                        sent.setAttr("id", sdpGraphi.id);
                        Sentence stanfAn = new Sentence(modified.getSentence().subList(0, modified.getSentence().size()-1)); //remove artifical root "word"

                        List<String> posTags = SGraphConverter.extractPOS(sdpGraphi);
                        posTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                        sent.addPos(posTags);

                        List<String> neTags = new ArrayList<>(stanfAn.nerTags());
                        neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                        sent.addNEs(neTags);

                        List<String> lemmata = SGraphConverter.extractLemmas(sdpGraphi);
                        lemmata.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
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
                        AMDependencyTree amdep = AMDependencyTree.fromSentence(sent);
                        //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getDelexSupertag() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
                        //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();

                        SGraph alignedGraph = amdep.evaluate(true);
                        Graph emptyCopy = new Graph(sdpGraphi.id);
                        sdpGraphi.getNodes().forEach(node -> emptyCopy.addNode(node.form, node.lemma, node.pos, false,false, ""));

                        Graph convertedGraph = SGraphConverter.toSDPGraph(alignedGraph, emptyCopy);
                        Scorer scorer = new Scorer();
                        scorer.update(sdpGraphi, convertedGraph);
                        overall.update(sdpGraphi,convertedGraph);


    //                    if (scorer.getF1() != 1.0 ) { //|| scorer.getCorePredicationsF1() != 1.0 || scorer.getSemanticFramesF1() != 1.0){
    //                        System.err.println("Reconstructing SDP Graph didn't work completely for: "+inst.getSentence());
    //                        System.err.println("Precision "+scorer.getPrecision()+" Recall "+scorer.getRecall()+" corePredications F "+scorer.getCorePredicationsF1()+" semantic frames F "+scorer.getSemanticFramesF1());
    //                    } 
                   } else {
                        problems.incValue();
    //                    problems ++;
    //                    System.err.println("not decomposable " + inst.getSentence());
    //                    System.err.println(inst.getGraph());
    //                    SGraphDrawer.draw(modified.getGraph(), "");
    //                    if (cli.debug){
    //                        for (Alignment al : inst.getAlignments()){
    //                            System.err.println(inst.getSentence().get(al.span.start));
    //                            System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
    //                        }
    //                    }
    //                    if (problems > 1){ //ignore the first problems
    //                        //SGraphDrawer.draw(inst.getGraph(), "");
    //                        //break;
    //                    }
                    }
                } catch (Exception ex){
                    problems.incValue();
                    System.err.println("Ignoring an exception in graph "+i+":");
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
            ConllSentence.writeFile(outPath+"/"+prefix+".amconll", outCorpus);
            if (vocab == null){ //only write vocab if it wasn't restored.
                supertagDictionary.writeToFile(outPath+"/"+prefix+"-supertags.txt");
            }
        }
    }
        
 
    private static SGraph getReadableGraph(MRInstance inst) {
        SGraph ret = new SGraph();
        for (GraphNode node : inst.getGraph().getGraph().vertexSet()) {
            String label = node.getLabel();
            if (node.getName().startsWith("i_")) {
                int i = Integer.parseInt(node.getName().substring(2))-1;
                label = inst.getSentence().get(i)+"_"+String.valueOf(i);
            }
            ret.addNode(node.getName(), label);
        }
        for (GraphEdge edge : inst.getGraph().getGraph().edgeSet()) {
            ret.addEdge(ret.getNode(edge.getSource().getName()), ret.getNode(edge.getTarget().getName()), edge.getLabel());
        }
        return ret;
    }
}
    

    

