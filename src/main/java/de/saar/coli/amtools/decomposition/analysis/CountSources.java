/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition.analysis;

import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 *
 * @author mego
 */
public class CountSources {

    /**
     * Write a summary of a map of maps, e.g. source to map from edge label to examples
     * Prints in order of frequency (most to least)
     * @param map organisation of sentences. map from key to inner key to AmConllSentence
     * @param outpath absolute path to dir for writing. Writes to summary.txt in this directory
     * @param heading String to print at the top of the file
     * @param <F> something hashable, usually String
     * @param <E> something hashable, usually String or Pair
     */
    public static <F,E> void writeSummary(Map<F, Map<E, List<AmConllSentence>>> map, String outpath, String heading) throws IOException {
        // To print the graphs in order of frequency (most to least), make a list and then use the (negative) int
        // comparator to sort it.
        List<F> sortedKeys = new ArrayList<>(map.keySet());
        sortedKeys.sort((label1, label2) -> {
            int totalCount1 = 0;
            for (E source : map.get(label1).keySet()
            ) {
                totalCount1 += map.get(label1).get(source).size();
            }
            int totalCount2 = 0;
            for (E source : map.get(label2).keySet()
            ) {
                totalCount2 += map.get(label2).get(source).size();
            }
            return -Integer.compare(totalCount1, totalCount2);
        });

        // create text file to print counts to
        Files.createDirectories(Paths.get(outpath));
        String outFilename = outpath + "/summary.txt";
        createFile(outFilename);

        // write to the file
        try {
            FileWriter myWriter = new FileWriter(outFilename);

            myWriter.write(heading + "\n\n");

            myWriter.write("Total: " + sortedKeys.size());
            myWriter.write("\nstructure:\nfile name.  item  ####  total number of subtypes\nsubtype: count\n\n");
            // Print for each graph edge label
            for (int i=0; i < sortedKeys.size(); i++) {
                F label = sortedKeys.get(i);
                myWriter.write(i + ". " + label + "  ####  " + map.get(label).size());
                myWriter.write("\n");
                // make a counter so we can use writeAllSorted()
                map2counter(map.get(label)).writeAllSorted(myWriter);
                myWriter.write("\n");

            }
            myWriter.close();
            System.out.println("Successfully wrote to the summary file.");

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    /**
     * Write examples of each variety in a map to an amconll file
     * Note this is for maps to maps
     * @param map organisation of sentences. map from key to inner key to AmConllSentence
     * @param outpath absolute path for writing. Will have "examples/" appended and files will be named key_innerKey
     * @param <F> something hashable, usually String
     * @param <E> something hashable, usually String or Pair
     * @throws IOException for writeToFile
     */
    public static <F,E> void writeExamples(Map<F, Map<E, List<AmConllSentence>>> map, String outpath, Boolean numbers) throws IOException {
        List<F> sortedKeys = new ArrayList<>(map.keySet());
        if (numbers) {

            sortedKeys.sort((label1, label2) -> {
                int totalCount1 = 0;
                for (E source : map.get(label1).keySet()
                ) {
                    totalCount1 += map.get(label1).get(source).size();
                }
                int totalCount2 = 0;
                for (E source : map.get(label2).keySet()
                ) {
                    totalCount2 += map.get(label2).get(source).size();
                }
                return -Integer.compare(totalCount1, totalCount2);
            });
        }
        Files.createDirectories(Paths.get(outpath + "examples/"));
        for (int i = 0; i < sortedKeys.size(); i++) {
            // write examples to files for this label and each source
            List<E> sortedInnerKeys = new ArrayList<>(map.get(sortedKeys.get(i)).keySet());
            if (numbers) {
                final F finalKey = sortedKeys.get(i);
                sortedInnerKeys.sort((label1, label2) -> {
                    int totalCount1 = map.get(finalKey).get(label1).size();
                    int totalCount2 = map.get(finalKey).get(label2).size();
                    return -Integer.compare(totalCount1, totalCount2);
                });
            }

            for (int j = 0; j < sortedInnerKeys.size(); j++) {
                String key;
                String innerKeyRep;
                if (numbers) {
                    key = Integer.toString(i);
                    innerKeyRep = Integer.toString(j);
                } else {
                    key = sortedKeys.get(i).toString();
                    innerKeyRep = sortedInnerKeys.get(j).toString();
                }
                String exampleFilename = outpath + "examples/" + key + "_" + innerKeyRep + ".amconll";
                createFile(exampleFilename);
                // write to the example file
                AmConllSentence.writeToFile(exampleFilename, map.get(sortedKeys.get(i)).get(sortedInnerKeys.get(j)));
            }
        }
    }

    /**
     * Creates a file in this location
     * @param pathToFile path to file including its name
     */
    public static void createFile(String pathToFile) {
        try {
            File myObj = new File(pathToFile);
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
            } else {
                System.out.println("Overwriting existing file");
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }


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
        // input will be pathPrefix + corporaFolder + corpus.amconll  (e.g. ... /training/AMR4.amconll)
        // output will be pathPrefix + /analysis/ + corporaFolder + corpus + /supertags/ (e.g. ... /analysis/training/AMR4/sources/)
        String corpus = args[2];  // AMR
        String corporaFolder = args[1];  //EM
        String pathPrefix = args[0];  //"/home/mego/Documents/amconll_files/";

        // output path
        String outpath = pathPrefix + "analysis/" + corporaFolder + "/" + corpus + "/sources/";
        String heading = "Sources by graph edge label in " + corporaFolder + "/" + corpus;

        // read in the file and make it into a list of type AmConllSentence
        String amconllFilePath = pathPrefix + corporaFolder + "/" + corpus + ".amconll";
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
        // write the files
        writeSummary(map, outpath, heading);
        writeExamples(map, outpath, false);







    }
    
}
