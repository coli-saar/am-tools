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
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 *
 * @author mego
 */
public class CountSources {

    /**
     * Given a map from any type of keys to lists, returns a counter with same keys, values as size of values
     * @param map Map from objects to ArrayList of AMConllSentences
     * @return Counter
     */
    public static <E> Counter<E> map2counter(Map<E, List<AmConllSentence>> map) {
        Counter<E> counter = new Counter<>();
        for (E key: map.keySet()
             ) {
            counter.add(key, map.get(key).size());

        }
        return counter;
    }

    /**
     * Given an amconll file of am-parser output, counts the sources by incoming edge label
     * Prints the results to std error
     * file is hard-coded for now
     * @param args nothing yet
     * @throws IOException for reading in file
     * @throws ParseException not sure
     * @throws ParserException not sure
     */
    public static void main(String[] args) throws IOException, ParseException, ParserException {

        // Change this as needed
        String corpus = "DM";

        String outpath = "/home/mego/Documents/amconll_files/analysis/training/" + corpus + "/sources/";

        // read in the file and make it into a list of type AmConllSentence
        String amconllFilePath = "/home/mego/Documents/amconll_files/training/" + corpus + ".amconll";
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amconllFilePath);

        System.err.println("Counting sources incident to edge labels in " + amconllFilePath + "\n");

        // a map for storing the edge labels and the sources they are incident to. With each source we store the sentence
        Map<String, Map<String, List<AmConllSentence>>> map = new HashMap<>();

        // for every word in the corpus, add the incoming edge labels and sources to map
        for (AmConllSentence sent : amConllSentences) {
            for (AmConllEntry word : sent) {
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
                        String label = edge.getLabel();
                        // get the current map for this edge label, making it if it doesn't exist yet
                        Map<String, List<AmConllSentence>> mapForEdge = map.computeIfAbsent(label, k -> new HashMap<>());

                        // get the examples for this source, making it if it doesn't exist yet
                        List<AmConllSentence> currentExamples = mapForEdge.computeIfAbsent(source, k -> new ArrayList<>());
                        // add the sentence and put the updated example list back
                        currentExamples.add(sent);
                        mapForEdge.put(source, currentExamples);
                    }
                }
            }
        }


        // write source counts to file
        // To print the graphs in order of frequency (most to least), make a list and then use the (negative) int
        // comparator to sort it.
        List<String> sortedKeys = new ArrayList<>(map.keySet());
        sortedKeys.sort((label1, label2) -> {
            int totalCount1 = 0;
            for (String source: map.get(label1).keySet()
                 ) {
                totalCount1 += map.get(label1).get(source).size();
            }
            int totalCount2 = 0;
            for (String source: map.get(label2).keySet()
            ) {
                totalCount2 += map.get(label2).get(source).size();
            }
            return -Integer.compare(totalCount1, totalCount2);
        });

        // create text file to print counts to
        String outFilename = outpath + "summary.txt";
        try {
            File myObj = new File(outFilename);
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
            } else {
                System.out.println("Overwriting existing file");
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

        try {
            FileWriter myWriter = new FileWriter(outFilename);

            myWriter.write("Summary of " + corpus + " edges incident to sources\n\n");
            // Print for each graph edge label
            for (String label : sortedKeys) {
                myWriter.write(label + "  ####  " + map.get(label).size());
                myWriter.write("\n");
                // make a counter so we can use writeAllSorted()
                map2counter(map.get(label)).writeAllSorted(myWriter);
                myWriter.write("\n");

                // write examples to files for this label and each source
                for (String source: map.get(label).keySet()
                     ) {
                    // make the file
                    String exampleFilename = outpath + "examples/" + label + "_" + source + ".amconll";
                    try {
                        File myObj = new File(exampleFilename);
                        if (myObj.createNewFile()) {
                            System.out.println("File created: " + myObj.getName());
                        } else {
                            System.out.println("Overwriting existing file");
                        }
                    } catch (IOException e) {
                        System.out.println("An error occurred.");
                        e.printStackTrace();
                    }
                    // write to the example file
                    AmConllSentence.writeToFile(exampleFilename, map.get(label).get(source));
                }
            }

            myWriter.close();
            System.out.println("Successfully wrote to the summary file.");

        } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }

    }
    
}
