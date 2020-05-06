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
import de.saar.coli.amrtagging.SupertagDictionary;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;


/**
 * Take an existing amconll file (train) and another existing one (gold-dev) and write both of them to the output path with .train and .dev suffix.
 * In the new files, consistent string representations for the supertags are chosen.
 * @author matthias
 */
public class ConsistentSupertags {
    @Parameter(names = {"--train"}, description = "Points to the train amconll corpus")//, required = true)
    private String trainPath = "/tmp/train.amconll";
    
    @Parameter(names = {"--dev"}, description = "Points to the gold-dev amconll corpus")//, required = true)
    private String devPath = "/tmp/gold-dev.amconll";

    @Parameter(names = {"-o"}, description = "Output path")//, required = true)
    private String outPath = "/tmp/consistent";
    
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException{
        ConsistentSupertags cli = new ConsistentSupertags();
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
        
        List<AmConllSentence> train = AmConllSentence.readFromFile(cli.trainPath);
        
        List<AmConllSentence> dev = AmConllSentence.readFromFile(cli.devPath);
        
        SupertagDictionary lexicon = new SupertagDictionary();
        
        for (AmConllSentence sent : train){
            for (AmConllEntry e : sent){
                e.setDelexSupertag(lexicon.getRepr(e.delexGraph()));
            }
        }
        
        for (AmConllSentence sent : dev){
            for (AmConllEntry e : sent){
                e.setDelexSupertag(lexicon.getRepr(e.delexGraph()));
            }
        }
        
        AmConllSentence.writeToFile(cli.outPath+".dev", dev);
        AmConllSentence.writeToFile(cli.outPath+".train", train);
    }
}
