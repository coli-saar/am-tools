package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.util.MutableInteger;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.regex.Pattern;

public class AMRLabelSequenceDecompositionToolset extends GraphbankDecompositionToolset {
    private final EdgeAttachmentHeuristic edgeAttachmentHeuristic = new AMRBlobUtils();
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s*");
    private static final String COMMENT_PREFIX_SYMBOL = "#";
    private static final String DELIMITER = "&&";

    /**
     * @param fasterModeForTesting If fasterModeForTesting is true, then slow preprocessing measures should be skipped.
     *                             In the default implementation, this skips getting POS, lemma and named entity tags via the
     *                             stanford tagger (implementations of this class may change the exact details).
     */
    public AMRLabelSequenceDecompositionToolset(Boolean fasterModeForTesting) {
        super(fasterModeForTesting);
    }

    private static String readNextLine(BufferedReader br, MutableInteger lineNumber)
            throws IOException {

        StringBuilder stringBuilder = new StringBuilder();
        String currentString = br.readLine();

        // end of file
        if (currentString == null)
            return null;

        while (currentString != null && !WHITESPACE_PATTERN.matcher(currentString).matches()){
            // to skip comments
            if (!currentString.startsWith(AMRLabelSequenceDecompositionToolset.COMMENT_PREFIX_SYMBOL)){
                // I append new line char, because AMR graph strings are actually multiline, and we need to preserve it
                stringBuilder.append(currentString).append('\n');
            }

            lineNumber.incValue();
            currentString = br.readLine();
        }

        String resultString = stringBuilder.toString();
        // I strip last new line symbol
        return resultString.substring(0, resultString.length()-1);
    }

    @Override
    public List<MRInstance> readCorpus(String filePath) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        MutableInteger lineNumber = new MutableInteger(0);

        String line = readNextLine(br, lineNumber);
        List<MRInstance> mrInstances = new ArrayList<>();
        while (line != null) {
            // delimiter token is determined by Python code, that produces the source file.
            String[] amrInputData = line.split(DELIMITER);
            String amrId = amrInputData[0];
            String graphString = amrInputData[1];
            String alignmentsString = amrInputData[2];
            String labelsString = amrInputData[3];

            String[] labelsSentence = labelsString.split(" ");
            String[] rawAlignments = alignmentsString.split(" ");

            List<Alignment> alignmentsList = new ArrayList<>(rawAlignments.length);
            for (int i = 0; i < rawAlignments.length; i++){
                Alignment alignment = new Alignment(rawAlignments[i], i);
                alignmentsList.add(alignment);
            }

            // This makes the first node in the string the root, which is correct for AMR.
            // Admittedly, a bit hacky to edit the string like this, but it's the simplest way.
            graphString = graphString.replaceFirst("/", "<root> /");
            try {
                // System.out.print(amrId + "         ");
                SGraph sGraph = new IsiAmrInputCodec().read(graphString);

                MRInstance mrInstance = new MRInstance(Arrays.asList(labelsSentence), sGraph, alignmentsList);
                mrInstance.setId(amrId);
                mrInstances.add(mrInstance);

                line = readNextLine(br, lineNumber);
            }
            catch (Error e){
                int t = 0;
            }
        }

        return mrInstances;
    }

    @Override
    public EdgeAttachmentHeuristic getEdgeHeuristic() {
        return edgeAttachmentHeuristic;
    }
}
