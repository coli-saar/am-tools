/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.mrp.MRPInputCodec;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.utils.Fuser;
import de.up.ling.tree.ParseException;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Takes an amconll file to be split and an existing dev set (amconll file) and selects the same sentences.
 * Intended for one-time use, if it's reused, please adapt this comment!
 * @author matthias
 */
public class SameSplitAs {
    @Parameter(names={"--input"}, description = "Path to amconll file that you want to split")//, required=true)
    private String input = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/our-amr/everything/concept_net/all.amconll";
    
    @Parameter(names={"--dev"}, description = "Path to amconll dev set")//, required=true)
    private String devSet = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/AMR/gold-dev/gold-dev.amconll";
    
    @Parameter(names = {"--mrp"}, description = "Path to MRP corpus")//, required = true)
    private String mrp = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/amr/amr.mrp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/our-amr/everything/concept_net/old_split/";
    
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException{
        
        SameSplitAs cli = new SameSplitAs();
        JCommander commander = new JCommander(cli);

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }
        
        
        List<ConllSentence> sentences = ConllSentence.readFromFile(cli.input);
        
        //those IDs of the dev set that we want to replicate
        Set<String> devIDs = ConllSentence.readFromFile(cli.devSet).stream().map(sentence -> sentence.getId()).collect(Collectors.toSet());
        
        //load graphs
        Map<String,MRPGraph> id2Graph = new HashMap<>();
        MRPInputCodec graphCodec = new MRPInputCodec();
        BufferedReader fr = new BufferedReader(new FileReader(cli.mrp));
        String line;
        while ( (line = fr.readLine()) != null){
            MRPGraph graph = graphCodec.read(line);
            id2Graph.put(graph.getId(), graph);
        }
        
        List<ConllSentence> dev = new ArrayList<>();
        List<ConllSentence> train = new ArrayList<>();
        List<MRPGraph> devGraphs = new ArrayList<>();
        
        for (ConllSentence sent : sentences){
            String id = sent.getId();
            if (id.contains("___")){
                //Pia's version where the id consists of <file name.mrp>___<actual id>
                id = id.split("___")[1];
            }
            sent.setId(id);
            
            MRPGraph correspondingGraph = id2Graph.get(id);
            //while we're at it: add metadata
            sent.setAttr("version", correspondingGraph.getVersion());
            sent.setAttr("input", correspondingGraph.getInput());
            sent.setAttr("time", correspondingGraph.getTime());

            if (devIDs.contains(id)){
                dev.add(sent);
                devGraphs.add(correspondingGraph);
            } else {
                train.add(sent);
            }
        }
        System.out.println("Devset has size "+dev.size());
        System.out.println("Devset that this should be based on has size "+devIDs.size());
        ConllSentence.writeToFile(cli.outPath+"/dev.amconll", dev);
        ConllSentence.writeToFile(cli.outPath+"/train.amconll", train);
        MRPOutputCodec outputCodec = new MRPOutputCodec();
        OutputStream output = new FileOutputStream(cli.outPath+"/dev.mrp");
        for (MRPGraph devGraph : devGraphs){
            outputCodec.write(devGraph, output);
        }
        
        output.close();
        
        
    }
    
    public static List<String> getWords(ConllSentence sent){
        List<String> words = new ArrayList<>();
        for (int i = 0; i < sent.size(); i++){
            String w = sent.get(i).getForm();
            for (String part : w.split("_")){
                words.add(part);
            }
        }
        return words;
    }
    
}
