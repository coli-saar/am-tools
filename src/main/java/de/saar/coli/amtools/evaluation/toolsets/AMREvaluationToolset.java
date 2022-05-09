package de.saar.coli.amtools.evaluation.toolsets;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.PropertyDetection;
import de.saar.coli.amrtagging.formalisms.amr.tools.Relabel;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.*;
import org.apache.tools.ant.types.Commandline;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AMREvaluationToolset extends EvaluationToolset {


    public static final String AL_LABEL_SEP = "|";




    @Parameter(names = {"--keep-aligned"}, description = "keep index of token position in node label")
    private boolean keepAligned = false;

    @Parameter(names = {"--add-sense-to-nn-label"}, description = "if true, adds a -01 suffix to all lexlabel predictions " +
            "by the neural network that have no sense suffix but belong to a node with outgoing ARGx edges")
    private boolean addSenseToNNLabel = false;

    @Parameter(names = {"--th"}, description = "Threshold for relabeler. Default: 10")
    private int threshold = 10;

    @Parameter(names = {"--wn"}, description = "Path to WordNet", required = true)
    private String wordnet = null;

    @Parameter(names = {"--lookup"}, description = "Lookup path. Path to where the files nameLookup.txt, " +
            "nameTypeLookup.txt, wikiLookup.txt, words2labelsLookup.txt are.", required = true)
    private String lookup = null;


    private final Relabel relabeler;

    public AMREvaluationToolset(String extra) throws IOException, InterruptedException {
        JCommander commander = new JCommander(this);
        commander.setProgramName("constraint_extractor");

//        System.out.println("Creating AMREvaluationToolset with the following parameters:");
//        System.out.println(extra);



        try {
            commander.parse(Commandline.translateCommandline(extra));
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured when reading the parameters for AMREvaluationToolset, presumably passed with the '--extra' option: " + ex.toString());
            System.err.println("This constructor received these parameters: "+extra);
            System.err.println("\n Available options: ");
            commander.usage();
            throw new RuntimeException("Invalid arguments to constructor of AMREvaluationToolset");
        }

//        System.err.println("wordnet");
//        System.err.println(wordnet);
//        System.err.println("lookup");
//        System.err.println(lookup);
//        System.err.println("threshold");
//        System.err.println(threshold);

        relabeler = new Relabel(wordnet, null, lookup, threshold, 0,false, addSenseToNNLabel);

    }

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
     * Applies relabeling to the graph. This is (should be) equivalent to the technique of
     * de.saar.coli.amrtagging.formalisms.amr.tools.EvaluateCorpus with --relabel enabled.
     * @param mrInstance the meaning representation (including the sentence and alignments, as well as POS, NE tags and lemmata, as far as they were given in the AMConll file) before postprocessing
     * @param origAMConllSentence the original AMConll sentence, which contains information pertinent to the postprocessing
     * @return
     */
    @Override
    public void applyPostprocessing(MRInstance mrInstance,AmConllSentence origAMConllSentence) {


        // relabel graph
        SGraph evaluatedGraph = mrInstance.getGraph();

        // Change nodes labels from 1@@m@@--LEX-- to LEX@0
        // This is for compatibility with the older Relabel class
        List<String> labels = origAMConllSentence.lemmas();
        for (String n : evaluatedGraph.getAllNodeNames()) {
            if (evaluatedGraph.getNode(n).getLabel().contains("LEX")) {
                Pair<Integer, Pair<String, String>> info = AlignedAMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                labels.set(info.left - 1, origAMConllSentence.get(info.left - 1).getReLexLabel());
                evaluatedGraph.getNode(n).setLabel(Relabel.LEXMARKER + (info.left - 1));
            } else {
                Pair<Integer, Pair<String, String>> info = AlignedAMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                evaluatedGraph.getNode(n).setLabel(info.right.right);
            }
        }

        try {
            relabeler.fixGraph(evaluatedGraph, origAMConllSentence.getFields((AmConllEntry entry) ->
            {
                if (entry.getReplacement().equals("_")) {
                    //if supertagger thinks this is a named entity, we trust it
                    if (entry.getLexLabel().toLowerCase().equals("_name_")) {
                        return entry.getLexLabel().toLowerCase();
                    }
                    return entry.getForm().toLowerCase();
                } else {
                    return entry.getReplacement().toLowerCase();
                }
            }), origAMConllSentence.words(), origAMConllSentence.getFields(AmConllEntry::getReLexLabel));

            if (keepAligned) {
                //now add alignment again, format: POSITION|NODE LABEL where POSITION is 0-based.

                Map<String, Integer> nodeNameToPosition = new HashMap<>();

                for (Alignment al : mrInstance.getAlignments()) {
                    for (String nodeName : al.nodes) {
                        nodeNameToPosition.put(nodeName, al.span.start);
                    }
                }

                for (String nodeName : nodeNameToPosition.keySet()) {
                    GraphNode node = evaluatedGraph.getNode(nodeName);
                    if (node == null) {
                        System.err.println("Warning: a nodename for which we have an alignment cannot be found in the relabeled graph");
                    } else {
                        node.setLabel(nodeNameToPosition.get(nodeName) + AL_LABEL_SEP + node.getLabel());
                    }

                }

                // try to find alignments for nodes that the relabeling introduced.

                for (GraphNode node : evaluatedGraph.getGraph().vertexSet()) {
                    if (!nodeNameToPosition.containsKey(node.getName())) {
                        Set<GraphEdge> edges = evaluatedGraph.getGraph().edgesOf(node);
                        if (edges.size() == 1) {
                            GraphNode endPoint = BlobUtils.otherNode(node, edges.iterator().next());
                            if (nodeNameToPosition.containsKey(endPoint.getName())) {
                                node.setLabel(nodeNameToPosition.get(endPoint.getName()) + AL_LABEL_SEP + node.getLabel());
                            } else {
                                System.err.println("Warning: cannot find unique alignment for a node with no inherent alignment.");
                            }
                        } else {
                            System.err.println("Warning: cannot find unique alignment for a node with no inherent alignment and multiple adjacent edges.");
                        }
                    }
                }
            }

            //fix properties
            evaluatedGraph = PropertyDetection.fixProperties(evaluatedGraph);

            mrInstance.setGraph(evaluatedGraph);

            fixPropertyAlignments(mrInstance);
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

    /**
     * fixing the properties in the graph changes some node names by adding a "_" in front. This breaks the
     * alignments in MRInstance. Here we fix that.
     * @param mrInstance
     */
    public static void fixPropertyAlignments(MRInstance mrInstance) {
        for (GraphNode node : mrInstance.getGraph().getGraph().vertexSet()) {
            if (node.getName().startsWith("_")) {
                String oldNodeName = node.getName().substring(1); // without the "_"
                for (Alignment al : mrInstance.getAlignments()) {
                    if (al.nodes.contains(oldNodeName)) {
                        al.nodes.remove(oldNodeName);
                        al.nodes.add(node.getName());
                    }
                    if (al.lexNodes.contains(oldNodeName)) {
                        al.lexNodes.remove(oldNodeName);
                        al.lexNodes.add(node.getName());
                    }
                }
            }
        }
    }

}
