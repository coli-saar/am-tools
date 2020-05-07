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

public class AmConllWithSourcesCreator {

    public static void createTrainingCorpus(List<SGraph> graphCorpus, List<DecompositionPackage> decompositionPackageList,
                                            List<SourceAssigner> sourceAssignerList, String amConllOutPath, String supertagDictionaryOutPath) throws IOException {
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        createCorpus(graphCorpus, decompositionPackageList, sourceAssignerList, supertagDictionary, amConllOutPath);
        supertagDictionary.writeToFile(supertagDictionaryOutPath);

    }

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
        String rootNodeName = dep.getHeadGraph().left.getNodeForSource("root");
        int id = decompositionPackage.getSentencePositionForGraphFragment(dep.getHeadGraph().left, sent);
        AmConllEntry headEntry = sent.get(id-1);
        if (!rootNodeName.equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
            headEntry.setLexLabel(decompositionPackage.getLexLabelFromGraphFragment(dep.getHeadGraph().left));
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

}
