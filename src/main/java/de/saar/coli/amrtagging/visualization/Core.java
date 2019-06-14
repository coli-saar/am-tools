/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.visualization;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Counter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contains several functions to visualize Graphs and dependency trees.
 * @author JG
 */
public class Core {
    
    private static final String[] COLORS = new String[]{
        "0.63 1.00 0.83",//blue
        "0.30 1.00 0.71",//green
        "0.50 1.00 0.88",//turquoise
        "0.10 1.00 1.00",//orange
        "0.00 1.00 0.86",//red
        "0.79 1.00 0.82",//purple
        "0.84 1.00 1.00",//pink
        "0.13 1.00 0.58",//brown
        "0.20 1.00 0.93",//yellow-green
        "0.66 1.00 0.48",//navy-blue
        "0.19 1.00 0.55",//olive
        "0.63 1.00 0.63",//blue//repeat a bit darker
        "0.30 1.00 0.51",//green
        "0.50 1.00 0.68",//turquoise
        "0.10 1.00 0.80",//orange
        "0.00 1.00 0.66",//red
        "0.79 1.00 0.62",//purple
        "0.84 1.00 0.80",//pink
        "0.13 1.00 0.38",//brown
        "0.20 1.00 0.73",//yellow-green
        "0.66 1.00 0.28",//navy-blue
        "0.19 1.00 0.35",//olive
        "0.63 0.66 0.83",//blue//repeat with lower saturation
        "0.30 0.66 0.71",//green
        "0.50 0.66 0.88",//turquoise
        "0.10 0.66 1.00",//orange
        "0.00 0.66 0.86",//red
        "0.79 0.66 0.82",//purple
        "0.84 0.66 1.00",//pink
        "0.13 0.66 0.58",//brown
        "0.20 0.66 0.93",//yellow-green
        "0.66 0.66 0.48",//navy-blue
        "0.19 0.66 0.55"//olive
        };
    
    private static final String[] COLORS_LIGHT = new String[]{
        "0.63 0.20 0.83",//blue
        "0.30 0.20 0.71",//green
        "0.50 0.20 0.88",//turquoise
        "0.10 0.20 1.00",//orange
        "0.00 0.20 0.86",//red
        "0.79 0.20 0.82",//purple
        "0.84 0.20 1.00",//pink
        "0.13 0.20 0.58",//brown
        "0.20 0.20 0.93",//yellow-green
        "0.66 0.15 0.65",//navy-blue
        "0.19 0.15 0.70",//olive
        "0.63 0.20 0.83",//blue//currently just repeating the light colors!
        "0.30 0.20 0.71",//green
        "0.50 0.20 0.88",//turquoise
        "0.10 0.20 1.00",//orange
        "0.00 0.20 0.86",//red
        "0.79 0.20 0.82",//purple
        "0.84 0.20 1.00",//pink
        "0.13 0.20 0.58",//brown
        "0.20 0.20 0.93",//yellow-green
        "0.66 0.15 0.65",//navy-blue
        "0.19 0.15 0.70",//olive
        "0.63 0.20 0.83",//blue
        "0.30 0.20 0.71",//green
        "0.50 0.20 0.88",//turquoise
        "0.10 0.20 1.00",//orange
        "0.00 0.20 0.86",//red
        "0.79 0.20 0.82",//purple
        "0.84 0.20 1.00",//pink
        "0.13 0.20 0.58",//brown
        "0.20 0.20 0.93",//yellow-green
        "0.66 0.15 0.65",//navy-blue
        "0.19 0.15 0.70"//olive
    };
    
    private final Counter<String> usedNodeNames;
    
    public Core() {
        usedNodeNames = new Counter<>();
    }
    
    public String visualizeNondecomposable(Signature sig, boolean isConcreteSignature, MRInstance instance, boolean isSDP) {
        
        //TODO currently assumes non-concrete signature and isSDP=true. Add other versions
        
        throw new UnsupportedOperationException();
    }
    
    
    public String visualizeAMdep(ConllSentence conll) {
        for (ConllEntry entry : conll) {
            
        }
        throw new UnsupportedOperationException();
    }
    
    
    private Pair<String, List<String>> sentence2dot(List<String> sentence) {
        throw new UnsupportedOperationException();
    } 
    
