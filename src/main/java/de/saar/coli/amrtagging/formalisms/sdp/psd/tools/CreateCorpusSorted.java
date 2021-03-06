/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDConcreteSignatureBuilder;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *  Create PSD training data.
 * @author jonas
 */
public class CreateCorpusSorted {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/meine_Daten/PSD-toy/toy2.sdp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/meine_Daten/PSD-toy/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "generalized";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;

    @Parameter(names = {"--legacyACL19"}, description = "Uses legacy version of debugging, compatible with our ACL 2019 paper")
    private boolean legacyACL19=false;

    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=false;
    
    @Parameter(names = {"--timeout"}, description = "Seconds for timeout for a single sentence")
    private int timeout = 120;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    private static void printEdges(Graph sdpGraph){
        for (Edge e : sdpGraph.getEdges()){
            System.out.println(e.source+" --"+e.label+"--> "+e.target);
        }
    }


    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException, InterruptedException, ExecutionException{
        CreateCorpusSorted cli = new CreateCorpusSorted();
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
        
        int counter = 0;
        int problems = 0;
        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        Scorer overall = new Scorer();
        
        SGraphConverter.READABLE_NODE_LABELS = false; //if false -> node names are senses, if true -> nodenames also contain word forms
        
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        List<Graph> graphs = new ArrayList<>();
        Graph aGraph;
        while ((aGraph = gr.readGraph()) != null){
            graphs.add(aGraph);
        }
        graphs.sort((g1,g2) -> Integer.compare(g1.getNNodes(), g2.getNNodes()));
        for (Graph sdpGraph : graphs){
            if (counter % 10 == 0 && counter>0){
                System.err.println(counter);
            }
            if (counter % 10 == 0 && counter>0){ //every now and then write intermediate results.
                cli.write(outCorpus, supertagDictionary);
            }
            counter ++;
            
            MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<AmConllSentence> future = executor.submit(new Task(inst,sdpGraph, supertagDictionary, cli.legacyACL19));

            try {
                AmConllSentence o = future.get(cli.timeout, TimeUnit.SECONDS);
                if (o != null){
                    outCorpus.add(o);
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                System.err.println("Skipping this sentence "+inst.getSentence());
            }

            executor.shutdownNow();
        }
        cli.write(outCorpus,supertagDictionary);
        
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
    
    static class Task implements Callable<AmConllSentence> {
        MRInstance inst;
        Graph sdp;
        SupertagDictionary supertagDictionary;
        boolean legacyACL19;
        public Task(MRInstance inst,Graph sdpGraph, SupertagDictionary dict, boolean legacyACL19){
            this.inst = inst;
            this.sdp = sdpGraph;
            supertagDictionary = dict;
            this.legacyACL19 = legacyACL19;
        }
        @Override
        public AmConllSentence call() throws Exception {
            try {
                ConcreteAlignmentSignatureBuilder sigBuilder =
                    new PSDConcreteSignatureBuilder(inst.getGraph(), inst.getAlignments(), new PSDBlobUtils());

                MRInstance modified = new MRInstance(inst.getSentence(), ConjHandler.handleConj(inst.getGraph(), (PSDBlobUtils)sigBuilder.getBlobUtils(), legacyACL19), inst.getAlignments());

                AlignmentTrackingAutomaton auto = ConcreteAlignmentTrackingAutomaton.create(modified,sigBuilder, false);
                auto.processAllRulesBottomUp(null);
                Tree<String> t = auto.viterbi();

                if (t != null){
                    AmConllSentence sent = AmConllSentence.fromIndexedAMTerm(t, modified, supertagDictionary);
                    sent.setAttr("id", sdp.id);
                    sent.setAttr("git", AMToolsVersion.GIT_SHA);
                    sent.setAttr("framework", "psd");
                    Sentence stanfAn = new Sentence(modified.getSentence().subList(0, modified.getSentence().size()-1)); //remove artifical root "word"

                    List<String> posTags = SGraphConverter.extractPOS(sdp);
                    posTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addPos(posTags);

                    List<String> neTags = new ArrayList<>(stanfAn.nerTags());
                    neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addNEs(neTags);

                    List<String> lemmata = SGraphConverter.extractLemmas(sdp);
                    lemmata.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addLemmas(lemmata);

                    return sent;

               }
            } catch (Exception ex){
                System.err.println("Ignoring an exception:");
                ex.printStackTrace();
            }
            return null;
        }
    }
        
    
        
}
