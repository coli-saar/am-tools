/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

import de.saar.basic.StringTools;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.MrpPreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.StanfordPreprocessedData;
import de.up.ling.irtg.util.Util;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.*;
import java.util.List;

/**
 * Converts raw AMR and english sentences into an alto corpus; this corpus may
 * need to be fixed by FixAMRAltoCorpus.
 *
 * @author Jonas
 */
public class Stripped2Corpus {

    /**
     * Converts raw AMR and english sentences into an alto corpus; this corpus
     * may need to be fixed by FixAMRAltoCorpus. First argument is output folder
     * containing files raw.en and raw.amr; second argument is path to stanford
     * grammar file enlishPCFG.txt
     *
     * -- as of July 2019, this program is never called by itself, but only
     * through {@link FullProcess}. Thus we only do the handling of CLI
     * arguments there. AK
     *
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     */
    /*
    public static void main(String[] args) throws FileNotFoundException, IOException {

        String outputPath = args[0];
        if (!outputPath.endsWith("/")) {
            outputPath = outputPath + "/";
        }
        String pFileName = args[1];

        stripped2Corpus(outputPath, pFileName);

    }
    */

    /**
     * Converts raw AMR and english sentences into an alto corpus; this corpus
     * may need to be fixed by FixAMRAltoCorpus. First argument is output folder
     * containing files raw.en and raw.amr; second argument is path to stanford
     * grammar file enlishPCFG.txt. If the latter is null, a corpus without
     * trees is created.
     *
     * @param path
     * @param stanfordGrammarFile
     * @throws java.io.IOException
     */
    public static void stripped2Corpus(String path, String stanfordGrammarFile, String companionDataFile) throws IOException {
        PreprocessedData preprocData;

        if (companionDataFile != null) {
            System.err.printf("Reading MRP companion data from %s ...\n", companionDataFile);
            preprocData = new MrpPreprocessedData(new File(companionDataFile));
        } else {
            System.err.println("Using Stanford CoreNLP for low-level preprocessing.");
            preprocData = new StanfordPreprocessedData(null);
        }


        //load grammar, if one is provided (only then trees are added to the corpus)
        boolean makeTrees = stanfordGrammarFile != null;
        LexicalizedParser parser = makeTrees ? LexicalizedParser.loadModel(stanfordGrammarFile) : null;

        //input and output files
        BufferedReader enRD = new BufferedReader(new FileReader(path + "raw.en"));
        BufferedReader amrRD = new BufferedReader(new FileReader(path + "raw.amr"));
        BufferedReader idRD = new BufferedReader(new FileReader(path + "graphIDs.txt"));
        FileWriter resWR = new FileWriter(path + "raw.corpus");

        //corpus header
        String treeHeaderLine = makeTrees ? "/// interpretation tree: class de.up.ling.irtg.algebra.TreeWithAritiesAlgebra\n" : "";
        resWR.write("/// IRTG unannotated corpus file, v1.0\n"
                + "///\n"
                + "/// Semeval 2017 train corpus\n"
                + "///\n"
                + "/// interpretation string: class de.up.ling.irtg.algebra.StringAlgebra\n"
                + treeHeaderLine
                + "/// interpretation graph: class de.up.ling.irtg.algebra.graph.GraphAlgebra\n"
                + "/// interpretation id: class de.up.ling.irtg.algebra.StringAlgebra\n"
                + "\n\n");

        //iterate over instances
        int i = 0;
        while (enRD.ready() && amrRD.ready() && idRD.ready()) {
            String id = idRD.readLine();

            //sentence
            String line = enRD.readLine();

            preprocData.setUntokenizedSentence(id, line);
            List<CoreLabel> tokens = preprocData.getTokens(id);

            List<String> sTokens = Util.mapToList(tokens, coreLabel -> coreLabel.word());
            String concatTokens = StringTools.join(sTokens, " ");
            resWR.write("[string] " + concatTokens + "\n");

            //tree
            if (makeTrees) {
                edu.stanford.nlp.trees.Tree tree = parser.apply(tokens);
                if (tokens.size() != tree.getLeaves().size()) {
                    System.err.println("Unequal word and tree leaf count! " + tokens.size() + " vs " + tree.getLeaves().size());
                    System.err.println(tokens);
                    System.err.println(tree);
                }
                resWR.write("[tree] " + tree.toString() + "\n");
            }

            //amr
            String amr = amrRD.readLine();
            resWR.write("[graph] " + amr + "\n");

            // graph IDs
            resWR.write("[id] " + id + "\n\n");

            // on to next item
            i++;
            if (i % 1000 == 0) {
                System.err.println(i);
            }
        }

        enRD.close();
        amrRD.close();
        idRD.close();
        resWR.close();
    }

}
