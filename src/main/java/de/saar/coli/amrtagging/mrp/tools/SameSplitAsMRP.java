/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.ArrayList;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Takes an amconll file (let's call it decomposable amsentences) and an MRP file (let's call it devset) 
 and produces two amconll files (train and gold-dev), where gold-dev will contain all the amconll amsentences of the intersection
 of decomposable amsentences with MRP file, based on id.
 * 
 * @author matthias
 */
public class SameSplitAsMRP {
    @Parameter(names={"--input"}, description = "Path to amconll file that you want to split")//, required=true)
    private String input = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/AMR/conceptnet_corenlp/corenlp_all.amconll";
    
    @Parameter(names = {"--mrp"}, description = "Path to MRP corpus")//, required = true)
    private String mrp = "/home/matthias/Schreibtisch/Hiwi/am-parser/data-preparation/devset_B.mrp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/AMR/conceptnet_corenlp/devsetB/";
    
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException{
        
        SameSplitAsMRP cli = new SameSplitAsMRP();
        JCommander commander = new JCommander(cli);

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }
        
        
        List<ConllSentence> amsentences = ConllSentence.readFromFile(cli.input);
        
        //those IDs of the dev set
        Set<String> devIDs = MRPGraph.readFromFile(cli.mrp).stream().map(sentence -> sentence.getId()).collect(Collectors.toSet());
        
        
        List<ConllSentence> dev = new ArrayList<>();
        List<ConllSentence> train = new ArrayList<>();
        
        for (ConllSentence sent : amsentences){
            String id = sent.getId();
            sent.setId(id);
            
            if (devIDs.contains(id)){
                dev.add(sent);
            } else {
                train.add(sent);
            }
        }
        System.out.println("AMconll Devset has size "+dev.size());
        System.out.println("MRP devset has size "+devIDs.size());
        ConllSentence.writeToFile(cli.outPath+"/gold-dev.amconll", dev);
        ConllSentence.writeToFile(cli.outPath+"/train.amconll", train);
        
        
    }
    
    
}
