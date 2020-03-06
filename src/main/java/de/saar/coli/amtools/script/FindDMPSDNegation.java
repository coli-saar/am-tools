/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import edu.stanford.nlp.util.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  Create PSD training data.
 * @author jonas
 */
public class FindDMPSDNegation {
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the primary input corpus")//, required = true)
    private String corpusPathDM = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    @Parameter(names = {"--corpus2", "-c2"}, description = "Path to the secondary input corpus")//, required = true)
    private String corpusPathPSD = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;



    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        //just getting command line args
        FindDMPSDNegation cli = new FindDMPSDNegation();
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

        //setup
        GraphReader2015 grDM = new GraphReader2015(cli.corpusPathDM);
        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
        Graph dmGraph;
        Graph psdGraph;
        int psdNegLemma = 0;
        int linesPrinted = 0;
        int noEdge = 0;
        int targetIsVerb = 0;
        int notIsShiftedRoot = 0;
        int targetIsVerbAndNotIsShiftedRoot = 0;

        Counter<String> targetPOSCounter = new Counter<>();
        Counter<String> negFormCounter = new Counter<>();
        Counter<String> negPSDLemmaCounter = new Counter<>();
        Counter<String> notPSDLemmaCounter = new Counter<>();
        Counter<String> edgeLabelsInNegNotDiff = new Counter<>();

        List<String> verbPOS = Arrays.asList("VB VBD VBG VBP VBZ VBN MD".split(" "));

        while ((dmGraph = grDM.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null){
            List<Node> nodeListDM = dmGraph.getNodes();
            List<Node> nodeListPSD = psdGraph.getNodes();
            if (nodeListDM.size() != nodeListPSD.size()) {
                System.err.println("WARNING: sentences of different length");
            }
            for (int i = 0; i< nodeListDM.size() && i<nodeListPSD.size(); i++) {
                Node nodeDM = nodeListDM.get(i);
                Node nodePSD = nodeListPSD.get(i);
                //check PSD lemma
                boolean hasDMnegEdge = false;
                for (Edge dmEdge : nodeDM.outgoingEdges) {
                    if (dmEdge.label.equals("neg")) {
                        //TODO check if there are multiple neg edges on one node sometimes
                        hasDMnegEdge = true;
                        String targetPOS = nodeListDM.get(dmEdge.target).pos;
                        targetPOSCounter.add(targetPOS);
                        negFormCounter.add(nodeDM.form);
                        negPSDLemmaCounter.add(nodePSD.lemma);

                        if (verbPOS.contains(targetPOS)) {
                            targetIsVerb++;
                        }

                        Node psdTargetNode = nodeListPSD.get(dmEdge.target);

                        Set<Integer> dmIncomingEdgeParents = new HashSet<>();
                        for (Edge e : nodeDM.incomingEdges) {
                            dmIncomingEdgeParents.add(e.source);
                        }
                        Set<Integer> psdIncomingEdgeParents = new HashSet<>();
                        for (Edge e : psdTargetNode.incomingEdges) {
                            psdIncomingEdgeParents.add(e.source);
                        }
                        boolean shiftedRoot = (nodeDM.isTop && psdTargetNode.isTop)
                                || (Sets.intersects(dmIncomingEdgeParents, psdIncomingEdgeParents));

                        if (shiftedRoot) {
                            notIsShiftedRoot++;
                        }

                        if (shiftedRoot && verbPOS.contains(targetPOS)) {
                            targetIsVerbAndNotIsShiftedRoot++;
                        }
                    }
                }


                if (nodeDM.form.equals("not")) {
                    notPSDLemmaCounter.add(nodePSD.lemma);
                }

                if (nodePSD.lemma.equals("#Neg")) {
                    psdNegLemma++;
                }


                if (nodePSD.lemma.equals("#Neg") && !hasDMnegEdge) {
                    for (Edge e : nodeDM.outgoingEdges) {
                        edgeLabelsInNegNotDiff.add(e.label);
                    }
                    for (Edge e : nodeDM.incomingEdges) {
                        edgeLabelsInNegNotDiff.add(e.label);
                    }
                    if (nodeDM.incomingEdges.isEmpty() && nodeDM.outgoingEdges.isEmpty()) {
                        noEdge++;
                    }
                    if (linesPrinted < 20) {
                        int spanStart = 0;
                        int spanEnd = nodeListDM.size();
                        StringJoiner sj = new StringJoiner(" ");
                        for (int k = spanStart; k < spanEnd; k++) {
                            sj.add(nodeListPSD.get(k).form);
                        }
                        System.err.println(sj.toString());
                        linesPrinted++;
                    }
                }


            }
        }


        //beFormAndDMLemmaCounter.printAllSorted();
        targetPOSCounter.printAllSorted();
        System.err.println();
        negFormCounter.printAllSorted();
        System.err.println();
        negPSDLemmaCounter.printAllSorted();
        System.err.println();
        notPSDLemmaCounter.printAllSorted();
        System.err.println("total #Neg lemma: "+psdNegLemma);
        System.err.println();
        edgeLabelsInNegNotDiff.printAllSorted();
        System.err.println(noEdge);
        System.err.println();
        System.err.println("target has verb POS: "+targetIsVerb);
        System.err.println("shifted root: "+notIsShiftedRoot);
        System.err.println("both: "+targetIsVerbAndNotIsShiftedRoot);
    }

    private static Set<String> getOutgoingEdgeLabels(Node node) {
        List<Edge> edges = node.getOutgoingEdges();
        return edges.stream().map(edge -> edge.label).collect(Collectors.toSet());
    }

}




