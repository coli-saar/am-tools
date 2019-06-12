/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds.tools;

import de.saar.coli.amrtagging.formalisms.sdp.dm.tools.*;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractorCLI;
import de.saar.coli.amrtagging.formalisms.amr.tools.ReadRawCorpus;
import de.saar.coli.amrtagging.formalisms.eds.EDSBlobUtils;
import de.saar.coli.amrtagging.formalisms.eds.EDSConverter;
import de.saar.coli.amrtagging.formalisms.eds.EDSUtils;
import de.saar.coli.amrtagging.formalisms.eds.PostprocessLemmatize;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.simple.Sentence;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import se.liu.ida.nlp.sdp.toolkit.graph.*;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;


import java.util.ArrayList;
import java.util.List;

/**
 *  Create DM training data.
 * @author matthias
 */
public class CreateCorpusParallel {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof", required = true)
    private String corpusPath ;

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files", required = true)
    private String outPath ;
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "train";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode")
    private boolean debug=false;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
        CreateCorpusParallel cli = new CreateCorpusParallel();
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
        List<Integer> numbers = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        int i = 0;
        while ((line = br.readLine()) != null){
            if (line.startsWith("# ::id ")){
                ids.add(line.substring("# ::id ".length()));
            }
            if (line.startsWith("# ::snt ")) {
                allSents.add(line.substring("# ::snt ".length()));
                numbers.add(i);
                i++;
            }
        }
        br.close();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        ArrayList<String> edmCorpus = new ArrayList<>();
        ArrayList<String> amrCorpus = new ArrayList<>();
        
        numbers.stream().parallel().forEach((Integer lineIndex) -> {
            
            MRInstance inst = EDSConverter.toSGraph(allGraphs.get(lineIndex), allSents.get(lineIndex));
            
            try {
                ConcreteAlignmentSignatureBuilder sigBuilder = new ConcreteAlignmentSignatureBuilder(inst.getGraph(), inst.getAlignments(), new EDSBlobUtils());
                AlignmentTrackingAutomaton auto = AlignmentTrackingAutomaton.create(inst,sigBuilder, false);
                auto.processAllRulesBottomUp(null);
                Tree<String> t = auto.viterbi();

                if (t != null){
                    ConllSentence sent = ConllSentence.fromIndexedAMTerm(t, inst, supertagDictionary);
                    EDSUtils.checkSentence(sent); //check if something bad is happening and print if that's the case
                    sent.setAttr("id", ids.get(lineIndex));
                    sent.setAttr("raw", allSents.get(lineIndex));
                    Sentence stanfAn = new Sentence(inst.getSentence());

                    List<String> posTags = new ArrayList<>(stanfAn.posTags());
                    sent.addPos(posTags);

                    List<String> neTags = new ArrayList<>(stanfAn.nerTags());
                    sent.addNEs(neTags);

                    List<String> lemmata = new ArrayList<>(stanfAn.lemmas());
                    sent.addLemmas(lemmata);
                    PostprocessLemmatize.edsLemmaPostProcessing(sent);
                    
                    String edm = EDSConverter.toEDM(allGraphs.get(lineIndex));
                    SGraph amr = EDSConverter.undoExplicitAnon(EDSConverter.makeNodeNamesExplicit(allGraphs.get(lineIndex)));
                    String amrStr = EDSUtils.stripLnks(amr).toIsiAmrString();
                    synchronized(outCorpus){
                        synchronized(amrCorpus){
                            synchronized(edmCorpus){
                            outCorpus.add(sent);
                            edmCorpus.add(edm);
                            amrCorpus.add(amrStr);
                            if (outCorpus.size() % 10 == 0){
                                System.err.println(outCorpus.size());
                            }
                            if (outCorpus.size() % 1000 == 0 || (outCorpus.size() > 22000 && outCorpus.size() % 10 == 0 ) ){
                                synchronized(supertagDictionary){
                                    cli.write(outCorpus,edmCorpus, amrCorpus, supertagDictionary);
                                }
                            }
                            }
                        }
                        
                    }

                } else {
                    System.err.println("not decomposable " + inst.getSentence());
                    if (cli.debug){
                        for (Alignment al : inst.getAlignments()){
                            System.err.println(inst.getSentence().get(al.span.start));
                            System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                        }
                    }
                }
            } catch (Exception ex){
                System.err.println("Ignoring an exception:");
                ex.printStackTrace();
            }
        });
        System.err.println("ok: "+outCorpus.size());
        System.err.println("total: "+numbers.size());
        System.err.println("losing " + 100*(1.0 - ((float)outCorpus.size() / (float)numbers.size()))+ "%");
        cli.write(outCorpus,edmCorpus, amrCorpus, supertagDictionary);
        
    }
    
    
        private void write(ArrayList<ConllSentence> outCorpus, ArrayList<String>edmCorpus,ArrayList<String> amrCorpus,SupertagDictionary supertagDictionary) throws IOException{
            if (outPath != null && prefix != null){
                ConllSentence.writeFile(outPath+"/"+prefix+".amconll", outCorpus);
                if (vocab == null){ //only write vocab if it wasn't restored.
                    supertagDictionary.writeToFile(outPath+"/"+prefix+"-supertags.txt");
                }
                PrintWriter goldEDM = new PrintWriter(outPath+"/"+prefix+"-gold.edm");
                PrintWriter goldAMR = new PrintWriter(outPath+"/"+prefix+"-gold.amr.txt");
                for (String s : edmCorpus){
                    goldEDM.println(s);
                }
                goldEDM.close();
                for (String s : amrCorpus){
                    goldAMR.println(s);
                    goldAMR.println();
                }
                goldAMR.close();
            }
        }
        
 
        
}
    

    

