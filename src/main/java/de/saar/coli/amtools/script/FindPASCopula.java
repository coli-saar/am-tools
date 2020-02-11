/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Find and count copula in PAS graphs
 * @author pia
 */
public class FindPASCopula {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.pas.sdp";

    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   

    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        //just getting command line args
        FindPASCopula cli = new FindPASCopula();
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
        int almost_copula = 0;
        int almost_adj_copula = 0;
        int real_adj_copula = 0;
        int almost_copulagraphs = 0;
        int almost_adj_copula_graphs = 0;
        int real_adj_copula_graphs = 0;
        Counter<String> pat_arg_pos_counter = new Counter<>();
        List<String> adjlabels = Arrays.asList("JJ", "JRR", "JJS");  // positive / comparative / superlative adjective
        // TODO: what is a copula? only with adjective or also nouns and others allowed as 2nd argument?

        Locale.setDefault(new Locale("en", "US")); // TODO added this for me (pia) ..
        // ... to force string format to use english decimal point delimiter (.) rather than German (,):

        boolean restrict_2outgoing = true; // or false? TODO restrict to exactly 2 outgoing edges or not?
        System.err.println("Restrict to only those to-be nodes with exactly 2 outgoing edges? " + restrict_2outgoing);

        while ((sdpGraph = gr.readGraph()) != null){
            totalGraphs += 1;
            totalEdges += sdpGraph.getNEdges();
            totalNodes += sdpGraph.getNNodes();
            boolean has_almost_copula = false;
            boolean has_almost_adj_copula = false;
            boolean has_real_adj_copula = false;
            for (Node node : sdpGraph.getNodes()) {
                // Idea: 'Adjective'-Copula look like control (b/be :verb_ARG1 s/Subject :verb_ARG2 (a/Adj :adj_ARG1 s))
                // Non-adjective uses don't have this adjective to subject edge (e.g. 'Parts are a tragedy')
                if (node.lemma.equals("be")) {
                    totalBe++;
                    Set<String> outgoingLabels = getOutgoingEdgeLabels(node);
                    int noutgoing = outgoingLabels.size();

                    // check it has correct outgoing edges (to subject and predicate/object)
                    if ((noutgoing == 2 || !restrict_2outgoing) &&
                            outgoingLabels.contains("verb_ARG1") &&
                            outgoingLabels.contains("verb_ARG2")) { // TODO what if not verb_ but aux_ prefix?
                        almost_copula++;  // copula if to-be lemma node with outgoing edges (one ARG1, one ARG2)
                        has_almost_copula = true;

                        // further info about 2nd argument:
                        // 1. is it an adjective?
                        Node patnode = null;
                        Node actnode = null;
                        for (Edge e: node.getOutgoingEdges()) { // check adjective is pat
                            if (e.label.equals("verb_ARG2")) {
                                patnode = sdpGraph.getNode(e.target);
                                pat_arg_pos_counter.add(patnode.pos);
                                if (adjlabels.contains(patnode.pos)) {
                                    almost_adj_copula += 1;
                                    has_almost_adj_copula = true;
                                }
                            }
                            else if (e.label.equals("verb_ARG1")) {
                                actnode = sdpGraph.getNode(e.target);
                            }
                        }
                        // 2. and does it have an edge to first argument?
                        // if there is an edge 2nd-adj-arg to 1st arg, we have a control-like structure 'true/real adj copula'
                        if (patnode != null && actnode != null && adjlabels.contains(patnode.pos)) {
                            for (Edge pat_act_edge: patnode.getOutgoingEdges()) {
                                if (pat_act_edge.target == actnode.id) {
                                    real_adj_copula += 1;
                                    has_real_adj_copula = true;
                                }
                            }
                        }

                    }
                }
            }
            almost_copulagraphs += has_almost_copula ? 1 : 0;
            almost_adj_copula_graphs += has_almost_adj_copula ? 1 : 0;
            real_adj_copula_graphs += has_real_adj_copula ? 1 : 0;
        }

        System.err.println("Note: " +
                "\n\t'almost copula' be-node has correct outgoing edges" +
                "\n\tadj almost copula: previous plus arg2 points to adjective PoS-tagged token" +
                "\n\tadj real copula: previous plus edge from adjective to subject exists");
        System.err.println(String.format("total graphs:                  %10d", totalGraphs));
        System.err.println(String.format("total edges:                   %10d", totalEdges));
        System.err.println(String.format("total nodes:                   %10d", totalNodes));
        System.err.println(String.format("total occurrences of to be:    %10d || %8.3f percent of nodes", totalBe, 100* totalBe /(float)totalNodes));
        System.err.println(String.format("detected as almost copula:     %10d || %8.3f percent of to-be-occ", almost_copula, 100*almost_copula / (float)totalBe));
        System.err.println(String.format("detected as almost adj copula: %10d || %8.3f percent of to-be-occ", almost_adj_copula, 100*almost_adj_copula / (float)totalBe));
        System.err.println(String.format("detected as real adj copula:   %10d || %8.3f percent of to-be-occ", real_adj_copula, 100*real_adj_copula / (float)totalBe));
        System.err.println(String.format("graphs with >=1 almost copula:     %10d || %8.3f percent of all graphs", almost_copulagraphs, 100*almost_copulagraphs / (float)totalGraphs));
        System.err.println(String.format("graphs with >=1 almost adj copula: %10d || %8.3f percent of all graphs", almost_adj_copula_graphs, 100*almost_adj_copula_graphs / (float)totalGraphs));
        System.err.println(String.format("graphs with >=1 real adj copula:   %10d || %8.3f percent of all graphs", real_adj_copula_graphs, 100*real_adj_copula_graphs / (float)totalGraphs));

        System.err.println("\nPoS tag of node to second argument of almost copulas (incl true copluas)");
        //pat_arg_pos_counter.printAllSorted();
        printCounter(pat_arg_pos_counter);

    }


    private static Set<String> getOutgoingEdgeLabels(Node node) {
        List<Edge> edges = node.getOutgoingEdges();
        return edges.stream().map(edge -> edge.label).collect(Collectors.toSet());
    }

    /**
     * Prints keys of counter in descending frequency order, additionally prints relative frequency
     */
    private static void printCounter(Counter<String> counter) throws IllegalArgumentException {
        List<Object2IntMap.Entry<String>> list = counter.getAllSorted();
        int total = counter.sum();
        if (total == 0) {
            throw new IllegalArgumentException("Counter sum is 0");
        }
        System.err.println(String.format("%20s : %10s : %s", "Key", "Count", "Percentage of all counts"));
        for (Object2IntMap.Entry<String> o : list) {
            System.err.println(String.format("%20s : %10d : %8.3f", o.getKey(), o.getIntValue(), 100* o.getIntValue() / (float)total));
        }
        System.err.println("  -------------------------------------------");
        System.err.println(String.format("%20s : %10d : %8.3f", "total", total, (float) 100));
    }

}
    

    

