/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd.tools;

import de.saar.coli.amrtagging.formalisms.sdp.dm.tools.*;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import me.tongfei.progressbar.ProgressBar;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.Constants;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.io.GraphWriter2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;

/**
 * Outputs an SDP graphs as json objects. This is used by the new web demo
 * that uses Vulcan for visualization, as this graph format is easy to read.
 * 
 * Example: 
 * {"tokens" : ["This", "is", "a", "test", "."], "edges": [[3, "BV", 4], [2, "ARG1", 1], [2, "ARG2", 4]]}
 * 
 * Note the one-based indexing.
 * @author matthias
 */
public class ToSDPJsonl {
    
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")
//, required = true)
    private String corpusPath = "/tmp/test_psdl.amconll";

    @Parameter(names = {"--outFile", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/tmp/psd/a";

    @Parameter(names = {"--help", "-?", "-h"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;
    
    @Parameter(names = {"--legacyACL19"}, description = "Uses legacy version of debugging, compatible with our ACL 2019 paper")
    private boolean legacyACL19 = false;
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException {
        ToSDPJsonl cli = new ToSDPJsonl();
        JCommander commander = new JCommander(cli);
        commander.setProgramName("amconll_sdp_jsonl_converter");
        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }


        List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);

        final ProgressBar pb = new ProgressBar("Converting", sents.size());
        
        PrintWriter pw = new PrintWriter(cli.outPath + ".jsonl");


        for (AmConllSentence s : sents) {
            
            String id = s.getAttr("id") != null ? s.getAttr("id") : "#NO-ID";

            try {
                AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
                SGraph evaluatedGraph = amdep.evaluate(true);
                
                evaluatedGraph = ConjHandler.restoreConj(evaluatedGraph, new PSDBlobUtils(), cli.legacyACL19); //really important!
                
                pw.println(SGraphConverter.toSDPJson(evaluatedGraph, s.words()));


            } catch (Exception ex) {
                System.err.printf("In line %d, id=%s: ignoring exception.\n", s.getLineNr(), id);
                ex.printStackTrace();
                System.err.println("Writing graph without edges instead.\n");

                pw.println(SGraphConverter.toSDPJson(new SGraph(), s.words()));

            }

            synchronized (pb) {
                pb.step();
            }
        }

        pb.close();
        
        pw.close();

    }
    
}
