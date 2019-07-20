/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.amr;

import com.google.common.collect.Sets;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import static de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractorCLI.LITERAL_JOINER;
import de.saar.coli.amrtagging.formalisms.amr.tools.Relabel;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPEdge;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author matthias
 */
public class AMR implements Formalism{
    
    private final Relabel relabeler;
    
    //List of most frequent "properties" (we treat them as edge labels)
    public static final List<Pattern> PROPERTY_EDGES = Arrays.asList( 
        Pattern.compile("op[0-9]+"), Pattern.compile("polarity"), Pattern.compile("quant"), Pattern.compile("mode"),
        Pattern.compile("year[0-9]*"),Pattern.compile("month"), Pattern.compile("day"),
        Pattern.compile("li"), Pattern.compile("polite"),
        Pattern.compile("decade"), Pattern.compile("century"),
        Pattern.compile("timezone"), Pattern.compile("era"));
    
    //node labels that make an edge definitely not a property
    public static final Set<String> DEFINITELY_EDGE = Sets.newHashSet("and","or");
    
    //node labels that make an edge definitely a property
    public static final Set<String> PROBABLY_PROPERTY = Sets.newHashSet("name","monetary-quantity","temporal-quantity","date-entity");
    
    //node labels (at the target) that are not properties
    public static final Set<String> DEFINITELY_EDGE_BASED_ON_TARGET = Sets.newHashSet("amr-unknown");
    
    
    
    public AMR(String wordnetPath, String conceptnetPath, String mapsPath, int nnThreshold) throws IOException, MalformedURLException, InterruptedException{
        relabeler = new Relabel(wordnetPath, conceptnetPath, mapsPath, nnThreshold, 0);
    }
    
    
    @Override
    public ConlluSentence refine(ConlluSentence sentence) {
        return sentence;
    }

    @Override
    public MRPGraph preprocess(MRPGraph mrpgraph) {
        return mrpgraph;
    }

    @Override
    public MRPGraph postprocess(MRPGraph mrpgraph) {
        return mrpgraph;
    }

    @Override
    public MRInstance toMRInstance(ConlluSentence sentence, MRPGraph mrpgraph) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MRPGraph evaluate(ConllSentence amconll) {
        for (ConllEntry entry : amconll){
            entry.setForm(entry.getForm().replace(" ", LITERAL_JOINER));
        }
        
        SGraph evaluatedGraph;
        try {
            AMDependencyTree amdep = AMDependencyTree.fromSentence(amconll);
            evaluatedGraph = amdep.evaluateWithoutRelex(true);
        } catch (ParserException | AMDependencyTree.ConllParserException  | ParseException ex ) {
            ex.printStackTrace();
            System.err.println("Returning empty graph");
            return MRPUtils.getDummy("amr", amconll.getId(), amconll.getAttr("raw"), amconll.getAttr("time"), amconll.getAttr("version"));
        }
        //rename nodes names from 1@@m@@--LEX-- to LEX@0
        List<String> labels = amconll.lemmas();
        for (String n : evaluatedGraph.getAllNodeNames()){
            if (evaluatedGraph.getNode(n).getLabel().contains("LEX")){
                Pair<Integer,Pair<String,String>> info = AMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                labels.set(info.left-1, amconll.get(info.left-1).getReLexLabel());
                evaluatedGraph.getNode(n).setLabel("LEX@"+(info.left-1));
            } else {
                Pair<Integer,Pair<String,String>> info = AMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                evaluatedGraph.getNode(n).setLabel(info.right.right);
            }
        }
        evaluatedGraph = evaluatedGraph.withFreshNodenames();
        relabeler.fixGraph(evaluatedGraph, amconll.getFields((ConllEntry entry) ->
         {
             if (entry.getReplacement().equals("_")) {
                 return entry.getForm().toLowerCase();
             } else {
                 return entry.getReplacement().toLowerCase();
             }
        }), amconll.words(), amconll.getFields(entry -> {
            String relex = entry.getReLexLabel();
            if (relex.equals("_")) return "NULL";
                    else return relex;
        }));
        removeWikiEdges(evaluatedGraph);
        
        //MRPGraph g = MRPUtils.fromSGraph(evaluatedGraph,PROPERTY_EDGES, 2, "amr", amconll.getId(), amconll.getAttr("raw"), amconll.getAttr("version"), amconll.getAttr("time"));
        String input = amconll.getAttr("raw");
        if (input == null){
            input = amconll.getAttr("input");
        }
        MRPGraph g = fromSGraph(evaluatedGraph, 2, "amr", amconll.getId(), input , amconll.getAttr("version"), amconll.getAttr("time"));
        return g;
        
    }
    
