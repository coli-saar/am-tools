package de.saar.coli.amtools.decomposition;

import de.up.ling.irtg.algebra.graph.GraphEdge;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;


import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OldSourceAssigner implements SourceAssigner{
    private final Map<IntSet, String> bestLabels;

    public OldSourceAssigner(List<GraphEdge> edges) {
        bestLabels = new HashMap<>();

        for (GraphEdge e:edges) {
            IntSet nodes = new IntOpenHashSet();
            nodes.add(Integer.valueOf(e.getSource().getName()));
            nodes.add(Integer.valueOf(e.getTarget().getName()));
            //System.out.println(e.getLabel());
            //System.out.println("SOURCE");
            //System.out.println(e.getSource());
            //System.out.println("TARGET");
            //System.out.println(e.target);
            //System.out.println("EDGE LABEL");
            //System.out.println(e.label);
            //System.out.println("SOURCE NAME");
            //System.out.println(mapEdgeLabelToSource(e.label));
            //System.out.println("=======================================");
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
            return "process";
        } else if (Operation.contains("S")){
            return "state";
        } else if (Operation.contains("L")){
          return "link";
        } else if(Operation.contains("C")) {
            return "op";
        } else if(Operation.contains("N")){
          return "conj";
        } else if(Operation.equals("U")) {
            return "pnct";
        } else if(Operation.contains("F")) {
            return "aux";
        } else if(Operation.contains("H")){
            return "scene";
        } else if(matcher.matches()){
            String modSufix = matcher.group(0);
            System.out.println(modSufix);
            return "mod" + modSufix;
        } else {
            return "mod";
        }
    }

    @Override
    public String getSourceName(int parent, int child, String operation){
        IntSet set = new IntOpenHashSet();
        set.add(parent);
        set.add(child);
        return bestLabels.getOrDefault(set, "NULL");
    }
}
