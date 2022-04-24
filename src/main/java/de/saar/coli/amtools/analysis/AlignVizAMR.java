package de.saar.coli.amtools.analysis;


import de.saar.coli.amrtagging.Alignment;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 *
 * @author Jonas
 */
public class AlignVizAMR {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to corpus", required=true)
    private String corpusPath;

    // is optional; if not given, assumes alignments are given in corpus file as [alignment] entry
    @Parameter(names = {"--alignments", "-a"}, description = "Path to alignment file")
    private String alignmentPath = null;

    @Parameter(names = {"--outdir", "-o"}, description = "Output folder", required=true)
    private String outDir;

    @Parameter(names = {"--max", "-m"}, description = "maximum number of instances processed. Use -1 for all instances (is default)")
    private int max=-1;

    @Parameter(names = {"--set", "-s"}, description = "add this parameter with a list of numbers (comma separated) to do only the indices with this index. Example: -s 5,7,10 will visualize the alignments for instances 5, 7, 10 (0 based)")
    private String set = null;


    @Parameter(names = {"--verbose", "-v"}, description = "Add this flag to include node names and word indices in output.")
    private boolean verbose = false;

    @Parameter(names = {"--lexBold", "-l"}, description = "Prints nodes marked with a '!' bold.")
    private boolean lexBold = false;

    @Parameter(names = {"--writeAsPNG", "-png"}, description = "Writes output in png format rather than pdf.")
    private boolean writeAsPNG = false;

    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;


    private static final String WORD_NODE_PREFIX = "wordindex";
    private static final String INDEX_NODE_PREFIX = "indexLit";

    //TODO: fix colors and have more
    //colors are in HSV (hue, saturation, value)
    public static final String[] COLORS = new String[]{
            "0.63 1.00 0.83",//blue
            "0.30 1.00 0.71",//green
            "0.50 1.00 0.88",//turquoise
            "0.10 1.00 1.00",//orange
            "0.00 1.00 0.86",//red
            "0.79 1.00 0.82",//purple
            "0.84 1.00 1.00",//pink
            "0.13 1.00 0.58",//brown
            "0.20 1.00 0.93",//yellow-green
            "0.66 1.00 0.48",//navy-blue
            "0.19 1.00 0.55",//olive
            "0.63 1.00 0.63",//blue//repeat a bit darker
            "0.30 1.00 0.51",//green
            "0.50 1.00 0.68",//turquoise
            "0.10 1.00 0.80",//orange
            "0.00 1.00 0.66",//red
            "0.79 1.00 0.62",//purple
            "0.84 1.00 0.80",//pink
            "0.13 1.00 0.38",//brown
            "0.20 1.00 0.73",//yellow-green
            "0.66 1.00 0.28",//navy-blue
            "0.19 1.00 0.35",//olive
            "0.63 0.66 0.83",//blue//repeat with lower saturation
            "0.30 0.66 0.71",//green
            "0.50 0.66 0.88",//turquoise
            "0.10 0.66 1.00",//orange
            "0.00 0.66 0.86",//red
            "0.79 0.66 0.82",//purple
            "0.84 0.66 1.00",//pink
            "0.13 0.66 0.58",//brown
            "0.20 0.66 0.93",//yellow-green
            "0.66 0.66 0.48",//navy-blue
            "0.19 0.66 0.55"//olive
    };

    private static final String[] COLORS_LIGHT = new String[]{
            "0.63 0.20 0.83",//blue
            "0.30 0.20 0.71",//green
            "0.50 0.20 0.88",//turquoise
            "0.10 0.20 1.00",//orange
            "0.00 0.20 0.86",//red
            "0.79 0.20 0.82",//purple
            "0.84 0.20 1.00",//pink
            "0.13 0.20 0.58",//brown
            "0.20 0.20 0.93",//yellow-green
            "0.66 0.15 0.65",//navy-blue
            "0.19 0.15 0.70",//olive
            "0.63 0.20 0.83",//blue//currently just repeating the light colors!
            "0.30 0.20 0.71",//green
            "0.50 0.20 0.88",//turquoise
            "0.10 0.20 1.00",//orange
            "0.00 0.20 0.86",//red
            "0.79 0.20 0.82",//purple
            "0.84 0.20 1.00",//pink
            "0.13 0.20 0.58",//brown
            "0.20 0.20 0.93",//yellow-green
            "0.66 0.15 0.65",//navy-blue
            "0.19 0.15 0.70",//olive
            "0.63 0.20 0.83",//blue
            "0.30 0.20 0.71",//green
            "0.50 0.20 0.88",//turquoise
            "0.10 0.20 1.00",//orange
            "0.00 0.20 0.86",//red
            "0.79 0.20 0.82",//purple
            "0.84 0.20 1.00",//pink
            "0.13 0.20 0.58",//brown
            "0.20 0.20 0.93",//yellow-green
            "0.66 0.15 0.65",//navy-blue
            "0.19 0.15 0.70"//olive
    };

