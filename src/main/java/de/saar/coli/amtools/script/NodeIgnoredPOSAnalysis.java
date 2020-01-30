/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.up.ling.irtg.util.Counter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.IOException;
import java.util.List;

/**
 * Compare which nodes are ignored in two SDP corpora based on their Part-of-speech tags
 * @author pia
 */
public class NodeIgnoredPOSAnalysis {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the primary input corpus")//, required = true)
    private String corpusPath = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    @Parameter(names = {"--corpus2", "-c2"}, description = "Path to the secondary input corpus")//, required = true)
    private String corpusPath2 = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";

    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   

    
    public static void main(String[] args) throws IOException {
        //just getting command line args
        NodeIgnoredPOSAnalysis cli = new NodeIgnoredPOSAnalysis();
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

        System.err.println("Corpus 1: "+ cli.corpusPath.substring(cli.corpusPath.length()-10));
        System.err.println("Corpus 2: "+ cli.corpusPath2.substring(cli.corpusPath2.length()-10));
        //setup
        GraphReader2015 gr = new GraphReader2015(cli.corpusPath);
        GraphReader2015 gr2 = new GraphReader2015(cli.corpusPath2);
        Graph sdpGraph1;
        Graph sdpGraph2;

        int totalGraphs = 0;
        int totalNodes1 = 0;
        int totalNodes2 = 0;
        // counters for pos ignored
        Counter<String> ignored1Counter = new Counter<>();
        Counter<String> ignored2Counter = new Counter<>();
        Counter<String> ignoredBothCounter = new Counter<>();
        Counter<String> ignored1not2Counter = new Counter<>();
        Counter<String> ignored2not1Counter = new Counter<>();

        //Counter<Pair<String, String>> posPairCounter = new Counter<>();

        while ((sdpGraph1 = gr.readGraph()) != null && (sdpGraph2 = gr2.readGraph()) != null){
            totalGraphs += 1;
            totalNodes1 += sdpGraph1.getNNodes();
            totalNodes2 += sdpGraph2.getNNodes();
            if (sdpGraph1.getNNodes() != sdpGraph2.getNNodes()) {
                System.err.println("Unequal number of nodes! skipped");
                continue;
            }
            int sentsize = sdpGraph1.getNNodes();
            List<Node> nodes1 = sdpGraph1.getNodes();
            List<Node> nodes2 = sdpGraph2.getNodes();
            for (int position = 0; position < sentsize; ++position) {
                Node node1 = nodes1.get(position);
                Node node2 = nodes2.get(position);
                String pos1 = node1.pos;
                String pos2 = node2.pos;
                boolean ignored1 = !node1.hasIncomingEdges() && !node1.hasOutgoingEdges();
                boolean ignored2 = !node2.hasIncomingEdges() && !node2.hasOutgoingEdges();
                if (ignored1) { ignored1Counter.add(pos1); }
                if (ignored2) { ignored2Counter.add(pos2); }
                if (ignored1 && ignored2) { ignoredBothCounter.add(pos1); } // todo: assert both pos the same?
                if (ignored1 && !ignored2) { ignored1not2Counter.add(pos1); } // todo: assert both pos the same?
                if (!ignored1 && ignored2) { ignored2not1Counter.add(pos2); } // todo: assert both pos the same?
            }
        }

        System.err.println("total graphs in graphbank:  "+totalGraphs);
        System.err.println("total nodes in graphbank 1: "+totalNodes1);
        System.err.println("total nodes in graphbank 2: "+totalNodes2);
        System.err.println();

        // ignored1
        System.err.println(String.format("IGNORED in graphbank 1:        total %10d || %8.3f percent of all nodes1",
                ignored1Counter.sum(), ignored1Counter.sum() / (float) totalNodes1  ));
        //printCounter(ignored1Counter);
        // ignored2
        System.err.println(String.format("IGNORED in graphbank 2:        total %10d || %8.3f percent of all nodes2",
                ignored2Counter.sum(), ignored2Counter.sum() / (float) totalNodes2  ));
        //printCounter(ignored2Counter);
        // ignoredBoth
        System.err.println(String.format("IGNORED in both graphbanks:    total %10d || %8.3f percent of all nodes2",
                ignoredBothCounter.sum(), ignoredBothCounter.sum() / (float) totalNodes2  ));
        //printCounter(ignoredBothCounter);
        // ignored1not2
        System.err.println(String.format("IGNORED in 1 not 2 graphbank:  total %10d || %8.3f percent of all nodes1",
                ignored1not2Counter.sum(), ignored1not2Counter.sum() / (float) totalNodes1  ));
        //printCounter(ignored1not2Counter);
        // ignored2not1
        System.err.println(String.format("IGNORED in 2 not 1 graphbank:  total %10d || %8.3f percent of all nodes2",
                ignored2not1Counter.sum(), ignored2not1Counter.sum() / (float) totalNodes2  ));
        //printCounter(ignored1not2Counter);
    }

    /**
     * Prints keys of counter in descending frequency order, additionally prints relative frequency
     */
    private static void printCounter(Counter<String> counter) throws IllegalArgumentException {
        List<Object2IntMap.Entry<String>> list = counter.getAllSorted();
        int total = counter.sum();
        if (total == 0) {
            throw new IllegalArgumentException("Counter sum is 0");
        }
        System.err.println(String.format("%20s : %10s : %s", "Key", "Count", "Percentage of all counts"));
        for (Object2IntMap.Entry<String> o : list) {
            System.err.println(String.format("%20s : %10d : %8.3f", o.getKey(), o.getIntValue(), 100* o.getIntValue() / (float)total));
        }
        System.err.println("  -------------------------------------------");
        System.err.println(String.format("%20s : %10d : %8.3f", "total", total, (float) 100));
    }

}
    

    

