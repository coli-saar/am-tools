/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp;

import com.owlike.genson.Genson;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.up.ling.irtg.codec.CodecMetadata;
import de.up.ling.irtg.codec.CodecParseException;
import de.up.ling.irtg.codec.InputCodec;
import java.io.IOException;
import java.io.InputStream;


/**
 *
 * @author matthias
 */
@CodecMetadata(name = "mrp", description = "MRP Shared Task 2019 format", type = MRPGraph.class, extension = "mrp")
public class MRPInputCodec extends InputCodec<MRPGraph>{
    
    private final Genson genson = new Genson();

    @Override
    public MRPGraph read(InputStream in) throws CodecParseException, IOException {
        MRPGraph g = genson.deserialize(in, MRPGraph.class);
        g.sanitize();
        for (MRPNode n : g.getNodes()){
            n.setLabel(n.getLabel().toLowerCase());
        }
        return g;
    }
    
}
