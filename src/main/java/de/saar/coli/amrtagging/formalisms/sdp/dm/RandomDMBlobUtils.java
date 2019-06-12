/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.dm;

import de.saar.basic.Pair;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 *
 * @author Jonas
 */
public class RandomDMBlobUtils extends DMBlobUtils {

    private String[] sources = new String[]{OBJ, SUBJ, POSS, COMP, DET, COORD, MOD};
    
    public final Map<String, String> e2s = new HashMap<>();
    public final Map<String, Boolean> e2out = new HashMap<>();
    
    @Override
    public double scoreGraph(Pair<SGraph, ApplyModifyGraphAlgebra.Type> g) {
        return 1.0;
    }

    @Override
    public String edge2Source(GraphEdge edge, SGraph graph) {
        synchronized (e2s) {
//            System.err.println("e2s: "+e2s);
            String el = edge.getLabel();
            if (e2s.containsKey(el)) {
//                System.err.println(e2s.get(el));
                return e2s.get(el);
            } else {
                String ret = getRandom(sources);
                e2s.put(el, ret);
//                System.err.println(ret);
                return ret;
            }
        }
    }

    Random random1 = new Random();
    Random random2 = new Random();
    
    private String getRandom(String[] array) {
        int rnd = random1.nextInt(array.length);
        return array[rnd];
    }

    
    @Override
    public boolean isOutbound(GraphEdge edge) {
        synchronized (e2out) {
//            System.err.println(e2out);
            String el = edge.getLabel();
            if (e2out.containsKey(el)) {
                return e2out.get(el);
            } else {
                Boolean ret = random2.nextBoolean();
                e2out.put(el, ret);
                return ret;
            }
        }
    }
    
    
    
}
