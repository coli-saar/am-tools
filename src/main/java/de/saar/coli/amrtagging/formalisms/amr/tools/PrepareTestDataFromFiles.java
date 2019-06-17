/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.tree.ParseException;
import edu.stanford.nlp.simple.Sentence;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 *
 * @author matthias
 */
public class PrepareTestDataFromFiles {
    
    @Parameter(names = {"--corpusPath", "-c"}, description = "Path to the input corpus") //required = true)
    private String corpusPath = "/home/matthias/uni/multi-amparser/eval_tools/AMR/2015/cleanamrdata2019/2019rerun/metadata/";
    
    @Parameter(names = {"--outPath", "-o"}, description = "Prefix for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/";
         
   @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. dev --> dev.amconll)")//, required=true)
    private String prefix = "dev";
       
    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;
    
    public static HashSet<String> specialTokens = new HashSet<>(Arrays.asList(new String[]{"_name_", "_number_", "_date_"}));
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        PrepareTestDataFromFiles cli = new PrepareTestDataFromFiles();
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
        List<List<String>> sentences = readFile(cli.corpusPath+"/sentences.txt");
        List<List<String>> literals = readFile(cli.corpusPath+"/literal.txt");
        List<List<String>> posTags = readFile(cli.corpusPath+"/pos.txt");
        
        if (sentences.size() != literals.size() || literals.size() != posTags.size()){
            System.err.println("Inputs seem to have different lengths: "+sentences.size()+" "+literals.size()+posTags.size());
            return;
        }
        
        List<ConllSentence> output = new ArrayList<>();
        for (int i = 0; i < sentences.size();i++){
            ConllSentence o = new ConllSentence();
            
            List<String> expandedWords = new ArrayList<>();
            List<Integer> origPositions = new ArrayList<>();
            List<String> ners = new ArrayList<>();
            for (int id = 0; id < sentences.get(i).size();id++){
                ConllEntry e = new ConllEntry(id+1, literals.get(i).get(id));
                o.add(e);
                ners.add("O");
                for (String w : literals.get(i).get(id).split("_")){
                    expandedWords.add(w);
                    origPositions.add(id);
                }
            }
            Sentence stanfSent = new Sentence(expandedWords);
            List<String> nerTags = stanfSent.nerTags();
            List<String> ourLemmas = new ArrayList<>(o.words());
            List<String> lemmas = stanfSent.lemmas();
            for (int j = 0; j < lemmas.size(); j++){
                ners.set(origPositions.get(j), nerTags.get(j));
                ourLemmas.set(origPositions.get(j), lemmas.get(j));
                
            }
            o.addReplacement(sentences.get(i));
            o.addPos(posTags.get(i));
            System.err.println(literals.get(i));
            System.err.println(ourLemmas);
            o.addLemmas(ourLemmas);
            o.addNEs(ners);
            
            output.add(o);
            
        }
        
        ConllSentence.writeToFile(cli.outPath+"/"+cli.prefix+".amconll", output);
        
        
    }
    
    public static List<List<String>> readFile(String filename) throws IOException{
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        List<List<String>> allSents = new ArrayList<>(); 
        while ((line = br.readLine()) != null){
            allSents.add(Arrays.asList(line.split(" ")));
        }
        br.close();
        return allSents;
    }
    
    
}
