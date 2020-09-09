package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class has two methods (one for training set, one for dev set) that each take lists with decomposition information
 * for a corpus, and create an AmConll file (and in the training case a supertag lexicon) with the decompositions.
 */
public class AmConllWithSourcesCreator {

    /**
     * Creates a training set AmConll corpus and a supertag dictionary. The three input lists must have the same length,
     * with the i-th DecompositionPackage and the i-th SourceAssigner corresponding to the i-th SGraph.
     * @param graphCorpus
     * @param decompositionPackageList
     * @param sourceAssignerList
     * @param amConllOutPath
     * @param supertagDictionaryOutPath
     * @throws IOException
     */
    public static void createTrainingCorpus(List<SGraph> graphCorpus, List<DecompositionPackage> decompositionPackageList,
                                            List<SourceAssigner> sourceAssignerList, String amConllOutPath, String supertagDictionaryOutPath) throws IOException {
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        createCorpus(graphCorpus, decompositionPackageList, sourceAssignerList, supertagDictionary, amConllOutPath);
        supertagDictionary.writeToFile(supertagDictionaryOutPath);

    }

    /**
     * Creates a dev set AmConll corpus with constants matching the given supertag dictionary (invents new
     * constants as necessary, but does not add them to the dictionary file). The three input lists must have the same length,
     * with the i-th DecompositionPackage and the i-th SourceAssigner corresponding to the i-th SGraph.
     * @param graphCorpus
     * @param decompositionPackageList
     * @param sourceAssignerList
     * @param amConllOutPath
     * @param existingSupertagDictionaryPath
     * @throws IOException
     * @throws ParserException
     */
    public static void createDevCorpus(List<SGraph> graphCorpus, List<DecompositionPackage> decompositionPackageList,
                                            List<SourceAssigner> sourceAssignerList, String amConllOutPath, String existingSupertagDictionaryPath) throws IOException, ParserException {
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        supertagDictionary.readFromFile(existingSupertagDictionaryPath);
        createCorpus(graphCorpus, decompositionPackageList, sourceAssignerList, supertagDictionary, amConllOutPath);
        //don't write updated supertagDictionary to file, in accordance with precedence in other classes.
    }

    private final SupertagDictionary supertagDictionary;
    private final Counter<String> supertagCounter;

    private AmConllWithSourcesCreator(SupertagDictionary supertagDictionary) {
        this.supertagCounter = new Counter<>();
        this.supertagDictionary = supertagDictionary;
    }

    private static void createCorpus(List<SGraph> graphCorpus, List<DecompositionPackage> decompositionPackageList,
                                     List<SourceAssigner> sourceAssignerList, SupertagDictionary supertagDictionary,
                                     String amConllOutPath) throws IOException {
        //first make sure all lists have the same length.
        if (graphCorpus.size() != decompositionPackageList.size() || graphCorpus.size() != sourceAssignerList.size()) {
            throw new IllegalArgumentException("graphCorpus, decompositionPackageList and sourceAssignerList must have " +
                    "same size in AmConllWithSourcesCreator.createCorpus.");
        }

        AmConllWithSourcesCreator creator = new AmConllWithSourcesCreator(supertagDictionary);


        int fails = 0;
        int nondecomposeable = 0;
        List<AmConllSentence> amConllSentenceList = new ArrayList<>();
        for (int i = 0; i < graphCorpus.size(); i++) {
            if (i % 500 == 0) {
                System.out.println("processing graph with index "+i);
            }
            SGraph graph = graphCorpus.get(i);
            DecompositionPackage decompositionPackage = decompositionPackageList.get(i);
            SourceAssigner sourceAssigner = sourceAssignerList.get(i);

                try {
                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, decompositionPackage);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, decompositionPackage.getBlobUtils());

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton, graph);
                    System.out.println(result.toString());

                    // if multiple nodes in the graph belong to the same word
                    result = condenseNodesWithSameAlignment(result, decompositionPackage);

