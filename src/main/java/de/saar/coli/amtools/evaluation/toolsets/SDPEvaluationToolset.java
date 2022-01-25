package de.saar.coli.amtools.evaluation.toolsets;

import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import me.tongfei.progressbar.ProgressBar;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.Constants;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.io.GraphWriter2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;

import java.io.IOException;
import java.util.List;

public class SDPEvaluationToolset extends EvaluationToolset {

    @Override
    public void writeCorpus(String outputFolderPath, List<MRInstance> outputCorpus) throws IOException, InterruptedException {
        GraphWriter2015 grW = new GraphWriter2015(outputFolderPath + "/parserOut.sdp");
        final ProgressBar pb = new ProgressBar("Converting to write to file", outputCorpus.size());
        for (MRInstance inst : outputCorpus) {
            Graph outputSent = convertToSDPGraph(inst);
            grW.writeGraph(outputSent);

            pb.step();
        }
        pb.close();
        grW.close();
    }


    /**
     * Calls its super method for relabeling. Also adds the sentence ID to mrInstance.
     * @param mrInstance the meaning representation to apply postprocessing to. Change in place!
     *    (Includes the sentence and alignments, as well as POS, NE tags and lemmata, as far as they were given in the AMConll file)
     * @param origAMConllSentence the original AMConll sentence, which may contain information pertinent to the postprocessing
     */
    @Override
    public void applyPostprocessing(MRInstance mrInstance, AmConllSentence origAMConllSentence) {
        super.applyPostprocessing(mrInstance, origAMConllSentence);
        mrInstance.setExtra("id", origAMConllSentence.getAttr("id"));
    }

    @Override
    public void compareToGold(List<MRInstance> predictedInstances, String goldFilePath) throws IOException {
        // We are converting to SDP graphs again here. If this is too slow, we can store the SDP graph as an
        // extra in the MRInstance objects. But that is a bit hacky, so for now I'll leave it at this.
        GraphReader2015 goldReader = new GraphReader2015(goldFilePath);
        Scorer scorer = new Scorer();
        final ProgressBar pb = new ProgressBar("Converting to compare to gold", predictedInstances.size());
        for (MRInstance inst : predictedInstances) {
            Graph goldGraph = goldReader.readGraph();
            Graph outputSent = convertToSDPGraph(inst);
            scorer.update(goldGraph, outputSent);
            pb.step();
        }
        pb.close();
        System.out.println("Labeled Scores");
        System.out.println("Precision " + scorer.getPrecision());
        System.out.println("Recall " + scorer.getRecall());
        System.out.println("F " + scorer.getF1());
        System.out.println("Exact Match " + scorer.getExactMatch());
        System.out.println("------------------------");
        System.out.println("Core Predications");
        System.out.println("Precision " + scorer.getCorePredicationsPrecision());
        System.out.println("Recall " + scorer.getCorePredicationsRecall());
        System.out.println("F " + scorer.getCorePredicationsF1());
        System.out.println("------------------------");
        System.out.println("Semantic Frames");
        System.out.println("Precision " + scorer.getSemanticFramesPrecision());
        System.out.println("Recall " + scorer.getSemanticFramesRecall());
        System.out.println("F " + scorer.getSemanticFramesF1());
    }

    private Graph convertToSDPGraph(MRInstance inst) {

        // prepare raw output without edges
        String id = inst.getExtra("id") != null ? (String)inst.getExtra("id") : "#NO-ID";
        if (!id.startsWith("#")) id = "#" + id;
        Graph sdpSent = new Graph(id);
        sdpSent.addNode(Constants.WALL_FORM, Constants.WALL_LEMMA, Constants.WALL_POS, false, false, Constants.WALL_SENSE); //some weird dummy node.

        for (int i = 0; i<inst.getSentence().size(); i++) { //build a SDP Graph with only the words copied from the input.
            if (!inst.getSentence().get(i).equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)) {
                sdpSent.addNode(inst.getSentence().get(i), inst.getLemmas().get(i), inst.getPosTags().get(i), false, false, "_");
            }
        }

        try {
            sdpSent = SGraphConverter.toSDPGraph(inst, sdpSent); //add edges
        } catch (Exception ex) {
            System.err.printf("For id=%s: ignoring exception.\n", id);
            ex.printStackTrace();
            System.err.println("Writing graph without edges instead.\n");
        }

        return sdpSent;

    }
}
