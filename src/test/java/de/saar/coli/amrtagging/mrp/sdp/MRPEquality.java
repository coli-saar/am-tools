/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.sdp;
import static org.junit.Assert.*;
import de.saar.coli.amrtagging.mrp.MRPInputCodec;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import java.io.IOException;
import org.junit.*;

/**
 *
 * @author matthias
 */
public class MRPEquality {
    /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
    
    final String strG = "{\"id\": \"20041037\", \"flavor\": 0, \"framework\": \"dm\", \"version\": 0.9, \"time\": \"2019-04-10 (20:16)\", \"input\": \"Virginia:\", \"tops\": [0], \"nodes\": [{\"id\": 0, \"label\": \"Virginia\", \"properties\": [\"pos\"], \"values\": [\"NNP\"], \"anchors\": [{\"from\": 0, \"to\": 8}]}], \"edges\": []}";
    MRPInputCodec codec = new MRPInputCodec();
    MRPOutputCodec out = new MRPOutputCodec();
    MRPGraph g = codec.read(strG);
    
    @Test
    public void testEquality1() {
        MRPGraph g2 = g.deepCopy();
        assertEquals(g,g2);
    }
    
    @Test
    public void testEquality2() throws IOException{
        DM dm = new DM();
        MRPGraph g2 = dm.postprocess(dm.preprocess(g));
        out.write(g,System.err);
        out.write(g2,System.err);
        System.err.println(g.equals(g2));
        System.err.println(g2.equals(g));
        assertEquals(g,g2);
        assertEquals(g2,g);
        
    }


}
