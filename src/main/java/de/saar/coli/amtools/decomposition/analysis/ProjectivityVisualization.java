package de.saar.coli.amtools.decomposition.analysis;

import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
//import jdk.nashorn.internal.objects.Global;
import org.mapdb.Atomic;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.Constants;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class ProjectivityVisualization {

    public static void main(String[] args) throws IOException, ParseException {

        String amConllPath = args[0];
        List<AmConllSentence> amConllSentences = AmConllSentence.readFromFile(amConllPath);
        OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(args[1]),
                Charset.forName("UTF-8").newEncoder()
        );

        int index = 0;
        int fails = 0;
        Counter<Integer> nonprojectivityDegrees = new Counter<>();
        Counter<String> headCounter = new Counter<>();

        for (AmConllSentence amConllSentence : amConllSentences) {

            // turn amConllSentence into latex and write to file

            int minimumNonprojectivityDepth = 1;

            Set<AmConllEntry> nonprojectiveTargets = amConllSentence.stream().filter(entry ->
                    !entry.getEdgeLabel().equals(AmConllEntry.IGNORE)
                            && getNonprojectivityDegree(amConllSentence, entry.getHead(), entry.getId()) == minimumNonprojectivityDepth).collect(Collectors.toSet());

            boolean hasNonprojective = !nonprojectiveTargets.isEmpty();

            if (hasNonprojective) {
                StringJoiner sj = new StringJoiner("\n");
                //header
                sj.add("\\begin{dependency}[amdep]");

                //sentence text
                sj.add("\t\\begin{deptext}[column sep=.5cm]");
                StringJoiner sjDepText = new StringJoiner(" \\& ");
                StringJoiner sjFauxDepText = new StringJoiner(" \\& ");
                for (AmConllEntry entry : amConllSentence) {
                    String form = entry.getForm();
                    form = form.equals("$") ? "\\$" : form;
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
                        String style = nonprojectiveTargets.contains(entry) ? "edge style={blue!60!black,ultra thick}" : "";
                        if (nonprojectiveTargets.contains(entry)) {
                            nonprojectivityDegrees.add(getNonprojectivityDegree(amConllSentence, entry.getHead(), entry.getId()));
                            headCounter.add(amConllSentence.get(head-1).getLemma());
                        }
                        sj.add("\t\\depedge["+style+"]{" + head + "}{" + target + "}{" + operation + "{\\src{" + source + "}}}");
                    } else if (entry.getEdgeLabel().equals(AmConllEntry.ROOT_SYM)) {
                        sj.add("\t\\deproot[]{" + (entry.getId()) + "}{root}");
                    }
                }

                //finish
                sj.add("\\end{dependency}");

                w.write(sj.toString());
                w.write("\n\n\\\\\n\n");


                // evaluate to get SDP graph

                Graph sdpSent = new Graph(Integer.toString(index));
                sdpSent.addNode(Constants.WALL_FORM, Constants.WALL_LEMMA, Constants.WALL_POS, false, false, Constants.WALL_SENSE); //some weird dummy node.

                for (AmConllEntry word : amConllSentence) { //build a SDP Graph with only the words copied from the input.
                    if (!word.getForm().equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
                        sdpSent.addNode(word.getForm(), word.getLemma(), word.getPos(), false, false, "_");
                    }
                }

                try {
                    AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(amConllSentence);
                    SGraph evaluatedGraph = amdep.evaluate(true);
                    Graph outputSent = SGraphConverter.toSDPGraph(evaluatedGraph, sdpSent); //add edges

                    //                System.out.println(amConllSentence);
                    //                System.out.println(outputSent);


                    // turn SDP graph into latex and write to file


                } catch (Exception ex) {
                    ex.printStackTrace();
                    fails++;
                }
            }
            index++;
        }

        w.close();

        nonprojectivityDegrees.printAllSorted();

        headCounter.printAllSorted();

        System.out.println("Fails: "+fails);

    }

    /**
     * both origin and target are 1-based
     * @param amConllSentence
     * @param origin
     * @param target
     * @return
     */
    private static int getNonprojectivityDegree(AmConllSentence amConllSentence, int origin, int target) {
        boolean foundNonprojective = false;
        for (AmConllEntry entry : amConllSentence) {
            //TODO this probably is easier with stream
            if (!entry.getEdgeLabel().equals(AmConllEntry.IGNORE) && intersect(entry, origin, target)) {
                foundNonprojective = true;
                break;
            }
        }
        if (!foundNonprojective) {
            return 0;
        } else {
            int originsParent = amConllSentence.getParent(origin - 1).getId();
            return getNonprojectivityDegree(amConllSentence, originsParent, target)+1;
        }
    }

    private static boolean isBetween(AmConllEntry edge1, int index) {
        int min = Math.min(edge1.getId(), edge1.getHead());
        int max = Math.max(edge1.getId(), edge1.getHead());
        return min < index && index < max;
    }

    private static boolean isOutside(AmConllEntry edge1, int index) {
        int min = Math.min(edge1.getId(), edge1.getHead());
        int max = Math.max(edge1.getId(), edge1.getHead());
        return index < min || max < index;
    }

    private static boolean intersect(AmConllEntry edge1, int origin, int target) {
        return isBetween(edge1, origin) && isOutside(edge1, target);
    }

}
