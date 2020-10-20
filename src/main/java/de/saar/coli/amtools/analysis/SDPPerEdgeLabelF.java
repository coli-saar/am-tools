package de.saar.coli.amtools.analysis;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.Constants;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SDPPerEdgeLabelF {

    public static void main(String[] args) throws IOException, ParseException {
//        String goldDMPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\en.id.dm.sdp";
//        String predictedDMPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\DM_full_mtl_1\\test_DM_id.amconll";
//        Pair<Map<String, Double>, Counter<String>> fAndCount = perEdgeLabelF(goldDMPath, predictedDMPath, false);
        writeCSV("DM", "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\en.id.dm.sdp", false,
                "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\dm.csv");
        writeCSV("PAS", "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\en.id.pas.sdp", false,
                "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\pas.csv");
        writeCSV("PSD", "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\en.id.psd.sdp", true,
                "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\psd.csv");
    }

    private static void writeCSV(String graphbankType, String goldFilePath, boolean doPSDPostprocessing, String outputFilePath) throws IOException, ParseException {
        String pathPrefix = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\testsetResults\\";
        String pathPostfix = "_id.amconll";
        String originalFullMiddle = "_full_mtl_1\\test_";
        String normalizedFullMiddle = "_mtl_04_19_full_1\\test_";
        String original100Middle = "_mtl_100_1\\test_";
        String normalized100Middle = "_04_19_mtl_100_1\\test_";
        Pair<Map<String, Double>, Counter<String>> of = perEdgeLabelF(goldFilePath, pathPrefix+graphbankType+originalFullMiddle+graphbankType+pathPostfix, doPSDPostprocessing);
        Pair<Map<String, Double>, Counter<String>> o100 = perEdgeLabelF(goldFilePath, pathPrefix+graphbankType+original100Middle+graphbankType+pathPostfix, doPSDPostprocessing);
        Pair<Map<String, Double>, Counter<String>> nf = perEdgeLabelF(goldFilePath, pathPrefix+graphbankType+normalizedFullMiddle+graphbankType+pathPostfix, doPSDPostprocessing);
        Pair<Map<String, Double>, Counter<String>> n100 = perEdgeLabelF(goldFilePath, pathPrefix+graphbankType+normalized100Middle+graphbankType+pathPostfix, doPSDPostprocessing);

        FileWriter w = new FileWriter(outputFilePath);
        w.write("label, count, original(full), normalized(full), original(100), normalized(100)\n");
        for (Object2IntMap.Entry<String> entry : of.right.getAllSorted()) {
            w.write(entry.getKey()+", "+entry.getIntValue()+", "+ of.left.get(entry.getKey())+", "+ nf.left.get(entry.getKey())+", "+ o100.left.get(entry.getKey())+", "+ n100.left.get(entry.getKey())+"\n");
        }
        w.close();
    }

    /**
     * Returns F and total count for each label in the gold graphs.
     * @param goldSDPPath
     * @param amconllPath
     * @param doPSDPostprocessing
     * @return
     * @throws IOException
     * @throws ParseException
     */
    public static Pair<Map<String, Double>, Counter<String>> perEdgeLabelF(String goldSDPPath, String amconllPath, boolean doPSDPostprocessing) throws IOException, ParseException {

        List<AmConllSentence> sents = AmConllSentence.readFromFile(amconllPath);
        System.err.println(sents.size());
        GraphReader2015 goldReader = new GraphReader2015(goldSDPPath);

        Counter<String> goldLabelCounts = new Counter<>();
        Counter<String> correctLabelCounts = new Counter<>();
        Counter<String> predictedLabelCounts = new Counter<>();


        for (AmConllSentence s : sents) {

            // prepare raw output without edges
            String id = s.getAttr("id") != null ? s.getAttr("id") : "#NO-ID";
            if (!id.startsWith("#")) id = "#" + id;
            Graph sdpSent = new Graph(id);
            sdpSent.addNode(Constants.WALL_FORM, Constants.WALL_LEMMA, Constants.WALL_POS, false, false, Constants.WALL_SENSE); //some weird dummy node.

            for (AmConllEntry word : s) { //build a SDP Graph with only the words copied from the input.
                if (!word.getForm().equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
                    sdpSent.addNode(word.getForm(), word.getLemma(), word.getPos(), false, false, "_");
                }
            }

            try {
                AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
                SGraph evaluatedGraph = amdep.evaluate(true);
                if (doPSDPostprocessing) {
                    evaluatedGraph = ConjHandler.restoreConj(evaluatedGraph, new PSDBlobUtils(), false);
                }

                Graph outputSent = SGraphConverter.toSDPGraph(evaluatedGraph, sdpSent); //add edges
                Graph goldGraph = goldReader.readGraph();
                if (outputSent.getNNodes() != goldGraph.getNNodes()) {
                    throw new IllegalArgumentException("word counts don't match "+outputSent.getNNodes()+" vs "+goldGraph.getNNodes());
                }
                Set<EdgeWithEquals> goldEdges = goldGraph.getEdges().stream().map(EdgeWithEquals::new).collect(Collectors.toSet());
                // Note: this isn't quite correct with duplicate edges (same source, target and label), but I don't think they exist in SDP
                for (Edge edge : outputSent.getEdges()) {
                    if (goldEdges.contains(new EdgeWithEquals(edge))) {
                        correctLabelCounts.add(edge.label);
                    }
                    predictedLabelCounts.add(edge.label);
                }
                for (Edge edge : goldGraph.getEdges()) {
                    goldLabelCounts.add(edge.label);
                }

            } catch (Exception ex) {
                System.err.printf("In line %d, id=%s: ignoring exception.\n", s.getLineNr(), id);
                ex.printStackTrace();
            }

        }

        Map<String, Double> ret = new HashMap<>();
        for (String label : goldLabelCounts.getAllSeen()) {
            double p;
            if (predictedLabelCounts.get(label) == 0) {
                p = 0;
            } else {
                p = correctLabelCounts.get(label) / (double) predictedLabelCounts.get(label);
            }
            double r;
            if (goldLabelCounts.get(label) == 0) {
                r = 0;
            } else {
                r = correctLabelCounts.get(label) / (double) goldLabelCounts.get(label);
            }
            if (p + r < 0.00000001) {
                ret.put(label, 0.0);
            } else {
                ret.put(label, 2*p*r/(p+r));
            }

        }
        return new Pair<>(ret, goldLabelCounts);
    }


    private static class EdgeWithEquals {
        private final Edge e;
        public EdgeWithEquals(Edge e) {
            this.e = e;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 31 * hash + e.target;
            hash = 31 * hash + e.source;
            hash = 31 * hash + (e.label == null ? 0 : e.label.hashCode());
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof EdgeWithEquals)) {
                return false;
            }
            EdgeWithEquals other = (EdgeWithEquals)obj;
            if (e.source != other.e.source || e.target != other.e.target) {
                return false;
            }
            if (e.label == null) {
                return other.e.label == null;
            }
            return e.label.equals(other.e.label);
        }
    }


}
