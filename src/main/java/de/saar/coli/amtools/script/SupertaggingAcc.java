/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * Take two amconll files and computes the supertagging accuracy (as-graphs and types).
 * @author matthias
 */
public class SupertaggingAcc {
    @Parameter(names = {"--gold"}, description = "Points to the gold amconll corpus")//, required = true)
    private String goldPath = "/tmp/gold.amconll";
    
    @Parameter(names = {"--system"}, description = "Points to the system output corpus")//, required = true)
    private String systemPath = "/tmp/system.amconll";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException{
        SupertaggingAcc cli = new SupertaggingAcc();
        JCommander commander = new JCommander(cli);
        commander.setProgramName("constraint_extractor");

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
        
        Map<String, AmConllSentence> gold = new HashMap<>();
        AmConllSentence.readFromFile(cli.goldPath).stream().forEach(sent -> gold.put(sent.getId(), sent));
        
        Map<String, AmConllSentence> system = new HashMap<>();
        AmConllSentence.readFromFile(cli.systemPath).stream().forEach(sent -> system.put(sent.getId(), sent));
        
        Set<String> intersection = system.keySet();
        intersection.retainAll(gold.keySet());
        
        int total = 0;
        int correct = 0;
        
        for (String id : intersection){
            AmConllSentence goldSent = (AmConllSentence) gold.get(id);
            AmConllSentence systemSent = (AmConllSentence) system.get(id);
            for (int i = 0; i < goldSent.size(); i++){
                AmConllEntry goldEntry = goldSent.get(i);
                AmConllEntry systemEntry = systemSent.get(i);
                
                total++;
                
                if ((goldEntry.getType() == systemEntry.getType() || goldEntry.getType().equals(systemEntry.getType()))
                        && (goldEntry.delexGraph() == systemEntry.delexGraph() || goldEntry.delexGraph().equals(systemEntry.delexGraph()))){
                    correct++;
                }
            }
        }
        System.out.println("Graph constant accuracy: "+ (float) correct / (float) total);
    }
}
