/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.AMDecompositionAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.BoundaryRepresentation;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.tree.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A version of AlignmentTrackingAutomaton that matches node names
 * in constants exactly. Note that the constants in the signature for this have
 * to be subgraphs of the full SGraph (i.e. node names need to match node names
 * in the full SGraph). The word 'concrete' in the class name is a reference to
 * the concrete vs absolute 
 * @author jonas
 */
public class ConcreteAlignmentTrackingAutomaton extends AlignmentTrackingAutomaton {
    // BoundaryRepresentation = s-graph state, AMDecompositionAutomaton.Type = AM type, both together are as-graph. Integer: position in string (head index)
    
    private final IsiAmrInputCodec codec = new IsiAmrInputCodec();
    
    protected ConcreteAlignmentTrackingAutomaton(ApplyModifyGraphAlgebra alg, Signature signature, SGraph graph, Map<Integer, Set<String>> index2nns,
            Function<Pair<SGraph, ApplyModifyGraphAlgebra.Type>, Double> scoreConst) throws ParseException {
        super(alg, signature, graph, index2nns, scoreConst);
    }

    @Override
    public Iterable getRulesBottomUp(int labelId, int[] childStates) {
        String label = signature.resolveSymbolId(labelId);
        String[] labelParts = label.split(SEPARATOR);
        switch (childStates.length) {
            case 0:
            {
                String[] asGraphParts = labelParts[1].split(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP);
                AMDecompositionAutomaton.Type type;
                try {
                    type = AMDecompositionAutomaton.Type.fromAlgebraType(new ApplyModifyGraphAlgebra.Type(asGraphParts[1]), decomp.getGraphInfo());
                } catch (ParseException ex) {
                    System.err.println("Could not parse type "+asGraphParts[1]+". Ignored this constant "+ label +".");
                    System.err.println(ex.toString());
                    return cacheRules(Collections.EMPTY_SET, labelId, childStates);
                }
                SGraph subgraph = codec.read(asGraphParts[0]);
                //return exactly matching 
                BoundaryRepresentation br = new BoundaryRepresentation(subgraph, decomp.getGraphInfo());
                double score = scoreConst.get(decomp.getSignature().getIdForSymbol(labelParts[1]));
                Pair<BoundaryRepresentation, AMDecompositionAutomaton.Type> state = new Pair(br, type);
                decomp.getStateInterner().addObject(state);
                int stateID = makeState(state, Integer.parseInt(labelParts[0]));
                Collection<Rule> ret = Collections.singleton(createRule(stateID,labelId, childStates, score));
                return cacheRules(ret, labelId, childStates);
            }
            case 2:
            {
                String[] heads = label.split(FROM_TO_SEPARATOR);
                if (Integer.valueOf(heads[0]) != state2head.get(childStates[0]) || Integer.valueOf(heads[1]) != state2head.get(childStates[1])) {
                    return Collections.EMPTY_LIST;
                }
                List<Rule> ret = new ArrayList<>();
                int[] decompStates = new int[2];
                decompStates[0] = state2decompstate.get(childStates[0]);
                decompStates[1] = state2decompstate.get(childStates[1]);
                int head = labelParts[1].startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION) ? state2head.get(childStates[0]) : state2head.get(childStates[0]);
                for (Rule rule : decomp.getRulesBottomUp(decomp.getSignature().getIdForSymbol(labelParts[1]), decompStates)) {
                    ret.add(createRule(makeState(decomp.getStateForId(rule.getParent()), head), labelId, childStates, 1.0));
                }
                return cacheRules(ret, labelId, childStates);
            }
            default:
                throw new UnsupportedOperationException("Not supported (can only deal with 0 or 2 children).");
        }
    }
    
    // The following are just copied over from AlignmentTrackingAutomaton
    
    /**
     * Like the other 'create' function, but assigning weight according to sigBuilder to constants (scoreGraph).
     * @param inst
     * @param sigBuilder builder for signature, i.e. class for finding the constants
     * @param coref
     * @return
     * @throws IllegalArgumentException
     * @throws ParseException 
     */
    public static ConcreteAlignmentTrackingAutomaton create(MRInstance inst, AMSignatureBuilder sigBuilder, boolean coref) throws IllegalArgumentException, ParseException {
        return create(inst, sigBuilder, coref, sigBuilder::scoreGraph);
    }
    
    /**
     * Creates a new AlignmentTrackingAutomaton, also building the signature for decomposing it,
     * using the given sigBuilder and its getConstantsForAlignment method.This function is similar to de.up.ling.irtg.algebra.graph.AMSignatureBuilder.makeDecompositionSignatureWithAlignments.
     * @param inst the Instance (graph, sentence and alignments) to be decomposed
     * @param sigBuilder builder for signature, i.e. class for finding the constants (for instance AMRSignatureBuilder) and invokes getConstantsForAlignment
     * @param coref whether to use coref sources (set to false in the ACL 2018 experiments)
     * @param scoreConst A function that assigns a weight to each constant. Used for
     * scoring source assignments according to heuristic preferences in the ACL 2018 experiments.
     * @return
     * @throws IllegalArgumentException
     * @throws ParseException 
     */
    public static ConcreteAlignmentTrackingAutomaton create(MRInstance inst, AMSignatureBuilder sigBuilder, boolean coref,
            Function<Pair<SGraph, ApplyModifyGraphAlgebra.Type>, Double> scoreConst) throws IllegalArgumentException, ParseException {
        Signature sig = new Signature();
        Signature plainSig = new Signature();
        Map<Integer, Set<String>> index2nns = new HashMap();
        HashSet<String> usedSources = new HashSet();
        ApplyModifyGraphAlgebra strToGraph = new ApplyModifyGraphAlgebra();
        for (Alignment al : inst.getAlignments()) {
            index2nns.put(al.span.start, al.nodes);
            Set<String> consts = sigBuilder.getConstantsForAlignment(al, inst.getGraph(), coref);
            consts.stream().forEach(c -> {
//                System.err.println(c);
                try {
                    strToGraph.parseString(c).left.getAllSources().stream().filter(sourceName -> !sourceName.equals("root")).forEach(usedSources::add);
                } catch (ParserException ex) {
                    throw new IllegalArgumentException("The string representation of a constant couldn't be read in again (very strange)",ex);
                }
            });
            consts.stream().forEach(c -> sig.addSymbol(al.span.start+SEPARATOR+c, 0));
            consts.stream().forEach(c -> plainSig.addSymbol(c, 0));
        }
        
//        System.err.println(usedSources);
        //Collection<String> sources = sigBuilder.getAllPossibleSources(inst.getGraph()); //ml: don't ask the graph with this method which sources might be needed, instead extract them from the extracted constants --matthias
        for (String s : usedSources) {
            plainSig.addSymbol(ApplyModifyGraphAlgebra.OP_APPLICATION+s, 2);
            plainSig.addSymbol(ApplyModifyGraphAlgebra.OP_MODIFICATION+s, 2);
            for (int i = 0; i<inst.getSentence().size(); i++) {
                for (int j = 0; j<inst.getSentence().size(); j++) {
                    if (i != j) {
                        sig.addSymbol(i+FROM_TO_SEPARATOR+j+SEPARATOR+ApplyModifyGraphAlgebra.OP_APPLICATION+s, 2);
                        sig.addSymbol(i+FROM_TO_SEPARATOR+j+SEPARATOR+ApplyModifyGraphAlgebra.OP_MODIFICATION+s, 2);
                    }
                }
            }
        }
        return new ConcreteAlignmentTrackingAutomaton(new ApplyModifyGraphAlgebra(plainSig), sig, inst.getGraph(), index2nns, scoreConst);
    }
    
    
    
    
    public static void main(String[] args) throws IllegalArgumentException, ParseException {
        SGraph graph;
        List<Alignment> als;
        List<String> sent;
        for (int i =0; i<=11; i++) {
            als = new ArrayList<>();
            switch (i){
                case 0:
                    //the boy sleeps
                    sent = Arrays.asList("the boy sleeps".split(" "));
                    graph = new IsiAmrInputCodec().read("(s<root> / sleep :ARG0 (b / boy))");
                    als.add(Alignment.read("s!||2-3"));
                    als.add(Alignment.read("b!||1-2"));
                    break;
                case 1:
                    //the boy wants to sleep
                    sent = Arrays.asList("the boy wants to sleep".split(" "));
                    graph = new IsiAmrInputCodec().read("(w<root> / want :ARG1 (s / sleep :ARG0 (b / boy)) :ARG0 b)");
                    als.add(Alignment.read("s!||4-5"));
                    als.add(Alignment.read("b!||1-2"));
                    als.add(Alignment.read("w!||2-3"));
                    break;
                case 2:
                    //the girl sings and dances
                    sent = Arrays.asList("the girl sings and dances".split(" "));
                    graph = new IsiAmrInputCodec().read("(a<root> / and :op1 (s / sing :ARG0 (g / girl)) :op2 (d / dance :ARG0 g))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("s!||2-3"));
                    als.add(Alignment.read("d!||4-5"));
                    als.add(Alignment.read("g!||1-2"));
                    break;
                case 3:
                    //the lizard wants and trains to sing beautifully
                    sent = Arrays.asList("the lizard wants and trains to sing beautifully".split(" "));
                    graph = new IsiAmrInputCodec().read("(a<root> / and :op1 (w / want :ARG1"
                                + " (s / sing :ARG0 (l / lizard) :manner (b / beautiful)) :ARG0 l)"
                            + " :op2 (t / train :ARG1 s :ARG0 l))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("s!||6-7"));
                    als.add(Alignment.read("b!||7-8"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("w!||2-3"));
                    als.add(Alignment.read("t!||4-5"));
                    break;
                case 4:
                    //the lizard wants and seems to dance (testing coordination of complex types)
                    sent = Arrays.asList("the lizard wants and seems to dance".split(" "));
                    graph = new IsiAmrInputCodec().read("(a<root> / and :op1 (w / want :ARG1"
                                + " (d / dance :ARG0 (l / lizard)) :ARG0 l)"
                            + " :op2 (s / seem :ARG1 d))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("d!||6-7"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("w!||2-3"));
                    als.add(Alignment.read("s!||4-5"));
                    break;
                case 5:
                    //the lion commands and persuades the snake to dance (testing rename and coord of rename)
                    sent = Arrays.asList("the lion commands and persuades the snake to dance".split(" "));
                    graph = new IsiAmrInputCodec().read("(a<root> / and :op1 (c / command"
                                + " :ARG2 (d / dance :ARG0 (s / snake)) :ARG1 s :ARG0 (l / lion))"
                            + " :op2 (p / persuade :ARG2 d :ARG1 s :ARG0 l))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("d!||8-9"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("c!||2-3"));
                    als.add(Alignment.read("p!||4-7"));
                    als.add(Alignment.read("s!||6-7"));
                    break;
                case 6:
                    //the boy wants to sleep, bad alignments (testing multiple aligned nodes) //TODO currently multiple sources <s, s2> at b
                    sent = Arrays.asList("the boy wants to sleep".split(" "));
                    graph = new IsiAmrInputCodec().read("(w<root> / want :ARG1 (s / sleep :TEST-of (b / boy)) :ARG0 b)");
                    als.add(Alignment.read("w|s!||4-5"));
                    als.add(Alignment.read("b!||1-2"));
                    break;
                case 7:
                    //the good dog
                    sent = Arrays.asList("the good dog".split(" "));
                    graph = new IsiAmrInputCodec().read("(d<root> / dog :mod (g / good))");
                    als.add(Alignment.read("g!||2-3"));
                    als.add(Alignment.read("d!||1-2"));
                    break;
                case 8:
                    //the lizard sings beautifully
                    sent = Arrays.asList("the lizard sings beautifully".split(" "));
                    graph = new IsiAmrInputCodec().read("(s<root> / sing :ARG0 (l / lizard) :manner (b / beautiful))");
                    als.add(Alignment.read("s!||2-3"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("b!||3-4"));
                    break;
                case 9:
                    //the lizard wants and trains to sing beautifully
                    sent = Arrays.asList("the lizard wants and trains to sing".split(" "));
                    graph = new IsiAmrInputCodec().read("(a<root> / and :op1 (w / want :ARG1"
                                + " (s / sing :ARG0 (l / lizard)) :ARG0 l)"
                            + " :op2 (t / train :ARG1 s :ARG0 l))");
                    als.add(Alignment.read("a!||3-4"));
                    als.add(Alignment.read("s!||6-7"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("w!||2-3"));
                    als.add(Alignment.read("t!||4-5"));
                    break;
                case 10:
                    //the lizard wants to sing beautifully
                    sent = Arrays.asList("the lizard wants to sing beautifully".split(" "));
                    graph = new IsiAmrInputCodec().read("(w<root> / want :ARG1"
                                + " (s / sing :ARG0 (l / lizard) :manner (b / beautiful)) :ARG0 l)");
                    als.add(Alignment.read("s!||4-5"));
                    als.add(Alignment.read("b!||5-6"));
                    als.add(Alignment.read("l!||1-2"));
                    als.add(Alignment.read("w!||2-3"));
                    break;
                case 11:
                    //the boy wants to seem to sleep, (testing nested control and raising)
                    sent = Arrays.asList("the boy wants to seem to sleep".split(" "));
                    graph = new IsiAmrInputCodec().read("(w<root> / want :ARG1 (s / seem :ARG1 (s2 / sleep :ARG0 (b / boy))) :ARG0 b)");
                    als.add(Alignment.read("w!||2-3"));
                    als.add(Alignment.read("s!||4-5"));
                    als.add(Alignment.read("s2!||6-7"));
                    als.add(Alignment.read("b!||1-2"));
                    break;
                default:
                    return;
            }

            ConcreteAlignmentSignatureBuilder sb = new ConcreteAlignmentSignatureBuilder(graph, als, new AMRBlobUtils());
            
            //print alignments and constants for debugging
//            for (TokenAlignment al : als) {
//                System.err.println();
//                System.err.println(al);
//                System.err.println(sb.getConstantsForAlignment(al, graph, false));
//            }
            
            ConcreteAlignmentTrackingAutomaton auto = create(new MRInstance(sent, graph, als), sb, false);
            
            //print constants in signature for debugging
//            System.err.println("\nSIGNATURE");
//            for (int id = 1; id <= auto.signature.getMaxSymbolId(); id++) {
////                if (auto.signature.getArity(id) ==0) {
//                    System.err.println(auto.signature.resolveSymbolId(id));
////                }
//            }
//            System.err.println();

            //print source
//            for (int j = 0; j<auto.decomp.getGraphInfo().getNrSources(); j++) {
//                System.err.println(auto.decomp.getGraphInfo().getSourceForInt(j));
//            }
////            
            auto.processAllRulesBottomUp(rule -> {
//                System.err.println(rule.toString(auto));
//                System.err.println("LABEL: "+rule.getLabel(auto));
//                System.err.println("TRESULT: "+auto.getStateForId(rule.getParent()).left.right.toAlgebraType(auto.decomp.getGraphInfo()));
//                System.err.println();
                    });
            System.err.println(auto.viterbi());
//            auto.decomp.processAllRulesBottomUp(null);
//            System.err.println(auto.decomp.viterbi());
            
//            for (Map<Pair<GraphNode, String>, Set<GraphNode>> nestedArgNSource2blobTarget : sb.node2nestedArgNsource2blobTarget.values()) {
//                System.err.println(getCompatibleAnnotations(nestedArgNSource2blobTarget));
//            }
//            for (GraphNode node : graph.getGraph().vertexSet()) {
//                System.err.println(node.toString()+": "+sb.getSourceAssignments(sb.blobUtils.getBlobEdges(graph, node), graph));
//            }
            System.err.println();
        }
    }
    
    
}
