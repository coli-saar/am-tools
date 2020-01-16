package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
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
        ruleTree.dfs(new TreeBottomUpVisitor<Rule, AMDependencyTree>() {
            @Override
            public AMDependencyTree combine(Tree<Rule> node, List<AMDependencyTree> childrenValues) {
                Pair<ConnectedComponent, DAGComponent> state = auto.getStateForId(node.getLabel().getParent());
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

                AMDependencyTree ret = new AMDependencyTree(converter.node2Constant(graphNode, graph));
                System.err.println(ret);

                return null;
            }
        });
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

    private String node2Constant(GraphNode node, SGraph graph) {
        SGraph ret = new SGraph();
        ret.addNode(node.getName(), node.getLabel());
        ret.addSource("root", node.getName());
        for (GraphEdge edge : blobUtils.getBlobEdges(graph, node)) {
            GraphNode other = GeneralBlobUtils.otherNode(node, edge);
            ret.addNode(other.getName(), "");
            ret.addEdge(edge.getSource(), edge.getTarget(),edge.getLabel());
            ret.addSource(other.getName(), other.getName());//use node name as source name at this stage
        }
        return ret.toIsiAmrStringWithSources();
    }

}
