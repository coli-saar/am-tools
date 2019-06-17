/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;

/**
 *
 * @author koller
 */
public class GraphvizUtils {
    
    public static String simpleAlignViz(MRInstance instance){
        return simpleAlignViz(instance, false);
    }
    
    public static String simpleAlignViz(MRInstance instance, boolean drawEdgeLabels){
        StringBuilder b = new StringBuilder("digraph {   bgcolor=\"transparent\" ; rankdir = \"TD\"");
        int i = 0;
        for (Alignment al : instance.getAlignments()){
            b.append("subgraph cluster_"+i+" { \n");
            b.append("\tlabel=\""+instance.getSentence().get(al.span.start).replaceAll("\"", "''")+"\"\n");
            for (String node : al.nodes){
                b.append("\t");
                b.append(node);
                GraphNode no = instance.getGraph().getNode(node);
                if (no == null) throw new IllegalArgumentException("Can't visualize graph. Node "+node+" seems not to exist");
                if (no.getLabel() == null) throw new IllegalArgumentException("Can't visualize graph. Node "+node+" has no label");
                b.append(" [label=\""+no.getLabel().replaceAll("\"", "''")+"\"]");
                
                if (al.lexNodes.contains(node)){
                    b.append(" [style=bold]");
                }
                b.append("\n");
            }
            b.append("}");
            i++;
        }
        for (GraphEdge e : instance.getGraph().getGraph().edgeSet()){
            b.append(e.getSource().getName());
            b.append("->");
            b.append(e.getTarget().getName());
            if (drawEdgeLabels){
                b.append("[label=\""+e.getLabel().replaceAll("\"", "''")+"\"]");
            }
            
            b.append("\n");
        }
        b.append("}");
        return b.toString();
    }
    
}
