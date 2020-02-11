/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
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
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Find and count copula in PSD graphs
 * @author jonas
 */
public class FindPSDCopula {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";

    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   

    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        //just getting command line args
        FindPSDCopula cli = new FindPSDCopula();
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
        Graph sdpGraph;
        int totalGraphs = 0;
        int totalEdges = 0;
        int totalNodes = 0;
        int totalBe = 0;
        int copula = 0;
        int copula_adj = 0;
        int copulagraphs = 0;
        int copulaadjgraphs = 0;
        List<String> adjlabels = Arrays.asList("JJ", "JRR", "JJS");  // positive / comparative / superlative adjective
        // TODO: what is a copula? only with adjective or also nouns and others allowed as PAT-arg?

        while ((sdpGraph = gr.readGraph()) != null){
            totalGraphs += 1;
            totalEdges += sdpGraph.getNEdges();
            totalNodes += sdpGraph.getNNodes();
            boolean hascopula = false;
            boolean hasadjcopula = false;
            for (Node node : sdpGraph.getNodes()) {
                if (node.lemma.equals("be")) {
                    totalBe++;
                    Set<String> outgoingLabels = getOutgoingEdgeLabels(node);
                    if (outgoingLabels.size() == 2 &&
                            outgoingLabels.contains("ACT-arg") && outgoingLabels.contains("PAT-arg")) {
                        // what about 'Parts are a tragedy'? 'Both are mining concerns'
                        copula++;  // copula if to-be lemma node with *exactly* 2 outgoing edges (one ACT, one PAT-arg)
                        hascopula = true;

                        for (Edge e: node.getOutgoingEdges()) { // check adjective is pat
                            if (e.label.equals("PAT-arg")) {
                                Node patnode = sdpGraph.getNode(e.target);
                                if (adjlabels.contains(patnode.pos)) {
                                    copula_adj += 1;
                                    hasadjcopula = true;
                                }
                            }
                        }
                    }
                }
            }
            copulagraphs += hascopula ? 1 : 0;
            copulaadjgraphs += hasadjcopula ? 1 : 0;
        }

        System.err.println(String.format("total graphs:               %10d", totalGraphs));
        System.err.println(String.format("total edges:                %10d", totalEdges));
        System.err.println(String.format("total nodes:                %10d", totalNodes));
        System.err.println(String.format("total occurrences of to be: %10d || %8.3f percent of nodes", totalBe, 100* totalBe /(float)totalNodes));
        System.err.println(String.format("detected as copula:         %10d || %8.3f percent of to-be-occ", copula, 100*copula / (float)totalBe));
        System.err.println(String.format("detected as adj copula:     %10d || %8.3f percent of to-be-occ", copula_adj, 100*copula_adj / (float)totalBe));
        System.err.println(String.format("graphs with >=1 copula:     %10d || %8.3f percent of all graphs", copulagraphs, 100*copulagraphs / (float)totalGraphs));
        System.err.println(String.format("graphs with >=1 adj copula: %10d || %8.3f percent of all graphs", copulaadjgraphs, 100*copulaadjgraphs / (float)totalGraphs));
        
    }


    private static Set<String> getOutgoingEdgeLabels(Node node) {
        List<Edge> edges = node.getOutgoingEdges();
        return edges.stream().map(edge -> edge.label).collect(Collectors.toSet());
    }

}
    

    

