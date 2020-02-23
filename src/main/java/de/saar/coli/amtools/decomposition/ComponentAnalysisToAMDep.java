package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.*;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeBottomUpVisitor;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

public class ComponentAnalysisToAMDep {

    private final SGraph graph;
    private final AMRBlobUtils blobUtils;

    public ComponentAnalysisToAMDep(SGraph graph, AMRBlobUtils blobUtils) {
        this.blobUtils = blobUtils;
        this.graph = graph;
    }

    public static void main(String[] args) throws Exception {
        String corpusPath = "C://Users/Jonas/Documents/Work/data/sdp/2015/psd/train.sdp";
        //"/Users/jonas/Documents/data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";
        PSDBlobUtils blobUtils = new PSDBlobUtils();
        GraphReader2015 gr = new GraphReader2015(corpusPath);
        Graph sdpGraph = gr.readGraph();

        MRInstance inst = SGraphConverter.toSGraph(sdpGraph);

        SGraph graph = inst.getGraph();
        System.err.println(graph.toIsiAmrString());

        ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, blobUtils);

        ConcreteTreeAutomaton<Pair<ConnectedComponent, DAGComponent>> auto =
                new ComponentAutomaton(graph, blobUtils).asConcreteTreeAutomatonTopDown();

        System.err.println(auto.viterbi());
        System.err.println(auto.getRuleTree(auto.viterbiRaw().getTree()));
        Tree<Rule> ruleTree = auto.getRuleTree(auto.viterbiRaw().getTree());
        Pair<AMDependencyTree, GraphNode> result = ruleTree.dfs(new TreeBottomUpVisitor<Rule, Pair<AMDependencyTree, GraphNode>>() {
            @Override
            public Pair<AMDependencyTree, GraphNode> combine(Tree<Rule> node, List<Pair<AMDependencyTree, GraphNode>> childrenValues) {
                Rule rule = node.getLabel();
                Pair<ConnectedComponent, DAGComponent> state = auto.getStateForId(rule.getParent());
//                System.err.println(state);
                String nodeName = node.getLabel().getLabel(auto).split("/")[0];
                GraphNode graphNode = graph.getNode(nodeName);
                Iterable<GraphNode> forbiddenNodes;
                if (state.right == null) {
                    forbiddenNodes = Collections.EMPTY_SET;
                } else {
                    forbiddenNodes = state.right.getAllAsGraphNodes();
                }
                DAGComponent dagComponent = DAGComponent.createWithoutForbiddenNodes(graph, graphNode, blobUtils, forbiddenNodes);
                System.err.println(nodeName);
                System.err.println(dagComponent);

                AMDependencyTree ret = converter.dag2AMDep(dagComponent);
                for (int i = 0; i<rule.getArity(); i++) {
                    Pair<AMDependencyTree, GraphNode> child = childrenValues.get(i);
                    Pair<ConnectedComponent, DAGComponent> childState = auto.getStateForId(rule.getChildren()[i]);
                    GraphNode uniqueModifiee = dagComponent.findUniqueModifiee(childState.left.getAllNodes());
                    String modifyOperation = ApplyModifyGraphAlgebra.OP_MODIFICATION+nodeName2source(uniqueModifiee.getName());
                    System.err.println("evaluating "+modifyOperation);
                    System.err.println("child is "+child.left.evaluate().left.toIsiAmrStringWithSources());
                    ret.addEdge(modifyOperation, child.left);
                }
                System.err.println(ret.evaluate().left.toIsiAmrStringWithSources());

                return new Pair(ret, graphNode);
            }
        });
        SGraph resultGraph = result.left.evaluate().left;
        resultGraph.removeNode("ART-ROOT");
        System.err.println(resultGraph.toIsiAmrStringWithSources());
        System.err.println(graph.toIsiAmrStringWithSources());
        System.err.println(resultGraph.isIdenticalExceptSources(graph));

//        auto.viterbi().dfs(new TreeBottomUpVisitor<String, Void>() {
//            @Override
//            public Void combine(Tree<String> node, List<Void> childrenValues) {
//                String nodeName = node.getLabel().split("/")[0];
//                System.err.println(nodeName);
//                GraphNode graphNode = graph.getNode(nodeName);
//                DAGComponent dagComponent = new DAGComponent(graph, graphNode, blobUtils);
//                System.err.println(blobUtils.getBlobEdges(graph, graphNode));
//                System.err.println(dagComponent);
//
//                return null;
//            }
//        });

    }

    private AMDependencyTree dag2AMDep(DAGComponent dagComponent) {
        Pair<AMDependencyTree, GraphNode> ret = dagComponent.toTreeWithDuplicates()
                .dfs(new TreeBottomUpVisitor<GraphNode, Pair<AMDependencyTree, GraphNode>>() {
            @Override
            public Pair<AMDependencyTree, GraphNode> combine(Tree<GraphNode> node, List<Pair<AMDependencyTree, GraphNode>> childrenValues) {
                GraphNode graphNode = node.getLabel();
                AMDependencyTree ret = new AMDependencyTree(node2Constant(graphNode, graph));
                for (Pair<AMDependencyTree, GraphNode> child : childrenValues) {
                    String operation = ApplyModifyGraphAlgebra.OP_APPLICATION+nodeName2source(child.right.getName()); // root of child is app source at this stage
                    ret.addEdge(operation, child.left);
                }
                return new Pair(ret, graphNode);
            }
        });
        return ret.left;
    }

    private String node2Constant(GraphNode node, SGraph graph) {
        SGraph ret = new SGraph();
        StringJoiner typeBuilder = new StringJoiner(", ", "(", ")");
        ret.addNode(node.getName(), node.getLabel());
        ret.addSource("root", node.getName());
        for (GraphEdge edge : blobUtils.getBlobEdges(graph, node)) {
            GraphNode other = GeneralBlobUtils.otherNode(node, edge);
            ret.addNode(other.getName(), null);
            ret.addEdge(ret.getNode(edge.getSource().getName()), ret.getNode(edge.getTarget().getName()),edge.getLabel());
            String sourceName = nodeName2source(other.getName());
            ret.addSource(sourceName, other.getName());//use node name as source name at this stage
            typeBuilder.add(sourceName);
        }
        return ret.toIsiAmrStringWithSources()+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+typeBuilder.toString();
    }

    private static String nodeName2source(String nodeName) {
        return nodeName.replaceAll("_", "");
    }

}
