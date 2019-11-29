/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import static de.saar.coli.amrtagging.AlignedAMDependencyTree.ALIGNED_SGRAPH_SEP;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.IOException;

import se.liu.ida.nlp.sdp.toolkit.graph.*;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;

/**
 *  Checks how well preprocessing (conjunctions!) in PSD works.
 * @author matthias
 */
public class CheckPreprocessing {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "../../data/corpora/semDep/toy/en.psd.sdp";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=false;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{      
        CheckPreprocessing cli = new CheckPreprocessing();
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
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        Scorer overall = new Scorer();
        int isomorphic = 0;
        int exceptions = 0;
        
        SGraphConverter.READABLE_NODE_LABELS = true; //if false -> node names are senses, if true -> nodenames also contain word forms
        
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        while ((sdpGraph = gr.readGraph()) != null){
            if (counter % 10 == 0 && counter>0){
                System.err.println(counter);
                System.err.println("Overall reconstruction F-Score: "+overall.getF1() + " and exact match "+overall.getExactMatch());
            }
            counter ++;
            MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
            //System.err.println(inst.getGraph());
            AMRBlobUtils blobUtils = new PSDBlobUtils();
            
            Scorer currentScorer = new Scorer();
            Graph rawGraph = new Graph(sdpGraph.id);
            for (Node n: sdpGraph.getNodes()){
                rawGraph.addNode(n.form, n.lemma, n.pos, false, false, "_");
            }
            try {
                SGraph modified = ConjHandler.handleConj(inst.getGraph(), (PSDBlobUtils)blobUtils);
                SGraph restored = ConjHandler.restoreConj(modified, (PSDBlobUtils)blobUtils);
                SGraph copyOfRestored = new SGraph();
                for (GraphNode n :restored.getGraph().vertexSet()){
                    if (!n.getLabel().equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)){
                        copyOfRestored.addNode(n.getName(), n.getName().split("_")[1]+ALIGNED_SGRAPH_SEP+n.getName()+ALIGNED_SGRAPH_SEP+n.getLabel());
                    } else copyOfRestored.addNode(n.getName(), n.getLabel());
                    for (String source : restored.getSourcesAtNode(n.getName())){
                        copyOfRestored.addSource(source, n.getName());
                    }
                }
                for (GraphEdge e : restored.getGraph().edgeSet()){
                    copyOfRestored.addEdge(copyOfRestored.getNode(e.getSource().getName()), copyOfRestored.getNode(e.getTarget().getName()), e.getLabel());
                }
                Graph output = SGraphConverter.toSDPGraph(copyOfRestored, rawGraph);
               
                currentScorer.update(sdpGraph, output);
                overall.update(sdpGraph, output);
                double f = currentScorer.getF1();
                double fp = currentScorer.getCorePredicationsF1();
                double fm = currentScorer.getSemanticFramesF1();
                if (f != 1.0 || fp != 1.0 || fm != 1.0){
                    System.err.println(f +" " + fp + " "+fm);
                    System.err.println(inst.getGraph());
                    System.err.println(modified);
                    System.err.println(restored);
                    inst.getGraph().setEqualsMeansIsomorphy(true);
                    System.err.println(inst.getGraph().equals(restored)? "Matches!" : "does not match");
                    if (inst.getGraph().equals(restored)) {
                        isomorphic++;
                    } 
                    // break;
                } else {
                    isomorphic++;
                }
            } catch (IllegalArgumentException ex){
                System.err.println(sdpGraph.id);
                ex.printStackTrace();
                exceptions++;
            }
           
            

        
        }
        System.err.println(overall.getF1());
        System.err.println(overall.getExactMatch());
        System.err.println("isomorphic: "+isomorphic);
        System.err.println("Exceptions "+exceptions);
    }
        
        
 
        
}
    

    

