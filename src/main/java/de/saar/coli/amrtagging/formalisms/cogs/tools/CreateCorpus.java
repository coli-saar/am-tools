package de.saar.coli.amrtagging.formalisms.cogs.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.cogs.COGSBlobUtils;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm;
import de.saar.coli.amrtagging.formalisms.cogs.LogicalFormConverter;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
//import edu.stanford.nlp.simple.Sentence;
import java.io.IOException;

import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;

import java.util.ArrayList;

/**
 * Create COGS training data. Takes COGS TSV-file (input, output, gentype) and writes AMCoNLL file
 *
 * TODO check whether postprocessing works (graph1 -> am dep tree -> graph2; assert graph1 equals graph2)
 * TODO edge2source missing
 * @author piaw (weissenh)
 */
public class CreateCorpus {
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (.tsv)")  //, required = true)
    private String corpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/graphparsing/toy/toy2.tsv";
    // private String corpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/train.tsv";
    // private String corpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/train_100.tsv";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")  //, required = true)
    private String outPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/graphparsing/amconll/";

    @Parameter(names = {"--prefix", "-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)") //, required=true)
    private String prefix = "train";

    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;

    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug = false;

    @Parameter(names = {"--help", "-?", "-h"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;


    public static void main(String[] args) throws IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException {
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

        RawCOGSReader reader = new RawCOGSReader(cli.corpusPath);
        SGraph cogsGraph;
        int counter = 0;
        int problems = 0;
        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        Scorer overall = new Scorer();

        if (cli.vocab != null) {
            supertagDictionary.readFromFile(cli.vocab);
        }
        while (reader.hasNext()) {
            RawCOGSReader.CogsSample sample = reader.getNextSample();
            if (counter % 10 == 0 && counter > 0) {
                System.err.println(counter);
                System.err.println("decomposable so far " + 100 * (1.0 - (problems / (float) counter)) + "%");
                System.err.println("Overall reconstruction F-Score: " + overall.getF1() + " and exact match " + overall.getExactMatch());
            }
            if (counter % 1000 == 0 && counter > 0) { //every now and then write intermediate results.
                cli.write(outCorpus, supertagDictionary);
            }
            counter++;
            COGSLogicalForm lf = new COGSLogicalForm(sample.tgt_tokens);
            MRInstance inst = LogicalFormConverter.toSGraph(lf, sample.src_tokens);
            //System.out.println(inst.getSentence());
            //System.out.println(inst.getAlignments());
            //System.out.println(inst.getGraph());
            AMRSignatureBuilder sigBuilder = new AMRSignatureBuilder();
            sigBuilder.blobUtils = new COGSBlobUtils();
            try {
                AlignmentTrackingAutomaton auto = AlignmentTrackingAutomaton.create(inst, sigBuilder, false);
                auto.processAllRulesBottomUp(null);
                Tree<String> t = auto.viterbi();

                if (t != null) {
                    //SGraphDrawer.draw(inst.getGraph(), "");
                    AmConllSentence sent = AmConllSentence.fromIndexedAMTerm(t, inst, supertagDictionary);
                    // sent.setAttr("id", sdpGraph.id);
                    // sent.setAttr("lineno", String.valueOf(reader.getLinenumber()));
                    sent.setAttr("framework", "cogs");
                    sent.setAttr("sentence", sample.getSentenceAsString());
                    sent.setAttr("logical_form", sample.getLogicalFormAsString());
                    sent.setAttr("generalization_type", sample.generalizationType);
                    sent.setAttr("git", AMToolsVersion.GIT_SHA);
//                    Sentence stanfAn = new Sentence(inst.getSentence().subList(0, inst.getSentence().size() - 1)); //remove artifical root "word"

                    // todo do we need pos tags, ne tags, lemmata?
//                    List<String> posTags = SGraphConverter.extractPOS(sdpGraph);
//                    posTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
//                    sent.addPos(posTags);

                    outCorpus.add(sent);
                    AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(sent);
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getDelexSupertag() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();

                    SGraph alignedGraph = amdep.evaluate(true);
                    /*  TODO  convert graph back to logical form and see whether it is still the same!
                    Graph emptyCopy = new Graph(sdpGraph.id);
                    sdpGraph.getNodes().forEach(node -> emptyCopy.addNode(node.form, node.lemma, node.pos, false, false, ""));

                    Graph convertedGraph = SGraphConverter.toSDPGraph(alignedGraph, emptyCopy);
                    Scorer scorer = new Scorer();
                    scorer.update(sdpGraph, convertedGraph);
                    overall.update(sdpGraph, convertedGraph);
                    if (scorer.getF1() != 1.0) { //|| scorer.getCorePredicationsF1() != 1.0 || scorer.getSemanticFramesF1() != 1.0){
                        System.err.println("Reconstructing SDP Graph didn't work completely for: " + inst.getSentence());
                        System.err.println("Precision " + scorer.getPrecision() + " Recall " + scorer.getRecall() + " corePredications F " + scorer.getCorePredicationsF1() + " semantic frames F " + scorer.getSemanticFramesF1());
                    }*/
                } else {
                    problems++;
                    System.err.println("not decomposable " + inst.getSentence());
                    if (cli.debug) {
                        for (Alignment al : inst.getAlignments()) {
                            System.err.println(inst.getSentence().get(al.span.start));
                            System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                        }
                    }
                    if (problems > 1) { //ignore the first problems
                        //SGraphDrawer.draw(inst.getGraph(), "");
                        //break;
                    }
                }
            } catch (Exception ex) {
                System.err.println("Ignoring an exception:");
                ex.printStackTrace();
            }
        }
        System.err.println("ok: " + (counter - problems));
        System.err.println("total: " + counter);
        System.err.println("i.e. " + 100 * (1.0 - (problems / (float) counter)) + "%");
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
