package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FindModalsAcrossSDP {

    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPathDM = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.dm.sdp";// data/sdp/
    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")//, required = true)
    private String corpusPathPAS = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.pas.sdp";// data/corpora/semdep/
    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")//, required = true)
    private String corpusPathPSD = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.psd.sdp";// data/corpora/semdep/


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static List<String> auxLemmas = Arrays.asList(new String[]{"be", "can", "could", "dare", "do", "have", "may", "might", "must", "need",
        "ought", "shall", "should", "will", "would"});

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
        FindModalsAcrossSDP cli = new FindModalsAcrossSDP();
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
        DMBlobUtils dmBlobUtils = new DMBlobUtils();
        PASBlobUtils pasBlobUtils = new PASBlobUtils();
        PSDBlobUtils psdBlobUtils = new PSDBlobUtils();
        GraphReader2015 grDM = new GraphReader2015(cli.corpusPathDM);
        GraphReader2015 grPAS = new GraphReader2015(cli.corpusPathPAS);
        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
        Graph dmGraph;
        Graph pasGraph;
        Graph psdGraph;
        Counter<String> lemmaCounter = new Counter<>();
        Map<String, Counter<Integer>> lemma2patternCounterDM = new HashMap<>();
        Map<String, Counter<Integer>> lemma2patternCounterPAS = new HashMap<>();
        Map<String, Counter<Integer>> lemma2patternCounterPSD = new HashMap<>();
        Counter<String> mdPosCounter = new Counter<>();
        Counter<String> vPosCounter = new Counter<>();

        for (String lemma : auxLemmas) {
            lemma2patternCounterDM.put(lemma, new Counter<>());
            lemma2patternCounterPAS.put(lemma, new Counter<>());
            lemma2patternCounterPSD.put(lemma, new Counter<>());
        }


        while ((dmGraph = grDM.readGraph()) != null && (pasGraph = grPAS.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null){
            for (int i = 0; i< psdGraph.getNNodes(); i++) {
                String psdLemma = psdGraph.getNode(i).lemma;
                if (auxLemmas.contains(psdLemma)) {
                    lemmaCounter.add(psdLemma);
                    if (psdGraph.getNode(i).pos.equals("MD")) {
                        mdPosCounter.add(psdLemma);
                    } else if (psdGraph.getNode(i).pos.startsWith("V")) {
                        vPosCounter.add(psdLemma);
                    }

                    lemma2patternCounterDM.get(psdLemma).add(FindPatternsAcrossSDP.getPattern(dmGraph, i, dmBlobUtils));
                    lemma2patternCounterPAS.get(psdLemma).add(FindPatternsAcrossSDP.getPattern(pasGraph, i, pasBlobUtils));
                    lemma2patternCounterPSD.get(psdLemma).add(FindPatternsAcrossSDP.getPattern(psdGraph, i, psdBlobUtils));

                }
            }
        }

        lemmaCounter.printAllSorted();


        for (String lemma : lemmaCounter.getAllSorted().stream().map(entry -> entry.getKey()).collect(Collectors.toList())) {
            System.err.println("\n");
            System.err.println("Lemma,count,MD,V*");
            System.err.println(lemma+","+lemmaCounter.get(lemma)+","+mdPosCounter.get(lemma)+","+vPosCounter.get(lemma));
            System.err.println();
            System.err.println("Pattern,P0,P1,P2,P3,P4,P5,P6,P7");
            String dmString = "DM";
            for (int i = 0; i<=7; i++) {
                dmString += ","+lemma2patternCounterDM.get(lemma).get(i);
            }
            System.err.println(dmString);
            String pasString = "PAS";
            for (int i = 0; i<=7; i++) {
                pasString += ","+lemma2patternCounterPAS.get(lemma).get(i);
            }
            System.err.println(pasString);
            String psdString = "PSD";
            for (int i = 0; i<=7; i++) {
                psdString += ","+lemma2patternCounterPSD.get(lemma).get(i);
            }
            System.err.println(psdString);
        }

    }





}
