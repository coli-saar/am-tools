/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.ucca.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.*;
import de.saar.coli.amrtagging.formalisms.ucca.UCCABlobUtils;
import de.saar.coli.amrtagging.mrp.ucca.UCCA;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.ling.TaggedWord;

import java.io.File;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Create UCCA training data (AMConll).
 *
 * @author matthias, Mario
 */
public class CreateCorpus {
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus ")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Mario/alto_reverted 2/training.txt";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Mario/alto_reverted 2/";

    @Parameter(names = {"--prefix", "-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")
//, required=true)
    private String prefix = "train";
    
    @Parameter(names = {"--companion"}, description = "Path to companion data.")//, reqteuired = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/ucca/all_ucca.conllu";

    @Parameter(names = {"--merge-uiuc-ner"}, description = "Compute UIUC NER tags and merge them")
    private boolean mergeUiucNer = false;

    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;

    @Parameter(names = {"--debug"}, description = "Enables debug mode")
    private boolean debug = true;

    @Parameter(names = {"--help", "-?", "-h"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;
    
    @Parameter(names = {"--timeout"}, description = "maximum runtime of the tree-automaton step per thread, in seconds. Default = 1800 (=30 mins)")
    private int timeout = 1800;


    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException, CorpusReadingException, PreprocessingException {
        CreateCorpus cli = new CreateCorpus();
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

        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation("id", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("flavor", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("framework", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("version", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("time", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("spans", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("input", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("string", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("graph", new Interpretation(new GraphAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("alignment", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));

        Corpus corpus = Corpus.readCorpusWithStrictFormatting(new FileReader(cli.corpusPath), loaderIRTG);

        int counter = 0;
        int problems = 0;
        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        
        PreprocessedData preprocData = new MrpPreprocessedData(new File(cli.companion));

        NamedEntityRecognizer ner = null;
        if( cli.mergeUiucNer) {
            ner = new UiucNamedEntityRecognizer();
        }
        
        UCCA ucca = new UCCA();
        
        Set<String> companionIds = ConlluSentence.readFromFile(cli.companion).stream().map((ConlluSentence s) -> s.getId()).collect(Collectors.toSet());
        for (Instance corpusInstance : corpus){
            String id = ((List<String>) corpusInstance.getInputObjects().get("id")).get(0);
            if (!  companionIds.contains(id)){
                System.err.println("Check companion data! We don't have an analysis for the sentence belonging to graph "+id);
            }
        }


        if (cli.vocab != null) { //vocab given, read from file
            supertagDictionary.readFromFile(cli.vocab);
        }

        instanceLoop:
        for (Instance corpusInstance : corpus) {
            if (counter % 10 == 0 && counter > 0) {
                System.err.println(counter);
                System.err.println("decomposable so far " + 100 * (1.0 - (problems / (float) counter)) + "%");
            }
            if (counter % 1000 == 0 && counter > 0) { //every now and then write intermediate results.
                cli.write(outCorpus, supertagDictionary);
            }
            
            //if (problems > 30) break;
            counter++;

            //read graph, string and alignment from corpus
            String id = ((List<String>) corpusInstance.getInputObjects().get("id")).get(0);
            String inputString = ((List<String>) corpusInstance.getInputObjects().get("input")).stream().collect(Collectors.joining(" "));
            String version = ((List<String>) corpusInstance.getInputObjects().get("version")).get(0);
            String time = ((List<String>) corpusInstance.getInputObjects().get("time")).get(0);
            String framework = ((List<String>) corpusInstance.getInputObjects().get("framework")).get(0);
            String flavor = ((List<String>) corpusInstance.getInputObjects().get("flavor")).get(0);
            
            List<String> tokenRanges = (List) corpusInstance.getInputObjects().get("spans");
            SGraph graph = (SGraph) corpusInstance.getInputObjects().get("graph");
            List<String> sentence = (List) corpusInstance.getInputObjects().get("string");
            List<String> als = (List) corpusInstance.getInputObjects().get("alignment");

//            sentence = ucca.refineTokens(sentence); // no longer needed, see #67


            // read alignments

            if (als.size() == 1 && als.get(0).equals("")) {
                //System.err.println("Repaired empty alignment!");
                als = new ArrayList<>();
            }

            String[] alStrings = als.toArray(new String[0]);
            List<Alignment> alignments = new ArrayList<>();

            for (String alString : alStrings) {
                Alignment al = Alignment.read(alString, 0);
                alignments.add(al);
            }


            // create MRInstance object that bundles the three:
            MRInstance inst = new MRInstance(sentence, graph, alignments);
            try {
                inst.checkEverythingAligned();
            } catch (Exception e){
                System.err.println("Ignoring an exception:");
                System.err.println("id " + id);
                e.printStackTrace();
                problems++;
                continue;
            }
            //System.out.println(GraphvizUtils.simpleAlignViz(inst, true)); //this generates a string that you can compile with graphviz dot to get a visualization of what the grouping induced by the alignment looks like.
            //System.out.println(inst.getSentence());
            //System.out.println(inst.getAlignments());
            //System.out.println(inst.getGraph().);
            //SGraphDrawer.draw(inst.getGraph(), ""); //display graph
            //break;
            ConcreteAlignmentSignatureBuilder sigBuilder = new ConcreteAlignmentSignatureBuilder(inst.getGraph(), inst.getAlignments(), new UCCABlobUtils());
            try {
                ConcreteAlignmentTrackingAutomaton auto = ConcreteAlignmentTrackingAutomaton.create(inst, sigBuilder, false);
                try {
                    auto.processAllRulesBottomUp(null, cli.timeout*1000);
                } catch (InterruptedException ex) {
                    System.err.println("Decomposition of graph "+id+" interrupted after "+cli.timeout+" seconds. Will be excluded in output.");
                }
                Tree<String> t = auto.viterbi();

                if (t != null) { //graph can be decomposed
                    //SGraphDrawer.draw(inst.getGraph(), ""); //display graph
                    AmConllSentence sent = AmConllSentence.fromIndexedAMTerm(t, inst, supertagDictionary);
                    sent.setAttr("git", AMToolsVersion.GIT_SHA);
                    sent.setAttr("id", id);
                    sent.setAttr("input", inputString);
                    sent.setAttr("flavor", flavor);
                    sent.setAttr("time", time);
                    sent.setAttr("framework",framework);
                    sent.setAttr("version", version);
                    sent.addRanges(tokenRanges.stream().map((String range) -> TokenRange.fromString(range)).collect(Collectors.toList()));
                    
                    
                    //Sentence stanfAn = new Sentence(inst.getSentence());

                    sent.addPos(preprocData.getPosTags(id).stream().map((TaggedWord w) -> w.tag()).collect(Collectors.toList()));

                    //sent.addNEs(stanfAn.nerTags()); //slow, only add this for final creation of training data

                    sent.addLemmas(preprocData.getLemmas(id));
                    ucca.refineDelex(sent);
                    outCorpus.add(sent); //done with this sentence
                    //we can also create an AM dependency tree now
                    AMDependencyTree amdep = AMDependencyTree.fromSentence(sent);
                    //use one of these to get visualizations
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getDelexSupertag() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
                    //amdep.getTree().map(ent -> ent.getForm() +" "+ent.getEdgeLabel()).draw();

                    //this is how we can get back the graph (with alignment to positions where the individual parts came from):
                    //SGraph alignedGraph = amdep.evaluate(true);
                    //SGraphDrawer.draw(alignedGraph, "Reconstructed Graph");

                } else {
                    problems ++;
                    System.err.println("id "+id);
                    System.err.println(inst.getGraph());
                    System.err.println("not decomposable " + inst.getSentence());
                    if (cli.debug){
                        for (Alignment al : inst.getAlignments()){
                            System.err.println(inst.getSentence().get(al.span.start));
                            System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                        }
                        System.err.println(GraphvizUtils.simpleAlignViz(inst, true));
                    }
                    System.err.println("=====end not decomposable=====");
                }
            } catch (Exception ex) {
                System.err.println("Ignoring an exception:");
                System.err.println("id "+id);
                System.err.println(inst.getSentence());
                ex.printStackTrace();
                problems++;
                if (cli.debug){
                        for (Alignment al : inst.getAlignments()){
                            System.err.println(inst.getSentence().get(al.span.start));
                            try {
                                System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                            } catch (IllegalArgumentException ex2){
                                System.err.println("[]"); //print empty list
                            } catch (Exception e){
                                System.err.println(e.getMessage());
                            }
                            
                        }
                        System.err.println(GraphvizUtils.simpleAlignViz(inst, true));
                        System.err.println("=====end not decomposable=====");
                }
            }
        }

        System.err.println("ok: " + (counter - problems));
        System.err.println("total: " + counter);
        System.err.println("i.e. " + 100 * (1.0 - (problems / (float) counter)) + "% translated correctly");
        cli.write(outCorpus, supertagDictionary);
    }


    private void write(ArrayList<AmConllSentence> outCorpus, SupertagDictionary supertagDictionary) throws IOException {
        if (outPath != null && prefix != null) {
            AmConllSentence.writeToFile(outPath + "/" + prefix + ".amconll", outCorpus);
            if (vocab == null) { //only write vocab if it wasn't restored.
                supertagDictionary.writeToFile(outPath + "/" + prefix + "-supertags.txt");
            }
        }
    }


}
    

    

