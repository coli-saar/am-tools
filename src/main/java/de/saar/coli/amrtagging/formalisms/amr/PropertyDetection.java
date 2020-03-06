/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr;

import com.google.common.collect.Sets;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides utilities to discover "properties" (MRP 2019 terminology) in AMR graphs, which are treated differently by Smatch.
 * 
 * @author matthias, based on work done by Pia
 */
public class PropertyDetection {
    
// not in use, right now
//        //List of most frequent "properties" (we treat them as edge labels)
//    public static final List<Pattern> COMMON_PROPERTY_LABELS = Arrays.asList(
//        Pattern.compile("op[0-9]+"), Pattern.compile("polarity"), Pattern.compile("quant"), Pattern.compile("mode"),
//        Pattern.compile("year[0-9]*"),Pattern.compile("month"), Pattern.compile("day"),
//        Pattern.compile("li"), Pattern.compile("polite"),
//        Pattern.compile("decade"), Pattern.compile("century"),
//        Pattern.compile("timezone"), Pattern.compile("era"));
//    
//    //source node labels that make an edge definitely not a property
//    public static final Set<String> DEFINITELY_EDGE_BASED_ON_SRC = Sets.newHashSet("and","or");
//    
//    //source node labels that make an edge definitely a property
//    public static final Set<String> PROBABLY_PROPERTY_BASED_ON_SRC = Sets.newHashSet("name","monetary-quantity","temporal-quantity","date-entity");
//    
//    //target node labels that are not values of properties
//    public static final Set<String> DEFINITELY_EDGE_BASED_ON_TARGET = Sets.newHashSet("amr-unknown");
    
    
      /**
     * Tells if the specific edge actually is a property (true) or a normal edge (false).
     * @param e edge
     * @param sg graph containing edge e
     * @return true if edge e is a proerty (with target as its value), otherwise returns false: edge is a normal edge
     */
    //private boolean isPropertyEdge2(GraphEdge e, SGraph sg){
    //    if (sg.getGraph().edgesOf(e.getTarget()).size() != 1) return false; //only leaf nodes can be values of properties
    //    if (sg.getSourcesAtNode(e.getTarget().getName()).size() > 0) return false; //must not have sources
    //
    //    String sourcenodelabel = e.getSource().getLabel();
     //   String targetnodelabel = e.getTarget().getLabel();

    //    if (DEFINITELY_EDGE_BASED_ON_SRC.contains(sourcenodelabel)) return false;
    //    if (DEFINITELY_EDGE_BASED_ON_TARGET.contains(targetnodelabel)) return false;
    //
    //    if (e.getTarget().getName().startsWith("explicitanon")) return true;
    //
    //    for (Pattern propertyName : COMMON_PROPERTY_LABELS){
    //        Matcher m = propertyName.matcher(e.getLabel());
    //        if (m.matches()){
    //            return true;
     //       }
     //   }
     //   return PROBABLY_PROPERTY_BASED_ON_SRC.contains(sourcenodelabel) ;
    //}


