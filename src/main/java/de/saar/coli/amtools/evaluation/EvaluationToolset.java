package de.saar.coli.amtools.evaluation;

import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.up.ling.irtg.algebra.graph.GraphNode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;

import static de.saar.coli.amrtagging.AlignedAMDependencyTree.ALIGNED_SGRAPH_SEP;

/**
 * @author JG
 */
public class EvaluationToolset {

    /**
     * Writes the corpus to a file. The default implementation creates one file parserOut.txt which contains each graph in the corpus in a line,
     * separated by a blank line each. The graphs are in ISI AMR format (without sources), with the root being the first
     * node in the linearization.
     * @param outputFolderPath Filepath for the output folder. Create your own files in there!
     * @param outputCorpus The corpus that will be written to that file.
     */
    public void writeCorpus(String outputFolderPath, List<MRInstance> outputCorpus) throws IOException, InterruptedException {
        PrintWriter o = new PrintWriter(outputFolderPath+"/parserOut.txt"); //will contain graphs, potentially relabeled


        for (MRInstance s : outputCorpus){

            o.println(s.getGraph().toIsiAmrString());
            o.println();

        }

        o.close();

    }

    /**
     * Applies postprocessing to a corpus. The default implementation applies basic relabeling.
     * The graph at this stage is expected to have the output format of AlignedAMDependencyTree#evaluateWithoutRelex(true).
     * In particular, node labels are of the form i@@nn@@nl where i is the aligned token position in the sentence,
     * nn is the node name and nl is the node label, which is AmConllEntry.LEX_MARKER for lexical nodes and the actual
     * node label for all other nodes. The given node name nn does not have to match the actual node name here, since it is not used
     * (for example, SGraph#withFreshNodeNames may be called before this, as is the case in EvaluateAMConll).
     *
     * @param mrInstance the meaning representation to apply postprocessing to. Change in place!
     *    (Includes the sentence and alignments, as well as POS, NE tags and lemmata, as far as they were given in the AMConll file)
     * @param origAMConllSentence the original AMConll sentence, which may contain information pertinent to the postprocessing
     * @return nothing
     */
    public void applyPostprocessing(MRInstance mrInstance, AmConllSentence origAMConllSentence) {
        for (GraphNode node : new HashSet<>(mrInstance.getGraph().getGraph().vertexSet())) {
            String[] nodeLabelTriple = node.getLabel().split(ALIGNED_SGRAPH_SEP); // this is [sentence position, node name, node label]
            if (nodeLabelTriple[2].contains(AmConllEntry.LEX_MARKER)) {
                int i = Integer.parseInt(nodeLabelTriple[0]);
                node.setLabel(origAMConllSentence.get(i).getReLexLabel()); // then we relabel the node with the lexical lable for that position
            } else {
                node.setLabel(nodeLabelTriple[2]); // then we have a secondary node that already has its label
            }
        }
    }

    public void compareToGold(List<MRInstance> predictedInstances, String goldFilePath) throws IOException {
        throw new UnsupportedOperationException("This EvaluationToolset class cannot compare predicted graphs to gold graphs. " +
                "Do not use it with the --gold option ");
    }


}
