package de.saar.coli.amtools.decomposition;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.cogs.COGSBlobUtils;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm;
import de.saar.coli.amrtagging.formalisms.cogs.LF2GraphConverter;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.codec.BinaryIrtgOutputCodec;
import de.up.ling.irtg.util.Counter;
import org.apache.commons.lang.NotImplementedException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Given a train and dev corpus in the native COGS format (TSV), to decomposition and source assignment automata
 *
 * So far only supports <i>automata</i> as algorithm (other SourceAutomataCLI s support more options)<br>
 * Run with the the option <i>--help</i> to display usage info.
 * @author pia (weissenh)
 */
public class SourceAutomataCLICOGS {

    @Parameter(names = {"--trainingCorpus", "-t"}, description = "Path to the input training corpus (*.tsv file)")//, required = true)
    private String trainingCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/COGS/datasmall/train20.tsv";
//    private String trainingCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/COGS/data/train.tsv";

    @Parameter(names = {"--devCorpus", "-d"}, description = "Path to the input dev corpus (*.tsv file)")//, required = true)
    private String devCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/COGS/datasmall/dev20.tsv";
//    private String devCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/COGS/data/dev.tsv";

    @Parameter(names = {"--outPath", "-o"}, description = "Path to output folder where amconll and supertag dictionary files are created")//, required = true)
    private String outPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/graphparsing/amconll/";

    @Parameter(names = {"--nrSources", "-s"}, description = "how many sources to  (default = 3)")//, required = true)
    private int nrSources = 3;

    // @Parameter(names = {"--iterations"}, description = "max number of EM iterations")//, required = true)
    // private int iterations = 100;

    // @Parameter(names = {"--difference"}, description = "difference in log likelihood for early EM stopping")//, required = true)
    // private double difference = 0.1;

    @Parameter(names = {"--algorithm", "-a"}, description = "so far, only allowed option is: automata")//, required = true)
    private String algorithm = "automata";

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    public static void main(String[] args) throws IOException, ParserException, ClassNotFoundException {
        // read command line arguments
        // cli stands for CommandLineInterface
        SourceAutomataCLICOGS cli = new SourceAutomataCLICOGS();
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
        boolean noPrimitives = true;  // exclude primitives for now todo change later! (inly debugging)

        AMRBlobUtils blobUtils = new COGSBlobUtils();

        SupertagDictionary supertagDictionary = new SupertagDictionary();//future: load from file for dev set (better: get dev scores from training EM)

        //get automata for training set
        List<MRInstance> trainCorpus = getSamplesFromFile(cli.trainingCorpusPath, noPrimitives);

        List<TreeAutomaton<?>> concreteDecompositionAutomata = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomata = new ArrayList<>();
        List<DecompositionPackage> decompositionPackages = new ArrayList<>();

        cli.processCorpus(trainCorpus, blobUtils, concreteDecompositionAutomata, originalDecompositionAutomata, decompositionPackages);

/*
        //get automata for dev set
        List<MRInstance> devCorpus = getSamplesFromFile(cli.devCorpusPath, noPrimitives);

        List<TreeAutomaton<?>> concreteDecompositionAutomataDev = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomataDev = new ArrayList<>();
        List<DecompositionPackage> decompositionPackagesDev = new ArrayList<>();

        cli.processCorpus(devCorpus, blobUtils, concreteDecompositionAutomataDev, originalDecompositionAutomataDev, decompositionPackagesDev);
        // todo also deen to comment line createAutomataZip...
*/
        Files.createDirectories(Paths.get(cli.outPath));

        if (cli.algorithm.equals("automata")) {
            createAutomataZip(originalDecompositionAutomata, decompositionPackages, supertagDictionary, "train", cli.outPath);
            // createAutomataZip(originalDecompositionAutomataDev, decompositionPackagesDev, supertagDictionary, "dev", cli.outPath);

        } else {
            throw new NotImplementedException("For COGS only 'automata' option implemented so far");
            // see SDP or AMR for other options
        }
    }

