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
import de.saar.coli.amtools.decomposition.analysis.CountSources;

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

        // Change this as needed
        // input will be pathPrefix + corporaFolder + corpus.amconll  (e.g. ... /training/AMR4.amconll)
        // output will be pathPrefix + /analysis/ + corporaFolder + corpus + /supertags/ (e.g. ... /analysis/training/AMR4/supertags/)
        String corpus = "COGS_amconll_list_train_epoch75"; //args[2];  // AMR
        String corporaFolder = ""; //args[1];  //EM
        String pathPrefix = "temp/"; //args[0];  //"/home/mego/Documents/amconll_files/";

        String heading = "Supertags sorted by graph constant in " + corporaFolder + "/" + corpus;
        String outpath = pathPrefix + "analysis/" + corporaFolder  + "/" + corpus + "/supertags/";


        // read in the file and make it into a list of type AmConllSentence
        String amconllFilePath = pathPrefix + corporaFolder  + "/" + corpus + ".amconll";
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amconllFilePath);

        System.err.println("Counting supertags for graphs in " + amconllFilePath + "\n");

        // a map for storing the graphs and their types. With each graph we store the sentence
        Map<SGraph, Map<Pair<String, Type>, List<AmConllSentence>>> map = new HashMap<>();

        // for every word in the corpus, add the graphs and supertags to map
        for (AmConllSentence sent : amConllSentences) {
            for (AmConllEntry word : sent) {
                // delexicalise and remove all sources but the root
                SGraph noSourceGraph = word.delexGraph();
                noSourceGraph = noSourceGraph.forgetSourcesExcept(Collections.singleton("root"));
                noSourceGraph.setEqualsMeansIsomorphy(true); // to ignore node names in the equality check
                // get the current counter for this graph, making it if it doesn't exist yet
                Map<Pair<String, Type>, List<AmConllSentence>> mapForNoSourceGraph =
                        map.computeIfAbsent(noSourceGraph, k -> new HashMap<>());
                Pair<String, Type> supertag = new Pair<>(word.getDelexSupertag(), word.getType());
                List<AmConllSentence> currentExamples =
                        mapForNoSourceGraph.computeIfAbsent(supertag, k -> new ArrayList<>());
                currentExamples.add(sent);
                mapForNoSourceGraph.put(supertag, currentExamples);
            }
        }

        System.out.println("Writing summary");
        CountSources.writeSummary(map, outpath, heading);

        System.out.println("Writing examples");
        CountSources.writeExamples(map, outpath, true);


    }
    
}
