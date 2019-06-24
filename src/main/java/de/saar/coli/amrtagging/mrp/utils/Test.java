/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.utils;


import com.owlike.genson.Genson;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.mrp.MRPInputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;


/**
 *
 * @author matthias
 */
public class Test {
    
    public static void main(String args[]) throws FileNotFoundException, IOException{
        Reader fr = new FileReader("/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/psd/wsj.mrp");
        
        Reader sentReader = new FileReader("/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu");
        List<Pair<MRPGraph, ConlluSentence>> pairs = Fuser.fuse(fr, sentReader);
        
        Genson g = new Genson();
        for (Pair<MRPGraph, ConlluSentence> p : pairs){
            MRPGraph wo = p.left.deepCopy();
            MRPUtils.addArtificalRoot(p.right, p.left);
            MRPGraph recon = MRPUtils.removeArtificalRoot(p.left);
            if (! recon.equals(wo)){
                System.out.println(g.serialize(wo));
                System.out.println(g.serialize(recon));
                System.out.println("===");
            }
        }
        
        
        
    }
}
