/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import static de.saar.coli.amrtagging.formalisms.amr.tools.PrepareTestDataFromFiles.readFile;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import edu.stanford.nlp.simple.Sentence;

/**
 * Tool to create amconll file from nnData/train
 * @author matthias
 */
public class ToAMConll {
    @Parameter(names = {"--corpusPath", "-c"}, description = "Path to the input corpus") //required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR/2017/train/";

    @Parameter(names = {"--outPath", "-o"}, description = "Prefix for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/AMR-toy/2017/";
    
    
    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;
    
    /**
     * Command line interface for the DependencyExtractor class; call with --help to see options.
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws CorpusReadingException
     * @throws IllegalArgumentException
     * @throws ParseException
     * @throws InterruptedException 
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, CorpusReadingException, IllegalArgumentException, ParseException, InterruptedException, AMDependencyTree.ConllParserException, ParserException {
        
        ToAMConll cli = new ToAMConll();
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
       List<List<String>> ops = readFile(cli.corpusPath+"/ops.txt");
       List<List<String>> tags = readFile(cli.corpusPath+"/tags.txt");
       List<List<String>> labels = readFile(cli.corpusPath+"/labels.txt");
        
        if (sentences.size() != literals.size() || literals.size() != posTags.size()){
            System.err.println("Inputs seem to have different lengths: "+sentences.size()+" "+literals.size()+posTags.size());
            return;
        }
        ApplyModifyGraphAlgebra ga = new ApplyModifyGraphAlgebra();
        List<ConllSentence> output = new ArrayList<>();
        
        sents:
        for (int i = 0; i < sentences.size();i++){
            if (i % 1000 == 0){
                System.err.println(i);
            }
            ConllSentence o = new ConllSentence();
            List<String> expandedWords = new ArrayList<>();
            List<Integer> origPositions = new ArrayList<>();
            List<String> ners = new ArrayList<>();
            for (int id = 0; id < sentences.get(i).size();id++){
                ConllEntry e = new ConllEntry(id+1, literals.get(i).get(id));
                String tag = tags.get(i).get(id).replaceAll("__ALTO_WS__", " ");
                if (!tag.equals("NULL")){
                    String[] infos = tag.split("--TYPE--");
                    e.setDelexSupertag(infos[0]);
                    try {
                        e.setType(ga.parseString(tag).getRight());
                    } catch (ParserException ex) {
                        System.err.println("Couldn't parse tag "+tag);
                        System.err.println("Will skip the sentence");
                        continue sents;
                    }
                }
                String label = labels.get(i).get(id);
                if (! label.equals("NULL")){
                    e.setLexLabel(label);
                }
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
            o.addLemmas(ourLemmas);
            o.addNEs(ners);
            
            //now we add the edges
            HashSet<Integer> hasOutgoing = new HashSet<>();
            HashSet<Integer> hasIncoming = new HashSet<>();
            for (String edge : ops.get(i)){
                if (edge.equals("")){
                    break;
                }
                String[] infos = edge.split("\\[");
                String label = infos[0];
                String fromTo[] = infos[1].replace("]","").split(",");
                int from = Integer.parseInt(fromTo[0]);
                int to = Integer.parseInt(fromTo[1]);
                hasOutgoing.add(from);
                hasIncoming.add(to);
                o.get(to).setHead(from+1); //1-based indexing
                o.get(to).setEdgeLabel(label);
            }
            hasOutgoing.removeAll(hasIncoming);
            if (hasOutgoing.isEmpty()){
                int found = 0;
                for (ConllEntry e : o){
                    if (!e.getDelexSupertag().equals(ConllEntry.DEFAULT_NULL)){
                        found++;
                        e.setEdgeLabel("ROOT");
                    }
                }
                if (found != 1){
                    System.err.println("Sentence has no unique root?");
                }
            } else if (hasOutgoing.size() == 1){
                 int rootIndex = hasOutgoing.iterator().next();
                 o.get(rootIndex).setEdgeLabel("ROOT");
            } else {
                System.err.println("Sentence has multiple roots?");
            }
            for (ConllEntry e : o){
                if (e.getEdgeLabel().equals(ConllEntry.DEFAULT_NULL)){
                    e.setEdgeLabel("IGNORE");
                }
            }
            try {
                AMDependencyTree amdep = AMDependencyTree.fromSentence(o);
                amdep.evaluate(false); //sanity check
               
                output.add(o);
            } catch (Exception e){
                System.err.println("Ignoring problem:");
                System.err.println(o);
                e.printStackTrace();
            }
            
        }
      
      ConllSentence.writeToFile(cli.outPath+"/corpus.amconll", output);
    }
    
    
    
}
