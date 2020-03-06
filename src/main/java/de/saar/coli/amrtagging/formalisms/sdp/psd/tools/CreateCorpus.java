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
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
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
import java.util.stream.Collectors;

/**
 *  Create PSD training data.
 * @author jonas
 */
public class CreateCorpus {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "experimentData/generalized_am/SDP/PSD/toy/en.psd.sdp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "experimentData/generalized_am/SDP/PSD/toy/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "generalized";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=false;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    private static void printEdges(Graph sdpGraph){
        for (Edge e : sdpGraph.getEdges()){
            System.out.println(e.source+" --"+e.label+"--> "+e.target);
        }
    }
    
//    private static void printEdgeDiff(Graph gold, Graph recon){
//        Set<Pair<Integer,Pair<Integer,String>>> g = gold.getEdges().stream().map(e -> new Pair<Integer,Pair<Integer,String>>(e.source, new Pair(e.target,e.label))).collect(Collectors.toSet());
//        Set<Pair<Integer,Pair<Integer,String>>> r = recon.getEdges().stream().map(e -> new Pair<Integer,Pair<Integer,String>>(e.source, new Pair(e.target,e.label))).collect(Collectors.toSet());
//        Set<Pair<Integer,Pair<Integer,String>>>  g2 = r.stream().collect(Collectors.toSet());
//        
//        g2.removeAll(g); //g2 := r - g
//        g.removeAll(r); //g := g - r
//        
//        if (!g.isEmpty()){
//            System.out.println("Reconstruction did not include:");
//            for (Pair<Integer,Pair<Integer,String>> e :g){
//                System.err.println(e.left+"-"+e.right.right+"->"+e.right.left);
//            }
//        }
//        if (!g2.isEmpty()){
//            System.out.println("Gold did not include:");
//            for (Pair<Integer,Pair<Integer,String>> e :g2){
//                System.err.println(e.left+"-"+e.right.right+"->"+e.right.left);
//            }
//        }
//        
//    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
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
        
        
        GraphReader2015 gr = new GraphReader2015(cli.corpusPath);
        Graph sdpGraph;
        int counter = 0;
        int problems = 0;
        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        Scorer overall = new Scorer();
        
