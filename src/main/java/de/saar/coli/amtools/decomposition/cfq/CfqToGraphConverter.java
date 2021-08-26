package de.saar.coli.amtools.cfq;

import de.saar.coli.amrtagging.GraphvizUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.GraphVizDotOutputCodec;
import de.up.ling.irtg.codec.OutputCodec;
import de.up.ling.irtg.codec.SgraphAmrOutputCodec;
import de.up.ling.irtg.codec.TikzSgraphOutputCodec;
import javassist.expr.Instanceof;
import me.tongfei.progressbar.ProgressBar;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprFunction;
import org.apache.jena.sparql.syntax.*;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;

public class CfqToGraphConverter {
    private static String PREFIX = "http://ns/";

    public static void main(String[] args) throws IOException {
        int numLines = countLines(args[0]);
        BufferedReader r = new BufferedReader(new FileReader(args[0]));
        String line = null;
        int instanceNumber = 1;

        OutputCodec<SGraph> ocGraph = new SgraphAmrOutputCodec();
        OutputCodec<SGraph> ocGraphviz = new MyGraphvizOutputCodec();

        try (ProgressBar pb = new ProgressBar("Converting CFQ corpus", numLines)) {
            while ((line = r.readLine()) != null) {
                pb.step();
                String s = "PREFIX ns: <" + PREFIX + "> " + line.strip(); //ad pseudo prefix to cope with freebase constants
                EdgeCollector ec = new EdgeCollector();

                for (String x : List.of("M0", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9")) {
                    s = s.replace(x, "ns:" + x);
                }

                // iterate triples and filters
                Query q = QueryFactory.create(s);
                ElementGroup conditions = (ElementGroup) q.getQueryPattern();

                for (Element el : conditions.getElements()) {
                    if (el instanceof ElementPathBlock) {
                        ElementPathBlock epb = (ElementPathBlock) el;
                        Iterator<TriplePath> it = epb.patternElts();
                        while (it.hasNext()) {
                            TriplePath x = it.next();

                            if (x.asTriple() == null) {
                                ec.collect(x.getSubject().toString(), x.getPath().toString(), x.getObject().toString());
                            } else {
                                ec.collect(x.getSubject().toString(), x.getPredicate().toString(), x.getObject().toString());
                            }
                        }
                    } else if (el instanceof ElementFilter) {
                        ElementFilter ef = (ElementFilter) el;
                        Expr filterExpression = ef.getExpr();
                        ExprFunction functionExpression = filterExpression.getFunction();

                        if (functionExpression == null) {
                            System.err.printf("[nofunction] %s\n", filterExpression);
                        } else {
                            ec.collect(functionExpression.getArg(1).toString(), "FILTER_" + functionExpression.getOpName(), functionExpression.getArg(2).toString());
                        }
                    } else {
                        System.err.printf("[%s] %s\n", el.getClass(), el);
                    }
                }

                // analyze SELECT query
                if (q.isDistinct()) {
                    // SELECT DISTINCT
                    Var var = q.getProjectVars().get(0);
                    String variableName = var.toString();
                    if (!variableName.startsWith("?")) {
                        variableName = "?" + variableName;
                    }

                    ec.collect("root", "SELECT_DISTINCT", variableName);
//                    System.err.printf("SELECT DISTINCT %s\n", variableName);
                } else {
                    // SELECT COUNT(*)
                    String variableName = stripPrefix(ec.getFirstEntity());
                    ec.collect("root", "SELECT_COUNT", variableName);
//                    System.err.printf("SELECT COUNT %s\n", variableName);
                }

                // convert into s-graph
                SGraph graph = new SGraph();
                ec.foreachEdge((source, label, target) -> {
                    GraphNode src = graph.addNode(source, null);
                    GraphNode tgt = graph.addNode(target, null);
                    graph.addEdge(src, tgt, label);
                });

                graph.addSource("root", "root");


                // save graphviz to a file
                File gvFilename = new File(String.format("graphviz/%d.txt", instanceNumber));
                gvFilename.getParentFile().mkdirs();
                try (FileOutputStream os = new FileOutputStream(gvFilename)) {
                    ocGraphviz.write(graph, os);
                }

                // save s-graph to a file
                File filename = new File(String.format("graphs/%d.txt", instanceNumber));
                filename.getParentFile().mkdirs();
                try (FileOutputStream os = new FileOutputStream(filename)) {
                    ocGraph.write(graph, os);
                }


                instanceNumber++;
//                if (instanceNumber > 100) {
//                    System.exit(0);
//                }
            }

        }

    }

    // TODO: prefix stripping needs to fit with CFQ format
    // (also replace <http...> strings; leave ns: prefix in where needed)
    public static String stripPrefix(String s) {
        if (s.startsWith("<" + PREFIX)) {
            s = s.substring(PREFIX.length() + 1);
            return s.substring(0, s.length() - 1);
        } else if (s.startsWith(PREFIX)) {
            return s.substring(PREFIX.length());
        } else {
            return s;
        }
    }

    public static class MyGraphvizOutputCodec extends OutputCodec<SGraph> {
        public void write(SGraph graph, OutputStream ostream) throws UnsupportedOperationException {
            PrintWriter w = new PrintWriter(new OutputStreamWriter(ostream));
            w.write("digraph G {\n");

            for (GraphNode node : graph.getGraph().vertexSet()) {
                String label;
                String name = stripPrefix(node.getName());
                label = "\"" + name + "\"";
                w.write("  \"" + name + "\" [label=" + label + "];\n");
            }

            for (GraphEdge edge : graph.getGraph().edgeSet()) {
                w.write("  \"" + stripPrefix(edge.getSource().getName())
                        + "\" -> \"" + stripPrefix(edge.getTarget().getName()) +
                        "\" [label=\""
                        + stripPrefix(edge.getLabel()) + "\"];\n");
            }
            w.write("}");
            w.close();
        }
    }

    private static int countLines(String filename) throws IOException {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            while (reader.readLine() != null) lines++;
        }
        return lines;
    }
}
