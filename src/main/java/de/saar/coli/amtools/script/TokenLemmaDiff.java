/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import org.w3c.dom.NodeList;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 *  Create PSD training data.
 * @author jonas
 */
public class TokenLemmaDiff {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the primary input corpus")//, required = true)
    private String corpusPath = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";

    @Parameter(names = {"--corpus2", "-c2"}, description = "Path to the secondary input corpus")//, required = true)
    private String corpusPath2 = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.pas.sdp";

    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   

    
    public static void main(String[] args) throws IOException {
        //just getting command line args
        TokenLemmaDiff cli = new TokenLemmaDiff();
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
        
        //setup
        GraphReader2015 gr = new GraphReader2015(cli.corpusPath);
        GraphReader2015 gr2 = new GraphReader2015(cli.corpusPath2);
        Graph sdpGraph;
        Graph sdpGraph2;
        int wordCountDiffs = 0;
        int formDiffs = 0;
        int lemmaDiffs = 0;
        int senseDiffs = 0;

        Counter<Pair<String, String>> lemmaMismatchCounter = new Counter<>();
        Counter<Pair<String, String>> senseMismatchCounter = new Counter<>();
        Counter<Pair<String, String>> formCounter = new Counter<>();

        int sentCount = 0;
        while ((sdpGraph = gr.readGraph()) != null && (sdpGraph2 = gr2.readGraph()) != null){
            sentCount++;
            if (sdpGraph.getNNodes() != sdpGraph2.getNNodes()) {
                wordCountDiffs++;
            }
            List<Node> nodeList = sdpGraph.getNodes();
            List<Node> nodeList2 = sdpGraph2.getNodes();
            for (int i = 0; i< nodeList.size() && i<nodeList2.size(); i++) {
                if (!nodeList.get(i).form.equals(nodeList2.get(i).form)) {
                    formDiffs++;
                }
                formCounter.add(new Pair(nodeList.get(i).form, nodeList2.get(i).form));
                if (!nodeList.get(i).lemma.equals(nodeList2.get(i).lemma)) {
                    lemmaDiffs++;
                    lemmaMismatchCounter.add(new Pair(nodeList.get(i).lemma, nodeList2.get(i).lemma));
                }
                if (!nodeList.get(i).sense.equals(nodeList2.get(i).sense)) {
                    senseDiffs++;
                    senseMismatchCounter.add(new Pair(nodeList.get(i).sense, nodeList2.get(i).sense));
                }
            }
        }

        System.err.println("sentences analyzed: "+sentCount);
        System.err.println("sentences with different word count: "+wordCountDiffs);
        System.err.println("(unaligned) words with different form: "+formDiffs);
        System.err.println("(unaligned) words with different lemma: "+lemmaDiffs);
        System.err.println("(unaligned) words with different sense: "+senseDiffs);
        //lemmaMismatchCounter.printAllSorted();
        //senseMismatchCounter.printAllSorted();
        formCounter.printAllSorted();
    }


}
    

    

