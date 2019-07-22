/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMToolsVersion;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.Util;
import static de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractorCLI.LITERAL_JOINER;
import static edu.illinois.cs.cogcomp.core.datastructures.ViewNames.NER_CONLL;

import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.MrpPreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.NamedEntityRecognizer;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.StanfordNamedEntityRecognizer;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.UiucNamedEntityRecognizer;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.simple.Sentence;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author matthias
 */
public class PrepareTestDataFromFiles {
    
    @Parameter(names = {"--corpusPath", "-c"}, description = "Path to the input corpus") //required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/our-amr/second_shot/evalDev/";
    
    @Parameter(names = {"--outPath", "-o"}, description = "Prefix for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/our-amr/second_shot/evalDev/";
         
   @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. dev --> dev.amconll)")//, required=true)
    private String prefix = "dev";
       
    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;
    
    @Parameter(names = {"--companion"}, description = "Path to MRP companion data (will disable builtin lemmatization)", required = false)
    private String companionDataFile = null;

    @Parameter(names = {"--stanford-ner-model"}, description = "Filename of Stanford NER model english.conll.4class.distsim.crf.ser.gz; if argument is not given, use UIUC NER tagger")
    private String stanfordNerFilename = null;

    @Parameter(names = {"--uiuc-ner-tagset"}, description = "Tagset to use for UIUC NER tagger; options: NER_CONLL (default), NER_ONTONOTES")
    private String uiucNerTagset = NER_CONLL;

    //public static HashSet<String> specialTokens = new HashSet<>(Arrays.asList(new String[]{"_name_", "_number_", "_date_"}));
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {
        
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
        List<List<String>> IDs = readFile(cli.corpusPath+"/graphIDs.txt");
        
        if (sentences.size() != literals.size() || literals.size() != posTags.size()){
            System.err.println("Inputs seem to have different lengths: "+sentences.size()+" "+literals.size()+posTags.size());
            return;
        }
        
        PreprocessedData preprocData = null;
        NamedEntityRecognizer neRecognizer = null;

        if( cli.companionDataFile != null ) {
            preprocData = new MrpPreprocessedData(new File(cli.companionDataFile));
        } else {
            // It would be nice to encapsulate the choice between MRP preproc data and
            // Stanford preproc data as neatly as elsewhere, but as I read the code below,
            // the Stanford lemmatizer is applied to the expandedWords, in which tokens
            // have been split apart at underscores. Constructing a StanfordPreprocData
            // up here would only duplicate the code. - AK, July 2019.
        }

        if( cli.stanfordNerFilename != null ) {
            neRecognizer = new StanfordNamedEntityRecognizer(new File(cli.stanfordNerFilename));
        } else {
            neRecognizer = new UiucNamedEntityRecognizer(cli.uiucNerTagset);
        }

        
        
        List<AmConllSentence> output = new ArrayList<>();
        for (int i = 0; i < sentences.size();i++){
            AmConllSentence o = new AmConllSentence();
            o.setAttr("git", AMToolsVersion.GIT_SHA);
            String graphID = IDs.get(i).get(0);
            List<String> expandedWords = new ArrayList<>();
            List<Integer> origPositions = new ArrayList<>();
            List<String> ners = new ArrayList<>();
            for (int id = 0; id < sentences.get(i).size();id++){
                String wordForm = literals.get(i).get(id).replace(LITERAL_JOINER, " ");
                AmConllEntry e = new AmConllEntry(id + 1, wordForm);
                o.add(e);
                ners.add("O");
                for (String w : literals.get(i).get(id).split(LITERAL_JOINER)){
                    expandedWords.add(w);
                    origPositions.add(id);
                }
            }
            List<String> lemmas;
            List<CoreLabel> nerTags;
            try {
                if( preprocData != null ) {
                    lemmas = preprocData.getLemmas(graphID);
                    nerTags = neRecognizer.tag(preprocData.getTokens(graphID));
                } else {
                    Sentence stanfSent = new Sentence(expandedWords);
                    lemmas = stanfSent.lemmas();
                    nerTags = neRecognizer.tag(Util.makeCoreLabelsForTokens(expandedWords));
                }
            
                List<String> ourLemmas = new ArrayList<>(o.words());
                for (int j = 0; j < lemmas.size(); j++){
                    ners.set(origPositions.get(j), nerTags.get(j).ner());
                    ourLemmas.set(origPositions.get(j), lemmas.get(j));

                }
                o.addReplacement(sentences.get(i),false);
                o.addPos(posTags.get(i));
                System.err.println(literals.get(i));
                System.err.println(ourLemmas);
                o.addLemmas(ourLemmas);
                o.addNEs(ners);
                o.setId(graphID);

                output.add(o);
            } catch (Exception ex) { //PreprocessingException
                System.err.println("Ignoring exception");
                ex.printStackTrace();
             }
            
        }
        
        AmConllSentence.writeToFile(cli.outPath+"/"+cli.prefix+".amconll", output);
        
        
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
