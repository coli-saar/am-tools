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
    
    
    
    
    public static void main(String[] args) throws IOException, ParseException, ParserException {
        String amconllFilePath = "/home/mego/Documents/amconll_files/PAS_auto3_dev_epoch_32.amconll";
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amconllFilePath);
        Map<SGraph, Counter<Pair<String, Type>>> counterMap = new HashMap<>();
        for (AmConllSentence sent : amConllSentences) {
            for (AmConllEntry word : sent) {
                SGraph noSourceGraph = word.delexGraph();
                noSourceGraph = noSourceGraph.forgetSourcesExcept(Collections.singleton("root"));
                noSourceGraph.setEqualsMeansIsomorphy(true); // to ignore node names in the equality check
                Counter<Pair<String, Type>> counterForNoSourceGraph = counterMap.get(noSourceGraph);
                if (counterForNoSourceGraph == null) {
                    counterForNoSourceGraph = new Counter<>();
                    counterMap.put(noSourceGraph, counterForNoSourceGraph);
                }
                counterForNoSourceGraph.add(new Pair<>(word.getDelexSupertag(), word.getType()));
            }
        }

        List<SGraph> sortedKeys = new ArrayList<>(counterMap.keySet());
        sortedKeys.sort((sGraph1, sGraph2) -> {
            int totalCount1 = counterMap.get(sGraph1).sum();
            int totalCount2 = counterMap.get(sGraph2).sum();
            return -Integer.compare(totalCount1, totalCount2);
        });

        for (SGraph graph : sortedKeys) {
            System.err.println(graph.toIsiAmrStringWithSources() + "  ####  " + counterMap.get(graph).sum());
            counterMap.get(graph).printAllSorted();
            System.err.println();
        }


    }
    
}
