package de.saar.coli.amrtagging.formalisms.cogs.tools;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * RawCOGSReader takes filename and returns CogsSamples (word and logical form tokens, generalization types) one by one
 *
 * Does NOT parse the logical forms or checks well-formedness. This class is just reads the samples from a TSV file and
 * provides access to the source (words) and target (logical form) tokens together with the generalization type
 * (through the <code>getNextSample</code> method).
 * TODO: is this the right way to implement a lazy reader?
 * TODO how to enforce UTF-8? InputStreamReader?
 * @author piaw (weissenh)
 */
public class RawCOGSReader {
    private final BufferedReader reader;
    private final String filename;
    private int linenumber;

    public RawCOGSReader(String filename) throws FileNotFoundException {
        Objects.requireNonNull(filename);
        this.linenumber = 0;
        this.filename = filename;
        this.reader = new BufferedReader(new FileReader(this.filename));
    }

    public int getLinenumber() { return linenumber; }
    public String getFilename() { return filename; }

    public boolean hasNext() {
        try {
            return reader.ready();
        } catch (IOException e) {
            //e.printStackTrace();
            return false;
        }
    }

    public CogsSample getNextSample() throws IOException, RuntimeException {
//        if (!reader.ready()) {
//            return null;
//        }
        this.linenumber += 1;
        String line = reader.readLine();
        return new CogsSample(line);
    }

    /**
     * Holds a list of source tokens (words) and target tokens (logical form tokens), plus generalization type info
     */
    public class CogsSample {
        public final List<String> src_tokens;
        public final List<String> tgt_tokens;
        public final String generalizationType;

        public CogsSample(String line) throws RuntimeException {
            Objects.requireNonNull(line);
            List<String> columns = Arrays.asList(line.split("\t"));
            if (columns.size()!= 3) {  // columns: sentence, logical form, generalization_type
                throw new RuntimeException("Each line must consist of 3 columns (tab-separated): " +
                        "Failed for " + linenumber +"\n");
            }
            String sentence = columns.get(0);
            String logicalFormString = columns.get(1);
            this.generalizationType = columns.get(2);
            this.src_tokens = Arrays.asList(sentence.split(" "));  // ["The", "boy", "wanted", "to", "go", "."]
            this.tgt_tokens = Arrays.asList(logicalFormString.split(" "));  // ["*", "boy", "(", "x", "_", "1", ")", ";", "want" , ... ]
        }

        // todo maybe store strings instead and not split/join them? depends on what we need more often: tokens or str
        public String getLogicalFormAsString() {return String.join(" ", tgt_tokens); }
        public String getSentenceAsString() {return String.join(" ", src_tokens); }
    }

}
