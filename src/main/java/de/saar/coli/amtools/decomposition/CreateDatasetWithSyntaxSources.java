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
    private String corpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\dev.sdp";

    @Parameter(names = {"--syntaxScores", "-s"}, description = "Path to the opProbs.txt file containing syntax edge scores")//, required = true)
    private String syntaxEdgeScoresPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\ud_scores_march2020\\dev\\opProbs.txt";

    @Parameter(names = {"--outPath", "-o"}, description = "Path to output folder where")//, required = true)
    private String outPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\";

    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "experimenting";

    @Parameter(names = {"--vocab", "-v"}, description = "existing vocab file containing supertags (e.g. points to training vocab when doing dev/test files). Using this flag means dev set mode, don't use it for the training set")
    private String vocab = null;

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    private static Counter<String> supertagCounter = new Counter<>();

    public static void main(String[] args) throws Exception {

        CreateDatasetWithSyntaxSources cli = new CreateDatasetWithSyntaxSources();
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


        DMBlobUtils blobUtils = new DMBlobUtils();
        GraphReader2015 gr = new GraphReader2015(cli.corpusPath);

        List<List<List<Pair<String, Double>>>> syntaxEdgeScores = Util.readEdgeProbs(new FileReader(cli.syntaxEdgeScoresPath),
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
        if (cli.vocab != null) {
            supertagDictionary.readFromFile(cli.vocab);
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

                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, blobUtils);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, blobUtils);

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton, graph, blobUtils);


                    try {
                        SGraph resultGraph = result.evaluate().left;
                        resultGraph.removeNode("ART-ROOT");

                        graph.setEqualsMeansIsomorphy(false);

                        if (graph.equals(resultGraph)) {
                            AmConllSentence amConllSentence = dep2amConll(result, sdpGraph, syntaxEdges, supertagDictionary);
                            amConllSentenceList.add(amConllSentence);
                        } else {
                            System.err.println(index);
                            System.err.println(graph.toIsiAmrStringWithSources());
                            System.err.println(resultGraph.toIsiAmrStringWithSources());
                            fails++;
                        }
                    } catch (java.lang.Exception ex) {
                        System.err.println(index);
//                        System.err.println(graph.toIsiAmrStringWithSources());
//                        System.err.println(result);
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

        Path outPath = Paths.get(cli.outPath);
        Path amconllOutPath = outPath.resolve(cli.prefix+".amconll");
        AmConllSentence.writeToFile(amconllOutPath.toString(), amConllSentenceList);
        // only write supertag dictionary if no starting dictionary was given (i.e. if we are in the training set case)
        if (cli.vocab == null) {
            Path supertagLexOutPath = outPath.resolve(cli.prefix + "_supertags.txt");
            supertagDictionary.writeToFile(supertagLexOutPath.toString());
        }

        System.err.println("Fails: "+fails);
        System.err.println("Non-decomposeable: "+nondecomposeable);
        supertagCounter.printAllSorted();
    }

    static AmConllSentence dep2amConll(AMDependencyTree dep, Graph sdpGraph, List<Pair<String, Double>> syntaxEdges,
                                       SupertagDictionary supertagDictionary) {
        AmConllSentence sent = new AmConllSentence();

        //add all words from the SDP graph, treating all as ignored for now
        for (Node word : sdpGraph.getNodes()) {
            if (word.id >= 1) {
                AmConllEntry amConllEntry = new AmConllEntry(word.id, word.form);
                amConllEntry.setAligned(true);
                amConllEntry.setHead(0);
                amConllEntry.setLemma(word.lemma);
                amConllEntry.setPos(word.pos);
                amConllEntry.setEdgeLabel(AmConllEntry.IGNORE);
                sent.add(amConllEntry);
            }
        }
        // add artificial root
        AmConllEntry artRoot = new AmConllEntry(sdpGraph.getNNodes(), SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setEdgeLabel(AmConllEntry.ROOT_SYM);
        artRoot.setHead(0);
        artRoot.setAligned(true);
        artRoot.setLemma(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setPos(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        artRoot.setLexLabel(AmConllEntry.LEMMA_PLACEHOLDER);
        sent.add(artRoot);

        //add NE tags
        List<String> forms = sdpGraph.getNodes().subList(1, sdpGraph.getNNodes()).stream().map(word -> word.form).collect(Collectors.toList());
        Sentence stanfAn = new Sentence(forms);
        List<String> neTags = new ArrayList<>(stanfAn.nerTags());
        neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
        sent.addNEs(neTags);

        // get best edge labels
        Map<Set<Integer>, String> edge2bestLabel = new HashMap<>();
        Map<Set<Integer>, Double> bestScorePerEdge = new HashMap<>();
        for (Pair<String, Double> edgeAndScore : syntaxEdges) {
            Pair<String, Pair<Integer, Integer>> labelSourceTarget = Util.edgeString2Edge(edgeAndScore.left);
            Set<Integer> nodes = new HashSet<>();
            nodes.add(labelSourceTarget.right.left);
            nodes.add(labelSourceTarget.right.right);
            if (edgeAndScore.right > bestScorePerEdge.getOrDefault(nodes, 0.0)) {
                edge2bestLabel.put(nodes, syntaxRole2Source(labelSourceTarget.left));
                bestScorePerEdge.put(nodes, edgeAndScore.right);
            }
        }

        // go through AM dependency tree, adding delexicalized supertags, lex labels, and edges.
        addDepToAmConll(dep, sdpGraph, sent, edge2bestLabel, new HashMap<>(), supertagDictionary);

        return sent;
    }

    /**
     * actually modifies the original dependency tree as well, so careful!
     * @param dep
     * @param sent
     */
    private static void addDepToAmConll(AMDependencyTree dep, Graph sdpGraph, AmConllSentence sent,
                                        Map<Set<Integer>, String> edge2bestLabel, Map<String, String> old2newSource,
                                        SupertagDictionary supertagDictionary) {
        String rootNodeName = dep.getHeadGraph().left.getNodeForSource("root");
        int id = getIdFromGraph(dep.getHeadGraph().left, sent);
        AmConllEntry headEntry = sent.get(id-1);
        if (!rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
            headEntry.setLexLabel(sdpGraph.getNode(id).sense);
        }
        dep.getHeadGraph().left.addNode(rootNodeName, AmConllEntry.LEX_MARKER);//modifies the label in the original graph

        //sort children by word order in sentence
        List<Pair<String, AMDependencyTree>> sortedOpsAndChildren = dep.getOperationsAndChildren().stream().sorted(new Comparator<Pair<String, AMDependencyTree>>() {
            @Override
            public int compare(Pair<String, AMDependencyTree> o1, Pair<String, AMDependencyTree> o2) {
                return Integer.compare(getIdFromGraph(o1.right.getHeadGraph().left, sent), getIdFromGraph(o2.right.getHeadGraph().left, sent));
            }
        }).collect(Collectors.toList());
        for (Pair<String, AMDependencyTree> opAndChild : sortedOpsAndChildren) {
            int childId = getIdFromGraph(opAndChild.right.getHeadGraph().left, sent);
            String newSource;
            if (rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
                newSource = SGraphConverter.ROOT_EDGE_LABEL;
            } else {
                Set<Integer> nodes = new HashSet<>();
                nodes.add(id);
                nodes.add(childId);
                newSource = edge2bestLabel.getOrDefault(nodes, "NULL");
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
            addDepToAmConll(opAndChild.right, sdpGraph, sent, edge2bestLabel, old2newSource, supertagDictionary);
        }
        // fix remaining sources that are not filled by an apply (e.g. sources that are unified through a modify above)
        for (String oldSource : Sets.intersect(dep.getHeadGraph().right.getAllSources(), old2newSource.keySet())) {
            String newSource = old2newSource.get(oldSource);
            changeSourceInHeadGraph(dep, oldSource, newSource);
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


    private static int getIdFromGraph(SGraph graph, AmConllSentence sent) {
        String rootNodeName = graph.getNodeForSource("root");
        int id;
        if (rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
            id = sent.size();
        } else {
            id = Integer.parseInt(rootNodeName.substring(2));// maps i_x to x
        }
        return id;
    }

    private static String syntaxRole2Source(String syntaxRole) {
        return syntaxRole.split(":")[0];//TODO
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
