package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;

public class FindModals {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "../../data/sdp/sdp2014_2015/data/2015/en.pas.sdp";// data/corpora/semdep/


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;



    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        //just getting command line args
        FindModals cli = new FindModals();
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
        GraphReader2015 gr = new GraphReader2015(cli.corpusPath);
        Graph sdpGraph;
        Counter<String> ignoredVerbs = new Counter<>();
        Counter<String> modals = new Counter<>();
        Counter<String> ignoredModals = new Counter<>();


        while ((sdpGraph = gr.readGraph()) != null){
            for (Node node : sdpGraph.getNodes()) {
                if (node.pos.startsWith("V") && node.getNIncomingEdges() == 0 && node.getNOutgoingEdges() == 0) {
                    ignoredVerbs.add(node.form);
                }
                if (node.pos.equals("MD")) {
                    modals.add(node.form);
                    if (node.getNIncomingEdges() == 0 && node.getNOutgoingEdges() == 0) {
                        ignoredModals.add(node.form);
                    }
                }
            }
        }

        System.err.println("IGNORED VERBS:");
        ignoredVerbs.printAllSorted();
        System.err.println("\n\n\nMODALS:");
        modals.printAllSorted();
        System.err.println("\n\n\nIGNORED MODALS:");
        ignoredModals.printAllSorted();

//        System.err.println(String.format("total graphs:               %10d", totalGraphs));

    }

}
