/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition.analysis;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;

import java.io.IOException;
import java.util.*;

/**
 *
 * @author mego
 */
public class CountSourcesPOS {


    /**
     * Given an amconll file of am-parser output, counts the sources by incoming edge label and POS tag
     * Prints the results to std error
     * file is hard-coded for now
     * @param args nothing yet
     * @throws IOException for reading in file
     * @throws ParseException not sure
     * @throws ParserException not sure
     */
    public static void main(String[] args) throws IOException, ParseException, ParserException {

        // read in the file and make it into a list of type AmConllSentence
        String amconllFilePath = "/home/mego/Documents/amconll_files/PAS_auto3_dev_epoch_32.amconll";
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amconllFilePath);

        System.err.println("Counting sources incident to edge labels in " + amconllFilePath + "\n");

        // a map for storing the edge labels and the count of the sources they are incident to
        Map<Pair<String, String>, Counter<String>> counterMap = new HashMap<>();

        // for every word in the corpus, add the incoming edge labels and sources to counterMap
        for (AmConllSentence sent : amConllSentences) {
            for (AmConllEntry word : sent) {
                // get the POS tag
                String pos = word.getPos();
                // get the graph constant
                SGraph graph = word.delexGraph();
                // all the sources but the root
                Set<String> sources = graph.getAllSources();
                sources.remove("root");
                for (String source : sources) {
                    // get the node NAME for the source, then get the actual node. Use that to get all incident edges
                    GraphNode node = graph.getNode(graph.getNodeForSource(source));
                    Set<GraphEdge> edges = graph.getGraph().edgesOf(node);

                    for (GraphEdge edge : edges) {
                        Pair<String, String> key = new Pair<>(edge.getLabel(), pos);
                        // get the current counter for this edge label and pos, making it if it doesn't exist yet
                        Counter<String> counterForLabel = counterMap.get(key);
                        if (counterForLabel == null) {
                            counterForLabel = new Counter<>();
                            counterMap.put(key, counterForLabel);
                        }
                        // add the source
                        counterForLabel.add(source);
                    }
                }
            }
        }

        // To print the graphs in order of frequency (most to least), make a list and then use the (negative) int
        // comparator to sort it.
        List<Pair<String, String>> sortedKeys = new ArrayList<>(counterMap.keySet());
        sortedKeys.sort((label1, label2) -> {
            int totalCount1 = counterMap.get(label1).sum();
            int totalCount2 = counterMap.get(label2).sum();
            return -Integer.compare(totalCount1, totalCount2);
        });

        // Print to std error since that's how counterMap.get(graph).printAllSorted() does it
        for (Pair<String, String> label : sortedKeys) {
            System.err.println(label + "  ####  " + counterMap.get(label).sum());
            counterMap.get(label).printAllSorted();
            System.err.println();
        }


    }
    
}
