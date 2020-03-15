package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.Util;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
                    List<Pair<String, Double>> syntaxEdges = syntaxEdgeScoresIterator.next();
                    for (Pair<String, Double> edgeAndScore : syntaxEdges) {
                        if (edgeAndScore.right > 0.1) {
                            System.err.println(edgeAndScore);
                        }
                    }
                    AmConllSentence amConllSentence = dep2amConll(result, sdpGraph, syntaxEdges);
                    System.err.println(amConllSentence);

                    try {
                        SGraph resultGraph = result.evaluate().left;
                        resultGraph.removeNode("ART-ROOT");

                        graph.setEqualsMeansIsomorphy(false);

                        if (!graph.equals(resultGraph)) {
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

        // go through AM dependency tree, adding delexicalized supertags, lex labels, and edges.
        addDepToAmConll(dep, sdpGraph, sent);

        return sent;
    }

    /**
     * actually modifies the original dependency tree as well, so careful!
     * @param dep
     * @param sent
     */
    private static void addDepToAmConll(AMDependencyTree dep, Graph sdpGraph, AmConllSentence sent) {
        String rootNodeName = dep.getHeadGraph().left.getNodeForSource("root");
        GraphNode rootNode = dep.getHeadGraph().left.getNode(rootNodeName);
        int id = getIdFromGraph(dep.getHeadGraph().left, sent);
        AmConllEntry headEntry = sent.get(id-1);
        if (!rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
            headEntry.setLexLabel(sdpGraph.getNode(id).sense);
        }
        rootNode.setLabel(AmConllEntry.LEX_MARKER);//modifies the label in the original graph!
        headEntry.setDelexSupertag(dep.getHeadGraph().left.toIsiAmrStringWithSources());
        headEntry.setType(dep.getHeadGraph().right);
        for (Pair<String, AMDependencyTree> opAndChild : dep.getOperationsAndChildren()) {
            int childId = getIdFromGraph(opAndChild.right.getHeadGraph().left, sent);
            AmConllEntry childEntry = sent.get(childId - 1);
            childEntry.setEdgeLabel(opAndChild.left);
            childEntry.setHead(id);
            addDepToAmConll(opAndChild.right, sdpGraph, sent);
        }
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

}
