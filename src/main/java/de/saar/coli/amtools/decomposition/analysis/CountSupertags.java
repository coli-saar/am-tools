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
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;

import java.io.IOException;
import java.util.*;

/**
 *
 * @author mego
 */
public class CountSupertags {


    /**
     * Given an amconll file of am-parser output, counts the supertags assigned
     *  to sourceless delexicalized graph constants.
     * Prints the results to std error
     * file is hard-coded for now
     * @param args nothing yet
     * @throws IOException for reading in file
     * @throws ParseException not sure
     * @throws ParserException not sure
     */
    public static void main(String[] args) throws IOException, ParseException, ParserException {

        // read in the file and make it into a list of type AmConllSentence
        String amconllFilePath = "/home/mego/Documents/amconll_files/training/DM.amconll";
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amconllFilePath);

        // a map for storing the sourceless delexicalised constants and their supertags,
        // where the supertags are Pairs of String representation of the SGraph and its Type
        Map<SGraph, Counter<Pair<String, Type>>> counterMap = new HashMap<>();

        // for every word in the corpus, add the graphs and supertags to counterMap
        for (AmConllSentence sent : amConllSentences) {
            for (AmConllEntry word : sent) {
                // delexicalise and remove all sources but the root
                SGraph noSourceGraph = word.delexGraph();
                noSourceGraph = noSourceGraph.forgetSourcesExcept(Collections.singleton("root"));
                noSourceGraph.setEqualsMeansIsomorphy(true); // to ignore node names in the equality check
                // get the current counter for this graph, making it if it doesn't exist yet
                Counter<Pair<String, Type>> counterForNoSourceGraph = counterMap.get(noSourceGraph);
                if (counterForNoSourceGraph == null) {
                    counterForNoSourceGraph = new Counter<>();
                    counterMap.put(noSourceGraph, counterForNoSourceGraph);
                }
                // add the supertag
                counterForNoSourceGraph.add(new Pair<>(word.getDelexSupertag(), word.getType()));
            }
        }

        // To print the graphs in order of frequency (most to least), make a list and then use the (negative) int
        // comparator to sort it.
        List<SGraph> sortedKeys = new ArrayList<>(counterMap.keySet());
        sortedKeys.sort((sGraph1, sGraph2) -> {
            int totalCount1 = counterMap.get(sGraph1).sum();
            int totalCount2 = counterMap.get(sGraph2).sum();
            return -Integer.compare(totalCount1, totalCount2);
        });

        // Print to std error since that's how counterMap.get(graph).printAllSorted() does it
        for (SGraph graph : sortedKeys) {
            System.err.println(graph.toIsiAmrStringWithSources() + "  ####  " + counterMap.get(graph).sum());
            counterMap.get(graph).printAllSorted();
            System.err.println();
        }


    }
    
}
