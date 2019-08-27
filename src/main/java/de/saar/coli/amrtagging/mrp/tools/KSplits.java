/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.up.ling.irtg.codec.OutputCodec;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Takes an amconll file (let's call it decomposable decomposable), another amconll file (all data prepared as if it was test data) an MRP file (with all graphs) 
 and produces k training/dev splits into an output directory.
 * @author matthias
 */
public class KSplits {
    @Parameter(names={"--decomp"}, description = "Path to amconll with decomposed sentences")//, required=true)
    private String decomp = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/AMR/k_splits/clean_decomp_all.amconll";
    
    @Parameter(names={"--dev"}, description = "Path to amconll that serves as source of the devsets, prepared as if it was test data.")//, required=true)
    private String dev = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/AMR/k_splits/all_dev.amconll";
    
    @Parameter(names = {"--mrp"}, description = "Path to MRP corpus")//, required = true)
    private String mrp = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/AMR/k_splits/amr.mrp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output directories")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/AMR/k_splits";
    
    @Parameter(names = {"-k"}, description = "How many splits")//, required = true)
    private int k = 1;
    
    @Parameter(names = {"-fraction"}, description = "Fraction used for dev. default=3%")//, required = true)
    private float fraction = 0.03f;
    
    
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException{
        
        KSplits cli = new KSplits();
        JCommander commander = new JCommander(cli);

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }
        
        List<AmConllSentence> decomposable = AmConllSentence.readFromFile(cli.decomp);
        List<AmConllSentence> devSet = AmConllSentence.readFromFile(cli.dev);
        
        Map<String,AmConllSentence> id2decomp = new HashMap<>();
        decomposable.stream().forEach((AmConllSentence sent) -> id2decomp.put(sent.getId(), sent));
        
        Map<String,AmConllSentence> id2dev = new HashMap<>();
        devSet.stream().forEach((AmConllSentence sent) -> id2dev.put(sent.getId(), sent));
        
        Map<String,MRPGraph> id2graph = new HashMap<>();
        MRPGraph.readFromFile(cli.mrp).stream().forEach((MRPGraph g) -> id2graph.put(g.getId(),g));
        
        Set<String> allIDs = new HashSet<>();
        allIDs.addAll(id2decomp.keySet());
        allIDs.addAll(id2dev.keySet());
        
        List<String> allIDList = new ArrayList(allIDs);
        Random rnd = new Random(30);
        
        int border = Math.round(cli.fraction * allIDs.size());
        
        for (int k = 1; k <= cli.k; k++ ){
            List<AmConllSentence> goldDev = new ArrayList<>();
            List<AmConllSentence> dev = new ArrayList<>();
            List<AmConllSentence> train = new ArrayList<>();
            List<MRPGraph> devGraphs = new ArrayList<>();
            
            String dirName = "split_"+k;
            
            for (String type : new String[]{"train","dev","gold-dev"}){
                Files.createDirectories(Paths.get(cli.outPath,dirName,type));
            }
            
            Collections.shuffle(allIDList, rnd);
            // dev data
            for (int i = 0; i < border; i++){
                String id = allIDList.get(i);
                dev.add(id2dev.get(id));
                devGraphs.add(id2graph.get(id));
                
                if (id2decomp.containsKey(id)){
                   goldDev.add(id2decomp.get(id));
                }
            }
            
            //training data
            for (int i = border; i < allIDs.size(); i++){
                String id = allIDList.get(i);
                if (id2decomp.containsKey(id)){
                    train.add(id2decomp.get(id));
                }
            }
            
            System.out.println("Split "+k);
            
            AmConllSentence.writeToFile(Paths.get(cli.outPath,dirName,"train","train.amconll").toString(), train);
            AmConllSentence.writeToFile(Paths.get(cli.outPath,dirName,"dev","dev.amconll").toString(), dev);
            AmConllSentence.writeToFile(Paths.get(cli.outPath,dirName,"gold-dev","gold-dev.amconll").toString(), goldDev);
            
            OutputStream output = new FileOutputStream(Paths.get(cli.outPath,dirName,"dev","dev.mrp").toFile());
            OutputCodec outputCodec = new MRPOutputCodec();
            
            for (MRPGraph g : devGraphs){
                outputCodec.write(g, output);
            }
            
            output.close();
            
            
        }
       
        
        
    }
    
    
}
