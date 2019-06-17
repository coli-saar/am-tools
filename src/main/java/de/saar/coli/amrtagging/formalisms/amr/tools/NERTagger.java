/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import de.saar.coli.amrtagging.formalisms.ud.tools.*;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.formalisms.eds.tools.CreateCorpus;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import edu.stanford.nlp.simple.Sentence;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Insert NER tags into an existing AMR amconll file. Guess why I wrote this :/
 * @author matthias
 */
public class NERTagger {
    
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (*.amconll)")//, required = true)
    private String corpusPath = "/home/matthias/uni/multi-amparser/data/AMR/train/train.amconll";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/uni/multi-amparser/data/AMR/train/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "train_with_ner";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
        NERTagger cli = new NERTagger();
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
        
        List<ConllSentence> sents = ConllSentence.readFromFile(cli.corpusPath);
        int i = 0;
        for (ConllSentence sent : sents){
            System.err.println(i);
            i++;
            List<String> words = sent.words();
            List<String> expandedWords = new ArrayList<>();
            List<Integer> origPositions = new ArrayList<>();
            int pos = 0;
            for (String word : words){
                for (String w : word.split("_")){
                    expandedWords.add(w);
                    origPositions.add(pos);
                }
                pos++;
            }
            Sentence stanfSent = new Sentence(expandedWords);
            List<String> ners = Arrays.asList(new String[words.size()]);
            List<String> nerTags = stanfSent.nerTags();
            for (int j = 0; j < nerTags.size(); j++){
                ners.set(origPositions.get(j), nerTags.get(j));
            }
            sent.addNEs(ners);
        }
        
        ConllSentence.writeToFile(cli.outPath+cli.prefix+".amconll", sents);
        
    }
        
    
}
