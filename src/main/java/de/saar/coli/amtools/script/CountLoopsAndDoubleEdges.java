package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CountLoopsAndDoubleEdges {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to an amconll corpus")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/github/bla.amconll";

    @Parameter(names = {"--psd"}, description = "use PSD post-processing")
    private boolean psdPost=false;
    
    @Parameter(names = {"--psdACL19"}, description = "use PSD post-processing as used for ACL 2019")
    private boolean psdPostACL19=false;

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;



    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        CountLoopsAndDoubleEdges cli = new CountLoopsAndDoubleEdges();
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

        List<AmConllSentence> sentences = AmConllSentence.readFromFile(cli.corpusPath);
        
        int loops = 0; //number of cases where there is an edge from a node N to node N.
        int doubleEdges = 0; //number of cases where there are multiple edges between two nodes.
        
        for (AmConllSentence s: sentences){
            try {
               
                AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
                SGraph evaluatedGraph = amdep.evaluate(true);
                if (cli.psdPost || cli.psdPostACL19){
                    evaluatedGraph = ConjHandler.restoreConj(evaluatedGraph, new PSDBlobUtils(), cli.psdPostACL19); 
                }

                //loops

                for (GraphEdge e : evaluatedGraph.getGraph().edgeSet()){
                    if (e.getSource().getName().equals(e.getTarget().getName())) loops++;
                }
                
                Set<Set<GraphNode>> seen = new HashSet<>();
                //multiple edges between same nodes
                for (GraphNode n1 : evaluatedGraph.getGraph().vertexSet()){
                    
                    for (GraphNode n2 : evaluatedGraph.getGraph().vertexSet()){
                        if (n1.equals(n2)) continue;
                        
                        GraphNode[] arr = {n1, n2};
                        Set<GraphNode> pair = Arrays.stream(arr).collect(Collectors.toSet());
                        if (seen.contains(pair)) continue;
                        seen.add(pair);
                        
                        Set<GraphEdge> edges1 = new HashSet<>(evaluatedGraph.getGraph().edgesOf(n1));
                        edges1.retainAll(evaluatedGraph.getGraph().edgesOf(n2));
                        
                        if (edges1.size() > 1){
                            doubleEdges++;
                        }
                    }
                    
                }
            } catch (Exception ex){
                System.err.println("Ignoring a sentence because of exception:");
                ex.printStackTrace();
            }
        }
        
        System.out.println("Self-Loops: "+loops);
        System.out.println("Pairs of nodes with multiple edges between them: "+doubleEdges);
       
        
    }

}