        SGraphConverter.READABLE_NODE_LABELS = true; //if false -> node names are senses, if true -> nodenames also contain word forms
        
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        PSDBlobUtils blobUtils = new PSDBlobUtils();
        while ((sdpGraph = gr.readGraph()) != null){
            if (counter % 10 == 0 && counter>0){
                System.err.println(counter);
                System.err.println("decomposable so far " + 100*(1.0 - (problems / (float) counter))+ "%");
                System.err.println("Overall reconstruction F-Score: "+overall.getF1() + " and exact match "+overall.getExactMatch());
            }
            if (counter % 1000 == 0 && counter>0){ //every now and then write intermediate results.
                cli.write(outCorpus, supertagDictionary);
            }
            counter ++;
            
//            System.err.println("Starting instance "+counter + " ("+inst.getSentence()+") ... ");
//            CpuTimeStopwatch watch = new CpuTimeStopwatch();
//            watch.record();
            try {
                MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
                System.err.println(getReadableGraph(inst).toIsiAmrString());
//                SGraphDrawer.draw(getReadableGraph(inst), "original: "+inst.getSentence().stream().collect(Collectors.joining(" ")));
                //System.out.println(inst.getSentence());
                //System.out.println(inst.getAlignments());
                //System.out.println(inst.getGraph());
//                System.err.println(inst.getSentence());
//                System.err.println(inst.getGraph());
                MRInstance modified = new MRInstance(inst.getSentence(), ConjHandler.handleConj(inst.getGraph(), blobUtils), inst.getAlignments());
//                System.err.println(modified.getSentence());
//                System.err.println(modified.getGraph());
                ConcreteAlignmentSignatureBuilder sigBuilder =
                    new PSDConcreteSignatureBuilder(modified.getGraph(), modified.getAlignments(), blobUtils);
                AlignmentTrackingAutomaton auto = ConcreteAlignmentTrackingAutomaton.create(modified,sigBuilder, false);
//                System.err.println("Signature size: "+auto.getSignature().getMaxSymbolId());
//                System.err.println("Signature size decomp: "+auto.getDecomp().getSignature().getMaxSymbolId());
                auto.processAllRulesBottomUp(null);
                Tree<String> t = auto.viterbi();

                if (t != null){
                    
                    AmConllSentence sent = AmConllSentence.fromIndexedAMTerm(t, modified, supertagDictionary);
                    sent.setAttr("id", sdpGraph.id);
                    Sentence stanfAn = new Sentence(modified.getSentence().subList(0, modified.getSentence().size()-1)); //remove artifical root "word"

                    List<String> posTags = SGraphConverter.extractPOS(sdpGraph);
                    posTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addPos(posTags);

                    List<String> neTags = new ArrayList<>(stanfAn.nerTags());
                    neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addNEs(neTags);

                    List<String> lemmata = SGraphConverter.extractLemmas(sdpGraph);
                    lemmata.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addLemmas(lemmata);

                    outCorpus.add(sent);
                    AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(sent);
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getDelexSupertag() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();

                    SGraph alignedGraph = ConjHandler.restoreConj(amdep.evaluate(true), (PSDBlobUtils)sigBuilder.getBlobUtils());
                    Graph emptyCopy = new Graph(sdpGraph.id);
                    sdpGraph.getNodes().forEach(node -> emptyCopy.addNode(node.form, node.lemma, node.pos, false,false, ""));

                    Graph convertedGraph = SGraphConverter.toSDPGraph(alignedGraph, emptyCopy);
                    Scorer scorer = new Scorer();
                    scorer.update(sdpGraph, convertedGraph);
                    overall.update(sdpGraph, convertedGraph);
                    
                    //SGraphDrawer.draw(inst.getGraph(), "");
//                    SGraphDrawer.draw(getReadableGraph(modified), "OK: "+inst.getSentence().stream().collect(Collectors.joining(" ")));
                    
                    if (scorer.getF1() < 0.999999999 ) { //|| scorer.getCorePredicationsF1() != 1.0 || scorer.getSemanticFramesF1() != 1.0){
//                        SGraphDrawer.draw(getReadableGraph(new MRInstance(inst.getSentence(), alignedGraph, inst.getAlignments())), "Our output");
                        //printEdgeDiff(sdpGraph, convertedGraph);
                        //System.err.println();
                        //System.err.println("Original");
                        //printEdges(sdpGraph);
                        //SGraphDrawer.draw(inst.getGraph(), "Original graph as s-graph");
                        //System.err.println("Converted");
                        //printEdges(convertedGraph);
                        //SGraphDrawer.draw(alignedGraph, "Our output");
                        //System.err.println("original: "+inst.getGraph().toIsiAmrStringWithSources());
                        //System.err.println("convertd: " + alignedGraph.toIsiAmrStringWithSources());
//                        System.err.println("Reconstructing SDP Graph didn't work completely for: "+inst.getSentence());
                        
//                        System.err.println("Precision "+scorer.getPrecision()+" Recall "+scorer.getRecall()+" corePredications F "+scorer.getCorePredicationsF1()+" semantic frames F "+scorer.getSemanticFramesF1());
                    } 
               } else {
                    problems ++;
//                    System.err.println("not decomposable " + inst.getSentence());
//                    System.err.println(inst.getGraph());
                    SGraphDrawer.draw(getReadableGraph(modified), "failed: "+inst.getSentence().stream().collect(Collectors.joining(" ")));
                    if (cli.debug){
                        for (Alignment al : inst.getAlignments()){
                            System.err.println(inst.getSentence().get(al.span.start));
                            System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                        }
                    }
                    if (problems > 1){ //ignore the first problems
                        //SGraphDrawer.draw(inst.getGraph(), "");
                        //break;
                    }
                }
            } catch (Exception ex){
                System.err.println("Ignoring an exception:");
                ex.printStackTrace();
            }
//            watch.record();
//            System.err.println(watch.getMillisecondsBefore(1));
        }
        System.err.println("ok: "+(counter-problems));
        System.err.println("total: "+counter);
        System.err.println("i.e. " + 100*(1.0 - (problems / (float) counter))+ "%");
        System.err.println("Total reconstruction F1 "+overall.getF1()); 
        //alter Code:
        // Overall reconstruction F-Score: 0.9877037460680583 and exact match 0.9035087719298246
        //neuer Code:
        //Overall reconstruction F-Score: 0.9510932105868815 and exact match 0.6491228070175439
        //Bug? gefixt: Overall reconstruction F-Score: 0.9877037460680583 and exact match 0.9035087719298246
        //Koordination gar nicht wiederherstellen:
        //Overall reconstruction F-Score: 0.9574775817182528 and exact match 0.6491228070175439
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
    
        
}
    

    

