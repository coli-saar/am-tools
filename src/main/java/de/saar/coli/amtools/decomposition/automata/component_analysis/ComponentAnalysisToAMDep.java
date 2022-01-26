package de.saar.coli.amtools.decomposition.automata.component_analysis;

import com.google.common.collect.Multiset;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.DecompositionPackage;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.SDPDecompositionPackage;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.tree.TokenMgrError;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeBottomUpVisitor;
import edu.stanford.nlp.util.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.util.*;
import java.util.stream.Collectors;

public class ComponentAnalysisToAMDep {

    private final SGraph graph;
    private final DecompositionPackage decompositionPackage;

    public ComponentAnalysisToAMDep(SGraph graph, DecompositionPackage decompositionPackage) {
        this.decompositionPackage = decompositionPackage;
        this.graph = graph;
    }

    /**
     * Just for testing/debugging things
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        String corpusPath = "/Users/jonas/Documents/data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";
                //"C://Users/Jonas/Documents/Work/data/sdp/2015/dm/train.sdp";
        DMBlobUtils edgeAttachmentHeuristic = new DMBlobUtils();
        GraphReader2015 gr = new GraphReader2015(corpusPath);

        Graph sdpGraph;

        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        while ((sdpGraph = gr.readGraph()) != null) {
            if (index % 100 == 0) {
                System.err.println(index);
            }
            if (true) { //index == 1268 //index == 23523
                MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
                SGraph graph = inst.getGraph();


                try {

                    DecompositionPackage decompositionPackage = new SDPDecompositionPackage(inst, edgeAttachmentHeuristic, true);

                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, decompositionPackage);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, edgeAttachmentHeuristic);

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton);

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

    public AMDependencyTree componentAnalysis2AMDep(ComponentAutomaton componentAutomaton) throws IllegalArgumentException {
        ConcreteTreeAutomaton<Pair<ConnectedComponent, DAGComponent>> auto = componentAutomaton.asConcreteTreeAutomatonTopDown();
        Tree<Rule> ruleTree;
        try {
            ruleTree = auto.getRuleTree(auto.viterbiRaw().getTree());
        } catch (Exception ex) {
            throw new IllegalArgumentException("ComponentAutomaton provided to componentAnalysis2AMDep does" +
                    "not resolve tree uniquely (or, possibly, a different error occured: "+ex.getMessage());
        }
        Pair<AMDependencyTree, GraphNode> result = ruleTree.dfs(new TreeBottomUpVisitor<Rule, Pair<AMDependencyTree, GraphNode>>() {
            /**
             *
             * @param node
             * @param childrenValues
             * @return
             */
            @Override
            public Pair<AMDependencyTree, GraphNode> combine(Tree<Rule> node, List<Pair<AMDependencyTree, GraphNode>> childrenValues) {
                Rule rule = node.getLabel();
                Pair<ConnectedComponent, DAGComponent> state = auto.getStateForId(rule.getParent());
//                System.err.println(state);
                String nodeName = node.getLabel().getLabel(auto).split("/")[0];
                GraphNode graphNode = graph.getNode(nodeName);
                //get all nodes in connected component
                Set<GraphNode> allowedNodes = state.left.getAllNodes();
                //add all nodes that are direct targets of edges coming from those nodes (we need to percolate them up later, so put them in the DAG now)
                Set<GraphNode> boundaryNodes = new HashSet<>();
                for (GraphEdge e : graph.getGraph().edgeSet()) {
                    if (decompositionPackage.getEdgeAttachmentHeuristic().isOutbound(e)) {
                        if (allowedNodes.contains(e.getSource())) {
                            boundaryNodes.add(e.getTarget());
                        }
                    } else {
                        if (allowedNodes.contains(e.getTarget())) {
                            boundaryNodes.add(e.getSource());
                        }
                    }
                }
                boundaryNodes.removeAll(allowedNodes);
                DAGComponent coreDAGComponent = DAGComponent.createFromSubset(graph, graphNode, decompositionPackage.getEdgeAttachmentHeuristic(), allowedNodes);
                DAGComponent dagComponentWithBoundary = DAGComponent.createFromSubset(graph, graphNode, decompositionPackage.getEdgeAttachmentHeuristic(), Sets.union(allowedNodes, boundaryNodes));
//                System.err.println(nodeName);
//                System.err.println(dagComponent);

                AMDependencyTree ret = dag2AMDep(dagComponentWithBoundary, boundaryNodes, decompositionPackage);//non-static method call
                for (int i = 0; i<rule.getArity(); i++) {
                    Pair<AMDependencyTree, GraphNode> child = childrenValues.get(i);
                    Pair<ConnectedComponent, DAGComponent> childState = auto.getStateForId(rule.getChildren()[i]);
                    GraphNode uniqueModifiee = coreDAGComponent.findUniqueModifiee(childState.left.getAllNodes());
                    String modifyOperation = ApplyModifyGraphAlgebra.OP_MODIFICATION+decompositionPackage.getTempSourceForNodeName(uniqueModifiee.getName());
//                    System.err.println("evaluating "+modifyOperation);
//                    System.err.println("child is "+child.left.evaluate().left.toIsiAmrStringWithSources());
                    AMDependencyTree addEdgeHere = findSubtreeForNodename(ret, uniqueModifiee.getName());
                    if (addEdgeHere == null) {
                        throw new IllegalArgumentException();
                    }
                    addEdgeHere.addEdge(modifyOperation, child.left);
                }
//                System.err.println(ret.evaluate().left.toIsiAmrStringWithSources());

                return new Pair(ret, graphNode);
            }
        });
        return result.left;
    }


    private AMDependencyTree dag2AMDep(DAGComponent dagComponent, Set<GraphNode> boundaryNodes, DecompositionPackage decompositionPackage) {
        //turn DAGComponent into AMDependencyTree with possible duplicate nodes
        AMDependencyTree ret = dagComponent.toTreeWithDuplicates()
                .dfs(new TreeBottomUpVisitor<GraphNode, AMDependencyTree>() {
            @Override
            public AMDependencyTree combine(Tree<GraphNode> node, List<AMDependencyTree> childrenValues) {
                GraphNode graphNode = node.getLabel();
                AMDependencyTree ret;
                try {
                    Pair<SGraph, ApplyModifyGraphAlgebra.Type> asGraph = new ApplyModifyGraphAlgebra().parseString(node2Constant(graphNode, graph));
                    asGraph.left.setEqualsMeansIsomorphy(false);//so that the AM dependency trees we use here keep track of node identity, which we need.
                    ret = new AMDependencyTree(asGraph);
                } catch (ParserException | TokenMgrError e) {
                    e.printStackTrace();
                    ret=null;
                }
                for (AMDependencyTree child : childrenValues) {
                    String operation = ApplyModifyGraphAlgebra.OP_APPLICATION+decompositionPackage.getTempSourceForNodeName(getHeadRootNode(child)); // root of child is app source at this stage
                    ret.addEdge(operation, child);
                }
                return ret;
            }
        });
        //resolve duplicates with upwards percolation
        Set<String> boundaryNodeNames = boundaryNodes.stream().map(GraphNode::getName).collect(Collectors.toSet());
        executeForcedPercolates(ret, boundaryNodeNames);
        //now that percolates are executed, we can remove boundary nodes again
        removeRecursive(ret, boundaryNodeNames);
        return ret;
    }

    /**
     * assumes we don't have to remove the head
     * @param dep
     * @param headNodeNamesToBeRemoved
     */
    private static void removeRecursive(AMDependencyTree dep, Set<String> headNodeNamesToBeRemoved) {
        List<Pair<String, AMDependencyTree>> removeThis = new ArrayList<>();
        for (Pair<String, AMDependencyTree> opAndChild : dep.getOperationsAndChildren()) {
            if (headNodeNamesToBeRemoved.contains(getHeadRootNode(opAndChild.right))) {
                removeThis.add(opAndChild);
            } else {
                removeRecursive(opAndChild.right, headNodeNamesToBeRemoved);
            }
        }
        for (Pair<String, AMDependencyTree> opAndChild : removeThis) {
            dep.removeEdge(opAndChild.left, opAndChild.right);
        }
    }

    /**
     * If dep has a unique subtree where the head graph's root has node name rootNodeName, that unique subtree
     * is returned. Otherwise, returns null.
     * @param dep
     * @param rootNodeName
     * @return
     */
    private static AMDependencyTree findSubtreeForNodename(AMDependencyTree dep, String rootNodeName) {
        List<AMDependencyTree> matches = new ArrayList<>();
        for (AMDependencyTree subTree : dep.getAllSubtrees()) {
            if (rootNodeName.equals(subTree.getHeadGraph().left.getNodeForSource("root"))) {
                matches.add(subTree);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        } else {
            System.err.println(dep);
            System.err.println();
            System.err.println(matches);
            System.err.println();
            return null;
        }
    }

    private String node2Constant(GraphNode node, SGraph graph) {
        SGraph ret = new SGraph();
        StringJoiner typeBuilder = new StringJoiner(", ", "(", ")");
        ret.addNode(node.getName(), node.getLabel());
        ret.addSource("root", node.getName());
        for (GraphEdge edge : decompositionPackage.getEdgeAttachmentHeuristic().getBlobEdges(graph, node)) {
            GraphNode other = GeneralBlobUtils.otherNode(node, edge);
            ret.addNode(other.getName(), null);
            ret.addEdge(ret.getNode(edge.getSource().getName()), ret.getNode(edge.getTarget().getName()),edge.getLabel());
            String sourceName = decompositionPackage.getTempSourceForNodeName(other.getName());//use (more or less) node name as source name at this stage
            ret.addSource(sourceName, other.getName());
            typeBuilder.add(sourceName);
        }
        return ret.toIsiAmrStringWithSources()+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+typeBuilder.toString();
    }


    /**
     * modifies dep by resolving necessary percolations.
     * @param dep
     */
    private void executeForcedPercolates(AMDependencyTree dep, Collection<String> boundaryNodeNames) {

        IntList path = new IntArrayList();

        Multiset<AMDependencyTree> subtrees = dep.getAllSubtrees();


        // map node names to depth of lowest common ancester
        Map<String, Integer> nn2lcaDepth = mapNodeNamesToDepthOfLowestCommonAncestor(dep);

        List<String> allRootNodenames = new ArrayList<>(getRootNodeNames(dep));
        allRootNodenames.sort((o1, o2) -> {
            return -Integer.compare(nn2lcaDepth.get(o1), nn2lcaDepth.get(o2));// '-' for reverse order
        });

        // iterate over the sorted list, so things don't get in each others way.
        for (String nn : allRootNodenames) {
            int lcaDepth = nn2lcaDepth.get(nn);
            List<PercolationPackage> pps;
            //System.err.println(getPercolationPackagesRecursive(dep, nn, null, 0));
            while ((pps = getPercolationPackagesRecursive(dep, nn, null, 0)).size() > 1) {
                for (PercolationPackage pp : pps) {
                    if (pp.depth > lcaDepth) {
                        boolean addDependencyToGrandparent = !(boundaryNodeNames.contains(getHeadRootNode(pp.movingChild))
                                && boundaryNodeNames.contains(getHeadRootNode(pp.parent)));
                        percolate(pp, addDependencyToGrandparent);
                    }
                }
            }

        }

    }

    private static void percolate(PercolationPackage perc, boolean addDependencyToGrandparent) {
        if (perc.grandParent == null) {
            throw new IllegalArgumentException("This should never happen. Something is probably wrong. ComponentAnalysisToAMDep#percolate");
        }
        //remove child from parent
        perc.parent.removeEdge(perc.getOperation(), perc.movingChild);
        //figure out if grandparent has a matching child already
        AMDependencyTree matchingChildAtGrandParent = null;
        for (Pair<String, AMDependencyTree> opAndChild : perc.grandParent.getOperationsAndChildren()) {
            if (getHeadRootNode(perc.movingChild).equals(getHeadRootNode(opAndChild.right))) {
                matchingChildAtGrandParent = opAndChild.right;
                break;// should only ever have one, and if not, this is the least of our problems (TODO maybe double check)
            }
        }
        if (matchingChildAtGrandParent == null) {
            // no matching child found, so we add child to grandparent
            perc.grandParent.addEdge(perc.getOperation(), perc.movingChild);
            ApplyModifyGraphAlgebra.Type t = perc.grandParent.getHeadGraph().right;
            t = t.addSource(perc.getMovingChildSource());
            if (addDependencyToGrandparent) {
                t = t.setDependency(perc.getParentSource(), perc.getMovingChildSource(), perc.getMovingChildSource());
            }
            perc.grandParent.setHeadGraph(new Pair(perc.grandParent.getHeadGraph().left, t));
        } else {
            ApplyModifyGraphAlgebra.Type t = perc.grandParent.getHeadGraph().right;
            //don't have to add the source here (is already there), but have to add the dependency.
            if (addDependencyToGrandparent) {
                t = t.setDependency(perc.getParentSource(), perc.getMovingChildSource(), perc.getMovingChildSource());
            }
            perc.grandParent.setHeadGraph(new Pair(perc.grandParent.getHeadGraph().left, t));
            //matching child found, so we transfer children of moving child over to the matching child
            for (Pair<String, AMDependencyTree> opAndChild : perc.movingChild.getOperationsAndChildren()) {
                matchingChildAtGrandParent.addEdge(opAndChild.left, opAndChild.right);
            }
        }
    }

    private class PercolationPackage {

        private final AMDependencyTree movingChild;
        private final AMDependencyTree parent;
        private final AMDependencyTree grandParent;
        private final int depth;

        private PercolationPackage(AMDependencyTree movingChild, AMDependencyTree parent,
                                   AMDependencyTree grandParent, int depth) {
            this.parent = parent;
            this.movingChild = movingChild;
            this.grandParent = grandParent;
            this.depth = depth;
        }

        private String getOperation() {
            return ApplyModifyGraphAlgebra.OP_APPLICATION+getMovingChildSource();
        }

        private String getMovingChildSource() {
            return decompositionPackage.getTempSourceForNodeName(getHeadRootNode(movingChild));
        }

        private String getParentSource() {
            return decompositionPackage.getTempSourceForNodeName(getHeadRootNode(parent));
        }

        @Override
        public String toString() {
            String gpString = grandParent == null ? "null" : getHeadRootNode(grandParent);
            return gpString+"->"+getHeadRootNode(parent)+"->"+getHeadRootNode(movingChild)+" depth "+depth;
        }
    }

    private static String getHeadRootNode(AMDependencyTree dep) {
        String ret = dep.getHeadGraph().left.getNodeForSource("root");
        if (ret == null) {
            System.err.println("Graph "+dep.getHeadGraph().left.toIsiAmrStringWithSources()+" has no root!! This will probably cause a null pointer exception.");
        }
        String nodeLabel = dep.getHeadGraph().left.getNode(ret).getLabel();
        if (nodeLabel == null || nodeLabel.equals("")) {
            System.err.println("Graph "+dep.getHeadGraph().left.toIsiAmrStringWithSources()+" has root at unlabeled node. This may not be right at this point (cf ComponentAnalysisToAMDep).");
        }
        return ret;
    }

    /**
     * for the full dependency tree, call with grandParent = null and currentDepth = 0
     * @param parent
     * @param nn
     * @param grandParent
     * @param currentDepth
     * @return
     */
    private List<PercolationPackage> getPercolationPackagesRecursive(AMDependencyTree parent, String nn,
                                                                            AMDependencyTree grandParent, int currentDepth) {
        List<PercolationPackage> ret = new ArrayList<>();
        for (Pair<String, AMDependencyTree> opAndChild : parent.getOperationsAndChildren()) {
            if (nn.equals(getHeadRootNode(opAndChild.right))) {
                ret.add(new PercolationPackage(opAndChild.right, parent, grandParent, currentDepth));
            }
            ret.addAll(getPercolationPackagesRecursive(opAndChild.right, nn, parent, currentDepth+1));
        }
        return ret;
    }

    private static Set<String> getRootNodeNames(AMDependencyTree dep) {
        Multiset<AMDependencyTree> subtrees = dep.getAllSubtrees();
        Set<String> allRootNodenames = subtrees.stream().map(ComponentAnalysisToAMDep::getHeadRootNode)
                .collect(Collectors.toSet());
        allRootNodenames.remove(null);//just in case
        return allRootNodenames;
    }

    private static Map<String, Integer> mapNodeNamesToDepthOfLowestCommonAncestor(AMDependencyTree dep) {
        // create a map that maps each node name to all paths to graphs with that node as root
        // (order of children is somewhat arbitrary, but consistent across all paths we build here)
        Map<String, Set<IntList>> nodeName2Paths = new HashMap<>();
        Set<String> allRootNodeNames = getRootNodeNames(dep);
        for (String nn : allRootNodeNames) {
            nodeName2Paths.put(nn, new HashSet<>());
        }
        mapNN2LCADepthRecursive(dep, new IntArrayList(), nodeName2Paths);

        Map<String, Integer> ret = new HashMap<>();
        for (String nn : allRootNodeNames) {
            ret.put(nn, mapPathsToCommonDepth(nodeName2Paths.get(nn)));
        }
        return ret;
    }

    private static void mapNN2LCADepthRecursive(AMDependencyTree dep, IntList prefix,
                                                Map<String, Set<IntList>> nodeName2Paths) {
        //add root of dep to map
        String rootNodeName = dep.getHeadGraph().left.getNodeForSource("root");
        if (rootNodeName != null) {
            nodeName2Paths.get(rootNodeName).add(prefix);
        }
        //recursively call on children, each with its respective path
        int childPosition = 0;
        for (Pair<String, AMDependencyTree> opAndChild : dep.getOperationsAndChildren()) {
            IntList childPath = new IntArrayList();
            childPath.addAll(prefix);
            childPath.add(childPosition);
            mapNN2LCADepthRecursive(opAndChild.right, childPath, nodeName2Paths);
            childPosition++;
        }
    }

    /**
     * Maps a set of paths to the largest value d, such that for all i < d, the paths agree (p(i)=p'(i) for all p, p')
     * That is, returns 0 if paths do not agree at all (then head is last common point on paths).
     * @param paths a set of paths encoded as IntLists. Head is empth list, from there on the next integer always gives
     *              the index of the next child to go to.
     * @return
     */
    private static int mapPathsToCommonDepth(Set<IntList> paths) {
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Empty set of paths when computing common depth! This should never happen, probably something is wrong.");
        }
        //now we can "get" in the statement below without worry.
        int minPathLength = paths.stream().map(list -> list.size()).collect(Collectors.minBy(Comparator.naturalOrder())).get();
        for (int depth = 0; depth < minPathLength; depth++) {
            int baseValue = paths.iterator().next().getInt(depth);
            for (IntList path : paths) {
                if (path.getInt(depth) != baseValue) {
                    return depth;
                }
            }
        }
        return minPathLength;
    }




}
