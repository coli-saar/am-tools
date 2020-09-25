package de.saar.coli.amtools.decomposition.analysis;

import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amtools.analysis.AmConllComparator;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.Constants;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

//import jdk.nashorn.internal.objects.Global;

public class VisualizeThreeAmconll {

    private static final String RED = "red!60!black";
    private static final String BLUE = "blue!60!black";

    public static void main(String[] args) throws IOException, ParseException {

        String amConllPath = args[0];
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amConllPath);
        List<AmConllSentence> amConllSentences1 = AmConllSentence.readFromFile(args[1]);
        List<AmConllSentence> amConllSentences2 = AmConllSentence.readFromFile(args[2]);

        System.out.println("UF gold vs original: "+ AmConllComparator.getF(amConllSentences, amConllSentences2, false, false));
        System.out.println("UF gold vs harmonized: "+ AmConllComparator.getF(amConllSentences, amConllSentences1, false, false));
        System.out.println("AM-F gold vs original: "+ AmConllComparator.getF(amConllSentences, amConllSentences2, true, false));
        System.out.println("AM-F gold vs harmonized: "+ AmConllComparator.getF(amConllSentences, amConllSentences1, true, false));
        System.out.println("LF gold vs original: "+ AmConllComparator.getF(amConllSentences, amConllSentences2, true, true));
        System.out.println("LF gold vs harmonized: "+ AmConllComparator.getF(amConllSentences, amConllSentences1, true, true));



        OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(args[3]),
                StandardCharsets.UTF_8.newEncoder()
        );


        for (int i = 0; i<amConllSentences.size(); i++) {

            AmConllSentence amConllSentence = amConllSentences.get(i);
            AmConllSentence amConllSentence1 = amConllSentences1.get(i);
            AmConllSentence amConllSentence2 = amConllSentences2.get(i);
            // turn amConllSentence into latex and write to file


            Map<Integer, String> edgeColorsGold = new HashMap<>();
            Map<Integer, String> edgeLabelColorsGold = new HashMap<>();
            for (AmConllEntry goldEntry : amConllSentence) {
                AmConllEntry harmonizedEntry = amConllSentence1.get(goldEntry.getId()-1); // getId is 1-based, get is 0-based
                if (harmonizedEntry.getEdgeLabel().equals(AmConllEntry.IGNORE) && !goldEntry.getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                    edgeColorsGold.put(goldEntry.getId(), BLUE);
                    edgeLabelColorsGold.put(goldEntry.getId(), BLUE);
                }
            }
            Map<Integer, String> edgeColors1 = new HashMap<>();
            Map<Integer, String> edgeLabelColors1 = new HashMap<>();
            for (AmConllEntry entry : amConllSentence1) {
                AmConllEntry goldEntry = amConllSentence.get(entry.getId()-1); // getId is 1-based, get is 0-based
                if (entry.getHead() != goldEntry.getHead()) {
                    edgeColors1.put(entry.getId(), RED);
                    edgeLabelColors1.put(entry.getId(), RED);
                } else if (!entry.getEdgeLabel().equals(goldEntry.getEdgeLabel())) {
                    edgeLabelColors1.put(entry.getId(), RED);
                }
            }
            Map<Integer, String> edgeColors2 = new HashMap<>();
            Map<Integer, String> edgeLabelColors2 = new HashMap<>();
            for (AmConllEntry entry : amConllSentence2) {
                AmConllEntry goldEntry = amConllSentence.get(entry.getId()-1); // getId is 1-based, get is 0-based
                if (entry.getHead() != goldEntry.getHead()) {
                    edgeColors2.put(entry.getId(), RED);
                    edgeLabelColors2.put(entry.getId(), RED);
                } else if (!entry.getEdgeLabel().equals(goldEntry.getEdgeLabel())) {
                    edgeLabelColors2.put(entry.getId(), RED);
                }
            }

            writeSentencePrefix(w);



            writeTableEntry(amConllSentence, "PAS original", w, edgeColorsGold, edgeLabelColorsGold);
            writeTableEntry(amConllSentence1, "DM harmonized", w, edgeColors1,  edgeLabelColors1);
            writeTableEntry(amConllSentence2, "DM original", w, edgeColors2,  edgeLabelColors2);

            writeSentencePosfix(w);

        }

        w.close();
    }

    private static void writeSentencePrefix(Writer w) throws IOException {
        w.write("\\begin{my}\n" +
                "\\begin{tabular}{r l}\n");
    }

    private static void writeSentencePosfix(Writer w) throws IOException {
        w.write("\\end{tabular}\n" +
                "\\end{my}\n");
    }

    private static void writeTableEntry(AmConllSentence amConllSentence, String graphLabel, Writer w, Map<Integer, String> edgeColors,
                                        Map<Integer, String> edgeLabelColors) throws IOException {
        writePrefix(graphLabel, w);
        w.write(getAmConllSentenceVisualizationCode(amConllSentence, edgeColors, edgeLabelColors));
        writePostfix(w);
    }

    private static void writePrefix(String graphLabel, Writer w) throws IOException {
        //TODO make this heading align better with the tree
        w.write("\\Large{\\textbf{"+graphLabel+"}} &\n");
    }

    private static void writePostfix(Writer w) throws IOException {
        w.write("\n\n\\\\\n\n");
    }

    /**
     *
     * @param amConllSentence
     * @param edgeColors Maps sentence entries to the color that their incoming edge label should be (1-based word indices)
     *                   Can be a partial map or null; then the default color (black) is used.
     * @param edgeLabelColors Maps sentence entries to the color that their incoming edge label should be (1-based word indices)
     *                   Can be a partial map or null; then the default color (black) is used.
     * @return
     */
    private static String getAmConllSentenceVisualizationCode(AmConllSentence amConllSentence, Map<Integer, String> edgeColors,
                                                              Map<Integer, String> edgeLabelColors) {
        StringJoiner sj = new StringJoiner("\n");
        //header
        sj.add("\\begin{dependency}[amdep]");

        //sentence text
        sj.add("\t\\begin{deptext}[column sep=.5cm]");
        StringJoiner sjDepText = new StringJoiner(" \\& ");
        StringJoiner sjFauxDepText = new StringJoiner(" \\& ");
        for (AmConllEntry entry : amConllSentence) {
            String form = entry.getForm();
            // escape some latex special characters, TODO may need to add more
            form = form.equals("$") ? "\\$" : form;
            form = form.equals("%") ? "\\%" : form;

            sjDepText.add(form);
            sjFauxDepText.add("");
        }
        sj.add("\t\t" + sjDepText.toString() + "\\\\");
        sj.add("\t\t" + sjFauxDepText.toString() + "\\\\");
        sj.add("\t\\end{deptext}");

        //edges
        for (AmConllEntry entry : amConllSentence) {
            boolean isApp = entry.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION);
            boolean isMod = entry.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION);
            if (isApp || isMod) {
                int target = entry.getId();
                int head = entry.getHead();
                String source = entry.getEdgeLabel().substring(4); // 4 is length of APP_ or MOD__
                String operation = isApp ? "\\app" : "\\modify";
                String style = "";
                if (edgeColors != null && edgeColors.containsKey(target)) {
                    style = "edge style={"+edgeColors.get(target)+",ultra thick}";
                }
                String edgeLabelColor = "black";
                if (edgeLabelColors != null && edgeLabelColors.containsKey(target)) {
                    edgeLabelColor = edgeLabelColors.get(target);
                }
                sj.add("\t\\depedge["+style+"]{" + head + "}{" + target + "}{\\textcolor{"+edgeLabelColor+"}{" + operation + "{\\src{" + source + "}}}}");
            } else if (entry.getEdgeLabel().equals(AmConllEntry.ROOT_SYM)) {
                sj.add("\t\\deproot[]{" + (entry.getId()) + "}{root}");
            }
        }

        //finish
        sj.add("\\end{dependency}");

        return sj.toString();
    }


}
