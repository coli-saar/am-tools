package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;
import org.eclipse.collections.impl.factory.Sets;
import org.jetbrains.annotations.NotNull;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

public class CreateDatasetWithSyntaxSources {
    public static void main(String[] args) throws Exception {
        String corpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\dev.sdp";
        String syntaxEdgeScoresPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\ud_scores_march2020\\dm_dev\\opProbs.txt";
        //"C://Users/Jonas/Documents/Work/data/sdp/2015/dm/train.sdp";
        DMBlobUtils blobUtils = new DMBlobUtils();
        GraphReader2015 gr = new GraphReader2015(corpusPath);

        List<List<List<Pair<String, Double>>>> syntaxEdgeScores = Util.readEdgeProbs(new FileReader(syntaxEdgeScoresPath),
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

        Graph sdpGraph;

        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        while ((sdpGraph = gr.readGraph()) != null) {
            if (index >= 1) {
                break;
            }
            if (index % 100 == 0) {
                System.err.println(index);
            }
            if (true) { //index == 1268
                System.err.println(index);
                MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
                SGraph graph = inst.getGraph();


                try {

                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, blobUtils);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, blobUtils);

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton, graph, blobUtils);

                    System.err.println(result);

                    try {
                        SGraph resultGraph = result.evaluate().left;
                        resultGraph.removeNode("ART-ROOT");

                        graph.setEqualsMeansIsomorphy(false);

                        if (graph.equals(resultGraph)) {
                            List<Pair<String, Double>> syntaxEdges = syntaxEdgeScoresIterator.next();
                            AmConllSentence amConllSentence = dep2amConll(result, sdpGraph, syntaxEdges);
                            System.err.println(amConllSentence);
                            System.err.println(AlignedAMDependencyTree.fromSentence(amConllSentence).evaluate(false));
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
                    System.err.println(graph.toIsiAmrStringWithSources());
                    ex.printStackTrace();
                    fails++;
                }
            }

            index++;
        }
        System.err.println("Fails: "+fails);
        System.err.println("Non-decomposeable: "+nondecomposeable);
    }

    static AmConllSentence dep2amConll(AMDependencyTree dep, Graph sdpGraph, List<Pair<String, Double>> syntaxEdges) {
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

        //add NE tags TODO add this back in (not doing this during testing)
//        List<String> forms = sdpGraph.getNodes().subList(1, sdpGraph.getNEdges()).stream().map(word -> word.form).collect(Collectors.toList());
//        Sentence stanfAn = new Sentence(forms);
//        List<String> neTags = new ArrayList<>(stanfAn.nerTags());
//        neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
//        sent.addNEs(neTags);

        // get best edge labels
        //TODO edge direction is iffy, maybe just use Set<Integer> instead of <Pair<Integer, Integer>
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
        addDepToAmConll(dep, sdpGraph, sent, edge2bestLabel, new HashMap<>());

        return sent;
    }

    /**
     * actually modifies the original dependency tree as well, so careful!
     * @param dep
     * @param sent
     */
    private static void addDepToAmConll(AMDependencyTree dep, Graph sdpGraph, AmConllSentence sent,
                                        Map<Set<Integer>, String> edge2bestLabel, Map<String, String> old2newSource) {
        String rootNodeName = dep.getHeadGraph().left.getNodeForSource("root");
        GraphNode rootNode = dep.getHeadGraph().left.getNode(rootNodeName);
        int id = getIdFromGraph(dep.getHeadGraph().left, sent);
        AmConllEntry headEntry = sent.get(id-1);
        if (!rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
            headEntry.setLexLabel(sdpGraph.getNode(id).sense);
        }
        rootNode.setLabel(AmConllEntry.LEX_MARKER);//modifies the label in the original graph!
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
            addDepToAmConll(opAndChild.right, sdpGraph, sent, edge2bestLabel, old2newSource);
        }
        // fix remaining sources that are not filled by an apply (e.g. sources that are unified through a modify above)
        for (String oldSource : Sets.intersect(dep.getHeadGraph().right.getAllSources(), old2newSource.keySet())) {
            String newSource = old2newSource.get(oldSource);
            changeSourceInHeadGraph(dep, oldSource, newSource);
        }
        headEntry.setDelexSupertag(dep.getHeadGraph().left.toIsiAmrStringWithSources());
        headEntry.setType(dep.getHeadGraph().right);
    }

    private static void changeSourceInHeadGraph(AMDependencyTree dep, String oldSource, String newSource) {
        SGraph newHeadGraph = dep.getHeadGraph().left.renameSource(oldSource, newSource);
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
