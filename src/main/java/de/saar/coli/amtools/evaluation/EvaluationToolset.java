package de.saar.coli.amtools.evaluation;

import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

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
     * Applies postprocessing to a corpus. The default implementation applies no postprocessing, returning the meaning representation unchanged
     * unchanged. Implementations of this may change mrInstance in place, but must still return it!
     * @param mrInstance the meaning representation (including the sentence and alignments, as well as POS, NE tags and lemmata, as far as they were given in the AMConll file) before postprocessing
     * @param origAMConllSentence the original AMConll sentence, which may contain information pertinent to the postprocessing
     * @return the mrInstance after postprocessing
     */
    public MRInstance applyPostprocessing(MRInstance mrInstance, AmConllSentence origAMConllSentence) {
        return mrInstance;
    }


}
