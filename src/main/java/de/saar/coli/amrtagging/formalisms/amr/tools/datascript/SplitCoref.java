/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.TreeWithAritiesAlgebra;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.up.ling.irtg.algebra.graph.*;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.CorpusWriter;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Counter;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Removes reentrant edges that the AM algebra cannot handle.
 * @author JG
 */
public class SplitCoref {

    private final AMRSignatureBuilder signatureBuilder;
    private final Collection<Alignment> alignments;

    private final int timeout;

    /**
     * One SplitCoref object is specific to a single graph.
     * @param signatureBuilder
     * @param alignments
     */
    public SplitCoref(AMRSignatureBuilder signatureBuilder, Collection<Alignment> alignments, int timeoutMS) {
        this.signatureBuilder = signatureBuilder;
        this.alignments = alignments;
        this.timeout = timeoutMS;
    }

    /**
     * One SplitCoref object is specific to a single graph. This constructor initializes the signature builder to a
     * new AMRSignatureBuilder and alignments to null.
     */
    public SplitCoref() {
        this.signatureBuilder = new AMRSignatureBuilder();
        this.alignments = null;
        this.timeout = -1; // no timeout here
    }
    
    /**
     * Calls splitCoref with the arguments in order.
     * @param args
     * @throws ParserException
     * @throws ParseException
     * @throws FileNotFoundException
     * @throws IOException
     * @throws CorpusReadingException
     * @throws InterruptedException 
     */
    public static void main(String[] args) throws ParserException, ParseException, FileNotFoundException, IOException, CorpusReadingException, InterruptedException {

//        String rawGraph = "(a / and :op1 (b / believe-01 :ARG0 (p2 / person :ARG0-of (h2 / have-org-role-91 :ARG1 (c2 / company :wiki - :name (n / name :op1 \"IM\") :mod (c3 / country :wiki \"United_States\" :name (n2 / name :op1 \"United\" :op2 \"States\"))) :ARG2 (c7 / CEO))) :ARG1 (c8 / capable-01 :ARG1 (p / person :ARG1-of (e / employ-01 :ARG0 c2) :mod (e2 / each)) :ARG2 (i / innovate-01 :ARG0 p))) :op2 (f / formulate-01 :ARG0 (c / ceo) :ARG1 (c4 / countermeasure :mod (s / strategy) :purpose (i2 / innovate-01 :prep-in (i3 / industry)))) :time (a3 / after :op1 (i4 / invent-01 :ARG0 (c5 / company :ARG0-of (c6 / compete-02 :ARG1 c2)) :ARG1 (m / machine :ARG0-of (w / wash-01) :ARG1-of (l / load-01 :mod (f2 / front))))))";
//        String ourGraph = "(a <root> / and :op1 (b / believe-01 :ARG0 (p2 / person :ARG0-of (h2 / have-org-role-91 :ARG1 (c2 / company :wiki - :name (n / name :op1 \"IM\") :mod (c3 / country :wiki \"United_States\" :name (n2 / name :op1 \"United\" :op2 \"States\"))) :ARG2 (c7 / CEO))) :ARG1 (c8 / capable-01 :ARG1 (p / person :ARG1-of (e / employ-01 :ARG0 c2) :mod (e2 / each)) :ARG2 (i / innovate-01 :ARG0 p))) :op2 (f / formulate-01 :ARG0 (c / ceo) :ARG1 (c4 / countermeasure :mod (s / strategy) :purpose (i2 / innovate-01 :prep-in (i3 / industry)))) :time (a3 / after :op1 (i4 / invent-01 :ARG0 (c5 / company :ARG0-of (c6 / compete-02 :ARG1 c2)) :ARG1 (m / machine :ARG0-of (w / wash-01) :ARG1-of (l / load-01 :mod (f2 / front))))))";
        String rawGraph = "(l / look-01 :ARG0 (p / person :ARG0-of (c / capture-01 :ARG1 (h2 / he))) :ARG1 h2)";
        String ourGraph = "(l <root> / look-01 :ARG0 (p / person :ARG0-of (c / capture-01 :ARG1 (h2 / he))) :ARG1 h2)";
        SGraph graph = new IsiAmrInputCodec().read(ourGraph);
        new SplitCoref().split(rawGraph, graph, new MutableInteger(0));
        System.err.println(graph.toIsiAmrStringWithSources());

//        splitCoref(args[0], args[1], args[2], Integer.valueOf(args[3]), Integer.valueOf(args[4]));

    }
    
