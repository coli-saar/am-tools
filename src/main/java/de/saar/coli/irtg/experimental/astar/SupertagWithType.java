package de.saar.coli.irtg.experimental.astar;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AnnotatedSupertag;
import de.up.ling.irtg.algebra.Algebra;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;

import java.io.Serializable;
import java.util.Objects;

/**
 * A supertag graph together with its type. Note that the graph by itself
 * does not uniquely determine the type; thus we have to package them together.
 */
public class SupertagWithType implements Serializable {
    private SGraph graph;
    private ApplyModifyGraphAlgebra.Type type;

    public SupertagWithType(SGraph graph, ApplyModifyGraphAlgebra.Type type) {
        this.graph = graph;
        this.type = type;
    }

    public SGraph getGraph() {
        return graph;
    }

    public ApplyModifyGraphAlgebra.Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupertagWithType that = (SupertagWithType) o;
        return Objects.equals(graph, that.graph) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(graph, type);
    }

    @Override
    public String toString() {
        return type + ":" + graph;
    }

    public static SupertagWithType fromAnnotatedSupertag(AnnotatedSupertag st, Algebra<Pair<SGraph, ApplyModifyGraphAlgebra.Type>> alg) throws ParserException, ParseException {
        Pair<SGraph, ApplyModifyGraphAlgebra.Type> gAndT = alg.parseString(st.graph);

        if( st.type != null ) {
            // Null type should only happen for the NULL supertag.
            gAndT.right = new ApplyModifyGraphAlgebra.Type(st.type);
        }

        return new SupertagWithType(gAndT.left, gAndT.right);
    }
}
