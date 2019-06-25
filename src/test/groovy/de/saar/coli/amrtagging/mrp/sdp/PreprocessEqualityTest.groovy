/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.saar.coli.amrtagging.mrp.sdp

import static org.junit.Assert.*
import de.saar.coli.amrtagging.mrp.MRPInputCodec
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph
import de.saar.coli.amrtagging.mrp.sdp.dm.DM
import de.saar.coli.amrtagging.mrp.sdp.psd.PSD
import org.junit.*
/**
 *
 * @author matthias
 */
class PreprocessEqualityTest {
    
    @Test
    public void testEquality() {
        String strG = "{\"id\": \"20041037\", \"flavor\": 0, \"framework\": \"dm\", \"version\": 0.9, \"time\": \"2019-04-10 (20:16)\", \"input\": \"Virginia:\", \"tops\": [0], \"nodes\": [{\"id\": 0, \"label\": \"Virginia\", \"properties\": [\"pos\"], \"values\": [\"NNP\"], \"anchors\": [{\"from\": 0, \"to\": 8}]}], \"edges\": []}"
        MRPInputCodec codec = new MRPInputCodec()
        MRPGraph g = codec.read(strG)
        DM dm = new DM();
        MRPGraph g2 = dm.postprocess(dm.preprocess(g));
        System.err.println(g.equals(g2))
        System.err.println(g2.equals(g))
        assertEquals(g,g2)
        assertEquals(g2,g) //not equal, what the heck?
        
    }
}

