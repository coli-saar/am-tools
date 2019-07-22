/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.dm.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.simple.Sentence;
import java.io.FileNotFoundException;
import java.io.IOException;

import se.liu.ida.nlp.sdp.toolkit.graph.*;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;


import java.util.ArrayList;
import java.util.List;

/**
 *  Create DM training data.
 * @author matthias
 */
public class CreateCorpusParallel {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof", required = true)
    private String corpusPath ;

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath;
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "train";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode")
    private boolean debug=false;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
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
        
//        int counter = 0;
//        int problems = 0;
        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        Scorer overall = new Scorer();
        
        SGraphConverter.READABLE_NODE_LABELS = false; //if false -> node names are senses, if true -> nodenames also contain word forms
        
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        ArrayList<Graph> inputs = new ArrayList<>();
        Graph anSdpGraph;
        while ((anSdpGraph = gr.readGraph()) != null){
         inputs.add(anSdpGraph);
        }
        inputs.stream().parallel().forEach(sdpGraph -> {
//            if (counter % 10 == 0 && counter>0){
//                System.err.println(counter);
//                System.err.println("decomposable so far " + 100*(1.0 - (problems / (float) counter))+ "%");
//                System.err.println("Overall reconstruction F-Score: "+overall.getF1() + " and exact match "+overall.getExactMatch());
//            }
//            if (counter % 1000 == 0 && counter>0){ //every now and then write intermediate results.
//                cli.write(outCorpus, supertagDictionary);
//            }
//            counter ++;
            MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
            //System.out.println(inst.getSentence());
            //System.out.println(inst.getAlignments());
            //System.out.println(inst.getGraph());
            AMRSignatureBuilder sigBuilder = new AMRSignatureBuilder();
            sigBuilder.blobUtils = new DMBlobUtils();
            try {
                AlignmentTrackingAutomaton auto = AlignmentTrackingAutomaton.create(inst,sigBuilder, false);
                auto.processAllRulesBottomUp(null);
                Tree<String> t = auto.viterbi();

                if (t != null){
                    //SGraphDrawer.draw(inst.getGraph(), "");
                    AmConllSentence sent = AmConllSentence.fromIndexedAMTerm(t, inst, supertagDictionary);
                    sent.setAttr("id", sdpGraph.id);
                    Sentence stanfAn = new Sentence(inst.getSentence().subList(0, inst.getSentence().size()-1)); //remove artifical root "word"

                    List<String> posTags = SGraphConverter.extractPOS(sdpGraph);
                    posTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addPos(posTags);

                    List<String> neTags = new ArrayList<>(stanfAn.nerTags());
                    neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addNEs(neTags);

                    List<String> lemmata = SGraphConverter.extractLemmas(sdpGraph);
                    lemmata.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    sent.addLemmas(lemmata);
                    synchronized(outCorpus){
                        outCorpus.add(sent);
                        if (outCorpus.size() % 10 == 0){
                            System.err.println(outCorpus.size());
                        }
                        if (outCorpus.size() % 1000 == 0){
                            synchronized(supertagDictionary){
                                cli.write(outCorpus, supertagDictionary);
                            }
                        }
                    }
                    
//                    AMDependencyTree amdep = AMDependencyTree.fromSentence(sent);
//                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getDelexSupertag() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
//                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
//
//                    SGraph alignedGraph = amdep.evaluate(true);
//                    Graph emptyCopy = new Graph(sdpGraph.id);
//                    sdpGraph.getNodes().forEach(node -> emptyCopy.addNode(node.form, node.lemma, node.pos, false,false, ""));
//
//                    Graph convertedGraph = SGraphConverter.toSDPGraph(alignedGraph, emptyCopy);
//                    Scorer scorer = new Scorer();
//                    scorer.update(sdpGraph, convertedGraph);
//                    overall.update(sdpGraph,convertedGraph);
//                    if (scorer.getF1() != 1.0 ) { //|| scorer.getCorePredicationsF1() != 1.0 || scorer.getSemanticFramesF1() != 1.0){
//                        System.err.println("Reconstructing SDP Graph didn't work completely for: "+inst.getSentence());
//                        System.err.println("Precision "+scorer.getPrecision()+" Recall "+scorer.getRecall()+" corePredications F "+scorer.getCorePredicationsF1()+" semantic frames F "+scorer.getSemanticFramesF1());
//                    }
                } else {
                    System.err.println("not decomposable " + inst.getSentence());
                    if (cli.debug){
                        for (Alignment al : inst.getAlignments()){
                            System.err.println(inst.getSentence().get(al.span.start));
                            System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                        }
                    }
                }
            } catch (Exception ex){
                System.err.println("Ignoring an exception:");
                ex.printStackTrace();
            }
        });
        System.err.println("ok: "+outCorpus.size());
        System.err.println("total: "+inputs.size());
        System.err.println("i.e. " + 100*(1.0 - ((float)outCorpus.size() / (float)inputs.size()))+ "%");
        cli.write(outCorpus,supertagDictionary);
        
    }
        private void write(ArrayList<AmConllSentence> outCorpus, SupertagDictionary supertagDictionary) throws IOException{
            if (outPath != null && prefix != null){
                AmConllSentence.writeToFile(outPath+"/"+prefix+".amconll", outCorpus);
                if (vocab == null){ //only write vocab if it wasn't restored.
                    supertagDictionary.writeToFile(outPath+"/"+prefix+"-supertags.txt");
                }
            }
        }
        
 
        
}
    

    

