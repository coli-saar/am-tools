/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.ud.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllSentence;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import edu.stanford.nlp.simple.Sentence;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Replace POS-Tags in UD amconll file to match the training data for the other formalisms.
 * @author matthias
 */
public class Tagger {
    
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (*.amconll)")//, required = true)
    private String corpusPath = "/home/matthias/uni/multi-amparser/data/UD/EWT/en_ewt-ud-test.conllu.amconll";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/uni/multi-amparser/data/UD/EWT/train/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "train";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    

    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        Tagger cli = new Tagger();
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
        
        List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);
        int i = 0;
        for (AmConllSentence sent : sents){
            System.err.println(i);
            i++;
            List<String> words = sent.words();
            Sentence stanfSent = new Sentence(words);
            sent.addLemmas(stanfSent.lemmas());
            sent.addPos(stanfSent.posTags());
            sent.addNEs(stanfSent.nerTags());
        }
        
        AmConllSentence.writeToFile(cli.outPath+cli.prefix+".amconll", sents);
        
    }
        
    
}
