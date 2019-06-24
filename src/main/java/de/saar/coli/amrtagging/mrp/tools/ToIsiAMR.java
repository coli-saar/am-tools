/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.owlike.genson.Genson;
import de.saar.coli.amrtagging.mrp.MRPInputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.InputCodec;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Takes an MRP corpus and creates an ISI-style AMR corpus from it.
 * @author matthias
 */
public class ToIsiAMR {
    
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/amr/fables.mrp";

    @Parameter(names = {"--out", "-o"}, description = "File")//, required = true)
    private String outPath = "/tmp/checking_this.amr.txt";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParserException {      
        ToIsiAMR cli = new ToIsiAMR();
        JCommander commander = new JCommander(cli);

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
        
        BufferedReader fr = new BufferedReader(new FileReader(cli.corpusPath));
        PrintWriter pw = new PrintWriter(cli.outPath);
        String line;
        InputCodec mrpCodec = new MRPInputCodec();
        while ((line = fr.readLine()) != null){
            MRPGraph g = (MRPGraph) mrpCodec.read(line);
            pw.println("# ::id "+g.getId());
            pw.println("# ::snt "+g.getInput());
            SGraph o = MRPUtils.toSGraphWoAnchoring(g);
            
            //GraphAlgebra alg = new GraphAlgebra();
            //SGraph recon = alg.parseString(o.toIsiAmrString());
            //System.err.println(o.isIdenticalExceptSources(recon));
            
            pw.println(o.toIsiAmrString());
            pw.println();
            pw.flush();
        }
        pw.close();
        fr.close();
        
    }
        
       
    
}
