/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.utils;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.sdp.EDS;
import de.up.ling.irtg.codec.OutputCodec;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

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
        FileOutputStream g = new FileOutputStream("/tmp/eds/gold.mrp");
        FileOutputStream sys = new FileOutputStream("/tmp/eds/system.mrp");
        
        for (Pair<MRPGraph, ConlluSentence> p : pairs){
            System.err.println(eds.refine(p.right));
//            MRPGraph orig = p.left;
//            MRPGraph prepro = eds.preprocess(orig);
//            AnchoredSGraph asg = MRPUtils.toSGraphWithAnchoring(prepro);
//            MRPGraph b = MRPUtils.fromAnchoredSGraph(asg, orig.getFlavor(), orig.getFramework(), orig.getId(), orig.getInput(), orig.getVersion(), orig.getTime());
//            codec.write(orig, g);
//            codec.write(eds.postprocess(b),sys);
        }
        g.close();
        sys.close();
        
        
        
    }
}
