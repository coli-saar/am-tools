/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.mrp.MRPInputCodec;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Separates train and dev data of DM and PSD. Uses the same split as in the SemEval 2015 shared task.
 * @author matthias
 */
public class SeparateTrainDev {
    
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus  or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/dm/wsj.mrp";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
        SeparateTrainDev cli = new SeparateTrainDev();
        JCommander commander = new JCommander(cli);

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }
        if (cli.help) {
            commander.usage();
            return;
        }
        
        BufferedReader fr = new BufferedReader(new FileReader(cli.corpusPath));
        String line;
        MRPInputCodec inputCodec = new MRPInputCodec();
        MRPOutputCodec outputCodec = new MRPOutputCodec();
        FileOutputStream dev = new FileOutputStream(cli.corpusPath+"_dev.mrp");
        FileOutputStream train = new FileOutputStream(cli.corpusPath+"_train.mrp");
        
        while ((line = fr.readLine()) != null){
            MRPGraph g = inputCodec.read(line);
            if (g.getId().substring(1, 3).equals("20")){
                outputCodec.write(g, dev);
            } else {
                outputCodec.write(g, train);
            }
        }
        
        
    }
    
}
