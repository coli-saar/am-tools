/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.utils;

import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.mrp.MRPInputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Takes an MRP corpus and the companion data and pairs them up nicely.
 * @author matthias
 */
public class Fuser {
    
    /**
     * Returns a list of pairs of graphs and ConlluSentences in the order in which the graphs appear in the corpus.
     * @param graphReader
     * @param sentReader
     * @return
     * @throws IOException 
     */
    public static List<Pair<MRPGraph, ConlluSentence>> fuse(Reader graphReader, Reader sentReader) throws IOException{
        List<Pair<MRPGraph, ConlluSentence>> results = new ArrayList<>();
        
        BufferedReader fr = new BufferedReader(graphReader);
        String line;
        MRPInputCodec mrpInput = new MRPInputCodec();
        
        
        List<ConlluSentence> sents = ConlluSentence.read(sentReader);
        HashMap<String,ConlluSentence> id2sent = new HashMap<>();
      
        for (ConlluSentence s : sents){
            id2sent.put(s.getId(), s);
        }
        while ( (line = fr.readLine()) != null){
            MRPGraph graph = mrpInput.read(line);
            if (id2sent.containsKey(graph.getId())){
                results.add(new Pair(graph, id2sent.get(graph.getId())));
            } else {
                throw new IllegalArgumentException("Companion data doesn't contain analysis for graph "+graph.getId());
            }
        }

        return results;
        
    }
    
}
