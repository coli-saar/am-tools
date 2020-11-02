package de.saar.coli.amrtagging.formalisms.sdp.tools;

import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;

public class TestSDPFile {

    public static void main(String[] args) throws IOException {
        GraphReader2015 gr = new GraphReader2015("test.sdp");

        while (gr.ready()) {
            System.out.println(gr.readGraph().id);
        }

    }

}
