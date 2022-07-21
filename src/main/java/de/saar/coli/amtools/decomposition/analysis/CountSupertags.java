/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition.analysis;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.tools.datascript.RawAMRCorpus2TrainingData;
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
 * @author meaghan
 */
public class CountSupertags {

    @Parameter(names = {"--inputPath", "-i"}, description = "Path to amconll file to be analysed", required = true)
    private String inputPath;

    @Parameter(names = {"--outputParentPath", "-o"}, description = "Path to write output to. Will create and use subfolder /corpusName/analysis/supertags/ where corpusName is taken from the amconll filename", required = true)
    private String outputParentPath;

    @Parameter(names = {"--keepSources", "-k"}, description = "Flag; if set, supertags with the same shape but different sources are counted as different")
    private boolean keepSources = false;

    @Parameter(names = {"--help", "-?", "-h"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;

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
        // String corpus = "COGS_amconll_list_train_epoch75"; //args[2];  // AMR
        // String corporaFolder = ""; //args[1];  //EM
        // String pathPrefix = "temp/"; //args[0];  //"/home/mego/Documents/amconll_files/";

        CountSupertags counter = new CountSupertags();

        JCommander commander = new JCommander(counter);
        commander.setProgramName("viz");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occurred: " + ex);
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (counter.help) {
            commander.usage();
            return;
        }

//        String inputPath = args[0];
//        String outputParentPath = args[1];

        String[] pathArray = counter.inputPath.split("/");
        String[] fileNameArray = pathArray[pathArray.length -1].split("\\.");
        String corpus = fileNameArray[0];

        String heading = "Supertags sorted by graph constant in " + counter.inputPath;
        String outPath = counter.outputParentPath + "/" + corpus + "/analysis/" + "/supertags/";

        // read in the file and make it into a list of type AmConllSentence
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(counter.inputPath);

        System.err.println("Counting supertags for graphs in " + counter.inputPath + "\n");

        // a map for storing the graphs and their types. With each graph we store the sentence
        Map<SGraph, Map<Pair<String, Type>, List<AmConllSentence>>> map = new HashMap<>();

        // for every word in the corpus, add the graphs and supertags to map
        for (AmConllSentence sent : amConllSentences) {
            for (AmConllEntry word : sent) {
                // delexicalise
                SGraph delexGraph = word.delexGraph();
                if (!counter.keepSources) {
                    // remove all sources but the root
                    delexGraph = delexGraph.forgetSourcesExcept(Collections.singleton("root"));
                }
                // ignore node names in the equality check
                delexGraph.setEqualsMeansIsomorphy(true);
                // get the current counter for this graph, making it if it doesn't exist yet
                Map<Pair<String, Type>, List<AmConllSentence>> mapForNoSourceGraph =
                        map.computeIfAbsent(delexGraph, k -> new HashMap<>());
                Pair<String, Type> supertag = new Pair<>(word.getDelexSupertag(), word.getType());
                List<AmConllSentence> currentExamples =
                        mapForNoSourceGraph.computeIfAbsent(supertag, k -> new ArrayList<>());
                currentExamples.add(sent);
                mapForNoSourceGraph.put(supertag, currentExamples);
            }
        }

        System.out.println("Writing summary");
        CountSources.writeSummary(map, outPath, heading);

        System.out.println("Writing examples");
        CountSources.writeExamples(map, outPath, true);


    }
    
}