    /**
     * Removes reentrant edges that the AM algebra cannot handle. Looks up the original graphs to
     * find out which edges are reentrant in the original notation. Removes them all first,
     * and one by one re-adds the edges that do not make the graph unparseable.
     * @param corpusPath
     * @param origGraphsPath
     * @param outPath
     * @param threads
     * @param maxMinutes
     * @throws IOException
     * @throws InterruptedException
     * @throws CorpusReadingException 
     */
    public static void splitCoref(String corpusPath, String origGraphsPath, String outPath, int threads, int maxMinutes) throws IOException, InterruptedException, CorpusReadingException {
        //read input data
        InterpretedTreeAutomaton dummyIrtg = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>());
        Signature dummySignature = new Signature();
        dummyIrtg.addInterpretation("string", new Interpretation(new StringAlgebra(), new Homomorphism(dummySignature, dummySignature)));
        dummyIrtg.addInterpretation("tree", new Interpretation(new TreeWithAritiesAlgebra(), new Homomorphism(dummySignature, dummySignature)));
        dummyIrtg.addInterpretation("graph", new Interpretation(new GraphAlgebra(), new Homomorphism(dummySignature, dummySignature)));
        dummyIrtg.addInterpretation("id", new Interpretation(new StringAlgebra(), new Homomorphism(dummySignature, dummySignature)));
        Corpus corpus = Corpus.readCorpus(new FileReader(corpusPath), dummyIrtg);
        BufferedReader origBR = new BufferedReader(new FileReader(origGraphsPath));
        
        
        //setup stat trackers
        Counter<Integer> removedEdgeCounter = new Counter<>();
        IntSet changed = new IntOpenHashSet();
        MutableInteger totalEdgesRemoved = new MutableInteger(0);
        MutableInteger totalEdges = new MutableInteger(0);
        MutableInteger totalReentrantEdges = new MutableInteger(0);
        
        //setup multithreading
        MutableInteger nextInstanceID = new MutableInteger(0);
        ForkJoinPool forkJoinPool = new ForkJoinPool(threads);
        
        //loop over corpus to split
        for (Instance inst : corpus) {
            String rawString = origBR.readLine();
            forkJoinPool.execute(() -> {
                final int i = nextInstanceID.incValue();//returns old value
                
                if ((i+1)%100 == 0) {
                    System.err.println("Up to instance "+i+" (modulo thread reordering):");
                    System.err.println("total edges: "+totalEdges.getValue());
                    System.err.println("reentrant edges: "+totalReentrantEdges.getValue());
                    System.err.println("removed edges: "+totalEdgesRemoved.getValue());
                }
                
                SGraph graph = (SGraph)inst.getInputObjects().get("graph");
                graph.setWriteAsAMR(true);
                
                synchronized (totalEdges) {
                    totalEdges.setValue(totalEdges.getValue() + graph.getGraph().edgeSet().size());
                }
                
                //make a new graph as a copy of the old, and then split it.
                SGraph newGraph = graph.merge(new IsiAmrInputCodec().read("(r<root>)"));//TODO this is a very hacky way to copy the graph
                newGraph.setWriteAsAMR(true);
                
                //add old graph to the instance as a backup for the result, in case something goes wrong.
                //EDIT: we just overwrite it in the "graph" slot now, so it should be fine
                //inst.getInputObjects().put("graphSplit", graph);
                
                try {
                    new SplitCoref().split(rawString, newGraph, totalReentrantEdges);
                } catch (Exception ex) {
                    System.err.println("Exception in instance "+i);
                    ex.printStackTrace();
                }

                //record stats
                int edgesRemoved = graph.getGraph().edgeSet().size() - newGraph.getGraph().edgeSet().size();
                synchronized (removedEdgeCounter) {
                    removedEdgeCounter.add(edgesRemoved);
                }
                synchronized (totalEdgesRemoved) {
                    totalEdgesRemoved.setValue(totalEdgesRemoved.getValue() + edgesRemoved);
                }
                graph.setEqualsMeansIsomorphy(true);
                if (!graph.equals(newGraph)) {
                    synchronized (changed) {
                        changed.add(i);
                    }
                }
                //now add new graph to the instance (overwriting the backup). This way, we can later just write the corpus and have the new graph included.
                try {
                    newGraph.toIsiAmrStringWithSources();
                } catch (java.lang.Exception ex) {
                    System.err.println("Exception in instance "+i);
                    System.err.println("Could not linearize new graph to string");
                    ex.printStackTrace();
                    newGraph = graph;//if we can't write the new graph (why though??), then we just use the old graph.
                }
                inst.getInputObjects().put("graph", newGraph);
            });
        }
        
