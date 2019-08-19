/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.utils;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.ConlluSentence;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Dead-simple POS tagger for single words.
 * @author matthias
 */
public class StupidPOSTagger {
    
    private Map<String,String> lookup = new HashMap<>();
    private Map<String,Integer> correspondingCount = new HashMap<>();
    private Map<Pair<String,String>,Integer> counter = new HashMap<>();

    private String UNK = "UNK_POS";
    
    /**
     * "Trains" a simple majority tagger. The extractor extracts the tags from the training data.
     * @param trainingData
     * @param extractor 
     */
    public StupidPOSTagger(List<ConlluSentence> trainingData, Function<ConlluEntry, String> extractor){
        for (ConlluSentence sent : trainingData){
            for (ConlluEntry e : sent){
                Pair<String,String> wPos = new Pair<>(e.getForm(),extractor.apply(e));
                counter.put(wPos, counter.getOrDefault(wPos, 0) + 1) ;
            }
        }
        for (Pair<String,String> wPos : counter.keySet()){
            int count = counter.get(wPos);
            if (count >= correspondingCount.getOrDefault(wPos.left, 0)){
                lookup.put(wPos.left, wPos.right);
                correspondingCount.put(wPos.left, count);
            }
        }
        
    }
    
    public String tag(String word){
        return lookup.getOrDefault(word, UNK);
    }
    
}
