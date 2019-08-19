/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.utils;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.GraphvizUtils;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.up.ling.irtg.codec.OutputCodec;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author matthias
 */
public class Test {
    
    public static void main(String args[]) throws FileNotFoundException, IOException{
        Reader fr = new FileReader("/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/eds/wsj.mrp");
        Reader sentReader = new FileReader("/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu");
        List<Pair<MRPGraph, ConlluSentence>> pairs = Fuser.fuse(fr, sentReader);
        
        List<ConlluSentence> training = new ArrayList<>();
        for (Pair<MRPGraph, ConlluSentence> p : pairs){
            training.add(p.right);
        }
        EDS eds = new EDS(training);
        OutputCodec codec = new MRPOutputCodec();
        FileOutputStream g = new FileOutputStream("/tmp/interesting.mrp");
        
        for (Pair<MRPGraph, ConlluSentence> p : pairs){
            System.err.println(eds.refine(p.right));
            MRInstance instance;
            try {
                instance = eds.toMRInstance(eds.refine(p.right), eds.preprocess(p.left));
            } catch (IllegalArgumentException ex){
                ex.printStackTrace();
                continue;
            }
            
            try {
                instance.checkEverythingAligned();
            } catch (MRInstance.UnalignedNode ex) {
                if (p.right.size() < 10)
                codec.write(p.left, g);
                ex.printStackTrace();
                System.err.println(GraphvizUtils.simpleAlignViz(instance));
            } catch (MRInstance.MultipleAlignments ex) {
                if (p.right.size() < 10) codec.write(p.left, g);
                ex.printStackTrace();
            }
        }
        g.close();
        
        
        
    }
}
