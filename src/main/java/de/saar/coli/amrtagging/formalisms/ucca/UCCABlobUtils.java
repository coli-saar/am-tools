/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.ucca;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;

/**
 *
 * @author JG
 */
public class UCCABlobUtils extends AMRBlobUtils {
    public static final String[] OUTBOUND_EDGES = new String[]{"C", "P", "S", "A", "N", "H", "L", "LR", "LA"};


    @Override
    public boolean isOutbound(GraphEdge edge) {
        //edge.getLabel()
        //throw new UnsupportedOperationException();
        //will this work??
        for (String regex : OUTBOUND_EDGES) {
            if (edge.getLabel().contains(regex)) {
                return true;
            }
        }
        return false;
    }

    public static String extractNumber(final String str) {

        if(str == null || str.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for(char c : str.toCharArray()){
            if(Character.isDigit(c)){
                sb.append(c);
                found = true;
            } else if(found){
                // If we already found a digit before and this char is not a digit, stop looping
                break;
            }
        }

        return sb.toString();
    }

    @Override
    public String edge2Source(GraphEdge edge, SGraph graph){
        if (edge.getLabel().equals("A") || edge.getLabel().contains("H")) {
            return "a" ;
        } else if(edge.getLabel().contains("C")) {
            return "op";
        }else if(!extractNumber(edge.getLabel()).equals("")){
            String suffix = extractNumber(edge.getLabel());
            return "mod-" + suffix;
        } else if(edge.getLabel().contains("F")) {
            return "aux";
        }else if(edge.getLabel().contains("U")) {
            return "pnct";
        } else {
            return "mod";
        }

    }

    @Override
    public boolean isConjEdgeLabel(String edgeLabel) {
        //throw new UnsupportedOperationException();
        return false;

    }

    @Override
    public boolean isConjunctionNode(SGraph graph, GraphNode node) {
        //throw new UnsupportedOperationException();
        return false;
    }


}
