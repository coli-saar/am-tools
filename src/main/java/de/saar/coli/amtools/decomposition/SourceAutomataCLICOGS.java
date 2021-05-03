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
import de.saar.coli.amrtagging.formalisms.cogs.LogicalFormConverter;
import de.saar.coli.amrtagging.formalisms.cogs.tools.RawCOGSReader;
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
import de.up.ling.tree.Tree;
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
//    private String trainingCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/train_100.tsv";
//    private String trainingCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/train.tsv";
    private String trainingCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/COGS/datasmall/train20.tsv";
//    private String trainingCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/graphparsing/toy/toy2.tsv";

    @Parameter(names = {"--devCorpus", "-d"}, description = "Path to the input dev corpus (*.tsv file)")//, required = true)
    private String devCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/COGS/datasmall/dev20.tsv";
//    private String devCorpusPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/cogsdata/dev.tsv";

    @Parameter(names = {"--outPath", "-o"}, description = "Path to output folder where amconll and supertag dictionary files are created")//, required = true)
    private String outPath = "/home/wurzel/Dokumente/Masterstudium/WS2021/MasterSeminar/cogs/graphparsing/amconll/";

    @Parameter(names = {"--nrSources", "-s"}, description = "how many sources to  (default = 3)")//, required = true)
    private int nrSources = 3;

    // @Parameter(names = {"--iterations"}, description = "max number of EM iterations")//, required = true)
    // private int iterations = 100;

    // @Parameter(names = {"--difference"}, description = "difference in log likelihood for early EM stopping")//, required = true)
    // private double difference = 0.1;

    @Parameter(names = {"--algorithm", "-a"}, description = "so far, only allowed options are 'automata' and 'random' (the former is the default)")//, required = true)
    private String algorithm = "random";
    //private String algorithm = "automata";

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
        System.out.println("-------->> IMPORTANT: Excluding primitives for debugging? " + noPrimitives + " <<--------");
        System.out.println("Train set: " + cli.trainingCorpusPath);
        System.out.println("Dev set:   " + cli.devCorpusPath);
        System.out.println("Output path: " + cli.outPath);

        AMRBlobUtils blobUtils = new COGSBlobUtils();

        SupertagDictionary supertagDictionary = new SupertagDictionary();//future: load from file for dev set (better: get dev scores from training EM)

        //get automata for training set
        List<MRInstance> trainCorpus = getSamplesFromFile(cli.trainingCorpusPath, noPrimitives);

        List<TreeAutomaton<?>> concreteDecompositionAutomata = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomata = new ArrayList<>();
        List<DecompositionPackage> decompositionPackages = new ArrayList<>();

        cli.processCorpus(trainCorpus, blobUtils, concreteDecompositionAutomata, originalDecompositionAutomata, decompositionPackages);

        //get automata for dev set
        List<MRInstance> devCorpus = getSamplesFromFile(cli.devCorpusPath, noPrimitives);

        List<TreeAutomaton<?>> concreteDecompositionAutomataDev = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomataDev = new ArrayList<>();
        List<DecompositionPackage> decompositionPackagesDev = new ArrayList<>();

        cli.processCorpus(devCorpus, blobUtils, concreteDecompositionAutomataDev, originalDecompositionAutomataDev, decompositionPackagesDev);

        Files.createDirectories(Paths.get(cli.outPath));

        if (cli.algorithm.equals("automata")) {
            createAutomataZip(originalDecompositionAutomata, decompositionPackages, supertagDictionary, "train", cli.outPath);
            createAutomataZip(originalDecompositionAutomataDev, decompositionPackagesDev, supertagDictionary, "dev", cli.outPath);

        } else {
            if (cli.algorithm.equals("random")) {
                // copied from SourceAutomataCLI.java
                //write training set
                List<AmConllSentence> outputCorpus = new ArrayList<>();
                Iterator<DecompositionPackage> decompositionPackageIterator = decompositionPackages.iterator();
                Iterator<SourceAssignmentAutomaton> originalAutomataIterator = originalDecompositionAutomata.iterator();
                for (TreeAutomaton<?> dataAutomaton : concreteDecompositionAutomata) {
                    Tree<String> chosenTree = dataAutomaton.getRandomTree();
                    DecompositionPackage decompositionPackage = decompositionPackageIterator.next();
                    outputCorpus.add(originalAutomataIterator.next().tree2amConll(chosenTree, decompositionPackage, supertagDictionary));
                }

                System.out.println("Entropy in train.amconll file: " + SupertagEntropy.computeSupertagEntropy(outputCorpus));

                File trainPath = Paths.get(cli.outPath).toFile(); //,"train"
                trainPath.mkdirs();
                String amConllOutPath = Paths.get(cli.outPath, "train.amconll").toString();//,"train"
                AmConllSentence.writeToFile(amConllOutPath, outputCorpus);

                //write dev set
                List<AmConllSentence> outputCorpusDev = new ArrayList<>();
                Iterator<DecompositionPackage> decompositionPackageIteratorDev = decompositionPackagesDev.iterator();
                Iterator<SourceAssignmentAutomaton> originalAutomataIteratorDev = originalDecompositionAutomataDev.iterator();

                for (TreeAutomaton<?> dataAutomaton : concreteDecompositionAutomataDev) {
                    Tree<String> chosenTree = dataAutomaton.viterbi();
                    DecompositionPackage decompositionPackage = decompositionPackageIteratorDev.next();
                    outputCorpusDev.add(originalAutomataIteratorDev.next().tree2amConll(chosenTree, decompositionPackage, supertagDictionary));
                }

                File devPath = Paths.get(cli.outPath).toFile();//,"gold-dev"
                devPath.mkdirs();
                String amConllOutPathDev = Paths.get(cli.outPath, "dev.amconll").toString();//,"gold-dev"
                AmConllSentence.writeToFile(amConllOutPathDev, outputCorpusDev);

                //write supertag dictionary
                String supertagDictionaryPath = Paths.get(cli.outPath, "supertagDictionary.txt").toString();//,"train"
                supertagDictionary.writeToFile(supertagDictionaryPath);
            }
            else {
                throw new NotImplementedException("For COGS only 'automata' and 'random' option implemented so far");
                // see SDP or AMR for other options
            }
        }
    }

    // todo transform this into a class or have a separate file for this? this is a very general function...
    private static List<MRInstance> getSamplesFromFile(String filename, boolean noPrimitives) throws IOException {
        RawCOGSReader reader = new RawCOGSReader(filename);
        List<MRInstance> samples = new ArrayList<>();
        while (reader.hasNext()) {
            RawCOGSReader.CogsSample sample = reader.getNextSample();
            COGSLogicalForm lf = new COGSLogicalForm(sample.tgt_tokens);
            MRInstance mr = LogicalFormConverter.toSGraph(lf, sample.src_tokens);
            try {
                if (!noPrimitives || lf.getFormulaType()== COGSLogicalForm.AllowedFormulaTypes.IOTA) {
                    samples.add(mr);
                }
                mr.checkEverythingAligned();
//                samples.add(mr);
            } catch (MRInstance.UnalignedNode unalignedNode) {
                System.err.println("Alignment problem detected for following logical form: " + sample.getLogicalFormAsString());
                // System.err.println("Unaligned for LF type: " + lf.getFormulaType());
                if (lf.getFormulaType() != COGSLogicalForm.AllowedFormulaTypes.LAMBDA) {
                    unalignedNode.printStackTrace();
                }
                // else {
                    // System.err.println("to do: fix the lambda alignment problem");
                // }
            } catch (MRInstance.MultipleAlignments multipleAlignments) {
                System.err.println("Alignment problem detected for following logical form: " + sample.getLogicalFormAsString());
                // System.err.println("Multiple Aligned for LF type: " + lf.getFormulaType());
                multipleAlignments.printStackTrace();
            }
        } // buffer ready
        return samples;
    }

    // mostly copied from AMR version (SourceAutomataCLIAMR) and SDP one (SourceAutomataCLI)
    private void processCorpus(List<MRInstance> corpus, AMRBlobUtils blobUtils,
                               List<TreeAutomaton<?>> concreteDecompositionAutomata, List<SourceAssignmentAutomaton> originalDecompositionAutomata,
                               List<DecompositionPackage> decompositionPackages) {

        int[] buckets = new int[]{0, 3, 10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000, 300000, 1000000};
        Counter<Integer> bucketCounter = new Counter<>();  // count automata sizes
        Counter<String> successCounter = new Counter<>();
        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        for (MRInstance inst: corpus) {
            if (index % 2000 == 0) {
                System.err.println("At instance number " + index);
                // bucketCounter.printAllSorted();  // automata sizes
            }
            if (true) { //index == 1268
                // if lf.formula type == lambda  : expect many "has root at unlabeled node. This may not be right at this point (cf ComponentAnalysisToAMDep)."
                // System.err.println("Sentence: "+inst.getSentence());
                // System.err.println("Index: "+index);
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
            if (i % 1000 == 0) {
                System.err.println("Writing instance ... " + i);
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
