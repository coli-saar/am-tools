/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;

import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;

import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.IOException;

import se.liu.ida.nlp.sdp.toolkit.graph.*;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;


import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

import se.liu.ida.nlp.sdp.toolkit.io.Constants;
import se.liu.ida.nlp.sdp.toolkit.io.GraphWriter2015;

/**
 * Converts an AM Dependency corpus (amconll) into an SDP corpus (.sdp).
 *
 * @author matthias
 */
public class ToSDPCorpus {
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")
//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/meine_Daten/PSD-toy/generalized.amconll";

    @Parameter(names = {"--outFile", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/meine_Daten/PSD-toy/bla";

    @Parameter(names = {"--gold", "-g"}, description = "Path to gold corpus. Make sure it contains exactly the same instances, in the same order.")
//, required=true)
    private String goldCorpus = null;

    @Parameter(names = {"--legacyACL19"}, description = "Uses legacy version of debugging, compatible with our ACL 2019 paper")
    private boolean legacyACL19 = false;

    @Parameter(names = {"--repeat"}, description = "Run the evaluation N times; useful for runtime experiments.")
    private int repeat = 1;

    @Parameter(names = {"--help", "-?", "-h"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;


    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException {
        ToSDPCorpus cli = new ToSDPCorpus();
        JCommander commander = new JCommander(cli);
        commander.setProgramName("constraint_extractor");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }

        for (int repetition = 0; repetition < cli.repeat; repetition++) {

            List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);
            GraphReader2015 goldReader = null;
            if (cli.goldCorpus != null) {
                goldReader = new GraphReader2015(cli.goldCorpus);
            }
            GraphWriter2015 grW = null;
            if (cli.outPath != null) {
                grW = new GraphWriter2015(cli.outPath + ".sdp");
            }

            Scorer scorer = new Scorer();
            long conversionTimeNs = 0;
            long evaluationTimeNs = 0;
            long graphTimeNs = 0;
            ThreadMXBean cpuTimeBean = ManagementFactory.getThreadMXBean();

            for (AmConllSentence s : sents) {
                long t1 = cpuTimeBean.getCurrentThreadCpuTime();

                //prepare raw output without edges
                String id = s.getAttr("id") != null ? s.getAttr("id") : "#NO-ID";
                if (!id.startsWith("#")) id = "#" + id;
                Graph sdpSent = new Graph(id);
                sdpSent.addNode(Constants.WALL_FORM, Constants.WALL_LEMMA, Constants.WALL_POS, false, false, Constants.WALL_SENSE); //some weird dummy node.

                for (AmConllEntry word : s) { //build a SDP Graph with only the words copied from the input.
                    if (!word.getForm().equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
                        sdpSent.addNode(word.getForm(), word.getLemma(), word.getPos(), false, false, "_");
                    }
                }

                boolean read = false;

                try {
                    AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
                    SGraph evaluatedGraph = amdep.evaluate(true);
                    evaluatedGraph = ConjHandler.restoreConj(evaluatedGraph, new PSDBlobUtils(), cli.legacyACL19); //really important!
                    long t2a = cpuTimeBean.getCurrentThreadCpuTime();

                    Graph outputSent = SGraphConverter.toSDPGraph(evaluatedGraph, sdpSent); //add edges
                    long t2 = cpuTimeBean.getCurrentThreadCpuTime();

                    if (goldReader != null) {
                        read = true;
                        Graph goldGraph = goldReader.readGraph();
                        scorer.update(goldGraph, outputSent);
                    }

                    long t3 = cpuTimeBean.getCurrentThreadCpuTime();

                    if (grW != null) {
                        grW.writeGraph(outputSent);
                    }

                    graphTimeNs += t2a - t1;
                    conversionTimeNs += t2 - t2a;
                    evaluationTimeNs += t3 - t2;
                } catch (Exception ex) {
                    System.err.println("In line " + s.getLineNr());
                    //AMDependencyTree amdep = AMDependencyTree.fromSentence(s);
                    //SGraph evaluatedGraph = amdep.evaluate(true);
                    //SGraphDrawer.draw(evaluatedGraph, "");
                    System.err.println("Ignoring exception:");
                    ex.printStackTrace();

                    System.err.println("Writing graph without edges instead");
                    grW.writeGraph(sdpSent);
                    if (!read && goldReader != null) {
                        Graph goldGraph = goldReader.readGraph();
                        scorer.update(goldGraph, sdpSent);
                    }
                }
            }

            if (grW != null) {
                grW.close();
            }

            System.out.printf("Total time: AM evaluation %fs, conversion to evaluator %fs, f-score evaluation %fs.\n", graphTimeNs / 1000000000.0, conversionTimeNs / 1000000000.0, evaluationTimeNs / 1000000000.0);
            
            if (goldReader != null) {
                System.out.println("Labeled Scores");
                System.out.println("Precision " + scorer.getPrecision());
                System.out.println("Recall " + scorer.getRecall());
                System.out.println("F " + scorer.getF1());
                System.out.println("Exact Match " + scorer.getExactMatch());
                System.out.println("------------------------");
                System.out.println("Core Predications");
                System.out.println("Precision " + scorer.getCorePredicationsPrecision());
                System.out.println("Recall " + scorer.getCorePredicationsRecall());
                System.out.println("F " + scorer.getCorePredicationsF1());
                System.out.println("------------------------");
                System.out.println("Semantic Frames");
                System.out.println("Precision " + scorer.getSemanticFramesPrecision());
                System.out.println("Recall " + scorer.getSemanticFramesRecall());
                System.out.println("F " + scorer.getSemanticFramesF1());
            }
        }
    }
}