    /**
     * Removes wiki edges and the nodes at their end since they don't occur in the MRP data.
     * @param sg 
     */
    private void removeWikiEdges(SGraph sg){
        List<GraphEdge> edges = new ArrayList<>(sg.getGraph().edgeSet());
        for (GraphEdge e : edges){
            if (e.getLabel().equals("wiki")){
                sg.getGraph().removeEdge(e);
                sg.removeNode(e.getTarget().getName());
            }
        }
    }

    @Override
    public AMSignatureBuilder getSignatureBuilder(MRInstance instance) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public AlignmentTrackingAutomaton getAlignmentTrackingAutomaton(MRInstance instance) throws ParseException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void refineDelex(ConllSentence sentence) {
        
    }
    
    
    
     /**
     * Converts back to MRPGraph, no anchoring is used -- employs some AMR-specific hacks.
     * @param sg
     * @param propertyNames a list of regular expressions that match on edge labels which are actually properties (and the node label at the target is the value)
     * @param flavor
     * @param framework
     * @param graphId
     * @param raw
     * @param version
     * @param time
     * @return 
     */
    private MRPGraph fromSGraph(SGraph sg, int flavor, String framework, String graphId, String raw, String version, String time){
        MRPGraph output = new MRPGraph();
        output.sanitize();
        output.setId(graphId);
        output.setFramework(framework);
        output.setFlavor(flavor);
        output.setInput(raw);
        output.setVersion(version);
        output.setTime(time);
        
        Map<Integer,String> id2node = new HashMap<>();
        Map<String,Integer> node2id = new HashMap<>();
        
        int index = 0;
        
        List<String> nodes = new ArrayList<>(sg.getAllNodeNames());
        nodes.sort((String s1, String s2) -> s1.compareTo(s2));
        for (String node: nodes){
            node2id.put(node, index);
            id2node.put(index, node);
            GraphNode gN = sg.getNode(node);
            if (sg.getGraph().incomingEdgesOf(gN).size() == 1){
                //only nodes with single incoming edges can be properties
                GraphEdge e = sg.getGraph().incomingEdgesOf(gN).iterator().next();
                if (! isPropertyEdge(e,sg)) {
                    //nodes with sources cannot be properties
                    output.getNodes().add(new MRPNode(index,gN.getLabel(),new ArrayList<>(),new ArrayList<>(),null));
                    index++;
                }
            } else {
                output.getNodes().add(new MRPNode(index,gN.getLabel(),new ArrayList<>(),new ArrayList<>(),null));
                index++;
            }
            
        }

        //add top node
        Set<Integer> tops = new HashSet<>();
        String rootName = sg.getNodeForSource("root");
        tops.add(node2id.get(rootName));
        output.setTops(tops);

        //add edges
        List<GraphEdge> edges = new ArrayList<>(sg.getGraph().edgeSet());
        edges.sort((GraphEdge e1, GraphEdge e2) -> e1.getSource().getName().compareTo(e2.getSource().getName()));
        for (GraphEdge e :  edges){
            if (! isPropertyEdge(e,sg)) {
                output.getEdges().add(new MRPEdge(node2id.get(e.getSource().getName()), node2id.get(e.getTarget().getName()),e.getLabel()));
            } else {
                output.getNode(node2id.get(e.getSource().getName())).getProperties().add(e.getLabel());
                output.getNode(node2id.get(e.getSource().getName())).getValues().add(e.getTarget().getLabel());
            }
        }
        
        
        return output;
    }
    
    /**
     * Tells if the specific edge actually is a property (true) or a normal edge (false).
     * @param e
     * @param sg
     * @return 
     */
    private boolean isPropertyEdge(GraphEdge e, SGraph sg){
        if (sg.getGraph().edgesOf(e.getTarget()).size() != 1) return false; //must have single incoming edge
        if (sg.getSourcesAtNode(e.getTarget().getName()).size() > 0) return false; //must not have sources
        
        String label = e.getSource().getLabel();
        
        if (DEFINITELY_EDGE.contains(label)) return false;
        if (DEFINITELY_EDGE_BASED_ON_TARGET.contains(e.getTarget().getLabel())) return false;
        
        if (e.getTarget().getName().startsWith("explicitanon")) return true;
        
        for (Pattern propertyName : PROPERTY_EDGES){
            Matcher m = propertyName.matcher(e.getLabel());
            if (m.matches() && sg.getGraph().edgesOf(e.getTarget()).size() == 1){
                return true;
            }
        }
        return PROBABLY_PROPERTY.contains(label) ;
    }
    
    
}
