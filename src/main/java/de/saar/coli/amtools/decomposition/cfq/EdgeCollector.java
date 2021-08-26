package de.saar.coli.amtools.cfq;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.saar.basic.Pair;

public class EdgeCollector {
    private ListMultimap<String, Pair<String,String>> edges;
    private String firstEntity = null;

    public EdgeCollector() {
        edges = ArrayListMultimap.create();
    }

    public void collect(String source, String label, String target) {
        source = CfqToGraphConverter.stripPrefix(source);
        label = CfqToGraphConverter.stripPrefix(label);
        target = CfqToGraphConverter.stripPrefix(target);

        edges.put(source, new Pair(label, target));

        if( firstEntity == null ) {
            firstEntity = source;
        }
    }

    public String getFirstEntity() {
        return firstEntity;
    }

    public void foreachEdge(EdgeVisitor visitor) {
        edges.forEach((source, pair) -> {
            visitor.visit(source, pair.left, pair.right);
        });
    }

//    public void print() {
//        foreachEdge((source, label, target) -> {
//            System.err.printf("%s --%s--> %s\n", source, label, target);
//        });
//    }

    @Override
    public String toString() {
        return edges.toString();
    }

    public static interface EdgeVisitor {
        public void visit(String source, String label, String target);
    }
}