    // todo transform this into a class or have a separate file for this? this is a very general function...
    private static List<MRInstance> getSamplesFromFile(String filename, boolean noPrimitives) throws IOException {
        BufferedReader samplesBR = new BufferedReader(new FileReader(filename));
        List<MRInstance> samples = new ArrayList<>();
        String line;
        int lineno = 0;
        while (samplesBR.ready()) {
            line = samplesBR.readLine();
            List<String> samplerow = Arrays.asList(line.split("\t"));
            lineno += 1;
            // todo maybe check if line is empty? is there a better exception class than runtime exception?
            if (samplerow.size()!= 3) {  // rows: sentence, logical form, generalization_type
                throw new RuntimeException("Each line must consist of 3 rows (tab-separated): " +
                        "Failed for " + lineno+"\n");
            }
            String sentence = samplerow.get(0);
            String logicalformstr = samplerow.get(1);
            String gen_type = samplerow.get(2);

            List<String> src_tokens = Arrays.asList(sentence.split(" "));  // ["The", "boy", "wanted", "to", "go", "."]
            List<String> tgt_tokens = Arrays.asList(logicalformstr.split(" "));  // ["*", "boy", "(", "x", "_", "1", ")", ";", "want" , ... ]

            COGSLogicalForm lf = new COGSLogicalForm(tgt_tokens);
            MRInstance mr = LF2GraphConverter.toSGraph(lf, src_tokens);
            try {
                if (!noPrimitives || lf.getFormulaType()== COGSLogicalForm.AllowedFormulaTypes.IOTA) {
                    samples.add(mr);
                }
                mr.checkEverythingAligned();
//                samples.add(mr);
            } catch (MRInstance.UnalignedNode unalignedNode) {
                System.err.println("Alignment problem detected for following logical form: " + logicalformstr);
                // System.err.println("Unaligned for LF type: " + lf.getFormulaType());
                if (lf.getFormulaType() != COGSLogicalForm.AllowedFormulaTypes.LAMBDA) {
                    unalignedNode.printStackTrace();
                }
                // else {
                    // System.err.println("to do: fix the lambda alignment problem");
                // }
            } catch (MRInstance.MultipleAlignments multipleAlignments) {
                System.err.println("Alignment problem detected for following logical form: " + logicalformstr);
                // System.err.println("Multiple Aligned for LF type: " + lf.getFormulaType());
                multipleAlignments.printStackTrace();
            }
        } // buffer ready
        return samples;
    }

