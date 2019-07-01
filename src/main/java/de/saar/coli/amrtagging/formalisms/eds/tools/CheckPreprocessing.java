/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds.tools;

import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.GraphStats;
import de.saar.coli.amrtagging.formalisms.amr.tools.ReadRawCorpus;
import de.saar.coli.amrtagging.formalisms.eds.Aligner;
import de.saar.coli.amrtagging.formalisms.eds.EDSConverter;
import de.saar.coli.amrtagging.formalisms.eds.EDSUtils;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes a raw ISI-AMR style corpus (which is used for EDS) and preprocesses it (infers tokenization from graph).
 * @author matthias
 */
public class CheckPreprocessing {
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        String path = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/meine_Daten/EDS-toy/test.amr.txt";
        PrintWriter w = new PrintWriter("g.txt");
        PrintWriter edm = new PrintWriter("g.edm");
        List<AnchoredSGraph> allGraphs = ReadRawCorpus.readGraphs(path);
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;
        List<String> allSents = new ArrayList<>(); 
        while ((line = br.readLine()) != null){
            if (line.startsWith("# ::snt ")) {
                allSents.add(line.substring("# ::snt ".length()));
            }
        }
        br.close();
        int ok = 0;
        for (int i = 0; i< allGraphs.size(); i++){
            //System.err.println(allGraphs.get(i).toIsiAmrString());
            //System.err.println(allSents.get(i));
            MRInstance inst = EDSConverter.toSGraph(allGraphs.get(i), allSents.get(i));
            //System.err.println(Aligner.simpleAlignViz(inst));
            //SGraph recon = EDSConverter.restoreComplexSpans(inst.getGraph());
            //boolean reconstructionWorked = recon.isIsomorphicAlsoEdges(allGraphs.get(i));
            SGraph out = EDSConverter.undoExplicitAnon(AnchoredSGraph.fromSGraph(inst.getGraph()));
            w.println(out.toIsiAmrString());
            w.println();
            edm.println(EDSConverter.toEDM(allGraphs.get(i)));
            //System.err.println(reconstructionWorked);
            if (false){
                ok++;
            } else{
                //SGraphDrawer.draw(allGraphs.get(i), "orig");
                //SGraphDrawer.draw(recon, "recon");
            }
            //System.err.println(inst.getAlignments());
            
            //System.err.println();
       }
        w.close();
        edm.close();
        System.err.println("OK "+ok + " von "+allGraphs.size());
        
        //recall with heuristically restoring complex spans: around 0.9983
        //Hypothese: folgendes könnte gehen:
        //lösche alle komplexen Spannen
        //Rekonstruktion (Projektivität?):
        //für alle Knoten (ohne eingehende carg-Kante) define die Spanne als <min(Kinder),max(Kinder)>
        
    }
    
}
