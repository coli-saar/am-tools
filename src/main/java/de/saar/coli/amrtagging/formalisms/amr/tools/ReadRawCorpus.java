/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import de.saar.coli.amrtagging.Util;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.CodecParseException;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author JG
 */
public class ReadRawCorpus {
    
    public static List<SGraph> readGraphs(String corpusPath) throws FileNotFoundException, IOException {
        BufferedReader br = new BufferedReader(new FileReader(corpusPath));
        List<SGraph> ret = new ArrayList<>();
        IsiAmrInputCodec codec = new IsiAmrInputCodec();
        String graphSoFar = "";
        while (br.ready()) {
            String line = br.readLine().trim();
            if (!line.startsWith("#")) {
                if (line.equals("")) {
                    // graph is done, and we read it from string
                    try {
                        graphSoFar = graphSoFar.replaceAll("\\/ ([^ \"]+)", "/ \"$1\"");
                        graphSoFar = graphSoFar.replaceFirst("/", "<root> /");
                        //System.err.println(graphSoFar);
                        graphSoFar = Util.fixPunct(graphSoFar);
                        ret.add(codec.read(graphSoFar));
                    } catch (java.lang.Exception | java.lang.Error ex ) {
                        System.err.println("Error reading graph: "+graphSoFar);
                        System.err.println(ex.toString());
                    }
                    graphSoFar = "";
                } else {
                    graphSoFar += " " + line;
                }
            }
        }
        br.close();
        return ret;
    }
    
}