    /**
     * 
     * @param graph
     * @return pair of dot string and map from node names in graph to node names in dot
     */
    private Pair<String, Map<String, String>> graph2dot(SGraph graph, Type type) {
        String ret = "digraph G {\n";
        for (GraphNode node : graph.getGraph().vertexSet()) {
            ret += makeGraphNode(graph, type, node.getName(), false, COLORS[0], "  ");
        }
        for (GraphEdge e : graph.getGraph().edgeSet()) {
            ret += makeGraphEdge(e.getSource().getName(), e.getTarget().getName(), e.getLabel(), COLORS[0]);
        }
        ret += "}";
        return new Pair(ret, null);
    }
    
    
    private String alignments2dot(List<Alignment> alignments, List<String> sentenceNodeNames, Map<String, String> graphNodeNames) {
        throw new UnsupportedOperationException();
    }
    
    
    
    
    
    private String makeGraphNode(SGraph graph, Type type, String name, boolean bold, String color, String prefixWS) {
        String ret = prefixWS+name;
        ret += " [label="+getNodeLabel(graph, type, name, bold);
        if (graph.getSourcesAtNode(name).contains("root")) {
            ret += ", style=\"bold\"";
        }
        ret += ", color=\""+color+"\"";
        ret += "];\n";
        return ret;
    }
    
    
    private String getNodeLabel(SGraph graph, Type type, String nn, boolean bold) {
        String ret = "<";
        if (bold) {
            ret += "<b>";
        }
        Collection<String> sources = graph.getSourcesAtNode(nn);
        if (sources.isEmpty() || sources.contains("root")) {
            String label = graph.getNode(nn).getLabel();
            if (label == null) {
                label = "";
            }
            ret += label;
        } else {
            // here we assume that we have either node label or source
            String s = sources.iterator().next(); // currently ignoring the possibility of multiple sources
            System.err.println(type);
            System.err.println(s);
            String retS = s + toStringWithUnify(type.getTargetType(s), type.getUnifications(s), false);
            ret += "<FONT COLOR=\"red\">"+retS+"</FONT>";
        }
        if (bold) {
            ret +="</b>";
        }
        ret +=">";
        return ret;
    }
    
    
    private String makeInvisibleEdge(String nn1, String nn2) {
        throw new UnsupportedOperationException();
    }
    
    private String makeDottedEdge(String nn1, String nn2, String color) {
        throw new UnsupportedOperationException();
    }
    
    private String makeGraphEdge(String nn1, String nn2, String edgeLabel, String color) {
        String ret = "";
        ret += "  "+nn1+"->"+nn2;
        ret += " [label=\""+edgeLabel+"\", style=\"bold\"";
        ret += ", color=\""+color+"\"";
        ret += "];\n";
        return ret;
    }
    
    private String toStringWithUnify(Type type, Map<String, String> id4Unif, boolean withBrackets) {
        List<String> roleStrings = new ArrayList();
        for (String role : type.keySet()) {
            System.err.println(type);
            System.err.println(role);
            System.err.println(type.getTargetType(role));
            System.err.println(type.getUnifications(role));
            roleStrings.add(role + "--" + id4Unif.get(role) + toStringWithUnify(type.getTargetType(role), type.getUnifications(role), true));
        }
        roleStrings.sort(Comparator.naturalOrder());
        if (withBrackets) {
            return "[" + roleStrings.stream().collect(Collectors.joining(", ")) + "]";
        } else {
            return roleStrings.stream().collect(Collectors.joining(", "));
        }
    }
    
    
    public static void main(String[] args) throws ParserException {
        Pair<SGraph, Type> graph = new ApplyModifyGraphAlgebra().parseString("(s<root> / --LEX--  :ARG1 (w<s>)  :ARG2 (d<o>))--TYPE--(o(), s())");
        Core c = new Core();
        System.err.println(c.graph2dot(graph.left, graph.right).left);
    }
    
    
    
    
    
}
