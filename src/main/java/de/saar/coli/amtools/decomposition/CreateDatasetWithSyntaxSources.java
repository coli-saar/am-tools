package de.saar.coli.amtools.decomposition;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import edu.stanford.nlp.simple.Sentence;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CreateDatasetWithSyntaxSources {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\smallDev.sdp";

    @Parameter(names = {"--syntaxScores", "-s"}, description = "Path to the opProbs.txt file containing syntax edge scores")//, required = true)
    private String syntaxEdgeScoresPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\ud_scores_march2020\\smallDev\\opProbs.txt";

    @Parameter(names = {"--outPath", "-o"}, description = "Path to output folder where")//, required = true)
    private String outPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\";

    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "smallDev";

    @Parameter(names = {"--vocab", "-v"}, description = "existing vocab file containing supertags (e.g. points to training vocab when doing dev/test files). Using this flag means dev set mode, don't use it for the training set")
    private String vocab = null;

    @Parameter(names = {"--debug"}, description = "maxes things run faster for debugging (skips NER tags)")
    private boolean debug=true;

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    private static Counter<String> supertagCounter = new Counter<>();

    public static void main(String[] args) throws Exception {

        CreateDatasetWithSyntaxSources datasetCreator = new CreateDatasetWithSyntaxSources();
        JCommander commander = new JCommander(datasetCreator);

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (datasetCreator.help) {
            commander.usage();
            return;
        }


        DMBlobUtils blobUtils = new DMBlobUtils();
        GraphReader2015 gr = new GraphReader2015(datasetCreator.corpusPath);

        List<List<List<Pair<String, Double>>>> syntaxEdgeScores = Util.readEdgeProbs(new FileReader(datasetCreator.syntaxEdgeScoresPath),
                true, 0, 5, false);//indices are 1-based, like in the am-dependency tree
        //work around weird legacy issue for edge scores
        Iterator<List<Pair<String, Double>>> syntaxEdgeScoresIterator = new Iterator<List<Pair<String, Double>>>() {
            Iterator<List<List<Pair<String, Double>>>> it = syntaxEdgeScores.iterator();
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }
            @Override
            public List<Pair<String, Double>> next() {
                return it.next().get(0);
            }
        };

        //make supertag dictionary; if a starting dictionary was given (i.e. if we are in the dev set case), read it.
        SupertagDictionary supertagDictionary = new SupertagDictionary();;
        if (datasetCreator.vocab != null) {
            supertagDictionary.readFromFile(datasetCreator.vocab);
        }

        Graph sdpGraph;

        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        List<AmConllSentence> amConllSentenceList = new ArrayList<>();
        while ((sdpGraph = gr.readGraph()) != null) {
            List<Pair<String, Double>> syntaxEdges = syntaxEdgeScoresIterator.next(); // synchronously step forward with this
            if (index % 100 == 0) {
                System.err.println(index);
            }
            if (true) { //index == 1268
                MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
                SGraph graph = inst.getGraph();


                try {

                    DecompositionPackage decompositionPackage = new DMDecompositionPackage(sdpGraph);
                    SourceAssigner sourceAssigner = new SyntaxSourceAssigner(syntaxEdges);

                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, decompositionPackage);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, blobUtils);

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton, graph);


                    try {
                        SGraph resultGraph = result.evaluate().left;
                        resultGraph.removeNode("ART-ROOT");

                        graph.setEqualsMeansIsomorphy(false);


                        if (graph.equals(resultGraph)) {
                            AmConllSentence amConllSentence = datasetCreator.dep2amConll(result, sdpGraph, supertagDictionary, decompositionPackage, sourceAssigner);
                            amConllSentenceList.add(amConllSentence);
                        } else {
                            System.err.println(index);
                            System.err.println(graph.toIsiAmrStringWithSources());
                            System.err.println(resultGraph.toIsiAmrStringWithSources());
                            fails++;
                        }
                    } catch (java.lang.Exception ex) {
                        System.err.println(index);
                        System.err.println(graph.toIsiAmrStringWithSources());
                        System.err.println(result);
                        ex.printStackTrace();
                        fails++;
                    }
                } catch (DAGComponent.NoEdgeToRequiredModifieeException | DAGComponent.CyclicGraphException ex) {
                    nondecomposeable++;
                } catch (java.lang.Exception ex) {
                    System.err.println(index);
//                    System.err.println(graph.toIsiAmrStringWithSources());
                    ex.printStackTrace();
                    fails++;
                }
            }

            index++;
        }

        Path outPath = Paths.get(datasetCreator.outPath);
        Path amconllOutPath = outPath.resolve(datasetCreator.prefix+".amconll");
        AmConllSentence.writeToFile(amconllOutPath.toString(), amConllSentenceList);
        // only write supertag dictionary if no starting dictionary was given (i.e. if we are in the training set case)
        if (datasetCreator.vocab == null) {
            Path supertagLexOutPath = outPath.resolve(datasetCreator.prefix + "_supertags.txt");
            supertagDictionary.writeToFile(supertagLexOutPath.toString());
        }

        System.err.println("Fails: "+fails);
        System.err.println("Non-decomposeable: "+nondecomposeable);
        supertagCounter.printAllSorted();
    }

    AmConllSentence dep2amConll(AMDependencyTree dep, Graph sdpGraph, SupertagDictionary supertagDictionary,
                                DecompositionPackage decompositionPackage, SourceAssigner sourceAssigner) {

        AmConllSentence sent = decompositionPackage.makeBaseAmConllSentence();

        // go through AM dependency tree, adding delexicalized supertags, lex labels, and edges.
        addDepToAmConllRecursive(dep, sdpGraph, sent, new HashMap<>(), supertagDictionary, decompositionPackage, sourceAssigner);

        return sent;
    }

    /**
     * actually modifies the original dependency tree as well, so careful!
     * @param dep
     * @param sent
     */
    private static void addDepToAmConllRecursive(AMDependencyTree dep, Graph sdpGraph, AmConllSentence sent,
                                                 Map<String, String> old2newSource, SupertagDictionary supertagDictionary,
                                                 DecompositionPackage decompositionPackage, SourceAssigner sourceAssigner) {
        String rootNodeName = dep.getHeadGraph().left.getNodeForSource("root");
        int id = decompositionPackage.getSentencePositionForGraphFragment(dep.getHeadGraph().left, sent);
        AmConllEntry headEntry = sent.get(id-1);
        if (!rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
            headEntry.setLexLabel(sdpGraph.getNode(id).sense);
        }
        dep.getHeadGraph().left.addNode(rootNodeName, AmConllEntry.LEX_MARKER);//modifies the label in the original graph

        //sort children by word order in sentence
        List<Pair<String, AMDependencyTree>> sortedOpsAndChildren = dep.getOperationsAndChildren().stream().sorted(new Comparator<Pair<String, AMDependencyTree>>() {
            @Override
            public int compare(Pair<String, AMDependencyTree> o1, Pair<String, AMDependencyTree> o2) {
                return Integer.compare(decompositionPackage.getSentencePositionForGraphFragment(o1.right.getHeadGraph().left, sent),
                        decompositionPackage.getSentencePositionForGraphFragment(o2.right.getHeadGraph().left, sent));
            }
        }).collect(Collectors.toList());
        for (Pair<String, AMDependencyTree> opAndChild : sortedOpsAndChildren) {
            int childId = decompositionPackage.getSentencePositionForGraphFragment(opAndChild.right.getHeadGraph().left, sent);
            String newSource;
            if (rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
                newSource = SGraphConverter.ROOT_EDGE_LABEL;
            } else {
                newSource = sourceAssigner.getSourceName(id, childId, opAndChild.left);
            }
            AmConllEntry childEntry = sent.get(childId - 1);
            if (opAndChild.left.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
                String fixedNewSource = newSource;
                int addition = 1;
                while (dep.getHeadGraph().left.getAllSources().contains(fixedNewSource)) {
                    addition++;
                    fixedNewSource = newSource+addition;
                }
                newSource = fixedNewSource;

                //change the source in s-graph and type
                String oldSource = opAndChild.left.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
                changeSourceInHeadGraph(dep, oldSource, newSource);

                //store the change for later use
                old2newSource.put(oldSource, newSource);

                //set edge label in dependency tree
                childEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+newSource);
            } else {
                // don't need to fix the source here like in the apply case, because we only need to check for duplicates
                // in the child, which will happen in the apply case of the recursive call
                //change the source in s-graph and type
                String oldSource = opAndChild.left.substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
                changeSourceInHeadGraph(opAndChild.right, oldSource, newSource);

                //store the change for later use
                old2newSource.put(oldSource, newSource);

                //set edge label in dependency tree
                childEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION+newSource);
            }
            childEntry.setHead(id);
        }
        // have separate loop for the recursive call, such that old2newSource has been fully updated
        for (Pair<String, AMDependencyTree> opAndChild : sortedOpsAndChildren) {
            addDepToAmConllRecursive(opAndChild.right, sdpGraph, sent, old2newSource, supertagDictionary, decompositionPackage, sourceAssigner);
        }

        // fix remaining sources that are not filled by an apply (e.g. sources that are unified through a modify above)
        for (String oldSource : Sets.intersect(dep.getHeadGraph().right.getAllSources(), old2newSource.keySet())) {
            String newSource = old2newSource.get(oldSource);
            changeSourceInHeadGraph(dep, oldSource, newSource);
        }
        for (String source : dep.getHeadGraph().right.getAllSources()) {
            if (source.startsWith("i")) {
                System.err.println("bad source found! "+source);
                System.err.println(dep);
            }
        }
        dep.getHeadGraph().left.setEqualsMeansIsomorphy(true);
        String delexSupertag = supertagDictionary.getRepr(dep.getHeadGraph().left);
        supertagCounter.add(dep.getHeadGraph().right.toString() + " | " + delexSupertag);
        headEntry.setDelexSupertag(delexSupertag);
        headEntry.setType(dep.getHeadGraph().right);
    }

    private static void changeSourceInHeadGraph(AMDependencyTree dep, String oldSource, String newSource) {
        SGraph newHeadGraph;
        if (dep.getHeadGraph().left.getNodeForSource(oldSource) == null) {
            newHeadGraph = dep.getHeadGraph().left;
        } else {
            newHeadGraph = dep.getHeadGraph().left.renameSource(oldSource, newSource);
        }
        Type newHeadType = changeSource(dep.getHeadGraph().right, oldSource, newSource);
        dep.setHeadGraph(new Pair<>(newHeadGraph, newHeadType));
    }




    /**
     * Returns a copy of Type with oldSource replaced by newSource. All incoming edges of that source now
     * also have the label newSource (i.e. this deletes all renames).
     * @param type
     * @param oldSource
     * @param newSource
     * @return
     */
    private static Type changeSource(Type type, String oldSource, String newSource) {
        Type ret = Type.EMPTY_TYPE;
        for (String source : type.getAllSources()) {
            if (source.equals(oldSource)) {
                ret = ret.addSource(newSource);
            } else {
                ret = ret.addSource(source);
            }
        }
        for (Type.Edge edge : type.getAllEdges()) {
            if (edge.getSource().equals(oldSource)) {
                ret = ret.setDependency(newSource, edge.getTarget(), edge.getLabel());
            } else if (edge.getTarget().equals(oldSource)) {
                // also need to change the edge label here, since otherwise it will appear as a rename
                // (this assumes there is no rename before).
                ret = ret.setDependency(edge.getSource(), newSource, newSource);
            } else {
                ret = ret.setDependency(edge.getSource(), edge.getTarget(), edge.getLabel());
            }
        }
        return ret;
    }

}
