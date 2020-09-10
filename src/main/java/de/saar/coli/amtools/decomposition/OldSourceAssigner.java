package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.SgraphAmrOutputCodec;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;


import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OldSourceAssigner implements SourceAssigner{
    public MRInstance inst;
    private final Map<IntSet, String> bestLabels;
    public static final String[] orderedLexicalEdgeLabels = new String[]{"L", "P", "S", "N", "C", "A", "G", "R", "T", "D","U", "F", "H"};


    public OldSourceAssigner(List<GraphEdge> edges, MRInstance instance) {
        inst = instance;
        bestLabels = new HashMap<>();
        for (GraphEdge e:edges) {
            IntSet nodes = new IntOpenHashSet();
            //Set nodes = new HashSet();
            nodes.add(Integer.valueOf(e.getSource().getName().split("_")[1]));
            nodes.add(Integer.valueOf(e.getTarget().getName().split("_")[1]));
            bestLabels.put(nodes, mapEdgeLabelToSource(e.getLabel()));
        }
    }

    public String mapEdgeLabelToSource(String Operation){
        Pattern pattern  = Pattern.compile("[0-9]");
        Matcher matcher = pattern.matcher(Operation);

        if (Operation.equals("A") || Operation.equals("A-flipped") || Operation.equals("A/E") || Operation.equals("A/D") || Operation.equals("A/G/P") || Operation.equals("A/G")) {
            return "participant";
        } else if(Operation.equals("A-remote")){
            //return "participant[participant]";
            return "participant";
        } else if (Operation.contains("P")){
            return "scene";
        } else if (Operation.contains("S")){
            return "scene";
        } else if (Operation.contains("L")){
            return "link";
        } else if(Operation.contains("C")) {
            return "op";
        } else if(Operation.contains("N")){
            return "participant";
        } else if(Operation.equals("U")) {
            return "pnct";
        } else if(Operation.contains("F")) {
            return "aux";
        } else if(Operation.contains("H")){
            return "scene";
        } else if(matcher.matches()){
            String modSufix = matcher.group(0);
            return "mod" + modSufix;
        } else {
            return "mod";
        }
    }

    @Override
    public String getSourceName(int parent, int child, String operation){
        Set set = new IntOpenHashSet();
        String[] mods = new String[]{"D", "F", "G", "U", "E"};
        List<String> modList = Arrays.asList(mods);

        //id(parent or child variable)= lex nodename + 1
        int realChild = child -1;
        int realParent = parent-1;

        //child node name in sgraph
        String childNode = "n_" + String.valueOf(realChild);
        String parentNode = "n_" + String.valueOf(realParent);
        SGraph sgraph = inst.getGraph();

        Set<GraphEdge> incomingEdges = sgraph.getGraph().incomingEdgesOf(sgraph.getNode(childNode));
        System.out.println(incomingEdges.toString());
        for(String l:orderedLexicalEdgeLabels){
            for (GraphEdge e:incomingEdges){
                //just for checking
                if (e.getLabel().contains(l)){
                    //if the child has an incoming mod edges, then it belongs to a s-graph
                    return mapEdgeLabelToSource(e.getLabel());
                }
            }

        }


        //if it hasn't returned a value yet, there are no incoming mod edges,
        //USE SGRAPH TO LOOK AT ALL EDGES THAT TOUCH THE NDOE
        //SINCE THEY'RE ALL LEXICAL, THERE SHOULD ONLY BE INCOMING EDGES UNLESS THAT SOURCE IS C

        // obtaining the ids like this assumes that the dependency relationship is between the lexical items?

        set.add(realChild);
        set.add(realParent);
        return bestLabels.getOrDefault(set, "NULL");
    }
}
