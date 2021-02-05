/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition.analysis;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amtools.decomposition.analysis.CountSources;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

        // Change this as needed
        // input will be pathPrefix + corporaFolder + corpus.amconll  (e.g. ... /training/AMR4.amconll)
        // output will be pathPrefix + /analysis/ + corporaFolder + corpus + /supertags/ (e.g. ... /analysis/training/AMR4/supertags/)
        String corpus = args[2];  // AMR
        String corporaFolder = args[1];  //EM
        String pathPrefix = args[0];  //"/home/mego/Documents/amconll_files/";

        String heading = "Edges in " + corporaFolder + "/" + corpus;
        String outpath = pathPrefix + "analysis/" + corporaFolder  + "/" + corpus + "/edges/";

        // read in the file and make it into a list of type AmConllSentence
        String amconllFilePath = pathPrefix + corporaFolder  + "/" + corpus + ".amconll";
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amconllFilePath);


        // Store the counts of the edge labels in edgeCounter
        Map<String, List<AmConllSentence>> map = new HashMap<>();

        // for every word in the corpus, add the incoming edge label to edgeCounter
        for (AmConllSentence sent : amConllSentences) {
            for (AmConllEntry word : sent) {
                List<AmConllSentence> examples = map.computeIfAbsent(word.getEdgeLabel(), k -> new ArrayList<>());
                examples.add(sent);
                map.put(word.getEdgeLabel(), examples);
            }
        }

        // print them out in order of frequency
        List<String> sortedKeys = new ArrayList<>(map.keySet());
        sortedKeys.sort((label1, label2) -> {
            int totalCount1 = map.get(label1).size();
            int totalCount2 = map.get(label2).size();
            return -Integer.compare(totalCount1, totalCount2);
        });
        // create text file to print counts to
        Files.createDirectories(Paths.get(outpath + "/examples/"));
        String outFilename = outpath + "/summary.txt";
        CountSources.createFile(outFilename);

        // write to the file
        try {
            FileWriter myWriter = new FileWriter(outFilename);

            myWriter.write(heading + "\n\n");
            // Print for each graph edge label
            for (int i=0; i < sortedKeys.size(); i++) {
                String label = sortedKeys.get(i);
                myWriter.write(i + ". " + label + "  ####  " + map.get(label).size());
                myWriter.write("\n");
                myWriter.write("\n");

                String exampleFilename = outpath + "examples/" + sortedKeys.get(i) + ".amconll";
                CountSources.createFile(exampleFilename);
                // write to the example file
                AmConllSentence.writeToFile(exampleFilename, map.get(sortedKeys.get(i)));

            }
            myWriter.close();
            System.out.println("Successfully wrote to the summary file.");

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }
    
}
