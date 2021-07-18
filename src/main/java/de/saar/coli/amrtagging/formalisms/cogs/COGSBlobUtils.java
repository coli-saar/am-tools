package de.saar.coli.amrtagging.formalisms.cogs;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.SGraph;
import org.apache.commons.lang.NotImplementedException;


/**
 * BlobUtils used for the logical forms found in the COGS dataset (Kim and Linzen 2020), or at least our graph-version of it.
 * 
 * We try to combine it with automatic learning of source names, that's why -for now- we don't care about
 * methods like <code>edge2Source</code>.  <br>
 * Links:  <br>
 * - <a href="https://www.aclweb.org/anthology/2020.emnlp-main.731/">Kim and Linzen (2020): the COGS Paper</a><br>
 * - <a href="https://github.com/najoungkim/COGS">COGS dataset and code on Gihtub</a><br>
 * @author pia ( weissenh )  <br>
 * Created: April 2021
 */
public class COGSBlobUtils extends AMRBlobUtils {

    /** OUTBOUND_EDGES : these are edges which will end up in the graph constant of their *source* node<br>
     *
     * Note: the edges here _somewhat_ de-lexicalized: predicate <i>want.agent</i> ends up as edge <i>agent</i><br>
     *
     * Which edges should belong to their source node?<br>
     * - the usually frame edges (<i>agent, theme, recipient, xcomp, ccomp</i>): their source node is the verb<br>
     * - the special <i>iota</i> edge: its source node is the determiner node<br>
     * - if preposition reification: the outgoing edges from the preposition node (op1, op2)
     *
     * (the opposite is inbound edges, implicit)<br>
     * - if no preposition reification:
     *   since we decided to put the preposition edges ( <code>nmod.preposition</code> ) into the graph constant of the
     *   PP-noun, the edge should belong to the target node (the PP noun) instead of the source node (the modified noun)
     *   note: COGS dataset contains 3 prepositions: <code>"nmod.beside", "nmod.in", "nmod.on"</code>
     */
    public static final String[] OUTBOUND_EDGES = new String[]{
            "agent", "theme", "recipient", "xcomp", "ccomp",
            LogicalFormConverter.IOTA_EDGE_LABEL,
            LogicalFormConverter.NMOD_EDGE_1_LABEL, LogicalFormConverter.NMOD_EDGE_2_LABEL
    };


    @Override
    public boolean isOutbound(GraphEdge edge) {
        for (String regex : OUTBOUND_EDGES) {
            if (edge.getLabel().matches(regex)) {
                return true;
            }
        }
        return false;   
    }

    @Override
    public String edge2Source(GraphEdge edge, SGraph graph) {
        throw new NotImplementedException("COGS logical form: so far no edge2source implemented");
    }

    @Override
    public double scoreGraph(Pair<SGraph, ApplyModifyGraphAlgebra.Type> g){
        throw new NotImplementedException("COGS logical form: so far no scoreGraph implemented");
    }

     
}