    // todo change process corpus to fit cogs
    // mostly copied from AMR version (SourceAutomataCLIAMR) and SDP one (SourceAutomataCLI)
    private void processCorpus(List<MRInstance> corpus, AMRBlobUtils blobUtils,
                               List<TreeAutomaton<?>> concreteDecompositionAutomata, List<SourceAssignmentAutomaton> originalDecompositionAutomata,
                               List<DecompositionPackage> decompositionPackages) throws IOException {

        int[] buckets = new int[]{0, 3, 10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000, 300000, 1000000};
        Counter<Integer> bucketCounter = new Counter<>();
        Counter<String> successCounter = new Counter<>();
        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        for (MRInstance inst: corpus) {
            if (index % 500 == 0) {
                System.err.println("At instance number " + index);
                bucketCounter.printAllSorted();
            }
            if (true) { //index == 1268
                SGraph graph = inst.getGraph();
                try {
                    DecompositionPackage decompositionPackage = new COGSDecompositionPackage(inst, blobUtils);
                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, decompositionPackage);
                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, blobUtils);
                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton);
                    try {
                        SGraph resultGraph = result.evaluate().left;
                        resultGraph.removeNode("ART-ROOT");
                        graph.setEqualsMeansIsomorphy(false);
                        if (graph.equals(resultGraph)) {
                            SourceAssignmentAutomaton auto = SourceAssignmentAutomaton
                                    .makeAutomatonWithAllSourceCombinations(result, nrSources, decompositionPackage);
                            ConcreteTreeAutomaton<SourceAssignmentAutomaton.State> concreteTreeAutomaton = auto.asConcreteTreeAutomatonBottomUp();
//                            System.out.println(auto.signature);
                            //System.out.println(result);
//                            System.out.println(concreteTreeAutomaton);
//                            System.out.println(concreteTreeAutomaton.viterbi());
                            if (concreteTreeAutomaton.viterbi() != null) {
                                successCounter.add("success");
                                concreteTreeAutomaton = (ConcreteTreeAutomaton<SourceAssignmentAutomaton.State>)concreteTreeAutomaton.reduceTopDown();
                                concreteDecompositionAutomata.add(concreteTreeAutomaton);
                                decompositionPackages.add(decompositionPackage);
                                originalDecompositionAutomata.add(auto);
//                                if (concreteTreeAutomaton.getNumberOfRules() < 30) {
//                                    System.err.println(concreteTreeAutomaton);
//                                    System.err.println();
//                                    System.err.println();
//                                }
                            } else {
                                successCounter.add("fail");
                            }
//                            System.out.println(concreteTreeAutomaton.reduceTopDown().getNumberOfRules());
                            int automatonSize = (int)concreteTreeAutomaton.reduceTopDown().getNumberOfRules();
                            OptionalInt bucket = Arrays.stream(buckets).filter(bucketSize -> automatonSize > bucketSize).max();
                            if (bucket.isPresent()) {
                                bucketCounter.add(bucket.getAsInt());
                            }
//                            System.out.println();
                        } else {
                            System.err.println("--(Evaluation)Converting AM dependency tree back to graph yielded a different graph than expected: (index,gold,yielded)");
                            System.err.println(index);
                            System.err.println(graph.toIsiAmrStringWithSources());
                            System.err.println(resultGraph.toIsiAmrStringWithSources());
                            fails++;
                        }
                    } catch (java.lang.Exception ex) {
                        System.err.println("--(Evaluation) Something else happened at this index/sentence: ");
                        System.err.println(index);
                        System.err.println(inst.getSentence().toString());
                        System.err.println(graph.toIsiAmrStringWithSources());
                        System.err.println(result);
                        ex.printStackTrace();
                        fails++;
                    } // checking evaluation results
                } catch (DAGComponent.NoEdgeToRequiredModifieeException | DAGComponent.CyclicGraphException ex) {
                    nondecomposeable++;
                } catch (java.lang.Exception ex) {
                    System.err.println("--(Before evaluation) Something else happened at this index: ");
                    System.err.println(index);
//                    System.err.println(graph.toIsiAmrStringWithSources());
                    ex.printStackTrace();
                    fails++;
                }
            } // if true

            index++;
        }
        System.err.println("#Nondecomposable: " + nondecomposeable);
        System.err.println("#Fails:           " + fails);
        System.err.println("Automata sizes: [automata size : count]");
        bucketCounter.printAllSorted();
        System.err.println("Success vs fail counts: ");
        successCounter.printAllSorted();
    }


    // todo just copied from sourceautomatacli: can I prevent copying code???
    static void createAutomataZip(List<SourceAssignmentAutomaton> originalDecompositionAutomata,
                                  List<DecompositionPackage> decompositionPackages,
                                  SupertagDictionary supertagDictionary, String zipFileName, String outPath) throws IOException {
        //create zip file
        System.err.println("Writing zip file "+Paths.get(outPath, zipFileName+ ".zip"));
        ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(Paths.get(outPath, zipFileName+ ".zip").toFile()));

        //write metadata file
        ZipEntry meta = new ZipEntry("meta.txt");
        zipFile.putNextEntry(meta);
        zipFile.write(Integer.toString(originalDecompositionAutomata.size()).getBytes());
        zipFile.closeEntry();

        //create base amconll file
        List<AmConllSentence> baseAmConllSentences = decompositionPackages.parallelStream().map(dp -> dp.makeBaseAmConllSentence()).collect(Collectors.toList());



        ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra();
        for (int i = 0; i<originalDecompositionAutomata.size(); i++) {
            if (i % 500 == 0) {
                System.err.println(i);
            }
            SourceAssignmentAutomaton decomp = originalDecompositionAutomata.get(i);
            DecompositionPackage decompositionPackage = decompositionPackages.get(i);
            AmConllSentence amConllSentence = baseAmConllSentences.get(i);

            Map<SourceAssignmentAutomaton.State, Integer> stateToWordPosition = new HashMap<>();

            ConcreteTreeAutomaton<String> fakeIRTGAutomaton = new ConcreteTreeAutomaton<>();
            Map<Rule, Pair<Integer, String>> rule2supertag = new HashMap<>();
            Map<Rule, Pair<Pair<Integer, Integer>, String>> rule2edge = new HashMap<>();

            decomp.processAllRulesBottomUp(rule -> {
                if (rule.getArity() == 0) {
                    try {
                        // we want a new rule format
                        // state.toString() -> wordPosition_supertag
                        // which is, below, parent.toString() -> newRuleLabel
                        String oldRuleLabel = rule.getLabel(decomp);
                        Pair<SGraph, ApplyModifyGraphAlgebra.Type> constant = alg.parseString(oldRuleLabel);
                        int wordPosition = decompositionPackage.getSentencePositionForGraphFragment(constant.left);
                        SourceAssignmentAutomaton.State parent = decomp.getStateForId(rule.getParent());
                        // obtain delexicalized graph fragment
                        GraphNode lexicalNode = decompositionPackage.getLexNodeFromGraphFragment(constant.left);
                        constant.left.addNode(lexicalNode.getName(), AmConllEntry.LEX_MARKER);
//                            String newRuleLabel = wordPosition+"_"
//                                    + constant.left.toIsiAmrStringWithSources()+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+constant.right.toString();

                        String constantString = supertagDictionary.getRepr(constant.left)+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+constant.right.toString();
                        String newRuleLabel = constantString;
                        //add rule to new automaton, with array of size 0 for children
                        Rule newRule = fakeIRTGAutomaton.createRule(parent.toString(), newRuleLabel, new String[0]);
                        fakeIRTGAutomaton.addRule(newRule);
                        rule2supertag.put(newRule, new Pair<>(wordPosition, constantString));
                        // add entry to state-to-word-position map
                        stateToWordPosition.put(parent, wordPosition);
                    } catch (ParserException e) {
                        throw new RuntimeException(e);
                    }
                } else if (rule.getArity() == 2) {
                    // we want a new rule format
                    // state.toString() -> i_j_oldRuleLabel(state0.toString(), state1.toString())
                    // where below state = parent, state0 = child0, state1 = child1
                    // and i = child0 wordPosition, j = child1 wordPosition
                    SourceAssignmentAutomaton.State parent = decomp.getStateForId(rule.getParent());
                    SourceAssignmentAutomaton.State child0 = decomp.getStateForId(rule.getChildren()[0]);
                    SourceAssignmentAutomaton.State child1 = decomp.getStateForId(rule.getChildren()[1]);
                    int wordPosition0 = stateToWordPosition.get(child0);
                    int wordPosition1 = stateToWordPosition.get(child1);
                    int parentWordPosition = wordPosition0; // in AM operations, the left child is the head, and this rule reflects that.
                    // add entry to state-to-word-position map
                    stateToWordPosition.put(parent, parentWordPosition);
                    // make and add new rule to new automaton
//                        String newRuleLabel = wordPosition0+"_"+wordPosition1+"_"+rule.getLabel(decomp);
                    String operationLabel = rule.getLabel(decomp);
                    String newRuleLabel = operationLabel;
                    Rule newRule = fakeIRTGAutomaton.createRule(parent.toString(), newRuleLabel,
                            new String[]{child0.toString(), child1.toString()});
                    fakeIRTGAutomaton.addRule(newRule);
                    rule2edge.put(newRule, new Pair<>(new Pair<>(wordPosition0, wordPosition1), operationLabel));

                    //add edge existence into amconll sentence
                    amConllSentence.get(wordPosition1-1).setHead(wordPosition0);
                } else {
                    System.err.println("uh-oh, rule arity was "+rule.getArity());
                }
            });

            // transfer final states over to new automaton
            for (int finalStateID : decomp.getFinalStates()) {
                // a whole lot of converting IDs and states and strings..
                fakeIRTGAutomaton.addFinalState(fakeIRTGAutomaton.getIdForState(decomp.getStateForId(finalStateID).toString()));
            }

//                System.out.println(fakeIRTGAutomaton);
//                System.out.println(fakeIRTGAutomaton.viterbi());

//                for (Rule rule : rule2supertag.keySet()) {
//                    System.out.println(rule.toString(fakeIRTGAutomaton));
//                    System.out.println(rule2supertag.get(rule));
//                }

            //write supertagmap
            ZipEntry supertagMapZip = new ZipEntry(i+".supertagmap");
            zipFile.putNextEntry(supertagMapZip);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(zipFile);
            objectOutputStream.writeObject(rule2supertag);
            objectOutputStream.flush();
            zipFile.closeEntry();

            //write edgemap
            ZipEntry edgeMapZip = new ZipEntry(i+".edgemap");
            zipFile.putNextEntry(edgeMapZip);
            objectOutputStream = new ObjectOutputStream(zipFile);
            objectOutputStream.writeObject(rule2edge);
            objectOutputStream.flush();
            zipFile.closeEntry();

            //write automaton
            ZipEntry automatonZip = new ZipEntry(i+".irtb");
            zipFile.putNextEntry(automatonZip);
            BinaryIrtgOutputCodec codec = new BinaryIrtgOutputCodec();
            codec.write(new InterpretedTreeAutomaton(fakeIRTGAutomaton), zipFile);
            zipFile.closeEntry();

//                FileInputStream fileInputStream = new FileInputStream(file);
//                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
//                HashMap<Rule, Pair<Integer, String>> fileObj = (HashMap<Rule, Pair<Integer, String>>) objectInputStream.readObject();
//                objectInputStream.close();
//                for (Rule rule : fileObj.keySet()) {
//                    System.out.println(rule.toString(fakeIRTGAutomaton));
//                    System.out.println(fileObj.get(rule));
//                }

        }

        //write AMConll file
        ZipEntry amconllZip = new ZipEntry("corpus.amconll");
        zipFile.putNextEntry(amconllZip);
        Writer amConllWriter = new OutputStreamWriter(zipFile);
        AmConllSentence.write(amConllWriter, baseAmConllSentences);
        amConllWriter.flush();
        zipFile.closeEntry();

        //write supertag dictionary
        ZipEntry supertagZip = new ZipEntry("supertags.txt");
        zipFile.putNextEntry(supertagZip);
        Writer supertagWriter = new OutputStreamWriter(zipFile);
        supertagDictionary.writeToWriter(supertagWriter);
        supertagWriter.flush();
        zipFile.closeEntry();


        zipFile.finish();
    }


}
