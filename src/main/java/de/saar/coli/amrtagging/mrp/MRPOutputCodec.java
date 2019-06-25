/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp;

import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.up.ling.irtg.codec.CodecMetadata;
import de.up.ling.irtg.codec.CodecParseException;
import de.up.ling.irtg.codec.OutputCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;


/**
 *
 * @author matthias
 */
@CodecMetadata(name = "mrp", description = "MRP Shared Task 2019 format", type = MRPGraph.class, extension = "mrp")
public class MRPOutputCodec extends OutputCodec<MRPGraph>{
    
    private final Genson genson = new GensonBuilder().setSkipNull(true).create();

    @Override
    public void write(MRPGraph e, OutputStream out) throws IOException, UnsupportedOperationException {
        genson.serialize(e, out);
        out.write('\n');
    }
    
}
