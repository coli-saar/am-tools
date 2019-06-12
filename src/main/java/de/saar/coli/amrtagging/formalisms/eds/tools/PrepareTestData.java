/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds.tools;

import de.saar.coli.amrtagging.formalisms.sdp.tools.*;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.tools.ReadRawCorpus;
import de.saar.coli.amrtagging.formalisms.eds.EDSConverter;
import de.saar.coli.amrtagging.formalisms.eds.EDSUtils;
import de.saar.coli.amrtagging.formalisms.eds.PostprocessLemmatize;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;

import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

/**
 * Prepare test data to be in the format we use for the parser. Strips off all gold information but the tokenization.
 * @author matthias
 */
public class PrepareTestData {
    @Parameter(names = {"--corpus", "-c"}, description = "Points to the input corpus (en.dm.sdp) or subset thereof", required = true)
    private String corpusPath ;

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files", required = true)
    private String outPath ;
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "test_like";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException{
        PrepareTestData cli = new PrepareTestData();
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
        
        List<SGraph> allGraphs = ReadRawCorpus.readGraphs(cli.corpusPath);
        BufferedReader br = new BufferedReader(new FileReader(cli.corpusPath));
        String line;
        List<String> allSents = new ArrayList<>(); 
        List<String> ids = new ArrayList<>();
        while ((line = br.readLine()) != null){
            if (line.startsWith("# ::id ")){
                ids.add(line.substring("# ::id ".length()));
            }
            if (line.startsWith("# ::snt ")) {
                allSents.add(line.substring("# ::snt ".length()));
            }
        }
        br.close();

        PrintWriter goldEDM = new PrintWriter(cli.outPath+cli.prefix+"-gold.edm");
        PrintWriter goldAMR = new PrintWriter(cli.outPath+cli.prefix+"-gold.amr.txt");

        ArrayList<ConllSentence> out = new ArrayList<ConllSentence>();
        for (int i = 0; i < allGraphs.size(); i++){
            ConllSentence currentSent = new ConllSentence();
            currentSent.setAttr("id", ids.get(i));
            currentSent.setAttr("raw", allSents.get(i));
            List<String> words = EDSUtils.edsTokenizeString(allSents.get(i),true).getRight();
            int wordId = 1;
            for (String word : words){
                  currentSent.add(new ConllEntry(wordId,word));
                  wordId++;
            }
            Sentence stanfordSent = new Sentence(words);
            
            ArrayList<String> posTags = new ArrayList<>(stanfordSent.posTags());
            
            ArrayList<String> lemmas = new ArrayList<>(stanfordSent.lemmas());
            
            ArrayList<String> neTags = new ArrayList<>(stanfordSent.nerTags());
            
            currentSent.addLemmas(lemmas);
            currentSent.addPos(posTags);
            currentSent.addNEs(neTags);
            PostprocessLemmatize.edsLemmaPostProcessing(currentSent);
            out.add(currentSent);
            
            goldEDM.println(EDSConverter.toEDM(allGraphs.get(i)));
            
            //in AMR notation: make sure that node names are displayed correctly
            SGraph amr = EDSConverter.undoExplicitAnon(EDSConverter.makeNodeNamesExplicit(allGraphs.get(i)));
            amr = EDSUtils.stripLnks(amr); //remove pseudo lnks
            goldAMR.println(amr.toIsiAmrString());
            goldAMR.println();
            
        } 
        ConllSentence.writeFile(cli.outPath+"/"+cli.prefix+".amconll", out);
        goldEDM.close();
        goldAMR.close();
    }
}
