/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amtools.decomposition.DAGComponent.NoEdgeToRequiredModifieeException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.signature.Signature;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

/**
 * States are a pair of a connected component (to be turned into a DAG) and the parent DAG component, with respect
 * to which the connected component needs to be treated. This is necessary since the answer to the question which nodes in the
 * connected component can become DAG roots depends on the parent DAG.
 * @author JG
 */
public class ComponentAutomaton extends TreeAutomaton<Pair<ConnectedComponent, DAGComponent>> {

    private final SGraph graph;
    private final AMRBlobUtils blobUtils;
    
    public ComponentAutomaton(SGraph graph, AMRBlobUtils blobUtils) {
        super(new Signature());
        this.graph = graph;
        this.blobUtils = blobUtils;
        
        // add one final state: All nodes are in the ConnectedComponent, and the "parent" DAGComponent is null.
        addFinalState(addState(new Pair(new ConnectedComponent(graph.getGraph().vertexSet()), null)));
        
    }

    @Override
    public Iterable<Rule> getRulesBottomUp(int i, int[] ints) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isBottomUpDeterministic() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    

    @Override
    public Iterable<Rule> getRulesTopDown(int labelId, int parentState) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * The central function of this automaton, the only queries it can answer.
     * Makes up labels for the signature as it goes (this is necessary since
     * we don't know the arity before we compute the children of the rule).
     * @param parentState
     * @return 
     */
    @Override
    public Iterable<Rule> getRulesTopDown(int parentState) {
        
        Pair<ConnectedComponent, DAGComponent> parent = getStateForId(parentState);
        
        if (parent.right == null) {
            //this means we have the start state
            assert parent.left.getAllNodes().equals(graph.getGraph().vertexSet());
            
            //compute main DAG component
            GraphNode root = this.graph.getNode(this.graph.getNodeForSource("root"));
            DAGComponent mainDAG = new DAGComponent(graph, root, blobUtils);
            
            // get remaining connected components
            Collection<ConnectedComponent> connComps = ConnectedComponent.getAllConnectedComponents(graph, mainDAG.getAllAsGraphNodes());
            
            return Collections.singleton(makeRule(connComps, mainDAG, root, parent));
            
        } else {
            // this means we don't have the start state as parent and need to do some actual work.
            DAGComponent dagComp = parent.right;
            ConnectedComponent connComp = parent.left;


            //find the node in dagComp that *must* be the target of the modify operation that connects connComp to dagComp
            GraphNode uniqueModifiee = dagComp.findUniqueModifiee(connComp.getAllNodes());
            
            //find possible roots in connComp
            Set<GraphNode> possibleRoots = new HashSet<>();
            for (GraphEdge e : graph.getGraph().edgesOf(uniqueModifiee)) {
                if (connComp.getAllNodes().contains(e.getTarget())) {
                    possibleRoots.add(e.getTarget());
                } else if (connComp.getAllNodes().contains(e.getSource())) {
                    possibleRoots.add(e.getSource());
                }
            }
            
            List<Rule> ret = new ArrayList<>();
            for (GraphNode root : possibleRoots) {

                DAGComponent newDAG = DAGComponent.createWithoutForbiddenNodes(graph, root, blobUtils, dagComp.getAllAsGraphNodes());
                // the following may be possible to do more efficiently via some sort of automaton-wide dynamic programming
                Set<GraphNode> removedNodes = new HashSet<>();
                removedNodes.addAll(graph.getGraph().vertexSet());
                removedNodes.removeAll(connComp.getAllNodes());
                removedNodes.addAll(newDAG.getAllAsGraphNodes());
                Collection<ConnectedComponent> connComps = ConnectedComponent.getAllConnectedComponents(graph, removedNodes);
                
                ret.add(makeRule(connComps, newDAG, root, parent));
                
            }
            
            return ret;
        }
    }
    
     
    private Rule makeRule(Collection<ConnectedComponent> connComps, DAGComponent dagComp, GraphNode root, Pair<ConnectedComponent, DAGComponent> parent) {
        int ar = connComps.size();
        String label = root.getName()+"/"+root.getLabel().split("~")[0]+"__"+ar;
        
        List<Pair<ConnectedComponent, DAGComponent>> children = 
                connComps.stream().map(cc -> new Pair<>(cc, dagComp)).collect(Collectors.toList());
        
        return this.createRule(parent, label, children);
        
    }
    
    
    public ConcreteTreeAutomaton<Pair<ConnectedComponent, DAGComponent>> asConcreteTreeAutomatonTopDown() {        
        ConcreteTreeAutomaton ret = new ConcreteTreeAutomaton(getSignature(),this.stateInterner);
        processAllRulesTopDown(rule -> ret.addRule(ret.createRule(getStateForId(rule.getParent()),
                rule.getLabel(this), getStatesFromIds(rule.getChildren()),rule.getWeight())));
        finalStates.stream().forEach(finalState -> ret.addFinalState(ret.getIdForState(getStateForId(finalState))));
        return ret;
        //return new UniversalAutomaton(getSignature()).intersect(this).asConcreteTreeAutomaton();
    }
    
    /**
     * Iterates through all rules top-down, applying processingFunction to each
     * rule found.
     *
     * @param processingFunction
     */
    @Override
    public void processAllRulesTopDown(Consumer<Rule> processingFunction) {
        BitSet seenStates = new BitSet();
        IntPriorityQueue agenda = new IntArrayFIFOQueue();

        for (int finalState : getFinalStates()) {
            seenStates.set(finalState);
            agenda.enqueue(finalState);
        }

        while (!agenda.isEmpty()) {
            int state = agenda.dequeueInt();

            Iterable<Rule> rules = getRulesTopDown(state);

            for (Rule rule : rules) {
                if (processingFunction != null) {
                    processingFunction.accept(rule);
                }

                for (int child : rule.getChildren()) {
                    if (!seenStates.get(child)) {
                        seenStates.set(child);
                        agenda.enqueue(child);
                    }
                }
            }
        }

    }
    
    
    
    public static void main(String[] args) throws IOException {
        
        String corpusPath = "/Users/jonas/Documents/data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";
        AMRBlobUtils blobUtils = new DMBlobUtils();
        
        GraphReader2015 gr = new GraphReader2015(corpusPath);
        Graph sdpGraph;
        
        int max = 1000;
        int i = 0;
        
        while ((sdpGraph = gr.readGraph()) != null && i++ < max){
            MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
            
            SGraph graph = inst.getGraph();
            
            ConcreteTreeAutomaton auto = new ComponentAutomaton(graph, blobUtils).asConcreteTreeAutomatonTopDown();
            
            System.err.println(Math.log(auto.countTrees())/Math.log(2));
            
            
//            System.err.println(auto.asConcreteTreeAutomatonTopDown());
//            System.err.println();
//            System.err.println();

        }
        
    }
    
    
}