    // edge label (100% prop) is  mode  li  mod  polite  year  month  day  decade  century  era  quarter
    // edge label  is  polarity  (83.9657 % of the time a prop)  value (99.4341 %)  timezone (93.8776 %)
    public static final List<Pattern> DEFINITELY_PROPERTY_LABELp = Arrays.asList(
            Pattern.compile("li"), Pattern.compile("polite"), Pattern.compile("mode"), //Pattern.compile("mod"),
            Pattern.compile("year[0-9]*"),Pattern.compile("month"), Pattern.compile("day"),
            Pattern.compile("quarter"), Pattern.compile("decade"), Pattern.compile("century"), Pattern.compile("era"),
            Pattern.compile("polarity"), Pattern.compile("value"), Pattern.compile("timezone"));
    // edge label  op (53.1026 % of the time a property)  quant (56.5030 % of the time a property)
    public static final List<Pattern> PROBABLY_PROPERTY_LABELp = Arrays.asList(
            Pattern.compile("op[0-9]*"));  // , Pattern.compile("quant")
    // edge label (100% edge) is  unit  domain
    // public static final Set<String> DEFINITELY_EDGE_LABEL = Sets.newHashSet("unit", "domain");
    // value is Number (digit, float, time like dd:dd or dd:dd:dd ), URL , -
    public static final List<Pattern> DEFINITELY_PROPERTY_BASED_ON_TARGETp = Arrays.asList(
            Pattern.compile("-"), Pattern.compile("-?[0-9]+([.:/][0-9]+)?(:[0-9][0-9])?"),
            Pattern.compile("http.*"), Pattern.compile("www[.].*"));  // todo Pattern.compile("__NUMBER__") add ?
    // amr-unknown ( 0.269 % )  wordsense assumed to be 0 % property  todo: calculate percentage
    public static final List<Pattern> DEFINITELY_EDGE_BASED_ON_TARGETp = Arrays.asList(
            Pattern.compile("[a-z]+-[0-9][0-9]"), Pattern.compile("amr-unknown"), Pattern.compile("truth-value"));
    // public static final Set<String> PROBABLY_EDGE_BASED_ON_SRC = Sets.newHashSet("and","or");
    // , "over", "after", "before", "even-if", "amr-choice"  ?
    /**
     * Tells if the specific edge actually is a property (true) or a normal edge (false).
     * @param e edge
     * @param sg graph containing edge e
     * @return true if edge e is a proerty (with target as its value), otherwise returns false: edge is a normal edge
     */
    public static boolean isPropertyEdge(GraphEdge e, SGraph sg){
        if (sg.getGraph().edgesOf(e.getTarget()).size() != 1) return false; //only leaf nodes can be values of properties
        if (sg.getSourcesAtNode(e.getTarget().getName()).size() > 0) return false; //must not have sources

        String sourcenodelabel = e.getSource().getLabel();
        String targetnodelabel = e.getTarget().getLabel();
        String edgelabel = e.getLabel();

        if (e.getTarget().getName().startsWith("explicitanon")) return true;  // not tested

        for (Pattern nodename : DEFINITELY_EDGE_BASED_ON_TARGETp){  // e.g wordsense, amr-unknown
            Matcher m = nodename.matcher(targetnodelabel);
            if (m.matches()) return false;
        }
        // if (DEFINITELY_EDGE_LABEL.contains(edgelabel)) return false;  // e.g.  unit  domain
        for (Pattern nodename : DEFINITELY_PROPERTY_BASED_ON_TARGETp){  // e.g number, url, '-'  todo add __NUMBER__ ?
            Matcher m = nodename.matcher(targetnodelabel);
            if (m.matches()) return true;
        }
        for (Pattern label : DEFINITELY_PROPERTY_LABELp){  // e.g  li  year  mode  value  polarity
            Matcher m = label.matcher(edgelabel);
            if (m.matches()) return true;
        }
        if (targetnodelabel.endsWith("-quantity") || targetnodelabel.endsWith("-entity")) return false;
        // return False

        // if edgesrcname in PROBABLY_EDGE_BASED_ON_SRC:
        //     return False
        /*  maybe test:  if tgtname.endswith("-quantity") or tgtname.endswith("-entity"): return False */
        // if (PROBABLY_EDGE_BASED_ON_SRC.contains(sourcenodelabel)) return false;  // e.g.  and  or
        /* maybe test
        if targetnodelabel in QUANTITYWORDS: return False
        if sourcenodelabel in DIRECTIONWORDS: return False
        */
        for (Pattern label : PROBABLY_PROPERTY_LABELp){  // e.g  op1  quant   fps: before -quant-> multiple ?
            Matcher m = label.matcher(edgelabel);
            if (m.matches() && sourcenodelabel.equals("name")) return true;
        }
        return false;
        // return PROBABLY_PROPERTY_BASED_ON_SOURCE.contains(sourcenodelabel);
    }
    
    
    /**
     * Returns a copy of the AMR graph where property nodes are renamed with a prefixing "_", which causes the
     * Penman code to suppress the name of the node.
     * @param evaluatedGraph
     * @return 
     */
     public static SGraph fixProperties(SGraph evaluatedGraph){
        
        Set<String> hideNames = new HashSet<>();
        
         //now rename property nodes so they have the correct style 
        for (GraphNode n : evaluatedGraph.getGraph().vertexSet()){
            
            Set<GraphEdge> edges = evaluatedGraph.getGraph().incomingEdgesOf(n);
            if (edges.size() == 1 && evaluatedGraph.getGraph().outDegreeOf(n) == 0 
                    && evaluatedGraph.getSourcesAtNode(n.getName()).isEmpty()){ 
                
                //exactly one incoming edge, no outgoing edge, no sources, in particular no root source
                
                GraphEdge e = edges.iterator().next();
                if (isPropertyEdge(e, evaluatedGraph)){
                    hideNames.add(n.getName()); 
                } 
            }
        }
        
        SGraph ret = new SGraph();
        
        for (GraphNode n : evaluatedGraph.getGraph().vertexSet()){
            if (hideNames.contains(n.getName())){ 
                //node names starting with _ get suppressed in output
                ret.addNode("_"+n.getName(), n.getLabel());
            } else {
                ret.addNode(n.getName(), n.getLabel());
            }
        }
        
        for (GraphEdge e : evaluatedGraph.getGraph().edgeSet()){
            String tgt = hideNames.contains(e.getTarget().getName()) ? "_" + e.getTarget().getName() : e.getTarget().getName();
            
            ret.addEdge(ret.getNode(e.getSource().getName()), ret.getNode(tgt), e.getLabel());
        }
        
        //now copy over root source
        
        String rootNode = evaluatedGraph.getNodeForSource("root");
        
        if (hideNames.contains(rootNode)){
            ret.addSource("root", "_"+rootNode);
        } else {
            ret.addSource("root", rootNode);
        }
        
        
        return ret;
    }

    
    
    
    
}
