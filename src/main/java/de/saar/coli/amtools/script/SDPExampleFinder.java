package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.Sets;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class SDPExampleFinder {


    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPathDM = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.dm.sdp";// data/sdp/

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")//, required = true)
    private String corpusPathPAS = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.pas.sdp";// data/corpora/semdep/

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")//, required = true)
    private String corpusPathPSD = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.psd.sdp";// data/corpora/semdep/

    @Parameter(names = {"-n"}, description = "Number of examples to be printed")//, required = true)
    private int n = 5;

    @Parameter(names = {"--pattern", "-p"}, description = "Only print examples of this pattern")
    private String pattern = null;

    @Parameter(names = {"-pos"}, description = "Only print examples with this part of speech tag (case sensitive!)")
    private String pos = null;

    @Parameter(names = {"--lemma", "-l"}, description = "Only print examples with this lemma")
    private String lemma = null;

    @Parameter(names = {"--full", "-f"}, description = "Print full sentence")
    private boolean full = false;


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    /**
     * prints CSV tables for all auxiliary verbs according to wikipedia. Information includes total counts, and counts of
     * edge patterns.
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ParseException
     * @throws ParserException
     * @throws AlignedAMDependencyTree.ConllParserException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        //just getting command line args
        SDPExampleFinder cli = new SDPExampleFinder();
        JCommander commander = new JCommander(cli);
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

        DMBlobUtils dmBlobUtils = new DMBlobUtils();
        PASBlobUtils pasBlobUtils = new PASBlobUtils();
        PSDBlobUtils psdBlobUtils = new PSDBlobUtils();

        //setup
        GraphReader2015 grDM = new GraphReader2015(cli.corpusPathDM);
        GraphReader2015 grPAS = new GraphReader2015(cli.corpusPathPAS);
        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
        Graph dmGraph;
        Graph pasGraph;
        Graph psdGraph;

        int examplesPrinted = 0;

        while ((dmGraph = grDM.readGraph()) != null && (pasGraph = grPAS.readGraph()) != null
                && (psdGraph = grPSD.readGraph()) != null && examplesPrinted < cli.n) {


            for (int i = 0; i < dmGraph.getNNodes() && examplesPrinted < cli.n; i++) {
                boolean doPrint = true;
                if (cli.pattern != null && !cli.pattern.equals(FindPatternsAcrossSDP.getPatternCombination(dmGraph, pasGraph, psdGraph, i))) {
                    doPrint = false;
                }
                if (cli.pos != null && !cli.pos.equals(psdGraph.getNode(i).pos)) {
                    doPrint = false;
                }
                if (cli.lemma != null && !cli.lemma.equals(psdGraph.getNode(i).lemma)) {
                    doPrint = false;
                }

                if (doPrint) {
                    System.err.println(dmGraph.id);
                    printExample(dmGraph, i, dmBlobUtils, cli.full);
                    printExample(pasGraph, i, pasBlobUtils, cli.full);
                    printExample(psdGraph, i, psdBlobUtils, cli.full);
                    System.err.println("\n");
                    examplesPrinted++;
                }
            }

        }
    }



    public static void printExample(Graph graph, int wordIndex, AMRBlobUtils blobUtils, boolean fullSentence) {
        Node node = graph.getNode(wordIndex);
        List<Edge> blobEdges = FindPatternsAcrossSDP.getBlobEdges(node, blobUtils);
        List<Edge> nonblobEdges = FindPatternsAcrossSDP.getNonblobEdges(node, blobUtils);

        Set<Integer> blobEdgeTargets = new HashSet<>();
        Set<Integer> nonblobEdgeTargets = new HashSet<>();

        for (Edge e : blobEdges) {
            if (e.target == wordIndex) {
                blobEdgeTargets.add(e.source);
            } else {
                blobEdgeTargets.add(e.target);
            }
        }

        for (Edge e : nonblobEdges) {
            if (e.target == wordIndex) {
                nonblobEdgeTargets.add(e.source);
            } else {
                nonblobEdgeTargets.add(e.target);
            }
        }

        Optional<Integer> minArg = Sets.union(blobEdgeTargets, nonblobEdgeTargets).stream().min(Comparator.naturalOrder());
        Optional<Integer> maxArg = Sets.union(blobEdgeTargets, nonblobEdgeTargets).stream().max(Comparator.naturalOrder());
        int min;
        int max;
        if (minArg.isPresent()) {
            min = Math.min(wordIndex, minArg.get());
        } else {
            min = wordIndex;
        }
        if (maxArg.isPresent()) {
            max = Math.max(wordIndex, maxArg.get());
        } else {
            max = wordIndex;
        }

        StringJoiner sj = new StringJoiner(" ");

        for (Node iNode : graph.getNodes()) {
            //always skip first node (art root), then print relevant nodes or full sentence
            if (iNode.id >= 1 && (fullSentence || (iNode.id >= min && iNode.id <= max))) {
                String form = iNode.form;
                if (iNode.id == wordIndex) {
                    form = "__"+form+"__";
                }
                if (blobEdgeTargets.contains(iNode.id)) {
                    form = "+"+form+"+";
                }
                if (nonblobEdgeTargets.contains(iNode.id)) {
                    form = "-"+form+"-";
                }
                sj.add(form);
            }
        }

        System.err.println(sj.toString());

    }
}
