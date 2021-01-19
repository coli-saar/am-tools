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
     * Given an amconll file of am-parser output, counts the sources by incoming edge label
     * Prints the results to std error
     * file is hard-coded for now
     * @param args nothing yet
     * @throws IOException for reading in file
     * @throws ParseException not sure
     * @throws ParserException not sure
     */
    public static void main(String[] args) throws IOException, ParseException, ParserException {

        // Change these as needed
        String corpus = "AMR-2017";
        String epoch = "57";
        int numberOfExamples = 5; // for printing example IDs


        // read in the file and make it into a list of type AmConllSentence
        String amconllFilePath = "/home/mego/Documents/amconll_files/training/" + corpus + "_amconll_list_train_epoch" + epoch + ".amconll";
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amconllFilePath);

        System.err.println("Counting sources incident to edge labels in " + amconllFilePath + "\n");

        // a map for storing the edge labels and the count of the sources they are incident to
        Map<String, Pair<Counter<String>, ArrayList<String>>> counterMap = new HashMap<>();

        // for every word in the corpus, add the incoming edge labels and sources to counterMap
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
                        // get the current counter and list for this edge label, making it if it doesn't exist yet
                        Pair<Counter<String>, ArrayList<String>> pair = counterMap.get(label);
                        if (pair == null) {
                            pair = new Pair<>(new Counter<>(), new ArrayList<>());
                            counterMap.put(label, pair);
                        }
                        // add the source
                        pair.getLeft().add(source);

                        // add the sentence ID
                        pair.getRight().add(sent.getId());

                    }
                }
            }
        }

        // create file to print to
        String outFilename = "/home/mego/Documents/amconll_files/analysis/sources_" + corpus + "_epoch" + epoch + ".txt";
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

        // write source counts to file
        try {
            FileWriter myWriter = new FileWriter(outFilename);

            // To print the graphs in order of frequency (most to least), make a list and then use the (negative) int
            // comparator to sort it.
            List<String> sortedKeys = new ArrayList<>(counterMap.keySet());
            sortedKeys.sort((label1, label2) -> {
                int totalCount1 = counterMap.get(label1).getLeft().sum();
                int totalCount2 = counterMap.get(label2).getLeft().sum();
                return -Integer.compare(totalCount1, totalCount2);
            });

            // Print for each graph edge label
            for (String label : sortedKeys) {
                myWriter.write(label + "  ####  " + counterMap.get(label).getLeft().sum());
                myWriter.write("\n");
                counterMap.get(label).getLeft().writeAllSorted(myWriter);
                myWriter.write("\n");

                // print examples
                myWriter.write("Examples: ");
                ArrayList<String> examples = counterMap.get(label).getRight();

                // just numberOfExamples, randomly selected (or all we have)
                Random rand = new Random();
                ArrayList<String> subsetExamples = new ArrayList<>();

                for (int i = 0; i < Math.min(numberOfExamples, examples.size()); i++) {
                        int randomIndex = rand.nextInt(examples.size());
                        subsetExamples.add(examples.get(randomIndex));
                        examples.remove(randomIndex);
                }
                myWriter.write(subsetExamples.toString());
                myWriter.write("\n\n");
            }

            myWriter.close();
            System.out.println("Successfully wrote to the file.");

        } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }

    }
    
}
