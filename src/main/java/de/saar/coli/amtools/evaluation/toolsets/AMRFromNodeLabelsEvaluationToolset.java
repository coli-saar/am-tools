package de.saar.coli.amtools.evaluation.toolsets;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.PropertyDetection;
import de.saar.coli.amrtagging.formalisms.amr.tools.Relabel;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static de.saar.coli.amrtagging.AlignedAMDependencyTree.ALIGNED_SGRAPH_SEP;

public class AMRFromNodeLabelsEvaluationToolset extends EvaluationToolset {

    /**
     * This implementation creates one file parserOut.txt which contains each graph in the corpus in a line,
     * separated by a blank line each. The graphs are in ISI AMR format (without sources), with the root being the first
     * node in the linearization. (Note: this is equal to the default implementation, but remains here for clarity, and
     * to have to freedom to change the default implementation without worrying about AMR).
     * @param outputFolderPath Filepath for the output folder. Create your own files in there!
     * @param outputCorpus The corpus that will be written to that file.
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void writeCorpus(String outputFolderPath, List<MRInstance> outputCorpus) throws IOException, InterruptedException {
        PrintWriter o = new PrintWriter(outputFolderPath+"/parserOut.txt"); //will contain graphs, potentially relabeled


        for (MRInstance s : outputCorpus){

            o.println(s.getGraph().toIsiAmrString());
            o.println();

        }

        o.close();

    }

    /**
     * Applies relabeling to the graph. Since we already have the node labels in the input sequence, this can be the default implementation,
     * plus some warnings and making sure that the "properties" nodes of the AMRs show up properly.
     * @param mrInstance the meaning representation (including the sentence and alignments, as well as POS, NE tags and lemmata, as far as they were given in the AMConll file) before postprocessing
     * @param origAMConllSentence the original AMConll sentence, which contains information pertinent to the postprocessing
     * @return
     */
    @Override
    public void applyPostprocessing(MRInstance mrInstance, AmConllSentence origAMConllSentence) {

        // Apply the same relabeling as per default. Simply copies the input over where applicable. Since there may be a version of this that still uses
        // multinode constants, I'll keep the functionality to deal with those here, without printing a warning -- JG
        for (GraphNode node : new HashSet<>(mrInstance.getGraph().getGraph().vertexSet())) {
            String[] nodeLabelTriple = node.getLabel().split(ALIGNED_SGRAPH_SEP); // this is [sentence position, node name, node label]
            if (nodeLabelTriple[2].contains(AmConllEntry.LEX_MARKER)) {
                int i = Integer.parseInt(nodeLabelTriple[0]);// is 1-based
                node.setLabel(origAMConllSentence.get(i-1).getReLexLabel()); // then we relabel the node with the lexical label for that position
            } else {
                node.setLabel(nodeLabelTriple[2]); // then we have a secondary node that already has its label
            }
        }

        // This here is AMR-specific now, dealing with removing node names where applicable.
        SGraph evaluatedGraph = mrInstance.getGraph();
        try {
            //fix properties, i.e. the nodes that do not have their own node name in the AMR string
            evaluatedGraph = PropertyDetection.fixProperties(evaluatedGraph);

            mrInstance.setGraph(evaluatedGraph);

            AMREvaluationToolset.fixPropertyAlignments(mrInstance);
        } catch (Exception ex) {
            System.err.println("In line " + origAMConllSentence.getLineNr());
            System.err.println("Ignoring exception:");
            ex.printStackTrace();
            System.err.println("Writing dummy graph instead");

            try {
                mrInstance.setGraph(new GraphAlgebra().parseString("(d / dummy)"));
            } catch (ParserException e) {
                System.err.println("This error should really really never happen...");  // famous last words -- JG
                e.printStackTrace();
            }
        }

    }

}
