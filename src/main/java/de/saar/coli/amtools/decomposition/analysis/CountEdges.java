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
 * @author meaghan fowlie
 */
public class CountEdges {


    /**
     * Given an amconll file of am-parser output, counts the edge occurrences
     * Prints the results to std error
     * file is hard-coded for now
     * @param args nothing yet
     * @throws IOException for file path
     * @throws ParseException not sure
     * @throws ParserException not sure
     */
    public static void main(String[] args) throws IOException, ParseException, ParserException {

        // read in the file and make it into a list of type AmConllSentence
        String amconllFilePath = "/home/mego/Documents/amconll_files/PAS_auto3_dev_epoch_32.amconll";
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amconllFilePath);

        // Store the counts of the edge labels in edgeCounter
        Counter<String> edgeCounter = new Counter<>();

        // for every word in the corpus, add the incoming edge label to edgeCounter
        for (AmConllSentence sent : amConllSentences) {
            for (AmConllEntry word : sent) {
                edgeCounter.add(word.getEdgeLabel());
            }
        }

        // print them out in order of frequency
        edgeCounter.printAllSorted();

    }
    
}