    private final static String GREY = "lightgrey";//should never be used
    private final static String BLACK = "black";

    /**
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     * @throws de.up.ling.irtg.corpus.CorpusReadingException
     * @throws java.lang.InterruptedException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, CorpusReadingException, InterruptedException {

        AlignVizAMR viz = new AlignVizAMR();
        JCommander commander = new JCommander(viz);
        commander.setProgramName("viz");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (viz.help) {
            commander.usage();
            return;
        }

        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation("graph", new Interpretation(new GraphAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("string", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        if (viz.alignmentPath == null) {
            loaderIRTG.addInterpretation("alignment", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        }
        Corpus corpus = Corpus.readCorpus(new FileReader(viz.corpusPath), loaderIRTG);

        BufferedReader alBR = null;
        if (viz.alignmentPath != null) {
            alBR = new BufferedReader(new FileReader(viz.alignmentPath));
        }

        String outpath = viz.outDir;
        if (!outpath.endsWith("/")) {
            outpath = outpath+"/";
        }
        new File(outpath).mkdirs();

        boolean useLexColor = false;//Boolean.valueOf(args[3]);

        IntSet set = null;
        if (viz.set != null) {
            set = new IntOpenHashSet();
            for (String index : viz.set.split(",")) {
                set.add(Integer.parseInt(index));
            }
            System.err.println("Visualizing alignments for instances in "+set);
        }

        int i = 0;
        for (Instance inst : corpus) {
            //stop loop if we ran out of alignments in the file, or if the max is reached
            if ((alBR != null && !alBR.ready()) || (viz.max>=0 && i>=viz.max)) {
                break;
            }
            String alLine;
            if (alBR == null) {
                alLine = String.join(" ", (List)inst.getInputObjects().get("alignment"));
            } else {
                alLine = alBR.readLine();//do this before any skips, to keep the reader up to date
            }
            //if we specified a set of indices and i is not in it, skip it.
            if (set != null && !set.contains(i)) {
                i++;
                continue;
            }
            SGraph graph = (SGraph)inst.getInputObjects().get("graph");
            if (graph == null) {
                i++;
                continue;
            }
            List<String> sentence = (List)inst.getInputObjects().get("string");

            //TODO: deal with edge alignments --EDIT: do this later, work with only blobs for now.
            Set<Alignment> allAlignments = new HashSet();
            readAlignments(graph, alLine, allAlignments);

            visualize(viz.verbose, viz.lexBold, outpath, i, graph, sentence, allAlignments, viz.writeAsPNG);
            i++;
        }

    }

    public static void visualize(boolean verbose, boolean lexBold, String outpath, int i, SGraph graph,
                                 List<String> sentence, Collection<Alignment> allAlignments, boolean writeAsPNG)
            throws IOException, InterruptedException {

        AMRBlobUtils amrBlobUtils = new AMRBlobUtils();

        Files.createDirectories(Paths.get(outpath));


        Map<String, Set<Alignment>> node2align = new HashMap<>();
        for (GraphNode node : graph.getGraph().vertexSet()) {
            node2align.put(node.getName(), new HashSet());
        }
        for (Alignment alignment : allAlignments) {
            for (String nn : alignment.nodes) {
                node2align.get(nn).add(alignment);
            }
        }

        FileWriter w = new FileWriter(outpath + i +".dot");
        w.write("digraph G {\n");

        //TODO: add root

        //amr nodes
        for (GraphNode node : graph.getGraph().vertexSet()) {
            Set<Alignment> alsHere = node2align.get(node.getName());
            Boolean doLex = false;
            for (Alignment al : alsHere) {
                if (al.lexNodes.contains(node.getName())) {
                    if (doLex) {
                        System.err.println("Node "+node.getName()+" has multiple lexical alignments in graph "+ i +". Using last one found in set iterator.");
                    }
                    doLex = true;
                }
            }
            String label = verbose ? node.getName()+" / "+ node.getLabel() : node.getLabel();
            label = escapeBadCharacters(label);
            if (!lexBold || !doLex) {
                w.write("  "+node.getName()+" [label=\""+label+"\"");
            } else {
                w.write("  "+node.getName()+" [label=<<b>"+label+"</b>>");//fontcolor=\""+lexColor+"\",
            }
            if (alsHere.size()>=2) {
                String col = makeColorList(alsHere, true);
                w.write(", style=\"wedged\", color=\""+col+"\"");
            } else {
                String col = makeColorList(alsHere, false);
                w.write(", style=\"bold\", color=\""+col+"\"");
            }
            w.write("];\n");
        }

        //amr edges
        for (GraphEdge edge : graph.getGraph().edgeSet()) {
            w.write("  "+edge.getSource().getName()+"->"+edge.getTarget().getName());
            w.write(" [label=\""+edge.getLabel()+"\", style=\"bold\"");
            if (amrBlobUtils.isOutbound(edge)) {
                w.write(", color=\""+makeColorList(node2align.get(edge.getSource().getName()), false)+"\"");
            } else {
                w.write(", color=\""+makeColorList(node2align.get(edge.getTarget().getName()), false)+"\"");
            }
            w.write("];\n");
        }

        //sentence
        w.write("\n  subgraph sentence {\n    rank=\"sink\";\n");
        int j = 1;
        for (String word : sentence) {
            String wordLabel = verbose ? (j-1)+"_"+word : word;
            wordLabel = escapeBadCharacters(wordLabel);
            w.write("    "+WORD_NODE_PREFIX+j+" [label=\""+wordLabel+"\", penwidth=0];\n");
            j++;
        }
        for (j=1; j< sentence.size(); j++) {
            w.write("    "+WORD_NODE_PREFIX+j+" -> "+WORD_NODE_PREFIX+(j+1)+" [style=\"invis\"];\n");
        }
        w.write("  }\n\n");
        //alignments
        for (Alignment al : allAlignments) {
            Set<String> attachingNodes = new HashSet();
            if (al.lexNodes.isEmpty()) {
                attachingNodes.add(al.nodes.iterator().next());
            } else {
                attachingNodes.addAll(al.lexNodes);
            }
            for (String nn : attachingNodes) {
                w.write("  "+nn+"->"+WORD_NODE_PREFIX+(al.span.start+1));//note the index shift
                w.write(" [color=\""+COLORS[al.color]+"\", style=\"dashed\", arrowhead=\"none\"];\n");
            }
            for (int index = al.span.start+1; index<al.span.end; index++) {
                w.write("  "+WORD_NODE_PREFIX+index+"->"+WORD_NODE_PREFIX+(index+1));
                w.write(" [color=\""+COLORS[al.color]+"\", style=\"dashed\", arrowhead=\"none\"];\n");
            }
        }

        w.write("}");
        w.close();

        if (writeAsPNG) {
            Process p = Runtime.getRuntime().exec("dot -Tpng -o"+ outpath + i +".png "+ outpath + i +".dot");
            p.waitFor();
        } else {
            Process p = Runtime.getRuntime().exec("dot -Tpdf -o" + outpath + i + ".pdf " + outpath + i + ".dot");
            p.waitFor();
        }
    }


    private static String escapeBadCharacters(String label) {
        return label.replaceAll("\"", "'");
    }


    private static String makeColorList(Set<Alignment> als, boolean isLight) {
        if (als.isEmpty()) {
            return isLight? GREY : BLACK;
        }
        StringJoiner ret = new StringJoiner(":");
        for (Alignment al : als) {
            if (isLight) {
                ret.add(COLORS_LIGHT[al.color]);
            } else {
                ret.add(COLORS[al.color]);
            }
        }
        return ret.toString();
    }

    /**
     * Writes all alignments of the alLine string in the node2align map, such that
     * each node name is mapped to the set of alignments it is part of. The graph
     * is just for reference: node2align will contain an entry for all node names
     * in the graph (possibly mapping to an empty set).
     * @param graph
     * @param alLine
     */
    private static void readAlignments(SGraph graph, String alLine, Set<Alignment> allAlignments) {

    /*
    Alignment format: n1|n2!|n3||start-end
    Arbitrary amounts of nx, where nx are node names, and start and end are
    boundaries of a span (the span includes start and excludes end). The '!' denotes the lexicalised nodes, and
    is optional (if no node is marked, lexNodes in the Alignment is empty).
    */




        String[] parts = alLine.split(" ");
        int i = 0;
        for (String al : parts) {
            if (!al.equals("")) {

                Alignment alignment = Alignment.read(al, i);

                allAlignments.add(alignment);

                i = (i+1)%COLORS.length;
            }
        }
    }



}