                    try {
                        SGraph resultGraph = result.evaluate().left;

                        resultGraph.removeNode("ART-ROOT");

                        graph.setEqualsMeansIsomorphy(false);


                        if (graph.equals(resultGraph)) {
                            AmConllSentence amConllSentence = creator.dep2amConll(result, decompositionPackage, sourceAssigner);
                            amConllSentenceList.add(amConllSentence);
                        } else {
                            System.err.println(i);
                            System.err.println(graph.toIsiAmrStringWithSources());
                            System.err.println(resultGraph.toIsiAmrStringWithSources());
                            fails++;
                        }
                    } catch (java.lang.Exception ex) {
                        System.err.println(i);
                        System.err.println(graph.toIsiAmrStringWithSources());
                        System.err.println(result);
                        ex.printStackTrace();
                        fails++;
                    }
                } catch (DAGComponent.NoEdgeToRequiredModifieeException | DAGComponent.CyclicGraphException ex) {
                    nondecomposeable++;
                } catch (java.lang.Exception ex) {
                    System.err.println(i);
//                    System.err.println(graph.toIsiAmrStringWithSources());
                    ex.printStackTrace();
                    fails++;
                }

        }

        AmConllSentence.writeToFile(amConllOutPath, amConllSentenceList);
        System.out.println("All "+graphCorpus.size()+" graphs processed and AmConllFile written to "+amConllOutPath);
        System.out.println("Fails: "+fails);
        System.out.println("Non-decomposeable: "+nondecomposeable);

        System.err.println("Supertags with counts:");
        creator.supertagCounter.printAllSorted();
    }


    private AmConllSentence dep2amConll(AMDependencyTree dep, DecompositionPackage decompositionPackage, SourceAssigner sourceAssigner) {

        AmConllSentence sent = decompositionPackage.makeBaseAmConllSentence();

        // go through AM dependency tree, adding delexicalized supertags, lex labels, and edges.
        addDepToAmConllRecursive(dep, sent, new HashMap<>(), decompositionPackage, sourceAssigner);

        return sent;
    }

    /**
     * actually modifies the original dependency tree as well, so careful!
     * @param dep
     * @param sent
     */
    private void addDepToAmConllRecursive(AMDependencyTree dep, AmConllSentence sent, Map<String, String> old2newSource,
                                                 DecompositionPackage decompositionPackage, SourceAssigner sourceAssigner) {
        int id = decompositionPackage.getSentencePositionForGraphFragment(dep.getHeadGraph().left);
        //sort children by word order in sentence
        List<Pair<String, AMDependencyTree>> sortedOpsAndChildren = dep.getOperationsAndChildren().stream().sorted(new Comparator<Pair<String, AMDependencyTree>>() {
            @Override
            public int compare(Pair<String, AMDependencyTree> o1, Pair<String, AMDependencyTree> o2) {
                return Integer.compare(decompositionPackage.getSentencePositionForGraphFragment(o1.right.getHeadGraph().left),
                        decompositionPackage.getSentencePositionForGraphFragment(o2.right.getHeadGraph().left));
            }
        }).collect(Collectors.toList());
        String rootNodeName = dep.getHeadGraph().left.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        for (Pair<String, AMDependencyTree> opAndChild : sortedOpsAndChildren) {
            int childId = decompositionPackage.getSentencePositionForGraphFragment(opAndChild.right.getHeadGraph().left);
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
            addDepToAmConllRecursive(opAndChild.right, sent, old2newSource, decompositionPackage, sourceAssigner);
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
        //supertagCounter.add(dep.getHeadGraph().right.toString() + " | " + delexSupertag);
        setSupertag(dep.getHeadGraph().left, dep.getHeadGraph().right, decompositionPackage, id, sent, supertagDictionary);
    }

    /**
     * sets a supertag (consisting of graph and type) to be the supertag in the given sentenceToAddTo at position
     * wordID.
     * @param graph
     * @param type
     * @param decompositionPackage
     * @param wordID 1-based
     * @param sentenceToAddTo
     */
    public static void setSupertag(SGraph graph, ApplyModifyGraphAlgebra.Type type, DecompositionPackage decompositionPackage,
                                   int wordID, AmConllSentence sentenceToAddTo, SupertagDictionary supertagDictionary) {
        String rootNodeName = graph.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
        if (rootNodeName == null) {
            throw new IllegalArgumentException("As-graph does not have root.");
        }
        AmConllEntry headEntry = sentenceToAddTo.get(wordID-1);
        if (!rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
            GraphNode lexNode = decompositionPackage.getLexNodeFromGraphFragment(graph);
            if (lexNode != null) {
                headEntry.setLexLabel(lexNode.getLabel());
                // now relabel the node in a way that tells the graph that it needs to recompute its hashcode
                graph.addNode(lexNode.getName(), AmConllEntry.LEX_MARKER);
            }
        }
        graph.setEqualsMeansIsomorphy(true);
        System.out.println("Dict size before: "+supertagDictionary.size());
        System.out.println("Graph input: "+graph.toIsiAmrStringWithSources());
        String delexSupertag = supertagDictionary.getRepr(graph);
        System.out.println("Representation out: "+delexSupertag);
        System.out.println("Dict size after: "+supertagDictionary.size());
        headEntry.setDelexSupertag(delexSupertag);
        headEntry.setType(type);
    }

    private static void changeSourceInHeadGraph(AMDependencyTree dep, String oldSource, String newSource) {
        SGraph newHeadGraph;
        if (dep.getHeadGraph().left.getNodeForSource(oldSource) == null) {
            newHeadGraph = dep.getHeadGraph().left;
        } else {
            newHeadGraph = dep.getHeadGraph().left.renameSource(oldSource, newSource);
        }
        ApplyModifyGraphAlgebra.Type newHeadType = changeSource(dep.getHeadGraph().right, oldSource, newSource);
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
    private static ApplyModifyGraphAlgebra.Type changeSource(ApplyModifyGraphAlgebra.Type type, String oldSource, String newSource) {
        ApplyModifyGraphAlgebra.Type ret = ApplyModifyGraphAlgebra.Type.EMPTY_TYPE;
        for (String source : type.getAllSources()) {
            if (source.equals(oldSource)) {
                ret = ret.addSource(newSource);
            } else {
                ret = ret.addSource(source);
            }
        }
        for (ApplyModifyGraphAlgebra.Type.Edge edge : type.getAllEdges()) {
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


    private static AMDependencyTree condenseNodesWithSameAlignment(AMDependencyTree dep, DecompositionPackage decompositionPackage) {

        int alignmentHere = decompositionPackage.getSentencePositionForGraphFragment(dep.getHeadGraph().left);
        // build an AMDependency tree with all nodes belonging to the same word. We will later evaluate and thus condense it.
        AMDependencyTree localCondensationTree = new AMDependencyTree(dep.getHeadGraph());
        // keep all children that are aligned ot a different word.
        List<Pair<String, AMDependencyTree>> childrenToKeep = new ArrayList<>();
        for (Pair<String, AMDependencyTree> opAndChild : dep.getOperationsAndChildren()) {
            int childAlignment = decompositionPackage.getSentencePositionForGraphFragment(opAndChild.right.getHeadGraph().left);
            // recursively condense the children
            AMDependencyTree condensedChild = condenseNodesWithSameAlignment(opAndChild.right, decompositionPackage);
            if (childAlignment == alignmentHere) {
                localCondensationTree.addEdge(opAndChild.left, condensedChild);
            } else {
                childrenToKeep.add(new Pair(opAndChild.left, condensedChild));
            }
        }

        // build resulting dependency tree, by evaluating the localCondensationTree
        Pair<SGraph, ApplyModifyGraphAlgebra.Type> newHead = localCondensationTree.evaluate();
        AMDependencyTree ret = new AMDependencyTree(newHead);
        // keep the other children
        for (Pair<String, AMDependencyTree> child : childrenToKeep) {
            ret.addEdge(child.left, child.right);
        }
        return ret;
    }

}