        //finish multithreading
        forkJoinPool.shutdown();
        boolean finished = forkJoinPool.awaitTermination(maxMinutes, TimeUnit.MINUTES);
        
        System.err.println("finished? "+finished);
        
        //print stats
        removedEdgeCounter.printAllSorted();
        System.err.println("Changed graph IDs: "+changed);
        System.err.println("total edges removed: "+totalEdgesRemoved.getValue());
        
        //write corpus
        //dummyIrtg.addInterpretation("graphSplit", new Interpretation(new GraphAlgebra(), new Homomorphism(dummySignature, dummySignature)));
        new CorpusWriter(dummyIrtg, " all (most) non-AM COREF split", "///###", new FileWriter(outPath)).writeCorpus(corpus);
        
        //old test stuff
//        //String graphString = "(w<root> / want-01 :ARG0 (p/prince) :ARG1 (o/obligate-01 :ARG2 (s/sleep-01 :ARG0 p)))";
//        String graphString = "(a <root> / and :op1 (w / want-01 :ARG0 (m / Mary) :ARG1 (l / leave :ARG0 m) ) :op2 (p / possible :ARG1 l) )";
//        //String graphString = "(s<root>/see-01 :ARG0 (i/i) :ARG1 (r/read-01 :ARG0 (y/you) :ARG1 (b/book :poss i)))";
//        SGraph graph = new GraphAlgebra().parseString(graphString);
//        split(graphString, graph);
        
    }

    /**
     * This modifies the graph orig.
     * @param graphString
     * @param orig
     * @throws ParseException
     */
    void split(String graphString, SGraph orig,
                      MutableInteger totalReentrantEdges) throws ParseException {
        
        Tree<String> nodeTree = Amr2Tree.amr2NodeTree(graphString);
        
        //System.err.println(nodeTree);
        
        Map<String, String> toKeep = nodeTree.dfs((Tree<String> node, List<Map<String, String>> childrenValues) -> {
            Map<String, String> ret = new HashMap<>();
            if (childrenValues.isEmpty()) {
                ret.put("NODE_NAME", node.getLabel());
            } else {
                for (Map<String, String> map : childrenValues) {
                    //this loop automatically keeps the mappings of the leftmost occurrence
                    for (String nn : map.keySet()) {
                        if (nn.equals("NODE_NAME")) {
                            ret.put(map.get(nn), node.getLabel());
                        } else {
                            if (!ret.containsKey(nn)) {
                                ret.put(nn, map.get(nn));
                            }
                        }
                    }
                }
            }
            return ret;
        });
        
        //System.err.println(toKeep);
        
        //now get all removal candidates
        Set<Pair<String, String>> candidates = nodeTree.dfs((Tree<String> node, List<Set<Pair<String, String>>> childrenValues) -> {
            Set<Pair<String, String>> ret = new HashSet<>();
            if (childrenValues.isEmpty()) {
                ret.add(new Pair("NODE_NAME", node.getLabel()));
            } else {
                for (Set<Pair<String, String>> list : childrenValues) {
                    //this loop automatically keeps the mappings of the leftmost occurrence
                    for (Pair<String, String> p : list) {
                        if (p.left.equals("NODE_NAME")) {
                            ret.add(new Pair(p.right, node.getLabel()));
                        } else {
                            ret.addAll(list);
                        }
                    }
                }
            }
            return ret;
        });
        
        
        //System.err.println(candidates);
        
        //collect all internal nodes
        Set<String> internalNodes = new HashSet<>();
        nodeTree.getAllNodes().forEach(node -> {
            if (!node.getChildren().isEmpty()) {
                internalNodes.add(node.getLabel());
            }
        });
        
        //System.err.println(internalNodes);
        
        List<GraphEdge> toRemove = new ArrayList<>();
        for (Pair<String, String> cand : candidates) {
            if (internalNodes.contains(cand.left) || !(toKeep.containsKey(cand.left) && toKeep.get(cand.left).equals(cand.right))) {
                GraphEdge e = orig.getGraph().getEdge(orig.getNode(cand.left), orig.getNode(cand.right));
                if (e == null) {
                    e = orig.getGraph().getEdge(orig.getNode(cand.right), orig.getNode(cand.left));
//                    if (e == null) {
//                        System.err.println("cand: "+cand);
//                    }
                }
                if (e == null) {
                    System.err.println("candidates: "+cand);
                    System.err.println(orig.toIsiAmrStringWithSources());
                    System.err.println(orig);
                }
                //TODO if there are multiple edges between those nodes, e may be the wrong edge.
                toRemove.add(e);
            }
        }
        
        synchronized (totalReentrantEdges) {
            totalReentrantEdges.setValue(totalReentrantEdges.getValue() + toRemove.size());
        }
        
        //System.err.println(toRemove);
        
        if (!testParse(orig)) {
            orig.getGraph().removeAllEdges(toRemove);
            assert testParse(orig);
            //TODO allow possible coordination edges to be added simultaneously
            for (GraphEdge edge : toRemove) {
//                System.err.println("orig: "+orig);
//                System.err.println("string: "+graphString);
//                System.err.println("edge: "+edge);
                orig.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
                if (!testParse(orig)) {
                    orig.getGraph().removeEdge(edge);
                }
            }
        }
        //else leave orig unchanged
        
        //System.err.println(orig.toIsiAmrStringWithSources());
        
    }
    
    private boolean testParse(SGraph graph) throws ParseException {
        Signature sig;
        if (alignments == null) {
            sig = signatureBuilder.makeDecompositionSignature(graph, 0);
        } else {
            sig = signatureBuilder.makeDecompositionSignatureWithAlignments(graph, alignments, false);
        }
        //de.saar.coli.amrtools.util.Util.printSignatureReadable(sig);
        TreeAutomaton decomp = new ApplyModifyGraphAlgebra(sig).decompose(new Pair(graph, ApplyModifyGraphAlgebra.Type.EMPTY_TYPE));
        if (timeout > 0) {
            try {
                decomp.processAllRulesBottomUp(null, timeout);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            decomp.processAllRulesBottomUp(null);
        }
        return !decomp.getFinalStates().isEmpty();
    }



    public SGraph scoreBasedSplit(SGraph graph, Function<GraphEdge, Double> edgeScore, MutableInteger totalReentrantEdges) throws ParseException {
        SGraph mst = getMinimumSpanningTreeKruskal(graph, edgeScore);

        totalReentrantEdges.setValue(totalReentrantEdges.getValue() + graph.getGraph().edgeSet().size() - mst.getGraph().edgeSet().size());

        Set<GraphEdge> edgesToAddBackIn = getEdgesToAddBackIn(graph, mst);

        addEdgesThatAreForbiddenToRemove(edgeScore, mst, edgesToAddBackIn);

        addEdgesAsLongAsTheyDontBreakParsing(mst, edgesToAddBackIn);

        return mst;
    }

    @NotNull
    private Set<GraphEdge> getEdgesToAddBackIn(SGraph graph, SGraph mst) {
        Set<GraphEdge> edgesToAddBackIn = new HashSet<>(graph.getGraph().edgeSet());
        edgesToAddBackIn.removeAll(mst.getGraph().edgeSet());
        return edgesToAddBackIn;
    }

    private void addEdgesAsLongAsTheyDontBreakParsing(SGraph mst, Set<GraphEdge> edgesToAddBackIn) throws ParseException {
        for (GraphEdge edge : edgesToAddBackIn) {
            GraphEdge addedEdge = mst.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
            if (!testParse(mst)) {
                mst.getGraph().removeEdge(addedEdge);
            }
        }
    }

    private void addEdgesThatAreForbiddenToRemove(Function<GraphEdge, Double> edgeScore, SGraph mst, Set<GraphEdge> edgesToAddBackIn) {
        for (Iterator<GraphEdge> iterator = edgesToAddBackIn.iterator(); iterator.hasNext();) {
            GraphEdge edge = iterator.next();
            if (edgeScore.apply(edge).equals(Double.NEGATIVE_INFINITY)) {
                mst.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
                iterator.remove();
            }
        }
    }

    public Function<GraphEdge, Double> scoreEdgesWithAlignmentsAndPreferredRemovals(SGraph graph, List<Alignment> alignments, Set<GraphEdge> preferredRemovals) {
        Set<GraphEdge> forbiddenRemovals = getForbiddenRemovalsFromAlignments(graph, alignments);

        return scoreEdgesWithPreferredAndForbiddenRemovals(preferredRemovals, forbiddenRemovals);
    }

    @NotNull
    private Set<GraphEdge> getForbiddenRemovalsFromAlignments(SGraph graph, List<Alignment> alignments) {
        Set<GraphEdge> forbiddenRemovals = new HashSet<>();
        for (Alignment alignment : alignments) {
            for (GraphEdge edge : graph.getGraph().edgeSet()) {
                if (alignment.nodes.contains(edge.getSource()) && alignment.nodes.contains(edge.getTarget())) {
                    forbiddenRemovals.add(edge);
                }
            }
        }
        return forbiddenRemovals;
    }

    @NotNull
    private Function<GraphEdge, Double> scoreEdgesWithPreferredAndForbiddenRemovals(Set<GraphEdge> preferredRemovals, Set<GraphEdge> forbiddenRemovals) {
        return edge -> {
            if (forbiddenRemovals.contains(edge)) {
                return Double.NEGATIVE_INFINITY;
            } else if (preferredRemovals.contains(edge)) {
                return Double.POSITIVE_INFINITY;
            } else {
                return edge.getLabel().chars().mapToDouble(c -> c).sum(); // more or less an arbitrary mapping, but consistent for the same label
            }
        };
    }


    private SGraph getMinimumSpanningTreeKruskal(SGraph graph, Function<GraphEdge, Double> edgeScore) {
        List<GraphEdge> edgesLowToHighScore = new ArrayList<>(graph.getGraph().edgeSet());
        edgesLowToHighScore.sort(Comparator.comparing(edgeScore));
        SGraph mstGraph = initializeGraphWithNodesOnly(graph);
        Map<GraphNode, Set<GraphNode>> node2ConnectedComponent = initializeConnectedComponentMapWithSingletons(graph, mstGraph);

        for (GraphEdge edge : edgesLowToHighScore) {
            if (!node2ConnectedComponent.get(edge.getSource()).equals(node2ConnectedComponent.get(edge.getTarget()))) {
                addEdge(mstGraph, edge, node2ConnectedComponent);
            }
        }

        return mstGraph;
    }

    /**
     * Initializes a graph with the same nodes as the original graph, but no edges. Also copies sources.
     * @param originalGraph
     * @return
     */
    @NotNull
    private SGraph initializeGraphWithNodesOnly(SGraph originalGraph) {
        SGraph mstGraph = new SGraph();
        for (GraphNode node : originalGraph.getGraph().vertexSet()) {
            mstGraph.addNode(node.getName(), node.getLabel());
        }
        for (String source : originalGraph.getAllSources()) {
            mstGraph.addSource(source, originalGraph.getNodeForSource(source));
        }
        return mstGraph;
    }

    @NotNull
    private Map<GraphNode, Set<GraphNode>> initializeConnectedComponentMapWithSingletons(SGraph graph, SGraph mstGraph) {
        Map<GraphNode, Set<GraphNode>> node2ConnectedComponent = new HashMap<>();
        for (GraphNode node : graph.getGraph().vertexSet()) {
            node2ConnectedComponent.put(node, new HashSet<>(Collections.singleton(node)));
        }
        return node2ConnectedComponent;
    }

    private void addEdge(SGraph mstGraph, GraphEdge edge, Map<GraphNode, Set<GraphNode>> node2ConnectedComponent) {
        mergeSourceAndTargetConnectedComponents(edge, node2ConnectedComponent);
        mstGraph.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
    }

    private void mergeSourceAndTargetConnectedComponents(GraphEdge edge, Map<GraphNode, Set<GraphNode>> node2ConnectedComponent) {
        Set<GraphNode> sourceCC = node2ConnectedComponent.get(edge.getSource());
        Set<GraphNode> targetCC = node2ConnectedComponent.get(edge.getTarget());
        sourceCC.addAll(targetCC);
        for (GraphNode nodeInTargetCC : targetCC) {
            node2ConnectedComponent.put(nodeInTargetCC, sourceCC);
        }
    }


}
