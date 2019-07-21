/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes a udpipe.mrp file and creates the corresponding .conllu file.
 * @author matthias
 */
public class MRPConlluToConllu {
    @Parameter(names = {"--mrp"}, description = "Path to udpipe.mrp")//, required = true)
    private String input = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/test/udpipe.mrp";
    
    @Parameter(names = {"-o"}, description = "Name of output file (*.conllu)")//, required = true)
    private String output = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/test/udpipe.conllu";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws IOException {      
        MRPConlluToConllu cli = new MRPConlluToConllu();
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
        List<MRPGraph> graphs = MRPGraph.readFromFile(cli.input);
        List<ConlluSentence> sents = new ArrayList<>();
        for (MRPGraph g : graphs){
            sents.add(g.toConlluSentence());
        }
        ConlluSentence.writeToFile(cli.output, sents);
        
        
    }
              
}
